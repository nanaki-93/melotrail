# MIDI-First Task Index

This index replaces the completed/deleted audio-arranger task set with the implementation sequence defined by `PLAN_MIDI.md`. Implement exactly one task at a time.

## Required workflow for every task

Before implementation:

1. Read `README.md`, `plan/AGENT_GUIDELINES.md`, `plan/PLAN_MIDI.md`, and the selected task file completely.
2. Inspect the repository tree and find existing equivalent code.
3. Run `./gradlew test`, `python -m unittest discover -s worker/tests`, and the smallest relevant build/check.
4. Record pre-existing failures separately. At task-set creation time, Kotlin tests pass; the local Python 3.14 environment has a pre-existing `librosa`/`numba` cache-related worker-test failure.
5. State the smallest intended file set before coding.

During implementation:

- keep changes local, explicit, deterministic, and testable;
- preserve source and inspectable intermediate files;
- never assume 48 kHz or stereo and work in frames for multi-channel audio;
- keep intermediate audio lossless and MP3 as a final separate export;
- validate every path, MIDI boundary, registry value, and AI response;
- allow AI to select only schema-defined musical roles and never execute model output;
- do not rewrite working DSP or introduce cloud/SaaS infrastructure.

After implementation:

1. Run relevant unit/integration tests, the full Kotlin tests/build, and worker tests.
2. Run the task's manual smoke/listening check when it changes musical/audio behavior.
3. Review the diff and checkpoint successful work if that is the active Git workflow.
4. Report changed files, all commands, automated/manual results, assumptions, and remaining issues.

## Ordered tasks

| Task | Name | Depends on | Primary result | Status |
|---|---|---|---|---|
| [001](tasks/completed/001-audio-to-midi-research-spike.md) | Audio-to-MIDI research spike | — | Selected, locally proven solo-piano transcription engine | Planned |
| [002](tasks/completed/002-transcribe-worker-endpoint.md) | Transcription worker endpoint and CLI | 001 | `/transcribe` and standalone `transcribe` command | Planned |
| [003](tasks/completed/003-midi-cleanup.md) | Deterministic MIDI cleanup | 002 | `/midi-clean`, soft quantization, validated clean MIDI | Planned |
| [004](tasks/completed/004-unified-input-adapter.md) | Unified MIDI-first input adapter | 002–003 | V2 project format and MIDI/audio convergence | Planned |
| [005](tasks/completed/005-midi-analysis.md) | MIDI musical analysis | 004 | Versioned `analysis/<partId>.json` | Planned |
| [006](tasks/completed/006-instrument-and-license-registry.md) | Instrument and license registry | 004 | Validate/enrich existing five-name `sounds/` registry and licenses | Assets present; implementation planned |
| [007](tasks/completed/007-midi-sfz-wav-rendering.md) | MIDI → SFZ → WAV rendering | 006 | Render existing starter piano/bass assets | Assets present; renderer missing |
| [008](tasks/completed/008-bass-midi-generator.md) | Deterministic bass MIDI generator | 005, 007 | `midi/generated/bass.mid` | Planned |
| [009](tasks/completed/009-global-song-planner.md) | Global song planner | 005–006 | Validated whole-song `song_plan.json` | Planned |
| [010](tasks/completed/010-repeated-section-variation.md) | Repeated-section variation | 009 | Stable A1/A2/B1 identities and bounded variation | Planned |
| [011](tasks/completed/011-detailed-arrangement-plan.md) | Detailed MIDI-first arrangement | 006, 009–010 | Version-3 `arrangement.json` roles | Planned |
| [012](tasks/completed/012-piano-bass-quality-gate.md) | First complete piano + bass gate | 001–011 | Musically accepted MIDI-first dry mix | Planned |
| [013](tasks/completed/013-drum-midi-generator.md) | Deterministic drum MIDI generator | 012 accepted | `midi/generated/drums.mid` | Blocked by gate |
| [014](tasks/completed/014-pad-midi-generator.md) | Deterministic pad MIDI generator | 012 accepted | `midi/generated/pad.mid` | Blocked by gate |
| [015](tasks/completed/015-midi-transition-engine.md) | MIDI transition engine | 008, 013–014 | Contextual bass/drum/pad transitions | Blocked by gate |
| [016](tasks/completed/016-strings-midi-generator.md) | Deterministic strings MIDI generator | 013–015 | `midi/generated/strings.mid` | Blocked by gate |
| [017](tasks/completed/017-stem-rendering-and-mixer.md) | Render all stems and dry mix | 007–008, 013–016 | Compatible stems and `mix/dry.wav` | Blocked by gate |
| [018](tasks/completed/018-ai-arrangement-critic.md) | Structured AI arrangement critic | 011, 017 | Reviewable `arrangement_v1.json` → approved plan | Blocked by gate |
| [019](tasks/completed/019-lofi-ab-debugging.md) | LoFi A/B measurement and debugging | 017–018 | Measurable `mix/lofi.wav` and compare command | Blocked by gate |
| [020](tasks/completed/020-mastering-integration.md) | Mastering integration | 017, optional 019 | Validated `output/master.wav` | Blocked by gate |
| [021](tasks/completed/021-final-mp3-export.md) | Final MP3 export and release check | 020 | Optional `output/song.mp3` and release report | Blocked by gate |

## Phase gates and artifacts

### Existing sound-library baseline

The workspace already contains the five MVP SFZ instruments and 25 local 44.1 kHz mono PCM-16 sample files under `sounds/`. Reuse them; do not create `instruments/` or schedule sound acquisition as part of Tasks 006–007.

Task 006 still must validate/enrich registry and license links, define the MIDI-channel convention, verify SFZ/sample references, and resolve the fact that sample WAVs are ignored by Git. Task 007 still must install/configure a compatible SFZ renderer and prove real piano/bass output. See `SOUND_LIBRARY_BASELINE.md`.

### Gate A — Transcription selected

Task 001 must prove recognizable solo-piano notes/timing and settle runtime/license constraints before worker integration.

### Gate B — MIDI input foundation

After Task 005, both direct MIDI and transcribed audio must expose preserved source, validated clean MIDI, and versioned analysis:

```text
source/<part>.*
midi/raw/<part>.mid       # transcribed audio only
midi/clean/<part>.mid
analysis/<part>.json
```

### Gate C — Real instrument rendering

The piano/bass SFZ and sample prerequisite is satisfied by `sounds/`. Task 007 must still audibly prove both instruments through an installed local renderer in the project's explicit format. A test oscillator or the mere presence of sample files does not satisfy this gate.

### Gate D — Piano + bass musical usefulness

Task 012 is a hard stop. Do not start drums, pads, transitions, strings, LoFi, mastering, or MP3 until the direct-MIDI and transcribed-WAV piano+bass results pass the documented listening checklist and are explicitly accepted.

### Gate E — Final release

Tasks 017–021 must preserve this artifact chain:

```text
song_plan.json
arrangement_v1.json       # when critic is used
arrangement.json
midi/generated/*.mid
stems/*.wav
mix/dry.wav
mix/lofi.wav              # optional
output/master.wav
output/song.mp3            # optional
output/release.json
```

`master.wav` is always the authoritative lossless output. MP3 is never written by a WAV writer or before mastering.

## Scope boundary

This plan is for a local personal tool. It intentionally excludes authentication, databases, cloud queues, multi-user support, payments, publishing integrations, generic plugin frameworks, arbitrary AI-generated notes/code/paths, and unrelated DSP rewrites.

## UI and song-creation improvement sequence

The Compose Desktop workflow is the active product UI. The detailed visual,
input-quality, and creation-flow plan is [PLAN_UI_AND_CREATION.md](PLAN_UI_AND_CREATION.md).
These tasks start only after Task 028 and must be implemented one at a time.

| Task | Name | Depends on | Primary result |
|---|---|---|---|
| [029](tasks/completed/029-sound-library-locator.md) | Sound-library locator | 028 | One CWD-independent injected library root |
| [030](tasks/completed/030-desktop-library-settings.md) | Desktop library settings | 029 | Validated user-selectable library root |
| [031](tasks/completed/031-audio-player-lifecycle.md) | JVM audio-player lifecycle | 028 | Reliable one-line/one-thread WAV playback |
| [032](tasks/completed/032-runtime-readiness-model.md) | Runtime readiness model | 029–031 | Capability-specific dependency states |
| [033](tasks/completed/033-preview-artifact-resolver.md) | Preview artifact resolver | 029, 031 | Validated WAV/MP3/MIDI preview artifacts |
| [034](tasks/completed/034-preview-viewmodel-state.md) | Preview view-model state | 032–033 | Truthful preview/playback state machine |
| [035](tasks/completed/035-preview-transport-ui.md) | Preview and transport UI | 034 | Accessible preview controls and errors |
| [036](tasks/completed/036-input-inspection-contract.md) | Input inspection contract | 032 | Versioned preparation report schema |
| [037](tasks/completed/037-input-inspection-worker.md) | Input inspection worker | 036 | Deterministic MIDI/WAV/MP3 validation and metrics |
| [038](tasks/completed/038-input-inspection-service.md) | Input inspection service | 036–037 | Atomic reports and preparation snapshot |
| [039](tasks/completed/039-guided-import-dialog.md) | Guided import dialog | 032, 038 | Clear MIDI/WAV/MP3 import workflow |
| [040](tasks/completed/040-deterministic-audio-cleanup-worker.md) | Deterministic audio cleanup worker | 037 | Safe independently tested repair operations |
| [041](tasks/completed/041-cleanup-plan-service.md) | Cleanup plan and provenance service | 038, 040 | Bounded deterministic/AI-assisted cleanup plan |
| [042](tasks/completed/042-transcription-quality-gate.md) | Transcription quality gate | 038, 041 | Validated raw MIDI and stage-specific failures |
| [043](tasks/completed/043-audio-preparation-ui.md) | Audio preparation and A/B UI | 035, 039, 041–042 | Consented cleanup, A/B, input selection |
| [044](tasks/completed/044-midi-cleanup-profiles.md) | MIDI cleanup profiles | 042 | Conservative/transcription/timing profiles |
| [045](tasks/completed/045-midi-quality-report.md) | MIDI quality report | 044 | Persisted raw-to-clean metrics/provenance |
| [046](tasks/completed/046-midi-quality-ui.md) | MIDI quality review UI | 035, 045 | Explicit cleanup review and retry |
| [047](tasks/completed/047-creation-progress-model.md) | Creation progress model | 032, 034, 039, 043, 046 | Deterministic stage and next-action state |
| [048](tasks/completed/048-creation-header-and-checklist.md) | Creation header and checklist | 047 | Visible creation stepper and next action |
| [049](tasks/049-parts-and-structure-workspace.md) | Parts and structure workspace | 048 | Linked parts/preparation/structure experience |
| [050](tasks/050-arrangement-build-workspace.md) | Arrangement and build workspace | 049 | Clear approval and build lifecycle |
| [051](tasks/051-workspace-theme-tokens.md) | Workspace theme tokens | 050 | Stable reference-aligned visual vocabulary |
| [052](tasks/052-responsive-workspace-shell.md) | Responsive workspace shell | 051 | Wide/medium/narrow reference layout |
| [053](tasks/053-timeline-mixer-visualization.md) | Timeline, mixer, visual states | 052 | Real-data timeline and final visual states |
| [054](tasks/054-end-to-end-workflow-compatibility.md) | End-to-end workflow and compatibility | 029–053 | Fixture-backed new/legacy flow proof |
| [055](tasks/055-documentation-and-package-smoke.md) | Documentation and package smoke | 054 | Verified current-OS desktop documentation/package |
| [056](tasks/056-repository-bug-audit.md) | Repository-wide bug audit | 055 | Reproducible report and Task 059+ blockers |
| [057](tasks/057-static-frontend-retirement.md) | Static-frontend retirement | 056 + blockers | Deprecated web source/server/fallback removed |
| [058](tasks/058-repository-guards-and-final-docs.md) | Repository guards and final docs | 057 | Drift guard and final verified desktop-first repo |

Task 056 is intentionally read-only except for reports/task contracts. Complete
every P0/P1 or retirement-blocking Task 059+ it creates before Task 057. Do not
merge unknown bug fixes into the frontend-removal diff.
