---
name: gsd:new-project
description: Initialize a new project with deep context gathering and PROJECT.md
argument-hint: "[--auto]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
  - Task
  - Question
---

# GSD New Project Command

## Objective

Initialize a new project through unified flow: questioning → research (optional) → requirements → roadmap.

**Creates:**
- `.planning/PROJECT.md` — project context
- `.planning/config.json` — workflow preferences
- `.planning/research/` — domain research (optional)
- `.planning/REQUIREMENTS.md` — scoped requirements
- `.planning/ROADMAP.md` — phase structure
- `.planning/STATE.md` — project memory

**After this command:** Run `/gsd:plan-phase 1` to start execution.

## Flags

- `--auto` — Automatic mode. After config questions, runs research → requirements → roadmap without further interaction. Expects idea document via @ reference.

## Process

1. **Questioning** — Interactive questions to capture project vision, tech stack, constraints
2. **Research** (optional) — Spawn `gsd-project-researcher` for domain research
3. **Requirements** — Synthesize requirements from vision + research
4. **Roadmap** — Create phased roadmap with milestones
5. **State** — Initialize STATE.md for session persistence

## Execution

Spawn the `gsd-project-researcher` agent for research if needed, then synthesize all artifacts.

## References

- `@.opencode/skill/gsd/workflows/new-project.md`
- `@.opencode/skill/gsd/templates/project.md`
- `@.opencode/skill/gsd/templates/requirements.md`
- `@.opencode/skill/gsd/templates/roadmap.md`
- `@.opencode/skill/gsd/templates/state.md`