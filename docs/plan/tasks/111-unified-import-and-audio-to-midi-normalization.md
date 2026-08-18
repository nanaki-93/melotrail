# Task 111 — Unified Import and Audio-to-MIDI Normalization

## Goal

Make direct MIDI and eligible WAV/WAVE/MP3 import converge on the same validated
raw-MIDI boundary before MIDI cleaning.

## Dependencies

- Task 110 accepted.

## Requirements

- Keep one import entry point that detects and validates the selected source by
  extension and actual container. Accept `.mid`/`.midi`, `.wav`/`.wave`, and
  `.mp3`; reject mismatches and unsupported data before project registration.
- Preserve the imported file under `source/` as immutable evidence. Use safe,
  collision-aware part identities and project-confined paths.
- For MIDI, publish a byte-preserved validated raw-MIDI artifact without
  transcription.
- For audio, run the current eligible solo-piano transcription route and
  publish its output only after Standard MIDI validation and the transcription
  quality gate succeed. Never describe transcription as format conversion that
  preserves every musical detail.
- Keep conservative audio inspection/preparation as an explicit recovery or
  quality option before transcription, not a competing required happy path.
  Prepared audio must remain derived and reversible.
- Record source and raw-MIDI fingerprints so interruption, retry, and stale
  worker output cannot register a false success.
- Present the same next action for both successful routes: **Clean MIDI**.

## Tests

- Cover valid and invalid MIDI, WAV/WAVE, and MP3 containers; extension/content
  mismatches; path traversal; collisions; interrupted publication; and worker
  failure.
- Verify direct MIDI bypasses transcription and retains byte identity.
- Verify audio import never registers raw MIDI until transcription output has
  passed validation and that source hashes do not change.
- Test retry/idempotency and the optional prepared-audio recovery route.

## Acceptance criteria

- A successfully imported part always has immutable source evidence and one
  current raw-MIDI reference, regardless of whether the input was MIDI or
  eligible audio.

## Out of scope

- Full-mix transcription, stem separation, FLAC import, cloud transcription,
  or changes to later MIDI-cleaning behavior.
