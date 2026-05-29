package com.acaboumony.user.exception;

/** Thrown when a registration is attempted with an email that already exists. Maps to HTTP 409. */
public class EmailAlreadyExistsException extends UserServiceException {
    public EmailAlreadyExistsException() {
        super("EMAIL_ALREADY_EXISTS", "An account with this email already exists");
    }
}
