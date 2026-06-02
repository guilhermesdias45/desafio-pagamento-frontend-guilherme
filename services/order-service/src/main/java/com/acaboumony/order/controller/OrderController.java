package com.acaboumony.order.controller;

import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.response.OrderDetailResponse;
import com.acaboumony.order.dto.response.OrderResponse;
import com.acaboumony.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for order management (customer-facing endpoints).
 *
 * <p>No authentication is enforced here — the api-gateway injects trusted headers
 * ({@code X-User-Id}, {@code X-User-Role}, {@code X-Merchant-Id}) after verifying the JWT.
 * This service only processes the headers it receives.</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order.
     *
     * <p>Returns HTTP 201 on creation. Returns HTTP 200 when the idempotency key was already
     * processed (safe retry).</p>
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {

        boolean isIdempotencyHit = orderService.hasExistingOrder(request.idempotencyKey());
        OrderResponse response = orderService.createOrder(request, userId);
        return isIdempotencyHit
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a single order by ID with authorization enforcement.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader(value = "X-Merchant-Id", required = false) UUID merchantId) {

        OrderDetailResponse response = orderService.getOrder(orderId, userId, userRole, merchantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists orders for the authenticated user with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<OrderDetailResponse>> listOrders(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader(value = "X-Merchant-Id", required = false) UUID merchantId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<OrderDetailResponse> page = orderService.listOrders(userId, userRole, merchantId, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * Cancels a PENDING order.
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {

        orderService.cancelOrder(orderId, userId, userRole);
        return ResponseEntity.noContent().build();
    }
}
