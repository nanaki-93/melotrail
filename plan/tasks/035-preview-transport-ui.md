# Task 035 — Preview and Transport UI

## Goal

Expose the preview states and transport controls clearly in the existing Compose
workspace before the broader visual redesign.

## Dependencies

- Task 034 accepted.

## Requirements

- Add selected part/artifact label, progress/status text, play/pause, stop, seek,
  volume, elapsed/total time, and retry where applicable.
- Show preparing versus starting versus playing; never show a success state while
  output is unavailable. Explain missing analysis, library, renderer, stale
  artifact, unsupported source, and device errors beside the affected control.
- Keep controls keyboard reachable, labelled, and disabled only by derived
  capability/state. Preserve existing transport shortcuts.
- Add no new service/filesystem logic to composables.

## Tests

- Compose semantic tests for all states, disabled reasons, retry, keyboard
  transport, seek, and accessible labels.
- Manual WAV, MP3, MIDI, dry, LoFi, and master audition when dependencies exist.

## Acceptance criteria

- A user can see whether preview is preparing, ready, playing, or failed and can
  recover without guessing.

## Out of scope

Theme/layout overhaul, waveform editing, or new audio processing.
