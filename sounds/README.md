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

Use an SFZ-compatible renderer such as sfizz/sfizz_render, Sforzando, or another SFZ sampler.
