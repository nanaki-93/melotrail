"""Solo-piano audio-to-MIDI transcription using the optional Basic Pitch runtime."""

from __future__ import annotations

import importlib.metadata
import os
import tempfile
from pathlib import Path
from typing import Protocol

import soundfile as sf

from worker.tools.transcribe_piano_spike import MidiValidationError, parse_midi_notes
from worker.commands.mp3_convert import mp3_convert_command
from worker.errors import (
    TranscriptionDecodeError,
    TranscriptionModelError,
    TranscriptionOutputValidationError,
    TranscriptionValidationError,
)
from worker.registry import register_command

INPUT_SUFFIXES = {".wav", ".wave", ".mp3"}
OUTPUT_SUFFIXES = {".mid", ".midi"}
SUPPORTED_INSTRUMENTS = {"piano"}
PIANO_MIN_FREQUENCY_HZ = 27.5
PIANO_MAX_FREQUENCY_HZ = 4186.01


class TranscriptionEngine(Protocol):
    """Small inference boundary so command tests never need a model runtime."""

    name: str
    version: str

    def transcribe(self, input_path: Path, output_path: Path, instrument: str) -> None:
        """Write one Standard MIDI file to ``output_path``."""


class BasicPitchEngine:
    name = "basic-pitch"

    def __init__(self) -> None:
        try:
            from basic_pitch import ICASSP_2022_MODEL_PATH
            from basic_pitch.inference import Model, predict
        except ImportError as exc:
            raise TranscriptionModelError(
                "Basic Pitch is unavailable. Use Python 3.11 and install "
                "worker/requirements-transcription.txt in an isolated environment."
            ) from exc

        self._model_path = ICASSP_2022_MODEL_PATH
        self._model_class = Model
        self._predict = predict
        try:
            self.version = importlib.metadata.version("basic-pitch")
        except importlib.metadata.PackageNotFoundError:
            self.version = "unknown"

    def transcribe(self, input_path: Path, output_path: Path, instrument: str) -> None:
        if instrument != "piano":
            raise TranscriptionValidationError(f"Unsupported instrument: {instrument}")
        try:
            model = self._model_class(self._model_path)
            _, midi_data, _ = self._predict(
                str(input_path),
                model,
                minimum_frequency=PIANO_MIN_FREQUENCY_HZ,
                maximum_frequency=PIANO_MAX_FREQUENCY_HZ,
            )
            midi_data.write(str(output_path))
        except TranscriptionValidationError:
            raise
        except Exception as exc:
            raise TranscriptionModelError("Basic Pitch transcription failed") from exc


def _validate_request(request: dict) -> tuple[Path, Path, str]:
    raw_input = request.get("path")
    raw_output = request.get("outputPath")
    instrument = request.get("instrument")
    if not isinstance(raw_input, str) or not raw_input.strip():
        raise TranscriptionValidationError("Missing path")
    if not isinstance(raw_output, str) or not raw_output.strip():
        raise TranscriptionValidationError("Missing outputPath")
    if not isinstance(instrument, str) or not instrument.strip():
        raise TranscriptionValidationError("Missing instrument")

    input_path = Path(raw_input).expanduser().resolve(strict=False)
    output_path = Path(raw_output).expanduser().resolve(strict=False)
    if not input_path.is_file():
        raise TranscriptionValidationError(f"Input file not found: {input_path}")
    if input_path == output_path or (output_path.exists() and input_path.samefile(output_path)):
        raise TranscriptionValidationError("Input and output paths must differ")
    if input_path.suffix.lower() not in INPUT_SUFFIXES:
        raise TranscriptionValidationError("Input must use a .wav, .wave, or .mp3 extension")
    if output_path.suffix.lower() not in OUTPUT_SUFFIXES:
        raise TranscriptionValidationError("Output must use a .mid or .midi extension")
    if instrument.lower() not in SUPPORTED_INSTRUMENTS:
        raise TranscriptionValidationError("Unsupported instrument: %s. Supported instruments: piano" % instrument)
    if output_path.exists() and output_path.is_dir():
        raise TranscriptionValidationError(f"Output path is a directory: {output_path}")
    return input_path, output_path, instrument.lower()


def _decode_mp3(input_path: Path, temporary_directory: Path) -> Path:
    decoded_path = temporary_directory / "decoded.wav"
    try:
        mp3_convert_command({"path": str(input_path), "outputPath": str(decoded_path)})
    except Exception as exc:
        raise TranscriptionDecodeError("Could not decode MP3 input to lossless WAV") from exc
    if not decoded_path.is_file() or decoded_path.stat().st_size == 0:
        raise TranscriptionDecodeError("MP3 decode did not produce a WAV file")
    return decoded_path


def _audio_duration(path: Path) -> float:
    try:
        info = sf.info(path)
        if info.samplerate <= 0:
            raise RuntimeError("invalid sample rate")
        return float(info.frames / info.samplerate)
    except Exception as exc:
        raise TranscriptionDecodeError("Could not read decoded audio duration") from exc


def transcribe_command(request: dict, engine: TranscriptionEngine | None = None) -> dict:
    """Validate, transcribe, validate MIDI, then atomically publish the output."""
    input_path, output_path, instrument = _validate_request(request)
    engine = engine or BasicPitchEngine()

    with tempfile.TemporaryDirectory(prefix="ai-music-transcribe-") as temporary_directory_name:
        temporary_directory = Path(temporary_directory_name)
        transcription_input = (
            _decode_mp3(input_path, temporary_directory)
            if input_path.suffix.lower() == ".mp3"
            else input_path
        )
        duration = _audio_duration(transcription_input)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(
            prefix=f".{output_path.stem}.", suffix=".mid", dir=output_path.parent, delete=False
        ) as temporary_output:
            temporary_midi = Path(temporary_output.name)
        try:
            engine.transcribe(transcription_input, temporary_midi, instrument)
            notes = parse_midi_notes(temporary_midi)
            os.replace(temporary_midi, output_path)
        except MidiValidationError as exc:
            raise TranscriptionOutputValidationError(f"Generated MIDI is invalid: {exc}") from exc
        except TranscriptionModelError:
            raise
        except Exception as exc:
            raise TranscriptionModelError("Transcription engine failed") from exc
        finally:
            temporary_midi.unlink(missing_ok=True)

    return {
        "output": str(output_path),
        "notes": len(notes),
        "duration": duration,
        "engine": engine.name,
        "engineVersion": engine.version,
    }


@register_command("transcribe")
def registered_transcribe_command(request: dict) -> dict:
    return transcribe_command(request)
