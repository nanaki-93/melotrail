# Task 046 — MIDI Quality Review UI

## Goal

Let users understand raw-to-clean changes and explicitly retry with a named
cleanup profile.

## Dependencies

- Tasks 035 and 045 accepted.

## Requirements

- Show profile, raw→clean metrics, warnings, timing-change summary, and readiness
  in the selected-part inspector.
- Offer explicit profile selection/retry; make conservative the default and
  visually warn before `tighten-timing`. Do not expose arbitrary unsafe fields.
- Explain exactly why analysis/structure/arrangement is blocked and provide the
  next valid action. Refresh preview fingerprint after a successful retry.
- Preserve accessibility and view-model/service separation.

## Tests

- View-model/Compose tests for legacy, clean, warned, failed, stale, each profile,
  confirmation, retry, and downstream readiness.

## Acceptance criteria

- Users can choose stronger cleanup knowingly and see what changed before
  arranging.

## Out of scope

Worker algorithm changes, note editing, or visual shell redesign.
