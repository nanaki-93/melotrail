# Mockup redesign execution log

Status: NOT STARTED — planning documents created; no UI task implemented

Authority: [UI tasks](UI_MOCKUP_TASKS.md) under [PLAN.md](../../PLAN.md).
Prompt: [sequential execution](EXECUTE_UI_MOCKUP_TASKS_PROMPT.md).

## Planning observation — 2026-09-05

- Repository: `/Users/marcoandreose/DEV/lab/melotrail`.
- Observed HEAD: `bdf0080` — `midi-core: MC-048H simplify draft review`.
- The worktree was already dirty. Existing source/test/document edits include
  MC-048I preparation, Chords/validation work and a deleted tracked Kotlin
  compiler-session marker. No pre-existing edit was staged or committed by this
  planning request; UI-000 must re-inventory exact ownership before execution.
- All nine requested reference images were visually inspected. Existing wide
  Project, Structure & Harmony, Arrange and Review test captures were inspected
  as baseline evidence, not accepted graphics.
- No new UI, generated artwork, video runtime, or task commit is claimed here.
- MC-048I remains AWAITING_HUMAN; its previously prepared evidence must be
  preserved and refreshed after the redesign. MC-049/cleanup remain gated.

### Planning validation

- `make test`: PASS; Gradle reported the test tasks UP-TO-DATE. This is a
  successful gate invocation, not a claim of a fresh full-suite rerun.
- `./gradlew :test --tests 'app.melotrail.documentation.*' --rerun-tasks --console=plain`:
  the generated documentation test report records 2 tests, 0 failures/errors.
- `make build`: PASS, including documentation coverage and a fresh root test
  execution; desktop tests were UP-TO-DATE (27 seconds, 2 tasks executed,
  13 UP-TO-DATE).
- Markdown link/task-inventory audit and `git diff --check`: PASS. The UI
  backlog contains 20 ordered tasks with 20 commit subjects; the separately
  gated video backlog contains 7 tasks.
- These checks validate this planning handoff and the existing checkout. They
  do not establish redesigned screenshots, user approval, Logic Pro evidence,
  or completion of UI-000. No implementation task or commit was performed.

## Status vocabulary

`TODO`, `IN_PROGRESS` (one task only), `AWAITING_HUMAN`, `BLOCKED`, `DONE`.
DONE requires the task's implementation, validation/evidence and verified commit.
A planned commit subject is not proof that a commit exists.

## Ledger

| Task | Status | Commit subject / verified hash | Evidence |
| --- | --- | --- | --- |
| UI-000 | DONE | `ui: UI-000 establish mockup redesign baseline` | Clean baseline at `d7aaf08`; manifest and validation are recorded below. |
| UI-001 | DONE | `ui: UI-001 define measured visual targets` | Versioned geometry, palette, responsive and comparator policy recorded in `ui-evidence/`. |
| UI-002 | TODO | | |
| UI-003 | TODO | | |
| UI-004 | TODO | | |
| UI-005 | TODO | | |
| UI-006 | TODO | | |
| UI-007 | TODO | | |
| UI-008 | TODO | | |
| UI-009 | TODO | | |
| UI-010 | TODO | | |
| UI-011 | TODO | | |
| UI-012 | TODO | | |
| UI-013 | TODO | | |
| UI-014 | TODO | | |
| UI-015 | TODO | | |
| UI-016 | TODO | | |
| UI-017 | TODO | | |
| UI-018 | TODO | | |
| UI-019 | TODO | | |

## Per-task evidence template

```text
Task / title:
Status / start / completion date:
Starting branch / commit:
Previous task verified commit:
Preserved pre-existing files and overlapping hunks:
Contracts and actual implementation/test owners inspected:
Changes / removed target owners / recovery information:
Reference regions and deliberate MIDI substitutions:
Focused commands / exit codes / results:
make test / make build / documentation audit / diff check:
Screenshot state / size / density / font / source fixture:
Expected / actual / diff paths and hashes:
Geometry / contrast / keyboard / performance results:
Workflow or MIDI export impact classification and reason:
Required manual evidence / reviewer / date / result:
Known limitations / blocker / exact unblock action:
Planned exact task commit subject:
Commit verification (Git subject, parent, paths):
Next task:
```

Record the completed commit hash in the next task entry (or a later authorized
log update). Do not create a second commit or amend merely to place a commit's
own hash inside itself. Before continuing, independently verify it in Git.

### UI-000 — Establish a safe redesign baseline

Status / start / completion date: DONE / 2026-09-05 / 2026-09-05.

Starting branch / commit: `main` at `d7aaf0839b2f229779dd9b10d1e714b5c8835dcc`
(`cleaning and new pln for a better UI`), nine commits ahead of `origin/main`.

Previous task verified commit: MC-048H is `bdf0080` (`midi-core: MC-048H
simplify draft review`); the root-plan insertion after it is present.

Preserved pre-existing files and overlapping hunks: `git status --short --branch`,
index inspection, and both diff checks found a clean worktree and empty index.
There were no uncommitted MC-048I, Chords/validator, documentation, or
compiler-session-marker hunks to adopt. The planning handoff's broad committed
changes are part of `d7aaf08`, not this UI task. The committed legacy visual
test owner `desktopApp/src/test/kotlin/app/melotrail/desktop/WorkspaceScreenTest.kt`
still reads old mockup references and exercises obsolete pages; it is recorded
as an existing baseline/deletion-scope owner for UI-017/MC-051, not changed here.

Contracts and actual implementation/test owners inspected: the required root
and MIDI contracts; the UI redesign plan/tasks/prompt/future-video proposal;
all nine reference PNGs; `MidiCoreWorkspace.kt`; `WorkspacePageRouter.kt`;
`MidiCoreFocusedWorkflowTest.kt`; `MidiCoreVisualRegressionTest.kt`;
`MidiCoreArrangePageTest.kt`; `MidiCoreReviewPageTest.kt`; and the pre-existing
`WorkspaceScreenTest.kt` reference-overlay owner.

Changes / removed target owners / recovery information: added the reproducible
reference and existing-capture manifest at
`docs/plan/ui-evidence/UI-000-baseline.md`. No production or test owner was
removed, and no user project/media/build output was modified. `build/` captures
are ignored and are evidence only, recoverable by the listed test fixture flow.

Reference regions and deliberate MIDI substitutions: all nine 1536 × 1024
references were inspected directly. Their exact filenames, dimensions and
SHA-256 values, mapped MIDI substitutions, and the unapproved existing six-page
wide/compact captures are recorded in the baseline manifest. Video, Mix &
Master, Library and Settings are design references only; they do not authorize
routes or runtime features.

Focused commands / exit codes / results: `make test` exited 0 (14 tasks,
up-to-date); `make build` exited 0 (15 tasks; documentation coverage executed);
reference/capture manifest and `git diff --check`/cached diff checks exited 0.

make test / make build / documentation audit / diff check: PASS, with the
baseline caveat that the Gradle test tasks were up-to-date rather than a forced
redesign rerun. Documentation links are covered by the current build's
`checkDocumentationCoverage` and the committed documentation-integrity test.

Screenshot state / size / density / font / source fixture: the stored existing
real-service workflow captures are 1280 × 900 wide and 720 × 900 compact under
`desktopApp/build/test-results/midi-core-focused-workflow/`; they are
unapproved pre-redesign PNGs, with hashes in the manifest. They use the
existing test fixture/density/font path. UI-017 replaces the write-only capture
condition with deterministic target expected/actual/diff comparison.

Expected / actual / diff paths and hashes: this baseline intentionally has no
approved expected image or visual diff threshold. Reference and capture hashes
are in `docs/plan/ui-evidence/UI-000-baseline.md`; future test artifacts remain
ignored beneath `desktopApp/build/test-results/`.

Geometry / contrast / keyboard / performance results: no presentation behavior
changed. UI-001 owns exact geometry/contrast targets; UI-003/UI-016 own control
semantics, keyboard, responsive and performance verification.

Workflow or MIDI export impact classification and reason: none; this task is
evidence/documentation only, so no Logic Pro matrix rerun is applicable.

Required manual evidence / reviewer / date / result: none for UI-000. UI-019
still requires real user visual acceptance and MC-048I still requires five
observed musician sessions.

Known limitations / blocker / exact unblock action: existing captures and
legacy reference-overlay tests are not target goldens and do not prove visual
fidelity. UI-001 must freeze measured targets before visual implementation;
UI-017 must replace write-success-only visual assertions.

Planned exact task commit subject: `ui: UI-000 establish mockup redesign baseline`.

Commit verification (Git subject, parent, paths): recorded after commit in the
UI-001 entry, to avoid self-referential evidence.

Next task: UI-001 — Freeze measurable visual targets.

### UI-001 — Freeze measurable visual targets

Status / start / completion date: DONE / 2026-09-05 / 2026-09-05.

Starting branch / commit: `main` after `1073a4011a9939494732d98e732a115c9f11ea10`.

Previous task verified commit: `1073a40` — `ui: UI-000 establish mockup redesign
baseline`; its parent is `d7aaf08` and its only paths are the UI log and
UI-000 baseline manifest.

Preserved pre-existing files and overlapping hunks: the UI-000 commit left a
clean worktree. No earlier committed application/test owners were changed here.

Contracts and actual implementation/test owners inspected: all nine native-size
references; `MIDI_WORKSPACE_VISUAL_SPEC.md`; the redesign plan/tasks; the
existing `WorkspaceTheme.kt`, `MidiCoreWorkspaceShell.kt` and
`MidiCoreSongMap.kt`; plus current visual-capture tests. The current shell's
64-dp padded layout, 196-dp navigation width, 256-dp generic context rail,
12/16-dp shapes and 132-dp song-map buttons are recorded as pre-redesign values,
not copied into the new target contract.

Changes / removed target owners / recovery information: added
`docs/plan/ui-evidence/UI-001-reference-measurements.json` and
`docs/plan/ui-evidence/UI-001-region-map.md`, then linked them from the MIDI
workspace visual specification. No production owner was changed or removed.

Reference regions and deliberate MIDI substitutions: every reference remains in
the machine-readable manifest with its UI-000 digest. The region map resolves
all six page compositions, explicitly maps Review to `04-arrange.png`, assigns
the component-only references and excludes unsupported audio/video/library/
settings functions. It freezes source landmarks: 62-pixel top band, 221–228-pixel
rail, page-specific wide inspectors (Project 458; MIDI 381; Structure 390;
Arrange/Review 332; Export 407), 64-pixel Arrange strip, 52-pixel role lane and
reference-density transport.

Focused commands / exit codes / results: native reference inspection and raster
pixel sampling completed; `jq` JSON validation, manifest cardinality/mapping
audit and `git diff --check` are recorded with the task validation.

make test / make build / documentation audit / diff check: documentation-only
task. UI-000's full baseline `make test` and `make build` remain passing. This
task runs the explicit JSON/link audit and focused documentation test; it does
not claim a product change or force unrelated desktop work.

Screenshot state / size / density / font / source fixture: all references are
1536 × 1024 at 1x coordinate density. The new responsive target preserves
1536 × 1024, 1280 × 900 and 720 × 900 without bitmap scaling. System sans is
the declared temporary deterministic fallback; UI-002 must record a license if
it bundles a font.

Expected / actual / diff paths and hashes: UI-017 will own target expected,
actual and diff PNGs. UI-001 instead freezes independent partition, color,
radius and anti-aliasing policy: shell ±8 px, content grid ±4 px, opaque
surface/text/fill channel delta ≤8, border delta ≤12 and a narrowly scoped
one-pixel glyph-edge allowance.

Geometry / contrast / keyboard / performance results: no runtime geometry or
behavior changed. The palette explicitly requires 4.5:1 normal-text and 3:1
large-text/essential-boundary validation in UI-002/UI-016; keyboard/hit-target
and performance gates remain owned by UI-003/UI-016.

Workflow or MIDI export impact classification and reason: none; documentation
and measurement contract only, so no DAW matrix applies.

Required manual evidence / reviewer / date / result: none for UI-001. UI-019
still requires real user visual approval and MC-048I still requires genuine
observed musician sessions.

Known limitations / blocker / exact unblock action: measurements describe a
truthful MIDI adaptation rather than pixel-equating unsupported source screens.
The current tests still write images/dimensions and legacy overlay readers;
UI-017 must replace that behavior with the frozen comparator policy.

Planned exact task commit subject: `ui: UI-001 define measured visual targets`.

Commit verification (Git subject, parent, paths): recorded after commit in the
UI-002 entry, to avoid self-referential evidence.

Next task: UI-002 — Establish target typography, colors and assets.

## Final visual review

Each of six pages: mapped original, final screenshot, shell proportions,
typography, surfaces/colors, controls/icons, focal composition, user score and
comments. Every dimension must reach 4/5 and all objective tests must pass.
Record user-approved deviations and fix/retest evidence; leave blank until real
review occurs.

## Handoff

Final UI commit:
User visual decision:
Automated validation:
Outstanding applicable DAW evidence:
Refreshed MC-048I build/fixtures/session instructions:
MC-048I observation status:
Future video status: specification only; VID implementation not authorized.
