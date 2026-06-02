package com.acaboumony.order.exception;

/**
 * Thrown when attempting to cancel an order that is not in a cancellable state.
 */
public class OrderCannotBeCancelledException extends OrderServiceException {

    public OrderCannotBeCancelledException(String currentStatus) {
        super("ORDER_CANNOT_BE_CANCELLED",
                "Order cannot be cancelled because it is in status: " + currentStatus);
    }
}
