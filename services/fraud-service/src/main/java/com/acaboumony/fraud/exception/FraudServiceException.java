package com.acaboumony.fraud.exception;

/**
 * Base class for all domain exceptions in the fraud-service.
 *
 * <p>Subclasses must provide a specific {@code errorCode} string that maps to an HTTP status
 * in {@link GlobalExceptionHandler}. The {@code retryable} flag signals to clients whether
 * retrying the same request might succeed (e.g. transient failures).</p>
 */
public abstract class FraudServiceException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    protected FraudServiceException(String errorCode, String message) {
        this(errorCode, message, false);
    }

    protected FraudServiceException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    protected FraudServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = false;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
