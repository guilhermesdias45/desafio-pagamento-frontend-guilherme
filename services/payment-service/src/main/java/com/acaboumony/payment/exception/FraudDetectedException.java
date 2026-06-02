package com.acaboumony.payment.exception;

public class FraudDetectedException extends PaymentServiceException {

    public FraudDetectedException() {
        super("SUSPECTED_FRAUD", "Transaction blocked due to suspected fraud", false);
    }
}
