# Task 076 — Guided MIDI Import and Preparation

## Goal

Make direct MIDI import understandable and structure-ready through one import
confirmation and one obvious preparation action in the normal case, while
retaining safe artifact stages and advanced recovery.

## Dependencies

- Task 074 accepted.
- Task 075 may proceed in parallel, but both must be accepted before Task 077.
- Read `../../PLAN.md` and Task 074 completely before implementation.

## Scope

This task owns import/preparation UX, the orchestration boundary for standard
repair plus analysis, state-derived primary actions, concise progress, and
advanced-detail placement. It does not rebuild the full workspace layout.

## Requirements

- Provide the three reference entry points in the Parts surface:
  - `+ Add Part` chooses a source and routes by validated content;
  - `Import MIDI` preselects MIDI;
  - `Import Audio` preselects the supported audio path.
- All entry points use the same typed import state machine and application
  service. Do not duplicate import business logic in composables.
- Replace the current long import dialog with a compact two-step sheet:
  1. select a source and validate its actual format;
  2. confirm an auto-derived stable part ID/name and optional musical role.
- Keep file-extension filters as hints only. Continue validating actual MIDI,
  WAV/WAVE, or MP3 structure at the owning service boundary.
- Put rights attestation and advanced metadata in an expandable Details area.
  Keep the attestation available and required for commercial-ready export, but
  do not let it obscure ordinary local import.
- After import, derive exactly one primary part action from canonical state:
  - `Prepare MIDI` when immutable raw MIDI is ready;
  - `Review repair` when thresholds require approval;
  - `Apply Lo-fi change` when a feel selection awaits application;
  - `Add to structure` when analysis is current;
  - `Fix issue` when a current artifact or dependency is invalid.
- Secondary preview and Details controls may remain, but must not visually
  compete with the primary action or duplicate it elsewhere.
- Add a typed `Prepare MIDI` orchestration command that:
  - runs the standard transcription-safe repair;
  - validates/publishes the repaired MIDI and quality report atomically;
  - stops for explicit repair approval when thresholds require it;
  - otherwise analyzes the selected MIDI;
  - reports named phases and a single final result;
  - preserves safe cancellation and one retry identity.
- Keep advanced repair profiles and reports accessible from a part Details
  surface. `Tighten timing` retains its warning and explicit confirmation.
- Keep audio import visibly distinct and truthful: only eligible solo-piano
  WAV/MP3 follows inspect -> optional safe cleanup -> transcription -> standard
  MIDI preparation. Do not imply support for vocals, full mixes, or arbitrary
  polyphony.
- Replace idle internal-stage explanations with concise outcome language.
  Technical stages may appear while work is running or in Details.
- Use one dismissible global operation banner and one retry action. Remove
  duplicate readiness/retry/status messages from rows and competing panels.
- Project switch, close, cancellation, or failure must stop the active import/
  preparation session without deleting last-known-good artifacts.
- Preserve source/raw immutability, project-relative paths, current stale rules,
  worker/renderer readiness truth, and atomic project writes.

## Tests

- Application/view-model tests for source selection, content validation,
  auto-derived/sanitized IDs, duplicate IDs, optional roles, and entry-point
  convergence on one import command.
- Prepare-MIDI tests for normal success, approval-required repair, approval
  continuation, repair failure/retry, analysis failure/retry, cancellation,
  stale analysis, and project switching.
- Audio-path tests for inspection, cleanup recommendation, transcription
  dependency failures, unsupported content, and truthful solo-piano wording.
- Compose tests asserting one primary CTA for every part state, one import sheet,
  one retry surface, and advanced controls hidden until Details is expanded.
- Source/raw hash assertions around every success and failure path.
- Run focused tests, then `./gradlew test :desktopApp:test :desktopApp:build`.
  If worker code changes, run `.venv/bin/python -m unittest discover -s
  worker/tests`; do not use ambiguous system Python.

## Acceptance criteria

- A valid direct MIDI becomes structure-ready with one import confirmation and
  one `Prepare MIDI` action when approval is not required.
- The UI always shows one clear next action for a part.
- Normal import does not expose worker names, filesystem paths, cleanup
  parameters, schema versions, or several competing maintenance buttons.
- Exceptional repair decisions and diagnostics remain reviewable in Details.
- Import, repair, and analysis progress/failure use one global banner and one
  safe retry.
- Source and raw MIDI are unchanged; every underlying report remains
  inspectable.

## Out of scope

- Exact three-column/footer visual reconstruction.
- Variable Lo-fi controls or changes to its algorithm.
- Arrangement synchronization changes.
- Expanding transcription beyond the current bounded solo-piano contract.
