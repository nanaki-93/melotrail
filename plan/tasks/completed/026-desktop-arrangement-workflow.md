# Task 026 — Desktop Arrangement and Song Timeline

## Goal

Expose deterministic/Qwen planning, review/approval, AI song-plan information, and a proportional instrument timeline using validated arrangement artifacts.

## Dependencies

- Task 025 prepared project workflow and Task 023 arrangement services.

## Requirements

- Add planner selection, style input, and bounded instrument choices.
- Keep piano selected and disabled because detailed arrangements require the source piano.
- Validate non-empty structure, complete MIDI analyses, style length, and selected instruments before generating.
- Generate the global song plan, section variations, and detailed arrangement through one service request with stage progress.
- Deterministic output is approved; Qwen output is a draft with preview, approve, and regenerate actions.
- Display the AI song plan table with instance label, purpose, energy, instruments, and transition intent.
- Render a proportional timeline using section durations, stable occurrence labels, and five logical instrument lanes.
- Selecting a section shows its role, energy, instrument plan details, and transition in/out. Editing detailed notes or arbitrary model JSON is not allowed.
- Show stale badges when project parts/structure/settings no longer match an artifact; do not present stale output as current.
- Preserve strict validation/allow-lists and never execute model-provided code, commands, notes, or paths.

## Tests

- Planner validation and deterministic success.
- Fixture-backed Qwen draft, invalid response, approval, and regeneration states.
- Timeline lane mapping, proportional section sizing, selection, empty/stale states, and accessibility descriptions.
- Reopen project and reconstruct the same arrangement snapshot from artifacts.

## Acceptance criteria

- A user can generate and, when required, approve a valid detailed arrangement without the CLI.
- Timeline and song-plan UI derive only from validated snapshots.
- The user can see why Build Song is disabled when arrangement prerequisites are incomplete.
- Deterministic mode works without Qwen.

## Out of scope

- Piano roll, arbitrary note/role editing, drag editing of clips, AI-generated audio, and scene/video generation.

