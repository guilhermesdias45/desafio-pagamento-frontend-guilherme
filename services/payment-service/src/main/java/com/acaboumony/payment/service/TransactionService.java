package com.acaboumony.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.acaboumony.payment.client.FraudServiceClient;
import com.acaboumony.payment.client.MercadoPagoGateway;
import com.acaboumony.payment.client.OrderServiceClient;
import com.acaboumony.payment.client.UserServiceClient;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.TransactionResponse;
import com.acaboumony.payment.dto.response.TransactionSummary;
import com.acaboumony.payment.domain.entity.AuditLog;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.event.TransactionCompletedEvent;
import com.acaboumony.payment.event.TransactionFailedEvent;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.mapper.TransactionMapper;
import com.acaboumony.payment.repository.AuditLogRepository;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.result.TransactionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final StringRedisTemplate redis;
    private final FraudServiceClient fraudClient;
    private final OrderServiceClient orderClient;
    private final UserServiceClient userClient;
    private final MercadoPagoGateway mpGateway;
    private final TransactionEventProducer eventProducer;
    private final TransactionMapper mapper;
    private final ObjectMapper objectMapper;
    private final Counter approvedCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;

    public TransactionService(TransactionRepository transactionRepository,
                              AuditLogRepository auditLogRepository,
                              StringRedisTemplate redis,
                              FraudServiceClient fraudClient,
                              OrderServiceClient orderClient,
                              UserServiceClient userClient,
                              MercadoPagoGateway mpGateway,
                              TransactionEventProducer eventProducer,
                              TransactionMapper mapper,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.redis = redis;
        this.fraudClient = fraudClient;
        this.orderClient = orderClient;
        this.userClient = userClient;
        this.mpGateway = mpGateway;
        this.eventProducer = eventProducer;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.approvedCounter = Counter.builder("payment.transactions.approved")
            .description("Approved transactions").register(meterRegistry);
        this.failedCounter = Counter.builder("payment.transactions.failed")
            .description("Failed transactions").register(meterRegistry);
        this.processingTimer = Timer.builder("payment.transactions.processing.time")
            .description("Transaction processing time").register(meterRegistry);
    }

    public TransactionResult processTransaction(TransactionRequest request, String customerEmail,
                                                 UUID merchantId, String ipAddress) {
        Instant start = Instant.now();

        if (!"BRL".equals(request.currency())) {
            return fail("INVALID_CURRENCY", "Only BRL is supported", false, start);
        }

        try {
            var rateLimitKey = "rate_limit:payment:" + request.customerId();
            var currentCount = redis.opsForValue().increment(rateLimitKey);
            if (currentCount != null && currentCount == 1) {
                redis.expire(rateLimitKey, Duration.ofMinutes(1));
            }
            if (currentCount != null && currentCount > 100) {
                log.warn("Rate limit exceeded for customer {}: {} requests in window", request.customerId(), currentCount);
                return fail("RATE_LIMIT_EXCEEDED", "Too many transactions. Try again later.", true, start);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limiting, skipping: {}", e.getMessage());
        }

        var idempotencyKey = "idempotency:payment:" + request.idempotencyKey();
        Boolean alreadyProcessed;
        try {
            alreadyProcessed = redis.opsForValue().setIfAbsent(idempotencyKey, "PENDING", Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency, processing without lock: {}", e.getMessage());
            alreadyProcessed = null;
        }
        if (Boolean.FALSE.equals(alreadyProcessed)) {
            var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                Transaction tx = existing.get();
                return new TransactionResult.Approved(
                    tx.getTransactionId(), tx.getMpPaymentId(),
                    tx.getOrderId(), Duration.between(start, Instant.now()).toMillis(), true);
            }
            return fail("DUPLICATE_IDEMPOTENCY_KEY", "Key already processed", false, start);
        }

        var transactionId = generateTransactionId();
        logAudit(transactionId, merchantId, "CREATED", null, ipAddress);

        var orderValidation = orderClient.validateOrder(request.orderId(), merchantId);
        if (!orderValidation.valid()) {
            safeRedisDelete(idempotencyKey);
            return fail(orderValidation.errorCode(), "Order validation failed", false, start);
        }

        var customerValidation = userClient.validateCustomer(request.customerId());
        if (!customerValidation.valid()) {
            safeRedisDelete(idempotencyKey);
            return fail(customerValidation.errorCode(), "Customer validation failed", false, start);
        }

        var fraudRequest = new FraudServiceClient.FraudAnalysisRequest(
            transactionId, request.customerId(), merchantId, request.amountInCents(),
            request.paymentMethodId(), ipAddress, null, null, null
        );
        var fraudResult = fraudClient.score(fraudRequest);
        logAudit(transactionId, merchantId, "FRAUD_CHECK", "risk=" + fraudResult.decision(), ipAddress);

        if ("BLOCK".equals(fraudResult.decision())) {
            saveFailedTransaction(transactionId, request, merchantId, "SUSPECTED_FRAUD", start);
            publishFailed(transactionId, request, customerEmail, "SUSPECTED_FRAUD", start);
            safeRedisDelete(idempotencyKey);
            return fail("SUSPECTED_FRAUD", "Transaction blocked by fraud analysis", false, start);
        }

        var gatewayResult = mpGateway.createPayment(
            request.cardToken(), request.amountInCents(),
            request.paymentMethodId(), request.installments() != null ? request.installments() : 1,
            request.orderId(), customerEmail
        );

        if (!gatewayResult.success()) {
            if (gatewayResult.isTimeout()) {
                safeRedisDelete(idempotencyKey);
                logAudit(transactionId, merchantId, "GATEWAY_TIMEOUT", null, ipAddress);
                return fail("MP_GATEWAY_TIMEOUT", "Payment gateway timeout", true, start);
            }
            saveFailedTransaction(transactionId, request, merchantId, "CARD_DECLINED", start);
            publishFailed(transactionId, request, customerEmail, "CARD_DECLINED", start);
            logAudit(transactionId, merchantId, "PAYMENT_FAILED", "CARD_DECLINED", ipAddress);
            safeRedisDelete(idempotencyKey);
            return fail("CARD_DECLINED", "Payment gateway error", true, start);
        }

        var transaction = new Transaction(
            transactionId, request.orderId(), request.customerId(), merchantId,
            request.amountInCents(), "BRL", request.paymentMethodId(),
            TransactionStatus.APPROVED, request.idempotencyKey()
        );
        transaction.setMpPaymentId(gatewayResult.mpPaymentId());
        transaction.setCardBrand(request.paymentMethodId());
        transaction.setCardLastFour(request.cardToken().substring(request.cardToken().length() - 4));
        transaction.setInstallments(request.installments() != null ? request.installments() : 1);
        transaction.setProcessingTimeMs(Duration.between(start, Instant.now()).toMillis());
        saveApprovedTransaction(transaction);

        try {
            redis.opsForValue().setIfAbsent(idempotencyKey, transactionId, Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Redis unavailable to set idempotency result: {}", e.getMessage());
        }

        var completedEvent = new TransactionCompletedEvent(
            transactionId, gatewayResult.mpPaymentId(), request.orderId(),
            request.customerId(), merchantId, customerEmail, null,
            request.amountInCents(), "BRL", null, null,
            request.installments(), null, Instant.now(), "APPROVED"
        );
        eventProducer.publishCompleted(completedEvent);

        approvedCounter.increment();
        processingTimer.record(Duration.between(start, Instant.now()));
        log.info("Transaction {} approved in {}ms", transactionId, transaction.getProcessingTimeMs());
        logAudit(transactionId, merchantId, "PAYMENT_APPROVED", null, ipAddress);
        return new TransactionResult.Approved(
            transactionId, gatewayResult.mpPaymentId(),
            request.orderId(), transaction.getProcessingTimeMs(), false);
    }

    public Optional<TransactionResponse> findById(String transactionId, UUID merchantId) {
        var cacheKey = "transaction:" + transactionId + ":" + merchantId;
        try {
            var cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                return Optional.ofNullable(objectMapper.readValue(cached, TransactionResponse.class));
            }
        } catch (Exception e) {
            log.debug("Redis cache miss for {}: {}", cacheKey, e.getMessage());
        }

        return transactionRepository.findByTransactionId(transactionId)
            .map(tx -> {
                if (!tx.getMerchantId().equals(merchantId)) {
                    return null;
                }
                var response = mapper.toResponse(tx);
                try {
                    redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofSeconds(60));
                } catch (Exception e) {
                    log.debug("Failed to cache transaction: {}", e.getMessage());
                }
                return response;
            });
    }

    public TransactionResponse findById(String transactionId) {
        var cacheKey = "transaction:" + transactionId;
        try {
            var cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, TransactionResponse.class);
            }
        } catch (Exception e) {
            log.debug("Redis cache miss for {}: {}", cacheKey, e.getMessage());
        }

        return transactionRepository.findByTransactionId(transactionId)
            .map(tx -> {
                var response = mapper.toResponse(tx);
                try {
                    redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofSeconds(60));
                } catch (Exception e) {
                    log.debug("Failed to cache transaction: {}", e.getMessage());
                }
                return response;
            })
            .orElse(null);
    }

    public Page<TransactionSummary> findByCustomer(UUID customerId, UUID merchantId, Pageable pageable) {
        return transactionRepository
            .findByCustomerIdAndMerchantIdOrderByCreatedAtDesc(customerId, merchantId, pageable)
            .map(TransactionSummary::from);
    }

    public Page<TransactionSummary> findByCustomerAndStatus(UUID customerId, UUID merchantId, TransactionStatus status, Pageable pageable) {
        return transactionRepository
            .findByCustomerIdAndMerchantIdAndStatusOrderByCreatedAtDesc(customerId, merchantId, status, pageable)
            .map(TransactionSummary::from);
    }

    @Transactional
    public void handlePaymentWebhook(Long mpPaymentId, String action, JsonNode payload) {
        log.info("Handling MP webhook: paymentId={}, action={}", mpPaymentId, action);

        var transaction = transactionRepository.findByMpPaymentId(mpPaymentId);
        if (transaction.isEmpty()) {
            log.warn("No transaction found for mpPaymentId={}", mpPaymentId);
            return;
        }

        var tx = transaction.get();

        switch (action) {
            case "payment.created" -> {
                log.info("Payment {} created, status={}", mpPaymentId, tx.getStatus());
            }
            case "payment.updated" -> {
                log.info("Payment {} updated — refreshing status", mpPaymentId);
                var mpStatus = payload.path("data").path("status").asText("");
                var newStatus = mapMpStatus(mpStatus);
                if (newStatus != null && tx.getStatus() != newStatus) {
                    tx.setStatus(newStatus);
                    tx.setMpPaymentId(mpPaymentId);
                    transactionRepository.save(tx);
                    log.info("Transaction {} updated to {} via webhook", tx.getTransactionId(), newStatus);
                }
            }
            case "payment.cancelled" -> {
                if (tx.getStatus() == TransactionStatus.APPROVED ||
                    tx.getStatus() == TransactionStatus.PARTIALLY_REFUNDED) {
                    tx.setStatus(TransactionStatus.CANCELLED);
                    transactionRepository.save(tx);
                    log.info("Transaction {} cancelled via webhook", tx.getTransactionId());
                }
            }
            default -> log.debug("Unhandled webhook action: {}", action);
        }
    }

    private TransactionStatus mapMpStatus(String mpStatus) {
        return switch (mpStatus) {
            case "approved" -> TransactionStatus.APPROVED;
            case "rejected" -> TransactionStatus.DECLINED;
            case "in_process" -> TransactionStatus.PROCESSING;
            case "cancelled" -> TransactionStatus.CANCELLED;
            default -> null;
        };
    }

    @Transactional
    public void saveApprovedTransaction(Transaction transaction) {
        transactionRepository.save(transaction);
    }

    @Transactional
    void saveFailedTransaction(String transactionId, TransactionRequest request,
                                        UUID merchantId, String errorCode, Instant start) {
        var status = "SUSPECTED_FRAUD".equals(errorCode)
            ? TransactionStatus.SUSPECTED_FRAUD : TransactionStatus.DECLINED;
        var tx = new Transaction(
            transactionId, request.orderId(), request.customerId(), merchantId,
            request.amountInCents(), "BRL", request.paymentMethodId(),
            status, request.idempotencyKey()
        );
        tx.setErrorCode(errorCode);
        tx.setCardBrand(request.paymentMethodId());
        tx.setCardLastFour(request.cardToken().substring(request.cardToken().length() - 4));
        tx.setInstallments(request.installments() != null ? request.installments() : 1);
        tx.setProcessingTimeMs(Duration.between(start, Instant.now()).toMillis());
        transactionRepository.save(tx);
    }

    private void publishFailed(String transactionId, TransactionRequest request,
                                String customerEmail, String errorCode, Instant start) {
        var failedEvent = new TransactionFailedEvent(
            transactionId, request.orderId(), request.customerId(),
            customerEmail, request.amountInCents(), errorCode,
            start.toString(), "FAILURE"
        );
        eventProducer.publishFailed(failedEvent);
    }

    private TransactionResult fail(String code, String message, boolean retryable, Instant start) {
        failedCounter.increment();
        return new TransactionResult.Failed(
            code, message, retryable, Duration.between(start, Instant.now()).toMillis());
    }

    private String generateTransactionId() {
        return "txn_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void logAudit(String transactionId, UUID merchantId, String action, String details, String ipAddress) {
        try {
            var audit = new AuditLog(transactionId, action, merchantId, details, ipAddress);
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    private void safeRedisDelete(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable for delete {}: {}", key, e.getMessage());
        }
    }
}
