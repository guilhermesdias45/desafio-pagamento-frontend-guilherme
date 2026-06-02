package com.acaboumony.payment.client;

import com.acaboumony.payment.client.mp.*;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoGatewayImplTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    MercadoPagoGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new MercadoPagoGatewayImpl(
                wireMock.baseUrl(),
                "TEST-ACCESS-TOKEN",
                800
        );
    }

    @Test
    void processes_approved_payment() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 1234567890,
                                  "status": "approved",
                                  "status_detail": "accredited",
                                  "payment_method_id": "visa",
                                  "card": {
                                    "last_four_digits": "4242",
                                    "first_six_digits": "411111",
                                    "brand": "visa"
                                  }
                                }
                                """)));

        MpPaymentRequest request = buildRequest();
        MpPaymentResult result = gateway.processPayment(request);

        assertThat(result).isInstanceOf(MpPaymentResult.Approved.class);
        MpPaymentResult.Approved approved = (MpPaymentResult.Approved) result;
        assertThat(approved.mpPaymentId()).isEqualTo(1234567890L);
        assertThat(approved.statusDetail()).isEqualTo("accredited");
        assertThat(approved.cardLastFour()).isEqualTo("4242");
        assertThat(approved.cardBrand()).isEqualTo("visa");
    }

    @Test
    void handles_rejected_payment() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 123,
                                  "status": "rejected",
                                  "status_detail": "cc_rejected_card_type_not_allowed"
                                }
                                """)));

        MpPaymentResult result = gateway.processPayment(buildRequest());

        assertThat(result).isInstanceOf(MpPaymentResult.Rejected.class);
        MpPaymentResult.Rejected rejected = (MpPaymentResult.Rejected) result;
        assertThat(rejected.statusDetail()).isEqualTo("cc_rejected_card_type_not_allowed");
    }

    @Test
    void handles_insufficient_funds() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 456,
                                  "status": "rejected",
                                  "status_detail": "cc_rejected_insufficient_amount"
                                }
                                """)));

        MpPaymentResult result = gateway.processPayment(buildRequest());

        assertThat(result).isInstanceOf(MpPaymentResult.Rejected.class);
        MpPaymentResult.Rejected rejected = (MpPaymentResult.Rejected) result;
        assertThat(rejected.statusDetail()).isEqualTo("cc_rejected_insufficient_amount");
    }

    @Test
    void handles_timeout() {
        // Simulate a connection reset / timeout by using a fixed delay greater than timeout
        wireMock.stubFor(post(urlEqualTo("/v1/payments"))
                .willReturn(aResponse()
                        .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        MpPaymentResult result = gateway.processPayment(buildRequest());

        assertThat(result).isInstanceOf(MpPaymentResult.Timeout.class);
    }

    @Test
    void processes_refund_successfully() {
        long mpPaymentId = 1234567890L;
        wireMock.stubFor(post(urlEqualTo("/v1/payments/" + mpPaymentId + "/refunds"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 456789,
                                  "payment_id": 1234567890,
                                  "amount": 50.00,
                                  "status": "approved"
                                }
                                """)));

        MpRefundResult result = gateway.refundPayment(mpPaymentId, 5000L);

        assertThat(result).isInstanceOf(MpRefundResult.Success.class);
        MpRefundResult.Success success = (MpRefundResult.Success) result;
        assertThat(success.mpRefundId()).isEqualTo(456789L);
    }

    private MpPaymentRequest buildRequest() {
        return new MpPaymentRequest(
                5000L, "test_card_token", "visa", 1,
                "pagamento@acaboumony.com", UUID.randomUUID(), UUID.randomUUID()
        );
    }
}
