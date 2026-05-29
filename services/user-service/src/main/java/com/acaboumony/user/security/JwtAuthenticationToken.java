package com.acaboumony.user.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated token set in the SecurityContext after JWT validation.
 * Principal is the user ID (UUID string); name is also the user ID.
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final JwtClaims claims;

    public JwtAuthenticationToken(JwtClaims claims) {
        super(buildAuthorities(claims));
        this.claims = claims;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return claims;
    }

    @Override
    public String getName() {
        return claims.sub().toString();
    }

    public JwtClaims getClaims() {
        return claims;
    }

    private static Collection<GrantedAuthority> buildAuthorities(JwtClaims claims) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()));
    }
}
