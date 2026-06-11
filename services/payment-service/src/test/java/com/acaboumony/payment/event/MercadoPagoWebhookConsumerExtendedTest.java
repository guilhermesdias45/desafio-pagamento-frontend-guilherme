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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MercadoPagoWebhookConsumerExtendedTest {

    @Mock
    private TransactionService transactionService;

    private MercadoPagoWebhookConsumer consumer;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String WEBHOOK_SECRET = "test-secret-key-12345";

    @BeforeEach
    void setUp() {
        consumer = new MercadoPagoWebhookConsumer(transactionService, WEBHOOK_SECRET, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(consumer).build();
    }

    private final String TEST_X_REQUEST_ID = UUID.randomUUID().toString();
    private final String TEST_DATA_ID = "123456";

    private ObjectNode buildPayload(long mpPaymentId, String action) {
        var payload = objectMapper.createObjectNode();
        payload.put("type", "payment");
        payload.put("action", action);
        var data = objectMapper.createObjectNode();
        data.put("id", mpPaymentId);
        payload.set("data", data);
        return payload;
    }

    private ObjectNode buildPayloadWithType(long mpPaymentId, String action, String type) {
        var payload = objectMapper.createObjectNode();
        payload.put("type", type);
        payload.put("action", action);
        var data = objectMapper.createObjectNode();
        data.put("id", mpPaymentId);
        payload.set("data", data);
        return payload;
    }

    private String signPayload(String dataId, String xRequestId, long tsSeconds) {
        try {
            var template = MercadoPagoWebhookConsumer.buildSignatureTemplate(dataId, xRequestId, String.valueOf(tsSeconds));

            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var hash = HexFormat.of().formatHex(mac.doFinal(template.getBytes(StandardCharsets.UTF_8)));
            return "ts=" + tsSeconds + ",v1=" + hash;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handleWebhook_blankSignature_returnsOk() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var rawBody = payload.toString();

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", "  ")
                .content(rawBody))
            .andExpect(status().isOk());

        verify(transactionService, never()).handlePaymentWebhook(anyLong(), anyString(), any());
    }

    @Test
    void handleWebhook_nonPaymentType_returnsOk() throws Exception {
        var payload = buildPayloadWithType(123456L, "payment.updated", "charge");
        var rawBody = payload.toString();
        var ts = Instant.now().getEpochSecond();
        var signature = signPayload(TEST_DATA_ID, TEST_X_REQUEST_ID, ts);

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", signature)
                .header("x-request-id", TEST_X_REQUEST_ID)
                .param("data.id", TEST_DATA_ID)
                .content(rawBody))
            .andExpect(status().isOk());

        verify(transactionService, never()).handlePaymentWebhook(anyLong(), anyString(), any());
    }

    @Test
    void handleWebhook_serviceThrowsException_returns500() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var rawBody = payload.toString();
        var ts = Instant.now().getEpochSecond();
        var signature = signPayload(TEST_DATA_ID, TEST_X_REQUEST_ID, ts);

        doThrow(new RuntimeException("DB error"))
            .when(transactionService).handlePaymentWebhook(eq(123456L), eq("payment.updated"), any());

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", signature)
                .header("x-request-id", TEST_X_REQUEST_ID)
                .param("data.id", TEST_DATA_ID)
                .content(rawBody))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void handleWebhook_invalidFormatSignature_returnsForbidden() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var rawBody = payload.toString();

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", "invalid-format-no-ts-or-v1")
                .content(rawBody))
            .andExpect(status().isForbidden());
    }

    @Test
    void handleWebhook_invalidTsNumber_returnsForbidden() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var rawBody = payload.toString();

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", "ts=not-a-number,v1=abc")
                .content(rawBody))
            .andExpect(status().isForbidden());
    }

    @Test
    void handleWebhook_signatureOnlyTs_returnsForbidden() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var rawBody = payload.toString();

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", "ts=12345")
                .content(rawBody))
            .andExpect(status().isForbidden());
    }

    @Test
    void handleWebhook_signatureOnlyV1_returnsForbidden() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var rawBody = payload.toString();

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", "v1=abc123")
                .content(rawBody))
            .andExpect(status().isForbidden());
    }

    @Test
    void handleWebhook_emptyTsValue_returnsForbidden() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var rawBody = payload.toString();

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", "ts=,v1=abc")
                .content(rawBody))
            .andExpect(status().isForbidden());
    }

    @Test
    void handleWebhook_emptyV1Value_returnsForbidden() throws Exception {
        var payload = buildPayload(123456L, "payment.updated");
        var rawBody = payload.toString();

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                .contentType("application/json")
                .header("x-signature", "ts=12345,v1=")
                .content(rawBody))
            .andExpect(status().isForbidden());
    }
}
