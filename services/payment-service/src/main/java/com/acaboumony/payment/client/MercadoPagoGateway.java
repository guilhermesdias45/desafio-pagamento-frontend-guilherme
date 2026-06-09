package com.acaboumony.payment.client;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class MercadoPagoGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGateway.class);

    private final PaymentClient paymentClient;
    private final ExecutorService executor;
    private final long timeoutMs;
    private final CircuitBreaker circuitBreaker;

    public MercadoPagoGateway(
            @Value("${mercadopago.timeout-ms:800}") long timeoutMs,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.paymentClient = new PaymentClient();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.timeoutMs = timeoutMs;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("mercadoPago");
    }

    public PaymentResult createPayment(String cardToken, Long amountInCents,
                                        String paymentMethodId, Integer installments,
                                        UUID orderId, String customerEmail) {
        return createPayment(cardToken, amountInCents, paymentMethodId,
            installments, orderId, customerEmail, null);
    }

    public PaymentResult createPayment(String cardToken, Long amountInCents,
                                        String paymentMethodId, Integer installments,
                                        UUID orderId, String customerEmail,
                                        @Nullable String sellerAccessToken) {
        try {
            return circuitBreaker.executeSupplier(() ->
                doCreatePayment(cardToken, amountInCents, paymentMethodId,
                    installments, orderId, customerEmail, sellerAccessToken));
        } catch (Exception e) {
            log.warn("MP gateway circuit breaker fallback: {}", e.getMessage());
            return PaymentResult.timeout();
        }
    }

    private PaymentResult doCreatePayment(String cardToken, Long amountInCents,
                                           String paymentMethodId, Integer installments,
                                           UUID orderId, String customerEmail,
                                           @Nullable String sellerAccessToken) {
        var start = Instant.now();
        var future = CompletableFuture.supplyAsync(() -> {
            try {
                var request = PaymentCreateRequest.builder()
                    .transactionAmount(new BigDecimal(amountInCents).divide(new BigDecimal(100)))
                    .token(cardToken)
                    .description("Acabou o Mony - Pedido " + orderId)
                    .installments(installments)
                    .paymentMethodId(paymentMethodId)
                    .payer(PaymentPayerRequest.builder()
                        .email(customerEmail).build())
                    .build();

                if (sellerAccessToken != null) {
                    var requestOptions = MPRequestOptions.builder()
                        .accessToken(sellerAccessToken)
                        .build();
                    return paymentClient.create(request, requestOptions);
                }
                return paymentClient.create(request);
            } catch (MPApiException e) {
                throw new CompletionException(e);
            } catch (MPException e) {
                throw new CompletionException(e);
            }
        }, executor);

        try {
            Payment payment = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("MP payment created in {}ms: id={}, status={}",
                Duration.between(start, Instant.now()).toMillis(),
                payment.getId(), payment.getStatus());

            return switch (payment.getStatus()) {
                case "approved" -> PaymentResult.approved(payment.getId());
                case "rejected" -> PaymentResult.declined(
                    payment.getStatusDetail() != null ? payment.getStatusDetail() : "CARD_DECLINED");
                default -> PaymentResult.declined("UNEXPECTED_STATUS");
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (TimeoutException e) {
            log.warn("MP gateway timeout after {}ms", Duration.between(start, Instant.now()).toMillis());
            throw new CompletionException(e);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof MPApiException mpApi) {
                var status = mpApi.getStatusCode();
                if (status == 400 || status == 422) {
                    return PaymentResult.declined("CARD_DECLINED");
                }
            }
            throw new CompletionException("MP gateway error", e.getCause());
        }
    }

    public RefundResult refundPayment(Long mpPaymentId, Long amountInCents) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    BigDecimal amount = amountInCents != null
                        ? new BigDecimal(amountInCents).divide(new BigDecimal(100))
                        : null;
                    var refund = paymentClient.refund(mpPaymentId, amount);
                    return new RefundResult(true, refund.getId());
                } catch (MPApiException e) {
                    throw new CompletionException(e);
                } catch (MPException e) {
                    throw new CompletionException(e);
                }
            });
        } catch (Exception e) {
            log.error("MP refund error for payment {}: {}", mpPaymentId, e.getMessage());
            return new RefundResult(false, null);
        }
    }

    public record PaymentResult(
        boolean success,
        Long mpPaymentId,
        String errorCode,
        boolean isTimeout
    ) {
        public static PaymentResult approved(Long mpPaymentId) {
            return new PaymentResult(true, mpPaymentId, null, false);
        }
        public static PaymentResult declined(String errorCode) {
            return new PaymentResult(false, null, errorCode, false);
        }
        public static PaymentResult timeout() {
            return new PaymentResult(false, null, "MP_GATEWAY_TIMEOUT", true);
        }
    }

    public record RefundResult(boolean success, Long mpRefundId) {}
}
