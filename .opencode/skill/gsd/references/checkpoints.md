# Checkpoint Protocols

## Philosophy

**Automation before verification** — Users NEVER run CLI commands. Users ONLY visit URLs, click UI, evaluate visuals, provide secrets. The agent does all automation.

## Checkpoint Types

### checkpoint:human-verify (90%)

Visual/functional verification after automation.

**Agent provides:**
- What was built (feature, endpoint, UI)
- Exact verification steps:
  - URL to visit
  - Commands to run (agent runs, user observes output)
  - Expected behavior/result
  - Screenshot/video if UI

**Example:**
```markdown
### Checkpoint Details

**Built:** POST /api/v1/transactions endpoint with idempotency

**Verify:**
1. Visit: http://localhost:8080/swagger-ui.html
2. Run: `curl -X POST http://localhost:8080/api/v1/transactions -H "X-Merchant-Id: <uuid>" -H "X-User-Email: test@test.com" -H "X-Forwarded-For: 1.2.3.4" -d '{"amountInCents": 1000, "currency": "BRL", "customerId": "...", "orderId": "...", "cardToken": "test_token", "paymentMethodId": "visa", "idempotencyKey": "..."}'`
3. Expect: HTTP 201 with transactionId, mpPaymentId
4. Repeat same request → Expect HTTP 200 with duplicate=true
```

### checkpoint:decision (9%)

Implementation choice needed.

**Agent provides:**
- Decision context
- Options table with pros/cons
- Recommendation with rationale
- Selection prompt

**Example:**
```markdown
### Checkpoint Details

**Decision:** Cache invalidation strategy for transaction queries

| Option | Pros | Cons |
|--------|------|------|
| TTL-based (60s) | Simple, eventual consistency | Stale reads up to 60s |
| Write-through | Strong consistency | More complex, cache write latency |
| Event-based (Kafka) | Real-time, scalable | Requires Kafka consumer |

**Recommendation:** TTL-based (60s) — matches current architecture, low complexity

**Select:** [1] TTL / [2] Write-through / [3] Event-based
```

### checkpoint:human-action (1%)

Truly unavoidable manual step.

**Agent provides:**
- What automation was attempted
- Single manual step needed
- Verification command to confirm

**Example:**
```markdown
### Checkpoint Details

**Automation attempted:** Tried to create Mercado Pago test account via API
**Blocked:** API requires manual email verification

**Manual step needed:**
1. Visit: https://www.mercadopago.com.br/developers/panel
2. Create test account (seller type)
3. Copy client_id and client_secret

**Verification:**
Run: `gsd_run query config-set mercadopago.client_id <id> && gsd_run query config-set mercadopago.client_secret <secret>`
```

## Auto-Mode Behavior

When `AUTO_CFG=true` (from config or chain flag):

| Checkpoint Type | Behavior |
|-----------------|----------|
| human-verify | Auto-approve (except package-legitimacy gates) |
| decision | Auto-select first option (recommended) |
| human-action | STOP normally (cannot automate) |

**Package-legitimacy gate:** Any checkpoint with `gate="blocking-human"` or mentioning "Package verification required" or "Package install failed" — NEVER auto-approve.

## Checkpoint Return Format

```markdown
## CHECKPOINT REACHED

**Type:** [human-verify | decision | human-action]
**Plan:** {phase}-{plan}
**Progress:** {completed}/{total} tasks complete

### Completed Tasks

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | [task name] | [hash] | [key files] |

### Current Task

**Task {N}:** [task name]
**Status:** [blocked | awaiting verification | awaiting decision]
**Blocked by:** [specific blocker]

### Checkpoint Details

[Type-specific content]

### Awaiting

[What user needs to do/provide]
```

## Continuation Handling

When resuming after checkpoint:
1. Verify previous commits exist: `git log --oneline -5`
2. DO NOT redo completed tasks
3. Start from resume point in prompt
4. Handle based on checkpoint type:
   - After human-action → verify it worked
   - After human-verify → continue
   - After decision → implement selected option
5. If another checkpoint hit → return with ALL completed tasks (previous + new)