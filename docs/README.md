# Melotrail documentation

This directory describes one product: the Kotlin/Compose Desktop MIDI arranger
defined by the root `PLAN.md`.

## Read order

1. [Product README](../README.md) — product introduction and current transition
   status.
2. [Root plan](../PLAN.md) — the only active roadmap and delivery sequence.
3. [Architecture](ARCHITECTURE.md) — target ownership, components, data flow, and dependency
   rules.
4. [Functional specification](FUNCTIONAL_SPEC.md) — numbered product functions and user-visible
   acceptance behavior.
5. [MIDI contract](MIDI_CONTRACT.md) — supported Standard MIDI input, semantic model,
   validation, and output package.
6. [DAW compatibility](DAW_COMPATIBILITY.md) — Logic Pro boundary and manual
   checks.
7. [Quality gates](QUALITY_GATES.md) — automated, musical, UI, and DAW acceptance
   gates.
8. [Cleanup scope](CLEANUP_SCOPE.md) — explicit keep, refactor, delete, and data-disposition
   decisions.
9. [MIDI workspace visual specification](MIDI_WORKSPACE_VISUAL_SPEC.md) — focused desktop visual
   language and visual acceptance rules.
10. [Troubleshooting](TROUBLESHOOTING.md) — target build, project, MIDI, audition, and export
   recovery guidance.

## Planning directory

[Planning index](plan/README.md) owns the active execution suite. The suite now
contains the dependency-ordered [MIDI Core tasks](plan/MIDI_CORE_TASKS.md), the
[serial agent prompt](plan/EXECUTE_MIDI_CORE_TASKS_PROMPT.md), and the
[execution log](plan/MIDI_CORE_EXECUTION_LOG.md). The
[MC-048I arrangement UX rubric](plan/MC048I_ARRANGEMENT_UX_RUBRIC.md) records
the required anonymized observed-session evidence. Consult the log for the
current serial-execution status and manual gate.

Root Plan 7.7 now inserts the [mockup-faithful UI plan](plan/UI_MOCKUP_REDESIGN_PLAN.md),
[20 sequential UI tasks](plan/UI_MOCKUP_TASKS.md),
[UI execution prompt](plan/EXECUTE_UI_MOCKUP_TASKS_PROMPT.md), and
[UI log](plan/UI_MOCKUP_EXECUTION_LOG.md) before MC-048I's final observations.
The [future video creator](plan/FUTURE_VIDEO_CREATOR.md) is a specification and
gated backlog only; it adds no current video feature or dependency.

The old quality-pipeline and guided-arranger suites have been removed because
they describe a rejected audio-production product. Git history is their
archive; no old plan or prompt is executable.

## Retained build contract and visual evidence

The obsolete audio import/workflow, commercial-policy, compatibility-reader,
and Spring-retirement documents were removed on 2026-09-05 together with their
exclusive documentation readers. Git history is their archive; do not restore
them to satisfy old guide tests. Exact disposition is in
[Cleanup scope](CLEANUP_SCOPE.md#56-documentation-and-planning).

These files still serve a current purpose:

- [Function documentation inventory](FUNCTION_DOCUMENTATION_INVENTORY.md) and
  [its JSON](FUNCTION_DOCUMENTATION_INVENTORY.json): required by the current
  Gradle documentation-coverage check. MC-058 removes them with that wiring;
  they are not product authority.
- `pictures/UI/`: nine retained design references for the requested redesign
  and future video specification; never runtime assets or normal test goldens.
- `pictures/App-pages.png`: legacy executable visual-test fixture, retained
  until MC-051 removes its readers. It is not a target design reference.
- `checks/`: recorded Logic Pro import evidence, indexed by the
  [DAW matrix](plan/MC048_DAW_MATRIX.md); retain it with the acceptance record.

The worker's local README remains with its still-present teardown owners until
MC-054 removes the worker. It is not MIDI Core setup guidance. No old plan or
compatibility runtime is reauthorized by these temporary build dependencies.

## Documentation ownership

- Product boundary and user promise: `README.md` and `FUNCTIONAL_SPEC.md`.
- Delivery order and gates: root `PLAN.md`.
- Technical ownership: `ARCHITECTURE.md`.
- File/event compatibility: `MIDI_CONTRACT.md` and `DAW_COMPATIBILITY.md`.
- Deletion decisions: `CLEANUP_SCOPE.md`.
- Test evidence: `QUALITY_GATES.md`.
- Executable work and evidence: the files indexed by `plan/README.md`.

Do not duplicate a contract across several documents. Link to its owner and
state only the local consequence.

## Documentation rules

- Describe target behavior as target behavior until it is implemented.
- Never present the superseded audio runtime as an active product option.
- Never claim DAW compatibility without recording the manual matrix version and
  result.
- Update the functional ID and its owning test when behavior changes.
- Remove transition documents when their last executable reader is removed.
- Prefer deletion to historical folders; Git contains the history.
- Keep links relative and run the documentation link audit with documentation
  changes (`DocumentationIntegrityTest`, also included in `make test`).
