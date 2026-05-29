package com.acaboumony.user.security;

import com.acaboumony.user.config.JwtProperties;
import com.acaboumony.user.domain.enums.UserRole;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Generates RS256-signed JWT access tokens.
 *
 * <p>Claims included: {@code sub} (userId), {@code email}, {@code role},
 * {@code merchantId} (nullable), {@code iat}, {@code exp}.</p>
 *
 * <p>The RSA private key is parsed from {@link JwtProperties} once at startup
 * ({@code @PostConstruct}) to avoid repeated PEM decoding on every token generation.</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties jwtProperties;
    private PrivateKey privateKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void init() {
        this.privateKey = RsaKeyLoader.loadPrivateKey(jwtProperties.privateKey());
        log.debug("JwtTokenProvider: RSA private key loaded successfully");
    }

    /**
     * Generates a signed RS256 JWT access token.
     *
     * @param userId     user UUID (becomes JWT {@code sub})
     * @param email      user email
     * @param role       user role
     * @param merchantId merchant UUID, or {@code null} for CUSTOMER/STAFF roles
     * @return compact JWT string
     */
    public String generateAccessToken(UUID userId, String email, UserRole role, UUID merchantId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.accessTokenExpirationSeconds());

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .claim("merchantId", merchantId != null ? merchantId.toString() : null)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        log.debug("Generated access token for userId={}", userId);
        return token;
    }
}
