# Melotrail documentation

Documentation is split by ownership so active implementation plans do not
compete with current operating instructions.

## Completed quality-pipeline record

The complete canonical melody and release-quality roadmap is under
[`plan/`](plan/README.md):

- [`plan/PLAN.md`](plan/PLAN.md) — completed outcome and target architecture
- [`plan/PROJECT_ANALYSIS.md`](plan/PROJECT_ANALYSIS.md) — baseline diagnosis
  retained as rationale for the completed four-source and Ensemble Cohesion work
- [`plan/TASKS.md`](plan/TASKS.md) — ordered QP-001–QP-018 contracts
- [`plan/QUALITY_GATES.md`](plan/QUALITY_GATES.md) — musical, listening, and
  release gates
- [`plan/YOUTUBE_READINESS.md`](plan/YOUTUBE_READINESS.md) — current platform
  policy scope
- [`plan/EXECUTE_ALL_TASKS_PROMPT.md`](plan/EXECUTE_ALL_TASKS_PROMPT.md) —
  sequential implementation/commit prompt
- [`plan/EXECUTION_LOG.md`](plan/EXECUTION_LOG.md) — task evidence ledger
- [`plan/DOCUMENTATION_AUDIT.md`](plan/DOCUMENTATION_AUDIT.md) — consolidation
  decisions

## Current product operation

- [`MIDI_IMPORT_PROCESS.md`](MIDI_IMPORT_PROCESS.md) — direct MIDI and eligible
  solo-melody audio import
- [`TRACK_PROCESS_WORKFLOW.md`](TRACK_PROCESS_WORKFLOW.md) — current schema-v4
  stage order, artifacts, approvals, and recovery
- [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — desktop, worker, Basic Pitch,
  sound library, renderer, and build recovery

These documents describe shipped behavior. The completed QP record in
`docs/plan/` is retained for acceptance evidence; do not present its historical
baseline defects as current behavior.

## Release, policy, and maintenance

- [`COMMERCIAL_PROVENANCE.md`](COMMERCIAL_PROVENANCE.md) — current commercial
  evidence and YouTube policy-review boundary
- [`RELEASE_ACCEPTANCE.md`](RELEASE_ACCEPTANCE.md) — automated and manual release
  gate
- [`COMPATIBILITY_READERS.md`](COMPATIBILITY_READERS.md) — active external
  compatibility contracts and removal conditions
- [`SPRING_API_RETIREMENT.md`](SPRING_API_RETIREMENT.md) — non-destructive legacy
  data disposition retained by an executable test
- [`FUNCTION_DOCUMENTATION_INVENTORY.md`](FUNCTION_DOCUMENTATION_INVENTORY.md) —
  production callable-documentation coverage

## Visual regression fixtures

`pictures/App-pages.png` and the referenced images under `pictures/UI/` are
test inputs for the Compose Desktop visual regression suite. They are not an
active product roadmap. Do not delete or rename them without migrating their
test consumers.

## Documentation rules

- Root `README.md` introduces the product; root `PLAN.md` points to the one
  active plan suite.
- Operational docs remain at stable paths used by the UI and tests.
- Remove superseded plans once their information is either implemented or
  consolidated; Git history is the archive.
- Never claim model, renderer, transcription, packaging, listening, rights, or
  platform support that has not been verified.
- Run documentation coverage and dangling-reference checks with each behavior
  task that changes contracts.
