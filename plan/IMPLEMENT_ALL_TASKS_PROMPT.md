# Personal AI Music Arranger — Sequential Task Implementation Prompt

Use this prompt with an implementation agent that has access to this repository.
It deliberately invokes `PROMPT_TEMPLATE.md` **once per task**: the template's
one-task boundary remains binding, so a single unrestricted implementation pass
must not blend tasks together.

```text
You are the delivery coordinator and implementation agent for the Personal AI
Music Arranger. Complete the active task contracts in `plan/tasks/` in their
dependency order. You must use `plan/PROMPT_TEMPLATE.md` as the exact
implementation procedure for *every individual task*.

Important scope rule: `PROMPT_TEMPLATE.md` says "implementing exactly Task
XXX" and "Do not continue into another task." Honor that rule literally.
Treat this request as a sequence of independent task runs, not permission to
implement several tasks in one diff or one verification report.

## Establish the task queue

1. Read these files completely before selecting any task:
   - `README.md`
   - `plan/AGENT_GUIDELINES.md`
   - `plan/PROMPT_TEMPLATE.md`
   - `plan/TASKS.md`
   - `plan/PLAN_UI_AND_CREATION.md`
2. Discover only active task contracts with:
   `rg --files plan/tasks -g '*.md' | rg '/[0-9]{3}-' | sort`
   Do not treat `plan/tasks/completed/` as work to redo.
3. Build a dependency-aware queue from each task's `Dependencies` section,
   beginning with the lowest numbered task that is both present and eligible.
   A dependency is eligible only when its acceptance criteria are demonstrably
   met in the repository and its task has been explicitly accepted/recorded.
   Never infer that a missing or merely planned dependency is complete.
4. The active checked-in sequence is expected to be Tasks 047–058, in this
   order, subject to the dependency gates in the task contracts:

   `047 -> 048 -> 049 -> 050 -> 051 -> 052 -> 053 -> 054 -> 055 -> 056
   -> generated 059+ P0/P1/retirement blockers -> 057 -> 058`

   Task 047 also depends on Tasks 032, 034, 039, 043, and 046; Task 054
   depends on Tasks 029–053. If any required task is unavailable or has not
   been accepted, do not guess, backfill it, or start its dependent task. Report
   the exact dependency and stop for direction.

## Execute one task at a time

For the next eligible task number `NNN`:

1. Start a fresh task run by applying the complete procedure in
   `plan/PROMPT_TEMPLATE.md`, replacing every `XXX` with `NNN`.
2. Read exactly the selected `plan/tasks/NNN-*.md` contract completely and make
   its Goal, Dependencies, Requirements, Tests, Acceptance criteria, and Out of
   scope binding. Preserve all existing user changes.
3. Implement only that task and its focused tests. Run its required checks and
   the template's verification steps. Do not absorb a later task, speculative
   cleanup, or unrelated failing test.
4. When every acceptance criterion passes, create the task commit required by
   `PROMPT_TEMPLATE.md`: stage only that task's explicit file set, inspect the
   staged diff, and commit using `Task NNN: <concise completed-task summary>`.
   Never stage all changes wholesale or include pre-existing/user changes. If
   any acceptance check was skipped or failed, do not commit, mark the task
   incomplete, and stop—do not advance the queue.
5. Before moving on, produce a task-closeout record containing: task number and
   acceptance result; commit hash and message (or why no commit was made);
   changed files; automated/manual commands and outcomes; source/artifact
   validation; assumptions; pre-existing failures; deferred work; and
   limitations.
6. Re-read `git status --short`, rediscover the queue, and then begin a new
   independent run for the next eligible task.

## Mandatory special handling

- Task 056 is read-only except for its dated report and generated task-contract
  files. It must not fix source code. It creates one narrow Task 059+ contract
  for each verified P0, P1, or retirement-blocking finding using
  `plan/BUG_TASK_TEMPLATE.md`.
- After Task 056, implement and accept every generated P0/P1/retirement-blocking
  Task 059+ using this same one-task process before starting Task 057. Leave
  P2/P3 findings deferred unless explicitly promoted.
- Start Task 057 only after Task 056 and every required generated blocker are
  accepted. Start Task 058 only after Task 057 is accepted.
- Do not delete the static frontend before Task 057. Do not claim package, OS,
  model, renderer, sound-library, or audio-device support without actually
  verifying it under the selected task's requirements.

## Final delivery condition

Finish only when every discovered active task is accepted, including all
Task-056-generated P0/P1/retirement-blocking tasks, and Task 058's final
verification record is complete. Otherwise return a concise blocked/incomplete
status naming the exact task, failed acceptance criterion, evidence, and the
smallest required next action.
```

## Current dependency note

The current repository contains active contracts 047–058. The earliest active
contract, Task 047, requires accepted Tasks 032, 034, 039, 043, and 046. Do
not begin Task 047 until that prerequisite state is established.
