# Documentation consolidation audit

> **Historical schema-v4 audit.** The active product roadmap is now
> [`../../PLAN.md`](../../PLAN.md); the current execution index is
> [`README.md`](README.md).

Audit date: 2026-08-24

## Organization decision

At the audit date, `docs/plan/` contained the only active implementation
roadmap. Operational,
troubleshooting, compatibility, provenance, release, and executable inventory
documents remain directly under `docs/` because the running application and
tests link to them. “Put all docs in `docs/plan`” is applied to the complete plan
suite, not to durable user/maintainer manuals whose stable paths are product
contracts.

## Plan suite at audit completion

- `README.md`
- `PLAN.md`
- `PROJECT_ANALYSIS.md`
- `TASKS.md`
- `QUALITY_GATES.md`
- `YOUTUBE_READINESS.md`
- `EXECUTE_ALL_TASKS_PROMPT.md`
- `EXECUTION_LOG.md`
- `DOCUMENTATION_AUDIT.md`

## Retained operational documents

| Document | Reason retained |
| --- | --- |
| `docs/README.md` | Current documentation index and ownership rules |
| `docs/MIDI_IMPORT_PROCESS.md` | Linked from README, desktop UI, and executable documentation tests |
| `docs/TRACK_PROCESS_WORKFLOW.md` | Linked from the desktop UI and describes current, not target, behavior |
| `docs/TROUBLESHOOTING.md` | Local worker/renderer/library/import/build recovery |
| `docs/COMMERCIAL_PROVENANCE.md` | Current release evidence and policy-review contract |
| `docs/RELEASE_ACCEPTANCE.md` | Living automated/manual release gate |
| `docs/COMPATIBILITY_READERS.md` | Active external-format reader ownership/removal conditions |
| `docs/SPRING_API_RETIREMENT.md` | Executable non-destructive legacy-data disposition contract |
| `docs/FUNCTION_DOCUMENTATION_INVENTORY.md/.json` | Gradle build documentation-coverage contract |
| `docs/pictures/App-pages.png` and referenced `docs/pictures/UI/*.png` | Current Compose visual regression fixtures |

## Removed as superseded or unreferenced

| Removed group | Reason |
| --- | --- |
| Completed `docs/milestones/ui-*` and `docs/prompts/completed/ui-*` | Already removed in the incoming worktree; implementation exists and Git history is the archive |
| Old `docs/plan` architecture/domain/pipeline/UI/instrument/mix/provenance proposals | Referenced completed Tasks 118–130, missing task files, or superseded runtime behavior |
| Old plan manifests and prompt templates | Pointed to missing prompt/task paths and could not execute |
| `docs/API_REQUIREMENTS.md`, `BASELINE_SUPPORT_MATRIX.md`, `ROADMAP.md`, `UI_ARCHITECTURE.md`, `UI_STYLE.md`, `WORKFLOW_SCREENS.md`, `docs/manifest.json` | Completed UI/baseline planning duplicated current code, screenshots, or operational docs |
| Unreferenced Moki/Tabi/mockup/test image experiments | No production/test/doc consumer |
| `.DS_Store` metadata | OS-generated, non-product metadata |
| `README_BAK.md` | Stale backup; current root README is canonical |

## Maintenance rules

- Behavior changes update the operational document that users/tests already
  resolve; do not create task-local duplicate manuals.
- Planning changes update this suite and root `PLAN.md`; do not add another
  manifest or milestone directory.
- A document may be deleted only after repository-wide reference search and,
  when applicable, caller/test migration.
- Visual fixture images remain while referenced by Compose tests.
- Historical plans and implementation decisions remain available through Git.
