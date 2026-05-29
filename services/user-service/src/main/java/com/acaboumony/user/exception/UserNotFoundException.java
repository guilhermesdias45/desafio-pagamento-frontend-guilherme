package com.acaboumony.user.exception;

/** Thrown when a requested user ID does not exist. Maps to HTTP 404. */
public class UserNotFoundException extends UserServiceException {
    public UserNotFoundException() {
        super("USER_NOT_FOUND", "User not found");
    }
}
