package com.acaboumony.order.service;

import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.domain.entity.OrderItem;
import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.request.ItemRequest;
import com.acaboumony.order.dto.response.OrderDetailResponse;
import com.acaboumony.order.dto.response.PagedResponse;
import com.acaboumony.order.event.OrderCreatedEvent;
import com.acaboumony.order.event.OrderEventProducer;
import com.acaboumony.order.exception.InsufficientPermissionsException;
import com.acaboumony.order.exception.OrderCannotBeCancelledException;
import com.acaboumony.order.exception.OrderNotFoundException;
import com.acaboumony.order.mapper.OrderMapper;
import com.acaboumony.order.repository.OrderRepository;
import com.acaboumony.order.service.OrderService.CreateOrderResult;
import com.acaboumony.order.service.OrderService.EmptyOrderException;
import com.acaboumony.order.service.OrderService.InvalidItemPriceException;
import com.acaboumony.order.service.OrderService.InvalidQuantityException;
import com.acaboumony.order.service.OrderService.OrderTotalExceedsLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private OrderEventProducer orderEventProducer;
    @Mock
    private OrderCacheService orderCacheService;
    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private OrderMapper orderMapper;
    private OrderService orderService;

    private UUID customerId;
    private String customerEmail;
    private UUID merchantId;
    private UUID idempotencyKey;
    private CreateOrderRequest validRequest;

    @BeforeEach
    void setUp() {
        orderMapper = new OrderMapper();
        orderService = new OrderService(orderRepository, idempotencyService, orderMapper, orderEventProducer, orderCacheService);
        customerId = UUID.randomUUID();
        customerEmail = "customer@test.com";
        merchantId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID();
        validRequest = new CreateOrderRequest(
                merchantId,
                List.of(new ItemRequest("prod-1", "Item 1", 2, 1000L))
        );
    }

    @Nested
    class CreateOrder {

        @Test
        void shouldCreateOrderWithServerSideTotal() {
            when(idempotencyService.isDuplicate(idempotencyKey)).thenReturn(false);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateOrderRequest(
                    merchantId,
                    List.of(
                            new ItemRequest("prod-1", "Item 1", 2, 1000L),
                            new ItemRequest("prod-2", "Item 2", 1, 5000L)
                    )
            );

            var result = orderService.createOrder(customerId, customerEmail, idempotencyKey, request);

            assertThat(result).isInstanceOf(CreateOrderResult.Success.class);
            var success = (CreateOrderResult.Success) result;
            assertThat(success.created()).isTrue();
            assertThat(success.order().totalInCents()).isEqualTo(7000L);
            assertThat(success.order().status()).isEqualTo("PENDING");
            assertThat(success.order().items()).hasSize(2);
            verify(idempotencyService).markProcessed(idempotencyKey, success.order().orderId());
            verify(orderEventProducer).publishOrderCreated(any());
        }

        @Test
        void shouldRejectEmptyOrder() {
            var request = new CreateOrderRequest(merchantId, List.of());

            assertThatThrownBy(() -> orderService.createOrder(customerId, customerEmail, idempotencyKey, request))
                    .isInstanceOf(EmptyOrderException.class)
                    .hasMessageContaining("at least one item");
        }

        @Test
        void shouldRejectNullItems() {
            var request = new CreateOrderRequest(merchantId, null);

            assertThatThrownBy(() -> orderService.createOrder(customerId, customerEmail, idempotencyKey, request))
                    .isInstanceOf(EmptyOrderException.class);
        }

        @Test
        void shouldRejectInvalidPrice() {
            var request = new CreateOrderRequest(
                    merchantId,
                    List.of(new ItemRequest("prod-1", "Item", 1, 0L))
            );

            assertThatThrownBy(() -> orderService.createOrder(customerId, customerEmail, idempotencyKey, request))
                    .isInstanceOf(InvalidItemPriceException.class);
        }

        @Test
        void shouldRejectPriceExceedingMax() {
            var request = new CreateOrderRequest(
                    merchantId,
                    List.of(new ItemRequest("prod-1", "Item", 1, 1_000_000L))
            );

            assertThatThrownBy(() -> orderService.createOrder(customerId, customerEmail, idempotencyKey, request))
                    .isInstanceOf(InvalidItemPriceException.class);
        }

        @Test
        void shouldRejectInvalidQuantity() {
            var request = new CreateOrderRequest(
                    merchantId,
                    List.of(new ItemRequest("prod-1", "Item", 0, 1000L))
            );

            assertThatThrownBy(() -> orderService.createOrder(customerId, customerEmail, idempotencyKey, request))
                    .isInstanceOf(InvalidQuantityException.class);
        }

        @Test
        void shouldRejectQuantityExceedingMax() {
            var request = new CreateOrderRequest(
                    merchantId,
                    List.of(new ItemRequest("prod-1", "Item", 1000, 1000L))
            );

            assertThatThrownBy(() -> orderService.createOrder(customerId, customerEmail, idempotencyKey, request))
                    .isInstanceOf(InvalidQuantityException.class);
        }

        @Test
        void shouldReturnExistingOrderWhenIdempotent() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            when(idempotencyService.isDuplicate(idempotencyKey)).thenReturn(true);
            when(idempotencyService.getExistingOrderId(idempotencyKey))
                    .thenReturn(Optional.of(order.getId()));
            when(orderCacheService.findById(order.getId())).thenReturn(Optional.of(order));

            var result = orderService.createOrder(customerId, customerEmail, idempotencyKey, validRequest);

            assertThat(result).isInstanceOf(CreateOrderResult.Duplicate.class);
            var dup = (CreateOrderResult.Duplicate) result;
            assertThat(dup.existingOrder()).isNotNull();
            assertThat(dup.existingOrder().orderId()).isEqualTo(order.getId());
            verify(orderRepository, never()).save(any());
        }

        @Test
        void shouldHandleIdempotentWithoutExistingOrder() {
            when(idempotencyService.isDuplicate(idempotencyKey)).thenReturn(true);
            when(idempotencyService.getExistingOrderId(idempotencyKey))
                    .thenReturn(Optional.empty());

            var result = orderService.createOrder(customerId, customerEmail, idempotencyKey, validRequest);

            assertThat(result).isInstanceOf(CreateOrderResult.Duplicate.class);
            var dup = (CreateOrderResult.Duplicate) result;
            assertThat(dup.existingOrder()).isNull();
        }

        @Test
        void shouldCalculateTotalServerSide() {
            when(idempotencyService.isDuplicate(idempotencyKey)).thenReturn(false);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateOrderRequest(
                    merchantId,
                    List.of(
                            new ItemRequest("p1", "Item 1", 3, 1500L),
                            new ItemRequest("p2", "Item 2", 2, 2990L)
                    )
            );

            var result = orderService.createOrder(customerId, customerEmail, idempotencyKey, request);

            var success = (CreateOrderResult.Success) result;
            assertThat(success.order().totalInCents()).isEqualTo(3 * 1500L + 2 * 2990L);
        }

        @Test
        void shouldSetExpiresAt() {
            when(idempotencyService.isDuplicate(idempotencyKey)).thenReturn(false);
            when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(customerId, customerEmail, idempotencyKey, validRequest);

            var saved = orderCaptor.getValue();
            assertThat(saved.getExpiresAt()).isNotNull();
            assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        void shouldRejectTotalExceedingLimit() {
            when(idempotencyService.isDuplicate(idempotencyKey)).thenReturn(false);

            var request = new CreateOrderRequest(
                    merchantId,
                    List.of(new ItemRequest("prod-1", "Item", 1_000, 1_000L))
            );

            assertThatThrownBy(() -> orderService.createOrder(customerId, customerEmail, idempotencyKey, request))
                    .isInstanceOf(InvalidQuantityException.class);
        }
    }

    @Nested
    class GetOrder {

        @Test
        void shouldReturnOrderWhenCustomerOwnsIt() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PAID, now);
            order.setTransactionId("txn_123");
            when(orderCacheService.findById(order.getId())).thenReturn(Optional.of(order));

            var result = orderService.getOrder(order.getId(), customerId, "CUSTOMER", null);

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(order.getId());
            assertThat(result.status()).isEqualTo("PAID");
            assertThat(result.transactionId()).isEqualTo("txn_123");
        }

        @Test
        void shouldThrowWhenNotFound() {
            var id = UUID.randomUUID();
            when(orderCacheService.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(id, customerId, "CUSTOMER", null))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        void shouldAllowAdminAccess() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            when(orderCacheService.findById(order.getId())).thenReturn(Optional.of(order));

            var result = orderService.getOrder(order.getId(), UUID.randomUUID(), "ADMIN", null);

            assertThat(result).isNotNull();
        }

        @Test
        void shouldAllowMerchantAccess() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            when(orderCacheService.findById(order.getId())).thenReturn(Optional.of(order));

            var result = orderService.getOrder(order.getId(), UUID.randomUUID(), "MERCHANT", merchantId);

            assertThat(result).isNotNull();
        }

        @Test
        void shouldThrow403ForCrossCustomerAccess() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            var otherUser = UUID.randomUUID();
            when(orderCacheService.findById(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.getOrder(order.getId(), otherUser, "CUSTOMER", null))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        void shouldThrow403ForMerchantNotOwningOrder() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            var otherMerchant = UUID.randomUUID();
            when(orderCacheService.findById(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.getOrder(order.getId(), UUID.randomUUID(), "MERCHANT", otherMerchant))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    class ListOrders {

        @Test
        void shouldListCustomerOrders() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            var page = new PageImpl<>(List.of(order));
            when(orderRepository.findByCustomerId(customerId, PageRequest.of(0, 20, Sort.by("createdAt").descending())))
                    .thenReturn(page);

            var result = orderService.listOrders(customerId, "CUSTOMER", null, null, 0, 20);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        void shouldListMerchantOrders() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            var page = new PageImpl<>(List.of(order));
            when(orderRepository.findByMerchantId(merchantId, PageRequest.of(0, 20, Sort.by("createdAt").descending())))
                    .thenReturn(page);

            var result = orderService.listOrders(UUID.randomUUID(), "MERCHANT", merchantId, null, 0, 20);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        void shouldListAdminOrders() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            var page = new PageImpl<>(List.of(order));
            when(orderRepository.findAll(PageRequest.of(0, 20, Sort.by("createdAt").descending())))
                    .thenReturn(page);

            var result = orderService.listOrders(UUID.randomUUID(), "ADMIN", null, null, 0, 20);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        void shouldRespectPageSizeLimit() {
            var pageable = PageRequest.of(0, 100, Sort.by("createdAt").descending());
            var page = Page.<Order>empty(pageable);
            when(orderRepository.findByCustomerId(customerId, pageable))
                    .thenReturn(page);

            var result = orderService.listOrders(customerId, "CUSTOMER", null, null, 0, 200);

            assertThat(result.size()).isEqualTo(100);
        }
    }

    @Nested
    class CancelOrder {

        @Test
        void shouldCancelPendingOrder() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            orderService.cancelOrder(order.getId(), customerId, "CUSTOMER", null);

            verify(orderRepository).save(any());
        }

        @Test
        void shouldThrowWhenOrderIsPaid() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PAID, now);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), customerId, "CUSTOMER", null))
                    .isInstanceOf(OrderCannotBeCancelledException.class);
        }

        @Test
        void shouldThrowWhenOrderNotFound() {
            var id = UUID.randomUUID();
            when(orderRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(id, customerId, "CUSTOMER", null))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        void shouldThrowWhenProcessing() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PROCESSING, now);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), customerId, "CUSTOMER", null))
                    .isInstanceOf(OrderCannotBeCancelledException.class);
        }

        @Test
        void shouldThrow403ForCrossCustomerCancel() {
            var now = Instant.now();
            var order = createOrderEntity(OrderStatus.PENDING, now);
            var otherUser = UUID.randomUUID();
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), otherUser, "CUSTOMER", null))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    private Order createOrderEntity(OrderStatus status, Instant now) {
        var order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setMerchantId(merchantId);
        order.setStatus(status);
        order.setTotalInCents(1000L);
        order.setIdempotencyKey(idempotencyKey);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setExpiresAt(status == OrderStatus.PENDING ? now.plusSeconds(900) : null);

        var item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProductId("prod-1");
        item.setDescription("Item 1");
        item.setQuantity(1);
        item.setUnitPriceInCents(1000L);
        item.setSubtotalInCents(1000L);
        order.setItems(List.of(item));

        return order;
    }
}
