# Task 103 — Track-Process Workflow Documentation

## Goal

Publish an accurate, user-facing explanation of how a track moves from a new
project through import, preparation, arrangement, mixing, and release export.

## Dependencies

- Task 102 accepted.

## Requirements

- Add a dedicated Markdown document under `../../..` and link it from `../../../../README.md`.
- Describe each workflow stage in the order derived by `WorkflowReadModel`:
  create/open, import and inspect, audio transcription when needed, MIDI repair
  and approval, optional MIDI feel, analysis, structure, cohesion, arrangement,
  MIDI/stem generation, mix/master, and release export.
- For every stage state: the user action, inputs, resulting canonical artifacts,
  prerequisites, whether the source changes, what can make the stage stale,
  and the safe recovery action.
- Include one compact diagram (Mermaid is preferred) that differentiates the
  direct-MIDI route from the eligible solo-piano audio route, then joins the
  common track process.
- Use product UI labels that exist after the planned simplification. Do not
  present optional Spring API or implementation details as the normal user
  workflow.

## Tests

- Link and Markdown lint/check if configured; otherwise verify all referenced
  files and anchor links locally.
- Cross-check every documented transition against `WorkflowReadModel` and the
  corresponding application service tests.

## Acceptance criteria

- A new user can tell which action is next, why a later action is unavailable,
  and where each meaningful output is stored without reading Kotlin code.

## Out of scope

- Changing workflow semantics or adding a generic DAW tutorial.
