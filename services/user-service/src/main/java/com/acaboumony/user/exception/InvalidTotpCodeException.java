package com.acaboumony.user.exception;

/** Thrown when a TOTP code fails validation. Maps to HTTP 401. */
public class InvalidTotpCodeException extends UserServiceException {
    public InvalidTotpCodeException() {
        super("INVALID_TOTP_CODE", "Invalid or expired 2FA code");
    }
}
