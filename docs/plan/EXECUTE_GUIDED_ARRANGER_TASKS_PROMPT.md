# Prompt: execute the guided-arranger roadmap serially

Commit these planning documents first, then copy the prompt below into a coding
agent session opened at the Melotrail repository root. It authorizes the 76
local task commits in GA-001A through GA-014I. It does **not** authorize pushing,
publishing, uploading, purchases, external account actions, destructive cleanup,
or the frozen optional-AI phase.

```text
You are the primary implementation agent for Melotrail's guided-arranger MVP.

Objective:
Complete every mandatory task in
docs/plan/GUIDED_ARRANGER_TASKS.md, in its exact written order, from GA-001A
through GA-014I. Implement one task at a time, validate it, and create exactly
one focused local Git commit using that task's exact commit subject. Continue
automatically between ordinary tasks. Stop only for a documented human
checkpoint, a failed gate, missing authority/evidence, or an unsafe dirty-tree
condition.

Authority, in order:
1. The user's current instructions and AGENTS.md.
2. Root PLAN.md for product direction, invariants, and gates.
3. docs/plan/GUIDED_ARRANGER_TASKS.md for ordered implementation scope.
4. docs/plan/GUIDED_ARRANGER_PHASE_0.md for GarageBand handoff.
5. Current operational docs for behavior that has not yet been cut over.

The old QP roadmap, docs/plan/TASKS.md, and
docs/plan/EXECUTE_ALL_TASKS_PROMPT.md are historical evidence. Never execute
QP-001 through QP-018 from this prompt.

Read completely before acting:
- AGENTS.md
- README.md
- PLAN.md
- every file under docs/
- docs/plan/GUIDED_ARRANGER_PHASE_0.md
- docs/plan/GUIDED_ARRANGER_TASKS.md
- docs/plan/GUIDED_ARRANGER_EXECUTION_LOG.md
- the implementation, tests, resources, Make targets, and operational docs
  directly overlapping the first pending task

Authorization boundaries:
- The user authorizes the local code/document/test changes and one local commit
  per mandatory GA task.
- Do not amend, squash, rebase, reset, stash, tag, push, publish, upload, or
  change an external service/account.
- Do not run git clean, destructive checkout/reset, broad rm, Make clean, or any
  command that removes projects, sources, data/audio/input, local-fixtures,
  accepted outputs, or known-good artifacts.
- Do not add a large/user-owned GarageBand binary to Git. A small diagnostic
  binary requires the explicit GA-002F rights/provenance gate and a narrow
  allow-list; never use git add -f merely to bypass ignore rules.
- Do not start docs/plan/GUIDED_ARRANGER_OPTIONAL_AI_TASKS.md. It requires a
  separate explicit user request after GA-014 and the human MVP gate.

Agent coordination:
- Only this primary agent may edit, stage, or commit in the shared worktree.
- Read-only subagents may audit bounded areas or review a diff. They must not
  edit files, run competing mutating commands, stage, or commit.
- Tasks are serial. Do not implement or scaffold a later task while the current
  task is active.

Initial preflight:
1. Inspect git status --short, current branch, HEAD, recent commits, repository
   instructions, and ignored Phase 0 readiness without modifying anything.
2. Require a clean tracked/untracked working tree. If any change exists, report
   every path and stop. Do not absorb planning edits into GA-001A, discard them,
   or stash them. The planning documents must already be committed.
3. Confirm root PLAN.md names the guided arranger as active and the detailed task
   file contains 76 mandatory GA rows matching the execution ledger.
4. If the ledger's planning-baseline field is UNSET, remember the current HEAD;
   GA-001A will record that parent hash in its own commit. If already set, verify
   that commit exists and is an ancestor of HEAD.
5. Run make test, make worker-test, and make build. Any failure stops execution.
   Do not weaken a test/gate or call it harmless without user direction.
6. Locate the first Pending task and confirm all earlier task commits and human
   dependencies are current.

For each GA task:
1. Confirm the previous GA row is Complete and its exact-subject commit exists.
   Confirm every named human dependency is explicitly satisfied and hash-current.
2. Read the entire selected task contract, parent PLAN section, production code,
   direct/adjacent tests, resource schemas, and operational docs. Inspect before
   changing architecture.
3. Mark only this task In progress in the working ledger. Do not commit yet.
4. Run the smallest focused baseline tests that exercise the selected seam.
5. Implement only the selected task and its direct migration/removal. Preserve:
   - project key, tempo, meter, bars, structure, and harmony authority;
   - source and approved artifact bytes;
   - approved semantic melody identity;
   - project-relative paths, SHA-256 lineage, atomic publication, and stale
     invalidation;
   - useful intermediate MIDI/audio in debug mode.
6. Add regression tests for all acceptance and failure cases named by the task.
   Ordinary tests remain offline/deterministic. Live Basic Pitch, renderer,
   GarageBand files, audio devices, and listening run only in the opt-in task
   that owns them.
7. Update affected KDoc/docstrings, operational docs, schemas, Make help, and
   function-documentation inventory in the same task. Remove replaced code only
   when this task owns the cutover and replacement coverage is green.
8. Run focused tests. Then run all of:
   - make test
   - make worker-test
   - make build
   - git diff --check
   - python3 tools/check_documentation_coverage.py --repository . when
     production declarations or documentation coverage changed
9. Review the complete diff for unrelated edits, source mutation, missing
   hashes, absolute/unsafe paths, stale fallbacks, silent bypass, fake approval,
   unsupported quality claims, secrets, unexpected/generated binaries, and
   files belonging to a later task.
10. Change only the selected execution-ledger row to Complete, put SELF in its
    commit field, and record concise checks/evidence. For GA-001A, also set the
    planning-baseline field to the preflight HEAD.
11. Stage explicit task paths only. Never use git add -A or git add . Review:
    - git diff --cached --name-status
    - git diff --cached
    - git diff --cached --check
    - staged file sizes/types
12. If the staged diff contains an unrelated path, user asset, large/generated
    binary, secret, incomplete migration, or unverified human claim, unstage only
    the explicit mistaken path without discarding it, report the problem, and
    stop if it cannot be resolved safely.
13. Commit with the task's exact subject. The user has authorized this local
    commit. Do not amend an earlier task or combine two task IDs.
14. Verify the commit subject and files, record the resulting SHA in the running
    report, and require a clean worktree before selecting the next task.

Human checkpoints:
- H0-01 through H0-05, H3-01, H4-01, and H5-01 are blocking.
- The preceding implementation task must be committed and the tree clean before
  stopping for a checkpoint.
- Print exactly what the user must create/listen to, exact local paths/commands,
  required manifest/review fields, and how to resume.
- Do not create an empty checkpoint commit.
- Do not write or infer reviewer/listener identity, date, device, rating,
  GarageBand action, color, approval, rejection, or comments.
- Automated work may publish PENDING_HUMAN_REVIEW only. File existence, hashes,
  metrics, passing tests, or an agent's listening opinion are not approval.
- On resume, validate the user-supplied evidence and its hashes/freshness. The
  next evidence-recording GA task commits it. A rejection returns to the owning
  task area and never lowers a threshold.

Conditional/import rules:
- CAF embedded-MIDI extraction follows only the documented CAF chunk contract.
- AIFF embedded-MIDI extraction exists only if GA-002D/G evidence approves a
  stable contract. Otherwise implement the explicit audio-transcription route.
- Never scan arbitrary bytes for MThd, silently fall back from extraction to
  transcription, or describe transcription as exact conversion.
- Direct/embedded MIDI bypasses Basic Pitch. Audio transcription consumes only
  the canonical decoded WAV and stops at MELODY_DRAFT.
- No test/live workflow can invent reviewer=user or auto-approve a draft.

Failure and resume rules:
- If acceptance or any required check fails, do not commit and do not begin a
  later task. Leave the selected row/working changes visible, report exact
  evidence, and stop for user direction.
- Do not discard or reset failed-task work. A later resume is allowed only when
  the user explicitly says to resume that named task and confirms those dirty
  paths are retained work from it.
- On dirty-tree resume, audit every path against the named task before editing.
  If changes are mixed, unrelated, or cannot be attributed safely, stop.
- Missing GarageBand assets, human review, renderer/model, licensed electric
  piano, or extraction certainty is a real block, not permission for a bypass.
- Do not edit the active task contract to make implementation easier. If the
  plan is internally inconsistent or unsafe, stop and request a planning change.

Phase gates:
- Re-run the phase-specific gate in GUIDED_ARRANGER_TASKS.md after the final
  task in each phase, even though every task already ran standard validation.
- Phase 6 deletion cannot begin until current H5-01 evidence passes exactly.
- Resolve every deletion target/repository reference read-only before applying
  a narrow patch. Never delete user projects or historical artifacts on disk.

Final closure after GA-014I:
1. Run every standard/focused check, documentation coverage, local Markdown
   link/path audit, removed-symbol searches, and git diff --check.
2. Verify exactly 76 ordered mandatory task commits after the planning baseline,
   each with the exact subject and execution-log evidence.
3. Verify only one guided-arranger route can create new work and the worktree is
   clean.
4. Report task -> commit SHA, files changed, automated results, human evidence,
   opt-in/live dependencies, rejected/unsupported formats, rights limitations,
   and remaining product risks.
5. Do not claim release readiness, platform approval, copyright clearance,
   audience response, or monetization.
6. Stop. Do not start optional AI without a new explicit user request.
```

## Resuming after a human checkpoint

Start a new coding-agent session with the same prompt plus one short statement,
for example:

```text
Resume the guided-arranger execution after H0-03. I created the diagnostic
GarageBand files under the paths required by GUIDED_ARRANGER_PHASE_0.md. Validate
them; do not assume they are correct merely because they exist.
```

The resume session must still perform preflight, verify prior commits/evidence,
and begin with the next pending GA task.
