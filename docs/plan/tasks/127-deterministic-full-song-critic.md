# Task 127 — Deterministic full-song critic

## Goal

Analyze the complete approved cohesive ensemble with code-owned metrics and
produce targeted evidence for Full-Song Enhance without mutating MIDI.

## Dependencies

Tasks 119–126.

## Public contracts

- Add `FullSongCriticInput` containing the whole-song authority projection,
  approved Cohesion occurrence/role artifacts and hashes, approved arrangement,
  melody identity, role reports, and critic policy version.
- Add `FullSongIssue` with stable issue ID, category, severity, target role and
  optional occurrence, half-open tick/bar window, observed and expected numeric
  evidence, reason code, and suggested allow-listed correction families.
- Add `FullSongCriticReport` with schema version, input/context hashes, processor
  identity, aggregate metrics, deterministically ordered issues, warnings, and
  report hash.
- Add a read-only application service that runs/loads the report and marks the
  critic stage current only when every referenced input hash is current.

## Version-1 policy

- Re-run the common MIDI, anchor, harmony, and role invariants. Any violation is
  `BLOCKING` and cannot be sent to AI for repair.
- Report `HARMONIC_CLASH` for a non-chord note lasting at least half a beat that
  does not meet the passing-tone resolution rule.
- Report `VOICE_COLLISION` when two non-drum roles hold the same pitch for at
  least half a beat and the overlap is not an intentional melody doubling named
  by the approved arrangement.
- Report `BASS_LEAP` for a leap above nineteen semitones without opposite
  stepwise resolution on the immediately following note.
- Report `DENSITY_MISMATCH` when normalized note-on density differs from the
  arrangement target by more than 25 percentage points.
- Report `CONTRAST_DISCONTINUITY` when adjacent occurrence density changes by a
  ratio above 2.5 while planned energy differs by less than 20 percentage points.
- Report `TRANSITION_ABRUPTNESS` for an unplanned ensemble silence longer than
  one beat across a boundary or a boundary-beat onset count above twice the
  median beat onset count of both neighboring occurrences.
- Consolidate contiguous findings of the same category/target. Emit at most 64
  issues ordered by blocking/actionable severity, absolute tick, role, category,
  and stable ID. Record a bounded truncation warning when needed.

## Required behavior

- Criticism is offline, deterministic, and side-effect-free except for atomic
  report publication and workflow metadata.
- It must not call a model, change MIDI, select an output, or invent a subjective
  completion score.
- A report with no actionable issues is valid and enables Task 128 to record a
  hash-bound no-op.
- Keep the earlier arrangement-plan critic separate and clearly named.

## Tests

- One focused fixture for every metric at just-inside and exact-threshold values.
- Deterministic issue IDs, consolidation, ordering, truncation, and report hash.
- Repeated occurrences and boundary windows map to exact targets.
- Any changed Cohesion/arrangement/context hash makes the report stale.
- Verify byte-for-byte that critic execution does not modify input MIDI.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Every actionable issue is reproducible from persisted numeric evidence.
- Full-Song Enhance never needs to ask a model to decide whether the song is
  generally “good.”
- Blocking invariant failures route users to the owning upstream stage.

## Exclusions

Do not apply fixes, generate AI suggestions, or add audio-domain criticism.
