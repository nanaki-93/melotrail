# Task 066 — Semantic Backend Loading and Operation Feedback

## Goal

Make backend activity, information, warnings, success, and errors immediately
understandable without adding another navigation row or duplicating actions.

## Dependencies

- Task 065 accepted.

## Requirements

- Define a UI-neutral operation model with stable operation/session ID, phase,
  completed work, total work when known, short message, optional safe artifact
  label, cancellability, retry action, and typed outcome severity.
- Add progress boundaries for project open/hydration, import, inspection, audio
  cleanup, transcription, MIDI repair, MIDI rendering, preview decoding/render,
  cohesion, arrangement, approval, stem render, mixing, audio lo-fi, mastering,
  and export.
- Show determinate progress only when counts are meaningful. Otherwise use an
  indeterminate indicator and name the backend phase explicitly.
- Display loading feedback in the stable status surface and contextual action;
  avoid layout-shifting global banners for routine progress.
- Use the following semantic palette through theme tokens:
  - primary/teal: selected, ready, completed successfully;
  - information/blue: neutral facts and prerequisites;
  - warning/amber: recoverable issue, stale artifact, or user review required;
  - loading/violet: active backend or AI work;
  - error/red: failed or blocking condition.
- Pair every color with text and/or an icon. Meet contrast requirements in dark
  mode and do not infer severity by searching words in a message.
- Keep the last meaningful success or failure visible until superseded or
  dismissed. Do not replace a failure with generic `Idle`.
- Disable only controls whose mutation or playback would conflict with the
  active operation. Keep safe navigation and inspection available.
- Expose retry only for idempotent/recoverable operations and cancellation only
  where the backend honors a safe boundary.
- Log the same operation/phase IDs used by the UI for diagnosis.

## Tests

- Unit tests for operation transitions, unknown totals, completion, failure,
  stale callback rejection, retry eligibility, and cancellation boundaries.
- View-model tests covering every long operation listed above.
- Compose tests for all severities, determinate and indeterminate loading,
  screen-reader live regions, text/icon redundancy, and targeted disabling.
- Theme tests for token consistency and contrast.

## Acceptance criteria

- A user can always tell whether Melotrail is idle, loading locally, waiting for
  the worker/model/renderer, validating, complete, warning, or failed.
- Long operations never appear frozen and never report invented percentages.
- Information, warnings, loading, success, and errors are visually distinct and
  remain understandable without color.
- Error severity is typed rather than derived from message wording.

## Out of scope

- Time-remaining predictions without measured historical data.
- Notifications outside the application.
- Telemetry or remote monitoring.

