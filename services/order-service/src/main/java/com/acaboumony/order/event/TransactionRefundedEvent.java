package com.acaboumony.order.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionRefundedEvent(
        String refundId,
        String transactionId,
        UUID orderId,
        Long amountRefundedInCents,
        Boolean isFullRefund,
        String reason
) {}
