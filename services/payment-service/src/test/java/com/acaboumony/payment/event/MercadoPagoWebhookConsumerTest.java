package com.acaboumony.payment.event;

import com.acaboumony.payment.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MercadoPagoWebhookConsumerTest {

    @Mock
    private TransactionService transactionService;

    private MercadoPagoWebhookConsumer consumer;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String WEBHOOK_SECRET = "test-secret-key-12345";

    @BeforeEach
    void setUp() {
        consumer = new MercadoPagoWebhookConsumer(transactionService, WEBHOOK_SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(consumer).build();
    }

    private ObjectNode buildPayload(long mpPaymentId, String action) {
        var payload = objectMapper.createObjectNode();
        payload.put("type", "payment");
        payload.put("action", action);
        var data = objectMapper.createObjectNode();
        data.put("id", mpPaymentId);
        data.put("created_at", Instant.now().toEpochMilli());
        payload.set("data", data);
        return payload;
    }

    private String signPayload(ObjectNode payload, long tsSeconds) {
        var dataId = payload.path("data").path("id").asText();
        var createdAt = payload.path("data").path("created_at").asText("");
        var body = payload.toString();
        var payloadToSign = "id" + dataId + "created-at" + createdAt + body;

        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var hash = HexFormat.of().formatHex(mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8)));
            return "ts=" + tsSeconds + ",v1=" + hash;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handleWebhook_whenValidSignature_returnsOk() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var ts = Instant.now().getEpochSecond();
        var signature = signPayload(payload, ts);

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", signature)
                .content(payload.toString()))
            .andExpect(status().isOk());

        verify(transactionService).handlePaymentWebhook(eq(123456L), eq("payment.updated"), any());
    }

    @Test
    void handleWebhook_whenExpiredTimestamp_returnsForbidden() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var expiredTs = Instant.now().getEpochSecond() - 400;
        var signature = signPayload(payload, expiredTs);

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", signature)
                .content(payload.toString()))
            .andExpect(status().isForbidden());

        verify(transactionService, never()).handlePaymentWebhook(anyLong(), anyString(), any());
    }

    @Test
    void handleWebhook_whenMissingSignature_returnsUnauthorized() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .content(payload.toString()))
            .andExpect(status().isUnauthorized());

        verify(transactionService, never()).handlePaymentWebhook(anyLong(), anyString(), any());
    }

    @Test
    void handleWebhook_whenInvalidSignature_returnsForbidden() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var ts = Instant.now().getEpochSecond();

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", "ts=" + ts + ",v1=invalidhash")
                .content(payload.toString()))
            .andExpect(status().isForbidden());

        verify(transactionService, never()).handlePaymentWebhook(anyLong(), anyString(), any());
    }
}
