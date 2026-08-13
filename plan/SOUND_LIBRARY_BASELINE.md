# Local Sound Library Baseline

## Current state

The repository workspace already contains a lightweight MVP sound pack under:

```text
sounds/
├── instruments.json
├── LICENSES.json
├── piano/piano.sfz
├── bass/bass.sfz
├── drums/drums.sfz
├── pad/pad.sfz
└── strings/strings.sfz
```

The five approved logical instruments are already present:

```text
piano
bass
drums
pad
strings
```

Do not create a second top-level `instruments/` directory. New registry and renderer work must use `sounds/` as the default instrument-library root, with an explicit local configuration override only when needed.

## Verified inventory

| Instrument | SFZ | Local samples | Key coverage |
|---|---|---:|---|
| piano | `sounds/piano/piano.sfz` | 5 | MIDI 21–108 using C2–C6 root samples |
| bass | `sounds/bass/bass.sfz` | 5 | MIDI 24–60 using E1, A1, D2, G2, C3 |
| drums | `sounds/drums/drums.sfz` | 5 | kick 36, snare 38, clap 39, closed hat 42, open hat 46 |
| pad | `sounds/pad/pad.sfz` | 5 | MIDI 24–96 using C2–C6 root samples |
| strings | `sounds/strings/strings.sfz` | 5 | MIDI 24–96 using C2–C6 root samples |

All 25 sample files currently present in the workspace are:

```text
RIFF/WAVE
PCM 16-bit
mono
44,100 Hz
```

This is valid source material for the starter sampler. Rendered project stems must still follow the project's explicit render format and use lossless PCM-24 WAV as required by the audio rules.

## Registry facts and required enrichment

`sounds/instruments.json` already provides:

- schema version 1;
- `workingSampleRate: 44100`;
- SFZ-relative paths for all five instruments;
- MIDI programs for piano, bass, pad, and strings;
- a drum channel value.

Task 006 must validate and enrich this existing file rather than replace it. Required decisions/changes are:

- define whether stored MIDI channels are zero-based (`0..15`) or human-readable one-based (`1..16`) and normalize the current drum value accordingly;
- add a `licenseId` reference for every instrument;
- add the named drum note map already represented by `drums.sfz`;
- validate every SFZ `sample=` reference and every referenced WAV file;
- validate sample metadata against the registry's declared working sample rate;
- keep logical instrument names and file paths separate at the AI boundary.

The current drum library contains no crash/cymbal sample. Task 015 must either add a separately licensed crash sample and registry mapping before enabling the `cymbal` transition or reject/degrade that transition deterministically. It must not silently map cymbal to clap or open hi-hat.

## License baseline

`sounds/LICENSES.json` declares one local library, `starter-generated`:

- generated specifically for this starter pack;
- no third-party samples included;
- commercial use allowed;
- attribution not required.

Task 006 must connect each instrument to that library ID and validate the declaration. If any files are later replaced with downloaded libraries, add a separate license entry per library with source URL, license identifier/text, commercial-use flag, attribution requirements, acquisition date, and redistribution status. Never overwrite the `starter-generated` record with third-party provenance.

## Git and portability constraint

The SFZ and registry files are tracked, but the repository-wide `*.wav` ignore rule excludes the sample WAV files. Therefore:

- the current workspace is renderable once an SFZ renderer is installed;
- a fresh clone is not guaranteed to contain the samples;
- tasks and tests must not assume the sample files are committed;
- Task 006 must choose and document one explicit local-asset setup policy before declaring the registry portable.

Acceptable policies are a documented local installation/copy step, a narrowly scoped `.gitignore` exception only for redistributable starter samples, or another simple local mechanism. Do not add automatic network downloading to the application.

## Remaining gate

No compatible SFZ renderer (`sfizz_render`, sfizz, FluidSynth, Carla, or equivalent) was found on the current PATH during this audit. The sound assets satisfy Task 007's piano/bass asset prerequisite, but Task 007 remains incomplete until one renderer is installed/configured and real piano/bass MIDI-to-WAV output is inspected and auditioned.
