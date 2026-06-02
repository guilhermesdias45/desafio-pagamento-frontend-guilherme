package com.acaboumony.order.exception;

/**
 * Thrown when a create order request is rejected due to an already-processed idempotency key
 * that belongs to a different customer/merchant combination.
 */
public class DuplicateOrderException extends OrderServiceException {

    public DuplicateOrderException() {
        super("DUPLICATE_ORDER", "An order with this idempotency key already exists for a different context");
    }
}
