# Task 086 — Focused Arrange Page

## Goal

Rebuild Arrange as the single focused planning destination shown in
`../../pictures/App-pages.png` while preserving bounded deterministic/Qwen rules.

## Dependencies

- Task 085 accepted.

## Requirements

- Reproduce the two planner-choice cards, instrument toggle grid, style field,
  intensity control, and single Generate Arrangement CTA.
- Planner and instrument values must come from existing typed enums and
  allow-lists. Model output remains strict JSON planning data and is never
  executed as code, commands, paths, notes, DSP, or arbitrary instruments.
- Enable generation only when canonical structure, analyses, and cohesion
  prerequisites are current. Show one short missing-prerequisite reason near
  the CTA and detailed diagnostics on demand.
- Preserve deterministic auto-approval behavior and explicit Qwen draft review
  and approval. Never present a draft as approved/current.
- Keep review/approval in the Arrange page without composing the Structure,
  Timeline, Song Plan, or Mix pages beneath it.
- Preserve operation cancellation boundaries, stale evidence, one feedback
  banner, and one safe retry action.

## Verification

- Compose tests cover deterministic/Qwen selection, instrument toggles,
  settings, blocked prerequisites, generating, draft, approval, stale, failed,
  and approved states.
- Interaction tests verify the existing typed planner/settings/generate/approve
  intents and exactly one primary generation action.
- Tests assert no other destination page root and no duplicate song-plan or
  feedback surface.
- Capture and overlay a deterministic Arrange golden against the numbered
  Arrange region of `../../pictures/App-pages.png`.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Out of scope

New planners, arbitrary instruments, MIDI note editing, generation algorithms,
model execution, or changes to arrangement schemas.
