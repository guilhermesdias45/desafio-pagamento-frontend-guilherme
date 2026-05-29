package com.acaboumony.payment.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class FraudServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FraudServiceClient.class);

    private final RestTemplate restTemplate;
    private final String fraudServiceUrl;

    public FraudServiceClient(@Value("${fraud.service.url}") String fraudServiceUrl) {
        this.fraudServiceUrl = fraudServiceUrl;
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(250))
            .setReadTimeout(Duration.ofMillis(250))
            .build();
    }

    public FraudScoreResult score(FraudAnalysisRequest request) {
        try {
            var start = Instant.now();
            var response = restTemplate.postForEntity(
                fraudServiceUrl + "/internal/fraud/score",
                request,
                FraudScoreResult.class
            );
            log.debug("Fraud check completed in {}ms",
                java.time.Duration.between(start, Instant.now()).toMillis());
            return response.getBody();
        } catch (Exception e) {
            log.warn("Fraud service unavailable or timeout, returning fallback score=50: {}", e.getMessage());
            return new FraudScoreResult(50, "APPROVE", List.of("FALLBACK_TIMEOUT"), 0L);
        }
    }

    public record FraudAnalysisRequest(
        String transactionId,
        UUID customerId,
        UUID merchantId,
        Long amountInCents,
        String paymentMethodId,
        String ipAddress,
        String deviceFingerprint,
        Double latitude,
        Double longitude
    ) {}

    public record FraudScoreResult(
        Integer score,
        String decision,
        List<String> reasons,
        Long analysisTimeMs
    ) {}
}
