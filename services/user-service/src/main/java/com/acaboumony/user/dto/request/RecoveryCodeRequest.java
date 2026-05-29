package com.acaboumony.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/2fa/recovery}. */
public record RecoveryCodeRequest(
        @NotBlank String twoFactorToken,
        @NotBlank String code
) {}
