package com.acaboumony.fraud.exception;

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
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FraudServiceException.class)
    public ResponseEntity<ProblemDetail> handleDomain(FraudServiceException ex,
                                                       HttpServletRequest req) {
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

        String errorCode = resolveValidationErrorCode(ex.getBindingResult().getAllErrors());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setType(URI.create("about:blank"));
        pd.setProperty("errorCode", errorCode);
        pd.setProperty("fieldErrors", fieldErrors);
        pd.setProperty("retryable", false);
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/problem+json")
                .body(pd);
    }

    private String resolveValidationErrorCode(List<ObjectError> errors) {
        List<String> distinct = errors.stream()
                .map(ObjectError::getDefaultMessage)
                .filter(m -> m != null && m.matches("[A-Z][A-Z0-9_]+"))
                .distinct()
                .toList();
        return distinct.size() == 1 ? distinct.get(0) : "VALIDATION_FAILED";
    }

    private HttpStatus mapStatus(String errorCode) {
        return switch (errorCode) {
            case "FRAUD_ANALYSIS_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            case "VALIDATION_FAILED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
