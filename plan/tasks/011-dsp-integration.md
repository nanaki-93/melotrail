# Task 011 — Existing Repair/LoFi/Master Integration

## Goal
Connect mix.wav to the existing Python DSP pipeline.

## Agent prompt
First inspect and run the currently working AudioPipeline and Python worker.

Then integrate:

mix.wav
 -> repair
 -> LoFi
 -> master
 -> master.wav

Requirements:
- WAV between processing stages;
- repair/LoFi/master optional;
- master output uses explicit WAV container/subtype;
- MP3 export remains separate;
- do not pass `.mp3` to WAV writers;
- preserve current DSP behavior unless integration requires a change.

Do not rewrite DSP algorithms.
