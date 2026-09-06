# Melotrail

A local Kotlin/Compose Desktop MIDI arranger for musicians who finish songs in
Logic Pro. Import a melody, confirm structure and harmony, preview styles,
create and refine a complete draft, then export separate MIDI tracks.
The musician keeps control of the melody, harmony and final sound.

## Current state

Source protection, deterministic Chords/Bass/Drums generation, style previews,
complete drafts, atomic acceptance/undo and validated MIDI export already exist.
The current output averages **5/10 in the user's feedback**: timing works, but
piano/melody fit and whole-song development need improvement. UI foundations are
partly rebuilt; legacy audio/worker code is still scheduled for removal.

The new [PLAN](PLAN.md) and [task queue](TASKS.md) replace all previous plans.
They cover musical quality, the supplied UI design and a smaller repository.
A TABI video companion is planned separately; no video runtime is shipped.

## Run and validate

```bash
make desktop
make test
make build
```

Use the JDK selected by the Gradle toolchain. At this planning handoff the
existing local edits select JDK 21/Kotlin 2.2.21; native packaging/toolchain
confirmation is F01. Python and sound libraries are unnecessary for the current
MIDI workflow. Remaining worker/renderer Make targets are removal scope.

Input: one SMF 0/1 file, one note-bearing track/channel, fixed tempo/meter.
Additional meta-only tracks are allowed. Current structure uses whole bars.
M03 plans explicit chord-duration editing and optional trailing-silence padding;
those improvements are not implemented yet.

Output: an immutable complete-song MIDI file, aligned role files and a manifest.
Import at song start in Logic Pro, confirm tempo/meter and choose instruments.
Logic Pro performs all audio production. GarageBand is not a supported target.

## Documentation

- [PLAN](PLAN.md): findings, product design, priorities and agent workflow.
- [TASKS](TASKS.md): dependency queue, acceptance criteria and reusable prompt.
- [Architecture](docs/ARCHITECTURE.md): owners, state and safety boundaries.
- [MIDI contract](docs/MIDI_CONTRACT.md): input, preservation and export semantics.
- [UI guideline](docs/UI_GUIDELINE.md): measured references and six-page design.
- [Validation](docs/VALIDATION.md): automated/manual checks and retained evidence.
- [TABI video](docs/TABI_VIDEO.md): assets, generative animation and optional companion.

Original UI/TABI/train references and Logic Pro captures remain in `docs/pictures`
and `docs/checks`. Git is the archive for retired plans and logs. License: MIT.

Automatic Terra execution is configured in [TASKS](TASKS.md#configured-automatic-execution):
hourly bounded tasks, isolated worktrees and fresh review. The active queue lives
on `codex/terra-implementation`; the original checkout remains unchanged by runs.
