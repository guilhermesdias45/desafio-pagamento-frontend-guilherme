package com.acaboumony.order.exception;

/**
 * Base class for all typed domain exceptions in the order-service.
 *
 * <p>Subclasses must always provide a specific {@code errorCode} and never throw
 * a generic {@link RuntimeException} from service code.</p>
 */
public abstract class OrderServiceException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    protected OrderServiceException(String errorCode, String message) {
        this(errorCode, message, false);
    }

    protected OrderServiceException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
