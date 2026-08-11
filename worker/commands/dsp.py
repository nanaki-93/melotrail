"""DSP command.

The Kotlin application currently owns the LoFi DSP chain. This endpoint is
kept as the Python-side integration point for future Python DSP processing.
"""

from worker.registry import register_command


@register_command("apply_dsp")
def apply_dsp_command(request: dict) -> dict:
    input_path = request.get("path", "")
    if not input_path:
        raise ValueError("Missing path")
    return {
        "output": input_path,
        "settings": request.get("settings", {}),
    }
