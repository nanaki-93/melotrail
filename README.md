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

The automated arrangement UX evidence is maintained with the active task
suite. Five observed authority-complete musician sessions, including three by
musicians who did not implement the feature, remain a required manual gate
before the separate musical holdout can start; see the
[arrangement UX rubric](docs/plan/MC048I_ARRANGEMENT_UX_RUBRIC.md).

[PLAN.md](PLAN.md) is the only active roadmap. Historical audio, quality-pipeline, and
guided-arranger plans are superseded; Git history is their archive.

## Product workflow

```text
Create project
  -> Import one complete-song, single-melody-track MIDI file
  -> Protect that melody automatically
  -> Set key, tempo in BPM, meter, named sections in bars, and chord progressions
  -> Audition the source through the built-in MIDI synthesizer
  -> Choose a named style and preview the selected song-map section
  -> Create one complete arrangement draft, then repair only exceptions
  -> Listen to and use the complete draft
  -> Export a Logic Pro MIDI package
```

The MVP accepts one SMF format 0 or 1 file with exactly one note-bearing track
and one note-bearing channel. Additional non-note conductor/reference tracks
are allowed. The melody is protected automatically, tempo is entered in BPM,
structure is entered as named whole-bar sections, and harmony is written as one
readable chord progression per section. The section total must match the source length. Multiple source
files, tempo maps, meter changes, audio import, and transcription are outside
V1.

## Desktop UI

The Compose Desktop UI remains the product. Its target workspace contains:

- Project
- MIDI
- Structure & Harmony
- Arrange
- Review
- Export

The imported melody plays through an audible built-in synthesizer by default.
Arrange and Review share a bar-proportional song map. Arrange presents named
style previews and one full-draft action; Review plays and accepts that draft
in one atomic decision, with a safe latest-batch undo. Role/profile correction,
comparison, lifecycle, device, and transport controls stay contextual to a
selected section. Settings that are genuinely
required for MIDI audition or export may use a small dialog. Melotrail does not
reproduce a DAW mixer, mastering suite, sound browser, video editor, or
publishing console.

## Visual direction

The desktop is a focused dark MIDI workstation: a compact project header,
six-destination navigation, an adaptive work area, and context that explains
the current musical decision. Section, harmony, candidate, and MIDI-event
visuals must show real project evidence rather than audio waveforms, video
art, or simulated mixer controls. The visual system and its acceptance rules
are defined in [MIDI workspace visual specification](docs/MIDI_WORKSPACE_VISUAL_SPEC.md).

A [mockup-faithful redesign plan](docs/plan/UI_MOCKUP_REDESIGN_PLAN.md) now
specifies the next visual revision, adapting every supplied UI picture to the
real MIDI workflow. Its [20 ordered tasks](docs/plan/UI_MOCKUP_TASKS.md) and
[execution prompt](docs/plan/EXECUTE_UI_MOCKUP_TASKS_PROMPT.md) require one commit
per completed task and visual comparison, not only functional tests. This
redesign is planned, not yet implemented. A
[future Create Video specification](docs/plan/FUTURE_VIDEO_CREATOR.md) is
separately gated; video is not part of the current MIDI-only runtime.

## DAW relationship

- Logic Pro is a supported Standard MIDI input and output workflow.
- GarageBand is unverified and not a supported destination.
- Melotrail exports performance intent and optional instrument suggestions; the
  musician chooses the actual instruments and production chain in the DAW.

See [DAW compatibility](docs/DAW_COMPATIBILITY.md) for the verified boundary and manual acceptance
matrix.

## Target runtime

- JDK 25
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
- [MIDI workspace visual specification](docs/MIDI_WORKSPACE_VISUAL_SPEC.md) —
  focused desktop visual language and visual acceptance rules
- [Implementation tasks](docs/plan/MIDI_CORE_TASKS.md) — mandatory sequential
  build, cutover, and cleanup work
- [Agent execution prompt](docs/plan/EXECUTE_MIDI_CORE_TASKS_PROMPT.md) —
  standalone prompt for executing the task suite
- [Execution log](docs/plan/MIDI_CORE_EXECUTION_LOG.md) — evidence and sign-off
  ledger
- [Documentation index](docs/README.md) — ownership and transition notes

## License

MIT
