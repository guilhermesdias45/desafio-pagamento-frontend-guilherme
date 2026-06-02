package com.acaboumony.payment.exception;

public class AmountExceedsOriginalException extends PaymentServiceException {

    public AmountExceedsOriginalException() {
        super("AMOUNT_EXCEEDS_ORIGINAL", "Refund amount exceeds the remaining refundable amount", false);
    }
}
