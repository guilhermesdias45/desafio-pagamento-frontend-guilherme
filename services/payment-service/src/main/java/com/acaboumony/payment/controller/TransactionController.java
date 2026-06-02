package com.acaboumony.payment.controller;

import com.acaboumony.payment.dto.request.RefundRequest;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.TransactionDetailResponse;
import com.acaboumony.payment.dto.response.TransactionResponse;
import com.acaboumony.payment.result.RefundResult;
import com.acaboumony.payment.result.TransactionResult;
import com.acaboumony.payment.service.RefundService;
import com.acaboumony.payment.service.TransactionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for payment transaction operations.
 * Headers: X-User-Id (UUID), X-User-Role (String), X-Merchant-Id (UUID, optional)
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;
    private final RefundService refundService;

    public TransactionController(TransactionService transactionService, RefundService refundService) {
        this.transactionService = transactionService;
        this.refundService = refundService;
    }

    /**
     * POST /api/v1/transactions — Process a payment transaction.
     * Returns 201 Created on success.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> processTransaction(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String userRole
    ) {
        TransactionResult result = transactionService.processTransaction(request, customerId);
        return switch (result) {
            case TransactionResult.Success s -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(new TransactionResponse(s.transactionId(), s.mpPaymentId(), s.orderId(), "APPROVED", s.processingTimeMs()));
            case TransactionResult.Failure f -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(null);
        };
    }

    /**
     * GET /api/v1/transactions/{transactionId} — Get transaction details.
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionDetailResponse> getTransaction(
            @PathVariable String transactionId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String userRole
    ) {
        TransactionDetailResponse detail = transactionService.findByTransactionId(transactionId);
        return ResponseEntity.ok(detail);
    }

    /**
     * GET /api/v1/transactions — List transactions for the authenticated customer (paginated).
     */
    @GetMapping
    public ResponseEntity<Page<TransactionDetailResponse>> listTransactions(
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String userRole,
            Pageable pageable
    ) {
        Page<TransactionDetailResponse> page = transactionService.findByCustomer(customerId, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * POST /api/v1/transactions/{transactionId}/refund — Refund a transaction.
     */
    @PostMapping("/{transactionId}/refund")
    public ResponseEntity<RefundResult> refundTransaction(
            @PathVariable String transactionId,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String userRole,
            @RequestHeader(value = "X-Merchant-Id", required = false) UUID merchantId
    ) {
        RefundResult result = refundService.refundTransaction(transactionId, request, userId, merchantId, userRole);
        return ResponseEntity.ok(result);
    }
}
