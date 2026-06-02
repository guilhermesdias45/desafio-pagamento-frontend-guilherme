package com.acaboumony.fraud;

import com.acaboumony.fraud.config.InternalSecretProperties;
import com.acaboumony.fraud.config.SecurityConfig;
import com.acaboumony.fraud.config.TestSecurityConfig;
import com.acaboumony.fraud.controller.InternalFraudController;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.dto.response.FraudScoreResponse;
import com.acaboumony.fraud.exception.FraudAnalysisException;
import com.acaboumony.fraud.exception.GlobalExceptionHandler;
import com.acaboumony.fraud.security.InternalSecretFilter;
import com.acaboumony.fraud.service.FraudDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link InternalFraudController}.
 * Verifies HTTP contract, Bean Validation, and InternalSecretFilter enforcement.
 */
@WebMvcTest(
        controllers = InternalFraudController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class, InternalSecretFilter.class})
class InternalFraudControllerTest {

    static final String TEST_SECRET = "test-internal-secret";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean FraudDetectionService fraudDetectionService;
    @MockBean InternalSecretProperties internalSecretProperties;

    @BeforeEach
    void setUp() {
        when(internalSecretProperties.secret()).thenReturn(TEST_SECRET);
    }

    private static final String URL = "/internal/fraud/score";

    private FraudAnalysisRequest validRequest() {
        return new FraudAnalysisRequest(
                "txn-001",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                1000L,
                "pm_card_visa",
                "192.168.1.1",
                null, null, null
        );
    }

    @Test
    @DisplayName("returns_200_with_valid_request — happy path")
    void returns_200_with_valid_request() throws Exception {
        FraudScoreResponse mockResponse = new FraudScoreResponse(0, "APPROVE", List.of(), 10L);
        when(fraudDetectionService.analyzeTransaction(any())).thenReturn(mockResponse);

        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.decision").value("APPROVE"));
    }

    @Test
    @DisplayName("returns_400_with_missing_transaction_id — Bean Validation")
    void returns_400_with_missing_transaction_id() throws Exception {
        FraudAnalysisRequest invalidRequest = new FraudAnalysisRequest(
                "",   // blank transactionId — @NotBlank violation
                UUID.randomUUID(), 1000L, "pm_card_visa", "192.168.1.1",
                null, null, null
        );

        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("returns_403_without_internal_secret — InternalSecretFilter rejects")
    void returns_403_without_internal_secret() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("returns_403_with_wrong_internal_secret")
    void returns_403_with_wrong_internal_secret() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("returns_200_with_correct_internal_secret — passes filter")
    void returns_200_with_correct_internal_secret() throws Exception {
        FraudScoreResponse mockResponse = new FraudScoreResponse(40, "REVIEW", List.of("IP_BLACKLISTED"), 5L);
        when(fraudDetectionService.analyzeTransaction(any())).thenReturn(mockResponse);

        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REVIEW"));
    }

    @Test
    @DisplayName("returns_400_with_null_customer_id — Bean Validation")
    void returns_400_with_null_customer_id() throws Exception {
        FraudAnalysisRequest invalidRequest = new FraudAnalysisRequest(
                "txn-001", null, 1000L, "pm_card_visa", "192.168.1.1",
                null, null, null
        );

        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("returns_500_on_domain_exception — GlobalExceptionHandler maps FraudServiceException")
    void returns_500_on_domain_exception() throws Exception {
        when(fraudDetectionService.analyzeTransaction(any()))
                .thenThrow(new FraudAnalysisException("Redis unavailable"));

        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", TEST_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("FRAUD_ANALYSIS_ERROR"));
    }
}
