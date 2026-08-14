# Task 031 — JVM Audio-Player Lifecycle

## Goal

Make the local WAV player deterministic, thread-safe, and truthful about audio
device failures without changing preview resolution.

## Dependencies

- Task 028 completed.

## Requirements

- Change the player boundary to report typed prepare/start failures rather than
  swallowing exceptions. Preserve causes for bounded local logs.
- Decode and open the device off the Compose UI dispatcher. Validate file,
  finite samples, format, duration, channels, and device support.
- Guarantee one playback worker/output line. Correct play, pause, resume, seek,
  stop, EOF, source replacement, project switch, and close ownership/races.
- Do not report `PLAYING` until output starts. Retain selected source on a
  recoverable device failure and allow retry.

## Tests

- Fake line/device tests for start failure, pause/resume race, seek bounds,
  source switch, EOF, volume, close, and exactly-one-worker behavior.
- Verify UI dispatcher is not used for decode/device open.

## Acceptance criteria

- Playback cannot falsely report success, leak a line/thread, or run two sources
  concurrently.

## Out of scope

MP3 decoding, MIDI rendering, preview UI, recording, or low-latency monitoring.
