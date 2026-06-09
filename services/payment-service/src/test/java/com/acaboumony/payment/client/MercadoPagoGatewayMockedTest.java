package com.acaboumony.payment.client;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.core.MPRequestOptions;
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
    void createPayment_withSellerToken_passesRequestOptions() throws Exception {
        var payment = new com.mercadopago.resources.payment.Payment();
        var idField = com.mercadopago.resources.payment.Payment.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(payment, 999L);
        var statusField = com.mercadopago.resources.payment.Payment.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(payment, "approved");

        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class)))
            .thenReturn(payment);

        var result = gateway.createPayment(
            "tok_visa", 5000L, "visa", 1,
            UUID.randomUUID(), "test@test.com", "seller_test_token_123");

        assertTrue(result.success());
        assertEquals(999L, result.mpPaymentId());
        verify(paymentClient).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
    }

    @Test
    void createPayment_withoutSellerToken_callsDefaultCreate() throws Exception {
        var payment = new com.mercadopago.resources.payment.Payment();
        var idField = com.mercadopago.resources.payment.Payment.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(payment, 888L);
        var statusField = com.mercadopago.resources.payment.Payment.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(payment, "approved");

        when(paymentClient.create(any(PaymentCreateRequest.class))).thenReturn(payment);

        var result = gateway.createPayment(
            "tok_visa", 5000L, "visa", 1,
            UUID.randomUUID(), "test@test.com");

        assertTrue(result.success());
        assertEquals(888L, result.mpPaymentId());
        verify(paymentClient).create(any(PaymentCreateRequest.class));
    }

    @Test
    void createPayment_withSellerToken_mpException_returnsTimeout() throws Exception {
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class)))
            .thenThrow(new MPException("SDK error with seller token"));

        var result = gateway.createPayment(
            "tok_visa", 5000L, "visa", 1,
            UUID.randomUUID(), "test@test.com", "seller_bad_token");

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
