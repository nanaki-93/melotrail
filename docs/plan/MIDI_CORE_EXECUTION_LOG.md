# MIDI Core execution log

Status: MC-001 complete; MC-002 is next

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
| MC-002 | TODO | | | |
| MC-003 | TODO | | | |
| MC-004 | TODO | | | |
| MC-005 | TODO | | | |
| MC-006 | TODO | | | |
| MC-007 | TODO | | | |
| MC-008 | TODO | | | |
| MC-009 | TODO | | | |
| MC-010 | TODO | | | |
| MC-011 | TODO | | | |
| MC-012 | TODO | | | |
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
| G0 Documentation ready | MC-000 | TODO | |
| G1 MIDI compatibility proven | MC-001–MC-009 | TODO | |
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
Commit: `midi-core: MC-001 add owned MIDI fixtures`.
Next task: MC-002 after MC-001 validation and commit.

## 6. Manual gate records

### MC-009 — Early DAW compatibility

- Melotrail build/commit:
- Fixture/export snapshot and hashes:
- macOS version:
- Logic Pro version/result/evidence:
- GarageBand version/result/evidence:
- Required user actions:
- Reviewer/date:
- Decision:

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
