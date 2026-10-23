package com.enterprise.billing.exception;

public class DuplicateResourceException extends BillingException {
    public DuplicateResourceException(String message) {
        super(message, "ERR_DUPLICATE_RESOURCE");
    }
}
