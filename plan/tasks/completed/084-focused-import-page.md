# Task 084 — Focused Import Page

## Goal

Build the standalone Import destination from `../../pictures/App-pages.png` and
give every imported part one clear, state-derived next action.

## Dependencies

- Task 083 accepted.

## Requirements

- Render only the Import page inside the workflow shell: page heading, source
  drop/chooser card, supported-format summary, and imported-files list.
- Browse and drag/drop, when supported by Compose Desktop, must enter the same
  existing file-dialog/import boundary and actual-format validation. A dropped
  path is external input and must be validated identically to a chosen path.
- Accept only the currently supported MIDI, WAV/WAVE, and MP3 paths. State the
  solo-piano audio limitation once; move worker/transcription diagnostics to
  failure recovery or Details.
- Each imported row shows concise filename, type/size, preparation status, and
  an overflow/Details action. Do not expose the internal artifact graph in the
  normal idle list.
- Show exactly one primary action derived from canonical state: Prepare MIDI,
  review repair, inspect/transcribe supported audio, apply a pending feel, or
  continue to Structure.
- Reuse existing typed intents and application services. Composables must not
  inspect files, call the worker, or orchestrate preparation.
- Preserve source immutability, atomic derived publication, cancellation, one
  feedback banner, and one safe retry action.

## Verification

- Compose tests cover empty, validating, MIDI-imported, audio-imported,
  approval-required, ready-for-structure, failed, and long-filename states.
- Tests assert one chooser/drop surface, one imported list, and no Structure,
  Arrange, Mix, Overview, Video Preview, or Export page root.
- Interaction tests prove each primary CTA dispatches the expected typed intent
  and unsupported input never appears imported.
- Capture and overlay a deterministic Import golden against the numbered Import
  region of `../../pictures/App-pages.png`.
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Out of scope

Changing worker endpoints, cleanup DSP, transcription eligibility, project
format, or the Structure editor.
