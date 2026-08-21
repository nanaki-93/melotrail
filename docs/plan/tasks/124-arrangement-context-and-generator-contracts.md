# Task 124 — Arrangement context and deterministic generator contracts

## Goal

Make arrangement planning and all MIDI generators consume the same canonical
occurrence/harmony projection while preserving the AI-planner/Kotlin-executor
boundary.

## Dependencies

Tasks 119–123.

## Required changes

- Build Qwen arrangement input only from the arrangement projection: stable
  occurrences, selected melody evidence, canonical key/scale/tempo/meter,
  per-occurrence chords, profile/mood, energy/density analysis, and current input
  hashes.
- Keep model output at `SongPlan`/`DetailedArrangement` level. Strictly allow-list
  roles, registered instrument IDs, section/occurrence IDs, density/energy
  values, patterns, and generator parameters. The model cannot emit executable
  code, paths, raw MIDI bytes, or arbitrary instruments.
- Require every detailed section to map exactly once to a current structure
  occurrence. Reject omissions, duplicates, unknown IDs, reordered identity, or
  harmony/settings echoed differently from canonical values.
- Supply each deterministic generator a role-specific projection with occurrence
  tick bounds, harmonic timeline, arrangement intent, resolved instrument range,
  seed, and input/context hashes.
- Bind approved arrangement and generated MIDI evidence to arrangement plan,
  authority, registry, generator version, seed, output, and validation-report
  hashes.
- Keep `ArrangementCritic` as a pre-generation plan critic. Rename it to
  `ArrangementPlanCritic`, or document/deprecate an adapter if a direct rename
  would break supported callers; it must not be used as the post-Cohesion critic.

## Tests

- Repeated Verse occurrences retain distinct plan/generator inputs.
- Unknown instruments/roles/occurrences and missing sections are rejected.
- Canonical harmony cannot be overridden by model output.
- Same projection, generator version, and seed yields byte-identical MIDI.
- Arrangement or registry changes invalidate generated roles and descendants.
- Compatibility tests cover retained serialized arrangement evidence.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- AI chooses musical intent; deterministic Kotlin owns note generation.
- Every generated note is attributable to a current occurrence, harmony window,
  plan, generator version, and seed.
- No second instrument registry or context builder is introduced.

## Exclusions

Do not add new instruments, generators, or arrangement roles.
