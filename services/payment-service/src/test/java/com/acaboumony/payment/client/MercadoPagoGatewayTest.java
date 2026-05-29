package com.acaboumony.payment.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoGatewayTest {

    private final MercadoPagoGateway gateway = new MercadoPagoGateway();

    @Test
    void createPayment_whenMpUnavailable_returnsTimeout() {
        var result = gateway.createPayment(
            "invalid_token", 1000L, "visa", 1,
            UUID.randomUUID(), "test@test.com");

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.isTimeout());
    }
}
