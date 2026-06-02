package com.acaboumony.order.exception;

/**
 * Thrown when an order item has an invalid quantity (zero or negative).
 */
public class InvalidQuantityException extends OrderServiceException {

    public InvalidQuantityException() {
        super("INVALID_QUANTITY", "Item quantity must be at least 1");
    }
}
