package com.acaboumony.payment.domain.enums;

public enum TransactionStatus {
    APPROVED,
    DECLINED,
    SUSPECTED_FRAUD,
    FULLY_REFUNDED,
    PARTIALLY_REFUNDED,
    PROCESSING,
    CANCELLED
}
