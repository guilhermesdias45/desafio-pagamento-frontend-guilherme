package com.acaboumony.payment.service;

import com.acaboumony.payment.client.MercadoPagoGateway;
import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.domain.entity.AuditLog;
import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.repository.AuditLogRepository;
import com.acaboumony.payment.result.TransactionResult;
import com.acaboumony.payment.support.BaseIntegrationTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class TransactionServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private StringRedisTemplate redis;

    @MockBean
    private MercadoPagoGateway mpGateway;

    private UUID merchantId;
    private UUID customerId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        auditLogRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        merchantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        wiremock.stubFor(WireMock.get(WireMock.urlPathMatching("/api/v1/orders/" + orderId))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"data\": {\"orderId\": \"%s\", \"customerId\": \"%s\", \"merchantId\": \"%s\", \"status\": \"PENDING\", \"totalInCents\": 5000}}"
                    .formatted(orderId, customerId, merchantId))));

        wiremock.stubFor(WireMock.get(WireMock.urlPathMatching("/internal/users/" + customerId))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"id\": \"%s\", \"email\": \"test@test.com\", \"role\": \"CUSTOMER\"}".formatted(customerId))));

        wiremock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/internal/fraud/score"))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"score\": 20, \"decision\": \"APPROVE\", \"reasons\": [], \"analysisTimeMs\": 5}")));

        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString()))
            .thenReturn(new MercadoPagoGateway.PaymentResult(true, 123456789L, null, false));
    }

    @Test
    void processTransaction_approved_successfully() {
        var request = new TransactionRequest(
            8990L, "BRL", customerId, orderId,
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "visa", 1, UUID.randomUUID()
        );

        TransactionResult result = transactionService.processTransaction(
            request, "customer@email.com", merchantId, "192.168.1.1");

        assertInstanceOf(TransactionResult.Approved.class, result);
        TransactionResult.Approved approved = (TransactionResult.Approved) result;
        assertEquals(123456789L, approved.mpPaymentId());
        assertFalse(approved.duplicate());
    }

    @Test
    void processTransaction_persistsTransactionToDatabase() {
        var idempotencyKey = UUID.randomUUID();
        var request = new TransactionRequest(
            5000L, "BRL", customerId, orderId,
            "token1234567890123456789012345678", "master", 2, idempotencyKey
        );

        transactionService.processTransaction(request, "buyer@test.com", merchantId, "10.0.0.1");

        List<Transaction> txs = transactionRepository.findAll();
        assertEquals(1, txs.size());
        Transaction tx = txs.getFirst();
        assertEquals(TransactionStatus.APPROVED, tx.getStatus());
        assertEquals(merchantId, tx.getMerchantId());
        assertEquals(123456789L, tx.getMpPaymentId());
    }

    @Test
    void processTransaction_writesAuditLog() {
        var request = new TransactionRequest(
            3000L, "BRL", customerId, orderId,
            "tokenabcdefabcdefabcdefabcdef12", "elo", 1, UUID.randomUUID()
        );

        transactionService.processTransaction(request, "audit@test.com", merchantId, "10.0.0.2");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty());
        assertTrue(logs.stream().anyMatch(l -> "PAYMENT_APPROVED".equals(l.getAction())));
    }

    @Test
    void processTransaction_rateLimitExceeded_returnsFailure() {
        String customerKey = "rate_limit:payment:" + customerId;
        redis.opsForValue().set(customerKey, "101");

        var request = new TransactionRequest(
            1000L, "BRL", customerId, orderId,
            "tokenxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "visa", 1, UUID.randomUUID()
        );

        TransactionResult result = transactionService.processTransaction(
            request, "test@test.com", merchantId, "10.0.0.3");

        assertInstanceOf(TransactionResult.Failed.class, result);
        TransactionResult.Failed failed = (TransactionResult.Failed) result;
        assertTrue(failed.errorCode().contains("RATE_LIMIT"));
    }

    @Test
    void processTransaction_duplicateIdempotencyKey_returnsIdempotentResult() {
        var idempotencyKey = UUID.randomUUID();
        var request = new TransactionRequest(
            2000L, "BRL", customerId, orderId,
            "tokendupdupdupdupdupdupdupdup1234", "visa", 1, idempotencyKey
        );

        transactionService.processTransaction(request, "dup@test.com", merchantId, "10.0.0.4");

        TransactionResult result = transactionService.processTransaction(
            request, "dup@test.com", merchantId, "10.0.0.4");

        assertInstanceOf(TransactionResult.Approved.class, result);
        assertTrue(((TransactionResult.Approved) result).duplicate());
    }

    @Test
    void processTransaction_fraudBlocked_returnsFailure() {
        wiremock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/internal/fraud/score"))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"score\": 95, \"decision\": \"BLOCK\", \"reasons\": [\"SUSPECTED_FRAUD\"], \"analysisTimeMs\": 10}")));

        var request = new TransactionRequest(
            50000L, "BRL", customerId, orderId,
            "tokenfraudfraudfraudfraudfraud01", "visa", 1, UUID.randomUUID()
        );

        TransactionResult result = transactionService.processTransaction(
            request, "fraud@test.com", merchantId, "10.0.0.99");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("SUSPECTED_FRAUD", ((TransactionResult.Failed) result).errorCode());
    }

    @Test
    void processTransaction_gatewayTimeout_returnsFailure() {
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString()))
            .thenReturn(new MercadoPagoGateway.PaymentResult(false, null, "MP_GATEWAY_TIMEOUT", true));

        var request = new TransactionRequest(
            1000L, "BRL", customerId, orderId,
            "tokentimeouttimeouttimeouttimeout00", "visa", 1, UUID.randomUUID()
        );

        TransactionResult result = transactionService.processTransaction(
            request, "timeout@test.com", merchantId, "10.0.0.5");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("MP_GATEWAY_TIMEOUT", ((TransactionResult.Failed) result).errorCode());
    }

    @Test
    void processTransaction_gatewayDeclined_returnsFailure() {
        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString()))
            .thenReturn(new MercadoPagoGateway.PaymentResult(false, null, "CARD_DECLINED", false));

        var request = new TransactionRequest(
            1000L, "BRL", customerId, orderId,
            "tokendeclineddeclineddeclineddeclin", "visa", 1, UUID.randomUUID()
        );

        TransactionResult result = transactionService.processTransaction(
            request, "declined@test.com", merchantId, "10.0.0.6");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("CARD_DECLINED", ((TransactionResult.Failed) result).errorCode());
    }

    @Test
    void processTransaction_invalidCurrency_returnsFailure() {
        var request = new TransactionRequest(
            1000L, "USD", customerId, orderId,
            "tokenusdusdusdusdusdusdusdusdusd", "visa", 1, UUID.randomUUID()
        );

        TransactionResult result = transactionService.processTransaction(
            request, "usd@test.com", merchantId, "10.0.0.7");

        assertInstanceOf(TransactionResult.Failed.class, result);
        assertEquals("INVALID_CURRENCY", ((TransactionResult.Failed) result).errorCode());
    }

    @Test
    void findTransactions_byCustomer_returnsMappedPage() {
        merchantId = UUID.randomUUID();

        wiremock.stubFor(WireMock.get(WireMock.urlPathMatching("/api/v1/orders/" + orderId))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"data\": {\"orderId\": \"%s\", \"customerId\": \"%s\", \"merchantId\": \"%s\", \"status\": \"PENDING\", \"totalInCents\": 5000}}"
                    .formatted(orderId, customerId, merchantId))));

        wiremock.stubFor(WireMock.get(WireMock.urlPathMatching("/internal/users/" + customerId))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"id\": \"%s\", \"email\": \"test@test.com\", \"role\": \"CUSTOMER\"}".formatted(customerId))));

        var request = new TransactionRequest(
            4500L, "BRL", customerId, orderId,
            "tokenlisttokenlisttokenlisttoken00", "visa", 1, UUID.randomUUID()
        );
        transactionService.processTransaction(request, "list@test.com", merchantId, "10.0.0.8");

        var page = transactionService.findByCustomer(customerId, merchantId, PageRequest.ofSize(10));
        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
    }

    @Test
    void findTransactions_byCustomerAndStatus_filtersCorrectly() {
        var orderId2 = UUID.randomUUID();

        wiremock.stubFor(WireMock.get(WireMock.urlPathMatching("/api/v1/orders/" + orderId))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"data\": {\"orderId\": \"%s\", \"customerId\": \"%s\", \"merchantId\": \"%s\", \"status\": \"PENDING\", \"totalInCents\": 5000}}"
                    .formatted(orderId, customerId, merchantId))));

        wiremock.stubFor(WireMock.get(WireMock.urlPathMatching("/api/v1/orders/" + orderId2))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"data\": {\"orderId\": \"%s\", \"customerId\": \"%s\", \"merchantId\": \"%s\", \"status\": \"PENDING\", \"totalInCents\": 5000}}"
                    .formatted(orderId2, customerId, merchantId))));

        wiremock.stubFor(WireMock.get(WireMock.urlPathMatching("/internal/users/" + customerId))
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/json")
                .withStatus(200)
                .withBody("{\"id\": \"%s\", \"email\": \"test@test.com\", \"role\": \"CUSTOMER\"}".formatted(customerId))));

        when(mpGateway.createPayment(anyString(), anyLong(), anyString(), anyInt(), any(), anyString()))
            .thenReturn(
                new MercadoPagoGateway.PaymentResult(true, 111L, null, false),
                new MercadoPagoGateway.PaymentResult(true, 222L, null, false)
            );

        var req1 = new TransactionRequest(
            1000L, "BRL", customerId, orderId,
            "tokencode1tokencode1tokencode11234", "visa", 1, UUID.randomUUID()
        );
        var req2 = new TransactionRequest(
            2000L, "BRL", customerId, orderId2,
            "tokencode2tokencode2tokencode25678", "visa", 1, UUID.randomUUID()
        );
        transactionService.processTransaction(req1, "filt@test.com", merchantId, "10.0.0.9");
        transactionService.processTransaction(req2, "filt@test.com", merchantId, "10.0.0.9");

        var approvedPage = transactionService.findByCustomerAndStatus(
            customerId, merchantId, TransactionStatus.APPROVED, PageRequest.ofSize(10));
        assertEquals(2, approvedPage.getTotalElements());
    }

}
