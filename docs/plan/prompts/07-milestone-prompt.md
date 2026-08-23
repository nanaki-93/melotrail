# Agent Prompt — Milestone 07: Qwen Producer / Global Arrangement Schema

Use this prompt from the root of the MeloTrail repository.

```text
Role:
You are the coding agent implementing exactly one MeloTrail milestone.

Goal:


Repository context:
- Read AGENTS.md first.
- Read PLAN.md.
- Read the V2 roadmap milestone file for Milestone 07: Qwen Producer / Global Arrangement Schema.
- Inspect the existing implementation before proposing new abstractions.
- Reuse existing patterns/classes when they already satisfy part of the milestone.

Required outcome:
- Define strict `song_plan.json` and `arrangement_plan.json` schemas.
- Qwen may choose section purpose, energy, density, instrument entry/exit, pattern family, variation, groove character, swing target, drum-fill placement, transition strategy and repeated-section evolution.
- Allow tempo-feel changes only when the style profile/user settings permit them.
- Human-authored melody, project harmony and structure remain authoritative by default.
- Qwen must not output raw filesystem paths, arbitrary MIDI events or unknown pattern identifiers.
- Use allow-lists and numeric bounds.
- Keep a deterministic planner baseline for A/B comparison.
- Ensure two different source songs can naturally receive different arrangement fingerprints rather than merely the same preset template.

Success criteria:
- Qwen cannot emit arbitrary raw MIDI or file paths.
- Unknown patterns/instruments are rejected.
- All numeric values are validated.
- Global plan covers the complete song before instrument generation.
- Human structure is preserved unless an explicit future opt-in permits structural editing.
- Arrangement schema supports real variation in groove, swing, fill placement, density, orchestration and transitions.

Global MeloTrail constraints:
- Project key/mode, tempo/meter, and Verse/Chorus/Bridge chord progressions are authoritative.
- Preserve the recognizable human-authored melody and protected anchors.
- Never silently replace project harmony with inferred harmony.
- Prefer deterministic code for objective musical rules.
- Qwen should make high-level producer/arranger decisions, not arbitrary raw-MIDI decisions, unless this milestone explicitly says otherwise.
- Do not use a flattened WAV mix as the canonical arrangement state.
- Preserve intermediate MIDI/audio/reports in debug mode.
- Do not rewrite the working per-track AI Enhance/LoFi transformation unless this milestone requires it.
- Keep changes narrowly scoped to this milestone.
- Do not implement later milestones early.

Execution:
1. Inspect the codebase and identify the current path related to this milestone.
2. Run the relevant existing tests before changing code.
3. Briefly state:
   - what already exists,
   - what is missing,
   - which files you expect to change.
4. Implement the smallest coherent solution that meets this milestone.
5. Add/update tests.
6. Run relevant unit/integration tests and the project build.
7. Run one realistic smoke example if MIDI/audio behavior changed.
8. Review the diff for unrelated changes.

Verification:

Stop rules:
- If an existing implementation already satisfies the milestone, do not rewrite it; prove it with tests and make only missing changes.
- If a required architectural assumption conflicts with the current codebase, stop after documenting the conflict and propose the smallest resolution.
- If a generated musical candidate fails validation, do not weaken validation merely to make the test pass.
- If tests reveal a pre-existing unrelated bug, report it separately unless a tiny safe fix is required for this milestone.
- Do not continue to the next milestone.

Final response:
Return only:
1. Summary of what changed.
2. Files changed.
3. Tests/build/smoke commands run and their results.
4. Any assumptions.
5. Remaining issues or follow-up items for this milestone only.
```
