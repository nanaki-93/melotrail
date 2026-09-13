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
and TASKS V10–V33 is planned, not implemented: it will add an independent Video
tab to this Kotlin/Compose application for turning generic reference assets and
a free-form prompt into a complete 3–5 minute silent video. It needs no MIDI
project, export, song or soundtrack. Local generation is evaluated first, and
audio is added later in the user's external Apple editor.

The repository still contains the superseded Swift soundtrack companion and its
owned technical fixtures pending V30–V31. They are preserved as historical
evidence and do not satisfy or define the new asset-and-prompt workflow. Six
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

Input: one SMF 0/1 file, one note-bearing track/channel, fixed tempo/meter.
Additional meta-only tracks are allowed. Sections use whole bars; chord durations
are explicit and may be sub-bar. Source extent and optional trailing-silence
padding are confirmed separately without changing the original MIDI.

Output: an immutable complete-song MIDI file, aligned role files and a manifest.
Import at song start in Logic Pro, confirm tempo/meter and choose instruments.
Logic Pro performs all audio production. GarageBand is not a supported target.

There is no implemented Video-tab command yet. V20 adds the tab and V32 verifies
the complete in-app route. **Temporary launcher for the superseded Swift
workflow:** on macOS 14+ with Xcode command-line tools and an existing composition
request, the current checkout still supports:

```bash
make video VIDEO_REQUEST="/path/to/composition-request.json"
```

Optionally add `VIDEO_JOBS="/path/to/animation-jobs.json"` to display a saved job
ledger. See the [historical native scene editor](companion/README.md#native-scene-editor)
for input requirements and controls. This launcher and the MIDI Export handoff
remain until V30–V31 replace their owners after the integrated owned-fixture path
works. The MIDI app builds, runs, auditions and exports independently of them;
the planned asset-and-prompt workflow is described above.

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
[TASKS](TASKS.md#configured-automatic-execution): 20-minute wakes, one Sol High
writer at a time, coordinator validation, fresh review and bounded Astra High
repair for concrete failures. Successful rows advance
`codex/video-generation-sequential`; tracked user edits remain protected. The
retired Terra CLI runner and its local configuration do not control this run.
