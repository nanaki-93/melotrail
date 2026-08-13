# Task 004 — Unified MIDI-First Input Adapter

## Goal

Make direct MIDI and WAV/MP3 input converge on the same clean-MIDI project representation while preserving every original source file.

## Dependencies

- Task 002 provides transcription.
- Task 003 provides deterministic cleanup.

## Existing behavior to preserve

- Project creation and `part add` already validate IDs, copy inputs, and prevent duplicate parts.
- Version-1 projects store source audio in `parts/` and must remain readable.
- Project paths are relative and validated against traversal and escaping symlinks.

Do not rewrite the web application's separate project model.

## Project format v2

Introduce a versioned MIDI-first arranger project format conceptually equivalent to:

```json
{
  "version": 2,
  "name": "song-001",
  "renderFormat": {
    "sampleRate": 44100,
    "channels": 2,
    "bitDepth": 24
  },
  "parts": [
    {
      "id": "A",
      "role": "verse",
      "sourceFile": "source/A.wav",
      "midi": {
        "raw": "midi/raw/A.mid",
        "clean": "midi/clean/A.mid"
      },
      "analysis": null
    }
  ],
  "structure": ["A", "A"]
}
```

For direct MIDI, `midi.raw` may be absent because the untouched original is already in `source/`; `midi.clean` is always required before downstream processing.

Use dedicated v1/v2 serialization DTOs and a project store/migrator rather than scattering version checks through CLI code.

## Compatibility policy

- Continue reading v1 projects.
- Map the v1 `file` field to an in-memory source reference without moving or deleting the referenced file.
- Upgrade `project.json` to v2 only after all required MIDI artifacts have been created and validated.
- Use an atomic project-file replacement and retain the previous metadata until the operation succeeds.
- Existing source files under `parts/` may remain there; migration must not reorganize user files merely to match the preferred layout.
- A legacy audio part that has not been transcribed must fail MIDI-only commands with an actionable preparation message.

## CLI behavior

Support:

```bash
music-cli project create ./projects/song-001 [--sample-rate 44100] [--channels 2]

music-cli part add ./projects/song-001 \
  --id A --file verse.mid --role verse

music-cli part add ./projects/song-001 \
  --id B --file chorus.wav --role chorus --transcribe
```

MIDI input flow:

```text
copy original -> source/<id>.<ext>
normalize/cleanup -> midi/clean/<id>.mid
validate artifacts -> update project.json
```

Audio input flow:

```text
copy original -> source/<id>.<ext>
transcribe -> midi/raw/<id>.mid
cleanup -> midi/clean/<id>.mid
validate artifacts -> update project.json
```

The standalone Task 002 and Task 003 commands remain available for debugging.

## Boundary validation

- Part IDs use the current restricted ID pattern.
- Supported input is `.mid`, `.midi`, `.wav`, `.wave`, or `.mp3`.
- `--transcribe` is required for audio and rejected for MIDI.
- Reject duplicate IDs, destinations, unsupported render formats, source/output identity, path traversal, and symlink escape.
- Render format is explicit in v2. New projects default to PCM-24/44.1 kHz/stereo but store those values rather than relying on processor defaults.
- On a failed transcribe/cleanup step, do not register a partial part. Preserve the copied source and report any unregistered artifacts for diagnosis rather than silently deleting them.

## Tests

- New v2 project creation and JSON round trip.
- Direct MIDI import and normalized clean artifact.
- WAV and MP3 import through fake transcription/cleanup clients.
- V1 load and non-destructive migration.
- Failure before metadata update.
- Duplicate IDs, bad flags, invalid formats, traversal, symlink escape, and source overwrite prevention.
- Explicit sample-rate/channel validation.

Manual smoke test:

- Create a project with one direct MIDI part and one transcribed audio part.
- Confirm both expose a valid `midi/clean/*.mid` path.
- Verify original files byte-for-byte against their source copies.

## Acceptance criteria

- All new downstream MIDI-first code can depend on a validated clean-MIDI reference.
- V1 projects remain readable and migrate without moving/deleting source material.
- New projects contain the planned `source/`, `midi/raw/`, `midi/clean/`, and `midi/generated/` structure as needed.
- Partial failures cannot leave `project.json` referencing missing artifacts.
- Existing project, CLI, and path-security tests still pass.

## Out of scope

- MIDI musical analysis.
- Instrument rendering or arrangement.
- Web UI migration.

## Completion report

Report schema/migration behavior, changed files, commands, automated and manual results, preserved source checks, assumptions, and any unsupported legacy state.
