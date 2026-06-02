package com.acaboumony.payment.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleConstraintViolation_returns400WithFieldErrors() {
        var violation = mock(ConstraintViolation.class);
        var path = mock(Path.class);
        when(path.toString()).thenReturn("amountInCents");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be greater than 0");
        Set<ConstraintViolation<?>> violations = Set.of(violation);
        var ex = new ConstraintViolationException(violations);

        var response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
        assertEquals("Validation failed", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldErrors() {
        var bindingResult = mock(BindingResult.class);
        var fieldError = new FieldError("request", "currency", "Only BRL is supported");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        var ex = new MethodArgumentNotValidException(
                (MethodParameter) null, bindingResult);

        var response = handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
    }

    @Test
    void handleMethodArgumentNotValid_withNullDefaultMessage_usesFallback() {
        var bindingResult = mock(BindingResult.class);
        var fieldError = new FieldError("request", "amount", null, false, null, null, "raw value");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        var ex = new MethodArgumentNotValidException(
                (MethodParameter) null, bindingResult);

        var response = handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void handleIllegalArgument_returns422() {
        var ex = new IllegalArgumentException("TRANSACTION_NOT_FOUND");

        var response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(422, body.get("status"));
        assertEquals("TRANSACTION_NOT_FOUND", body.get("message"));
    }

    @Test
    void handleResponseStatus_preservesStatusCode() {
        var ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");

        var response = handler.handleResponseStatus(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(404, body.get("status"));
        assertEquals("Resource not found", body.get("message"));
    }

    @Test
    void handleResponseStatus_withNullReason_usesDefaultPhrase() {
        var ex = new ResponseStatusException(HttpStatus.BAD_REQUEST);

        var response = handler.handleResponseStatus(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("Bad Request", body.get("message"));
    }

    @Test
    void handleGenericException_returns500() {
        var ex = new RuntimeException("unexpected error");

        var response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.get("status"));
        assertEquals("Internal server error", body.get("message"));
    }

    @Test
    void handleHttpMessageNotReadable_withCause_returns400() {
        var cause = new IllegalArgumentException("Cannot deserialize");
        var ex = new HttpMessageNotReadableException("Bad request", cause, null);

        var response = handler.handleHttpMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
        assertEquals("Cannot deserialize", body.get("message"));
    }

    @Test
    void handleHttpMessageNotReadable_withoutCause_returns400() {
        var ex = new HttpMessageNotReadableException("Bad request", null, null);

        var response = handler.handleHttpMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
    }
}
