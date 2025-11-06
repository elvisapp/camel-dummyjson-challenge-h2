package com.example.challenge.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.camel.Exchange;
import com.example.challenge.service.OrderService;

/**
 * Rota do pagamento implementada:
 * - from("direct:payOrder") com headers orderId e amount
 * - if amount > 1000 -> chamar failureUrl, else -> successUrl
 * - onException(HttpOperationFailedException) com retries/backoff das props
 * - chamar OrderService diretamente para marcar PAID/FAILED
 */
@Component
public class PaymentRoute extends RouteBuilder {
  private static final Logger log = LoggerFactory.getLogger(PaymentRoute.class);
  
  public static final String DIRECT_PAY = "direct:payOrder";

  private final PaymentProperties props;
  private final OrderService orderService;

  public PaymentRoute(PaymentProperties props, OrderService orderService) {
    this.props = props;
    this.orderService = orderService;
  }

  @Override
  public void configure() throws Exception {
    // Configurar retry com backoff exponencial
    onException(HttpOperationFailedException.class)
      .maximumRedeliveries(props.getRetry().getMaxRedeliveries())
      .redeliveryDelay(props.getRetry().getRedeliveryDelayMs())
      .backOffMultiplier(props.getRetry().getBackoffMultiplier())
      .logRetryAttempted(true)
      .logExhausted(true)
      .log("Falha no pagamento para pedido ${header.orderId} - exchangeId: ${exchangeId}")
      .process(exchange -> {
        String orderId = exchange.getMessage().getHeader("orderId", String.class);
        // Fallback: usar exchange property se header estiver null
        if (orderId == null) {
          orderId = exchange.getProperty("orderId", String.class);
        }
        log.info("Marcando pedido {} como FAILED_PAYMENT após exaustar retries - exchangeId: {}",
                 orderId, exchange.getExchangeId());
        if (orderId != null) {
          orderService.markFailed(orderId);
        } else {
          log.error("Não foi possível obter orderId - exchangeId: {}", exchange.getExchangeId());
        }
      });

    from("direct:payOrder")
      .log("Iniciando processamento de pagamento para pedido ${header.orderId}, amount: ${header.amount} - exchangeId: ${exchangeId}")
      // Salvar orderId como property para casos de erro
      .setProperty("orderId").header("orderId")
      .choice()
        .when().simple("${header.amount} <= 1000")
          .log("Chamando URL de sucesso para pedido ${header.orderId} - exchangeId: ${exchangeId}")
          .to(props.getSuccessUrl())
          .log("Pagamento aprovado para pedido ${header.orderId} - exchangeId: ${exchangeId}")
          .process(exchange -> {
            String orderId = exchange.getMessage().getHeader("orderId", String.class);
            log.info("Marcando pedido {} como PAID - exchangeId: {}",
                     orderId, exchange.getExchangeId());
            orderService.markPaid(orderId);
          })
        .otherwise()
          .log("Chamando URL de falha para pedido ${header.orderId} - exchangeId: ${exchangeId}")
          .to(props.getFailureUrl())
      .endChoice();
  }
}
