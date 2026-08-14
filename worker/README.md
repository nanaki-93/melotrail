# Worker directory for Python-based AI processing services

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
(Apache-2.0). It is loaded lazily, so normal worker commands and tests neither
download nor require a model. Install it in an isolated Python 3.11 environment:

```bash
python3.11 -m venv .venv-transcription
.venv-transcription/bin/python -m pip install -r worker/requirements.txt -r worker/requirements-transcription.txt
.venv-transcription/bin/python -m worker.main
```

The Task 001 report records an unresolved macOS/Python 3.12 compatibility gate.
Use the command only after confirming Basic Pitch inference in the chosen local
Python 3.11 environment; no model download is attempted automatically.

Example request:

```json
{"path":"/absolute/input.wav","outputPath":"/absolute/output.mid","instrument":"piano"}
```

## Deterministic MIDI cleanup

`POST /midi-clean` uses the pinned `mido==1.3.3` worker dependency to clean an
existing `.mid`/`.midi` file. It keeps the MIDI tracks and safe metadata in
place, writes through a temporary file, reparses it, and atomically publishes
only a valid result. It never changes the raw input.

Cleanup requests use version `2` and a named profile. The default
`conservative` profile removes exact duplicate notes, notes shorter than 50 ms
(using the MIDI tempo map), and note-on velocities below 8. It preserves
expressive timing, pedal controls, orphan note-offs, and same-pitch retriggers.

`transcription-safe` additionally removes orphan note-offs and repeated CC64
values on the same track/channel, ends an earlier same-channel/pitch retrigger
at the later start, and clamps retained velocity outliers to 12–120.
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
