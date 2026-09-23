# Melotrail

A local Kotlin/Compose Desktop MIDI arranger for musicians who finish songs in
Logic Pro. Import a melody, confirm structure and harmony, preview styles,
create and refine a complete draft, then export separate MIDI tracks.
The musician keeps control of the melody, harmony and final sound.

## Current state

The MIDI workflow now includes explicit chord durations and source-end padding,
melody-aware piano, coordinated section plans, targeted repairs, compact six-page
UI, one persistent player and accepted-only MIDI export. Legacy audio/worker
runtime has been removed. The original **5/10 feedback** remains the musical
baseline until the final listening review; tests cannot award a new score.

MIDI engineering is complete. Its UI, listening, Logic and release decisions
remain in the [final review](docs/VALIDATION.md#final-manual-review). The new
video workstream in [PLAN §9](PLAN.md#9-video-generation-from-assets-and-a-prompt)
has implemented its isolated project, asset, planning, job, media and owned
ComfyUI foundations. The current plan takes externally finished scene pictures,
with optional character/background layers, and generates video only. In-app image
generation and outfit/style synthesis are deferred. The Video tab, ready-asset
motion setup, continuous animation and complete 3–5 minute silent export remain
later V10–V33 work. Video needs no MIDI project, export, song or soundtrack; audio is
added later in the user's external Apple editor.

The Swift soundtrack handoff and its repository-owned runtime have been removed.
Historical evidence remains in the validation references; it does not satisfy the
new asset-and-prompt workflow. Six
human gates remain open: U07, Q01, Q02, Q03, V24 and V33.

## Run and validate

```bash
make desktop
make test
make build
```

Use the JDK selected by the Gradle toolchain. The project uses JDK 21 and Kotlin
2.2.21. Python and sound libraries are not part of the MIDI workflow.

The coordinator's [clean native installation check](docs/VALIDATION.md#q03a-clean-native-installation-and-startup)
packages a macOS DMG and launches a private installed copy with its bundled JVM.
The 2026-09-13 clean check passed; user listening, Logic and visual release
approval remain separate final-review gates.

The opt-in V17 production-adapter check accepts one absolute, owner-controlled
JSON request beside a fresh output-directory path. It remains outside ordinary
tests and app startup:

```bash
./gradlew :comfyVideoProbe \
  -PcomfyVideoRequest="/absolute/path/to/owned-request.json"
```

The request names the verified application-support root, pinned FFmpeg tools,
one digest-pinned composed image under an `inputs` sibling, prompt, width,
height, duration, FPS, expected frame count, loopback port, polling interval and
protected MIDI roots. The generated `result.json` records the exact production
job path, full media decode, frames, geometry, cadence, silence, timing, memory,
swap, reconstructed-job recovery, an immediate sequential active-cancellation
check, lifecycle stop and unchanged inputs. It is evidence for one short
composed-image input only; it does not prove full-length continuity. The current
plan supplies finished pictures externally; image synthesis V18b1/V18b is deferred.

Input: one SMF 0/1 file, one note-bearing track/channel, fixed tempo/meter.
Additional meta-only tracks are allowed. Sections use whole bars; chord durations
are explicit and may be sub-bar. Source extent and optional trailing-silence
padding are confirmed separately without changing the original MIDI.

Output: an immutable complete-song MIDI file, aligned role files and a manifest.
Import at song start in Logic Pro, confirm tempo/meter and choose instruments.
Logic Pro performs all audio production. GarageBand is not a supported target.

`make video` now invokes the desktop application with `--video`, without a JSON
request or Swift tools. **V20 has not implemented the Video tab yet**: this
checkout rejects that option before opening a window, so this command is not
functional until V20 implements its route. Use `make desktop` for the supported MIDI path.
No soundtrack or MIDI export is required for the planned independent Video tab.

For musical evaluation, `musicalEvaluation` freezes supplied owned projects before
generating from isolated copies, and `musicalComparison` exports the separate M01
development comparisons. Both require new output directories. See the
[evaluation commands and score forms](docs/VALIDATION.md#frozen-musical-evaluation-commands-q01a).
No final songs or new listening scores are bundled; missing songs remain explicit.

## Documentation

- [PLAN](PLAN.md): findings, product design, priorities and agent workflow.
- [TASKS](TASKS.md): dependency queue, acceptance criteria and reusable prompt.
- [Architecture](docs/ARCHITECTURE.md): owners, state and safety boundaries.
- [MIDI contract](docs/MIDI_CONTRACT.md): input, preservation and export semantics.
- [UI guideline](docs/UI_GUIDELINE.md): measured references and six-page design.
- [Validation](docs/VALIDATION.md): automated/manual checks and retained evidence.
- [TABI video](docs/TABI_VIDEO.md): planned in-app asset-and-prompt video contract and preserved artistic/history evidence.

Original UI/TABI/train references and Logic Pro captures remain in `docs/pictures`
and `docs/checks`. Git is the archive for retired plans and logs. License: MIT.

Sequential video implementation is configured in
[TASKS](TASKS.md#configured-automatic-execution): continuous sequential execution
with 20-minute recovery wakes. Each verified task commit immediately admits the
next eligible task after dependency and usage checks. One Terra High
writer at a time with two retries, then Sol High with two retries. Each model
gets one initial attempt plus its retries, with full task and failure context
passed to each fresh retry agent. Coordinator validation and fresh Sol High
review precede one implementation commit per successful task. Successful rows advance
`codex/video-generation-sequential`; tracked user edits remain protected. The
retired Terra CLI runner and its local configuration do not control this run.
