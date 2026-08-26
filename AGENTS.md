# Melotrail Agent Instructions

## Product authority

Melotrail is a local desktop MIDI-arrangement companion for musicians who finish
their work in Logic Pro or GarageBand. The active product direction is defined
by `PLAN.md`; the target architecture and behavior are defined under `docs/`.

Before changing the project, read:

- `PLAN.md`
- `README.md`
- `docs/README.md`
- `docs/ARCHITECTURE.md`
- `docs/FUNCTIONAL_SPEC.md`
- `docs/MIDI_CONTRACT.md`
- `docs/DAW_COMPATIBILITY.md`
- `docs/CLEANUP_SCOPE.md`
- `docs/QUALITY_GATES.md`
- `docs/plan/MIDI_CORE_TASKS.md`
- `docs/plan/MIDI_CORE_EXECUTION_LOG.md`

## Target architecture

- Kotlin/JVM owns the domain, orchestration, MIDI processing, persistence, and
  Compose Desktop UI.
- MIDI is the only musical interchange and generated-song representation.
- The desktop UI remains a first-class tool; this is not a headless-only
  rewrite.
- Logic Pro and GarageBand perform instrument selection, audio rendering,
  mixing, mastering, and release production.
- Python, audio ingestion, transcription, DSP, rendering, mastering, video,
  publishing, and commercial-release workflows have no place in the target
  architecture.
- Qwen may later propose constrained musical choices, but it is not part of the
  deterministic MVP and never owns project authority.

## Musical invariants

- Project key, tempo, meter, structure, and section harmony are authoritative.
- Chromatic authoritative chords are valid; key compatibility is advisory.
- The selected source melody is immutable.
- Optional melody-connection edits are separate, versioned candidates and
  require explicit approval.
- Never silently overwrite an imported MIDI file, accepted candidate, or export
  snapshot.
- Generated roles must be validated against authoritative harmony and protected
  melody anchors.
- Regeneration is targeted by section and role.
- Candidate generation is deterministic for the same inputs, settings, and
  seed.

## Development rules

- Inspect existing behavior and tests before extracting reusable MIDI logic.
- Reuse proven MIDI, harmony, pattern, artifact, and validation behavior where
  it fits the new contracts.
- Replace behavior behind the new architecture before deleting its old owner.
- Once a replacement is proven, delete the old branch; do not keep compatibility
  modes, dead adapters, duplicate schemas, or archived source trees.
- Old audio projects do not require migration and may be deleted by the cleanup
  tasks after their exact repository-owned locations are verified.
- Make small, reviewable changes and add regression tests for every fixed bug.
- Preserve original MIDI inputs and accepted candidates.
- Do not introduce a Python service or an audio-production dependency.

## UI rules

- Preserve Compose Desktop as the primary interaction surface.
- Keep the UI focused on Project, MIDI, Structure & Harmony, Arrange, Review,
  and Export.
- Audition is MIDI playback, not an audio-rendering pipeline.
- Remove Mix/Master, sound-library, video, publishing, and release pages when
  their old runtime owners are removed.

## Validation

Run the applicable automated gates:

```bash
make test
make build
```

For MIDI export or workflow changes, also complete the manual Logic Pro and
GarageBand checks in `docs/DAW_COMPATIBILITY.md` and record the evidence
required by `docs/QUALITY_GATES.md`.

The current repository is in transition and still contains obsolete worker and
audio targets. They are deletion scope, not target validation requirements.
