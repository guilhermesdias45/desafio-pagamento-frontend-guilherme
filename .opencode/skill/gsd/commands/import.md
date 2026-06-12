---
name: gsd:import
description: Onboard existing codebase into GSD
argument-hint: "[--path <dir>] [--shallow]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
  - Task
  - Question
---

# GSD Import Command

## Objective

Onboard an existing codebase into GSD workflow: map codebase, extract requirements, create PROJECT.md, ROADMAP.md.

## Flags

- `--path <dir>` — Path to codebase (default: current directory)
- `--shallow` — Skip deep analysis, create minimal artifacts

## Process

1. **Map codebase** — Spawn `gsd-codebase-mapper` to analyze structure
2. **Extract requirements** — Reverse-engineer requirements from code
3. **Create artifacts** — PROJECT.md, REQUIREMENTS.md, ROADMAP.md, STATE.md
4. **Identify phases** — Group work into logical phases for future planning

## Execution

Spawns `gsd-codebase-mapper` and `gsd-project-researcher` agents.

## References

- `@.opencode/skill/gsd/workflows/import.md`
- `@.opencode/skill/gsd/agents/gsd-codebase-mapper.md`
- `@.opencode/skill/gsd/templates/project.md`
- `@.opencode/skill/gsd/templates/requirements.md`
- `@.opencode/skill/gsd/templates/roadmap.md`