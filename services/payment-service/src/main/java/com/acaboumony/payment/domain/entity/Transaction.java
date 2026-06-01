package com.acaboumony.payment.domain.entity;

import com.acaboumony.payment.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 64)
    private String transactionId;

    @Column(name = "mp_payment_id")
    private Long mpPaymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_in_cents", nullable = false)
    private Long amountInCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "card_brand", length = 32)
    private String cardBrand;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "installments")
    private Integer installments;

    @Column(name = "payment_method_id", nullable = false, length = 32)
    private String paymentMethodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransactionStatus status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "refunded_amount_in_cents")
    private Long refundedAmountInCents = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Transaction() {}

    public Transaction(String transactionId, UUID orderId, UUID customerId, UUID merchantId,
                       Long amountInCents, String currency, String paymentMethodId,
                       TransactionStatus status, UUID idempotencyKey) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.merchantId = merchantId;
        this.amountInCents = amountInCents;
        this.currency = currency;
        this.paymentMethodId = paymentMethodId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public Long getMpPaymentId() { return mpPaymentId; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getMerchantId() { return merchantId; }
    public Long getAmountInCents() { return amountInCents; }
    public String getCurrency() { return currency; }
    public String getCardBrand() { return cardBrand; }
    public String getCardLastFour() { return cardLastFour; }
    public Integer getInstallments() { return installments; }
    public String getPaymentMethodId() { return paymentMethodId; }
    public TransactionStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public Long getProcessingTimeMs() { return processingTimeMs; }
    public Long getRefundedAmountInCents() { return refundedAmountInCents; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public void setMpPaymentId(Long mpPaymentId) { this.mpPaymentId = mpPaymentId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public void setAmountInCents(Long amountInCents) { this.amountInCents = amountInCents; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
    public void setInstallments(Integer installments) { this.installments = installments; }
    public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    public void setRefundedAmountInCents(Long refundedAmountInCents) { this.refundedAmountInCents = refundedAmountInCents; }
}
