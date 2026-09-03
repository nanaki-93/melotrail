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

1. [MIDI Core tasks](MIDI_CORE_TASKS.md) — the only executable task sequence,
   ordered by dependency and cutover safety.
2. [Agent execution prompt](EXECUTE_MIDI_CORE_TASKS_PROMPT.md) — the standalone
   prompt for one implementation agent to execute the sequence.
3. [Execution log](MIDI_CORE_EXECUTION_LOG.md) — the task ledger, validation
   evidence, manual gates, cleanup ledger, and final sign-off record.

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
through `MC-048I` in letter order between `MC-048` and `MC-049`. Every task
must pass its own gate and receive exactly one task commit before the next task
begins.

Do not begin the optional enhancements in the root plan. Tasks that require
Logic Pro or human musical or usability review must stop at their named manual
gate and preserve all automated evidence already collected.
