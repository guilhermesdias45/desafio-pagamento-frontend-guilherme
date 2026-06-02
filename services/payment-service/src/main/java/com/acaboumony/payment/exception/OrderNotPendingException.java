package com.acaboumony.payment.exception;

public class OrderNotPendingException extends PaymentServiceException {

    public OrderNotPendingException(String status) {
        super("ORDER_NOT_PENDING", "Order is not in PENDING state, current status: " + status, false);
    }
}
