package com.acaboumony.payment.exception;

public class OrderNotFoundException extends PaymentServiceException {

    public OrderNotFoundException(String orderId) {
        super("ORDER_NOT_FOUND", "Order not found: " + orderId, false);
    }
}
