package com.acaboumony.payment.dto;

import com.acaboumony.payment.domain.enums.RefundReason;
import com.acaboumony.payment.dto.request.RefundRequest;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.RefundResponse;
import com.acaboumony.payment.dto.response.TransactionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DtoRecordsTest {

    @Test
    void transactionRequest_allAccessors() {
        var request = new TransactionRequest(
            5000L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 3, UUID.randomUUID()
        );

        assertEquals(5000L, request.amountInCents());
        assertEquals("BRL", request.currency());
        assertNotNull(request.customerId());
        assertNotNull(request.orderId());
        assertEquals("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", request.cardToken());
        assertEquals("visa", request.paymentMethodId());
        assertEquals(3, request.installments());
        assertNotNull(request.idempotencyKey());
    }

    @Test
    void transactionRequest_nullInstallments_defaultsToOne() {
        var request = new TransactionRequest(
            5000L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", null, UUID.randomUUID()
        );

        assertEquals(1, request.installments());
    }

    @Test
    void refundRequest_allAccessors() {
        var request = new RefundRequest(
            3000L, RefundReason.FRAUD, UUID.randomUUID(), UUID.randomUUID()
        );

        assertEquals(3000L, request.amountInCents());
        assertEquals(RefundReason.FRAUD, request.reason());
        assertNotNull(request.requestedBy());
        assertNotNull(request.idempotencyKey());
    }

    @Test
    void refundRequest_nullAmount() {
        var request = new RefundRequest(
            null, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(), UUID.randomUUID()
        );

        assertNull(request.amountInCents());
    }

    @Test
    void transactionResponse_allAccessors() {
        var refunds = List.of(
            new TransactionResponse.RefundSummary("ref_1", 1000L, true, "FRAUD", Instant.now())
        );
        var response = new TransactionResponse(
            "txn_001", 123L, UUID.randomUUID(), "APPROVED",
            5000L, "BRL", "visa", "1234", 1, 150L,
            Instant.now(), refunds
        );

        assertEquals("txn_001", response.transactionId());
        assertEquals(123L, response.mpPaymentId());
        assertNotNull(response.orderId());
        assertEquals("APPROVED", response.status());
        assertEquals(5000L, response.amountInCents());
        assertEquals("BRL", response.currency());
        assertEquals("visa", response.cardBrand());
        assertEquals("1234", response.cardLastFour());
        assertEquals(1, response.installments());
        assertEquals(150L, response.processingTimeMs());
        assertNotNull(response.createdAt());
        assertEquals(1, response.refunds().size());
    }

    @Test
    void refundSummary_allAccessors() {
        var summary = new TransactionResponse.RefundSummary(
            "ref_001", 2500L, false, "CUSTOMER_REQUEST", Instant.now()
        );

        assertEquals("ref_001", summary.refundId());
        assertEquals(2500L, summary.amountInCents());
        assertFalse(summary.isFullRefund());
        assertEquals("CUSTOMER_REQUEST", summary.reason());
        assertNotNull(summary.processedAt());
    }

    @Test
    void refundResponse_allAccessors() {
        var response = new RefundResponse(
            "ref_001", "txn_001", 5000L, true, "FRAUD", "COMPLETED", Instant.now()
        );

        assertEquals("ref_001", response.refundId());
        assertEquals("txn_001", response.transactionId());
        assertEquals(5000L, response.amountInCents());
        assertTrue(response.isFullRefund());
        assertEquals("FRAUD", response.reason());
        assertEquals("COMPLETED", response.status());
        assertNotNull(response.processedAt());
    }
}
