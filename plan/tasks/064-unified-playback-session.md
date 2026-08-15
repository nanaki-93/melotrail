# Task 064 — Unified Playback Session and Root-Cause Repair

## Goal

Make imported-source preview, prepared-audio preview, MIDI preview, dry mix,
lo-fi mix, and master playback work reliably through one truthful playback
session.

## Dependencies

- Task 063 accepted.

## Requirements

- Reproduce and record failures for WAV/MP3 source, prepared WAV, rendered MIDI,
  dry mix, lo-fi mix, and master before changing behavior. Separate artifact
  resolution failures from decoding and audio-device failures.
- Replace parallel `preview` and `playback` ownership with one immutable session
  state containing session ID, source kind, exact artifact identity, lifecycle
  phase, position, duration, volume, failure stage, and retry action.
- Keep one `JvmAudioPlayer`, playback worker, and output line. A new source must
  cancel/stop and fully replace the old session before it can start.
- Decode and open the device off the UI dispatcher. Report `PLAYING` only after
  the device has started successfully.
- Support play, pause, resume, bounded seek, stop, replay after stop, replay
  after EOF, rapid source switching, project switching, and application close.
- Retrying must preserve the exact original/prepared/MIDI/mix selection.
- Validate the path, WAV container, samples, channel count, duration, and device
  compatibility before playback.
- Return useful recovery messages for missing artifact, unavailable renderer,
  unsupported MP3 provider, invalid WAV, and audio-device open/start failure.
- Add bounded diagnostic events with no audio content or model output.
- Do not report a preview as successful merely because its file resolved.

## Tests

- Fake decoder/device/line tests for every lifecycle transition, failure stage,
  EOF replay, source replacement, race, seek bound, volume, and close.
- View-model tests proving preview and mix cannot control different logical
  sources through the same player.
- Tests proving prepared-preview retry retains `PREPARED_CLEAN`.
- Tests for stale callbacks from a replaced session.
- Manual matrix on a real audio device for all available artifact kinds. Record
  unavailable optional dependencies rather than claiming them as tested.

## Acceptance criteria

- Every valid available artifact in the matrix is audible on the manual test
  device and all transport state matches what is heard.
- No two sources play concurrently and no stale session controls a new source.
- Stop and EOF both permit replay.
- Every failure identifies whether resolution, decode, prepare, device start,
  or runtime dependency failed.

## Out of scope

- UI layout redesign beyond the state/boundary needed for Task 065.
- Low-latency monitoring, recording, streaming, or waveform editing.
- MIDI lo-fi transformation.

