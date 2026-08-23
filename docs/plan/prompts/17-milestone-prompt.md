# Agent Prompt — Milestone 17: Mastering + Commercial-Ready Gate

Use this prompt from the root of the MeloTrail repository.

```text
Role:
You are the coding agent implementing exactly one MeloTrail milestone.

Goal:


Repository context:
- Read AGENTS.md first.
- Read PLAN.md.
- Read the V2 roadmap milestone file for Milestone 17: Mastering + Commercial-Ready Gate.
- Inspect the existing implementation before proposing new abstractions.
- Reuse existing patterns/classes when they already satisfy part of the milestone.

Required outcome:
- Replace RMS-style loudness approximation with BS.1770 / EBU-style integrated loudness measurement.
- Add real/oversampled true-peak measurement.
- Introduce `MasteringProfile`.
- LoFi default: nominal -14 LUFS integrated, tolerance ±1 LU, maximum true peak -1.0 dBTP.
- Treat -14 LUFS as a delivery reference, not an exact pass/fail law.
- Measure/inspect LRA, crest/peak-to-loudness behavior and limiter gain reduction.
- Reject masters with excessive dynamics destruction even if loudness is numerically correct.
- Create `CommercialReadyGate`.
- Require source provenance, instrument commercial-use licenses, required attribution, human approvals, recognizability, melody audibility, critic results and AI metadata.
- Generate `provenance.json` and `youtube-release.json`.
- Record AI disclosure recommendation when generative AI materially contributed.

Success criteria:
- Integrated loudness is measured using a proper loudness standard implementation.
- True peak is measured, not merely sample peak.
- Default LoFi profile uses <= -1.0 dBTP.
- Exact -14 LUFS is not required if dynamics quality would suffer.
- Over-compressed masters can fail even when loudness target is met.
- Commercial Ready is separate from build success.
- Missing license/approval/recognizability blocks release.
- Release metadata records relevant AI/provenance information.

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
