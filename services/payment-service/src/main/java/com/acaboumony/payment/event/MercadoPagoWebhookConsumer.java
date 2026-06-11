package com.acaboumony.payment.event;

import com.acaboumony.payment.service.TransactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
import java.time.Instant;
import java.util.HexFormat;

@RestController
public class MercadoPagoWebhookConsumer {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookConsumer.class);
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long MAX_SIGNATURE_AGE_SECONDS = 300;

    private final TransactionService transactionService;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public MercadoPagoWebhookConsumer(TransactionService transactionService,
                                       @Value("${mercadopago.webhook-secret:}") String webhookSecret,
                                       ObjectMapper objectMapper) {
        this.transactionService = transactionService;
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/webhooks/mercadopago")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            HttpServletRequest request) {

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        log.info("Received MP webhook: type={}", payload.path("type").asText());

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("MERCADOPAGO_WEBHOOK_SECRET not configured — rejecting webhook for security");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (signature == null || signature.isBlank()) {
            log.warn("Webhook sem assinatura x-signature — ACK 200, ignorando payload");
            return ResponseEntity.ok().build();
        }

        var dataIdFromQuery = request.getParameter("data.id");

        if (!validateSignature(payload, signature, xRequestId, dataIdFromQuery)) {
            log.warn("Invalid x-signature for webhook — rejecting");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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

    private boolean validateSignature(JsonNode payload, String signature, String xRequestId, String dataIdFromQuery) {
        try {
            var tsAndV1 = parseSignatureHeader(signature);
            if (tsAndV1 == null) {
                log.warn("x-signature header does not match expected format ts=...,v1=...");
                return false;
            }

            var ts = tsAndV1[0];
            var v1 = tsAndV1[1];

            long tsSeconds;
            try {
                tsSeconds = Long.parseLong(ts);
            } catch (NumberFormatException e) {
                log.warn("x-signature ts is not a valid number");
                return false;
            }
            long nowSeconds = Instant.now().getEpochSecond();
            if (Math.abs(nowSeconds - tsSeconds) > MAX_SIGNATURE_AGE_SECONDS) {
                log.warn("x-signature timestamp expired: ts={}, now={}, maxAge={}s",
                    tsSeconds, nowSeconds, MAX_SIGNATURE_AGE_SECONDS);
                return false;
            }

            var template = buildSignatureTemplate(dataIdFromQuery, xRequestId, ts);

            var mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            var expectedHash = HexFormat.of().formatHex(mac.doFinal(template.getBytes(StandardCharsets.UTF_8)));

            log.debug("HMAC template: {} | expected={} | received={}", template, expectedHash, v1);

            if (!expectedHash.equals(v1)) {
                log.warn("x-signature HMAC mismatch (expected={}, received={})", expectedHash, v1);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error validating webhook signature: {}", e.getMessage());
            return false;
        }
    }

    static String buildSignatureTemplate(String dataId, String xRequestId, String ts) {
        var sb = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            var normalized = dataId.chars().allMatch(Character::isDigit) ? dataId : dataId.toLowerCase();
            sb.append("id:").append(normalized).append(";");
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            sb.append("request-id:").append(xRequestId).append(";");
        }
        sb.append("ts:").append(ts).append(";");
        return sb.toString();
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
