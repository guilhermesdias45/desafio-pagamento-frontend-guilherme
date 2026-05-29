package com.acaboumony.user.exception;

/** Thrown when the email confirmation token is missing, expired, or invalid. Maps to HTTP 400. */
public class EmailConfirmTokenInvalidException extends UserServiceException {
    public EmailConfirmTokenInvalidException() {
        super("EMAIL_CONFIRM_TOKEN_INVALID", "Email confirmation token is invalid or has expired");
    }
}
