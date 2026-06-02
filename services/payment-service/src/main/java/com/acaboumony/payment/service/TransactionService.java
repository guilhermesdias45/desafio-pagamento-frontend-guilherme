package com.acaboumony.payment.service;

import com.acaboumony.payment.client.fraud.FraudAnalysisRequest;
import com.acaboumony.payment.client.fraud.FraudScoreResponse;
import com.acaboumony.payment.client.fraud.FraudServiceClient;
import com.acaboumony.payment.client.mp.*;
import com.acaboumony.payment.client.order.InternalOrderResponse;
import com.acaboumony.payment.client.order.OrderServiceClient;
import com.acaboumony.payment.domain.entity.AuditLog;
import com.acaboumony.payment.domain.entity.Refund;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.RefundSummary;
import com.acaboumony.payment.dto.response.TransactionDetailResponse;
import com.acaboumony.payment.dto.response.TransactionResponse;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.exception.*;
import com.acaboumony.payment.repository.AuditLogRepository;
import com.acaboumony.payment.repository.RefundRepository;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.result.TransactionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core payment transaction processing service.
 * Orchestrates: rate limiting → idempotency → order validation → fraud analysis → gateway → persistence → events.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;
    private final AuditLogRepository auditLogRepository;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;
    private final FraudServiceClient fraudServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final MercadoPagoGateway mercadoPagoGateway;
    private final TransactionEventProducer eventProducer;

    public TransactionService(
            TransactionRepository transactionRepository,
            RefundRepository refundRepository,
            AuditLogRepository auditLogRepository,
            IdempotencyService idempotencyService,
            RateLimitService rateLimitService,
            FraudServiceClient fraudServiceClient,
            OrderServiceClient orderServiceClient,
            MercadoPagoGateway mercadoPagoGateway,
            TransactionEventProducer eventProducer
    ) {
        this.transactionRepository = transactionRepository;
        this.refundRepository = refundRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyService = idempotencyService;
        this.rateLimitService = rateLimitService;
        this.fraudServiceClient = fraudServiceClient;
        this.orderServiceClient = orderServiceClient;
        this.mercadoPagoGateway = mercadoPagoGateway;
        this.eventProducer = eventProducer;
    }

    @Transactional(noRollbackFor = {
            FraudDetectedException.class,
            CardDeclinedException.class,
            InsufficientFundsException.class,
            MercadoPagoTimeoutException.class
    })
    public TransactionResult processTransaction(TransactionRequest request, UUID customerId) {
        long startTime = System.currentTimeMillis();

        // 1. Validate currency (only BRL)
        if (!"BRL".equals(request.currency())) {
            throw new InvalidCurrencyException(request.currency());
        }

        // 2. Rate limit check
        if (!rateLimitService.isAllowed(customerId)) {
            throw new RateLimitExceededException();
        }

        // 3. Idempotency check — return cached result if duplicate
        Optional<TransactionResponse> cached = idempotencyService.getExisting(request.idempotencyKey());
        if (cached.isPresent()) {
            TransactionResponse r = cached.get();
            log.info("Returning cached result for idempotencyKey={}", request.idempotencyKey());
            return new TransactionResult.Success(
                    r.transactionId(), r.mpPaymentId(), r.orderId(),
                    System.currentTimeMillis() - startTime);
        }

        // 4. Validate order exists and is PENDING (throws OrderNotFoundException / OrderNotPendingException)
        InternalOrderResponse order = orderServiceClient.getOrder(request.orderId());

        // 5. Fraud analysis
        String transactionId = generateTransactionId();
        FraudAnalysisRequest fraudRequest = new FraudAnalysisRequest(
                transactionId, customerId, request.amountInCents(),
                request.paymentMethodId(), "0.0.0.0"
        );
        FraudScoreResponse fraudScore = fraudServiceClient.analyzeTransaction(fraudRequest);

        if ("BLOCK".equals(fraudScore.decision())) {
            log.warn("Transaction blocked by fraud service: transactionId={}", transactionId);
            saveFailedTransaction(transactionId, request, customerId, order.merchantId(),
                    TransactionStatus.SUSPECTED_FRAUD, "SUSPECTED_FRAUD", fraudScore, startTime);
            auditLog(transactionId, "TRANSACTION_BLOCKED_FRAUD", "fraudDecision=BLOCK");
            throw new FraudDetectedException();
        }

        // 6. Call Mercado Pago
        MpPaymentRequest mpRequest = new MpPaymentRequest(
                request.amountInCents(), request.cardToken(), request.paymentMethodId(),
                request.installments() != null ? request.installments() : 1,
                "pagamento@acaboumony.com", request.orderId(), request.idempotencyKey()
        );

        MpPaymentResult mpResult = mercadoPagoGateway.processPayment(mpRequest);

        return switch (mpResult) {
            case MpPaymentResult.Approved approved -> {
                long processingTimeMs = System.currentTimeMillis() - startTime;
                saveApprovedTransaction(transactionId, request, customerId, order.merchantId(), approved, fraudScore, processingTimeMs);
                TransactionResponse txResponse = new TransactionResponse(
                        transactionId, approved.mpPaymentId(), request.orderId(), "APPROVED", processingTimeMs);
                idempotencyService.store(request.idempotencyKey(), txResponse);
                eventProducer.publishTransactionCompleted(transactionId, request.orderId(), customerId, request.amountInCents());
                auditLog(transactionId, "TRANSACTION_APPROVED", "status=APPROVED");
                log.info("Transaction approved: transactionId={} processingTimeMs={}", transactionId, processingTimeMs);
                yield new TransactionResult.Success(transactionId, approved.mpPaymentId(), request.orderId(), processingTimeMs);
            }
            case MpPaymentResult.Rejected rejected -> {
                long processingTimeMs = System.currentTimeMillis() - startTime;
                String errorCode = mapRejectedCode(rejected.statusDetail());
                saveFailedTransaction(transactionId, request, customerId, order.merchantId(),
                        TransactionStatus.DECLINED, errorCode, fraudScore, startTime);
                eventProducer.publishTransactionFailed(transactionId, request.orderId(), customerId, errorCode);
                auditLog(transactionId, "TRANSACTION_REJECTED", "statusDetail=" + rejected.statusDetail());
                log.warn("Transaction rejected: transactionId={} statusDetail={}", transactionId, rejected.statusDetail());
                if ("cc_rejected_insufficient_amount".equals(rejected.statusDetail())) {
                    throw new InsufficientFundsException();
                }
                throw new CardDeclinedException(rejected.statusDetail());
            }
            case MpPaymentResult.Timeout timeout -> {
                long processingTimeMs = System.currentTimeMillis() - startTime;
                eventProducer.publishTransactionFailed(transactionId, request.orderId(), customerId, "MP_GATEWAY_TIMEOUT");
                auditLog(transactionId, "TRANSACTION_TIMEOUT", "MP gateway timeout after " + processingTimeMs + "ms");
                log.error("Mercado Pago timeout: transactionId={}", transactionId);
                throw new MercadoPagoTimeoutException();
            }
        };
    }

    @Transactional(readOnly = true)
    public TransactionDetailResponse findByTransactionId(String transactionId) {
        Transaction tx = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        List<Refund> refunds = refundRepository.findByTransactionId(transactionId);
        List<RefundSummary> refundSummaries = refunds.stream()
                .map(r -> new RefundSummary(r.getId(), r.getAmountInCents(), r.getReason().name(), r.getStatus().name(), r.getCreatedAt()))
                .toList();

        return toDetailResponse(tx, refundSummaries);
    }

    @Transactional(readOnly = true)
    public Page<TransactionDetailResponse> findByCustomer(UUID customerId, Pageable pageable) {
        return transactionRepository.findByCustomerId(customerId, pageable)
                .map(tx -> {
                    List<Refund> refunds = refundRepository.findByTransactionId(tx.getTransactionId());
                    List<RefundSummary> summaries = refunds.stream()
                            .map(r -> new RefundSummary(r.getId(), r.getAmountInCents(), r.getReason().name(), r.getStatus().name(), r.getCreatedAt()))
                            .toList();
                    return toDetailResponse(tx, summaries);
                });
    }

    private Transaction saveApprovedTransaction(
            String transactionId,
            TransactionRequest request,
            UUID customerId,
            UUID merchantId,
            MpPaymentResult.Approved approved,
            FraudScoreResponse fraudScore,
            long processingTimeMs) {

        Transaction tx = new Transaction();
        tx.setTransactionId(transactionId);
        tx.setMpPaymentId(approved.mpPaymentId());
        tx.setCustomerId(customerId);
        tx.setMerchantId(merchantId);
        tx.setOrderId(request.orderId());
        tx.setStatus(TransactionStatus.APPROVED);
        tx.setAmountInCents(request.amountInCents());
        tx.setCurrency(request.currency());
        tx.setCardBrand(approved.cardBrand());
        tx.setCardLastFour(approved.cardLastFour());
        tx.setPaymentMethodId(request.paymentMethodId());
        tx.setInstallments(request.installments() != null ? request.installments() : 1);
        tx.setIdempotencyKey(request.idempotencyKey());
        tx.setFraudScore(fraudScore.score());
        tx.setFraudDecision(fraudScore.decision());
        tx.setProcessingTimeMs(processingTimeMs);
        return transactionRepository.save(tx);
    }

    private void saveFailedTransaction(
            String transactionId,
            TransactionRequest request,
            UUID customerId,
            UUID merchantId,
            TransactionStatus status,
            String errorCode,
            FraudScoreResponse fraudScore,
            long startTime) {

        Transaction tx = new Transaction();
        tx.setTransactionId(transactionId);
        tx.setCustomerId(customerId);
        tx.setMerchantId(merchantId);
        tx.setOrderId(request.orderId());
        tx.setStatus(status);
        tx.setAmountInCents(request.amountInCents());
        tx.setCurrency(request.currency());
        tx.setPaymentMethodId(request.paymentMethodId());
        tx.setInstallments(request.installments() != null ? request.installments() : 1);
        tx.setIdempotencyKey(request.idempotencyKey());
        tx.setFraudScore(fraudScore != null ? fraudScore.score() : null);
        tx.setFraudDecision(fraudScore != null ? fraudScore.decision() : null);
        tx.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        tx.setErrorCode(errorCode);
        transactionRepository.save(tx);
    }

    private void auditLog(String transactionId, String eventType, String details) {
        AuditLog log = new AuditLog();
        log.setTransactionId(transactionId);
        log.setEventType(eventType);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    private String generateTransactionId() {
        return "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String mapRejectedCode(String statusDetail) {
        return switch (statusDetail) {
            case "cc_rejected_insufficient_amount" -> "INSUFFICIENT_FUNDS";
            default -> "CARD_DECLINED";
        };
    }

    private TransactionDetailResponse toDetailResponse(Transaction tx, List<RefundSummary> refunds) {
        return new TransactionDetailResponse(
                tx.getTransactionId(),
                tx.getMpPaymentId(),
                tx.getStatus().name(),
                tx.getAmountInCents(),
                tx.getCurrency(),
                tx.getCardBrand(),
                tx.getCardLastFour(),
                tx.getPaymentMethodId(),
                tx.getOrderId(),
                tx.getCreatedAt(),
                tx.getUpdatedAt(),
                refunds,
                tx.getProcessingTimeMs()
        );
    }
}
