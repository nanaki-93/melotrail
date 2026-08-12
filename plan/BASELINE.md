# Baseline and Guardrails

## Repository state — 2026-08-13

This repository currently contains planning and task documentation only. There
are no Kotlin sources, Python sources, audio assets, tests, build files, or
dependency manifests. The Git repository has no commits yet.

Consequently, there is no existing application build, test suite, DSP worker,
or pipeline command to run. This is a documented pre-implementation condition,
not a build failure.

## Current pipeline

The intended pipeline is defined in `README.md` and `ARCHITECTURE.md`:

```text
music-cli
  -> project/parts
  -> analyze
  -> arrange
  -> generation/render
  -> stems/*.wav
  -> mix.wav
  -> repair -> lofi -> master
  -> output/master.wav
```

There is no executable implementation of this pipeline yet. The planned user
commands are:

```bash
music-cli arrange --project ./projects/demo --structure "A A B B A C B"
music-cli render --project ./projects/demo
music-cli process --project ./projects/demo
```

## Future code placement

When implementation starts, keep the responsibilities described by the
architecture separate:

- Kotlin: CLI, project/part models, validation, arrangement planning, and
  orchestration.
- Python: audio conversion, analysis, rendering helpers, and the existing
  repair/LoFi/master stages once they are introduced or imported.
- Project data: `project.json`, source audio in `parts/`, analysis JSON,
  `arrangement.json`, lossless WAV stems/mixes, and final output under the
  local project directory.

## Guardrails

- Do not overwrite source audio.
- Preserve each input's actual sample rate and channel count; operate on frames
  for multi-channel data.
- Keep intermediate audio lossless. MP3 is only a separate final export.
- Treat AI output only as validated arrangement data; never execute generated
  code, commands, or arbitrary paths.
- Retain a deterministic planner as the fallback and test oracle.
