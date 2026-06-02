package com.acaboumony.payment.exception;

/**
 * Base class for all domain exceptions in the payment-service.
 *
 * <p>Subclasses provide a specific {@code errorCode} string that maps to an HTTP status
 * in {@code GlobalExceptionHandler}. The {@code retryable} flag signals to clients whether
 * retrying the operation with the same input might succeed.</p>
 */
public abstract class PaymentServiceException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    protected PaymentServiceException(String errorCode, String message) {
        this(errorCode, message, false);
    }

    protected PaymentServiceException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    protected PaymentServiceException(String errorCode, String message, Throwable cause) {
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
