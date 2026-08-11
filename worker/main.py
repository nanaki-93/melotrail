#!/usr/bin/env python3
"""AI Music Workstation - Python Worker

The worker communicates with the Kotlin application via JSON over stdin/stdout.
Supports commands: analyze, apply_dsp, health, repair, master, etc.
"""

import sys
import json
import logging
import argparse
from typing import Dict, Any

logging.basicConfig(
    stream=sys.stderr,
    level=logging.INFO,
    format='[%(asctime)s] %(levelname)s %(name)s: %(message)s'
)
logger = logging.getLogger('worker')

COMMANDS: Dict[str, Any] = {}


def run_single_command():
    """Process a single JSON command from stdin."""
    try:
        line = sys.stdin.readline().strip()
        if not line:
            return

        request = json.loads(line)
        command = request.get('command')

        if command not in COMMANDS:
            send_error(request.get('jobId', ''), f'Unknown command: {command}')
            return

        try:
            result = COMMANDS[command](request)
            send_response(request.get('jobId', ''), 'completed', result)
        except Exception as e:
            logger.exception(f"Error executing command: {command}")
            send_error(request.get('jobId', ''), str(e))
    except json.JSONDecodeError as e:
        send_error('', f'Invalid JSON: {str(e)}')
    except Exception as e:
        logger.exception("Unexpected error")
        send_error('', str(e))


def run_persistent_server():
    """Run as persistent server, processing commands until EOF."""
    logger.info("Starting persistent worker server")
    while True:
        line = sys.stdin.readline()
        if not line:
            break
        try:
            run_single_command()
        except Exception as e:
            logger.exception("Error in persistent mode")


def send_response(job_id: str, status: str, output: dict = None):
    """Send a success response to stdout."""
    response = {
        'version': 1,
        'jobId': job_id,
        'status': status,
        'output': output or {}
    }
    sys.stdout.write(json.dumps(response) + '\n')
    sys.stdout.flush()


def send_error(job_id: str, message: str):
    """Send an error response to stdout."""
    response = {
        'version': 1,
        'jobId': job_id,
        'status': 'error',
        'error': {
            'type': 'WorkerError',
            'message': message
        }
    }
    sys.stdout.write(json.dumps(response) + '\n')
    sys.stdout.flush()


def send_progress(job_id: str, progress: float, message: str = ''):
    """Send a progress update to stdout."""
    response = {
        'version': 1,
        'jobId': job_id,
        'status': 'progress',
        'progress': progress,
        'message': message
    }
    sys.stdout.write(json.dumps(response) + '\n')
    sys.stdout.flush()


# Health check command
def health_command(request: dict) -> dict:
    """Health check - returns worker status."""
    return {
        'status': 'ok',
        'version': '1.0.0',
        'commands': list(COMMANDS.keys())
    }


def register_command(name: str):
    """Decorator to register a command handler."""
    def decorator(func):
        COMMANDS[name] = func
        return func
    return decorator


# Import commands
try:
    from worker.commands import analyze, dsp, health, repair, mastering
except ImportError:
    logger.warning("Could not import command modules")


def main():
    parser = argparse.ArgumentParser(description='AI Music Workstation Worker')
    parser.add_argument('--persistent', action='store_true',
                       help='Run in persistent mode (process multiple commands)')
    args = parser.parse_args()

    if args.persistent:
        run_persistent_server()
    else:
        run_single_command()


if __name__ == '__main__':
    main()
