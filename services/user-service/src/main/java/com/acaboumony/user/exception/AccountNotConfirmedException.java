package com.acaboumony.user.exception;

/** Thrown when login is attempted on an account with PENDING_EMAIL_CONFIRMATION status. Maps to HTTP 403. */
public class AccountNotConfirmedException extends UserServiceException {
    public AccountNotConfirmedException() {
        super("ACCOUNT_NOT_CONFIRMED", "Email address has not been confirmed");
    }
}
