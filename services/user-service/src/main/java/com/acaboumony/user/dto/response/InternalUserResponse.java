package com.acaboumony.user.dto.response;

import java.util.UUID;

public record InternalUserResponse(
        UUID id,
        String email,
        String role
) {}
