package com.acaboumony.order.service;

import com.acaboumony.order.config.OrderProperties;
import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.domain.entity.OrderItem;
import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.request.OrderItemRequest;
import com.acaboumony.order.dto.response.OrderDetailResponse;
import com.acaboumony.order.dto.response.OrderResponse;
import com.acaboumony.order.event.OrderEventProducer;
import com.acaboumony.order.exception.*;
import com.acaboumony.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private OrderEventProducer eventProducer;

    private OrderService orderService;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OrderProperties props = new OrderProperties(15);
        orderService = new OrderService(orderRepository, redisTemplate, eventProducer, props);
    }

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    void createOrder_success_returns_order_with_pending_status() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            o.setUpdatedAt(Instant.now());
            return o;
        });

        CreateOrderRequest request = buildRequest(List.of(new OrderItemRequest("P1", "Product 1", 2, 500L)));

        OrderResponse response = orderService.createOrder(request, CUSTOMER_ID);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalInCents()).isEqualTo(1000L);
        verify(eventProducer).publishOrderCreated(any(), eq(CUSTOMER_ID), eq(MERCHANT_ID), eq(1000L));
    }

    @Test
    void createOrder_returns_existing_on_duplicate_idempotency_key() {
        Order existing = buildSavedOrder(500L);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        CreateOrderRequest request = buildRequest(List.of(new OrderItemRequest("P1", "Product 1", 1, 500L)));

        OrderResponse response = orderService.createOrder(request, CUSTOMER_ID);

        assertThat(response.orderId()).isEqualTo(existing.getId());
        verify(orderRepository, never()).save(any());
        verify(eventProducer, never()).publishOrderCreated(any(), any(), any(), anyLong());
    }

    @Test
    void createOrder_calculates_total_server_side_ignoring_client_total() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            o.setUpdatedAt(Instant.now());
            return o;
        });

        // 3 items × R$10,00 = R$30,00 (3000 cents) — client cannot override this
        CreateOrderRequest request = buildRequest(List.of(
                new OrderItemRequest("P1", "Item 1", 3, 1000L)
        ));

        OrderResponse response = orderService.createOrder(request, CUSTOMER_ID);

        assertThat(response.totalInCents()).isEqualTo(3000L);
    }

    @Test
    void createOrder_throws_when_items_empty() {
        CreateOrderRequest request = new CreateOrderRequest(MERCHANT_ID, List.of(), IDEMPOTENCY_KEY);

        assertThatThrownBy(() -> orderService.createOrder(request, CUSTOMER_ID))
                .isInstanceOf(EmptyOrderException.class)
                .extracting("errorCode").isEqualTo("EMPTY_ORDER");
    }

    @Test
    void createOrder_throws_when_item_price_zero() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        CreateOrderRequest request = buildRequest(List.of(new OrderItemRequest("P1", "Item", 1, 0L)));

        assertThatThrownBy(() -> orderService.createOrder(request, CUSTOMER_ID))
                .isInstanceOf(InvalidItemPriceException.class)
                .extracting("errorCode").isEqualTo("INVALID_ITEM_PRICE");
    }

    @Test
    void createOrder_throws_when_total_exceeds_limit() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        // 2 items × 500000 cents = 1000000 > 999999 limit
        CreateOrderRequest request = buildRequest(List.of(
            new OrderItemRequest("P1", "Item A", 1, 500000L),
            new OrderItemRequest("P2", "Item B", 1, 500000L)
        ));

        assertThatThrownBy(() -> orderService.createOrder(request, CUSTOMER_ID))
                .isInstanceOf(TotalExceedsLimitException.class)
                .extracting("errorCode").isEqualTo("TOTAL_EXCEEDS_LIMIT");
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void getOrder_throws_not_found_when_order_missing() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId, CUSTOMER_ID, "CUSTOMER_OWNER", null))
                .isInstanceOf(OrderNotFoundException.class)
                .extracting("errorCode").isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void getOrder_throws_403_when_customer_accesses_other_customer_order() {
        Order order = buildSavedOrder(1000L);
        UUID differentCustomer = UUID.randomUUID();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(order.getId(), differentCustomer, "CUSTOMER_OWNER", null))
                .isInstanceOf(InsufficientPermissionsException.class)
                .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSIONS");
    }

    @Test
    void getOrder_allows_merchant_to_view_own_merchant_orders() {
        Order order = buildSavedOrder(1000L);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderDetailResponse response = orderService.getOrder(
                order.getId(), UUID.randomUUID(), "MERCHANT_OWNER", MERCHANT_ID);

        assertThat(response.orderId()).isEqualTo(order.getId());
    }

    @Test
    void getOrder_allows_admin_to_view_any_order() {
        Order order = buildSavedOrder(1000L);
        UUID anyUser = UUID.randomUUID();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderDetailResponse response = orderService.getOrder(order.getId(), anyUser, "ADMIN", null);

        assertThat(response.orderId()).isEqualTo(order.getId());
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    void cancelOrder_throws_when_order_not_pending() {
        Order order = buildSavedOrder(1000L);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), CUSTOMER_ID, "CUSTOMER_OWNER"))
                .isInstanceOf(OrderCannotBeCancelledException.class)
                .extracting("errorCode").isEqualTo("ORDER_CANNOT_BE_CANCELLED");
    }

    @Test
    void cancelOrder_success_changes_status_to_cancelled() {
        Order order = buildSavedOrder(1000L);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.cancelOrder(order.getId(), CUSTOMER_ID, "CUSTOMER_OWNER");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventProducer).publishOrderCancelled(eq(order.getId()), eq(CUSTOMER_ID), eq("USER_REQUEST"));
    }

    // ── markOrderPaid / Failed / Refunded ────────────────────────────────────

    @Test
    void markOrderPaid_updates_status_to_paid() {
        Order order = buildSavedOrder(1000L);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.markOrderPaid(order.getId(), "TXN-001");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(captor.getValue().getTransactionId()).isEqualTo("TXN-001");
    }

    @Test
    void markOrderFailed_keeps_status_pending_if_not_expired() {
        Order order = buildSavedOrder(1000L);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.markOrderFailed(order.getId());

        // Status should remain PENDING — no save should be called
        verify(orderRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void markOrderRefunded_full_sets_refunded_status() {
        Order order = buildSavedOrder(1000L);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.markOrderRefunded(order.getId(), true);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void markOrderRefunded_partial_sets_partially_refunded() {
        Order order = buildSavedOrder(1000L);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.markOrderRefunded(order.getId(), false);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PARTIALLY_REFUNDED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CreateOrderRequest buildRequest(List<OrderItemRequest> items) {
        return new CreateOrderRequest(MERCHANT_ID, items, IDEMPOTENCY_KEY);
    }

    private Order buildSavedOrder(long totalInCents) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(CUSTOMER_ID);
        order.setMerchantId(MERCHANT_ID);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalInCents(totalInCents);
        order.setIdempotencyKey(UUID.randomUUID());
        order.setExpiresAt(Instant.now().plusSeconds(900));
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setItems(new ArrayList<>());
        return order;
    }
}
