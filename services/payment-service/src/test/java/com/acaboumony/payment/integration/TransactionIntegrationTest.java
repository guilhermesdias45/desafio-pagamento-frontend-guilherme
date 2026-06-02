package com.acaboumony.payment.integration;

import com.acaboumony.payment.domain.enums.TransactionStatus;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.repository.AuditLogRepository;
import com.acaboumony.payment.repository.RefundRepository;
import com.acaboumony.payment.repository.TransactionRepository;
import com.acaboumony.payment.result.TransactionResult;
import com.acaboumony.payment.service.TransactionService;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class TransactionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("payment_db")
            .withUsername("aom")
            .withPassword("aom");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("mercadopago.base-url", wireMock::baseUrl);
        registry.add("fraud-service.url", wireMock::baseUrl);
        registry.add("order-service.url", wireMock::baseUrl);
        registry.add("internal.secret", () -> "test-internal-secret");
        registry.add("mercadopago.access-token", () -> "TEST-ACCESS-TOKEN");
        registry.add("mercadopago.timeout-ms", () -> "800");
        registry.add("payment.rate-limit.max-requests-per-minute", () -> "100");
    }

    @Autowired TransactionService transactionService;
    @Autowired TransactionRepository transactionRepository;
    @Autowired RefundRepository refundRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        transactionRepository.deleteAll();

        // Stub order service
        wireMock.stubFor(get(urlPathMatching("/internal/orders/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "orderId": "%s",
                                  "status": "PENDING",
                                  "totalInCents": 5000,
                                  "merchantId": "%s",
                                  "customerId": "%s"
                                }
                                """.formatted(ORDER_ID, MERCHANT_ID, CUSTOMER_ID))));

        // Stub fraud service - default APPROVE
        wireMock.stubFor(post(urlEqualTo("/internal/fraud/score"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "score": 25,
                                  "decision": "APPROVE",
                                  "reasons": [],
                                  "analysisTimeMs": 45
                                }
                                """)));
    }

    @AfterEach
    void cleanUp() {
        auditLogRepository.deleteAll();
        refundRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void full_flow_approved_transaction_persisted_and_kafka_published() {
        // Stub MP to approve
        wireMock.stubFor(post(urlEqualTo("/v1/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 987654321,
                                  "status": "approved",
                                  "status_detail": "accredited",
                                  "payment_method_id": "visa",
                                  "card": {
                                    "last_four_digits": "1234",
                                    "first_six_digits": "411111",
                                    "brand": "visa"
                                  }
                                }
                                """)));

        UUID idempotencyKey = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                5000L, "BRL", ORDER_ID,
                "abcdef1234567890abcdef1234567890",
                "visa", 1, idempotencyKey
        );

        TransactionResult result = transactionService.processTransaction(request, CUSTOMER_ID);

        // Verify result
        assertThat(result).isInstanceOf(TransactionResult.Success.class);
        TransactionResult.Success success = (TransactionResult.Success) result;
        assertThat(success.transactionId()).startsWith("txn_");

        // Verify persisted in DB
        var savedTx = transactionRepository.findByTransactionId(success.transactionId());
        assertThat(savedTx).isPresent();
        assertThat(savedTx.get().getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(savedTx.get().getAmountInCents()).isEqualTo(5000L);
        assertThat(savedTx.get().getCardLastFour()).isEqualTo("1234");
        assertThat(savedTx.get().getMpPaymentId()).isEqualTo(987654321L);

        // Verify Kafka message published (read from topic)
        boolean messageReceived = consumeKafkaMessage("transaction.completed", success.transactionId(), 8000);
        assertThat(messageReceived).isTrue();
    }

    @Test
    void fraud_block_transaction_persisted_with_suspected_fraud_status() {
        // Override fraud service to BLOCK
        wireMock.stubFor(post(urlEqualTo("/internal/fraud/score"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "score": 95,
                                  "decision": "BLOCK",
                                  "reasons": ["UNUSUAL_AMOUNT", "SUSPICIOUS_DEVICE"],
                                  "analysisTimeMs": 80
                                }
                                """)));

        UUID idempotencyKey = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                50000L, "BRL", ORDER_ID,
                "abcdef1234567890abcdef1234567890",
                "visa", 1, idempotencyKey
        );

        assertThrows(
                com.acaboumony.payment.exception.FraudDetectedException.class,
                () -> transactionService.processTransaction(request, CUSTOMER_ID)
        );

        // Verify SUSPECTED_FRAUD transaction was saved for audit
        var transactions = transactionRepository.findAll();
        assertThat(transactions).isNotEmpty();
        var fraudTx = transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.SUSPECTED_FRAUD)
                .findFirst();
        assertThat(fraudTx).isPresent();
        assertThat(fraudTx.get().getErrorCode()).isEqualTo("SUSPECTED_FRAUD");
    }

    @Test
    void idempotency_returns_same_result_on_second_call() {
        // Stub MP to approve
        wireMock.stubFor(post(urlEqualTo("/v1/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 111222333,
                                  "status": "approved",
                                  "status_detail": "accredited",
                                  "payment_method_id": "visa",
                                  "card": {
                                    "last_four_digits": "5678",
                                    "first_six_digits": "555555",
                                    "brand": "mastercard"
                                  }
                                }
                                """)));

        UUID idempotencyKey = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                8000L, "BRL", ORDER_ID,
                "abcdef1234567890abcdef1234567890",
                "master", 1, idempotencyKey
        );

        // First call
        TransactionResult result1 = transactionService.processTransaction(request, CUSTOMER_ID);
        assertThat(result1).isInstanceOf(TransactionResult.Success.class);
        TransactionResult.Success success1 = (TransactionResult.Success) result1;

        // Second call with same idempotency key — should return cached result
        TransactionResult result2 = transactionService.processTransaction(request, CUSTOMER_ID);
        assertThat(result2).isInstanceOf(TransactionResult.Success.class);
        TransactionResult.Success success2 = (TransactionResult.Success) result2;

        // Both should have the same transaction ID
        assertThat(success1.transactionId()).isEqualTo(success2.transactionId());
        assertThat(success1.mpPaymentId()).isEqualTo(success2.mpPaymentId());

        // MP should only have been called once (idempotency)
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/payments")));
    }

    private boolean consumeKafkaMessage(String topic, String expectedKey, long timeoutMs) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (expectedKey.equals(record.key())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
