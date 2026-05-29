package com.acaboumony.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Reads the {@code Authorization: Bearer <token>} header, validates the JWT via
 * {@link JwtTokenValidator}, and sets a {@link JwtAuthenticationToken} in the
 * {@link SecurityContextHolder}. Public and internal routes are skipped.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/confirm-email",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/resend-confirmation",
            "/api/v1/auth/2fa/verify",
            "/api/v1/auth/2fa/recovery",
            "/actuator/health",
            "/api-docs",
            "/swagger-ui"
    );

    private final JwtTokenValidator jwtTokenValidator;

    public JwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator) {
        this.jwtTokenValidator = jwtTokenValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // Skip internal routes (handled by InternalSecretFilter) and public routes
        if (uri.startsWith("/internal/") || isPublic(uri)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JwtClaims claims = jwtTokenValidator.validate(token);
                JwtAuthenticationToken auth = new JwtAuthenticationToken(claims);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtValidationException ignored) {
                // Let Spring Security's entry point return 401
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isPublic(String uri) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }
}
