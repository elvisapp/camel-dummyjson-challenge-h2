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

    @Produce("direct:payOrder")
    private ProducerTemplate producerTemplate;

    @MockBean
    private OrderService orderService;

    @EndpointInject("mock:success-url")
    private MockEndpoint successEndpoint;

    @EndpointInject("mock:failure-url")
    private MockEndpoint failureEndpoint;

    private PaymentProperties paymentProperties;

    @BeforeEach
    void setUp() throws Exception {
        paymentProperties = new PaymentProperties();
        paymentProperties.setSuccessUrl("mock:success-url");
        paymentProperties.setFailureUrl("mock:failure-url");
        
        PaymentProperties.Retry retry = new PaymentProperties.Retry();
        retry.setMaxRedeliveries(3);
        retry.setRedeliveryDelayMs(100);
        retry.setBackoffMultiplier(2.0);
        paymentProperties.setRetry(retry);

        // Usar AdviceWith para interceptar a rota e usar endpoints mock
        AdviceWith.adviceWith(camelContext, "payment-route", route -> {
            // Interceptar o when() que chama success-url e substituir por mock
            route.interceptSendToEndpoint("https://dummyjson.com/http/200")
                .skipSendToOriginalEndpoint()
                .to("mock:success-url");
            
            // Interceptar o when() que chama failure-url e substituir por mock
            route.interceptSendToEndpoint("https://dummyjson.com/http/500")
                .skipSendToOriginalEndpoint()
                .to("mock:failure-url");
        });

        // Iniciar contexto se não estiver rodando
        if (!camelContext.isStarted()) {
            camelContext.start();
        }
    }

    @Test
    void testPaymentSuccess() throws Exception {
        String orderId = "order123";
        double amount = 500.0; // <= 1000, deve usar success-url

        // Verificar se o contexto está iniciado
        if (!camelContext.isStarted()) {
            camelContext.start();
        }

        // Configurar mock endpoint para receber 1 mensagem
        successEndpoint.expectedMessageCount(1);

        // Enviar para a rota
        Map<String, Object> headers = new HashMap<>();
        headers.put("orderId", orderId);
        headers.put("amount", amount);

        try {
            producerTemplate.sendBodyAndHeaders(null, headers);
            
            // Verificar se o mock recebeu a mensagem
            successEndpoint.assertIsSatisfied();
            
            // Verificar se o método markPaid foi chamado
            verify(orderService, times(1)).markPaid(orderId);
        } finally {
            // Limpar expectativas
            successEndpoint.reset();
        }
    }

    @Test
    void testPaymentFailure() throws Exception {
        String orderId = "order456";
        double amount = 1500.0; // > 1000, deve usar failure-url

        // Verificar se o contexto está iniciado
        if (!camelContext.isStarted()) {
            camelContext.start();
        }

        // Configurar mock endpoint para receber 1 mensagem
        failureEndpoint.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("orderId", orderId);
        headers.put("amount", amount);

        try {
            producerTemplate.sendBodyAndHeaders(null, headers);
            
            // Verificar se o mock recebeu a mensagem
            failureEndpoint.assertIsSatisfied();
            
            // Como o amount > 1000, deve usar failure-url e não deve chamar markPaid
            verify(orderService, never()).markPaid(orderId);
        } finally {
            // Limpar expectativas
            failureEndpoint.reset();
        }
    }

    @Test
    void testPaymentSuccessWithExactBoundary() throws Exception {
        String orderId = "order789";
        double amount = 1000.0; // Exatamente 1000, deve usar success-url

        // Verificar se o contexto está iniciado
        if (!camelContext.isStarted()) {
            camelContext.start();
        }

        // Configurar mock endpoint para receber 1 mensagem
        successEndpoint.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("orderId", orderId);
        headers.put("amount", amount);

        try {
            producerTemplate.sendBodyAndHeaders(null, headers);
            
            // Verificar se o mock recebeu a mensagem
            successEndpoint.assertIsSatisfied();
            
            // Verificar se o método markPaid foi chamado
            verify(orderService, times(1)).markPaid(orderId);
        } finally {
            // Limpar expectativas
            successEndpoint.reset();
        }
    }
}