package com.enterprise.billing.config;

import com.enterprise.billing.dto.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * AccessDeniedHandler that produces RFC 7807 Problem Details JSON on 403 Forbidden errors.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        String detail = accessDeniedException != null && accessDeniedException.getMessage() != null
                ? accessDeniedException.getMessage()
                : "Access denied: insufficient permissions to perform this operation.";

        ProblemDetails problem = ProblemDetails.of(
                "https://api.billing.enterprise.com/errors/forbidden",
                "Forbidden",
                HttpServletResponse.SC_FORBIDDEN,
                detail,
                request.getRequestURI(),
                "ERR_FORBIDDEN"
        );

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
