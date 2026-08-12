# Task 009 — First Instrument Generation Spike

## Goal
Generate one instrument stem, preferably bass.

## Agent prompt
Implement a small generation adapter:

arrangement -> MIDI/notes -> local instrument renderer -> bass.wav

If no reliable local renderer exists, create a minimal test renderer/adapter instead of a full synthesizer.

Requirements:
- generation behind an interface;
- output under project/stems;
- preserve source;
- known sample rate/channels;
- duration aligned to section.

Do not implement a full instrument library or DAW.
