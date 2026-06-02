package com.acaboumony.payment.exception;

public class RefundWindowExpiredException extends PaymentServiceException {

    public RefundWindowExpiredException() {
        super("REFUND_WINDOW_EXPIRED", "The 90-day refund window has expired for this transaction", false);
    }
}
