package com.acaboumony.payment.exception;

public class InsufficientPermissionsException extends PaymentServiceException {

    public InsufficientPermissionsException() {
        super("INSUFFICIENT_PERMISSIONS", "You do not have permission to perform this action", false);
    }
}
