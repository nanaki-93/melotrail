# Melotrail MIDI Core Plan

Status: MIDI Core execution in progress; product-acceptance holdout is paused for
the arrangement UX remediation defined below

Last updated: 2026-09-04

Authority: this is the only active Melotrail roadmap

## 1. Decision

Melotrail will become a focused desktop MIDI-arrangement tool that supports a
musician's Logic Pro workflow. GarageBand is unverified and not a supported
destination.

The current audio-production platform is superseded. Its output quality did not
justify its complexity. Old audio projects do not require migration, and the
Python worker, audio/transcription pipeline, rendering stack, mixing/mastering,
video, publishing, commercial-release workflow, and unused UI must be removed
after the MIDI replacement path is proven.

The Compose Desktop UI remains a core product surface. This is a product
simplification, not a conversion to a headless library or CLI-only utility.

The UX remediation in this revision does not change the product into a
model-generated music service. It keeps the deterministic MIDI generators,
musical authority, immutable candidates, and Logic Pro handoff. The change is
how quickly the musician can hear a coherent whole-song proposal and refine the
few parts that need attention.

This plan supersedes every earlier root or `docs/plan/` roadmap. Git history is
the archive for those plans.

## 2. Product goal

Given one user-authored Standard MIDI file and explicit musical authority,
Melotrail helps the musician create a coherent accompaniment without replacing
the DAW or taking ownership of the composition.

The MVP succeeds when a user can:

1. create a project;
2. import a valid SMF format 0 or 1 file containing the whole song as exactly
   one note-bearing melody track;
3. have that melody track protected automatically during import;
4. confirm fixed tempo, meter, key, whole-bar sections, and section harmony;
5. audition the source and arrangement through MIDI playback;
6. generate multiple deterministic chord, bass, and drum candidates;
7. approve, reject, or regenerate a role within one section;
8. review the assembled song without losing accepted work; and
9. import the exported package successfully into Logic Pro.

The primary arrangement path must feel like **whole song first, exceptions
second**. Once authority is complete, the musician should be able to audition a
style, create a complete draft, and hear that draft without manually generating
and accepting every section/role combination first.

## 3. Non-goals

The target product does not:

- import audio;
- transcribe audio to MIDI;
- separate stems;
- repair, denoise, normalize, mix, master, or encode audio;
- render WAV, MP3, AAC, AIFF, or CAF;
- manage SFZ files, samplers, or commercial sound libraries;
- replace Logic Pro instrument selection;
- edit video or prepare publishing releases;
- assess copyright, monetization, or platform eligibility;
- accept unrestricted model-written music;
- migrate old audio projects; or
- preserve legacy runtime branches after their replacement is accepted.

## 4. V1 input contract

### 4.1 Source

- Exactly one `.mid` or `.midi` file per project.
- Standard MIDI format 0 or 1.
- Exactly one track contains notes, on exactly one MIDI channel, and it spans
  the complete song.
- The importer protects that melody automatically; there is no track-selection
  step or later source switch inside the project.
- Additional non-note tracks may retain conductor or reference events, but they
  cannot contain notes.
- The original source bytes and SHA-256 digest are preserved.
- Internal processing uses a normalized semantic event model; byte-identical
  round-trip output is not required.

### 4.2 Supported musical context

- One fixed tempo.
- One fixed time signature.
- One project key and mode.
- An ordered section/occurrence timeline entered as positive whole-bar lengths.
- Section bar counts must total the imported melody length exactly; contiguous
  tick boundaries are derived from PPQ and the confirmed meter.
- Authoritative chord events with explicit durations; sub-bar changes are
  allowed.

Tempo maps, meter changes, SMPTE division, multiple source files, and extra
note-bearing tracks/channels are rejected at import with actionable
explanations in V1. A source ending outside a whole-bar boundary in the
confirmed meter blocks structure save with the same standard of explanation.

### 4.3 Validation policy

Blocking failures are structural or unsafe: unreadable MIDI, unsupported
division, unpaired note events that cannot be interpreted safely, invalid tick
ranges, an invalid single-melody shape, or authority that cannot cover the song.

Polyphony, chromatic harmony, unusual ranges, repeated notes, controller use,
and musical density are findings unless a specific target-role invariant makes
them unsafe. Key compatibility is advisory; project harmony remains
authoritative.

## 5. Musical authority and candidate safety

The following records are authoritative:

- source MIDI identity;
- automatically protected melody track;
- tempo and meter;
- project key and mode;
- section definitions and ordered occurrences;
- chord events and durations;
- protected melody anchors; and
- accepted candidate references.

The protected source melody is immutable. Optional connection notes or melody
edits are separate candidates with an event-level diff and explicit approval.
They are not required for MVP completion.

Every generated candidate carries:

- stable candidate ID;
- role and section occurrence ID;
- generator version;
- input authority hash;
- deterministic seed;
- MIDI artifact hash;
- validation report; and
- creation timestamp.

Acceptance points to a candidate; it never rewrites it. Regeneration creates a
new candidate and leaves previously accepted material recoverable.

## 6. Core roles

### 6.1 Chords / keys

- Performs only authoritative chord tones and supported extensions.
- Provides a small curated set of complete rhythmic patterns.
- Uses bounded register, spacing, and voice-leading rules.
- Avoids protected melody space and leaves room for bass.

### 6.2 Bass

- Follows authoritative chord roots and validated approach tones.
- Offers sustained/sub-like and muted/plucked performance profiles.
- Uses section purpose, phrase boundaries, kick intent, and melody activity.
- Avoids uncontrolled leaps, invalid range, and melody collisions.

### 6.3 Drums

- Uses complete authored groove variants rather than deleting arbitrary steps
  from one dense pattern.
- Supports section-aware energy, fills, and phrase boundaries.
- Coordinates kick intent with the accepted bass candidate.
- Uses the General MIDI percussion channel in export.

### 6.4 Optional post-MVP roles

Pad and constrained Qwen suggestions are separate post-MVP decisions. Neither
may delay deterministic core acceptance or become a mandatory runtime
dependency.

## 7. Desktop workflow

The target workspace has six focused destinations:

1. **Project** — create/open project and show authority summary.
2. **MIDI** — import, automatically protect, inspect, validate, and audition the
   single melody track.
3. **Structure & Harmony** — enter ordered section lengths in bars, then edit
   key and chord windows.
4. **Arrange** — audition a curated whole-arrangement style, inspect the song
   map, create a complete draft, and refine selected exceptions.
5. **Review** — listen to the assembled draft, use it as the accepted
   arrangement, or compare alternatives only where a section/role needs work.
6. **Export** — create and verify the DAW MIDI package.

The UI must expose the state required to make a decision. It must not expose
obsolete audio stages or recreate a general DAW mixer.

MIDI audition is required in the MVP. It may use a system MIDI synthesizer,
selected MIDI output, or another small adapter proven on the supported desktop.
Preview timbre is non-authoritative and is never exported as audio.

### 7.1 Arrangement UX principle

The default flow is **whole song first, exceptions second**:

1. Complete Structure & Harmony once.
2. Open Arrange and click a style. The current section begins a short MIDI
   preview automatically.
3. Click **Create full draft**. Melotrail generates Chords, Bass, and Drums for
   every occurrence in dependency order and groups them as one draft.
4. Listen to the complete draft immediately. Draft playback does not require
   accepting each candidate first.
5. Click **Use this draft** to accept every valid selected candidate together,
   or select a problem section and regenerate only its role or the whole
   section.
6. Export after all required scopes have an accepted candidate.

A draft is an orchestration record that groups immutable scoped candidates; it
is not a new audio artifact or a second musical representation. Creating a new
draft never overwrites a previous candidate or accepted reference. **Use this
draft** performs one validated project mutation and must fail without partial
acceptance if any selected candidate is stale, missing, or blocking.

The batch generator remains deterministic. It runs Chords before Bass and Bass
before Drums where a downstream role depends on accepted or draft context. It
reports progress by occurrence and role, supports cancellation, and retries
only failed or stale scopes. Repeated section names remain separate
occurrences, while the selected style supplies related, section-aware
variations so a repeated chorus is coherent without being mechanically
identical.

### 7.2 Persistent player

Playback belongs to the workspace shell, outside every page's vertical scroll
container. One fixed transport dock remains visible across all six
destinations, at both wide and compact supported window sizes. Scrolling or
navigating must not hide it, stop playback, reset the playhead, or create a
second page-local transport.

The always-visible surface contains only:

- current playback target and section;
- play/pause, stop, and playhead position;
- loop on/off; and
- a clear switch among source, current preview/draft, and accepted arrangement
  when those targets exist.

MIDI output selection, seek-to-boundary actions, role mute/solo, and device
problems belong in an expandable playback panel opened from the dock. The
expanded panel must not obscure the primary Arrange action. All controls need
keyboard focus, accessible names, and at least the existing minimum hit target.
Device loss stops safely and explains the next action without discarding the
current selection.

### 7.3 Song map and section workspace

Arrange and Review share one horizontally navigable song map above their main
content. It is derived from authoritative occurrences and shows each occurrence
as a bar-proportional block with:

- musical label and occurrence number where names repeat;
- bar range and compact chord summary;
- section purpose/energy when available;
- draft and acceptance status for Chords, Bass, and Drums; and
- the live playhead and current loop selection.

Clicking a block selects and loops that occurrence without moving the player or
losing the user's scroll position. The selected section workspace shows its
three role statuses together and offers **Regenerate section** plus a
progressively disclosed **Adjust roles** control. Individual profile and
pattern choices remain available for advanced correction, but they are not part
of the default whole-song path. Previous/next-section navigation and clear
not-generated, ready, accepted, stale, and needs-attention states prevent users
from hunting through dropdowns.

Structure boundaries and chord windows are still edited only in Structure &
Harmony. Arrange may link to the relevant occurrence but must not create a
second authority editor.

### 7.4 Click-to-preview arrangement styles

The primary choice is a small catalog of four to six musically named
arrangement styles, not separate technical profile and pattern dropdowns for
every role. Each versioned style definition maps Chords, Bass, and Drums to the
existing deterministic performance profiles, complete patterns, fill policy,
section-energy behavior, and bounded generator settings. Style names and short
descriptions explain audible intent; internal IDs remain available only in
advanced details and evidence.

Selecting a style by mouse or keyboard immediately replaces the active preview
with a two-to-four-bar loop from the selected occurrence. The preview uses the
project's protected melody slice, authoritative harmony, and all three style
roles through the MIDI audition adapter. It is ephemeral: it creates no
candidate, acceptance, project revision, or exportable audio. **Create full
draft** is the explicit persistence boundary.

Preview requests are keyed by authority hash, style version, occurrence, and
seed. The application caches completed previews, cancels or ignores superseded
requests, debounces rapid navigation, and never layers two preview sessions.
The first click may show a short preparing state; warm switches should sound
effectively immediately. A missing MIDI output produces an actionable inline
message while leaving the style selected.

### 7.5 Interaction rules that keep the flow smooth

- Keep one primary action visible for the current state: preview a style,
  create a draft, use the draft, fix an exception, or export.
- Preserve the selected style, section, loop, playback target, and playhead when
  moving between Arrange and Review.
- Do not use modal dialogs for routine generation, playback, acceptance, or
  navigation.
- Generate in the background with visible scoped progress; let the user inspect
  completed sections while remaining scopes finish when this is safe.
- Allow **Regenerate section** and **Regenerate role** directly from the song
  map; never make the user reconstruct the global style choice.
- Surface validation blockers beside the affected section/role and provide the
  exact next action. Keep non-blocking musical findings in contextual details.
- Offer undo for the latest acceptance change by restoring the prior candidate
  references; never mutate candidate artifacts.
- Keep expert controls discoverable but collapsed. The first-run path must not
  ask for profile IDs, pattern IDs, seeds, raw ticks, MIDI channels, or artifact
  paths.
- Do not add a piano roll, audio waveform, mixer, instrument browser, model
  prompt, or audio renderer to solve these interaction problems.

### 7.6 Visual hierarchy and responsive layout

The workspace should feel like a compact musical tool, not a sequence of large
forms. Every screen must have one obvious focal area and must use space to show
musical relationships rather than repeating status text.

For the shell:

- compress the current project header into a single toolbar row; keep the
  project name visible, but move the full filesystem path and revision into
  project details;
- show active work as a compact progress indicator and show failures as an
  actionable banner near the affected control instead of reserving a large
  permanent status card;
- keep the six destinations, but use short labels and clear current/completed/
  blocked states rather than large identical navigation buttons; and
- remove generic duplicate page descriptions from the wide context rail. Use
  that space for the selected section inspector on Arrange/Review, and collapse
  it when it has no contextual decision to support.

The wide Arrange layout is:

```text
compact project toolbar
song map / playhead / section statuses
style gallery + full-draft action | selected-section inspector
persistent playback dock
```

The compact layout keeps the same order in one vertical work area, makes the
song map horizontally scrollable, and uses a reduced-height playback dock. The
dock is outside that work area's scroll container. No primary control may be
hidden behind it.

Cards should be reserved for meaningful selectable objects such as styles,
sections, and candidates. Headings, status summaries, and sequential steps
should use lighter grouping so every part of the page does not compete at the
same visual weight. Selected, playing, generated, accepted, warning, and stale
states need distinct text/icon treatment and must not rely on color alone.
Spacing, typography, focus rings, contrast, truncation, and reduced-motion
behavior must be covered by wide and compact visual fixtures.

## 8. Export contract

Each immutable export snapshot contains:

- `complete-song.mid` as SMF format 1;
- `melody.mid`;
- `chords.mid`;
- `bass.mid`;
- `drums.mid`;
- optional role files only when that role is enabled; and
- `manifest.json`.

The complete file contains a conductor/meta track plus separate named role
tracks. Tempo, meter, supported markers, channel policy, tick resolution,
program-change policy, controller policy, and deterministic track ordering are
defined in `docs/MIDI_CONTRACT.md`.

The manifest records musical authority, source and candidate hashes, role
presence, performance profiles, optional DAW instrument suggestions, generator
versions, validation summaries, and filenames.

Export is written to a staging directory, validated by re-import, and moved
atomically. An existing export is never silently overwritten.

## 9. Target architecture

The target application is one Kotlin/JVM desktop application with clear domain
boundaries:

```text
Compose Desktop UI
    -> application use cases
        -> project and musical authority
        -> MIDI import / semantic model / writer
        -> structure and harmony
        -> deterministic arrangement
        -> candidate review and acceptance
        -> MIDI audition
        -> DAW export
    -> filesystem artifact store
```

There is no worker process and no audio canonical representation. Detailed
component and dependency rules are in `docs/ARCHITECTURE.md`.

## 10. Reuse policy

Reuse behavior, not obsolete architecture. Candidate implementation should
extract and characterize useful current capabilities such as:

- Java MIDI parsing/writing behavior;
- canonical key, harmony, and occurrence logic;
- curated bass, chord-rhythm, drum, fill, and pad patterns;
- deterministic bass and drum generation;
- voice leading and collision evidence;
- stable IDs, hashes, lineage, atomic artifact writes, and stale invalidation;
- focused Compose components that remain useful; and
- regression fixtures for known-good MIDI behavior.

Do not preserve an old service, schema, page, dependency, or stage merely
because one helper inside it is useful. Extract the helper behind a target
contract, prove it, and delete the old owner.

## 11. Delivery sequence

### Phase 0 — documentation and authority

- Establish this plan as the only active roadmap.
- Define architecture, functions, MIDI contract, DAW boundary, cleanup scope,
  and quality gates.
- Mark unavoidable old documentation contracts as transitional.
- Do not begin the old guided-arranger task suite.

Gate: documentation has no competing active product direction.

### Phase 1 — compatibility and safety spike

- Capture small legal/test-owned SMF 0 and 1 fixtures.
- Characterize current MIDI parsing and writing that may be reused.
- Prove a minimal format-1 conductor-plus-role export.
- Import the result into the current supported version of Logic Pro.
- Record channel, marker, tempo, track-name, and instrument-assignment results.

Gate: one minimal package passes semantic re-import plus the Logic Pro check.

### Phase 2 — MIDI project kernel

- Introduce the new project schema and artifact layout.
- Implement source import, immutable identity, track inspection, melody
  selection, validation, authority editing, and exact occurrence timelines.
- Implement candidate and export snapshot records.
- Add minimal MIDI audition.
- Explicitly reject legacy audio projects; do not migrate them.

Gate: a project can be created, saved, reopened, audited, and auditioned without
the worker or an audio artifact.

### Phase 3 — complete vertical arranger slice

- Generate one simple validated chords candidate.
- Generate one simple validated bass candidate.
- Generate one simple validated drums candidate.
- Accept one candidate per role/section.
- Assemble and export the complete song.
- Verify that regeneration preserves accepted and prior candidates.

Gate: import-to-export succeeds before any role engine is deeply expanded.

### Phase 4 — focused arrangement UI and musical depth

- Replace the old navigation with the six target destinations.
- Add per-section alternatives, compare, mute/solo, lock, reject, and targeted
  regeneration.
- Adapt curated rhythm, voicing, bass, groove, and fill behavior.
- Add clear validation findings and candidate diffs.
- Keep MIDI audition responsive and non-authoritative.

Gate: the user can complete the workflow without entering an old audio page or
editing project files manually.

### Phase 5 — arrangement UX remediation, export hardening, and acceptance

- Complete separate-role files and manifest.
- Enforce deterministic ordering, channel/controller policy, atomic export,
  collision handling, and semantic re-import.
- Keep `MC-049` holdout listening paused while the arrangement UX is repaired.
- Add and execute the following work between completed `MC-048C` and `MC-049`:
  - `MC-048D` — move playback state and transport into a persistent,
    scroll-independent workspace dock; remove the duplicate Review transport;
  - `MC-048E` — define versioned arrangement-style bundles and implement
    cancellable, non-persistent click-to-preview MIDI audition;
  - `MC-048F` — add deterministic full-draft orchestration, grouped draft
    identity, progress/cancellation, partial-failure recovery, complete draft
    playback, and atomic **Use this draft** acceptance;
  - `MC-048G` — replace the section/role dropdown-first Arrange page with the
    song map, style chooser, full-draft action, and exception workspace;
  - `MC-048H` — simplify Review around whole-draft listening, batch acceptance,
    focused comparison, targeted regeneration, and acceptance undo; and
  - `MC-048I` — complete wide/compact visual regression, keyboard/accessibility,
    performance, workflow, and musician usability evidence, then update the
    functional, architecture, audition, quality-gate, and task documentation.
- Run automated fixtures, holdout musical reviews, and the Logic Pro
  compatibility matrix only after `MC-048I` passes.
- Resolve blockers through targeted changes only.

Gate: the arrangement UX gates in section 12 pass, the supporting contracts are
synchronized, and every criterion in `docs/QUALITY_GATES.md` passes.

### Phase 6 — destructive cleanup and simplification

- Resolve exact repository-owned old project and generated-artifact locations.
- Delete old audio projects and obsolete bundled media.
- First replace the legacy screenshot-measured UI with the six-page MIDI
  workspace visual system and its target visual regression fixtures. Delete the
  old UI references only after the new fixtures and focused desktop smoke pass.
- Delete the Python worker, requirements, environments/contracts, HTTP client,
  and worker tests.
- Delete audio import/transcription, DSP, renderer, mix/master, sound-library,
  video, publishing, commercial-release, legacy schema/stages, and obsolete UI.
- Delete transitional documentation, fixtures, tools, tests, dependencies, and
  Make targets with their owners.
- Remove empty packages and rename retained concepts to match the target domain.

Gate: source and build scans find no Python/audio-production runtime, and the
desktop builds and tests using only the target commands.

### Phase 7 — optional enhancements

Only after MVP acceptance, separately evaluate:

- pad generation;
- suggestion-only Qwen planning;
- additional fixed tempo/meter profiles;
- expanded DAW metadata; and
- a more capable MIDI audition adapter.

Optional work must have its own value hypothesis and may be rejected without
affecting the core product.

## 12. Quality strategy

Automated validation proves structure, determinism, authority preservation,
candidate immutability, and export integrity. It cannot prove that an
arrangement is enjoyable.

Musical acceptance therefore uses:

- three development fixtures;
- at least ten unseen holdout projects;
- per-role and per-song minimum scores, not only an average;
- correction-time and rejection-rate observations;
- protected-melody comparison; and
- manual Logic Pro import checks.

The exact gates and evidence format are in `docs/QUALITY_GATES.md`.

Before `MC-048D` implementation begins, the corresponding detailed tasks and
contract updates must be added to the execution suite. The UX remediation then
adds these mandatory gates:

### 12.1 Workflow gates

- From an authority-complete project, a user can start hearing a style with one
  style-card action and can start complete-draft playback with no more than
  three primary actions: choose style, create draft, play draft.
- No per-section or per-role acceptance is required before complete-draft
  playback.
- A valid complete draft can be accepted with one **Use this draft** action;
  invalid or stale scopes block the entire batch and identify their song-map
  locations.
- A user can select a section and trigger section regeneration in at most two
  primary actions, or select a role and trigger role regeneration in at most
  three.
- The fixed player and its essential controls remain visible while the longest
  Arrange and Review pages are scrolled at the minimum supported compact and
  wide window sizes.
- Arrange and Review preserve style, section, playback target, loop, and
  playhead context when the user moves between them.

### 12.2 Responsiveness and reliability gates

- After MIDI output warm-up, cached or newly prepared style previews begin
  audible playback within 300 ms at the 95th percentile on the reference
  development machine; a cold first preview begins within one second.
- Rapid style or section changes produce only the latest requested preview and
  never overlap note streams or leave hanging notes.
- Full-draft generation exposes per-scope progress, remains cancellable, and
  can retry failed scopes without regenerating valid scopes.
- Previewing or cancelling does not change the project revision, candidate
  inventory, accepted references, or source artifacts.
- The same authority, style version, settings, and seed produce the same scoped
  candidate bytes and draft membership.
- Playback continues correctly across destination changes and stops cleanly on
  project close, source replacement, stale authority, or MIDI-device loss.

### 12.3 Usability evidence

- Run five observed arrangement sessions before the ten-project musical
  holdout. At least three sessions must use a musician who did not implement
  the feature.
- For an authority-complete project, the median observed time to a first
  complete-draft listen is at most two minutes, excluding deliberate listening
  time; no participant may need to discover advanced profile/pattern controls
  to reach it.
- Record action count, time to first sound, time to first complete draft,
  abandoned actions, wrong-scope regenerations, and whether the musician could
  explain which section and playback target were active.
- Any repeated confusion shared by two participants is a product-acceptance
  blocker even if automated tests pass.

## 13. Completion definition

The MIDI Core migration is complete only when:

- the six-page desktop workflow is functional;
- one persistent player remains visible and usable across scrolling and page
  changes;
- a musician can preview a coherent arrangement style from the current song,
  create and hear a complete draft, and accept it without a section-by-section
  setup loop;
- the song map makes section boundaries, occurrence identity, playback context,
  draft status, and acceptance gaps clear;
- one MIDI source becomes a reviewable, DAW-ready arrangement;
- generated chords, bass, and drums obey project authority;
- accepted work cannot be silently overwritten;
- exported files pass semantic re-import and the Logic Pro check;
- old audio projects require no migration and have been removed from the
  repository-owned project locations;
- Python and all audio-production code, tests, dependencies, assets, commands,
  and docs are gone;
- no competing plan or architecture remains; and
- `make test` and `make build` pass from a clean checkout.

## 14. Execution artifacts

The mandatory implementation sequence is
`docs/plan/MIDI_CORE_TASKS.md`. Execute it strictly from `MC-000` through
`MC-060`, including `MC-048A` through `MC-048I` after `MC-048`, using
`docs/plan/EXECUTE_MIDI_CORE_TASKS_PROMPT.md`, and record every task, commit,
validation result, manual gate, deletion, and final sign-off in
`docs/plan/MIDI_CORE_EXECUTION_LOG.md`. The task suite and execution prompt must
include `MC-048D` through `MC-048I` before implementation resumes. Each new
task updates its owning functional, architecture, audition, visual, and quality
contracts before changing their implementations; `MC-049` must not start
against the superseded dropdown-first Arrange/Review flow.

No implementation agent may execute an obsolete task suite, skip a task, hide
a failed gate, or begin the optional enhancements before MIDI Core acceptance.
