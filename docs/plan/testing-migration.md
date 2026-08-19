# Testing, migration, and rollout strategy

## Test layers

### Characterization first

Before changing a boundary, pin current behavior for:

- v1/v2/v3 open/validate/save and no rewrite-on-open;
- direct MIDI and eligible audio import, including the exact import branch;
- selected MIDI precedence and reversal;
- cleanup approval, AI-fix/Lo-fi Feel legacy references;
- repeated parts, current occurrence/boundary identity, and arrangement inputs;
- render/stem/mix/master/output hashes and failure behavior;
- current REST/worker protocols that will remain supported;
- README/Makefile/Gradle support claims.

### Domain and persistence

Property/table tests cover pitch-class enharmonics, modes, tempo/meter bounds,
chord formatting/round-trip, all required qualities, progression reorder IDs,
profile/mood resolution/clamping/versioning, part/occurrence invariants, context
serialization/hashes, instrument-registry v1/v2 compatibility, deterministic
instrument selection, and schema migration.

Golden fixtures include valid v1/v2/v3 projects, incomplete legacy settings,
unknown legacy roles, selected/rejected AI fixes, Lo-fi Feel, repeated parts,
approved cohesion/arrangement/mix/master, and missing/stale artifacts.

### Stage runner and processors

Contract tests exercise first run, cache hit, changed config/context/input, stale
propagation, recoverable failure, worker timeout, malformed model response, crash
recovery, retry, output validation failure, atomic publication, source immutability,
and concurrent duplicate commands.

MIDI fixtures assert exact invariants and edit reports for clean/normalize/
transpose/correct/enhance/humanize. Deterministic processors must produce stable
hashes for the same version/config/seed.

Instrument fixtures cover multiple candidates per role, weighted affinity/trait
ranking, capability/license hard filters, embedded license/provenance round-trip,
CC0/CC BY admission, NC rejection, unknown review, default no-attribution
preference, stable tie-break, user pin, no-candidate
diagnostics, asset/capability mismatch, explicit substitution, and the invariant
that approved arrangements never re-resolve at render time.

Release-credit fixtures derive exact used instruments from resolved final-mix
lineage; exclude candidates/unused/CC0 entries; deduplicate/sort CC BY attribution;
cover CC0-only output, missing attribution, conservative uncertain usage, atomic
publication, audio/credits hash pairing, and live-registry mutation independence.

### UI

Compose tests cover Setup, Harmony, Parts progress/failure/retry, source-key
confirmation, enhancement bypass/comparison, structure occurrence identity,
arrangement roles/instruments, Build substages, readiness links, and stale-state
explanations across wide/medium/narrow layouts. Test semantics, keyboard reorder,
focus, and playing-artifact labels.

Library/Arrange UI tests also cover metadata filtering, suggestion reasons,
verified capability/license presentation, unavailable pinned IDs, explicit
substitution, and path/filename redaction.

### End to end and manual

Offline end-to-end tests use fakes for worker/model/renderer/audio device and a
real temporary file-backed project. Python unit tests validate every retained/
new command schema, path/error behavior, and deterministic fixtures.

Manual gates remain mandatory for real Basic Pitch, local Qwen, sfizz/sample
rendering, playback/listening A/B, style-processing/master quality, accessibility,
packaging, and new/legacy project flows. Update `docs/RELEASE_ACCEPTANCE.md`; do
not infer audio quality from structural tests.

## Migration mechanics

1. Add a pure v3-to-v4 mapper and v4 validator.
2. `open` returns a compatible snapshot plus setup/migration requirements and
   never writes.
3. Explicit migrate/save writes new JSON/manifests to temporary paths, validates
   them, atomically publishes, and retains recoverable legacy evidence.
4. Map existing paths/hashes rather than copy or rename audio/MIDI unnecessarily.
5. Assign deterministic structure occurrence IDs once and persist them.
6. Mark missing creative settings as user input required. Never invent key,
   harmony, mood, or profile from MIDI analysis without confirmation.
7. Preserve legacy selected AI-fix/feel and current release artifacts until a
   user changes upstream context or reruns a new stage.
8. Changing cohesion dependency requires explicit target-order regeneration and
   approval; old cohesion remains historical.
9. Continue reading sound-library registry v1; preserve current logical keys as
   legacy instrument IDs, materialize hashed license records into embedded
   snapshots, and require explicit v2 migration without moving assets.

## Compatibility-code lifecycle

Compatibility is an active support obligation, not a place to park obsolete code.
Every temporary reader/adapter records its supported schema/contract, caller,
owner, removal condition, and deletion task. New writes and runtime orchestration
switch to the canonical path immediately. After supported fixtures/data migrate,
the same owning task deletes superseded writers, services, registrations, routes,
configuration, resources, dependencies, UI actions, exclusive tests, and docs.

Immutable historical artifacts remain data. Git history remains the source archive.
Neither requires dormant executable implementations, commented-out code, or
permanently disabled flags in the repository.

## Incremental rollout

### Phase A: read-only compatibility

Ship v4 readers/mappers, profile catalog, and new query DTOs behind existing UI.
All current tests remain green. Compare v3/v4 readiness snapshots in tests.

### Phase B: UI/domain milestone

Enable explicit v4 save, Setup, Harmony, structured Parts, and persistent
occurrences. Introduce stage ledger and automatic progress with deterministic or
mock new processors. Existing advanced workflow stays behind compatibility
adapters until v4 prerequisites are complete.

### Phase C: processor replacement

Enable transpose, separated correction, and context-aware enhancement one stage
at a time. Dual-read legacy references; new runs write only stage-ledger records.

### Phase D: build order

Make arrangement independent, add arrangement-aware cohesion/humanization, then
switch render handoff. Perform one explicit downstream invalidation and require
approval where hashes change.

### Phase E: production/adapters

Update mix/style/master/provenance and release gates. Task 028 found no
demonstrated Spring callers and deleted its product surface after recording the
recoverable legacy-data disposition. Delete every superseded runtime path as soon
as usage evidence and migration tests prove the cutover.

## Documentation gates

Every behavior task updates relevant README, workflow/MIDI/troubleshooting/
commercial docs, API/worker schemas, function inventory, and release acceptance.
The generated documentation coverage check stays passing. Stale module and
unsupported entry-point claims are removed before release.

## Definition of done for the roadmap

- All task acceptance criteria pass at their proper layer.
- Legacy source/projects remain intact and can be opened/migrated explicitly.
- Setup through Export readiness is understandable in the supported desktop UI.
- Each selected release artifact has closed, hash-validated lineage.
- Original/corrected/enhanced melody versions are comparable and bypassable.
- Failed expensive stages can resume without rerunning completed upstream work.
- Lo-fi works as a versioned profile; no core workflow requires a lo-fi string.
- Automated suites and documented manual gates are complete for claimed support.
- No obsolete/deprecated project-owned implementation, dormant registration,
  disabled feature flag, exclusive obsolete test, configuration, dependency, or
  stale documentation remains.
- Every retained compatibility reader has an active declared support obligation,
  owner, fixture, and removal condition.
