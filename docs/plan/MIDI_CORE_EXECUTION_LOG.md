# MIDI Core execution log

Status: MC-030 complete

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
| MC-013 | DONE | `midi-core: MC-013 import immutable MIDI source` | PASS — source-import/store/schema/lifecycle/architecture tests, `make test`, `make build` | One-file SMF import validates before publication, preserves source/report bytes under digest, persists inspection summaries, and leaves the prior project document unchanged on every tested failure. |
| MC-014 | DONE | `midi-core: MC-014 protect selected melody` | PASS — melody-selection/validator/architecture tests, `make test`, `make build` | Exactly one source track/channel yields a digest-bound protected view with controller/expression policy, deterministic anchors, source-event lineage, and no source mutation. |
| MC-015 | DONE | `midi-core: MC-015 add core musical authority` | PASS — authority/schema/architecture tests, `make test`, `make build` | Typed fixed tempo/meter and spelling-aware key/mode authority are explicitly confirmed, persisted, and reopened without source mutation. |
| MC-016 | DONE | `midi-core: MC-016 add exact occurrence timeline` | PASS — focused timeline/schema/application tests, `make test` | Explicit tick/beat occurrence timeline, pickup policy, deterministic markers, ordered mutations, source-range coverage, and safe persistence are implemented without duration inference. |
| MC-017 | DONE | `midi-core: MC-017 add authoritative harmony` | PASS — focused harmony/application tests, `make test`, `make build` | Exact chord-window coverage, bounded parsing/realization, chromatic advisories, stale-safe application binding, reopen, and atomic save-failure behavior are covered. |
| MC-018 | DONE | `midi-core: MC-018 add scoped invalidation` | PASS — fingerprint/invalidation/application tests, `make test`, `make build` | Canonical component and role/occurrence hashes, generator/dependency inputs, scoped impact preview, immutable artifact retention, and stale async rejection are covered. |
| MC-019 | DONE | `midi-core: MC-019 add candidate lifecycle records` | PASS — candidate lifecycle/schema/artifact/application tests, `make test`, `make build` | Immutable candidate publication, accepted/rejected/locked/restored transitions, prior acceptance history, scoped stale status, and export provenance snapshots are persisted and digest-bound. |
| MC-020 | DONE | `midi-core: MC-020 define generation context` | PASS — context/catalog tests, documentation coverage, `git diff --check`, `make test` | One immutable occurrence-scoped request and target-only pattern/profile inventory cover all three core roles without project files or analysis sidecars. |
| MC-021 | DONE | `midi-core: MC-021 validate core roles` | PASS — role-validation tests, documentation coverage, `git diff --check`, `make test` | Deterministic typed validation reports cover common, Chords, Bass, and Drums policies before publication. |
| MC-022 | DONE | `midi-core: MC-022 generate chord candidates` | PASS — chord-generator tests, documentation coverage, `git diff --check`, `make test` | Deterministic Chords candidates cover complete chord tones, curated rhythm alternatives, bounded inversions, voice leading, and scoped validation. |
| MC-023 | DONE | `midi-core: MC-023 generate bass candidates` | PASS — focused Bass suite, documentation coverage, `git diff --check`, `make test` | Deterministic Bass candidates cover exact harmony windows, all curated patterns, both MIDI performance profiles, phrase/melody activity, accepted Chords rhythm, low-end spacing, and typed fallback/rejection. |
| MC-024 | DONE | `midi-core: MC-024 generate drum candidates` | PASS — focused Drum suite, documentation coverage, `git diff --check`, `make test` | Deterministic Drum candidates cover complete groove/fill variants, density selection, phrase boundaries, accepted Bass kick intent, GM mapping, energy/purpose velocities, and typed validation. |
| MC-025 | DONE | `midi-core: MC-025 publish generated candidates` | PASS — focused generation suite, documentation coverage, `git diff --check`, `make test` | One-role/one-occurrence generation publishes immutable MIDI/report evidence only after validation and current-authority admission; cancellation, stale completion, save failure, concurrency, immutable collision, and collision retry are covered. |
| MC-026 | DONE | `midi-core: MC-026 review arrangement candidates` | PASS — focused schema/lifecycle/review suite, documentation coverage, `git diff --check`, `make test` | User-owned candidate listing and state transitions now use optimistic project revisions, authority/digest revalidation, immutable evidence, acceptance history, locking, restoration, and targeted regeneration. |
| MC-027 | DONE | `midi-core: MC-027 assemble accepted song` | PASS — focused diff/assembly/review suite, documentation coverage, `git diff --check`, `make test` | Deterministic event-level diff summaries and accepted-song assembly preserve the protected melody, aggregate exact occurrence-scoped role candidates, and reject incomplete, stale, tampered, malformed, mis-channelled, or overflowing evidence before review. |
| MC-028 | DONE | `midi-core: MC-028 add MIDI audition` | PASS — focused audition/writer/JVM-boundary suite, documentation coverage, `git diff --check`, `make test` | MIDI-only scope/session control supports source melody, candidate, occurrence, role, and accepted arrangement views with bounded seek/loop, mute/solo, supersession, recoverable device errors, and cleanup without project writes. |
| MC-029 | DONE | `midi-core: MC-029 export MIDI package` | PASS — focused exporter/MIDI/project suite, documentation coverage, `git diff --check`, `make test` | Complete and optional-role MIDI packages are semantically re-imported, atomically published, portable-manifested, collision-safe, and bound to reopened project snapshots. |
| MC-030 | DONE | `midi-core: MC-030 prove vertical slice` | PASS — JVM vertical slice, `make test`, `make build`, documentation coverage, `git diff --check` | Target project creation through semantic MIDI package re-import passes without legacy services; failure/recovery and immutable evidence are covered. |
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
| G2 MIDI project kernel complete | MC-010–MC-019 | DONE | MC-010–MC-019 target schema, artifact, authority, invalidation, lifecycle, and full test/build gates pass. |
| G3 Vertical slice complete | MC-020–MC-030 | DONE | MC-020–MC-030 target context, validation, generation, review, assembly, audition, export, and the JVM vertical-slice gate pass. |
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

### MC-013 — Import immutable MIDI source

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `b9f232f` / MC-013 log-only `IN_PROGRESS` update; the unrelated compiler session marker recorded under MC-011 remains excluded from this task commit.
Contracts read: F-MIDI-001, F-MIDI-002, F-MIDI-004, F-MIDI-005; MIDI Contract import/source-identity rules; MC-013 task contract.
Current owners inspected: Target `JdkMidiReader` and `MidiImportValidator`; target project lifecycle/schema/artifact store; and the legacy project import service. The legacy service owns audio/stage-era concerns and was not reused.
Behavior retained/extracted: `MidiCoreSourceImport` invokes only the target reader/validator/store. It accepts `.mid`/`.midi` SMF 0/1 PPQ sources, records typed validation, detects source mutation by comparing the inspected identity to the copied immutable artifact, publishes a digest-bound import report, and atomically binds source identity, track summaries, and end tick to `project.json`. The artifact adapter now verifies the report with every bound source and permits cleanup only for canonical import artifacts that are demonstrably unbound.
Files added/changed: `src/main/kotlin/app/melotrail/application/MidiCoreSourceImport.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProject.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProjectSchema.kt`; `src/main/kotlin/app/melotrail/project/adapter/MidiCoreArtifactStore.kt`; schema/lifecycle/store/source-import tests; the v1 schema golden fixture; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreSourceImportTest --tests app.melotrail.project.MidiCoreProjectSchemaTest --tests app.melotrail.project.adapter.MidiCoreArtifactStoreTest --tests app.melotrail.application.MidiCoreProjectLifecycleTest --rerun-tasks` PASS; `./gradlew :test --tests app.melotrail.architecture.TargetArchitectureRulesTest --rerun-tasks` PASS. The source-import coverage includes SMF 0/1 import, canonical original/report publication, SHA-256 checks for both artifacts, persisted ordered summaries, renamed non-MIDI refusal, source mutation during copy, duplicate-source refusal, and simulated final-save failure cleanup.
Full validation: `make test` PASS (2026-08-27; root and desktop Gradle test tasks); `make build` PASS (2026-08-27; 15 Gradle tasks including the documentation-inventory check).
Manual evidence: Not required.
Artifact and project-hash evidence: The supported-fixture test compares copied source bytes to the selected source and verifies `source/original.mid` plus `reports/import.json` against their persisted SHA-256 values. Failure tests capture the original `project.json` bytes and SHA-256 before renamed-input, source-mutation, duplicate, and simulated-save failures, then require the identical bytes and digest afterward; neither canonical import artifact remains after unbound failures.
Decisions/deviations: Import is allowed to return `AWAITING_AUTHORITY` for missing explicit melody selection, tempo, or meter because MC-013 only admits a safe immutable source; MC-014 through MC-016 own user authority capture. A stale project session, unsupported extension, pre-existing unbound canonical artifacts, unreadable/unsupported MIDI, blocking findings, source mutation, and save failure are exposed as stable UI-ready problem codes rather than throwing into desktop callers. `SourceMidiRecord` gained the report artifact and inspection facts before UI cutover, so all later target consumers read one durable source record.
Known limitations: The current target import use case is not yet wired into Compose dialogs or recent-project state; MC-031/MC-034 own that cutover. Melody selection, project timing/key/meter, and structural authority continue in MC-014 through MC-016.
Commit: `midi-core: MC-013 import immutable MIDI source`.
Next task: MC-014 — add melody selection and immutable anchor extraction.

### MC-014 — Implement protected melody selection and view

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `c617cfd` / MC-014 log-only `IN_PROGRESS` update; the unrelated compiler session marker recorded under MC-011 remains excluded from this task commit.
Contracts read: F-MIDI-003 and F-MIDI-005; MIDI Contract sections 3, 5, 6.1, and 10; Musical invariants; MC-014 task contract.
Current owners inspected: Legacy `MelodyIdentityBuilder`, mutation-anchor invariants, and full-song melody sidecar. They provide only the proven source-event identity and phrase/held-note anchor assertions; their raw-JDK parsing, monophonic cleanup, phrase evidence, and mutation stages are not used by target code.
Behavior retained/extracted: `MidiProtectedMelodySelector` works from the existing immutable semantic sequence. It selects exactly one track/channel, derives a channel-1-ready (zero-indexed channel 0) view with original source-event ordering, maps notes/CC/pitch bend/channel pressure consistently, omits unsupported messages under an explicit policy, derives stable phrase/held-note anchors, and hashes the entire selected view plus anchor policy. The application re-inspects and verifies the preserved source before binding `SelectedMelodyTrack`; it returns stable problems for stale projects, unknown track/channel, no notes, unsafe pairing, and MPE-like multi-channel pitch-bend/channel-pressure input.
Files added/changed: `src/main/kotlin/app/melotrail/midi/domain/MidiProtectedMelody.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreMelodySelection.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreMelodySelectionTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; `docs/plan/MIDI_CORE_EXECUTION_LOG.md`.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreMelodySelectionTest --tests app.melotrail.midi.domain.MidiImportValidatorTest --tests app.melotrail.architecture.TargetArchitectureRulesTest --rerun-tasks` PASS. Tests cover SMF 1 and format-0 resolution, exact one-track/channel binding, source-controller/pitch-bend mapping, output channel 1 projection, protected-anchor and identity persistence, source-byte plus semantic-event diff preservation, selection change before derived work, and MPE-like refusal without project mutation.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `make build` PASS (2026-08-27; 15 Gradle tasks including the documentation-inventory check).
Manual evidence: Not required.
Melody identity evidence: Each selected note ID hashes the source SHA-256, source track/channel, source-event ordinal, timing, pitch, velocity, and release velocity. The selected-view SHA-256 includes source identity, PPQ, policy, all preserved projected events, and sorted protected-anchor IDs. The selection test proves a different source track/channel produces a different durable identity digest; its before/after reader event signatures and source bytes are identical.
Decisions/deviations: MPE-like input is defined conservatively as a selected note channel in a track with pitch-bend or channel-pressure evidence across multiple note-bearing channels; V1 requires a single-channel protected melody. Selection changes are allowed before candidates/acceptances/snapshots exist. Once immutable derived work exists, the application refuses the selection change rather than silently deleting or retargeting immutable evidence; the explicit invalidation lifecycle is completed by MC-019.
Known limitations: The selected view is re-derived from immutable source and durable selection digest rather than persisted as a second MIDI artifact. Compose UI selection wiring is MC-034; authority fields and candidate lifecycle are MC-015 through MC-019.
Commit: `midi-core: MC-014 protect selected melody`.
Next task: MC-015 — implement fixed tempo, meter, key, and mode authority.

### MC-015 — Implement fixed tempo, meter, key, and mode authority

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `1c4531d` / clean after the MC-014 task commit, then this task's log-only `IN_PROGRESS` update; the pre-existing deleted compiler session marker remains excluded from this task commit.
Contracts read: F-AUTH-001 and F-AUTH-002; MIDI Contract sections 2, 7, and 8; Architecture sections 4.1 and 4.3; MC-015 task contract.
Current owners inspected: Target project schema/aggregate, source import, melody selection, MIDI validator, and semantic tempo/meter events; legacy `CompositionSettingsApplicationService`, `MusicalPrimitives`, and their tests. The legacy owner combines profiles, moods, render-era workflow invalidation, template transposition, and catalog constraints, so it is not a target authority caller.
Behavior retained/extracted: Retained only fixed Standard MIDI tempo/meter representation, enharmonic tonic spelling, major/natural-minor advisory scale membership, and source timing facts. `MidiCoreMusicalAuthority` re-inspects immutable source bytes, supplies fixed source tempo/meter as suggestions, requires an explicit musician authority decision, preserves chromatic melody as advisory, and saves no audio/render/profile/model setting.
Files added/changed: `src/main/kotlin/app/melotrail/music/core/MidiCoreMusicalAuthority.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreMusicalAuthority.kt`; target project aggregate/schema; target project JSON golden fixture; `MidiCoreMusicalAuthorityTest`; documentation inventory; execution log.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable.
Ignored deletion recoverability: Not applicable.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreMusicalAuthorityTest --tests app.melotrail.project.MidiCoreProjectSchemaTest --tests app.melotrail.architecture.TargetArchitectureRulesTest --rerun-tasks` PASS. The tests cover typed tempo/meter bounds, enharmonic key spelling, unsupported mode/spelling rejection, source timing suggestions, explicit missing-metadata confirmation, map rejection before import, chromatic advisory preservation, source immutability, persistence, and reopen.
Full validation: `make test` PASS (2026-08-27; root and desktop suites); `make build` PASS (2026-08-27; 15 Gradle tasks, including documentation inventory verification).
Manual evidence: Not required.
Authority evidence: `ProjectAuthority` now stores `ProjectTempo`, `ProjectMeter`, and a spelling-aware `ProjectKey`; the v1 JSON golden document records its tonic spelling. Empty section/occurrence/chord lists remain valid until MC-016 and MC-017 own their authoring rules. Source metadata is never promoted automatically: missing source metadata is cleared from the post-confirmation blocking state only by the explicit fixed values in the request.
Decisions/deviations: Major and natural-minor are the only executable V1 advisory modes, identified as `major` and `natural-minor`; a project key preserves one valid enharmonic spelling. Source tempo/meter values remain suggestions and can be explicitly confirmed rather than inferred. Changing authority with immutable derived records is conservatively refused until MC-018 provides dependency-aware invalidation.
Known limitations: Exact occurrence timing, pickup policy, duration-aware chord windows, and scoped invalidation remain MC-016 through MC-018. Desktop authority editing is MC-036.
Commit: `midi-core: MC-015 add core musical authority`.
Next task: MC-016 — implement exact section occurrence timelines.

### MC-016 — Implement exact section occurrence timelines

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `c0b8c4a` / worktree contained the user's in-progress MC-016 timeline files and execution-log status update plus an unrelated deleted Kotlin compiler session marker; the unrelated deletion was preserved and excluded from the task commit.
Contracts read: F-AUTH-003; Architecture sections 4.3 and 7; MIDI Contract sections 4, 8, and 11; MC-016 task contract.
Current owners inspected: Target `ProjectAuthority`/schema and the in-progress occurrence timeline; legacy `SongTimeline`, structure occurrence mutations, MIDI time mapping, and canonical-authority occurrence construction. Legacy occurrences infer timing from audio-era part analysis and remain outside the target path.
Behavior retained/extracted: `MidiCoreOccurrenceTimeline` owns one PPQ/meter-aware, tick-exact contiguous timeline with stable occurrence and definition identities, explicit durations, optional explicit start assertions, bounded pickup metadata, exact beat-position views, and deterministic sanitized marker derivation. `MidiCoreStructureEditor` provides ordered replace/insert/duplicate/move/remove mutations without inferring duration or discarding authoritative harmony. `MidiCoreStructureTimeline` validates the intended source range, checks the project revision, previews derived-work invalidation, and atomically persists the new authority.
Files added/changed: `src/main/kotlin/app/melotrail/structure/MidiCoreOccurrenceTimeline.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreStructureTimeline.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProject.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProjectSchema.kt`; `src/test/kotlin/app/melotrail/structure/MidiCoreOccurrenceTimelineTest.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreStructureTimelineTest.kt`; `src/test/resources/fixtures/project/midi-core-v1.json`; and this execution log.
Files/data deleted: None. The pre-existing `.kotlin/sessions/kotlin-compiler-10759057547151889139.salive` deletion remains a preserved unrelated user change.
Tracked deletion recoverability: Not applicable to MC-016; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.structure.MidiCoreOccurrenceTimelineTest --tests app.melotrail.application.MidiCoreStructureTimelineTest --tests app.melotrail.project.MidiCoreProjectSchemaTest --rerun-tasks` PASS (12 tests). Coverage includes repeated sections, explicit starts, exact tick/beat positions, pickup bounds and persistence, marker sanitization, insert/duplicate/move/remove, source-range coverage, harmony preservation, stale-safe save, and atomic save failure recovery.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks). `git diff --check` PASS.
Manual evidence: Not required.
Decisions/deviations: Pickup length is explicit project metadata and must be shorter than one representable meter bar; it remains zero on an empty authority draft and is preserved through every editor mutation. The application defaults intended song coverage to the preserved source end tick while allowing an explicit arrangement range for deliberate extension. Existing authoritative chord events are carried forward and invalid structure/harmony relationships fail rather than silently deleting them. Marker rendering delegates to the established `MidiExportMarker` sanitization policy.
Known limitations: Duration-aware chord editing and scoped candidate invalidation remain MC-017 and MC-018. The timeline application is not yet wired to Compose Desktop; MC-036 owns the target structure page. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-016 add exact occurrence timeline`.
Next task: MC-017 — implement duration-aware authoritative harmony.

### MC-017 — Implement duration-aware authoritative harmony

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `c92b99f` / clean after the MC-016 task commit; the unrelated deleted Kotlin compiler session marker remains outside the task commit.
Contracts read: F-AUTH-004; MIDI Contract section 8; Architecture sections 4.4 and 7; MC-017 task contract.
Current owners inspected: Target project authority and exact occurrence timeline; legacy harmony parser/formatter/application and harmonic timeline owners. The legacy path remains outside the target architecture and is not used as a fallback.
Behavior retained/extracted: `MidiCoreChordSymbol` provides spelling-preserving roots, bounded major/minor/seventh/ninth/suspended/sixth/add-ninth qualities, optional slash bass, and deterministic pitch-class realization. `MidiCoreHarmonyValidator` requires explicit, positive-duration windows that cover every saved occurrence without gaps or overlaps, reports syntax/occurrence/order blockers, and emits key compatibility only as an advisory. `MidiCoreAuthoritativeHarmony` checks the session revision, validates the complete request before mutation, preserves approved chromatic symbols without transposition, refuses changes that would silently orphan immutable derived work, and saves through the atomic project adapter.
Files added/changed: `src/main/kotlin/app/melotrail/music/core/MidiCoreHarmony.kt`; `src/main/kotlin/app/melotrail/structure/MidiCoreHarmonyTimeline.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreAuthoritativeHarmony.kt`; `src/test/kotlin/app/melotrail/structure/MidiCoreHarmonyTimelineTest.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreAuthoritativeHarmonyTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None. The pre-existing `.kotlin/sessions/kotlin-compiler-10759057547151889139.salive` deletion remains a preserved unrelated user change.
Tracked deletion recoverability: Not applicable to MC-017; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.structure.MidiCoreHarmonyTimelineTest --tests app.melotrail.application.MidiCoreAuthoritativeHarmonyTest --tests app.melotrail.application.MidiCoreStructureTimelineTest --tests app.melotrail.project.MidiCoreProjectSchemaTest --rerun-tasks` PASS (16 tests). Coverage includes `Dbmaj9/F`, extensions, chromatic advisory preservation, repeated occurrences, exact sub-bar boundaries, gaps, overlaps, out-of-bound request events, invalid symbols, deterministic event order, transposition independence, reopen, stale-safe application binding, blocking rejection without project-byte changes, and atomic save-failure preservation.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `make build` PASS (2026-08-27; 15 Gradle tasks including the documentation-inventory check); `git diff --check` PASS.
Manual evidence: Not required.
Harmonic timeline golden output: `verse-1` resolves `[0,960) C` then `[960,1920) Db`; repeated `verse-2` resolves `[1920,2880) G7` then `[2880,3840) C`. At exact boundary ticks 960 and 1920, lookup selects the next approved window. `Dbmaj9/F` remains the stored symbol and produces pitch classes `{0,1,3,5,8}`; its out-of-key status is advisory only.
Decisions/deviations: The parser deliberately exposes a bounded V1 vocabulary rather than accepting arbitrary formatter syntax; all accepted roots and slash basses retain their requested enharmonic spelling. The persisted `ProjectAuthority` rejects impossible out-of-occurrence events, while the application validator accepts request input long enough to return typed out-of-bound findings before any write. Existing legacy harmony/template fallback code is not modified during this target task; its deletion belongs to the scheduled cleanup after target callers are cut over.
Known limitations: Dependency-aware scoped invalidation and authority fingerprints remain MC-018; candidate status and acceptance lifecycle remain MC-019. The harmony application is not yet wired to Compose Desktop; MC-036 owns the target structure/harmony page. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-017 add authoritative harmony`.
Next task: MC-018 — implement authority hashes and dependency-aware invalidation.

### MC-018 — Implement authority hashes and dependency-aware invalidation

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `3ef533a` / clean after the MC-017 task commit; the unrelated deleted Kotlin compiler session marker remains outside the task commit.
Contracts read: F-AUTH-005 and F-REV-005; Architecture sections 7 and 8; Quality Gate 3; MC-018 task contract.
Current owners inspected: Target project authority, candidate/export records, and all target authority mutation use cases; legacy stale workflow artifacts, context hashes, project mutation coordination, and arrangement fingerprints. Legacy state remains outside the target path.
Behavior retained/extracted: `MidiCoreAuthorityHasher` uses length-delimited canonical serialization for source, selected melody, timing, structure, harmony, and settings components, then derives one exact hash for every role/occurrence scope. Source and melody identity, fixed tempo/meter/pickup, occurrence boundaries, key spelling/chords, and explicit settings are never inferred or normalized. `MidiCoreGenerationFingerprint` adds generator ID/version/pattern/seed and accepted role/occurrence/candidate hash inputs. `MidiCoreInvalidationPlanner` compares component and scoped hashes, reports affected candidates and whole-song exports before mutation, propagates accepted-candidate impact to dependents, and never deletes an artifact. `MidiCoreGenerationAdmission` rejects any completion whose scoped authority, generator, or dependency fingerprint differs. Melody, musical-authority, structure, and harmony application mutations now return this preview and preserve existing authority/derived files while allowing later candidate lifecycle code to expose stale status.
Files added/changed: `src/main/kotlin/app/melotrail/project/MidiCoreAuthorityFingerprint.kt`; `src/main/kotlin/app/melotrail/arrangement/core/MidiCoreInvalidation.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreMusicalAuthority.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreMelodySelection.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreStructureTimeline.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreAuthoritativeHarmony.kt`; `src/test/kotlin/app/melotrail/project/MidiCoreAuthorityFingerprintTest.kt`; `src/test/kotlin/app/melotrail/arrangement/core/MidiCoreInvalidationTest.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreAuthoritativeHarmonyTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None. The pre-existing `.kotlin/sessions/kotlin-compiler-10759057547151889139.salive` deletion remains a preserved unrelated user change.
Tracked deletion recoverability: Not applicable to MC-018; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.project.MidiCoreAuthorityFingerprintTest --tests app.melotrail.arrangement.core.MidiCoreInvalidationTest --tests app.melotrail.application.MidiCoreMusicalAuthorityTest --tests app.melotrail.application.MidiCoreMelodySelectionTest --tests app.melotrail.application.MidiCoreStructureTimelineTest --tests app.melotrail.application.MidiCoreAuthoritativeHarmonyTest --rerun-tasks` PASS. Coverage includes every authority dimension, stable canonical serialization, chorus-only scope changes, role-prefixed settings, accepted-dependency propagation, generator/seed changes, unrelated-scope async admission, stale completion rejection, pre-mutation impact reporting, authority re-confirmation without structure loss, candidate artifact retention, and atomic persistence.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `make build` PASS (2026-08-27; 15 Gradle tasks including the documentation-inventory check); `git diff --check` PASS.
Manual evidence: Not required.
Invalidation matrix: source or selected melody change affects every role/occurrence; tempo, meter, or pickup change affects every role/occurrence; a structure or harmony change affects only the changed occurrence scopes; a `chords.`, `bass.`, or `drums.` setting change affects only that role across occurrences; accepted dependency changes propagate to dependent candidates; any authority change makes a current whole-song export snapshot stale. Existing files remain inspectable and digest-verified.
Decisions/deviations: Candidate `authorityHash` is defined as the role/occurrence-scoped authority hash, while export `authorityHash` remains the complete-project hash. Role-specific settings use the lower-case role prefix convention; unprefixed settings are global. Existing v1 candidate records do not yet serialize accepted-dependency lists or explicit stale status, so MC-018 carries those inputs in the generation fingerprint and gives MC-019 the dependency record/lifecycle extension. Musical-authority confirmation now preserves existing structure and harmony instead of rebuilding empty lists. The target application reports impact before its atomic write; it does not mutate or delete candidate/export artifacts.
Known limitations: Candidate status, persisted accepted-dependency history, lock/rejection/restore transitions, and export-snapshot lifecycle remain MC-019. The target desktop does not yet render impact previews; MC-036 owns that UI. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-018 add scoped invalidation`.
Next task: MC-019 — implement candidate, acceptance, lock, and export-snapshot records.

### MC-019 — Implement candidate, acceptance, lock, and export-snapshot records

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `e81e4ad` / only the unrelated deleted Kotlin compiler session marker was present and remained outside the task commit.
Contracts read: F-REV-001–F-REV-005, F-EXP-001, and F-SYS-003; Architecture sections 4.1, 4.5, and 6; Quality Gate 3 persistence/generation; MC-019 task contract.
Current owners inspected: Target project records/schema, immutable artifact store, target authority mutation applications, and the generic accepted-candidate/export evidence patterns in the legacy arrangement state. Release, commercial, audio, renderer, and worker lineage was not introduced into the target records.
Behavior retained/extracted: `MidiCoreCandidate` now records stable role/occurrence identity, generator version, seed, profile, pattern, scoped authority hash, immutable MIDI/report artifacts, status, rejection reason, and accepted dependency IDs. `MidiCoreCandidateLifecycle` verifies the current project and scoped authority before publication, preserves immutable evidence on save failure, and implements explicit accept, replace, reject, lock, unlock, and restore transitions with chronological `CandidateAcceptanceHistory`. Authority mutations now persist `STALE` status for affected current/accepted candidates while retaining their files. `MidiCoreExportSnapshot` records accepted candidate artifact digests, role settings, generator versions, source/complete authority identity, and immutable export files; currentness is re-evaluated without rewriting historical snapshots.
Files added/changed: `src/main/kotlin/app/melotrail/application/MidiCoreCandidateLifecycle.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProject.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProjectSchema.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreAuthoritativeHarmony.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreMelodySelection.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreMusicalAuthority.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreStructureTimeline.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreCandidateLifecycleTest.kt`; `src/test/resources/fixtures/project/midi-core-v1.json`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None. The pre-existing `.kotlin/sessions/kotlin-compiler-10759057547151889139.salive` deletion remains a preserved unrelated user change.
Tracked deletion recoverability: Not applicable to MC-019; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreCandidateLifecycleTest --tests app.melotrail.project.MidiCoreProjectSchemaTest --tests app.melotrail.project.adapter.MidiCoreArtifactStoreTest --tests app.melotrail.application.MidiCoreAuthoritativeHarmonyTest --tests app.melotrail.application.MidiCoreMelodySelectionTest --tests app.melotrail.application.MidiCoreMusicalAuthorityTest --tests app.melotrail.application.MidiCoreStructureTimelineTest --rerun-tasks` PASS. Coverage includes immutable publication, artifact/report digests, collision refusal, all review transitions, lock enforcement, rejected-candidate admission refusal, restoration, stale authority status, accepted-candidate snapshot references, snapshot currentness, schema round-trip, and artifact reopening.
Full validation: `make test` PASS; `make build` PASS; documentation inventory check PASS; `git diff --check` PASS.
Manual evidence: Not required.
Lifecycle evidence: Candidate files remain at stable ID-derived paths and are never replaced or deleted by review transitions. Acceptance pointers move atomically while prior candidates and history remain recoverable. A changed authoritative chord marks the dependent candidate stale, rejects its later acceptance, makes the prior export snapshot stale, and leaves MIDI/report/export bytes unchanged.
Decisions/deviations: Candidate `authorityHash` remains the role/occurrence-scoped hash established by MC-018; export snapshots use the complete authority hash plus role settings and explicit accepted-candidate references. Existing v1 documents remain readable because all new schema fields have defaults, while newly encoded documents include explicit lifecycle fields. Snapshot capture consumes already-published export artifacts; staged file writing and complete package assembly remain MC-029 responsibilities. No release/commercial/audio lineage fields were added.
Known limitations: Candidate generation and review UI remain MC-020 through MC-040; complete package export remains MC-029. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-019 add candidate lifecycle records`.
Next task: MC-020 — define generation context.

### MC-020 — Establish shared generation context and curated patterns

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `b7fbaa2` / only the unrelated deleted Kotlin compiler session marker was present and remained outside the task commit.
Contracts read: F-ARR-004, F-ARR-006, F-ARR-007; Architecture section 4.4; MC-020 task contract.
Current owners inspected: `MusicalPatternLibrary.kt`, `CompositionProfile.kt`, `CompositionProfileCatalog.kt`, legacy arrangement harmony/musical-intent and density/space policy, protected melody semantic model, exact harmony timeline, authority fingerprint, and target architecture rules. Transition, strings, AI, renderer, and sound-library owners were not imported into the target context.
Behavior retained/extracted: `MidiCoreAuthoritySnapshot` projects loaded target authority, source/melody identities, PPQ, fixed tempo/meter/key, exact occurrences, and validated chord windows without a project path. `MidiCoreGenerationContext` binds one role and one occurrence to scoped harmony, protected melody notes, accepted semantic dependency notes, section energy/density/fill intent, a curated performance profile, pattern ID, generator identity/version, explicit seed, and a representable tick grid. Its context hash is length-delimited and scoped so unrelated occurrence edits do not change the request identity. `MidiCorePatternCatalog` contains only the extracted bass, chord-rhythm, complete drum-groove, and drum-fill variants; `MidiCorePerformanceProfileCatalog` contains MIDI intent/register profiles, not instrument or audio choices.
Files added/changed: `src/main/kotlin/app/melotrail/arrangement/core/MidiCoreGenerationContext.kt`; `src/main/kotlin/app/melotrail/arrangement/core/MidiCorePatternCatalog.kt`; `src/test/kotlin/app/melotrail/arrangement/core/MidiCoreGenerationContextTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None. The pre-existing `.kotlin/sessions/kotlin-compiler-10759057547151889139.salive` deletion remains a preserved unrelated user change.
Tracked deletion recoverability: Not applicable to MC-020; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.arrangement.core.MidiCoreGenerationContextTest --rerun-tasks` PASS (5 tests). Coverage includes stable scoped hashes and seed changes, unrelated-occurrence hash isolation, allowed core pattern IDs, complete authored groove/fill inventory, representable PPQ/grid boundaries, occurrence/harmony/melody/dependency scoping, and role/profile/pattern enforcement.
Full validation: `make test` PASS (2026-08-27; 14 tasks); `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS.
Manual evidence: Not required.
Context fixture: The five-test `MidiCoreGenerationContextTest` fixture uses two exact 1,920-tick occurrences with full authoritative harmony, a PPQ-480 quarter/sixteenth grid, scoped protected notes, and an accepted chord dependency. The extracted inventory is `MidiCorePatternCatalog.inventory()` with 6 chord-rhythm, 5 bass, 4 complete drum-groove, and 4 drum-fill entries; profiles are `chords.sustained`, `chords.pulsed`, `bass.sustained-sub-like`, `bass.muted-plucked`, `drums.dusty`, and `drums.lifted`.
Decisions/deviations: The generation boundary stores semantic dependency notes rather than artifact paths, so role engines can consume accepted context without filesystem access. `MidiCoreAuthoritySnapshot.from` requires an imported source, selected melody, complete authority, and gap-free harmony before constructing a request. A context hash uses the role/occurrence scope hash and exact local inputs, while the exposed snapshot retains complete authority for export/stale checks. Legacy pattern/profile owners remain compiled for old callers and are not deleted until their assigned cutover/cleanup tasks.
Known limitations: Role validation and the three target generators remain MC-021 through MC-024; candidate publication remains MC-025. The context is not yet wired to the desktop or project mutation use cases. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-020 define generation context`.
Next task: MC-021 — implement target role validation.

### MC-021 — Implement target role validation

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `2da3e74` / only the unrelated deleted Kotlin compiler session marker was present; the MC-021 log entry was then marked in progress.
Contracts read: F-ARR-005; MIDI Contract sections 6–8 and 10; Quality Gate 3 generation; MC-021 task contract.
Current owners inspected: `GeneratedRoleValidation.kt`, `BassQualityValidator.kt`, `LowEndInteraction.kt`, protected melody/context models, exact harmony windows, and target candidate lifecycle. The render/audio validators remain legacy owners until their assigned cutover and cleanup tasks; no render dependency entered the target validator.
Behavior retained/extracted: `MidiCoreRoleValidator` validates in-memory note-only candidates against one immutable `MidiCoreGenerationContext`. Common findings cover role/occurrence identity, fixed role channel, supported event type, deliberate silence, positive timing, occurrence bounds, representable grid ticks, MIDI register, velocity, and exact duplicates. Chords and Bass must occupy exact authoritative chord pitch classes; Drums use zero-based channel 9 (musician-facing channel 10) and the curated GM starter pitches. Density is a deterministic role/section ceiling, protected anchor collisions block, close non-anchor melody proximity is advisory, and every report carries context/candidate SHA-256 evidence in stable order. A rejected result is typed and is not a publication request.
Files added/changed: `src/main/kotlin/app/melotrail/arrangement/core/MidiCoreRoleValidation.kt`; `src/test/kotlin/app/melotrail/arrangement/core/MidiCoreRoleValidationTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None. No target validator for piano/pad/strings/transitions existed under the target roots; legacy render-dependent owners remain compiled only for pre-cutover callers and are assigned to later deletion tasks.
Tracked deletion recoverability: Not applicable to MC-021; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.arrangement.core.MidiCoreRoleValidationTest --rerun-tasks` PASS (7 tests). Coverage includes passing Chords/Bass/Drums, every common blocking policy, chromatic authority, exact chord/bass mismatch, protected-anchor blocking versus advisory melody proximity, duplicate/density limits, deliberate silence, GM drum channel/pitches, and order-independent report evidence.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS.
Manual evidence: Not required.
Role-policy matrix:

| Policy | Chords | Bass | Drums |
| --- | --- | --- | --- |
| Required channel | zero-based 1 / musician-facing 2 | zero-based 2 / musician-facing 3 | zero-based 9 / musician-facing 10 |
| Register | MIDI pitches 48–84 | MIDI pitches 28–55 | MIDI pitches 0–127 |
| Harmony | Every occupied chord window | Every occupied chord window; one-grid approach exception to the next window | Not applicable |
| Density at full section density | 4 notes per quarter | 2 notes per quarter | 8 notes per quarter |
| Additional event policy | Note events only | Note events only | Note events only; GM starter pitches 36/38/42/46 |
| Melody space | Protected anchor exact collision blocks; close non-anchor proximity advises | Same | Same |

Decisions/deviations: The validator consumes semantic values rather than MIDI files or artifact paths so malformed generator output can be rejected before storage. Harmony is authoritative even when chromatic; key scale compatibility is not substituted. The current target validator exposes a compatibility-named delegate for candidate callers, while the legacy generated-role validator is not imported. Publication integration remains MC-025, where only an accepted report may reach immutable artifact storage.
Known limitations: Candidate generation and validation-report serialization/publication remain MC-022 through MC-025; the current report is an in-memory target evidence type. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-021 validate core roles`.
Next task: MC-022 — implement the chord/keys accompaniment generator.

### MC-022 — Implement the chord/keys accompaniment generator

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `bab5d5c` / only the unrelated deleted Kotlin compiler session marker was present; MC-022 was then marked in progress.
Contracts read: F-ARR-001 and F-ARR-004–F-ARR-007; Architecture section 4.4 and 8; MIDI Contract sections 6, 8, and 10–12; Quality Gate 3 generation; MC-022 task contract.
Current owners inspected: `PadMidiGeneration.kt`, `MusicalPatternLibrary.kt`, `PadMidiGenerationTest.kt`, target authority/harmony/context/grid/pattern/validation models, and candidate lifecycle. The legacy pad adapter remains compiled for old callers and is not imported by the target generator.
Behavior retained/extracted: `MidiCoreChordGenerator` consumes only one immutable Chords context and emits note-only semantic events on musician-facing channel 2 (zero-based 1). Each exact authoritative chord window receives all realized chord tones and extensions in a bounded 48–84 register. Slash-bass symbols force the declared bass pitch class into the lowest voice; non-slash windows enumerate deterministic inversions and choose a seeded, register-centered, prior-voicing-aware result. Complete curated chord rhythms repeat per window, clip at harmony boundaries, and use profile-aware representable note lengths and energy/accent velocities. Protected melody anchors and accepted bass notes are treated as spacing constraints when a safe voicing exists. The generator returns the candidate and typed validation result without filesystem or project-state writes.
Files added/changed: `src/main/kotlin/app/melotrail/arrangement/core/MidiCoreChordGenerator.kt`; `src/test/kotlin/app/melotrail/arrangement/core/MidiCoreChordGeneratorTest.kt`; `src/test/resources/fixtures/midi-core/chords-golden.json`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None. The old pad adapter and pattern owner remain until their assigned MC-055 cleanup because pre-cutover callers still compile against them; no audio patch, renderer, sound-library, or path dependency entered the target path.
Tracked deletion recoverability: Not applicable to MC-022; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.arrangement.core.MidiCoreChordGeneratorTest --rerun-tasks` PASS (6 tests). Coverage includes chord extensions, all six curated rhythm variants with semantic golden output, slash-bass inversion, sub-bar chord boundaries, bounded voice leading, protected melody/bass spacing, seeded distinct alternatives, deterministic repeatability, and typed off-grid rejection.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS.
Manual evidence: Not required.
Per-pattern semantic golden: `src/test/resources/fixtures/midi-core/chords-golden.json`, SHA-256 `f8b1ad0069950e893be4f855ac970080c3e0b1bd6b7fd456c1dd4a6491cb4eb1`; PPQ 480, 4/4, C triad, pulsed profile, seed 17. It records note start/end/pitch/velocity sequences for sustained, laid-back quarters, late entry, dusty offbeats, broken syncopation, and bridge half-time patterns.
Decisions/deviations: Rhythm positions are anchored at each authoritative window start so sub-bar chord changes re-articulate immediately and never inherit a prior chord's pitch material. Pattern note durations are snapped down to the shared grid after applying the MIDI-only profile fraction; an unrepresentable authority boundary remains a typed validation rejection rather than being silently moved. Alternatives advance through the curated rhythm catalog and derive their explicit seed deterministically; they do not invoke random, prompt, audio, or analysis inputs.
Known limitations: Candidate publication and persisted validation-report serialization remain MC-025; bass and drum generators remain MC-023 and MC-024. Legacy pad behavior remains until its scheduled target cutover/cleanup. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-022 generate chord candidates`.
Next task: MC-023 — implement the bass generator and performance profiles.

### MC-023 — Implement the bass generator and performance profiles

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `007ab91` / only the unrelated deleted Kotlin compiler session marker was present; MC-023 was then marked in progress.
Contracts read: F-ARR-002 and F-ARR-004–F-ARR-007; Architecture sections 4.4 and 8; MIDI Contract sections 6, 8, and 10–12; Quality Gate 3 generation; MC-023 task contract.
Current owners inspected: `BassStemGeneration.kt`, `BassQualityValidator.kt`, `MusicalPatternLibrary.kt`, `LowEndInteraction.kt`, target authority/harmony/context/profile/pattern/validator models, accepted dependency context, and candidate lifecycle. The legacy Bass adapter remains compiled for pre-cutover callers and is not imported by the target generator.
Behavior retained/extracted: `MidiCoreBassGenerator` consumes one immutable occurrence-scoped Bass context and emits note-only semantic events on musician-facing channel 3 (zero-based 2). It covers sustained-root, root/fifth, octave, walk-to-next-root, and diatonic-approach patterns; slash-chord bass classes; exact authoritative chord-window clipping; sustained sub-like and muted/plucked MIDI intent profiles; bounded 28–55 register placement; phrase/purpose/energy accents; melody-activity density reduction; accepted Chords onset alignment; deterministic low-end spacing and voice continuity; and seeded, repeatable pattern alternatives. Walking and approach requests are quantized to legal current-chord tones, so every published note remains valid under exact harmony. Candidate validation is returned with the context-bound result before publication; no analysis-confidence fallback, instrument renderer, sound-library, filesystem, path, or audio dependency entered the target path.
Files added/changed: `src/main/kotlin/app/melotrail/arrangement/core/MidiCoreBassGenerator.kt`; `src/test/kotlin/app/melotrail/arrangement/core/MidiCoreBassGeneratorTest.kt`; `src/test/resources/fixtures/midi-core/bass-golden.json`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None. The old Bass adapter and pattern owner remain until their scheduled target cutover/cleanup; no obsolete audio data was touched.
Tracked deletion recoverability: Not applicable to MC-023; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.arrangement.core.MidiCoreBassGeneratorTest --rerun-tasks` PASS (9 tests). Coverage includes every curated pattern with both profiles, the semantic golden sequence, slash bass and sub-bar harmony boundaries, profile note lengths, walking/diatonic legality and movement bounds, accepted Chords rhythm, melody activity, protected-anchor and low-end collision avoidance, seeded alternatives, and typed off-grid rejection.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS.
Manual evidence: Not required.
Per-pattern semantic golden: `src/test/resources/fixtures/midi-core/bass-golden.json`, SHA-256 `db6f7834cdf5677dcec8d133a1535cc8f2f3c4b28ecceec46573db59cc523938`; PPQ 480, 4/4, C harmony, muted-plucked profile, seed 17. It records note start/end/pitch/velocity sequences for all five curated Bass patterns.
Decisions/deviations: Accepted Chords rhythm is advisory context only: only grid-representable onsets within the exact occurrence and harmony window can move a non-sustained attack, while unrepresentable authority remains a typed validation rejection. Melody activity reduces authored attack density without changing authority. Alternatives advance through the curated Bass pattern catalog and derive their explicit seed deterministically; no random, prompt, analysis, or renderer input is consulted.
Known limitations: Candidate publication and persisted validation-report serialization remain MC-025; drum generation remains MC-024. The legacy Bass adapter remains until its scheduled cleanup, and the transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-023 generate bass candidates`.
Next task: MC-024 — implement the drum generator and complete drum variants.

### MC-024 — Implement the complete-variant drum generator

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `0c72b83` / only the unrelated deleted Kotlin compiler session marker was present; MC-024 was then marked in progress.
Contracts read: F-ARR-003–F-ARR-006; MIDI Contract section 10; Architecture sections 4.4 and 8; Quality Gate 3 generation; MC-024 task contract.
Current owners inspected: `DrumMidiGeneration.kt`, `MusicalPatternLibrary.kt`, legacy drum quality/context behavior, target pattern/profile/context/validator models, accepted Bass dependency context, and GM drum policy. The legacy filesystem/MIDI writer and renderer-specific note-map path remain compiled for pre-cutover callers and are not imported by the target generator.
Behavior retained/extracted: `MidiCoreDrumGenerator` consumes one immutable occurrence-scoped Drum context and emits note-only semantic events on musician-facing channel 10 (zero-based 9), using only GM starter pitches kick 36, snare 38, closed hat 42, and open hat 46. It repeats each of the four complete authored groove variants without arbitrary hit decimation, selects another complete catalog variant only when the requested groove exceeds the occurrence density budget, applies an explicit fill only to the final phrase bar, clips every hit and note-off to the occurrence/bar boundary, and incorporates eligible representable off-beat attacks from accepted Bass context as deterministic kick intent. Energy, section purpose, phrase position, authored accents, profile intent, and seed shape bounded deterministic velocities; the result carries context-bound target validation before publication. No renderer, sound-library, filesystem, path, analysis, or audio dependency entered the target path.
Files added/changed: `src/main/kotlin/app/melotrail/arrangement/core/MidiCoreDrumGenerator.kt`; `src/test/kotlin/app/melotrail/arrangement/core/MidiCoreDrumGeneratorTest.kt`; `src/test/resources/fixtures/midi-core/drums-golden.json`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None. The legacy Drum adapter, renderer map, and pattern owner remain until their scheduled MC-055 cleanup; no obsolete audio data was touched.
Tracked deletion recoverability: Not applicable to MC-024; the unrelated session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.arrangement.core.MidiCoreDrumGeneratorTest --rerun-tasks` PASS (9 tests). Coverage includes all four complete grooves with both Drum profiles, groove and fill semantic goldens, whole-variant density selection, final-bar fill placement, cross-boundary safety, accepted Bass kick intent, GM pitches/channel, energy/purpose/profile velocity shaping, deterministic alternatives, deliberate silence, and off-grid dependency handling.
Full validation: `make test` PASS (2026-08-27; 14 Gradle tasks); `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS.
Manual evidence: Not required.
Complete groove/fill semantic golden: `src/test/resources/fixtures/midi-core/drums-golden.json`, SHA-256 `5b6184ab6d86bbe6f7106e4010ea43746838751de55f183ad15d763858baaf8c`; PPQ 480, 4/4, dusty profile, seed 17. It records all four complete groove sequences and each of the four phrase-fill sequences.
Decisions/deviations: `sectionPolicy.density` is a whole-variant selector: an explicit requested groove is preserved when its authored attack count fits the target role budget; otherwise the nearest compatible catalog groove is selected without filtering its steps. If no complete catalog variant can satisfy a very low density budget, the authored output is left intact for a typed validator rejection rather than silently deleting hits. A phrase fill replaces only a same-hit attack at the same boundary tick as an explicit fill operation; all other groove attacks remain. Accepted Bass context can add at most four sorted, off-beat, grid-representable kicks with available density budget, and off-grid dependency timing is ignored rather than shifted.
Known limitations: Candidate publication and persisted validation-report serialization remain MC-025. Drum hardening across development fixtures remains MC-043; the legacy Drum adapter and renderer-specific map remain until MC-055, and the transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-024 generate drum candidates`.
Next task: MC-025 — implement candidate generation and immutable publication use cases.

### MC-025 — Implement candidate generation and immutable publication use cases

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `a0e3f5a` / only the preserved unrelated deleted Kotlin compiler session marker is present.
Contracts read: F-ARR-001–F-ARR-006; F-SYS-004; Architecture sections 4.4 and 8; MIDI Contract sections 4, 6, 9, and 13–14; Quality Gate 3 generation/persistence; MC-025 task contract.
Current owners inspected: Target `MidiCoreCandidateLifecycle`, `MidiCoreArtifactStore`, semantic MIDI reader/writer, generation context/catalog, Chords/Bass/Drums generators, role validator, and target project schema. The old arrangement application service and stage artifact writers remain legacy owners and are not imported into the target use case.
Behavior retained/extracted: `MidiCoreCandidateGeneration` owns one deterministic role/occurrence request, reconstructs source and protected-melody identity from immutable artifacts, consumes accepted dependency notes only after digest/status checks, validates generated semantic notes, round-trips the role MIDI through the target writer/reader, serializes the context-bound validation report, and publishes immutable MIDI/report artifacts before the atomic project append. `MidiCoreProjectWriteCoordinator` serializes candidate publication per project root. Cancellation after evidence publication leaves the evidence inspectable but unbound; stale sessions, save failures, and collisions never replace project state or existing files.
Files added/changed: `src/main/kotlin/app/melotrail/application/MidiCoreCandidateGeneration.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreProjectWriteCoordinator.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreCandidateLifecycle.kt`; `src/main/kotlin/app/melotrail/arrangement/core/MidiCoreRoleValidation.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreCandidateGenerationTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable to MC-025; the unrelated Kotlin compiler session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreCandidateGenerationTest --rerun-tasks` PASS (8 tests). Coverage includes all three roles, validation-report round trip, immutable overwrite rejection, collision-free retry, cancellation before/after publication, malformed-grid rejection, stale completion, atomic save failure with recovery evidence, and concurrent requests.
Full validation: `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS; `make test` PASS (2026-08-27).
Manual evidence: Not required.
Decisions/deviations: The application boundary intentionally accepts a cooperative cancellation token in addition to coroutine cancellation so a UI cancel action can be represented as a typed result after immutable evidence has been published. A per-project-root lock protects the final load/check/publish/save transaction; the existing session equality remains the optimistic authority check. Candidate generation does not approve, mutate accepted references, generate all roles, render audio, or consult legacy arrangement services.
Known limitations: The existing target review lifecycle remains the MC-026 application surface for list/accept/reject/lock/unlock/restore commands; this task only supplies safe candidate publication. Orphan evidence after cancellation or project-save failure is retained for inspection and later recovery rather than silently deleted.
Commit: `midi-core: MC-025 publish generated candidates`.
Next task: MC-026 — implement candidate review mutations.

### MC-026 — Implement candidate review mutations

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `861126d` / only the preserved unrelated deleted Kotlin compiler session marker is present.
Contracts read: F-REV-001–F-REV-005; F-ARR-006; Architecture sections 4.1, 4.4, and 8; MIDI Contract sections 6, 9, and 13–14; MC-026 task contract.
Current owners inspected: Target `MidiCoreCandidateLifecycle`, target project/schema records, scoped invalidation planner, candidate generation use case, target MIDI reader, and legacy arrangement acceptance state only for transition behavior. Legacy mutable current-output paths and automatic approval remain outside the target review boundary.
Behavior retained/extracted: Added a persisted monotonic project revision to the target project/schema and advanced it on source, melody, authority, timeline, harmony, candidate publication, review transitions, and export-snapshot mutations. Added a review facade that lists candidates with validation evidence, compares deterministic semantic notes, delegates explicit accept/reject/lock/unlock/restore transitions, and regenerates exactly one role/occurrence without changing accepted pointers. Review mutations are serialized per project root and reject stale revisions, stale authority, digest mismatch, wrong scope, and locked replacements; stale candidates remain inspectable but cannot be accepted.
Files added/changed: `src/main/kotlin/app/melotrail/application/MidiCoreCandidateReview.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreCandidateLifecycle.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProject.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProjectSchema.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreSourceImport.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreMelodySelection.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreMusicalAuthority.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreStructureTimeline.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreAuthoritativeHarmony.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreCandidateReviewTest.kt`; `src/test/resources/fixtures/project/midi-core-v1.json`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable to MC-026; the unrelated Kotlin compiler session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.project.MidiCoreProjectSchemaTest --tests app.melotrail.application.MidiCoreCandidateLifecycleTest --tests app.melotrail.application.MidiCoreCandidateReviewTest --rerun-tasks` PASS. The review matrix covers list/compare, wrong role/occurrence, full accept/reject/lock/unlock/restore transitions, tampered evidence, revision conflict, targeted regeneration, and concurrent decisions.
Full validation: `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS; `make test` PASS (2026-08-27; root and desktop suites).
Manual evidence: Not required.
Decisions/deviations: Revision defaults to zero for backward-compatible decoding of existing MIDI Core v1 documents and advances only on persisted project mutations. Review requests default their expected revision from the opened session, while callers can provide an explicit revision. Candidate evidence is reverified before acceptance or inspection; regeneration is intentionally not an approval operation. Review semantic comparison is note-event scoped and deterministic; full protected-melody/song assembly belongs to MC-027.
Known limitations: The review facade is an application boundary and is not yet wired to the Compose Desktop Review page; MC-038 owns that UI integration. Full accepted-song assembly, gap/overflow validation, and export-facing review sequences remain MC-027. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-026 review arrangement candidates`.
Next task: MC-027 — implement semantic candidate diff and accepted-song assembly.

### MC-027 — Implement semantic candidate diff and accepted-song assembly

Status: DONE
Started: 2026-08-27
Completed: 2026-08-27
Starting commit/status: `ff4285a` / only the preserved unrelated deleted Kotlin compiler session marker is present.
Contracts read: F-REV-001 and F-REV-006; Architecture sections 4.4–4.5 and 7–9; MIDI Contract sections 8–10; MC-027 task contract.
Current owners inspected: Target review facade, candidate lifecycle/artifact store, protected-melody selector, semantic MIDI reader/writer, authority timeline, role validation report, and legacy arrangement full-song/stage comparison paths only for reusable event-comparison behavior.
Behavior retained/extracted: `MidiCoreCandidateDiff` now reports deterministic semantic note additions, removals, and changes. `MidiCoreAcceptedSongAssembly` verifies the immutable source and protected melody, current authority and exact occurrence scopes, accepted candidate/report digests, role channels, file shape, note counts, boundaries, and intentional silence before building one in-memory review sequence with Melody first and one aggregated track per selected role. Assembly preserves absolute occurrence ticks and writes no source, candidate, or project artifacts.
Files added/changed: `src/main/kotlin/app/melotrail/application/MidiCoreCandidateReview.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreAcceptedSongAssembly.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreAcceptedSongAssemblyTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable to MC-027; the unrelated Kotlin compiler session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreAcceptedSongAssemblyTest --tests app.melotrail.application.MidiCoreCandidateReviewTest --rerun-tasks --console=plain` PASS. Coverage includes repeated occurrences, pickup and sub-bar harmony windows, intentional role silence, missing/stale/digest-invalid evidence, candidate overflow and channel mismatch, source identity preservation, and deterministic additions/removals/changes.
Full validation: `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS; `make test` PASS (2026-08-27; 14 Gradle tasks).
Manual evidence: Not required.
Decisions/deviations: Role candidates are generated and stored per absolute occurrence, but the review sequence has one track per role; assembly validates each occurrence independently and then aggregates its notes in occurrence order. An accepted candidate with zero notes is intentional role silence and remains a required accepted scope. Semantic diff pairs notes at equal start ticks after deterministic event-value ordering; it does not attempt musical alignment or critic/humanization behavior.
Known limitations: The assembled review sequence remains in-memory until MC-029's exporter captures and publishes an immutable package; Compose Review/audition integration remains MC-028 and MC-038. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-027 assemble accepted song`.
Next task: MC-028 — implement minimal local MIDI audition.

### MC-028 — Implement minimal local MIDI audition

Status: DONE
Started: 2026-08-27
Completed: 2026-08-28
Starting commit/status: `50f1438` / only the preserved unrelated deleted Kotlin compiler session marker is present.
Contracts read: F-PLAY-001–F-PLAY-004 and F-SYS-001; Architecture sections 4.6 and 5; MC-028 task contract.
Current owners inspected: Target semantic MIDI export model/writer and reader; the existing audio player/renderer only as a boundary reference and not as an implementation dependency; JDK Sequencer, Receiver, Transmitter, and device-discovery APIs.
Behavior retained/extracted: `MidiAuditionPort` and `MidiAuditionController` own immutable source/candidate/occurrence/role/full-arrangement views, bounded seek/loop plans, one active session, deterministic state history, mute/solo state, supersession tokens, end-of-view handling, and typed recoverable failures. `JdkMidiAuditionOutput` routes the existing target writer's in-memory format-1 sequence through a local JVM receiver, clips transient occurrence windows, maps role mute/solo to Sequencer tracks, discovers receiver-capable devices, and sends all-notes-off/all-sound-off during stop and close. Audition opens no project store and writes no files.
Files added/changed: `src/main/kotlin/app/melotrail/audition/MidiAudition.kt`; `src/main/kotlin/app/melotrail/audition/adapter/JdkMidiAuditionOutput.kt`; `src/main/kotlin/app/melotrail/midi/adapter/JdkMidiWriter.kt`; `src/test/kotlin/app/melotrail/audition/MidiAuditionControllerTest.kt`; `src/test/kotlin/app/melotrail/audition/adapter/JdkMidiAuditionOutputTest.kt`; `src/test/kotlin/app/melotrail/midi/adapter/JdkMidiWriterTest.kt`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: None.
Tracked deletion recoverability: Not applicable to MC-028; the unrelated Kotlin compiler session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None.
Focused tests: `./gradlew :test --tests app.melotrail.audition.MidiAuditionControllerTest --tests app.melotrail.audition.adapter.JdkMidiAuditionOutputTest --tests app.melotrail.midi.adapter.JdkMidiWriterTest --rerun-tasks --console=plain` PASS. Coverage includes state-machine transport, exact seek/loop boundaries, mute/solo, rapid start/stop, superseded callbacks, device loss/unavailable output, cleanup assertions, project-byte preservation, in-memory writer output, and JVM boundary error mapping.
Full validation: `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS; `make test` PASS (2026-08-28; 14 Gradle tasks).
Manual evidence: Not required.
Decisions/deviations: The port accepts a complete semantic `MidiExportSong` view and keeps preview timbre entirely inside the local output adapter. A new play request closes the prior session before opening its replacement; stale completion callbacks are ignored by session ID. Occurrence windows are clipped only in the transient playback sequence so source/candidate/project data and their absolute musical authority remain untouched. A non-looping view uses the window as its end boundary; an explicit loop repeats only its validated subrange.
Known limitations: Compose wiring remains with the later desktop integration task; the adapter does not render or persist audio and is not export evidence. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-028 add MIDI audition`.
Next task: MC-029 — implement the vertical MIDI package exporter.

### MC-029 — Implement the vertical MIDI package exporter

Status: DONE
Started: 2026-08-28
Completed: 2026-08-28
Starting commit/status: `0408a70` / only the preserved unrelated deleted Kotlin compiler session marker is present.
Contracts read: F-EXP-001–F-EXP-006 and F-SYS-003; Architecture sections 4.7 and 5; MIDI Contract sections 9–14; MC-029 task contract.
Current owners inspected: MC-008 `MinimalMidiExportBundle`, target MIDI writer/reader, artifact store, project export-snapshot records/lifecycle, and accepted-song assembly.
Behavior retained/extracted: Added `MidiCoreMidiPackageExporter` as the target application boundary. It reopens and revision-checks the project, assembles the protected melody and selected accepted role scopes, writes complete and per-role SMF1 files in deterministic order, semantically re-imports every MIDI file, writes a portable minimum manifest with authority/candidate/profile/instrument-suggestion/file-validation evidence, verifies staged digests, atomically publishes one snapshot directory, and records the immutable snapshot only after publication. Enabled generated roles are explicit; Melody remains mandatory while disabled generated roles are omitted. Snapshot records now bind enabled roles and the manifest timestamp.
Files added/changed: `src/main/kotlin/app/melotrail/application/MidiCoreMidiPackageExporter.kt`; `src/test/kotlin/app/melotrail/application/MidiCoreMidiPackageExporterTest.kt`; `src/main/kotlin/app/melotrail/application/MidiCoreCandidateLifecycle.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProject.kt`; `src/main/kotlin/app/melotrail/project/MidiCoreProjectSchema.kt`; `src/test/resources/fixtures/project/midi-core-v1.json`; `build.gradle.kts`; `docs/FUNCTION_DOCUMENTATION_INVENTORY.json`; and this execution log.
Files/data deleted: `src/main/kotlin/app/melotrail/export/adapter/MinimalMidiExportBundle.kt`; `src/test/kotlin/app/melotrail/export/adapter/MinimalMidiExportBundleTest.kt`; and the obsolete `melotrail.dawSpikeDirectory` test hook.
Tracked deletion recoverability: The superseded MC-008 exporter and test are recoverable from Git; no user MIDI source, candidate, accepted artifact, or export snapshot was deleted. The unrelated Kotlin compiler session-marker deletion remains recoverable from Git and was not included in the task commit.
Ignored deletion recoverability: None planned.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreMidiPackageExporterTest --tests app.melotrail.application.MidiCoreAcceptedSongAssemblyTest --tests app.melotrail.application.MidiCoreCandidateLifecycleTest --tests app.melotrail.project.MidiCoreProjectSchemaTest --tests app.melotrail.project.adapter.MidiCoreArtifactStoreTest --tests app.melotrail.midi.adapter.JdkMidiWriterTest --tests app.melotrail.midi.adapter.JdkMidiReaderTest --tests app.melotrail.architecture.TargetArchitectureRulesTest --rerun-tasks --console=plain` PASS. The exporter matrix covers complete/reopened package evidence, deterministic file order and hashes, optional-role omission, missing/stale/unaccepted blockers, failed/tampered staging cleanup, collision refusal, manifest portability, and source/candidate immutability.
Full validation: `python3 tools/check_documentation_coverage.py --repository .` PASS; `git diff --check` PASS; `make test` PASS (2026-08-28; 14 Gradle tasks).
Manual evidence: Not required.
Decisions/deviations: The target exporter always includes Melody and treats `enabledRoles` as the explicit generated-role set. It records only enabled-role accepted references in the snapshot, while preserving disabled candidates and acceptances in project state. Manifest JSON uses relative filenames/digests and descriptive DAW instrument suggestions without program changes, device names, or private configuration. The old MC-008 spike and its dedicated test property were deleted once the target owner was live.
Known limitations: Compose Export-page wiring remains with MC-039; manual Logic Pro/GarageBand export evidence remains the MC-048 gate. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-029 export MIDI package`.
Next task: MC-030 — prove the kernel vertical slice end to end.

### MC-030 — Prove the kernel vertical slice end to end

Status: DONE
Started: 2026-08-28
Completed: 2026-08-28
Starting commit/status: `7a666f9` / only the preserved unrelated deleted Kotlin compiler session marker is present.
Contracts read: F-PROJ-001–F-EXP-006 and G3; MC-030 task contract.
Current owners inspected: target project lifecycle, source import, melody selection, authority, structure/harmony, candidate generation/review, accepted-song assembly, MIDI audition, vertical package exporter, target test fixtures, and test composition boundaries.
Behavior retained/extracted: Added `MidiCoreVerticalSliceTest` as a target-only JVM composition proof. The test creates and reopens a project, imports and protects an SMF source, confirms fixed authority, persists structure and harmony, rejects an incomplete export and recovers after review, generates two semantically different alternatives for each core role, rejects/accepts/locks the selected candidates, preserves the first candidate bytes through targeted regeneration, assembles the accepted song, auditions it through a fake MIDI output port, publishes the complete package, verifies its portable tree and digests, and semantically re-imports every exported MIDI file.
Files added/changed: `src/test/kotlin/app/melotrail/application/MidiCoreVerticalSliceTest.kt`; and this execution log.
Files/data deleted: None planned.
Tracked deletion recoverability: The unrelated Kotlin compiler session-marker deletion remains recoverable from Git and will not be included in the task commit.
Ignored deletion recoverability: None planned.
Focused tests: `./gradlew :test --tests app.melotrail.application.MidiCoreVerticalSliceTest --rerun-tasks --console=plain` PASS. One JVM test covers the project tree (`source/original.mid`, `reports/import.json`, six immutable candidate MIDI/report pairs, and `exports/vertical-export`), the source fixture SHA-256 `a2e32b1df5e78867193191a15c82caaa0b7c070b2e328c56b41a1ea5aaba4a35`, persisted source/candidate digests, five exported MIDI digests, manifest digest, snapshot references, and semantic re-import facts (SMF 1, PPQ 480, exact role names/order, and end tick 480).
Full validation: `git diff --check` PASS; `make test` PASS (2026-08-28; 14 Gradle tasks); `make build` PASS (2026-08-28; 15 Gradle tasks, including documentation coverage).
Manual evidence: Not required.
Decisions/deviations: The E2E uses the application boundaries directly with a fake `MidiAuditionOutput`; no Python worker, audio renderer, legacy project schema, or old arrangement service is reachable from the test. The failure/recovery path intentionally attempts export before any acceptance, verifies no snapshot or project mutation, then completes the same project after explicit review transitions. Each role uses one first candidate and one targeted regenerated alternative; the second is rejected, the first accepted, and the first acceptance locked.
Known limitations: Compose desktop composition and workflow wiring remain with MC-031–MC-040; final Logic Pro/GarageBand package evidence remains the MC-048 gate. The transitional Python documentation-inventory build check remains until MC-058.
Commit: `midi-core: MC-030 prove vertical slice`.
Next task: MC-031 — compose the desktop from target services.

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
