package com.acaboumony.user.exception;

/** Thrown when a CNPJ fails the Módulo 11 validation algorithm. Maps to HTTP 400. */
public class InvalidCnpjException extends UserServiceException {
    public InvalidCnpjException() {
        super("INVALID_CNPJ", "The provided CNPJ is invalid");
    }
}
