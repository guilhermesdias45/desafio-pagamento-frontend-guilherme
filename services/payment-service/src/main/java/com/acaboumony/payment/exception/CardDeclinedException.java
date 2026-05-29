package com.acaboumony.payment.exception;

public class CardDeclinedException extends RuntimeException {
    public CardDeclinedException(String message) {
        super(message);
    }
}
