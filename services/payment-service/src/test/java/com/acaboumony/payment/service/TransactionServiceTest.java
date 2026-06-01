package com.acaboumony.payment.service;

import com.acaboumony.payment.client.FraudServiceClient;
import com.acaboumony.payment.client.MercadoPagoGateway;
import com.acaboumony.payment.client.OrderServiceClient;
import com.acaboumony.payment.client.UserServiceClient;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.event.TransactionEventProducer;
import com.acaboumony.payment.mapper.TransactionMapper;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.result.TransactionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private FraudServiceClient fraudClient;
    @Mock private OrderServiceClient orderClient;
    @Mock private UserServiceClient userClient;
    @Mock private MercadoPagoGateway mpGateway;
    @Mock private TransactionEventProducer eventProducer;
    @Mock private TransactionMapper mapper;

    private TransactionService service;
    private com.acaboumony.payment.dto.request.TransactionRequest validRequest;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, redis, fraudClient,
            orderClient, userClient, mpGateway, eventProducer, mapper);
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
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString()))
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
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString()))
            .thenReturn(MercadoPagoGateway.PaymentResult.timeout());

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("MP_GATEWAY_TIMEOUT", ((TransactionResult.Failed) result).errorCode());
        assertTrue(((TransactionResult.Failed) result).retryable());
    }

    @Test
    void processTransaction_whenFraudServiceUnavailable_usesFallbackAndApproves() {
        mockRedisForNewRequest();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        when(fraudClient.score(any())).thenReturn(
            new FraudServiceClient.FraudScoreResult(50, "APPROVE", java.util.List.of("FALLBACK_TIMEOUT"), 0L));
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString()))
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
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString()))
            .thenReturn(MercadoPagoGateway.PaymentResult.approved(123456L));

        TransactionResult result = service.processTransaction(validRequest, "test@test.com", UUID.randomUUID(), "127.0.0.1");

        assertInstanceOf(TransactionResult.Approved.class, result);
        var approved = (TransactionResult.Approved) result;
        assertNotNull(approved.transactionId());
        assertEquals(123456L, approved.mpPaymentId());
        assertEquals(validRequest.orderId(), approved.orderId());
    }
}
