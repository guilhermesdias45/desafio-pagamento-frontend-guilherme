---
name: gsd:config
description: View or modify GSD configuration
argument-hint: "[--get <key>] [--set <key>=<value>] [--list] [--reset]"
allowed-tools:
  - Read
  - Write
  - Bash
---

# GSD Config Command

## Objective

Manage GSD workflow preferences in `.planning/config.json`.

## Flags

- `--get <key>` — Get specific config value
- `--set <key>=<value>` — Set config value
- `--list` — Show all config
- `--reset` — Reset to defaults

## Config Keys

```json
{
  "workflow": {
    "auto_advance": false,
    "default_executor_model": "opencode",
    "commit_docs": true
  },
  "editor": "vscode",
  "runtime": "opencode",
  "phase_loop": {
    "max_plan_iterations": 3,
    "verify_before_ship": true
  }
}
```

## Process

Read/write `.planning/config.json`.

## References

- `@.opencode/skill/gsd/templates/config.json`