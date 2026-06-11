package com.acaboumony.payment.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoGatewayTest {

    private final MercadoPagoGateway gateway = new MercadoPagoGateway(800L, "",
        CircuitBreakerRegistry.ofDefaults());

    @Test
    void createPayment_whenMpUnavailable_returnsTimeout() {
        var result = gateway.createPayment(
            "invalid_token", 1000L, "visa", 1,
            UUID.randomUUID(), "test@test.com");

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.isTimeout());
    }

    @Test
    void paymentResult_approved_hasCorrectFields() {
        var result = MercadoPagoGateway.PaymentResult.approved(123456L);

        assertTrue(result.success());
        assertEquals(123456L, result.mpPaymentId());
        assertNull(result.errorCode());
        assertFalse(result.isTimeout());
    }

    @Test
    void paymentResult_declined_hasCorrectFields() {
        var result = MercadoPagoGateway.PaymentResult.declined("CARD_DECLINED");

        assertFalse(result.success());
        assertNull(result.mpPaymentId());
        assertEquals("CARD_DECLINED", result.errorCode());
        assertFalse(result.isTimeout());
    }

    @Test
    void paymentResult_timeout_hasCorrectFields() {
        var result = MercadoPagoGateway.PaymentResult.timeout();

        assertFalse(result.success());
        assertNull(result.mpPaymentId());
        assertEquals("MP_GATEWAY_TIMEOUT", result.errorCode());
        assertTrue(result.isTimeout());
    }

    @Test
    void refundResult_success_hasCorrectFields() {
        var result = new MercadoPagoGateway.RefundResult(true, 789L);

        assertTrue(result.success());
        assertEquals(789L, result.mpRefundId());
    }

    @Test
    void refundResult_failure_hasCorrectFields() {
        var result = new MercadoPagoGateway.RefundResult(false, null);

        assertFalse(result.success());
        assertNull(result.mpRefundId());
    }

    @Test
    void refundPayment_whenServiceUnavailable_returnsFailure() {
        var result = gateway.refundPayment(123456L, 5000L);

        assertNotNull(result);
        assertFalse(result.success());
        assertNull(result.mpRefundId());
    }

    @Test
    void refundPayment_withNullAmount_returnsFailure() {
        var result = gateway.refundPayment(123456L, null);

        assertNotNull(result);
        assertFalse(result.success());
    }
}
