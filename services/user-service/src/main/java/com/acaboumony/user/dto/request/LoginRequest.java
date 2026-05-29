package com.acaboumony.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * @param email             user's email address
 * @param password          plaintext password (compared against BCrypt hash — never stored)
 * @param totpCode          optional 6-digit TOTP code (required when 2FA is enabled)
 * @param deviceFingerprint optional client device identifier for audit logging
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String totpCode,
        String deviceFingerprint
) {
    /** Exclude password and totpCode from toString() to prevent log leakage. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", deviceFingerprint=" + deviceFingerprint + "]";
    }
}
