package com.acaboumony.payment.client;

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

    public UserServiceClient(@Value("${user.service.url}") String userServiceUrl) {
        this.userServiceUrl = userServiceUrl;
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(300))
            .setReadTimeout(Duration.ofMillis(300))
            .build();
    }

    public UserValidationResult validateCustomer(UUID customerId) {
        try {
            var headers = new HttpHeaders();
            headers.set("X-Internal-Secret", "internal-payment-service");
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
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return new UserValidationResult(false, "CUSTOMER_NOT_FOUND");
        } catch (Exception e) {
            log.warn("User service unavailable or error: {}", e.getMessage());
            return new UserValidationResult(true, null);
        }
    }

    public record UserValidationResult(boolean valid, String errorCode) {}

    private record UserResponse(UUID id, String email, String role) {}
}
