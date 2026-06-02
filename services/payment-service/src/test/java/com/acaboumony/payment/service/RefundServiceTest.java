package com.acaboumony.payment.service;

import com.acaboumony.payment.client.MercadoPagoGateway;
import com.acaboumony.payment.domain.entity.Refund;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.RefundReason;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.RefundRequest;
import com.acaboumony.payment.dto.response.RefundResponse;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.repository.AuditLogRepository;
import com.acaboumony.payment.repository.RefundRepository;
import com.acaboumony.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private MercadoPagoGateway mpGateway;
    @Mock private TransactionEventProducer eventProducer;
    @Mock private AuditLogRepository auditLogRepository;

    private RefundService service;
    private Transaction approvedTransaction;
    private RefundRequest refundRequest;

    @BeforeEach
    void setUp() {
        service = new RefundService(transactionRepository, refundRepository,
            auditLogRepository, redis, mpGateway, eventProducer);
        approvedTransaction = new Transaction();
        approvedTransaction.setTransactionId("txn_abc123");
        approvedTransaction.setMpPaymentId(123456L);
        approvedTransaction.setAmountInCents(10000L);
        approvedTransaction.setStatus(TransactionStatus.APPROVED);
        approvedTransaction.setMerchantId(UUID.randomUUID());

        refundRequest = new RefundRequest(null, RefundReason.CUSTOMER_REQUEST,
            UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void refund_whenTransactionNotFound_throwsException() {
        var merchantId = approvedTransaction.getMerchantId();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> service.refund("txn_nonexistent", refundRequest, merchantId));
    }

    @Test
    void refund_whenAlreadyFullyRefunded_throwsException() {
        var merchantId = approvedTransaction.getMerchantId();
        approvedTransaction.setStatus(TransactionStatus.FULLY_REFUNDED);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));

        assertThrows(IllegalArgumentException.class,
            () -> service.refund("txn_abc123", refundRequest, merchantId));
    }

    @Test
    void refund_whenAmountExceedsOriginal_throwsException() {
        var merchantId = approvedTransaction.getMerchantId();
        var partialRequest = new RefundRequest(20000L, RefundReason.CUSTOMER_REQUEST,
            UUID.randomUUID(), UUID.randomUUID());
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByTransactionIdOrderByCreatedAtDesc("txn_abc123")).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
            () -> service.refund("txn_abc123", partialRequest, merchantId));
    }

    @Test
    void refund_whenInsufficientPermissions_throwsException() {
        var wrongMerchantId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.refund("txn_abc123", refundRequest, wrongMerchantId));
        assertEquals("INSUFFICIENT_PERMISSIONS", ex.getMessage());
    }

    @Test
    void refund_whenFullRefundSucceeds_returnsCompleted() {
        var merchantId = approvedTransaction.getMerchantId();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(mpGateway.refundPayment(123456L, null)).thenReturn(
            new MercadoPagoGateway.RefundResult(true, 789L));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.refund("txn_abc123", refundRequest, merchantId);

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        assertTrue(response.isFullRefund());
        assertEquals(10000L, response.amountInCents());
        verify(eventProducer).publishRefunded(any());
    }

    @Test
    void refund_whenDuplicateIdempotencyKeyAndExistingRefund_returnsExisting() {
        var merchantId = approvedTransaction.getMerchantId();
        var idempotencyKey = UUID.randomUUID();
        var request = new RefundRequest(null, RefundReason.CUSTOMER_REQUEST,
            UUID.randomUUID(), idempotencyKey);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        var existingRefund = new Refund("ref_existing", "txn_abc123", 10000L,
            true, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(),
            idempotencyKey, "COMPLETED");
        when(refundRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.of(existingRefund));

        RefundResponse response = service.refund("txn_abc123", request, merchantId);

        assertNotNull(response);
        assertEquals("ref_existing", response.refundId());
        assertEquals("COMPLETED", response.status());
    }

    @Test
    void refund_whenDuplicateIdempotencyKeyAndNoExistingRefund_throwsException() {
        var merchantId = approvedTransaction.getMerchantId();
        var idempotencyKey = UUID.randomUUID();
        var request = new RefundRequest(null, RefundReason.CUSTOMER_REQUEST,
            UUID.randomUUID(), idempotencyKey);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(refundRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());

        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.refund("txn_abc123", request, merchantId));
        assertEquals("DUPLICATE_IDEMPOTENCY_KEY", ex.getMessage());
    }

    @Test
    void refund_whenTransactionNotRefundable_throwsException() {
        var merchantId = approvedTransaction.getMerchantId();
        approvedTransaction.setStatus(TransactionStatus.DECLINED);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.refund("txn_abc123", refundRequest, merchantId));
        assertEquals("TRANSACTION_NOT_REFUNDABLE", ex.getMessage());
    }

    @Test
    void refund_whenRefundWindowExpired_throwsException() throws Exception {
        var merchantId = approvedTransaction.getMerchantId();
        var createdAtField = Transaction.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(approvedTransaction, Instant.now().minus(Duration.ofDays(91)));

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.refund("txn_abc123", refundRequest, merchantId));
        assertEquals("REFUND_WINDOW_EXPIRED", ex.getMessage());
    }

    @Test
    void refund_whenPartialRefundSucceeds_returnsCompleted() {
        var merchantId = approvedTransaction.getMerchantId();
        var partialRequest = new RefundRequest(3000L, RefundReason.DUPLICATE,
            UUID.randomUUID(), UUID.randomUUID());

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByTransactionIdOrderByCreatedAtDesc("txn_abc123")).thenReturn(List.of());
        when(mpGateway.refundPayment(123456L, 3000L)).thenReturn(
            new MercadoPagoGateway.RefundResult(true, 101L));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.refund("txn_abc123", partialRequest, merchantId);

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        assertFalse(response.isFullRefund());
        assertEquals(3000L, response.amountInCents());
        verify(transactionRepository).save(argThat(tx ->
            tx.getStatus() == TransactionStatus.PARTIALLY_REFUNDED &&
            tx.getRefundedAmountInCents() == 3000L
        ));
    }

    @Test
    void refund_whenPartialRefundEqualsTotal_setsFullyRefunded() {
        var merchantId = approvedTransaction.getMerchantId();
        var fullAmountRequest = new RefundRequest(10000L, RefundReason.FRAUD,
            UUID.randomUUID(), UUID.randomUUID());

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(mpGateway.refundPayment(eq(123456L), isNull())).thenReturn(
            new MercadoPagoGateway.RefundResult(true, 202L));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.refund("txn_abc123", fullAmountRequest, merchantId);

        assertNotNull(response);
        assertTrue(response.isFullRefund());
        verify(transactionRepository).save(argThat(tx ->
            tx.getStatus() == TransactionStatus.FULLY_REFUNDED &&
            tx.getRefundedAmountInCents() == 10000L
        ));
    }

    @Test
    void refund_whenMpRefundFails_setsFailedStatus() {
        var merchantId = approvedTransaction.getMerchantId();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(mpGateway.refundPayment(123456L, null)).thenReturn(
            new MercadoPagoGateway.RefundResult(false, null));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.refund("txn_abc123", refundRequest, merchantId);

        assertNotNull(response);
        assertEquals("FAILED", response.status());
        verify(eventProducer, never()).publishRefunded(any());
    }

    @Test
    void refund_whenMpRefundFails_doesNotUpdateTransactionStatus() {
        var merchantId = approvedTransaction.getMerchantId();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(mpGateway.refundPayment(123456L, null)).thenReturn(
            new MercadoPagoGateway.RefundResult(false, null));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.refund("txn_abc123", refundRequest, merchantId);

        verify(transactionRepository).save(argThat(tx ->
            tx.getStatus() == TransactionStatus.APPROVED
        ));
    }

    @Test
    void refund_whenPartiallyRefundedStatus_canRefundRemaining() {
        var merchantId = approvedTransaction.getMerchantId();
        approvedTransaction.setStatus(TransactionStatus.PARTIALLY_REFUNDED);
        approvedTransaction.setRefundedAmountInCents(5000L);

        var fullAmountRequest = new RefundRequest(5000L, RefundReason.PRODUCT_NOT_DELIVERED,
            UUID.randomUUID(), UUID.randomUUID());

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByTransactionIdOrderByCreatedAtDesc("txn_abc123")).thenReturn(List.of());
        when(mpGateway.refundPayment(123456L, 5000L)).thenReturn(
            new MercadoPagoGateway.RefundResult(true, 303L));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.refund("txn_abc123", fullAmountRequest, merchantId);

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        verify(transactionRepository).save(argThat(tx ->
            tx.getStatus() == TransactionStatus.FULLY_REFUNDED
        ));
    }

    @Test
    void refund_whenAmountExceedsWithExistingPartialRefunds_throwsException() {
        var merchantId = approvedTransaction.getMerchantId();
        var partialRequest = new RefundRequest(6000L, RefundReason.CUSTOMER_REQUEST,
            UUID.randomUUID(), UUID.randomUUID());

        var existingRefund = new Refund("ref_1", "txn_abc123", 5000L,
            false, RefundReason.CUSTOMER_REQUEST, UUID.randomUUID(),
            UUID.randomUUID(), "COMPLETED");

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByTransactionIdOrderByCreatedAtDesc("txn_abc123"))
            .thenReturn(List.of(existingRefund));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.refund("txn_abc123", partialRequest, merchantId));
        assertEquals("AMOUNT_EXCEEDS_ORIGINAL", ex.getMessage());
    }

    @Test
    void refund_whenNullRefundedAmountInCents_usesRefundAmount() {
        var merchantId = approvedTransaction.getMerchantId();
        approvedTransaction.setRefundedAmountInCents(null);

        var partialRequest = new RefundRequest(5000L, RefundReason.CUSTOMER_REQUEST,
            UUID.randomUUID(), UUID.randomUUID());

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByTransactionIdOrderByCreatedAtDesc("txn_abc123")).thenReturn(List.of());
        when(mpGateway.refundPayment(123456L, 5000L)).thenReturn(
            new MercadoPagoGateway.RefundResult(true, 404L));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.refund("txn_abc123", partialRequest, merchantId);

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        verify(transactionRepository).save(argThat(tx ->
            tx.getRefundedAmountInCents() == 5000L
        ));
    }

    @Test
    void refund_setsEstimatedArrivalDaysCorrectly() {
        var merchantId = approvedTransaction.getMerchantId();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(mpGateway.refundPayment(123456L, null)).thenReturn(
            new MercadoPagoGateway.RefundResult(true, 505L));
        when(refundRepository.save(any())).thenAnswer(i -> {
            var r = (Refund) i.getArgument(0);
            assertEquals(10, r.getEstimatedArrivalDays());
            return r;
        });

        service.refund("txn_abc123", refundRequest, merchantId);
    }

    @Test
    void refund_partialRefund_setsEstimatedArrivalDaysTo7() {
        var merchantId = approvedTransaction.getMerchantId();
        var partialRequest = new RefundRequest(3000L, RefundReason.DUPLICATE,
            UUID.randomUUID(), UUID.randomUUID());

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByTransactionId("txn_abc123")).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByTransactionIdOrderByCreatedAtDesc("txn_abc123")).thenReturn(List.of());
        when(mpGateway.refundPayment(123456L, 3000L)).thenReturn(
            new MercadoPagoGateway.RefundResult(true, 606L));
        when(refundRepository.save(any())).thenAnswer(i -> {
            var r = (Refund) i.getArgument(0);
            assertEquals(7, r.getEstimatedArrivalDays());
            return r;
        });

        service.refund("txn_abc123", partialRequest, merchantId);
    }
}
