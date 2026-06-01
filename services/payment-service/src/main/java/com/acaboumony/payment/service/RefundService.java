package com.acaboumony.payment.service;

import com.acaboumony.payment.client.MercadoPagoGateway;
import com.acaboumony.payment.domain.entity.Refund;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.RefundReason;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.RefundRequest;
import com.acaboumony.payment.dto.response.RefundResponse;
import com.acaboumony.payment.event.TransactionRefundedEvent;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.repository.RefundRepository;
import com.acaboumony.payment.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    static final long REFUND_WINDOW_DAYS = 90;
    static final Set<TransactionStatus> REFUNDABLE_STATUSES = Set.of(
        TransactionStatus.APPROVED,
        TransactionStatus.PARTIALLY_REFUNDED
    );

    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;
    private final StringRedisTemplate redis;
    private final MercadoPagoGateway mpGateway;
    private final TransactionEventProducer eventProducer;

    public RefundService(TransactionRepository transactionRepository,
                         RefundRepository refundRepository,
                         StringRedisTemplate redis,
                         MercadoPagoGateway mpGateway,
                         TransactionEventProducer eventProducer) {
        this.transactionRepository = transactionRepository;
        this.refundRepository = refundRepository;
        this.redis = redis;
        this.mpGateway = mpGateway;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public RefundResponse refund(String transactionId, RefundRequest request, UUID merchantId) {
        var idempotencyKey = "idempotency:refund:" + request.idempotencyKey();
        Boolean alreadyProcessed = redis.opsForValue().setIfAbsent(idempotencyKey, "PENDING", Duration.ofHours(24));
        if (Boolean.FALSE.equals(alreadyProcessed)) {
            var existing = refundRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
            throw new IllegalArgumentException("DUPLICATE_IDEMPOTENCY_KEY");
        }

        var transaction = transactionRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("TRANSACTION_NOT_FOUND"));

        if (!transaction.getMerchantId().equals(merchantId)) {
            throw new IllegalArgumentException("INSUFFICIENT_PERMISSIONS");
        }

        if (!REFUNDABLE_STATUSES.contains(transaction.getStatus())) {
            throw new IllegalArgumentException("TRANSACTION_NOT_REFUNDABLE");
        }

        if (transaction.getStatus() == TransactionStatus.FULLY_REFUNDED) {
            throw new IllegalArgumentException("ALREADY_FULLY_REFUNDED");
        }

        if (transaction.getCreatedAt() != null &&
            Duration.between(transaction.getCreatedAt(), Instant.now()).toDays() >= REFUND_WINDOW_DAYS) {
            throw new IllegalArgumentException("REFUND_WINDOW_EXPIRED");
        }

        var refundAmount = request.amountInCents() != null
            ? request.amountInCents() : transaction.getAmountInCents();
        var isFullRefund = refundAmount.equals(transaction.getAmountInCents());

        if (request.amountInCents() != null && !isFullRefund) {
            var refundedSoFar = refundRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId)
                .stream().mapToLong(Refund::getAmountInCents).sum();
            if (refundedSoFar + refundAmount > transaction.getAmountInCents()) {
                throw new IllegalArgumentException("AMOUNT_EXCEEDS_ORIGINAL");
            }
            isFullRefund = false;
        }

        var refundId = "ref_" + UUID.randomUUID().toString().substring(0, 8);

        var mpRefund = mpGateway.refundPayment(transaction.getMpPaymentId(),
            isFullRefund ? null : refundAmount);

        var status = mpRefund.success() ? "COMPLETED" : "FAILED";

        var refund = new Refund(refundId, transactionId, refundAmount,
            isFullRefund, request.reason(), request.requestedBy(),
            request.idempotencyKey(), status);
        refund.setEstimatedArrivalDays(isFullRefund ? 10 : 7);
        refundRepository.save(refund);

        if (mpRefund.success()) {
            var newRefundedTotal = transaction.getRefundedAmountInCents() != null
                ? transaction.getRefundedAmountInCents() + refundAmount : refundAmount;
            if (newRefundedTotal >= transaction.getAmountInCents()) {
                transaction.setStatus(TransactionStatus.FULLY_REFUNDED);
                transaction.setRefundedAmountInCents(transaction.getAmountInCents());
            } else {
                transaction.setStatus(TransactionStatus.PARTIALLY_REFUNDED);
                transaction.setRefundedAmountInCents(newRefundedTotal);
            }
        }
        transactionRepository.save(transaction);

        if (mpRefund.success()) {
            var refundedEvent = new TransactionRefundedEvent(
                refundId, transactionId, transaction.getOrderId(),
                null, refundAmount, isFullRefund,
                request.reason().name(), refund.getEstimatedArrivalDays(),
                Instant.now()
            );
            eventProducer.publishRefunded(refundedEvent);
        }

        log.info("Refund {} for transaction {}: {}", refundId, transactionId, status);
        return toResponse(refund);
    }

    private RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
            refund.getRefundId(), refund.getTransactionId(),
            refund.getAmountInCents(), refund.getIsFullRefund(),
            refund.getReason().name(), refund.getStatus(),
            refund.getProcessedAt()
        );
    }
}
