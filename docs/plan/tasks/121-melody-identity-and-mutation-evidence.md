# Task 121 — Melody identity, anchors, and mutation evidence

## Goal

Give every note-modifying stage one stable way to identify melody notes, protect
musically important anchors, calculate budgets, and explain exact mutations.

## Dependencies

Tasks 119–120.

## Public contracts

- Add a stable `MelodyNoteId` derived from selected input SHA-256, MIDI track and
  channel, note-on ordinal, pitch, and original start/end ticks. It identifies an
  input note; derived timing is never part of its identity.
- Add `MelodyIdentity` containing ordered notes, phrase IDs, anchor IDs, source
  hash, occurrence mapping, and schema/version hash.
- Add `MelodyIdentityBuilder` using existing analyzed phrase evidence when
  current. When phrase evidence is absent, split phrases only at rests of at
  least one canonical beat.
- Mark the first and last note of every phrase as anchors. Also anchor notes held
  for at least one canonical beat and local highest/lowest notes held for at
  least half a beat. Deduplicate overlapping rules.
- Add a common `MidiMutationReport` with input/output hashes, context hash,
  target, stage, operation, note ID, before/after values, reason code, budget
  totals, warnings, and rejection summary.
- Add reusable invariant checks for anchor preservation, allowed pitch delta,
  note-count budget, occurrence/window bounds, tempo/meter preservation, and
  deterministic report ordering.

## Required behavior

- Identity derivation is deterministic across project relocation and repeated
  reads of the same MIDI.
- A stage may append its own reason codes but cannot change the common evidence
  semantics.
- Reports contain no absolute paths, raw model prose, timestamps in hashes, or
  unbounded diagnostic strings.
- Existing first/last-note protections must migrate to the common anchor set;
  delete superseded exclusive helpers once all direct callers compile against
  the common contract.

## Tests

- Stable identity for chords, overlapping notes, multiple tracks/channels, and
  duplicate pitches.
- Phrase fallback and each anchor rule at exact beat boundaries.
- Timing-only changes retain note identity; pitch/deletion of an anchor fails.
- Budget and window validators reject one-over-limit edits.
- Mutation reports are deterministic and reject duplicate note operations,
  invalid hashes, unknown reason codes, and control/path leakage.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- AI Fix, Enhance, Cohesion, Full-Song Enhance, and Humanization can use the same
  identity/evidence primitives without redefining anchors.
- The original and every accepted mutation are traceable by stable note ID and
  exact before/after values.

## Exclusions

Do not change any stage's model prompt or edit policy in this task.
