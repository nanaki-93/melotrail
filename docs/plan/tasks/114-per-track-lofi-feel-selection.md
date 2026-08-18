# Task 114 — Per-Track Lo-fi Feel Selection

## Goal

Apply the optional Lo-fi tempo/rhythm transformation to each part before
analysis and Structure, using either cleaned MIDI or approved AI-fixed MIDI as
its exact input.

## Dependencies

- Tasks 110, 112, and 113 accepted.

## Requirements

- Resolve the current base MIDI through Task 110's selection boundary; never
  assume cleaned MIDI when an approved AI-fixed version is selected.
- Offer **Keep current feel** and **Apply Lo-fi Feel** per part. Keep the profile
  code-owned and versioned; the current 80 BPM/58% swing profile may be reused
  unless product requirements deliberately replace it.
- Preserve note identity and source time-signature events while changing only
  the profile's allowed tempo and rhythmic placement. Continue to bound shifts
  and repair only collisions introduced by the transformation.
- Publish a derived MIDI and report bound to the selected base hash. Allow A/B
  preview and a reversible return to the base version.
- Analyze only the selected final MIDI (base or Lo-fi). A change to the AI-fix
  selection or Lo-fi selection must invalidate analysis and all downstream
  artifacts.
- Reuse one selected per-part Lo-fi artifact for repeated Structure
  occurrences. Do not run a song-wide post-Structure Lo-fi MIDI pass.
- Keep MIDI Lo-fi Feel terminology distinct from the optional post-mix Lo-fi
  audio texture.

## Tests

- Cover cleaned and approved-AI inputs, keep/apply/revert selection, repeated
  occurrences, stale base hashes, odd PPQ, tempo maps, meter preservation,
  collision bounds, and output round-trip.
- Assert source/raw/cleaned/AI-fixed artifacts do not change.
- Test that analysis resolves exactly the selected output and that changes
  invalidate Structure-dependent work.
- Add UI tests for per-track choice, A/B preview, terminology, and recovery.

## Acceptance criteria

- Every part reaches analysis with exactly one current MIDI selection, and the
  same reviewed Lo-fi result is reused wherever that part occurs in Structure.

## Out of scope

- Occurrence-specific bridges, post-mix DSP texture, arbitrary groove editing,
  or changing Structure itself.
