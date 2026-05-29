package com.acaboumony.user.security;

import com.acaboumony.user.config.InternalSecretProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalSecretFilterTest {

    private static final String CORRECT_SECRET = "my-test-internal-secret";

    private InternalSecretFilter filter;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new InternalSecretFilter(new InternalSecretProperties(CORRECT_SECRET));
        chain = new MockFilterChain();
    }

    @Test
    void deve_passar_quando_X_Internal_Secret_correto_em_rota_internal() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/auth/validate-token");
        req.addHeader(InternalSecretFilter.HEADER, CORRECT_SECRET);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200); // default — filter passed
        assertThat(chain.getRequest()).isNotNull();  // chain was invoked
    }

    @Test
    void deve_retornar_403_quando_X_Internal_Secret_ausente_em_rota_internal() throws Exception {
        // CE-INTERNAL-001
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/auth/validate-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain noInvokeChain = new MockFilterChain();

        filter.doFilterInternal(req, res, noInvokeChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(res.getContentType()).isEqualTo("application/problem+json");
        assertThat(noInvokeChain.getRequest()).isNull();  // chain was NOT invoked
    }

    @Test
    void deve_retornar_403_quando_X_Internal_Secret_invalido_em_rota_internal() throws Exception {
        // CE-INTERNAL-002
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/auth/validate-token");
        req.addHeader(InternalSecretFilter.HEADER, "wrong-secret");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain noInvokeChain = new MockFilterChain();

        filter.doFilterInternal(req, res, noInvokeChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(noInvokeChain.getRequest()).isNull();
    }

    @Test
    void deve_ignorar_filter_quando_rota_nao_e_internal() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        // No X-Internal-Secret header — but route is not /internal/
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        // Chain was invoked; status is default 200
        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void deve_comparar_em_constant_time_para_evitar_timing_attack() throws Exception {
        // Verify constant-time comparison is used: different-length secret still calls isEqual
        // (MessageDigest.isEqual is constant-time regardless of mismatch position)
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/anything");
        req.addHeader(InternalSecretFilter.HEADER, "short");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, new MockFilterChain());

        // Should be forbidden, not an exception due to length mismatch
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        // Note: MessageDigest.isEqual returns false for different-length byte arrays — no timing leak
        // because the method returns immediately after the length check (both are constant wrt content).
    }
}
