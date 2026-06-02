package com.acaboumony.payment.client;

import com.acaboumony.payment.client.fraud.FraudAnalysisRequest;
import com.acaboumony.payment.client.fraud.FraudScoreResponse;
import com.acaboumony.payment.client.fraud.FraudServiceClient;
import com.acaboumony.payment.config.InternalSecretProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class FraudServiceClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    FraudServiceClient fraudServiceClient;

    @BeforeEach
    void setUp() {
        fraudServiceClient = new FraudServiceClient(
                wireMock.baseUrl(),
                new InternalSecretProperties("test-secret")
        );
    }

    @Test
    void returns_fraud_score_when_service_responds() {
        wireMock.stubFor(post(urlEqualTo("/internal/fraud/score"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "score": 35,
                                  "decision": "APPROVE",
                                  "reasons": [],
                                  "analysisTimeMs": 55
                                }
                                """)));

        FraudAnalysisRequest request = new FraudAnalysisRequest(
                "txn_abc", UUID.randomUUID(), 5000L, "visa", "127.0.0.1"
        );
        FraudScoreResponse response = fraudServiceClient.analyzeTransaction(request);

        assertThat(response.score()).isEqualTo(35);
        assertThat(response.decision()).isEqualTo("APPROVE");

        // Verify X-Internal-Secret header was sent
        wireMock.verify(postRequestedFor(urlEqualTo("/internal/fraud/score"))
                .withHeader("X-Internal-Secret", equalTo("test-secret")));
    }

    @Test
    void returns_conservative_fallback_when_service_unavailable() {
        wireMock.stubFor(post(urlEqualTo("/internal/fraud/score"))
                .willReturn(aResponse().withStatus(503)));

        FraudAnalysisRequest request = new FraudAnalysisRequest(
                "txn_abc", UUID.randomUUID(), 5000L, "visa", "127.0.0.1"
        );
        FraudScoreResponse response = fraudServiceClient.analyzeTransaction(request);

        // Fallback: score=50, decision=APPROVE
        assertThat(response.score()).isEqualTo(50);
        assertThat(response.decision()).isEqualTo("APPROVE");
        assertThat(response.reasons()).isEmpty();
    }

    @Test
    void returns_block_decision_for_high_risk_transaction() {
        wireMock.stubFor(post(urlEqualTo("/internal/fraud/score"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "score": 92,
                                  "decision": "BLOCK",
                                  "reasons": ["UNUSUAL_AMOUNT"],
                                  "analysisTimeMs": 70
                                }
                                """)));

        FraudAnalysisRequest request = new FraudAnalysisRequest(
                "txn_xyz", UUID.randomUUID(), 99999L, "visa", "0.0.0.0"
        );
        FraudScoreResponse response = fraudServiceClient.analyzeTransaction(request);

        assertThat(response.decision()).isEqualTo("BLOCK");
        assertThat(response.score()).isEqualTo(92);
    }
}
