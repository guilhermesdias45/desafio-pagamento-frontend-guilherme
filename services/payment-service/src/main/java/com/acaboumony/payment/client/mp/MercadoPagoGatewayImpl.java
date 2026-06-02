package com.acaboumony.payment.client.mp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * MercadoPago gateway implementation using RestClient.
 * Uses the MP REST API directly for testability with WireMock.
 */
@Component
public class MercadoPagoGatewayImpl implements MercadoPagoGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGatewayImpl.class);

    private final RestClient restClient;

    public MercadoPagoGatewayImpl(
            @Value("${mercadopago.base-url}") String baseUrl,
            @Value("${mercadopago.access-token}") String accessToken,
            @Value("${mercadopago.timeout-ms:800}") int timeoutMs
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public MpPaymentResult processPayment(MpPaymentRequest request) {
        try {
            Map<String, Object> body = buildPaymentBody(request);

            MpPaymentApiResponse response = restClient.post()
                    .uri("/v1/payments")
                    .header("X-Idempotency-Key", request.idempotencyKey().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MpPaymentApiResponse.class);

            if (response == null) {
                log.error("Null response from Mercado Pago API");
                return new MpPaymentResult.Timeout();
            }

            if ("approved".equals(response.status())) {
                String cardBrand = response.card() != null ? response.card().brand() : null;
                String lastFour = response.card() != null ? response.card().lastFourDigits() : null;
                log.info("MP payment approved: id={}, brand={}, last4={}", response.id(), cardBrand, lastFour);
                return new MpPaymentResult.Approved(response.id(), response.statusDetail(), cardBrand, lastFour);
            } else {
                log.warn("MP payment rejected: id={}, statusDetail={}", response.id(), response.statusDetail());
                return new MpPaymentResult.Rejected(response.statusDetail());
            }

        } catch (ResourceAccessException e) {
            log.warn("Mercado Pago timeout or connection error", e);
            return new MpPaymentResult.Timeout();
        } catch (RestClientResponseException e) {
            log.error("Mercado Pago HTTP error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return new MpPaymentResult.Rejected("gateway_error");
        } catch (Exception e) {
            log.error("Unexpected error calling Mercado Pago", e);
            return new MpPaymentResult.Timeout();
        }
    }

    @Override
    public MpRefundResult refundPayment(long mpPaymentId, Long amountInCents) {
        try {
            Map<String, Object> body = new HashMap<>();
            if (amountInCents != null) {
                BigDecimal amount = BigDecimal.valueOf(amountInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                body.put("amount", amount);
            }

            MpRefundApiResponse response = restClient.post()
                    .uri("/v1/payments/{id}/refunds", mpPaymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MpRefundApiResponse.class);

            if (response == null) {
                return new MpRefundResult.Failure("Null response from refund API");
            }

            log.info("MP refund created: refundId={}, paymentId={}", response.id(), mpPaymentId);
            return new MpRefundResult.Success(response.id());

        } catch (Exception e) {
            log.error("Error processing refund for paymentId={}", mpPaymentId, e);
            return new MpRefundResult.Failure(e.getMessage());
        }
    }

    private Map<String, Object> buildPaymentBody(MpPaymentRequest request) {
        BigDecimal amount = BigDecimal.valueOf(request.amountInCents())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Map<String, Object> payer = new HashMap<>();
        payer.put("email", request.payerEmail());

        Map<String, Object> body = new HashMap<>();
        body.put("transaction_amount", amount);
        body.put("token", request.cardToken());
        body.put("description", "Pagamento Acabou o Mony");
        body.put("installments", request.installments());
        body.put("payment_method_id", request.paymentMethodId());
        body.put("payer", payer);
        body.put("external_reference", request.externalReference().toString());
        return body;
    }

    // Internal response DTOs for deserializing MP API responses
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MpPaymentApiResponse(
            long id,
            String status,
            @JsonProperty("status_detail") String statusDetail,
            @JsonProperty("payment_method_id") String paymentMethodId,
            @JsonProperty("card") MpCardInfo card
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MpCardInfo(
            @JsonProperty("last_four_digits") String lastFourDigits,
            @JsonProperty("first_six_digits") String firstSixDigits,
            String brand
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MpRefundApiResponse(
            long id,
            @JsonProperty("payment_id") long paymentId,
            BigDecimal amount,
            String status
    ) {}
}
