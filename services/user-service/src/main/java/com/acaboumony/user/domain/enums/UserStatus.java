package com.acaboumony.user.domain.enums;

/**
 * Lifecycle statuses for a user account.
 * <ul>
 *   <li>{@link #PENDING_EMAIL_CONFIRMATION} – Registered but email not yet confirmed.</li>
 *   <li>{@link #ACTIVE} – Email confirmed; can log in and transact.</li>
 *   <li>{@link #LOCKED} – Temporarily locked after too many failed login attempts.</li>
 *   <li>{@link #DISABLED} – Permanently disabled by an administrator.</li>
 * </ul>
 * Values MUST match the PostgreSQL {@code user_status} enum defined in V1__create_users.sql.
 */
public enum UserStatus {
    PENDING_EMAIL_CONFIRMATION,
    ACTIVE,
    LOCKED,
    DISABLED
}
