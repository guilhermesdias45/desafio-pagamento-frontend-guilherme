---
name: gsd:discuss-phase
description: Capture implementation decisions before planning
argument-hint: "[phase] [--text]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Task
  - Question
---

# GSD Discuss Phase Command

## Objective

Capture implementation decisions, constraints, and architectural choices before planning begins. Creates/updates CONTEXT.md with locked decisions.

## Flags

- `--text` — Use plain-text numbered lists instead of TUI menus

## Process

1. **Load phase** — Read ROADMAP.md for phase goals
2. **Question** — Interactive discussion on key decisions:
   - Architecture patterns
   - Library/framework choices
   - Data models
   - API contracts
   - Security requirements
   - Performance targets
3. **Record** — Write decisions to CONTEXT.md as locked choices
4. **Scope** — Define what's in/out of scope for the phase

## Execution

Interactive questioning session. Output feeds directly into `plan-phase`.

## References

- `@.opencode/skill/gsd/workflows/discuss-phase.md`
- `@.opencode/skill/gsd/templates/context.md`
- `@.opencode/skill/gsd/references/questioning.md`