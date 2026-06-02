package com.acaboumony.payment.client.mp;

public interface MercadoPagoGateway {

    MpPaymentResult processPayment(MpPaymentRequest request);

    MpRefundResult refundPayment(long mpPaymentId, Long amountInCents);
}
