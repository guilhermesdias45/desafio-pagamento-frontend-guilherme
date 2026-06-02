package com.acaboumony.payment.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventRecordsTest {

    @Test
    void transactionCompletedEvent_allAccessors() {
        var items = List.of(
            new TransactionCompletedEvent.ItemEvent("Item 1", 2, 1500L),
            new TransactionCompletedEvent.ItemEvent("Item 2", 1, 3500L)
        );
        var event = new TransactionCompletedEvent(
            "txn_001", 123L, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "c@t.com", "m@t.com",
            5000L, "BRL", "visa", "1234", 1,
            items, Instant.now(), "APPROVED"
        );

        assertEquals("txn_001", event.transactionId());
        assertEquals(123L, event.mpPaymentId());
        assertNotNull(event.orderId());
        assertNotNull(event.customerId());
        assertNotNull(event.merchantId());
        assertEquals("c@t.com", event.customerEmail());
        assertEquals("m@t.com", event.merchantEmail());
        assertEquals(5000L, event.amountInCents());
        assertEquals("BRL", event.currency());
        assertEquals("visa", event.cardBrand());
        assertEquals("1234", event.cardLastFour());
        assertEquals(1, event.installments());
        assertEquals(2, event.items().size());
        assertNotNull(event.processedAt());
        assertEquals("APPROVED", event.status());
    }

    @Test
    void itemEvent_allAccessors() {
        var item = new TransactionCompletedEvent.ItemEvent("Widget", 3, 999L);

        assertEquals("Widget", item.description());
        assertEquals(3, item.quantity());
        assertEquals(999L, item.unitPriceInCents());
    }

    @Test
    void transactionFailedEvent_allAccessors() {
        var event = new TransactionFailedEvent(
            "txn_002", UUID.randomUUID(), UUID.randomUUID(),
            "d@e.com", 3000L, "CARD_DECLINED",
            "2025-06-01T12:00:00Z", "FAILURE"
        );

        assertEquals("txn_002", event.transactionId());
        assertNotNull(event.orderId());
        assertNotNull(event.customerId());
        assertEquals("d@e.com", event.customerEmail());
        assertEquals(3000L, event.amountInCents());
        assertEquals("CARD_DECLINED", event.reason());
        assertEquals("2025-06-01T12:00:00Z", event.createdAt());
        assertEquals("FAILURE", event.status());
    }

    @Test
    void transactionRefundedEvent_allAccessors() {
        var event = new TransactionRefundedEvent(
            "ref_001", "txn_003", UUID.randomUUID(),
            "f@g.com", 2500L, true, "CUSTOMER_REQUEST",
            7, Instant.now()
        );

        assertEquals("ref_001", event.refundId());
        assertEquals("txn_003", event.transactionId());
        assertNotNull(event.orderId());
        assertEquals("f@g.com", event.customerEmail());
        assertEquals(2500L, event.amountRefundedInCents());
        assertTrue(event.isFullRefund());
        assertEquals("CUSTOMER_REQUEST", event.reason());
        assertEquals(7, event.estimatedArrivalDays());
        assertNotNull(event.refundedAt());
    }

    @Test
    void transactionRefundedEvent_partialRefund() {
        var event = new TransactionRefundedEvent(
            "ref_002", "txn_004", UUID.randomUUID(),
            null, 1000L, false, "DUPLICATE",
            5, Instant.now()
        );

        assertFalse(event.isFullRefund());
        assertNull(event.customerEmail());
        assertEquals(5, event.estimatedArrivalDays());
    }
}
