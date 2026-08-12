# Guidelines for Coding Agents

This is a personal local project, not a commercial SaaS.

## Before every task

1. Read README.md.
2. Inspect the repository tree.
3. Find existing equivalent code.
4. Run current tests/build.
5. Identify the smallest set of files to change.
6. Do not rewrite working DSP.

## Implementation rules

Prefer:
- simple
- local
- deterministic
- testable
- explicit
- small changes

Avoid:
- microservices
- Kubernetes
- cloud queues
- databases
- authentication
- generic frameworks
- premature abstractions
- unrelated refactors

Always:
- validate input at boundaries;
- use explicit audio formats;
- preserve source files;
- add tests for new behavior;
- report assumptions.

## Audio rules

- Never assume 48 kHz.
- Always propagate actual sample rate and channels.
- Work in frames for multi-channel audio.
- Keep intermediate files lossless.
- MP3 only at final export.
- Never use an `.mp3` filename for a WAV writer.
- Avoid per-sample random gain modulation.
- Keep a dry reference when debugging DSP.
- Do not add audible noise unless explicitly requested.

## AI rules

The model is a planner.

Never:
- execute model-generated code;
- execute model-generated shell commands;
- accept arbitrary model file paths;
- allow arbitrary instruments.

Always:
1. request JSON-only output;
2. parse JSON;
3. validate against schema;
4. restrict instruments/transitions to allow-lists;
5. reject invalid output clearly.

Keep a deterministic planner available.

## Task workflow

```text
read task
 -> inspect repository
 -> baseline tests
 -> smallest implementation
 -> tests
 -> manual smoke test
 -> review diff
 -> checkpoint
```

At the end of every task, report:
- changed files
- tests/build commands
- manual tests
- assumptions
- remaining issues

If git is available, checkpoint after a successful task unless the user uses another workflow.

## Audio debugging rule

When audio sounds wrong, isolate the chain one effect/stage at a time. Do not change five DSP effects simultaneously.
