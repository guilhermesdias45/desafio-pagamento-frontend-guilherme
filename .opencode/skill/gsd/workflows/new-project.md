# New Project Workflow

## Objective
Initialize a new GSD project through unified flow: questioning → research (optional) → requirements → roadmap.

## Steps

### 1. Questioning (Interactive)
Ask user:
- Project name and vision
- Problem being solved
- Target users
- Tech stack preferences (language, framework, database, infrastructure)
- Compliance requirements
- Success metrics
- Timeline/scope

### 2. Research (Optional)
If user provides idea document or complex domain:
- Spawn `gsd-project-researcher` agent
- Write `.planning/research/initial.md`

### 3. Requirements
Synthesize REQUIREMENTS.md from vision + research:
- Functional requirements with acceptance criteria
- Non-functional requirements with targets
- Compliance requirements
- Traceability matrix

### 4. Roadmap
Create ROADMAP.md:
- Phase structure with goals
- Requirements mapped to phases
- Milestone definitions
- Initial timeline

### 5. State & Config
- Initialize STATE.md with current position
- Create config.json with defaults
- Create PROJECT.md with project context

### 6. Git Commit
```bash
git add .planning/
git commit -m "chore(init): initialize GSD project structure"
```

## Post-Command
Tell user: "Run `/gsd:plan-phase 1` to start execution."