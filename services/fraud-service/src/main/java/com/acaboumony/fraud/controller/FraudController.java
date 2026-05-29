package com.acaboumony.fraud.controller;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.dto.response.FraudScore;
import com.acaboumony.fraud.service.FraudDetectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/fraud")
public class FraudController {

    private final FraudDetectionService fraudDetection;

    public FraudController(FraudDetectionService fraudDetection) {
        this.fraudDetection = fraudDetection;
    }

    @PostMapping("/score")
    public ResponseEntity<FraudScore> score(@Valid @RequestBody FraudAnalysisRequest request) {
        FraudScore result = fraudDetection.score(request);
        return ResponseEntity.ok(result);
    }
}
