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
