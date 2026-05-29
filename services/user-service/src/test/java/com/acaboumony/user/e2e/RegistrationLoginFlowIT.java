package com.acaboumony.user.e2e;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.dto.request.LoginRequest;
import com.acaboumony.user.dto.request.RegisterRequest;
import com.acaboumony.user.repository.MerchantRepository;
import com.acaboumony.user.repository.UserRepository;
import com.acaboumony.user.support.BaseIntegrationTest;
import com.acaboumony.user.support.KafkaTestConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class RegistrationLoginFlowIT extends BaseIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void cleanup() {
        // Best-effort cleanup between tests — actual state isolation via unique emails
    }

    @Test
    void deve_completar_fluxo_register_CUSTOMER_confirm_email_login_e_obter_cookie_refresh() throws Exception {
        String email = "customer-" + System.nanoTime() + "@test.com";

        // 1. Register
        var registerReq = new RegisterRequest(email, "Senha@1234", "Teste Customer",
                UserRole.CUSTOMER, null, null);
        MvcResult registerResult = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String userId = mapper.readTree(registerResult.getResponse().getContentAsString())
                .get("userId").asText();

        // 2. Verify Kafka event user.registered
        try (KafkaTestConsumer consumer = new KafkaTestConsumer(KAFKA.getBootstrapServers())) {
            var record = consumer.pollForEventType("user.registered", 10, 500);
            assertThat(record.value()).contains("user.registered");
        }

        // 3. Fetch confirm token from Redis
        Set<String> keys = redis.keys("email_confirm:*");
        assertThat(keys).isNotEmpty();
        String confirmToken = keys.stream()
                .filter(k -> userId.equals(redis.opsForValue().get(k)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Confirm token not found in Redis for userId=" + userId));
        String token = confirmToken.substring("email_confirm:".length());

        // 4. Confirm email
        mvc.perform(post("/api/v1/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        // 5. Login
        var loginReq = new LoginRequest(email, "Senha@1234", null, null);
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refreshToken")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));

        // 6. Verify login.success Kafka event
        try (KafkaTestConsumer consumer = new KafkaTestConsumer(KAFKA.getBootstrapServers())) {
            var record = consumer.pollForEventType("user.login.success", 10, 500);
            assertThat(record.value()).contains("user.login.success");
        }
    }

    @Test
    void deve_completar_fluxo_register_MERCHANT_OWNER_atomico_user_e_merchant() throws Exception {
        String email = "owner-" + System.nanoTime() + "@test.com";
        String cnpj = "11222333000181"; // valid CNPJ

        var req = new RegisterRequest(email, "Senha@1234", "Ana Merchant",
                UserRole.MERCHANT_OWNER, "Loja da Ana", cnpj);

        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantId").exists())
                .andReturn();

        String merchantIdStr = mapper.readTree(result.getResponse().getContentAsString())
                .get("merchantId").asText();
        String userId = mapper.readTree(result.getResponse().getContentAsString())
                .get("userId").asText();

        // Verify atomicity in DB
        var user = userRepository.findById(java.util.UUID.fromString(userId)).orElseThrow();
        var merchant = merchantRepository.findById(java.util.UUID.fromString(merchantIdStr)).orElseThrow();
        assertThat(user.getMerchant().getId()).isEqualTo(merchant.getId());
        assertThat(merchant.getOwner().getId()).isEqualTo(user.getId());
    }

    @Test
    void deve_rejeitar_login_com_status_PENDING_quando_email_nao_confirmado() throws Exception {
        String email = "pending-" + System.nanoTime() + "@test.com";
        var req = new RegisterRequest(email, "Senha@1234", "Pending User", UserRole.CUSTOMER, null, null);
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        var loginReq = new LoginRequest(email, "Senha@1234", null, null);
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_CONFIRMED"));
    }

    @Test
    void deve_bloquear_conta_apos_5_tentativas_e_publicar_user_login_blocked() throws Exception {
        String email = "lockout-" + System.nanoTime() + "@test.com";
        var req = new RegisterRequest(email, "Senha@1234", "Lock User", UserRole.CUSTOMER, null, null);
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        var badLogin = new LoginRequest(email, "WrongPassword!", null, null);
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(badLogin)))
                    .andExpect(status().isUnauthorized());
        }
        // 5th attempt → locked
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized()); // still INVALID_CREDENTIALS on 5th

        // Now account should be locked
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(badLogin)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));

        // Verify Kafka event
        try (KafkaTestConsumer consumer = new KafkaTestConsumer(KAFKA.getBootstrapServers())) {
            var record = consumer.pollForEventType("user.login.blocked", 10, 500);
            assertThat(record.value()).contains("user.login.blocked");
        }
    }
}
