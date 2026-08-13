# Task 012 — First Complete Piano + Bass Quality Gate

## Goal

Validate the MIDI-first architecture end to end with only source piano and generated bass, then stop and assess whether it is musically useful.

## Dependencies

Tasks 001–011 must be complete. In particular, real transcription and SFZ rendering must have passed their manual gates.

Tasks 013–021 must not begin until this task is accepted.

## Target workflow

```text
piano WAV or MIDI
 -> preserved source
 -> raw MIDI when transcribed
 -> clean MIDI
 -> MIDI analysis
 -> song_plan.json
 -> arrangement.json (piano + bass only)
 -> midi/generated/bass.mid
 -> piano.wav + bass.wav
 -> mix/dry.wav
```

Use a project with at least A and B parts and structure `A A B B A` so repeated-section variation is audible.

## CLI orchestration

Add or adapt a narrow build path for this gate. It may be an explicit option/subcommand, but it must:

1. validate the project and all dependencies;
2. prepare clean MIDI for every part;
3. analyze missing/stale MIDI analyses;
4. create or reuse an approved song/arrangement plan;
5. reject any generated instrument other than bass;
6. generate full-timeline bass MIDI;
7. render piano and bass stems using Task 007;
8. mix a dry lossless WAV;
9. preserve and report every artifact.

Do not run drums, pads, strings, transitions, LoFi, repair, mastering, or MP3 in the quality-gate command.

## Automated end-to-end test

Use small MIDI fixtures, fake transcription, and a fake/local test renderer so CI does not require a model or SFZ library. Verify:

- exact stage order and actionable stage errors;
- all required intermediate paths exist;
- source hashes are unchanged;
- project sample rate/channels propagate to both stems and mix;
- bass events align to section/chord boundaries;
- repeated instances use the approved variations;
- piano and bass stem frame counts equal the arrangement timeline;
- output starts at frame zero, contains finite samples, and stays below the documented peak ceiling;
- deterministic mode produces equivalent event/audio metadata on repeat;
- resumption reuses valid artifacts and does not silently reuse stale ones.

## Manual listening gate

Run the real pipeline with the selected transcription engine, piano/bass SFZs, and both:

- direct MIDI input;
- a clean solo-piano WAV transcription.

Review:

- source melody/chords remain recognizable;
- section order is exactly `A A B B A`;
- bass notes match the harmony and enter/change as planned;
- repeated sections are related but not identical in arrangement;
- timing does not drift between piano and bass;
- no clicks, clipped notes, missing attacks, extreme velocity, or excessive tail;
- bass supports rather than masks the piano;
- dry mix is materially more useful than the current fixed-tone bass output.

Record test inputs, commands, artifact metadata, and observations in a checked-in quality report without committing copyrighted audio.

## Acceptance criteria

- Both automated and real local workflows complete.
- All planned intermediate MIDI/JSON/WAV files remain inspectable.
- At least one direct-MIDI and one transcribed-WAV result pass the listening checklist.
- Any known limitations have bounded follow-up tasks and do not invalidate the architecture.
- The user explicitly accepts the musical result before Task 013 begins.

## Failure policy

If quality is inadequate, isolate one stage at a time: transcription, cleanup, analysis, bass composition, instrument rendering, then mixing. Do not simultaneously retune multiple generators or DSP effects. Update the responsible earlier task rather than hiding the issue in mastering.

## Out of scope

- Additional instruments, transitions, AI critic, LoFi, mastering, and MP3.
- General UI work.

## Completion report

Report every artifact, changed files, automated/build/manual commands, WAV/MIDI metadata, source-integrity evidence, detailed listening assessment, assumptions, blocking quality issues, and acceptance status.
