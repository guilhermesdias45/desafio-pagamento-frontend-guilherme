package com.acaboumony.payment.exception;

public class InsufficientFundsException extends PaymentServiceException {

    public InsufficientFundsException() {
        super("INSUFFICIENT_FUNDS", "Insufficient funds to complete the transaction", true);
    }
}
