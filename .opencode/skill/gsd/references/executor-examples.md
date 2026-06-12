# Executor Deviation Examples

## Rule 1: Auto-fix Bugs

### Example 1: Null Pointer Exception
**Task:** Implement user lookup by email
**Found:** `user.getEmail().equals(input)` throws NPE when user not found
**Fix:** Add null check, return Optional.empty()
**Commit:** `fix(auth-1): handle null user in email lookup`

### Example 2: Wrong Query Logic
**Task:** Find transactions by customer and date range
**Found:** Query uses `AND` instead of `OR` for status filter
**Fix:** Correct boolean logic, add test case
**Commit:** `fix(payment-2): correct status filter in transaction query`

### Example 3: Race Condition
**Task:** Increment payment counter
**Found:** Read-modify-write without locking
**Fix:** Use atomic Redis INCR or DB row lock
**Commit:** `fix(fraud-1): use atomic Redis counter for velocity tracking`

---

## Rule 2: Auto-add Missing Critical Functionality

### Example 1: Missing Input Validation
**Task:** Create refund endpoint
**Found:** No validation on `amountInCents` (negative, zero, > original)
**Fix:** Add validation, return 400 with clear error
**Commit:** `feat(refund-1): add amount validation for refund endpoint`

### Example 2: Missing Authorization
**Task:** GET /api/v1/transactions/{id}
**Found:** No merchant ownership check
**Fix:** Verify `transaction.merchantId == request.merchantId`, return 403 if not
**Commit:** `fix(payment-3): add merchant ownership check to transaction lookup`

### Example 3: Missing Error Handling
**Task:** Call Mercado Pago gateway
**Found:** No timeout, no retry, no circuit breaker
**Fix:** Add Resilience4j timeout (800ms), retry (2x), circuit breaker
**Commit:** `feat(payment-4): add resilience patterns to MP gateway client`

### Example 4: Missing Database Index
**Task:** Query transactions by customerId + createdAt
**Found:** Full table scan on 1M+ rows
**Fix:** Add composite index `(customer_id, created_at DESC)`
**Commit:** `perf(payment-5): add composite index for transaction history query`

---

## Rule 3: Auto-fix Blocking Issues

### Example 1: Wrong Types
**Task:** Implement fraud score response DTO
**Found:** Plan says `score: Integer` but implementation uses `Long`
**Fix:** Change to Integer, update all references
**Commit:** `fix(fraud-2): correct score type to Integer per spec`

### Example 2: Broken Import
**Task:** Use new validation library
**Found:** Import path changed in v2.0
**Fix:** Update import, adjust API usage
**Commit:** `fix(common-1): update validation library imports for v2`

### Example 3: Missing Environment Variable
**Task:** Start application with Kafka config
**Found:** `KAFKA_BOOTSTRAP_SERVERS` not set
**Fix:** Add default for local dev, document required for prod
**Commit:** `chore(config-1): add default Kafka bootstrap for local dev`

---

## Rule 4: Ask About Architectural Changes

### Example 1: New Database Table
**Task:** Store refund reasons
**Found:** Need new `refund_reasons` table with FK to refunds
**Action:** STOP — checkpoint:decision
**Options:** [1] New table / [2] JSON column in refunds / [3] Enum in code

### Example 2: Switching Libraries
**Task:** Replace custom JWT with jose4j
**Found:** Current implementation tightly coupled to custom code
**Action:** STOP — checkpoint:decision
**Options:** [1] Full rewrite / [2] Adapter pattern / [3] Defer to next phase

### Example 3: New Infrastructure
**Task:** Add caching layer
**Found:** Requires new Redis cluster, not in current infra
**Action:** STOP — checkpoint:decision
**Options:** [1] Provision Redis / [2] Use in-memory (dev only) / [3] Defer

---

## Edge Cases

### Missing Validation → Rule 2 (Security)
Any missing validation on external input is a security issue — auto-add.

### Crashes on Null → Rule 1 (Bug)
Null pointer crashes are bugs — auto-fix.

### Need New Column → Rule 1 or 2
- Add column to existing table → Rule 1/2 (implementation detail)
- Need new table → Rule 4 (architectural)

### Pre-existing Lint Warnings → Out of Scope
- Log to `deferred-items.md` in phase directory
- Do NOT fix
- Do NOT re-run build hoping they resolve

---

## Fix Attempt Limit

Track auto-fix attempts per task. After 3 attempts:
1. STOP fixing
2. Document remaining issues in SUMMARY.md under "Deferred Issues"
3. Continue to next task (or return checkpoint if blocked)
4. Do NOT restart build to find more issues

---

## Package Install Failure → checkpoint:human-verify (blocking-human)

```markdown
## CHECKPOINT REACHED

**Type:** human-verify
**Plan:** payment-3
**Progress:** 2/5 tasks complete

### Current Task
**Task 3:** Add Mercado Pago SDK
**Status:** blocked
**Blocked by:** Package `com.mercadopago:sdk-java:2.1.29` not found in Maven Central

### Checkpoint Details

`com.mercadopago:sdk-java:2.1.29` could not be installed. Before proceeding:
1. Verify the package exists: https://mvnrepository.com/artifact/com.mercadopago/sdk-java
2. Confirm the version is spelled correctly in PLAN.md
3. If the package does not exist, re-run /gsd:plan-phase --research-phase 3 to find the correct package

### Awaiting
Type "verified" with the correct package coordinates, or "abort" to stop the phase
```