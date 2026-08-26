# MIDI Core execution log

Status: not started

Task authority: `MIDI_CORE_TASKS.md`

Execution prompt: `EXECUTE_MIDI_CORE_TASKS_PROMPT.md`

This file is evidence, not a second plan. Update it after every task and commit.

## 1. Baseline

- Repository root:
- Branch:
- Starting commit:
- Starting status:
- Preserved unrelated changes:
- JDK/Gradle/macOS:
- Production Kotlin files/lines:
- Test Kotlin files/lines:
- Python files/lines:
- Repository-local old audio data size:
- Local sound-library size:
- Legacy UI fixture size:
- Baseline `make test`:
- Baseline `make build`:
- Recorded by/date:

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
| MC-000 | TODO | | | |
| MC-001 | TODO | | | |
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
