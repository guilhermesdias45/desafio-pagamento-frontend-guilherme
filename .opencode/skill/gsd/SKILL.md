# GSD (Git. Ship. Done.) Skill for OpenCode

## Overview

GSD is a spec-driven development framework that drives AI coding agents through a disciplined phase loop: **Discuss → Plan → Execute → Verify → Ship**. It solves context rot by running heavy research, planning, and execution in fresh-context subagents while keeping the main session lean.

This skill brings GSD's commands, agents, and workflows to OpenCode.

## Quick Start

```bash
# Initialize a new GSD project
/gsd:new-project

# Or if you have an existing project to onboard
/gsd:import
```

## Core Commands

| Command | Description |
|---------|-------------|
| `/gsd:new-project` | Initialize new project with context gathering, requirements, roadmap |
| `/gsd:plan-phase [N]` | Create detailed phase plan (PLAN.md) with verification |
| `/gsd:execute-phase [N]` | Execute phase plans with atomic commits, checkpoints |
| `/gsd:verify-work [N]` | Verify executed work against plan, generate fix plans |
| `/gsd:ship [N]` | Create PR, archive phase, prepare next |
| `/gsd:discuss-phase [N]` | Capture implementation decisions before planning |
| `/gsd:review` | Cross-AI code review of completed work |
| `/gsd:help` | Show available commands |

## Phase Loop

```
1. DISCUSS  → /gsd:discuss-phase    (capture decisions)
2. PLAN     → /gsd:plan-phase       (research + plan + verify)
3. EXECUTE  → /gsd:execute-phase    (atomic commits, checkpoints)
4. VERIFY   → /gsd:verify-work      (walkthrough, fix plans)
5. SHIP     → /gsd:ship             (PR, archive, next phase)
```

## Key Artifacts Created

- `.planning/PROJECT.md` — project context and vision
- `.planning/CONTEXT.md` — locked decisions, constraints
- `.planning/REQUIREMENTS.md` — traceable requirements
- `.planning/ROADMAP.md` — phase structure and milestones
- `.planning/STATE.md` — project memory across sessions
- `.planning/phases/XX-name/PLAN.md` — executable plan per phase
- `.planning/phases/XX-name/SUMMARY.md` — execution results

## Specialized Agents

GSD uses specialized subagents for different tasks:

| Agent | Purpose |
|-------|---------|
| `gsd-planner` | Creates detailed executable plans from requirements |
| `gsd-executor` | Executes plans with atomic commits, deviation handling |
| `gsd-verifier` | Verifies implementation against plan |
| `gsd-phase-researcher` | Deep domain research for planning |
| `gsd-plan-checker` | Validates plan quality before execution |
| `gsd-code-reviewer` | Reviews completed code |
| `gsd-security-auditor` | Security audit of implementation |
| `gsd-codebase-mapper` | Maps existing codebase for onboarding |

## Installation

The skill is automatically available when placed in `.opencode/skill/gsd/`. No additional installation required.

## Configuration

GSD stores preferences in `.planning/config.json`:

```json
{
  "workflow": {
    "auto_advance": false,
    "default_executor_model": "opencode"
  },
  "editor": "vscode",
  "runtime": "opencode"
}
```

## Usage Examples

### New Project
```
/gsd:new-project
# Answer questions about project type, tech stack, goals
# Optionally provide idea document via @reference
```

### Plan a Phase
```
/gsd:plan-phase 1
# Researches domain, creates PLAN.md, verifies with plan-checker
```

### Execute a Phase
```
/gsd:execute-phase 1
# Runs tasks atomically, handles checkpoints, creates SUMMARY.md
```

### Verify Work
```
/gsd:verify-work 1
# Walks through implementation, generates fix plan if needed
```

### Ship Phase
```
/gsd:ship 1
# Creates PR, updates roadmap, prepares next phase
```

## Integration with OpenCode

This skill provides:
- **Commands** in `.opencode/skill/gsd/commands/` — invokable via `/gsd:*`
- **Agents** in `.opencode/skill/gsd/agents/` — specialized subagents
- **References** in `.opencode/skill/gsd/references/` — shared knowledge
- **Templates** in `.opencode/skill/gsd/templates/` — artifact templates
- **Workflows** in `.opencode/skill/gsd/workflows/` — command implementations

## Key Differences from Claude Code Version

1. **Runtime detection** — Uses OpenCode's native tooling
2. **No npx dependency** — Core tools bundled or available via OpenCode
3. **Agent spawning** — Uses OpenCode's `task` tool with `subagent_type`
4. **File operations** — Uses OpenCode's `read`, `write`, `edit`, `bash`, `glob`, `grep`
5. **Configuration** — Stored in `.opencode/skill/gsd/` instead of `~/.claude/gsd-core/`

## Documentation

- [GSD Core Documentation](https://github.com/open-gsd/gsd-core/tree/next/docs)
- [Phase Loop Explanation](https://github.com/open-gsd/gsd-core/blob/next/docs/explanation/the-phase-loop.md)
- [Context Engineering](https://github.com/open-gsd/gsd-core/blob/next/docs/explanation/context-engineering.md)

## License

MIT — See [GSD Core License](https://github.com/open-gsd/gsd-core/blob/next/LICENSE)