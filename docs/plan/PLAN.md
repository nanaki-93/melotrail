# Melotrail canonical melody and release-quality pipeline

Status: implementation required

Baseline reviewed: 2026-08-24

Canonical project format: schema v4

Task IDs: QP-001 through QP-018

## Product outcome

Starting with independently performed intro, verse, chorus, bridge, and optional
outro melody sources, Melotrail must build one musically coherent song whose
melody:

- is aligned to the authoritative project tempo, meter, beats, and section bars;
- is adapted from its confirmed source key and mode into the project key/mode;
- respects the project scale and the active authoritative section harmony;
- contains exactly one melodic note at a time;
- is assembled into one approved full-song MIDI melody before arrangement;
- retains stable occurrence structure for arrangement, transitions, review, and
  provenance;
- preserves recognizable protected anchors after deterministic preparation;
- is the exact piano/melody input used by arrangement, Cohesion, humanization,
  rendering, criticism, and release evidence.

The final product target is original, release-quality music suitable for human
review and YouTube release preparation. Melotrail can provide technical,
musical, rights, AI-use, and lineage evidence. It cannot guarantee copyright or
Content ID clearance, audience response, advertiser suitability, YouTube
Partner Program admission, or monetization.

## Verified current defects

The current implementation has useful stages but does not yet satisfy the
outcome:

1. Source performances have different detected tempos and pickups. Normalizing
   tempo metadata does not warp performed beats or detect the downbeat.
2. Occurrence duration is copied exactly, so fractional-bar source lengths move
   later sections progressively off the project bar grid.
3. QP-004 maps recognized source scale degrees when source and project modes
   differ, but intentionally retains unresolved chromatic notes as evidence for
   the later harmony-fit gate.
4. Selected source MIDI remains immutable and can retain its original texture;
   QP-005 publishes a separate controller-aware monophonic source candidate and
   QP-006 publishes a further occurrence-local harmony-fitted candidate.
   Ambiguous/excessive repairs block with note-level evidence rather than altering
   authority or selected MIDI.
5. QP-007 now assembles those harmony-fitted candidates into an immutable,
   two-track canonical full melody with occurrence/lineage/harmony/groove
   sidecar evidence; Melody Connection and Source Song Critic use its global
   note identity.
6. Source-song approval is checked before arrangement, but arrangement state,
   humanization, and rendering later reconstruct piano from occurrence
   artifacts instead of consuming the exact approved connected full melody.
7. Selected artifact precedence can choose Enhanced while ignoring a selected
   MIDI Feel derivative.
8. Source and full-song critics report serious problems, but explicit or silent
   bypass paths can still produce a successful build.
9. Cohesion derives supported instruments globally instead of at each boundary;
   `CONTINUITY` can become a drum fill, and bridge renderers do not execute all
   declared musical intent fields.
10. Existing automated tests prove serialization, bounds, hashes, and output
    existence more strongly than they prove meter, groove, harmony, melodic
    identity, or listening quality.
11. QP-005 materializes pedal-extended sounding intervals before monophony, and
    QP-006 applies the versioned tempo/PPQ-derived chord/occurrence boundary-
    release policy with explicit common-tone/suspension evidence.
12. Generated bass and kick can share the same 50–150 Hz region. The mixer has
    filter, EQ, and compression primitives, but the default plan has no
    kick-triggered bass ducking and the current overlap critic is only a generic
    warning.
13. Pad and string generators choose smooth voicings inside one generation
    request, but independent section requests reset their previous-voicing
    state. Section boundaries can therefore introduce avoidable octave jumps.
14. Drum and bass generation use an exact grid or independently seeded timing
    variation. Neither role consumes a robust micro-timing template derived
    from the approved source performance, so accompaniment can flam against the
    piano even after global tempo alignment.
15. The mastering worker already measures gated loudness and oversampled true
    peak and applies a ceiling. The remaining operational risk is proving that
    the selected release artifact used that path and remains inside the
    versioned policy after a representative local lossy-codec round trip.

These are pipeline defects, not an invitation to replace authoritative project
harmony or broadly rewrite working DSP.

The file-level evidence and the specific `EnsembleCohesion.kt` diagnosis are in
[`PROJECT_ANALYSIS.md`](PROJECT_ANALYSIS.md).

## Musical authority

Authority order is explicit:

1. User-authored project structure, key/mode, tempo, meter, and section harmony.
2. The approved prepared full melody and its protected anchors.
3. Approved arrangement decisions and generated-role validation.
4. Bounded Cohesion, targeted polish, and seeded humanization.
5. Mix and production decisions.

Detected tempo, downbeats, key, chords, and note confidence are evidence. They
never overwrite declared authority without a reviewed derived candidate.

Project scale and harmony can legitimately interact. A pitch is harmonically
eligible when it is a project-scale tone or a chord tone explicitly authorized
by the active user-authored chord. Stable or exposed melody notes must be active
chord tones; short weak-position scale tones may be passing or neighbour tones.
If project settings and harmony cannot produce a valid stable tone for a span,
the project is blocked for correction rather than silently reinterpreted.

## Target pipeline

```text
Project Setup + authoritative Harmony
  -> immutable source import
  -> audio/MIDI inspection and transcription cleanup
  -> beat/downbeat evidence
  -> project-grid timing conformance, source-groove evidence,
     and explicit pickup/body/tail mapping
  -> confirmed source-key and mode-aware project-key transposition
  -> selected technical correction / AI Fix / per-track Enhance / Feel chain
  -> monophonic source reduction with reviewable discarded-note evidence
  -> sustain-aware effective-note analysis and chord-boundary release conformance
  -> section-scale and active-harmony melody repair
  -> protected-anchor derivation on the prepared melody
  -> one canonical full-song melody + stable structure/harmony sidecar
  -> melody-boundary connection
  -> strict Source Song Critic and explicit approval
  -> arrangement plan and deterministic role generation
     using one bounded full-song groove map
  -> core validation and approval
  -> cross-section voice-leading and boundary-local Ensemble Cohesion
  -> deterministic Full-Song Critic
  -> optional targeted improvement that must improve code-owned metrics
  -> seeded Humanization
  -> render, kick/bass interaction control, production mix,
     audio criticism, master
  -> selected-master and representative lossy-codec validation
  -> originality/provenance/AI-use/release review
  -> export
```

No later stage may rebuild the source melody from independent part files. A
consumer either resolves the exact approved canonical melody hash or fails with
an actionable stale/prerequisite error.

## Canonical full-melody artifact

The prepared source-song MIDI contains:

- one conductor/meta track for project tempo, meter, section markers, and end
  time;
- exactly one note-bearing melody track;
- canonical PPQ and global tick positions;
- no drum-channel melody events;
- no overlapping notes at any pitch or channel;
- no uncontrolled sustain-controller state or pedal-extended sounding note
  crossing a chord/occurrence boundary;
- no note crossing an occurrence boundary unless an explicit tied-boundary
  policy records and validates it.

Its versioned sidecar contains:

- project-authority and processor versions/hashes;
- every stable occurrence ID, section type, source part, order, start/end
  bar/tick, and pickup/body/tail classification;
- active chord spans;
- original, normalized, transposed, selected, prepared, connected, and approved
  artifact hashes;
- note-level timing, pitch, monophony, and harmony mutation evidence;
- controller materialization, boundary release, per-source groove evidence, and
  one accepted occurrence-indexed full-song groove map;
- removed/deduplicated-note evidence and ambiguity/blocking findings;
- protected melody identity and anchor IDs derived after deterministic repair.

The artifact is content-addressed and immutable. Regeneration publishes a new
candidate and invalidates dependents; it never overwrites a known-good source or
approved candidate.

## Separation of responsibilities

### Deterministic code

Owns beat-grid application, bar/pickup mapping, source-groove extraction,
sustain-aware sounding intervals, boundary release, mode-aware transposition,
monophony, pitch eligibility, active-chord lookup, MIDI transformation,
cross-section voice-leading validation, collision detection, low-end
interaction policy, budgets, hashes, validation, cache/invalidation, and
publication.

### Qwen

May propose bounded producer, arrangement, transition, or targeted-correction
choices using strict schemas and controlled vocabularies. It never chooses
project harmony, writes MIDI/files, returns paths, approves itself, or bypasses
code-owned quality gates.

### Ensemble Cohesion

Runs after arrangement against the approved full melody and actual generated
roles. It owns boundary handoffs only. It does not transpose source parts,
repair general melody harmony, reduce polyphony, rebuild the melody, or perform
an unrestricted whole-song rewrite.

### Human review

Owns ambiguous source-key decisions, correction candidates beyond safe budgets,
melodic identity judgments, source-song approval, core arrangement approval,
listening A/B decisions, rights attestations, AI disclosure, and release signoff.

## Delivery phases

| Phase | Tasks | Outcome |
| --- | --- | --- |
| A. Baseline and timing | QP-001–QP-003 | Reproducible quality fixture, beat/downbeat evidence, bar-aligned source MIDI |
| B. Canonical melody | QP-004–QP-008 | Mode-aware pitch preparation and one downstream canonical melody |
| C. Quality gates | QP-009–QP-010 | Correct artifact lineage and strict source approval |
| D. Arrangement and cohesion | QP-011–QP-014 | Expressive validated roles, boundary-local cohesion, improving targeted polish |
| E. Product and production | QP-015–QP-016 | Review UI plus low-end and delivery-master hardening |
| F. Proof and release | QP-017–QP-018 | End-to-end listening evidence, policy/provenance, and cleanup closure |

The binding task details are in [`TASKS.md`](TASKS.md). Later tasks may not be
implemented early unless their contract explicitly identifies a prerequisite
seam needed by the current task.

## Definition of done

The roadmap is complete only when:

- all QP tasks are implemented and individually committed after their required
  checks pass;
- the real four-source quality scenario begins every ordinary occurrence on its
  declared beat/bar or explicit pickup and has no uncontrolled phase drift;
- sustain-extended notes cannot contaminate the following authoritative chord;
- imported key/mode differences are resolved with reviewable evidence;
- the approved full melody is globally monophonic and harmonically valid;
- drums and bass follow one bounded occurrence-indexed full-song groove map, and
  pad/string voice leading remains smooth across section boundaries;
- arrangement, Cohesion, humanization, render, and release lineage all reference
  that exact approved melody;
- critical critic findings cannot silently pass a quality-certified build;
- targeted changes demonstrably reduce rather than increase code-owned issues;
- kick/bass low-end interaction is controlled without pumping, and the exact
  selected delivery master passes the versioned loudness/true-peak policy plus
  its representative lossy-codec preview check;
- automated suites pass and documentation coverage is current;
- renderer/model integration and documented listening A/B gates have recorded
  evidence;
- a release review confirms source/instrument/model rights evidence, appropriate
  AI-use disclosure recommendation, meaningful creator contribution, and no
  unresolved release blockers;
- obsolete branches, flags, DTOs, tests, and documentation replaced by the new
  pipeline are removed in their owning task.
