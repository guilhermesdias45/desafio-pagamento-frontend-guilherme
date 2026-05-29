package com.acaboumony.user.exception;

/** Thrown when a MERCHANT_OWNER registration uses a CNPJ that is already registered. Maps to HTTP 409. */
public class CnpjAlreadyRegisteredException extends UserServiceException {
    public CnpjAlreadyRegisteredException() {
        super("CNPJ_ALREADY_REGISTERED", "This CNPJ is already registered to another merchant");
    }
}
