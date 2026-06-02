package com.acaboumony.fraud.security;

import com.acaboumony.fraud.config.InternalSecretProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Servlet filter that enforces machine-to-machine authentication for {@code /internal/**} routes.
 *
 * <p>Any request whose URI starts with {@code /internal/} must include the header
 * {@code X-Internal-Secret} with the exact value of {@code internal.secret}. The comparison
 * uses {@link MessageDigest#isEqual(byte[], byte[])} to prevent timing oracle attacks.</p>
 *
 * <p>Requests to other paths bypass this filter entirely.</p>
 */
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalSecretFilter.class);

    /** Header name carrying the shared machine-to-machine secret. */
    static final String HEADER = "X-Internal-Secret";

    private final InternalSecretProperties internalSecretProperties;

    public InternalSecretFilter(InternalSecretProperties internalSecretProperties) {
        this.internalSecretProperties = internalSecretProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        byte[] expected = internalSecretProperties.secret().getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided != null
                ? provided.getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        // Constant-time comparison to prevent timing oracle on secret value
        if (!MessageDigest.isEqual(expected, providedBytes)) {
            log.warn("Rejected /internal request: invalid or missing X-Internal-Secret");
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Forbidden","status":403,\
                    "detail":"Invalid or missing X-Internal-Secret"}""");
            return;
        }

        chain.doFilter(request, response);
    }
}
