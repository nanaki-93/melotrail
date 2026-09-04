# Sequential mockup redesign execution prompt

Copy the prompt below into a coding-agent task opened at the Melotrail repository
root. It executes the UI redesign only, with one commit per completed task.
The user request that produced this file was planning-only; storing this prompt
does not itself start implementation.

---

Implement the approved mockup-faithful Melotrail redesign from
`docs/plan/UI_MOCKUP_TASKS.md`, strictly UI-000 through UI-019, sequentially,
with exactly one commit per completed task. Continue to the next task after
validation and commit without requesting routine confirmation. Work in the
current task, not new tasks or parallel sub-agents.

The visual objective is to match the supplied `docs/pictures/UI` mockups as
closely as practical in composition, typography, colors, borders, control
shapes, spacing, iconography and detail, adapting them to actual MIDI Core
functions. A generic dark theme, working buttons, or newly generated screenshots
alone are not completion. Inspect the images and the rendered result yourself.

## Read and reconcile authority first

Read completely before changes:

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
14. `docs/plan/UI_MOCKUP_REDESIGN_PLAN.md`
15. `docs/plan/UI_MOCKUP_TASKS.md`
16. `docs/plan/UI_MOCKUP_EXECUTION_LOG.md`
17. `docs/plan/FUTURE_VIDEO_CREATOR.md`

View all nine PNG references, not only Arrange. Review has no supplied dedicated
image and must use the documented adaptation. Read applicable skills before
using them, including any image-generation skill if making decorative raster
assets. Do not create a web implementation or replace Compose Desktop.

`PLAN.md` remains the sole roadmap. Its approved insertion is MC-048H ->
UI-000…UI-019 -> refreshed MC-048I observations -> MC-049 -> remaining core
tasks. MC-048I preparation remains parked/preserved, not DONE. This is the only
exception to the core task suite's ordinary numeric ordering. Never use an old
prompt to bypass this insertion or discard a manual gate.

## Preflight and existing changes

- Inspect repository root, branch, HEAD, status, index and diffs before editing.
  Trust the current log and Git evidence, not a hard-coded starting commit.
- Resume the first UI task not genuinely DONE. If a subject exists in Git but
  the log is incomplete, verify its paths/evidence and reconcile status; do not
  create a duplicate or empty commit. A log saying DONE without a matching
  validated commit is not sufficient.
- Preserve all existing edits, especially MC-048I preparation, Chords/validator
  fixes, documentation changes and the compiler-session marker deletion found
  during planning. Inventory their exact paths and overlapping hunks in UI-000.
- Never `git add .`, `git add -A`, `git commit -a`, stash, reset, clean, checkout
  away changes, or commit an entire overlapping file by assumption. Stage only
  the reviewed task-owned changes. If clean separation is not possible, stop
  and ask the user about the exact overlap. Do not silently adopt the other work.
- Existing failures are recorded with their baseline; do not fix unrelated
  product behavior merely to manufacture a green task commit.

## Required implementation boundaries

- Kotlin/JVM + Compose Desktop; six routes only: Project, MIDI, Structure &
  Harmony, Arrange, Review, Export. No web app or hidden legacy screen.
- Reuse actual application use cases. Preserve protected source MIDI,
  authoritative timing/harmony, deterministic generation, immutable candidates,
  atomic draft acceptance/undo and accepted-only export.
- Match the reference's compact rectangular controls, aligned musical lanes,
  top band, project rail, genuine right inspector and compact dock. Do not leave
  inherited giant pill buttons or a vertically stacked inspector on wide screens.
- Read-only note lanes use verified source/candidate data; missing/stale data
  is labeled. Never synthesize fake note/waveform graphics or parse MIDI in UI.
- The real audition output is the only playhead clock. One persistent player,
  one bounded observation subscription, no page-local transport or audio render.
- Expose a control only when it has an implemented action. No mock accounts,
  sound library, arbitrary intensity sliders, mixer, master, model selector,
  fake recent projects, unsupported export format, or unavailable drag/drop.
- Preserve all nine original `docs/pictures/UI` images as design-only references.
  No runtime dependency on them. Record ownership/license for any production
  font/icon/art; do not treat screenshot artwork as automatically reusable.
- Static rail decoration is allowed only under the documented narrow rule; it
  is not video preview or musical evidence. Use the designed fallback if rights
  or an asset are unavailable.
- Do not implement VID-000…VID-006. UI-018 is a future-function specification
  only. No video service, renderer, dependency, schema, stub or disabled button
  enters MIDI Core. Legacy video/audio deletion scope remains unchanged.
- Remove only replaced target presentation owners when safe. Do not start
  MC-050–MC-060 cleanup from this prompt or delete user media/projects.

## Execution loop

For each task:

1. Verify its predecessor's DONE status and actual commit. Read the complete
   task, linked contracts, actual current code and tests.
2. Mark this task IN_PROGRESS in the UI log; no other UI task is active.
3. Give a concise user update identifying the result being implemented.
4. Add characterization/regression tests for risky changes and every fixed bug.
5. Implement the smallest complete task outcome; switch target callers and
   remove replaced target duplication. Keep changes within the task boundary.
6. For visual work render all affected sizes/states, inspect actual screenshots,
   compare them to mapped references and UI-001 metrics, and correct differences.
   Check the initial viewport and scrolled/expanded states, not only one crop.
7. Run the named focused tests with forced rerun, `make test`, `make build`,
   documentation checks and `git diff --check`. Explicit documentation-only
   exceptions are in the task. Use JVM tooling for new verification; do not add
   Python or worker validation. Current legacy build checks are not new authority.
8. Classify workflow/export impact. Complete applicable manual Logic Pro checks
   and record required evidence before closing an affected task. If evidence
   requires a human, prepare it and pause honestly as described below.
9. Update the UI log with exact commands/results, screenshots/hashes/diffs,
   dimensions, geometry/contrast/focus/performance checks, changed/deleted files,
   accepted substitutions, limitations and planned exact commit subject.
10. Inspect worktree and staged diffs. Stage only task-owned implementation,
    tests, relevant documentation and log changes. Preserve pre-existing hunks.
11. Once every task criterion passes, record completion evidence and make exactly
    one commit using the task's `ui: UI-NNN ...` subject. If commit creation fails,
    the task is not complete; resolve it before advancing.
12. Verify the resulting subject, parent and path list using Git. Do not amend,
    squash, rewrite history, push, or create an extra evidence-only commit.
    Record its hash in the next task entry, avoiding a self-referential hash
    inside the commit being created.
13. Continue immediately with the next UI task unless a genuine blocker/manual
    gate requires input. Do not stop simply because one task is finished or a
    context compaction occurred. Re-read the log and resume without duplication.

Do not mark DONE with failing tests, missing functionality, unreviewed baselines,
clipped controls, fake graphics, omitted evidence, or a missing commit. If a
previous task's defect is discovered later, fix it with a regression in the
current owning task and record the relationship; do not rewrite its commit.

## Visual and behavioral acceptance

- Preserve 1280 × 900 and 720 × 900 gates; add 1536 × 1024 reference matching and
  the resize/short-window matrix in the visual plan.
- Keep 48-dp usable hit targets, keyboard focus, accessible labels and non-color
  status. Never lower contrast or remove checks just to resemble a screenshot.
- Keep the three-action first-draft, two-action section-repair and three-action
  role-repair limits, single-player continuity and exact immutable state rules.
- Add actual deterministic screenshot comparisons and independent landmark
  assertions. Tests that only write images/check their dimensions are insufficient.
- Fix clocks/IDs/data/fonts/density/scroll/playhead. Keep actual/expected/diff
  evidence on failure; prove wrong geometry/color/radius is rejected.
- Do not silently regenerate goldens to pass a test. Review each update against
  the accepted target and preserve the update's reason in the log.
- Final fidelity rubric: every page scores at least 4/5 in shell proportions,
  typography, surfaces/colors, controls/icons and focal composition, with no
  clipping, overlap or illegible primary content, plus explicit user approval.
  Never invent human scores or claim an unmeasured percentage match.

## Manual gates and blockers

Finish safe automated preparation first. When blocked by required visual
approval, musician/DAW evidence, ambiguous overlapping changes, unavailable
capability or a product-scope decision:

- record AWAITING_HUMAN or BLOCKED and the exact unmet condition;
- provide concise instructions and exact artifact links for the user;
- preserve existing work and do not commit the task as complete;
- do not begin a later task;
- resume the same task after genuine evidence/input, fix/retest if necessary,
  then create its one final commit.

UI-019 requires the user's real visual decision. The prior MC-048I musician
sessions, MC-049 unseen holdouts, and final core sign-off are separate gates.
Do not replace them with screenshots, fake MIDI tests or agent subjective scores.

## Finish

Stop after UI-019 has passed and its commit is verified. Report completed task
IDs, final commit, validation, screenshot/evidence links, approved deviations,
and any limitations. Hand back to MC-048I with refreshed build/fixture/session
instructions. State explicitly that video is specified but not implemented.
Do not continue into the VID backlog or destructive MIDI Core cleanup without
the separately applicable execution request and gates.

Begin by inspecting status/history and the UI log, then execute the first
eligible incomplete UI task.

---
