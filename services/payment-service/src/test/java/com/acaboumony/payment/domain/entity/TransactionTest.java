package com.acaboumony.payment.domain.entity;

import com.acaboumony.payment.domain.enums.TransactionStatus;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    @Test
    void constructor_setsFields() {
        var transactionId = "txn_001";
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var merchantId = UUID.randomUUID();
        var idempotencyKey = UUID.randomUUID();

        var tx = new Transaction(transactionId, orderId, customerId, merchantId,
            5000L, "BRL", "visa", TransactionStatus.APPROVED, idempotencyKey);

        assertEquals(transactionId, tx.getTransactionId());
        assertEquals(orderId, tx.getOrderId());
        assertEquals(customerId, tx.getCustomerId());
        assertEquals(merchantId, tx.getMerchantId());
        assertEquals(5000L, tx.getAmountInCents());
        assertEquals("BRL", tx.getCurrency());
        assertEquals("visa", tx.getPaymentMethodId());
        assertEquals(TransactionStatus.APPROVED, tx.getStatus());
        assertEquals(idempotencyKey, tx.getIdempotencyKey());
        assertEquals(0L, tx.getRefundedAmountInCents());
    }

    @Test
    void setters_updateFields() {
        var tx = new Transaction();
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var merchantId = UUID.randomUUID();
        var idempotencyKey = UUID.randomUUID();

        tx.setTransactionId("txn_002");
        tx.setMpPaymentId(123L);
        tx.setOrderId(orderId);
        tx.setCustomerId(customerId);
        tx.setMerchantId(merchantId);
        tx.setAmountInCents(1000L);
        tx.setCurrency("USD");
        tx.setCardBrand("master");
        tx.setCardLastFour("5678");
        tx.setInstallments(2);
        tx.setPaymentMethodId("master");
        tx.setStatus(TransactionStatus.DECLINED);
        tx.setErrorCode("CARD_DECLINED");
        tx.setErrorMessage("Card declined");
        tx.setIdempotencyKey(idempotencyKey);
        tx.setProcessingTimeMs(500L);
        tx.setRefundedAmountInCents(5000L);

        assertEquals("txn_002", tx.getTransactionId());
        assertEquals(123L, tx.getMpPaymentId());
        assertEquals(orderId, tx.getOrderId());
        assertEquals(customerId, tx.getCustomerId());
        assertEquals(merchantId, tx.getMerchantId());
        assertEquals(1000L, tx.getAmountInCents());
        assertEquals("USD", tx.getCurrency());
        assertEquals("master", tx.getCardBrand());
        assertEquals("5678", tx.getCardLastFour());
        assertEquals(2, tx.getInstallments());
        assertEquals("master", tx.getPaymentMethodId());
        assertEquals(TransactionStatus.DECLINED, tx.getStatus());
        assertEquals("CARD_DECLINED", tx.getErrorCode());
        assertEquals("Card declined", tx.getErrorMessage());
        assertEquals(idempotencyKey, tx.getIdempotencyKey());
        assertEquals(500L, tx.getProcessingTimeMs());
        assertEquals(5000L, tx.getRefundedAmountInCents());
    }

    @Test
    void onCreate_setsTimestamps() {
        var tx = new Transaction();
        tx.onCreate();
        assertNotNull(tx.getCreatedAt());
        assertNotNull(tx.getUpdatedAt());
    }
}
