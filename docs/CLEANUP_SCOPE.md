# MIDI Core cleanup scope

Status: approved disposition; runtime/data deletion occurs only in the ordered
implementation tasks after target replacements pass. The user-requested
documentation-only cleanup below is completed separately.

Authority: what is kept, extracted, or deleted

2026-09-05 exception: retain the nine PNGs under `docs/pictures/UI` as the
user-requested mockup references for root Plan 7.7 and its future video design.
They are design-only, never runtime assets or normal test goldens. This
exception supersedes older blanket screenshot/media deletion wording in this
document and MC-051/057/058; it does not preserve any legacy video/audio code,
image-reader test, theme measurement owner, other media, or user project.

## 1. Cleanup decision

The audio-era product will not be maintained. Its quality was insufficient and
its complexity obstructs the MIDI companion. Old audio projects do not require
migration.

Cleanup is not a compatibility exercise. Useful behavior is extracted behind
the target architecture, callers are switched, replacement tests pass, and the
old owner is deleted. Git history is the archive.

## 2. Data disposition

The implementation task must resolve exact targets before deletion.

Approved repository-owned deletion scope includes:

- old generated audio project roots beneath the repository;
- bundled demonstration audio/video used only by the rejected product;
- generated WAV/MP3/stem/render/mix/master/release artifacts;
- old worker caches and environments; and
- obsolete visual fixtures once their UI tests are removed or replaced.

Never recursively delete a workspace root, home directory, unresolved variable,
or user-selected external project directory. The cleanup task must print or
record the resolved repository-relative targets before deleting them.

No automatic audio-project migration is implemented. Opening an old project in
the new application returns an unsupported-project message.

## 3. Keep and strengthen

Keep behavior that fits the MIDI Core contract:

- Kotlin/JVM and Compose Desktop build structure;
- project-root confinement and atomic JSON writes;
- artifact hashing, stable IDs, lineage, and stale-result rejection;
- Standard MIDI parsing/writing facts proven by characterization tests;
- project key, chord-symbol parsing, chord realization, and harmony timelines;
- stable section occurrence identity;
- protected melody anchors and immutable-candidate principles;
- deterministic seeds and candidate validation;
- curated bass, chord-rhythm, drum-groove, and fill definitions;
- useful voice-leading, register, collision, density, and phrase-boundary rules;
- focused, reusable Compose controls and accessibility semantics; and
- known-good legal/test-owned MIDI fixtures.

Keeping behavior does not require keeping its current package, oversized
service, schema, page, or audio-era name.

## 4. Refactor or rename

The following capabilities have value but need a new owner:

- scattered `javax.sound.midi` access -> one MIDI adapter and semantic model;
- schema-v4 project storage -> MIDI-only project kernel and new schema;
- pad voicing/rhythm generation -> chord/keys accompaniment;
- current bass generation -> authority-only bass role engine;
- current drum generation -> complete curated groove variants and bass context;
- arrangement acceptance state -> generic per-role/per-occurrence candidate
  review;
- canonical harmony/occurrence services -> explicit exact authority timeline;
- artifact references/hashes -> source, candidate, report, and export records;
- current workspace state -> focused application state for six destinations;
- current preview concepts -> MIDI sequencer/output audition only; and
- current export naming/atomic patterns -> MIDI package export.

Extraction tasks must first add characterization tests for behavior they intend
to preserve.

## 5. Delete after replacement

### 5.1 Python and process boundary

Delete:

- the complete `worker/` tree;
- Python requirements, worker environments, caches, commands, and tests;
- Basic Pitch, transcription, source-separation, analysis, and repair handlers;
- worker HTTP contracts, health checks, queues, clients, job services, and
  configuration;
- Kotlin worker adapters and retry/error models used only by that boundary;
- OkHttp/Jackson dependencies if no target feature still proves a need; and
- Make targets and documentation for worker setup or testing.

The target build must not invoke Python, including for documentation coverage.

### 5.2 Audio and DSP

Delete:

- audio import/container inspection and decode paths;
- WAV/MP3/CAF/AIFF handling owned by Melotrail production;
- transcription and audio-to-MIDI preparation;
- denoise, cleanup, normalization, loudness, EQ, compression, saturation,
  reverb, delay, texture, and other DSP;
- renderer processes, SFZ/sampler contracts, sound libraries, and validation;
- stem, mix, master, codec preview, and audio export services;
- audio-stage artifacts, reports, settings, and project fields; and
- audio-specific tests, fixtures, docs, tools, and dependencies.

MIDI audition must not preserve an audio renderer by another name.

### 5.3 Production and publishing

Delete:

- release packaging and selected-master logic;
- commercial provenance and platform-policy review;
- YouTube readiness, video preview, soundtrack, and publishing flows;
- copyright/monetization UI and evidence not required by the MIDI manifest;
- licensing infrastructure used only to validate bundled sounds/models; and
- production/release pages, tests, documentation, and assets.

The small MIDI manifest retains technical input/output provenance only.

### 5.4 Legacy musical pipeline

Delete after the target engines replace needed behavior:

- raw/clean/normalized/transposed/corrected/AI-fixed/enhanced/MIDI-feel audio-era
  stage chains;
- analyze/structure/arrange/cohesion/critic/full-song-enhance/humanize/render/
  mix/master/export orchestration graphs;
- mandatory clean/normalize/transposition gates that do not belong to source
  MIDI import;
- unrestricted AI mutation paths;
- global cohesion or final-polish rewrites;
- duplicate harmony, timing, or duration inference;
- schema-v4 readers/writers and compatibility branches; and
- old application services once their last target behavior is extracted.

### 5.5 UI

Delete:

- Mix/Master pages;
- sound library/browser management;
- video preview;
- release/publishing/commercial pages;
- worker, transcription, renderer, sound-path, and model-runtime settings;
- old dashboard cards and navigation for removed stages;
- audio waveform/render/codec preview controls;
- obsolete intents, state fields, tests, screenshots, and theme measurements; and
- oversized router/view-model branches after the focused pages own their state.

Keep Compose Desktop and reusable controls. Do not keep hidden legacy pages.

The checked-in `docs/pictures/UI` images are retained design references, not
target fixtures. UI-000–UI-019 refines the MC-048B–MC-048H MIDI-only visuals and
adds real deterministic image comparison before MC-048I's final observations.
MC-051 removes legacy executable image readers and measurements and only those
obsolete images outside the protected reference set after consumer checks.
Retaining the design inputs does not authorize retaining their old runtime.

MC-048I also removes active documentation claims that the superseded
dropdown-first scope/feel/generate and play/accept/continue journey is the
current workflow. Historical task evidence may remain in the execution log;
it is not a current UI contract or direct reader.

### 5.6 Documentation and planning

Completed by explicit user request on 2026-09-05, before the UI implementation:

- `docs/MIDI_IMPORT_PROCESS.md` and `docs/TRACK_PROCESS_WORKFLOW.md`: obsolete
  schema-v4 audio/transcription workflows. Removed their documentation test,
  the uncalled `ImportHelpLinks` component/tags, its already-ignored UI test,
  and the root README link. Current MIDI contracts and pages remain.
- `docs/COMMERCIAL_PROVENANCE.md`: obsolete release/policy record. Removed its
  test-only `YoutubePolicyDocumentation.kt` reader and that reader's test;
  no production caller existed. Other commercial runtime/tests remain MC-056.
- `docs/COMPATIBILITY_READERS.md`: obsolete compatibility inventory with no
  executable reader; it described unsupported audio-era contracts.
- `docs/SPRING_API_RETIREMENT.md`: historical record, removed with its
  document-content assertion. The separate test preventing Spring server/build
  reintroduction remains.

All five documents and removed helpers were tracked and unchanged before this
cleanup, so Git history preserves them. No project, MIDI, screenshot, DAW
evidence, or other user data was deleted. Historical REST store names
`data/projects/`, `data/audio/`, and `data/config/server-config.json` are only
inventory context here: this documentation cleanup does not touch them,
auto-import them, or approve deleting them without the exact-target checks.

This is a narrow documentation-and-exclusive-reader retirement, not completion
of MC-051/056/058 or permission to start broader runtime/data cleanup. Preserve
the active core/UI plans, prompts, ledgers, manual rubrics and recorded evidence.
`DocumentationIntegrityTest` guards local links, retained planning evidence,
and absence of the retired guides/readers.

Remaining documentation deletion must occur with its executable/build owner:

- current function inventory/checker when its Python/build owner is removed;
- the worker README with its remaining worker/tool owners in MC-054;
- old UI screenshots outside the retained `docs/pictures/UI` design set after
  replacement fixtures exist;
- any newly found obsolete audio troubleshooting/release acceptance material;
  and
- any remaining obsolete link or compatibility reader.

Only the MIDI Core documentation indexed by `docs/README.md` remains at final
completion.

## 6. Dependency cleanup

After source deletion:

- remove unused Gradle dependencies and repositories;
- remove Python and worker variables/targets from the Makefile;
- remove obsolete environment variables and configuration files;
- remove empty resources and packages;
- remove ignored-path entries that existed only for deleted outputs;
- ensure build/check tasks use JVM tooling only; and
- regenerate or replace any source inventory with a non-Python target only if it
  still provides proportional value.

## 7. Required deletion evidence

The final cleanup gate records:

- repository status before deletion;
- exact resolved data/artifact targets;
- source/package and dependency removals;
- searches for worker/audio/render/mix/master/video/release identifiers;
- searches for `.py`, Python requirements, and worker commands;
- build and test results from a clean checkout; and
- confirmation that the six focused UI destinations still work.

Search hits are reviewed rather than blindly ignored. A historical word in this
cleanup document is allowed; a runtime dependency or active product claim is
not.
