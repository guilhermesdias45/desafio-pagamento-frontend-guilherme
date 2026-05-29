package com.acaboumony.order.service;

import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.domain.entity.OrderItem;
import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.request.ItemRequest;
import com.acaboumony.order.dto.response.OrderDetailResponse;
import com.acaboumony.order.dto.response.OrderResponse;
import com.acaboumony.order.dto.response.PagedResponse;
import com.acaboumony.order.event.OrderCreatedEvent;
import com.acaboumony.order.event.OrderEventProducer;
import com.acaboumony.order.exception.InsufficientPermissionsException;
import com.acaboumony.order.exception.OrderCannotBeCancelledException;
import com.acaboumony.order.exception.OrderNotFoundException;
import com.acaboumony.order.mapper.OrderMapper;
import com.acaboumony.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderService {

    static final int MAX_PAGE_SIZE = 100;
    static final long MAX_TOTAL_IN_CENTS = 999_999;
    static final long EXPIRATION_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final OrderMapper orderMapper;
    private final OrderEventProducer orderEventProducer;

    public OrderService(OrderRepository orderRepository,
                        IdempotencyService idempotencyService,
                        OrderMapper orderMapper,
                        OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.idempotencyService = idempotencyService;
        this.orderMapper = orderMapper;
        this.orderEventProducer = orderEventProducer;
    }

    @Transactional
    public CreateOrderResult createOrder(UUID customerId, UUID idempotencyKey, CreateOrderRequest request) {
        if (idempotencyService.isDuplicate(idempotencyKey)) {
            var existingOrderId = idempotencyService.getExistingOrderId(idempotencyKey);
            if (existingOrderId.isPresent()) {
                var existingOrder = orderRepository.findById(existingOrderId.get())
                        .orElseThrow(() -> new OrderNotFoundException(existingOrderId.get()));
                return new CreateOrderResult.Duplicate(orderMapper.toResponse(existingOrder));
            }
            return new CreateOrderResult.Duplicate(null);
        }

        validateItems(request.items());

        var totalInCents = calculateTotal(request.items());
        if (totalInCents > MAX_TOTAL_IN_CENTS) {
            throw new OrderTotalExceedsLimitException(totalInCents);
        }

        var now = Instant.now();
        var orderId = UUID.randomUUID();

        var order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setMerchantId(request.merchantId());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalInCents(totalInCents);
        order.setIdempotencyKey(idempotencyKey);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setExpiresAt(now.plusSeconds(EXPIRATION_MINUTES * 60));

        var items = request.items().stream()
                .map(item -> buildItem(order, item))
                .toList();
        order.setItems(items);

        orderRepository.save(order);

        idempotencyService.markProcessed(idempotencyKey, orderId);

        var event = new OrderCreatedEvent(
                orderId, customerId, request.merchantId(), totalInCents,
                request.items().stream()
                        .map(i -> new OrderCreatedEvent.OrderItemEvent(
                                i.productId(), i.description(), i.quantity(),
                                i.unitPriceInCents(), i.unitPriceInCents() * i.quantity()))
                        .toList(),
                now
        );
        orderEventProducer.publishOrderCreated(event);

        return new CreateOrderResult.Success(orderMapper.toResponse(order), true);
    }

    public OrderDetailResponse getOrder(UUID orderId, UUID userId, String role, UUID merchantId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        authorizeAccess(order, userId, role, merchantId);
        return orderMapper.toDetailResponse(order);
    }

    public PagedResponse<OrderResponse> listOrders(
            UUID userId, String role, UUID merchantId,
            String statusFilter, int page, int size) {

        var actualSize = Math.min(size, MAX_PAGE_SIZE);
        var pageable = PageRequest.of(page, actualSize, Sort.by("createdAt").descending());

        Page<Order> orderPage;
        if ("ADMIN".equals(role)) {
            orderPage = statusFilter != null
                    ? orderRepository.findByCustomerIdAndStatus(null, OrderStatus.valueOf(statusFilter), pageable)
                    : orderRepository.findAll(pageable);
        } else if ("MERCHANT".equals(role) && merchantId != null) {
            orderPage = statusFilter != null
                    ? orderRepository.findByMerchantIdAndStatus(merchantId, OrderStatus.valueOf(statusFilter), pageable)
                    : orderRepository.findByMerchantId(merchantId, pageable);
        } else {
            orderPage = statusFilter != null
                    ? orderRepository.findByCustomerIdAndStatus(userId, OrderStatus.valueOf(statusFilter), pageable)
                    : orderRepository.findByCustomerId(userId, pageable);
        }

        var items = orderMapper.toResponseList(orderPage.getContent());
        return new PagedResponse<>(
                items,
                orderPage.getNumber(),
                orderPage.getSize(),
                orderPage.getTotalElements(),
                orderPage.getTotalPages()
        );
    }

    @Transactional
    public void cancelOrder(UUID orderId, UUID userId, String role, UUID merchantId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        authorizeAccess(order, userId, role, merchantId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderCannotBeCancelledException(orderId, order.getStatus().name());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        order.setExpiresAt(null);
        orderRepository.save(order);
    }

    private void validateItems(List<ItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new EmptyOrderException();
        }
        for (var item : items) {
            if (item.unitPriceInCents() <= 0 || item.unitPriceInCents() > 999999) {
                throw new InvalidItemPriceException(item.unitPriceInCents());
            }
            if (item.quantity() <= 0 || item.quantity() > 999) {
                throw new InvalidQuantityException(item.quantity());
            }
        }
    }

    private long calculateTotal(List<ItemRequest> items) {
        return items.stream()
                .mapToLong(item -> item.unitPriceInCents() * item.quantity())
                .sum();
    }

    private OrderItem buildItem(Order order, ItemRequest request) {
        var item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProductId(request.productId());
        item.setDescription(request.description());
        item.setQuantity(request.quantity());
        item.setUnitPriceInCents(request.unitPriceInCents());
        item.setSubtotalInCents(request.unitPriceInCents() * request.quantity());
        return item;
    }

    void authorizeAccess(Order order, UUID userId, String role, UUID merchantId) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("MERCHANT".equals(role)) {
            if (order.getMerchantId().equals(merchantId)) {
                return;
            }
        }
        if (order.getCustomerId().equals(userId)) {
            return;
        }
        throw new InsufficientPermissionsException("Access denied to order " + order.getId());
    }

    public sealed interface CreateOrderResult {
        record Success(OrderResponse order, boolean created) implements CreateOrderResult {}
        record Duplicate(OrderResponse existingOrder) implements CreateOrderResult {}
    }

    public static class EmptyOrderException extends RuntimeException {
        public EmptyOrderException() {
            super("Order must have at least one item");
        }
    }

    public static class InvalidItemPriceException extends RuntimeException {
        private final long price;
        public InvalidItemPriceException(long price) {
            super("Invalid item price: " + price);
            this.price = price;
        }
        public long getPrice() { return price; }
    }

    public static class InvalidQuantityException extends RuntimeException {
        private final int quantity;
        public InvalidQuantityException(int quantity) {
            super("Invalid quantity: " + quantity);
            this.quantity = quantity;
        }
        public int getQuantity() { return quantity; }
    }

    public static class OrderTotalExceedsLimitException extends RuntimeException {
        private final long total;
        public OrderTotalExceedsLimitException(long total) {
            super("Order total exceeds limit: " + total);
            this.total = total;
        }
        public long getTotal() { return total; }
    }
}
