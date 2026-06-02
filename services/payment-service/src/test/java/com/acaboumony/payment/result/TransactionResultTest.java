package com.acaboumony.payment.result;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TransactionResultTest {

    @Test
    void approved_record_createsSuccessfully() {
        var orderId = UUID.randomUUID();
        var result = new TransactionResult.Approved("txn_001", 123L, orderId, 500L, false);

        assertEquals("txn_001", result.transactionId());
        assertEquals(123L, result.mpPaymentId());
        assertEquals(orderId, result.orderId());
        assertEquals(500L, result.processingTimeMs());
        assertFalse(result.duplicate());
    }

    @Test
    void approved_record_duplicateTrue() {
        var result = new TransactionResult.Approved("txn_001", 123L, UUID.randomUUID(), 500L, true);

        assertTrue(result.duplicate());
    }

    @Test
    void failed_record_createsSuccessfully() {
        var result = new TransactionResult.Failed("CARD_DECLINED", "Card declined", true, 300L);

        assertEquals("CARD_DECLINED", result.errorCode());
        assertEquals("Card declined", result.message());
        assertTrue(result.retryable());
        assertEquals(300L, result.processingTimeMs());
    }

    @Test
    void failed_record_notRetryable() {
        var result = new TransactionResult.Failed("INVALID_CURRENCY", "Only BRL is supported", false, 100L);

        assertFalse(result.retryable());
    }

    @Test
    void toResponse_withApproved_returnsTransactionResponse() {
        var result = new TransactionResult.Approved("txn_001", 123L, UUID.randomUUID(), 500L, false);

        var response = TransactionResult.toResponse(result);

        assertNotNull(response);
        assertEquals("txn_001", response.transactionId());
        assertEquals("APPROVED", response.status());
    }

    @Test
    void toResponse_withFailed_returnsTransactionResponse() {
        var result = new TransactionResult.Failed("CARD_DECLINED", "Card declined", true, 300L);

        var response = TransactionResult.toResponse(result);

        assertNotNull(response);
        assertNull(response.transactionId());
        assertEquals("FAILURE", response.status());
    }
}
