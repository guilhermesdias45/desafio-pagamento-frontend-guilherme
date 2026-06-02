package com.acaboumony.order.exception;

/**
 * Thrown when the calculated order total exceeds the maximum allowed value (999999 cents).
 */
public class TotalExceedsLimitException extends OrderServiceException {

    public TotalExceedsLimitException() {
        super("TOTAL_EXCEEDS_LIMIT", "Order total exceeds the maximum allowed limit of R$ 9,999.99");
    }
}
