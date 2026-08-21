# Task 128 — AI Full-Song Enhance

## Goal

Add an explicit, optional, targeted AI correction stage after approved Cohesion
and deterministic criticism and before seeded Humanization.

## Dependencies

Task 127.

## Public contracts

- Add `FullSongEnhancementSelection` with `UNRESOLVED`, `BYPASS`, `NO_OP`, and
  `APPROVED` states. Only `NO_OP` and `APPROVED` require a current critic hash;
  bypass records the exact current Cohesion input hash.
- Add strict versioned DTOs for `FullSongEnhancementInput`,
  `FullSongEnhancementPlan`, typed operations, candidate/approved artifact
  references, validation, and application reports.
- Add an application service with `generateCandidate`, `load`, `approve`,
  `selectBypass`, and deterministic input-resolution operations. Compose uses
  this service and does not parse model output or write files.

## AI and operation contract

- Send the model only canonical whole-song context, current actionable critic
  issues, exact target windows, note identities needed for those windows, current
  budgets, and the allowed operation schema. Never send blocking issues as
  repairable targets.
- Require exact input/context/critic hashes and strict JSON. Reject prose,
  unknown fields, unknown IDs, duplicate note operations, and any target/window
  not named by the critic.
- Allow only: `REVOICE_CHORD`, `SIMPLIFY_BASS_LEAP`, `REDUCE_DENSITY`,
  `REMOVE_COLLISION`, `ADJUST_TIMING`, `ADJUST_VELOCITY`, `ADJUST_DURATION`,
  `CORRECT_CHORD_CLASH`, and `ADJUST_TRANSITION_NOTE`.
- Kotlin resolves each typed operation to exact note edits, applies them in
  deterministic target/tick/note-ID order, and publishes only after all common,
  harmonic, role, and critic-window validators pass.

## Limits and invariants

- For each occurrence/role target, changed existing notes plus additions and
  deletions must not exceed `floor(noteCount * 0.05)`.
- Additions and deletions must each not exceed `floor(noteCount * 0.02)`.
- A zero integer budget permits no operation of that kind.
- Melody anchors cannot be deleted or pitch-shifted. Other melody pitch changes
  are at most two semitones and must be valid for the active chord/scale.
- Structure, occurrence duration/order, canonical harmony, tempo, meter,
  instrument assignment, MIDI format, and regions outside critic issue windows
  are immutable.
- A plan may address at most the first 32 actionable issues in deterministic
  critic order. Unaddressed issues remain visible; they do not permit extra edits.

## Workflow and UI behavior

- A candidate is draft evidence only. Show existing comparison metrics, issues
  addressed/unaddressed, edit counts, warnings, preview, Approve, Retry, and
  Bypass through the current desktop review patterns.
- Approval atomically selects hash-validated outputs for the entire ensemble.
  Partial role approval is not supported.
- With zero actionable issues, publish `NO_OP` without calling the model and use
  Cohesion artifacts as the selected downstream inputs.
- Humanization is blocked while selection is `UNRESOLVED`; it consumes approved
  outputs for `APPROVED` and Cohesion outputs for `BYPASS` or `NO_OP`.
- A retry never overwrites earlier draft/approved evidence. Any upstream or
  policy/model/context change invalidates the selection and Humanization onward.

## Tests

- Strict model-response parsing and every operation type.
- Exact 5%/2% floors, zero budgets, 32-issue cap, anchor and two-semitone rules.
- Off-window, stale-hash, unknown-ID, partial-application, and invalid-MIDI
  rejection leave selection unchanged.
- No-issue flow makes no model call and records `NO_OP`.
- Approve, retry, bypass, reload, invalidation, and Humanization input precedence.
- Deterministic fake plans produce byte-identical MIDI and reports.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Full-song AI editing exists only as this named workflow stage.
- AI is a bounded planner; Kotlin is the executor and validator.
- Rendering can trace its note-level input through enhancement selection and
  Humanization selection without file-existence inference.

## Exclusions

Do not regenerate the arrangement, add arbitrary creative remixing, modify
audio, or move the stage after Humanization.
