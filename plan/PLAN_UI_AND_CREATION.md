# UI and Song-Creation Improvement Plan

## Product decision

The Compose Desktop application is the product UI. Improve that application;
do not extend the older static Spring frontend. It is deprecated and will be
retired in Task 057 after the desktop workflow and its package have passed the
release gate. The reference in `plan/UI.png` is a visual and interaction
reference, not a feature specification: retain its dark, compact, three-column
studio feel and section timeline, but do not add its travel, scene, video,
weather, or image features.

The workflow becomes a guided, recoverable path:

```text
Open/Create project
  -> Import source MIDI or WAV/MP3
  -> Inspect and non-destructively prepare the source
  -> Clean/analyze MIDI and confirm musical information
  -> Compose structure
  -> Generate + review/approve arrangement
  -> Build, repair, master, and audition
```

Each stage must display its status, why it is unavailable, its next action, and
the local artifact it produces. Source files stay immutable. Derived preview,
prepared-audio, MIDI, mix, and release files remain inside the project and are
written atomically.

## Findings to address first

| Symptom | Current cause | Required outcome |
|---|---|---|
| WAV/MP3 import feels broken | Audio is accepted only through a separate dialog, immediately needs optional Basic Pitch, and does not preflight the decoder/model or explain the stages. | One import sheet validates the chosen file, shows the exact prerequisites, and presents clear stage-specific recovery. |
| Part preview fails or says success without sound | MIDI preview depends on a renderer/library discovered only at render time; `JvmAudioPlayer` decodes on the UI path and swallows device failures. | Preview has preflight, visible render/playback states, a usable error, and no false success. |
| Build reports that `/sound` or `sounds/` is missing | `InstrumentRegistryLoader` defaults to a CWD-relative `sounds`, which is invalid from a packaged app or arbitrary launcher directory. | A single injected locator resolves a validated absolute library path, reports it at startup, and documents how to configure it. |
| Imported material is only lightly cleaned | Existing repair is used only late in the mix chain; MIDI cleanup defaults intentionally do very little. | A conservative, auditable, opt-in input-preparation pass runs before transcription/analysis and produces a before/after report and A/B preview. |
| Creation is cognitively fragmented | The current compact panels expose all controls at once without a clear state progression. | The workspace uses the reference’s hierarchy while making the next safe creation action prominent. |

## Input-preparation contract

Audio import supports only WAV/WAVE and MP3 in this phase. It is still
solo-piano transcription: do not claim that a polyphonic, vocal, or full-mix
track can be converted to trustworthy editable MIDI. The import sheet must say
this before a user commits the operation.

For audio, retain these artifacts:

```text
source/<part>.<original-extension>       immutable original
prepared/<part>/decoded.wav              PCM-24 working copy when required
prepared/<part>/clean.wav                optional prepared copy
prepared/<part>/report.json              versioned measurements and decisions
midi/raw/<part>.mid                      transcription output
midi/clean/<part>.mid                    cleaned, validated MIDI
analysis/<part>.json                     musical analysis
previews/<fingerprinted>.wav             monitor-only audio
```

`report.json` records source and derived SHA-256 fingerprints, input format,
duration, measured DC offset, clipped-run count, hum/noise evidence, selected
safe operations and parameters, output measurements, warnings, and tool
versions. It must never contain source paths outside the project or model text.

The preparation engine has two explicit modes:

- **Inspect only** (the default): decode and measure, make no clean copy, and
  report whether each safe repair is worth offering.
- **Safe cleanup**: independently enables DC removal, short-clip repair,
  declicking, narrow hum removal, and gentle stationary-noise reduction only
  when evidence exceeds documented thresholds. It never normalizes loudness,
  removes silence/time, pitch-shifts, time-stretches, separates stems, or
  invents musical notes.

The AI-assisted portion may select from a schema-bounded list of measured,
reversible cleanup candidates and rank them by measurable artifacts/noise
reduction. It may not produce code, shell commands, paths, arbitrary DSP
parameters, or changes to the original source. Keep a deterministic fallback
and let the user choose original versus prepared audio before transcription.

MIDI cleanup remains a separate stage. Extend it only with opt-in,
musically-bounded repairs such as duplicate removal, dangling-event handling,
low-confidence/very-short-note removal, sustain-pedal normalization, velocity
outlier limiting, and soft grid quantization. Never silently quantize a
performance or fabricate notes.

## UI target

At 1440x900, use a dense desktop shell close to the reference:

```text
Top bar: project identity | workflow tabs/stepper | readiness | Build Song
Left:    Parts and import actions | selected-part preparation card
Centre:  structure chips | arrangement controls/details | proportional timeline
Right:   selected preview/transport | creation checklist | operation/status log
Bottom:  persistent transport | compact mixer | master controls
```

- Teal is the sole primary/action colour; piano, bass, drums, pad, and strings
  receive restrained, stable accent colours in the timeline and mixer.
- Use charcoal page/background surfaces, one card elevation level, strong
  section labels, compact spacing, and text/icon labels rather than icon-only
  primary actions.
- The selected section and selected part must be visibly linked across the
  parts list, structure, arrangement detail, timeline, and preview.
- At 1100x720 retain Parts + main workspace and move preview/status/mix into a
  horizontally accessible second column. Below 760dp use one scrollable column
  with the transport pinned at the bottom.
- Provide a visible empty-project checklist instead of blank panels. Never hide
  unavailable controls without explaining their prerequisite.

## Dependency policy

All dependency checks run before a user starts a long operation. Readiness must
separately report: worker reachability, transcription runtime availability,
sound-library location and registry validity, sample presence, renderer
executable/version, and audio output availability. A build only enables when
its exact required set is ready; an audio-source preview may be available even
if the renderer is not.

Sound-library resolution order is explicit `MUSIC_SOUNDS_ROOT`, a configured
desktop preference containing an already validated absolute path, then a
development/bundled discovery location. A packaged application must package the
approved assets or require a documented local asset install; it must never
silently depend on the process working directory. The UI should display the
resolved path and offer a folder-selection recovery action. Do not weaken SFZ
path traversal, license, or sample validation.

## Repository health, bug audit, and legacy retirement

Every implementation task must check its changed surface for regressions and
leave unrelated working-tree changes alone. Task 056 is the dedicated
repository-wide pass; it must not be folded invisibly into a UI task.

It first creates a written, prioritized bug inventory with reproducible steps,
affected adapter (engine/CLI/desktop/worker/API), severity, evidence, proposed
owner/task, and a resolution state. The audit covers compilation, tests,
runtime dependency recovery, project/artifact compatibility, source mutation,
error reporting, resource ownership, dead code/references, documentation, and
the public local command surface. A known failure is reported separately from a
new regression. Fix only verified, in-scope defects; do not use the audit as
permission for an uncontrolled rewrite.

Static frontend retirement is deliberate and complete, not a partial hide:

```text
inventory references and API coverage
  -> prove Compose/CLI API independence
  -> remove static HTML/CSS/JS/assets/test page and static server helper
  -> remove Spring SPA fallback only (keep /api controllers)
  -> remove Makefile/frontend and README instructions
  -> assert no tracked legacy-web references remain
```

Before deletion, verify the Compose app and CLI do not depend on the browser UI.
Preserve the Spring JSON API in this sequence; removing it requires a separate
explicit product decision even if current adapters do not call it. Do not delete
project data, audio assets under `sounds/`, API controllers, or worker code. The
resulting root route may return a normal 404 or an explicit API-only response;
it must not redirect to stale HTML.

## Task sizing for Qwen3-Coder-30B

Each task has one primary layer or concern. A task may add its matching tests,
fixtures, and the smallest compile-safe seam, but should not combine a worker
algorithm, application orchestration, view-model state, and visual redesign.
Aim for a reviewable change rather than a line-count target. If the selected
task requires a new public contract that makes the next task possible, keep the
next behavior inert.

Implement exactly one numbered task per Qwen session. Do not start the next task
until focused tests, required module tests, acceptance criteria, and diff review
pass. When a task exposes an unrelated defect, record it; do not expand scope.
Task 056 is the only full-repository audit. It creates one new Task 059+ per
verified critical/blocking issue, which must be completed before Task 057.

## Delivery sequence

| Phase | Tasks | Outcomes |
|---|---|---|
| Runtime/playback foundations | 029–032 | Library location/settings, reliable player, typed readiness |
| Preview | 033–035 | Preview artifacts/state and usable transport UI |
| Input inspection | 036–039 | Stable report, worker measurement, service integration, guided import |
| Audio preparation | 040–043 | Deterministic cleanup, bounded plan, transcription gate, A/B UI |
| MIDI quality | 044–046 | Named cleanup profiles, quality report, review UI |
| Creation workflow | 047–050 | Derived progress, header/checklist, parts/structure, arrangement/build |
| Visual refinement | 051–053 | Theme tokens, responsive shell, real timeline/mixer states |
| Release proof | 054–055 | End-to-end compatibility, docs, packaged-app smoke |
| Repository health | 056 | Read-only full audit and small follow-up Task 059+ contracts |
| Legacy retirement | 057–058 | Static frontend removal, guards, reconciled docs/final verification |

The existing CLI remains supported and calls the same application services; no
workflow logic belongs in a composable.
