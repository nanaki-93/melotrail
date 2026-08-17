# Task 087 — Focused Mix & Master Page

## Goal

Rebuild Mix & Master as the focused mixer/mastering destination shown in
`../../pictures/App-pages.png` using existing validated mix and build behavior.

## Dependencies

- Task 086 accepted.

## Requirements

- Reproduce the reference split: five logical channel rows on the left,
  supported preset/effect controls on the right, Listen/Mix/Master modes, and
  one Render/Build primary action.
- Connect gain, pan, mute, solo, reset, playback source, master volume, Lo-Fi
  audio texture, MP3 build option, and Build Song only through existing typed
  intents/application services.
- Every visible control must be functional or truthfully disabled with an
  accessible reason. Omit unsupported decorative knobs rather than implying
  that arbitrary DSP parameters are accepted.
- Meters and waveform-like graphics use measured UI state only. When no levels
  exist, render an explicit zero-signal placeholder.
- Preserve one playback session and current transport shortcuts. Listen/Mix/
  Master selects or controls that session; it does not instantiate another
  audio player.
- Render/Build reports success only after validated artifact work completes.
  Preserve source/sample rate/channels, lossless intermediates, atomic
  publication, stale checks, and master WAV authority.
- Do not compose Arrange, Structure, Import, Overview, Video Preview, or Export
  page roots within Mix & Master.

## Verification

- Compose tests cover five channels, settings dispatch, mute/solo, playback
  selection, master volume, disabled unsupported controls, build lifecycle,
  failure recovery, stale mix, and zero-signal state.
- View-model tests verify one shared playback session and real Build Song
  completion/failure semantics through fakes.
- Capture and overlay a deterministic Mix & Master golden against the numbered
  Mix & Master region of `../../pictures/App-pages.png`.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.
- Real renderer/audio-device listening remains a recorded manual check; do not
  claim it from automated fakes.

## Out of scope

New DSP algorithms, arbitrary mastering controls, stem separation, source
normalization, new codecs, or audio-device implementation changes.
