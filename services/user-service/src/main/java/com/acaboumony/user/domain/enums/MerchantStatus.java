package com.acaboumony.user.domain.enums;

/**
 * Operational statuses for a merchant account.
 * <ul>
 *   <li>{@link #ACTIVE} – Merchant is operating normally.</li>
 *   <li>{@link #SUSPENDED} – Temporarily suspended (e.g. compliance review).</li>
 *   <li>{@link #INACTIVE} – No longer active.</li>
 * </ul>
 * Values MUST match the PostgreSQL {@code merchant_status} enum defined in V2__create_merchants.sql.
 */
public enum MerchantStatus {
    ACTIVE,
    SUSPENDED,
    INACTIVE
}
