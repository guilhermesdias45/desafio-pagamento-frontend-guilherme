# Thinking Models for Planning

## First Principles Decomposition

Break complex problems to fundamental truths:
1. Identify the core problem (not symptoms)
2. Strip away assumptions and conventions
3. Rebuild solution from ground up
4. Validate each component serves the core problem

## Inversion

Instead of "how to succeed," ask "how to fail":
- What would cause this plan to fail?
- What assumptions must hold?
- What dependencies are risky?
- Plan mitigations for each failure mode

## Wardley Mapping

Map components by visibility and maturity:
```
Visible to User          Hidden (Internal)
    │                        │
    ▼                        ▼
+----------+           +----------+
|  Custom  |           |  Custom  |  ← Build (competitive advantage)
|  Built   |           |  Built   |
+----------+           +----------+
|  Product |           |  Product |  ← Buy/Adopt (commodity)
|  (SaaS)  |           |  (OSS)   |
+----------+           +----------+
| Commodity|           | Commodity|  ← Utility (outsource)
+----------+           +----------+
  Genesis    Custom    Product    Commodity
```
Focus custom-building only on competitive advantages.

## Pre-mortem

Before finalizing plan:
1. Imagine the phase is complete but failed
2. Write the failure story
3. Extract root causes
4. Add mitigations to plan

## Checklist Manifesto

Every plan must pass these gates:
- [ ] **Atomicity** — Each task one commit
- [ ] **Verifiability** — Done criteria measurable
- [ ] **Traceability** — Tasks → Requirements
- [ ] **Feasibility** — Fits in context window
- [ ] **Reversibility** — Can rollback if needed
- [ ] **Observability** — Progress trackable

## Red Team Review

Before signing off on plan:
- What would an attacker exploit?
- What would a maintainer hate?
- What would scale poorly?
- What dependencies could vanish?

## References

- [First Principles](https://fs.blog/first-principles/)
- [Wardley Maps](https://medium.com/wardleymaps)
- [Pre-mortem](https://hbr.org/2007/09/performing-a-project-premortem)
- [Checklist Manifesto](https://atulgawande.com/book/the-checklist-manifesto/)