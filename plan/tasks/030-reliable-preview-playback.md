# Task 030 — Reliable Preview and Playback

## Goal

Make source, MIDI-part, and built-artifact monitoring reliable, responsive, and
truthful about rendering or audio-device failures.

## Requirements

- Split preview into typed resolution, optional MIDI rendering, and playback
  stages. Represent `checking`, `rendering`, `ready`, `playing`, `paused`,
  `stopped`, and `failed` in UI state; do not set “Previewing” until the player
  has accepted the decoded artifact and started its output line.
- Resolve audio-source preview directly when a supported local WAV is usable.
  For MP3, decode a fingerprinted PCM-24 monitor copy under `previews/` or
  `prepared/`; never overwrite the source and never make an MP3 extension for a
  WAV. MIDI previews continue using clean MIDI, analysis duration, piano, and a
  fingerprinted WAV.
- Move decoding and device opening off the Compose UI dispatcher. Change the
  player boundary so it returns/streams a typed start failure instead of
  swallowing exceptions. Preserve the underlying diagnostic cause in logs.
- Correct pause/resume/seek/source-switch lifecycle: only one output thread and
  line may own playback; a paused line must not race a replacement worker;
  stop/project switch/close must release bytes, line, and thread promptly.
- Validate WAV format, finite samples, duration, seek bounds, channel layout,
  and device support before output. If the device cannot open, retain the
  selected artifact and offer retry; do not crash the workspace.
- Add clear disabled reasons for a missing analysis, renderer, sound library,
  stale release, unsupported source, or unavailable device. Include selected
  part/artifact name and elapsed/total time in the transport.

## Tests

- Service tests for WAV, MP3 cache, MIDI fingerprint reuse/invalidation, and
  source immutability.
- Fake audio-line/player tests for start failure, pause/resume, seek boundaries,
  source switch, EOF, close, and exactly-one-worker behavior.
- View-model and Compose tests for every preview state/error and transport
  controls.
- Manual audition of direct MIDI, WAV input, MP3 input, dry mix, LoFi mix, and
  master on the current OS.

## Acceptance criteria

- A user can tell whether preview is preparing, rendering, playing, or failed,
  and failures identify the failing prerequisite or device.
- Preview never freezes the UI, falsely reports success, alters source media,
  or leaks concurrent playback.

## Out of scope

Recording, low-latency monitoring, MIDI-editor playback, streaming services,
and changing rendered release artifacts during preview.
