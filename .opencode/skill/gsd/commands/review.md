---
name: gsd:review
description: Cross-AI code review of completed work
argument-hint: "[phase] [--model <model>] [--focus <area>]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
  - Task
---

# GSD Review Command

## Objective

Perform cross-AI code review using a different model/perspective. Produces REVIEWS.md for incorporation in next planning cycle.

## Flags

- `--model <model>` — Specify review model (default: different from executor)
- `--focus <area>` — Focus area: security, performance, architecture, all

## Process

1. **Load context** — Read PLAN.md, SUMMARY.md, changed files
2. **Spawn reviewer** — `gsd-code-reviewer` agent with fresh context
3. **Review** — Check against plan, best practices, security, maintainability
4. **Report** — Write REVIEWS.md with findings, severity, suggestions
5. **Integrate** — Next `plan-phase --reviews` incorporates feedback

## Execution

Spawns `gsd-code-reviewer` agent. Output feeds into next planning cycle.

## References

- `@.opencode/skill/gsd/workflows/review.md`
- `@.opencode/skill/gsd/agents/gsd-code-reviewer.md`
- `@.opencode/skill/gsd/templates/reviews.md`