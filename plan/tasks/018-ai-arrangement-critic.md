# Task 018 — Structured AI Arrangement Critic

## Goal

Add an optional second planning pass that critiques a complete arrangement using bounded structured changes while retaining a deterministic pass-through path and human approval.

## Dependencies

- Task 017 produces a complete dry arrangement and inspectable stem/mix artifacts.
- Task 011 defines the version-3 arrangement schema and validation.

## Artifact flow

```text
song_plan.json
 -> arrangement_v1.json
 -> critic
 -> arrangement.draft.json
 -> preview and human approval
 -> arrangement.json
```

Keep all three arrangement artifacts while debugging. Do not overwrite an existing approved `arrangement.json` merely because the critic returned valid JSON.

## Critic input

Provide Qwen only:

- `song_plan.json` musical metadata;
- the validated proposed arrangement;
- part MIDI analyses without paths;
- allowed logical instruments and role/transition enums;
- compact deterministic render metrics such as section energy, active-instrument count, and transition presence.

Do not upload or expose source file paths, registry paths, executables, arbitrary audio, shell commands, or raw model-controllable renderer settings.

## Critic output

Use a strict versioned schema containing:

- overall decision: `accept` or `revise`;
- issue list with allow-listed categories:
  - `too_repetitive`;
  - `weak_transition`;
  - `abrupt_energy_change`;
  - `too_many_instruments`;
  - `insufficient_contrast`;
  - `weak_climax`;
  - `source_identity_risk`;
- target section indexes;
- short non-executable rationale;
- bounded replacement values for existing arrangement fields only.

Apply changes through typed domain operations. Do not accept JSON Patch, arbitrary field names, deletion of the source piano, structure edits, new instruments, note events, paths, code, or commands.

## Validation and application rules

- Request JSON only with temperature zero and reject unknown fields.
- Every referenced section must exist.
- Revised energy/density/role/transition values must pass v3 validation and remain within the song plan.
- Preserve section count, order, indexes, instance IDs, and part IDs exactly.
- Preserve at least one piano source plan in every section.
- Limit the number of modified sections/fields per pass to a documented bound.
- Run only one critic pass initially; do not create an autonomous refinement loop.
- Validate the complete resulting arrangement, not only individual edits.
- On any error, keep `arrangement_v1.json` and the approved arrangement untouched.

## Deterministic behavior

Provide a critic mode that validates and passes the arrangement through unchanged. It must be usable without LM Studio and serve as the automated end-to-end default.

## Preview and approval

Reuse the current review principle. Generate boundary or representative section previews from `arrangement.draft.json`; the user must run an explicit approval command before it becomes `arrangement.json`. Approval is atomic.

## Tests

- Deterministic pass-through.
- Fixture-backed `accept` and valid bounded `revise`.
- Every issue category.
- Reject invalid JSON/prose, unknown fields/categories, out-of-range values, excessive edits, structure changes, source removal, new instruments, paths, commands, code, and note data.
- Validate resulting arrangement globally.
- Approved arrangement remains byte-identical on critic/preview failure.
- No live model required by automated tests.

Manual smoke test:

- Critique the Task 017 arrangement.
- Compare `arrangement_v1.json`, draft, and approved plan.
- Listen to affected boundary/section previews and decide explicitly whether revisions improve repetition, contrast, climax, and piano identity.

## Acceptance criteria

- The critic can recommend only bounded schema-defined musical changes.
- Structure and source piano identity cannot be altered.
- Deterministic mode requires no AI.
- All pre-critic and draft artifacts are preserved.
- Human approval remains mandatory for critic changes.

## Out of scope

- Iterative autonomous agents, waveform/audio uploads to Qwen, AI mastering, or arbitrary prose instructions executed by code.

## Completion report

Report schema/edit bounds, changed files, fixtures, tests/build/manual commands, preview/listening decision, assumptions, and critic limitations.
