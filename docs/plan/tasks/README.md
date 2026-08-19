# Composition builder implementation tasks

These tasks implement the roadmap in root `PLAN.md`. Complete them in order
unless a task explicitly permits parallel work. Completed 110–117 tasks in the
`completed/` directory are predecessor evidence, not dependencies by number.

| Order | Task | Horizon |
| ---: | --- | --- |
| 001 | [Baseline characterization and support-contract reconciliation](001-baseline-characterization.md) | MVP |
| 002 | [v4 project schema and migration scaffold](002-v4-project-schema.md) | MVP |
| 003 | [Structured musical primitives](003-musical-primitives.md) | MVP |
| 004 | [Composition profile and mood catalog](004-profile-mood-catalog.md) | MVP |
| 005 | [Composition settings application contract](005-composition-settings-service.md) | MVP |
| 006 | [Project Setup UI](006-project-setup-ui.md) | MVP |
| 007 | [Structured chord and progression domain](007-chord-progression-domain.md) | MVP |
| 008 | [Harmony application service](008-harmony-service.md) | MVP |
| 009 | [Harmony editor UI](009-harmony-editor-ui.md) | MVP |
| 010 | [Structured song parts and section types](010-song-part-model.md) | MVP |
| 011 | [Processing artifact and stage-run manifest](011-stage-run-manifest.md) | MVP |
| 012 | [Persistent stage runner and recovery](012-stage-runner.md) | MVP |
| 013 | [Automatic import orchestration](013-automatic-import-orchestration.md) | MVP |
| 014 | [Melody Parts progress and recovery UI](014-melody-parts-ui.md) | MVP |
| 015 | [Deterministic MIDI normalization](015-midi-normalization.md) | MVP |
| 016 | [Project-key transposition](016-project-key-transposition.md) | MVP |
| 017 | [Technical correction stage](017-technical-correction.md) | MVP |
| 018 | [Enhancement context and intensity contracts](018-enhancement-contracts.md) | MVP |
| 019 | [Context-aware AI enhancement adapter](019-ai-enhancement-adapter.md) | Later |
| 020 | [Artifact comparison, bypass, and retry UX](020-artifact-comparison-ui.md) | MVP |
| 021 | [Persistent structure occurrence identity](021-structure-occurrences.md) | MVP |
| 022 | [Profile-independent arrangement roles](022-arrangement-roles.md) | Later |
| 023 | [Arrangement-before-cohesion dependency migration](023-arrangement-cohesion-order.md) | Later |
| 024 | [Arrangement-aware cohesion](024-arrangement-aware-cohesion.md) | Later |
| 025 | [Seeded humanization stage](025-seeded-humanization.md) | Later |
| 026 | [Render, mix, profile processing, and master handoff](026-production-handoff.md) | Later |
| 027 | [Stage lineage and commercial release provenance](027-release-provenance.md) | Later |
| 028 | [Canonical Spring API decision and adapter](028-spring-api-adapter.md) | Later |
| 029 | [CLI support decision and service-backed adapter](029-cli-adapter.md) | Later |
| 030 | [End-to-end rollout, documentation, and release acceptance](030-rollout-acceptance.md) | Later |

Tasks 002–021 establish the UI/domain-first milestone. Task 019 can follow the
MVP because task 018 provides a transparent deterministic placeholder contract.
Tasks 028–029 are optional adapters and must not block the supported desktop
composition workflow.

