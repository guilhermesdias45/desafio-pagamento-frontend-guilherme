package com.acaboumony.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/confirm-email}. */
public record ConfirmEmailRequest(@NotBlank String token) {}
