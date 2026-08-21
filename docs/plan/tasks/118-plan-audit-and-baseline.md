# Task 118 — Pipeline audit and executable baseline

## Goal

Turn the verified repository findings in `docs/plan/PLAN.md` into a durable
implementation baseline. This task is documentation and characterization only;
it must not change production behavior.

## Dependencies

None. This is the first task in the alignment sequence.

## Required work

- Create `docs/plan/music-context-audit.md` with a table for every declared or
  analyzed musical fact: owner, Kotlin type, serialization, producer, consumers,
  authority level, validation, and known duplication.
- Inventory the real input, output, approval/bypass, report, hashes, and
  invalidation behavior for AI Fix, per-track Enhance, MIDI Feel, Arrangement,
  generated roles, Cohesion, Humanization, render, mix, texture, and master.
- Record the current service order and separately record discrepancies in
  `StageId`, `WorkflowArtifactGraph`, project workflow references, readiness,
  `WorkflowReadModel`, build/render input resolution, and desktop navigation.
- Explicitly capture these starting findings: canonical v4 settings/harmony
  already exist; AI Fix re-infers harmony; Cohesion v5 owns `songEdits`;
  Humanization is deterministic; existing arrangement criticism is pre-generation;
  canonical artifacts make duplicate numbered history unnecessary.
- Run and record the baseline results for `./gradlew test :desktopApp:test`.
  Record pre-existing failures without changing unrelated code.
- Link the audit from `docs/plan/PLAN.md` and the task index. Do not rewrite
  older completed-task evidence to make it appear current.

## Tests and evidence

- Verify every named class/path in the audit with `rg`; do not rely on the old
  planning documents where code disagrees.
- Check every audit row has at least one evidence path or is explicitly marked
  absent.
- Verify the documented order matches application services and not just UI copy.

## Acceptance criteria

- A new engineer can identify the authoritative and descriptive musical data
  without rediscovering the repository.
- Every current MIDI mutation has an owner and its approval, budget, report, and
  validation status are documented.
- All known plan/implementation mismatches have an owner in Tasks 119–130.
- No production source, serialized schema, or runtime behavior changes.

## Exclusions

Do not create the canonical context, alter stage identifiers, or fix any audited
defect in this task.
