package com.acaboumony.user.exception;

/** Thrown when 2FA setup is attempted on a user who already has 2FA enabled. Maps to HTTP 409. */
public class TwoFactorAlreadyEnabledException extends UserServiceException {
    public TwoFactorAlreadyEnabledException() {
        super("TWO_FACTOR_ALREADY_ENABLED", "Two-factor authentication is already enabled");
    }
}
