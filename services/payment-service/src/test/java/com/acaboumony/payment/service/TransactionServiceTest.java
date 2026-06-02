package com.acaboumony.payment.service;

import com.acaboumony.payment.client.fraud.FraudAnalysisRequest;
import com.acaboumony.payment.client.fraud.FraudScoreResponse;
import com.acaboumony.payment.client.fraud.FraudServiceClient;
import com.acaboumony.payment.client.mp.*;
import com.acaboumony.payment.client.order.InternalOrderResponse;
import com.acaboumony.payment.client.order.OrderServiceClient;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.TransactionResponse;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.exception.*;
import com.acaboumony.payment.repository.AuditLogRepository;
import com.acaboumony.payment.repository.RefundRepository;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.result.TransactionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock RefundRepository refundRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock IdempotencyService idempotencyService;
    @Mock RateLimitService rateLimitService;
    @Mock FraudServiceClient fraudServiceClient;
    @Mock OrderServiceClient orderServiceClient;
    @Mock MercadoPagoGateway mercadoPagoGateway;
    @Mock TransactionEventProducer eventProducer;

    TransactionService service;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

    private TransactionRequest validRequest;
    private InternalOrderResponse pendingOrder;
    private FraudScoreResponse approveScore;

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                transactionRepository, refundRepository, auditLogRepository,
                idempotencyService, rateLimitService, fraudServiceClient,
                orderServiceClient, mercadoPagoGateway, eventProducer
        );

        validRequest = new TransactionRequest(
                5000L, "BRL", ORDER_ID, "abcdef1234567890abcdef1234567890",
                "visa", 1, IDEMPOTENCY_KEY
        );

        pendingOrder = new InternalOrderResponse(ORDER_ID, "PENDING", 5000L, MERCHANT_ID, CUSTOMER_ID);
        approveScore = new FraudScoreResponse(30, "APPROVE", List.of(), 50L);
    }

    @Test
    void CE001_returns_cached_result_on_duplicate_idempotency_key() {
        // Given
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        TransactionResponse cached = new TransactionResponse("txn_abc123", 999L, ORDER_ID, "APPROVED", 200L);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.of(cached));

        // When
        TransactionResult result = service.processTransaction(validRequest, CUSTOMER_ID);

        // Then
        assertThat(result).isInstanceOf(TransactionResult.Success.class);
        TransactionResult.Success success = (TransactionResult.Success) result;
        assertThat(success.transactionId()).isEqualTo("txn_abc123");
        assertThat(success.mpPaymentId()).isEqualTo(999L);

        // Should not call order service, fraud, or MP
        verifyNoInteractions(orderServiceClient, fraudServiceClient, mercadoPagoGateway);
    }

    @Test
    void CE002_throws_invalid_currency_for_non_brl() {
        // Currency validation at service layer — only BRL accepted
        TransactionRequest usdRequest = new TransactionRequest(
                5000L, "USD", ORDER_ID, "abcdef1234567890abcdef1234567890",
                "visa", 1, IDEMPOTENCY_KEY
        );
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processTransaction(usdRequest, CUSTOMER_ID))
                .isInstanceOf(InvalidCurrencyException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("INVALID_CURRENCY"));
    }

    @Test
    void CE003_throws_card_declined_when_mp_rejects() {
        // Given
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenReturn(pendingOrder);
        when(fraudServiceClient.analyzeTransaction(any())).thenReturn(approveScore);
        when(mercadoPagoGateway.processPayment(any())).thenReturn(new MpPaymentResult.Rejected("cc_rejected_card_type_not_allowed"));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(validRequest, CUSTOMER_ID))
                .isInstanceOf(CardDeclinedException.class)
                .satisfies(e -> {
                    assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("CARD_DECLINED");
                    assertThat(((PaymentServiceException) e).isRetryable()).isTrue();
                });

        // Failed transaction should be saved for audit
        verify(transactionRepository).save(argThat(tx -> tx.getStatus() == TransactionStatus.DECLINED));
        verify(eventProducer).publishTransactionFailed(any(), eq(ORDER_ID), eq(CUSTOMER_ID), eq("CARD_DECLINED"));
    }

    @Test
    void CE004_throws_fraud_detected_when_score_above_90() {
        // Given
        FraudScoreResponse blockScore = new FraudScoreResponse(95, "BLOCK", List.of("UNUSUAL_AMOUNT"), 80L);
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenReturn(pendingOrder);
        when(fraudServiceClient.analyzeTransaction(any())).thenReturn(blockScore);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(validRequest, CUSTOMER_ID))
                .isInstanceOf(FraudDetectedException.class)
                .satisfies(e -> {
                    assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("SUSPECTED_FRAUD");
                    assertThat(((PaymentServiceException) e).isRetryable()).isFalse();
                });

        // Verify MP was NOT called
        verifyNoInteractions(mercadoPagoGateway);
        verify(transactionRepository).save(argThat(tx -> tx.getStatus() == TransactionStatus.SUSPECTED_FRAUD));
    }

    @Test
    void CE005_throws_gateway_timeout_on_mp_timeout() {
        // Given
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenReturn(pendingOrder);
        when(fraudServiceClient.analyzeTransaction(any())).thenReturn(approveScore);
        when(mercadoPagoGateway.processPayment(any())).thenReturn(new MpPaymentResult.Timeout());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(validRequest, CUSTOMER_ID))
                .isInstanceOf(MercadoPagoTimeoutException.class)
                .satisfies(e -> {
                    assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("MP_GATEWAY_TIMEOUT");
                    assertThat(((PaymentServiceException) e).isRetryable()).isTrue();
                });

        verify(eventProducer).publishTransactionFailed(any(), eq(ORDER_ID), eq(CUSTOMER_ID), eq("MP_GATEWAY_TIMEOUT"));
    }

    @Test
    void CE006_throws_order_not_pending_when_order_already_paid() {
        // Given
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenThrow(new OrderNotPendingException("PAID"));

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(validRequest, CUSTOMER_ID))
                .isInstanceOf(OrderNotPendingException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("ORDER_NOT_PENDING"));

        verifyNoInteractions(fraudServiceClient, mercadoPagoGateway, eventProducer);
    }

    @Test
    void CE007_throws_rate_limit_exceeded() {
        // Given
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(validRequest, CUSTOMER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> {
                    assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
                    assertThat(((PaymentServiceException) e).isRetryable()).isTrue();
                });

        verifyNoInteractions(idempotencyService, orderServiceClient, fraudServiceClient, mercadoPagoGateway);
    }

    @Test
    void success_approved_saves_transaction_and_publishes_kafka_event() {
        // Given
        MpPaymentResult.Approved approved = new MpPaymentResult.Approved(123456789L, "accredited", "visa", "4242");
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenReturn(pendingOrder);
        when(fraudServiceClient.analyzeTransaction(any())).thenReturn(approveScore);
        when(mercadoPagoGateway.processPayment(any())).thenReturn(approved);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        TransactionResult result = service.processTransaction(validRequest, CUSTOMER_ID);

        // Then
        assertThat(result).isInstanceOf(TransactionResult.Success.class);
        TransactionResult.Success success = (TransactionResult.Success) result;
        assertThat(success.transactionId()).startsWith("txn_");
        assertThat(success.mpPaymentId()).isEqualTo(123456789L);
        assertThat(success.orderId()).isEqualTo(ORDER_ID);

        // Verify transaction was saved with correct status
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction savedTx = txCaptor.getValue();
        assertThat(savedTx.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(savedTx.getAmountInCents()).isEqualTo(5000L);
        assertThat(savedTx.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(savedTx.getMerchantId()).isEqualTo(MERCHANT_ID);
        // Card token must NOT be stored (PCI DSS)
        assertThat(savedTx.getCardLastFour()).isEqualTo("4242");

        // Verify Kafka event published
        verify(eventProducer).publishTransactionCompleted(any(), eq(ORDER_ID), eq(CUSTOMER_ID), eq(5000L));
    }

    @Test
    void success_stores_idempotency_key_after_approval() {
        // Given
        MpPaymentResult.Approved approved = new MpPaymentResult.Approved(111L, "accredited", "master", "1111");
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenReturn(pendingOrder);
        when(fraudServiceClient.analyzeTransaction(any())).thenReturn(approveScore);
        when(mercadoPagoGateway.processPayment(any())).thenReturn(approved);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        service.processTransaction(validRequest, CUSTOMER_ID);

        // Then
        verify(idempotencyService).store(eq(IDEMPOTENCY_KEY), any(TransactionResponse.class));
    }

    @Test
    void fraud_block_saves_declined_transaction_for_audit() {
        // Given
        FraudScoreResponse blockScore = new FraudScoreResponse(92, "BLOCK", List.of("HIGH_RISK"), 60L);
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenReturn(pendingOrder);
        when(fraudServiceClient.analyzeTransaction(any())).thenReturn(blockScore);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(validRequest, CUSTOMER_ID))
                .isInstanceOf(FraudDetectedException.class);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.SUSPECTED_FRAUD);
        assertThat(txCaptor.getValue().getErrorCode()).isEqualTo("SUSPECTED_FRAUD");
    }

    @Test
    void card_declined_saves_transaction_and_publishes_failed_event() {
        // Given
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenReturn(pendingOrder);
        when(fraudServiceClient.analyzeTransaction(any())).thenReturn(approveScore);
        when(mercadoPagoGateway.processPayment(any())).thenReturn(new MpPaymentResult.Rejected("cc_rejected_bad_filled_card_number"));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(validRequest, CUSTOMER_ID))
                .isInstanceOf(CardDeclinedException.class);

        verify(transactionRepository).save(argThat(tx -> tx.getStatus() == TransactionStatus.DECLINED));
        verify(eventProducer).publishTransactionFailed(any(), eq(ORDER_ID), eq(CUSTOMER_ID), eq("CARD_DECLINED"));
    }

    @Test
    void insufficient_funds_throws_correct_exception() {
        // Given
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(ORDER_ID)).thenReturn(pendingOrder);
        when(fraudServiceClient.analyzeTransaction(any())).thenReturn(approveScore);
        when(mercadoPagoGateway.processPayment(any())).thenReturn(new MpPaymentResult.Rejected("cc_rejected_insufficient_amount"));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(validRequest, CUSTOMER_ID))
                .isInstanceOf(InsufficientFundsException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS"));
    }

    @Test
    void currency_validation_rejects_usd() {
        // Given
        TransactionRequest usdRequest = new TransactionRequest(
                10000L, "USD", ORDER_ID, "abcdef1234567890abcdef1234567890",
                "visa", 1, IDEMPOTENCY_KEY
        );
        when(rateLimitService.isAllowed(CUSTOMER_ID)).thenReturn(true);
        when(idempotencyService.getExisting(any())).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> service.processTransaction(usdRequest, CUSTOMER_ID))
                .isInstanceOf(InvalidCurrencyException.class)
                .satisfies(e -> assertThat(((PaymentServiceException) e).getErrorCode()).isEqualTo("INVALID_CURRENCY"));

        verifyNoInteractions(orderServiceClient, fraudServiceClient, mercadoPagoGateway);
    }
}
