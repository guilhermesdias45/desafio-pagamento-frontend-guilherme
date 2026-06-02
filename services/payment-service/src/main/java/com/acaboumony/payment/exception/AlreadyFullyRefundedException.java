package com.acaboumony.payment.exception;

public class AlreadyFullyRefundedException extends PaymentServiceException {

    public AlreadyFullyRefundedException() {
        super("ALREADY_FULLY_REFUNDED", "This transaction has already been fully refunded", false);
    }
}
