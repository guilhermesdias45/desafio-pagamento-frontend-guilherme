package com.acaboumony.user.e2e;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.dto.request.LoginRequest;
import com.acaboumony.user.dto.request.RegisterRequest;
import com.acaboumony.user.dto.request.TwoFactorConfirmRequest;
import com.acaboumony.user.dto.request.TwoFactorDisableRequest;
import com.acaboumony.user.repository.RecoveryCodeRepository;
import com.acaboumony.user.repository.UserRepository;
import com.acaboumony.user.support.BaseIntegrationTest;
import com.acaboumony.user.support.KafkaTestConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class TwoFactorFullFlowIT extends BaseIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StringRedisTemplate redis;
    @Autowired UserRepository userRepository;
    @Autowired RecoveryCodeRepository recoveryCodeRepository;

    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);

    private String generateTotpCode(String secret) throws Exception {
        long counter = new SystemTimeProvider().getTime() / 30;
        return codeGenerator.generate(secret, counter);
    }

    /**
     * Registers, confirms email, and logs in — returns the accessToken as a Bearer header value.
     */
    private String registerConfirmAndLogin(String email) throws Exception {
        var regReq = new RegisterRequest(email, "Senha@1234", "TwoFactor User", UserRole.CUSTOMER, null, null);
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        Set<String> keys = redis.keys("email_confirm:*");
        String tokenKey = keys.stream()
                .filter(k -> redis.opsForValue().get(k) != null)
                .findFirst().orElseThrow();
        String confirmToken = tokenKey.substring("email_confirm:".length());
        mvc.perform(post("/api/v1/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + confirmToken + "\"}"))
                .andExpect(status().isOk());

        var loginReq = new LoginRequest(email, "Senha@1234", null, null);
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        return mapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void deve_completar_fluxo_setup_confirm_login_com_2FA() throws Exception {
        String email = "2fa-" + System.nanoTime() + "@test.com";
        String accessToken = registerConfirmAndLogin(email);

        // Setup 2FA
        MvcResult setupResult = mvc.perform(post("/api/v1/auth/2fa/setup")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").exists())
                .andExpect(jsonPath("$.recoveryCodes").isArray())
                .andReturn();

        String secret = mapper.readTree(setupResult.getResponse().getContentAsString())
                .get("secret").asText();

        // Confirm 2FA with valid TOTP code
        String totpCode = generateTotpCode(secret);
        mvc.perform(post("/api/v1/auth/2fa/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TwoFactorConfirmRequest(totpCode))))
                .andExpect(status().isOk());

        // Verify Kafka event
        try (KafkaTestConsumer consumer = new KafkaTestConsumer(KAFKA.getBootstrapServers())) {
            var record = consumer.pollForEventType("user.2fa.enabled", 10, 500);
            assertThat(record.value()).contains("user.2fa.enabled");
        }

        // Login without totpCode → requiresTwoFactor
        var loginReq = new LoginRequest(email, "Senha@1234", null, null);
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresTwoFactor").value(true))
                .andExpect(jsonPath("$.twoFactorToken").exists())
                .andReturn();

        String twoFactorToken = mapper.readTree(loginResult.getResponse().getContentAsString())
                .get("twoFactorToken").asText();

        // Verify 2FA
        String verifyCode = generateTotpCode(secret);
        mvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorToken\":\"" + twoFactorToken + "\",\"code\":\"" + verifyCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken")));
    }

    @Test
    void deve_usar_recovery_code_e_marcar_used() throws Exception {
        String email = "recovery-" + System.nanoTime() + "@test.com";
        String accessToken = registerConfirmAndLogin(email);

        // Setup + confirm 2FA
        MvcResult setupResult = mvc.perform(post("/api/v1/auth/2fa/setup")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode setupJson = mapper.readTree(setupResult.getResponse().getContentAsString());
        String secret = setupJson.get("secret").asText();
        List<String> recoveryCodes = new ArrayList<>();
        setupJson.get("recoveryCodes").forEach(n -> recoveryCodes.add(n.asText()));

        mvc.perform(post("/api/v1/auth/2fa/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TwoFactorConfirmRequest(generateTotpCode(secret)))))
                .andExpect(status().isOk());

        // Login → get twoFactorToken
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, "Senha@1234", null, null))))
                .andExpect(status().isOk())
                .andReturn();
        String twoFactorToken = mapper.readTree(loginResult.getResponse().getContentAsString())
                .get("twoFactorToken").asText();

        // Use recovery code[0] → success
        mvc.perform(post("/api/v1/auth/2fa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorToken\":\"" + twoFactorToken + "\",\"code\":\"" + recoveryCodes.get(0) + "\"}"))
                .andExpect(status().isOk());

        // Use recovery code[0] again → 401
        // Need a new 2fa session token first
        MvcResult loginResult2 = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, "Senha@1234", null, null))))
                .andExpect(status().isOk())
                .andReturn();
        String twoFactorToken2 = mapper.readTree(loginResult2.getResponse().getContentAsString())
                .get("twoFactorToken").asText();

        mvc.perform(post("/api/v1/auth/2fa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorToken\":\"" + twoFactorToken2 + "\",\"code\":\"" + recoveryCodes.get(0) + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("RECOVERY_CODE_INVALID"));
    }

    @Test
    void deve_desativar_2FA_com_senha_e_code_validos() throws Exception {
        String email = "disable2fa-" + System.nanoTime() + "@test.com";
        String accessToken = registerConfirmAndLogin(email);

        MvcResult setupResult = mvc.perform(post("/api/v1/auth/2fa/setup")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String secret = mapper.readTree(setupResult.getResponse().getContentAsString()).get("secret").asText();

        mvc.perform(post("/api/v1/auth/2fa/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TwoFactorConfirmRequest(generateTotpCode(secret)))))
                .andExpect(status().isOk());

        // Disable
        String disableCode = generateTotpCode(secret);
        mvc.perform(post("/api/v1/auth/2fa/disable")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TwoFactorDisableRequest("Senha@1234", disableCode))))
                .andExpect(status().isNoContent());

        // Login without 2FA now works directly
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, "Senha@1234", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }
}
