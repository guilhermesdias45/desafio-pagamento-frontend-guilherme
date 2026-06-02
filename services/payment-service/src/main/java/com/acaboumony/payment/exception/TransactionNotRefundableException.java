package com.acaboumony.payment.exception;

public class TransactionNotRefundableException extends PaymentServiceException {

    public TransactionNotRefundableException(String status) {
        super("TRANSACTION_NOT_REFUNDABLE", "Transaction cannot be refunded in status: " + status, false);
    }
}
