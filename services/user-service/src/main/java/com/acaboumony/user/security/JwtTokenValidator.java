package com.acaboumony.user.security;

import com.acaboumony.user.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;

/**
 * Validates RS256-signed JWT access tokens and extracts their claims.
 *
 * <p>The RSA public key is parsed from {@link JwtProperties} once at startup.</p>
 */
@Component
public class JwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);

    private final JwtProperties jwtProperties;
    private PublicKey publicKey;

    public JwtTokenValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void init() {
        this.publicKey = RsaKeyLoader.loadPublicKey(jwtProperties.publicKey());
        log.debug("JwtTokenValidator: RSA public key loaded successfully");
    }

    /**
     * Validates the token signature and expiration, then returns the parsed claims.
     *
     * @param token compact JWT string
     * @return parsed {@link JwtClaims}
     * @throws JwtValidationException if the token is expired, has invalid signature,
     *                                is malformed, or is missing mandatory claims
     */
    public JwtClaims validate(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);

            Claims payload = jws.getPayload();
            String subject = payload.getSubject();
            Date issuedAt = payload.getIssuedAt();
            Date expiration = payload.getExpiration();

            Instant iat = issuedAt != null ? issuedAt.toInstant() : Instant.now();
            Instant exp = expiration != null ? expiration.toInstant() : Instant.now();

            JwtClaims claims = JwtClaims.fromMap(payload, subject, iat, exp);
            log.debug("Token validated for userId={}", claims.sub());
            return claims;

        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
            throw new JwtValidationException("REFRESH_TOKEN_EXPIRED", "Token has expired", e);
        } catch (SignatureException e) {
            log.warn("JWT invalid signature");
            throw new JwtValidationException("INVALID_SIGNATURE", "Token signature is invalid", e);
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed");
            throw new JwtValidationException("MALFORMED_TOKEN", "Token is malformed", e);
        } catch (JwtValidationException e) {
            throw e;
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            throw new JwtValidationException("INVALID_TOKEN", "Token validation failed", e);
        }
    }
}
