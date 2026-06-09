package com.acaboumony.payment.service;

import com.acaboumony.payment.client.FraudServiceClient;
import com.acaboumony.payment.client.MercadoPagoGateway;
import com.acaboumony.payment.client.OrderServiceClient;
import com.acaboumony.payment.client.UserServiceClient;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.response.TransactionResponse;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.mapper.TransactionMapper;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.acaboumony.payment.result.TransactionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private FraudServiceClient fraudClient;
    @Mock private OrderServiceClient orderClient;
    @Mock private UserServiceClient userClient;
    @Mock private MercadoPagoGateway mpGateway;
    @Mock private TransactionEventProducer eventProducer;
    @Mock private TransactionMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TransactionService service;
    private com.acaboumony.payment.dto.request.TransactionRequest validRequest;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, auditLogRepository, redis, fraudClient,
            orderClient, userClient, mpGateway, eventProducer, mapper, objectMapper, new SimpleMeterRegistry(),
            "test@testuser.com", mock(ObjectProvider.class));
        validRequest = new com.acaboumony.payment.dto.request.TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );
        lenient().when(orderClient.validateOrder(any(), any())).thenReturn(
            new OrderServiceClient.OrderValidationResult(true, null));
        lenient().when(userClient.validateCustomer(any())).thenReturn(
            new UserServiceClient.UserValidationResult(true, null));
    }

    private void mockRedisForNewRequest() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    @Test
    void processTransaction_whenInvalidCurrency_returnsInvalidCurrency() {
        var request = new com.acaboumony.payment.dto.request.TransactionRequest(
            8990L, "USD", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        TransactionResult result = service.processTransaction(request, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("INVALID_CURRENCY", ((TransactionResult.Failed) result).errorCode());
        assertFalse(((TransactionResult.Failed) result).retryable());
    }

    @Test
    void processTransaction_whenRateLimitExceeded_returns429() {
        mockRedisForNewRequest();
        when(valueOps.increment(anyString())).thenReturn(101L);

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("RATE_LIMIT_EXCEEDED", ((TransactionResult.Failed) result).errorCode());
        assertTrue(((TransactionResult.Failed) result).retryable());
    }

    @Test
    void processTransaction_usesPayerEmailFromConfig() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(10, "APPROVE", java.util.List.of(), 15L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), eq("test@testuser.com"), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));

        var result = service.processTransaction(validRequest, "customer@email.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Approved.class, result);
        verify(mpGateway).createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), eq("test@testuser.com"), any());
    }

    @Test
    void processTransaction_whenDuplicateIdempotencyKey_returnsConflict() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(false);
        when(transactionRepository.findByIdempotencyKey(any()))
            .thenReturn(java.util.Optional.empty());

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("DUPLICATE_IDEMPOTENCY_KEY", ((TransactionResult.Failed) result).errorCode());
    }

    @Test
    void processTransaction_whenFraudDetected_returnsSuspectedFraudAndPublishesEvent() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(95, "BLOCK", java.util.List.of("IP_BLACKLISTED"), 50L));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("SUSPECTED_FRAUD", ((TransactionResult.Failed) result).errorCode());
        assertFalse(((TransactionResult.Failed) result).retryable());
        verify(eventProducer).publishFailed(any());
    }

    @Test
    void processTransaction_whenCardDeclined_returnsCardDeclined() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(20, "APPROVE", java.util.List.of(), 20L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.declined("CARD_DECLINED"));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("CARD_DECLINED", ((TransactionResult.Failed) result).errorCode());
        assertTrue(((TransactionResult.Failed) result).retryable());
    }

    @Test
    void processTransaction_whenMpTimeout_returnsGatewayTimeout() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(20, "APPROVE", java.util.List.of(), 20L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.timeout());

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("MP_GATEWAY_TIMEOUT", ((TransactionResult.Failed) result).errorCode());
        assertTrue(((TransactionResult.Failed) result).retryable());
    }

    @Test
    void processTransaction_whenMpTimeout_doesNotPersistTransaction() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(20, "APPROVE", java.util.List.of(), 20L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.timeout());

        service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        verify(transactionRepository, never()).save(any());
        verify(eventProducer, never()).publishFailed(any());
    }

    @Test
    void processTransaction_whenFraudServiceUnavailable_usesFallbackAndApproves() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(50, "APPROVE", java.util.List.of("FALLBACK_TIMEOUT"), 0L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Approved.class, result);
        assertEquals(123456L, ((TransactionResult.Approved) result).mpPaymentId());
    }

    @Test
    void processTransaction_whenSuccess_returnsApproved() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(10, "APPROVE", java.util.List.of(), 15L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Approved.class, result);
        var approved = (TransactionResult.Approved) result;
        assertNotNull(approved.transactionId());
        assertEquals(123456L, approved.mpPaymentId());
        assertEquals(validRequest.orderId(), approved.orderId());
    }

    @Test
    void processTransaction_whenSuccess_savesCardBrandAndLastFour() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(10, "APPROVE", java.util.List.of(), 15L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        verify(transactionRepository).save(argThat(tx ->
            "visa".equals(tx.getCardBrand()) && "c5d6".equals(tx.getCardLastFour())
        ));
    }

    @Test
    void processTransaction_whenSuccess_savesInstallments() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(10, "APPROVE", java.util.List.of(), 15L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        verify(transactionRepository).save(argThat(tx ->
            Integer.valueOf(1).equals(tx.getInstallments())
        ));
    }

    @Test
    void processTransaction_whenCardDeclined_savesCardBrandAndLastFour() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(20, "APPROVE", java.util.List.of(), 20L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.declined("CARD_DECLINED"));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        verify(transactionRepository).save(argThat(tx ->
            "visa".equals(tx.getCardBrand()) && "c5d6".equals(tx.getCardLastFour())
        ));
    }

    @Test
    void processTransaction_whenRedisUnavailable_skipsRateLimit() {
        when(redis.opsForValue()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Connection refused"));
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(10, "APPROVE", java.util.List.of(), 15L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Approved.class, result);
    }

    @Test
    void handlePaymentWebhook_paymentCreated_doesNotChangeStatus() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        service.handlePaymentWebhook(123456L, "payment.created", payload);

        assertEquals(TransactionStatus.APPROVED, tx.getStatus());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void handlePaymentWebhook_paymentUpdated_statusChange_updatesTransaction() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.PROCESSING, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        var data = objectMapper.createObjectNode();
        data.put("status", "approved");
        payload.set("data", data);

        service.handlePaymentWebhook(123456L, "payment.updated", payload);

        assertEquals(TransactionStatus.APPROVED, tx.getStatus());
        verify(transactionRepository).save(tx);
    }

    @Test
    void handlePaymentWebhook_paymentUpdated_noStatusChange_doesNotSave() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        var data = objectMapper.createObjectNode();
        data.put("status", "approved");
        payload.set("data", data);

        service.handlePaymentWebhook(123456L, "payment.updated", payload);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void handlePaymentWebhook_paymentUpdated_rejectedStatus_setsDeclined() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.PROCESSING, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        var data = objectMapper.createObjectNode();
        data.put("status", "rejected");
        payload.set("data", data);

        service.handlePaymentWebhook(123456L, "payment.updated", payload);

        assertEquals(TransactionStatus.DECLINED, tx.getStatus());
        verify(transactionRepository).save(tx);
    }

    @Test
    void handlePaymentWebhook_paymentCancelled_approvedStatus_setsCancelled() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        service.handlePaymentWebhook(123456L, "payment.cancelled", payload);

        assertEquals(TransactionStatus.CANCELLED, tx.getStatus());
        verify(transactionRepository).save(tx);
    }

    @Test
    void handlePaymentWebhook_paymentCancelled_partiallyRefunded_setsCancelled() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.PARTIALLY_REFUNDED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        service.handlePaymentWebhook(123456L, "payment.cancelled", payload);

        assertEquals(TransactionStatus.CANCELLED, tx.getStatus());
        verify(transactionRepository).save(tx);
    }

    @Test
    void handlePaymentWebhook_paymentCancelled_nonCancellableStatus_doesNotSave() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.DECLINED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        service.handlePaymentWebhook(123456L, "payment.cancelled", payload);

        assertEquals(TransactionStatus.DECLINED, tx.getStatus());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void handlePaymentWebhook_unknownAction_doesNotSave() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        service.handlePaymentWebhook(123456L, "payment.unknown", payload);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void handlePaymentWebhook_nonExistentPayment_doesNotThrow() {
        when(transactionRepository.findByMpPaymentId(999L)).thenReturn(Optional.empty());

        var payload = objectMapper.createObjectNode();
        assertDoesNotThrow(() -> service.handlePaymentWebhook(999L, "payment.updated", payload));

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void handlePaymentWebhook_paymentUpdated_unknownMpStatus_doesNotChange() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        var data = objectMapper.createObjectNode();
        data.put("status", "some_unknown_status");
        payload.set("data", data);

        service.handlePaymentWebhook(123456L, "payment.updated", payload);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void findById_withCacheHit_returnsCachedResponse() throws Exception {
        var merchantId = UUID.randomUUID();
        var cacheKey = "transaction:txn_001:" + merchantId;
        var txResponse = new TransactionResponse("txn_001", 123L, UUID.randomUUID(),
            "APPROVED", 5000L, "BRL", "visa", "1234", 1, 500L, null, null);
        var cachedJson = objectMapper.writeValueAsString(txResponse);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(cacheKey)).thenReturn(cachedJson);

        var result = service.findById("txn_001", merchantId);

        assertTrue(result.isPresent());
        assertEquals("txn_001", result.get().transactionId());
        verify(transactionRepository, never()).findByTransactionId(anyString());
    }

    @Test
    void findById_withCacheMiss_queriesAndCaches() {
        var merchantId = UUID.randomUUID();
        var cacheKey = "transaction:txn_001:" + merchantId;

        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), merchantId,
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setMpPaymentId(123L);

        var txResponse = new TransactionResponse("txn_001", 123L, tx.getOrderId(),
            "APPROVED", 8990L, "BRL", "visa", "1234", 1, 500L, null, null);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(cacheKey)).thenReturn(null);
        when(transactionRepository.findByTransactionId("txn_001")).thenReturn(Optional.of(tx));
        when(mapper.toResponse(tx)).thenReturn(txResponse);

        var result = service.findById("txn_001", merchantId);

        assertTrue(result.isPresent());
        assertEquals("txn_001", result.get().transactionId());
        verify(valueOps).set(eq(cacheKey), anyString(), any(Duration.class));
    }

    @Test
    void findById_withCacheMiss_wrongMerchantId_returnsEmpty() {
        var merchantId = UUID.randomUUID();
        var otherMerchantId = UUID.randomUUID();
        var cacheKey = "transaction:txn_001:" + merchantId;

        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), otherMerchantId,
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(cacheKey)).thenReturn(null);
        when(transactionRepository.findByTransactionId("txn_001")).thenReturn(Optional.of(tx));

        var result = service.findById("txn_001", merchantId);

        assertFalse(result.isPresent());
    }

    @Test
    void findById_withCacheException_queriesDatabase() {
        var merchantId = UUID.randomUUID();
        var cacheKey = "transaction:txn_001:" + merchantId;

        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), merchantId,
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());

        var txResponse = new TransactionResponse("txn_001", 123L, tx.getOrderId(),
            "APPROVED", 8990L, "BRL", "visa", "1234", 1, 500L, null, null);

        when(redis.opsForValue()).thenThrow(new RuntimeException("Redis down"));
        when(transactionRepository.findByTransactionId("txn_001")).thenReturn(Optional.of(tx));
        when(mapper.toResponse(tx)).thenReturn(txResponse);

        var result = service.findById("txn_001", merchantId);

        assertTrue(result.isPresent());
        assertEquals("txn_001", result.get().transactionId());
    }

    @Test
    void findById_oneArg_withCacheHit_returnsCachedResponse() throws Exception {
        var cacheKey = "transaction:txn_001";
        var txResponse = new TransactionResponse("txn_001", 123L, UUID.randomUUID(),
            "APPROVED", 5000L, "BRL", "visa", "1234", 1, 500L, null, null);
        var cachedJson = objectMapper.writeValueAsString(txResponse);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(cacheKey)).thenReturn(cachedJson);

        var result = service.findById("txn_001");

        assertNotNull(result);
        assertEquals("txn_001", result.transactionId());
        verify(transactionRepository, never()).findByTransactionId(anyString());
    }

    @Test
    void findById_oneArg_withCacheMiss_queriesAndCaches() {
        var cacheKey = "transaction:txn_001";

        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());

        var txResponse = new TransactionResponse("txn_001", 123L, tx.getOrderId(),
            "APPROVED", 8990L, "BRL", "visa", "1234", 1, 500L, null, null);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(cacheKey)).thenReturn(null);
        when(transactionRepository.findByTransactionId("txn_001")).thenReturn(Optional.of(tx));
        when(mapper.toResponse(tx)).thenReturn(txResponse);

        var result = service.findById("txn_001");

        assertNotNull(result);
        assertEquals("txn_001", result.transactionId());
        verify(valueOps).set(eq(cacheKey), anyString(), any(Duration.class));
    }

    @Test
    void findById_oneArg_withCacheException_queriesDatabase() {
        var cacheKey = "transaction:txn_001";

        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());

        var txResponse = new TransactionResponse("txn_001", 123L, tx.getOrderId(),
            "APPROVED", 8990L, "BRL", "visa", "1234", 1, 500L, null, null);

        when(redis.opsForValue()).thenThrow(new RuntimeException("Redis down"));
        when(transactionRepository.findByTransactionId("txn_001")).thenReturn(Optional.of(tx));
        when(mapper.toResponse(tx)).thenReturn(txResponse);

        var result = service.findById("txn_001");

        assertNotNull(result);
        assertEquals("txn_001", result.transactionId());
    }

    @Test
    void findById_oneArg_notFound_returnsNull() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("transaction:txn_001")).thenReturn(null);
        when(transactionRepository.findByTransactionId("txn_001")).thenReturn(Optional.empty());

        var result = service.findById("txn_001");

        assertNull(result);
    }

    @Test
    void processTransaction_whenIdempotencyKeyAlreadyInRedis_returnsDuplicateWithExistingTx() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(false);

        var existingTx = new Transaction("txn_existing", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        existingTx.setMpPaymentId(999L);
        when(transactionRepository.findByIdempotencyKey(any()))
            .thenReturn(Optional.of(existingTx));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Approved.class, result);
        var approved = (TransactionResult.Approved) result;
        assertTrue(approved.duplicate());
        assertEquals("txn_existing", approved.transactionId());
    }

    @Test
    void processTransaction_whenOrderValidationFails_returnsError() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(orderClient.validateOrder(any(), any()))
            .thenReturn(new OrderServiceClient.OrderValidationResult(false, "ORDER_NOT_FOUND"));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("ORDER_NOT_FOUND", ((TransactionResult.Failed) result).errorCode());
    }

    @Test
    void processTransaction_whenCustomerValidationFails_returnsError() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(orderClient.validateOrder(any(), any()))
            .thenReturn(new OrderServiceClient.OrderValidationResult(true, null));
        when(userClient.validateCustomer(any()))
            .thenReturn(new UserServiceClient.UserValidationResult(false, "CUSTOMER_NOT_FOUND"));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("CUSTOMER_NOT_FOUND", ((TransactionResult.Failed) result).errorCode());
    }

    @Test
    void processTransaction_whenSuccess_publishesEvent() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(10, "APPROVE", java.util.List.of(), 15L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));

        service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        verify(eventProducer).publishCompleted(any());
    }

    @Test
    void processTransaction_whenInstallmentsNull_usesDefaultOne() {
        var request = new com.acaboumony.payment.dto.request.TransactionRequest(
            8990L, "BRL", UUID.randomUUID(), UUID.randomUUID(),
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", null, UUID.randomUUID()
        );

        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(10, "APPROVE", java.util.List.of(), 15L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.processTransaction(request, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        verify(mpGateway).createPayment(anyString(), anyLong(), anyString(), eq(1), any(), anyString(), any());
    }

    @Test
    void findByCustomer_returnsMappedPage() {
        var customerId = UUID.randomUUID();
        var merchantId = UUID.randomUUID();
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), merchantId,
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setCardBrand("visa");
        tx.setCardLastFour("1234");

        var page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(tx));
        when(transactionRepository.findByCustomerIdAndMerchantIdOrderByCreatedAtDesc(customerId, merchantId, PageRequest.of(0, 10)))
            .thenReturn(page);

        var result = service.findByCustomer(customerId, merchantId, PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
        assertEquals("txn_001", result.getContent().get(0).transactionId());
    }

    @Test
    void handlePaymentWebhook_paymentUpdated_inProcess_setsProcessing() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        var data = objectMapper.createObjectNode();
        data.put("status", "in_process");
        payload.set("data", data);

        service.handlePaymentWebhook(123456L, "payment.updated", payload);

        assertEquals(TransactionStatus.PROCESSING, tx.getStatus());
        verify(transactionRepository).save(tx);
    }

    @Test
    void handlePaymentWebhook_paymentUpdated_cancelled_setsCancelled() {
        var tx = new Transaction("txn_001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            8990L, "BRL", "visa", TransactionStatus.APPROVED, UUID.randomUUID());
        tx.setMpPaymentId(123456L);
        when(transactionRepository.findByMpPaymentId(123456L)).thenReturn(Optional.of(tx));

        var payload = objectMapper.createObjectNode();
        var data = objectMapper.createObjectNode();
        data.put("status", "cancelled");
        payload.set("data", data);

        service.handlePaymentWebhook(123456L, "payment.updated", payload);

        assertEquals(TransactionStatus.CANCELLED, tx.getStatus());
        verify(transactionRepository).save(tx);
    }

    @Test
    void processTransaction_whenAuditLogThrows_continuesProcessing() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(10, "APPROVE", java.util.List.of(), 15L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Approved.class, result);
    }

    @Test
    void processTransaction_whenRedisDeleteThrows_continuesProcessing() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(20, "APPROVE", java.util.List.of(), 20L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(MercadoPagoGateway.PaymentResult.declined("CARD_DECLINED"));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Redis down")).when(redis).delete(anyString());

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
    }
}
