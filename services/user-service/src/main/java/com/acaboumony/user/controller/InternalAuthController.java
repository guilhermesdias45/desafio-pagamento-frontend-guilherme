package com.acaboumony.user.controller;

import com.acaboumony.user.dto.response.ValidateTokenResponse;
import com.acaboumony.user.security.JwtClaims;
import com.acaboumony.user.security.JwtTokenValidator;
import com.acaboumony.user.security.JwtValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint used by the api-gateway to validate JWT access tokens.
 *
 * <p>Route: {@code POST /internal/auth/validate-token}</p>
 *
 * <p>This endpoint is protected by {@link com.acaboumony.user.security.InternalSecretFilter}
 * (the {@code X-Internal-Secret} header must be present and valid). The JWT is passed via the
 * standard {@code Authorization: Bearer <token>} header.</p>
 *
 * <p>P99 target: &lt; 50 ms (RSA public key is cached after first {@code @PostConstruct} load).</p>
 */
@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthController.class);

    private final JwtTokenValidator jwtTokenValidator;

    public InternalAuthController(JwtTokenValidator jwtTokenValidator) {
        this.jwtTokenValidator = jwtTokenValidator;
    }

    /**
     * Validates a JWT and returns its principal claims.
     *
     * @param authHeader value of the {@code Authorization} header (must start with {@code "Bearer "})
     * @return 200 with {@link ValidateTokenResponse}, or propagates {@link JwtValidationException}
     *         (handled by {@code GlobalExceptionHandler} → 401)
     */
    @PostMapping("/validate-token")
    public ResponseEntity<ValidateTokenResponse> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "");
        JwtClaims claims = jwtTokenValidator.validate(token); // throws JwtValidationException on failure
        log.debug("Token validated: userId={}", claims.sub());
        return ResponseEntity.ok(new ValidateTokenResponse(
                claims.sub(),
                claims.email(),
                claims.role().name(),
                claims.merchantId()));
    }
}
