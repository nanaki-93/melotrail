# Agent Prompt — Milestone 16: Production Mixer & Audio Critic

Use this prompt from the root of the MeloTrail repository.

```text
Role:
You are the coding agent implementing exactly one MeloTrail milestone.

Goal:


Repository context:
- Read AGENTS.md first.
- Read PLAN.md.
- Read the V2 roadmap milestone file for Milestone 16: Production Mixer & Audio Critic.
- Inspect the existing implementation before proposing new abstractions.
- Reuse existing patterns/classes when they already satisfy part of the milestone.

Required outcome:
- Create typed `MixPlan`.
- Support gain, pan, filtering, EQ, compression, reverb send, stereo placement and section automation.
- Create shared room/reverb and optional drum/music buses.
- Add audio critic for clipping, headroom, low-end overlap, masking, stereo correlation, stem loudness and melody audibility.
- Keep deterministic reference mixer for debugging.

Success criteria:
- Production mixer can create shared ambience.
- Melody audibility is measured.
- Blocking mix issues prevent commercial-ready status.
- Mix decisions are persisted/reproducible.

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
