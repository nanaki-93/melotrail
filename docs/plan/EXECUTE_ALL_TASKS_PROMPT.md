# Prompt: execute the complete quality-pipeline roadmap

> **Historical—do not run.** QP-001–QP-018 are complete. Use
> [`EXECUTE_GUIDED_ARRANGER_TASKS_PROMPT.md`](EXECUTE_GUIDED_ARRANGER_TASKS_PROMPT.md)
> for new implementation work.

Copy the prompt below into a coding-agent session from the repository root. It
authorizes the task commits described here; it does not authorize pushing,
publishing, uploading, deleting user projects, or changing external services.

```text
You are implementing the complete Melotrail quality-pipeline roadmap, in order,
from QP-001 through QP-018.

Read completely before acting:
- AGENTS.md
- README.md
- PLAN.md
- docs/plan/README.md
- docs/plan/PLAN.md
- docs/plan/PROJECT_ANALYSIS.md
- docs/plan/QUALITY_GATES.md
- docs/plan/TASKS.md
- docs/plan/YOUTUBE_READINESS.md
- docs/plan/EXECUTION_LOG.md
- every current operational document linked by the selected task

Goal:
Complete every task in docs/plan/TASKS.md sequentially. At the end of each task,
run its required checks and create exactly one focused Git commit. Continue to
the next task only after that commit succeeds. Do not squash task commits.

Preflight:
1. Inspect `git status --short`, the current branch, recent commits, repository
   instructions, and the implementation/tests overlapping QP-001.
2. Require a clean working tree before beginning. If unrelated or pre-existing
   changes exist, stop and report their exact paths; do not stage, discard,
   stash, reset, or commit them.
3. Run `make test`, `make worker-test`, and `make build`. Record pre-existing
   failures. A failure does not authorize weakening a gate or deleting a test.
4. Read the entire QP-001 task section and identify its smallest safe file set.

For each task QP-NNN:
1. Confirm every earlier task is `Complete` in docs/plan/EXECUTION_LOG.md and its
   commit exists in `git log`. Do not skip tasks or implement a later task early.
2. Read the complete task contract, relevant production code, direct tests, and
   current operational docs. Inspect before changing architecture.
3. Run the smallest relevant baseline tests before editing.
4. Implement only the selected task and its direct migration/removal work.
   Preserve source/approved artifacts and all Melotrail musical invariants.
5. Add regression tests for the task's failure mode. Tests must remain offline;
   live Qwen, Basic Pitch, renderer, audio-device, packaging, and listening work
   is opt-in only where the task explicitly requires it.
6. Update affected KDoc/docstrings, operational docs, and the function
   documentation inventory in the same task. Remove only code/tests/docs proven
   superseded by this task. Git history is the archive.
7. Run focused tests, then every command named by the task. Also run
   `git diff --check` and `python3 tools/check_documentation_coverage.py --repository .`
   when production declarations changed.
8. Review the complete diff for unrelated edits, source mutation, stale fallback
   branches, unsafe paths, silent bypass, false-success states, missing hashes,
   and claims not supported by evidence.
9. Update only the selected row in docs/plan/EXECUTION_LOG.md to `Complete`, with
   the checks and concise evidence. Use `SELF` for the commit cell; it denotes
   the commit containing that row because a commit cannot contain its own hash.
10. Stage only explicit task files. Verify `git diff --cached --name-status` and
    ensure no unrelated path is staged.
11. Commit with the exact task commit subject specified in TASKS.md. The user has
    authorized these local task commits. Do not amend, rebase, push, tag, publish,
    or upload.
12. Record the resulting commit hash in your running report, verify the working
    tree is clean, then proceed to the next task.

Failure/blocking rules:
- If a task's acceptance criteria or required tests do not pass, do not commit
  partial implementation and do not continue. Keep the task `In progress` or
  `Blocked`, report exact evidence, and stop for user direction.
- Do not convert a model/renderer/worker/listening failure into a silent bypass.
- Do not reduce validation thresholds merely to make fixtures pass.
- Do not overwrite a known-good MIDI/audio candidate or delete project data.
- Do not make network policy checks during ordinary automated tests.
- Ask for new authority before any external publication, push, upload, purchase,
  account action, or destructive project-data operation.

Final closure after QP-018:
1. Run `make test`, `make worker-test`, and `make build` from the final tree.
2. Run documentation coverage, dangling-link/path searches, and `git diff --check`.
3. Verify there are exactly 18 ordered QP task commits after the plan baseline,
   unless a documented task was already complete before execution.
4. Report each task/commit, files changed, automated results, manual/listening
   evidence, unverified dependencies, and any policy/rights limitations.
5. Do not claim YouTube approval or guaranteed monetization.
```
