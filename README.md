# Melotrail

Melotrail is a local desktop MIDI arranger for musicians who want help building
a song before finishing it in Logic Pro.

The musician owns the melody, key, harmony, structure, arrangement approvals,
and final sound. Melotrail imports Standard MIDI, helps define musical context,
generates reviewable chord, bass, and drum alternatives, and exports a clean
DAW-ready MIDI package.

## Project status

The MIDI-only product direction and its dependency-ordered implementation task
suite are being executed serially. The repository still contains the
superseded audio-production runtime while the replacement vertical slice is
built; that runtime and its Python worker are scheduled for complete removal
and are not supported product directions.

[PLAN.md](PLAN.md) is the only active roadmap. Historical audio, quality-pipeline, and
guided-arranger plans are superseded; Git history is their archive.

## Product workflow

```text
Create project
  -> Import one Standard MIDI file
  -> Select and protect one melody track
  -> Confirm key, tempo, meter, structure, and harmony
  -> Audition the source through MIDI playback
  -> Generate and compare chord, bass, and drum candidates
  -> Accept or regenerate by section and role
  -> Review the complete arrangement
  -> Export a Logic Pro MIDI package
```

The MVP accepts one SMF format 0 or 1 file, uses a fixed tempo and time
signature, and supports one selected melody track. Multiple source files,
tempo maps, meter changes, audio import, and transcription are outside V1.

## Desktop UI

The Compose Desktop UI remains the product. Its target workspace contains:

- Project
- MIDI
- Structure & Harmony
- Arrange
- Review
- Export

Settings that are genuinely required for MIDI audition or export may use a
small dialog. Melotrail does not reproduce a DAW mixer, mastering suite, sound
browser, video editor, or publishing console.

## DAW relationship

- Logic Pro is a supported Standard MIDI input and output workflow.
- GarageBand is unverified and not a supported destination.
- Melotrail exports performance intent and optional instrument suggestions; the
  musician chooses the actual instruments and production chain in the DAW.

See [DAW compatibility](docs/DAW_COMPATIBILITY.md) for the verified boundary and manual acceptance
matrix.

## Target runtime

- JDK 21
- Kotlin/JVM
- Compose Desktop
- `javax.sound.midi` or a narrowly wrapped replacement proven by tests

Python is not part of the target runtime.

## Development commands

```bash
make desktop
make test
make build
```

Obsolete worker, renderer, and live-audio commands may remain in the current
Makefile until their owning implementation is deleted. Do not build new work on
them.

The current UI still exposes a transitional [MIDI import process](docs/MIDI_IMPORT_PROCESS.md)
from the rejected runtime. It remains only until the focused MIDI page replaces
that executable documentation contract.

## Safety and musical authority

- Imported MIDI is immutable.
- Project key, harmony, tempo, meter, and structure are authoritative.
- Accepted candidates are immutable and content-addressed.
- Regeneration creates a new candidate; it does not overwrite the accepted one.
- Qwen, if added after the deterministic MVP, can only return constrained plans
  that deterministic code validates.
- Export uses an immutable snapshot and never silently replaces an existing
  package.

## Documentation

- [Plan](PLAN.md) — authoritative delivery plan
- [Architecture](docs/ARCHITECTURE.md) — target components and ownership
- [Functional specification](docs/FUNCTIONAL_SPEC.md) — user-visible functions
  and acceptance rules
- [MIDI contract](docs/MIDI_CONTRACT.md) — MIDI import, internal semantics, and
  export contract
- [DAW compatibility](docs/DAW_COMPATIBILITY.md) — Logic Pro support boundary
- [Cleanup scope](docs/CLEANUP_SCOPE.md) — keep, refactor, and delete decisions
- [Quality gates](docs/QUALITY_GATES.md) — automated, musical, and DAW
  acceptance gates
- [Implementation tasks](docs/plan/MIDI_CORE_TASKS.md) — mandatory sequential
  build, cutover, and cleanup work
- [Agent execution prompt](docs/plan/EXECUTE_MIDI_CORE_TASKS_PROMPT.md) —
  standalone prompt for executing the task suite
- [Execution log](docs/plan/MIDI_CORE_EXECUTION_LOG.md) — evidence and sign-off
  ledger
- [Documentation index](docs/README.md) — ownership and transition notes

## License

MIT
