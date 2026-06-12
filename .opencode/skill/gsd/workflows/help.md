# Help Workflow

## Tier Detection

Parse `$ARGUMENTS`:
- `--brief` + `<topic>` → compact lookup
- `--brief` → one-line per command
- `--full` → complete reference
- `<topic>` → single topic section
- (none) → default one-page tour

## Output Formatting

### Brief (One-Line)
```
/gsd:new-project       Initialize new project
/gsd:plan-phase [N]    Create phase plan
/gsd:execute-phase [N] Execute phase plans
/gsd:verify-work [N]   Verify implementation
/gsd:ship [N]          Create PR, archive phase
/gsd:discuss-phase [N] Capture decisions
/gsd:review [N]        Cross-AI code review
/gsd:phase             Show current status
/gsd:config            Manage configuration
/gsd:import            Onboard existing codebase
```

### Default (One-Page)
For each command: name, description, key flags, typical usage.

### Full (Complete)
For each command: full description, all flags, process, examples, artifact descriptions.

### Topic Lookup
Extract relevant section from command file.

## Implementation

Read this file, parse arguments, format output accordingly.