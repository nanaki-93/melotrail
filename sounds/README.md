# AI Music Starter Instruments

This is a lightweight development pack for your MIDI-first arranger.

Included:
- piano.sfz
- bass.sfz
- drums.sfz
- pad.sfz
- strings.sfz
- WAV samples
- instruments.json
- LICENSES.json

## Purpose

These sounds are intentionally small and lightweight. They are suitable for:
- testing MIDI -> SFZ -> WAV rendering;
- validating your arrangement engine;
- generating early demos;
- testing bass/drums/pad/string generation.

They are not intended to replace a high-quality production sample library.

## Local asset setup and portability

The starter WAV files are intentionally local assets and are excluded by the
repository-wide audio-artifact ignore rule. A fresh checkout is render-ready
only after you copy the approved 25 WAV files into the existing `sounds/`
subdirectories, preserving the SFZ-relative `samples/*.wav` paths. Copy them
from an approved local checkout or archive of this project; the application
never downloads samples. Run `music-cli licenses <project>` (or load the
registry before rendering) to verify the complete pack. Do not create an
`instruments/` tree or substitute third-party files under `starter-generated`.

`instruments.json` uses human-readable, one-based MIDI channels: drums value
`10` is converted to zero-based MIDI API channel `9`. The supported drum map
contains kick, snare, clap, closed hat, and open hat only; there is no crash or
cymbal mapping in this starter pack.

### Selecting the pack in the desktop application

The macOS package does not rely on its launch directory and does not bundle
this local pack. In **Melotrail**, select **Library** and
choose this `sounds/` directory after the sample-copy step. The app validates
the registry and samples before retaining an absolute desktop preference; it
does not copy, download, or modify the library. A terminal-launched desktop
app may instead use `MUSIC_SOUNDS_ROOT=/absolute/path/to/sounds`, which is
authoritative for that launch and disables folder selection until it is unset.

If the app reports missing samples, restore only the approved 25 files at the
existing SFZ-relative `*/samples/*.wav` locations and refresh readiness. If it
reports an invalid registry, do not substitute a different directory layout or
weaken the registry checks; correct the selected library instead.

## MIDI drum mapping

- 36: Kick
- 38: Snare
- 39: Clap
- 42: Closed hi-hat
- 46: Open hi-hat

## Recommended production upgrade

For better acoustic sounds, replace individual instruments with CC0 libraries from:
- Versilian Community Sample Library (VCSL)
- VSCO 2 Community Edition
- VCSL Keys

Keep the same logical instrument names in instruments.json, so no arrangement code has to change.

## Renderer

The application uses the offline `sfizz_render` executable from sfizz, tested
against the documented sfizz 1.2.3 command-line interface and licensed
BSD-2-Clause.
It is a local prerequisite, not an application dependency and not an automatic
download. Install/build it yourself, then point the application at its absolute
path when it is not on `PATH`:

```bash
export SFZ_RENDERER_PATH=/absolute/path/to/sfizz_render
export SFZ_RENDERER_VERSION=1.2.3   # recorded in render metadata
```

The adapter calls it with structured arguments equivalent to:

```text
sfizz_render --sfz <registry-selected.sfz> --midi <validated.mid> --wav <temporary.wav> --samplerate <project-rate> --use-eot
```

`sfizz_render` renders stereo; the application then validates that temporary
WAV and writes the requested project channel layout as PCM-24 WAV. It accepts
at most a two-second renderer tail, pads short output with trailing silence,
and atomically publishes only the verified `expectedFrames` result. MIDI, SFZ,
samples, registry files, and other sound-library paths are never valid outputs.
