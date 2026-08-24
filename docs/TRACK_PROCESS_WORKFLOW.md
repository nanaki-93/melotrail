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
  -> approved/no-op/explicitly bypassed targeted Full-Song Enhance
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
| Clean/Normalize/Transpose | Separate MIDI plus quality/normalization/transposition reports | Review cleanup; confirm low-confidence source key; rerun earliest stale stage |
| Correction/AI Fix/Enhance/Feel | Hash-bound branch candidates and selections | Approve, reject, regenerate, select no-op/bypass; never copy by filename |
| Analysis/Structure | `analysis/<part>.json`, stable `StructureOccurrence` entries | Reanalyze affected part or save intended occurrence order |
| Source Song | Structured source MIDI/sidecar, connected candidate, critic report, approval | Preview exact source song; repair or explicitly review reported issues |
| Arrangement | `song_plan.json`, `section_variations.json`, approved/draft detailed plan | Approve draft or regenerate from current authority/source approval |
| Generated roles/core | Validated bass/drums/pad and optional string/transition evidence | Regenerate the failed role; later roles use accepted prior state |
| Cohesion | Boundary contexts/plans, bridge/role/occurrence outputs, approval | Review each adjacent occurrence against the approved arrangement |
| Full-song review | Critic report and targeted enhancement selection | Repair targeted issues, record no-op, retry, or explicit experimental bypass |
| Humanization | Seeded per-role candidates/reports or bypass | Select current seed/config or bypass to current upstream input |
| Render/mix/master | Stems, mix plans/reports, `mix/dry.wav`, `output/master.wav` | Restore worker/library/renderer and rerun only stale descendants |
| Release | Export plus frozen provenance/credits/release metadata | Resolve rights/license/AI-use/policy/manual blockers; never overwrite master |

## Source Song boundary

The current application assembles selected source MIDI into ordered occurrences,
retains occurrence IDs and canonical harmony in a sidecar, connects adjacent
melody boundaries, runs a deterministic source critic, and requires explicit
approval before arrangement.

Known limitation: current downstream piano paths can reconstruct occurrence
MIDI rather than consume the exact approved connected source song. QP-002 now
stores source beat/onset/tempo/downbeat and source-groove evidence, but it does
not yet warp MIDI into whole bars or choose an unreviewed downbeat. A
low-support groove template is explicitly review-required rather than inferred
from silence. Current
assembly also does not guarantee mode-aware pitch mapping, harmony-fit stable
tones, or global one-note-at-a-time melody. The remaining gaps are QP-003–QP-010
and must not be described as current capabilities.

## Arrangement and Cohesion boundary

Arrangement uses authoritative project context and deterministic MIDI
generators. Core piano/bass/drums/pad evidence is validated before optional
layers. Cohesion runs after arrangement and publishes reviewed boundary
derivatives without overwriting selected source MIDI.

Known limitation: Cohesion currently needs stronger boundary-local active-role,
gesture-execution, harmony, overlay, and post-merge validation. Until QP-013 is
complete, audition every hard join/bridge at matched volume and reject an
instrument or gesture that is unrelated to the adjacent sections.

## Build, release, and quality labels

An ordinary successful build proves that required artifacts were generated and
validated under current structural contracts. It does not by itself prove good
composition, listening quality, originality, commercial rights, or YouTube
monetization eligibility.

Use these distinctions:

- **Private audition:** current enough to preview, possibly with explicit
  experimental bypass.
- **Quality-certified:** satisfies the future hard/quality/listening gates in
  [`plan/QUALITY_GATES.md`](plan/QUALITY_GATES.md).
- **Commercial evidence ready:** provenance and policy evidence is complete
  under [`COMMERCIAL_PROVENANCE.md`](COMMERCIAL_PROVENANCE.md); it is not legal
  or platform approval.

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
