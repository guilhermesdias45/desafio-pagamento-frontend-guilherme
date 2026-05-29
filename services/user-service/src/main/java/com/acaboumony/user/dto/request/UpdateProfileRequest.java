package com.acaboumony.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /api/v1/users/me}. Sprint 1: only fullName is updatable. */
public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName
) {}
