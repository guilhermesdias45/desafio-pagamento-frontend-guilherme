package com.acaboumony.order.controller;

import com.acaboumony.order.exception.OrderServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler that converts domain exceptions to RFC 7807 ProblemDetail responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderServiceException.class)
    public ResponseEntity<ProblemDetail> handleDomain(OrderServiceException ex, HttpServletRequest req) {
        HttpStatus status = mapStatus(ex.getErrorCode());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("retryable", ex.isRetryable());
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(status)
                .header("Content-Type", "application/problem+json")
                .body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest req) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .map(err -> {
                    if (err instanceof FieldError fe) {
                        return Map.of("field", fe.getField(),
                                "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
                    }
                    return Map.of("object", err.getObjectName(),
                            "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "invalid");
                })
                .toList();

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setType(URI.create("about:blank"));
        pd.setProperty("errorCode", "VALIDATION_FAILED");
        pd.setProperty("fieldErrors", fieldErrors);
        pd.setProperty("retryable", false);
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/problem+json")
                .body(pd);
    }

    private HttpStatus mapStatus(String errorCode) {
        return switch (errorCode) {
            case "DUPLICATE_ORDER" -> HttpStatus.CONFLICT;
            case "ORDER_CANNOT_BE_CANCELLED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "INSUFFICIENT_PERMISSIONS" -> HttpStatus.FORBIDDEN;
            case "ORDER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "EMPTY_ORDER",
                 "INVALID_ITEM_PRICE",
                 "INVALID_QUANTITY",
                 "TOTAL_EXCEEDS_LIMIT" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
