package com.acaboumony.payment.client;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MercadoPagoGatewayMockedTest {

    @Mock
    private PaymentClient paymentClient;

    private MercadoPagoGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        gateway = new MercadoPagoGateway(800L, CircuitBreakerRegistry.ofDefaults());
        var clientField = MercadoPagoGateway.class.getDeclaredField("paymentClient");
        clientField.setAccessible(true);
        clientField.set(gateway, paymentClient);
    }

    @Test
    void createPayment_mpException_returnsTimeout() throws Exception {
        when(paymentClient.create(any())).thenThrow(new MPException("SDK error"));

        var result = gateway.createPayment(
            "tok_visa", 5000L, "visa", 1,
            UUID.randomUUID(), "test@test.com");

        assertFalse(result.success());
        assertTrue(result.isTimeout());
    }

    @Test
    void refundPayment_withAmount_success() throws Exception {
        var refund = new com.mercadopago.resources.payment.PaymentRefund();
        when(paymentClient.refund(eq(123456L), any(BigDecimal.class))).thenReturn(refund);

        var result = gateway.refundPayment(123456L, 5000L);

        assertTrue(result.success());
    }

    @Test
    void refundPayment_failure_returnsUnsuccess() throws Exception {
        when(paymentClient.refund(eq(123456L), any(BigDecimal.class)))
            .thenThrow(new RuntimeException("Network error"));

        var result = gateway.refundPayment(123456L, 5000L);

        assertFalse(result.success());
        assertNull(result.mpRefundId());
    }
}
