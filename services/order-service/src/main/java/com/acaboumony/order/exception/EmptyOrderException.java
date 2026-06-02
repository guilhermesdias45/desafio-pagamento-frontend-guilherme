package com.acaboumony.order.exception;

/**
 * Thrown when a create order request contains no items.
 */
public class EmptyOrderException extends OrderServiceException {

    public EmptyOrderException() {
        super("EMPTY_ORDER", "Order must contain at least one item");
    }
}
