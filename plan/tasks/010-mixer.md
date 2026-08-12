# Task 010 — Simple Stem Mixer

## Goal
Mix source and generated stems.

## Agent prompt
Implement a deterministic mixer supporting:
- gain
- pan
- mute
- timeline placement
- WAV output

Requirements:
- operate in frames;
- preserve actual sample rate;
- mono/stereo support;
- explicit resampling if required;
- safe clipping handling;
- dry mix option.

Tests:
- piano only;
- piano + bass;
- mono + stereo.

Do not implement mastering here.
