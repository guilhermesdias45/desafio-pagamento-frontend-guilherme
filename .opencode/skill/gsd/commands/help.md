---
name: gsd:help
description: Show available GSD commands and usage guide
argument-hint: "[--brief | --full | <topic> | --brief <topic>]"
allowed-tools:
  - Read
---

# GSD Help Command

## Objective

Display GSD help at the tier the user asked for: brief (one-line refresher), default (one-page tour), full (complete reference), a single topic section, or a compact scoped lookup of one topic.

## Flags

- `--brief` — One-line per command
- `--full` — Complete reference with all flags and examples
- `<topic>` — Single topic section (e.g., "plan-phase", "execute-phase")
- `--brief <topic>` — Compact lookup: signature + one-line summary

## Output Tiers

**Brief:**
```
/gsd:new-project       Initialize new project
/gsd:plan-phase [N]    Create phase plan
/gsd:execute-phase [N] Execute phase plans
/gsd:verify-work [N]   Verify implementation
/gsd:ship [N]          Create PR, archive phase
/gsd:discuss-phase [N] Capture decisions
/gsd:review [N]        Cross-AI code review
```

**Default:** One-page tour with descriptions, flags, typical flow.

**Full:** Complete reference with all flags, process details, examples, artifact descriptions.

## Process

Read this command file and format output based on flags.

## References

- `@.opencode/skill/gsd/workflows/help.md`