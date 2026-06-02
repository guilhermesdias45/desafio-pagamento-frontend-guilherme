package com.acaboumony.payment.exception;

public class DuplicateIdempotencyKeyException extends PaymentServiceException {

    public DuplicateIdempotencyKeyException() {
        super("DUPLICATE_IDEMPOTENCY_KEY", "A transaction with this idempotency key already exists", false);
    }
}
