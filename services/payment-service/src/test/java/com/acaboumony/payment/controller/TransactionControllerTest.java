package com.acaboumony.payment.controller;

import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.TransactionResponse;
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
    void getTransaction_whenNotFound_returns404() throws Exception {
        var merchantId = UUID.randomUUID();
        when(transactionService.findById("txn_nonexistent")).thenReturn(null);

        mockMvc.perform(get("/api/v1/transactions/txn_nonexistent")
                .header("X-Merchant-Id", merchantId.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errors[0].code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void getTransaction_whenUnauthorized_returns403() throws Exception {
        var merchantId = UUID.randomUUID();
        var otherMerchantId = UUID.randomUUID();
        var tx = new TransactionResponse("txn_001", 123L, UUID.randomUUID(),
            "APPROVED", 5000L, "BRL", "visa", "1234",
            1, 500L, null, null);
        when(transactionService.findById("txn_001")).thenReturn(tx);
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
        when(transactionService.findById("txn_001")).thenReturn(tx);
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
        var tx = new TransactionResponse("txn_001", 123L, UUID.randomUUID(),
            "APPROVED", 5000L, "BRL", "visa", "1234",
            1, 500L, null, null);
        when(transactionService.findByCustomer(eq(customerId), eq(merchantId), any()))
            .thenReturn(new PageImpl<>(List.of(tx)));

        mockMvc.perform(get("/api/v1/transactions")
                .param("customerId", customerId.toString())
                .header("X-Merchant-Id", merchantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].transactionId").value("txn_001"));
    }
}
