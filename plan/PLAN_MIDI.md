# MIDI-First AI Music Arranger

## 1. Goal

Build a local-first AI-assisted music arranger for personal use.

The application should allow the user to start from either:

```text
MIDI
```

or:

```text
WAV / MP3
```

If the input is audio, the application converts it into MIDI first.

From that point onward, the composition and arrangement pipeline is **MIDI-first**.

The target workflow is:

```text
MIDI / WAV / MP3
       ↓
Input Adapter
       ↓
Audio → MIDI transcription
       ↓
MIDI cleanup
       ↓
MIDI analysis
       ↓
Song structure
       ↓
Global AI Song Planner
       ↓
Arrangement plan
       ↓
MIDI instrument generation
       ↓
Instrument rendering
       ↓
WAV stems
       ↓
Mix
       ↓
LoFi
       ↓
Master
       ↓
master.wav
       ↓
optional MP3 export
```

---

# 2. Project Scope

This application is currently **only for personal use**.

Prioritize:

* simple architecture
* local execution
* understandable code
* deterministic behavior where possible
* easy debugging
* inspectable intermediate files
* reusable MIDI/instrument pipeline
* commercial-safe sample libraries

Do not add:

* authentication
* database
* web accounts
* cloud infrastructure
* payments
* multi-user support
* distributed workers
* automatic YouTube publishing
* Spotify integration
* SaaS architecture

These can be considered much later if necessary.

---

# 3. Core Architectural Principle

The creative part of the application should operate primarily on MIDI.

Qwen should make **musical decisions**, not directly manipulate audio.

The architecture should therefore be:

```text
             MUSICAL INPUT
                  │
        ┌─────────┴──────────┐
        │                    │
      MIDI                 AUDIO
        │               WAV / MP3
        │                    │
        │              transcription
        │                    │
        └─────────┬──────────┘
                  ↓
               MIDI
                  ↓
            MIDI cleanup
                  ↓
            MIDI analysis
                  ↓
         AI musical planning
                  ↓
         MIDI composition
                  ↓
        instrument rendering
                  ↓
                WAV
                  ↓
           audio processing
```

The AI does not generate arbitrary Kotlin, Python, shell commands, file paths, or DSP code during song creation.

It produces validated structured musical plans.

---

# 4. Recommended Responsibilities

## Kotlin

Use Kotlin for:

```text
CLI
project management
song structure
domain models
validation
AI planning orchestration
instrument registry
pipeline orchestration
Python worker communication
```

## Python

Use Python for:

```text
MP3 → WAV
audio → MIDI transcription
MIDI cleanup helpers
audio analysis helpers
MIDI rendering if needed
mixing helpers
repair
LoFi
mastering
final MP3 conversion
```

## Qwen

Use Qwen for:

```text
global song planning
arrangement decisions
energy curve
instrument roles
section variation
transition planning
arrangement critique
optional mixing suggestions
```

Qwen should initially **not generate every individual MIDI note**.

Deterministic generators should create actual bass, drum, pad, and string notes from higher-level instructions.

---

# 5. Project Structure

Recommended song project:

```text
projects/song-001/
├── project.json
│
├── source/
│   ├── A.wav
│   ├── B.mp3
│   └── C.mid
│
├── midi/
│   ├── raw/
│   │   ├── A.mid
│   │   └── B.mid
│   │
│   ├── clean/
│   │   ├── A.mid
│   │   ├── B.mid
│   │   └── C.mid
│   │
│   └── generated/
│       ├── bass.mid
│       ├── drums.mid
│       ├── pad.mid
│       └── strings.mid
│
├── analysis/
│   ├── A.json
│   ├── B.json
│   └── C.json
│
├── song_plan.json
├── arrangement_v1.json
├── arrangement.json
│
├── stems/
│   ├── piano.wav
│   ├── bass.wav
│   ├── drums.wav
│   ├── pad.wav
│   └── strings.wav
│
├── mix/
│   ├── dry.wav
│   └── lofi.wav
│
└── output/
    ├── master.wav
    └── song.mp3
```

Keep intermediate files.

Do not delete them automatically while the application is under development.

They are critical for debugging.

---

# 6. Instrument Library

Maintain instruments separately from song projects.

```text
instruments/
├── instruments.json
├── LICENSES.json
│
├── piano/
├── bass/
├── drums/
├── pads/
└── strings/
```

For the first version support only:

```text
piano
bass
drums
pad
strings
```

Do not collect dozens of instruments yet.

---

# 7. Instrument Registry

Create:

```text
instruments/instruments.json
```

Example:

```json
{
  "piano": {
    "engine": "sfz",
    "path": "piano/piano.sfz"
  },

  "bass": {
    "engine": "sfz",
    "path": "bass/upright-bass.sfz"
  },

  "drums": {
    "engine": "sfz",
    "path": "drums/lofi-kit.sfz"
  },

  "pad": {
    "engine": "sfz",
    "path": "pads/warm-pad.sfz"
  },

  "strings": {
    "engine": "sfz",
    "path": "strings/chamber.sfz"
  }
}
```

The AI should only receive:

```text
piano
bass
drums
pad
strings
```

It should never receive filesystem paths.

The application resolves logical names to actual instruments.

---

# 8. License Registry

Because resulting music may later be monetized, track sample/instrument licenses.

Create:

```text
instruments/LICENSES.json
```

Example:

```json
{
  "upright-bass": {
    "license": "CC0-1.0",
    "commercialUse": true,
    "attributionRequired": false
  },

  "example-piano": {
    "license": "CC-BY-3.0",
    "commercialUse": true,
    "attributionRequired": true
  }
}
```

Prefer:

```text
CC0
Public Domain
commercial-use-friendly licenses
```

Avoid:

```text
NC
NonCommercial
```

for songs intended for monetization.

Eventually add:

```bash
music-cli licenses ./projects/song-001
```

---

# 9. Milestone 1 — Input Adapters

The application should support two input paths.

## Direct MIDI

```text
.mid
 ↓
MIDI import
```

## Audio

```text
.wav / .mp3
      ↓
audio transcription
      ↓
MIDI
```

Everything should converge into the same MIDI pipeline.

---

# 10. Audio-to-MIDI Command

Implement a dedicated command first.

Example:

```bash
music-cli transcribe \
  --input ./recordings/verse.wav \
  --output ./projects/song-001/midi/raw/A.mid \
  --instrument piano
```

Support:

```text
WAV
MP3
```

For MP3:

```text
MP3
 ↓
temporary WAV
 ↓
transcription model
 ↓
MIDI
```

Do not transcribe directly from compressed audio if the selected transcription tool works better with WAV.

---

# 11. Initial Transcription Scope

Start only with:

> **solo piano audio → piano MIDI**

Do not initially attempt:

```text
full song → all stems → MIDI
```

That is much harder and will introduce too many errors.

Suitable first inputs:

```text
solo piano
clean keyboard recording
simple monophonic melody
```

Possible later extensions:

```text
guitar
vocal melody
bass
multi-instrument transcription
```

---

# 12. Python Worker Transcription Endpoint

Add:

```text
POST /transcribe
```

Example request:

```json
{
  "path": "/music/verse.wav",
  "outputPath": "/project/midi/raw/A.mid",
  "instrument": "piano"
}
```

Possible response:

```json
{
  "output": "/project/midi/raw/A.mid",
  "notes": 184,
  "duration": 23.6
}
```

The endpoint should not perform arrangement.

It only converts audio into MIDI.

---

# 13. MIDI Cleanup

Never assume raw transcription MIDI is production-ready.

Add a second stage:

```text
raw.mid
   ↓
MIDI cleanup
   ↓
clean.mid
```

Implement:

```bash
music-cli midi-clean \
  --input ./midi/raw/A.mid \
  --output ./midi/clean/A.mid
```

---

# 14. MIDI Cleanup Operations

Initial cleanup should support:

```text
remove duplicate notes
remove extremely short notes
remove low-velocity noise notes
fix impossible overlaps
normalize note-off events
optional quantization
optional velocity cleanup
optional sustain cleanup
```

Do not make aggressive quantization the default.

---

# 15. Humanized Quantization

Support partial quantization.

Example:

```bash
music-cli midi-clean \
  --input raw.mid \
  --output clean.mid \
  --quantize 1/16 \
  --strength 0.4
```

Where:

```text
0.0 = unchanged

1.0 = completely snapped to grid
```

For expressive piano, start around:

```text
0.2 – 0.5
```

rather than forcing perfect timing.

---

# 16. Python Worker MIDI Cleanup Endpoint

Optional worker endpoint:

```text
POST /midi-clean
```

Example:

```json
{
  "path": "/project/midi/raw/A.mid",
  "outputPath": "/project/midi/clean/A.mid",
  "quantize": "1/16",
  "strength": 0.4,
  "minNoteMs": 50,
  "minVelocity": 8
}
```

Keep cleanup deterministic.

---

# 17. MIDI Import

For MIDI source files:

```bash
music-cli part add \
  --project ./projects/song-001 \
  --id A \
  --file verse.mid \
  --role verse
```

Copy the original MIDI into:

```text
source/
```

and normalized MIDI into:

```text
midi/clean/
```

Do not modify the user's source.

---

# 18. Convenience Audio Import

After standalone transcription is stable, optionally support:

```bash
music-cli part add \
  --project ./projects/song-001 \
  --id A \
  --file verse.wav \
  --role verse \
  --transcribe
```

Internally:

```text
verse.wav
   ↓
copy source
   ↓
transcribe
   ↓
raw/A.mid
   ↓
cleanup
   ↓
clean/A.mid
   ↓
register part
```

Do not implement this convenience command before the standalone transcription command works reliably.

---

# 19. Milestone 2 — MIDI Analysis

Once MIDI is clean, analyze it.

Extract:

```text
tempo
time signature
bars
beats
notes
velocities
note lengths
pitch range
note density
```

Derive where practical:

```text
key
chord progression
melodic range
rhythmic density
energy
```

---

# 20. MIDI Analysis Example

```json
{
  "partId": "A",

  "tempo": 82,

  "timeSignature": "4/4",

  "bars": 8,

  "key": "A minor",

  "pitchRange": {
    "min": 48,
    "max": 76
  },

  "noteDensity": 0.32,

  "energy": 0.28,

  "chords": [
    "Am",
    "F",
    "C",
    "G"
  ]
}
```

Store:

```text
analysis/A.json
```

---

# 21. Milestone 3 — Song Structure

Keep structure explicitly controlled by the user.

Example:

```text
A A B B A C B B
```

or:

```text
A*2 B*2 A C B*2
```

Normalize into:

```json
[
  "A",
  "A",
  "B",
  "B",
  "A",
  "C",
  "B",
  "B"
]
```

The AI must not silently reorder the song.

---

# 22. Section Instances

Repeated parts should become independent section instances.

Example:

```text
A A B B A
```

becomes:

```text
A1
A2
B1
B2
A3
```

All reference the same source MIDI but can have different arrangements.

---

# 23. Milestone 4 — Global Song Planner

Create:

```text
GlobalSongPlanner
```

Input:

```text
all part analyses
song structure
available instruments
desired style
constraints
```

Qwen should analyze the **whole composition**.

Do not call Qwen independently for A, then B, then C.

---

# 24. Global Planning Responsibilities

The planner decides:

```text
overall energy curve
intro behavior
development
climax
instrument progression
section variation
transition strategy
ending behavior
```

Output:

```text
song_plan.json
```

---

# 25. song_plan.json

Example:

```json
{
  "style": "warm melancholic lo-fi piano",

  "energyCurve": [
    0.20,
    0.30,
    0.55,
    0.72,
    0.40,
    0.52,
    0.78,
    0.90
  ],

  "sections": [
    {
      "index": 0,
      "partId": "A",
      "purpose": "introduction"
    },

    {
      "index": 1,
      "partId": "A",
      "purpose": "development"
    },

    {
      "index": 2,
      "partId": "B",
      "purpose": "first_climax"
    }
  ]
}
```

---

# 26. Repetition Variation

Do not render repeated sections identically.

For:

```text
A A B B A
```

avoid:

```text
A1 == A2 == A3
```

Prefer:

```text
A1
piano

A2
piano + pad

A3
piano + bass + subtle percussion
```

And:

```text
B1
piano + bass + drums

B2
piano + bass + drums + strings
```

---

# 27. Milestone 5 — Arrangement Plan

Convert song-level planning into detailed section instructions.

Create:

```text
arrangement.json
```

Example:

```json
{
  "version": 2,

  "sections": [
    {
      "index": 2,

      "partId": "B",

      "role": "chorus",

      "energy": 0.72,

      "instruments": [
        {
          "name": "piano",
          "mode": "source"
        },

        {
          "name": "bass",
          "mode": "generated",
          "role": "root_fifth",
          "density": 0.55
        },

        {
          "name": "drums",
          "mode": "generated",
          "role": "lofi_groove",
          "density": 0.60
        }
      ],

      "transitionIn": {
        "type": "drum_fill",
        "bars": 1,
        "intensity": 0.45
      }
    }
  ]
}
```

---

# 28. AI Musical Role Model

Initially ask Qwen for musical roles instead of individual notes.

Bass:

```json
{
  "role": "root_fifth",
  "density": 0.45,
  "movement": "rising",
  "register": "low",
  "syncopation": 0.2
}
```

Drums:

```json
{
  "role": "relaxed_lofi",
  "kickDensity": 0.4,
  "snare": "beats_2_4",
  "hihatDensity": 0.6,
  "swing": 0.12,
  "fillLastBar": true
}
```

Pads:

```json
{
  "role": "sustained_chords",
  "density": 0.25,
  "register": "mid_high"
}
```

---

# 29. Milestone 6 — Bass Generator

Implement first:

```text
BassGenerator
```

Input:

```text
key
chords
tempo
bars
energy
bass role
```

Output:

```text
bass.mid
```

Start with patterns:

```text
root
root + fifth
octave
sustained
simple walking
```

The AI chooses a pattern.

The deterministic generator creates actual MIDI notes.

---

# 30. Milestone 7 — Instrument Renderer

Before adding many generated instruments, prove MIDI rendering works.

Pipeline:

```text
bass.mid
+
bass.sfz
    ↓
sampler
    ↓
bass.wav
```

Create:

```text
InstrumentRenderer
```

Interface concept:

```text
render(midi, instrument, output)
```

Instrument paths are resolved through the registry.

---

# 31. Milestone 8 — First Complete Test

The first meaningful AI arrangement should only use:

```text
piano
+
bass
```

Example:

```text
A.mid
B.mid

Structure:
A A B B A
```

Then:

```text
MIDI analysis
    ↓
Qwen song planner
    ↓
bass planning
    ↓
bass MIDI
    ↓
piano rendering
bass rendering
    ↓
mix.wav
```

Do not add drums, pads, strings and transitions until this works well.

---

# 32. Milestone 9 — Drum Generator

Implement:

```text
DrumGenerator
```

Input:

```text
tempo
time signature
energy
style
density
transition info
```

Output:

```text
drums.mid
```

Initial patterns:

```text
minimal
soft lo-fi
standard groove
half-time
build
```

Support simple fills.

---

# 33. Milestone 10 — Pad Generator

Implement:

```text
PadGenerator
```

Input:

```text
chords
key
energy
section duration
```

Output:

```text
pad.mid
```

Start with sustained chord voicings.

Pads are particularly useful for connecting different source sections.

---

# 34. Milestone 11 — Strings

Add after bass/drums/pads work.

Initial roles:

```text
sustained harmony
climax reinforcement
long notes
simple countermelody
```

Avoid complex orchestration initially.

---

# 35. Milestone 12 — Transition Engine

Create:

```text
TransitionEngine
```

It receives:

```text
previous section
current section
next section
```

Possible transitions:

```text
none
drum_fill
bass_walk
pad_sustain
build
drop
cymbal
fade
```

Example:

```text
A
│
│ bass simplifies
│ drums reduce
│ pad sustains
│
├── drum fill
│
▼
B
full rhythm enters
```

Prefer MIDI-based musical transitions instead of inserting arbitrary generated audio.

---

# 36. Harmonic Connections

Use MIDI analysis to inspect:

```text
end chord of previous section
start chord of next section
```

Example:

```text
A ends:
Am

B starts:
F
```

A transition generator may introduce:

```text
Am → C → F
```

through:

```text
bass
pad
strings
```

This is one of the main places where the application can start feeling like a real arranger.

---

# 37. Milestone 13 — Render All Stems

Generate:

```text
stems/
├── piano.wav
├── bass.wav
├── drums.wav
├── pad.wav
└── strings.wav
```

Every stem must have compatible:

```text
sample rate
timeline
duration
channel layout
```

The project should explicitly define the working sample rate.

Do not make individual DSP processors silently assume 48 kHz.

---

# 38. Milestone 14 — Mixer

Create a simple deterministic mixer.

Input:

```text
piano.wav
bass.wav
drums.wav
pad.wav
strings.wav
```

Output:

```text
mix/dry.wav
```

Support:

```text
gain
pan
mute
timeline placement
safe peak handling
```

Do not put mastering inside the mixer.

---

# 39. AI Mix Plan

Later Qwen can suggest parameters:

```json
{
  "piano": {
    "gainDb": -1,
    "pan": 0
  },

  "bass": {
    "gainDb": -5,
    "pan": 0
  },

  "drums": {
    "gainDb": -4,
    "pan": 0
  },

  "pad": {
    "gainDb": -8,
    "pan": 0.15
  }
}
```

The deterministic mixer still performs the actual operation.

---

# 40. Milestone 15 — AI Critic

Add a second AI planning pass.

```text
song_plan
   ↓
arrangement_v1
   ↓
critic
   ↓
arrangement.json
```

Critic checks:

```text
too repetitive
weak transitions
abrupt energy changes
too many instruments
insufficient contrast
bad climax progression
loss of original piano identity
```

The critic returns structured changes.

Keep both files for debugging.

---

# 41. Milestone 16 — LoFi

LoFi remains downstream of musical arrangement.

```text
mix/dry.wav
   ↓
LoFi
   ↓
mix/lofi.wav
```

The current issue is that LoFi sounds almost identical to the dry mix.

Do not tune this until the MIDI arrangement pipeline works.

---

# 42. LoFi A/B Debug Command

Add:

```bash
music-cli compare \
  mix/dry.wav \
  mix/lofi.wav
```

Report:

```text
RMS difference
peak difference
spectral difference
sample difference
```

This confirms whether LoFi processing is really doing anything.

---

# 43. Milestone 17 — Mastering

Pipeline:

```text
mix/lofi.wav
      ↓
master
      ↓
output/master.wav
```

or without LoFi:

```text
mix/dry.wav
      ↓
master
      ↓
output/master.wav
```

Mastering must not compensate for bad arrangement or bad mixing.

---

# 44. Milestone 18 — MP3 Export

MP3 should always be a separate export stage.

```text
master.wav
    ↓
MP3 encoder
    ↓
song.mp3
```

Never use:

```text
.mp3 filename
+
WAV writer
```

Container and extension must agree.

---

# 45. Full Final Architecture

```text
                       INPUT
                         │
             ┌───────────┴────────────┐
             │                        │
           MIDI                  WAV / MP3
             │                        │
             │                 Audio → MIDI
             │                        │
             │                    raw.mid
             │                        │
             └───────────┬────────────┘
                         ▼
                    MIDI CLEANUP
                         │
                         ▼
                     CLEAN MIDI
                         │
                         ▼
                    MIDI ANALYSIS
                         │
                         ▼
                   SONG STRUCTURE
                         │
                         ▼
                GLOBAL QWEN PLANNER
                         │
                         ▼
                   song_plan.json
                         │
                         ▼
                 ARRANGEMENT ENGINE
                         │
                         ▼
                  arrangement.json
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
       Bass MIDI      Drum MIDI       Pad MIDI
          │              │              │
          └──────────────┼──────────────┘
                         │
                   TRANSITIONS
                         │
                         ▼
                INSTRUMENT RENDERER
                         │
                         ▼
              SFZ / SAMPLE LIBRARIES
                         │
                         ▼
                       STEMS
                         │
                         ▼
                        MIX
                         │
                         ▼
                    mix/dry.wav
                         │
                         ▼
                       LoFi
                         │
                         ▼
                   mix/lofi.wav
                         │
                         ▼
                      MASTER
                         │
                         ▼
                  output/master.wav
                         │
                         ▼
                  optional MP3 export
```

---

# 46. Updated Development Order

Implement in this exact order.

## Task 1 — Audio-to-MIDI research spike

Choose and test a transcription solution specifically with:

```text
solo piano WAV
```

Success criterion:

```text
piano.wav
 ↓
piano.mid
```

with recognizable notes and timing.

Do not integrate into the full application yet.

---

## Task 2 — `/transcribe` worker endpoint

Expose the transcription engine through the existing Python worker.

---

## Task 3 — MIDI cleanup

Implement deterministic cleanup and optional soft quantization.

---

## Task 4 — Unified input adapter

Support:

```text
MIDI → clean MIDI

WAV/MP3 → transcribe → clean MIDI
```

From this point forward, downstream code only sees MIDI.

---

## Task 5 — MIDI analysis

Extract musical information from MIDI.

---

## Task 6 — Instrument registry

Create logical instrument mapping and license registry.

---

## Task 7 — MIDI → SFZ → WAV rendering

Prove:

```text
piano.mid → piano.wav
```

and:

```text
bass.mid → bass.wav
```

---

## Task 8 — Bass generator

Create deterministic bass MIDI from chord information.

---

## Task 9 — Global song planner

Use Qwen to produce:

```text
song_plan.json
```

for the complete song.

---

## Task 10 — Repeated-section variation

Make:

```text
A1
A2
A3
```

musically related but arranged differently.

---

## Task 11 — Detailed arrangement plan

Generate:

```text
arrangement.json
```

---

## Task 12 — First full piano + bass arrangement

Stop here and evaluate quality.

Do not continue until this sounds musically useful.

---

## Task 13 — Drum generator

Add drums and simple fills.

---

## Task 14 — Pad generator

Add harmonic continuity.

---

## Task 15 — Transition engine

Connect sections using bass, drums and pads.

---

## Task 16 — Strings

Add only after the other instruments work well.

---

## Task 17 — Stem mixer

Render and mix all stems.

---

## Task 18 — AI critic

Review arrangement before final rendering.

---

## Task 19 — LoFi debugging

Make LoFi audibly different but musical.

---

## Task 20 — Mastering

Reuse the current working mastering pipeline.

---

## Task 21 — Final MP3 export

Convert final WAV only after mastering.

---

# 47. Qwen Coding Agent Guidelines

For every implementation task, prompt the coding agent with:

```text
You are implementing one task of the MIDI-first AI Music Arranger.

This is a local personal project.

Before coding:

1. Read plan.md.
2. Read README.md.
3. Inspect the repository.
4. Find existing related code.
5. Run current tests/build.
6. Identify the minimum files necessary.

Implementation rules:

- Keep the current Kotlin + Python architecture.
- Prefer small incremental changes.
- Do not rewrite working DSP.
- Do not introduce SaaS/cloud architecture.
- Add tests.
- Keep intermediate audio lossless.
- Preserve source files.
- Never assume 48kHz.
- Validate MIDI and AI output.
- Never execute AI-generated code.
- Keep AI planning separate from MIDI/audio execution.

After implementation:

1. Run tests.
2. Run build.
3. Run a small manual example.
4. List changed files.
5. List commands executed.
6. Report assumptions.
7. Report remaining issues.
```

Give the agent **one task at a time**.

---

# 48. Runtime Qwen Rule

During music creation Qwen is:

> **the arranger / producer / planner**

It is not:

> the audio engine.

Preferred:

```text
Qwen:
"Use a low-register root/fifth bass pattern with increasing activity in the second chorus."

        ↓

BassGenerator:
creates actual MIDI notes
```

rather than:

```text
Qwen:
outputs hundreds of arbitrary MIDI events
```

at least in the early versions.

---

# 49. First New Target

The first target after adding audio-to-MIDI should be:

```text
piano.wav
    ↓
transcribe
    ↓
raw piano MIDI
    ↓
cleanup
    ↓
clean piano MIDI
    ↓
analysis
    ↓
Qwen song plan
    ↓
generated bass MIDI
    ↓
piano + bass rendering
    ↓
mix.wav
```

This is the first version that should feel genuinely different from the current application.

If this works well, the architecture is validated.

Then add:

```text
drums
pads
transitions
strings
LoFi
```

one by one.

---

# 50. Long-Term User Experience

Eventually a command such as:

```bash
music-cli build \
  --project ./projects/song-001 \
  --style "warm melancholic lo-fi"
```

should perform:

```text
import audio/MIDI
      ↓
transcribe audio if needed
      ↓
clean MIDI
      ↓
understand notes/chords/rhythm
      ↓
understand song structure
      ↓
plan global arrangement
      ↓
vary repeated sections
      ↓
compose supporting instruments
      ↓
connect sections musically
      ↓
render virtual instruments
      ↓
mix stems
      ↓
LoFi
      ↓
master
      ↓
master.wav
```

The core objective remains:

> **Preserve the user's composition while using AI as an arranger/producer that builds a coherent song around it.**
