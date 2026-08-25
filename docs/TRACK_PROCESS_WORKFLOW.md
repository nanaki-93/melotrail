# Current track and song workflow

This document describes shipped schema-v4 behavior. The active implementation
roadmap is [`plan/PLAN.md`](plan/PLAN.md). Retained files are evidence, not proof
that a stage is current; readiness comes from validated references, hashes,
approvals, and stale-state dependencies.

For source formats and exact preparation paths, see the
[MIDI import process](MIDI_IMPORT_PROCESS.md).

## Current order

```text
Project Setup and structured Harmony
  -> Import source
  -> Extract/transcribe
  -> Clean
  -> Normalize
  -> source-timing evidence / reviewed timing candidate
  -> Detect/confirm source key
  -> Transpose to project key
  -> Technical Correction
  -> AI Fix selection
  -> per-track Enhance selection
  -> optional MIDI Feel selection
  -> Analyze
  -> Structure
  -> assemble/connect/criticize/approve Source Song
  -> Arrangement plan and approval
  -> generated core roles and approval
  -> optional roles
  -> Ensemble Cohesion and approval
  -> deterministic Full-Song Critic
  -> improvement-gated/no-op targeted Full-Song Enhance
  -> selected/bypassed seeded Humanization
  -> Render stems and dry mix
  -> production mix / optional texture
  -> Master
  -> Export and optional commercial evidence
```

The UI can group stages for readability, but it must not reorder their
dependencies or infer completion from page visits.

## Stage states

- **Locked** — a prerequisite is incomplete.
- **Ready/current action** — the stage can run against current inputs.
- **Running** — a typed operation is active.
- **Review required** — a draft/report awaits an explicit decision.
- **Approved/complete** — validated current evidence exists.
- **Stale** — retained evidence no longer matches current upstream inputs.
- **Failed** — current attempt failed; prior valid inputs remain recoverable.

## Current artifacts and recovery

| Stage | Current evidence | Primary recovery |
| --- | --- | --- |
| Project | Canonical schema-v4 `project.json` | Unsupported/malformed projects fail without rewriting; create/open a canonical project |
| Setup/Harmony | Structured key/mode, tempo/meter, profile/mood, section progressions | Resolve missing/incompatible authority before musical stages |
| Source/import | Immutable `source/<part>.*`, preparation report, `midi/raw/<part>.mid` | Reinspect/retranscribe/re-import only the affected source |
| Clean/Normalize/Timing/Transpose | Separate MIDI plus quality/normalization/timing/transposition reports | Review cleanup and timing evidence; confirm low-confidence source key; rerun earliest stale stage |
| Correction/AI Fix/Enhance/Feel | One ordered, hash-bound chain: transposed -> reviewed timing mapping (when selected) -> corrected -> AI Fix -> Enhance -> Feel | Approve, reject, regenerate, or select `NO_OP`; Feel is regenerated from the selected upstream candidate and never copied by filename |
| Analysis/Structure | `analysis/<part>.json`, stable `StructureOccurrence` entries | Reanalyze affected part or save intended occurrence order |
| Source Song | Versioned two-track source MIDI/sidecar, connected candidate, complete-count critic report, approval mode | Repair hard findings; a recorded ordinary-blocker override is private-audition-only and experimental |
| Arrangement | `song_plan.json`, `section_variations.json`, approved/draft detailed plan | Approve draft or regenerate from current authority/source approval |
| Generated roles/core | Validated bass/drums/pad and optional string/transition evidence | Regenerate the failed role; later roles use accepted prior state |
| Cohesion | Boundary contexts/plans, bridge/role/occurrence outputs, approval | Review each adjacent occurrence against the approved arrangement |
| Full-song review | Critic report, complete actionable evidence, bounded correction batches, and hash-bound candidate result | Repair/retry failed batches; record `NO_OP` only when no actionable evidence exists. A quality-certified run cannot bypass a failed or rejected planner. |
| Humanization | Seeded per-role candidates/reports or bypass | Select current seed/config or bypass to current upstream input |
| Render/mix/master | Stems, mix plans/reports, `mix/dry.wav`, `output/master.wav`, and a hash-bound pending `debug/quality/.../listening-record.json` when a review bundle is requested | Restore worker/library/renderer and rerun only stale descendants; a pending form is not listening approval |
| Release | Export plus frozen provenance/credits/release metadata | Resolve rights/license/AI-use/policy/manual blockers; never overwrite master |

## Source Song boundary

The current application assembles selected source MIDI into ordered occurrences
as one conductor track plus one controller-free full-melody track. Its versioned
sidecar retains occurrence IDs/windows, canonical harmony, source/preparation
hashes, post-fit anchors, note lineage, and a reviewable global groove map;
Melody Connection and Source Song Critic use that assembled identity before
explicit approval.

Current downstream piano paths consume the exact approved connected source song;
their occurrence views are clipped through its authoritative sidecar rather
than reconstructed from selected part MIDI. QP-002 stores source
beat/onset/tempo/downbeat and source-groove evidence, and QP-003
can publish a reviewed, source-hash-bound, piecewise timing candidate with
whole-bar body bounds plus explicit pickup/tail windows. An explicit approved
decision selects that candidate as the current correction baseline; an
unreviewed candidate and downbeat are never selected implicitly. QP-004 maps recognized
source scale degrees into the project mode and records unresolved chromatic
fallbacks. QP-005 turns each selected source section into a separate,
controller-aware one-track monophonic candidate. QP-006 then fits an immutable
candidate per structure occurrence to the authoritative local harmony, with
note-level pitch/tail/tie evidence and a tempo/PPQ-derived boundary gap. It
does not rewrite project harmony, and ambiguity or an excessive repair blocks
instead of publishing MIDI. QP-008 binds downstream arrangement, Cohesion,
criticism, humanization, preview, renderer, and release lineage to the approved
connected full melody. It clips occurrence views through the authoritative
sidecar; it never reconstructs piano timing from selected part durations.
QP-010 verifies canonical lineage, explicit windows, monophony, QP-006
eligibility, anchors, tails, source groove, and source-key confirmation before
approval. It exposes complete severity counts; hard invariants cannot be
overridden, and an ordinary-blocker override is explicitly private-audition and
experimental. QP-011 binds the approved full-song groove map and typed
profile/mood/role intent into each Arrangement occurrence, including bounded
density, register, articulation, groove allowance, and prior accepted
pad/string voicing evidence. QP-012 is the generated-role admission boundary:
bass and drums consume and are checked against the active approved map span,
including piano-flam rejection; pad/string requests retain actual prior
voicings; all reports record accepted-state/candidate metrics and registry/kick
evidence. A failed candidate remains outside the accepted ensemble state.

The Structure-page canonical melody review projects only current, verified
sidecars. It shows source key/confidence; downbeat and mapping confidence;
target bars and pickup/body/tail windows; accepted groove; controller-aware
sustain releases; monophony and harmony-fit changes; anchors; boundary
voice-leading findings; critic blockers; and each selected project-relative
artifact plus SHA-256. Its opt-in piano comparisons resolve the preserved
source, assembled prepared melody, connected full melody, or the exact two-
occurrence boundary segment. Each monitor is RMS-matched with a peak-safe
ceiling and never selects, overwrites, or publishes a MIDI/audio candidate.

## Arrangement and Cohesion boundary

Arrangement uses authoritative project context and deterministic MIDI
generators. Every new plan consumes the approved full-song groove map and
resolves profile/mood, section purpose, density, register, articulation, and
role-specific timing/voicing intent before detailed planning. A planner cannot
replace authoritative harmony or invent a separate timing grid; Qwen schema
illustrations are explicitly non-executable and flat copied defaults are
rejected. Core piano/bass/drums/pad evidence is validated before optional
layers. Cohesion runs after arrangement and publishes reviewed boundary
derivatives without overwriting selected source MIDI or locally replacing the
approved piano melody. Any future post-connection piano edit must first publish
and reapprove a complete full-melody candidate.

Cohesion is boundary-local: its input records active, entering, exiting, and
continuing generated roles for each adjacent pair. A bridge may select only a
locally supported role. `CONTINUITY` is an auditable no-op on a continuing role,
never a fallback drum fill. Pitched bridge notes follow the canonical boundary
harmony, drum bridge timing comes from the approved full-song groove-map span,
and merge replaces an exact same-pitch overlap instead of stacking attacks.
Before approval, the deterministic critic compares baseline and candidate role
MIDI; Cohesion cannot increase blocker or critical counts. The saved comparison
contains actual note deltas and critic aggregate deltas. Still audition hard
joins at matched volume: this automated evidence does not substitute listening.

## Build, release, and quality labels

An ordinary successful build proves that required artifacts were generated and
validated under current structural contracts. It does not by itself prove good
composition, listening quality, originality, commercial rights, or YouTube
monetization eligibility.

Use these distinctions:

- **Private audition:** current enough to preview, possibly with explicit
  experimental bypass.
- **Quality-certified:** satisfies the current hard/quality/listening gates in
  [`plan/QUALITY_GATES.md`](plan/QUALITY_GATES.md).
- **Commercial evidence ready:** provenance and policy evidence is complete
  under [`COMMERCIAL_PROVENANCE.md`](COMMERCIAL_PROVENANCE.md); it is not legal
  or platform approval.

The QP-017 review bundle copies named, hash-validated MIDI/WAV pairs under
`debug/quality/<project-context-sha256>/` and writes a `PENDING_HUMAN_REVIEW`
form. It preserves selected artifacts and source bytes; the form becomes
evidence only after a real listener records their identity, date, device, and
decision. Offline fixtures and omitted local renderer/model/audio-device runs
remain explicitly unverified.

## Practical recovery rules

1. Follow the earliest non-complete dependency and its named recovery action.
2. Preserve sources and approved historical candidates.
3. Regenerate stale descendants from current selected inputs; do not delete
   evidence to make readiness appear complete.
4. Treat worker/model/renderer/audio-device failures as missing dependencies or
   failed attempts, never successful bypass.
5. Review key, timing, harmony, monophony, source-song, arrangement, Cohesion,
   critic, and listening evidence before a quality claim.
6. Keep `output/master.wav` as the authoritative lossless master; export to a
   separate filename.

See [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) for local dependency and failure
recovery.
