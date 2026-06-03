package com.acaboumony.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String orderServiceUrl;
    private final CircuitBreaker circuitBreaker;

    public OrderServiceClient(@Value("${order.service.url}") String orderServiceUrl,
                              CircuitBreakerRegistry circuitBreakerRegistry) {
        this.orderServiceUrl = orderServiceUrl;
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(500))
            .setReadTimeout(Duration.ofMillis(500))
            .build();
        this.objectMapper = new ObjectMapper();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("orderService");
    }

    public OrderValidationResult validateOrder(UUID orderId, UUID merchantId) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                var headers = new HttpHeaders();
                headers.set("X-User-Id", "00000000-0000-0000-0000-000000000000");
                headers.set("X-User-Role", "ADMIN");
                headers.set("X-Merchant-Id", merchantId.toString());

                var entity = new HttpEntity<Void>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                    orderServiceUrl + "/api/v1/orders/{orderId}",
                    HttpMethod.GET,
                    entity,
                    Map.class,
                    orderId
                );

                var body = response.getBody();
                if (body == null) {
                    return new OrderValidationResult(false, "ORDER_NOT_FOUND");
                }

                var rawData = body.get("data");
                if (rawData == null) {
                    return new OrderValidationResult(false, "ORDER_NOT_FOUND");
                }

                var orderData = objectMapper.convertValue(rawData, OrderData.class);
                if (!"PENDING".equals(orderData.status())) {
                    return new OrderValidationResult(false, "ORDER_NOT_PENDING");
                }

                return new OrderValidationResult(true, null);
            });
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return new OrderValidationResult(false, "ORDER_NOT_FOUND");
        } catch (Exception e) {
            log.warn("Order service unavailable or error: {}", e.getMessage());
            return new OrderValidationResult(false, "ORDER_SERVICE_UNAVAILABLE");
        }
    }

    public record OrderValidationResult(boolean valid, String errorCode) {}

    private record OrderData(
        UUID orderId,
        UUID customerId,
        UUID merchantId,
        String status,
        Long totalInCents
    ) {}
}
