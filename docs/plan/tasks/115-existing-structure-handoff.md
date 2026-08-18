# Task 115 — Existing Structure Handoff

## Goal

Preserve the existing Structure feature while making its handoff to Cohesion
use stable occurrence identities and the final selected per-part analyses.

## Dependencies

- Tasks 110 and 114 accepted.

## Requirements

- Keep the current Structure editing model and canonical sequence in
  `project.json`: users order known parts and repeated parts receive stable
  occurrence identities such as `A1` and `A2`.
- Require current analysis for every referenced part, where analysis is bound to
  the selected base/Lo-fi MIDI from the canonical resolver.
- Do not apply Lo-fi changes, AI track fixes, note edits, or transitions while
  saving Structure.
- Expose an ordered, immutable Cohesion input containing every occurrence and
  the exact adjacent pairs derived from it. For `n` occurrences, derive `n - 1`
  boundaries; a one-occurrence structure has zero boundaries.
- Bind the handoff to a structure fingerprint plus selected-MIDI and analysis
  fingerprints. Saving a changed order or occurrence set invalidates Cohesion
  and everything after it, while retaining old artifacts for inspection.
- Preserve current Structure UI behavior except for readiness/recovery text
  needed to reflect the cleaned pipeline.

## Tests

- Cover empty, single, repeated, reordered, inserted, and removed occurrences;
  unknown parts; missing/stale analyses; and stable occurrence identities.
- Verify exact ordered boundary derivation and structure fingerprint changes.
- Verify saving an unchanged structure is idempotent and a real change
  invalidates Cohesion/Arrangement without changing MIDI artifacts.
- Retain existing Structure UI and application-service regression tests.

## Acceptance criteria

- Structure remains behaviorally unchanged for users and supplies one complete,
  fingerprinted occurrence/boundary input to Cohesion.

## Out of scope

- A new song-form editor, automatic ordering, transition planning, or
  arrangement changes.
