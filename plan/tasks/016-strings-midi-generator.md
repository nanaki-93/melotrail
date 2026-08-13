# Task 016 — Deterministic Strings MIDI Generator

## Goal

Add conservative strings only after piano, bass, drums, pads, and transitions work reliably.

## Dependencies

- Tasks 013–015 pass automated and manual checks.
- Task 005 provides confident harmony and source melodic range.
- Task 011 supplies typed strings roles.

## Supported roles

```text
sustained_harmony
climax_reinforcement
long_notes
simple_countermelody
```

Avoid complex orchestration and expose no raw note interface to Qwen.

## Generation rules

- Write `midi/generated/strings.mid` on a registry/configured channel.
- Sustained roles reuse the deterministic chord parsing and voice-leading foundation from pads but occupy a distinct documented register.
- `climax_reinforcement` is active only in sections designated at/near the song-plan climax.
- `simple_countermelody` is permitted only when harmony confidence exceeds a stricter threshold and the source melodic range/density leaves space.
- Countermelody uses a small deterministic scale/chord-tone contour, bounded note count, step size, register, velocity, and duration.
- Density/energy affect activity and velocity within explicit limits.
- Avoid source-range collision where practical, generated-track overlaps, hanging notes, and transition conflicts.
- Fall back from countermelody to sustained harmony or silence when requirements are not met.

## Tests

- Exact output for every role.
- Climax-only activation.
- Harmony/register/voice-leading bounds.
- Countermelody qualification and conservative fallback.
- Dense/high-register source collision avoidance.
- Section and transition boundaries.
- Density/energy limits and deterministic output.
- Disabling strings leaves earlier generated MIDI unchanged.

Manual smoke test:

- Render strings in one development and one climax section.
- Confirm they reinforce rather than obscure the piano and do not play continuously without purpose.

## Acceptance criteria

- Strings are deterministic inspectable MIDI.
- Complex/countermelodic behavior is confidence-gated and bounded.
- Earlier accepted arrangements remain unchanged when strings are disabled.
- No arbitrary orchestration or model-supplied notes are accepted.

## Out of scope

- Articulation switching, orchestral sections, divisi, expressive automation, or learned counterpoint.

## Completion report

Report role algorithms and gates, changed files, tests/build commands, artifacts/listening observations, collision handling, assumptions, and remaining orchestration limitations.
