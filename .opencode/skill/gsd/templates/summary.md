---
phase: {{PHASE}}
plan: {{PLAN}}
subsystem: {{SUBSYSTEM}}
tags: [{{TAGS}}]
depends_on: [{{DEPENDS_ON}}]
provides: [{{PROVIDES}}]
affects: [{{AFFECTS}}]
tech_stack:
  added: [{{TECH_ADDED}}]
  patterns: [{{PATTERNS_USED}}]
key_files:
  created: [{{FILES_CREATED}}]
  modified: [{{FILES_MODIFIED}}]
decisions: [{{KEY_DECISIONS}}]
metrics:
  duration_seconds: {{DURATION}}
  completed_date: {{DATE}}
  tasks_completed: {{TASKS_DONE}}
  files_changed: {{FILES_CHANGED}}
status: complete
---

# Phase {{PHASE}} Plan {{PLAN}}: {{PLAN_NAME}} Summary

## One-Liner

{{SUBSTANTIVE_ONE_LINER}}

## Completed Tasks

| Task | Name | Commit | Files | Status |
|------|------|--------|-------|--------|
| 1 | {{TASK_1_NAME}} | {{HASH_1}} | {{FILES_1}} | ✅ |
| 2 | {{TASK_2_NAME}} | {{HASH_2}} | {{FILES_2}} | ✅ |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] {{DESCRIPTION}}**
- **Found during:** Task {{TASK_NUM}}
- **Issue:** {{ISSUE}}
- **Fix:** {{FIX}}
- **Files modified:** {{FILES}}
- **Commit:** {{HASH}}

### Auth Gates

- **Task {{TASK_NUM}}:** {{GATE_DESCRIPTION}} — {{OUTCOME}}

### Stub Tracking

**Known Stubs:**
| Stub | File | Line | Reason | Target Resolution |
|------|------|------|--------|-------------------|
| {{STUB_1}} | {{FILE_1}} | {{LINE_1}} | {{REASON_1}} | {{TARGET_1}} |

### Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: {{TYPE}} | {{FILE}} | {{DESCRIPTION}} |

## Verification Results

- All done criteria met: {{YES_NO}}
- Test coverage: {{COVERAGE}}%
- Integration tests: {{PASS_COUNT}}/{{TOTAL_COUNT}} passing

## Self-Check

- [ ] Created files exist
- [ ] Commits exist in git history
- [ ] No unexpected deletions

**Result:** PASSED / FAILED

---

*Completed: {{DATE}}*
*Duration: {{DURATION}}*