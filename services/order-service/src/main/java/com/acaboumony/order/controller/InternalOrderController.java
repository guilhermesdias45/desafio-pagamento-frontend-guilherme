package com.acaboumony.order.controller;

import com.acaboumony.order.dto.response.InternalOrderResponse;
import com.acaboumony.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal REST controller for inter-service communication (payment-service → order-service).
 *
 * <p>Protected by {@link com.acaboumony.order.security.InternalSecretFilter} via the
 * {@code X-Internal-Secret} header. Not exposed through the public api-gateway.</p>
 */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<InternalOrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrderInternal(orderId));
    }
}
