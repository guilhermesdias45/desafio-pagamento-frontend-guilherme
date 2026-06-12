---
name: gsd:verify-work
description: Verify executed work against plan, generate fix plans
argument-hint: "[phase] [--plan <N>] [--auto-fix] [--full]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
  - Task
---

# GSD Verify Work Command

## Objective

Walk through what was built, diagnose issues, generate fix plans before declaring phase done.

**Spawns:** `gsd-verifier` agent.

## Flags

- `--plan <N>` — Verify specific plan
- `--auto-fix` — Auto-apply Rule 1-3 fixes during verification
- `--full` — Full verification including security audit

## Process

1. **Load context** — Read PLAN.md, SUMMARY.md, STATE.md
2. **Spawn verifier** — Walk through implementation vs plan
3. **Check** — All tasks complete, criteria met, no stubs blocking goal
4. **Report** — VERIFICATION.md with pass/fail, fix plan if needed
5. **Decide** — If fix plan needed, user chooses: fix now / defer / ship anyway

## Execution

Spawns `gsd-verifier` agent. If issues found, generates fix plan for next iteration.

## References

- `@.opencode/skill/gsd/workflows/verify-work.md`
- `@.opencode/skill/gsd/agents/gsd-verifier.md`
- `@.opencode/skill/gsd/templates/verification.md`