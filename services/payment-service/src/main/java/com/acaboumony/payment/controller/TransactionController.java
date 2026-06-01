package com.acaboumony.payment.controller;

import com.acaboumony.payment.dto.request.RefundRequest;
import com.acaboumony.payment.dto.request.TransactionRequest;
import com.acaboumony.payment.dto.response.RefundResponse;
import com.acaboumony.payment.dto.response.TransactionResponse;
import com.acaboumony.payment.result.TransactionResult;
import com.acaboumony.payment.service.RefundService;
import com.acaboumony.payment.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final RefundService refundService;

    public TransactionController(TransactionService transactionService,
                                 RefundService refundService) {
        this.transactionService = transactionService;
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> processTransaction(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader("X-Customer-Email") String customerEmail,
            @RequestHeader("X-Merchant-Id") UUID merchantId,
            @RequestHeader("X-Forwarded-For") String ipAddress) {

        var result = transactionService.processTransaction(request, customerEmail, merchantId, ipAddress);

        return switch (result) {
            case TransactionResult.Approved a -> ResponseEntity.status(a.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response(a.transactionId(), a.mpPaymentId(), a.orderId(),
                    "APPROVED", a.processingTimeMs(), null));
            case TransactionResult.Failed f -> {
                var responseEntity = ResponseEntity.status(errorHttpStatus(f.errorCode()))
                    .body(error(f.errorCode(), f.message(), f.retryable(), f.processingTimeMs()));
                if ("RATE_LIMIT_EXCEEDED".equals(f.errorCode())) {
                    yield ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "60")
                        .body(error(f.errorCode(), f.message(), f.retryable(), f.processingTimeMs()));
                }
                yield responseEntity;
            }
        };
    }

    @PostMapping("/{transactionId}/refund")
    public ResponseEntity<Map<String, Object>> refundTransaction(
            @PathVariable String transactionId,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader("X-Merchant-Id") UUID merchantId) {
        try {
            var refund = refundService.refund(transactionId, request, merchantId);
            return ResponseEntity.ok(Map.of(
                "data", refund,
                "meta", Map.of("timestamp", Instant.now().toString())
            ));
        } catch (IllegalArgumentException e) {
            var httpStatus = switch (e.getMessage()) {
                case "TRANSACTION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "INSUFFICIENT_PERMISSIONS" -> HttpStatus.FORBIDDEN;
                case "TRANSACTION_NOT_REFUNDABLE" -> HttpStatus.UNPROCESSABLE_ENTITY;
                case "REFUND_WINDOW_EXPIRED" -> HttpStatus.UNPROCESSABLE_ENTITY;
                case "ALREADY_FULLY_REFUNDED", "AMOUNT_EXCEEDS_ORIGINAL" -> HttpStatus.UNPROCESSABLE_ENTITY;
                default -> HttpStatus.BAD_REQUEST;
            };
            return ResponseEntity.status(httpStatus)
                .body(error(e.getMessage(), e.getMessage(), false, 0));
        }
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<Map<String, Object>> getTransaction(
            @PathVariable String transactionId,
            @RequestHeader("X-Merchant-Id") UUID merchantId) {
        var allTx = transactionService.findById(transactionId);
        if (allTx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("TRANSACTION_NOT_FOUND", "Transaction not found", false, 0));
        }
        var ownedTx = transactionService.findById(transactionId, merchantId);
        if (ownedTx.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("INSUFFICIENT_PERMISSIONS", "Access denied", false, 0));
        }
        return ResponseEntity.ok(Map.of(
            "data", ownedTx.get(),
            "meta", Map.of("timestamp", Instant.now().toString())
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTransactions(
            @RequestParam UUID customerId,
            @RequestHeader("X-Merchant-Id") UUID merchantId,
            Pageable pageable) {
        var page = transactionService.findByCustomer(customerId, merchantId, pageable);
        return ResponseEntity.ok(Map.of(
            "data", page.getContent(),
            "meta", Map.of(
                "timestamp", Instant.now().toString(),
                "page", page.getNumber(),
                "size", page.getSize(),
                "total", page.getTotalElements()
            )
        ));
    }

    private Map<String, Object> response(String transactionId, Long mpPaymentId,
                                          UUID orderId, String status,
                                          long processingTimeMs, List<?> errors) {
        return Map.of(
            "data", Map.of(
                "transactionId", transactionId,
                "mpPaymentId", mpPaymentId,
                "orderId", orderId != null ? orderId.toString() : null,
                "status", status,
                "processingTimeMs", processingTimeMs
            ),
            "meta", Map.of("timestamp", Instant.now().toString()),
            "errors", errors != null ? errors : List.of()
        );
    }

    private Map<String, Object> error(String code, String message, boolean retryable, long processingTimeMs) {
        return Map.of(
            "data", Map.of(
                "status", "FAILURE",
                "processingTimeMs", processingTimeMs
            ),
            "meta", Map.of("timestamp", Instant.now().toString()),
            "errors", List.of(Map.of(
                "code", code,
                "message", message,
                "retryable", retryable
            ))
        );
    }

    private HttpStatus errorHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "INVALID_AMOUNT", "INVALID_CURRENCY", "INVALID_CARD_TOKEN" -> HttpStatus.BAD_REQUEST;
            case "DUPLICATE_IDEMPOTENCY_KEY" -> HttpStatus.CONFLICT;
            case "ORDER_NOT_FOUND", "CUSTOMER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ORDER_NOT_PENDING" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "CARD_DECLINED", "INSUFFICIENT_FUNDS", "SUSPECTED_FRAUD" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "RATE_LIMIT_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "MP_GATEWAY_TIMEOUT", "ORDER_SERVICE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
