# MIDI Core planning artifacts

The root [PLAN.md](../../PLAN.md) is the only active product roadmap.

All audio-era quality-pipeline, guided-arranger, optional-AI, YouTube, and old
execution documents have been superseded and removed. Git history is their
archive; none of their prompts or task IDs may be executed.

## Current state

- Product direction: accepted.
- Documentation baseline: accepted.
- Dependency-ordered implementation tasks: in progress; consult the execution
  log for the current task and manual gate.
- Serial agent execution prompt: ready.
- Execution log: active.

## Active execution suite

Read and use these files in order:

1. [MIDI Core tasks](MIDI_CORE_TASKS.md) — the core task sequence,
   ordered by dependency and cutover safety.
2. [Agent execution prompt](EXECUTE_MIDI_CORE_TASKS_PROMPT.md) — the standalone
   prompt for one implementation agent to execute the sequence.
3. [Execution log](MIDI_CORE_EXECUTION_LOG.md) — the task ledger, validation
   evidence, manual gates, cleanup ledger, and final sign-off record.
4. [MC-048I arrangement UX rubric](MC048I_ARRANGEMENT_UX_RUBRIC.md) — the
   anonymized observed-session procedure and validator contract required before
   MC-049.

### Mockup-faithful UI insertion (2026-09-05)

Root Plan 7.7 inserts this work after MC-048H and before MC-048I's final
observations; it does not create a competing product roadmap:

1. [UI redesign plan](UI_MOCKUP_REDESIGN_PLAN.md) — reference-by-reference
   adaptation, actual implementation gaps, measured design targets and gates.
2. [UI tasks](UI_MOCKUP_TASKS.md) — UI-000–UI-019, strictly sequential with one
   commit per completed task.
3. [UI execution prompt](EXECUTE_UI_MOCKUP_TASKS_PROMPT.md) — copy/paste prompt,
   bounded to the UI tasks and preserving all existing dirty work/manual gates.
4. [UI execution log](UI_MOCKUP_EXECUTION_LOG.md) — new task evidence ledger.
5. [Future video creator](FUTURE_VIDEO_CREATOR.md) — specified future function
   and VID-000–VID-006 proposals, not currently executable implementation tasks.

The nine `docs/pictures/UI` references are retained as design-only inputs.
Current MIDI-only runtime and legacy video cleanup remain unchanged. MC-048I's
prepared work stays pending until refreshed evidence and real sessions pass.

### Retained acceptance evidence

These are evidence/procedures for the active product, not obsolete roadmaps:

- [Desktop smoke checklist](MC040_DESKTOP_SMOKE_CHECKLIST.md)
- [MIDI audition smoke procedure and evidence](MC045_MIDI_AUDITION_SMOKE.md)
- [Bounded property-test evidence](MC047_PROPERTY_EVIDENCE.md)
- [Logic Pro compatibility matrix](MC048_DAW_MATRIX.md)
- [Arrangement UX observations rubric](MC048I_ARRANGEMENT_UX_RUBRIC.md)
- [Unseen-project holdout rubric](MC049_HOLDOUT_RUBRIC.md)

The 2026-09-05 documentation cleanup removes only obsolete audio-era guides
and their exclusive readers. No remaining plan is obsolete: the core suite
still owns acceptance/cleanup, and the UI/video suite owns the requested
redesign and gated future feature. Completed task records remain necessary
evidence; their presence never authorizes rerunning completed tasks.

The task specification maps every item to:

- root plan phase;
- functional requirement IDs;
- target architecture owner;
- files/packages to inspect;
- behavior to reuse;
- code/data/docs/tests/dependencies to delete;
- implementation and regression tests;
- automated and manual validation;
- commit boundary;
- evidence; and
- stop condition.

No task may defer old-code deletion indefinitely. Deletion occurs with the
replacement task when safe, or in the final destructive cleanup phase when it
depends on full cutover.

Execute tasks strictly from `MC-000` through `MC-060`, including `MC-048A`
through `MC-048I` in letter order between `MC-048` and `MC-049`, with the
UI-000–UI-019 insertion between MC-048H and MC-048I completion. Every task
must pass its own gate and receive exactly one task commit before the next task
begins.

Do not begin the optional enhancements in the root plan. Tasks that require
Logic Pro or human musical or usability review must stop at their named manual
gate and preserve all automated evidence already collected.
