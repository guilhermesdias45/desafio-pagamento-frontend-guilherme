package com.acaboumony.payment.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceClientTest {

    private final OrderServiceClient client = new OrderServiceClient("http://localhost:9999", CircuitBreakerRegistry.ofDefaults());

    @Test
    void validateOrder_whenServiceUnavailable_returnsUnavailable() {
        var result = client.validateOrder(UUID.randomUUID(), UUID.randomUUID());

        assertNotNull(result);
        assertFalse(result.valid());
        assertEquals("ORDER_SERVICE_UNAVAILABLE", result.errorCode());
    }

    @Test
    void validateOrder_recordConstructor_works() {
        var result = new OrderServiceClient.OrderValidationResult(true, null);

        assertTrue(result.valid());
        assertNull(result.errorCode());
    }

    @Test
    void validateOrder_recordWithErrorCode() {
        var result = new OrderServiceClient.OrderValidationResult(false, "ORDER_NOT_FOUND");

        assertFalse(result.valid());
        assertEquals("ORDER_NOT_FOUND", result.errorCode());
    }
}
