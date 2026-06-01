package com.acaboumony.order.controller;

import com.acaboumony.order.dto.ApiResponse;
import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.request.ItemRequest;
import com.acaboumony.order.dto.response.OrderDetailResponse;
import com.acaboumony.order.dto.response.OrderResponse;
import com.acaboumony.order.dto.response.PagedResponse;
import com.acaboumony.order.exception.InsufficientPermissionsException;
import com.acaboumony.order.exception.OrderCannotBeCancelledException;
import com.acaboumony.order.exception.OrderNotFoundException;
import com.acaboumony.order.service.OrderService;
import com.acaboumony.order.service.OrderService.CreateOrderResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createOrderShouldReturn201() throws Exception {
        var customerId = UUID.randomUUID();
        var idempotencyKey = UUID.randomUUID();
        var merchantId = UUID.randomUUID();
        var orderId = UUID.randomUUID();

        var request = new CreateOrderRequest(
                merchantId,
                List.of(new ItemRequest("prod-1", "Item 1", 1, 1000L))
        );

        var response = new OrderResponse(orderId, "PENDING", 1000L,
                List.of(new OrderResponse.ItemResponse("prod-1", "Item 1", 1, 1000L, 1000L)),
                Instant.now().plusSeconds(900), Instant.now());

        when(orderService.createOrder(eq(customerId), any(), eq(idempotencyKey), any(CreateOrderRequest.class)))
                .thenReturn(new CreateOrderResult.Success(response, true));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", customerId)
                        .header("X-User-Email", "customer@test.com")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalInCents").value(1000))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void createOrderShouldReturn200ForDuplicate() throws Exception {
        var customerId = UUID.randomUUID();
        var idempotencyKey = UUID.randomUUID();
        var orderId = UUID.randomUUID();

        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new ItemRequest("prod-1", "Item 1", 1, 1000L))
        );

        var response = new OrderResponse(orderId, "PENDING", 1000L,
                List.of(new OrderResponse.ItemResponse("prod-1", "Item 1", 1, 1000L, 1000L)),
                Instant.now().plusSeconds(900), Instant.now());

        when(orderService.createOrder(eq(customerId), any(), eq(idempotencyKey), any(CreateOrderRequest.class)))
                .thenReturn(new CreateOrderResult.Duplicate(response));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", customerId)
                        .header("X-User-Email", "customer@test.com")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getOrderShouldReturn200() throws Exception {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        var detail = new OrderDetailResponse(orderId, customerId, UUID.randomUUID(), "PAID",
                1000L, List.of(), "txn_123",
                Instant.now(), Instant.now(), null);

        when(orderService.getOrder(eq(orderId), eq(customerId), eq("CUSTOMER"), any()))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("X-User-Id", customerId)
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void getOrderShouldReturn404() throws Exception {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        when(orderService.getOrder(eq(orderId), eq(customerId), eq("CUSTOMER"), any()))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("X-User-Id", customerId)
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void getOrderShouldReturn403() throws Exception {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        when(orderService.getOrder(eq(orderId), eq(customerId), eq("CUSTOMER"), any()))
                .thenThrow(new InsufficientPermissionsException("Access denied"));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("X-User-Id", customerId)
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void listOrdersShouldReturn200() throws Exception {
        var customerId = UUID.randomUUID();
        var paged = new PagedResponse<OrderResponse>(List.of(), 0, 20, 0, 0);

        when(orderService.listOrders(eq(customerId), eq("CUSTOMER"), any(), eq(null), eq(0), eq(20)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Id", customerId)
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void cancelOrderShouldReturn204() throws Exception {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
                        .header("X-User-Id", customerId)
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelOrderShouldReturn422() throws Exception {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        doThrow(new OrderCannotBeCancelledException(orderId, "PAID"))
                .when(orderService).cancelOrder(orderId, customerId, "CUSTOMER", null);

        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
                        .header("X-User-Id", customerId)
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("ORDER_CANNOT_BE_CANCELLED"));
    }

    @Test
    void createOrderShouldReturn400WhenInvalid() throws Exception {
        var customerId = UUID.randomUUID();

        var request = new CreateOrderRequest(null, List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", customerId)
                        .header("X-User-Email", "customer@test.com")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void createOrderShouldReturn400ForInvalidItemPrice() throws Exception {
        var customerId = UUID.randomUUID();
        var idempotencyKey = UUID.randomUUID();

        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new ItemRequest("p1", "Cheap item", 1, 1000L))
        );

        when(orderService.createOrder(eq(customerId), any(), eq(idempotencyKey), any(CreateOrderRequest.class)))
                .thenThrow(new OrderService.InvalidItemPriceException(1000L));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", customerId)
                        .header("X-User-Email", "customer@test.com")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_ITEM_PRICE"));
    }

    @Test
    void listOrdersShouldFilterByStatus() throws Exception {
        var customerId = UUID.randomUUID();
        var paged = new PagedResponse<OrderResponse>(List.of(), 0, 20, 0, 0);

        when(orderService.listOrders(eq(customerId), eq("CUSTOMER"), any(), eq("PENDING"), eq(0), eq(20)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Id", customerId)
                        .header("X-User-Roles", "CUSTOMER")
                        .param("status", "PENDING"))
                .andExpect(status().isOk());
    }
}
