package com.acaboumony.payment.exception;

public class MercadoPagoTimeoutException extends PaymentServiceException {

    public MercadoPagoTimeoutException() {
        super("MP_GATEWAY_TIMEOUT", "Mercado Pago gateway timeout. Please try again.", true);
    }
}
