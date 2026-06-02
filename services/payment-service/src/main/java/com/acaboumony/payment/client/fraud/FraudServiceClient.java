package com.acaboumony.payment.client.fraud;

import com.acaboumony.payment.config.InternalSecretProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Client for the fraud-service internal API.
 * On timeout or unavailability, returns a conservative fallback score of 50 (APPROVE).
 * Fraud score is NEVER exposed to callers — only logged internally.
 */
@Component
public class FraudServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FraudServiceClient.class);

    private final RestClient restClient;
    private final InternalSecretProperties internalSecretProperties;

    public FraudServiceClient(
            @Value("${fraud-service.url}") String fraudServiceUrl,
            InternalSecretProperties internalSecretProperties
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(fraudServiceUrl)
                .build();
        this.internalSecretProperties = internalSecretProperties;
    }

    public FraudScoreResponse analyzeTransaction(FraudAnalysisRequest request) {
        try {
            FraudScoreResponse response = restClient.post()
                    .uri("/internal/fraud/score")
                    .header("X-Internal-Secret", internalSecretProperties.secret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FraudScoreResponse.class);

            if (response == null) {
                log.warn("Null response from fraud service, using conservative fallback score=50");
                return fallback();
            }

            // NEVER log the fraud score to callers - internal use only
            log.info("Fraud analysis completed for transactionId={} decision={}",
                    request.transactionId(), response.decision());
            return response;

        } catch (Exception e) {
            log.warn("Fraud service unavailable, using conservative fallback score=50. Error: {}", e.getMessage());
            return fallback();
        }
    }

    private FraudScoreResponse fallback() {
        return new FraudScoreResponse(50, "APPROVE", List.of(), 0);
    }
}
