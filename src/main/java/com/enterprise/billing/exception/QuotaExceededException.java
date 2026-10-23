package com.enterprise.billing.exception;

public class QuotaExceededException extends BillingException {
    public QuotaExceededException(String message) {
        super(message, "ERR_QUOTA_EXCEEDED");
    }
}
