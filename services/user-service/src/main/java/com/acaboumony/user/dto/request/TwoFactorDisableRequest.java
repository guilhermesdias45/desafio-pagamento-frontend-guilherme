package com.acaboumony.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/auth/2fa/disable}. */
public record TwoFactorDisableRequest(
        @NotBlank String password,
        @NotBlank @Size(min = 6, max = 6) String code
) {}
