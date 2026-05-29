package com.acaboumony.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/auth/2fa/verify}. */
public record TwoFactorVerifyRequest(
        @NotBlank String twoFactorToken,
        @NotBlank @Size(min = 6, max = 6) String code
) {}
