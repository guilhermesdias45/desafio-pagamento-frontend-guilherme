package com.acaboumony.order.service;

import com.acaboumony.order.config.OrderProperties;
import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.domain.entity.OrderItem;
import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.request.OrderItemRequest;
import com.acaboumony.order.dto.response.InternalOrderResponse;
import com.acaboumony.order.dto.response.OrderDetailResponse;
import com.acaboumony.order.dto.response.OrderItemResponse;
import com.acaboumony.order.dto.response.OrderResponse;
import com.acaboumony.order.event.OrderEventProducer;
import com.acaboumony.order.exception.*;
import com.acaboumony.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core business logic for order creation, retrieval, cancellation, and payment lifecycle transitions.
 *
 * <p>All monetary totals are calculated server-side in cents. Client-provided totals are ignored.</p>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Maximum order total in cents (R$ 9,999.99). */
    static final long MAX_TOTAL_IN_CENTS = 999_999L;

    /** Redis key prefix for idempotency deduplication. TTL: 24h. */
    static final String IDEM_KEY_PREFIX = "order:idem:";
    static final Duration IDEM_TTL = Duration.ofHours(24);

    private final OrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;
    private final OrderEventProducer eventProducer;
    private final OrderProperties orderProperties;

    public OrderService(OrderRepository orderRepository,
                        StringRedisTemplate redisTemplate,
                        OrderEventProducer eventProducer,
                        OrderProperties orderProperties) {
        this.orderRepository = orderRepository;
        this.redisTemplate = redisTemplate;
        this.eventProducer = eventProducer;
        this.orderProperties = orderProperties;
    }

    /**
     * Returns {@code true} if the idempotency key already maps to an existing order in Redis.
     *
     * <p>Used by the controller to decide whether to return HTTP 200 (idempotency hit) or 201 (created).</p>
     */
    public boolean hasExistingOrder(UUID idempotencyKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(IDEM_KEY_PREFIX + idempotencyKey));
    }

    /**
     * Creates a new order or returns the existing one for the given idempotency key.
     *
     * <p>totalInCents is always calculated server-side.</p>
     *
     * @param request    validated create order request
     * @param customerId the authenticated customer UUID (from X-User-Id header)
     * @return the created or existing {@link OrderResponse}
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, UUID customerId) {
        // 1. Check Redis for idempotency hit
        String redisKey = IDEM_KEY_PREFIX + request.idempotencyKey();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotency hit for key={} customerId={}", request.idempotencyKey(), customerId);
                return toOrderResponse(existing.get());
            }
        }

        // 2. Validate items list is not empty
        if (request.items() == null || request.items().isEmpty()) {
            throw new EmptyOrderException();
        }

        // 3. Calculate total server-side and validate each item
        long total = 0L;
        for (OrderItemRequest item : request.items()) {
            if (item.quantity() == null || item.quantity() < 1) {
                throw new InvalidQuantityException();
            }
            if (item.unitPriceInCents() == null || item.unitPriceInCents() < 1) {
                throw new InvalidItemPriceException();
            }
            total += (long) item.quantity() * item.unitPriceInCents();
        }

        // 4. Validate total does not exceed limit
        if (total > MAX_TOTAL_IN_CENTS) {
            throw new TotalExceedsLimitException();
        }

        // 5. Build and persist order
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setMerchantId(request.merchantId());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalInCents(total);
        order.setIdempotencyKey(request.idempotencyKey());
        order.setExpiresAt(Instant.now().plus(Duration.ofMinutes(orderProperties.expirationMinutes())));

        for (OrderItemRequest itemReq : request.items()) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(itemReq.productId());
            item.setDescription(itemReq.description());
            item.setQuantity(itemReq.quantity());
            item.setUnitPriceInCents(itemReq.unitPriceInCents());
            item.setSubtotalInCents((long) itemReq.quantity() * itemReq.unitPriceInCents());
            order.getItems().add(item);
        }

        Order saved = orderRepository.save(order);

        // 6. Store idempotency key in Redis
        redisTemplate.opsForValue().set(redisKey, saved.getId().toString(), IDEM_TTL);

        // 7. Publish Kafka event
        eventProducer.publishOrderCreated(saved.getId(), customerId, request.merchantId(), total);

        log.info("Order created orderId={} customerId={} totalInCents={}", saved.getId(), customerId, total);
        return toOrderResponse(saved);
    }

    /**
     * Retrieves a single order with authorization enforcement.
     *
     * <p>Authorization rules:
     * <ul>
     *   <li>CUSTOMER_OWNER / CUSTOMER: only own orders</li>
     *   <li>MERCHANT_OWNER / MERCHANT: only orders belonging to their merchant</li>
     *   <li>ADMIN: any order</li>
     * </ul></p>
     */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrder(UUID orderId,
                                        UUID requestingCustomerId,
                                        String requestingRole,
                                        UUID requestingMerchantId) {
        Order order = findOrderOrThrow(orderId);
        checkReadAccess(order, requestingCustomerId, requestingRole, requestingMerchantId);
        return toOrderDetailResponse(order);
    }

    /**
     * Returns a paginated list of orders based on the caller's role.
     */
    @Transactional(readOnly = true)
    public Page<OrderDetailResponse> listOrders(UUID customerId,
                                                String role,
                                                UUID merchantId,
                                                Pageable pageable) {
        if (role != null && (role.equals("MERCHANT_OWNER") || role.equals("MERCHANT"))) {
            return orderRepository.findByMerchantId(merchantId, pageable)
                    .map(this::toOrderDetailResponse);
        }
        if (role != null && role.equals("ADMIN")) {
            return orderRepository.findAll(pageable).map(this::toOrderDetailResponse);
        }
        // CUSTOMER_OWNER, CUSTOMER, default
        return orderRepository.findByCustomerId(customerId, pageable)
                .map(this::toOrderDetailResponse);
    }

    /**
     * Cancels a PENDING order.
     *
     * <p>Only PENDING orders can be cancelled. Attempting to cancel an order in any other
     * state throws {@link OrderCannotBeCancelledException}.</p>
     */
    @Transactional
    public void cancelOrder(UUID orderId, UUID requestingCustomerId, String requestingRole) {
        Order order = findOrderOrThrow(orderId);

        // Authorization check
        if (!isAdminRole(requestingRole) && !order.getCustomerId().equals(requestingCustomerId)) {
            throw new InsufficientPermissionsException();
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderCannotBeCancelledException(order.getStatus().name());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        eventProducer.publishOrderCancelled(orderId, order.getCustomerId(), "USER_REQUEST");
        log.info("Order cancelled orderId={} by customerId={}", orderId, requestingCustomerId);
    }

    /**
     * Marks an order as PAID after a successful transaction event from payment-service.
     */
    @Transactional
    public void markOrderPaid(UUID orderId, String transactionId) {
        Order order = findOrderOrThrow(orderId);
        order.setStatus(OrderStatus.PAID);
        order.setTransactionId(transactionId);
        orderRepository.save(order);
        log.info("Order marked PAID orderId={} transactionId={}", orderId, transactionId);
    }

    /**
     * Handles a transaction failure: order remains PENDING if not expired.
     */
    @Transactional
    public void markOrderFailed(UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        // Keep as PENDING; expiration scheduler will cancel if TTL passes
        log.info("Transaction failed for orderId={} — order remains {}", orderId, order.getStatus());
    }

    /**
     * Marks an order as REFUNDED (full) or PARTIALLY_REFUNDED based on the event flag.
     */
    @Transactional
    public void markOrderRefunded(UUID orderId, boolean fullRefund) {
        Order order = findOrderOrThrow(orderId);
        order.setStatus(fullRefund ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);
        orderRepository.save(order);
        log.info("Order marked {} orderId={}", order.getStatus(), orderId);
    }

    /**
     * Internal lookup used by the {@code /internal/orders} endpoint for payment-service.
     */
    @Transactional(readOnly = true)
    public InternalOrderResponse getOrderInternal(UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        return new InternalOrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalInCents(),
                order.getMerchantId(),
                order.getCustomerId()
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Order findOrderOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
    }

    private void checkReadAccess(Order order,
                                  UUID requestingCustomerId,
                                  String requestingRole,
                                  UUID requestingMerchantId) {
        if (isAdminRole(requestingRole)) {
            return;
        }
        if (isMerchantRole(requestingRole)) {
            if (requestingMerchantId != null && order.getMerchantId().equals(requestingMerchantId)) {
                return;
            }
            throw new InsufficientPermissionsException();
        }
        // CUSTOMER_OWNER, CUSTOMER, or unknown — must own the order
        if (requestingCustomerId != null && order.getCustomerId().equals(requestingCustomerId)) {
            return;
        }
        throw new InsufficientPermissionsException();
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equals(role);
    }

    private boolean isMerchantRole(String role) {
        return "MERCHANT_OWNER".equals(role) || "MERCHANT".equals(role);
    }

    private OrderResponse toOrderResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalInCents(),
                toItemResponses(order),
                order.getExpiresAt(),
                order.getCreatedAt()
        );
    }

    private OrderDetailResponse toOrderDetailResponse(Order order) {
        return new OrderDetailResponse(
                order.getId(),
                order.getCustomerId(),
                order.getMerchantId(),
                order.getStatus().name(),
                order.getTotalInCents(),
                toItemResponses(order),
                order.getTransactionId(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getExpiresAt()
        );
    }

    private List<OrderItemResponse> toItemResponses(Order order) {
        return order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProductId(),
                        i.getDescription(),
                        i.getQuantity(),
                        i.getUnitPriceInCents(),
                        i.getSubtotalInCents()))
                .toList();
    }
}
