# Task 002 — Transcription Worker Endpoint and CLI

## Goal

Expose the engine selected in Task 001 through the standalone Python worker and a dedicated Kotlin CLI command.

## Dependencies

- Task 001 is complete.
- Its selected engine, supported Python version, setup, and licensing are documented.

## Existing code to reuse

- `../../../worker/main.py` and `../../../worker/registry.py` for one-endpoint-per-operation registration.
- `../../../worker/commands/mp3_convert.py` for compressed-audio decoding behavior.
- `WorkerCommand` and `WorkerClient` for Kotlin-to-worker requests.
- `ArrangementProjectCommands` for arranger-oriented CLI dispatch.

Do not introduce a second worker framework or make Kotlin start/stop the Python process.

## Public contracts

Add:

```text
POST /transcribe
```

Request:

```json
{
  "path": "/absolute/input.wav",
  "outputPath": "/absolute/output.mid",
  "instrument": "piano"
}
```

Successful worker output:

```json
{
  "output": "/absolute/output.mid",
  "notes": 184,
  "duration": 23.6,
  "engine": "selected-engine",
  "engineVersion": "x.y.z"
}
```

Add a typed Kotlin `TranscribeCommand` and map it only to `/transcribe`.

Add CLI behavior equivalent to:

```bash
music-cli transcribe \
  --input ./recordings/verse.wav \
  --output ./projects/song-001/midi/raw/A.mid \
  --instrument piano
```

## Functional requirements

- Accept `.wav`, `.wave`, and `.mp3` input, case-insensitively.
- Accept only `.mid` or `.midi` output.
- The initial instrument allow-list contains only `piano`.
- Reject missing, non-file, unsupported, or same input/output paths before inference.
- Create the output parent only after all validation succeeds.
- For MP3, decode to a temporary lossless WAV using existing conversion behavior, then transcribe that WAV.
- Use a uniquely created temporary directory and clean it in success and failure paths.
- Write transcription to a temporary MIDI file, validate it, then atomically replace the requested destination.
- Never overwrite the input or use a model-provided path.
- The endpoint performs transcription only; it must not clean MIDI, analyze harmony, arrange, or render audio.
- Errors must identify validation, decode, model, or output-validation failure without exposing a stack trace in the CLI message.

## Implementation sequence

1. Wrap the selected engine behind a small Python transcription interface so tests can inject a fake.
2. Implement the command handler and register it in worker startup.
3. Add typed Kotlin request construction and endpoint mapping.
4. Add CLI parsing, help, validation, and result formatting.
5. Document the worker dependency/model setup and command example.

## Tests

Python unit tests:

- valid WAV request using a fake engine;
- MP3 request invokes lossless decode before the engine;
- missing input/output/instrument;
- unsupported extension and instrument;
- same input/output target;
- engine failure does not leave a final or temporary partial MIDI;
- successful response reports parsed note count and duration.

Kotlin tests:

- `TranscribeCommand` maps to `/transcribe` with exact JSON fields;
- CLI parsing accepts valid arguments and rejects duplicates/missing values;
- worker error becomes a stage-specific CLI failure;
- no live model is required by the normal test suite.

Manual smoke test:

- Start the worker.
- Transcribe the Task 001 solo-piano fixture from WAV and, separately, MP3.
- Parse and audition both MIDI outputs.

## Acceptance criteria

- `/health` lists `transcribe`.
- The standalone command creates a validated MIDI file and reports useful metadata.
- WAV and MP3 paths both work without modifying source files.
- Automated tests do not download a model or require LM Studio.
- Existing worker commands and Kotlin tests still pass.

## Out of scope

- Convenience `part add --transcribe`.
- MIDI cleanup or analysis.
- Multi-instrument transcription.
- Arrangement or audio rendering.

## Completion report

Report changed files, endpoint and CLI examples, tests/build commands, real-fixture results, assumptions, model/runtime requirements, and remaining transcription limitations.
