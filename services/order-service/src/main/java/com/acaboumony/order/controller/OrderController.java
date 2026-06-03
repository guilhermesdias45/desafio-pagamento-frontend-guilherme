package com.acaboumony.order.controller;

import com.acaboumony.order.dto.ApiResponse;
import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.response.OrderDetailResponse;
import com.acaboumony.order.dto.response.OrderResponse;
import com.acaboumony.order.dto.response.PagedResponse;
import com.acaboumony.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestHeader("X-User-Email") String customerEmail,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        var result = orderService.createOrder(customerId, customerEmail, idempotencyKey, request);

        return switch (result) {
            case OrderService.CreateOrderResult.Success(var order, var created) ->
                    ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK)
                            .body(ApiResponse.success(order));
            case OrderService.CreateOrderResult.Duplicate(var existingOrder) ->
                    ResponseEntity.ok(ApiResponse.success(existingOrder));
        };
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role,
            @RequestHeader(value = "X-Merchant-Id", required = false) UUID merchantId) {

        var order = orderService.getOrder(orderId, userId, role, merchantId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> listOrders(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role,
            @RequestHeader(value = "X-Merchant-Id", required = false) UUID merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = orderService.listOrders(userId, role, merchantId, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role,
            @RequestHeader(value = "X-Merchant-Id", required = false) UUID merchantId) {

        orderService.cancelOrder(orderId, userId, role, merchantId);
        return ResponseEntity.noContent().build();
    }
}
