package com.enterprise.billing.exception;

/**
 * Thrown when a JWT token is malformed, expired, invalidly signed, or missing essential claims.
 */
public class InvalidTokenException extends BillingException {

    public InvalidTokenException(String message) {
        super(message, "ERR_INVALID_TOKEN");
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, "ERR_INVALID_TOKEN", cause);
    }
}
