package com.acaboumony.order.integration;

import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.request.ItemRequest;
import com.acaboumony.order.dto.response.OrderResponse;
import com.acaboumony.order.repository.OrderRepository;
import com.acaboumony.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true",
    disabledReason = "Requires Docker with Testcontainers — run with -Dintegration.tests.enabled=true")
class OrderServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("aom")
            .withPassword("aom");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void createOrder_full_flow_persists_to_db_and_caches_idempotency_key() {
        UUID customerId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        CreateOrderRequest request = new CreateOrderRequest(
                merchantId,
                List.of(
                        new ItemRequest("SHIRT-001", "Blue shirt size M", 2, 4999L),
                        new ItemRequest("PANTS-002", "Black jeans", 1, 8999L)
                )
        );

        var result = orderService.createOrder(customerId, "ana@loja.com", idempotencyKey, request);
        assertThat(result).isInstanceOf(OrderService.CreateOrderResult.Success.class);
        OrderResponse response = ((OrderService.CreateOrderResult.Success) result).order();

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalInCents()).isEqualTo(2 * 4999L + 8999L);
        assertThat(response.orderId()).isNotNull();
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.items()).hasSize(2);

        assertThat(orderRepository.findById(response.orderId())).isPresent();
        var saved = orderRepository.findById(response.orderId()).get();
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getMerchantId()).isEqualTo(merchantId);
        assertThat(saved.getTotalInCents()).isEqualTo(18997L);
        assertThat(saved.getItems()).hasSize(2);

        String redisKey = "order:idem:" + idempotencyKey;
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
    }

    @Test
    void createOrder_returns_same_order_on_duplicate_idempotency_key() {
        UUID customerId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        CreateOrderRequest request = new CreateOrderRequest(
                merchantId,
                List.of(new ItemRequest("P1", "Product", 1, 1000L))
        );

        var first = orderService.createOrder(customerId, "ana@loja.com", idempotencyKey, request);
        var second = orderService.createOrder(customerId, "ana@loja.com", idempotencyKey, request);

        UUID firstId = switch (first) {
            case OrderService.CreateOrderResult.Success s -> s.order().orderId();
            case OrderService.CreateOrderResult.Duplicate d -> d.existingOrder().orderId();
        };
        UUID secondId = switch (second) {
            case OrderService.CreateOrderResult.Success s -> s.order().orderId();
            case OrderService.CreateOrderResult.Duplicate d -> d.existingOrder().orderId();
        };

        assertThat(firstId).isEqualTo(secondId);
        assertThat(orderRepository.count()).isGreaterThanOrEqualTo(1);
    }
}
