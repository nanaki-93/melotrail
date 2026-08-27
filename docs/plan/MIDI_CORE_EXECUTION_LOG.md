# MIDI Core execution log

Status: MC-012 complete; MC-013 ready

Task authority: `MIDI_CORE_TASKS.md`

Execution prompt: `EXECUTE_MIDI_CORE_TASKS_PROMPT.md`

This file is evidence, not a second plan. Update it after every task and commit.

## 1. Baseline

- Repository root: `/Users/marcoandreose/DEV/lab/melotrail`
- Branch: `main` (ahead of `origin/main` by two approved documentation commits)
- Starting commit: `a7f03b7 Plan for cleaning and refactor the purpose of the project`
- Starting status: clean
- Preserved unrelated changes: none
- JDK/Gradle/macOS: OpenJDK 21.0.11 LTS; Gradle wrapper 8.14.3; macOS 26.6.2 (25G83)
- Production Kotlin files/lines: 319 / 53,630
- Test Kotlin files/lines: 171 / 23,370
- Python files/lines: 28 / 4,201 in `worker/` (additional obsolete tools and ignored environments are deletion inventory)
- Repository-local old audio data size: 303 MB in ignored `data/audio`
- Local sound-library size: 10 GB in `sounds` (two tracked metadata files; remaining library data is ignored)
- Legacy UI fixture size: 15 MB in `docs/pictures`; tracked root video is 4.8 MB
- Baseline `make test`: PASS — `make test` (2026-08-26; 14 Gradle tasks up-to-date)
- Baseline `make build`: PASS — `make build` (2026-08-26; documentation coverage executed and all 15 Gradle tasks succeeded)
- Recorded by/date: Codex / 2026-08-26

## 2. Status vocabulary

- `TODO` — no task work started.
- `IN_PROGRESS` — the only active task.
- `AWAITING_HUMAN` — automated work complete; required manual evidence pending.
- `BLOCKED` — genuine unresolved authority/external blocker with unblock
  condition recorded.
- `DONE` — implementation, deletion, tests, evidence, and commit complete.

## 3. Task ledger

| Task | Status | Commit | Validation | Evidence / decision |
| --- | --- | --- | --- | --- |
| MC-000 | DONE | `midi-core: MC-000 freeze execution baseline` | PASS — local Markdown links, `git diff --check`, `make test`, `make build` | Clean baseline at `a7f03b7`; no unrelated changes; metrics recorded below. |
| MC-001 | DONE | `midi-core: MC-001 add owned MIDI fixtures` | PASS — `OwnedMidiFixturesTest`; `make test` | Ten hand-authored, SHA-256-pinned fixtures cover all Phase 1 reader inputs without legacy audio-project data. |
| MC-002 | DONE | `midi-core: MC-002 characterize reusable MIDI safety` | PASS — focused characterization and `make test` | Retained/extract/delete owner map recorded below; no legacy owner was adopted wholesale. |
| MC-003 | DONE | `midi-core: MC-003 enforce target boundaries` | PASS — architecture rules, `make test`, `make build` | Target package policy is executable; legacy packages are intentionally outside the new roots until cutover. |
| MC-004 | DONE | `midi-core: MC-004 add semantic MIDI model` | PASS — `SemanticMidiTest`; `make test` | Immutable target semantic sequence records source/event identity, deterministic ordering, supported event types, and one rational tick-rounding policy without Java MIDI types. |
| MC-005 | DONE | `midi-core: MC-005 add Standard MIDI reader` | PASS — `JdkMidiReaderTest`; `make test` | One target adapter reads SMF 0/1 PPQ into semantic MIDI and deterministic track summaries without source mutation. |
| MC-006 | DONE | `midi-core: MC-006 classify MIDI findings` | PASS — target validator, reader, architecture tests, `make test` | Stable typed source findings classify every inspected input as accepted, rejected, or awaiting authority without legacy audio validation. |
| MC-007 | DONE | `midi-core: MC-007 add deterministic MIDI writer` | PASS — writer/reader/architecture tests, `make test`, `make build` | One target SMF format-1 writer owns conductor metadata, role ordering, channel remapping, marker sanitization, and aligned role files. |
| MC-008 | DONE | `midi-core: MC-008 prove MIDI export round trip` | PASS — focused export suite, `make test`, `make build` | Staged five-file core-role bundle is semantically re-imported, digest-bound, collision-safe, and test-only. |
| MC-009 | DONE | `midi-core: MC-009 record DAW compatibility spike` | PASS — automated preparation, GarageBand 10.4.14, Logic Pro 12.3.1 | Both DAWs imported the core-role bundle with correct timing/roles and safe playback; marker display is non-blocking metadata and was unassessed. |
| MC-010 | DONE | `midi-core: MC-010 define MIDI project schema` | PASS — schema/architecture tests, `make test`, `make build` | Strict MIDI Core v1 DTO boundary and golden document contain only target metadata, MIDI authority, candidate acceptance, and export ownership. |
| MC-011 | DONE | `midi-core: MC-011 add MIDI artifact store` | PASS — artifact-store/architecture tests, `make test`, `make build` | Target layout is path-confined, SHA-256-bound, immutable, and preserves the last good project JSON after an interrupted save. |
| MC-012 | DONE | `midi-core: MC-012 add project lifecycle` | PASS — lifecycle/store/schema tests, `make test`, `make build` | Target lifecycle creates, reopens, saves, closes, and safely rejects legacy project files without invoking a worker or migration. |
| MC-013 | TODO | | | |
| MC-014 | TODO | | | |
| MC-015 | TODO | | | |
| MC-016 | TODO | | | |
| MC-017 | TODO | | | |
| MC-018 | TODO | | | |
| MC-019 | TODO | | | |
| MC-020 | TODO | | | |
| MC-021 | TODO | | | |
| MC-022 | TODO | | | |
| MC-023 | TODO | | | |
| MC-024 | TODO | | | |
| MC-025 | TODO | | | |
| MC-026 | TODO | | | |
| MC-027 | TODO | | | |
| MC-028 | TODO | | | |
| MC-029 | TODO | | | |
| MC-030 | TODO | | | |
| MC-031 | TODO | | | |
| MC-032 | TODO | | | |
| MC-033 | TODO | | | |
| MC-034 | TODO | | | |
| MC-035 | TODO | | | |
| MC-036 | TODO | | | |
| MC-037 | TODO | | | |
| MC-038 | TODO | | | |
| MC-039 | TODO | | | |
| MC-040 | TODO | | | |
| MC-041 | TODO | | | |
| MC-042 | TODO | | | |
| MC-043 | TODO | | | |
| MC-044 | TODO | | | |
| MC-045 | TODO | | | |
| MC-046 | TODO | | | |
| MC-047 | TODO | | | |
| MC-048 | TODO | | | |
| MC-049 | TODO | | | |
| MC-050 | TODO | | | |
| MC-051 | TODO | | | |
| MC-052 | TODO | | | |
| MC-053 | TODO | | | |
| MC-054 | TODO | | | |
| MC-055 | TODO | | | |
| MC-056 | TODO | | | |
| MC-057 | TODO | | | |
| MC-058 | TODO | | | |
| MC-059 | TODO | | | |
| MC-060 | TODO | | | |

## 4. Phase gates

| Gate | Tasks | Status | Evidence |
| --- | --- | --- | --- |
| G0 Documentation ready | MC-000 | DONE | `7dee33b`; documentation baseline, link audit, `make test`, and `make build` are recorded under MC-000. |
| G1 MIDI compatibility proven | MC-001–MC-009 | DONE | MC-008 semantic/export gate and MC-009 GarageBand 10.4.14 plus Logic Pro 12.3.1 manual imports pass. |
| G2 MIDI project kernel complete | MC-010–MC-019 | TODO | |
| G3 Vertical slice complete | MC-020–MC-030 | TODO | |
| G4 Focused desktop complete | MC-031–MC-040 | TODO | |
| G5 Product behavior accepted | MC-041–MC-049 | TODO | |
| G6 Legacy product removed | MC-050–MC-059 | TODO | |
| G7 MVP complete | MC-060 | TODO | |

## 5. Per-task evidence template

Copy this block below for the active task:

```text
### MC-NNN — title

Status:
Started:
Completed:
Starting commit/status:
Contracts read:
Current owners inspected:
Behavior retained/extracted:
Files added/changed:
Files/data deleted:
Tracked deletion recoverability:
Ignored deletion recoverability:
Focused tests:
Full validation:
Manual evidence:
Decisions/deviations:
Known limitations:
Commit:
Next task:
```

### MC-000 — Freeze the approved MIDI Core baseline

Status: DONE
Started: 2026-08-26
Completed: 2026-08-26
Starting commit/status: `a7f03b7` / clean worktree on `main`; branch is ahead of `origin/main` by the two approved documentation commits.
Contracts read: AGENTS.md; PLAN.md; README.md; docs/README.md; ARCHITECTURE.md; FUNCTIONAL_SPEC.md; MIDI_CONTRACT.md; DAW_COMPATIBILITY.md; CLEANUP_SCOPE.md; QUALITY_GATES.md; MIDI_CORE_TASKS.md; MIDI_CORE_EXECUTION_LOG.md.
Current owners inspected: root/docs index; Makefile; Gradle verification wiring; repository status/history; task suite.
Behavior retained/extracted: None. MC-000 is documentation and execution-baseline work only.
Files added/changed: `PLAN.md`, `README.md`, `docs/README.md`, and `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: Local Markdown link audit PASS; `git diff --check` PASS.
Full validation: `make test` PASS (14 Gradle tasks up-to-date); `make build` PASS (15 Gradle tasks; the legacy documentation-inventory check executed successfully).
Manual evidence: Not required.
Decisions/deviations: The planning baseline was already committed at start; no unrelated user changes exist. Only PLAN.md, MIDI_CORE_TASKS.md, and EXECUTE_MIDI_CORE_TASKS_PROMPT.md are active plan/prompt candidates; the prompt is an execution aid, not a competing roadmap. Existing Python documentation coverage is recorded as legacy build wiring to be removed in MC-058, not adopted by target work.
Known limitations: The legacy build still invokes Python documentation coverage and exposes worker/audio targets; those are recorded deletion scope for MC-054 and MC-058, not target behavior.
Commit: `7dee33b` — `midi-core: MC-000 freeze execution baseline`.
Next task: MC-001 after MC-000 validation and commit.

### MC-001 — Establish owned Standard MIDI fixtures

Status: DONE
Started: 2026-08-26
Completed: 2026-08-26
Starting commit/status: `7dee33b` / clean worktree after MC-000.
Contracts read: F-MIDI-001 and F-MIDI-004; MIDI Contract sections 2 and 5; Quality Gate 3; MC-001 task contract.
Current owners inspected: `MidiTestFixtures.kt`, `CanonicalMidiFixture.kt`, `MidiTimeMappingTest.kt`, and `TranscriptionQualityGateTest.kt`; every tracked test resource; every MIDI file under the repository. No target-owned checked-in MIDI fixture existed. Discovered MIDI bytes are generated build output or ignored legacy `data/audio` state and are not reused.
Behavior retained/extracted: Reused only the proven PPQ-480 test convention. The new fixture source is independent of legacy writers so parser tests cannot validate their own output path.
Files added/changed: `src/test/kotlin/app/melotrail/midi/OwnedMidiFixtures.kt`, `src/test/kotlin/app/melotrail/midi/OwnedMidiFixturesTest.kt`, and `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.midi.OwnedMidiFixturesTest` PASS (two tests; materialization, SHA-256, SMF header, format, track count, bounded size, and purpose coverage).
Full validation: `make test` PASS (2026-08-26; 14 Gradle tasks, 27 seconds).
Manual evidence: Not required.
Decisions/deviations: Fixtures are hand-authored byte definitions in test source rather than opaque binary resources. This makes their source, purpose, and exact byte/hash contract reviewable while `writeAll` supplies ordinary `.mid` files to every later parser/export test. The failed root-only filter was corrected to `:test`; the initial all-project filter incorrectly required the test class in `desktopApp`.
Fixture manifest/hashes: `smf0-melody.mid` `a2e32b1df5e78867193191a15c82caaa0b7c070b2e328c56b41a1ea5aaba4a35`; `smf1-reference-tracks.mid` `f3166580ebc70d96168ad238471762d20882d86d967a02389b69a96b4c52af67`; `pickup-timing.mid` `edea690670c84305fe8d5ba17e13b3fa3567921faaafb41094dfe1b32242cb7f`; `sub-bar-harmony.mid` `507dd7d2f2b57d86b2c95b2019d7b5daf649d63fcb2a13861c5f58bd8ab1dd88`; `expressive-controller-pitch.mid` `9c08782d6e56327ea64b5ed6aebaf2158b41cea32e95ec6fcb52f79238580ef5`; `velocity-zero-note-off.mid` `3006621283cf65a5446dfa4c48b919e71445b830861e77e83dd81f33e2d98bae`; `final-boundary-note.mid` `08dde8e7da1e32fbbe6bbfa71937fc2c95e3fa778928a13479e323f163e66044`; `truncated-header.mid` `7ed5302ab537819c49fb41c3670d2080240a3c05af841b51bb04ced49d11f4a1`; `format-2.mid` `fd8d72b9fa38e47ec870001b8db2828ac60c0874e947e330f2d4c844cf933c5b`; `smpte-division.mid` `d18577cd143c20baf9b75f2b36a5369a426c6e8c7ba02074fc26b495fe9646cc`.
Known limitations: Semantic parser assertions begin in MC-005; MC-001 intentionally locks input facts and does not adopt legacy parsing behavior.
Commit: `da01dd5` — `midi-core: MC-001 add owned MIDI fixtures`.
Next task: MC-002 after MC-001 validation and commit.

### MC-002 — Characterize reusable MIDI and artifact behavior

Status: DONE
Started: 2026-08-26
Completed: 2026-08-26
Starting commit/status: `da01dd5` / clean worktree after MC-001.
Contracts read: F-MIDI-005, F-PROJ-004, F-SYS-004, and MC-002 task contract.
Current owners inspected: all `javax.sound.midi` users; `MidiPartAnalyzer`; `ProjectStore`; `StageRunStore`; `WorkflowArtifacts`; `ArrangementState`; `SelectedMidiArtifactResolver`; their focused tests; and duplicated SHA-256 helpers across legacy application/arrangement code.
Behavior retained/extracted: `MidiPartAnalyzer` contributes only proven PPQ/event-pairing, velocity-zero, tempo/meter, and track-name facts for extraction into MC-004/MC-005; its inference and audio-era ownership are not retained. `StageRunStore` and `WorkflowArtifactReference` contribute streaming SHA-256, root confinement (including real-path/symlink validation), immutable publication, and failed-index orphan evidence for extraction into MC-011/MC-019. `ProjectStore` contributes atomic-write recovery semantics only; its schema-v4 DTO and render fields are deleted in MC-050. Existing candidate/selected-artifact state contributes digest-before-selection and no-fallback behavior for extraction into MC-019/MC-026; its stage graph and mutation pipeline are deletion scope.
Files added/changed: `src/test/kotlin/app/melotrail/arrangement/LegacyMidiArtifactCharacterizationTest.kt` and `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.arrangement.LegacyMidiArtifactCharacterizationTest` PASS (JDK SMF facts, velocity-zero pairing, deterministic digest, confined reference rejection, immutable append, and tamper rejection). Existing `StageRunStoreTest` independently proves failed-index publication leaves an inspectable orphan rather than a visible run.
Full validation: `make test` PASS (2026-08-26; 14 Gradle tasks, 20 seconds).
Manual evidence: Not required.
Decisions/deviations: No code was extracted yet: MC-002 is intentionally a characterization boundary. The source-owned MC-001 fixtures are used rather than legacy generated MIDI. The legacy analyzer's automatic key/chord inference is expressly excluded because project authority must be explicit.
Known limitations: Current Java MIDI access remains widely scattered until MC-003 through MC-008 establish replacement boundaries; this task records the migration map rather than creating an adapter prematurely.
Commit: `d0da538` — `midi-core: MC-002 characterize reusable MIDI safety`.
Next task: MC-003 after MC-002 validation and commit.

### MC-003 — Enforce target dependency boundaries

Status: DONE
Started: 2026-08-26
Completed: 2026-08-26
Starting commit/status: `d0da538` / clean worktree after MC-002.
Contracts read: Architecture sections 3 and 5; F-SYS-001; F-SYS-002; and MC-003 task contract.
Current owners inspected: root and desktop Gradle modules; root source package inventory; desktop import graph; existing domain candidates; all existing Java MIDI owners.
Behavior retained/extracted: Target source roots are `project`, `midi/domain`, `music/core`, `structure`, `arrangement/core`, `review`, and `export/domain`; raw Java MIDI is allowed only in `midi/adapter` and `audition/adapter`; future focused Compose code is under `desktop/target`. These distinct roots let the migration enforce the target architecture while legacy packages remain compiled solely until their owning deletion tasks.
Files added/changed: `src/test/kotlin/app/melotrail/architecture/TargetArchitectureRulesTest.kt` and `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.architecture.TargetArchitectureRulesTest` PASS. The test proves violations fail for Compose/filesystem/HTTP/raw-MIDI imports in target domain code, raw-MIDI parsing in target desktop code, and raw MIDI outside target adapters.
Full validation: `make test` PASS (2026-08-26; 14 Gradle tasks, 20 seconds); `make build` PASS (15 Gradle tasks; documentation coverage executed).
Manual evidence: Not required.
Decisions/deviations: The task creates no target service, port, worker boundary, or compatibility adapter. Existing legacy application/arrangement/desktop packages are intentionally not scanned by the new policy because they are scheduled for replacement and deletion rather than adoption; every new target class is now governed from its first commit.
Known limitations: The target roots become populated in MC-004 onward; their empty initial state is deliberate and covered by concrete synthetic violation tests so the policy itself cannot pass vacuously.
Commit: `515839e` — `midi-core: MC-003 enforce target boundaries`.
Next task: MC-004 after MC-003 validation and commit.

### MC-004 — Implement the immutable semantic MIDI model

Status: DONE
Started: 2026-08-26
Starting commit/status: `515839e` / clean worktree after MC-003.
Contracts read: F-MIDI-001 through F-MIDI-005; MIDI Contract section 4; MC-004 task contract.
Completed: 2026-08-26
Current owners inspected: `MidiAnalysis.kt` (`MidiNote`, tempo/meter facts, and JDK parsing); `MidiTimeMapping.kt` (source track/index ordering and timing rounding); `StageComparisonService.kt` (stable note comparison order); `BassStemGeneration.kt` and `PadMidiGeneration.kt` (legacy generated-note shapes). These remain legacy owners for their existing callers.
Behavior retained/extracted: Retained the safe source track/event identity and stable ordering concepts only. The target model owns source identity, PPQ, reduced rational beat positions, nearest-tick/ties-up rounding, source/generated event keys, immutable tracks/sequences, and typed note/controller/pitch-bend/channel-pressure/tempo/meter/name/marker/text/unsupported records. It neither imports `javax.sound.midi` nor adopts legacy analysis, inferred harmony, mutable timing repair, or audio-era role models.
Files added/changed: `src/main/kotlin/app/melotrail/midi/domain/SemanticMidi.kt`; `src/test/kotlin/app/melotrail/midi/domain/SemanticMidiTest.kt`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.midi.domain.SemanticMidiTest` PASS (five tests: immutable snapshots, global ordering, source/generated key invariants, value boundaries, all supported event categories, exact/rational rounding, and overflow).
Full validation: `make test` PASS (2026-08-26; 14 Gradle tasks, 28 seconds).
Manual evidence: Not required.
Decisions/deviations: The source identity accepts only format 0/1 now because this target semantic model intentionally has no format-2 representation. Event ordering is tick, semantic priority, source track/event identity, then generated key; keys are unique sequence-wide. Rounding is non-negative nearest tick with half values rounded up; overflow is an explicit failure.
Known limitations: The JDK reader/writer remains in legacy owners until MC-005 and MC-007 route parsing/output through the target adapter. The semantic model has no persistence annotation yet because project serialization is owned by MC-010.
Commit: `midi-core: MC-004 add semantic MIDI model`.
Next task: MC-005 after MC-004 validation and commit.

### MC-005 — Implement the Standard MIDI reader and track inspector

Status: DONE
Started: 2026-08-26
Starting commit/status: `e429e4f` / clean worktree after MC-004.
Contracts read: F-MIDI-001 through F-MIDI-003; MIDI Contract sections 2 through 5; MC-005 task contract.
Completed: 2026-08-26
Current owners inspected: Every production `MidiSystem` owner was inventoried. `MidiAnalysis.kt` is the current direct reader/track analyzer; `MidiTimeMapping.kt`, `StageComparisonService.kt`, and old role readers contain local ordering/pairing variants. They remain legacy owners for their current callers and are scheduled for later cutover/deletion.
Behavior retained/extracted: `JdkMidiReader` is the only target parsing path. It computes source identity, rejects format 2 and SMPTE, translates JDK messages to MC-004 semantic events, pairs notes safely (including velocity-zero note-off), retains controller/pitch/channel-pressure metadata, records unsupported messages and recoverable orphan/unclosed/non-positive pairing findings, and emits ordered track/channel summaries with advisory role hints. It never writes or repairs the source.
Files added/changed: `src/main/kotlin/app/melotrail/midi/adapter/JdkMidiReader.kt`; `src/test/kotlin/app/melotrail/midi/adapter/JdkMidiReaderTest.kt`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.midi.adapter.JdkMidiReaderTest` PASS (seven tests: all valid owned fixtures; SMF 0/1 semantic snapshots and source immutability; track/channel/marker/controller/pitch/channel-pressure facts; velocity-zero note-off; orphan/unclosed findings; unsafe same-pitch overlap; malformed, format-2, and SMPTE rejection; omitted-message evidence).
Full validation: `make test` PASS (2026-08-26; 14 Gradle tasks, 27 seconds).
Manual evidence: Not required.
Decisions/deviations: A same-track/channel/pitch overlap is rejected immediately because V1 has no selected safe pairing policy. Orphan and unclosed notes are reader findings so MC-006 can classify them precisely after melody selection. Program changes are represented as explicitly omitted-message findings; no output policy is inferred from them. Role hints are advisory summary facts only.
Known limitations: Typed blocking/advisory user-facing classification belongs to MC-006. The adapter currently has no project import caller; MC-013 will make this target reader the project source-import path.
Commit: `midi-core: MC-005 add Standard MIDI reader`.
Next task: MC-006 after MC-005 validation and commit.

### MC-006 — Implement blocking and advisory MIDI validation

Status: DONE
Started: 2026-08-26
Starting commit/status: `c94b1e2` / clean worktree after MC-005.
Contracts read: F-MIDI-004, F-SYS-004; MIDI Contract section 7; MC-006 task contract.
Completed: 2026-08-26
Current owners inspected: `MidiQualityReport.kt`, `MidiMonophonicMelodyPreparation.kt`, `GeneratedRoleValidation.kt`, and `BassQualityValidator.kt`; their tests; and the MC-005 target reader/semantic model. The legacy validators depend on transcription cleanup, analysis confidence, audio-stage state, render instruments, or mutable correction, so none satisfies the source-import contract.
Behavior retained/extracted: Parser issues, track/channel summaries, and inspection results moved to the semantic MIDI domain so deterministic validation can consume them without JDK MIDI types. `MidiImportValidator` now returns one stable, typed finding list with scope, severity, message, action, and an accepted/rejected/awaiting-authority disposition. It blocks unsafe timing, changing/non-origin tempo or meter, invalid/missing selected melody, and selected-track unclosed notes; it keeps orphan note-offs, reference-track unclosed notes, unsupported messages, polyphony, and chromatic melody advisory. Missing fixed tempo/meter or melody selection is awaiting explicit authority.
Files added/changed: `src/main/kotlin/app/melotrail/midi/domain/MidiInspection.kt`, `src/main/kotlin/app/melotrail/midi/domain/MidiImportValidation.kt`, `src/main/kotlin/app/melotrail/midi/adapter/JdkMidiReader.kt`, `src/test/kotlin/app/melotrail/midi/domain/MidiImportValidatorTest.kt`, `src/test/kotlin/app/melotrail/midi/adapter/JdkMidiReaderTest.kt`, and `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.midi.domain.MidiImportValidatorTest --tests app.melotrail.midi.adapter.JdkMidiReaderTest --tests app.melotrail.architecture.TargetArchitectureRulesTest` PASS (13 tests; table-driven accepted/awaiting/rejected dispositions; fixed-map checks; selected-melody pairing; malformed timing; stable ordering; and explicit polyphony/chromatic advisory classification).
Full validation: `make test` PASS (2026-08-26; 14 Gradle tasks, 27 seconds).
Manual evidence: Not required.
Decisions/deviations: Repeated equal tempo/meter facts are accepted as one effective setting; an event that starts away from tick zero is blocked because target authority requires both values at the song origin. Key compatibility is deliberately an optional advisory pitch-class set; it does not replace later explicit key/mode/harmony authority. Structural reader failures (unreadable source, format 2, SMPTE, same-pitch overlap) remain immediate typed reader errors and will be mapped by the MC-013 import use case.
Known limitations: Target validation currently classifies an inspected source only; atomic import publication and UI-ready error mapping are owned by MC-012 and MC-013. MPE-like multi-channel protected-melody selection is owned by MC-014.
Commit: `midi-core: MC-006 classify MIDI findings`.
Next task: MC-007 after MC-006 validation and commit.

### MC-007 — Implement the deterministic Standard MIDI writer

Status: DONE
Started: 2026-08-26
Completed: 2026-08-26
Starting commit/status: `e1591df` / clean worktree after MC-006.
Contracts read: F-EXP-002 and F-EXP-003; MIDI Contract sections 9 through 12; MC-007 task contract.
Current owners inspected: `BassStemGeneration.kt`, `DrumMidiGeneration.kt`, `PadMidiGeneration.kt`, `StringsMidiGeneration.kt`, `MidiTimeMapping.kt`, `OccurrenceMidiArtifactResolver.kt`, their writer tests, and MC-005 semantic reader. Legacy writers are individually coupled to audio-era roles, instruments, stage artifacts, or inferred timing and remain compiled only for their legacy callers.
Behavior retained/extracted: `JdkMidiWriter` is the one target SMF format-1 output adapter. A target export model owns deterministic Melody/Chords/Bass/Drums ordering, channels 1/2/3/10, conductor sequence/tempo/meter/marker metadata, sanitized marker labels, role-file assembly, end-of-track boundaries, and the generated-role note-only policy. Melody controllers, pitch bend, and channel pressure are preserved and remapped consistently; program, SysEx, and unsupported messages have no semantic write path.
Files added/changed: `src/main/kotlin/app/melotrail/midi/domain/MidiExportModel.kt`, `src/main/kotlin/app/melotrail/midi/adapter/JdkMidiWriter.kt`, `src/test/kotlin/app/melotrail/midi/adapter/JdkMidiWriterTest.kt`, `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`, and `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.midi.adapter.JdkMidiWriterTest --tests app.melotrail.midi.adapter.JdkMidiReaderTest --tests app.melotrail.architecture.TargetArchitectureRulesTest` PASS (12 tests; byte-stable complete files, conductor/role order, channel policy, marker sanitization, song-boundary notes, aligned role file, and forbidden generated event/boundary rejection).
Full validation: `make test` PASS (2026-08-26; 14 Gradle tasks, 27 seconds); `make build` PASS (2026-08-26; 15 Gradle tasks).
Manual evidence: Not required.
Decisions/deviations: The first `make build` correctly failed because the current transitional Python documentation-inventory gate had no classifications for the MC-004 through MC-007 target files. Added specific inherited-contract/trivial rows with current callable digests; rerun passed. Atomic staging, collision refusal, manifests, and semantic re-import remain exporter work in MC-008/MC-029 rather than writer responsibilities.
Known limitations: The writer is not yet routed through a project export use case. Legacy role-specific writers remain until replacement callers are live and cleanup reaches their owners.
Commit: `midi-core: MC-007 add deterministic MIDI writer`.
Next task: MC-008 after MC-007 validation and commit.

### MC-008 — Prove semantic re-import and a minimal export bundle

Status: DONE
Started: 2026-08-26
Completed: 2026-08-26
Starting commit/status: `6a9e36e` / MC-008 in-progress worktree containing only the task log update and initial target export adapter.
Contracts read: F-EXP-001 through F-EXP-006; Architecture section 4.7; MIDI Contract sections 9 through 14; Quality Gate 3 persistence/export; MC-008 task contract.
Current owners inspected: MC-007 `JdkMidiWriter` and `JdkMidiReader`; legacy `ReleaseExportApplicationService`, `StageRunStore`, and `WorkflowArtifactReference` atomic/digest behavior. The legacy owners remain audio-era code and were not adopted.
Behavior retained/extracted: The target adapter owns a small immutable snapshot identity, staging beside the destination, collision refusal before and immediately before publication, per-file SHA-256, portable relative manifest entries, generated MIDI semantic re-import, semantic role/conductor comparison, and cleanup after interrupted or tampered staging. It uses only the target reader/writer and no audio/export dependency.
Files added/changed: `src/main/kotlin/app/melotrail/export/adapter/MinimalMidiExportBundle.kt`; `src/test/kotlin/app/melotrail/export/adapter/MinimalMidiExportBundleTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.export.adapter.MinimalMidiExportBundleTest --tests app.melotrail.midi.adapter.JdkMidiWriterTest --tests app.melotrail.midi.adapter.JdkMidiReaderTest --tests app.melotrail.architecture.TargetArchitectureRulesTest` PASS (semantic comparison, complete/role re-import, hash/manifest verification, collision refusal, interrupted staging cleanup, and post-validation digest tamper rejection).
Full validation: `make test` PASS (2026-08-26; 14 Gradle tasks, 28 seconds). `make build` initially exposed the mandatory transitional documentation-inventory row for the new target adapter; after the inherited-contract classification was added, `make build` PASS (2026-08-26; 15 Gradle tasks).
Manual evidence: Not required for MC-008. The resulting temporary test bundle has `complete-song.mid`, `melody.mid`, `chords.mid`, `bass.mid`, `drums.mid`, and `manifest.json`; its test asserts all returned SHA-256 digests match the published bytes and all files share PPQ 480 and end tick 480. MC-009 owns DAW import evidence.
Decisions/deviations: The snapshot and manifest deliberately remain minimal because project schema, authority serialization, accepted candidates, and production snapshot records are owned by MC-010 onward. Semantic comparison intentionally ignores source filename/digest and source-event identity while requiring format, PPQ, conductor metadata, track names/order, channel-remapped musical events, markers, and the exact song boundary.
Known limitations: This is a test-only bundle generator rather than a project export use case or full manifest. No DAW compatibility result is claimed; MC-009 must collect the manual matrix. The current Python documentation-inventory build gate is legacy deletion scope for MC-058 and is only updated here because it presently guards all production Kotlin sources.
Commit: `midi-core: MC-008 prove MIDI export round trip`.
Next task: MC-009 — prepare the bundle and await Logic Pro and GarageBand import evidence.

### MC-009 — Complete the early Logic Pro and GarageBand spike

Status: DONE
Started: 2026-08-26
Completed: 2026-08-27
Starting commit/status: `d2b4807` / clean worktree after MC-008; this task records the automated preparation and user-supplied manual decision in its single task commit.
Contracts read: F-EXP-007; DAW Compatibility sections 1 through 6; Quality Gate 6; MC-009 task contract.
Current owners inspected: MC-008 test-only bundle adapter and its semantic re-import coverage; installed-app metadata probe. The host is macOS 26.6.2 and has no inspectable Logic Pro or GarageBand installation, so it cannot supply the mandatory destination evidence.
Behavior retained/extracted: Added narrow test-property forwarding solely to materialize the already-tested MC-008 bundle under `build/mc009-daw-spike`; it does not change production export behavior or create a DAW dependency.
Files added/changed: `build.gradle.kts`; `src/test/kotlin/app/melotrail/export/adapter/MinimalMidiExportBundleTest.kt`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: The generated ignored `build/mc009-daw-spike` evidence bundle is disposable and regenerable from the focused test; it is not a source artifact.
Focused tests: `./gradlew :test --tests app.melotrail.export.adapter.MinimalMidiExportBundleTest -Dmelotrail.dawSpikeDirectory=build/mc009-daw-spike` PASS; bundle semantic re-import validates conductor, track order/names, channels, markers, PPQ 480, and end tick 480 before publication.
Full validation: final `./gradlew :test --tests app.melotrail.export.adapter.MinimalMidiExportBundleTest` PASS (2026-08-27); `make test` PASS (2026-08-27; 14 Gradle tasks); `make build` PASS (2026-08-27; 15 Gradle tasks).
Manual evidence: GarageBand PASS except unassessed marker display, recorded 2026-08-27: user imported `complete-song.mid` in GarageBand 10.4.14 on macOS Tahoe 26.6.2 and observed four tracks correctly named Melody, Chords, Bass, and Drums; each has the intentional one note over a half-bar; tempo is 120 BPM and meter is 4/4. User played the complete file through its end without stuck or truncated notes, imported the role files with their notes aligned at song origin, and confirmed the Drums note sounds correct after drum assignment. Logic Pro 12.3.1 then passed the same import test on the same user environment: role separation/names, 120 BPM, 4/4, song-origin alignment, drum interpretation, and safe playback. Marker display was not assessed; it is best-effort metadata and does not alter the confirmed timing result. Materialized files/SHA-256: `complete-song.mid` `75ccd9c7ba4c34e40d0c04f11fcd56efda2406e284642e099bb77021de135a04`; `melody.mid` `ac9d2bca1eb24dd898be72a5cf4043435642d8f9f83a62eacf00f3e3989d3d21`; `chords.mid` `aa8b57c623c22b2973a717b14ff64efe45991d56a415260e801ff544f7123ad3`; `bass.mid` `fc10b58c7290972c78a8abcf1f47466c8c2329c8ccb3773853a3e92dd5c2abe7`; `drums.mid` `2ab7c7b786f235c6b04a1208c9a94cbf9186c59c13bbe84e703c95ab355bff9e`; `manifest.json` `bca743a48c1ec02ad8ffd52b2aa7ff951f285ed42cac1a0e37eca804a9a4b766`.
Decisions/deviations: The bundle is intentionally a deterministic test fixture for the early compatibility spike, not an accepted project export snapshot. The host could not inspect installed DAWs directly; GarageBand and Logic Pro results, versions, and macOS version are user-supplied manual evidence rather than inferred host state.
Known limitations: The early fixture is intentionally a one-half-bar, one-note-per-role compatibility spike rather than a complete user arrangement. Marker display is unassessed and remains best-effort metadata; all timing/event-safety checks pass.
Commit: `midi-core: MC-009 record DAW compatibility spike`.
Next task: MC-010 — define the MIDI-only project schema.

### MC-010 — Define the MIDI-only project schema

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `daaeb20` / resumed the existing MC-010 in-progress worktree containing only the task log, two target schema sources, their focused test/golden fixture, and the required transitional documentation-inventory rows.
Contracts read: F-PROJ-001 through F-PROJ-003; Architecture sections 4.1 and 6; MC-010 task contract.
Current owners inspected: Legacy `ProjectStore`, its private schema-v4 DTO/envelope mapping and atomic writer, `ProjectV4SchemaTest`, and the resumed target schema implementation/tests. The legacy store remains compiled only for its current callers and is not imported by the target package.
Behavior retained/extracted: Retained strict unknown-field decoding, explicit schema/version discrimination, and validation before domain admission. `MidiCoreProjectSchema` owns a private DTO boundary distinct from target domain records and classifies legacy/unknown documents without migration or writes. `ProjectRelativePath` provides portable lexical confinement; actual root/symlink confinement remains MC-011 ownership.
Files added/changed: `src/main/kotlin/app/melotrail/project/MidiCoreProject.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProjectSchema.kt`; `src/test/kotlin/app/melotrail/project/MidiCoreProjectSchemaTest.kt`; `src/test/resources/fixtures/project/midi-core-v1.json`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.project.MidiCoreProjectSchemaTest --tests app.melotrail.architecture.TargetArchitectureRulesTest --rerun-tasks` PASS (five schema tests plus target boundary rules). Tests cover complete encode/decode, byte-stable golden serialization, imported-source state before melody selection, required fields, strict unknown fields, malformed discriminators, legacy/future/unknown versions, traversal/absolute/drive paths, and aggregate ownership invariants.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `make build` first exposed the reviewed callable-count/digest change, then PASS after refreshing the MC-010 inventory row (15 Gradle tasks).
Manual evidence: Not required.
Decisions/deviations: Added explicit section-definition records so occurrences cannot reference an undeclared section while MC-016 remains the owner of timeline mutations. Corrected the resumed aggregate invariant so a successfully imported source may await explicit melody selection; candidates, acceptances, and exports still require a selected melody. Field ownership is: document discriminator -> schema boundary; metadata/source/selection/authority -> project aggregate; paths/digests -> artifact records; candidates/acceptances/locks -> review state; snapshots/files -> export history. The golden fixture SHA-256 is `d2fecce83fcab99a29ba3aef3fe3a60511dfcc22d316717e8e2dfac7c1147ea5`.
Known limitations: Filesystem publication, digest verification, symlink confinement, and crash recovery are intentionally absent until MC-011. Project lifecycle use cases and UI-ready legacy rejection are MC-012. Later task-owned authority and candidate lifecycle fields will extend the still-unshipped v1 DTO before product cutover.
Commit: `midi-core: MC-010 define MIDI project schema`.
Next task: MC-011 — implement the target artifact store.

### MC-011 — Implement the target artifact store

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `729772e` / clean after the MC-010 commit, then this task's log-only `IN_PROGRESS` update. A compiler-generated `.kotlin/sessions/kotlin-compiler-10759057547151889139.salive` index/worktree marker appeared during Gradle execution; it is unrelated and explicitly excluded from this task commit.
Contracts read: F-PROJ-004; F-SYS-004; Architecture section 6; MC-011 task contract.
Current owners inspected: Legacy `ProjectStore` atomic replacement/recovery behavior; `StageRunStore` streaming SHA-256, real-path/symlink validation, immutable publication and failed-index orphan evidence; `WorkflowArtifactReference`; `StageRunStoreTest`; and `LegacyMidiArtifactCharacterizationTest`. No schema-v4/stage-run type is imported into the target store.
Behavior retained/extracted: Retained streaming SHA-256, real-path confinement, no-overwrite immutable publication, strict validation before state admission, temporary-file publication, and inspectable recovery evidence. The new project adapter owns the target source/candidate/report/export layout and project.json persistence; legacy stage indexes, workflow enums, and artifact records remain legacy-only.
Files added/changed: `src/main/kotlin/app/melotrail/project/adapter/MidiCoreArtifactStore.kt`; `src/test/kotlin/app/melotrail/project/adapter/MidiCoreArtifactStoreTest.kt`; `src/test/kotlin/app/melotrail/architecture/TargetArchitectureRulesTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.project.adapter.MidiCoreArtifactStoreTest --tests app.melotrail.architecture.TargetArchitectureRulesTest --rerun-tasks` PASS (artifact target tree, canonical paths, source/candidate/report/export publication, traversal and symlink escape rejection, missing/tampered digest rejection, exact-content republish, differing-content collision, interrupted project save recovery, missing-reference save rejection, and reopen digest verification).
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `make build` initially identified the required MC-011 source-inventory row, then PASS after its reviewed classification was recorded (15 Gradle tasks).
Manual evidence: Not required.
Decisions/deviations: `project/adapter` is the explicit filesystem boundary; the architecture rule continues to prohibit filesystem imports in `project` domain records and additionally prohibits Compose, network, HTTP, and raw-MIDI imports in the adapter. Immutable republishing is idempotent only for byte-identical content; differing bytes at an occupied canonical path fail without replacing the first artifact. An interrupted `project.json` replacement preserves the old document and moves the temporary bytes to a uniquely named, project-local recovery file.
Known limitations: Project lifecycle use cases, UI-ready error classification, source inspection/report binding, and post-publication cleanup belong to MC-012 and MC-013. Candidate lifecycle transitions and export snapshot policy remain MC-019/MC-029 responsibilities.
Commit: `midi-core: MC-011 add MIDI artifact store`.
Next task: MC-012 — implement create, open, save, and legacy rejection.

### MC-012 — Implement create, open, save, and legacy rejection

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `dd92dbd` / MC-012 log-only `IN_PROGRESS` update; the unrelated compiler session marker recorded under MC-011 remains excluded from this task commit.
Contracts read: F-PROJ-001 through F-PROJ-004; MC-012 task contract.
Current owners inspected: The schema-v4 `ProjectApplicationService` create/open/mutation paths and its focused test, legacy `ProjectStore`, and the current Compose create/open dialog/view-model behavior. Those owners create audio-era paths, render settings, stage recovery, and worker-facing state, so they are not target callers and remain for later cutover/removal.
Behavior retained/extracted: Retained only local root validation, explicit existing-project refusal, and UI-oriented failure handling. `MidiCoreProjectLifecycle` coordinates `MidiCoreArtifactStore` and target domain records, returns stable problem codes/messages/next actions, and never imports the legacy project service/store, stage runner, worker, renderer, or desktop runtime.
Files added/changed: `src/main/kotlin/app/melotrail/application/MidiCoreProjectLifecycle.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreProjectLifecycleTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreProjectLifecycleTest --tests app.melotrail.project.adapter.MidiCoreArtifactStoreTest --tests app.melotrail.project.MidiCoreProjectSchemaTest --rerun-tasks` PASS. Tests prove create/reopen/close identity, invalid-request pre-mutation rejection, corrupted/missing project problems, byte-preserving schema-v4 rejection, missing-artifact rejection, and failed-save recovery to the original readable project.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `make build` PASS (2026-08-27; 15 Gradle tasks including the transitional documentation-inventory check).
Manual evidence: Not required.
Decisions/deviations: Target open maps every non-current schema to `UNSUPPORTED_PROJECT` without a parse-and-migrate branch or any write. Artifact integrity is verified before a project session is returned. Close is deliberately a pure session boundary because this task opens no device/process resources. The existing desktop has not yet been redirected: target desktop composition and Project page cutover are MC-031 and MC-034.
Known limitations: The target project has no MIDI source until MC-013; target UI wiring, recent-project persistence, and dialogs remain later desktop work. Candidate and export lifecycle persistence will be completed by MC-019.
Commit: `midi-core: MC-012 add project lifecycle`.
Next task: MC-013 — implement immutable MIDI source import.

## 6. Manual gate records

### MC-009 — Early DAW compatibility

- Melotrail build/commit: MC-008 `d2b4807`; MC-009 `midi-core: MC-009 record DAW compatibility spike`.
- Fixture/export snapshot and hashes: `build/mc009-daw-spike`; complete file SHA-256 `75ccd9c7ba4c34e40d0c04f11fcd56efda2406e284642e099bb77021de135a04`; per-role and manifest hashes are recorded under MC-009.
- macOS version: Tahoe 26.6.2 (user-supplied).
- Logic Pro version/result/evidence: Logic Pro 12.3.1. PASS, user report 2026-08-27: the user ran the same complete and role-file import checks as GarageBand; role separation/names, 120 BPM, 4/4, song-origin alignment, drum interpretation, and playback passed. Marker display unassessed.
- GarageBand version/result/evidence: GarageBand 10.4.14. PASS except unassessed marker display, user report 2026-08-27: importing `complete-song.mid` produced Melody, Chords, Bass, and Drums tracks; each shows one intentional half-bar note; tempo is 120 BPM and meter is 4/4. The complete file played through its end without stuck/truncated notes; all role-file notes align at song origin; the drum note sounds correct after drum assignment.
- Required user actions: None for MC-009. Marker display is unassessed and recorded as best-effort metadata.
- Reviewer/date: User / 2026-08-27.
- Decision: PASS — G1 is complete; continue to MC-010.

### MC-048 — Final DAW compatibility

- Melotrail build/commit:
- Fixture/export snapshots and hashes:
- macOS version:
- Logic Pro complete/role results:
- GarageBand complete/role results:
- Tempo/meter/track/channel/marker/boundary/playback results:
- Conditional user actions:
- Reviewer/date:
- Decision:

### MC-049 — Holdout musical acceptance

- Holdout set ownership/source statement:
- Project count and hashes:
- Snapshot IDs:
- Melody-preservation results:
- Per-role scores:
- Overall scores/median:
- Review-time median:
- Failed cases and targeted fixes:
- Reviewer(s)/date:
- Decision:

### MC-060 — Final sign-off

- Final commit:
- Clean test/check/build:
- Desktop smoke:
- DAW evidence status:
- Holdout status:
- Cleanup status:
- Known limitations:
- User decision/date:

## 7. Destructive cleanup ledger

| Task | Exact resolved target | Tracked/ignored | Size/files | Consumer scan | Recoverability | Result |
| --- | --- | --- | --- | --- | --- | --- |
| MC-050 | | | | | | |
| MC-051 | | | | | | |
| MC-052 | | | | | | |
| MC-053 | | | | | | |
| MC-054 | | | | | | |
| MC-055 | | | | | | |
| MC-056 | | | | | | |
| MC-057 | | | | | | |
| MC-058 | | | | | | |
| MC-059 | | | | | | |

## 8. Final reduction report

- Final production Kotlin files/lines:
- Final test Kotlin files/lines:
- Final Python files/lines (must be zero):
- Final obsolete audio-project bytes (must be zero):
- Final local sound-library bytes (must be zero):
- Removed Gradle dependencies:
- Removed Make targets:
- Removed packages/features:
- Remaining target packages:
- Documentation-link audit:
- Dead-code/legacy scan:

## 9. Known limitations and optional future work

Record only accepted limitations. Pad, Qwen, melody connection, variable tempo/
meter, multiple source files, direct DAW automation, advanced MIDI editing, and
enhanced preview remain optional until separately approved.

## 10. Final summary

- Completed task range:
- Final commit:
- Automated gate result:
- Manual gate result:
- Cleanup result:
- Product sign-off:
