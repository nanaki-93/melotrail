# Composition builder implementation tasks

These tasks implement the roadmap in root `PLAN.md`. Complete them in order
unless a task explicitly permits parallel work. Completed 110–117 tasks in the
`completed/` directory are predecessor evidence, not dependencies by number.

Cross-cutting completion rule: replacement is not complete while its superseded
project-owned implementation remains. Each task that cuts over a runtime path
must delete obsolete source, exclusive tests/fixtures, registrations, routes,
configuration, dependencies, resources, UI controls, build targets, and stale
documentation after supported data/callers are migrated. Git history is the
archive. A compatibility reader may remain only while the declared supported
schema window actively requires it and must have a named removal condition.

| Order | Task | Horizon |
| ---: | --- | --- |
| 001 | [Baseline characterization and support-contract reconciliation](completed/001-baseline-characterization.md) | MVP |
| 002 | [v4 project schema and migration scaffold](completed/002-v4-project-schema.md) | MVP |
| 003 | [Structured musical primitives](completed/003-musical-primitives.md) | MVP |
| 004 | [Composition profile and mood catalog](completed/004-profile-mood-catalog.md) | MVP |
| 005 | [Composition settings application contract](completed/005-composition-settings-service.md) | MVP |
| 006 | [Project Setup UI](completed/006-project-setup-ui.md) | MVP |
| 007 | [Structured chord and progression domain](completed/007-chord-progression-domain.md) | MVP |
| 008 | [Harmony application service](completed/008-harmony-service.md) | MVP |
| 009 | [Harmony editor UI](completed/009-harmony-editor-ui.md) | MVP |
| 010 | [Structured song parts and section types](completed/010-song-part-model.md) | MVP |
| 011 | [Processing artifact and stage-run manifest](completed/011-stage-run-manifest.md) | MVP |
| 012 | [Persistent stage runner and recovery](completed/012-stage-runner.md) | MVP |
| 013 | [Automatic import orchestration](completed/013-automatic-import-orchestration.md) | MVP |
| 014 | [Melody Parts progress and recovery UI](completed/014-melody-parts-ui.md) | MVP |
| 015 | [Deterministic MIDI normalization](completed/015-midi-normalization.md) | MVP |
| 016 | [Project-key transposition](completed/016-project-key-transposition.md) | MVP |
| 017 | [Technical correction stage](completed/017-technical-correction.md) | MVP |
| 018 | [Enhancement context and intensity contracts](completed/018-enhancement-contracts.md) | MVP |
| 019 | [Context-aware AI enhancement adapter](completed/019-ai-enhancement-adapter.md) | Later |
| 020 | [Artifact comparison, bypass, and retry UX](completed/020-artifact-comparison-ui.md) | MVP |
| 021 | [Persistent structure occurrence identity](021-structure-occurrences.md) | MVP |
| 022 | [Profile-independent arrangement roles and sound intent](022-arrangement-roles.md) | Later |
| 022B | [Metadata-driven Instrument Registry and resolver](022b-instrument-registry-resolution.md) | Later |
| 023 | [Arrangement-before-cohesion dependency migration](023-arrangement-cohesion-order.md) | Later |
| 024 | [Arrangement-aware cohesion](024-arrangement-aware-cohesion.md) | Later |
| 025 | [Seeded humanization stage](025-seeded-humanization.md) | Later |
| 026 | [Render, mix, profile processing, and master handoff](026-production-handoff.md) | Later |
| 027 | [Stage lineage and commercial release provenance](027-release-provenance.md) | Later |
| 027B | [Usage-based instrument credits export](027b-instrument-credits-export.md) | Later |
| 028 | [Spring API retain-or-delete migration](028-spring-api-adapter.md) | Later |
| 030 | [End-to-end rollout, documentation, and release acceptance](030-rollout-acceptance.md) | Later |

Tasks 002–021 establish the UI/domain-first milestone. Task 019 can follow the
MVP because task 018 provides a transparent deterministic placeholder contract.
Task 022B must complete before the arrangement-order migration in Task 023.
Task 027B turns Task 027's immutable used-instrument/license lineage into the
copy-ready credits artifact paired with each commercial audio export.
Task 028 is an optional adapter and must not block the supported desktop
composition workflow.
