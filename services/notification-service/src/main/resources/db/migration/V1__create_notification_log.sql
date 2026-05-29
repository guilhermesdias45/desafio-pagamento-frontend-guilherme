CREATE SCHEMA IF NOT EXISTS notification_service;

SET search_path TO notification_service;

CREATE TABLE notification_log (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    correlation_id  VARCHAR(100),
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_notification_log PRIMARY KEY (id)
);

CREATE INDEX idx_notification_log_event_type ON notification_log (event_type);
CREATE INDEX idx_notification_log_recipient  ON notification_log (recipient_email);
CREATE INDEX idx_notification_log_status     ON notification_log (status);
CREATE INDEX idx_notification_log_correlation ON notification_log (correlation_id);
