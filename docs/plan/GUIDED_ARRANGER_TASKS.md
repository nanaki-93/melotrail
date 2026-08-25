# Ordered guided-arranger implementation tasks

**Status:** Active execution contract<br>
**Roadmap:** [`../../PLAN.md`](../../PLAN.md)<br>
**Phase 0 handoff:** [`GUIDED_ARRANGER_PHASE_0.md`](GUIDED_ARRANGER_PHASE_0.md)<br>
**Execution ledger:** [`GUIDED_ARRANGER_EXECUTION_LOG.md`](GUIDED_ARRANGER_EXECUTION_LOG.md)

## 1. Execution contract

These tasks decompose the parent objectives GA-001 through GA-014 into focused,
dependency-ordered commits. Complete them in the order written. Do not combine
tasks, implement a later task early, or keep a replaced production route after
its owning cutover task.

There are 76 mandatory implementation/evidence commits:

| Phase | Parent objectives | Commit tasks | Human pause |
| --- | --- | ---: | --- |
| 0 | GA-001–GA-002 | 14 | H0-01 through H0-05 |
| 1 | GA-003–GA-005 | 12 | Phase gate |
| 2 | GA-006–GA-008 | 11 | Explicit melody review gate |
| 3 | GA-009–GA-011 | 10 | H3-01 catalog audition |
| 4 | GA-012 | 10 | H4-01 real-app walkthrough |
| 5 | GA-013 | 10 | H5-01 GarageBand/listening gate |
| 6 | GA-014 | 9 | Final product decision |

Human checkpoints are not implementation tasks and never receive empty commits.
The GA task immediately after a checkpoint validates and records only evidence
the user actually supplied.

For every GA task:

1. inspect the current implementation, direct tests, and affected operational
   documentation before editing;
2. preserve project authority, immutable sources, approved melody, artifact
   lineage, and atomic publication;
3. add regression tests for the task's exact failure modes;
4. run focused checks plus `make test`, `make worker-test`, `make build`, and
   `git diff --check` before committing;
5. update the selected execution-log row in the same commit;
6. stage explicit paths only and create exactly one commit with the subject in
   the task contract;
7. stop without committing if acceptance, licensing, a fixture, or a human gate
   is unresolved.

Tests may identify themselves as `TEST_FIXTURE` or `AUTOMATED_FIXTURE`. They may
not fabricate a musician/listener identity or an approval. Large/user-owned
GarageBand assets remain ignored and must never be added with `git add -f`.

## 2. Architectural sequencing constraints

- Separate real container and payload classification from filename evidence
  before adding extension support.
- The immutable candidate/provenance model must exist before unified import
  stops the current `EXTRACTED -> CLEANED` route; otherwise a draft can still be
  treated as canonical.
- CAF extraction uses Apple's documented chunk. AIFF extraction is implemented
  only if real fixtures and the GA-002 ADR prove a stable contract.
- Transcription consumes only the canonical decoded WAV and never overwrites a
  raw/known-good MIDI candidate.
- Declared section bars and source bounds must exist before timing conformance.
- Approved-melody occurrence assembly is a route cutover, not an extra validator
  after harmony fitting or AI mutation.
- New review/editor UI is split into focused components; do not add the entire
  workflow to the existing large `WorkspaceViewModel`.
- Style packs contain stable musical IDs/data, not absolute paths or raw MIDI
  filenames. Instruments resolve through the licensed registry.
- `pad -> keys` is one explicit public-role migration. Do not retain two public
  accompaniment roles.
- Human listening gates cannot be delegated to an agent or inferred from audio
  metrics.

## 3. Phase 0 — Baseline and GarageBand feasibility

### GA-001A — Establish the Phase 0 intake contract

**Depends on:** planning baseline committed.

**Change:**

- Add `/local-fixtures/` to `.gitignore` without changing existing user paths.
- Add versioned JSON Schemas/templates for `song.json`, `capture.json`, asset
  inventory, and human review evidence.
- Add a small read-only CLI/help command that creates missing directories and
  copies templates but never overwrites an existing file.
- Keep [`GUIDED_ARRANGER_PHASE_0.md`](GUIDED_ARRANGER_PHASE_0.md) synchronized
  with the actual schema fields and commands.

**Acceptance:** template examples validate; rerunning is idempotent; no command
cleans or mutates a source; documentation paths resolve.

**Commit:** `guided-arranger: GA-001A define phase zero intake contract`

### GA-001B — Capture the current Melotrail baseline immutably

**Depends on:** GA-001A.

**Change:**

- Add a command/service that inventories an existing five-source E2E run into a
  unique `build/guided-arranger/phase0/<run-id>/` directory.
- Record relative artifact IDs, sizes, hashes, project facts, selected lineage,
  and known pending listening state.
- Never invoke cleanup or rewrite the original project/artifacts.

**Acceptance:** temp-project tests prove byte-identical sources and known-good
artifacts; repeated capture yields equivalent evidence with a distinct run ID;
missing/stale references fail explicitly.

**Commit:** `guided-arranger: GA-001B capture immutable current baseline`

### GA-001C — Validate the GarageBand musical package

**Depends on:** GA-001A.

**Change:**

- Add `make phase0-validate` and its underlying validator.
- Validate required files, manifest schema, SHA-256, audio format, sample rate,
  channels, frame counts, 102.4-second musical end, declared effects tail, and
  aligned stem spans.
- Inspect files read-only and publish reports atomically under `build/`.

**Acceptance:** synthetic valid/missing/truncated/misaligned packages are tested;
invalid input publishes no success report; creative binaries remain ignored.

**Commit:** `guided-arranger: GA-001C validate GarageBand reference package`

### H0-01 — User supplies the golden musical assets

Stop with a clean tree and print the paths/instructions from the Phase 0 guide.
The user supplies the `.band` archive, reference mix, optional premaster, three
stems, five native green melody loops, and completed capture metadata. Do not
substitute a single final WAV for the missing package.

### GA-001D — Register the validated golden baseline

**Depends on:** H0-01 and GA-001C passing.

**Change:** commit only a relative inventory, hashes, technical validation
report, GarageBand/macOS versions, and user-provided rights/provenance statement.
Keep the `.band`, WAVs, stems, and native loops local and ignored.

**Acceptance:** every evidence hash resolves to a local immutable file during
the live check; a stale/missing file blocks; no creative binary is staged.

**Commit:** `guided-arranger: GA-001D register golden baseline evidence`

### GA-001E — Package blind baseline listening comparisons

**Depends on:** GA-001B and GA-001D.

**Change:** add deterministic loudness-matched, randomized A/B packaging for the
current Melotrail result versus the GarageBand reference, including isolated
melody/keys/drum comparisons where both sides exist. Publish a concealed map and
pending score sheet bound to hashes.

**Acceptance:** same inputs/seed produce the same concealed order and manifest;
no score or preference is generated by code; sources remain untouched.

**Commit:** `guided-arranger: GA-001E add baseline listening package`

### H0-02 — User approves or rejects the musical target

The user listens and returns the completed score sheet plus an explicit decision
that the GarageBand result is or is not the target. Metrics and file existence
cannot satisfy this checkpoint.

### GA-001F — Record the human baseline decision

**Depends on:** H0-02.

**Change:** validate score-sheet hashes and required fields, then commit the
user-supplied decision and concise evidence. Do not rewrite identity, scores,
device, time, or comments.

**Acceptance:** stale/mismatched sheets fail; rejection stays rejection and
blocks GA-002A until the reference is revised and re-reviewed.

**Commit:** `guided-arranger: GA-001F record golden target decision`

### GA-002A — Generate known GarageBand round-trip MIDI seeds

**Depends on:** GA-001F accepted.

**Change:** add deterministic 4-bar/75-BPM and 8-bar/80-BPM Standard MIDI seeds,
canonical semantic-event JSON, regeneration tooling, and exact GarageBand handoff
instructions. Cover repeated notes, rests, velocities, intentional microtiming,
boundary notes, register changes, one overlap, CC64, pitch bend, and conductor
metadata.

**Acceptance:** regeneration is hash-identical; MIDI parses completely; expected
events use rational beat positions and independently validate the binary.

**Commit:** `guided-arranger: GA-002A add GarageBand roundtrip MIDI seeds`

### H0-03 — User returns GarageBand diagnostic files

The user imports the seeds without editing, creates green User Loops, creates a
4-bar blue/audio User Loop and plain Share-export AIFF, and completes the capture
manifest. The agent must not operate or simulate GarageBand.

### GA-002B — Inventory real Apple Loop containers safely

**Depends on:** H0-03.

**Change:** add a read-only spike that emits a bounds-checked AIFF/AIFF-C/CAF
chunk tree, codec/stream facts, advisory Apple metadata, signatures, and hashes.
It performs no extraction and never scans blindly for `MThd`.

**Acceptance:** real green/blue/plain controls plus truncated, spoofed, unknown-
chunk, padding, overflow, and oversized-length fixtures are covered; failure
publishes no partial file.

**Commit:** `guided-arranger: GA-002B inventory Apple Loop containers`

### H0-04 — User confirms captured origins

The agent reports actual containers and findings. The user confirms which file
was created as green, blue/audio, or Share-exported input. The bytes, not this
label, remain authoritative for production classification.

### GA-002C — Prove documented CAF MIDI recovery

**Depends on:** GA-002B and H0-04.

**Change:** implement a spike parser for Apple's documented CAF `midi` chunk,
copy one complete bounded payload, and validate the full SMF.

**Acceptance:** format 0/1, track count, PPQ, running status, controller/pitch
bend, duplicate MIDI chunk, truncation, overflow, and malformed SMF tests pass;
no payload produces an explicit result rather than an output file.

**Commit:** `guided-arranger: GA-002C prove documented CAF MIDI recovery`

### GA-002D — Decide AIFF embedded-MIDI feasibility

**Depends on:** GA-002B.

**Change:** test real green AIFF/AIFF-C fixtures and document whether a stable,
defensible note-event contract exists. Implement a spike extractor only for a
validated chunk contract; otherwise record `AUDIO_ONLY_OR_UNSUPPORTED` and the
transcription fallback. Never carve an arbitrary `MThd` occurrence.

**Acceptance:** repeated classification is deterministic; malformed/control
fixtures cannot be mistaken for valid MIDI; the conclusion is evidence-backed.

**Commit:** `guided-arranger: GA-002D decide AIFF MIDI feasibility`

### GA-002E — Compare semantic GarageBand round trips

**Depends on:** GA-002C and GA-002D.

**Change:** add a comparator for tracks, notes, rational beat timing, duration,
velocity, PPQ, tempo/meter/key, CC64, pitch bend, and unexpected extra/missing
events. Report extractor correctness separately from GarageBand preservation.

**Acceptance:** equal, PPQ-converted-equivalent, intentionally changed, missing,
extra, controller-stripped, and metadata-stripped fixtures are tested.

**Commit:** `guided-arranger: GA-002E compare GarageBand MIDI roundtrips`

### GA-002F — Register diagnostic GarageBand fixtures

**Depends on:** GA-002E.

**Change:** curate the minimum test corpus, hashes, provenance, and rights
statement. Add narrow ignore exceptions only for small binaries explicitly
approved for redistribution; otherwise register real fixtures as local live-test
requirements and retain synthetic offline controls.

**Acceptance:** a fresh clone runs all offline tests; live tests explain missing
local assets; no copyrighted GarageBand sound library is committed implicitly.

**Commit:** `guided-arranger: GA-002F register diagnostic loop fixtures`

### GA-002G — Publish the Apple Loop import ADR

**Depends on:** GA-002C through GA-002F.

**Change:** record supported containers/codecs, exact extraction contracts,
audio-only classification, metadata authority, error behavior, test fixtures,
and rejected approaches. Include the final route matrix for direct MIDI,
embedded MIDI, Apple Loop audio, and plain audio.

**Acceptance:** suffix/mislabeling never changes the route; unsupported input has
actionable behavior; the ADR contains an explicit go/no-go for production work.

**Commit:** `guided-arranger: GA-002G decide Apple Loop import routes`

### H0-05 — User accepts the import route

The user accepts or rejects the evidence-backed route, including transcription
and review for any green container that does not expose stable MIDI.

### GA-002H — Record the import-route decision

**Depends on:** H0-05.

**Change:** validate and commit the user-supplied decision bound to the ADR and
fixture hashes. An accepted route unlocks Phase 1; rejection returns to GA-002
without weakening parsing or transcription claims.

**Acceptance:** the recorded ADR/evidence is current and the execution log does
not describe an agent decision as a user decision.

**Commit:** `guided-arranger: GA-002H record Apple Loop route decision`

### Phase 0 gate

All conditions in the Phase 0 completion checklist pass. Run the full repository
validation and verify user/large binaries remain ignored before GA-003A.

## 4. Phase 1 — Safe multi-format import

### GA-003A — Version the content-inspection contract

**Depends on:** GA-002H accepted.

**Change:** extend `InputInspectionContract.kt` and its report store with actual
containers (`AIFF`, `AIFF_C`, `CAF`), payload kinds (`STANDARD_MIDI`,
`EMBEDDED_MIDI`, `AUDIO_ONLY`), bounded track summaries, advisory Apple metadata,
and a new report version. Filename suffix is evidence only.

**Acceptance:** old reports are explicitly stale/rejected; `.aif` audio and CAF
embedded MIDI are representable without absolute paths or unbounded metadata;
contract/application tests pass. Parsing is out of scope.

**Commit:** `guided-arranger: GA-003A version input inspection contract`

### GA-003B — Securely inspect AIFF, AIFF-C, and CAF

**Depends on:** GA-003A.

**Change:** extend `worker/commands/input_inspection.py` to content-sniff SMF,
RIFF/WAVE, MPEG, `FORM/AIFF`, `FORM/AIFC`, and CAF using bounded chunk tables.
Map safe errors and use real plus synthetic fixtures.

**Acceptance:** misleading/uppercase suffixes, odd padding, unknown safe chunks,
truncation, overflow, duplicate critical chunks, empty/oversized input, and
unsupported codecs are tested; no blind `MThd` scan, source write, or temp file.

**Commit:** `guided-arranger: GA-003B securely inspect Apple containers`

### GA-003C — Map inspection through the Kotlin worker boundary

**Depends on:** GA-003B.

**Change:** update `WorkerInputInspectionBoundary.kt`, worker protocol/commands,
and contract tests to consume the new report without re-inferring content from
the suffix.

**Acceptance:** bounded path-free metadata and stable error codes round-trip;
unknown/malformed worker fields are rejected; no project mutation yet.

**Commit:** `guided-arranger: GA-003C map Apple inspection worker contract`

### GA-003D — Add one canonical audio-decode command

**Depends on:** GA-003B.

**Change:** add a worker audio-decode command and Kotlin boundary for validated
WAV/MP3/AIFF/AIFF-C/CAF audio. Decode to PCM-24 WAV atomically through one path.
Keep transcription format-agnostic.

**Acceptance:** proven codecs only; sample rate/channels preserved; duration is
within one source frame and lossless PCM within one destination LSB; no resample,
channel coercion, DSP, source change, or partial output.

**Commit:** `guided-arranger: GA-003D add canonical audio decode command`

### GA-003E — Publish hash-bound decoded audio

**Depends on:** GA-003C and GA-003D.

**Change:** add application-owned decode artifact/store/service and publish
`prepared/<section>/decoded.wav` with source/configuration/decoder lineage.
Integrate `InputCleanupPlan`, automatic import, and project references.

**Acceptance:** identical source/config reuses the artifact; changes invalidate
only dependents; failure publishes neither WAV nor project reference; original
source bytes remain exact. Transcription remains out of scope.

**Commit:** `guided-arranger: GA-003E publish decoded audio artifacts`

### GA-004A — Extract only validated embedded Standard MIDI

**Depends on:** GA-003B and the GA-002 ADR.

**Change:** add a worker extraction command for the complete documented/proven
payload. CAF is required; AIFF support exists only if GA-002G permits it.

**Acceptance:** format 0/1, multiple tracks, PPQ, conductor events, CC64, pitch
bend, running status, malformed/no/multiple payload, truncation, and oversized
data tests pass; no arbitrary scanning or partial output.

**Commit:** `guided-arranger: GA-004A extract validated embedded MIDI`

### GA-004B — Preserve immutable MIDI candidate provenance

**Depends on:** GA-004A.

**Change:** introduce common direct/embedded/transcribed candidate records,
content-addressed storage, paths, and project references. Define origins
`STANDARD_MIDI`, `APPLE_LOOP_EMBEDDED_MIDI`,
`APPLE_LOOP_AUDIO_TRANSCRIPTION`, and `PLAIN_AUDIO_TRANSCRIPTION`. Preserve raw
embedded SMF separately from previews/derivatives.

**Acceptance:** source/container/payload lineage validates; retry cannot
overwrite a candidate; stale/project-escaping references fail; A/B source audio
remains available. Approval/editing are out of scope.

**Commit:** `guided-arranger: GA-004B add immutable melody candidate provenance`

### GA-004C — Require explicit melody-track selection

**Depends on:** GA-004B.

**Change:** add MIDI track summaries and an application command that selects one
melody track. Zero/one playable track can resolve safely; more than one enters
`TRACK_SELECTION_REQUIRED`. Copy selected musical events plus required conductor
metadata into a derived candidate.

**Acceptance:** no silent merge; source/raw SMF remains unchanged; selected
track identity/hash is recorded; format 0/1/multi-track tests pass.

**Commit:** `guided-arranger: GA-004C require melody track selection`

### GA-005A — Expose raw transcription evidence

**Depends on:** GA-003D.

**Change:** make `worker/commands/transcribe.py` consume only canonical decoded
WAV and return bounded per-note confidence plus engine/model/settings evidence.
Keep raw engine MIDI; allow only valid pairs/ranges and byte-identical duplicate
sanitation.

**Acceptance:** private MP3/AIFF decode is removed; no quantization, velocity
normalization, note deletion, harmony repair, or cleanup; worker tests and the
existing benchmark pass.

**Commit:** `guided-arranger: GA-005A expose raw transcription evidence`

### GA-005B — Publish immutable review-required drafts

**Depends on:** GA-004B and GA-005A.

**Change:** replace the selected responsibilities of
`TranscriptionQualityGate.kt` with a candidate publisher binding decoded-WAV
hash, engine/settings, raw MIDI, and confidence sidecar.

**Acceptance:** every audio result is `MELODY_DRAFT`; reruns are
content-addressed and never replace the last good candidate; failed runs publish
nothing; no correctness/approval claim is emitted.

**Commit:** `guided-arranger: GA-005B publish transcription drafts`

### GA-005C — Stop unified import at truthful draft states

**Depends on:** GA-003E, GA-004C, and GA-005B.

**Change:** refactor `AutomaticImportProcessors.kt`,
`ProjectApplicationService.kt`, StageRunner/read-model seams, and integration
tests. Direct/embedded input becomes an exact draft; audio becomes decoded WAV
then transcription draft; ambiguous track pauses. Remove automatic
`EXTRACTED -> CLEANED` approval semantics from the selected route.

**Acceptance:** embedded/direct bypass Basic Pitch; audio always transcribes
decoded WAV and stops at draft; no mandatory Clean MIDI run; retry is idempotent
and source immutable. Full v4 deletion waits for GA-014.

**Commit:** `guided-arranger: GA-005C stop unreviewed import at draft`

### GA-005D — Add truthful desktop Apple Loop import states

**Depends on:** GA-005C.

**Change:** extend chooser, drag/drop, retry, preview, ViewModel, and import
presentation for all supported formats through the same application route. Show
exact embedded MIDI, audio transcription required, plain AIFF, track selection,
and actionable failure states.

**Acceptance:** support `.mid/.midi/.aif/.aiff/.aifc/.caf/.wav/.wave/.mp3`;
never claim `.aif == MIDI`; source/decoded audio and exact/draft MIDI can be
previewed; UI/application tests pass. Piano-roll editing waits for Phase 2.

**Commit:** `guided-arranger: GA-005D add Apple Loop desktop import flow`

### Phase 1 gate

Run all import/worker fixtures and full validation. Verify fixture hashes and
that no route silently transcribes embedded MIDI, marks audio transcription
approved, or overwrites a known-good candidate.

## 5. Phase 2 — Declared grid and melody review

### GA-006A — Persist declared section bounds and grid authority

**Depends on:** Phase 1 gate.

**Change:** add musician-owned section BPM/meter/bar count and explicit source
start/end records to the project/domain, application commands, hashes, and
invalidation. Target tick span derives only from project authority.

**Acceptance:** bounds are finite, ordered, and within source duration; changing
tempo/meter/bars/bounds invalidates mapping and downstream approval only;
detector-derived defaults and implicit pickup/tail are absent.

**Commit:** `guided-arranger: GA-006A persist declared melody grid`

### GA-006B — Conform melody uniformly to the declared grid

**Depends on:** GA-006A.

**Change:** add a focused declared-grid mapper or rewrite the selected path in
`MidiTimeMapping.kt` and timing alignment. Map one confirmed source interval
linearly to tick zero through the exact section end.

**Acceptance:** 12.8-second/4-bar and 25.6-second/8-bar inputs at 75 BPM map
exactly; tolerance is `max(80 ms, 0.5%)`; detector results at 150/152 BPM and a
2.415-second first beat cannot influence output; pitch/count/velocity/controller
events are retained. Remove `bars - 1` and invented pickup/tail behavior from
the live fixture. No quantization or piecewise warp.

**Commit:** `guided-arranger: GA-006B conform melody to declared grid`

### GA-006C — Require review for duration mismatch

**Depends on:** GA-006B.

**Change:** add application/read-model state and compact desktop trim/downbeat
controls for a source outside the declared tolerance.

**Acceptance:** no mapped MIDI is published until bounds are explicitly
confirmed; half/double-tempo analysis is warning evidence only; pending review
survives reopen/retry; no full piano-roll work enters this task.

**Commit:** `guided-arranger: GA-006C add off-grid source review`

### GA-007A — Model semantic approved-melody identity

**Depends on:** GA-006B.

**Change:** add a semantic MIDI reader/identity and
`ApprovedMelodyRevision` storage/reference. Include pitch, onset/end, velocity,
track/channel, CC, pitch bend, program, and retained musical metadata; document
non-musical serialization differences such as end-of-track encoding.

**Acceptance:** format 0/1, overlap, duplicate pitch, multi-channel, controller,
pitch-bend, confinement, and stale-hash tests pass; every musical change is
detected.

**Commit:** `guided-arranger: GA-007A model approved melody identity`

### GA-007B — Add immutable piano-roll edit history

**Depends on:** GA-007A.

**Change:** add typed edit operations and a deterministic reducer/store for
add/delete/move/resize/split/merge/pitch/velocity/track selection/optional snap,
with undo/redo/reset and content-addressed revisions. Do not mutate controllers
without a later explicit controller operation.

**Acceptance:** replay and undo/redo are deterministic; stale-base conflicts,
invalid bounds/overlaps, reset, and non-overwrite are tested; Compose UI is out
of scope.

**Commit:** `guided-arranger: GA-007B add immutable melody edits`

### GA-007C — Approve one exact reviewed melody revision

**Depends on:** GA-007A and GA-007B.

**Change:** add the local musician approval command/service, read model, exact
lineage hashes, and downstream invalidation. Replace free-form live-test
`reviewer = user` behavior with a typed user action.

**Acceptance:** stale candidate/bounds/authority is rejected; direct/embedded
MIDI still needs acceptance; transcription cannot auto-approve; reapproval
creates a new revision; only the approved hash is exposed to arrangement.

**Commit:** `guided-arranger: GA-007C approve reviewed melody revisions`

### GA-007D — Preserve approved events through occurrences

**Depends on:** GA-007C.

**Change:** cut the selected occurrence/source-song route over to approved MIDI
in `SourceSongApplicationService`, source-song/full-melody models, and occurrence
resolution. Bypass monophonic preparation, harmony fitting, connection note
mutation, AI fix/enhance, and humanization for approved events. Add
`ApprovedMelodyPreservationTest`.

**Acceptance:** zero changes to pitch/count/local onset/duration/velocity/
controllers; repeated sections differ only by absolute offset; preview note-off
is playback-only; harmony/scale analysis is advisory. File deletion waits for
GA-014.

**Commit:** `guided-arranger: GA-007D preserve approved song occurrences`

### GA-008A — Derive non-mutating review diagnostics

**Depends on:** GA-007A.

**Change:** add a review analyzer/read model joining confidence evidence and
flagging low confidence, micro-notes, suspicious gaps, overlaps, duration
mismatch, unexpected polyphony, and scale/chord advisories.

**Acceptance:** issue IDs/severity/location are deterministic; only malformed or
unbounded events block; analyzer output cannot contain a repaired MIDI path.

**Commit:** `guided-arranger: GA-008A add melody review diagnostics`

### GA-008B — Build the piano-roll melody editor

**Depends on:** GA-007B and GA-008A.

**Change:** add focused review/piano-roll presentation and Compose components for
selection, zoom/scroll, playhead, add/delete/drag/resize/split/merge, pitch,
velocity, snap, track selector, undo/redo, and reset. Dispatch typed domain
operations rather than editing MIDI in UI state.

**Acceptance:** screen/ViewModel/interaction tests cover core edits; stale async
callbacks cannot replace a newer revision; do not put the entire editor in the
existing monolithic ViewModel.

**Commit:** `guided-arranger: GA-008B add piano roll editor`

### GA-008C — Synchronize waveform and MIDI audition

**Depends on:** GA-003E and GA-008B.

**Change:** add cached waveform summaries, preview resolution, one section
transport/clock, source/candidate/render A/B, looping, playhead, and measured or
configured latency compensation.

**Acceptance:** waveform and piano roll share declared bounds; switching sources
and cancellation leave one playback owner; project switch cannot receive stale
callbacks; no unrestricted time-stretch/DSP is introduced.

**Commit:** `guided-arranger: GA-008C synchronize melody audition`

### GA-008D — Gate melody approval in the desktop

**Depends on:** GA-006C, GA-007C, GA-008A, and GA-008C.

**Change:** expose warnings, provenance, track/bounds blockers, acceptance, and
approved hash in the review page. Remove test shortcuts that fabricate a user
review.

**Acceptance:** transcription requires an explicit click; direct/embedded MIDI
requires preview/accept; unresolved track/bounds/malformed input disables the
action; state survives reopen; only the approved hash unlocks structure.

**Commit:** `guided-arranger: GA-008D gate explicit melody approval`

### Phase 2 gate

Run the semantic preservation suite and full validation. A diagnostic failure
returns to user editing; it never revives Harmony Fit, AI Fix, or another
automatic repair path.

## 6. Phase 3 — One producer-authored lo-fi style pack

### GA-009A — Add the strict StylePack contract

**Depends on:** Phase 2 gate.

**Change:** add versioned style-pack domain/schema/loader foundations for pack
ID/version/license, tempo/meter compatibility, coupled scenes, 2/4/8-bar
phrases, A/B/C variations, whole LOW/MEDIUM/HIGH variants, fills/dropouts,
instrument IDs, mix preset, and `productionReady` state.

**Acceptance:** reject duplicate IDs, missing variants, bad subdivisions,
out-of-range events, unresolved instrument/license IDs, absolute paths, free
prose plans, and raw MIDI path references. Rendering/data are out of scope.

**Commit:** `guided-arranger: GA-009A add StylePack contract`

### GA-009B — Author `lofi-core@1` scene manifests

**Depends on:** GA-009A.

**Change:** add resource manifests for intro, verse, chorus, bridge, and outro,
with coupled drums/keys, 2/4/8-bar phrases, A/B/C, complete energy variants, and
`NONE`/light/medium/section fills. Bass/strings are absent.

**Acceptance:** the loader sees a complete section/variation/energy matrix;
energy points to authored IDs and never a delete/multiplier algorithm;
`productionReady=false` until H3-01.

**Commit:** `guided-arranger: GA-009B author lofi core scenes`

### GA-009C — Select coupled scenes deterministically

**Depends on:** GA-009B.

**Change:** add `ArrangementPlan`/selector inputs for occurrence, exact scene,
variation, energy, fill, pack version, and seed. Keep drums/keys/dropout/fill/mix
coherent as one choice.

**Acceptance:** same inputs yield identical plans; incompatible section/tempo/
meter fails; no density field or independent random pattern selection; explicit
user choice wins. Qwen/UI are out of scope.

**Commit:** `guided-arranger: GA-009C select coupled scenes`

### GA-010A — Render complete authored drum phrases

**Depends on:** GA-009C.

**Change:** add a selected-path lo-fi drum phrase renderer. Render the complete
chosen LOW/MEDIUM/HIGH event list, including bounded authored swing and ghost
velocities, across exact 2/4/8-bar spans.

**Acceptance:** required kick/snare anchors remain; no density decimation,
same-pitch overlap, or section spill; same pack/choice/seed yields identical
MIDI. Fill behavior remains out of scope.

**Commit:** `guided-arranger: GA-010A render complete drum phrases`

### GA-010B — Render explicit bounded fills

**Depends on:** GA-010A.

**Change:** realize `NONE`, light, medium, and section-specific fill choices only
inside their declared final-beat/final-bar window.

**Acceptance:** `NONE` adds no event; collision handling preserves required
anchors and the next section downbeat; final section may use none; the golden
structure is not forced to have seven fills.

**Commit:** `guided-arranger: GA-010B add explicit drum fills`

### GA-011A — Voice every authoritative chord window

**Depends on:** GA-009C.

**Change:** add an electric-piano voicing engine consuming typed project harmony.
Respect inversions, range, common tones, defining chord tones, and explicit
style-pack extensions.

**Acceptance:** 100% non-rest chord coverage; without bass the root remains;
`G/B` has B lowest and G present; no unintended non-common sustain; maximum
single-voice movement <= 12 semitones and target median <= 7. No inferred chords
or density silence.

**Commit:** `guided-arranger: GA-011A voice authoritative chords`

### GA-011B — Realize authored keys rhythms and pedal

**Depends on:** GA-009B and GA-011A.

**Change:** render each complete scene rhythm/voicing variant with deterministic
timing, velocity, note release, and CC64 rules.

**Acceptance:** every A/B/C-energy combination covers all non-rest chords;
rhythm never drops a chord; pedal cannot bleed into incompatible harmony;
section bounds are exact. No AI humanization or arbitrary extensions.

**Commit:** `guided-arranger: GA-011B render lofi keys rhythms`

### GA-011C — Add the distinct electric-piano role

**Depends on:** GA-011B.

**Change:** migrate the selected public role from `pad` to `keys`, update
instrument registry/resolver, render/stem mappings, local inventory, and tests.
Audition the existing licensed FM-piano candidate or add a properly licensed
Rhodes-style local asset with provenance.

**Acceptance:** melody and keys resolve different stable instrument/SFZ IDs;
generated role/stem is `keys`; license checks pass; no GarageBand samples are
committed and no GM program number is treated as sound-quality evidence.

**Commit:** `guided-arranger: GA-011C add electric piano role`

### GA-011D — Render catalog and golden-song auditions

**Depends on:** GA-010B, GA-011B, and GA-011C.

**Change:** add a reusable application service/Make target that renders every
isolated pattern, coupled scene, and the C-major golden song into an ignored
hash-manifested listening bundle.

**Acceptance:** structural validators pass; isolated melody/drums/keys and scene
mixes render; loudness-matched randomized bundle is reproducible; product recipe
is not hidden in JUnit; no source is touched and no pass is generated.

**Commit:** `guided-arranger: GA-011D add style pack auditions`

### H3-01 — User auditions the complete style pack

The user scores every scene/pattern for groove, electric piano, transition, and
balance. Failed assets remain non-production and return to their owning task as
a new version/remediation commit; agents cannot lower gates or approve them.

### GA-011E — Record the human catalog decision

**Depends on:** H3-01 accepted.

**Change:** validate the supplied review against exact pack/instrument/artifact
hashes, record it, and switch only reviewed manifest entries to
`productionReady=true`.

**Acceptance:** stale/missing ratings fail; every production entry has current
human evidence; an agent-authored score is rejected.

**Commit:** `guided-arranger: GA-011E approve lofi core auditions`

### Phase 3 gate

Run all pattern, harmony, instrument, rendering, and full validation. Phase 4
cannot start without current H3-01 evidence.

## 7. Phase 4 — Application use case and guided arranger UI

### GA-012A — Add the guided-arrangement application contract

**Depends on:** Phase 3 gate.

**Change:** add one public request/result contract requiring authoritative
project revision, approved melody hashes, style-pack version, explicit
per-occurrence choices, instrument/mix references, and seed.

**Acceptance:** stale/unapproved melody, unresolved pack/instrument, invalid
structure/harmony, and non-production scene are rejected before work starts;
paths remain project-relative and bounded.

**Commit:** `guided-arranger: GA-012A add arrangement application contract`

### GA-012B — Move the golden song into a versioned manifest

**Depends on:** GA-012A.

**Change:** add the C-major 75-BPM structure, chords, section bars, approved
fixture references, style-pack choices, and seed as a validated manifest
consumed through the application contract.

**Acceptance:** no product decision remains hardcoded only in the live JUnit
test; manifest errors identify exact fields; no generated/large audio enters Git.

**Commit:** `guided-arranger: GA-012B add golden song manifest`

### GA-012C — Persist immutable arrangement choices

**Depends on:** GA-012A.

**Change:** add content-addressed per-occurrence revisions for scene, A/B/C,
energy, fill, keys rhythm/voicing, layer activity, gain, and pan, with exact
downstream invalidation.

**Acceptance:** changes create new revisions, preserve the prior plan, and
invalidate only affected arrangement/render/export artifacts; save/reopen is
deterministic.

**Commit:** `guided-arranger: GA-012C persist arrangement choices`

### GA-012D — Generate only the selected section and layer

**Depends on:** GA-012C.

**Change:** add selective generation/cancellation using the same production
renderers. Publish a new candidate for the selected occurrence/layer and merge
references atomically after validation.

**Acceptance:** every unaffected artifact remains byte/hash-identical; cancel or
failure keeps the previous valid plan; stale work cannot publish after a newer
choice.

**Commit:** `guided-arranger: GA-012D add selective arrangement generation`

### GA-012E — Split the five workspace pages

**Depends on:** GA-012A.

**Change:** add characterization tests, then extract Song setup, Melody, Chords
& structure, Arrange, and Mix & export page composables from the monolithic
router without changing behavior.

**Acceptance:** navigation, lifecycle, errors, project switching, and existing
states remain covered; the commit is structural and contains no new arrangement
feature.

**Commit:** `guided-arranger: GA-012E split workspace workflow pages`

### GA-012F — Split workflow state and controllers

**Depends on:** GA-012E.

**Change:** extract step-specific presentation/state/controllers from
`WorkspaceViewModel` while retaining one project owner and one operation/
playback lifecycle.

**Acceptance:** project changes cancel stale work; state has one source of truth;
characterization tests remain green; no duplicate orchestration service is
introduced.

**Commit:** `guided-arranger: GA-012F split workspace workflow state`

### GA-012G — Build the section/scene arrangement editor

**Depends on:** GA-012C and GA-012F.

**Change:** add section blocks and controls for scene, variation, energy, fill,
keys rhythm/voicing, mute/solo, gain, and pan using persisted revisions.

**Acceptance:** ready/dirty/stale/blocked/save/reopen states are tested; invalid
combinations are disabled with explanations; controls never modify approved
melody or project harmony.

**Commit:** `guided-arranger: GA-012G add guided arrangement editor`

### GA-012H — Add one-owner section audition workflow

**Depends on:** GA-012D and GA-012G.

**Change:** add section loop audition, selected-layer solo, safe switching, and
selective-regeneration progress/cancel UI.

**Acceptance:** one playback owner; stale callbacks and concurrent mutation are
blocked; audition resolves the exact current hashes; stopping/project switch
releases resources.

**Commit:** `guided-arranger: GA-012H add section audition workflow`

### GA-012I — Prove the golden application workflow

**Depends on:** GA-012B through GA-012H.

**Change:** replace the JUnit-owned recipe with an application-level deterministic
E2E from an explicitly reviewed fixture through arrangement and reopen. Test
provenance is `TEST_FIXTURE`, never `user`.

**Acceptance:** the live test invokes the public use case; structure/chords/
patterns are not recreated in test code; no live transcription can cross review
without a real action.

**Commit:** `guided-arranger: GA-012I prove golden arrangement workflow`

### H4-01 — User completes the real-app workflow

The agent publishes a pending checklist and exact local artifact paths. The user
performs import -> review -> structure -> arrange -> audition -> save -> reopen
-> rebuild in the actual desktop app and returns accept/reject evidence. A
rejection returns to the owning Phase 4 task.

### GA-012J — Record the guided-workflow decision

**Depends on:** H4-01 accepted.

**Change:** validate and commit the user-supplied walkthrough record against the
project/application build and artifact hashes.

**Acceptance:** no agent-synthesized reviewer/time/device/result; stale evidence
fails; accepted evidence unlocks Phase 5.

**Commit:** `guided-arranger: GA-012J record guided workflow acceptance`

### Phase 4 gate

Full validation passes and H4-01 is current. The user can complete the golden
song without source-code changes.

## 8. Phase 5 — Render, mix, export, and GarageBand round trip

### GA-013A — Freeze immutable render snapshots

**Depends on:** Phase 4 gate.

**Change:** add `RenderSnapshot` over project revision, approved melody, style
pack, choices, instruments, mix settings, processor versions, and all hashes.

**Acceptance:** any dependency change stales the snapshot; a snapshot is
immutable, project-confined, and sufficient to reproduce its render inputs;
partial freeze is impossible.

**Commit:** `guided-arranger: GA-013A freeze render snapshots`

### GA-013B — Render aligned core stems

**Depends on:** GA-013A.

**Change:** render distinct melody, electric-piano, and drum stems from one
snapshot with a shared nominal start/frame span and documented bounded tails.

**Acceptance:** first-downbeat alignment <= one MIDI tick/audio frame; nominal
frame counts match; files are finite/non-silent; different instrument IDs are
verified; failure publishes no partial stem set.

**Commit:** `guided-arranger: GA-013B render aligned core stems`

### GA-013C — Add an editable restrained lo-fi mix

**Depends on:** GA-013B.

**Change:** define the bounded mix contract and a documented restrained lo-fi
chain. Keep per-role gain, pan, mute, and solo explicit; reuse proven mixer/DSP
instead of an unrestricted polish rewrite.

**Acceptance:** true peak <= -1 dBTP, finite audio, default active-window melody
clearance >= +3 dB, no unplanned >500 ms silence, and stable metrics for the
same snapshot. Metrics do not claim musical approval.

**Commit:** `guided-arranger: GA-013C add editable lofi mix`

### GA-013D — Add guided mix controls

**Depends on:** GA-013C and GA-012F.

**Change:** add mix-page gain/pan/mute/solo, dirty/stale state, preview,
cancellation, and snapshot rebuild controls.

**Acceptance:** preview identifies the exact snapshot; file existence cannot
imply current/approved output; rapid edits and project switching cannot publish
stale mixes.

**Commit:** `guided-arranger: GA-013D add guided mix controls`

### GA-013E — Export an atomic arrangement bundle

**Depends on:** GA-013C.

**Change:** export approved melody MIDI, arrangement MIDI, role stems, stereo
WAV, and a complete hash/provenance manifest through atomic staging.

**Acceptance:** bundle files all bind to one snapshot; collision/failure leaves
the previous export; output paths are confined; MIDI and WAV validate on
re-read; debug intermediates remain optional and inspectable.

**Commit:** `guided-arranger: GA-013E export arrangement bundles`

### GA-013F — Add GarageBand round-trip evidence

**Depends on:** GA-013E.

**Change:** validate MIDI conductor metadata, section markers, event bounds,
stem alignment, sample format, and manifest; document the exact GarageBand
re-import checklist.

**Acceptance:** automated parsing proves file integrity/alignment but does not
claim GarageBand behavior; the manual checklist records version and results.

**Commit:** `guided-arranger: GA-013F add GarageBand roundtrip evidence`

### GA-013G — Add dimensional listening review

**Depends on:** GA-013C.

**Change:** replace binary listening evidence with hash-bound 1–5 ratings for
melody fidelity, timing, harmony, drums, keys, transitions, balance, and overall
usefulness. Migrate every current consumer in the same task.

**Acceptance:** pending/accepted/rejected are distinct; stale comparisons cannot
count; tests never fabricate human ratings; previous evidence is migrated or
explicitly rejected.

**Commit:** `guided-arranger: GA-013G add dimensional listening review`

### GA-013H — Add non-destructive live review targets

**Depends on:** GA-013F and GA-013G.

**Change:** add `make transcription-benchmark`, `make live-apple-loop-e2e`, and
`make listening-benchmark`, using unique run directories and explicit local
fixture readiness.

**Acceptance:** targets never clean input or accepted output; ordinary CI stays
offline/deterministic; missing renderer/model/GarageBand fixtures report exact
setup; live transcription stops at review.

**Commit:** `guided-arranger: GA-013H add live review targets`

### GA-013I — Prove deterministic export workflow

**Depends on:** GA-013E and GA-013H.

**Change:** add an offline E2E from an explicitly pre-approved fixture through
frozen snapshot, aligned stems, mix, and export bundle. Add live-test pending
state coverage separately.

**Acceptance:** same inputs produce identical semantic MIDI and stable audio
metrics; bundle manifests resolve; uncertain live input cannot auto-approve.

**Commit:** `guided-arranger: GA-013I prove deterministic export workflow`

### H5-01 — User completes GarageBand and listening gates

The user imports exported MIDI/stems into GarageBand and confirms structure and
alignment, then completes the ten-song randomized listening benchmark. Required
thresholds are exactly those in root `PLAN.md`; a rejection returns to the
owning task without weakening a metric.

### GA-013J — Record the MVP listening/product decision

**Depends on:** H5-01 accepted.

**Change:** validate review freshness/hashes, compute the published threshold
summary without changing ratings, and commit the user-supplied round-trip and
listening decision.

**Acceptance:** at least 8/10 useful drafts, required medians/minima, preferred
route, and under-ten-minute workflow pass; otherwise do not make an acceptance
commit or start deletion.

**Commit:** `guided-arranger: GA-013J record MVP listening decision`

### Phase 5 gate

GA-013J is current and accepted, all export checks pass, and the user has
confirmed GarageBand alignment. Only then may Phase 6 delete the old route.

## 9. Phase 6 — Schema cutover and legacy deletion

### GA-014A — Add the schema-v5 boundary

**Depends on:** Phase 5 gate.

**Change:** define schema v5 for the guided stages and explicit v4 handling.
Prefer actionable rejection unless a fully atomic, tested migration is justified.

**Acceptance:** no silent project rewrite; malformed/partial migration leaves the
original project untouched; error copy explains backup and next action.

**Commit:** `guided-arranger: GA-014A add schema v5 boundary`

### GA-014B — Cut over the guided stage graph

**Depends on:** GA-014A.

**Change:** switch runtime orchestration, StageRunner/invalidation, read models,
and desktop progression to `PROJECT_SETUP -> SOURCE_IMPORT -> MELODY_DRAFT ->
MELODY_REVIEWED -> STRUCTURE_HARMONY -> ARRANGEMENT -> RENDER_MIX -> EXPORT`.

**Acceptance:** only the new route can create new work; downstream consumers
resolve one approved melody and one render snapshot; no dual-route selector.

**Commit:** `guided-arranger: GA-014B cut over guided stage graph`

### GA-014C — Remove automatic timing warp

**Depends on:** GA-014B and GA-006 replacement coverage.

**Change:** delete production entry points, state, tests, and UI exclusive to
automatic declared-loop beat/downbeat warping and invented pickup/tail behavior.

**Acceptance:** `rg` and compiler evidence show no runtime consumer; declared
grid/off-grid review regressions remain green; historical user artifacts on disk
are not deleted.

**Commit:** `guided-arranger: GA-014C remove automatic timing warp`

### GA-014D — Remove melody mutation stages

**Depends on:** GA-014B and approved-melody preservation coverage.

**Change:** delete production entry points/state/exclusive tests for MIDI AI Fix,
harmony fitting, technical correction, melody enhancement, and selected-route
connection mutations. Preserve only non-mutating diagnostics that have current
consumers.

**Acceptance:** approved semantic MIDI remains exact; no UI/action/stage can
invoke a removed mutation; immutable historical files are left untouched.

**Commit:** `guided-arranger: GA-014D remove melody mutation stages`

### GA-014E — Remove legacy arrangement branches

**Depends on:** GA-014B and Phase 3 replacement coverage.

**Change:** delete legacy Qwen planners from the production arrangement path,
density-decimated drums/pad, mandatory bass/strings, old arrangement selection,
and the superseded `pad` role.

**Acceptance:** only StylePack scene realization generates roles; no bass/
strings unless a future pack reintroduces them through a new approved contract;
no orphan registry/stem/UI fields.

**Commit:** `guided-arranger: GA-014E remove legacy arrangement branches`

### GA-014F — Remove legacy post-arrangement stages

**Depends on:** GA-014B and Phase 5 replacement coverage.

**Change:** delete Ensemble Cohesion, full-song enhancement, seeded
humanization, their stale UI/actions/gates, and exclusive tests. Retain proven
bounded mix/export DSP through the new snapshot route.

**Acceptance:** no hidden melody/arrangement rewrite remains between arrangement
and render; mixer/export regressions stay green.

**Commit:** `guided-arranger: GA-014F remove post arrangement rewrites`

### GA-014G — Simplify quality and release gates

**Depends on:** GA-013G and GA-014B.

**Change:** remove old “quality-certified” composition gating and commercial/
YouTube coupling from ordinary creation while retaining objective export
integrity, source/license provenance, and real human review evidence.

**Acceptance:** technical gates never imply musical quality, publication, rights
clearance, or monetization; current dimensional reviews remain hash-bound.

**Commit:** `guided-arranger: GA-014G simplify quality gates`

### GA-014H — Purge schema-v4 compatibility code

**Depends on:** GA-014C through GA-014G.

**Change:** use repository-wide reference/compiler evidence to remove unreachable
v4 stage enums, DTO fields, commands, readers, tests, and resources. Update the
compatibility inventory in the same commit.

**Acceptance:** no current consumer/reference remains; a v4 project receives the
GA-014A behavior; never run `git clean`, broad `rm`, reset, checkout, or delete a
project/data directory.

**Commit:** `guided-arranger: GA-014H remove schema v4 compatibility code`

### GA-014I — Complete documentation and one-route audit

**Depends on:** GA-014H.

**Change:** update root README, import/workflow/troubleshooting/release docs,
plan index, function inventory, Make help, and screenshots where required.
Remove the old executable QP task/prompt instructions after all references move
to historical evidence or Git history.

**Acceptance:** documentation coverage and local link/path audit pass; searches
find no contradictory active roadmap or removed-stage instructions; all standard
and live-readiness checks are documented accurately.

**Commit:** `guided-arranger: GA-014I complete guided arranger cutover`

### Final mandatory gate

Run all focused suites, `make test`, `make worker-test`, `make build`,
documentation coverage, link/path audit, removed-symbol searches, and
`git diff --check`. Verify the working tree is clean, map all 76 task commits,
and report every human evidence record and remaining limitation. Do not claim
publishability or monetization.

Phase 7 is deliberately outside this mandatory execution sequence. Its frozen
tasks and authorization gate are in
[`GUIDED_ARRANGER_OPTIONAL_AI_TASKS.md`](GUIDED_ARRANGER_OPTIONAL_AI_TASKS.md).
