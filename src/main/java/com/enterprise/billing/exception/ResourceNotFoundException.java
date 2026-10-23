package com.enterprise.billing.exception;

public class ResourceNotFoundException extends BillingException {
    public ResourceNotFoundException(String message) {
        super(message, "ERR_NOT_FOUND");
    }
}
