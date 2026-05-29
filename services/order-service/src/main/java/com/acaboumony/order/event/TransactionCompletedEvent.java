package com.acaboumony.order.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCompletedEvent(
        String transactionId,
        UUID orderId,
        String status
) {}
