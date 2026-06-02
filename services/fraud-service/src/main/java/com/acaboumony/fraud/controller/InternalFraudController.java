package com.acaboumony.fraud.controller;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.dto.response.FraudScoreResponse;
import com.acaboumony.fraud.service.FraudDetectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal REST controller for fraud analysis.
 *
 * <p>Reachable only from the api-gateway via {@code X-Internal-Secret} header.
 * Results are never exposed to end users — only to the payment-service.</p>
 */
@RestController
@RequestMapping("/internal/fraud")
public class InternalFraudController {

    private final FraudDetectionService fraudDetectionService;

    public InternalFraudController(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    /**
     * Analyses a transaction for fraud risk and returns a score + decision.
     *
     * @param request validated fraud analysis request from the payment-service
     * @return 200 OK with fraud score response
     */
    @PostMapping("/score")
    public ResponseEntity<FraudScoreResponse> score(
            @Valid @RequestBody FraudAnalysisRequest request) {
        FraudScoreResponse response = fraudDetectionService.analyzeTransaction(request);
        return ResponseEntity.ok(response);
    }
}
