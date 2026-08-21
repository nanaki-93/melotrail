# Task 120 — Durable stage taxonomy, ordering, and invalidation

## Goal

Make persisted stage evidence, readiness, desktop workflow, build inputs, and
dependency invalidation represent the same canonical order.

## Dependencies

Task 119.

## Required changes

- Retain all existing `StageId` serialized names and add explicit IDs:
  `ai-fixed`, `midi-feel`, `critiqued`, `full-song-enhanced`, `humanized`, and
  `audio-textured`.
- Define part stages explicitly as source, extracted, cleaned, normalized,
  transposed, corrected, AI-fixed, enhanced, MIDI Feel, and analyzed. Remove the
  ordinal-based `isPartStage` rule.
- Define project order explicitly as structured, arranged, generated, cohesion,
  critiqued, full-song enhanced, humanized, rendered, mixed, audio textured,
  mastered, and exported.
- Extend `WorkflowStage`, actions, prerequisites, artifact identifiers,
  readiness, and project workflow references for Critic and Full-Song Enhance.
- Make `WorkflowReadModel`, application services, build/render input selection,
  and desktop navigation use the same order. Arrangement must precede generated
  MIDI and Cohesion; Humanization must follow Full-Song Enhance selection.
- Extend invalidation so any upstream content or selection change stales every
  descendant but retains prior artifacts as evidence. A critic rerun invalidates
  Full-Song Enhance onward; selecting enhancement or bypass invalidates
  Humanization onward.
- Replace ordering logic based on enum ordinals with named ordered lists or
  dependency edges.

## Persistence cutover

- Canonical schema-v4 enum wire names remain stable.
- Update the canonical schema directly; do not add defaults, aliases, readers,
  migration code, or UI states for superseded project shapes.
- A canonical project without a resolved Full-Song Enhance selection becomes
  ready only after a current critic report and an approved candidate, recorded
  no-op, or explicit bypass.

## Tests

- Serialization round trips all canonical `StageId` values.
- Table-test every `WorkflowChange` against exact invalidated descendants.
- Read-model tests cover blocked, current, review, stale, complete, bypass, and
  no-op states for the two new stages.
- Rendering refuses stale/missing enhancement selection and resolves approved or
  bypass inputs exactly.
- Unsupported project schemas and superseded v4 shapes fail to open; canonical
  fixtures round-trip without rewrite.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- All workflow representations expose one order and one next recovery action.
- File existence never marks a stage complete.
- No downstream stage can consume Cohesion directly when Full-Song Enhance
  selection is unresolved.

## Exclusions

Do not implement Critic or Full-Song Enhance processing here; introduce only
their durable workflow vocabulary and safe unresolved state.
