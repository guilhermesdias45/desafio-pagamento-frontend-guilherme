package com.acaboumony.user.exception;

/** Thrown when login credentials are invalid (generic — does not reveal which field failed). Maps to HTTP 401. */
public class InvalidCredentialsException extends UserServiceException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password");
    }
}
