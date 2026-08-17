# Task 097 — Mix & Master Reconstruction

## Goal

Rebuild Mix & Master to follow `../pictures/UI/06-mix-master.png` using the
existing typed mix, playback, build, mastering, and release-readiness model.

## Dependencies

- Task 096 accepted.

## Requirements

- Reproduce the supported reference hierarchy: Mix header, real channel strips,
  bounded mixer viewport, preview/context rail, supported master/build options,
  playback mode controls, monitoring volume, build status, and primary Build
  Song action.
- Create channel strips only for logical/rendered stems in current state.
  Preserve existing gain, pan, mute, solo, and supported grouping behavior.
  Do not display mock Vocal/Lead/FX/Master channels as real tracks when absent.
- Meter visuals must use measured level data when the read model provides it.
  Otherwise show a clearly zero-signal/unavailable meter and accessible label;
  never animate random or decorative levels as measurements.
- Keep mix changes within existing bounds and typed intents. Debounced/explicit
  persistence, canonical refresh, failure handling, and downstream stale rules
  remain authoritative.
- Use the one shared playback session for Listen/Mix/Master source selection,
  play/pause, stop, seek, and volume. Dry, Lo-Fi, and Master choices are enabled
  only when the validated artifact exists.
- Expose current Lo-Fi texture, optional MP3 build choice, reset, master volume,
  and Build Song behavior where supported. Show exact missing/stale dependency
  reasons and one recovery route.
- Omit or truthfully unavailable advanced EQ, dynamics, sends, automation,
  reference-track import, compression, saturation, reverb, LUFS, true peak,
  group buses, and monitor routing unless backed by real typed models and
  measured data.
- Build/Master success is reported only after the existing lossless pipeline
  validates and publishes the current release artifacts. Preview/navigation
  cannot produce success.
- Match reference channel density, selected purple states, track colors,
  context rail, controls, and responsive behavior. The mixer may scroll
  internally; the page must not scroll horizontally as a whole.

## Verification

- View-model/application tests cover bounded gain/pan, mute/solo, reset,
  persistence, stale artifact handling, playback source eligibility, Lo-Fi,
  optional MP3, build success/failure, and canonical refresh.
- Compose tests cover no stems, one/five/many channels, zero-signal, measured
  levels, dry/Lo-Fi/master availability, mutating, stale, missing renderer or
  worker, build failure, and successful master states.
- Keyboard tests cover channel selection, gain/pan sliders, mute/solo, playback
  modes, master volume, options, reset, and Build Song.
- Assert every displayed meter is labeled measured or unavailable and assert
  unsupported DSP/reference/monitor controls are absent.
- Verify bounded mixer scrolling at medium/narrow widths and full access at
  100%, 125%, and 150% scale.
- Capture and overlay a full 1536 × 1024 fixture against
  `../pictures/UI/06-mix-master.png`; document intentional capability
  differences.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- The page follows the supplied mixer hierarchy with real channels and
  truthful signal/readiness states.
- Existing mix, playback, and build behavior remains functional and safe.
- No unsupported DSP or measurement is represented as active.

## Out of scope

New DSP parameters/effects, reference-track analysis, live metering
infrastructure, new mastering algorithms, audio-device routing, or Library
implementation.
