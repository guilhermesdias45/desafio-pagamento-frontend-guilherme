package com.acaboumony.payment.service;

import com.acaboumony.payment.client.FraudServiceClient;
import com.acaboumony.payment.client.MercadoPagoGateway;
import com.acaboumony.payment.client.OrderServiceClient;
import com.acaboumony.payment.client.UserServiceClient;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.TransactionResponse;
import com.acaboumony.payment.event.TransactionCompletedEvent;
import com.acaboumony.payment.event.TransactionFailedEvent;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.exception.*;
import com.acaboumony.payment.mapper.TransactionMapper;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.result.TransactionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate redis;
    private final FraudServiceClient fraudClient;
    private final OrderServiceClient orderClient;
    private final UserServiceClient userClient;
    private final MercadoPagoGateway mpGateway;
    private final TransactionEventProducer eventProducer;
    private final TransactionMapper mapper;

    public TransactionService(TransactionRepository transactionRepository,
                              StringRedisTemplate redis,
                              FraudServiceClient fraudClient,
                              OrderServiceClient orderClient,
                              UserServiceClient userClient,
                              MercadoPagoGateway mpGateway,
                              TransactionEventProducer eventProducer,
                              TransactionMapper mapper) {
        this.transactionRepository = transactionRepository;
        this.redis = redis;
        this.fraudClient = fraudClient;
        this.orderClient = orderClient;
        this.userClient = userClient;
        this.mpGateway = mpGateway;
        this.eventProducer = eventProducer;
        this.mapper = mapper;
    }

    @Transactional
    public TransactionResult processTransaction(TransactionRequest request, String customerEmail,
                                                 UUID merchantId, String ipAddress) {
        Instant start = Instant.now();

        if (!"BRL".equals(request.currency())) {
            return fail("INVALID_CURRENCY", "Only BRL is supported", false, start);
        }

        var idempotencyKey = "idempotency:payment:" + request.idempotencyKey();
        Boolean alreadyProcessed = redis.opsForValue().setIfAbsent(idempotencyKey, "PENDING", Duration.ofHours(24));
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

        var orderValidation = orderClient.validateOrder(request.orderId(), merchantId);
        if (!orderValidation.valid()) {
            return fail(orderValidation.errorCode(), "Order validation failed", false, start);
        }

        var customerValidation = userClient.validateCustomer(request.customerId());
        if (!customerValidation.valid()) {
            return fail(customerValidation.errorCode(), "Customer validation failed", false, start);
        }

        var transactionId = generateTransactionId();

        var fraudRequest = new FraudServiceClient.FraudAnalysisRequest(
            transactionId, request.customerId(), merchantId, request.amountInCents(),
            request.paymentMethodId(), ipAddress, null, null, null
        );
        var fraudResult = fraudClient.score(fraudRequest);

        if ("BLOCK".equals(fraudResult.decision())) {
            saveFailedTransaction(transactionId, request, merchantId, "SUSPECTED_FRAUD", start);
            return fail("SUSPECTED_FRAUD", "Transaction blocked by fraud analysis", false, start);
        }

        var gatewayResult = mpGateway.createPayment(
            request.cardToken(), request.amountInCents(),
            request.paymentMethodId(), request.installments() != null ? request.installments() : 1,
            request.orderId(), customerEmail
        );

        if (!gatewayResult.success()) {
            saveFailedTransaction(transactionId, request, merchantId,
                gatewayResult.isTimeout() ? "MP_GATEWAY_TIMEOUT" : "CARD_DECLINED", start);
            var errorCode = gatewayResult.isTimeout() ? "MP_GATEWAY_TIMEOUT" : "CARD_DECLINED";
            var retryable = gatewayResult.isTimeout() || "CARD_DECLINED".equals(errorCode);
            publishFailed(transactionId, request, customerEmail, errorCode, start);
            return fail(errorCode, "Payment gateway error", retryable, start);
        }

        var transaction = new Transaction(
            transactionId, request.orderId(), request.customerId(), merchantId,
            request.amountInCents(), "BRL", request.paymentMethodId(),
            TransactionStatus.APPROVED, request.idempotencyKey()
        );
        transaction.setMpPaymentId(gatewayResult.mpPaymentId());
        transaction.setProcessingTimeMs(Duration.between(start, Instant.now()).toMillis());
        transactionRepository.save(transaction);

        redis.opsForValue().set(idempotencyKey, transactionId, Duration.ofHours(24));

        var completedEvent = new TransactionCompletedEvent(
            transactionId, gatewayResult.mpPaymentId(), request.orderId(),
            request.customerId(), merchantId, customerEmail, null,
            request.amountInCents(), "BRL", null, null,
            request.installments(), null, Instant.now(), "APPROVED"
        );
        eventProducer.publishCompleted(completedEvent);

        log.info("Transaction {} approved in {}ms", transactionId, transaction.getProcessingTimeMs());
        return new TransactionResult.Approved(
            transactionId, gatewayResult.mpPaymentId(),
            request.orderId(), transaction.getProcessingTimeMs(), false);
    }

    public TransactionResponse findById(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
            .map(mapper::toResponse)
            .orElse(null);
    }

    public Page<TransactionResponse> findByCustomer(UUID customerId, Pageable pageable) {
        return transactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
            .map(mapper::toResponse);
    }

    public void handlePaymentWebhook(Long mpPaymentId, String action) {
        log.info("Handling MP webhook: paymentId={}, action={}", mpPaymentId, action);
    }

    private void saveFailedTransaction(String transactionId, TransactionRequest request,
                                        UUID merchantId, String errorCode, Instant start) {
        var status = "SUSPECTED_FRAUD".equals(errorCode)
            ? TransactionStatus.SUSPECTED_FRAUD : TransactionStatus.DECLINED;
        var tx = new Transaction(
            transactionId, request.orderId(), request.customerId(), merchantId,
            request.amountInCents(), "BRL", request.paymentMethodId(),
            status, request.idempotencyKey()
        );
        tx.setErrorCode(errorCode);
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
        return new TransactionResult.Failed(
            code, message, retryable, Duration.between(start, Instant.now()).toMillis());
    }

    private String generateTransactionId() {
        return "txn_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
