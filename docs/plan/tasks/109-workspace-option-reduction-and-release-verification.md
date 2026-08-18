# Task 109 — Workspace Option Reduction and Release Verification

## Goal

Apply the same “one clear next action” principle across the remaining workspace
pages, verify the complete simplified experience, and align all documentation.

## Dependencies

- Tasks 105–108 accepted.

## Requirements

- Audit Overview, Structure, Arrange, Mix & Master, Export, Library, Video
  Preview, and Settings for duplicate, premature, disabled, or low-frequency
  controls. Keep the primary current workflow action visible and move optional
  configuration/evidence into labelled secondary panels or overflow routes.
- Do not remove features needed to inspect evidence, recover from a blocked
  workflow stage, configure a local sound library, or export a release. The
  reduction is presentation-only unless a separate product decision approves a
  domain change.
- Make status, prerequisites, error recovery, and progression consistent with
  the semantic colour system and the workflow documentation. One page must not
  claim completion while the underlying `WorkflowReadModel` is blocked/stale.
- Update README, troubleshooting, UI reference tokens, the track workflow, MIDI
  guide, and function documentation inventory for final labels, navigation,
  package locations, and any retained advanced routes.
- Perform an end-to-end local smoke using a fixture project where available:
  new project, direct MIDI import, audio import readiness/failure path, repair
  review, analysis, structure, arrange, mix/build prerequisite feedback, and
  export prerequisite feedback. Do not claim optional renderer/model/device
  success unless actually available.

## Tests

- Add focused tests for each page’s primary action and the disclosure/overflow
  route of each retained advanced control.
- Run `./gradlew test`, `./gradlew :desktopApp:test :desktopApp:build`, and the
  worker suite where worker-adjacent behaviour changed.
- Complete the documented wide/medium/narrow visual and keyboard checks at
  100%, 125%, and 150% scale.

## Acceptance criteria

- The workspace exposes fewer simultaneous choices, never hides the next safe
  action or recovery path, and its documentation accurately reflects the final
  product.

## Out of scope

- New audio/MIDI capabilities, a browser frontend, cloud services, telemetry,
  or unrelated architecture changes.
