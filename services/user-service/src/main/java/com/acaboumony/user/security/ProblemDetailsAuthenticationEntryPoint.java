package com.acaboumony.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** Returns RFC 7807 Problem Details JSON for unauthenticated access to protected routes. */
@Component
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemDetailsAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "type", "about:blank",
                "title", "Unauthorized",
                "status", 401,
                "detail", "Authentication required",
                "errorCode", "UNAUTHORIZED",
                "retryable", false,
                "instance", request.getRequestURI()
        ));
    }
}
