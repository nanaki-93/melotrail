# Worker directory for Python-based AI processing services

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

By default it removes exact duplicate notes, notes shorter than 50 ms (using
the MIDI tempo map), and note-on velocities below 8. It repairs
same-channel/pitch overlaps by ending the earlier note at the later start. It
does not quantize by default. `--quantize` accepts `1/4`, `1/8`, `1/16`, or
`1/32`; a strength of `0.0` leaves timing unchanged and `1.0` snaps it to the
grid. The CLI uses strength `0.4` when a grid is provided without an explicit
strength.

Optional `--clean-sustain` removes only repeated adjacent CC64 values on the
same track/channel, preserving valid pedal changes. Optional
`--normalize-velocity` linearly maps retained note-on velocities to 32–112;
it is off by default to retain performance dynamics.

Example request:

```json
{"path":"/project/midi/raw/A.mid","outputPath":"/project/midi/clean/A.mid","quantize":"1/16","strength":0.4,"minNoteMs":50,"minVelocity":8,"normalizeVelocity":false,"cleanSustain":false}
```
