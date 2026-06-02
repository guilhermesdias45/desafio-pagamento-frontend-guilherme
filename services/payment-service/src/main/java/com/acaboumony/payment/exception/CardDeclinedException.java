package com.acaboumony.payment.exception;

public class CardDeclinedException extends PaymentServiceException {

    private final String statusDetail;

    public CardDeclinedException(String statusDetail) {
        super("CARD_DECLINED", "Card was declined: " + statusDetail, true);
        this.statusDetail = statusDetail;
    }

    public String getStatusDetail() {
        return statusDetail;
    }
}
