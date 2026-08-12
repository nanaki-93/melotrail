# Personal AI Music Arranger — Implementation Plan

## Goal

Build a small local-first application for personal use that turns user-provided musical parts into a structured song arrangement, optionally adds generated instruments, mixes the result, and then reuses the existing Python repair/LoFi/mastering pipeline.

This is deliberately **not** a SaaS/business architecture.

First useful version:

```text
CLI
 -> project/parts
 -> analyze
 -> define structure
 -> deterministic arrangement
 -> optional local Qwen arrangement plan
 -> generate stems
 -> mix
 -> repair
 -> LoFi
 -> master
 -> WAV
```

## Core decision

Do not start by asking an LLM to generate finished audio.

The first AI layer produces a **strict JSON arrangement plan**. Audio generation is a separate layer.

Example:

```json
{
  "version": 1,
  "sections": [
    {
      "partId": "A",
      "instruments": [
        {"name": "piano", "mode": "source"},
        {"name": "bass", "mode": "generated", "role": "root_fifth", "density": 0.35}
      ]
    }
  ]
}
```

## MVP scope

### In scope
- Local CLI.
- WAV/MP3 input parts.
- Local project directory and JSON metadata.
- Explicit song structure such as `A A B B A C B`.
- Basic audio analysis: duration, sample rate, channels, RMS/peak, silence.
- Deterministic timeline/section assembly.
- Arrangement JSON schema.
- Local Qwen planner adapter.
- Simple instrument generation path, preferably MIDI first.
- Basic stem mixing.
- Existing repair/LoFi/mastering pipeline as final stage.
- WAV output.

### Out of scope for now
- Web UI.
- Authentication.
- Database.
- Cloud infrastructure.
- Multi-user support.
- Payments.
- Distributed jobs.
- Automatic YouTube/Spotify publishing.
- Training a custom music model.
- General-purpose DAW features.
- Building a Suno competitor.

## Recommended execution order

1. Baseline and guardrails
2. Project/part model
3. CLI project creation and part import
4. Structure parser and timeline
5. Basic analysis
6. Arrangement schema + deterministic planner
7. Arrangement planner interface
8. Local Qwen planner
9. MIDI/instrument generation spike
10. Stem mixer
11. Existing DSP integration
12. End-to-end CLI
13. Tests/docs/cleanup

Tasks 1–6 create a useful non-AI foundation. Only then enable Qwen.

## Definition of done

A personal project should eventually support:

```bash
music-cli arrange --project ./projects/demo --structure "A A B B A C B"
music-cli render --project ./projects/demo
music-cli process --project ./projects/demo
```

and produce:

```text
projects/demo/output/master.wav
```

Keep intermediate files for debugging.

## Principle

Every task must be independently testable. Never make a large speculative rewrite.
