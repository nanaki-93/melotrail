# Task 068 — Fixed 80 BPM Lo-fi MIDI Feel

## Goal

Add an optional stage after Repair MIDI that creates a derived melody with a
fixed 80 BPM tempo and a bounded lo-fi swing feel.

## Dependencies

- Task 067 accepted.

## Version 1 profile

- Profile ID: `lofi-80-swing-v1`
- Target tempo: exactly 80 BPM
- Swing: a single named, code-owned bounded value; use 58% eighth-note swing
  unless listening acceptance finds it musically unsafe, in which case changing
  the value requires a versioned profile rather than silently changing v1.

## Requirements

- Present a clear choice after repaired MIDI approval: `Original feel` or
  `Lo-fi feel · 80 BPM + swing`.
- Read only approved repaired MIDI and write a separate derived artifact under a
  versioned project-relative path. Never replace raw or repaired MIDI.
- Normalize the tempo map to 80 BPM while preserving time-signature events and
  musical bar/beat positions.
- Apply swing deterministically to eligible off-beat eighth-note positions.
  Preserve downbeats, note ordering, positive duration, note pairing, and legal
  MIDI bounds; prevent new same-pitch collisions.
- Use bounded velocity shaping only if explicitly included in the versioned
  profile. Do not add vinyl noise, filtering, saturation, or other audio DSP.
- Write a report with profile/version, input/output hashes, previous tempo map,
  output tempo, moved-note count, maximum shift, collision repairs, and warnings.
- Allow repaired/lo-fi A/B preview at matched monitor volume.
- Persist the selected canonical analysis input. Switching the choice invalidates
  analysis and all downstream artifacts but not repair evidence.
- Keep the existing final audio effect but rename it in UI and metadata to
  `Lo-fi audio texture` so it cannot be confused with MIDI Lo-fi Feel.
- Design the request/schema so future profiles can expose tempo and intensity,
  but do not expose variable controls in this task.

## Tests

- Deterministic MIDI tests across PPQN values, tempo changes, time signatures,
  chords, sustain, boundary notes, collisions, and repeated runs.
- Assert output tempo is 80 BPM and the named swing maps eligible offbeats to
  the expected ticks without moving downbeats.
- Test artifact/report atomicity, validation, stale detection, and source hashes.
- View-model and Compose tests for opt-in/off, A/B selection, invalidation, and
  distinct MIDI-feel versus audio-texture terminology.
- Manual listening comparison using representative straight and expressive
  melodies; document the selected v1 swing value.

## Acceptance criteria

- Lo-fi Feel is visible immediately after approved MIDI repair.
- Enabling it creates a valid, deterministic 80 BPM swung MIDI artifact while
  leaving raw and repaired MIDI unchanged.
- Disabling it restores repaired MIDI as the analysis input.
- MIDI Lo-fi Feel and final lo-fi audio texture are never presented as the same
  operation.

## Out of scope

- User-adjustable tempo, swing, humanization, or intensity.
- Random timing changes.
- Audio DSP or mastering.
- AI cohesion or melody patching.

