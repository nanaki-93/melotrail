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
