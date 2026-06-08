package com.acaboumony.payment.client;

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
import java.util.UUID;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestTemplate restTemplate;
    private final String userServiceUrl;
    private final String internalSecret;
    private final CircuitBreaker circuitBreaker;

    public UserServiceClient(
            @Value("${user.service.url}") String userServiceUrl,
            @Value("${payment.internal-secret:dev-secret}") String internalSecret,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.userServiceUrl = userServiceUrl;
        this.internalSecret = internalSecret;
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(2000))
            .setReadTimeout(Duration.ofMillis(2000))
            .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("userService");
    }

    public UserValidationResult validateCustomer(UUID customerId) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                var headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internalSecret);
                headers.set("X-User-Id", customerId.toString());

                var entity = new HttpEntity<Void>(headers);
                ResponseEntity<UserResponse> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/{customerId}",
                    HttpMethod.GET,
                    entity,
                    UserResponse.class,
                    customerId
                );

                return response.getBody() != null
                    ? new UserValidationResult(true, null)
                    : new UserValidationResult(false, "CUSTOMER_NOT_FOUND");
            });
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return new UserValidationResult(false, "CUSTOMER_NOT_FOUND");
        } catch (Exception e) {
            log.warn("User service unavailable or error: {}", e.getMessage());
            return new UserValidationResult(false, "SERVICE_UNAVAILABLE");
        }
    }

    public record UserValidationResult(boolean valid, String errorCode) {}

    private record UserResponse(UUID id, String email, String role) {}
}
