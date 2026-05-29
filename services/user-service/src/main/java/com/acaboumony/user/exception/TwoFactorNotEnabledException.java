package com.acaboumony.user.exception;

/** Thrown when a 2FA operation is attempted on a user without 2FA enabled. Maps to HTTP 422. */
public class TwoFactorNotEnabledException extends UserServiceException {
    public TwoFactorNotEnabledException() {
        super("TWO_FACTOR_NOT_ENABLED", "Two-factor authentication is not enabled");
    }
}
