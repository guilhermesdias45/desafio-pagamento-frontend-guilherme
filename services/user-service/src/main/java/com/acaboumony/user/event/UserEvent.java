package com.acaboumony.user.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface for all events published to the {@code user-events} Kafka topic.
 *
 * <p>The {@code eventType()} discriminator is included in the JSON payload so consumers can
 * distinguish event types without a wrapper envelope.</p>
 */
public interface UserEvent {

    UUID userId();

    /**
     * Event type discriminator — matches Kafka event type strings used by downstream consumers.
     * Values: {@code "user.registered"}, {@code "user.login.success"},
     * {@code "user.login.blocked"}, {@code "user.2fa.enabled"}.
     */
    String eventType();

    Instant occurredAt();
}
