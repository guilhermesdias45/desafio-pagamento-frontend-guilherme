package com.acaboumony.user.domain.enums;

/**
 * Roles available in the Acabou o Mony platform.
 * <ul>
 *   <li>{@link #CUSTOMER} – End-user who purchases products in live-commerce streams.</li>
 *   <li>{@link #MERCHANT_OWNER} – Entrepreneur who sells via live-commerce (e.g. "Ana"). Has an
 *       associated {@code Merchant} entity.</li>
 *   <li>{@link #STAFF} – Operator of a merchant account; cannot purchase; created via invite
 *       (Sprint 2).</li>
 * </ul>
 * Values MUST match the PostgreSQL {@code user_role} enum defined in V1__create_users.sql.
 */
public enum UserRole {
    CUSTOMER,
    MERCHANT_OWNER,
    STAFF;

    /**
     * Returns {@code true} if this role is allowed to place purchase orders.
     * Only {@link #STAFF} cannot purchase directly.
     */
    public boolean canPurchase() {
        return this != STAFF;
    }
}
