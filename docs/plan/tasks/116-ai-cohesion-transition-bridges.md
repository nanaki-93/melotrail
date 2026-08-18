# Task 116 — AI Cohesion Transition Bridges

## Goal

Make Cohesion use the local AI to plan a smooth, musically compatible bridge
for every adjacent pair of Structure occurrences, then render and approve those
bridges through deterministic code.

## Dependencies

- Task 115 accepted.

## Requirements

- Refocus Cohesion on boundaries. Whole-track musical correction belongs to
  Task 113; Arrangement instrumentation belongs to the existing Arrangement
  stage. Retire or migrate overlapping Cohesion responsibilities only after
  preserving supported project reads.
- For each ordered adjacent pair, build a bounded, path-free model input with
  stable outgoing/incoming occurrence IDs and hashes, boundary note summaries,
  key/chord confidence, tempo, meter, PPQ, energy, and supported instrument
  availability. The final occurrence produces no boundary request.
- Require the local AI to return exactly one plan per expected boundary using a
  strict, versioned vocabulary owned by code. Plans may choose bounded bridge
  type, length, harmonic handoff, rhythmic pickup/fill, energy contour, and
  permitted tempo/meter handoff; they may not return raw paths, commands,
  plugins, arbitrary instruments, or unbounded MIDI.
- Validate full pair coverage, order, identities, hashes, limits, supported
  instruments, musical ranges, and no unknown fields before rendering.
- Use deterministic engines/adapters to produce project-confined transition
  MIDI and preview audio. Verify MIDI round-trip, note pairs, timing/meter map,
  no hanging notes or collisions, and alignment of both sides of each boundary.
- Persist one draft plan/result/audit/provenance record per boundary plus an
  aggregate Cohesion draft tied to the entire Structure input.
- Provide per-boundary A/B preview (hard join versus proposed bridge), a
  whole-sequence preview, rationale/diagnostics, regenerate/reject, and explicit
  aggregate approval. Approval requires every boundary to be current and
  reviewed; a one-occurrence structure may complete with an explicit empty
  Cohesion result.
- Treat model unavailability, invalid output, renderer failure, and unsupported
  musical evidence as truthful blocked/retry states. Do not claim that a bridge
  is smooth solely because files exist or silently approve a no-op fallback as
  AI Cohesion.

## Tests

- Cover zero, one, repeated, and many boundaries; exact `n - 1` coverage;
  reorder/staleness; and aggregate approval rules.
- Use adversarial fake-model responses for missing/duplicate/reordered pairs,
  stale hashes, unknown fields, paths/commands, unsupported instruments,
  invalid harmony, excessive length, and out-of-range values.
- Test deterministic bridge generation, idempotency, note validity, collisions,
  tempo/meter alignment, atomic publication, partial failure, and recovery.
- Test preview source selection, rejection/regeneration, whole-sequence preview,
  and downstream invalidation.
- Add a documented manual listening matrix covering same/different key,
  same/different tempo, sparse/dense boundaries, repeated parts, and Lo-fi
  selections; record observations without turning subjective review into a
  false automated guarantee.

## Acceptance criteria

- A multi-occurrence Structure cannot proceed until every adjacent boundary has
  one current, validated, previewed, explicitly approved AI Cohesion result.

## Out of scope

- Whole-track repair, automatic Structure changes, unbounded note generation,
  cloud models, or final arrangement instrumentation.
