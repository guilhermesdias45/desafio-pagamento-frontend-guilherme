package com.acaboumony.payment.domain.entity;

import com.acaboumony.payment.domain.enums.RefundReason;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RefundTest {

    @Test
    void constructor_setsFields() {
        var refundId = "ref_001";
        var transactionId = "txn_001";
        var requestedBy = UUID.randomUUID();
        var idempotencyKey = UUID.randomUUID();

        var refund = new Refund(refundId, transactionId, 5000L,
            true, RefundReason.CUSTOMER_REQUEST, requestedBy,
            idempotencyKey, "COMPLETED");

        assertEquals(refundId, refund.getRefundId());
        assertEquals(transactionId, refund.getTransactionId());
        assertEquals(5000L, refund.getAmountInCents());
        assertTrue(refund.getIsFullRefund());
        assertEquals(RefundReason.CUSTOMER_REQUEST, refund.getReason());
        assertEquals(requestedBy, refund.getRequestedBy());
        assertEquals(idempotencyKey, refund.getIdempotencyKey());
        assertEquals("COMPLETED", refund.getStatus());
    }

    @Test
    void setters_updateFields() {
        var refund = new Refund();
        refund.setStatus("FAILED");
        refund.setEstimatedArrivalDays(7);
        var now = Instant.now();
        refund.setProcessedAt(now);

        assertEquals("FAILED", refund.getStatus());
        assertEquals(7, refund.getEstimatedArrivalDays());
        assertEquals(now, refund.getProcessedAt());
    }

    @Test
    void onCreate_setsTimestamps() {
        var refund = new Refund();
        refund.onCreate();
        assertNotNull(refund.getCreatedAt());
        assertNotNull(refund.getProcessedAt());
    }
}
