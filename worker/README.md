# Worker directory for Python-based AI processing services

## Baseline HTTP capability surface

The worker is a local HTTP process, not a browser or cloud service. Its
supported command-specific endpoints are `GET /health` and `POST /analyze`,
`/apply_dsp`, `/repair`, `/master`, `/mp3_export`, `/mp3_convert`,
`/transcribe`, `/midi-clean`, `/inspect-input`, and `/cleanup`. Every POST
response uses the version-1 job envelope; Kotlin validates the resulting
artifact before a later stage treats it as ready.

`/transcribe` is intentionally limited to the eligible solo-piano route. A
successful endpoint response is not a claim of reliable editable MIDI for
vocals, full mixes, or arbitrary polyphonic audio.

## Read-only input inspection

`POST /inspect-input` accepts one existing `.mid`, `.midi`, `.wav`, `.wave`,
or `.mp3` file and returns only bounded validation and measurement data. It
checks both extension and actual container, rejects empty/corrupt/non-finite
input, and never changes the source. MP3 is decoded to a unique temporary
PCM-24 WAV which is removed before the response. Audio preserves its decoded
sample rate and channel count and is measured in frames: silence is a frame
peak at or below `1e-4`, clipping at or above `0.999`, and the 50/60 Hz hum and
first-difference noise indicators use fixed `0.05/0.15/0.30` evidence
thresholds. The endpoint does not publish a report or prepared artifact.

Example request:

```json
{"path":"/project/source/intro.wav"}
```

## Deterministic audio cleanup

`POST /cleanup` accepts a RIFF/WAVE input and a different `.wav` output path.
It only accepts explicitly requested schema operations: `dc_removal`,
`clip_repair` (threshold `0.95..1.0`), `declick` (threshold `0.5..0.99`),
`hum_removal` (50 or 60 Hz), and `noise_reduction` (strength `0.05..0.5`).
It rejects unknown, duplicate, or unbounded settings. There is no normalize,
silence removal, pitch, tempo, or source overwrite operation.

Requested operations are conservatively skipped unless evidence is present:
absolute DC offset at least `0.005`, clipped frames at least `0.999`, an
isolated frame jump at least `0.25`, hum confidence at least `0.15`, or noise
confidence at least `0.15`. The response records before/after metrics,
applied/skipped operations, warnings, and tool versions. Output is atomically
published PCM-24 WAV with the original frame count, sample rate, and channels.

```json
{"path":"/project/source/intro.wav","outputPath":"/project/prepared/intro/clean.wav","operations":[{"type":"dc_removal"},{"type":"hum_removal","params":{"frequencyHz":50}}]}
```

## Optional solo-piano transcription

`POST /transcribe` accepts a local `.wav`, `.wave`, or `.mp3` source and writes
only a validated `.mid`/`.midi` result. The initial instrument allow-list is
`piano`. MP3 inputs are decoded to a unique temporary PCM-24 WAV; the source is
never changed. The requested MIDI is published only after validation succeeds.

The selected engine is [Spotify Basic Pitch 0.4.0](https://github.com/spotify/basic-pitch)
(Apache-2.0). The project runs one unified worker environment, including the
optional transcription runtime. It requires Python 3.11. With pyenv, select it
once with `pyenv local 3.11`, then start the worker with:

```bash
make worker
```

`make worker` creates `.venv-worker` and installs both requirements files.
Use the command only after confirming Basic Pitch inference in the selected
Python 3.11 environment; no model download is attempted automatically.

Example request:

```json
{"path":"/absolute/input.wav","outputPath":"/absolute/output.mid","instrument":"piano"}
```

### Reproducible transcription benchmark

The selected default is measured with the local benchmark rather than assumed.
It writes five synthetic solo-piano fixtures (simple melody, chord-heavy,
sustain-heavy, fast arpeggios, and expressive low-velocity playing), their
target MIDI, raw/clean outputs, and a JSON report with note correctness, false
and missed notes, duplicates, short notes, timing/chord capture, and cleanup
burden. Generate fixtures without a model or run every installed provider:

```bash
.venv/bin/python -m worker.tools.benchmark_transcription --fixtures-only
.venv-worker/bin/python -m worker.tools.benchmark_transcription --engine basic-pitch
```

Providers implement the `TranscriptionEngine` boundary in
`worker.commands.transcribe`; add a provider there and pass another `--engine`
value to compare it against the current Basic Pitch evidence. The benchmark's
`recommendedEngine` is the best measured clean-MIDI F1 result for that run.

## Deterministic MIDI cleanup

`POST /midi-clean` uses the pinned `mido==1.3.3` worker dependency to clean an
existing `.mid`/`.midi` file. It keeps the MIDI tracks and safe metadata in
place, writes through a temporary file, reparses it, and atomically publishes
only a valid result. It never changes the raw input.

Clean MIDI requests use version `2` and a named profile. The default documented
standard is `transcription-safe`; it removes exact duplicate notes, notes
shorter than 50 ms, quiet noise, orphan note-offs, and redundant sustain values,
ends same-pitch retriggers at their next start, and bounds retained velocities.
It preserves valid tempo and time-signature metadata.

`transcription-safe` additionally removes orphan note-offs and repeated CC64
values on the same track/channel, ends an earlier same-channel/pitch retrigger
at the later start, merges near-identical overlapping captures, and clamps
retained velocity outliers to 12–120. The mandatory audio-transcription profile
treats notes shorter than 50 ms or quieter than velocity 15 as suspicious, but
retains an identifiable low-velocity grace note leading into another attack or
a quiet chord member. These bounded thresholds are explicitly sent by Kotlin;
they are not unconditional note deletion rules.
`tighten-timing` includes those repairs and is the only profile that permits
quantization. It requires a grid of `1/4`, `1/8`, `1/16`, or `1/32` and a
strength greater than `0.0` and at most `1.0`. It never invents notes.

The legacy `normalizeVelocity` request field remains available only with a
non-conservative profile and maps retained velocities linearly to 32–112.
Likewise, legacy `cleanSustain` is accepted only with a non-conservative
profile; profile-based sustain cleanup is already enabled there. Ambiguous
profile/quantization combinations are rejected. Responses include exact input
and output note/event counts and an `appliedChanges` object.

Example request:

```json
{"version":2,"profile":"tighten-timing","path":"/project/midi/raw/A.mid","outputPath":"/project/midi/clean/A.mid","quantize":"1/16","strength":0.4,"minNoteMs":50,"minVelocity":8,"normalizeVelocity":false,"cleanSustain":false}
```

Worker responses and outputs are stage evidence, not workflow completion flags.
The Kotlin project boundary validates published files and their fingerprints
before later analysis or rendering is ready; a worker failure leaves prior
artifacts available for inspection.

## Mastering measurement

`POST /master` measures its output with ITU-R BS.1770-4 / EBU R128 K-weighted,
gated integrated loudness and a four-times oversampled true peak. Its response
also records loudness range, crest, and limiter gain reduction. The built-in
Lo-fi profile uses -14 LUFS as a nominal delivery reference (±1 LU), a maximum
-1.0 dBTP true peak, and independent dynamics checks. A quieter master may
remain acceptable when preserving dynamics requires it; a heavily limited
master is rejected even when its loudness number is near the reference.

`GET /health` also advertises the pinned `midiCleanup.requestVersion` and its
allow-listed profiles. Kotlin checks that capability before sending a Clean MIDI
request; it does not negotiate arbitrary worker options. Normalization is a
separate deterministic Kotlin stage after worker cleanup, so this worker does
not perform swing, creative quantization, pitch correction, or humanization.

## Source timing evidence

`POST /analyze` uses request contract version `2` (Kotlin sends `"version":2`)
for read-only source timing evidence. Its bounded response includes beat and
onset frame/time points, confidence-scored tempo candidates, leading activity,
and an explicit downbeat state. Audio-only phase is returned as
`REVIEW_REQUIRED`; silent, malformed, or insufficient inputs remain
`UNKNOWN`/failed evidence and never become invented beats or a confirmed
downbeat. `GET /health` advertises the supported revision at
`analysis.versions` so Kotlin fails with a recovery action when timing v2 is
unavailable.

Kotlin validates and stores the project-confined, source-hash-bound timing
report, then derives its own confidence-scored source-groove template.
Low-support bins remain neutral and make the template `REVIEW_REQUIRED`; a
later Kotlin decision must review it or use the approved grid. The worker never writes a
report, decides project bar placement, applies a warp, or replaces source MIDI.
