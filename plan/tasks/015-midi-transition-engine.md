# Task 015 — MIDI Transition Engine

## Goal

Connect adjacent section instances with deterministic, context-aware MIDI gestures instead of arbitrary generated audio.

## Dependencies

- Tasks 008, 013, and 014 provide bass, drums, and pad generation.
- Task 011 provides validated transition intents.
- Task 005 provides ending/starting chord context.

## Transition contract

The engine receives previous, current, and next section context and implements only:

```text
none
drum_fill
bass_walk
pad_sustain
build
drop
cymbal
```

`fade` and legacy audio `crossfade` remain audio-renderer compatibility behaviors; they are not new MIDI note generators in this task.

Each transition declares zero, one, or two bars. `none` and `drop` insert zero bars; other types follow explicit schema rules. The final section cannot request a transition to a nonexistent next section.

## Harmonic and timeline rules

- Read the previous section's final confident chord and next section's first confident chord.
- `bass_walk` may create a deterministic diatonic/chromatic approach only from supported harmony; otherwise degrade to `none` with a diagnostic.
- `pad_sustain` holds/revoices validated harmony without inventing an unrelated chord.
- `drum_fill` and `cymbal` use the registry drum map.
- `build` combines bounded activity from existing allowed instruments; `drop` removes/simplifies activity without inserting sound.
- Never edit source piano MIDI.
- Merge gestures into the applicable generated instrument MIDI track or a clearly named transition MIDI artifact while preventing collisions and hanging notes.
- Account for inserted bars in the global tick/frame timeline exactly once.
- Repeated generation must not accumulate duplicate transition events.

## Tests

- Every transition type and allowed duration.
- First, middle, and final boundaries.
- Compatible and incompatible/low-confidence harmony.
- `Am -> F` approach example.
- Tempo and meter changes at boundaries.
- Exact inserted-bar timeline offsets for all later sections.
- Collision handling with existing bass/drum/pad events.
- Invalid type, excessive bars, missing next section, and unavailable instrument rejection.
- Idempotent/deterministic regeneration.

Manual smoke test:

- Generate boundary-only preview renders for several transitions.
- Listen with two seconds of surrounding source context.
- Confirm the gesture prepares the next section, does not reorder material, and does not create timing drift.

## Acceptance criteria

- Transitions are generated as inspectable MIDI or deterministic track edits.
- Only allow-listed types/instruments are used.
- Harmonic transitions fail conservatively when analysis confidence is insufficient.
- Inserted time is represented consistently in MIDI, stems, and later mixing.
- Source piano identity and section order are preserved.

## Out of scope

- Arbitrary generated audio, riser samples, complex reharmonization, or AI-generated note events.
- Strings transitions.

## Completion report

Report transition algorithms and fallback rules, changed files, tests/build commands, preview artifacts/listening results, timeline evidence, assumptions, and remaining boundary limitations.
