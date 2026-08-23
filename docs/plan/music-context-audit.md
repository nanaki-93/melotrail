# Musical context audit and executable baseline

**Status:** Task 118 characterization evidence. **Captured:** 2026-08-22.

This is a code-audited description of the current Kotlin/Compose Desktop
implementation, not a proposed replacement architecture. Paths in the evidence
column were verified with `rg` in this repository. “Canonical” means a declared,
validated project decision; “descriptive” means a measured or inferred fact that
does not override a declaration. Artifact files and stale references remain
inspectable evidence and are never completion by themselves.

## Baseline

The required baseline was run before this document was added:

```text
./gradlew test :desktopApp:test
BUILD SUCCESSFUL in 627ms
14 actionable tasks: 14 up-to-date
```

There were no baseline test failures. The working tree contained one unrelated
untracked compiler-session file, `.kotlin/sessions/kotlin-compiler-15137692790847067120.salive`;
it is intentionally not part of Task 118.

After the strict-v4 removal was implemented, the required verification also
passed:

```text
./gradlew test :desktopApp:test :desktopApp:build
BUILD SUCCESSFUL
```

## Declared and analyzed musical facts

| Fact | Owner and Kotlin type | Serialization / producer | Consumers | Authority and validation | Known duplication or gap; evidence |
| --- | --- | --- | --- | --- | --- |
| Project identity, parts, source references, render format | `Project`, `SongPart`, `RenderFormat` | `project.json`, `@Serializable`; `ProjectStore` | Every application service and desktop snapshot | Canonical schema-v4 project envelope; `ProjectValidator` checks the exact version, project-relative paths, and PCM-24 render constraints | Missing-version, v1–v3, and superseded v4 documents are rejected without conversion or writes. `src/main/kotlin/app/melotrail/arrangement/Project.kt`, `ProjectStore.kt` |
| Declared key, tempo, meter, profile, and mood | `ProjectV4Envelope.compositionSettings: CompositionSettings`; `MusicalKey`, `Tempo`, `TimeSignature` | `project.json`; `CompositionSettingsApplicationService` | Transposition, enhancement context, arrangement, Cohesion policy, Humanization profile defaults, readiness | **Canonical once complete.** `CompositionSettings.requireWellFormed` and project validation reject incomplete or malformed settings | Individual stages still reconstruct projections instead of using one authority builder. Task 119. `Project.kt`, `application/CompositionSettingsApplicationService.kt`, `arrangement/Enhancement.kt` |
| Declared section harmony | `ProjectV4Envelope.harmony: HarmonySettings`, `ChordProgression`, `ChordEvent` | `project.json`; `HarmonyApplicationService` | Arrangement harmony context, enhancement context, Cohesion policy | **Canonical.** Well-formed progressions are keyed by section type and checked against the declared key | No reusable occurrence/tick harmonic timeline. AI Fix instead receives inferred `MidiKey`/`MidiChord` from its input analysis. Tasks 119 and 122. `harmony/ChordDomain.kt`, `application/HarmonyApplicationService.kt`, `arrangement/MidiAiFix.kt` |
| Declared part identity and section type | `SongPart.id`, `SongPart.sectionType` | `project.json`; import and project/structure services | Selected MIDI resolver, structure, arrangement, analysis lookup | Canonical bounded identifiers and section types; validator checks them | A part-level section type is reused by repeated occurrences; it is not an occurrence-level musical projection. Task 119. `arrangement/Project.kt`, `arrangement/SectionType.kt` |
| Declared structure occurrences | `StructureOccurrence` and `SectionInstance` | `project.json`; `ProjectApplicationService` / structure operations | Song planning, arrangement, Cohesion, occurrence MIDI resolver, Humanization | Canonical stable IDs, part references, labels, revisions; project validation checks uniqueness | Stable occurrence identity exists, but consumers independently derive ranges/evidence. Task 119. `arrangement/Project.kt`, `arrangement/GlobalSongPlanner.kt`, `arrangement/TransitionCohesion.kt` |
| Source-key evidence | `SourceKeyEvidence` | `project.json`; MIDI key detection and explicit confirmation | MIDI transposition and selected MIDI resolution | Descriptive detection plus canonical explicit override; confidence gate prevents automatic use below 0.70 | Distinct from project key as intended. It is not the canonical harmonic timeline. `arrangement/Project.kt`, `arrangement/MidiTransposition.kt`, `arrangement/SelectedMidiArtifactResolver.kt` |
| Raw, cleaned, normalized, transposed, corrected, AI-fix, enhanced, and MIDI-feel artifacts | `MidiReferences`, `TechnicalCorrectionReferences`, `MidiAiFixReferences`, `EnhancementReferences`, `MidiFeelReferences` | Project-relative references in `project.json`; stage-specific stores/reports | `SelectedMidiArtifactResolver`, analysis, planning, preview | Selected artifact is a validated, hash-checked derived input; raw/cleaned evidence is immutable | Selection is split across technical-correction, AI-fix, enhancement, `analysisInput`, and optional stage-run selection. This is an existing compatibility boundary, not numbered-history storage. Task 120 aligns taxonomy and invalidation; Task 122/123 align mutations. `Project.kt`, `WorkflowArtifacts.kt`, `SelectedMidiArtifactResolver.kt` |
| Audio inspection facts | `InputInspectionReport` | `prepared/<part>/report.json`; worker inspection via `InputInspectionReportStore` | Import/readiness and desktop project snapshot | Descriptive only: format, duration, levels, silence and signal evidence; no declared setting is overwritten | This is current preparation evidence. `preparation/InputInspection.kt`, `application/ProjectApplicationService.kt` |
| MIDI timing, note, phrase-like density/energy, key and chords | `MidiAnalysis`, `MidiTempoChange`, `MidiTimeSignature`, `MidiKey`, `MidiChord` | `analysis/<part>.json`; `MidiPartAnalyzer` / `MidiAnalysisStore` | Arrangement planning, AI Fix input, enhancement input, Cohesion input | Descriptive conservative inference; low-confidence key/chord values become null rather than guesses | AI Fix consumes these inferred chords rather than declared harmony; `MidiAnalysis` has no shared anchors/identity contract. Tasks 121 and 122. `arrangement/MidiAnalysis.kt`, `arrangement/MidiAiFix.kt`, `application/MidiAiFixApplicationService.kt` |
| Technical-correction context | `TechnicalCorrectionReferences` and technical-correction plan/report | `midi/corrected/<part>/<input-hash>/`; `TechnicalCorrectionApplicationService` | Selected MIDI resolver and AI Fix base selection | Derived and hash/context-bound; corrected/base selection is explicit | Current prerequisite is before AI Fix, but no canonical authority projection is passed to AI Fix. Task 122. `WorkflowArtifacts.kt`, `application/TechnicalCorrectionApplicationService.kt`, `application/MidiAiFixApplicationService.kt` |
| AI Fix note facts and defect regions | `MidiAiFixInput`, `MidiAiFixNote`, `MidiAiFixProblemRegion` | Strict JSON to the local advisor; built by `MidiAiFixInputFactory` | `LocalQwenMidiAiFixPlanner`, validator, transformer | Descriptive MIDI-derived input; validator allow-lists edit types and limits | Version 1 contains inferred `key` and `chords`, but no declared key/harmony, occurrence context, or anchors. Task 122. `arrangement/MidiAiFix.kt` |
| Per-track enhancement context | `MusicalProcessingContext`, `EnhancementChordContext`, `EnhancementNoteSummary` | Hash-bound plan/report files; `MusicalProcessingContextFactory` | Local Qwen enhancement planner and MIDI applier | Derived from project settings, section progressions, profile and corrected MIDI; strict plan validation and edit budgets | Carries section chords but does not consistently resolve the active chord at each edited note; no shared anchor evidence. Tasks 121 and 123. `arrangement/Enhancement.kt`, `application/EnhancementApplicationService.kt` |
| Arrangement plan, detailed arrangement, instrument assignments | `SongPlanningInput`, `SongPlan`, `DetailedArrangement`, `ArrangementAssignmentReference` | `song_plan.json`, `arrangement_plan.json`, drafts/reviews; arrangement service/stores | Generators, Cohesion, render | Approved arrangement is hash-bound to structure, occurrence, planning context, and plan | Each service builds its own context; current arrangement critic runs before generated MIDI. Tasks 124, 125, and 127. `arrangement/GlobalSongPlanner.kt`, `arrangement/DetailedArrangement.kt`, `application/ArrangementApplicationService.kt`, `arrangement/ArrangementCritic.kt` |
| Generated role MIDI | `GeneratedMidiWorkflowReferences`, `GeneratedMidiArtifactReference` | `midi/generated/<role>.mid`; deterministic role adapters | Cohesion, build validation, renderer | Derived, hash-bound to approved arrangement; required before build | Role validation/reporting is uneven across generators. Task 125. `WorkflowArtifacts.kt`, `application/ArrangementApplicationService.kt`, `arrangement/BassStemGeneration.kt`, `arrangement/DrumMidiGeneration.kt`, `arrangement/PadMidiGeneration.kt`, `arrangement/StringsMidiGeneration.kt` |
| Cohesion boundary and ensemble facts | `TransitionCohesionInput`, `TransitionCohesionPlan`, `CohesionWorkflowReferences` | `cohesion/`; strict JSON plan, plan/audit/bridge/role files | Cohesion service, occurrence resolver, Humanization, build | Derived, hash-bound to selected MIDI, arrangement, structure, and generated roles; approval is explicit | Schema v5 additionally owns whole-song `songEdits`; it is not boundary-only. Task 126. `arrangement/TransitionCohesion.kt`, `application/CohesionApplicationService.kt`, `WorkflowArtifacts.kt` |
| Humanization config, seed, edit evidence | `HumanizationConfig`, `HumanizationReport`, `HumanizationWorkflowReferences` | `midi/humanized/<run-hash>/`; seeded Kotlin processor | Renderer/build | Derived, deterministic, selected as `HUMANIZED` or `BYPASS`; input/output hashes and report are checked | Correctly non-AI, but has no explicit post-Cohesion full-song enhancement predecessor yet. Task 128. `arrangement/SeededHumanization.kt`, `application/HumanizationApplicationService.kt`, `WorkflowArtifacts.kt` |
| Render, mix, texture, master/release measurements | `StemRenderResult`, `MixSnapshot`, `DesktopReleaseMetadata` | `stems/`, `mix/`, `output/`; renderer, mix service, build service/worker | Build, export, desktop readiness | Derived outputs; WAV validation, format compatibility and release hash checks guard publication | `AUDIO_TEXTURE` is an artifact but has no `StageId` / read-model stage. Task 120 records taxonomy; Task 129 reports evidence. `arrangement/StemRenderingMixer.kt`, `application/MixApplicationService.kt`, `application/BuildApplicationService.kt` |

## Current mutation and artifact inventory

The table captures the real boundary, not intended future order. “Approval” is
the current behavior; a retained draft or stale file is never selected merely
because it exists.

| Stage | Current input and output | Approval / bypass | Report, hashes, and validation | Invalidation and evidence |
| --- | --- | --- | --- | --- |
| AI Fix | Corrected MIDI selected after clean/normalization/transposition; draft `midi/ai-fix/<part>/draft.mid`, then approved copy | `PENDING`, explicit `APPROVED`, or `SKIP` back to corrected MIDI | `plan.json`, `diff.json`, `audit.json`, `provenance.json`; input/output SHA-256; strict plan, collision simulation, output MIDI validation | AI-fix selection invalidates enhanced/feel/analysis and downstream workflow artifacts. `application/MidiAiFixApplicationService.kt`, `arrangement/MidiAiFix.kt`, `arrangement/WorkflowArtifacts.kt` |
| Per-track Enhance | Selected corrected/AI-fix base plus `MusicalProcessingContext`; immutable `midi/enhancement/<part>/<context-hash>/enhanced.mid` | Draft/approved/rejected evidence; `CORRECTED` selection is the off/bypass branch | plan, report, provenance and input/output/context hashes; bounded plan/applier validation | Enhancement selection invalidates feel, analysis, Cohesion and descendants. `application/EnhancementApplicationService.kt`, `arrangement/Enhancement.kt`, `WorkflowArtifacts.kt` |
| MIDI Feel | Current AI-fix base; `midi/derived/<part>/lofi-80-swing-v1.mid` | `analysisInput` selects `CURRENT` or `LOFI_FEEL`; current remains the bypass | Report records source/output hashes, tempo map, moved notes, collision repairs; output preserves PPQ, note identities and meter | MIDI-feel change invalidates analysis and descendants. It deliberately changes MIDI tempo to its fixed profile. `arrangement/MidiLoFiFeel.kt`, `SelectedMidiArtifactResolver.kt`, `WorkflowArtifacts.kt` |
| Arrangement | Analyses, saved occurrence structure, declared harmony/settings and role selections; plan plus detailed arrangement | Deterministic planner writes approved output; Qwen detailed arrangement is an explicit draft requiring approval | Plan/arrangement validity and approved reference hashes: arrangement, structure, occurrence, context, plan | Arrangement change invalidates Cohesion, generated MIDI, Humanization and audio descendants. `application/ArrangementApplicationService.kt`, `arrangement/GlobalSongPlanner.kt`, `arrangement/DetailedArrangement.kt` |
| Generated roles | Approved detailed arrangement and MIDI analyses; `midi/generated/{bass,drums,pad,strings,transitions}.mid` as applicable | No separate per-role approval; generated set is bound to approved arrangement | `GeneratedMidiWorkflowReferences` stores each output hash and arrangement hash; generators validate their own output | Generation invalidates Cohesion and descendants. `application/ArrangementApplicationService.kt`, `WorkflowArtifacts.kt` |
| Cohesion | Selected occurrence MIDI, approved arrangement, structure, generated roles, policy and model plan; cohesion occurrence/role derivatives and boundary bridges | Draft, per-boundary review, explicit approve; reject retains historical rejected plan | Full input/structure/arrangement/context hashes; plan validation, boundary audit files, generated role and occurrence hashes, optional A/B previews | Cohesion change invalidates Humanization and all audio descendants. Current v5 plan also applies full-song `songEdits` to melody and generated roles. `application/CohesionApplicationService.kt`, `arrangement/TransitionCohesion.kt`, `WorkflowArtifacts.kt` |
| Humanization | Approved Cohesion occurrence/role derivatives; `midi/humanized/<run-hash>/*.mid` | Explicit `HUMANIZED`; explicit `BYPASS` makes render use cohesive input | Deterministic seeded processor; per-file reports plus aggregate report, input/output hashes and config | Humanization change invalidates stems and audio descendants. It changes timing, velocity, duration and chord staggering, not pitch/note count. `application/HumanizationApplicationService.kt`, `arrangement/SeededHumanization.kt`, `WorkflowArtifacts.kt` |
| Render | Build validates selected humanization and fingerprinted generated ensemble, then renders approved stems | No standalone approval; completion is actual renderer output/reuse after validation | Renderer/stem mixer validates MIDI and output audio; stem state participates in readiness | Stems are stale from upstream changes. Renderer consumes humanized artifacts if selected, otherwise cohesive ones. `application/BuildApplicationService.kt`, `application/ArrangementApplicationService.kt`, `arrangement/StemRenderingMixer.kt` |
| Mix | Validated rendered stems and persisted mix settings; `mix/dry.wav` | User applies settings; no approval branch | Stem format checks and deterministic mixer write; project marks dry mix current | `MIX_ONLY` invalidates dry mix and audio descendants. `application/MixApplicationService.kt`, `arrangement/WorkflowArtifacts.kt` |
| Texture | Worker-repaired dry mix; optional Kotlin DSP result `mix/lofi.wav` | Build request enables or skips it; skip masters repaired mix | Lossless WAV atomic publication and format compatibility checks | `AUDIO_TEXTURE` invalidates texture/master/release, although this stage is absent from `StageId` and `WorkflowReadModel`. `application/BuildApplicationService.kt`, `WorkflowArtifacts.kt` |
| Master | Repaired or textured WAV; worker writes `output/master.wav` | Build-stage outcome, no separate approval | PCM-24 decode, format compatibility, finite samples, and -1 dB peak ceiling; release metadata binds master SHA-256 | Build marks `MASTER` and `RELEASE` current only after metadata writes. `application/BuildApplicationService.kt` |
| Release/export | Validated `output/master.wav` and release metadata; user target WAV or optional MP3 | Explicit export only; target cannot overwrite master or an existing file | Master digest checked before/after; exported output decoded/validated; MP3 is final conversion | Export does not alter authoritative master. `application/ReleaseExportApplicationService.kt` |

## Current service order and mismatches

### Actual service order

The current application-service order is:

```text
source/raw → clean → normalize → transpose → technical correction
  → AI Fix selection → per-track Enhance selection → MIDI Feel selection
  → MIDI analysis → saved structure → arrangement plan/detail/approval
  → deterministic generated roles → Cohesion & Enhance approval
  → seeded Humanization or explicit bypass → stem render → mix → repair
  → optional audio texture → worker master → optional MP3/release metadata
```

This follows `SelectedMidiArtifactResolver` for per-part selection,
`DefaultArrangementApplicationService` before
`DefaultCohesionApplicationService`, and the runtime order in
`DefaultBuildApplicationService`; it is not inferred from desktop labels.
Generated MIDI may be prepared by the Cohesion preparation boundary before its
input is built, but it is still validated as an approved-arrangement derivative
before Cohesion and build.

### Recorded discrepancies and assigned owners

| Surface | Current verified behavior | Difference from the canonical pipeline / owner |
| --- | --- | --- |
| `StageId` and `StageRunDependencyGraph` | Has `SOURCE` through `EXPORTED`, but lacks wire IDs for AI Fix, MIDI Feel, critiqued, full-song enhanced, humanized and audio textured. Project-stage list orders `COHESION` before `ARRANGED` and `GENERATED`. | Task 120 adds durable taxonomy and correct order without renaming existing wire values. Evidence: `arrangement/StageRuns.kt` |
| `WorkflowArtifactGraph` | Tracks `AI_FIX`, `MIDI_FEEL`, `HUMANIZATION` and `AUDIO_TEXTURE`, but has no separate critic or full-song-enhance artifacts and treats Cohesion as the full-song mutation. | Tasks 120, 126–128 define corrected dependencies and invalidation. Evidence: `arrangement/WorkflowArtifacts.kt` |
| Project workflow references | Stores arrangement approval, generated MIDI, Cohesion (including occurrence and role derivatives), humanization selection/run, and the stale set. | No critic/full-song-enhancement reference; Cohesion v5 owns `songEdits`. Tasks 120, 126–128. Evidence: `arrangement/WorkflowArtifacts.kt`, `arrangement/TransitionCohesion.kt` |
| Readiness and `WorkflowReadModel` | `WorkflowReadModel` orders Arrangement before Cohesion, then Humanization/render/mix/master/export. It exposes AI Fix and MIDI Feel, but no technical-correction, normalization/transposition, generation, critic, full-song enhancement, or texture stage. | Task 120 aligns UI-independent readiness with actual durable stages; Tasks 127–128 add the new stages. Evidence: `application/WorkflowReadModel.kt` |
| Build/render input resolution | Build requires current approved arrangement and current approved “full-song Cohesion & Enhance,” validates generated MIDI and humanization, then renders. `OccurrenceMidiArtifactResolver` uses approved Cohesion occurrence output or selected part fallback; humanization uses Cohesion roles. | Cohesion currently performs both boundary and whole-song work; no critic/full-song-enhance selection precedes Humanization. Tasks 126–128. Evidence: `application/BuildApplicationService.kt`, `arrangement/OccurrenceMidiArtifactResolver.kt`, `application/HumanizationApplicationService.kt` |
| Desktop navigation and creation progress | Primary UI destinations are Info, Setup, Harmony, Melody Parts, Structure, Arrange, Mix & Master; other destinations live in More. Creation progress stops at Cohesion before a combined mix/master presentation. | Navigation is deliberately a product projection rather than a durable stage graph; it cannot show critic/full-song enhancement separately today. Task 120 aligns stage/read-model evidence, and Tasks 127–129 add the review surfaces. Evidence: `desktopApp/src/main/kotlin/app/melotrail/desktop/WorkspaceViewModel.kt`, `WorkspaceShellFrame.kt`, `CreationProgress.kt` |
| AI Fix context | `MidiAiFixInputFactory.build(partId, corrected)` analyzes corrected MIDI and supplies inferred key/chords; the service does not pass project settings, harmony, occurrences or anchors. | Task 122 changes the input to a canonical authority projection; Task 121 supplies shared melody identity/anchors. Evidence: `application/MidiAiFixApplicationService.kt`, `arrangement/MidiAiFix.kt` |
| Enhance harmonic validation | Enhancement has project key, section chord lists and bounded plans, but its context is not an occurrence/tick harmonic timeline and validators do not consistently check every pitch edit against the active chord. | Task 123. Evidence: `arrangement/Enhancement.kt`, `arrangement/ValidatedEnhancementMidiApplier.kt` |
| Arrangement criticism | `ArrangementCritic` critiques `DetailedArrangement` before generated MIDI; the deterministic implementation always accepts. | It is an earlier arrangement-plan concern, not a post-Cohesion full-song critic. Task 127 names/separates it. Evidence: `arrangement/ArrangementCritic.kt` |
| Role validators and mutation evidence | Generators and mutation stages have local validation/reports, but no one reusable melody identity/anchor contract or consistent role comparison report. | Tasks 121, 125 and 129. Evidence: `arrangement/SeededHumanization.kt`, `arrangement/TransitionCohesion.kt`, generator files |
| End-to-end evidence and rollout | Existing canonical workflow tests cover current behavior; no deterministic reference-song fixture proves the target pipeline. | Task 130, after the preceding contracts exist. Evidence: `src/test/kotlin/app/melotrail/application/EndToEndWorkflowCompatibilityTest.kt` |

The documented canonical v4 settings/harmony, AI Fix inferred-harmony input,
Cohesion v5 `songEdits`, deterministic Humanization, pre-generation arrangement
criticism, and lack of need for duplicate numbered MIDI history are all current
starting findings. The artifact model already provides project-relative,
hash-bound evidence; a parallel numbered history tree would duplicate that
authority and is not required by any future task.

## Verification commands

The following focused checks support this audit and are safe to repeat:

```bash
rg -n 'data class (Project|CompositionSettings|StructureOccurrence|MidiAnalysis|MidiAiFixInput|MusicalProcessingContext|TransitionCohesionInput|HumanizationWorkflowReferences)' src/main/kotlin
rg -n 'enum class StageId|StageRunDependencyGraph|WorkflowArtifactGraph|WorkflowReadModelDeriver|songEdits|ArrangementCritic' src/main/kotlin desktopApp/src/main/kotlin
rg -n 'renderApprovedStems|requireCurrentGeneratedMidi|selectBypass|generateRequiredMidi' src/main/kotlin/app/melotrail
```

Every row above has a code evidence path. No absent implementation is silently
treated as an artifact: critic and full-song-enhancement entries are explicitly
recorded as missing and assigned to their contract tasks.
