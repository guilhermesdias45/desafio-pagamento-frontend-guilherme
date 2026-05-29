package com.acaboumony.user.exception;

/** Thrown when a registration is attempted with an invalid role (e.g. STAFF). Maps to HTTP 400. */
public class InvalidRoleException extends UserServiceException {
    public InvalidRoleException() {
        super("INVALID_ROLE", "Registration with this role is not allowed. STAFF accounts are created via invite only");
    }
}
