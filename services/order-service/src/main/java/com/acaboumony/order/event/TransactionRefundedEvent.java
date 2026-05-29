package com.acaboumony.order.event;

import java.util.UUID;

public record TransactionRefundedEvent(
        String refundId,
        String transactionId,
        UUID orderId,
        Long amountRefundedInCents,
        Boolean isFullRefund,
        String reason
) {}
