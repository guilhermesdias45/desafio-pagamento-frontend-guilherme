package com.acaboumony.payment.service;

import com.acaboumony.payment.client.mp.MercadoPagoGateway;
import com.acaboumony.payment.client.mp.MpRefundResult;
import com.acaboumony.payment.domain.entity.Refund;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.RefundReason;
import com.acaboumony.payment.domain.enums.RefundStatus;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.RefundRequest;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.exception.*;
import com.acaboumony.payment.repository.AuditLogRepository;
import com.acaboumony.payment.repository.RefundRepository;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.result.RefundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock RefundRepository refundRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock MercadoPagoGateway mercadoPagoGateway;
    @Mock TransactionEventProducer eventProducer;

    RefundService refundService;

    private static final String TX_ID = "txn_abc123456789";
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

    private Transaction approvedTransaction;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                transactionRepository, refundRepository, auditLogRepository,
                mercadoPagoGateway, eventProducer
        );

        approvedTransaction = buildTransaction(TX_ID, 10000L, TransactionStatus.APPROVED, Instant.now());
    }

    private Transaction buildTransaction(String txId, long amount, TransactionStatus status, Instant createdAt) {
        Transaction tx = new Transaction();
        tx.setTransactionId(txId);
        tx.setMpPaymentId(999L);
        tx.setCustomerId(CUSTOMER_ID);
        tx.setMerchantId(MERCHANT_ID);
        tx.setOrderId(ORDER_ID);
        tx.setStatus(status);
        tx.setAmountInCents(amount);
        tx.setCurrency("BRL");
        tx.setPaymentMethodId("visa");
        tx.setInstallments(1);
        tx.setIdempotencyKey(UUID.randomUUID());
        tx.setCreatedAt(createdAt);
        tx.setUpdatedAt(createdAt);
        return tx;
    }

    private RefundRequest buildRefundRequest(Long amount, String reason) {
        return new RefundRequest(amount, reason, IDEMPOTENCY_KEY);
    }

    @Test
    void refund_success_full_amount() {
        // Given
        when(transactionRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(refundRepository.sumAmountByTransactionIdAndStatus(eq(TX_ID), eq(RefundStatus.COMPLETED))).thenReturn(0L);
        when(mercadoPagoGateway.refundPayment(eq(999L), isNull())).thenReturn(new MpRefundResult.Success(77L));
        when(refundRepository.save(any())).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When — full refund (no amount specified)
        RefundResult result = refundService.refundTransaction(TX_ID, buildRefundRequest(null, "CUSTOMER_REQUEST"),
                USER_ID, MERCHANT_ID, "MERCHANT");

        // Then
        assertThat(result).isInstanceOf(RefundResult.Success.class);
        RefundResult.Success success = (RefundResult.Success) result;
        assertThat(success.amountInCents()).isEqualTo(10000L);
        assertThat(success.status()).isEqualTo("COMPLETED");

        // Transaction should be FULLY_REFUNDED
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.FULLY_REFUNDED);

        verify(eventProducer).publishTransactionRefunded(eq(TX_ID), eq(ORDER_ID), eq(CUSTOMER_ID), eq(10000L), eq(true));
    }

    @Test
    void refund_success_partial_amount() {
        // Given
        when(transactionRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(refundRepository.sumAmountByTransactionIdAndStatus(eq(TX_ID), eq(RefundStatus.COMPLETED))).thenReturn(0L);
        when(mercadoPagoGateway.refundPayment(eq(999L), eq(3000L))).thenReturn(new MpRefundResult.Success(88L));
        when(refundRepository.save(any())).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        RefundResult result = refundService.refundTransaction(TX_ID, buildRefundRequest(3000L, "DUPLICATE"),
                USER_ID, MERCHANT_ID, "MERCHANT");

        // Then
        assertThat(result).isInstanceOf(RefundResult.Success.class);
        RefundResult.Success success = (RefundResult.Success) result;
        assertThat(success.amountInCents()).isEqualTo(3000L);

        // Transaction should be PARTIALLY_REFUNDED
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.PARTIALLY_REFUNDED);

        verify(eventProducer).publishTransactionRefunded(eq(TX_ID), eq(ORDER_ID), eq(CUSTOMER_ID), eq(3000L), eq(false));
    }

    @Test
    void throws_when_transaction_not_found() {
        when(transactionRepository.findByTransactionId("txn_unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.refundTransaction("txn_unknown",
                buildRefundRequest(null, "CUSTOMER_REQUEST"), USER_ID, MERCHANT_ID, "MERCHANT"))
                .isInstanceOf(TransactionNotFoundException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void throws_when_transaction_not_refundable() {
        // DECLINED transactions cannot be refunded
        Transaction declined = buildTransaction(TX_ID, 10000L, TransactionStatus.DECLINED, Instant.now());
        when(transactionRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(declined));

        assertThatThrownBy(() -> refundService.refundTransaction(TX_ID,
                buildRefundRequest(null, "CUSTOMER_REQUEST"), USER_ID, MERCHANT_ID, "MERCHANT"))
                .isInstanceOf(TransactionNotRefundableException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("TRANSACTION_NOT_REFUNDABLE"));
    }

    @Test
    void throws_when_refund_window_expired_90_days() {
        // Transaction created 91 days ago
        Instant oldDate = Instant.now().minus(91, ChronoUnit.DAYS);
        Transaction oldTx = buildTransaction(TX_ID, 10000L, TransactionStatus.APPROVED, oldDate);
        when(transactionRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(oldTx));

        assertThatThrownBy(() -> refundService.refundTransaction(TX_ID,
                buildRefundRequest(null, "CUSTOMER_REQUEST"), USER_ID, MERCHANT_ID, "MERCHANT"))
                .isInstanceOf(RefundWindowExpiredException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("REFUND_WINDOW_EXPIRED"));
    }

    @Test
    void throws_when_amount_exceeds_original() {
        when(transactionRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(refundRepository.sumAmountByTransactionIdAndStatus(eq(TX_ID), eq(RefundStatus.COMPLETED))).thenReturn(0L);

        // Try to refund more than original (10000 cents)
        assertThatThrownBy(() -> refundService.refundTransaction(TX_ID,
                buildRefundRequest(15000L, "CUSTOMER_REQUEST"), USER_ID, MERCHANT_ID, "MERCHANT"))
                .isInstanceOf(AmountExceedsOriginalException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("AMOUNT_EXCEEDS_ORIGINAL"));
    }

    @Test
    void throws_when_already_fully_refunded() {
        // Transaction already fully refunded (alreadyRefunded == amountInCents)
        when(transactionRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(approvedTransaction));
        when(refundRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(refundRepository.sumAmountByTransactionIdAndStatus(eq(TX_ID), eq(RefundStatus.COMPLETED))).thenReturn(10000L);

        assertThatThrownBy(() -> refundService.refundTransaction(TX_ID,
                buildRefundRequest(null, "CUSTOMER_REQUEST"), USER_ID, MERCHANT_ID, "MERCHANT"))
                .isInstanceOf(AlreadyFullyRefundedException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("ALREADY_FULLY_REFUNDED"));
    }

    @Test
    void throws_when_insufficient_permissions() {
        when(transactionRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(approvedTransaction));

        UUID wrongMerchantId = UUID.randomUUID();
        assertThatThrownBy(() -> refundService.refundTransaction(TX_ID,
                buildRefundRequest(null, "CUSTOMER_REQUEST"), USER_ID, wrongMerchantId, "MERCHANT"))
                .isInstanceOf(InsufficientPermissionsException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void idempotency_returns_existing_refund() {
        when(transactionRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(approvedTransaction));

        Refund existingRefund = new Refund();
        existingRefund.setId(UUID.randomUUID());
        existingRefund.setAmountInCents(5000L);
        existingRefund.setStatus(RefundStatus.COMPLETED);
        existingRefund.setReason(RefundReason.CUSTOMER_REQUEST);
        existingRefund.setRequestedBy(USER_ID);
        existingRefund.setIdempotencyKey(IDEMPOTENCY_KEY);
        when(refundRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existingRefund));

        RefundResult result = refundService.refundTransaction(TX_ID,
                buildRefundRequest(5000L, "CUSTOMER_REQUEST"), USER_ID, MERCHANT_ID, "MERCHANT");

        assertThat(result).isInstanceOf(RefundResult.Success.class);
        RefundResult.Success success = (RefundResult.Success) result;
        assertThat(success.amountInCents()).isEqualTo(5000L);
        assertThat(success.status()).isEqualTo("COMPLETED");

        // Should NOT call MP or save anything
        verifyNoInteractions(mercadoPagoGateway, auditLogRepository, eventProducer);
        verify(transactionRepository, never()).save(any());
    }
}
