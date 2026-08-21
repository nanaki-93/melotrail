# Task 123 — Per-track enhancement harmonic validation

## Goal

Align the existing per-track AI Enhance stage with the canonical harmonic
timeline and common mutation evidence without changing its musical purpose.

## Dependencies

Tasks 119, 121, and 122.

## Required changes

- Build `MusicalProcessingContext` through the part-enhancement projection. Keep
  profile, mood, notes, draft/approval, intensity, and report behavior, but
  eliminate competing project/harmony assembly.
- Resolve the active occurrence and chord for every proposed note edit. A pitch
  must be a project-scale tone and either a chord tone or an allowed passing tone
  that is shorter than half a beat and resolves by step to a chord tone no later
  than the next beat.
- Apply the shared melody identity. No anchor deletion or anchor pitch change is
  allowed. Preserve the current enhancement edit budget when it is stricter than
  the common invariant; never silently raise a shipped limit.
- Validate pitch range, note bounds, velocity, positive duration, collisions,
  tempo/meter preservation, and deterministic output before publication.
- Emit the shared mutation report plus the existing user-facing summary. Bind
  draft and approval to input, context, plan, output, and report hashes.
- Remove the first/last-note-only protection and superseded scale-only validation
  after migration.

## Failure behavior

Reject the complete candidate if any edit cannot be mapped to one occurrence or
active chord, exceeds a limit, touches an anchor illegally, or produces invalid
MIDI. Do not publish a partially accepted model plan.

## Tests

- Chord changes inside one occurrence validate against the correct bar.
- A short resolving scale passing tone is accepted; a sustained chord clash is
  rejected.
- Anchor, range, collision, timing, velocity, and budget boundary cases.
- Draft/approval becomes stale after any authority or selected-input change.
- Identical input/context/model plan yields identical MIDI and report.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Every pitch edit is explained by active harmony, not only global scale.
- Existing explicit preview, approval, retry, and bypass behavior remains.
- No source or previously approved MIDI is overwritten.

## Exclusions

Do not add whole-song criticism, arrangement generation, or audio Lo-fi effects.
