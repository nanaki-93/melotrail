# Canonical musical pipeline implementation tasks

These ordered contracts implement [`PLAN.md`](../PLAN.md). Work on one task at a
time unless a contract explicitly permits parallel implementation. Task numbers
118–130 continue the repository's historical sequence; missing older task files
are Git-history evidence and are not live dependencies.

Cross-cutting completion rule: schema v4 is the only supported project format,
and replacement is not complete while any superseded project-owned runtime path
remains. Do not add or retain backward/retro-compatibility, dual reads, migration
commands, migration UI, old-schema DTOs/mappers, or exclusive legacy fixtures.
Remove obsolete source, tests, registrations, configuration, dependencies,
resources, UI copy, and stale documentation in the task that replaces them.

| Order | Task | Primary result |
| ---: | --- | --- |
| 118 | [Pipeline audit and executable baseline](completed/118-plan-audit-and-baseline.md) | Verified evidence and ownership map |
| 119 | [Canonical musical authority and harmonic timeline](completed/119-canonical-musical-authority.md) | Shared authority and projections |
| 120 | [Durable stage taxonomy, ordering, and invalidation](completed/120-stage-taxonomy-and-order.md) | One workflow order and dependency graph |
| 121 | [Melody identity, anchors, and mutation evidence](completed/121-melody-identity-and-mutation-evidence.md) | Shared note identity and reports |
| 122 | [AI Fix canonical context cutover](completed/122-ai-fix-canonical-context.md) | Declared harmony controls repair |
| 123 | [Per-track enhancement harmonic validation](completed/123-track-enhancement-harmonic-validation.md) | Chord-aware bounded enhancement |
| 124 | [Arrangement context and generator contracts](completed/124-arrangement-context-and-generator-contracts.md) | Canonical planner/executor inputs |
| 125 | [Generated-track quality validators](completed/125-generated-track-quality-validators.md) | Typed reports for every role |
| 126 | [Boundary-only Cohesion](completed/126-boundary-only-cohesion.md) | No whole-song Cohesion edits |
| 127 | [Deterministic full-song critic](completed/127-deterministic-full-song-critic.md) | Reproducible whole-song issues |
| 128 | [AI Full-Song Enhance](completed/128-ai-full-song-enhance.md) | Separate targeted AI stage |
| 129 | [Stage comparison and diagnostic reports](completed/129-stage-comparison-and-diagnostic-reports.md) | Persisted service/UI evidence |
| 130 | [Reference-song integration and rollout](completed/130-reference-song-integration-and-rollout.md) | End-to-end acceptance and cleanup |

The code-verified Task 118 audit is [`../music-context-audit.md`](../music-context-audit.md).

## Ordering notes

- Tasks 118–121 establish evidence and shared contracts used by every cutover.
- Tasks 122–126 align existing stages and must preserve explicit approvals.
- Task 127 is deterministic and non-mutating; Task 128 is the only post-Cohesion
  full-song AI mutation stage.
- Humanization remains after Task 128 and consumes approved, bypassed, or no-op
  Full-Song Enhance selection according to Task 128.
- Tasks 129–130 close diagnostics, cleanup, and rollout after runtime
  contracts are stable.
