package com.acaboumony.user.exception;

/** Thrown when a refresh token is invalid, expired, or already used. Maps to HTTP 401. */
public class RefreshTokenInvalidException extends UserServiceException {
    public RefreshTokenInvalidException() {
        super("REFRESH_TOKEN_INVALID", "Refresh token is invalid or has already been used");
    }
}
