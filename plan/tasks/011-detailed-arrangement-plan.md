# Task 011 — Detailed MIDI-First Arrangement Plan

## Goal

Convert the global song plan and section variations into a strictly validated, renderable `arrangement.json` containing musical roles—not arbitrary MIDI notes.

## Dependencies

- Task 009 produces `song_plan.json`.
- Task 010 produces stable instance variation.
- Task 006 defines allowed instruments.

## Compatibility

- Continue reading arrangement versions 1 and 2 used by the current audio renderer and transition preview workflow.
- Add the MIDI-first arrangement as version 3 rather than changing the meaning of old fields in place.
- Retain the repository's existing `transitionOut` direction to avoid ambiguity and unnecessary migration.
- Qwen plans continue to use a draft/preview/approve workflow; deterministic plans may be written directly after validation.

## Version 3 contract

Each section must contain:

- `index`, `instanceId`, and `partId` matching song structure exactly;
- `role`/purpose and finite energy `0.0..1.0`;
- exactly one piano source plan using `mode: source`;
- zero or one plan for each generated logical instrument;
- a validated `transitionOut` intent.

Generated instrument parameters are typed by instrument:

- bass: role, density, movement, register, syncopation;
- drums: role, density, kick density, snare pattern, hi-hat density, swing, fill-last-bar;
- pad: role, density, register;
- strings: role, density, register.

Use sealed/typed Kotlin models or equivalent strict validation so fields for one instrument cannot leak into another. All continuous values are finite and bounded. Roles, registers, movements, snare patterns, and transitions are allow-listed.

## Arrangement engine

- Deterministically expand the song plan/variation into safe defaults for missing detailed parameters.
- If Qwen supplies detail, treat it as an untrusted v3 document and validate it against song plan, structure, registry, and parameter bounds.
- Qwen must never supply notes, frequencies, sample data, file paths, commands, executable content, renderer configuration, sample rates, or output paths.
- The approved arrangement may choose patterns/roles only; deterministic generators produce events.
- Preserve `arrangement.draft.json` for Qwen and atomically approve to `arrangement.json`.
- Keep any existing approved file untouched until approval.

## Tests

- Deterministic v3 generation from a known song plan.
- Strict v1/v2 compatibility reads.
- V3 round trip and default expansion.
- Exact structure/instance matching.
- Piano source requirement and no duplicate instruments.
- Per-instrument field/role validation.
- Boundary values and rejection of NaN/infinity.
- Fixture-backed valid/invalid Qwen v3 outputs.
- Reject paths, code, commands, unknown fields, arbitrary notes, renderer options, and structure changes.
- Draft/approve atomicity and preservation of approved arrangement on failure.

Manual smoke test:

- Generate deterministic and Qwen drafts for the Task 009 project.
- Inspect all repeated sections, role changes, energy, and transition intents before approval.

## Acceptance criteria

- Version 3 is decision-complete for deterministic MIDI generators without containing note events.
- Old arrangements remain readable.
- Model output cannot influence filesystem or execution behavior.
- Approved and draft artifacts remain separate.
- Arrangement preserves source piano identity and exact user structure.

## Out of scope

- Rendering previews from the new roles.
- Actual MIDI composition or audio generation.
- AI mixing advice.

## Completion report

Report versioning/migration choices, schema/allow-lists, changed files, fixture and compatibility tests, commands, manual inspection, assumptions, and remaining schema limitations.
