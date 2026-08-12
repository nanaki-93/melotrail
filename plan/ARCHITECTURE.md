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
