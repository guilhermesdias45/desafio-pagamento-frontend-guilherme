package com.acaboumony.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** Returns RFC 7807 Problem Details JSON for forbidden access. */
@Component
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailsAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "type", "about:blank",
                "title", "Forbidden",
                "status", 403,
                "detail", "Access denied",
                "errorCode", "FORBIDDEN",
                "retryable", false,
                "instance", request.getRequestURI()
        ));
    }
}
