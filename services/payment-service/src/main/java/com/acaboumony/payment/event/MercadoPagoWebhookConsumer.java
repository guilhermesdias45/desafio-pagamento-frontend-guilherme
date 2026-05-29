package com.acaboumony.payment.event;

import com.acaboumony.payment.service.TransactionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MercadoPagoWebhookConsumer {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookConsumer.class);

    private final TransactionService transactionService;

    public MercadoPagoWebhookConsumer(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/api/v1/webhooks/mercadopago")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody JsonNode payload,
            @RequestHeader(value = "x-signature", required = false) String signature) {

        log.info("Received MP webhook: type={}", payload.path("type").asText());

        if (signature == null || signature.isBlank()) {
            log.warn("Webhook sem assinatura x-signature — ignorando");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!"payment".equals(payload.path("type").asText())) {
            return ResponseEntity.ok().build();
        }

        var data = payload.path("data");
        var mpPaymentId = data.path("id").asLong();
        var action = payload.path("action").asText();

        try {
            transactionService.handlePaymentWebhook(mpPaymentId, action);
        } catch (Exception e) {
            log.error("Error processing webhook for payment {}: {}", mpPaymentId, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
