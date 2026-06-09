package com.acaboumony.payment.dto.response;

import com.acaboumony.payment.domain.enums.MpAccountType;

import java.time.Instant;
import java.util.UUID;

public record MpTestAccountResponse(
    UUID id,
    MpAccountType type,
    Long mpUserId,
    String email,
    Instant createdAt
) {}
