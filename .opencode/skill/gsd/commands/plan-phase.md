---
name: gsd:plan-phase
description: Create detailed phase plan (PLAN.md) with verification loop
argument-hint: "[phase] [--auto] [--research] [--skip-research] [--research-phase <N>] [--view] [--gaps] [--skip-verify] [--prd <file>] [--ingest <path-or-glob>] [--ingest-format <auto|nygard|madr|narrative>] [--reviews] [--text] [--tdd] [--mvp]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
  - Task
  - Question
  - WebFetch
---

# GSD Plan Phase Command

## Objective

Create executable phase prompts (PLAN.md files) for a roadmap phase with integrated research and verification.

**Default flow:** Research (if needed) → Plan → Verify → Done

**Research-only mode (`--research-phase <N>`):** Spawn `gsd-phase-researcher` for phase `N`, write `RESEARCH.md`, then exit before the planner runs.

## Flags

- `--research` — Force re-research even if RESEARCH.md exists
- `--skip-research` — Skip research, go straight to planning
- `--gaps` — Gap closure mode (reads VERIFICATION.md, skips research)
- `--skip-verify` — Skip verification loop
- `--prd <file>` — Use a PRD/acceptance criteria file instead of discuss-phase
- `--ingest <path-or-glob>` — Use ADR files instead of discuss-phase
- `--ingest-format <auto|nygard|madr|narrative>` — ADR parser format override
- `--reviews` — Replan incorporating cross-AI review feedback from REVIEWS.md
- `--text` — Use plain-text numbered lists instead of TUI menus
- `--mvp` — Vertical MVP mode (feature slices instead of horizontal layers)
- `--tdd` — Enable TDD mode for the plan

## Process

1. **Validate phase** — Check ROADMAP.md for phase existence
2. **Research** (unless skipped) — Spawn `gsd-phase-researcher` for domain research
3. **Plan** — Spawn `gsd-planner` to create detailed PLAN.md
4. **Verify** — Spawn `gsd-plan-checker` to validate plan quality
5. **Iterate** — Loop until plan passes verification or max iterations
6. **Present** — Show results to user

## Execution

Orchestrates multiple agents: researcher → planner → checker → (iterate) → done.

## References

- `@.opencode/skill/gsd/workflows/plan-phase.md`
- `@.opencode/skill/gsd/templates/plan.md`
- `@.opencode/skill/gsd/references/thinking-models-planning.md`