package com.enterprise.billing.config;

import com.enterprise.billing.dto.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * AuthenticationEntryPoint that produces RFC 7807 Problem Details JSON on 401 Unauthorized errors.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        String detail = authException != null && authException.getMessage() != null
                ? authException.getMessage()
                : "Full authentication is required to access this resource.";

        ProblemDetails problem = ProblemDetails.of(
                "https://api.billing.enterprise.com/errors/unauthorized",
                "Unauthorized",
                HttpServletResponse.SC_UNAUTHORIZED,
                detail,
                request.getRequestURI(),
                "ERR_UNAUTHORIZED"
        );

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
