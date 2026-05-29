package com.acaboumony.user.exception;

/**
 * Base class for all domain exceptions in the user-service.
 *
 * <p>Subclasses should provide a specific {@code errorCode} string that maps to an HTTP status
 * in {@code GlobalExceptionHandler}. The {@code retryable} flag signals to clients whether
 * retrying the operation with the same input might succeed (e.g. transient network errors).</p>
 */
public abstract class UserServiceException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    protected UserServiceException(String errorCode, String message) {
        this(errorCode, message, false);
    }

    protected UserServiceException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    protected UserServiceException(String errorCode, String message, Throwable cause) {
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
