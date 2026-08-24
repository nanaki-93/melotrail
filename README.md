# Melotrail

Melotrail is a local, MIDI-first, AI-assisted music arranger and producer. The
musician owns the melody, project key, harmony, structure, and approvals. Kotlin
owns orchestration, validation, project artifacts, and the Compose Desktop UI;
the separate Python worker owns bounded audio and transcription workloads.

## Requirements

- JDK 21
- Python 3.10+ for the worker; the optional Basic Pitch route uses the
  environment documented in [`worker/README.md`](worker/README.md)
- `make`, or the equivalent Gradle/Python commands

## Run locally

```bash
make desktop
make worker
```

`make worker` is required only for operations that use the Python worker.
Rendering and MIDI preview additionally require a validated local sound library
and `sfizz_render`; see
[`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md).

## Current workflow

The current schema-v4 desktop workflow is:

```text
Setup and Harmony
  -> Import Melody Parts
  -> Clean / Normalize / Transpose
  -> Technical Correction / AI Fix / Enhance / optional MIDI Feel
  -> Analyze and Structure
  -> Assemble, connect, criticize, and approve the Source Song
  -> Arrange and generate roles
  -> Ensemble Cohesion
  -> Full-Song Critic and optional targeted enhancement
  -> Humanization
  -> Render, Mix, Master, and Export
```

Current source-song approval is a real gate. Reviewed timing candidates,
mode-aware transposition, sustain-aware monophonic preparation, and
occurrence-local harmony fitting now produce a versioned canonical full-melody
candidate with one conductor track, one controller-free melody track, stable
occurrence/harmony/lineage sidecar evidence, and a reviewable full-song groove
map. Arrangement, Cohesion, criticism, humanization, preview, rendering, and
release lineage now bind to that exact approved connected melody; occurrence
views are clipped from its sidecar windows rather than reconstructed from part
durations. The quality roadmap still documents material gaps: source
micro-timing is not yet applied across the ensemble; cross-section
accompaniment voicing can reset; and production lacks interaction-aware
kick/bass control and selected-master codec-preview evidence. Those defects are
the scope of [`PLAN.md`](PLAN.md).

Before arrangement, the Structure page exposes a canonical melody quality
review: source-key confidence, reviewed timing/downbeat mapping, pickup/body/
tail windows, accepted groove, monophony and harmony-fit changes, protected
anchors, critic blockers, and exact hash-bound artifact references. Its opt-in
source/prepared/full-melody/boundary piano monitors use one peak-safe RMS target
for fair listening; private audition remains experimental, while source
certification and commercial-evidence readiness remain distinct gates.

For current operational behavior, use:

- [MIDI import process](docs/MIDI_IMPORT_PROCESS.md)
- [`docs/TRACK_PROCESS_WORKFLOW.md`](docs/TRACK_PROCESS_WORKFLOW.md)
- [`docs/COMMERCIAL_PROVENANCE.md`](docs/COMMERCIAL_PROVENANCE.md)
- [`docs/RELEASE_ACCEPTANCE.md`](docs/RELEASE_ACCEPTANCE.md)

## Data and safety

- `project.json` and project-relative artifacts are canonical.
- Source MIDI/audio and known-good candidates are immutable.
- MIDI is canonical during composition; WAV is canonical during audio
  production.
- Model output is a bounded plan. Deterministic code validates and applies it.
- Stale or rejected artifacts remain inspectable evidence but never become
  current merely because a file exists.
- AI and automation do not replace project key, harmony, structure, protected
  melody anchors, or user approval.

## Validation

```bash
make test
make worker-test
make build
```

Automated checks establish deterministic and structural correctness. The
quality plan also requires renderer-backed A/B listening gates and a real
four-source end-to-end composition review; structural tests alone cannot prove
that a song sounds good.

## Project layout

```text
src/main/kotlin/app/melotrail/   Kotlin domain and application services
desktopApp/                      Compose Desktop product
worker/                          Stateless Python HTTP worker
sounds/                          Local sound-library contract and metadata
docs/                            Operational and release documentation
docs/plan/                       Canonical quality-pipeline roadmap
```

## Release and monetization scope

Melotrail can preserve provenance, expose AI-use metadata, detect selected
technical/compositional problems, and produce release evidence. It cannot
guarantee copyright ownership, Content ID clearance, YouTube policy compliance,
Partner Program admission, or monetization. Each release and channel still
requires human rights, policy, originality, and listening review.

## License

MIT
