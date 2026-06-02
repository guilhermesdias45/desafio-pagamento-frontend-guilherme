package com.acaboumony.payment.client.order;

import com.acaboumony.payment.config.InternalSecretProperties;
import com.acaboumony.payment.exception.OrderNotFoundException;
import com.acaboumony.payment.exception.OrderNotPendingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Client for the order-service internal API.
 * Validates that an order exists and is in PENDING status before processing payment.
 */
@Component
public class OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

    private final RestClient restClient;
    private final InternalSecretProperties internalSecretProperties;

    public OrderServiceClient(
            @Value("${order-service.url}") String orderServiceUrl,
            InternalSecretProperties internalSecretProperties
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(orderServiceUrl)
                .build();
        this.internalSecretProperties = internalSecretProperties;
    }

    /**
     * Fetches an order by ID and validates it is in PENDING status.
     *
     * @throws OrderNotFoundException    if the order does not exist (404)
     * @throws OrderNotPendingException  if the order is not in PENDING status
     */
    public InternalOrderResponse getOrder(UUID orderId) {
        try {
            InternalOrderResponse response = restClient.get()
                    .uri("/internal/orders/{orderId}", orderId)
                    .header("X-Internal-Secret", internalSecretProperties.secret())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            throw new OrderNotFoundException(orderId.toString());
                        }
                        throw new OrderNotFoundException(orderId.toString());
                    })
                    .body(InternalOrderResponse.class);

            if (response == null) {
                throw new OrderNotFoundException(orderId.toString());
            }

            if (!"PENDING".equals(response.status())) {
                throw new OrderNotPendingException(response.status());
            }

            log.info("Order validated: orderId={} status={}", orderId, response.status());
            return response;

        } catch (OrderNotFoundException | OrderNotPendingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching order: orderId={}", orderId, e);
            throw new OrderNotFoundException(orderId.toString());
        }
    }
}
