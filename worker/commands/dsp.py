"""DSP application command."""

import logging
from worker.main import send_progress, send_error, register_command

logger = logging.getLogger('worker.dsp')


@register_command('apply_dsp')
def apply_dsp_command(request: dict) -> dict:
    """Apply DSP chain to audio file."""
    job_id = request.get('jobId', '')
    input_path = request.get('input', {}).get('path', '')
    settings = request.get('input', {}).get('settings', {})

    if not input_path:
        send_error(job_id, 'Missing input path')
        return {}

    logger.info(f"Applying DSP to: {input_path}")

    send_progress(job_id, 0.0, "Loading audio...")

    # Placeholder: In production, this would apply the DSP chain
    try:
        send_progress(job_id, 0.5, "Applying effects...")

        result = {
            'output': input_path,  # In production, path to processed file
            'settings': settings
        }

        send_progress(job_id, 1.0, "Complete")
        return result

    except Exception as e:
        logger.exception("DSP application failed")
        send_error(job_id, f"DSP failed: {str(e)}")
        return {}
