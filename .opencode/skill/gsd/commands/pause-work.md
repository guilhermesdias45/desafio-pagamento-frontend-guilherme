---
name: gsd:pause-work
description: Pause current work, save state for resume
argument-hint: "[--reason <text>]"
allowed-tools:
  - Read
  - Write
  - Bash
---

# GSD Pause Work Command

## Objective

Save current execution state to STATE.md for later resumption with `/gsd:resume-work`.

## Flags

- `--reason <text>` — Reason for pausing (saved to STATE.md)

## Process

1. **Read current state** — STATE.md, current plan progress
2. **Update STATE.md** — Set status to "paused", record reason, timestamp
3. **Save context** — Current task, completed tasks, blockers
4. **Commit** — Save planning artifacts

## References

- `@.opencode/skill/gsd/workflows/pause-work.md`