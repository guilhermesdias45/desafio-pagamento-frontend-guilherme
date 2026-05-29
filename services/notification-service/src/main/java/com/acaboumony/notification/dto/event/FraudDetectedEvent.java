package com.acaboumony.notification.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FraudDetectedEvent(
        String transactionId,
        UUID customerId,
        Integer score,
        String decision,
        List<String> reasons,
        Instant detectedAt
) {}
