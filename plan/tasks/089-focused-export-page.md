# Task 089 — Focused Export Page

## Goal

Build the supported Export destination shown in
`../pictures/App-pages.png`, backed only by validated release artifacts and
safe typed export boundaries.

## Dependencies

- Task 088 accepted.

## Requirements

- Route the Overview Export CTA to one focused Export page with format,
  quality, sample rate, filename, destination, summary, and one Export Song
  action in the reference layout.
- Populate only genuinely supported choices. WAV remains the authoritative
  lossless release. MP3 appears only when the existing optional exporter and
  prerequisites are available; do not advertise video or unsupported codecs.
- Derive duration, format, quality, sample rate, channel/track summary, and
  readiness from current validated master/release metadata. Do not infer
  success from stale files or user-entered labels.
- Validate filename, extension, destination, and actual input/output formats.
  Prevent source/master overwrite and unsafe path escape. Publish through a
  typed application/filesystem boundary atomically and validate output before
  reporting completion.
- When current services cannot support a reference field, render the actual
  fixed value or a truthfully disabled control; do not add arbitrary worker or
  DSP parameters.
- Missing/stale master or optional dependency disables Export Song and exposes
  one concise recovery action. Preserve one global feedback surface.

## Verification

- Application tests use a fake filesystem/exporter and cover WAV, optional
  MP3, invalid extensions, disguised formats, escaped paths, overwrite
  rejection, atomic failure, stale master, and validated success.
- Compose tests cover ready, blocked, exporting, complete, failed, and optional
  MP3-unavailable states, plus correct Overview-to-Export routing.
- Assert no source/master mutation and no success before output validation.
- Capture and overlay a deterministic Export golden against the numbered Export
  region of `../pictures/App-pages.png`.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Out of scope

New codecs, video export, cloud destinations, automatic uploads, mastering
changes, or replacing the project-local authoritative master.
