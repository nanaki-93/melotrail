# MeloTrail — Reusable Milestone Agent Prompt

Use this when you want to run a milestone manually without the milestone-specific prompt.

```text
Role:
You are implementing one milestone in MeloTrail.

Goal:
Implement ONLY Milestone <NUMBER>: <TITLE>.

Read first:
- AGENTS.md
- PLAN.md
- the selected milestone markdown file

Before coding:
- inspect the current implementation;
- run relevant baseline tests;
- identify what already exists;
- list the smallest set of files likely to change.

MeloTrail invariants:
- project key/mode, tempo/meter and Verse/Chorus/Bridge chord progressions are authoritative;
- preserve recognizable source melody and protected anchors;
- deterministic logic owns objective musical rules;
- Qwen owns high-level producer/arranger decisions;
- do not use flattened audio as canonical arrangement state;
- preserve debug artifacts;
- do not implement later milestones early.

Success:
- all acceptance criteria in the selected milestone are satisfied;
- regression tests exist for the failure mode being addressed;
- existing relevant tests still pass;
- a smoke example succeeds if MIDI/audio output changed;
- the implementation does not weaken existing musical safeguards.

Stop:
- do not rewrite existing code if it already satisfies the milestone;
- stop and report architectural conflicts rather than making a broad speculative rewrite;
- do not continue to the next milestone automatically.

At the end report:
1. Summary
2. Files changed
3. Tests/build/smoke results
4. Assumptions
5. Remaining milestone-specific issues
```
