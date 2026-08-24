# Composition and release quality gates

These gates prevent “a file was produced” from being mistaken for “the song is
musically ready.” Code-owned gates are deterministic. Calibrated gates derive
their thresholds from checked-in fixtures. Listening and release gates require
recorded human decisions and cannot be replaced by unit tests.

## Gate levels

| Level | Meaning | Bypass policy |
| --- | --- | --- |
| Hard invariant | Corrupt, stale, contradictory, or musically invalid canonical state | No quality-certified bypass |
| Blocking quality gate | Valid artifact with a severe musical problem | Private audition allowed; quality/release progression blocked |
| Review warning | Ambiguous or subjective concern | Explicit review decision with reason |
| Advisory metric | Useful comparison that is not independently decisive | Never represented as pass/fail truth |

An experimental/private export may retain an explicit bypass where product
policy permits. Such an export must not be labeled quality-certified,
commercial-ready, or monetization-ready.

## Timing and structure

Hard invariants:

- every source and output MIDI uses supported PPQ timing;
- every ordinary occurrence starts and ends on its canonical bar boundary;
- a pickup or tail exists only through an explicit typed window and cannot
  silently shift the next occurrence;
- every note belongs to exactly one occurrence or an explicit validated tie;
- no section or full-song event exceeds the canonical timeline;
- beat/downbeat evidence, time mapping, input/output hashes, and processor
  version are present;
- the canonical melody and generated roles share the same global project grid.

Blocking quality checks include excessive beat-map residual, accumulated phase
drift, a missing downbeat, a boundary with no playable melody, unplanned long
silence, or a melody note crossing a boundary. Exact numeric tolerances must be
calibrated in QP-001/QP-003 against PPQ-scaled fixtures and stored as a versioned
policy; do not bury magic tick constants in individual validators.

## Key, scale, and harmony

Hard invariants:

- project key/mode and section harmony are present, executable, and unchanged;
- low-confidence source-key evidence is not automatically confirmed;
- mode-aware transposition reports every pitch movement and preserves timing,
  duration, velocity, controllers, meter, tempo events, and percussion policy;
- every prepared melody note is a project-scale tone or an explicitly active
  authoritative chord tone;
- every stable/exposed note is an active chord tone;
- every repair is bounded, deterministic, hash-bound, and reason-coded;
- an unresolved key/harmony conflict blocks canonical melody approval.

Short, weak-position scale notes may be passing or neighbour tones. “All melody
notes must be chord tones” is not a valid quality rule.

## Monophony and melody identity

Hard invariants:

- the canonical melody has exactly one note-bearing track;
- at every tick, at most one melody note is active regardless of channel or
  pitch;
- drum-channel events are absent from the melody;
- note pairs are closed, positive-duration, ordered, and within MIDI range;
- removed doubles/chord notes and shortened overlaps are persisted as evidence;
- unresolved ambiguous polyphony blocks rather than guesses;
- protected anchors are derived from the prepared canonical melody and survive
  every later selected stage.

Recognizability uses contour, rhythm, anchor, and matched-note evidence. It is a
blocking release gate when the selected signature motif is no longer clearly
present, but its score is not a copyright or originality judgment.

## Artifact and approval lineage

- One resolver owns the composed selected chain: transposed -> corrected -> AI
  Fix -> Enhance -> Feel -> prepared -> connected -> approved.
- A no-edit candidate records `NO_OP`; it must not pretend to be an enhanced
  musical result.
- Every consumer records the approved canonical-melody hash.
- File existence never overrides a stale selection or missing approval.
- A failed optional model operation preserves the last valid input and exposes
  retry/bypass, but cannot silently turn a quality-certified run into bypass.

## Arrangement and role quality

Code-owned validation checks:

- role range, note integrity, canonical harmony, occurrence bounds, and melody
  collision/masking constraints;
- bass and drums use the canonical beat phase and section groove;
- generated density stays within the approved variation plan and ensemble
  capacity;
- model output cannot copy schema examples as unchecked creative defaults;
- section energy/density/instrumentation is not accidentally identical across
  every occurrence;
- optional layers can publish explicit silence when no safe density remains;
- generated roles are admitted one at a time so later roles see accepted prior
  MIDI state.

Arrangement quality also requires a renderer-backed piano/core/full comparison.
Automated note counts cannot approve groove or “vibe.”

## Cohesion and targeted polish

- Cohesion receives boundary-local active, entering, and exiting roles.
- `CONTINUITY` sustains/ties an active role or performs no edit; it never maps
  unconditionally to a drum fill.
- A bridge cannot introduce an instrument inactive on both sides without an
  explicit reviewed entry action.
- Rendered bridge notes execute the declared gesture, harmonic handoff, bar
  length, and energy contour.
- Bass walks and pitched transitions fit active harmony.
- Merged output is revalidated for harmony, groove, masking, range, density,
  monophony, anchors, and bounds.
- Cohesion and targeted polish are selectable only when blocker/critical issue
  counts do not increase and their targeted metrics improve.
- Audit evidence compares actual before/after MIDI, not only the requested plan.

## Full-song critic

The deterministic critic must inspect the entire uncapped issue set before
deciding quality status. A bounded UI summary may be truncated only when the
report preserves total counts and severity/category aggregates.

Quality-certified progression requires:

- zero unresolved source-song blockers;
- zero stale or missing canonical-melody evidence;
- zero protected-anchor violations;
- zero hard timing, monophony, range, or MIDI-integrity violations;
- zero unreviewed critical harmony/groove/masking/transition findings;
- an explicit human decision for remaining warnings.

## Listening acceptance

For every material composition change, render loudness-matched comparison
artifacts and record listener, date, device, project/context hashes, compared
artifacts, decision, and reason.

Required comparisons:

1. each prepared section against its selected input;
2. hard concatenation against aligned/connected full melody;
3. full melody alone against melody plus core arrangement;
4. core arrangement against Cohesion output;
5. pre-polish against selected targeted polish;
6. dry mix against production mix and master.

The review covers tempo stability, downbeat placement, melodic naturalness,
harmony, groove, section contrast, transition intent, masking, fatigue, and
recognizable identity. A listener can reject a structurally valid candidate.

## Release quality

Release acceptance additionally requires:

- decoded, finite, non-silent output with expected duration and format;
- no clipping/true-peak, loudness, low-end, stereo, or melody-audibility blocker
  under the versioned production policy;
- source, instrument, sample, model, and selected-artifact provenance;
- required attribution text and no noncommercial/unknown dependency in a
  commercial-ready lineage;
- signature-motif/recognizability evidence;
- AI-use disclosure recommendation based on actual selected generative stages;
- a current human policy and rights review;
- materially original creator decisions and release-to-release variation review.

Platform normalization targets are production references, not a guarantee of
advertiser suitability or monetization.
