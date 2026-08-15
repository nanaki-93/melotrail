# Architecture

## Simple local architecture

```text
                    music-cli
                        |
          +-------------+-------------+
          |             |             |
       project       analyze       arrange
          |             |             |
          +-------------+-------------+
                        |
                 arrangement.json
                        |
                 generation/render
                        |
                    stems/*.wav
                        |
                      mix.wav
                        |
             existing audio pipeline
             repair -> lofi -> master
                        |
                    master.wav
```

## Responsibilities

### Kotlin
- CLI.
- Compose Desktop product UI over typed application services.
- Optional local Spring JSON API; it does not serve a browser UI or static
  fallback.
- Project/part/structure domain models.
- Validation.
- Arrangement model.
- AI planner interface.
- Local AI HTTP client.
- Orchestration of Python worker commands.
- File/path management.

### Python
- Audio conversion.
- Signal analysis.
- Audio rendering helpers.
- MIDI/instrument rendering when appropriate.
- Existing repair.
- Existing LoFi.
- Existing mastering.

### AI
The AI is an **arrangement planner**, not the audio processor.

Input:
- analyzed parts
- structure
- style
- available instruments
- constraints

Output:
- strict JSON
- no prose
- no code
- no arbitrary file paths

## Project layout

```text
project/
  project.json
  parts/
    A.wav
    B.wav
  analysis/
    A.json
    B.json
  arrangement.json
  stems/
  mix/
  output/
```

## Minimal project.json

```json
{
  "version": 1,
  "name": "demo",
  "parts": [
    {"id": "A", "file": "parts/A.wav", "role": "verse"},
    {"id": "B", "file": "parts/B.wav", "role": "chorus"}
  ],
  "structure": ["A", "A", "B", "B", "A"]
}
```

## Core entities

Keep these small:

```text
Project
Part
PartAnalysis
SectionInstance
Arrangement
InstrumentPlan
TransitionPlan
```

Avoid generic frameworks.

## Arrangement boundary

```kotlin
interface ArrangementPlanner {
    fun plan(input: ArrangementInput): Arrangement
}
```

Implement:
- DeterministicArrangementPlanner
- LocalQwenArrangementPlanner

The deterministic planner is the fallback/test oracle.

## Arrangement JSON

```json
{
  "version": 1,
  "sections": [
    {
      "index": 0,
      "partId": "A",
      "instruments": [
        {"name": "piano", "mode": "source"},
        {"name": "bass", "mode": "generated", "role": "root_fifth", "density": 0.35}
      ],
      "transitionOut": {"type": "none", "bars": 0}
    }
  ]
}
```

## Audio generation

Prefer:

```text
Arrangement
 -> MIDI/notes
 -> local instrument renderer
 -> WAV stems
```

Do not build a custom synthesizer for MVP.

## Local instrument library

The MVP sound library already exists separately from song projects:

```text
sounds/
  instruments.json
  LICENSES.json
  piano/piano.sfz
  bass/bass.sfz
  drums/drums.sfz
  pad/pad.sfz
  strings/strings.sfz
  */samples/*.wav
```

Use `sounds/` as the default library root. The current pack provides all five approved logical instruments and 25 mono 44.1 kHz PCM-16 source samples. The instrument renderer must convert/render these assets into each project's explicit lossless stem format; it must not treat sample-source bit depth or channel count as the project output format.

Only the renderer/registry boundary sees SFZ and sample paths. Project metadata, deterministic planners, and Qwen use logical names only: `piano`, `bass`, `drums`, `pad`, and `strings`.

The current sample WAV files are ignored by Git, so local setup/portability must be documented before a clean checkout can be considered render-ready. See `SOUND_LIBRARY_BASELINE.md`.

## Mixing

First mixer only needs:
- gain
- pan
- mute
- timeline placement
- safe output

No DAW engine.

## Existing pipeline

```text
mix.wav
 -> repair
 -> lofi
 -> master
 -> master.wav
```

Intermediate processing stays lossless. MP3 export is a separate final conversion step.

## Safety

- Never assume 48 kHz.
- Preserve actual sample rate/channels.
- Distinguish samples from frames.
- Do not overwrite source audio.
- Validate all AI output.
- Never execute model-generated code or paths.
