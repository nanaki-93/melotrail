# Task 099 — Preview and Export Reconstruction

## Goal

Rebuild the final-audition and release experience against
`../pictures/UI/08-video-preview.png` and `../pictures/UI/09-export.png`, while
remaining truthful that current support is a deterministic local visual with
shared audio playback and validated audio-only export.

## Dependencies

- Task 098 accepted.

## Requirements

### Video Preview

- Reproduce the supported hierarchy: large local artwork/placeholder stage,
  compact shared audio transport, canonical section/occurrence timeline,
  playback position, selected section, and concise status/context information.
- Use the one existing `PlaybackSession` for play/pause, stop, seek, and volume.
  Do not create a video clock, second audio player, or page-owned playback
  lifecycle.
- Derive the timeline from stable canonical occurrences and known durations.
  Unknown durations remain visibly unavailable; selection and playback position
  are UI state and do not mutate project structure.
- Use an approved bundled local artwork asset when available. Otherwise retain
  one deterministic local placeholder with the reference geometry and truthful
  semantics.
- Omit aspect ratio, resolution, frame rate, background generation, scene
  transitions, camera, fullscreen, and video export unless separately backed by
  validated typed services.

### Export

- Reproduce the supported hierarchy: audio-only mode, format/quality summary,
  current sample rate/channels/bit depth, filename, destination, preview,
  release summary, missing/stale recovery, and one Start Export action.
- WAV remains the authoritative lossless export. Expose MP3 only when the
  existing optional exporter and validated prerequisites are available.
- Derive duration, format, sample rate, channels, bit depth, master freshness,
  and release metadata from current inspected artifacts. Never use mockup file
  size/time estimates as measured state.
- Validate filename, extension, destination, actual input/output formats,
  overwrite prevention, and path safety through typed application/file-dialog
  boundaries. Publication remains atomic and success requires output
  validation.
- Hide video/audio-and-video, FLAC, unsupported resampling/bit depth changes,
  metadata editing, normalization, dithering, fade-out, stem export, and cloud
  destinations unless a separate contract implements them.
- Overview Export and any supported final-action routes must land on this same
  Export destination without resetting playback or project state.

### Shared behavior

- Preview and Export use shared shell components and one playback state. They
  must not duplicate context rails, transport, feedback, or release state.
- Missing/stale artifacts disable unsafe actions with one concise recovery
  route to the page that can regenerate the prerequisite.
- Match each reference's layout, purple states, cards, timeline/form density,
  context rail, and responsive stacking while documenting unsupported regions.

## Verification

- Playback tests prove Overview, Mix, Video Preview, and Export preview controls
  address the same session and lifecycle.
- Preview Compose tests cover no artifact, ready, playing, paused, seeking,
  failed, unknown duration, many occurrences, long title, and missing audio
  dependency states.
- Export application tests cover WAV, optional MP3, stale/missing master,
  invalid/disguised formats, unsafe paths, extension mismatch, overwrite,
  atomic failure, validation failure, and successful publication without
  source/master mutation.
- Export Compose tests cover blocked, ready, exporting, completed, failed,
  optional MP3 unavailable, destination cancel, and recovery routing.
- Assert video-only controls and unsupported audio controls are absent and no
  success is reported before output validation.
- Capture full 1536 × 1024 fixtures and overlay them against both reference
  images; document all capability-driven differences.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Video Preview provides a faithful local visual/audio audition experience
  without implying video generation or export.
- Export provides a faithful, safe audio release flow over current WAV/optional
  MP3 capabilities.
- Both pages share playback, project, feedback, and release truth.

## Out of scope

Video generation/rendering/export, scene generation, remote imagery, new audio
codecs, resampling, mastering changes, metadata authoring, uploads, or cloud
destinations.
