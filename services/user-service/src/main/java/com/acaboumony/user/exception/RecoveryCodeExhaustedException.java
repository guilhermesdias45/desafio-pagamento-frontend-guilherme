package com.acaboumony.user.exception;

/** Thrown when all 8 recovery codes have been used. Maps to HTTP 422. CE-2FA-001. */
public class RecoveryCodeExhaustedException extends UserServiceException {
    public RecoveryCodeExhaustedException() {
        super("RECOVERY_CODE_EXHAUSTED", "All recovery codes have been used. Please disable and re-enable 2FA to generate new codes");
    }
}
