package com.acaboumony.payment.client.mp;

public sealed interface MpPaymentResult
        permits MpPaymentResult.Approved, MpPaymentResult.Rejected, MpPaymentResult.Timeout {

    record Approved(
            long mpPaymentId,
            String statusDetail,
            String cardBrand,
            String cardLastFour
    ) implements MpPaymentResult {}

    record Rejected(String statusDetail) implements MpPaymentResult {}

    record Timeout() implements MpPaymentResult {}
}
