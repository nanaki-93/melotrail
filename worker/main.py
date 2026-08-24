"""HTTP server for the Melotrail Python worker.

The worker is a standalone service. Each operation has its own endpoint:
    GET  /health
    POST /analyze
    POST /apply_dsp
    POST /repair
    POST /master
    POST /mp3_export
    POST /mp3_convert
    POST /codec_preview
    POST /transcribe
    POST /midi-clean
    POST /inspect-input
    POST /cleanup

Request bodies contain the command-specific input directly, rather than a
generic {"command": "...", "input": {...}} envelope.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import logging
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Callable

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger("worker")

from worker.registry import COMMANDS, register_command
from worker.errors import WorkerCommandError


class WorkerHandler(BaseHTTPRequestHandler):
    """HTTP handler exposing one POST endpoint per command."""

    def log_message(self, fmt: str, *args: Any) -> None:
        logger.info(fmt, *args)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._send_json(200, {
                "status": "ok",
                "version": "1.0.0",
                "available": True,
                "commands": sorted(COMMANDS.keys()),
                "transcriptionRuntime": importlib.util.find_spec("basic_pitch") is not None,
                "mp3ExportRuntime": importlib.util.find_spec("lameenc") is not None,
                # Kotlin checks this bounded capability before using the versioned
                # Clean MIDI request contract; it never negotiates arbitrary options.
                "midiCleanup": {
                    "requestVersion": 2,
                    "profiles": ["conservative", "transcription-safe", "tighten-timing"],
                },
                "analysis": {
                    "versions": [2],
                },
            })
            return
        self._send_json(404, {"error": "Not found"})

    def do_POST(self) -> None:
        handler = COMMANDS.get(self.path.removeprefix("/"))
        if handler is None:
            self._send_json(404, {
                "error": "Not found",
                "message": f"Unknown endpoint: {self.path}",
            })
            return

        job_id = ""
        try:
            request = self._read_json()
            job_id = str(request.get("jobId", ""))
            logger.info("Executing %s (jobId=%s)", self.path, job_id)

            output = handler(request)
            self._send_json(200, {
                "version": 1,
                "jobId": job_id,
                "status": "completed",
                "output": output or {},
            })
        except WorkerCommandError as exc:
            logger.warning("Worker command rejected %s: %s", self.path, exc)
            self._send_json(exc.status_code, {
                "version": 1,
                "jobId": job_id,
                "status": "error",
                "error": {"type": exc.error_type, "message": str(exc)},
            })
        except ValueError as exc:
            logger.warning("Bad request on %s: %s", self.path, exc)
            self._send_json(400, {
                "version": 1,
                "jobId": job_id,
                "status": "error",
                "error": {"type": "BadRequest", "message": str(exc)},
            })
        except Exception as exc:
            logger.exception("Worker command failed: %s", self.path)
            self._send_json(500, {
                "version": 1,
                "jobId": job_id,
                "status": "error",
                "error": {"type": "WorkerError", "message": str(exc)},
            })

    def _read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length)
        if not raw:
            return {}
        try:
            value = json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError as exc:
            raise ValueError(f"Invalid JSON: {exc.msg}") from exc
        if not isinstance(value, dict):
            raise ValueError("JSON body must be an object")
        return value

    def _send_json(self, status_code: int, data: dict[str, Any]) -> None:
        encoded = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(encoded)


def load_commands() -> None:
    # Importing these modules executes @register_command decorators.
    from worker.commands import analyze, dsp, repair, mastering, mp3_convert, mp3_export, codec_preview, transcribe, midi_clean, input_inspection, cleanup  # noqa: F401
    logger.info("Loaded commands: %s", ", ".join(sorted(COMMANDS)))


def main() -> None:
    parser = argparse.ArgumentParser(description="Melotrail Python worker")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8081)
    args = parser.parse_args()

    load_commands()

    server = ThreadingHTTPServer((args.host, args.port), WorkerHandler)
    logger.info("Python worker listening on http://%s:%d", args.host, args.port)
    logger.info("Health endpoint: GET /health")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("Stopping worker")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
