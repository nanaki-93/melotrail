# Task 130 — Reference-song integration, migration, and rollout

## Goal

Prove the aligned pipeline end to end, finish compatibility cleanup, and make
operational documentation match the supported product.

## Dependencies

Tasks 118–129.

## Reference fixture

- Add a code-generated deterministic reference project; do not commit opaque
  binary output fixtures when a Kotlin fixture builder can express them.
- The song contains A minor, 4/4, fixed tempo, Verse–Chorus–Verse occurrences,
  repeated Verse identity, distinct section progressions, one imported melody,
  all supported generated roles, at least two boundaries, and seeded processors.
- Provide fixed strict fake-model responses for AI Fix, per-track Enhance,
  Arrangement, Cohesion, and Full-Song Enhance. Automated tests stay offline.
- Exercise source/selected MIDI through authority, analysis, structure,
  arrangement, generation, boundary Cohesion, critic, enhancement selection,
  Humanization, and render-input resolution. Audio rendering itself may use the
  existing fake renderer boundary.

## Required scenarios

- Approved enhancement followed by deterministic Humanization.
- Explicit enhancement bypass followed by Humanization.
- Critic with no actionable issues producing `NO_OP` without a model call.
- Rejection of a stale enhancement plan after harmony, structure, selected MIDI,
  arrangement, or generated-role change.
- Recovery after an interrupted/failed run without selecting partial output.
- Supported legacy project read with old Cohesion evidence marked stale and
  explicit atomic migration/regeneration.
- Reproducibility: same project, fake responses, policies, versions, and seeds
  produce identical selected MIDI/report hashes after project relocation.

## Cleanup and documentation

- Remove superseded runtime paths, exclusive tests/fixtures, dead DTOs, prompts,
  readiness branches, and UI copy identified by Tasks 118–129 once supported
  compatibility readers no longer need them.
- Update `README.md` and relevant files under `docs/plan/` to the canonical order,
  shared authority, boundary-only Cohesion, explicit Full-Song Enhance, and
  deterministic Humanization behavior.
- Remove claims that Spring is a supported/optional product surface and keep the
  Python worker described as a separate stateless HTTP worker.
- Ensure the task index and implementation prompt point to `docs/plan/PLAN.md`
  and `docs/plan/tasks/` and contain no dead completed-task links.

## Verification

- Run `./gradlew test :desktopApp:test :desktopApp:build`.
- Run worker tests only if worker code changed.
- Review the final repository for duplicate context/timeline/metric builders,
  obsolete whole-song Cohesion edits, ordinal stage ordering, path-based
  completion, absolute-path persistence, and unbounded model output.
- Perform one desktop smoke through the new Critic/Enhance approval and bypass
  paths; record any optional model/audio dependency not available locally.

## Acceptance criteria

- The deterministic integration suite proves the canonical stage order and
  artifact lineage through Humanization/render selection.
- Supported legacy reads are preserved and obsolete artifacts cannot satisfy new
  completion rules.
- Operational docs, task contracts, UI terminology, and runtime behavior agree.
- No unrelated product surface or infrastructure is introduced.

## Exclusions

Do not add cloud deployment, telemetry, a plugin system, a DAW editor, new
instruments, or audio-domain AI enhancement.
