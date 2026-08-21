# Testing, cutover, and rollout strategy

## Project-format policy

Schema v4 is the only supported project format. There is no legacy-project
compatibility window, dual read, converter, migration command, or migration UI.
Missing-version, v1–v3, and superseded v4 documents fail at open without being
rewritten. Git history is the archive for removed formats and implementations.

Every task that replaces a project-owned contract deletes the superseded reader,
writer, mapper, service, registration, UI branch, fixture, and exclusive test in
the same cutover. A new default or nullable field must not be used to make an old
serialized project shape appear current.

## Test layers

### Characterization and strict project admission

Before changing a live boundary, pin current canonical behavior for:

- schema-v4 create/read/write, strict unknown-field rejection, project-relative
  path validation, and atomic publication;
- rejection of missing-version, v1, v2, v3, and provisional v4 documents without
  writes or recovery-state creation;
- direct MIDI and eligible audio import, including the exact import branch;
- selected MIDI precedence and reversal;
- cleanup approval, AI Fix, Enhance, and MIDI Feel references;
- repeated parts, current occurrence/boundary identity, and arrangement inputs;
- render/stem/mix/master/output hashes and failure behavior;
- current worker protocols and support claims.

### Domain and persistence

Property/table tests cover pitch-class enharmonics, modes, tempo/meter bounds,
chord formatting/round-trip, required qualities, progression reorder IDs,
profile/mood resolution, part/occurrence invariants, context hashes,
deterministic instrument selection, and canonical schema serialization.

Canonical fixtures cover complete/incomplete current settings, selected/rejected
AI evidence, MIDI Feel, repeated occurrences, approved Cohesion/arrangement/
mix/master, and missing/stale artifacts. No legacy-project fixture is retained.

### Stage runner and processors

Contract tests exercise first run, cache hit, changed config/context/input, stale
propagation, recoverable failure, worker timeout, malformed model response, crash
recovery, retry, output validation failure, atomic publication, source
immutability, and concurrent duplicate commands.

MIDI fixtures assert exact invariants and edit reports for clean, normalize,
transpose, correct, enhance, and humanize. Deterministic processors must produce
stable hashes for the same version, configuration, and seed.

Instrument and release-credit fixtures cover hard capability/license filters,
deterministic selection, explicit substitution, embedded provenance, exact used
instrument lineage, and atomic audio/credits publication. These external-data
contracts do not authorize old project-schema readers.

### UI

Compose tests cover Setup, Harmony, Parts progress/failure/retry, source-key
confirmation, enhancement review/bypass, structure occurrence identity,
arrangement roles/instruments, Build substages, readiness links, and stale-state
explanations across wide/medium/narrow layouts. No migration action, badge, page,
or recovery state exists for unsupported projects.

### End to end and manual

Offline end-to-end tests use fakes for worker/model/renderer/audio device and a
real temporary canonical project. Python unit tests validate retained/current
command schemas, path/error behavior, and deterministic fixtures.

Manual gates remain mandatory for real Basic Pitch, local Qwen, sfizz/sample
rendering, playback/listening A/B, style-processing/master quality,
accessibility, packaging, canonical-project open, and unsupported-project
rejection. Update `docs/RELEASE_ACCEPTANCE.md`; do not infer audio quality from
structural tests.

## Incremental implementation

1. Task 118 removes all legacy-project readers, mappers, services, UI, and
   exclusive tests, and installs strict canonical project admission.
2. Tasks 119–121 add shared musical authority and durable contracts directly to
   the canonical schema.
3. Tasks 122–126 replace processors one at a time and delete each superseded
   runtime path in its owning task.
4. Tasks 127–129 add critic/enhancement/diagnostic contracts with current-schema
   fixtures only.
5. Task 130 proves the end-to-end canonical workflow, unsupported-project
   rejection, cleanup completeness, and release gates.

## Documentation gates

Every behavior task updates relevant README, workflow/MIDI/troubleshooting/
commercial docs, worker schemas, function inventory, and release acceptance.
The generated documentation coverage check stays passing. Stale module,
legacy-project, migration, and unsupported entry-point claims are removed before
release.

## Definition of done for the roadmap

- All task acceptance criteria pass at their proper layer.
- Only canonical schema-v4 projects open; unsupported documents remain unchanged.
- Setup through Export readiness is understandable in the desktop UI.
- Each selected release artifact has closed, hash-validated lineage.
- Original/corrected/enhanced melody versions are comparable and bypassable.
- Failed expensive stages can resume without rerunning completed upstream work.
- Automated suites and documented manual gates are complete for claimed support.
- No obsolete/deprecated project-owned implementation, dormant registration,
  disabled flag, exclusive obsolete test, configuration, dependency, or stale
  documentation remains.
