---
name: gsd:ship
description: Create PR, archive phase, prepare next
argument-hint: "[phase] [--no-pr] [--draft] [--auto-merge]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
  - Task
---

# GSD Ship Command

## Objective

Create PR for completed phase, archive artifacts, update roadmap, prepare next phase.

## Flags

- `--no-pr` — Skip PR creation (local only)
- `--draft` — Create draft PR
- `--auto-merge` — Enable auto-merge when checks pass

## Process

1. **Validate** — All plans in phase verified (VERIFICATION.md exists and passes)
2. **Create PR** — Bundle all phase commits, include SUMMARY.md files
3. **Archive** — Move phase to `.planning/archive/`, update ROADMAP.md status
4. **Prepare next** — Update STATE.md with next phase, increment counters
5. **Commit** — Final metadata commit with updated planning artifacts

## Execution

Uses `gh` CLI for PR creation. Updates ROADMAP.md, STATE.md, REQUIREMENTS.md.

## References

- `@.opencode/skill/gsd/workflows/ship.md`
- `@.opencode/skill/gsd/templates/pr-description.md`