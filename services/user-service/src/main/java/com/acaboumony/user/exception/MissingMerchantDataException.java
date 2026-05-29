package com.acaboumony.user.exception;

/** Thrown when a MERCHANT_OWNER registration is missing companyName or cnpj. Maps to HTTP 400. */
public class MissingMerchantDataException extends UserServiceException {
    public MissingMerchantDataException() {
        super("MISSING_MERCHANT_DATA", "MERCHANT_OWNER registration requires companyName and cnpj");
    }
}
