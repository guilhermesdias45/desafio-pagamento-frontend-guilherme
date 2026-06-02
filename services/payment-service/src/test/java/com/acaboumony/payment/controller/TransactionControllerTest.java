package com.acaboumony.payment.controller;

import com.acaboumony.payment.dto.request.RefundRequest;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.RefundResponse;
import com.acaboumony.payment.dto.response.TransactionResponse;
import com.acaboumony.payment.dto.response.TransactionSummary;
import com.acaboumony.payment.domain.enums.RefundReason;
import com.acaboumony.payment.result.TransactionResult;
import com.acaboumony.payment.service.RefundService;
import com.acaboumony.payment.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private RefundService refundService;

    @Test
    void processTransaction_whenValidRequest_returns201() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Approved("txn_001", 123L, request.orderId(), 500L, false));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.transactionId").value("txn_001"))
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void processTransaction_whenInvalidAmount_returns400() throws Exception {
        var request = new TransactionRequest(
            0L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getTransaction_whenNotFound_returns403() throws Exception {
        var merchantId = UUID.randomUUID();
        when(transactionService.findById("txn_nonexistent", merchantId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/transactions/txn_nonexistent")
                .header("X-Merchant-Id", merchantId.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void getTransaction_whenUnauthorized_returns403() throws Exception {
        var merchantId = UUID.randomUUID();
        when(transactionService.findById("txn_001", merchantId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/transactions/txn_001")
                .header("X-Merchant-Id", merchantId.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void getTransaction_whenFound_returns200() throws Exception {
        var merchantId = UUID.randomUUID();
        var tx = new TransactionResponse("txn_001", 123L, UUID.randomUUID(),
            "APPROVED", 5000L, "BRL", "visa", "1234",
            1, 500L, null, null);
        when(transactionService.findById("txn_001", merchantId)).thenReturn(Optional.of(tx));

        mockMvc.perform(get("/api/v1/transactions/txn_001")
                .header("X-Merchant-Id", merchantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.transactionId").value("txn_001"));
    }

    @Test
    void listTransactions_whenValidRequest_returns200() throws Exception {
        var merchantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var tx = new TransactionSummary("txn_001", 123L,
            "APPROVED", 5000L, "BRL", "visa", "1234",
            null, 500L);
        when(transactionService.findByCustomer(eq(customerId), eq(merchantId), any()))
            .thenReturn(new PageImpl<>(List.of(tx)));

        mockMvc.perform(get("/api/v1/transactions")
                .param("customerId", customerId.toString())
                .header("X-Merchant-Id", merchantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].transactionId").value("txn_001"));
    }

    @Test
    void refundTransaction_whenSuccess_returns200() throws Exception {
        var merchantId = UUID.randomUUID();
        var refundResponse = new RefundResponse("ref_001", "txn_001", 5000L,
            true, "CHARGEBACK", "COMPLETED", Instant.now());

        when(refundService.refund(eq("txn_001"), any(RefundRequest.class), eq(merchantId)))
            .thenReturn(refundResponse);

        var request = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/txn_001/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Id", merchantId.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.refundId").value("ref_001"))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void refundTransaction_whenInsufficientPermissions_returns403() throws Exception {
        var merchantId = UUID.randomUUID();
        when(refundService.refund(eq("txn_001"), any(RefundRequest.class), eq(merchantId)))
            .thenThrow(new IllegalArgumentException("INSUFFICIENT_PERMISSIONS"));

        var request = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/txn_001/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Id", merchantId.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void refundTransaction_whenTransactionNotFound_returns404() throws Exception {
        var merchantId = UUID.randomUUID();
        when(refundService.refund(eq("txn_nonexistent"), any(RefundRequest.class), eq(merchantId)))
            .thenThrow(new IllegalArgumentException("TRANSACTION_NOT_FOUND"));

        var request = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/txn_nonexistent/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Id", merchantId.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errors[0].code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void refundTransaction_whenNotRefundable_returns422() throws Exception {
        var merchantId = UUID.randomUUID();
        when(refundService.refund(eq("txn_001"), any(RefundRequest.class), eq(merchantId)))
            .thenThrow(new IllegalArgumentException("TRANSACTION_NOT_REFUNDABLE"));

        var request = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/txn_001/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Id", merchantId.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].code").value("TRANSACTION_NOT_REFUNDABLE"));
    }

    @Test
    void refundTransaction_whenRefundWindowExpired_returns422() throws Exception {
        var merchantId = UUID.randomUUID();
        when(refundService.refund(eq("txn_001"), any(RefundRequest.class), eq(merchantId)))
            .thenThrow(new IllegalArgumentException("REFUND_WINDOW_EXPIRED"));

        var request = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/txn_001/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Id", merchantId.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].code").value("REFUND_WINDOW_EXPIRED"));
    }

    @Test
    void listTransactions_returnsPaginatedResults() throws Exception {
        var merchantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var tx1 = new TransactionSummary("txn_001", 123L,
            "APPROVED", 5000L, "BRL", "visa", "1234",
            null, 500L);
        var tx2 = new TransactionSummary("txn_002", 456L,
            "DECLINED", 3000L, "BRL", "master", "5678",
            null, 600L);
        when(transactionService.findByCustomer(eq(customerId), eq(merchantId), any()))
            .thenReturn(new PageImpl<>(List.of(tx1, tx2)));

        mockMvc.perform(get("/api/v1/transactions")
                .param("customerId", customerId.toString())
                .param("page", "0")
                .param("size", "10")
                .header("X-Merchant-Id", merchantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].transactionId").value("txn_001"))
            .andExpect(jsonPath("$.data[1].transactionId").value("txn_002"))
            .andExpect(jsonPath("$.meta.page").value(0))
            .andExpect(jsonPath("$.meta.size").value(2));
    }

    @Test
    void processTransaction_withRequestId_includesInResponse() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );
        var requestId = UUID.randomUUID().toString();

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Approved("txn_001", 123L, request.orderId(), 500L, false));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .header("X-Request-Id", requestId)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.meta.requestId").value(requestId));
    }

    @Test
    void processTransaction_duplicate_returns200() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Approved("txn_001", 123L, request.orderId(), 500L, true));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.transactionId").value("txn_001"));
    }

    @Test
    void processTransaction_rateLimitExceeded_returns429() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Failed("RATE_LIMIT_EXCEEDED", "Too many", true, 10L));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void processTransaction_cardDeclined_returns422() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Failed("CARD_DECLINED", "Payment declined", true, 10L));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].code").value("CARD_DECLINED"));
    }

    @Test
    void getTransaction_withRequestId_includesInMeta() throws Exception {
        var merchantId = UUID.randomUUID();
        var requestId = UUID.randomUUID().toString();
        var tx = new TransactionResponse("txn_001", 123L, UUID.randomUUID(),
            "APPROVED", 5000L, "BRL", "visa", "1234",
            1, 500L, null, null);
        when(transactionService.findById("txn_001", merchantId)).thenReturn(Optional.of(tx));

        mockMvc.perform(get("/api/v1/transactions/txn_001")
                .header("X-Merchant-Id", merchantId.toString())
                .header("X-Request-Id", requestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.requestId").value(requestId));
    }

    @Test
    void refundTransaction_withRequestId_includesInMeta() throws Exception {
        var merchantId = UUID.randomUUID();
        var requestId = UUID.randomUUID().toString();
        var refundResponse = new RefundResponse("ref_001", "txn_001", 5000L,
            true, "CHARGEBACK", "COMPLETED", Instant.now());

        when(refundService.refund(eq("txn_001"), any(RefundRequest.class), eq(merchantId)))
            .thenReturn(refundResponse);

        var request = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/txn_001/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Id", merchantId.toString())
                .header("X-Request-Id", requestId)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.requestId").value(requestId));
    }

    @Test
    void processTransaction_invalidCurrency_returns400() throws Exception {
        var request = new TransactionRequest(
            8990L, "USD", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Failed("INVALID_CURRENCY", "Only BRL is supported", false, 10L));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void processTransaction_duplicateIdempotency_returns409() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Failed("DUPLICATE_IDEMPOTENCY_KEY", "Key already processed", false, 10L));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_IDEMPOTENCY_KEY"));
    }

    @Test
    void processTransaction_orderNotFound_returns404() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Failed("ORDER_NOT_FOUND", "Order validation failed", false, 10L));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errors[0].code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void processTransaction_orderNotPending_returns422() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Failed("ORDER_NOT_PENDING", "Order validation failed", false, 10L));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].code").value("ORDER_NOT_PENDING"));
    }

    @Test
    void processTransaction_gatewayTimeout_returns503() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Failed("MP_GATEWAY_TIMEOUT", "Payment gateway timeout", true, 10L));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errors[0].code").value("MP_GATEWAY_TIMEOUT"));
    }

    @Test
    void processTransaction_unknownError_returns500() throws Exception {
        var request = new TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        when(transactionService.processTransaction(any(), anyString(), any(), anyString()))
            .thenReturn(new TransactionResult.Failed("UNKNOWN_ERROR", "Something went wrong", false, 10L));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Email", "test@test.com")
                .header("X-Merchant-Id", UUID.randomUUID().toString())
                .header("X-Forwarded-For", "127.0.0.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_ERROR"));
    }

    @Test
    void refundTransaction_alreadyFullyRefunded_returns422() throws Exception {
        var merchantId = UUID.randomUUID();
        when(refundService.refund(eq("txn_001"), any(RefundRequest.class), eq(merchantId)))
            .thenThrow(new IllegalArgumentException("ALREADY_FULLY_REFUNDED"));

        var request = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/txn_001/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Id", merchantId.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].code").value("ALREADY_FULLY_REFUNDED"));
    }

    @Test
    void refundTransaction_unknownError_returns400() throws Exception {
        var merchantId = UUID.randomUUID();
        when(refundService.refund(eq("txn_001"), any(RefundRequest.class), eq(merchantId)))
            .thenThrow(new IllegalArgumentException("SOME_UNKNOWN_ERROR"));

        var request = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/txn_001/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Id", merchantId.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].code").value("SOME_UNKNOWN_ERROR"));
    }
}
