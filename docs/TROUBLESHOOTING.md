# Desktop troubleshooting

This guide covers the local Compose Desktop application. It does not require
Spring.

## Package and project startup

Build the current macOS package with:

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

Open `desktopApp/build/compose/binaries/main/dmg/Melotrail-1.0.0.dmg`,
copy the app to Applications (or another local folder), and launch it. The
package includes a Java runtime but is unsigned and not notarized. It has only
been packaged and smoke-tested on macOS; do not infer Windows or Linux support.

Use **New Project** to select an empty/new project folder, or **Open Project** to select a
directory containing a valid `project.json`. Project audio and metadata stay in
that selected project directory; desktop preferences retain only the last
successfully opened project and a validated sound-library location.

For a canonical v4 project, open **Setup** and explicitly save the project name,
key, BPM, time signature, Lo-fi profile, and mood before analysis. Older or
non-canonical project files are rejected without conversion or writes.

## Sound library or renderer unavailable

The package intentionally does not bundle the starter samples or an SFZ
renderer. In the app, open the shell **More** menu, then **Settings**, and select the absolute
`sounds/` directory that contains `instruments.json` and `LICENSES.json`. A fresh checkout
needs the approved local 25 sample WAV files copied back into the existing
`*/samples/` paths first; see [`sounds/README.md`](../sounds/README.md).

For a terminal launch only, use an explicit override:

```bash
export MUSIC_SOUNDS_ROOT=/absolute/path/to/sounds
```

The override takes precedence over the saved selection. Correct or unset it
before trying another folder. Do not use the app's launch directory as a
library location.

MIDI preview, stem rendering, and Build Song additionally need a real local
`sfizz_render` executable. Configure it with an absolute path and let the
readiness panel verify it:

```bash
export SFZ_RENDERER_PATH=/absolute/path/to/sfizz_render
```

No renderer, model, or sample-library support is implied until its readiness
check succeeds locally.

The desktop **Settings** destination contains only the validated sound-library
preference, local runtime readiness, and local build/platform information.
The sound-library chooser and its validation error remain visible; use the
labelled **Runtime details** and **Build information** disclosures for the
infrequent readiness rows and local build metadata.
Telemetry, cloud sync, update checks, themes, audio-device selection, autosave,
backups, model downloads, and broad preference resets are intentionally absent.

## Worker and optional transcription

Start the Python worker only when a selected operation needs it:

```bash
make worker
```

WAV/WAVE and MP3 import is a solo-piano transcription path. It requires the
optional Basic Pitch runtime in a separate Python 3.11 environment; follow
[`worker/README.md`](../worker/README.md). A worker without that optional
runtime remains useful for supported non-transcription operations. The app
shows the failed stage and recovery action rather than treating an unavailable
worker or model as a successful import.

## Import, cleanup, and preview

Choose MIDI, WAV/WAVE, or MP3 only. The service validates both extension and
actual format. Source files are immutable under `source/`.

- **Inspect only** is the default: it measures the input and writes a versioned
  `prepared/<part>/report.json` without creating a cleaned copy.
- **Safe cleanup** is opt-in and requires confirmation. It can write derived
  PCM-24 `decoded.wav`/`clean.wav` under `prepared/<part>/`; it never
  normalizes loudness, removes time or silence, changes pitch/tempo, separates
  stems, or overwrites the source.
- Select original or prepared audio explicitly before transcription. Raw and
  clean MIDI, analysis, and preview artifacts stay under the project.
- After raw MIDI exists, **Clean MIDI** repairs invalid events and then the deterministic
  **Normalize MIDI** stage publishes `midi/normalized/<part>-<run>.mid` plus a
  hash-bound `midi/normalization/<part>-<run>.json`. Both retain source/raw/clean
  MIDI unchanged; no pitch correction, swing, creative quantization, or humanization occurs.
  The detected source key and confidence are shown on the Melody Parts card. If confidence
  is low, choose the source tonic/mode before **Transpose to project key** can publish
  `midi/transposed/<part>-<run>.mid` and its hash-bound report; original and normalized
  MIDI remain unchanged.
  If clean review is required,
  **Approve Clean MIDI** binds approval to the exact cleanup evidence.

Audio-source monitoring can be available without an SFZ renderer. MIDI preview
needs the selected validated library, all samples, a verified renderer, and an
audio output device. If preview is unavailable, follow the specific readiness
message, then refresh or retry; do not treat a disabled Play button as proof
that audio started.

## Source song is out of sync or harmonically wrong

The current pipeline can produce structurally valid output from independently
performed parts that do not share a downbeat, performed tempo, mode, or chord
fit. Current normalization conforms MIDI representation/tempo metadata, while
reviewed beat/downbeat alignment and occurrence-local harmony fitting are
separate stages described in [`plan/PLAN.md`](plan/PLAN.md). Current source-song
assembly prepares each selected source section into a globally monophonic
candidate, fits it to the authoritative harmony for its exact occurrence, then
publishes one versioned canonical full melody with a conductor track,
controller-free note track, occurrence/harmony/lineage sidecar, and reviewable
groove map. A harmony-fit block means the nearest safe pitch is
ambiguous/excessive or a boundary tail cannot be released within policy; inspect
its report rather than replacing the candidate. QP-008 still must ensure every
later arrangement, humanization, renderer, and export path consumes the
approved connected full melody.

Before arranging a current project:

- inspect and explicitly confirm low-confidence source keys;
- compare the transposed MIDI against the project key and section progression;
- check every section's first musical beat and trailing duration;
- audition the connected source melody alone;
- do not override Source Song Critic blockers merely to reach Build;
- retain the original and rejected candidates for comparison.

If section starts drift away from the bar grid, different modes remain audible,
simultaneous melody notes survive, or exposed notes clash with the active chord,
treat the project as a private diagnostic run until QP-008–QP-010 are
implemented or the candidate is manually corrected and reviewed. Audio effects,
mastering, and Cohesion cannot repair a fundamentally misaligned source melody.

## Build and artifact recovery

Build Song stops until its precise prerequisites are ready: worker, sound
library, samples, and renderer. It does not implicitly approve a Qwen draft.

### LM Studio planner response is truncated

Melotrail requests up to 8,192 completion tokens by default. If the loaded
model still reaches its completion limit, restart the desktop app with a larger
per-process budget, for example `QWEN_MAX_TOKENS=16384 make desktop`. The
model's configured context window must also be large enough for the MIDI input
plus that completion budget.

After valid approved arrangement-aware Cohesion, current Critic/Full-Song
Enhance selection, and selected/bypassed Humanization, the build creates or
reuses inspectable generated MIDI, stems, dry mix, repair/optional LoFi output,
`output/master.wav`, and optional MP3/release metadata. The transition track
uses the exact approved Cohesion bridge at its shifted boundary and
`stem-render.json` records the boundary hashes. A changed structure, Cohesion
approval, or source can make downstream artifacts stale; regenerate rather than
copying old outputs.

`master.wav` is always the authoritative lossless release. MP3 is a separate
optional final conversion. If a failure occurs, keep the source and inspect the
project-local reports/artifacts plus the bounded diagnostic logs under
`~/.melotrail/logs/`.

On **Export**, the disabled or enabled **Export Song** action and its recovery
route are always visible. Open **Release options** only to change the permitted
format, filename, or project `output/` destination; it cannot make a stale or
missing master exportable.

## Unsupported projects and stale artifacts

New projects are schema v4, and only the current canonical v4 shape can open.
If an older or superseded document is rejected, the application will not rewrite
or convert it. Use Git history or an external archival checkout if its data must
be inspected; do not copy old serialized fields back into a current project.

Changing source/raw MIDI, cleaned MIDI, the selected Lo-fi Feel, analysis,
structure, cohesion, mix-only settings, or audio texture can mark downstream
artifacts stale. Retained stems, mixes, and masters are inspectable but not
build-ready until their prerequisites and fingerprints are current again. Do
not delete stale artifacts to make a stage look complete.
