# Implementation tasks

Authority: [PLAN](PLAN.md). Updated: 2026-09-13 for the integrated, local-first TABI video replacement. Task status is authoritative in this queue.
The old MC/UI/VID queues are retired. Reuse existing code and tests; do not
replay completed import, draft, acceptance, export or UI-foundation work.

## Execution contract

- One task produces one reviewable implementation commit. Suffixed rows are
  executable slices; the unsuffixed row implements the final slice and validates
  the combined parent contract. Aim for one behavior and a few production owners. Split an unexpectedly
  large task into suffixed subtasks here before editing; keep the parent pending
  until all children pass. Do not create another plan or log file.
- Read AGENTS, PLAN, this task and its owning references. Inspect actual callers
  and tests. For source paths below, `core` means
  `src/main/kotlin/app/melotrail`; `desktop` means
  `desktopApp/src/main/kotlin/app/melotrail/desktop`. Test owners mirror them.
- Dependencies in the queue are mandatory. The coordinator chooses the earliest
  ready mandatory task, skips an external wait, and records why. Optional tasks
  are excluded unless included in the implementation request.
- Configured implementer: Sol High (`gpt-5.6-sol`, `high`). On a concrete
  implementation, test or review failure, use Astra High (`gpt-6-astra`, `high`)
  with the exact failure evidence and preserved candidate. Use a fresh Sol High
  review context; reviewer approval requires evidence, not confidence language.
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
A manual gate stays WAITING_USER until actual evidence arrives. Per the user's
2026-09-11 decision, perform all listening scores, Logic import/play/reopen,
foreground desktop capture and MIDI visual review at the end of unpaid
MIDI engineering. The newer video request introduces an early V24 visual
checkpoint; independent engineering continues while that decision is pending. Do not admit these rows to automatic worker/retry/review runs merely
to rediscover missing evidence. Automated tests, builds and technical review still
run for every implementation; code failures retain their normal recovery policy.
Production rights, identity and spending authorization remain required before
production media use or paid generation; they do not block owned-fixture code.

MIDI engineering is complete; U07/Q01/Q02/Q03 retain their real manual gates.
The user's 2026-09-13 request retires the Swift V01–V07 workstream and its four
production gates. Their code/evidence remain historical until V30–V31 remove
exclusive runtime owners. **V10–V33 are the new video queue**, starting TODO;
old completion does not count toward the replacement. V24/V33 require visual
feedback, and V25 is an optional hosted fallback. The user authorized the
sequential scheduler, Sol High implementation, Astra High failure repair and one
local commit per validated task on 2026-09-13. The first implementation slice is
V10; the scheduler policy below supersedes the retired autopilot configuration.
Q03b's [MIDI review](docs/VALIDATION.md#final-manual-review) remains available.

The current video contract is PLAN §9: one Video tab inside Melotrail, assets +
prompt → real generated clips → a complete 3–5 minute silent video. Local first
on the user's M5 Pro/48 GB Mac; no soundtrack/MIDI prerequisite. Tokyo, the train
and coffee actions were illustrative only. Assets and a free-form prompt drive
all scenarios through the same primary workflow; no required scene preset or
character/background pair. The primary action is Generate video; look/shot
review controls are optional refinements, not prerequisites for a draft. This correction supersedes
older companion/soundtrack guidance in AGENTS and owner documents; V10 aligns
those references. Preserve existing Makefile/README edits until their named
replacement tasks. No docs/tasks.md or second implementation queue.

## Queue

| ID | Task | Depends on | State | Result / implementation commit |
| --- | --- | --- | --- | --- |
| F01 | Verify baseline and real dependency boundaries | — | DONE | f8013adfe12f; Preserved the staged F01 repair. It makes the architecture guard scan the real desktop root and verifies raw MIDI imports with an actual MidiCore page path. Static review found no reproduced issue. Default desktop startup delegates to the MIDI Core composition; its target graph has no worker/model/mixer/renderer construction. No queue edits or commits made.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T15-14-51-831Z-F01 |
| M01 | Freeze musical baseline and comparison harness | F01 | DONE | 7352ec8f050a; baseline fixture, deterministic comparison harness and review form; make test/build + fresh review passed; evidence runs/2026-09-06T17-31-17-824Z-M01 |
| F02 | Delete legacy desktop | M01 | DONE | b22cb8c09d58; Deleted the legacy desktop router, view model, pages, worker/audio/library composition, preferences migration, obsolete theme branches, and exclusive tests. Retained the MIDI Core launcher, composition, six-route shell, theme/primitives, and persistent MIDI player. Fixed the reproduced logger test to assert MIDI-only diagnostics. Next dependency-ready task: F03.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T20-44-18-740Z-F02 |
| F03 | Delete legacy application workflow | F02 | DONE | `596e605923f1`; legacy workflow/config entrypoints removed; `make test`, `make build`, diff check, and fresh coordinator review passed. |
| F04 | Delete obsolete musical generators and model paths | F03 | DONE | b520e0908aa7; Deleted all seven exclusive Qwen fixtures and the obsolete schema-V4 pending-run fixture. Added regression coverage preventing their return. No source MIDI, retained fixtures, Logic evidence, or UI/TABI references changed.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T22-23-31-708Z-F04 |
| F05 | Delete audio/worker runtime and finish schema/build cleanup | F04 | DONE | 9db46bd70a0f; Deleted obsolete audio/DSP/worker/Python runtime and exclusive tests, removed obsolete Make/Python wiring and root dependencies, retained MIDI storage/schema behavior, and added cleanup regression guards. Fixed the prior failure by removing empty retired directories from the worktree. Fresh diff review found no further issue. Next dependency-ready task: F06.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T23-17-34-111Z-F05 |
| F06 | Delete verified legacy data and measure repository reduction | F05 | DONE | `d6adb18ce53c`; 5,250,264 bytes removed (16.1%); `make test`, `make build`, diff check, Sol debug, and fresh Terra review passed. |
| A01 | Harden and verify bounded agent execution runner | F01 | DONE | a139773cdf8f; Requires a non-empty allowed-path policy for every allowlisted task before selection/admission. Added end-to-end coverage proving a now-next A01 with no policy creates no state, run directory, or worktree. Fresh diff review found no reproduced issue.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T15-42-09-677Z-A01 |
| A02 | Reduce runner overhead and recover retained work | A01 | DONE | 87963895d3a1; bounded evidence, saved repair sessions, three-task batches, disjoint M04/M06 workers, safe retained recovery; 39 runner regressions + application test/build, live CLI resume and fresh review passed. |
| A03 | Recover concrete review findings as bounded subtasks | A02 | DONE | Structured findings, preserved candidate and exact-file recovery with independent acceptance checks; one attempt per finding, three findings maximum, 20 min/150k tokens each. 60 runner regressions, 537 application tests/build, live Astra schema smoke and independent Astra Extra High review passed. Evidence ~/.codex/melotrail-terra/finding-recovery-evidence. |
| U01 | Finish verified lanes and live timeline projection | F06 | DONE | `d983d2098d93`; factual shared lanes, real-position observation, stale evidence and device-loss lifecycle coverage; `make test`, `make build`, diff check, Sol debug, and fresh Terra review passed. |
| U02 | Compact shell, player and inspector | U01 | DONE | 7d661a7a8eb1; Fixed review-reproduced U02 gaps: Arrange/Review use one 332dp selected-section inspector column at ≥1440px, retain inline inspectors below that width, and Review draft controls use rectangular shapes. Added reference-width regression coverage. No commit made.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-07T10-37-44-445Z-U02 |
| U03 | Refine Project and MIDI import | U02 | DONE | Native one-file MIDI drop, factual Project/MIDI facts, consistent fractional BPM, scoped accepted/rejected findings and honest recovery; Sol High fixes plus `make test`, `make build`, diff check and fresh Terra review passed. |
| M02 | Derive melody context and harmony-tension evidence | M01, F06 | DONE | 07924ab04ef3; read-only melody/harmony context; fixed 6/8 accents, pickup slicing and PPQ25; 8 focused + 337 full tests, build and fresh review passed; recovered preserved candidate. Musical acceptance remains advisory. |
| M03 | Add explicit harmony durations and source extent | M02 | DONE | fa5bd0dfd3f5; Occurrence source audition now uses the confirmed arrangement end, retaining padded trailing silence without changing source melody events or bytes. Added padded-occurrence regression and MIDI contract note.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-07T18-28-52-249Z-M03 |
| U04a | Expose compact chord-duration and source-end editing | U03, M03 | DONE | Selected-section durations and verified melody/source-end context; unique IDs after remove/add; confirmed-meter positions. 350 tests, build, diff check and fresh Terra review PASS; three sizes inspected. Evidence: ~/.codex/melotrail-terra/u04a-repair-evidence. Visual/Logic acceptance remains at U07/Q02. |
| M04a | Build bounded legal piano voicing choices | M03 | DONE | Bounded open/guide/reduced pool; continuous accepted bass-root coverage guards omission; single deterministic selection path. Engine v2 with safe retained-candidate retries; frozen v1 comparison repaired. 355 tests, build, diff check and fresh Terra review PASS. Evidence: ~/.codex/melotrail-terra/m04a-repair-evidence; listening/Logic pending Q01/Q02. |
| M04b | Rank piano voicings against protected melody | M04a | DONE | Engine v3 ranks M02 overlap, register and per-finding prominence, including held cross-chord suspensions. Validation record and frozen v1/current v3 MIDI pairs refreshed. 366 tests, focused checks, build and fresh Terra review PASS; Q01/Q02 listening/Logic pending. Evidence ~/.codex/melotrail-terra/m04b-repair-evidence. |
| M04 | Improve piano voicing against melody | M04b | DONE | Engine v4 bounded phrase lookahead and durable piano-boundary identity; verified retry/reopen and draft-use checks preserve prior artifacts. V4 snapshots and v1/v4 listening pack refreshed. 372 tests, focused checks, build and fresh Terra review PASS. Q01/Q02 human gates pending; short-section drum fixture recorded for M08. Evidence ~/.codex/melotrail-terra/m04-repair-evidence. |
| M05a | Anchor comping to meter and chord windows | M04 | DONE | Authored 4/4, 3/4 and 6/8 meter phase with exact harmony clipping; sustained offbeat revoicing retains bar attacks, unsupported meters reject explicitly. All real callers bind pattern identity. 379 tests, focused checks, build, Sol High repair and fresh Terra High review PASS. V1/v5 MIDI pack refreshed; Q01/Q02 pending. Evidence ~/.codex/melotrail-terra/m05a-repair-evidence. |
| M05 | Add phrase-aware, meter-aware comping | M05a | DONE | Versioned melody/CC64-aware support and answers, complete inter-phrase bar rests, phrase-end breathing room and preserved voicing across rest-only harmony. Early phrase ends, real publication/reopen/source preservation and six exact MIDI snapshots verified. 388 tests, focused checks, build and fresh Terra High review PASS. Comparison pack refreshed; Q01/Q02 pending. Evidence ~/.codex/melotrail-terra/m05-repair-evidence. |
| M06a | Persist versioned arrangement-plan records | M03 | DONE | 747e34385fcc; Added schema-v3 versioned arrangement-plan persistence, authority validation, role-scoped plan fingerprints, contract update, and reopen/malformed/no-op/protected-artifact regressions. No migration mode added.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-07T22-48-30-296Z-M06a |
| M06b | Create and confirm arrangement-plan proposals | M06a | DONE | Real Arrange propose/confirm/cancel flow with session-held suggestions, readable intent summary, confirmed/reopened state and stale-authority guards. Source/drafts preserved. 363 tests, build, diff check and fresh Terra review PASS; three-size captures inspected. Evidence: ~/.codex/melotrail-terra/m06b-repair-evidence. Plan-edit invalidation and plan-driven generation remain M06/M07. |
| M06 | Persist a deliberate whole-song arrangement plan | M06b | DONE | b7b57ecf27a9; Fixed the stale-write race: confirmed plan edits now run load/verify/preview/save under the shared project write lock. Snapshot capture now shares that lock, and a concurrent acceptance/snapshot regression verifies stale concurrent writes are rejected rather than overwritten.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-08T13-55-11-982Z-M06 |
| M07a | Resolve per-occurrence generation context | M05, M06 | DONE | e7949bd3e7b3; Prepared the M07a candidate for coordinator validation. It resolves role-scoped occurrence, neighbor, repeat, and Bass/Drums groove inputs; requires a confirmed plan through full-draft generation; and fixes over-invalidation from groove-only edits and whole-song piano-boundary hashes. Added regressions for deterministic fingerprints, Chords→Bass→Drums ordering, bounded invalidation, preserved unrelated acceptance, repeat ambiguity, and distant plan edits. TASKS remains untouched per coordinator ownership.; test/build + fresh revie |
| M07b | Represent planned rests in draft and acceptance | M07a | DONE | Schema-v4 typed rests through draft audition and atomic Use/Undo; revision-guarded unlock, inactive-role and rest-dependency guards, scoped invalidation and section repair. 401 tests, focused desktop/core checks, build, diff check and fresh Terra High review PASS. Evidence ~/.codex/melotrail-terra/m07b-recovery-evidence; M07 export and Q02 Logic evidence remain pending. |
| M07 | Generate drafts from plan, boundaries and explicit rests | M07b | DONE | Recovered accepted-rest assembly/export and readiness; verified all-song role omission and exact later Bass entry after intro rest. Materialized both Logic packages; current manifest v2 companion consumer aligned. Focused/full tests, native checks, build and fresh Terra review; evidence ~/.codex/melotrail-terra/m07-repair-evidence. Actual Logic playback remains Q02. |
| M08a | Coordinate bass support with chord and groove intent | M07 | DONE | Recovered shared groove/role fingerprints and repaired reduced-density held support, intro/outro kick ceilings and false pickup advisories. Focused regressions, 424 tests, build and Terra review passed. Evidence ~/.codex/melotrail-terra/m08a-repair-evidence; M08 full-song listening remains pending. |
| M08b | Shape drum fills and section transitions | M08a | DONE | acd724e30c9b; Addressed review feedback: a quiet next Intro/Outro now suppresses its incoming phrase fill and final-bar Bass-derived kick support. Added deterministic Intro and Outro pickup regressions; bumped Drum transition identity to v2 and updated contract notes.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-10T10-40-00-057Z-M08b |
| M08 | Coordinate bass/drums and section transitions | M08b | DONE | Recovered terminal-rest/end-boundary comparison evidence. Explicit uncached focused Bass/Drums/ComparisonHarness execution, 431 tests, build and Terra review passed. Three frozen-baseline/current pairs retained at ~/.codex/melotrail-terra/m08-repair-evidence/comparison-packages; 30 MIDI and 6 manifest digests verified. Listening/Logic approval remains Q01/Q02. |
| M09a | Define scoped deterministic musical repair intents | M08 | DONE | ff881e53ed9c; Repaired the failing transition-scope regression. The invalidation planner canonically orders scopes by occurrence ID then role, so the expected list now matches the real deterministic caller output. Existing M09a implementation and documentation remain otherwise unchanged.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-10T12-13-32-218Z-M09a |
| M09 | Add meaningful alternatives and targeted musical repair | M09a | DONE | Recovered bounded distinct alternatives and melody-inclusive A/B; repaired neighboring dependency generation, durable register identity, atomic batch/boundary validation and same-settings retries. Focused checks, 444 tests, build, diff check and fresh Terra High review passed. Evidence ~/.codex/melotrail-terra/m09-repair-evidence; Q01/Q02 listening/Logic pending. Arrange controls continue in U05b. |
| U04b | Edit sections and confirmed arrangement purpose | U04a, M06 | DONE | Section duplicate/split/move/remove and separately reviewed purpose/phrase confirmation. Repaired canonical test publication, invalid phrase input, section/plan write race and removed-rest evidence preservation. 410 tests, build, diff check and fresh Terra High review PASS; three-size controls inspected. Evidence ~/.codex/melotrail-terra/u04b-recovery-evidence; U04 layout and human visual/Logic gates pending. |
| U04 | Build compact Structure & Harmony editing | U04b | DONE | Recovered shared selection, unsaved impact and total recovery; fixed Compose test import/assertion, wrapped section actions and first-viewport name/save access. Unsaved sections cannot display another occurrence’s chords. 411 tests, build, three-size captures and fresh Terra review; evidence ~/.codex/melotrail-terra/u04-repair-evidence. |
| U05a | Make Arrange lanes and full-draft action dominant | U04, M07 | DONE | Compact five-style gallery/full-draft CTA, selected-section plan/progress, distinct draft/accepted rests and accepted-work precedence. Three-size captures and unclipped wide card bounds, combined test/build and Terra review passed. Evidence ~/.codex/melotrail-terra/u05a-implementation-evidence. |
| U05b | Wire bounded previews and contextual repair actions | U05a, M09 | DONE | Recovered six bounded repair actions and real one-bar loops; fixed saved-plan retry wording with Compose regression. Uncached focused checks, 445 tests/build, three-size visual inspection and fresh Terra review passed. Evidence ~/.codex/melotrail-terra/u05b-repair-evidence; full Arrange flow continues in U05, human gates pending. |
| U05 | Build timeline-first Arrange with plan and repairs | U05b | DONE | Recovered plan-aware ephemeral previews and three-action full-draft playback. Fixed intro fixture, post-confirm exception recovery, late-cancel transport/UI state and variable-precision history ordering; keyboard Enter verified. Uncached focused checks, 450 tests/build, three-size inspection and fresh Terra review passed. Evidence ~/.codex/melotrail-terra/u05-repair-evidence; human musical/Logic/visual gates pending. |
| U06a | Finish whole-song review and atomic decisions | U05 | DONE | Recovered exact Draft/Accepted identity, contextual melody playback, atomic mixed-rest Use/Undo/reuse and scoped blocker routing. Fixed confirmed-end test fixture and playback/candidate lane identity mismatch. Uncached focused checks, 456 tests/build, three-size inspection and fresh Terra High review passed. Evidence ~/.codex/melotrail-terra/u06a-repair-evidence; Logic/listening/visual gates pending; final Export handoff remains U06. |
| U06 | Finish whole-song Review and Logic export handoff | U06a | DONE | e4d2990b8f05; Corrected the reported compilation error by calculating bounds width from right minus left, preserving the 407 dp regression assertion. Changes remain uncommitted.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-11T05-44-43-333Z-U06 |
| U07a | Pin visual comparisons and accessibility regressions | U06 | DONE | Recovered exact image comparisons, geometry and accessibility guards; added 24 coherent accepted/ready and scrolled Review/Export captures, preserving existing baselines (69 total). Uncached focused checks, 537 tests, build, diff check and independent Astra Extra High review passed. Evidence ~/.codex/melotrail-terra/u07a-repair-evidence; native responsiveness/scaling remains U07b and human visual approval U07. |
| U07b | Measure responsiveness and prepare visual review | U07a | DONE | Recovered native resize/capture lifecycle and verified selection retention; 20-sample preview/draft/cancellation measurements meet preparation/generation targets. 28 historical screen captures and 66 six-page comparisons prepared; 543 tests and build passed then. Current automatic native evidence uses explicit frame replay; fresh compositor capture is a separate U07/Q03 gate (Validation). Evidence ~/.codex/melotrail-terra/u07b-repair-evidence; acoustic onset unmeasured, human visual decision remains U07. |
| U07 | Prove visuals, accessibility and responsiveness | U07b, Q03b | WAITING_USER | Final manual review deferred by user until engineering ends. Q03b reconciles current six-page evidence; foreground compositor capture and genuine visual scores remain required, including the recorded capture limitation. Prior failed candidate preserved. |
| Q01a | Prepare frozen musical evaluation packages | U06, M09 | DONE | Recovered immutable evaluation freeze/export and M01 comparison commands; input integrity, reproduction and accepted-only export regressions pass. Focused/full checks, build and independent review recorded in ~/.codex/melotrail-terra/q01a-q02a-repair-evidence. Five final songs (three unseen) and real scores remain Q01. |
| Q01 | Evaluate musical improvement and fix failures | Q01a, Q03b | WAITING_USER | Final listening deferred by user until engineering ends. Five owned/licensed full songs (three unseen) and genuine scores remain required. Preserve Q01 candidate and Q01a outputs; do not retry missing scores as code failures. |
| Q02a | Generate current Logic matrix and manifests | U06, M03, M07, M08 | DONE | Recovered 21 deterministic probes (20 current packages and one expected import rejection), semantic re-import/project reopen and immutable inventories. Export copies omit bank hints while protected source/expression survives. Focused/full checks, build and independent review: ~/.codex/melotrail-terra/q01a-q02a-repair-evidence. Actual Logic import/play/reopen remains Q02. |
| Q02 | Run the current Logic Pro matrix | Q02a, Q03b | WAITING_USER | Final manual Logic import/play/save/reopen deferred by user until engineering ends. Q02a packages are prepared; Q03b refreshes final build identity and instructions. No Logic pass claimed. |
| Q03a | Prove clean native build and startup | F06, U06 | DONE | Recovered clean-install verifier; fixed early companion-crash observation (150 ms→2 s) found under clean-run load, with delayed-crash/persistent-process regressions. Uncached architecture/22 focused checks, 581 full tests, build, private DMG installation/bundled-JVM startup and independent review passed. 73 production Kotlin files/27,702 lines (62.4% fewer than baseline); no source media deleted. Evidence ~/.codex/melotrail-terra/final-engineering-recovery-evidence/q03-*. |
| Q03b | Prepare the final manual-review handoff | Q03a | DONE | Fresh c20aecf583 implementation packets: empty final-song set (5/3 missing), development comparisons, 20 Logic packages/1 expected rejection/571 verified hashes, 66 pinned UI comparisons, 28 frame replays, timing and owned-video demos. Technical checks/build and independent review passed; README/Architecture/Validation reconciled. Evidence ~/.codex/melotrail-terra/final-review-2026-09-13; all human gates remain pending. |
| Q03 | Prove clean install and obtain MIDI release decision | Q03b, U07, Q01, Q02 | WAITING_USER | Final MIDI release decision waits for the end-of-engineering manual review. No release approval inferred from automatic checks. |
| V10 | Align video contracts and queue guards | — | DONE | Owner contracts aligned to planned assets-and-prompt Video tab; V24/V33 guarded and OPTIONAL V25 excluded. 61 Node tests, documentation check, make test/build and diff check passed; fresh Sol High review PASS after one Astra High repair. Evidence ~/.codex/melotrail-video-sequential/evidence/V10. Video runtime/visual acceptance remain later tasks. |
| V11a | Validate local profile and prepare bounded probe requests | V10 | DONE | Pinned request/profile preparation reports NOT_RUN with path, collision and preservation checks. 598 tests, owned probe, make test/build and diff check passed; fresh Sol High review PASS after three Astra repairs (third explicitly authorized by user). Evidence ~/.codex/melotrail-video-sequential/evidence/V11a. Real inference remains V11. |
| V11 | Prove one local generation workflow | V11a | DONE | Measured negative local result: FLUX keyframes succeed; both bounded LTX attempts stop on host memory pressure, no video/backend selected. Two Astra repairs protect original models and confirm cancellation cleanup; latest source audit passes 8/8. 618 tests, make test/build, diff check and fresh Sol High review PASS. Evidence ~/.codex/melotrail-video-sequential/evidence/V11; optional hosted proposal unselected, visual approval remains V24. |
| V12a | Supervise bounded owned media processes | V10 | DONE | Pinned native launch, private jobs, owned process groups and bounded cleanup; 13 real native cases, 611 total tests, build, diff check and fresh Sol High review PASS after one Astra High repair. Evidence: ~/.codex/melotrail-video-sequential/evidence/V12a. Real media proof remains V12. |
| V12 | Prove video-only media runtime | V12a | DONE | Pinned separate FFmpeg 9.0.1 tools prove real decode, seek, frame access and silent VideoToolbox H.264 encode: 72 frames, 320x180, 24 fps, 3 seconds, zero audio streams; source preserved. 622 tests, actual launcher rejection checks, make test/build, diff check and fresh Sol High review PASS after one Astra repair. Evidence ~/.codex/melotrail-video-sequential/evidence/V12. Final delivery/UI remain later tasks. |
| V13 | Persist independent video projects | V10 | DONE | Independent v1 project lifecycle/store, versioned immutable records/selections, artifact hashes, MIDI path isolation and locked atomic revision saves. Real filesystem regressions protect symlink traversal, control-file self-reference and creation collisions. 636 tests, make test/build, diff check and fresh Sol High review PASS after one Astra repair. Evidence ~/.codex/melotrail-video-sequential/evidence/V13. |
| V14 | Import reference assets | V13 | TODO | Planned 2026-09-13; not implemented. |
| V15 | Compile asset prompts and shot proposal | V13, V14 | TODO | Planned 2026-09-13; not implemented. |
| V16 | Persist bounded recoverable jobs | V13 | TODO | Planned 2026-09-13; not implemented. |
| V17 | Connect the chosen local backend | V11, V12, V14, V16 | TODO | Planned 2026-09-13; not implemented. |
| V18 | Generate scene looks with optional review | V14, V15, V16 | TODO | Planned 2026-09-13; not implemented. |
| V19 | Generate clips and preserve takes | V12, V15, V16, V18 | TODO | Planned 2026-09-13; not implemented. |
| V20 | Add Video tab and independent create/open | V13, V16 | TODO | Planned 2026-09-13; not implemented. |
| V21 | Wire uploads brief and local setup | V14, V15, V20, V11 | TODO | Planned 2026-09-13; not implemented. |
| V22 | Wire generation look selection and retry | V17, V18, V19, V20, V21 | TODO | Planned 2026-09-13; not implemented. |
| V23 | Play actual generated video in the tab | V12, V19, V22 | TODO | Planned 2026-09-13; not implemented. |
| V24 | Review real asset-and-prompt generation | V11, V22, V23 | WAITING_USER | Actual visual evidence/decision required; do not auto-admit. |
| V25 | Add selected hosted fallback | V11, V16, V18, V19, V21 | OPTIONAL | Only after local evidence and explicit user selection. |
| V26 | Assemble selected takes to exact duration | V15, V19 | TODO | Planned 2026-09-13; not implemented. |
| V27 | Encode and validate silent MP4 | V12, V19, V26 | TODO | Planned 2026-09-13; not implemented. |
| V28 | Expose full-cut review and export | V23, V26, V27 | TODO | Planned 2026-09-13; not implemented. |
| V29 | Prove installed app and runtime isolation | V20, V23, V28 | TODO | Planned 2026-09-13; not implemented. |
| V30 | Remove MIDI soundtrack companion handoff | V28, V29 | TODO | Planned 2026-09-13; not implemented. |
| V31 | Delete Swift companion and launch wiring | V29, V30 | TODO | Planned 2026-09-13; not implemented. |
| V32 | Verify complete UI and prepare evidence | V22, V23, V28, V31 | TODO | Planned 2026-09-13; not implemented. |
| V33 | Accept complete prompted video and editor handoff | V24, V32 | WAITING_USER | Actual visual evidence/decision required; do not auto-admit. |

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

### M04a — Build bounded legal piano voicing choices

**Scope:** Add open, guide-tone and reduced voicing candidates behind the existing generator. Preserve chord/slash identity, bass space and deterministic fallback. Test legal ranges, required tones, voice crossing and bounded candidate count.
**Inspect:** the M04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M04b — Rank piano voicings against protected melody

**Scope:** Consume M02 overlap/accent/register evidence in deterministic voicing costs. Test sustained close clash versus passing tension and low melody; do not rewrite melody or harmony.
**Inspect:** the M04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M04 — Improve piano voicing against melody

**Remaining parent slice:** Connect phrase-boundary continuity and stable lookahead to the new pool/ranker; generate the baseline/new listening pack and validate the complete M04 contract.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### M05a — Anchor comping to meter and chord windows

**Scope:** Implement authored 4/4, 3/4 and 6/8 phase and exact clipping at harmonic boundaries. Test offbeat changes, odd PPQ, short sections and compound accents.
**Inspect:** the M05 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M05 — Add phrase-aware, meter-aware comping

**Remaining parent slice:** Add activity/phrase-based support, answer and rest selection, version the changed patterns and compare audible development fixtures.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### M06a — Persist versioned arrangement-plan records

**Scope:** Add purpose, phrase/repeat identity, role activity/settings, shared groove and boundary fields with exact persistence and fingerprints. Test reopen, malformed records, no-op identity and protected artifacts; do not add a migration mode.
**Inspect:** the M06 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M06b — Create and confirm arrangement-plan proposals

**Scope:** Add deterministic style-derived proposal and explicit confirmation use cases. Separate suggestion from saved authority and draft. Test no writes before confirmation, rename-independent purpose and cancellation.
**Inspect:** the M06 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M06 — Persist a deliberate whole-song arrangement plan

**Remaining parent slice:** Implement affected-scope preview and dependency invalidation for confirmed plan edits; prove locked/accepted work is preserved and every consumed input is versioned.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### M07a — Resolve per-occurrence generation context

**Scope:** Resolve plan, shared groove, bounded neighbor and repeat inputs in Chords→Bass→Drums order. Test deterministic fingerprints and precise invalidation; keep unrelated accepted scopes intact.
**Inspect:** the M07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M07b — Represent planned rests in draft and acceptance

**Scope:** Make intentional rest distinct from missing/failed output through generation, audition and atomic use/undo. Test cancellation, retry, locked scopes and mixed candidate/rest batch acceptance.
**Inspect:** the M07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M07 — Generate drafts from plan, boundaries and explicit rests

**Remaining parent slice:** Finish accepted-only assembly/export for rests, including all-song inactive-role omission and exact source/end boundaries; update contract and Logic fixtures.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### M08a — Coordinate bass support with chord and groove intent

**Scope:** Implement bounded bass approaches, chord space and shared kick intent without circular dependencies. Test low/held melody, resolution, deterministic fingerprints and 3/4/6/8.
**Inspect:** the M08 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M08b — Shape drum fills and section transitions

**Scope:** Use phrase and next-section intent for complete authored grooves and fills. Test one/two-bar sections, quiet intros, repeat variation and harmony-edge behavior.
**Inspect:** the M08 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M08 — Coordinate bass/drums and section transitions

**Remaining parent slice:** Integrate intentional endings and cross-role boundary regressions; generate full-song baseline/new comparison packages.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### M09a — Define scoped deterministic musical repair intents

**Scope:** Map the six PLAN repair intents to bounded versioned settings and affected role/occurrence sets. Test precise invalidation, locked work and no accepted/source mutation.
**Inspect:** the M09 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M09 — Add meaningful alternatives and targeted musical repair

**Remaining parent slice:** Rank up to three semantically distinct alternatives, reject cosmetic duplicates and wire real preview/apply intents with baseline context and honest no-result reasons.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### U04a — Expose compact chord-duration and source-end editing

**Scope:** Use M03 services in a compact Structure & Harmony duration inspector with real melody/section context and explicit padding confirmation. Keep current plan behavior. Test unequal durations, cancel/save, non-bar source end and keyboard access; capture all three reference sizes.
**Inspect:** the U04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U04b — Edit sections and confirmed arrangement purpose

**Scope:** Add compact duplicate/move/split/remove section rows and purpose/phrase suggestion confirmation. Test repeated identity, source immutability and keyboard alternatives.
**Inspect:** the U04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U04 — Build compact Structure & Harmony editing

**Remaining parent slice:** Finish unsaved/affected-work preview, total-mismatch recovery and responsive shared-strip layout; inspect reference 03 and the three-size captures.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### U05a — Make Arrange lanes and full-draft action dominant

**Scope:** Build the reference 04 timeline composition, compact five-style gallery and selected-section plan/role inspector with factual rest/progress states. Test primary-action visibility and selection at all three sizes.
**Inspect:** the U05 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U05b — Wire bounded previews and contextual repair actions

**Scope:** Wire actual plan/repair services, latest-wins cancellation and one-bar looping. Test ephemeral versus persisted state, retry scope and no duplicate player.
**Inspect:** the U05 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U05 — Build timeline-first Arrange with plan and repairs

**Remaining parent slice:** Complete real-service ready-authority→draft playback within three actions, recovery and keyboard flow; inspect actual images and validate full/preview plan parity.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### U06a — Finish whole-song review and atomic decisions

**Scope:** Use shared lanes for clear Draft/Accepted identity, contextual repair comparison and Play/Use/Undo. Test batch atomicity, rests, selection/loop continuity and exact blocker routing.
**Inspect:** the U06 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U06 — Finish whole-song Review and Logic export handoff

**Remaining parent slice:** Finish accepted-only Export summary, immutable result/reveal and concise Logic handoff; prove real-service import→repair→use→undo→reuse→export at all three sizes.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### U07a — Pin visual comparisons and accessibility regressions

**Scope:** Create deterministic actual/expected/diff image artifacts and independent geometry, color, focus and hit-bound checks. Prove comparator rejects shifted panels, wrong primary color/radius and missing lanes; do not auto-approve changed goldens.
**Inspect:** the U07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U07b — Measure responsiveness and prepare visual review

**Scope:** Exercise large songs, long names, short windows and native density; measure the specified preparation/cancellation timings on a recorded machine. Prepare six-page image comparison and report unmeasured acoustic onset honestly.
**Inspect:** the U07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U07 — Prove visuals, accessibility and responsiveness

**Remaining parent slice:** Present the completed six-page evidence and record the actual user visual decision in Validation. If missing, record WAITING_USER; no new implementation is required merely to request this gate.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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
**Done:** automated gates, fresh `:desktopApp:nativeDesktopCapture` and genuine user visual decision pass. Frame replay cannot substitute for compositor evidence.
Leave WAITING_USER if review is outstanding; Q01/Q02 preparation can continue.

## Product evidence

### Q01a — Prepare frozen musical evaluation packages

**Scope:** Build the comparison/evaluation export command and compact score forms with source/settings/version hashes. Freeze only supplied owned final cases; explicitly flag any missing three unseen songs and keep synthetic development cases separate. Test reproducibility and case integrity.
**Inspect:** the Q01 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### Q01 — Evaluate musical improvement and fix failures

**Remaining parent slice:** Collect genuine scores against Validation thresholds; record specific failed bars as bounded corrective queue work. Missing songs or ratings are WAITING_USER and do not block independent engineering.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

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

### Q02a — Generate current Logic matrix and manifests

**Scope:** Prepare deterministic import/extent/harmony/rest/controller/ending packages and semantic re-import checks with build/source/output identity. Produce concise import/play/reopen instructions; never claim actual Logic playback.
**Inspect:** the Q02 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### Q02 — Run the current Logic Pro matrix

**Remaining parent slice:** Record real user Logic import/play/reopen results and exact versions against the frozen current packages, or WAITING_USER with the prepared artifacts.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** regenerate frozen packages covering current import, padding, chord
windows, rests, patterns, expressive source and final boundaries. Record build,
source/output hashes and exact macOS/Logic versions. User imports complete and
individual-role files, checks timing/roles/tempo/controllers/drums/end/playback,
then saves/closes/reopens. Preserve the 2026-08-28 record as historical evidence.
**Tests:** writer/manifest semantic re-import and all current export failure cases.
**Done:** actual Logic results meet Validation; marker display may be a cosmetic
finding, musical corruption cannot. WAITING_USER is valid until evidence arrives;
an export test alone cannot complete this task.

### Q03a — Prove clean native build and startup

**Scope:** Run isolated clean install/package/startup and the automated six-page MIDI path without worker/model/sound library. Foreground `:desktopApp:nativeDesktopCapture` is deferred to U07 final manual review; keep automated real-window frame replay and all other technical checks. Record build identity, reduction and any concrete install regressions; preserve source media and prior evidence.
**Inspect:** the Q03 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### Q03b — Prepare the final manual-review handoff

**Dependencies:** Q03a. DONE; retain the existing MIDI evaluation, Logic matrix,
UI/native-install and manual-review packets. The old companion portion is
historical evidence, not acceptance of the 2026-09-13 video replacement.
V32 prepares new video evidence; V33 owns its real visual decision. Do not replay
this completed task or reinstate the retired V gates.

### Q03 — Prove clean install and obtain MIDI release decision

**Remaining parent slice:** Reconcile acceptance evidence with the final engine/UI/export versions, update README to shipped behavior and obtain the actual MIDI release decision.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** use an isolated clean checkout of the integrated result; run test/build
and native install/startup without worker, sound library or model. Walk all six
pages and the full MIDI path, verify cleanup measurements and artifact identity.
Recheck that Q01/Q02/U07 evidence applies to the final engine/UI/export versions.
Update README to shipped behavior and keep only concise limitations/evidence.
**Done:** fresh gates including `:desktopApp:nativeDesktopCapture`, native smoke, user musical/UI decision and applicable Logic
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

### A02 — Reduce runner overhead and recover retained work

**Work:** bound review evidence to the current candidate and explicit check logs;
keep transcripts out of prompts; resume implementation context for focused
repairs, with fresh reviews and Sol High escalation after two failed attempts.
Run bounded continuous batches with explicitly owned independent workers and
serialized revalidation/integration. Preserve interrupted candidates and actual
failure reasons; never reset budgets within a batch or recycle failures forever.
Recover the retained M02 candidate after fresh review. Install only tested runner
code and preserve the user's checked-out branch and staged changes.
**Tests:** original runner gates plus real-process fixtures for session reuse,
transcript isolation, bounded evidence, task/time/usage limits, retained retries,
overlapping ownership, actual worker concurrency and combined-tree validation.
**Done:** required checks and fresh reviews pass, installed script matches Git,
M02 is recovered, and the existing heartbeat uses the tested bounded policy.

### A03 — Recover concrete review findings as bounded subtasks

**Scope:** Extend the existing runner after its normal retry/Sol repair sequence.
A fresh reviewer returns concrete finding IDs, exact owned files and verifiable
acceptance conditions. Up to three code findings become ordered execution
subtasks under the retained parent; never create a second product queue.
**Work:** one fresh Astra attempt per finding, one finding per wake, capped at
20 minutes and 150,000 reported tokens. Preserve the original contract, diff,
check errors and completed work. Validate the entire candidate and independently
resolve each original finding; integrate only after the whole parent passes.
Stop on unchanged/unresolved work, failed checks, scope violations, interruption
or exhausted limits. Human/environment/scope blockers stay outside recovery.
**Tests:** real-process fixtures for ordered partial repair and final integration,
exact ownership, preserved checkpoints/history, budget/pause/admission controls,
malformed reviews, interrupted recovery and no-progress deferral. Run the runner
suites, application test/build and fresh review before installing the script.

## Integrated generation from assets and a prompt — V10–V33

### Execution and acceptance contract for this workstream

Implement only the selected row after its listed dependencies are DONE. Each
row below supplies exact repository-relative target files (new paths are marked
new), inputs, rules, verification and an observable result. The coordinator owns
TASKS/status, shared-file integration and any allowed-path configuration. Agents
must not expand scope or revive V01–V07. The user has now authorized sequential
execution through the scheduler below; do not launch parallel task writers.

Every code slice runs its focused command below, then `make test`, `make build`
and `git diff --check`. Ordinary tests use owned media/fake providers and never
install a model, submit a cloud job or depend on paid credentials. Model/media
host probes are separate explicit commands, with measured evidence under ignored
`build/video/` or another retained owned directory. A missing native prerequisite
is reported as unavailable, never as a passing/skipped production proof.

Existing checkout baseline: the prior Makefile task's `make test` and `make build`
each reported 334 root tests with one failure in
`TargetArchitectureRulesTest.legacy data payloads and their application consumers
remain removed`, caused by pre-existing `.venv-worker`, `data/audio` and `sounds`.
These are unrelated untracked data; do not delete them or weaken the assertion.
Use a clean isolated checkout for implementation verification and preserve this
checkout. Current tracked Makefile/README/companion-README edits are intentional.

Start with Draw Things CLI + one measured local profile; V11 can select the
single ComfyUI alternative if automation/reference support fails. Do not build
both local adapters. No silent cloud fallback. V25 stays OPTIONAL unless the
user selects the proposed hosted service after local evidence. V24 can record
local failure and remain WAITING_USER while V25 is activated; V25 deliberately
does not depend on V24 being DONE. If V11 rejects local feasibility, the
coordinator changes V22's V17 dependency to V25 only after the user chooses that
route; V17 stays explicitly blocked/unselected and does not hold the hosted path.
V24 completes only when one chosen route has
real acceptable visual evidence. V33 depends on that decision.

A 3–5 minute deliverable is mandatory. A short test, image gallery, API wrapper,
manual JSON/CLI workflow or working synthetic encode cannot close V33. Show
unique versus reused footage and real joins. Default to unique footage until the
user explicitly chooses reuse; their preference is currently pending. Both modes
share the same shot planner and UI, with no hidden repeat-to-fill behavior. Audio sync and public upload are
excluded. Required human decisions cannot be inferred from source scans or tests.

When a future implementation run explicitly requests parallel agents, safe
initial pairs are V11 + V13 and, after prerequisites, V14 + V16. V11/V12
share Gradle/documentation owners and must run sequentially. Both writers
must have disjoint exact file lists and no shared resource/build edits. The
coordinator serializes Gradle/build-file, app-shell, documentation, runner and
removal changes. Default to one writer and a fresh reviewer; workers do not spawn
other workers. If a row exceeds a focused pass, the coordinator splits it here
before expanding file ownership. No fixed token budget is created by this plan.

### V10 — Align contracts and protect the new queue

**Target files:** `AGENTS.md`, `README.md`, `docs/ARCHITECTURE.md`,
`docs/TABI_VIDEO.md`, `docs/UI_GUIDELINE.md`, `docs/VALIDATION.md`,
`tools/terra-runner.mjs`, `tools/terra-runner.test.mjs`.
**Inputs / dependencies:** None; PLAN §9 and the 2026-09-13 user correction.
**Implementation rules:** Replace the separate-app/soundtrack requirement with
an independent Video tab and silent output. Preserve artistic references and
historical evidence with clear supersession; do not claim planned controls
already exist. Keep six MIDI destinations and separate video storage/runtime.
Update runner human-gate protection from retired V gates to V24/V33, ensure V25
cannot be selected while OPTIONAL, and add queue tests. Never change the user's
active heartbeat/configuration during this task. The coordinator handles any
future allowlist with exact row paths; retire old V IDs from that run.
**Verification command:** `node --test tools/terra-runner.test.mjs tools/terra-throughput.test.mjs`; `./gradlew :test --tests 'app.melotrail.documentation.DocumentationIntegrityTest'`.
**Done:** all owner guidance describes the same new product, and automated agents
cannot mark V24/V33 complete or run an unchosen cloud task.

### V11a — Validate local profiles and prepare bounded probe requests

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/LocalVideoProfile.kt`
(new), `src/test/kotlin/app/melotrail/video/VideoLocalFeasibilityCheck.kt` (new),
`src/test/kotlin/app/melotrail/video/LocalVideoProfileTest.kt` (new),
`src/main/resources/video/local-profile.json` (new), `build.gradle.kts`,
`docs/TABI_VIDEO.md` (local profile/probe preparation section only).
**Inputs / dependencies:** V10. Setup-independent slice of V11; no model download,
installed inference executable, production references or paid account required.
**Implementation rules:** Define a versioned local-profile/probe-request contract
and validate required tool/model/reference pins, supported local backend identity,
reference bindings, prompt, bounded attempts and explicit output locations. Keep
candidate/unverified configuration separate from measured/selected facts; never
ship invented model digests, supported flags or runtime/quality measurements.
Register `videoLocalProbe` on the test classpath to validate and prepare a supplied
request, reporting missing setup or NOT_RUN explicitly. Preparation is read-only
for tools/models/references, rejects missing/mismatched files and preserves existing
outputs. It never executes arbitrary commands, downloads, starts inference or
contacts a provider. Tests use small owned files and malformed requests to prove
pin validation, reference preservation, bounds and honest unmeasured outcomes.
Document exact preparation usage and that real invocation, measurement and model
selection remain V11. Do not implement the V17 provider adapter or V12 media runtime.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.LocalVideoProfileTest'`;
`./gradlew :videoLocalProbe` must fail with actionable missing-request guidance;
prepare an owned fixture request and verify an explicit NOT_RUN/setup report.
**Done:** the local request/profile boundary and preparation command are tested
and usable without a model, with no fake inference or production-readiness claim.

### V11 — Prove and pin one local generation workflow

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/LocalVideoProfile.kt`
(new), `src/test/kotlin/app/melotrail/video/VideoLocalFeasibilityCheck.kt` (new),
`src/test/kotlin/app/melotrail/video/LocalVideoProfileTest.kt` (new),
`src/main/resources/video/local-profile.json` (new), `build.gradle.kts`,
`docs/TABI_VIDEO.md` (measured local decision section only).
**Inputs / dependencies:** V11a. User-selected references and an explicit local
model setup/download choice for real inference; preparation is already owned by
V11a and does not establish model feasibility. Initial machine: M5 Pro, 20 GPU cores, 48 GB memory.
Missing setup is WAITING_USER for the real host probe; do not label a fake probe
as measured local evidence.
**Implementation rules:** Register `videoLocalProbe` on the test classpath. Test
Draw Things released CLI with a supported reference/editing model and LTX-2.3
distilled I2V. Pin full model dependencies, quantization, tool digest, reference
limits, input/output formats and offline flags. Prove uploaded reference assets
and a free-form prompt condition a scene keyframe and a real 5–10 second video.
Exercise a single-reference request and multiple reference roles; a particular
character, background image, location or action must not be required.
Try one Wan I2V alternative only for a reproduced failure. If needed assess
ComfyUI's exact local API/workflow; select one backend in the same profile,
without arbitrary custom nodes or invented dtype/MPS support. Bound the probe
(maximum two video profiles, three takes each); stop on memory pressure. Record
cold/warm runtime, memory/swap, quality failures, output cadence and projected
four-minute effort. Verify no remote fallback and review the selected model
terms. A failed local trial produces an explicit fallback recommendation, not
an endless model search or a fabricated pass; it need not block independent code.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.LocalVideoProfileTest'`; real host probe: `./gradlew :videoLocalProbe -PvideoProbeRequest=/absolute/path/to/probe.json`.
**Done:** a versioned, automatable profile and measured recommendation, or a
concrete local failure with the optional hosted proposal. Visual acceptance is V24.

### V12a — Supervise bounded owned media processes

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/VideoMediaProcess.kt`
(new), `src/test/kotlin/app/melotrail/video/VideoMediaProcessTest.kt` (new),
`build.gradle.kts` (one pinned native-access dependency only if required),
`docs/TABI_VIDEO.md` (bounded media-process section only).
**Inputs / dependencies:** V10. Native owned child-process fixtures; no models,
character assets or installed FFmpeg build required.
**Implementation rules:** Add a lazy video-only process supervisor for macOS
arm64. Pin the selected executable's actual bytes, pass argument arrays without
shell interpolation, use a private per-job working directory, and bound time and
captured stdout/stderr. Validate path identity before launch; never silently
normalize dot/dot-dot through symlinks or replace supplied inputs/previous jobs.
Launch children in an atomically owned process group (or equivalent proven
ownership); a post-launch group assignment or descendant-polling race must not
lose children when a parent exits. Support cancellation before/during launch and
execution, timeout, output overflow, failed launch and nonzero/crash exit; drain
pipes and terminate/reap owned work without hanging or touching unrelated
processes. Preserve bounded diagnostics and report failure honestly, including a
child-reported disk error. Native access may use one version-pinned JNA dependency
loaded only by this video adapter; do not link Swift, introduce an inference or
media framework, or add startup work to MIDI. Inspect historical supervision only
for proven behavior, without calling or importing the companion. Tests launch
small owned fixtures on the real host for Unicode/spaced paths, path/pin rejection,
stdout/stderr limits, deadline/cancel, early parent exit with a live descendant,
nonzero/disk diagnostics, input preservation and unrelated-process survival.
Do not implement the V12 media decode/encode API or claim codec/network/visual
acceptance from process fixtures.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoMediaProcessTest'`;
all required native cases must actually run on this Mac, plus normal project checks.
**Done:** bounded native process ownership/lifecycle and failure behavior are
proven; FFmpeg distribution and real media operations remain V12.

### V12 — Prove the video-only media process boundary

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/VideoMediaProcess.kt`, `src/main/kotlin/app/melotrail/video/adapter/VideoMediaProbe.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoMediaProcessTest.kt`,
`src/test/kotlin/app/melotrail/video/VideoMediaHostCheck.kt` (new),
`src/test/resources/fixtures/video/owned-motion.mp4` (new), `build.gradle.kts`,
`docs/TABI_VIDEO.md` (media runtime decision only).
**Inputs / dependencies:** V12a. Reuse its proven process supervisor. Owned generated fixture; no character assets/model.
**Implementation rules:** Select a pinned FFmpeg/ffprobe build for macOS arm64,
record its source/distribution/digest/build options and notices, and prove decode,
frame access, seek and silent H.264 encode (prefer available VideoToolbox).
Register test-classpath `videoMediaProbe`. Launch with argument arrays, bounded
output/timeout and per-job directories; cancel/reap only owned process trees.
Handle missing binary, crash, disk exhaustion and Unicode paths without harming
inputs. Media decoding must not read arbitrary network URLs or launch at MIDI
startup. Do not link the old Swift package or add audio processing.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoMediaProcessTest'`; `./gradlew :videoMediaProbe -PvideoToolsDirectory=/absolute/path/to/tools`.
**Done:** real owned frames and silent file decode correctly on the host, with
process lifecycle/error evidence and one chosen distribution strategy.

### V13 — Persist independent video projects and immutable records

**Target files:** `src/main/kotlin/app/melotrail/video/domain/VideoProject.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/VideoProjectStore.kt` (new),
`src/main/kotlin/app/melotrail/video/application/VideoProjectLifecycle.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoProjectStoreTest.kt` (new),
`src/test/kotlin/app/melotrail/architecture/TargetArchitectureRulesTest.kt`.
**Inputs / dependencies:** V10.
**Implementation rules:** One current video schema, independent create/open/save,
versioned references/looks/takes, selected IDs and export records. Require a video
root outside MIDI projects/exports; account for existing parents, symlinks and
path escape. Atomic saves with revision/concurrent-write checks; reject unsupported
schemas before writing and preserve originals. No Swift session or MIDI schema
migration. Add architectural negative controls for video→MIDI storage writes,
MIDI→video imports, and I/O in video domain code.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoProjectStoreTest' --tests 'app.melotrail.architecture.TargetArchitectureRulesTest'`.
**Done:** a video project reopens independently; corrupt saves and MIDI paths
cannot destroy current work.

### V14 — Import reference assets safely

**Target files:** `src/main/kotlin/app/melotrail/video/domain/VideoAsset.kt` (new),
`src/main/kotlin/app/melotrail/video/application/VideoAssetImport.kt` (new),
`src/main/kotlin/app/melotrail/video/adapter/VideoImageFiles.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoAssetImportTest.kt` (new).
**Inputs / dependencies:** V13.
**Implementation rules:** Decode PNG/JPEG initially; support optional subject/character,
environment, style and complete-scene roles without requiring a role combination.
Preserve raw source bytes, copy to immutable owned paths,
produce bounded thumbnails and record hashes/dimensions. Reject unsupported or
oversized/corrupt content with specific guidance. A duplicate reuses the exact
asset identity without overwriting. Derivative resizing/metadata removal is
separate. Never auto-approve identity or treat inspiration artwork as licensed
production input. No manually authored asset manifest or mask requirement.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoAssetImportTest'`.
**Done:** selected references are usable, hash-pinned and recoverable after reopen.

### V15 — Compile free-form prompts and a duration-aware shot proposal

**Target files:** `src/main/kotlin/app/melotrail/video/domain/VideoBrief.kt` (new),
`src/main/kotlin/app/melotrail/video/application/VideoShotPlanner.kt` (new),
`src/main/kotlin/app/melotrail/video/application/VideoPromptCompiler.kt` (new),
`src/main/resources/video/video-generation-guidelines.json` (new),
`src/test/kotlin/app/melotrail/video/VideoPromptCompilerTest.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoShotPlannerTest.kt` (new).
**Inputs / dependencies:** V13, V14.
**Implementation rules:** Free-form prompt is the primary input alongside
selected assets; reference roles and action/camera/motion/style guidelines are
optional refinements. Compile only backend-supported inputs and preserve the
user's prompt. No required preset or separate Custom mode, fixed camera, moving
background or injected action list. A generic shot proposal divides 180–300
seconds (default 240), retaining the prompt with optional user-written per-shot
overrides; do not invent semantic story parsing or hardcoded scenario templates.
Default to unique footage (roughly 30–60 useful short takes for
four minutes before transition/trim adjustments). An explicit reuse option may
propose 12–18 distinct takes and reviewed repeats; show the real unique/reused
seconds and revised local time estimate for each mode. No separate
LLM, automatic approval or silent reference omission. Hash all consumed inputs
and template versions; clip generation is not promised deterministic. Changes
invalidate only dependent pending requests, retaining earlier takes.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoPromptCompilerTest' --tests 'app.melotrail.video.VideoShotPlannerTest'`.
**Done:** the exact proposed prompts, asset bindings and unique/reused duration
are inspectable before inference. Contrasting prompts and changed assets use
the same pipeline; regressions reject injected Tokyo/train/coffee requirements.

### V16 — Persist bounded jobs and recover interrupted work

**Target files:** `src/main/kotlin/app/melotrail/video/domain/VideoGenerationJob.kt`
(new), `src/main/kotlin/app/melotrail/video/application/VideoJobCoordinator.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/VideoJobStore.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoJobCoordinatorTest.kt` (new).
**Inputs / dependencies:** V13.
**Implementation rules:** Define backend and setup-capability ports for
keyframe/video requests, availability/model requirements and explicit setup
actions so UI code can compile independently of the chosen adapter. Durable
attempts before launch, one local inference at a time, cancellation scoped to
owned work, bounded retries and restart reconciliation. Keep request/attempt/
output identities distinct. Unknown progress stays unknown. Local mode records
time/resources; hosted admission additionally requires a current estimate and
explicit spend cap. Reserve in-flight maximum cost across concurrent callers.
Never auto-retry a possibly charged request or equate stopped polling with
provider cancellation. Fixtures cover crash windows and late completion.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoJobCoordinatorTest'`.
**Done:** restart/retry/cancel cannot duplicate admission or overwrite a prior take.

### V17 — Connect the selected local backend

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/LocalVideoBackend.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/LocalVideoSetup.kt` (new),
`src/test/kotlin/app/melotrail/video/LocalVideoBackendTest.kt` (new),
`src/test/kotlin/app/melotrail/video/LocalVideoSetupTest.kt` (new).
**Inputs / dependencies:** V11, V12, V14, V16. Use V11's selected profile; if local
is rejected, coordinator marks this row BLOCKED with its evidence and activates
V25 only after user selection. Common UI/application work can use the backend port.
**Implementation rules:** Invoke the pinned local CLI, or the one proven ComfyUI
loopback API profile, without a second adapter. Check executable/model versions
and availability before running; no automatic download/cloud fallback. Supply
all selected references using the proven conditioning workflow and return owned
actual image/video results. Correlate job-specific progress and staged outputs.
For shared local servers cancel only a verified owned prompt; never clear the
queue or globally interrupt unrelated jobs. Tests use a fake binary/local server.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.LocalVideoBackendTest' --tests 'app.melotrail.video.LocalVideoSetupTest'`.
**Done:** real backend results are reachable through the same port as tests;
missing/offline models produce actionable setup state without affecting MIDI.

### V18 — Generate reference-conditioned scene looks with optional review

**Target files:** `src/main/kotlin/app/melotrail/video/application/VideoSceneLooks.kt`
(new), `src/test/kotlin/app/melotrail/video/VideoSceneLooksTest.kt` (new).
**Inputs / dependencies:** V14, V15, V16. Works against the port; concrete local
integration is V17, optional hosted integration V25.
**Implementation rules:** Submit look-generation jobs from the selected assets
and free-form prompt, including valid single-reference requests. Generate missing
scene elements from the prompt, without requiring a character/background pair.
Retain results and exact provenance. Offer optional approval of an immutable look
or selection of an already composed reference. The primary Generate video flow
may use a generated draft
look automatically; record it as unreviewed, never as user-approved. Feed its
exact identity to dependent video requests. A changed brief/reference leaves
earlier looks available but visibly stale for new work. Reject missing/digest-changed results.
No prompt-only substitute that discards the supplied reference assets.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoSceneLooksTest'`.
**Done:** the exact selected/generated scene look and its review status anchor
later requests after reopen; creating a draft needs no manual keyframe step.

### V19 — Generate clips and preserve independent takes

**Target files:** `src/main/kotlin/app/melotrail/video/application/VideoClipGeneration.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/VideoResultImport.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoClipGenerationTest.kt` (new).
**Inputs / dependencies:** V12, V15, V16, V18.
**Implementation rules:** Generate video orchestrates needed draft scene looks
and shots from the prompt, reference pins and optional shot overrides. Use the
exact supplied/selected/generated look and retain its actual review status.
Respect requested motion and camera behavior; do not inject predefined actions
or scenery. Import actual bounded result bytes, decode and probe them; record
native size/frame rate/duration/audio before normalization.
Require valid output before a take is reviewable. Rejected/regenerated takes remain
immutable; acceptance changes only the selected ID. Unsupported output, stale
completion or lost downloads never replace earlier selected work. Batch submission
shares coordinator limits and reveals its actual shot count and estimated effort.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoClipGenerationTest'`.
**Done:** an actual clip can be generated, reopened, rejected and replaced without
losing earlier work; wrong/malformed results never count as success.

### V20 — Add the Video tab and independent create/open flow

**Target files:** `desktopApp/src/main/kotlin/app/melotrail/desktop/DesktopMain.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreDesktopComposition.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/MelotrailAppShell.kt` (new),
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoDesktopComposition.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoWorkspace.kt`
(new), `desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoWorkspaceTest.kt`
(new), `desktopApp/src/test/kotlin/app/melotrail/desktop/MelotrailAppShellTest.kt`
(new), `desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreDesktopCompositionTest.kt`.
**Inputs / dependencies:** V13, V16.
**Implementation rules:** One window, app-level MIDI/Video tabs above the existing
MIDI shell; preserve its six destinations and one MIDI player. Video create/open
is available with no MIDI project. Keep its state when switching tabs/projects;
load video dependencies lazily. Entering Video pauses MIDI, retaining position.
Parse an explicit `--video` startup option without breaking native startup-check
arguments; render a real independent empty/setup state. Close video-owned work
cleanly while preserving persisted jobs. No Swift launch.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.MelotrailAppShellTest' --tests 'app.melotrail.desktop.video.VideoWorkspaceTest' --tests 'app.melotrail.desktop.MidiCoreDesktopCompositionTest'`.
**Done:** `./gradlew :desktopApp:run --args='--video'` opens the tab directly;
MIDI still works when every optional video tool is absent.

### V21 — Wire asset upload, brief and local setup controls

**Target files:** `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoAssetsPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoBriefPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoSetupPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoWorkspace.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoInputFlowTest.kt` (new).
**Inputs / dependencies:** V14, V15, V20, V11.
**Implementation rules:** Real chooser and native drag/drop, previews/reference
roles, a prominent free-form prompt, optional guideline controls, 3–5 minute
duration and model availability. One or more assets plus a prompt are sufficient
creative inputs; preset selection and background/character pairs are optional.
Show an explicit install/download choice with size/location and progress; use
V11's profile, not arbitrary commands. When local is unavailable preserve the
brief and explain setup. Keep the same prompt-first flow for every scenario; no hand-edited
JSON. Do not build a model marketplace or silently activate a hosted provider.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoInputFlowTest'`.
**Done:** references + brief can be supplied entirely in the application at all
three supported fixture sizes; cancelled imports leave previous work intact.

### V22 — Wire look selection, generation and retry controls

**Target files:** `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoGenerationPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoTakeGallery.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoWorkspace.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoDesktopComposition.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoGenerationFlowTest.kt`
(new).
**Inputs / dependencies:** V17, V18, V19, V20, V21. If the local route was
rejected and the user selected hosted generation, the coordinator replaces V17
with V25 in this row and its queue entry before admission. No fake-only completion.
**Implementation rules:** Connect the primary Generate video action from assets
and a prompt through the full production look/clip pipeline. Keep Generate look,
Use look and per-shot controls in optional refinements; no storyboard or keyframe
approval is required for a draft. Wire Cancel, Retry and Keep/reject take controls. Show
shot list, selected references, local estimate or hosted quote, actual job states
and errors. Concrete backend is supplied by V17 or V25; fake-only wiring cannot
be presented as completed integration. Handle tab changes, restart, missing tools,
late results and duplicate clicks without lost selections or duplicate jobs.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoGenerationFlowTest'`.
**Done:** GUI actions dispatch through production use cases and expose real results;
no API/CLI-only delivery, fictional percentages or synthetic production previews.

### V23 — Play actual generated video inside the tab

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/VideoPreviewDecoder.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoPreview.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoTakeGallery.kt`,
`src/test/kotlin/app/melotrail/video/VideoPreviewDecoderTest.kt` (new),
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoPreviewTest.kt` (new).
**Inputs / dependencies:** V12, V19, V22.
**Implementation rules:** Decode actual file frames through the proven media
boundary with bounded buffers and off-UI-thread work. Play/pause/seek/frame-step
and resize accurately; show unavailable/corrupt-file states. Measure actual frame
progress, timing, CPU/memory and process cleanup; no preview made from a still.
Suppress all source audio. Retain one preview session, stop it on tab departure,
and never interfere with MIDI state or start another MIDI player.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoPreviewDecoderTest'`; `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoPreviewTest'`.
**Done:** moving frames, seeking and clean teardown are proved with owned media
and an actual app-window capture.

### V24 — Review real asset-and-prompt generation before claiming product fit

**Target files:** `docs/TABI_VIDEO.md`, `docs/VALIDATION.md` (compact evidence and
actual decision only; generated media stays outside tracked documentation).
**Inputs / dependencies:** V11, V22, V23. One working route from V17 or explicitly
chosen V25; user-selected source references, local setup and, only for cloud, a concrete
authorized budget. WAITING_USER until real evidence/feedback exists.
**Implementation rules:** From the app import chosen references and enter a
prompt. Generate a scene look and three clips: the base case, a contrasting
prompt with the same assets, and a changed-reference case. No predefined location,
prop, action or motion is required. Compare reference fidelity and prompt
adherence as well as actual motion/temporal quality. Run the
PLAN §9.7 checklist at normal speed; retain source/request/result identities,
measured local resources or billed cost, and actual user feedback. Do not mark
pass from mocks, provider marketing or file decodability. If local fails, record
specific reasons and propose a small capped Runway comparison; user selection
can activate V25 while this gate remains waiting. Avoid repeatedly re-running a
missing-input gate. Independent assembly/packaging tasks remain runnable.
**Verification command:** `./gradlew :desktopApp:run --args='--video'`; manual
review of the three generated clips against PLAN §9.7.
**Done:** the user accepts reference fidelity and prompt-driven generation through
one actual backend; otherwise keep the gate open with concrete failed criteria.

### V25 — Add the explicitly chosen hosted fallback (optional)

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/RunwayVideoBackend.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/VideoProviderCredentials.kt`
(new), `src/test/kotlin/app/melotrail/video/RunwayVideoBackendTest.kt` (new),
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoSetupPanel.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoDesktopComposition.kt`,
`docs/TABI_VIDEO.md` (selected hosted profile only).
**Inputs / dependencies:** V11, V16, V18, V19, V21. OPTIONAL: activate only after
local evidence and explicit user selection. No dependency on V24 completion.
**Implementation rules:** Reverify official Runway endpoints/models/limits/terms/
prices. Support reference-conditioned still generation and image-to-video through
one backend; typed capabilities, narrow uploads, bounded downloads, digest pinning,
429/backoff, auth expiry, retention, cancellation and ambiguous-submit handling.
Use secure process configuration/credential storage; no secrets in project files,
logs or snapshots. Show mode, upload disclosure and reviewable capped quote.
Fake HTTP regressions are unpaid; a live request requires the concrete pilot
budget. Manual provider-site downloads cannot substitute for the app workflow.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.RunwayVideoBackendTest'`; `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoGenerationFlowTest'`.
**Done:** the selected hosted service works through the existing tab and job port,
with no automatic local-to-cloud fallback. If local passes, leave this task OPTIONAL.

### V26 — Assemble selected takes to an exact 3–5 minute duration

**Target files:** `src/main/kotlin/app/melotrail/video/domain/VideoAssembly.kt`
(new), `src/main/kotlin/app/melotrail/video/application/VideoAssemblyPlanner.kt`
(new), `src/test/kotlin/app/melotrail/video/VideoAssemblyPlannerTest.kt` (new).
**Inputs / dependencies:** V15, V19.
**Implementation rules:** Ordered selected-take trims, explicit transition lengths,
repeatability flags, explicit unique/reuse mode and exact frame-based net
duration. Unique mode rejects repeated take IDs; reuse requires both a selected
reuse mode and approved repeatable takes. Show proposed sequence,
unique/reused footage and missing usable seconds. Respect the requested 180–300s;
never pad a still, reverse requested actions/motion, stretch motion silently, repeat a
rejected take or claim loopability from a score. Respect scene/camera/motion continuity where requested by the prompt or shot
overrides; permit intentional changes. Let the user change order or request more takes.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoAssemblyPlannerTest'`.
**Done:** 180/240/300s plans have correct lengths including overlap and cannot
consume missing, unselected or unapproved-for-reuse clips.

### V27 — Encode and validate silent MP4 outputs

**Target files:** `src/main/kotlin/app/melotrail/video/application/VideoExport.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/VideoEncoder.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoExportTest.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoMediaHostCheck.kt`.
**Inputs / dependencies:** V12, V19, V26.
**Implementation rules:** Same resolved assembly for review/output; normalize
explicitly to 1920×1080 H.264, square pixels and selected constant cadence, with
zero audio streams. Record native/upscaled resolution; strip model audio rather
than adding an audio editor. Full decode, cadence/duration/stream verification,
first/last/join-frame checks, cancellable bounded encode and no-overwrite atomic
publication with adjacent provenance. Preserve accepted clips and earlier exports.
Write only into owned staging/new output paths; malformed inputs never count
as a successful export.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoExportTest'`; `./gradlew :videoMediaProbe -PvideoToolsDirectory=/absolute/path/to/tools`.
**Done:** a complete owned 180–300s assembly decodes at the exact target duration
with no audio, and cancellation/collisions preserve prior outputs.

### V28 — Expose full-cut review and export in the application

**Target files:** `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoAssemblyPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoExportPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoWorkspace.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoDesktopComposition.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoExportFlowTest.kt` (new).
**Inputs / dependencies:** V23, V26, V27.
**Implementation rules:** Select/reorder/trim approved takes, explicitly choose unique
footage or approved reuse, inspect unique/reused duration and generate missing
material. Reuse is never automatically enabled to satisfy the duration. Build a
local silent review cut using the resolved assembly and play it in the existing
preview. Display every join, then choose an output path, export, show real result
facts and reveal in Finder. No soundtrack, MIDI or JSON prerequisite. Changing
assembly invalidates review/export readiness but preserves earlier files.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoExportFlowTest'`.
**Done:** the UI produces and plays a complete silent file; exporting is not a
separate terminal command.

### V29 — Prove packaged app startup and optional-runtime isolation

**Target files:** `desktopApp/build.gradle.kts`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreNativeInstallCheck.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreNativeInstallCheckTest.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoInstalledAppCheck.kt`
(new), `src/test/kotlin/app/melotrail/architecture/TargetArchitectureRulesTest.kt`,
`docs/VALIDATION.md` (native video evidence only).
**Inputs / dependencies:** V20, V23, V28.
**Implementation rules:** Ship the selected media distribution/setup strategy
with exact notices and required jpackage modules; models remain external and
explicitly installed. Prove clean MIDI start/export with no video tools/models/
credentials and no network. Prove Video setup, real owned playback/encode with
configured tools and cleanup on app close. Register `videoInstalledSmoke` for a
private installed copy, never the user's installed app. Extend boundary tests
without weakening existing MIDI/source protection. No development PATH or Swift
package dependency in the installed launch path.
**Verification command:** `./gradlew :desktopApp:videoInstalledSmoke -PvideoInstallDirectory=/absolute/path/to/new-install-evidence`; `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.MidiCoreNativeInstallCheckTest'`.
**Done:** installed-window and media evidence is tied to the actual build, and
missing optional runtime cannot break MIDI use.

### V30 — Remove the soundtrack/MIDI companion handoff

**Target files:** `src/main/kotlin/app/melotrail/application/MidiCoreExportHandoff.kt`
(delete), `desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreCompanionLauncher.kt`
(delete), `desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreCompanionLauncherTest.kt`
(delete), `desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreExportPage.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreDesktopComposition.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreExportPageTest.kt`,
`src/test/kotlin/app/melotrail/application/MidiCoreMidiPackageExporterTest.kt`.
**Inputs / dependencies:** V28, V29.
**Implementation rules:** Trace and remove the optional executable probe/launch,
Open in TABI action, soundtrack guidance and exclusive tests. Keep MIDI package
validation and regression assertions that remain useful; do not change manifest
format or musical export behavior. Video is reached from the app tab without
an accepted MIDI package. Never touch saved MIDI projects/export snapshots.
**Verification command:** `./gradlew :test --tests 'app.melotrail.application.MidiCoreMidiPackageExporterTest'`; `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.MidiCoreExportPageTest'`.
**Done:** MIDI export has no Swift/handoff consumer and its original safety tests
still pass.

### V31 — Delete the Swift companion and replace its launch/check wiring

**Target files:** `companion/` (delete tracked package/source/tests/scripts/resources),
`tools/companion-check.mjs` (delete), `tools/companion-check.test.mjs` (delete),
`Makefile`, `.gitignore`, `README.md`, `docs/ARCHITECTURE.md`, `docs/TABI_VIDEO.md`,
`docs/VALIDATION.md`, `src/test/kotlin/app/melotrail/architecture/TargetArchitectureRulesTest.kt`.
**Inputs / dependencies:** V29, V30.
**Implementation rules:** Enumerate exact repository-owned companion files,
remaining consumers/symlinks and exclusions before deleting. Transfer only needed
job/process safety assertions into already implemented Kotlin tests; no retained
Swift shim/archive/migration. Preserve all supplied references, Logic evidence,
MIDI artifacts and external user media, sessions and installed applications.
Remove only verified owned generated caches after retaining needed evidence;
no broad workspace cleanup. Update make video to invoke the same app's --video
route without VIDEO_REQUEST/VIDEO_JOBS, and remove Swift build/test requirements
from active run instructions. Old evidence is historical. Installed runner config
is coordinator-owned and must drop companion checks when a future run is enabled.
**Verification command:** `make -n video`; `./gradlew :test --tests 'app.melotrail.architecture.TargetArchitectureRulesTest' --tests 'app.melotrail.documentation.DocumentationIntegrityTest'`; `rg -n 'melotrail-tabi-editor|MELOTRAIL_TABI_EXECUTABLE|companion-check|VIDEO_REQUEST' Makefile src/main desktopApp/src/main tools` (no active hits).
**Done:** no repository Swift app or runtime consumer remains, and make video
starts the integrated tab. Unrelated user files are preserved.

### V32 — Verify the complete UI path and prepare final visual evidence

**Target files:** `desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoEndToEndTest.kt`
(new), `desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoVisualTest.kt`
(new), `desktopApp/src/test/resources/visual/video/` (new reviewed technical
baselines), `docs/VALIDATION.md`, `README.md`.
**Inputs / dependencies:** V22, V23, V28, V31.
**Implementation rules:** End-to-end fake-backend tests start from an empty tab,
import references, enter a free-form prompt, click Generate video without a
preset/storyboard/keyframe prerequisite, optionally refine looks/clips, cancel/retry,
restart/reopen, assemble 180/240/300s and export. Use real owned file decode/encode
for media checks; fake providers are explicitly test-only. Prove no JSON/audio/
MIDI prerequisite, selected-source/MIDI hashes unchanged, single/multiple-reference
inputs and contrasting prompts without preset-specific paths,
no unintended network in local mode and no false completion. Capture empty/setup/
ready/progress/failure/full-review/export at 1536×1024, 1280×900, 720×900 and inspect
the actual images before adding baselines. Preserve existing MIDI visual tests.
Record measured playback/UI performance and exact remaining product gates; do
not call owned fixtures a successful generation trial.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoEndToEndTest' --tests 'app.melotrail.desktop.video.VideoVisualTest'`; `make test`; `make build`; `git diff --check`.
**Done:** technical behavior is proved through the real UI and current native
runtime, with a concrete review packet for V33.

### V33 — Accept a real complete prompted video and editor handoff

**Target files:** `docs/VALIDATION.md`, `docs/TABI_VIDEO.md`, `README.md`
(actual current result/limitations and user decision only).
**Inputs / dependencies:** V24, V32. User-selected references, selected local setup
or an explicitly authorized hosted budget; WAITING_USER until real evidence exists.
**Implementation rules:** Starting from user-chosen reference assets and a
free-form prompt in the app, create one matching 3–5 minute silent video. Content
and actions are determined by that prompt; Tokyo/train/coffee is only an optional
example and is not a release requirement. Record generated versus reused seconds,
actual generation resources/cost, rejected takes, model/tool versions and source/
output digests. The user watches the entire cut and joins, confirms reference
fidelity, prompt adherence, temporal coherence and acceptable repetition/quality,
then imports/plays
the export in their chosen Apple editor. Audio placement/sync and YouTube upload
are outside scope. Report every failed criterion and add only bounded corrective
work to this queue; no self-awarded artistic approval or inferred monetization.
**Verification command:** `make video`; review the final exported MP4 at normal
speed and import/play it in the user's chosen Apple editor.
**Done:** a real full-length result meets the user's visual criteria and the app
workflow is accepted, or the gate stays pending with explicit failed criteria.

## Configured automatic execution

The user authorized this policy on 2026-09-13. The existing paused Melotrail
heartbeat is repurposed as **Melotrail video sequential implementation**, attached
to the current planning/implementation conversation, with **20-minute** wakes.
It uses native collaboration agents. Do not invoke the retired Terra CLI runner,
resume its pause file, reset its retained state or start a second automation.
The old runner source remains subject to V10/V31's compatibility/removal checks;
its historical model/retry/commit policy does not control this run.

Execution checkout: `~/.codex/melotrail-video-sequential/worktree`, branch
`codex/video-generation-sequential`. This clean worktree starts from the current
tracked code and accepted PLAN/TASKS/launcher changes; unrelated untracked media
and environments remain in the normal checkout. Scheduler bookkeeping stays in
`~/.codex/melotrail-video-sequential/state.json`, outside the product repository.
This state records the current task, base, phase, agent ID, repair count, evidence
paths and commit receipt; TASKS remains the only implementation queue.

Each wake handles at most one unfinished mandatory V10–V33 row or its preserved
continuation. Select the earliest dependency-ready TODO, excluding V24/V33,
unchosen OPTIONAL V25 and the completed/retired MIDI/video queues. A missing human
or setup decision blocks its own task, while later independent ready engineering
may continue on a later wake. Never retry a WAITING_USER row without new evidence.
Read live queue status rather than hardcoding completed IDs. One worker/reviewer
runs at a time; no concurrent task, test suite or repair. Inspect live agents,
state, Git status and recent commits before admitting work. Resume an interrupted
stage and preserve its candidate; do not repeat completed work or reset retries.

1. Launch one fresh **Sol High** agent (`gpt-5.6-sol`, reasoning `high`,
   `fork_turns: none`) with the exact row, base commit, explicit worktree path,
   authorized files and required evidence. The coordinator owns task status and
   commits; workers must not spawn other agents, commit or edit PLAN/TASKS.
2. After the writer finishes, run the row's focused checks, `make test`,
   `make build` and `git diff --check` in the execution checkout. Do not edit
   documentation or source during validation because matrix evidence fingerprints
   build inputs. Run the row's actual native/media checks when applicable.
3. A concrete implementation/test/review failure goes directly to **Astra High**
   (`gpt-6-astra`, reasoning `high`, `fork_turns: none`), with the original row,
   base, current diff, exact failed command, exit code, relevant terminal output
   and review findings. Preserve the Sol candidate and repair only the same task.
   Allow at most two Astra repair attempts per task, retained across wakes; no
   extra Sol retry or automatic model substitution. Repeat affected required
   validation after a changed candidate. Missing rights, credentials, model setup
   choices or real visual decisions are external waits, not code defects.
4. Once checks pass, a fresh **Sol High** agent reviews the tested diff read-only
   against the row and PLAN. Review defects use the same Astra repair allowance,
   followed by validation and a fresh review. A test pass alone is insufficient.
5. After validation and review pass, the coordinator records DONE plus real
   evidence in TASKS, stages only task-owned changes and that status update,
   inspects the staged diff and creates **one local commit** with the row ID in
   its subject. The implementation and DONE update belong to the same commit;
   there is no separate queue-status commit. Record and verify the hash in the
   external receipt before selecting another row. Do not put the future commit's
   own hash in its contents. On interruption, find an existing task commit before
   retrying; a pending commit must finish before further work.
6. Fast-forward the normal checkout on `codex/terra-live` only if its tracked
   files are clean, it remains on that branch and its HEAD is an ancestor of the
   verified task commit. Never reset, stash or discard user edits. If synchronization
   cannot proceed, retain the implementation branch and report its commit/path.

If both Astra repairs fail, preserve the candidate and exact error, mark the row
BLOCKED without claiming completion, and pause the heartbeat with an actionable
failure report. A transient usage interruption resumes the same stage when
available; do not treat it as a code repair or start a substitute model. Check
current account usage before admission and defer below 10% remaining in any
available relevant limit. No credit purchase/reset is authorized.

No commit is created for a failed task. On a missing external decision, preserve
any unfinished changes and inspect whether they can stay isolated before working
on an independent row; use a dedicated task worktree if necessary. Do not mix
partial work into another task's commit. When all eligible work is DONE or only
external decisions remain, notify once with completed commit hashes and pending
gates, then pause this heartbeat. V24/V33 require real user review; V25 requires
explicit provider selection and a bounded paid budget. Native model setup/download
choices remain as specified in V11; ordinary build dependency resolution and
owned-fixture tests may proceed. No public push, publication or YouTube upload.

Stay quiet while state is unchanged. Notify on a validated task commit, a new
material failure, skipped live synchronization, required input or queue completion.
Local scheduled execution requires this computer and the Codex app to be running.

## Reusable agent prompt

```text
Implement only the assigned dependency-ready TASKS row and its explicit slice.
For MIDI retain the Q03b handoff; for video follow PLAN section 9 and V10–V33.
Do not revive V01–V07. V24 is the early real-video checkpoint; V33 is final
acceptance. Never retry WAITING_USER rows merely to rediscover missing feedback.
Read AGENTS, PLAN, README, Architecture and relevant task-owner references once.
Inspect current callers/tests. Reuse completed child tasks and the preserved
candidate; do not rebuild the whole parent feature. Do not edit PLAN/TASKS or
run other agents. Work within the coordinator's allowed paths, without commits.
Preserve source MIDI, authoritative settings, accepted candidates and exports.
No legacy compatibility, audio-production runtime or paid/public media actions.
Add regressions and call out exact focused test selectors. The host coordinator
runs Gradle/make, diff checks and fresh review; unavailable worker sandbox sockets
mean PENDING_COORDINATOR, not failure. UI work includes actual image inspection;
musical/export work prepares comparison/Logic evidence, never fictional ratings.
Return task ID, base, candidate status, summary, changed files, tests, artifacts
and the precise blocker when present. Reviews identify concrete findings with
file/line references and verification needed to resolve them. WAITING_USER describes an actual missing human decision.
Repairs receive the same task, current diff and concrete errors, and remain scoped.
Never read model transcripts or recursively search execution directories; use
only the bounded current evidence packet and its named failed check logs.
```
