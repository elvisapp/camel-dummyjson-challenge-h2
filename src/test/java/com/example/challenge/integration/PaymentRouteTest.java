package com.example.challenge.integration;

import com.example.challenge.service.OrderService;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes Camel para PaymentRoute:
 * - sucesso (amount <= 1000) → success-url → markPaid
 * - falha (amount > 1000) → failure-url → retry → markFailed
 */
@SpringBootTest
@CamelSpringBootTest
@UseAdviceWith
@ActiveProfiles("test")
class PaymentRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockBean
    private OrderService orderService;

    @EndpointInject("mock:success-url")
    private MockEndpoint mockSuccessUrl;

    @EndpointInject("mock:failure-url")  
    private MockEndpoint mockFailureUrl;

    private PaymentProperties paymentProperties;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties();
        paymentProperties.setSuccessUrl("mock:success-url");
        paymentProperties.setFailureUrl("mock:failure-url");
        
        PaymentProperties.Retry retry = new PaymentProperties.Retry();
        retry.setMaxRedeliveries(3);
        retry.setRedeliveryDelayMs(100);
        retry.setBackoffMultiplier(2.0);
        paymentProperties.setRetry(retry);
    }

    @Test
    void testPaymentSuccess() throws Exception {
        // Configurar mock para sucesso
        mockSuccessUrl.whenAnyExchangeReceived(exchange -> {
            // Simular resposta HTTP 200 de sucesso
        });

        // Advice para interceptar llamadas HTTP
        AdviceWith.adviceWith(camelContext, "payment-route", builder -> {
            builder.interceptSendToEndpoint("https://dummyjson.com/http/200")
                .to("mock:success-url");
        });

        // Iniciar contexto
        camelContext.start();

        String orderId = "order123";
        double amount = 500.0; // <= 1000, deve usar success-url

        // Enviar para a rota
        Map<String, Object> headers = new HashMap<>();
        headers.put("orderId", orderId);
        headers.put("amount", amount);

        producerTemplate.sendBodyAndHeaders("direct:payOrder", null, headers);

        // Verificar chamadas
        mockSuccessUrl.expectedMessageCount(1);
        verify(orderService, times(1)).markPaid(eq(orderId));
        verify(orderService, never()).markFailed(eq(orderId));
    }

    @Test
    void testPaymentFailure() throws Exception {
        // Configurar mock para falhar sempre
        mockFailureUrl.whenAnyExchangeReceived(exchange -> {
            // Não fazer nada, vai tentar múltiplas vezes até falhar
        });

        AdviceWith.adviceWith(camelContext, "payment-route", builder -> {
            builder.interceptSendToEndpoint("https://dummyjson.com/http/500")
                .to("mock:failure-url");
        });

        camelContext.start();

        String orderId = "order456";
        double amount = 1500.0; // > 1000, deve usar failure-url

        Map<String, Object> headers = new HashMap<>();
        headers.put("orderId", orderId);
        headers.put("amount", amount);

        producerTemplate.sendBodyAndHeaders("direct:payOrder", null, headers);

        // Esperar que a rota complete após o máximo de retries
        Thread.sleep(1000); // Aguardar processamento

        verify(orderService, never()).markPaid(eq(orderId));
        verify(orderService, times(1)).markFailed(eq(orderId));
    }

    @Test
    void testPaymentSuccessWithExactBoundary() throws Exception {
        // Testar amount = 1000 (limite exato)
        mockSuccessUrl.whenAnyExchangeReceived(exchange -> {
            // Simular resposta HTTP 200 de sucesso
        });

        AdviceWith.adviceWith(camelContext, "payment-route", builder -> {
            builder.interceptSendToEndpoint("https://dummyjson.com/http/200")
                .to("mock:success-url");
        });

        camelContext.start();

        String orderId = "order789";
        double amount = 1000.0; // Exatamente 1000, deve usar success-url

        Map<String, Object> headers = new HashMap<>();
        headers.put("orderId", orderId);
        headers.put("amount", amount);

        producerTemplate.sendBodyAndHeaders("direct:payOrder", null, headers);

        mockSuccessUrl.expectedMessageCount(1);
        verify(orderService, times(1)).markPaid(eq(orderId));
        verify(orderService, never()).markFailed(eq(orderId));
    }
}