package com.enterprise.billing.exception;

/**
 * Base unchecked exception for all B2B SaaS Billing Engine domain errors.
 */
public abstract class BillingException extends RuntimeException {

    private final String errorCode;

    protected BillingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BillingException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
