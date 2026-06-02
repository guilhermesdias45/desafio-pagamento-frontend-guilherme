package com.acaboumony.order.domain.enums;

/**
 * Lifecycle states of an order in the Acabou o Mony payment platform.
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    PAID,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
