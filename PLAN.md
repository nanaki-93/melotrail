# Melotrail MIDI Core Plan

Status: MIDI Core execution in progress; task evidence is recorded in the execution log

Last updated: 2026-08-28

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

This plan supersedes every earlier root or `docs/plan/` roadmap. Git history is
the archive for those plans.

## 2. Product goal

Given one user-authored Standard MIDI file and explicit musical authority,
Melotrail helps the musician create a coherent accompaniment without replacing
the DAW or taking ownership of the composition.

The MVP succeeds when a user can:

1. create a project;
2. import a valid SMF format 0 or 1 file;
3. designate one immutable melody track;
4. confirm fixed tempo, meter, key, sections, and section harmony;
5. audition the source and arrangement through MIDI playback;
6. generate multiple deterministic chord, bass, and drum candidates;
7. approve, reject, or regenerate a role within one section;
8. review the assembled song without losing accepted work; and
9. import the exported package successfully into Logic Pro.

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
- One track is selected as the protected melody.
- Other imported tracks may be retained as immutable references but are not
  arrangement authorities and are not mutated.
- The original source bytes and SHA-256 digest are preserved.
- Internal processing uses a normalized semantic event model; byte-identical
  round-trip output is not required.

### 4.2 Supported musical context

- One fixed tempo.
- One fixed time signature.
- One project key and mode.
- An ordered section/occurrence timeline with exact beat and tick boundaries.
- Authoritative chord events with explicit durations; sub-bar changes are
  allowed.

Tempo maps, meter changes, SMPTE division, multiple source files, and ambiguous
track selection are rejected with actionable explanations in V1.

### 4.3 Validation policy

Blocking failures are structural or unsafe: unreadable MIDI, unsupported
division, unpaired note events that cannot be interpreted safely, invalid tick
ranges, missing melody selection, or authority that cannot cover the song.

Polyphony, chromatic harmony, unusual ranges, repeated notes, controller use,
and musical density are findings unless a specific target-role invariant makes
them unsafe. Key compatibility is advisory; project harmony remains
authoritative.

## 5. Musical authority and candidate safety

The following records are authoritative:

- source MIDI identity;
- selected melody track;
- tempo and meter;
- project key and mode;
- section definitions and ordered occurrences;
- chord events and durations;
- protected melody anchors; and
- accepted candidate references.

The selected source melody is immutable. Optional connection notes or melody
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
2. **MIDI** — import, inspect tracks, select melody, validate, and audition.
3. **Structure & Harmony** — edit occurrences, exact lengths, key, and chords.
4. **Arrange** — generate alternatives by role and section.
5. **Review** — compare, mute/solo, approve, lock, and inspect findings.
6. **Export** — create and verify the DAW MIDI package.

The UI must expose the state required to make a decision. It must not expose
obsolete audio stages or recreate a general DAW mixer.

MIDI audition is required in the MVP. It may use a system MIDI synthesizer,
selected MIDI output, or another small adapter proven on the supported desktop.
Preview timbre is non-authoritative and is never exported as audio.

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

### Phase 5 — export hardening and product acceptance

- Complete separate-role files and manifest.
- Enforce deterministic ordering, channel/controller policy, atomic export,
  collision handling, and semantic re-import.
- Run automated fixtures, holdout musical reviews, and the Logic Pro
  compatibility matrix.
- Resolve blockers through targeted changes only.

Gate: every criterion in `docs/QUALITY_GATES.md` passes.

### Phase 6 — destructive cleanup and simplification

- Resolve exact repository-owned old project and generated-artifact locations.
- Delete old audio projects and obsolete bundled media.
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

## 13. Completion definition

The MIDI Core migration is complete only when:

- the six-page desktop workflow is functional;
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
`MC-060` using `docs/plan/EXECUTE_MIDI_CORE_TASKS_PROMPT.md`, and record every
task, commit, validation result, manual gate, deletion, and final sign-off in
`docs/plan/MIDI_CORE_EXECUTION_LOG.md`.

No implementation agent may execute an obsolete task suite, skip a task, hide
a failed gate, or begin the optional enhancements before MIDI Core acceptance.
