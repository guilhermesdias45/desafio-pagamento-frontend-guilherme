package com.acaboumony.payment.controller;

import com.acaboumony.payment.exception.PaymentServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler following RFC 7807 ProblemDetail format.
 * Maps domain error codes to HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentServiceException.class)
    public ResponseEntity<ProblemDetail> handleDomain(PaymentServiceException ex, HttpServletRequest req) {
        HttpStatus status = mapStatus(ex.getErrorCode());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("retryable", ex.isRetryable());
        pd.setInstance(URI.create(req.getRequestURI()));

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header("Content-Type", "application/problem+json");

        if ("RATE_LIMIT_EXCEEDED".equals(ex.getErrorCode())) {
            builder = builder.header("Retry-After", "60");
        }

        return builder.body(pd);
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
            case "DUPLICATE_IDEMPOTENCY_KEY" -> HttpStatus.CONFLICT;
            case "ORDER_NOT_FOUND", "TRANSACTION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ORDER_NOT_PENDING", "SUSPECTED_FRAUD", "CARD_DECLINED", "INSUFFICIENT_FUNDS",
                 "TRANSACTION_NOT_REFUNDABLE", "AMOUNT_EXCEEDS_ORIGINAL",
                 "ALREADY_FULLY_REFUNDED", "REFUND_WINDOW_EXPIRED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "INSUFFICIENT_PERMISSIONS" -> HttpStatus.FORBIDDEN;
            case "RATE_LIMIT_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "MP_GATEWAY_TIMEOUT" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "INVALID_CURRENCY", "VALIDATION_FAILED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
