package com.acaboumony.fraud.exception;

/**
 * Thrown when fraud analysis cannot be completed due to an internal error
 * (e.g. Redis/Kafka unavailability that should be retried).
 */
public class FraudAnalysisException extends FraudServiceException {

    public FraudAnalysisException(String message) {
        super("FRAUD_ANALYSIS_ERROR", message, true);
    }

    public FraudAnalysisException(String message, Throwable cause) {
        super("FRAUD_ANALYSIS_ERROR", message, cause);
    }
}
