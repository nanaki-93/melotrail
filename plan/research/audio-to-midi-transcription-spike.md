# Audio-to-MIDI Transcription Spike

## Decision

Select **Spotify Basic Pitch 0.4.0** for Task 002, subject to the runtime gate below. It is the better first integration target for this local project because it is lightweight, offline after installation, Apache-2.0 licensed, produces Standard MIDI Files directly, and is explicitly intended for one polyphonic instrument at a time. The initial product scope remains solo piano only.

The Task 002 worker boundary must keep the engine behind a small Python interface so a future piano-specialized engine can replace it without changing Kotlin, CLI, project, or MIDI-cleanup code.

## Evaluated candidates

| Candidate | Result | Notes |
|---|---|---|
| Basic Pitch 0.4.0 | **Selected, runtime gate pending** | Apache-2.0; polyphonic/instrument-agnostic; official documentation says it works best on a single instrument. It outputs MIDI and pitch bends. Input is downmixed to mono and resampled to 22,050 Hz by the model. |
| `piano-transcription-inference` 0.0.5 / ByteDance piano transcription | Not selected | Piano-specialized onset/offset/pedal model, therefore a valuable later quality benchmark. Its released inference package targets an older PyTorch/librosa stack, has a larger checkpoint/runtime burden, and its package metadata does not state a license. Do not integrate it until its current macOS/Python/license story is verified. |

Primary sources: [Basic Pitch repository](https://github.com/spotify/basic-pitch), [Basic Pitch packaging/runtime metadata](https://github.com/spotify/basic-pitch/blob/main/pyproject.toml), [piano-transcription-inference repository](https://github.com/qiuqiangkong/piano_transcription_inference), and [ByteDance high-resolution piano transcription paper](https://arxiv.org/abs/2010.01815).

## Scoring summary

| Criterion | Basic Pitch | Piano-specialized candidate |
|---|---|---|
| Recognizable pitch/onsets | Pending real fixture run | Pending real fixture run |
| Polyphonic piano | Yes; general-purpose model | Yes; piano-specific model |
| Repeated notes | Requires fixture evaluation | Designed for piano onset/offset evaluation; current runtime not verified |
| Sustain pedal | MIDI/pitch-bend output; pedal behavior requires fixture evaluation | Paper targets pedal transcription; package/runtime behavior not verified |
| Tempo | MIDI tempo defaults must be treated as provisional; Task 005 derives actual analysis | Same |
| Standard MIDI output | Yes | Yes |
| Determinism | Expected with fixed model/settings; two-run event comparison is mandatory | Expected but not verified |
| Local CPU | CoreML runtime on Apple Silicon; no cloud call | PyTorch CPU possible but heavier |
| Dependency fit | Isolate from existing Python 3.14 worker | Isolate from existing Python 3.14 worker |
| Commercial-use due diligence | Apache-2.0 code; verify the packaged model terms during Task 002 dependency lock | Package license metadata is unspecified; unsuitable as first choice |

## Runtime and setup

The repository's system Python is 3.14.6. Existing worker tests currently fail before this task when `librosa`/`numba` attempts to create a cache. Do not add either candidate to `worker/requirements.txt` yet.

Homebrew Python 3.12.14 was installed during this spike and an untracked `.venv-transcription-spike/` was created. It isolates experimentation from the worker.

Important compatibility finding: Basic Pitch 0.4.0's normal macOS dependency marker requests `tensorflow-macos >=2.4.1,<2.15.1` for Python newer than 3.11. No compatible Python 3.12 wheel is currently available. The CoreML model is packaged with Basic Pitch, but the standard `pip install basic-pitch` path therefore does not provide a reproducible Python 3.12 macOS environment.

**Task 001 runtime gate:** do not call Task 001 complete until one of these is demonstrated with the exact command/output recorded:

1. a Python 3.11 Apple-Silicon Basic Pitch CoreML/TensorFlow environment; or
2. a fully pinned Python 3.12 CoreML-only install that imports Basic Pitch, loads the bundled model, and passes the two-run fixture test.

The spike script intentionally names this constraint instead of pretending that a package-only install is evidence of successful inference.

### Intended isolated setup

```bash
brew install python@3.12
/opt/homebrew/bin/python3.12 -m venv .venv-transcription-spike
.venv-transcription-spike/bin/python -m pip install --upgrade pip

# This currently fails on macOS/Python 3.12 because Basic Pitch requests an
# unavailable tensorflow-macos version. Do not suppress the failure in Task 002.
.venv-transcription-spike/bin/python -m pip install 'basic-pitch==0.4.0' 'mido>=1.3,<2'
```

For an actual Basic Pitch execution, use only the selected, documented runtime after the gate passes. The intended command is:

```bash
.venv-transcription-spike/bin/python tools/transcribe_piano_spike.py \
  tests/audio/audio/piano_melody.wav /private/tmp/piano-melody.mid

.venv-transcription-spike/bin/python tools/transcribe_piano_spike.py \
  tests/audio/audio/piano_melody.wav /private/tmp/piano-melody-repeat.mid
```

Validate both outputs and compare their parsed note event lists:

```bash
.venv-transcription-spike/bin/python tools/transcribe_piano_spike.py \
  --validate-midi /private/tmp/piano-melody.mid
```

The script accepts only WAV for this task. MP3-to-temporary-WAV conversion belongs to Task 002.

## Fixture and expected musical result

Use `tests/audio/audio/piano_melody.wav`. It is a repository-generated 44.1 kHz, 16-bit stereo test fixture defined by `tests/audio/generate_test_files.py`; it contains four consecutive sustained sine-wave notes: C4, E4, G4, and C5, each 2.5 seconds long. It is legally usable for this spike and no copyrighted source recording is committed.

Manual pass criteria:

- piano roll/event listing contains those four pitches or musically equivalent nearby detections;
- events occur near 0, 2.5, 5.0, and 7.5 seconds;
- note duration/timing is recognizable;
- both runs produce equivalent parsed events;
- a local piano renderer audition sounds like the source melody.

The current environment could not finish downloading all scientific dependencies needed to run the real model within this task session. There is deliberately no committed generated MIDI result and no claim that this manual pass has occurred.

## Measured environment state

| Item | Observed result |
|---|---|
| Kotlin baseline | `./gradlew test` passed |
| Worker Python baseline | `python3 -m unittest discover -s worker/tests` failed in existing MP3 test due to Python 3.14 `librosa`/`numba` cache initialization |
| Basic Pitch package | 0.4.0 installed in isolated venv without normal dependencies |
| Piano specialized package | `piano-transcription-inference` 0.0.5 installed for metadata inspection only |
| Model/runtime execution | Blocked pending a compatible fully installed runtime |
| Fixture duration/format | 10 seconds, 44.1 kHz, 16-bit stereo WAV |
| CPU/RAM/elapsed inference | Not measured; record after runtime gate passes |
| Bundled Basic Pitch model assets | CoreML `.mlpackage` is approximately 264 KB (120 KB model + 143 KB weights); its TensorFlow/ONNX/TFLite alternatives are bundled too. The incomplete isolated environment reached 258 MB because CoreML tooling and scientific dependencies are much larger than the model itself. |

## Task 002 handoff

Task 002 may start only after the runtime gate is closed. It should:

- pin the successful engine/runtime versions in a dedicated optional transcription dependency definition;
- keep model loading and inference behind a testable worker-local interface;
- use the Task 001 input/output validation and parser check;
- create a temporary lossless WAV for MP3 input;
- write and validate a temporary MIDI before atomic output replacement;
- report note count, duration, engine, and engine version;
- preserve the source and never accept model-provided paths or commands.

## Files created by this spike

- `tools/transcribe_piano_spike.py` — standalone Basic Pitch runner plus dependency-free MIDI validation.
- `tools/tests/test_transcribe_piano_spike.py` — parser/validation regression tests.
- `plan/research/audio-to-midi-transcription-spike.md` — this report.

No worker endpoint, Kotlin command, project metadata, source audio, DSP, or instrument renderer was changed.
