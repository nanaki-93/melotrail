"""Health check command."""

import logging
from worker.main import send_response, register_command

logger = logging.getLogger('worker.health')


@register_command('health')
def health_command(request: dict) -> dict:
    """Health check endpoint."""
    return {
        'status': 'ok',
        'version': '1.0.0',
        'available': True
    }
