package com.acaboumony.order.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionFailedEvent(
        String transactionId,
        UUID orderId,
        String status,
        String reason
) {}
