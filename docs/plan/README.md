# MIDI Core planning artifacts

The root [PLAN.md](../../PLAN.md) is the only active product roadmap.

All audio-era quality-pipeline, guided-arranger, optional-AI, YouTube, and old
execution documents have been superseded and removed. Git history is their
archive; none of their prompts or task IDs may be executed.

## Current state

- Product direction: accepted.
- Documentation baseline: being reviewed.
- Dependency-ordered implementation tasks: not yet generated.
- Serial agent execution prompt: not yet generated.
- Execution log: not yet started.

## Files to add after documentation approval

The next planning step will add:

1. one mandatory task specification ordered by dependency and cutover safety;
2. one serial agent prompt that executes only that specification; and
3. one execution/evidence log template.

The task specification must map every item to:

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
