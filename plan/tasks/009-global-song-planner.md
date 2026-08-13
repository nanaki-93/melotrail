# Task 009 — Global Song Planner

## Goal

Plan the complete composition in one pass and persist `song_plan.json` without allowing AI to generate notes, paths, code, or executable behavior.

## Dependencies

- Task 005 supplies MIDI analyses for every referenced part.
- Task 006 supplies the instrument-name allow-list.
- Existing structure parsing supplies the exact user-controlled order.

## Existing code to reuse

- `LocalQwenClient`/LM Studio connection and strict JSON parsing patterns.
- Fixture-backed Qwen tests.
- The deterministic-planner fallback principle.

Do not overload the existing `ArrangementPlanner` with this song-level responsibility. Introduce a separate `GlobalSongPlanner` boundary.

## Input contract

`SongPlanningInput` contains:

- project name/version only—no file paths;
- all versioned MIDI part analyses;
- explicit ordered section instances;
- allowed logical instruments;
- requested style;
- bounded musical constraints.

Call Qwen once for the whole composition. Never call it independently for A, B, and C.

## Output contract

Create a versioned `SongPlan` conceptually equivalent to:

```json
{
  "version": 1,
  "style": "warm melancholic lo-fi piano",
  "energyCurve": [0.20, 0.30, 0.55, 0.72, 0.40],
  "sections": [
    {
      "index": 0,
      "instanceId": "A1",
      "partId": "A",
      "purpose": "introduction",
      "instrumentProgression": ["piano"],
      "transitionIntent": "none"
    }
  ],
  "climaxIndex": 3,
  "ending": "resolved"
}
```

Allow-list purposes, transition intents, and ending behaviors in code. Use `piano` as the source-instrument logical name in new MIDI-first plans; retain legacy `source` compatibility only when reading old arrangements.

## Deterministic planner

Implement a deterministic `GlobalSongPlanner` that:

- preserves the exact structure;
- produces a bounded arc based on normalized part energy and position;
- assigns conservative purposes and one climax;
- begins with piano and introduces only requested/available instruments gradually;
- uses no network/model and is stable for identical input.

It is the test oracle and explicit `--planner deterministic` fallback.

## Qwen planner safety

- Request JSON only with temperature zero.
- Parse with `ignoreUnknownKeys = false`.
- Enforce exact section count, indexes, instance IDs, and part IDs.
- Validate all energy values as finite `0.0..1.0` and climax index in range.
- Enforce instrument and enum allow-lists.
- Reject strings resembling or occupying paths, commands, code, extra fields, or arbitrary note data.
- Give Qwen names and musical metadata only; never expose source, registry, output, or executable paths.
- Failure must not overwrite an existing valid `song_plan.json`.

## CLI and persistence

Integrate song planning into `arrange --project ... --planner deterministic|qwen` as a distinct first stage. Persist `song_plan.json` atomically. Qwen output remains reviewable; do not silently approve a detailed arrangement in this task.

## Tests

- Deterministic plan for one section and `A A B B A`.
- Exact structure preservation and energy bounds.
- Only available instruments used.
- Fixture-backed valid Qwen response.
- Invalid JSON, prose/markdown, unknown fields, paths, unknown instruments/enums, NaN/infinite values, wrong order/count/index, and arbitrary note-event fields.
- Existing file remains unchanged on failure.
- No live LM Studio in automated tests.

Manual smoke test:

- Produce deterministic and local-Qwen plans for the same project.
- Inspect the full energy arc, climax, repeated-section intent, instrument progression, and ending.

## Acceptance criteria

- `song_plan.json` is a distinct, validated artifact for the entire song.
- Structure remains controlled by the user.
- Deterministic mode works without Qwen.
- Qwen acts only as a musical planner.
- All model output is untrusted until strict validation succeeds.

## Out of scope

- Detailed render parameters, MIDI note generation, mixing, or criticism.
- Per-part Qwen calls.
- Reordering or inventing source sections.

## Completion report

Report schema and allow-lists, prompt inputs, changed files, fixture coverage, tests/build commands, manual plan observations, assumptions, and remaining planner limitations.
