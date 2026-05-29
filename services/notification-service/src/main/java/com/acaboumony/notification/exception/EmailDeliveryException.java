package com.acaboumony.notification.exception;

public class EmailDeliveryException extends RuntimeException {
    private final String recipient;
    private final boolean permanent;

    public EmailDeliveryException(String recipient, String message, boolean permanent) {
        super(message);
        this.recipient = recipient;
        this.permanent = permanent;
    }

    public String getRecipient() { return recipient; }
    public boolean isPermanent() { return permanent; }
}
