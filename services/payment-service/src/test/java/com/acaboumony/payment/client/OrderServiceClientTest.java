package com.acaboumony.payment.client;

import com.acaboumony.payment.client.order.InternalOrderResponse;
import com.acaboumony.payment.client.order.OrderServiceClient;
import com.acaboumony.payment.config.InternalSecretProperties;
import com.acaboumony.payment.exception.OrderNotFoundException;
import com.acaboumony.payment.exception.OrderNotPendingException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    OrderServiceClient orderServiceClient;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderServiceClient = new OrderServiceClient(
                wireMock.baseUrl(),
                new InternalSecretProperties("test-secret")
        );
    }

    @Test
    void returns_order_when_found_and_pending() {
        wireMock.stubFor(get(urlEqualTo("/internal/orders/" + ORDER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "orderId": "%s",
                                  "status": "PENDING",
                                  "totalInCents": 5000,
                                  "merchantId": "%s",
                                  "customerId": "%s"
                                }
                                """.formatted(ORDER_ID, MERCHANT_ID, CUSTOMER_ID))));

        InternalOrderResponse order = orderServiceClient.getOrder(ORDER_ID);

        assertThat(order.orderId()).isEqualTo(ORDER_ID);
        assertThat(order.status()).isEqualTo("PENDING");
        assertThat(order.merchantId()).isEqualTo(MERCHANT_ID);

        wireMock.verify(getRequestedFor(urlEqualTo("/internal/orders/" + ORDER_ID))
                .withHeader("X-Internal-Secret", equalTo("test-secret")));
    }

    @Test
    void throws_order_not_found_when_404() {
        wireMock.stubFor(get(urlEqualTo("/internal/orders/" + ORDER_ID))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> orderServiceClient.getOrder(ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void throws_order_not_pending_when_already_paid() {
        wireMock.stubFor(get(urlEqualTo("/internal/orders/" + ORDER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "orderId": "%s",
                                  "status": "PAID",
                                  "totalInCents": 5000,
                                  "merchantId": "%s",
                                  "customerId": "%s"
                                }
                                """.formatted(ORDER_ID, MERCHANT_ID, CUSTOMER_ID))));

        assertThatThrownBy(() -> orderServiceClient.getOrder(ORDER_ID))
                .isInstanceOf(OrderNotPendingException.class);
    }
}
