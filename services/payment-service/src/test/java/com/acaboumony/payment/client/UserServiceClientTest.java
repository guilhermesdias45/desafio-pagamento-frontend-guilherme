package com.acaboumony.payment.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceClientTest {

    private final UserServiceClient client = new UserServiceClient("http://localhost:9999");

    @Test
    void validateCustomer_whenServiceUnavailable_returnsValid() {
        var result = client.validateCustomer(UUID.randomUUID());

        assertNotNull(result);
        assertTrue(result.valid());
        assertNull(result.errorCode());
    }

    @Test
    void validateCustomer_recordConstructor_works() {
        var result = new UserServiceClient.UserValidationResult(true, null);

        assertTrue(result.valid());
        assertNull(result.errorCode());
    }

    @Test
    void validateCustomer_recordWithErrorCode() {
        var result = new UserServiceClient.UserValidationResult(false, "CUSTOMER_NOT_FOUND");

        assertFalse(result.valid());
        assertEquals("CUSTOMER_NOT_FOUND", result.errorCode());
    }
}
