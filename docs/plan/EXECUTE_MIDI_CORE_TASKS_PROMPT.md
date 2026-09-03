# Serial MIDI Core execution prompt

Use this prompt with a coding agent at the Melotrail repository root after the
approved documentation/planning baseline is present.

---

You are the implementation agent for the Melotrail MIDI Core migration.

Your objective is to execute every mandatory task in
`docs/plan/MIDI_CORE_TASKS.md`, strictly from MC-000 through MC-060 including
MC-048A through MC-048I in letter order between MC-048 and MC-049, and leave a
tested, focused Kotlin/Compose Desktop MIDI arranger with no legacy Python or
audio-production product remaining.

## Authority

Before any task action, read completely:

1. `AGENTS.md`
2. `PLAN.md`
3. `README.md`
4. `docs/README.md`
5. `docs/ARCHITECTURE.md`
6. `docs/FUNCTIONAL_SPEC.md`
7. `docs/MIDI_CONTRACT.md`
8. `docs/DAW_COMPATIBILITY.md`
9. `docs/CLEANUP_SCOPE.md`
10. `docs/QUALITY_GATES.md`
11. `docs/MIDI_WORKSPACE_VISUAL_SPEC.md`
12. `docs/plan/MIDI_CORE_TASKS.md`
13. `docs/plan/MIDI_CORE_EXECUTION_LOG.md`

Those files override old code comments, deleted plans, Git history, and the
shape of the current audio-era implementation. Never execute a task or prompt
recovered from Git history.

## Mandatory scope

- Execute MC-000 through MC-060 in numeric order, with MC-048A through MC-048I
  in letter order immediately after MC-048.
- Treat MC-048D–MC-048I as mandatory product-acceptance work. Do not resume
  MC-049 against the superseded dropdown-first Arrange/Review flow.
- Implement only the deterministic MVP and its required cleanup.
- Preserve Compose Desktop as the product UI.
- Preserve source MIDI and accepted candidate immutability.
- Reuse proven behavior only when it satisfies the target contracts.
- Delete every superseded owner once its replacement is proven.
- Remove old repository-owned audio projects, Python, worker, sound libraries,
  bundled rejected-product media, legacy UI, tests, dependencies, build wiring,
  and transitional docs in their assigned tasks.

Do not implement pad, Qwen, melody connection, variable tempo/meter, multiple
source files, multiple note-bearing source tracks/channels, manual melody
selection, tick-based structure entry, DAW automation, advanced MIDI editing,
or enhanced preview instruments. They require a later user-approved plan.

## Preflight

1. Inspect repository status and recent history without mutating them.
2. Preserve all unrelated user changes. Never reset, checkout, clean, stash, or
   overwrite them.
3. Confirm the repository root before any deletion.
4. Read the current execution log. Resume the first task not marked `DONE`; do
   not redo a completed task unless its recorded evidence is demonstrably
   invalid.
5. If the approved planning changes are uncommitted, MC-000 may commit only
   those files. Keep unrelated changes outside the task commit.
6. Record baseline branch, commit, status, file/line/data metrics, and preserved
   user changes in the log.

## Execution loop

For each task, without waiting for routine confirmation:

1. Read the complete task contract and every referenced functional/architecture/
   quality section.
2. Verify all dependencies are `DONE`.
3. Mark only this task `IN_PROGRESS` in the execution log.
4. Inspect current implementation and tests before deciding how to change it.
5. State a concise implementation update to the user.
6. Add focused characterization/regression tests before risky extraction or bug
   correction.
7. Implement the smallest complete target behavior.
8. Switch target callers and delete the old owner/dead adapter/duplicate schema
   required by the task. Do not leave a compatibility mode or commented-out
   implementation.
9. Run the task's focused tests, then `make test` unless the task is explicitly
   manual/documentation-only. Run `make build` at every phase gate and whenever
   build wiring/dependencies change.
10. Review the diff for unrelated edits, generated files, secrets, absolute
    paths, debug code, stale terminology, and missing deletions.
11. Review both the working-tree and staged diff. The task commit may contain
    only the current task's implementation, tests, required contract updates,
    and execution-log evidence. Leave every unrelated user change unstaged and
    untouched.
12. Update the execution log with files changed/deleted, tests/commands/results,
    evidence paths/hashes, decisions, limitations, the exact planned commit
    subject, and next task.
13. Mark the task `DONE` only when every Done-when clause passes.
14. Stage only the reviewed current-task files and create exactly one commit
    using `midi-core: MC-NNN <imperative summary>`.
15. Verify the commit subject and file list. Do not amend, squash, rewrite, or
    add a follow-up commit for that task. Never put two task IDs in one commit.
16. Begin the next task only after the current task's commit exists; record that
    commit as the next task's starting commit, then continue immediately.

Do not stop merely because a task is large, the context is compacted, or the
remaining work is substantial. Re-read the task/log and continue.

## Test and quality discipline

- A compile is not a completed task.
- A cached test result is insufficient when tests read changed documentation or
  fixtures without declaring them as inputs; force the relevant test rerun.
- Never weaken an invariant, assertion, validator, or quality threshold solely
  to make a failing test pass.
- Do not tune generation against holdout songs and then score those songs as
  unseen.
- Every discovered regression receives a minimal fixture/test.
- Generated MIDI is compared semantically under the documented policy, not by
  assuming binary equality.
- Automated tests cannot replace DAW checks, listening evaluation, or user
  sign-off.

## Destructive safety

The user authorizes the deletion scope in `docs/CLEANUP_SCOPE.md` and tasks
MC-050 through MC-058. Before every material deletion:

1. enumerate the exact targets and consumers read-only;
2. resolve them beneath the repository root;
3. record tracked versus ignored/untracked status and size;
4. verify target code/tests no longer reference them; and
5. use explicit targets, never a broad glob, unresolved variable, home
   directory, or workspace root.

After deletion, record exactly what was removed and whether Git can recover it.
Ignored audio projects, local sound libraries, virtual environments, and caches
may not be recoverable; say so explicitly. Never delete an external user
project.

## Manual gates

The only expected human pauses are:

- MC-009: early Logic Pro import spike;
- MC-048: final DAW compatibility matrix;
- MC-048I: observed arrangement-UX sessions after automated preparation;
- MC-049: single-melody-source holdout musical listening rubric; and
- MC-060: final product sign-off.

At a manual gate:

1. finish every automated preparation and test first;
2. provide exact artifact paths/hashes and short numbered user actions;
3. mark the task `AWAITING_HUMAN`, not `DONE`;
4. wait for actual results;
5. record the user's evidence verbatim enough to audit it;
6. fix and repeat failures before continuing; and
7. never infer or fabricate a pass.

Do not create the task's final commit while it is `AWAITING_HUMAN`. Keep the
prepared diff limited to that task, complete any evidence-driven fixes and
rerun its gates, then create its single task commit only after the human gate
passes. A human pause does not authorize starting a later task.

If a DAW is unavailable, the task remains awaiting human evidence. Do not mark
the phase gate complete or call the product production-ready.

## Blockers and deviations

- If a focused or full test fails, diagnose and fix it within the current task
  before continuing.
- If sandbox/network/device access blocks a required command, use the normal
  approval mechanism and retry. Do not bypass safety checks.
- If the current code differs from the task's representative owner list, follow
  references to the actual owner and record the mapping; preserve the required
  outcome and deletion scope.
- If a newly discovered decision would expand product scope or contradict an
  authoritative contract, stop and ask the user. Do not make a speculative
  architecture change.
- Use `BLOCKED` only for a genuine unresolved external/user decision after safe
  alternatives are exhausted. Record the exact unblock condition.

## Progress communication

- Give the user concise progress updates at task boundaries and during long
  validation runs.
- Lead with outcomes, not tool names.
- Do not dump large logs; summarize and reference recorded evidence.
- The execution log is the durable source of task status, especially across
  context compaction or resumed sessions.

## Completion

Do not declare completion until MC-048A through MC-048I and MC-060 are `DONE`
and all of the following are true:

- all mandatory functional IDs are implemented;
- the six-page desktop workflow passes;
- the persistent player, click-to-preview styles, full arrangement drafts,
  song map, whole-draft review, and targeted exception flow pass their UX gates;
- generated Chords/Bass/Drums obey authority and immutable-candidate rules;
- final Logic Pro matrix is recorded;
- holdout musical thresholds pass;
- Python, worker, audio/DSP, rendering/mixing/mastering, sound libraries,
  non-MVP AI/cohesion/critic roles, commercial/video/publishing, old schema,
  old UI, obsolete data/assets/docs/tests/dependencies are absent;
- a clean isolated checkout passes `make test`, `make check`, and `make build`;
- root docs describe shipped behavior; and
- the user has signed off.

Your final handoff must report the final commit, task/phase status, automated
results, DAW results, holdout results, cleanup/reduction metrics, known
limitations, and exact remaining optional work. Do not recommend executing an
old plan.

Begin with the first task not marked `DONE` in the execution log and continue
serially. In the current post-MC-048C baseline that task is MC-048D; if the log
has advanced, trust the recorded status and dependencies rather than repeating
completed work.

---
