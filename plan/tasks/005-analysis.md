# Task 005 — Basic Audio Analysis

## Goal
Analyze imported parts and store metadata.

## Agent prompt
Implement a deterministic analysis command, reusing the existing Python analysis if available.

Store:
- duration
- sample rate
- channels
- frame count
- peak
- RMS
- near-silence

Optional BPM/key only if reliable existing tooling exists; do not make them mandatory.

Write `analysis/<partId>.json`.

Test mono/stereo and 44.1/48 kHz. Never overwrite source files.
