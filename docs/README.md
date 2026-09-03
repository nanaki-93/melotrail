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
[execution log](plan/MIDI_CORE_EXECUTION_LOG.md). Consult the log for the
current serial-execution status.

The old quality-pipeline and guided-arranger suites have been removed because
they describe a rejected audio-production product. Git history is their
archive; no old plan or prompt is executable.

## Transitional contracts

The implementation has not yet completed the MIDI Core migration. A few old
documents and image fixtures remain at stable paths because current source or
tests read them directly:

- `MIDI_IMPORT_PROCESS.md`
- `TRACK_PROCESS_WORKFLOW.md`
- `COMMERCIAL_PROVENANCE.md`
- `COMPATIBILITY_READERS.md`
- `SPRING_API_RETIREMENT.md`
- `FUNCTION_DOCUMENTATION_INVENTORY.md`
- `FUNCTION_DOCUMENTATION_INVENTORY.json`
- `pictures/`

They are not product authority. Each must be deleted together with the code,
tests, or build wiring that owns its path. Keeping a transitional contract does
not authorize a compatibility implementation in the new architecture.

The UI images are a temporary visual-language reference only. The target design
uses their calm dark workstation hierarchy, not their audio-production pages or
their product branding. MC-048B replaces them with target MIDI-only visual
fixtures; MC-051 deletes the old image set only after those fixtures pass.

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
  changes.
