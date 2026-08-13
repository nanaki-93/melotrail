# Task 021 — Final MP3 Export and End-to-End Release Check

## Goal

Export an optional MP3 only from the validated final WAV master and prove the complete MIDI-first local workflow is repeatable.

## Dependencies

- Task 020 produces `output/master.wav`.
- Existing worker `mp3_export` behavior is reused.

## CLI and artifact contract

Provide a separate export stage equivalent to:

```bash
music-cli export-mp3 \
  --input ./projects/song-001/output/master.wav \
  --output ./projects/song-001/output/song.mp3 \
  --bitrate 320
```

The full `build --project ...` command may invoke this stage after mastering when MP3 export is enabled, but mastering itself must never write MP3.

Canonical release artifacts:

```text
output/master.wav
output/song.mp3        # optional
output/release.json
```

Retain read compatibility for the current `youtube.mp3` artifact if needed, but new MIDI-first builds use the neutral `song.mp3` name because publishing integration is out of scope.

## Functional requirements

- Input must be an existing validated RIFF/WAVE master; reject MP3 or arbitrary intermediate input in project-build mode.
- Output must end in `.mp3` and cannot equal/overwrite any source, MIDI, stem, mix, master, registry, or instrument asset.
- Allow only documented bitrates supported by the encoder.
- Encode to a temporary file and atomically replace the output after validation.
- Validate non-empty output and an MP3 frame/ID3 signature; explicitly reject a RIFF/WAVE container with an `.mp3` name.
- Report encoder unavailability as an optional-stage result while retaining the authoritative master WAV.
- Do not install dependencies automatically or fail the completed WAV build solely because optional `lameenc` is absent, unless the user explicitly requested MP3-only success.
- Update `release.json` atomically with master/MP3 names, fingerprints, formats, bitrate, mastering metadata reference, and instrument license/attribution report.

## End-to-end deterministic verification

Run the full project path with AI disabled:

```text
import MIDI/WAV/MP3
 -> transcribe when required
 -> clean MIDI
 -> analyze
 -> structure/instances
 -> deterministic song and arrangement plans
 -> generate instrument MIDI
 -> render stems
 -> dry mix
 -> optional LoFi
 -> master.wav
 -> optional song.mp3
```

Use fakes/fixtures for the automated end-to-end test and real local models/instruments for the documented manual demo.

## Tests

- Successful WAV-to-MP3 worker request and atomic output.
- Bitrate validation.
- Missing encoder optional behavior and explicit-required behavior.
- Invalid input/output extensions and overwrite attempts.
- Empty, malformed, or RIFF-disguised-as-MP3 output rejection.
- Release metadata with and without MP3.
- Full deterministic build preserves all required intermediate artifacts and source hashes.
- Build resumption and exact stage failure reporting.
- Qwen remains optional and fixture-tested; no live Qwen/model required in the standard suite.

Manual release smoke test:

- Run a direct-MIDI project and a transcribed-piano project.
- Inspect/audition `master.wav` and `song.mp3`.
- Confirm MP3 duration/channels and musical start/end match the WAV master.
- Run `music-cli licenses` and include required attribution information in the release report.

## Acceptance criteria

- MP3 is created only after and from `master.wav`.
- File extension and actual container agree.
- `master.wav` remains the authoritative lossless release artifact.
- The full deterministic workflow is documented and repeatable.
- Optional Qwen planning remains structured, validated, and non-executable.
- No publishing, cloud, database, or SaaS functionality is added.

## Out of scope

- Uploading or publishing to YouTube, Spotify, or any other service.
- Metadata-store databases, accounts, cloud release automation, or additional lossy formats.
- Replacing the selected MP3 encoder or changing mastering DSP without a separate task.

## Completion report

Report release artifacts and fingerprints, changed files, tests/build/manual commands, encoder/version, WAV/MP3 metadata and listening results, license/attribution status, assumptions, and remaining end-to-end issues.
