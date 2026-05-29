package com.acaboumony.user.exception;

import java.time.Instant;

/** Thrown when an account is temporarily locked. Maps to HTTP 423. */
public class AccountLockedException extends UserServiceException {

    private final Instant unlockAt;

    public AccountLockedException(Instant unlockAt) {
        super("ACCOUNT_LOCKED", "Account temporarily locked due to too many failed attempts");
        this.unlockAt = unlockAt;
    }

    public Instant getUnlockAt() {
        return unlockAt;
    }
}
