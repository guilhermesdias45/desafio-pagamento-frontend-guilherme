package com.acaboumony.payment.service;

import com.acaboumony.payment.client.mp.MercadoPagoGateway;
import com.acaboumony.payment.client.mp.MpRefundResult;
import com.acaboumony.payment.domain.entity.AuditLog;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles refund processing for approved transactions.
 * Enforces: refund window (90 days), authorization, idempotency, amount limits.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private static final long REFUND_WINDOW_DAYS = 90;

    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;
    private final AuditLogRepository auditLogRepository;
    private final MercadoPagoGateway mercadoPagoGateway;
    private final TransactionEventProducer eventProducer;

    public RefundService(
            TransactionRepository transactionRepository,
            RefundRepository refundRepository,
            AuditLogRepository auditLogRepository,
            MercadoPagoGateway mercadoPagoGateway,
            TransactionEventProducer eventProducer
    ) {
        this.transactionRepository = transactionRepository;
        this.refundRepository = refundRepository;
        this.auditLogRepository = auditLogRepository;
        this.mercadoPagoGateway = mercadoPagoGateway;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public RefundResult refundTransaction(
            String transactionId,
            RefundRequest request,
            UUID requestingUserId,
            UUID requestingMerchantId,
            String requestingRole
    ) {
        // 1. Find transaction
        Transaction tx = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        // 2. Check transaction is refundable
        if (tx.getStatus() != TransactionStatus.APPROVED && tx.getStatus() != TransactionStatus.PARTIALLY_REFUNDED) {
            throw new TransactionNotRefundableException(tx.getStatus().name());
        }

        // 3. Check refund window (90 days)
        Instant cutoff = Instant.now().minus(REFUND_WINDOW_DAYS, ChronoUnit.DAYS);
        if (tx.getCreatedAt().isBefore(cutoff)) {
            throw new RefundWindowExpiredException();
        }

        // 4. Authorization: only the merchant who owns the transaction or ADMIN can refund
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requestingRole);
        boolean isMerchantOwner = requestingMerchantId != null && requestingMerchantId.equals(tx.getMerchantId());
        if (!isAdmin && !isMerchantOwner) {
            throw new InsufficientPermissionsException();
        }

        // 5. Idempotency check for refund
        Optional<Refund> existingRefund = refundRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingRefund.isPresent()) {
            Refund r = existingRefund.get();
            log.info("Returning cached refund for idempotencyKey={}", request.idempotencyKey());
            return new RefundResult.Success(r.getId().toString(), r.getAmountInCents(), r.getStatus().name());
        }

        // 6. Calculate already refunded amount
        Long alreadyRefunded = refundRepository.sumAmountByTransactionIdAndStatus(transactionId, RefundStatus.COMPLETED);
        if (alreadyRefunded == null) alreadyRefunded = 0L;

        long remaining = tx.getAmountInCents() - alreadyRefunded;

        // 7. Check not already fully refunded
        if (remaining <= 0) {
            throw new AlreadyFullyRefundedException();
        }

        // 8. Determine refund amount
        long refundAmount = request.amountInCents() != null ? request.amountInCents() : remaining;

        // 9. Check refund amount ≤ remaining
        if (refundAmount > remaining) {
            throw new AmountExceedsOriginalException();
        }

        // 10. Call MercadoPago refund
        Long mpAmountParam = request.amountInCents() != null ? refundAmount : null;
        MpRefundResult mpResult = mercadoPagoGateway.refundPayment(tx.getMpPaymentId(), mpAmountParam);

        // 11. Parse refund reason
        RefundReason refundReason = parseReason(request.reason());

        // 12. Save Refund entity
        Refund refund = new Refund();
        refund.setTransactionId(transactionId);
        refund.setAmountInCents(refundAmount);
        refund.setReason(refundReason);
        refund.setRequestedBy(requestingUserId);
        refund.setIdempotencyKey(request.idempotencyKey());

        boolean fullRefund = refundAmount >= tx.getAmountInCents();

        if (mpResult instanceof MpRefundResult.Success success) {
            refund.setStatus(RefundStatus.COMPLETED);
            refund.setMpRefundId(success.mpRefundId());
        } else {
            refund.setStatus(RefundStatus.FAILED);
        }

        refundRepository.save(refund);

        // 13. Update Transaction status
        long newTotalRefunded = alreadyRefunded + refundAmount;
        if (newTotalRefunded >= tx.getAmountInCents()) {
            tx.setStatus(TransactionStatus.FULLY_REFUNDED);
            fullRefund = true;
        } else {
            tx.setStatus(TransactionStatus.PARTIALLY_REFUNDED);
            fullRefund = false;
        }
        transactionRepository.save(tx);

        // 14. Audit log
        auditLog(transactionId, "TRANSACTION_REFUNDED",
                "refundId=" + refund.getId() + " amount=" + refundAmount + " fullRefund=" + fullRefund);

        // 15. Publish Kafka event
        eventProducer.publishTransactionRefunded(transactionId, tx.getOrderId(), tx.getCustomerId(), refundAmount, fullRefund);

        log.info("Refund completed: transactionId={} refundId={} amount={} fullRefund={}",
                transactionId, refund.getId(), refundAmount, fullRefund);

        return new RefundResult.Success(refund.getId().toString(), refundAmount, refund.getStatus().name());
    }

    private RefundReason parseReason(String reason) {
        try {
            return RefundReason.valueOf(reason.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RefundReason.CUSTOMER_REQUEST;
        }
    }

    private void auditLog(String transactionId, String eventType, String details) {
        AuditLog logEntry = new AuditLog();
        logEntry.setTransactionId(transactionId);
        logEntry.setEventType(eventType);
        logEntry.setDetails(details);
        auditLogRepository.save(logEntry);
    }
}
