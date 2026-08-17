# Melotrail — UI and MIDI Workflow Recovery Plan

## Goal

Rebuild the Compose Desktop workspace to match `plan/UI.png`, reduce MIDI
import to a guided workflow with one obvious next action, make the Lo-fi MIDI
Feel transformation audible throughout the whole pipeline, and generate an
arranged song whose piano, generated instruments, transitions, visual timeline,
and rendered stems share one authoritative musical clock.

This plan supersedes the completed UI/Lo-fi/arrangement assumptions where the
current product behavior or this new direction conflicts with them. It does not
silently promote unrelated deferred work. Future Task 059 is used only as the
worker-test interpreter contract: worker checks run with `.venv/bin/python`,
not an ambiguous system `python`.

## Product interpretation to confirm

`plan/UI.png` is treated as the exact wide-screen composition target at
1536 × 1024: its hierarchy, panel placement, proportions, density, typography,
color language, borders, controls, selection states, transport, and mixer must
be reproduced from real application state.

The mockup's travel-specific content—scene artwork, Video Concept, Current
Location, weather, and destination—is not represented in the current project
model. This plan assumes those regions become optional, local presentation
metadata with deterministic placeholders and no network/weather integration.
They must not pretend to be live data or block music creation. Confirm whether
this assumption is correct before implementing Task 077.

## Repository findings

- `WorkspaceApp.kt` is a 1,481-line composable containing navigation, parts,
  preparation, MIDI repair, structure, arrangement, timeline, mixing, status,
  dialogs, and transport. The current section-based layouts replace large parts
  of the workspace instead of preserving the single-screen workstation shown
  in `UI.png`.
- Part rows currently expose overlapping actions such as Prepare, Edit role,
  Preview, and Analyze, while repair, Lo-fi Feel, audio preparation, and retry
  actions live in separate panels. The import dialog describes internal stages
  and prerequisites before the user has a clear primary action.
- Direct MIDI import correctly preserves `source/<part>.*` and
  `midi/raw/<part>.mid`, but users must understand several internal artifacts
  and buttons before the part becomes arrangement-ready.
- `Project.requireCleanMidi()` correctly resolves the selected analysis input:
  repaired MIDI or the derived Lo-fi Feel MIDI. Analysis and cohesion input use
  that selection.
- `StemRenderingMixer.assembleMidi()` nevertheless reads
  `part.midi.clean` unconditionally for piano. With Lo-fi Feel selected, the
  analysis and generated instruments can be based on 80 BPM swung MIDI while
  the rendered piano still uses repaired MIDI. Its fingerprint also hashes the
  repaired MIDI rather than the selected render source.
- The per-occurrence cohesion engine and artifact store exist, but no desktop
  intent or application service generates, reviews, approves, or consumes those
  artifacts. The arrangement service currently creates the song plan and
  detailed arrangement directly, and the renderer does not read approved
  cohesion MIDI.
- Bass, drums, pad, strings, transitions, piano assembly, and stem rendering
  each reconstruct section offsets and tempo/meter metadata. Most adapters
  require every part to have the same PPQ. This duplicates the timing contract
  and leaves synchronization dependent on several implementations agreeing.
- Current tests prove deterministic local units and exact output frame counts,
  but no end-to-end fixture selects Lo-fi Feel, repeats multiple parts, inserts
  transitions, generates all instruments, and then verifies note-on/frame
  alignment across the rendered song.
- The current baseline passed on 2026-08-17:
  - `./gradlew test :desktopApp:test`
  - `.venv/bin/python -m unittest discover -s worker/tests` (34 tests; warnings
    from deliberately tiny audio fixtures)
  These green checks do not invalidate the reported defects.
- The worktree already contains user-owned staged deletions and additions under
  `plan/`. Implementation must preserve them and stage only explicitly selected
  files if a later task requests a commit.

## Target user workflow

```text
Open/Create project
  -> Import MIDI
  -> Prepare MIDI
       preserve source/raw
       standard repair
       stop only when review is required
       analyze selected MIDI
  -> Optional Lo-fi MIDI Feel
       create 80 BPM + 58% swing derivative
       A/B preview through the same transport
       re-analyze automatically after selection
  -> Add prepared parts to Song Structure
  -> Generate Cohesion + Arrangement
       deterministic mode auto-approves safe cohesion
       Qwen mode requires review/approval
  -> Inspect synchronized plan and instrument timeline
  -> Build song
  -> Mix, master, and export
```

At every point, the selected part exposes exactly one primary action. Advanced
repair profiles, reports, raw/repaired A/B, rights metadata, and recovery remain
available through a details surface, not as competing row actions.

## Architectural rules

1. **One selected-MIDI resolver.** Add a typed application/domain boundary that
   resolves and validates the current MIDI for a part. Analysis, cohesion,
   piano assembly, generated MIDI, preview, render fingerprints, and provenance
   must use it. Direct reads of `midi.clean` outside repair/report code are a
   regression.
2. **One song clock.** Create one immutable `SongTimeline` from the saved
   structure, selected/approved occurrence MIDI, PPQ conversion policy,
   tempo/meter maps, section boundaries, and transition insertions. Every MIDI
   generator, renderer, timeline view, and duration/frame calculation consumes
   it.
3. **Musical positions before wall-clock time.** Store section and event
   placement in rational bar/beat/tick coordinates, convert to seconds through
   the authoritative tempo map, and convert to frames once at the render
   boundary. Never derive later start positions by summing rounded audio
   durations.
4. **Occurrence MIDI is authoritative after cohesion.** Before cohesion, an
   occurrence resolves to the part's selected repaired/Lo-fi artifact. After
   cohesion approval, it resolves to the validated per-occurrence MIDI. A
   repeated source part may therefore have distinct safe edits without losing
   its stable identity.
5. **Immutable and atomic artifacts.** Source, raw, repaired, and prior approved
   files stay unchanged. Lo-fi, cohesion, arrangement, generated MIDI, reports,
   and audio are written to temporary files, validated, fingerprinted, then
   atomically published.
6. **A single UI command may orchestrate several explicit domain stages.** The
   simplified `Prepare MIDI` action may run standard repair and analysis, but
   each artifact, report, progress phase, approval threshold, cancellation
   boundary, and failure recovery remains visible and testable.
7. **One command, one home.** Contextual play buttons only select an artifact
   for the persistent transport. Import, retry, readiness recovery, arrangement,
   build, and export each have one primary location.
8. **UI is an adapter.** Compose renders immutable UI models and emits intents;
   file access, worker calls, MIDI transformation, orchestration, and timeline
   calculations stay in application/domain services.
9. **No false precision or success.** The UI may show a part as Lo-fi,
   arranged, synchronized, or built only after current artifact fingerprints
   and timing validation pass.

## Delivery sequence

| Task | Deliverable | Depends on |
| --- | --- | --- |
| [073](tasks/completed/073-authoritative-song-timing-contract.md) | Reproduction fixtures and authoritative timing contract | Current baseline |
| [074](tasks/completed/074-selected-midi-and-lofi-source.md) | Canonical selected-MIDI resolution and Lo-fi Feel repair | 073 |
| [075](tasks/completed/075-cohesion-and-synchronized-arrangement.md) | Cohesion integration and synchronized arrangement pipeline | 074 |
| [076](tasks/completed/076-guided-midi-import.md) | Guided MIDI import and preparation workflow | 074 |
| [077](tasks/completed/077-ui-reference-reconstruction.md) | Exact `UI.png` Compose workspace reconstruction | 075–076 and presentation-metadata decision |
| [078](tasks/completed/078-ui-midi-release-acceptance.md) | End-to-end listening, visual, accessibility, and packaging acceptance | 077 |
| [079](tasks/completed/079-reference-shell-and-left-rail.md) | Restore reference header and left rail | 078 |
| [080](tasks/completed/080-reference-center-workstation.md) | Restore reference structure, arrangement, and timeline | 079 |
| [081](tasks/081-reference-right-rail.md) | Restore reference scene/player and AI Song Plan | 079–080 |
| [082](tasks/082-reference-footer-and-visual-acceptance.md) | Restore footer and complete visual acceptance | 079–081 |

Do not combine all six tasks into one change. Each task must leave the project
buildable and include focused regression tests before the next task starts.

## Task 073 — Reproduce the defects and define one timing contract

### Goal

Turn the reported Lo-fi and arrangement failures into deterministic failing
tests, then introduce the data contract that all later stages will share.

### Requirements

- Add MIDI fixtures covering:
  - two parts with different PPQ values;
  - multiple tempo changes and 4/4 plus 3/4 meter;
  - straight eighth notes suitable for Lo-fi swing;
  - repeated occurrences of one part;
  - notes that end exactly at a section boundary;
  - all generated instruments and a one-bar transition;
  - enough bars to expose accumulated drift.
- Add a reproduction proving that Lo-fi selection currently reaches analysis
  but not the rendered piano source. Assert artifact identity/hashes as well as
  notes and duration; do not depend on listening alone.
- Add a reproduction for the reported arranged-MIDI drift. Compare absolute
  note-on times for piano, bass, drums, pad, strings, and transitions at every
  section boundary and selected internal beats.
- Define a `SongTimeline`/`SongClock` domain model containing stable occurrence
  IDs, canonical PPQ, section start/end ticks, local-to-song tick conversion,
  tempo/meter events, inserted transition ranges, total seconds, and total
  frames for a requested sample rate.
- Define a bounded PPQ normalization policy. Prefer an exact common PPQ when it
  is at most 9,600; otherwise use rational conversion with deterministic
  rounding, record the maximum tick/time error, and reject input that exceeds
  the documented tolerance.
- Centralize half-open boundary semantics: note-ons belong to
  `[sectionStart, sectionEnd)`, note-offs may occur at `sectionEnd`, and a
  transition has its own explicit range and incoming tempo/meter ownership.
- Add an inspectable synchronization report containing input hashes, occurrence
  ranges, tempo/meter map, PPQ conversions, expected duration/frames, and the
  maximum measured alignment error.

### Tests and acceptance

- Observe and record the hard-coded clean-MIDI failure with a red test before
  implementation. Land the reusable fixture and green timing-contract tests in
  this task; land the desired end-to-end regression assertion with Task 074 so
  no task is handed off with a failing suite.
- Timeline property tests cover conversion round trips, ordering, overflow,
  meter changes, transition insertions, boundary note-offs, and deterministic
  serialization.
- No source or existing derived artifact is modified by diagnostic tests.
- The task documents the measured reproduction in the test name/output; it
  does not guess at an audio-device problem.

## Task 074 — Make Lo-fi Feel the actual selected track source

### Goal

Ensure selecting Lo-fi MIDI Feel changes the MIDI that is analyzed, previewed,
cohesion-processed, arranged, rendered as piano, fingerprinted, and reported.

### Requirements

- Introduce `SelectedMidiArtifactResolver` (name may vary) returning a validated
  typed identity: project-relative path, kind, part ID, profile/version, hash,
  PPQ, tempo/meter summary, and freshness evidence.
- Replace downstream direct `midi.clean` reads with the resolver. Repair and
  quality-report code may still address raw/repaired artifacts explicitly.
- Fix piano timeline assembly and stem cache fingerprints to use the resolved
  selected MIDI. If current cohesion exists for an occurrence, delegate to the
  occurrence resolver defined in Task 075.
- Verify that the fixed `lofi-80-swing-v1` transform:
  - emits exactly one 80 BPM tempo at tick zero;
  - applies 58% eighth-note swing only to eligible offbeats;
  - preserves meter, note identity, legal order, and positive duration;
  - handles program changes, controllers, sustain, multi-track input, and end
    markers without losing relevant non-tempo events;
  - produces a valid type-0 or type-1 output supported by the input contract;
  - reports every bounded collision repair and cannot silently move a downbeat.
- Replace the UI's two ambiguous selection buttons with a clear Original /
  Lo-fi MIDI Feel segmented choice in the selected-part editor. Show the fixed
  80 BPM and 58% swing values beside it.
- Selecting either feel invalidates exactly analysis and downstream artifacts,
  then offers one primary `Apply and re-analyze` action. The guided path may run
  re-analysis automatically after confirmation; failure leaves the previous
  selected artifact inspectable and gives one safe retry.
- A/B buttons select repaired or Lo-fi MIDI in the shared playback session and
  use the same position and monitor volume.
- Keep the final DSP option named `Lo-fi audio texture`; it must never be
  confused with this MIDI transformation.

### Tests and acceptance

- Resolver tests reject missing, escaped, malformed, stale, or hash-mismatched
  references and preserve legacy readable projects.
- End-to-end test: import -> repair -> select Lo-fi -> analyze -> arrange ->
  build, then prove the piano render input hash is the Lo-fi artifact hash and
  the rendered timing is 80 BPM with the expected swung offbeats.
- Switching back to Original makes repaired MIDI authoritative everywhere and
  invalidates only documented descendants.
- Raw and repaired hashes are identical before and after every transform.
- Manual A/B check confirms that Original and Lo-fi are audibly distinct and
  that replay, pause, seek, and stop use the persistent transport.

## Task 075 — Integrate cohesion and synchronize arranged MIDI

### Goal

Connect the existing per-occurrence cohesion work to the supported desktop
workflow and make every arranged instrument consume the same song clock.

### Requirements

- Add typed application-service operations to generate, load, approve, reject,
  and regenerate cohesion. Expose deterministic and Qwen planners through the
  same bounded request model.
- Deterministic mode may publish an automatically approved no-op/safe cohesion
  artifact. Qwen mode must remain a draft until explicit review and approval.
- Make arrangement generation require current cohesion references for every
  structure occurrence. It must not infer cohesion completion from the mere
  presence of `song_plan.json`.
- Use approved cohesion occurrence MIDI as the piano/source lane. Repeated
  occurrences must resolve independently; falling back to a shared part file
  after approval is an error.
- Refactor bass, drums, pad, strings, transition generation, piano assembly,
  visual timeline snapshots, and stem rendering to consume the single
  `SongTimeline`. Remove their independent section-offset/tempo concatenation.
- Convert each part/occurrence to the canonical PPQ once. Carry tempo and meter
  changes through the timeline without duplicate conflicting meta events at
  boundaries.
- Generate a complete piano timeline artifact alongside bass/drums/pad/strings
  rather than assembling an untracked temporary source that cannot be audited.
- Validate each generated MIDI against the timeline:
  - expected PPQ and total end tick;
  - one authoritative tempo/meter map;
  - no event outside its occurrence/transition range;
  - no stuck notes or illegal same-pitch collisions;
  - expected section boundary note-on times;
  - stable occurrence identity and input hashes.
- Convert the authoritative song duration to frames once and require every
  rendered stem and mix to have that exact frame count. Padding/truncation may
  follow the documented renderer tail policy but cannot conceal shifted
  onsets.
- Include the timeline and every selected/occurrence MIDI hash in cache keys,
  reports, provenance, stale checks, and release evidence.
- Add a desktop review surface matching the AI Song Plan panel in `UI.png`:
  purpose, energy, instruments, transition, selected occurrence, draft/current
  state, and one regenerate/review action.

### Tests and acceptance

- Multi-part, mixed-PPQ tests verify onset alignment at all section boundaries
  to at most one canonical tick and rendered click/impulse fixtures to at most
  one audio frame after conversion.
- Long repeated arrangements prove there is no cumulative drift.
- Tests cover no transition, crossfade, bridge, tempo changes inside a section,
  meter changes, boundary note-offs, silence at a section start, and all five
  instruments.
- Approved cohesion edits are audible in the piano stem and visible in its MIDI
  hash; rejected/stale cohesion is never consumed.
- All generated MIDI files share the same total musical timeline and all stems
  share the same exact total frame count.
- Manual listening uses at least one straight melody and one expressive melody;
  the reviewer checks the first beat after every transition, not only the song
  opening.

## Task 076 — Simplify MIDI import and preparation

### Goal

Make adding a MIDI part understandable without requiring users to know the
artifact graph or choose among technical maintenance buttons.

### Requirements

- Match the reference's left-panel entry points: `+ Add Part`, `Import MIDI`,
  and `Import Audio`. `+ Add Part` opens the same source chooser and routes by
  validated content; the explicit buttons preselect the intended type.
- Use a compact two-step import sheet:
  1. choose a file and validate its real format;
  2. confirm an auto-derived part name/ID and optional musical role.
- Move rights attestation and advanced metadata into an expandable Details
  area. It remains required for commercial-ready export but must not obscure a
  normal local import.
- After import, show one state-derived primary action per part:
  - `Prepare MIDI` for raw MIDI;
  - `Review repair` only when thresholds require approval;
  - `Apply Lo-fi change` when a feel selection is pending;
  - `Add to structure` when analysis is current;
  - `Fix issue` when an artifact/dependency is invalid.
- `Prepare MIDI` orchestrates the standard transcription-safe repair followed
  by analysis. It stops safely for explicit approval when thresholds are
  exceeded. Advanced repair profiles move to a part Details menu and retain
  their warnings/confirmations.
- For audio, retain a visibly distinct supported path: solo-piano WAV/MP3 only,
  inspect -> optional safe cleanup -> transcription -> standard MIDI prepare.
  Never suggest that arbitrary full mixes or vocals are supported.
- Replace internal-stage prose with short outcome language. Progress can reveal
  technical phases while running, but the idle UI should say what the next
  action accomplishes.
- Keep one dismissible operation banner and one retry action. Remove duplicate
  readiness, retry, and status text from part rows and secondary panels.
- Preserve cancellation, atomic publication, source immutability, stale
  evidence, and dependency recovery.

### Tests and acceptance

- Compose tests assert one primary CTA per part state and one retry surface.
- View-model tests cover direct MIDI success, approval-required repair,
  repair failure/retry, stale analysis, audio transcription prerequisites,
  cancellation, and project switching during preparation.
- A new user can import a valid MIDI and make it structure-ready through one
  import confirmation plus one `Prepare MIDI` action in the normal case.
- No normal MIDI import screen exposes worker names, paths, cleanup parameters,
  schema versions, or more than one competing next action.
- Source/raw hashes remain unchanged and each underlying artifact/report remains
  independently inspectable.

## Task 077 — Reconstruct the Compose UI from `UI.png`

### Goal

Replace the current card stack/section substitution with the exact wide-screen
workstation composition shown in `plan/UI.png`, backed by real state and the
simplified actions from Tasks 074–076.

### Requirements

- Establish a 1536 × 1024 reference viewport and derive measured design tokens
  from the image: column widths, header/footer heights, gaps, padding, radii,
  border opacity, typography scale, icon sizes, lane colors, selected states,
  and control heights. Record them in a small immutable token layer.
- Reproduce the wide layout regions:
  - left rail: brand, Scenes/Parts, import actions, and optional presentation
    metadata cards;
  - top center: five navigation destinations and selected project control;
  - center: Song Structure, selected Arrangement section, and instrument
    Timeline;
  - right: scene/presentation panel and AI Song Plan;
  - footer: persistent transport/waveform, five channel strips, master strip,
    and master-bus controls.
- The wide layout remains one stable workstation. Navigation focuses/selects
  the relevant region or editing mode; it must not replace the three columns
  with unrelated panel sets.
- Every music control visible in the target is functional or truthfully
  disabled with one accessible reason. Do not show decorative fake transport,
  meters, weather, status, waveform, or export success.
- Use local vector icons or an approved bundled icon set rather than Unicode
  glyphs. Provide accessible names and selected/pressed semantics.
- Split `WorkspaceApp.kt` into bounded files/components such as shell/header,
  parts, structure, arrangement, timeline, scene/plan, transport, mixer,
  dialogs, and tokens. Keep state derivation outside composables.
- Remove duplicated labels and actions, including the duplicate Role line in
  the current part row. Remove the separate status card; operation feedback is
  a single dismissible banner without changing the reference geometry.
- Implement deterministic placeholders for unavailable optional presentation
  metadata. No network calls are permitted for scenes, maps, weather, or time.
- Provide explicit responsive compositions:
  - wide: exact reference at 1536 × 1024 and proportional support down to the
    wide breakpoint;
  - medium: preserve center timeline and transport, with side rails reachable
    through drawers/panes rather than horizontal page scrolling;
  - narrow: one focused pane plus persistent compact transport and direct
    navigation to all actions.
- Preserve Ctrl/Cmd transport shortcuts, keyboard structure reordering, visible
  focus, 48 dp hit targets, non-color status cues, screen-reader labels, and
  logical focus order.

### Visual verification and acceptance

- Add deterministic populated, empty, loading, blocked, error, and selected
  UI fixtures. No golden may depend on the user's project, machine paths,
  clock, network, renderer, worker, or audio device.
- Capture golden images at 1536 × 1024 and agreed medium/narrow sizes. Compare
  the wide golden to `plan/UI.png` with a documented overlay/diff workflow.
- Wide acceptance requires all major panel edges and heights within 4 px of the
  measured reference, colors within the documented token tolerance, and no
  clipped/overlapping text at 100% scale. Typography and icons are visually
  reviewed because raw pixel thresholds alone are insufficient.
- Test 100%, 125%, and 150% UI scaling, minimum supported window size, long
  project/part names, empty states, and five-plus structure occurrences.
- Compose semantics tests assert one navigation row, one project selector, one
  import action for each advertised path, one timeline, one AI plan, one
  persistent transport, one mixer, one master output, and one global feedback
  surface.

## Task 078 — End-to-end acceptance and release gate

### Goal

Verify that the redesigned workflow is not only visually faithful but produces
audibly synchronized, current, reproducible project artifacts.

### Automated checks

- Run focused tests after each task, then:
  - `./gradlew test`
  - `./gradlew :desktopApp:test :desktopApp:build`
  - `.venv/bin/python -m unittest discover -s worker/tests` only when worker
    behavior or its contract is touched; keep the Task 059 interpreter explicit.
- Run an offline end-to-end fixture through import, preparation, Original/Lo-fi
  selection, analysis, repeated structure, cohesion, arrangement, generated
  MIDI, stems, mix, master, and release metadata.
- Validate every input/output fingerprint, selected artifact identity, PPQ,
  tempo/meter map, section boundary, total tick, total seconds, sample rate,
  channels, PCM depth, and total frame count.
- Assert no mutation of source/raw/repaired MIDI and no false-current stale
  artifact after switching feel, structure, cohesion, arrangement, or mix.
- Add a regression rule/test that flags new downstream direct reads of
  `MidiReferences.clean` outside the selected-artifact/repair boundaries.

### Manual checks

- Import a real direct MIDI using only the normal UI and record whether any
  button or label is ambiguous.
- A/B Original versus Lo-fi MIDI Feel at matched volume; confirm tempo and swing
  change in preview and in the built piano stem.
- Listen to a multi-section arrangement with all five instruments and at least
  one transition. Check the opening, every boundary, the final bars, pause/
  resume, seek, replay, and source switching on a real audio device.
- Compare the running wide workspace side by side and by transparent overlay
  with `plan/UI.png`; record intentional deviations and obtain explicit product
  approval for each one.
- Verify keyboard-only completion of import, part selection/preparation,
  structure editing, arrangement review, transport, and build.
- Build and launch the current-OS desktop package when release packaging is in
  scope. Do not claim Windows/Linux or unavailable renderer/model/device
  support.

### Definition of done

- A user sees the same wide workstation hierarchy as `UI.png`, not the current
  stack of interchangeable card columns.
- Normal direct MIDI import needs one confirmation and one obvious preparation
  action; advanced controls do not compete with the primary flow.
- Selecting Lo-fi MIDI Feel changes the source actually heard in preview and
  used for cohesion, arrangement, piano rendering, caching, and provenance.
- Approved occurrence MIDI—not repaired MIDI by accident—is the source piano
  in arranged output.
- Piano, bass, drums, pad, strings, transitions, visual timeline, and audio
  stems use one timeline, remain aligned at every section boundary, and do not
  accumulate drift.
- Every success state is backed by a current validated artifact; every failure
  has one clear recovery action; source evidence remains immutable.
- Automated suites, visual goldens, real-device listening, keyboard checks, and
  the relevant package build are recorded with their actual results.

## Explicit non-goals

- A full piano-roll/DAW editor or arbitrary note-by-note editing.
- Variable Lo-fi tempo/swing controls unless separately approved; this plan
  repairs the existing fixed 80 BPM/58% profile.
- Cloud storage, telemetry, online weather/maps, live collaboration, or
  automatic downloads.
- Hiding worker, renderer, library, model, rights, or artifact failures behind
  optimistic UI states.
- Deleting stale artifacts automatically or modifying imported source/raw MIDI.
- Implementing deferred Tasks 060–062 or broad unrelated refactors.
