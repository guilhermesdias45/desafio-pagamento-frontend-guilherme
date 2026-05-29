package com.acaboumony.user.dto.response;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/auth/2fa/setup}.
 *
 * @param secret        base32-encoded TOTP secret (for manual entry in authenticator app)
 * @param qrCodeUrl     base64-encoded PNG QR code image
 * @param otpAuthUrl    {@code otpauth://} URI for deep-linking into authenticator apps
 * @param recoveryCodes list of 8 plaintext recovery codes — shown ONCE; store securely
 */
public record TwoFactorSetupResponse(
        String secret,
        String qrCodeUrl,
        String otpAuthUrl,
        List<String> recoveryCodes
) {}
