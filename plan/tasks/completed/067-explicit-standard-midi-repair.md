# Task 067 — Explicit Standard MIDI Repair Stage

## Goal

Turn the existing hidden cleanup into an explicit, reviewable **Repair MIDI**
stage after MIDI import or audio transcription and before analysis.

## Dependencies

- Task 063 accepted.

## Terminology

“Standard” means a deterministic, documented repair profile for valid Standard
MIDI Files; it does not claim that one universal musical-cleanup standard
exists. The first default is the existing `transcription-safe` profile.

## Requirements

- Change the workflow so MIDI import/transcription publishes immutable raw MIDI
  and an explicit Repair MIDI action produces the canonical repaired MIDI.
- Direct MIDI input must also preserve the imported file as source/raw evidence;
  never edit it in place.
- Validate format 0/1, positive PPQN, tracks, deltas, channel/pitch/velocity
  bounds, note pairing, positive note duration, tempo, and time signatures.
- The standard repair profile must deterministically:
  - canonicalize note-off representation and event ordering;
  - remove exact duplicate notes and unusably short/quiet transcription noise;
  - remove orphan note-offs and redundant sustain controller values;
  - end same-channel/pitch retrigger overlaps at the next start;
  - bound transcription velocity outliers;
  - preserve valid track/meta information, tempo, and time signatures.
- Fix the duplicated quantization assignment in the current worker while
  preserving behavior covered by characterization tests.
- Write repaired MIDI and its quality report atomically. The report must include
  input/output hashes, profile version, counts, each repair category, maximum
  timing shift, tempo/signature preservation, warnings, and recommendation.
- Expose raw/repaired A/B preview through the unified transport.
- Require explicit approval when a repair removes or moves more than configured
  conservative thresholds; normal standard repair may complete automatically
  but remains reviewable.
- Analysis reads only the approved repaired MIDI and is blocked on missing,
  invalid, rejected, or stale quality evidence.
- Retrying repair invalidates analysis, lo-fi MIDI, cohesion, arrangement,
  generated MIDI, stems, mixes, master, and release metadata—but never raw input.
- Keep versioned readers for projects whose clean MIDI was produced by the old
  embedded workflow.

## Tests

- Python unit and property-style fixture tests for malformed MIDI, duplicates,
  short/quiet notes, orphan offs, retriggers, sustain, velocities, tempo maps,
  time signatures, multiple tracks, idempotence, and deterministic output.
- Kotlin service tests for raw publication, atomic repair/report publication,
  rollback, approval thresholds, stale detection, and legacy migration.
- Preview tests for raw/repaired selection.
- End-to-end tests for direct MIDI and audio-transcription paths.
- Verify input/source hashes before and after every test operation.

## Acceptance criteria

- Repair MIDI is a visible stage after MIDI generation/import and before
  analysis.
- The source and raw MIDI never change.
- Running the same repair twice produces byte-identical MIDI and report content
  apart from explicitly excluded timestamps, preferably with no timestamps.
- Analysis cannot consume unvalidated or stale repaired MIDI.
- Users can see exactly what was repaired and audition raw versus repaired.

## Out of scope

- Lo-fi timing, tempo normalization, AI melody edits, or arrangement changes.
- Arbitrary per-event MIDI editing.
- Claiming that repair clears rights to copyrighted source material.

