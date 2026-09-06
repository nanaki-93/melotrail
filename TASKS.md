# Implementation tasks

Authority: [PLAN](PLAN.md). Updated: 2026-09-06. Task status is authoritative in the integration branch queue.
The old MC/UI/VID queues are retired. Reuse existing code and tests; do not
replay completed import, draft, acceptance, export or UI-foundation work.

## Execution contract

- One task produces one reviewable implementation commit. Split an unexpectedly
  large task into suffixed subtasks here before editing; keep the parent pending
  until all children pass. Do not create another plan or log file.
- Read AGENTS, PLAN, this task and its owning references. Inspect actual callers
  and tests. For source paths below, `core` means
  `src/main/kotlin/app/melotrail`; `desktop` means
  `desktopApp/src/main/kotlin/app/melotrail/desktop`. Test owners mirror them.
- Dependencies in the queue are mandatory. The coordinator chooses the earliest
  ready mandatory task, skips an external wait, and records why. Optional tasks
  are excluded unless included in the implementation request.
- Default implementer: GPT-5.6 Terra. Use a fresh review context and the same
  task contract; reviewer approval requires evidence, not confidence language.
- Inspect `git status` first. Preserve unrelated work. Use `codex/` branches and
  isolated worktrees from the approved integration base; never reset the user's
  working tree. Commits must contain only task-owned changes.
- Every implementation task runs focused checks, `make test`, `make build`, and
  `git diff --check`. Fix every reproduced bug with a regression test. Existing
  failures are recorded with an owner, never hidden by skips or weakened checks.
- Core musical/export changes also prepare the relevant Logic matrix. The user
  can perform it as a batch at Q02; no release claim precedes that evidence.
- UI work captures relevant states at the three supported fixture sizes and
  inspects the actual images. MIDI engines keep deterministic event fixtures.
- Deletion includes exclusive tests, resources, config and callers. Extract only
  a live required helper before deleting its old owner. No compatibility mode.
- A done result states behavior, files removed, commands/results, evidence
  paths, commit, limitations and next task in at most eight lines. Update the
  queue, not a second narrative history. Put large reports in ignored `build/`.

States: TODO, RUNNING, REVIEW, WAITING_USER, BLOCKED, DONE, OPTIONAL.
Only the coordinator updates the queue when parallel workers are explicitly used.
A manual gate stays WAITING_USER until actual evidence arrives. It never blocks
an unrelated task. A failed dependent gate cannot be bypassed.

## Queue

| ID | Task | Depends on | State | Result / implementation commit |
| --- | --- | --- | --- | --- |
| F01 | Verify baseline and real dependency boundaries | — | BLOCKED | Gradle cannot configure in this sandbox: FileLockContentionHandler local socket fails with java.net.SocketException: Operation not permitted. Therefore required JVM tests, build, native package, and startup smoke could not run.; preserved /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T10-50-51-461Z-F01 |
| M01 | Freeze musical baseline and comparison harness | F01 | TODO | |
| F02 | Delete legacy desktop | M01 | TODO | |
| F03 | Delete legacy application workflow | F02 | TODO | |
| F04 | Delete obsolete musical generators and model paths | F03 | TODO | |
| F05 | Delete audio/worker runtime and finish schema/build cleanup | F04 | TODO | |
| F06 | Delete verified legacy data and measure repository reduction | F05 | TODO | |
| A01 | Harden and verify bounded agent execution runner | F01 | TODO | Bootstrap runner prepared; validate remaining failure cases after F01. |
| U01 | Finish verified lanes and live timeline projection | F06 | TODO | |
| U02 | Compact shell, player and inspector | U01 | TODO | |
| U03 | Refine Project and MIDI import | U02 | TODO | |
| M02 | Derive melody context and harmony-tension evidence | M01, F06 | TODO | |
| M03 | Add explicit harmony durations and source extent | M02 | TODO | |
| M04 | Improve piano voicing against melody | M03 | TODO | |
| M05 | Add phrase-aware, meter-aware comping | M04 | TODO | |
| M06 | Persist a deliberate whole-song arrangement plan | M03 | TODO | |
| M07 | Generate drafts from plan, boundaries and explicit rests | M05, M06 | TODO | |
| M08 | Coordinate bass/drums and section transitions | M07 | TODO | |
| M09 | Add meaningful alternatives and targeted musical repair | M08 | TODO | |
| U04 | Build compact Structure & Harmony editing | U03, M06 | TODO | |
| U05 | Build timeline-first Arrange with plan and repairs | U04, M09 | TODO | |
| U06 | Finish whole-song Review and Logic export handoff | U05 | TODO | |
| U07 | Prove visuals, accessibility and responsiveness | U06 | TODO | |
| Q01 | Evaluate musical improvement and fix failures | U06, M09 | TODO | |
| Q02 | Run the current Logic Pro matrix | U06, M03, M07, M08 | TODO | |
| Q03 | Prove clean install and obtain MIDI release decision | F06, U07, Q01, Q02 | TODO | |
| V01 | Prove the isolated video/media boundary | F01; video implementation selected | TODO | Selected for unpaid implementation; human/budget gates remain. |
| V02 | Build the approved TABI asset library | V01 | TODO | Selected for unpaid implementation; human/budget gates remain. |
| V03 | Add one cost-bounded generative-animation adapter | V02 | TODO | Selected for unpaid implementation; human/budget gates remain. |
| V04 | Implement deterministic scene composition | V03 | TODO | Selected for unpaid implementation; human/budget gates remain. |
| V05 | Build real video editor/preview | V04 | TODO | Selected for unpaid implementation; human/budget gates remain. |
| V06 | Encode, validate and publish local video outputs | V05 | TODO | Selected for unpaid implementation; human/budget gates remain. |
| V07 | Complete a TABI music-video pilot and optional handoff | V06, Q03 | TODO | Selected for unpaid implementation; human/budget gates remain. |

## Foundation and removal

### F01 — Verify baseline and real dependency boundaries

**Outcome:** a reproducible foundation and exact removal map, using the current
working code rather than stale task status.
**Inspect:** both Gradle files, native packaging, `DesktopMain`,
`MidiCoreDesktopComposition`, `TargetArchitectureRulesTest`, existing core E2E,
visual-evidence provider and tests, and all source/test/resource imports.
**Work:** verify the pre-existing JDK/Kotlin changes and native startup; record
runtime/compiler/toolchain versions. Repair the architecture check so actual
root-level desktop owners are covered. Trace shared helpers and strongly
connected legacy consumers; generate a compact keep/extract/delete inventory
in build output. Verify the existing visual projection before extending it.
**Tests:** real create/import/authority/draft/use/undo/reopen/export workflow;
negative architecture fixtures at actual paths; package/startup smoke.
**Done:** focused MIDI flow runs without worker/network, all gates pass, every
legacy component has an exact deletion owner. No new runtime architecture yet.

### F02 — Delete legacy desktop

**Inspect:** `WorkspaceApp`, `WorkspaceViewModel`, `WorkspacePageRouter`,
`WorkspaceScreenTest`, `DesktopMain` legacy composition, preferences migration,
old playback/readiness/library helpers and shared theme/primitives.
**Work:** retain `MidiCoreDesktopEntrypoint` and live composition. Remove the
old router, view model, pages, factories and exclusive tests. Extract a shared
control only if a target caller needs it; remove legacy theme branches and
obsolete preferences migration. Replace image-reader assertions with target
behavior checks; preserve every UI and TABI reference image.
**Tests:** target entrypoint/composition, six-route inventory, theme/components,
keyboard navigation, current project open/reopen and no legacy route access.
**Done:** one desktop graph; no hidden audio page or old composition reachable
or retained for tests. New lanes and final styling can follow later.

### F03 — Delete legacy application workflow

**Inspect:** stage runner/registry, `BuildApplicationService`, old project and
arrangement services, preparation/enhancement/cohesion/critic/release services,
old CLI/config factories, orchestration tests and consumer map from F01.
**Work:** delete the rejected pipeline, its entrypoints and exclusive application
tests. Retain current `MidiCore*` use cases and only their proven live helpers.
Remove dead configurations and public adapters. Do not create a compatibility
facade. If old low-level tests still consume old models, assign those exact
remaining model files to F04/F05; do not extend or newly reference them.
**Tests:** current application workflow, cancellation, atomic acceptance/undo,
scoped stale admission, export re-import and entrypoint dependency scans.
**Done:** target services are the only application workflows; any temporary
remaining legacy leaf has a named imminent deletion task and no target consumer.

### F04 — Delete obsolete musical generators and model paths

**Inspect:** old non-core `arrangement` generators/planners, harmony helpers,
Qwen/client boundaries, critics, source repair/normalization/transposition,
Pad/Strings/Lead/FX, global humanization and melody-connection branches.
**Work:** prove required parser/pattern/voicing/artifact helpers already have
current owners, extract narrowly where necessary, then delete old implementations
and their exclusive tests. Remove optional-model configs/licenses and duplicate
musical schemas when their final consumers disappear.
**Tests:** M01 fixtures, current chord/bass/drum suites, authority/chromatic tests,
protected-source identity and no AI/network/source-mutation runtime.
**Done:** only current Chords/Bass/Drums generation remains; no “future” adapters
or old implementation used to keep an obsolete test compiling.

### F05 — Delete audio/worker runtime and finish schema/build cleanup

**Inspect:** `audio`, `dsp`, `model`, `worker`, renderer/mixer/SFZ/library/licensing,
commercial/release code, remaining legacy `Project`/stages, root resources,
Python tools, old live E2E and Gradle/Make/config/dependency consumers.
**Work:** delete the entire obsolete media/process boundary and exclusive tests;
remove schema-v4 readers, aliases and constructors once unused. Remove worker
requirements and tools; keep target storage/hash/logger behavior with one owner.
Reduce Make to help/test/check/build/desktop/clean; remove unused HTTP/JSON/audio
dependencies only after the consumer scan. Retain current serialization needs.
**Tests:** full current JVM workflow, absent worker/synth-output separation,
unsupported schema rejected without writes; clean target build/check.
**Done:** zero tracked Python source and no worker/audio-production or old-project
runtime; no dormant compatibility mode. Resolve dependency cycles as one tested
removal, or split this task before work rather than leaving a broken commit.

### F06 — Delete verified legacy data and measure reduction

**Inspect:** resolve repository root and exact targets including `sounds`,
`data/audio`, `.venv-worker`, old root media, caches and `App-pages.png` consumers.
**Work:** record target realpaths, bytes, tracked/ignored state, symlink behavior,
consumer absence and protected exclusions before deletion. Delete only verified
repository-owned obsolete material. Preserve all supplied UI/TABI/train images,
Logic evidence, owned MIDI fixtures and current user inputs/projects/snapshots.
Remove empty packages/resources and stale ignore entries. Do not rewrite Git.
**Tests:** no-reference scans, source/fixture digests, core workflow and clean
build. Review every apparent legacy scan exception; no blanket allowlists.
**Done:** publish file/line/disk/dependency deltas against PLAN, including the
40% production-line investigation target. No 10 GB sound-library prerequisite,
legacy sample/project payload or obsolete executable image reader remains.

## Musical workflow

### M01 — Freeze baseline and build comparison harness

**Inspect:** owned MIDI fixtures, candidate generation/draft tests, current
patterns, exporter, the user's 5/10 feedback and Validation's case taxonomy.
**Work:** build a Kotlin test/harness using owned small fixtures plus a full-song
fixture with repeated sections. Freeze source/authority/style/seed/engine IDs
and current output hashes before changes. Include sustained close melody/piano,
passing tones, low melody, sub-bar chords, repeated chorus, 3/4, 6/8 and endings.
Produce side-by-side MIDI packages and a compact review form in build output.
Accept optional user failure examples as development cases; never call them unseen.
**Tests:** harness determinism, source immutability, case manifest integrity,
invalid case rejection and reproducible semantic comparisons.
**Done:** later agents can compare baseline/new MIDI without legacy renderers;
5/10 remains the only supplied subjective result. Missing real songs does not
block synthetic development work or F02.

### M02 — Derive melody context and harmony-tension evidence

**Inspect:** protected melody model, harmony timeline, generation context and
current melody/register findings.
**Work:** pure versioned per-window context for active/sounding notes, accents,
rests, register, phrase hints and repetition. Weight interval tension by overlap
and beat prominence; distinguish held/accented tension from passing tones.
Define supported sustain/pitch-expression limitations. Return location, cause,
confidence and evidence; do not infer or save new harmony automatically.
**Tests:** held semitone versus passing tone, compound interval, suspension,
chromatic chord, pickup/rest, sustain beyond key release, ambiguous bend and
polyphony; identical inputs produce identical analysis, zero project writes.
**Done:** explain a specific melody/harmony issue at a bar/beat and expose a
usable read model; musical preference stays advisory.

### M03 — Add explicit harmony durations and source extent

**Inspect:** authority drafting/service/store, source end semantics, timeline,
fingerprints, candidate invalidation, writer and exporter boundary tests.
**Work:** persist/validate chord rows with explicit durations; losslessly seed
unchanged canonical windows from progression shorthand. Add last-note/source-end
facts and explicit trailing-silence padding to an arrangement end. Preserve
source events and byte identity. Update MIDI_CONTRACT and relevant schema once;
no general old-project migration. Explain unsupported schema before any write.
**Tests:** 3+1+2+2 beat harmony, odd PPQ, sub-bar windows, no-op edit exactness,
gap/overlap/overflow, non-bar note end, end-of-track silence, padding cancel,
reopen and scoped invalidation; export whole-song boundaries and source equality.
**Done:** services support musical duration editing without equal-slot ambiguity;
Q02's updated Logic fixtures are prepared, not falsely signed off.

### M04 — Improve piano voicing against melody

**Inspect:** chord generator/validator, M01 failures and M02 context.
**Work:** bounded voicing pool with open/guide-tone/reduced choices, required
chord/slash semantics, melody-aware costs, stable tie-breaking and phrase
lookahead. Specify bounds and deterministic fallback; carry an explicit boundary
summary input without depending on an unrelated mutable accepted candidate.
**Tests:** accented close clash improves against baseline; passing/intentional
tension stays legal; guide-tone identity, low melody, bass space, voice crossing,
common-tone continuity, search limits and deterministic results across seeds.
**Done:** pool/ranking fixes measured failure cases, preserves all authority,
and generates a short baseline/new listening pack. Ask for milestone feedback
when useful; do not stall independent tasks or award a subjective score.

### M05 — Add phrase-aware, meter-aware comping

**Inspect:** chord rhythm expansion, pattern catalog/tick grid and articulation.
**Work:** anchor pattern phase to meter/song bars, clip notes at harmony changes,
choose complete support/answer/rest patterns from melody activity and phrase
position. Define actual authored 4/4, 3/4 and 6/8 behavior and compound-meter
accents. Version any changed pattern/timing rules.
**Tests:** offbeat chord change keeps metrical phase; 3/4 downbeats, 6/8 grouping,
short sections, dense melody leaves room, phrase ending rests, valid swing/grid
rounding, no harmony-boundary overhang or source timing mutation.
**Done:** the same melody/chords can receive audibly distinct useful comping;
no forced 4/4 loop truncation masquerades as other-meter support.

### M06 — Persist a deliberate whole-song arrangement plan

**Inspect:** style catalog, project schema/fingerprint, occurrence identity,
M02 phrase/repeat suggestions and generation context.
**Work:** plan records store version, occurrence purpose, repeat family,
phrase groups, energy, role activity/density/register, shared groove intent and
entry/exit rules. Style produces an editable proposal; explicit confirmation
makes it arrangement authority. Never interpret a display label as the sole
purpose. Keep suggestion, confirmed plan and generated draft distinct.
**Tests:** intro/verse/chorus/bridge/outro differ by intended rules; repeat family
is related but bounded variation is possible; rename without purpose change;
reopen, no-op hashes, locked work and plan-edit invalidation previews.
**Done:** one versioned plan drives the full song and names every generation
input. Changing it never silently rewrites melody, chords or acceptances.

### M07 — Generate drafts from plan, boundaries and explicit rests

**Inspect:** draft orchestration, generation context/publication, retry, accepted
assembly, batch acceptance/undo, review/audition and export readiness.
**Work:** resolve per-occurrence plans; preserve Chords → Bass → Drums dependency
order, explicit groove input and bounded boundary/repeat context. Add planned
rest evidence so absence of a role in an intro is intentional and complete.
Hash every consumed dependency; allow scoped retry and exact affected-neighbor
invalidation. Rest/candidate selections use the same atomic acceptance boundary.
**Tests:** two identical seeds/versions, cancellation/retry, failure versus rest,
locked scopes, stale neighbor, repeated chorus, partial acceptance rejection,
all-song inactive role policy, source/role export origin/end and no overwrite.
**Done:** draft playback and export agree on accepted activity; missing output
cannot pass as silence. Update MIDI_CONTRACT and prepare Q02 fixtures.

### M08 — Coordinate bass/drums and section transitions

**Inspect:** bass/drum generators, shared groove plan, current kick-support and
fill policies, M01 whole-song fixture.
**Work:** use planned purpose and shared kick/bass intent, bounded bass approaches
and chord-space constraints. Preserve authored drum groove completeness. Drive
fills by phrases and next-section intent; avoid a fill at every arbitrary chord
or section edge. Give repeated sections controlled variation and an intentional
ending. Inputs cannot form a circular accepted-role dependency.
**Tests:** bass/chord separation, approach resolution, kick coordination, held
melody, 3/4 and 6/8, two-bar/one-bar sections, quiet intro/rest, boundary fill,
last-note/end behavior and stable previous/next dependency fingerprints.
**Done:** assembled songs have demonstrable role coordination and transitions;
M01 comparison packages show the changes without extra melody editing.

### M09 — Add meaningful alternatives and targeted musical repair

**Inspect:** candidate diff/lifecycle, style preview cache, workspace intents,
M04–M08 settings and validation findings.
**Work:** bounded repair intents from PLAN map to versioned settings and an
explicit role/occurrence or dependency set. Rank at most three semantically
distinct choices. Add same-position melody-inclusive A/B, clear impact labels,
rejection reason and same-scope retry. Acceptance remains explicit and atomic.
**Tests:** changing intent yields meaningful event/texture differences; identical
options deduplicate; unchanged unrelated accepted hashes; blocked/locked/stale
repair; audition cannot write state; no valid solution stops after bounded work.
**Done:** a musician can request “simplify piano” and hear/review that exact
change without seed hunting or global regeneration.

## UI/UX

### U01 — Finish verified lanes and live timeline projection

**Inspect:** existing `MidiCoreVisualEvidenceProjection`, its tests/caches,
workspace reducers, song-map geometry and audition position provider.
**Work:** finish and reuse source/candidate/draft/accepted read models; render
four factual lanes with one bar/chord/note/loop x-axis, fit/zoom and section
selection. Observe actual player position at a bounded cadence while playing;
stop observation on disposal/stop. No composable artifact reads or second clock.
**Tests:** real semantic events at exact x positions, read-only projection,
stale/missing/digest mismatch, clipping, zoom/scroll alignment, pause/seek/loop,
device loss and no observer/thread leak across navigation.
**Done:** real notes and moving playhead align; screenshot art is unnecessary.

### U02 — Compact shell, player and inspector

**Inspect:** shell frame, theme, primitives, player and page-local inspectors;
UI_GUIDELINE and current wide/compact captures.
**Work:** use existing tokens; collapse transport to the specified height,
compact rectangular controls and one contextual inspector. Expose real names
instead of internal IDs. Preserve six destinations, valid playhead/selection/
scroll, compact keyboard navigation, expandable device/role controls and errors.
**Tests:** player outside scroll on all pages, no duplicated inspector/transport,
short-window fit, keyboard/focus, contrast/hit targets and device recovery.
**Done:** first viewport reserves space for musical content; inspect images at
1536×1024, 1280×900 and 720×900, including scrolled Arrange/Review.

### U03 — Refine Project and MIDI import

**Inspect:** current pages, native file dialogs, import service and projections;
UI references 01 and 02.
**Work:** factual project metrics and next action, honest last-opened support,
one MIDI import well, compact source table/note lane and scoped findings.
Implement and test drop support before advertising it; file chooser remains
available. Add source/authority suggestion labels and recovery without replacement.
**Tests:** empty/ready/error/long name, source protected automatically, format
rejection explanations, keyboard chooser, real track counts and responsive layout.
**Done:** create/open/import/listen is concise and truthful; no audio queue,
cleaning options, fabricated history or source-replacement shortcut.

### U04 — Build compact Structure & Harmony editing

**Inspect:** M03 duration/extent services, M06 plan/purpose, authority drafts and
existing structure page; reference 03.
**Work:** shared section strip; compact musical settings and section rows with
bar totals, duplicate/move/split/remove, explicit chord duration spans and
selected-section context. Surface phrase/purpose suggestions for confirmation.
Show unsaved state and affected work before save; separate arrangement-plan
edits from source structure. Keep keyboard alternatives to dragging.
**Tests:** unequal chord lengths persist, reorder leaves melody fixed, repeated
labels stay distinct, source padding explained, cancel/no-op edits, total
mismatch, scoped invalidation and first-viewport edit/save accessibility.
**Done:** structure and harmonic rhythm are visible over the real melody, not
hidden inside equal-slot text or multiple scrolling cards.

### U05 — Build timeline-first Arrange with plan and repairs

**Inspect:** current Arrange/song map, M06 plan and M09 repair; references 04/07.
**Work:** dominant map/lanes, compact five-style gallery, top-right Create full
draft, selected-section plan/role inspector, per-scope real progress/cancel/retry,
planned rest states and meaningful repair intents. Support a one-bar occurrence
preview by looping its real content rather than demanding a two-bar source.
Use the same plan resolution for preview and full draft; label persisted versus
ephemeral work. Advanced pattern/profile details stay disclosed on demand.
**Tests:** full draft within three actions from ready authority, all visible
lanes and CTA, first/rapid/one-bar preview, latest-wins cancellation, plan
confirmation boundary, exceptions retain style/section and no double playback.
**Done:** the user can understand and hear the whole-song proposal and fix a
selected part without a section/role generation ladder.

### U06 — Finish whole-song Review and Logic export handoff

**Inspect:** Review/Export pages, accepted assembly, batch undo and writer;
reference 04 for Review and 09 for Export.
**Work:** shared lanes, obvious Draft/Accepted identity, Play/Use/Undo and exact
blocker locations. Compare repairs with melody in context and route back without
losing selection. Export a truthful package summary, accepted-role/rest inventory,
files/result/reveal and concise Logic steps. No bitrate/mix/master controls.
**Tests:** draft is playable but not exportable; batch use/undo atomic; role
rest reflected accurately; accepted-only export, stale/missing/locked errors,
no overwrite, reopen and loop continuity between Arrange/Review.
**Done:** real-service import→draft→repair→use→undo→reuse→export passes at all
three sizes, with no project-file edits or fake settings required.

### U07 — Prove visuals, accessibility and responsiveness

**Inspect:** fixture writers, UI_GUIDELINE measurement JSON, complete workflow
and actual native font/density behavior.
**Work:** pin deterministic target-image baselines with actual/expected/diff
artifacts; independently check geometry, palette, focus and hit bounds. Baseline
updates are explicit reviewed changes. Add ready/blocked/error/progress states,
short-window resizing, contrast, keyboard completion and preview/full-song
performance measurements. Prepare six-page comparison for user visual review.
**Tests:** comparator fails shifted panel, wrong primary color/radius and removed
lane; font allowance cannot mask layout; long names/sections/large note counts;
no duplicate player/inspector or undisclosed UI-thread work.
**Done:** automated gates pass and genuine user visual decision is recorded.
Leave WAITING_USER if review is outstanding; Q01/Q02 preparation can continue.

## Product evidence

### Q01 — Evaluate musical improvement and fix failures

**Work:** prepare five varied full-song projects following Validation, with at
least three unseen at evaluation start. Freeze the set before generating final
outputs. Preserve original settings and label any tuned case as development.
Collect user scores/reasons for piano/melody fit, role interaction, structure,
overall usefulness and repair time; compare against M01 where available.
**Tests:** all semantic invariants plus focused regression for every reproduced
failure. Re-evaluate changed engines on all cases, retaining failed results.
**Done:** all declared musical thresholds pass with real ratings; otherwise
record failed bars/intents and add bounded corrective tasks. Never use automated
metrics or the old 5/10 feedback as evidence of a new 8/10 score.

### Q02 — Run the current Logic Pro matrix

**Work:** regenerate frozen packages covering current import, padding, chord
windows, rests, patterns, expressive source and final boundaries. Record build,
source/output hashes and exact macOS/Logic versions. User imports complete and
individual-role files, checks timing/roles/tempo/controllers/drums/end/playback,
then saves/closes/reopens. Preserve the 2026-08-28 record as historical evidence.
**Tests:** writer/manifest semantic re-import and all current export failure cases.
**Done:** actual Logic results meet Validation; marker display may be a cosmetic
finding, musical corruption cannot. WAITING_USER is valid until evidence arrives;
an export test alone cannot complete this task.

### Q03 — Prove clean install and obtain MIDI release decision

**Work:** use an isolated clean checkout of the integrated result; run test/build
and native install/startup without worker, sound library or model. Walk all six
pages and the full MIDI path, verify cleanup measurements and artifact identity.
Recheck that Q01/Q02/U07 evidence applies to the final engine/UI/export versions.
Update README to shipped behavior and keep only concise limitations/evidence.
**Done:** fresh gates, native smoke, user musical/UI decision and applicable Logic
matrix pass. Record final build and user decision in Validation. Do not delete
acceptance evidence or rewrite the source to obtain a pass.

## Optional automation

### A01 — Harden and verify bounded agent execution runner

**Scope:** developer tooling outside the shipped app; selected on 2026-09-06. Read PLAN §11. Harden the prepared Node/installed-CLI runner instead of building another runner.
**Work:** the dependency-free `tools/terra-runner.mjs` runner, one coordinator and writer,
fresh reviewer, worktree per task, stable integration branch, and a strict
result schema containing task/status/base/commit/tests/artifacts/blocker.
Use explicit output/worktree locations and task-specific permission settings.
Coordinator validates actual Git state and commands, never just model JSON.
**Controls:** project lock with owner/PID and safe recovery; dependency and
in-progress checks; maximum tasks, elapsed time and configured usage/spend cap;
at most two fix retries; cancellation/resume; preserve dirty unrelated work.
No automatic public push/merge/upload or paid media job without scoped policy.
**Tests:** dry-run queue selection, double start, crash/restart, reviewer failure,
false success report, changed integration base, missing human evidence, budget
exhaustion and interrupted worktree cleanup. Keep logs out of tracked docs.
**Done:** dry-run and one real low-risk task complete end-to-end; summary points
to reviewable changes. Scheduling is configured only for the requested cadence.

## Optional TABI video companion

All V tasks use [TABI_VIDEO](docs/TABI_VIDEO.md). Build in a separate repository
or independently built/distributed companion chosen in V01. Do not put media
runtime into MIDI Core or reuse the old release/renderer branch. Shared commands
above apply to its equivalent tests/build; core tests apply to integration work.

### V01 — Prove isolated video/media boundary

**Work:** settle companion location and input/output presets; prove owned-media
preview, encode/decode, audio synchronization and native packaging. Inspect one
provider's current API/terms/credit limits without generation first. Record
provider/model constraints, encoder version/license/distribution and measured
resource costs. Request only still-missing generation-budget/media rights inputs
before dependent paid work; no new approval for already authorized scope.
**Tests:** absent companion leaves MIDI app build/install/export unchanged;
short local encode probes first/final frames, streams and duration.
**Done:** exact dependency choice and bounded pilot budget are reviewable; no
unverified codec or generative-continuity promise. No full app scaffold yet.

### V02 — Build approved TABI asset library

**Work:** reconcile character-sheet versus scene details once into an approved
identity bible; old “Moki” text never appears in output. Create a manifest with
hashes, origin/rights/model/version, geometry/anchor/alpha/layer and approvals.
Start with the small pilot kit in TABI_VIDEO; import owned assets and generate
missing assets only within the authorized budget. Keep originals immutable.
**Tests:** missing/hash-mismatched assets, real transparency, dimensions/pivots,
scene compatibility, rights/provenance and approved-version selection.
**Done:** user approves one coherent character/train kit; separate files are
usable in composition, not merely a pretty collage or giant prompt output.

### V03 — Add one cost-bounded animation adapter

**Work:** image-to-video jobs bind approved reference IDs, model/version,
parameters, prompt, seed when supported, budget and provider job ID. Persist
submission before polling; resume uncertain jobs by querying identity rather
than blindly resubmitting. Cache downloaded owned outputs by digest; quarantine
failed identity/loop clips. Provider seeds do not guarantee reproducible video.
**Tests:** timeout/rate limit/partial download, cancel/restart, duplicate request,
unknown-cost admission, budget/retry cap and redacted credentials/logs.
**Done:** several short TABI micro-actions retain identity and fixed train
geometry; failed takes are rejected with real costs recorded. Silent visuals
use the finished soundtrack later; no generated audio replaces the music.

### V04 — Implement deterministic scene composition

**Work:** immutable soundtrack + optional verified MIDI manifest become a
video-job plan. Use explicit bounce offset/tail alignment, rational tick-to-time
and frame rounding, layer/parallax motion, bounded loop schedules and scene
crossfades. Layer scrolling occurs behind the window mask. Reuse approved clips
without monotonously repeating the same episode.
**Tests:** unequal sections, rounding, overlaps, soundtrack longer/shorter,
changed tempo mismatch, stale assets, loop seams and no source/music writes.
**Done:** same accepted assets/job produce the same frame plan; the soundtrack
is neither trimmed/stretched nor remastered silently.

### V05 — Build real video editor and preview

**Work:** adapt reference 08: preview stage, scene thumbnail strip, selected-scene
inspector, supported crop/motion/transition settings and one soundtrack player.
Display asset approval, cost/progress and failures where actionable. Preview
uses the same timing/geometry as output; no unsupported controls or fake progress.
**Tests:** real playback/seek, A/V sync, scene-boundary/crop parity, changed
soundtrack/assets, keyboard, compact layout and cancellation/reopen.
**Done:** user can review an actual full-song pilot before encoding, including
character consistency, loop seams and final scene/audio tail.

### V06 — Encode and validate local outputs

**Work:** bounded owned encoder process with safe arguments, progress, timeout,
cancel, disk-space/error handling; stage, probe and atomically publish to a new
output. Produce chosen video preset and a compact provenance/technical report.
No upload action. Only clean job-owned temporary files.
**Tests:** readable first/final frames, duration/frame rate/dimensions/streams,
soundtrack policy and A/V sync; spaces/Unicode filenames, cancellation, crash,
stale input, destination collision and preservation of previous complete output.
**Done:** playable video matches preview and keeps the exact soundtrack timeline;
no partial output is labeled complete. Core remains independently installable.

### V07 — Complete pilot and optional MIDI handoff

**Work:** finish one original full-song TABI episode with the owned music,
controlled scene evolution and approved character assets. Human review checks
identity, flicker, loop seams, pacing, sound and ending. Record actual generation
cost, retries, reuse, production time and outstanding rights/disclosure decisions.
Add an optional capability-checked launch from MIDI Export only if the companion
is installed; consume the immutable manifest without editing its project.
**Tests:** companion installed/absent, stale snapshot, pilot encode/decode,
A/V/scene checks, companion build/install and core test/build.
**Done:** user approves a usable video and local upload package. Publication,
channel eligibility and monetization are separate user/platform decisions;
there is no fabricated YouTube-readiness score or promised revenue.

## Configured automatic execution

Selected on 2026-09-06: hourly execution, GPT-5.6 Terra with high reasoning for
both implementation and fresh review. All listed tasks are selected, including
A01 and unpaid video implementation. F01 runs first; A01 is prioritized as soon
as F01 passes. A01 remains pending until its full hardening acceptance passes.

The source checkout stays on its existing branch with its edits intact. A local
snapshot includes the documentation revision and existing Gradle/toolchain edits.
Automatic commits advance only the un-checked-out `codex/terra-implementation`
branch. Each task has a detached worktree. Do not check out that branch while the
runner is active; inspect a detached worktree or pause execution first.

Local control: `~/.codex/melotrail-terra/config.json`; pinned reviewed runner:
`~/.codex/melotrail-terra/terra-runner.mjs`. State, logs, JSON results and task
worktrees live there, outside the product. This is execution state, not a second
queue. Read current TASKS from the integration ref, not the unchanged source copy.
The control copy is updated only by the coordinator after reviewing/testing A01.
The current settings are one task per wake, 45 minutes total per run, six admitted
runs per UTC day, at most two repair retries, and 500,000 reported input/output
tokens per run as an admission threshold. Cached input is included. Token usage
is reported after a CLI response; this is **not a hard in-flight token/dollar cap**.
The wall deadline terminates owned processes. No automatic credit reset/purchase.
The heartbeat also defers new work when either available account limit is below
10% remaining. No authorized paid video budget or public push/upload exists.

Commands (Node is developer tooling only; existing Codex login is reused):

```bash
node ~/.codex/melotrail-terra/terra-runner.mjs status
node ~/.codex/melotrail-terra/terra-runner.mjs dry-run
node ~/.codex/melotrail-terra/terra-runner.mjs run
node ~/.codex/melotrail-terra/terra-runner.mjs pause
node ~/.codex/melotrail-terra/terra-runner.mjs resume
node --test tools/terra-runner.test.mjs
```

The runner stages candidate files, checks allowed/protected paths, runs the test
and build gates, binds the fresh review to an exact Git tree, then commits the
implementation and a separate queue update using the actual implementation hash.
A compare-and-swap ref update rejects a changed integration base. It never pushes
or modifies the source checkout. The scheduler owns integration and queue status;
workers leave changes uncommitted. State records are private local files.

An interrupted/failed run retains its worktree and blocks duplicate selection.
The coordinator inspects the recorded PID/process group and artifacts first.
`recover` only removes a provably dead lock; an incomplete/ambiguous owner needs
explicit inspection, never age-based eviction. `defer` records a BLOCKED queue
row on the integration branch while preserving all unfinished files. Resume
independent work next; never automatically recycle a failed task indefinitely.
To retry after a real fix/decision, the coordinator updates only that task to
TODO through a reviewed queue commit and preserves links to prior evidence.
If a crash followed integration, reconcile TASKS and the exact committed ref
before clearing state; do not re-run or overwrite an already integrated task.

Human listening, UI approval, Logic Pro checks, asset approval/rights and paid
budget gates remain real. Prepare their evidence, mark WAITING_USER and continue
unrelated ready work. Missing input is not approval. V01 defaults to an independently
built `companion/` project; media dependencies must not enter the MIDI app.
The scheduler stops when the queue is complete and waits quietly when only
external decisions remain. It runs locally while the computer and app are on.

## Reusable agent prompt

```text
Implement the next dependency-ready mandatory task in TASKS.md for Melotrail,
starting with F01 if none is done. Read AGENTS.md, PLAN.md, the task contract and
its owning references. Inspect actual source and tests before changing anything.
Preserve unrelated edits; use an isolated codex/ worktree from the approved base.
Implement only this task and its necessary deletions. Preserve source MIDI,
accepted candidates, exports and authoritative musical settings. No legacy
compatibility, Python service or audio-production dependency in the MIDI app.
Run focused regression checks, make test, make build and git diff --check.
For UI changes inspect actual captures; for musical/export changes prepare the
relevant comparison/Logic evidence. Do not invent human ratings or manual passes.
Obtain fresh diff review, fix reproduced issues, then commit only task-owned work
if commits are authorized for this implementation run. Report the actual commit,
checks, artifact paths, limitations and next dependency-ready task. Mark a manual
gate WAITING_USER while allowing independent ready work. Do not launch optional
video/automation tasks, public pushes, uploads or paid jobs outside the run scope.
```

For a bounded automatic batch, add the selected task IDs, task/time/usage limits,
integration/commit policy and permitted optional scope. Do not use “implement
everything until done” without these boundaries. The installed runner and hourly heartbeat use the configured policy above;
A01 owns the remaining hardening and end-to-end acceptance.
