package com.acaboumony.payment.event;

import com.acaboumony.payment.service.TransactionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@RestController
public class MercadoPagoWebhookConsumer {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookConsumer.class);
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long MAX_SIGNATURE_AGE_SECONDS = 300;

    private final TransactionService transactionService;
    private final String webhookSecret;

    public MercadoPagoWebhookConsumer(TransactionService transactionService,
                                       @Value("${mercadopago.webhook-secret:}") String webhookSecret) {
        this.transactionService = transactionService;
        this.webhookSecret = webhookSecret;
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

        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (!validateSignature(payload, signature)) {
                log.warn("Invalid x-signature for webhook — rejecting");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            log.warn("MERCADOPAGO_WEBHOOK_SECRET not configured — signature validation skipped");
        }

        if (!"payment".equals(payload.path("type").asText())) {
            return ResponseEntity.ok().build();
        }

        var data = payload.path("data");
        var mpPaymentId = data.path("id").asLong();
        var action = payload.path("action").asText();

        try {
            transactionService.handlePaymentWebhook(mpPaymentId, action, payload);
        } catch (Exception e) {
            log.error("Error processing webhook for payment {}: {}", mpPaymentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok().build();
    }

    private boolean validateSignature(JsonNode payload, String signature) {
        try {
            var tsAndV1 = parseSignatureHeader(signature);
            if (tsAndV1 == null) {
                log.warn("x-signature header does not match expected format ts=...,v1=...");
                return false;
            }

            var ts = tsAndV1[0];
            var v1 = tsAndV1[1];

            var dataId = payload.path("data").path("id").asText();
            var createdAt = payload.path("data").path("created_at").asText("");
            var body = payload.toString();

            var payloadToSign = "id" + dataId + "created-at" + createdAt + body;

            var mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            var expectedHash = HexFormat.of().formatHex(mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8)));

            if (!expectedHash.equals(v1)) {
                log.warn("x-signature HMAC mismatch");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error validating webhook signature: {}", e.getMessage());
            return false;
        }
    }

    private String[] parseSignatureHeader(String header) {
        String ts = null;
        String v1 = null;
        for (var part : header.split(",")) {
            var trimmed = part.trim();
            if (trimmed.startsWith("ts=")) {
                ts = trimmed.substring(3);
            } else if (trimmed.startsWith("v1=")) {
                v1 = trimmed.substring(3);
            }
        }
        if (ts == null || v1 == null || ts.isBlank() || v1.isBlank()) {
            return null;
        }
        return new String[]{ts, v1};
    }
}
