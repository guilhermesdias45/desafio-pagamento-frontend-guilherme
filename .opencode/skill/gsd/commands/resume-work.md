---
name: gsd:resume-work
description: Resume paused work from STATE.md
argument-hint: "[--force]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Task
---

# GSD Resume Work Command

## Objective

Resume execution from a paused state, restoring context from STATE.md.

## Flags

- `--force` — Resume even if state seems inconsistent

## Process

1. **Read STATE.md** — Get paused phase, plan, task, context
2. **Validate** — Check git status, uncommitted changes
3. **Restore** — Spawn appropriate agent (executor/verifier) with resumed context
4. **Continue** — Pick up from saved position

## References

- `@.opencode/skill/gsd/workflows/resume-work.md`