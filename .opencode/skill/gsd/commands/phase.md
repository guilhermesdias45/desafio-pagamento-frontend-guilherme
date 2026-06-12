---
name: gsd:phase
description: Show current phase status and progress
argument-hint: "[--detail] [--json]"
allowed-tools:
  - Read
  - Bash
---

# GSD Phase Command

## Objective

Display current phase, plan progress, and project status from STATE.md and ROADMAP.md.

## Flags

- `--detail` — Show task-level detail for current plan
- `--json` — Output as JSON for scripting

## Output

Shows:
- Current phase and plan
- Progress bar (completed/total plans)
- Recent decisions from STATE.md
- Blockers and deferred items
- Next actions

## Process

Read STATE.md, ROADMAP.md, and current phase directory.

## References

- `@.opencode/skill/gsd/workflows/phase.md`