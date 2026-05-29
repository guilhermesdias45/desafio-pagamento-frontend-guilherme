package com.acaboumony.user.security;

/**
 * Thrown when a JWT fails validation (expired, invalid signature, malformed, or missing claims).
 */
public class JwtValidationException extends RuntimeException {

    private final String errorCode;

    public JwtValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public JwtValidationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
