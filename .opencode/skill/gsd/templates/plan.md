---
phase: {{PHASE}}
plan: {{PLAN}}
type: standard  # standard | tdd | mvp | research
autonomous: true  # true | false
wave: 1
depends_on: []
requirements:
  - {{REQ_ID_1}}
  - {{REQ_ID_2}}
---

# Phase {{PHASE}} Plan {{PLAN}}: {{PLAN_NAME}}

## Objective

{{ONE_SENTENCE_GOAL}}

## Context References

- `@.planning/CONTEXT.md` — locked decisions
- `@.planning/REQUIREMENTS.md` — traced requirements
- `@.planning/phases/{{PHASE_DIR}}/RESEARCH.md` — domain research (if applicable)

## Threat Model (if security-relevant)

| Asset | Threat | Likelihood | Impact | Mitigation | Disposition |
|-------|--------|------------|--------|------------|-------------|
| {{ASSET}} | {{THREAT}} | {{LIKELIHOOD}} | {{IMPACT}} | {{MITIGATION}} | {{DISPOSITION}} |

## Tasks

### Task 1: {{TASK_1_NAME}}

**Type:** auto
**TDD:** false
**Files:**
- `src/{{FILE_1}}`
- `src/{{FILE_2}}`

**Behavior:** (if TDD)
{{BEHAVIOR_DESCRIPTION}}

**Implementation:** (if TDD)
{{IMPLEMENTATION_APPROACH}}

**Verification:**
```bash
{{VERIFICATION_COMMAND}}
```

**Done Criteria:**
- [ ] {{CRITERIA_1}}
- [ ] {{CRITERIA_2}}

---

### Task 2: {{TASK_2_NAME}}

**Type:** checkpoint:human-verify
**TDD:** false
**Files:**
- `src/{{FILE_3}}`

**Verification:**
```bash
{{VERIFICATION_COMMAND}}
```

**Done Criteria:**
- [ ] {{CRITERIA_1}}

---

## Success Criteria

- [ ] All tasks completed with passing verification
- [ ] No blocking stubs
- [ ] Test coverage ≥ {{COVERAGE_TARGET}}%
- [ ] No undeclared threat flags

## Output Spec

**Creates/Modifies:**
- {{OUTPUT_FILE_1}}
- {{OUTPUT_FILE_2}}

**Events Published:**
- {{EVENT_1}}
- {{EVENT_2}}

**Metrics Updated:**
- {{METRIC_1}}