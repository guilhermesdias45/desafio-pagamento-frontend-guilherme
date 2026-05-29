package com.acaboumony.user.controller;

import com.acaboumony.user.exception.AccountLockedException;
import com.acaboumony.user.exception.UserServiceException;
import com.acaboumony.user.security.JwtValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserServiceException.class)
    public ResponseEntity<ProblemDetail> handleDomain(UserServiceException ex, HttpServletRequest req) {
        HttpStatus status = mapStatus(ex.getErrorCode());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("retryable", ex.isRetryable());
        if (ex instanceof AccountLockedException locked) {
            pd.setProperty("unlockAt", locked.getUnlockAt());
        }
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(status)
                .header("Content-Type", "application/problem+json")
                .body(pd);
    }

    @ExceptionHandler(JwtValidationException.class)
    public ResponseEntity<ProblemDetail> handleJwt(JwtValidationException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("retryable", false);
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
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
            case "EMAIL_ALREADY_EXISTS", "CNPJ_ALREADY_REGISTERED", "TWO_FACTOR_ALREADY_ENABLED" -> HttpStatus.CONFLICT;
            case "WEAK_PASSWORD", "INVALID_EMAIL_FORMAT", "INVALID_ROLE", "INVALID_CNPJ",
                 "MISSING_MERCHANT_DATA", "VALIDATION_FAILED" -> HttpStatus.BAD_REQUEST;
            case "INVALID_CREDENTIALS", "INVALID_TOTP_CODE", "REFRESH_TOKEN_INVALID",
                 "REFRESH_TOKEN_EXPIRED", "RECOVERY_CODE_INVALID", "INVALID_SIGNATURE",
                 "MALFORMED_TOKEN", "INVALID_TOKEN", "MISSING_CLAIMS", "INVALID_CLAIMS" -> HttpStatus.UNAUTHORIZED;
            case "ACCOUNT_LOCKED" -> HttpStatus.LOCKED;
            case "ACCOUNT_NOT_CONFIRMED", "ACCOUNT_DISABLED" -> HttpStatus.FORBIDDEN;
            case "TWO_FACTOR_NOT_ENABLED", "RECOVERY_CODE_EXHAUSTED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "TOO_MANY_REQUESTS" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
