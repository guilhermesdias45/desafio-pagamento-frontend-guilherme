package com.acaboumony.payment.exception;

public class RateLimitExceededException extends PaymentServiceException {

    public RateLimitExceededException() {
        super("RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Please try again later.", true);
    }
}
