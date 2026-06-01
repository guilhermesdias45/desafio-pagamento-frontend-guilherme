package com.acaboumony.user.security;

import com.acaboumony.user.domain.enums.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtTokenValidator jwtTokenValidator;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenValidator);
        SecurityContextHolder.clearContext();
    }

    @Test
    void deve_ignorar_rotas_publicas_sem_verificar_token() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtTokenValidator, never()).validate(any());
    }

    @Test
    void deve_ignorar_rotas_internas_sem_verificar_token() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/auth/validate-token");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtTokenValidator, never()).validate(any());
    }

    @Test
    void deve_setar_autenticacao_quando_token_valido_em_rota_protegida() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(userId, "ana@loja.com.br", UserRole.CUSTOMER, null,
                Instant.now(), Instant.now().plusSeconds(900));

        when(request.getRequestURI()).thenReturn("/api/v1/users/me");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt.token");
        when(jwtTokenValidator.validate("valid.jwt.token")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(JwtAuthenticationToken.class);
        verify(chain).doFilter(request, response);
    }

    @Test
    void deve_continuar_chain_sem_autenticacao_quando_token_invalido() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/users/me");
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
        when(jwtTokenValidator.validate("bad.token"))
                .thenThrow(new JwtValidationException("MALFORMED_TOKEN", "JWT malformed"));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void deve_continuar_chain_sem_autenticacao_quando_sem_header() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/users/me");
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verify(jwtTokenValidator, never()).validate(any());
    }

    @Test
    void deve_continuar_chain_sem_autenticacao_quando_header_nao_e_Bearer() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/users/me");
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verify(jwtTokenValidator, never()).validate(any());
    }
}
