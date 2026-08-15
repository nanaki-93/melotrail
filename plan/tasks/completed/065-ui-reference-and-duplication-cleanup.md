# Task 065 — UI Reference Alignment and Duplication Cleanup

## Goal

Bring the Compose workspace as close as practical to `../../UI.png`, remove the
workflow-status menu line, and give every user action one clear home.

## Dependencies

- Task 064 accepted.

## Requirements

- Remove the second header row containing `Project · Complete`,
  `Prepare · Current`, `Structure · Blocked`, and similar workflow badges.
- Retain one primary navigation row: Project, Structure, Arrange, Mix & Master,
  and Library. Later tasks may add contextual workflow stages without creating
  another global menu row.
- Use one persistent bottom transport backed by the unified playback session.
  Remove preview transport from the status panel and duplicate play/seek/volume
  controls elsewhere; contextual play buttons select a source and delegate to
  the same transport.
- Audit and remove duplicated New/Open/Build, retry, readiness, next-action,
  MIDI cleanup, preview, and status functions. Assign ownership:
  - header: brand, primary navigation, project selector/actions;
  - left/context panels: sources and selected-part actions;
  - center: structure, arrangement, and timeline;
  - right/status context: current plan/detail and recovery;
  - footer: transport and master output.
- Preserve the dark visual language, three-column hierarchy, colored
  instrument lanes, selected-section emphasis, rounded panels, and compact
  footer shown by the reference image.
- Keep feature availability truthful; a cleaner UI must not hide prerequisites
  or silently trigger an unavailable operation.
- Fix duplicated or unreachable responsive layout branches and establish
  explicit wide, medium, and narrow compositions.
- Preserve keyboard access, focus order, semantic labels, and shortcuts.

## Tests

- Compose assertions that the workflow-status row is absent and the primary
  navigation appears exactly once.
- Assert a single transport, volume control, readiness recovery action, and
  operation retry surface in each relevant layout.
- Screenshot/golden or documented visual comparison at wide, medium, and narrow
  sizes against `../../UI.png`.
- Keyboard traversal, shortcut, accessible-name, disabled-reason, and contrast
  checks.

## Acceptance criteria

- No `Project · Complete` / `Prepare · Current` status row remains.
- The visible workspace is recognizably organized like `../../UI.png`.
- The same command is not presented in multiple competing panels.
- All contextual preview buttons control the one persistent transport.
- Wide, medium, and narrow layouts contain no duplicate branch or unreachable
  panel composition.

## Out of scope

- Recreating unimplemented travel imagery, weather, location, or video-concept
  features visible in the mockup.
- New MIDI or AI processing behavior.
- A new logo.

