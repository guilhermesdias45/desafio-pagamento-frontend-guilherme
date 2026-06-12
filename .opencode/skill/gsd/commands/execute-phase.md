---
name: gsd:execute-phase
description: Execute phase plans with atomic commits, checkpoints, deviation handling
argument-hint: "[phase] [--plan <N>] [--resume] [--model <model>] [--verify] [--no-verify]"
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
  - Task
---

# GSD Execute Phase Command

## Objective

Execute PLAN.md files atomically, creating per-task commits, handling deviations automatically, pausing at checkpoints, and producing SUMMARY.md files.

**Spawns:** `gsd-executor` agent for each plan in the phase.

## Flags

- `--plan <N>` — Execute specific plan number within phase
- `--resume` — Resume from checkpoint (requires completed_tasks in context)
- `--model <model>` — Override executor model
- `--verify` — Run verification after each task
- `--no-verify` — Skip verification

## Process

1. **Load state** — Read STATE.md, ROADMAP.md, phase directory
2. **For each plan in phase:**
   - Spawn `gsd-executor` agent with plan context
   - Execute tasks with atomic commits
   - Handle checkpoints (human-verify, decision, human-action)
   - Create SUMMARY.md
   - Update STATE.md, ROADMAP.md
3. **Final commit** — Commit all planning artifacts

## Execution

Spawns `gsd-executor` agents. Each executor runs in fresh context with clean 200k-token window.

## References

- `@.opencode/skill/gsd/workflows/execute-phase.md`
- `@.opencode/skill/gsd/agents/gsd-executor.md`
- `@.opencode/skill/gsd/templates/summary.md`
- `@.opencode/skill/gsd/references/checkpoints.md`
- `@.opencode/skill/gsd/references/executor-examples.md`