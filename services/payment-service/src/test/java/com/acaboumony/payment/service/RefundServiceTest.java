package com.acaboumony.payment.service;

import com.acaboumony.payment.client.MercadoPagoGateway;
import com.acaboumony.payment.domain.entity.Refund;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.RefundReason;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.RefundRequest;
import com.acaboumony.payment.dto.response.RefundResponse;
import com.acaboumony.payment.event.TransactionEventProducer;
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

    private RefundService service;
    private Transaction approvedTransaction;
    private RefundRequest refundRequest;

    @BeforeEach
    void setUp() {
        service = new RefundService(transactionRepository, refundRepository, redis,
            mpGateway, eventProducer);
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
}
