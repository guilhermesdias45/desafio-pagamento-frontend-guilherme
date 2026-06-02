package com.acaboumony.order.controller;

import com.acaboumony.order.config.InternalSecretProperties;
import com.acaboumony.order.config.OrderProperties;
import com.acaboumony.order.dto.request.CreateOrderRequest;
import com.acaboumony.order.dto.request.OrderItemRequest;
import com.acaboumony.order.dto.response.InternalOrderResponse;
import com.acaboumony.order.dto.response.OrderDetailResponse;
import com.acaboumony.order.dto.response.OrderItemResponse;
import com.acaboumony.order.dto.response.OrderResponse;
import com.acaboumony.order.exception.InsufficientPermissionsException;
import com.acaboumony.order.exception.OrderCannotBeCancelledException;
import com.acaboumony.order.security.InternalSecretFilter;
import com.acaboumony.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {OrderController.class, InternalOrderController.class})
@Import({OrderControllerTest.TestConfig.class, GlobalExceptionHandler.class, InternalSecretFilter.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @TestConfiguration
    static class TestConfig {
        @Bean
        public InternalSecretProperties internalSecretProperties() {
            return new InternalSecretProperties("test-internal-secret");
        }

        @Bean
        public OrderProperties orderProperties() {
            return new OrderProperties(15);
        }
    }

    @Test
    void returns_201_on_create_order() throws Exception {
        CreateOrderRequest req = buildCreateRequest();
        OrderResponse resp = buildOrderResponse();

        when(orderService.hasExistingOrder(any())).thenReturn(false);
        when(orderService.createOrder(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "CUSTOMER_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalInCents").value(1000));
    }

    @Test
    void returns_200_on_duplicate_idempotency_key() throws Exception {
        CreateOrderRequest req = buildCreateRequest();
        OrderResponse resp = buildOrderResponse();

        when(orderService.hasExistingOrder(any())).thenReturn(true);
        when(orderService.createOrder(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "CUSTOMER_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returns_400_on_empty_items() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest(MERCHANT_ID, List.of(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "CUSTOMER_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns_200_on_get_order() throws Exception {
        OrderDetailResponse detail = buildOrderDetailResponse();
        when(orderService.getOrder(eq(ORDER_ID), any(), any(), any())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "CUSTOMER_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()));
    }

    @Test
    void returns_403_on_unauthorized_access() throws Exception {
        when(orderService.getOrder(eq(ORDER_ID), any(), any(), any()))
                .thenThrow(new InsufficientPermissionsException());

        mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "CUSTOMER_OWNER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void returns_204_on_cancel_order() throws Exception {
        doNothing().when(orderService).cancelOrder(eq(ORDER_ID), any(), any());

        mockMvc.perform(delete("/api/v1/orders/{orderId}", ORDER_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "CUSTOMER_OWNER"))
                .andExpect(status().isNoContent());
    }

    @Test
    void returns_422_when_cancelling_paid_order() throws Exception {
        doThrow(new OrderCannotBeCancelledException("PAID"))
                .when(orderService).cancelOrder(eq(ORDER_ID), any(), any());

        mockMvc.perform(delete("/api/v1/orders/{orderId}", ORDER_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "CUSTOMER_OWNER"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ORDER_CANNOT_BE_CANCELLED"));
    }

    @Test
    void internal_endpoint_returns_403_without_secret() throws Exception {
        mockMvc.perform(get("/internal/orders/{orderId}", ORDER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_endpoint_returns_200_with_correct_secret() throws Exception {
        InternalOrderResponse resp = new InternalOrderResponse(
                ORDER_ID, "PENDING", 1000L, MERCHANT_ID, USER_ID);
        when(orderService.getOrderInternal(ORDER_ID)).thenReturn(resp);

        mockMvc.perform(get("/internal/orders/{orderId}", ORDER_ID)
                        .header("X-Internal-Secret", "test-internal-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private CreateOrderRequest buildCreateRequest() {
        return new CreateOrderRequest(
                MERCHANT_ID,
                List.of(new OrderItemRequest("P1", "Product 1", 2, 500L)),
                UUID.randomUUID()
        );
    }

    private OrderResponse buildOrderResponse() {
        return new OrderResponse(
                ORDER_ID, "PENDING", 1000L,
                List.of(new OrderItemResponse("P1", "Product 1", 2, 500L, 1000L)),
                Instant.now().plusSeconds(900),
                Instant.now()
        );
    }

    private OrderDetailResponse buildOrderDetailResponse() {
        return new OrderDetailResponse(
                ORDER_ID, USER_ID, MERCHANT_ID, "PENDING", 1000L,
                List.of(new OrderItemResponse("P1", "Product 1", 2, 500L, 1000L)),
                null, Instant.now(), Instant.now(), Instant.now().plusSeconds(900)
        );
    }
}
