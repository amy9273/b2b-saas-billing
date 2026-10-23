package com.enterprise.billing.exception;

import com.enterprise.billing.dto.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler converting application exceptions into RFC 7807 Problem Details responses
 * with MediaType.APPLICATION_PROBLEM_JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetails> handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        log.warn("Invalid JWT encountered at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ProblemDetails problem = ProblemDetails.of(
                "https://api.billing.enterprise.com/errors/unauthorized",
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getErrorCode()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetails> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failure at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ProblemDetails problem = ProblemDetails.of(
                "https://api.billing.enterprise.com/errors/unauthorized",
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "ERR_UNAUTHORIZED"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetails> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ProblemDetails problem = ProblemDetails.of(
                "https://api.billing.enterprise.com/errors/forbidden",
                "Forbidden",
                HttpStatus.FORBIDDEN.value(),
                "Access denied: insufficient permissions to perform this operation.",
                request.getRequestURI(),
                "ERR_FORBIDDEN"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetails problem = ProblemDetails.of(
                "https://api.billing.enterprise.com/errors/validation-failed",
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed for one or more fields.",
                request.getRequestURI(),
                "ERR_VALIDATION_FAILED",
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at [{}]:", request.getRequestURI(), ex);
        ProblemDetails problem = ProblemDetails.of(
                "https://api.billing.enterprise.com/errors/internal-server-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred. Please contact support.",
                request.getRequestURI(),
                "ERR_INTERNAL_SERVER"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
