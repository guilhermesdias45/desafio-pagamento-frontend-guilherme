package com.acaboumony.payment.exception;

public class TransactionNotFoundException extends PaymentServiceException {

    public TransactionNotFoundException(String transactionId) {
        super("TRANSACTION_NOT_FOUND", "Transaction not found: " + transactionId, false);
    }
}
