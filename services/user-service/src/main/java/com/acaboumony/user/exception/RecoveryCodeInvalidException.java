package com.acaboumony.user.exception;

/** Thrown when a recovery code does not match any stored hash. Maps to HTTP 401. */
public class RecoveryCodeInvalidException extends UserServiceException {
    public RecoveryCodeInvalidException() {
        super("RECOVERY_CODE_INVALID", "Recovery code is invalid or has already been used");
    }
}
