# Task 113 — Optional AI-Assisted Track Fix

## Goal

Let the user optionally request a bounded musical fix of one cleaned track,
preview the result, and explicitly approve it as the base for later processing.

## Dependencies

- Task 112 accepted.

## Requirements

- Add an explicit choice after Clean MIDI: **Keep cleaned MIDI** or **Create AI
  fix**. Keeping cleaned MIDI must immediately allow the workflow to continue.
- Build a path-free model input from the current cleaned-MIDI fingerprint and
  bounded musical analysis: PPQ, meter, tempo map, key/chord confidence, pitch
  range, note-density summaries, and detected problem regions. Do not send
  source paths, project paths, rights data, or raw commands.
- Define a versioned, code-owned edit vocabulary and numeric limits for the
  musical corrections the feature supports. At minimum distinguish timing,
  note duration/velocity, collision or duplicate removal, and bounded pitch
  correction; any note addition must be separately bounded to a detected local
  gap and may not generate a new phrase.
- Require strict JSON containing exact input identity and only allowed edits.
  Reject unknown fields, stale hashes, invalid ranges, excessive edits,
  executable text, and unsupported operations before touching MIDI.
- Apply a valid plan in deterministic Kotlin/worker code to a new draft MIDI,
  validate the round-trip, and persist a human-readable diff, audit, model
  identity/license provenance, and input/output hashes.
- Support A/B preview of cleaned versus AI-fixed MIDI, per-edit inspection,
  approve, reject, regenerate, and return-to-cleaned actions. Only an approved
  draft can become selected input.
- If the local model is unavailable or invalid, preserve cleaned MIDI as the
  usable selection and provide retry/recovery; never silently label a
  deterministic no-op as an AI fix.

## Tests

- Use a fake model for valid plans and adversarial responses: malformed JSON,
  unknown fields, paths/commands, stale IDs/hashes, out-of-range edits,
  excessive patches, collisions, and non-round-tripping output.
- Test deterministic application, audit completeness, approval binding,
  rejection/regeneration, selection reversal, and downstream invalidation.
- Verify skipped/unavailable AI does not block Lo-fi Feel or analysis and that
  no source/raw/cleaned file changes.
- Add view-model/UI tests for choice, progress, failure, A/B preview, diff
  review, approval, and returning to cleaned MIDI.

## Acceptance criteria

- AI can propose only bounded musical corrections to one exact cleaned track;
  code applies them to a derived draft; and nothing downstream uses that draft
  until the user approves it.

## Out of scope

- Free-form prompting, artist imitation, cloud model setup, executing model
  code, whole-song arrangement, or transition generation.
