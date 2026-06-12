# Verification Report: Phase {{PHASE}} Plan {{PLAN}}

## Status: {{PASS | FAIL | PARTIAL}}

## Task Verification

| Task | Status | Evidence | Issues |
|------|--------|----------|--------|
| 1 | ✅ | commit {{HASH}}, files present | None |
| 2 | ❌ | Missing verification step | Fix needed |

## Issues Found

### Blocking (must fix before ship)
- [ ] {{ISSUE_DESCRIPTION}}, File: {{FILE}}, Line: {{LINE}}

### Non-blocking (can defer)
- [ ] {{ISSUE_DESCRIPTION}}

## Fix Plan (if FAIL/PARTIAL)

### Required Fixes
1. Task {{N}}: {{SPECIFIC_FIX_NEEDED}}
   - Files: {{FILES_TO_MODIFY}}
   - Verification: {{HOW_TO_VERIFY_FIX}}

### Deferred Items
- Item: {{DESCRIPTION}}, Reason: {{WHY_DEFERRED}}, Target: {{FUTURE_PHASE}}

## Known Stubs

| Stub | File | Line | Reason | Target Resolution |
|------|------|------|--------|-------------------|
| {{STUB_1}} | {{FILE_1}} | {{LINE_1}} | {{REASON_1}} | {{TARGET_1}} |

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: {{TYPE}} | {{FILE}} | {{DESCRIPTION}} |

## Test Coverage

| Type | Coverage | Target | Status |
|------|----------|--------|--------|
| Unit | {{UNIT_PCT}}% | {{UNIT_TARGET}}% | {{PASS_FAIL}} |
| Integration | {{INT_PCT}}% | {{INT_TARGET}}% | {{PASS_FAIL}} |

## Recommendation

{{SHIP_NOW | FIX_THEN_SHIP | DEFER}}