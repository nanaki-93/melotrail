# Task 010 — Repeated-Section Variation

## Goal

Represent every occurrence as an independent section instance and deterministically vary repeated material without modifying the source piano MIDI.

## Dependencies

- Task 009 produces a song-level energy curve and instrument progression.
- Existing `StructureParser` produces ordered indexed sections.

## Section identity

Extend or adapt `SectionInstance` so persisted planning artifacts contain:

- zero-based `index`;
- source `partId`;
- one-based occurrence number for that part;
- stable `instanceId` such as `A1`, `A2`, `B1`, `A3`.

Instance IDs are derived by the application, never supplied by Qwen or user paths. Preserve the original source structure list in `project.json`.

## Variation model

Add a small validated `SectionVariation` containing:

- instance identity;
- purpose/energy inherited from `song_plan.json`;
- logical instruments selected from the registry;
- role/density adjustments expressed as bounded musical parameters;
- transition intent reference.

Implement deterministic variation rules:

- The first occurrence remains the most source-focused unless the global plan identifies another explicit intro.
- Later repeated occurrences may add one allowed supporting layer, increase/decrease density, change an allowed role, or simplify before an ending.
- Variation follows the global energy curve and may not exceed the song-plan instrument set.
- Preserve piano in every section.
- Do not require every occurrence to differ if energy/instrument constraints make a safe variation impossible, but reject plans where all occurrences of a repeated part are accidentally byte-identical despite available variation.
- Do not change source notes, source duration, part order, or section count.

Define exact precedence: validated explicit song-plan progression first, then deterministic variation fills missing detail, then schema validation. No hidden random seed is allowed.

## Tests

- `A A B B A` becomes `A1 A2 B1 B2 A3`.
- Multi-digit repetitions remain stable.
- Single occurrences are unchanged.
- Repeated sections receive bounded, musically related changes.
- Low→high energy introduces layers; high→low simplifies them.
- Piano is always retained.
- Unknown instruments/roles and structure mutations are rejected.
- Identical input produces identical variations.
- Source MIDI hashes are unchanged.

Manual smoke test:

- Inspect variation data for `A A B B A`.
- Render later during Task 012 and verify repeated sections are recognizably related but not mechanically identical in orchestration.

## Acceptance criteria

- Repeated occurrences have stable independent identities.
- Variation is deterministic and bounded by the global plan.
- Source MIDI is referenced, never copied and edited per occurrence.
- The model cannot reorder or rename instances.
- Variation data is ready for the detailed arrangement schema.

## Out of scope

- Rendering, note generation, or transition synthesis.
- Random humanization.
- Changing the user's song structure.

## Completion report

Report identity/variation rules, changed files, tests/build commands, example `A A B B A` output, source-integrity check, assumptions, and remaining variation limitations.
