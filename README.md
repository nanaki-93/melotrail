# MeloTrail Agent Prompts V2

This pack contains one implementation prompt per V2 milestone.

For the validated direct-MIDI and eligible solo-piano audio routes, see the
[MIDI import process](docs/MIDI_IMPORT_PROCESS.md).

## Recommended workflow

For each milestone:

1. Start a fresh agent run/session when practical.
2. Run the corresponding prompt from `prompts`.
3. Let the agent inspect `AGENTS.md`, `PLAN.md`, and the milestone file.
4. Review the agent's proposed scope before allowing a large refactor.
5. Require tests/build/smoke verification.
6. Review/commit the milestone before starting the next one.

Do not give the agent all 18 implementation prompts at once.

## Suggested invocation

Example:

```text
Implement MeloTrail Milestone 04 using prompts/04-milestone-prompt.md.
Work only on this milestone. Stop after tests and report.
```

## Why prompts are deliberately concise

They are outcome-first: the milestone file contains the detailed product requirements, while the prompt defines execution behavior, invariants, verification and stopping rules. This reduces duplicated context and makes it less likely that the agent tries to implement the entire roadmap in one run.
