# Melotrail guided lo-fi arranger plan

**Status:** Proposed product and implementation roadmap<br>
**Last updated:** 2026-08-26<br>
**Target:** macOS desktop, one high-quality lo-fi style pack, editable and deterministic

## 1. Plan authority

This file is the active roadmap for Melotrail's next product cycle. It
supersedes the release-quality/YouTube-oriented schema-v4 roadmap in
[`docs/plan/PLAN.md`](docs/plan/PLAN.md). The completed QP task documents remain
historical implementation evidence until the documentation cutover in GA-014;
they are not a second active product plan.

The implementation companions are:

- [`GUIDED_ARRANGER_PHASE_0.md`](docs/plan/GUIDED_ARRANGER_PHASE_0.md) — exact
  GarageBand assets, technical round trip, and user/agent checkpoints;
- [`GUIDED_ARRANGER_TASKS.md`](docs/plan/GUIDED_ARRANGER_TASKS.md) — 76 ordered,
  commit-sized mandatory tasks;
- [`GUIDED_ARRANGER_EXECUTION_LOG.md`](docs/plan/GUIDED_ARRANGER_EXECUTION_LOG.md)
  — task, commit, check, and human-evidence ledger;
- [`EXECUTE_GUIDED_ARRANGER_TASKS_PROMPT.md`](docs/plan/EXECUTE_GUIDED_ARRANGER_TASKS_PROMPT.md)
  — serial implementation/validation/commit prompt;
- [`GUIDED_ARRANGER_OPTIONAL_AI_TASKS.md`](docs/plan/GUIDED_ARRANGER_OPTIONAL_AI_TASKS.md)
  — frozen Phase 7 work requiring separate authorization.

No phase may claim that technical validation alone makes a song publishable.
Release readiness, rights clearance, audience response, and monetization are
outside the MVP contract and require later human evidence.

## 2. Product decision

Build Melotrail as a **guided, deterministic lo-fi arranger with a small
section/pattern editor**.

The musician provides and approves the melody, tempo, meter, key, section bars,
song structure, and chord progression. Melotrail adds a producer-authored drum
and electric-piano arrangement, makes every choice visible and editable, then
exports MIDI, stems, and a stereo mix.

This is deliberately:

- smaller than GarageBand or a general-purpose DAW;
- more controllable than an autonomous song generator;
- useful for moving a GarageBand melody into a coherent arrangement quickly;
- based on curated musical material before any generative arrangement AI.

The first success criterion is a genuinely useful arrangement draft, not an
automatically publishable master.

## 3. Why the current direction changes

The current pipeline's poor output is caused primarily by musical decisions,
not by final mastering:

| Current failure | Audible result | New rule |
| --- | --- | --- |
| Detected tempos near 150/152 BPM can influence a 75 BPM project, and the live test invents a one-beat pickup/three-beat tail | Sections feel displaced or rushed | Project tempo, bars, meter, and explicit trim bounds own the grid |
| AI fix, harmony fitting, enhancement, and cohesion can mutate a valid melody | Changed notes, long gaps, and disconnected phrases | An approved melody is immutable throughout arrangement |
| Accompaniment density can omit chord windows | Missing harmony and weak transitions | Every authoritative chord is voiced unless the musician explicitly inserts a rest |
| Drum density deletes steps from one pattern and the test forces fills at every boundary | Broken groove and repetitive transitions | Select complete authored variants; fills are explicit choices, including `NONE` |
| Melody and accompaniment can render with the same piano timbre | Roles blur instead of forming an ensemble | Use distinct melody and Rhodes-style electric-piano sounds |
| Mechanical critics can pass while listening review is pending | False confidence in a bad render | Automated gates prove integrity; human listening proves musical usefulness |

The project already has valuable foundations: typed project authority,
immutable/hash-bound artifacts, deterministic execution, source-song assembly,
instrument registration, SFZ rendering, mixing, and safe export. The plan keeps
those foundations and simplifies the musical path around them.

## 4. Product contract and invariants

### The musician owns

- project tempo, meter, key, and scale;
- each section's type, bar count, source, and trim/downbeat bounds;
- song structure and all chord events;
- acceptance or correction of imported/transcribed MIDI;
- arrangement scene, variation, fill, energy, mute, and mix choices.

### Melotrail owns

- safe source inspection, preservation, decoding, and provenance;
- exact placement on the declared project grid;
- synchronized source/candidate/render preview;
- deterministic realization of curated patterns and voicings;
- non-destructive revisions, invalidation, rendering, and export.

### Hard musical invariants

1. The project key, tempo, meter, section bars, structure, and chord progression
   are authoritative.
2. Apple Loop metadata and detected tempo/key are advisory evidence only.
3. No import, AI, correction, enhancement, arrangement, cohesion, or
   humanization step may silently replace project authority.
4. A transcription is a draft, never an automatically approved melody.
5. Once approved, melody pitches, onsets, durations, velocities, controllers,
   and note count do not change during arrangement.
6. A new transcription or edit creates a new revision and never overwrites an
   approved candidate.
7. Every chord window is represented by the electric piano unless it contains
   an explicit rest.
8. Arrangement is reproducible from the project revision, style-pack version,
   pattern IDs, and seed.
9. Original sources and useful intermediate MIDI/audio remain available in
   debug mode.

The confirmed first-release input contract is one isolated melody performance
per section, with no drums, chords, bass, or other accompaniment in the source.
The initial transcription benchmark focuses on monophonic melody lines, but
overlapping notes, CC64, pitch bend, velocity, and track structure must be
preserved and shown for review rather than silently flattened.

## 5. GarageBand and Apple Loop import

### 5.1 Product wording

The feature is named **Import GarageBand Apple Loop**, not “convert AIFF to
MIDI.” An `.aif`/`.aiff` file is not automatically a MIDI interchange file.
Melotrail must inspect content before deciding what it can recover.

| Input | Import route | Accuracy promise |
| --- | --- | --- |
| Standard `.mid`/`.midi` | Validate and import MIDI directly | Exact events after explicit review |
| Green Software Instrument Apple Loop with a verified embedded Standard MIDI payload | Extract the embedded MIDI, validate it, and retain the loop audio for A/B | Exact embedded note/controller events; GarageBand instruments/effects are not reproduced |
| Blue/audio Apple Loop | Decode audio, then transcribe to a draft MIDI candidate | Estimated notes requiring correction and approval |
| Plain GarageBand `.aif`/`.aiff` audio export | Treat as ordinary audio, request explicit section bounds, then transcribe | Estimated notes; export may have trimmed boundary silence |
| Existing `.wav`/`.wave`/`.mp3` | Keep the existing audio transcription route behind the same review contract | Estimated notes requiring correction and approval |

Initial container support is `.mid`, `.midi`, `.aif`, `.aiff`, `.aifc`, `.caf`,
`.wav`, `.wave`, and `.mp3`. Detection uses container signatures and validated
chunks, never the filename suffix alone.

Apple publicly specifies a `midi` chunk in the Core Audio Format (`.caf`) whose
payload is a complete Standard MIDI File. That route can use the documented CAF
chunk table. The plan does **not** assume that every `.aif`/`.aiff` Apple Loop
has the same contract. GA-002 must prove recoverable embedded MIDI using real
GarageBand fixtures before production extraction for AIFF is enabled.

### 5.2 Import decision flow

```text
immutable source
    -> sniff and bounds-check the actual container
    -> read Apple Loop metadata as advisory evidence
    -> classify payload
       |-- Standard/embedded MIDI -> exact MIDI candidate
       `-- audio only             -> canonical decoded WAV
                                      -> Basic Pitch draft candidate
    -> compare tempo/key/meter/bars with project authority
    -> synchronized waveform/piano-roll/render review
    -> user edits or accepts
    -> immutable approved MIDI revision
    -> structure, arrangement, render, and export
```

There is no silent fallback from embedded-MIDI extraction to transcription. The
UI must report one of these outcomes clearly:

- `MIDI data found — imported without audio transcription`;
- `Audio Apple Loop — MIDI will be estimated and must be reviewed`;
- `Plain AIFF audio export — no GarageBand MIDI data was found`.

If an embedded Standard MIDI File contains multiple musical tracks, Melotrail
asks which track is the melody. It must not merge them silently. If metadata
conflicts with project tempo, key, meter, or declared bars, import pauses for a
visible user decision; it never stretches or transposes automatically.

### 5.3 GarageBand onboarding inside the app

For the best input route, Melotrail will teach the musician to:

1. use a green Software Instrument region, not a blue recorded/audio region;
2. set GarageBand tempo, key, and meter to match the Melotrail project;
3. snap and trim the region to exact whole-bar section bounds;
4. choose **File > Add Region to Loop Library** or drag the region into the
   GarageBand Loop Browser;
5. drag the resulting User Loop into Melotrail or select it in the importer.

GarageBand's normal **Share > Export Song to Disk** AIFF route is audio-only and
may trim leading/trailing silence. It remains supported as transcription input,
but it is not presented as the lossless MIDI route.

## 6. First vertical slice and golden song

The first end-to-end product slice is the current song, rebuilt through a real
application use case rather than encoded inside a JUnit test:

- C major, 4/4, 75 BPM;
- structure: Intro -> Verse -> Verse -> Chorus -> Bridge -> Verse -> Outro;
- all arrangement assets from `lofi-core@1`;
- audible roles: approved melody, electric piano, and drums;
- bass and strings disabled for this release;
- a fill occurs only where the selected transition asks for one.

| Section | Bars | Authoritative chord progression |
| --- | ---: | --- |
| Intro | 4 | Cmaj7 \| Am7 \| Fmaj7 \| G |
| Verse | 4 | C \| G/B \| Am7 \| Fmaj7 |
| Chorus | 8 | F \| G \| C \| Am7 \| F \| G \| C \| C |
| Bridge | 4 | Am7 \| Em \| Fmaj7 \| G |
| Outro | 4 | Fmaj7 \| G \| Cmaj7 \| C6 |

Before implementation changes its output, capture a manually arranged
GarageBand reference for this song: source melody, MIDI-equivalent note data,
drum stem, electric-piano stem, dry mix, and reference mix. It is the musical
target and regression evidence, not a production runtime dependency.

## 7. Target desktop workflow

```text
Song setup -> Melody -> Chords & structure -> Arrange -> Mix & export
```

1. **Song setup:** enter tempo, meter, key/scale, title, and default section
   lengths.
2. **Melody:** add one source per unique section, inspect provenance, set exact
   bounds, A/B the source and MIDI render, correct the piano roll, and approve a
   revision.
3. **Chords & structure:** enter or select fixed chord events, arrange section
   occurrences, and validate duration/coverage without rewriting melody.
4. **Arrange:** select a coupled lo-fi scene and A/B/C variation per section;
   choose drum fill (`NONE`, light, medium, or section-specific), keys rhythm,
   voicing, energy, and mute state; loop-audition only the changed section.
5. **Mix & export:** adjust role gain/pan/mute/solo, preview the full song, freeze
   an export snapshot, and export melody MIDI, arrangement MIDI, stems, and WAV.

Every long operation is cancellable. Re-running import or arrangement publishes
a new candidate and invalidates only dependent artifacts.

## 8. Target domain and architecture

Kotlin continues to own the domain, orchestration, application use cases, and
desktop UI. The Python worker owns bounded audio/container inspection, decoding,
audio-to-MIDI transcription, and targeted DSP. MIDI is canonical only after
melody review; WAV remains canonical for audio production.

### 8.1 Core records

- `SongProject`: authoritative tempo, meter, key, scale, sections, structure,
  and chords.
- `SourceAsset`: immutable bytes, hash, filename, and acquisition provenance.
- `InputInspectionReport`: real container, payload classification, streams,
  Apple metadata evidence, warnings, and parser version.
- `TranscriptionCandidate`: raw engine output, engine/version/settings,
  confidence, source-time events, project-grid mapping, and hash lineage.
- `ApprovedMelodyRevision`: the exact accepted MIDI plus source, candidate,
  edit-history, bounds, and configuration hashes.
- `StylePack`: versioned coupled scenes, complete drum/keys patterns, fills,
  instruments, mix presets, compatibility, and license identity.
- `ArrangementPlan`: explicit scene/pattern/variation choices per occurrence.
- `RenderSnapshot`: frozen project and artifact hashes used for export.

### 8.2 Simplified stage graph

```text
PROJECT_SETUP
    -> SOURCE_IMPORT
    -> MELODY_DRAFT
    -> MELODY_REVIEWED
    -> STRUCTURE_HARMONY
    -> ARRANGEMENT
    -> RENDER_MIX
    -> EXPORT
```

Keep StageRunner-style hashing, invalidation, and atomic publication, but remove
the superseded schema-v4 stage branches after migration. Downstream melody
consumers resolve one exact approved hash.

## 9. Implementation roadmap

Phases are sequential. A phase starts only after the previous exit gate passes.
Each task is implemented in a small commit with regression tests; a replacement
removes its old production branch rather than leaving a permanent feature flag.

### Phase 0 — Baseline and Apple Loop feasibility

#### GA-001: Freeze a musical baseline

- Save the current live E2E output and its complete artifact manifest.
- Build the GarageBand golden song and stems described in section 6.
- Define the listening rubric and create loudness-matched randomized comparison
  packaging.
- Freeze unrelated arrangement/AI feature work.

#### GA-002: Prove GarageBand Apple Loop compatibility

- Collect small, redistributable fixtures made by a real GarageBand version:
  green Software Instrument and blue/audio User Loops, plus a plain Share-export
  AIFF. Include 4- and 8-bar, 75- and 80-BPM examples.
- Inspect actual `.aif`/`.aiff`/`.aifc` and `.caf` containers and Apple-specific
  chunks; document what is public, stable, and recoverable.
- For CAF, implement a throwaway bounds-checked proof of the documented `midi`
  chunk and validate the complete embedded SMF.
- For AIFF, enable exact extraction only if fixtures prove a stable, defensible
  contract. Never scan and copy an arbitrary `MThd` byte sequence.
- Round-trip a known Standard MIDI fixture through GarageBand and compare format,
  tracks, PPQ, pitch, onset, duration, velocity, CC64, pitch bend, and tempo map.
- Record an architecture decision. If extraction is not stable, make audio
  transcription plus review the supported route for that container.

**Exit gate:** Melotrail can distinguish exact embedded MIDI, audio Apple Loop,
and plain audio without relying on the extension or publishing corrupt output.

### Phase 1 — Safe multi-format import

#### GA-003: Extend inspection and decoding

- Add AIFF, AIFF-C, and CAF container models to the Kotlin/worker contract.
- Content-sniff `FORM/AIFF`, `FORM/AIFC`, CAF, WAV, MP3, and SMF; bounds-check
  every chunk and reject truncated, empty, spoofed, or unsupported input.
- Publish `prepared/<section>/decoded.wav` atomically for audio sources, using
  the Python/native decoder boundary and keeping JVM playback/render WAV-based.
- Preserve the original source bytes and SHA-256; cache deterministic inspection
  and decode evidence by source/configuration hash.
- Preserve sample rate and channel count unless a visible conversion is chosen.
- Safely ignore unknown metadata chunks and never publish partial WAV/MIDI on
  failure.

#### GA-004: Add exact embedded-MIDI import

- Extract a validated complete SMF only for the container contracts accepted by
  GA-002.
- Preserve the raw embedded SMF separately from any normalized preview file.
- Support format 0/1 and multiple tracks; ask for a melody track when ambiguous.
- Report metadata conflicts without changing project authority.
- Publish provenance `STANDARD_MIDI` or `APPLE_LOOP_EMBEDDED_MIDI`.

#### GA-005: Make audio transcription a draft

- Run Basic Pitch only from the canonical decoded WAV.
- Persist engine version, model/settings, raw events, per-note confidence, and
  source/configuration hashes.
- Allow only non-musical sanitation automatically: valid note pairs, valid MIDI
  ranges, and removal of byte-identical duplicate events.
- Publish provenance `APPLE_LOOP_AUDIO_TRANSCRIPTION` or
  `PLAIN_AUDIO_TRANSCRIPTION` and state that review is required.
- Never replace an existing candidate or approved revision silently.

**Exit gate:** chooser, drag/drop, retry, preview, and application import all use
one validated path; direct MIDI and embedded MIDI avoid Basic Pitch; audio-only
input always stops in `MELODY_DRAFT`.

### Phase 2 — Correct grid mapping and melody review

#### GA-006: Replace inferred timing with declared-grid conformance

- Require project BPM/meter, section bars, and explicit source start/end bounds.
- If source duration matches the declared section within
  `max(80 ms, 0.5%)`, map it uniformly from section tick zero to the exact section
  end.
- If it does not match, open trim/downbeat review; do not auto-accept half/double
  tempo, invent pickup/tail bars, or run destructive beat warping.
- Pickups and tails become explicit later UI choices, not heuristics.
- Remove the `bars - 1` body/pickup/tail behavior from the selected route.

#### GA-007: Add immutable review and approval records

- Model raw candidate, user edit operations, undo/redo history, and explicit
  approval as separate revisions.
- Bind approval to the exact source, decoded audio, candidate, bounds, project
  authority, editor operations, and result hashes.
- Treat scale and chord mismatches as advisories; remove harmony repair from the
  approved path.
- Terminate hanging preview notes safely at a section boundary without mutating
  the stored source/candidate.

#### GA-008: Build the melody review UI

- Add synchronized waveform and piano-roll overlay with source/draft/render A/B,
  section looping, playhead, zoom, and latency compensation.
- Support add/delete/move/resize/split/merge, pitch, velocity, track selection,
  optional snap, undo/redo, and candidate reset.
- Flag low confidence, micro-notes, suspicious gaps, overlaps, duration mismatch,
  unexpected polyphony, and advisory scale/chord conflicts.
- Require explicit approval for every transcribed candidate. Direct/embedded
  MIDI still receives a preview and explicit acceptance.

**Exit gate:** an `ApprovedMelodyPreservationTest` proves semantic note/controller
identity from approval through connected song occurrences, and no live test can
forge `reviewer = user` or auto-approve uncertain input.

### Phase 3 — One producer-authored lo-fi ensemble pack

#### GA-009: Introduce the style-pack contract

- Move musical behavior from hardcoded test setup into validated, versioned data
  manifests.
- Define coupled scenes containing drum phrase, electric-piano rhythm/voicing,
  fill/dropout behavior, instruments, mix preset, tempo range, section purposes,
  energy, and license identity.
- Support 2/4/8-bar phrases, A/B/C variants, and complete low/medium/high-energy
  patterns. Energy selects a whole authored variant; it never deletes events
  from another variant.
- Render every scene and isolated pattern as a catalog audition before it can be
  marked production-ready.

#### GA-010: Replace drum decimation with complete grooves

- Author coherent lo-fi verse, chorus, bridge, intro, and outro phrases with
  controlled swing, ghost notes, and transition space.
- Provide `NONE`, light, medium, and section-specific fills.
- Confine fills to their declared final-beat/final-bar window and preserve the
  next section's downbeat.
- Keep required kick/snare anchors intact in every complete variant; do not thin
  a single event list by density.

#### GA-011: Replace pad behavior with electric-piano accompaniment

- Rename the product role from generic pad to keys/electric piano.
- Voice every authoritative chord window, including inversions such as `G/B`;
  without bass, keep the harmonic root while respecting the requested bass note
  as the lowest voice.
- Use producer-authored lo-fi rhythms, smooth voicing movement, register limits,
  pedal rules, and explicit allowable chord extensions.
- Make density/energy select rhythm and voicing variants without ever dropping a
  chord.
- Add a distinct Rhodes-style instrument and keep it timbrally separate from the
  melody.

**Exit gate:** all initial patterns pass deterministic structure checks and a
human catalog audition; the golden song has 100% chord-window coverage and no
automatic fill at every section boundary.

### Phase 4 — Production use case and guided arranger UI

#### GA-012: Move the song recipe into the application

- Add one public use case that consumes a `SongProject`, approved melody hashes,
  style-pack identity, arrangement choices, and seed.
- Make the C-major golden project a versioned fixture/manifest consumed by the
  use case; delete product decisions embedded in
  `LiveLoFiFiveSourceEndToEndTest.kt`.
- Build section blocks and a scene/pattern browser with per-occurrence variation,
  fill, energy, keys rhythm/voicing, mute/solo, gain, and pan.
- Support loop audition and regeneration of only the selected section/layer.
- Split the workspace UI/ViewModel by the five workflow steps instead of adding
  more state to a single router.

**Exit gate:** a musician can import, review, structure, arrange, audition, edit,
save, reopen, and rebuild the golden song without changing source code.

### Phase 5 — Render, mix, export, and GarageBand round-trip

#### GA-013: Deliver trustworthy output

- Render distinct melody, electric-piano, and drum stems from one frozen
  snapshot, with identical nominal start/frame count.
- Apply a restrained, documented lo-fi chain and safe gain staging; keep role
  mixing editable rather than hiding it behind an unrestricted polish step.
- Export approved melody MIDI, full arrangement MIDI, per-role stems, and stereo
  WAV. Preserve useful debug intermediates and a hash manifest.
- Make MIDI/stem export suitable for continued production in GarageBand.
- Add opt-in Make targets for live Apple Loop import and listening artifacts;
  ordinary CI uses explicit reviewed fixtures and never deletes input/accepted
  output.

**Exit gate:** frozen-snapshot rebuilds are deterministic in MIDI and stable in
audio metrics; exported assets re-import into GarageBand with the expected
structure and alignment.

### Phase 6 — Cutover, deletion, and documentation

#### GA-014: Complete schema-v5 cutover

- Replace the schema-v4 stage graph with the graph in section 8.2; migrate known
  projects explicitly or reject them with an actionable message. Do not keep two
  runtime pipelines.
- Remove superseded selected-path code and tests after replacement coverage is
  green: automatic beat/downbeat warp for declared loops, melody mutations from
  `MidiAiFix`, `MidiHarmonyFitting`, technical correction, enhancement,
  full-song enhancement, cohesion, and seeded humanization; density-based drum
  and pad generation; mandatory strings/bass; and the test-only song recipe.
- Keep only objective integrity checks in ordinary composition. Replace binary
  “quality certified” evidence with per-dimension human listening ratings.
- Update `README.md`, `docs/MIDI_IMPORT_PROCESS.md`,
  `docs/TRACK_PROCESS_WORKFLOW.md`, `docs/plan/README.md`, and quality-gate docs.
  Mark the old QP roadmap historical and remove contradictory active guidance.
- Delete unused compatibility branches, stage enums, DTO fields, commands, and
  docs in the same cutover rather than accumulating legacy modes.

**Exit gate:** only the guided-arranger route is reachable, all repository docs
agree with this plan, and the validation commands in section 12 pass.

### Phase 7 — Optional AI, only after the deterministic MVP passes

Qwen or another planner may later rank compatible scenes, suggest energy/fill
choices, or explain alternatives. It must return bounded IDs from the validated
style pack and show a preview/diff. It may not rewrite project harmony or the
approved melody.

Do not restart autonomous publishing, unrestricted enhancement, or automatic
release-quality claims until the larger listening gate in section 13 passes.

## 10. Main implementation seams

| Workstream | Current files/components to change |
| --- | --- |
| Container and metadata contract | `InputInspectionContract.kt`, `InputCleanupPlan.kt`, `InputCleanupApplicationService.kt` |
| Worker inspection/decode/transcription | `worker/commands/input_inspection.py`, `worker/commands/transcribe.py`; add one canonical audio decode command rather than AIFF logic in multiple commands |
| Application import and provenance | `AutomaticImportProcessors.kt`, `ProjectApplicationService.kt`, transcription quality gate and project schema |
| Desktop import/review | `DesktopFileDialogs.kt`, `DesktopSupport.kt`, `WorkspaceViewModel.kt`, workspace routing/components |
| Timing and melody identity | current connection/occurrence assembly, beat/downbeat mapping, `MidiAiFix.kt`, `MidiHarmonyFitting.kt`, stage comparison/evidence |
| Arrangement | `MusicalPatternLibrary.kt`, `DrumMidiGeneration.kt`, `PadMidiGeneration.kt`, instrument registry, new style-pack manifests |
| Product E2E | `LiveLoFiFiveSourceEndToEndTest.kt`, new application use case, Makefile live targets |
| Evidence and docs | `QualityReviewEvidenceService.kt`, import/workflow/quality documentation |

Nominal `AudioFormat.isAiff` detection is not an importer. AIFF/CAF decoding and
validation must enter through the same worker and artifact contracts as every
other source format.

## 11. Acceptance and regression gates

### 11.1 Source/import integrity

- Original bytes and SHA-256 are unchanged after every successful or failed run.
- Container detection rejects mislabeled, corrupt, empty, truncated, oversized,
  and unsupported-compression fixtures without partial artifacts.
- Uppercase extensions and unknown safe metadata chunks work.
- Audio decode duration differs by at most one source frame; a lossless PCM
  fixture differs by at most one destination PCM least-significant bit.
- Same source/configuration produces the same evidence and reuses the same
  hash-matching artifact.
- Extracted MIDI matches the fixture's complete event stream. Unknown/no MIDI
  payload yields an explicit audio classification, never a corrupt SMF.
- At least two real GarageBand fixtures participate in test evidence; synthetic
  AIFF alone is insufficient. Check them in only when their redistribution
  rights are explicit; otherwise keep them local for the opt-in live suite.

### 11.2 Transcription benchmark

Build a golden corpus of at least ten independently labeled solo-melody loops:
sustained, repeated, short, legato, low-velocity, register-change, and
reverb/delay examples; 4/8 bars at 75/80 BPM.

Initial gate:

- aggregate note-on F1 >= 0.92 and every fixture F1 >= 0.85;
- median onset error <= 60 ms and 95th percentile <= 150 ms;
- at most two false plus missed notes per four bars;
- output/input note-count ratio from 0.85 through 1.15;
- no duplicate/unmatched events or notes outside MIDI 21 through 108.

Extend `worker/tools/benchmark_transcription.py` rather than creating a second
metric implementation. Runtime confidence never substitutes for ground truth or
user approval. If this gate fails, the safe product response is to improve the
review editor, keep source audio as the audible melody, or require direct/
embedded MIDI—not add another automatic melody-repair pass.

### 11.3 Timing regressions

- 12.8-second 4-bar and 25.6-second 8-bar sources at 75 BPM occupy their exact
  declared spans.
- Detector results at 150/152 BPM and a first detected beat at 2.415 seconds do
  not change the authoritative 75 BPM grid.
- Exact inputs begin at section tick zero and end at the exact declared section
  tick; local timing receives at most one visible uniform boundary mapping.
- A duration outside tolerance enters trim/downbeat review instead of auto-warp.

### 11.4 Approved melody preservation

From approval through repeated occurrences and render input, assert:

- zero pitch, note-count, local-onset, duration, velocity, or controller changes;
- occurrences differ only by their absolute section offsets;
- harmony/scale checks report but never repair;
- optional future quantization creates a reversible candidate with a visible
  diff and separate approval.

Tests compare semantic MIDI events, not merely file hashes or protected anchors.

### 11.5 Arrangement invariants

- Chord-window coverage is 100% for every keys density/energy level.
- Every accompaniment pitch is a declared chord tone or a style-pack-declared
  extension, with enough defining tones to distinguish chord quality.
- Without bass, roots remain present; `G/B` has B as the lowest voice and G
  present.
- No non-common tone sustains unintentionally across a chord boundary.
- Adjacent single-voice movement is at most 12 semitones, with target median <= 7.
- Same pattern/version/seed produces identical MIDI.
- Density selects a complete pattern ID; required kick/snare anchors remain.
- Fill events stay inside their declared window, do not delete the next downbeat,
  and appear only when the plan does not select `NONE`.
- No same-pitch drum overlap, section spill, unintended bass, or shared
  melody/keys instrument.

### 11.6 Render integrity

- All stems share nominal start and frame count; first-downbeat alignment differs
  by at most one MIDI tick or one audio frame.
- Song duration equals the authoritative structure plus only a documented,
  bounded effect tail.
- Audio samples are finite and non-silent; true peak is <= -1 dBTP.
- Default active-window melody clearance is at least +3 dB over accompaniment.
- No unplanned silence exceeds 500 ms while a sustained approved note or chord
  should sound.
- LUFS, hashes, critic counts, and non-clipping prove technical integrity only;
  they do not certify musical quality.

### 11.7 Listening benchmark

Use ten holdout melodies not used to author patterns. Loudness-match, randomize,
and retain hash-bound comparisons for:

1. source audio versus rendered approved MIDI;
2. approved melody alone versus the arrangement;
3. previous pipeline versus the guided arranger;
4. default arrangement versus a user-edited arrangement;
5. dry mix versus final mix.

Score melody fidelity, timing, harmony, drum groove, electric piano,
transitions, balance, and overall usefulness from 1 through 5.

MVP listening gate:

- median >= 4/5 for melody fidelity, timing, harmony, and groove;
- no song below 3/5 for fidelity, timing, or harmony;
- at least 8/10 songs are useful arrangement drafts;
- the new route is preferred over the old route in at least 8/10 comparisons;
- an acceptable draft is reached in under ten minutes without code changes.

## 12. Validation commands and test pyramid

Required existing commands remain:

```bash
make test
make worker-test
make build
```

Add these explicit targets during their owning phase:

```bash
make transcription-benchmark   # labeled corpus; no live UI approval
make live-apple-loop-e2e        # real GarageBand files; stops at review if needed
make listening-benchmark       # renders randomized, hash-bound audition set
```

Coverage layers:

- worker unit tests for inspection, secure chunk parsing, decode, extraction,
  and transcription;
- Kotlin contract tests for provenance, decoded artifacts, approval hashes, and
  invalidation;
- application integration tests for every import route through approved MIDI;
- desktop tests for chooser/drop, progress, error copy, A/B review, edit,
  undo/redo, and approval states;
- musical invariant tests for exact melody identity, chord coverage, complete
  patterns, fills, and deterministic scenes;
- deterministic CI E2E using an explicitly pre-approved known MIDI fixture;
- opt-in live E2E using GarageBand files, Basic Pitch when required, and the real
  renderer;
- human listening evidence before a style pack is marked production-ready.

Live targets never clean source or previously accepted artifacts. They use
unique run directories and print the paths needed for manual review.

## 13. Go/no-go decisions

1. **Apple Loop gate:** ten real GarageBand loops classify and import safely. If
   a container cannot expose stable embedded MIDI, support it honestly through
   audio transcription rather than private brittle parsing.
2. **Transcription gate:** the corpus thresholds pass. If not, ship the editor
   plus source-audio melody or require direct/embedded MIDI for the first MVP.
3. **Arrangement gate:** approved MIDI is unchanged, chord coverage is 100%, and
   every catalog scene passes human audition.
4. **Product gate:** at least 8/10 holdouts become useful drafts within ten
   minutes. If this fails after one carefully curated style pack, position
   Melotrail as an exportable sketch arranger and do not resume autonomous-song
   work.
5. **Publishable-output gate:** automatic publication work remains frozen until
   at least 30 holdout melodies and multiple listeners demonstrate repeatable
   publishable quality. This is not an MVP requirement.

## 14. Definition of done for this roadmap

The guided-arranger MVP is done only when:

- a GarageBand User Loop can be dropped into the app and its exact or estimated
  import route is explained truthfully;
- audio transcription can be reviewed and corrected in-app before approval;
- the C-major golden song and at least nine holdouts build without source-code
  edits;
- arrangement never changes approved melody or omits an authoritative chord;
- curated drums and electric piano sound coherent across sections and remain
  locally editable;
- MIDI, stems, and WAV round-trip into GarageBand at the correct grid positions;
- deterministic tests, live import evidence, and the MVP listening gate pass;
- the old active pipeline and contradictory product documentation are removed.

## 15. Confirmed input and remaining fixture detail

1. **Confirmed:** each section contains one isolated melody performance, not a
   full mix or a source containing accompaniment.
2. **Confirmed:** the melody is created with a GarageBand Software Instrument/
   MIDI track. The primary route is therefore a green User Loop and exact
   embedded-MIDI extraction when the exported container exposes valid MIDI.
3. The importer preserves unexpected overlaps/polyphony for review; it never
   forces the source to become monophonic by deleting notes.
4. A small in-app piano-roll editor remains part of the MVP because not every
   GarageBand Apple Loop container is guaranteed to expose MIDI, and no
   audio-to-MIDI model can guarantee an exact melody from every Apple Loop.

GA-002 will use a real exported loop from this GarageBand track to verify the
container contract. If valid embedded MIDI is present, Basic Pitch is bypassed.
If the particular export contains audio only, the app reports that fact and
uses the explicit transcription-and-approval fallback rather than silently
claiming an exact conversion.

## 16. Apple references behind the import decision

- [Create custom Apple Loops in GarageBand](https://support.apple.com/en-lamr/guide/garageband/gbndd0009b98/mac)
- [Audio and MIDI Apple Loops in GarageBand](https://support.apple.com/en-lamr/guide/garageband/gbnd84045a00/mac)
- [Export a song from GarageBand](https://support.apple.com/en-lamr/guide/garageband/gbnd7cbf5ed9/mac)
- [Apple Loop metadata and MIDI content in Logic](https://support.apple.com/en-mk/guide/logicpro/lgcp32add66e/10.7/mac/11.0)
- [Core Audio Format specification: MIDI chunk](https://developer.apple.com/library/archive/documentation/MusicAudio/Reference/CAFSpec/CAF_spec/CAF_spec.html)
