package com.acaboumony.user.event;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.event.payload.UserLoginBlockedEvent;
import com.acaboumony.user.event.payload.UserLoginSuccessEvent;
import com.acaboumony.user.event.payload.UserRegisteredEvent;
import com.acaboumony.user.event.payload.UserTwoFactorEnabledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes domain events to the {@code user-events} Kafka topic.
 *
 * <p>All events use {@code userId.toString()} as the Kafka message key — this ensures
 * events for the same user are ordered within a partition.</p>
 *
 * <p>The {@link #publish(UserEvent)} method is non-blocking: it returns after scheduling
 * the send and logs any asynchronous errors via {@code .whenComplete(...)}.</p>
 */
@Component
public class UserEventProducer {

    private static final Logger log = LoggerFactory.getLogger(UserEventProducer.class);
    static final String TOPIC = "user-events";

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    public UserEventProducer(KafkaTemplate<String, UserEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes any {@link UserEvent} to the {@code user-events} topic.
     * Non-blocking — errors are logged asynchronously.
     */
    public void publish(UserEvent event) {
        kafkaTemplate.send(TOPIC, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for userId={}: {}",
                                event.eventType(), event.userId(), ex.getMessage());
                    } else {
                        log.debug("Published {} for userId={}", event.eventType(), event.userId());
                    }
                });
    }

    public void publishUserRegistered(UUID userId, String email, UserRole role, UUID merchantId) {
        publish(new UserRegisteredEvent(userId, email, role, merchantId, Instant.now()));
    }

    public void publishLoginSuccess(UUID userId, String email, String deviceFingerprint) {
        publish(new UserLoginSuccessEvent(userId, email, deviceFingerprint, Instant.now()));
    }

    public void publishLoginBlocked(UUID userId, String email, Instant unlockAt) {
        // userId may be null if the email doesn't match any known user; use a sentinel UUID
        UUID safeUserId = userId != null ? userId : UUID.nameUUIDFromBytes(email.getBytes());
        publish(new UserLoginBlockedEvent(safeUserId, email, unlockAt, Instant.now()));
    }

    public void publishTwoFactorEnabled(UUID userId) {
        publish(new UserTwoFactorEnabledEvent(userId, Instant.now()));
    }
}
