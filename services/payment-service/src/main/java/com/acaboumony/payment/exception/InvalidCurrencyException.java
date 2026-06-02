package com.acaboumony.payment.exception;

public class InvalidCurrencyException extends PaymentServiceException {

    public InvalidCurrencyException(String currency) {
        super("INVALID_CURRENCY", "Unsupported currency: " + currency + ". Only BRL is accepted.", false);
    }
}
