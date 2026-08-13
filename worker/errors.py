"""Safe, typed errors returned by worker command handlers."""

from __future__ import annotations


class WorkerCommandError(Exception):
    """An expected command failure whose message is safe to return to clients."""

    error_type = "WorkerError"
    status_code = 500


class TranscriptionValidationError(WorkerCommandError):
    error_type = "ValidationError"
    status_code = 400


class TranscriptionDecodeError(WorkerCommandError):
    error_type = "DecodeError"
    status_code = 422


class TranscriptionModelError(WorkerCommandError):
    error_type = "ModelError"
    status_code = 500


class TranscriptionOutputValidationError(WorkerCommandError):
    error_type = "OutputValidationError"
    status_code = 422


class MidiCleanupValidationError(WorkerCommandError):
    error_type = "MidiCleanupValidationError"
    status_code = 400


class MidiCleanupOutputValidationError(WorkerCommandError):
    error_type = "MidiCleanupOutputValidationError"
    status_code = 422
