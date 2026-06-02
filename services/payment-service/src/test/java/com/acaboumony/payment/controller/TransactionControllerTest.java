package com.acaboumony.payment.controller;

import com.acaboumony.payment.config.InternalSecretProperties;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.TransactionDetailResponse;
import com.acaboumony.payment.exception.*;
import com.acaboumony.payment.result.TransactionResult;
import com.acaboumony.payment.service.RefundService;
import com.acaboumony.payment.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TransactionController.class)
class TransactionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TransactionService transactionService;
    @MockBean RefundService refundService;
    @MockBean InternalSecretProperties internalSecretProperties;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

    private TransactionRequest validRequest() {
        return new TransactionRequest(
                5000L, "BRL", ORDER_ID,
                "abcdef1234567890abcdef1234567890",
                "visa", 1, IDEMPOTENCY_KEY
        );
    }

    @Test
    void returns_201_on_success() throws Exception {
        TransactionResult.Success success = new TransactionResult.Success(
                "txn_abc123", 999L, ORDER_ID, 250L
        );
        when(transactionService.processTransaction(any(), eq(CUSTOMER_ID))).thenReturn(success);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("txn_abc123"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.mpPaymentId").value(999));
    }

    @Test
    void returns_409_on_duplicate_idempotency_key() throws Exception {
        when(transactionService.processTransaction(any(), any()))
                .thenThrow(new DuplicateIdempotencyKeyException());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_IDEMPOTENCY_KEY"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void returns_400_on_missing_card_token() throws Exception {
        // cardToken is blank — violates @NotBlank
        TransactionRequest badRequest = new TransactionRequest(
                5000L, "BRL", ORDER_ID, "", "visa", 1, IDEMPOTENCY_KEY
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void returns_422_on_fraud_detected() throws Exception {
        when(transactionService.processTransaction(any(), any()))
                .thenThrow(new FraudDetectedException());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SUSPECTED_FRAUD"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void returns_422_on_card_declined() throws Exception {
        when(transactionService.processTransaction(any(), any()))
                .thenThrow(new CardDeclinedException("cc_rejected_call_for_authorize"));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CARD_DECLINED"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void returns_503_on_gateway_timeout() throws Exception {
        when(transactionService.processTransaction(any(), any()))
                .thenThrow(new MercadoPagoTimeoutException());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MP_GATEWAY_TIMEOUT"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void returns_429_on_rate_limit_with_retry_after_header() throws Exception {
        when(transactionService.processTransaction(any(), any()))
                .thenThrow(new RateLimitExceededException());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void returns_200_on_get_transaction() throws Exception {
        String txId = "txn_abc123456789";
        TransactionDetailResponse detail = new TransactionDetailResponse(
                txId, 999L, "APPROVED", 5000L, "BRL", "visa", "4242",
                "visa", ORDER_ID, Instant.now(), Instant.now(), List.of(), 250L
        );
        when(transactionService.findByTransactionId(txId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/transactions/{id}", txId)
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amountInCents").value(5000));
    }

    @Test
    void returns_403_on_access_denied() throws Exception {
        when(transactionService.findByTransactionId(any()))
                .thenThrow(new InsufficientPermissionsException());

        mockMvc.perform(get("/api/v1/transactions/txn_abc123")
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_PERMISSIONS"));
    }
}
