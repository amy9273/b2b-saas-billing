package com.enterprise.billing.exception;

public class InvalidCredentialsException extends BillingException {
    public InvalidCredentialsException(String message) {
        super(message, "ERR_INVALID_CREDENTIALS");
    }
}
