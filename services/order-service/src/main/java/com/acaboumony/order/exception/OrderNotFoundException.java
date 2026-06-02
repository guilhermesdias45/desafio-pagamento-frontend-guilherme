package com.acaboumony.order.exception;

/**
 * Thrown when an order cannot be found by the given identifier.
 */
public class OrderNotFoundException extends OrderServiceException {

    public OrderNotFoundException(String orderId) {
        super("ORDER_NOT_FOUND", "Order not found: " + orderId);
    }
}
