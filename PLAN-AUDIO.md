# Melotrail audio feature plan

This is the only product roadmap for **audio composition via MIDI**.
[TASKS-AUDIO.md](TASKS-AUDIO.md) owns its executable steps, dependencies and status.
The independent video roadmap and queue are [PLAN-VIDEO.md](PLAN-VIDEO.md) and
[TASKS-VIDEO.md](TASKS-VIDEO.md). Neither workstream waits for the other's artistic
acceptance. They share one Kotlin/Compose application, not projects, musical timing
or generation state. This documentation split preserves existing software, task
status, acceptance evidence, Git history and user data; it authorizes no execution.

## Non-interactive validation

All required tests must run headlessly, without opening or driving an interactive
window. Foreground captures, native-window tests, GUI install/startup smoke tests,
manual UI walkthroughs and Logic/editor sessions are removed from delivery gates,
not marked passed. Keep offscreen component/semantics checks, service integration,
MIDI re-import, packaging inspection and reviews of supplied music/design artifacts.
Human musical and release decisions remain required; live UI usability and external
editor compatibility remain unverified, not implicit passes. Historical evidence
is preserved. Follow [Validation](docs/VALIDATION.md#non-interactive-validation);
this policy supersedes older interactive-test requirements in owner references.

## 1. Audio product outcome

### Audio composition: protected melody to a complete MIDI arrangement

Melotrail helps a musician arrange an existing melody with Chords, Bass and Drums.
The musician confirms the musical authority, hears a full draft, repairs specific
passages and exports accepted MIDI. **Logic Pro owns instruments, audio rendering,
mixing, mastering and the finished soundtrack.** Audio import/transcription and
in-app audio production are not part of this delivery.

```text
Create/open → import melody → confirm settings, structure and chord durations
→ confirm arrangement plan → generate/listen → repair → Use → export MIDI
→ finish the sound in Logic Pro
```

## 2. Current implementation baseline

The original repository inspection was at `32cc13746`. These are observed
implementation facts, not current-build verification or release approval.

| Area | Present in the current tree | Remaining gap |
| --- | --- | --- |
| MIDI intake and authority | Protected SMF import, explicit chord windows, source-end padding, section/plan confirmation and confined storage | Revalidate the current build and fix only reproduced failures |
| Arrangement | Melody/harmony analysis, bounded piano voicing/comping, authored 4/4, 3/4 and 6/8 patterns, coordinated roles, plan/rest/boundary fingerprints | Real full-song musical improvement remains unscored |
| MIDI workspace | Six pages, verified note lanes, style preview, full-draft playback, targeted repair, atomic Use/Undo and one player | Current offscreen visual/performance checks and listening decisions; live UI usability is outside validation |
| MIDI export | Immutable accepted-only complete/role files, semantic re-import and evaluation/Logic preparation commands | Current semantic package checks and release decision; interactive Logic compatibility is outside validation |

MIDI needs verification and acceptance, not another importer, generator rewrite
or six-page redesign. The reported **5/10** remains the qualitative baseline.
The active audio-production/worker runtime and Swift companion are removed; do
not rebuild them. Preserve external evidence and unrelated local data. Old build
receipts do not certify the current dirty tree: recheck the selected candidate
without deleting environments, media, caches or user projects.

## 3. Product and safety rules

1. Preserve original MIDI bytes and protected melody events. Confirmed tempo,
   meter, key, structure and exact chord durations remain authoritative.
   Chromatic harmony is valid; analysis and suggestions never auto-confirm.
2. Generate deterministically from the same inputs, settings, versions and seed.
   Fingerprint every consumed plan, upstream, neighboring and repeated-section
   dependency. Invalidate only the declared affected scopes.
3. Draft playback is allowed before acceptance; MIDI export is accepted-only.
   Missing/failed work is not a planned rest. Candidates, source files and export
   snapshots are immutable; Use/Undo are atomic reference changes.
4. Keep Project, MIDI, Structure & Harmony, Arrange, Review and Export, with one
   persistent MIDI player. A top-level MIDI/Video switch is not a seventh MIDI page.
5. Keep optional video runtimes behind their lazy boundary. Missing tools, models,
   credentials or network must not prevent MIDI startup, audition or export.
   Video projects, assets, jobs and outputs remain outside MIDI storage.
6. Reject unsupported schemas before writes and preserve current MIDI artifacts.
   No old-project migration, compatibility pipeline or broad filesystem cleanup.
7. Tests establish integrity, not musical quality, visual approval, rights or
   production readiness. Human decisions must identify the reviewed artifact/build.

Detailed ownership: [Architecture](docs/ARCHITECTURE.md),
[MIDI contract](docs/MIDI_CONTRACT.md), [UI guideline](docs/UI_GUIDELINE.md) and
[Validation](docs/VALIDATION.md).

## 4. Development foundation

### Feature CORE — Reproducible development baseline

1. Select the exact working candidate, preserving existing tracked and untracked
   changes. Separate task-owned MIDI WIP from unrelated local environments/media.
2. Verify Kotlin 2.2.21/JDK 21, current architecture/documentation tests, focused
   feature tests, `make test` and `make build`.
3. Record actual current failures and their owners. Use an inventoried isolated
   checkout when necessary; never weaken a guard or delete data to obtain a pass.

**Exit:** an identified, reproducible baseline and a bounded next task. No new
scheduler, inference run, commit, installation or cleanup is authorized by this plan.

## 5. Audio composition features

### Feature AC1 — Protected melody and musical authority

**Reuse:** MIDI import, project lifecycle, extent, structure and harmony services.

1. Verify supported SMF 0/1, one note-bearing track/channel and fixed tempo/meter;
   explain unsupported files without mutation.
2. Verify source note end versus file end, explicitly confirmed trailing padding,
   exact section totals and unequal/sub-bar chord durations at source PPQ.
3. Verify draft edits, affected-scope previews, confirmation, locks and reopening.
   Reordering accompaniment sections must not reorder the melody.

**Exit:** the musician can establish exact authority safely. Unsupported meters
remain valid authority, but generation outside authored 4/4, 3/4 and 6/8 is rejected
explicitly. This feature is implemented; the queue verifies it and scopes defects.

### Feature AC2 — Coherent whole-song arrangement

**Reuse:** melody context, arrangement-plan proposals and Chords → Bass → Drums.

1. Verify read-only overlap/sustain/register evidence and advisory harmony tension.
2. Confirm purpose, energy, role activity, groove, phrase/repeat and boundary intent.
3. Generate complete drafts with bounded melody-aware piano, metrical comping,
   coordinated bass/drums, intentional rests and endings.
4. Recheck deterministic replay, neighboring dependency invalidation and exact retry.

**Exit:** every required scope has a valid candidate or explicit rest, with source
and harmony unchanged. Full-song quality is assessed in AC5, not inferred here.

### Feature AC3 — Listen, repair and accept

**Reuse:** style preview, persistent transport, repair alternatives and batch Use.

1. Audition the melody, style and complete draft through the same player, before Use.
2. Compare scoped repairs at the same bar position with the protected melody:
   more melody space, simpler/lower piano, smoother transitions, less bass movement
   and calmer drums.
3. Keep at most three meaningful alternatives or one coherent dependency-spanning
   repair set. Cancel/retry without losing accepted work; apply settings only once.
4. Use the chosen draft/repair atomically, Undo safely and reopen unchanged artifacts.

**Exit:** the existing first-draft path takes at most three primary actions after
musical authority is ready; targeted repair never becomes a hidden whole-song edit.

### Feature AC4 — Accepted MIDI export and Logic handoff

1. Revalidate current accepted-only complete-song and aligned role MIDI exports,
   deliberate role omissions, common origin/end, expression and manifest hashes.
2. Prepare fresh current-build Logic-ready packages using the existing matrix command.
3. Verify complete/separate-file alignment, controllers, endings and hashes through
   semantic re-import and fixture checks, without launching Logic.

**Exit:** technical MIDI package checks pass; interactive Logic compatibility is
not tested or claimed. No render, mixer, sound-library, soundtrack or video
handoff enters the MIDI export page.

### Feature AC5 — Musical usefulness and MIDI release

1. Obtain five owned/licensed full-song projects, at least three unseen; freeze
   inputs/settings before final generation using the existing evaluation harness.
2. Compare piano+melody and complete arrangements with identical instrument mapping;
   record real scores, bad bars, requested repairs and failed results from supplied
   comparison artifacts, without a live app-timing exercise.
3. Refresh six-page offscreen visual, semantics and service-performance evidence.
   No foreground capture, live-window usability test or acoustic-onset UI session.
4. Inspect packaging, bundled JVM and runtime dependencies without starting a GUI,
   then reconcile evidence with final versions and obtain a separate MIDI release
   decision disclosing the omitted interactive checks.

**Targets:** median overall and piano/melody-fit ≥8/10; each song ≥7/10 on both;
no core-role/interaction/structure score below 6/10; no severe unresolved fault;
zero protected melody changes. The live draft-to-Use timing/usability gate is
removed. Use the applicable non-interactive performance/visual checks in Validation.
Failed retained checks create small corrective tasks, not lowered thresholds.
Listening and release decisions remain human gates on supplied artifacts;
preparation can proceed now. No live Logic or UI walkthrough is required.

## 6. Step-by-step delivery order

| Step | Feature delivery | What unlocks next |
| --- | --- | --- |
| 1 | CORE / preserve and recheck the current MIDI baseline | Exact candidate and current technical checks |
| 2 | AC1 → AC2 | Verified protected input/authority and deterministic whole-song arrangement |
| 3 | AC3 | Verified audition, targeted repair, atomic Use/Undo and safe reopen |
| 4 | AC4-01 and AC5 preparation | Fresh MIDI packages and musical/offscreen-visual/packaging evidence |
| 5 | AC5 review/release | Actual listening decisions and retained non-interactive checks, then a separate MIDI release decision with limitations |

[TASKS-AUDIO](TASKS-AUDIO.md) owns exact dependencies and current status. AC5-01
song intake can proceed independently; missing human evidence blocks only its
applicable gate. MIDI work does not wait for the video pilot, nor does it authorize
video rendering. Select current task IDs explicitly; no automatic agent run,
commit, installation, cleanup or spending follows from this order.

## 7. Explicitly outside this delivery

- Audio import/transcription, generated melody edits, unrestricted AI music,
  extra musical roles, multiple MIDI sources, tempo/meter maps or a piano-roll editor.
- Audio rendering/mixing/mastering, sound libraries, a mixer or publishing page.
  Logic Pro owns instruments and finished sound.
- Video generation, artwork preparation and video export: independently scoped in
  [PLAN-VIDEO](PLAN-VIDEO.md), never prerequisites for MIDI export.
- Soundtrack synchronization, public upload, a second Swift application, legacy
  project migration, broad filesystem cleanup, recovery of unavailable historical
  experiments or rewriting Git history.

## 8. Completion policy

Each feature closes through bounded tasks in TASKS-AUDIO. Reuse existing consumers and
tests; add regression tests for every fixed bug. Run focused checks, `make test`
and `make build` only through a verified headless path, plus `git diff --check`.
If default wiring opens a window, use a filtered headless invocation and disclose
exclusions; do not report the original full suite as passed. Retain artifact-based
listening/release review from Validation, not interactive UI/editor tests. Keep
artifacts in ignored build output or selected external evidence storage, not new
planning/history documents.

This documentation split authorizes documentation and its validation only. It starts
no automation, agent run, model setup, inference, rendering, spending or
implementation commit. Future runs select
current task IDs explicitly; old scheduler state cannot choose or complete them.
