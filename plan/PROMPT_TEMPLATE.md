# Agent Task Prompt Template

Copy this into your coding agent for one task at a time.

```text
You are implementing Task XXX from the Personal AI Music Arranger plan.

This is a local personal project. Keep the implementation simple.

Before coding:
1. Read README.md.
2. Read ARCHITECTURE.md and AGENT_GUIDELINES.md.
3. Inspect the current repository tree.
4. Find existing implementations that overlap with this task.
5. Run the current tests/build.
6. State briefly what you found and which files you will change.

Goal:
<task goal>

Requirements:
<requirements>

Acceptance criteria:
<acceptance criteria>

Do not:
<task-specific exclusions>

Implementation rules:
- Prefer existing libraries and patterns.
- Do not rewrite working code unnecessarily.
- Keep changes small and reviewable.
- Add tests for new behavior.
- Do not introduce unrelated refactors.
- Never execute AI-generated code.
- Keep audio processing lossless until final MP3 export.
- Never assume 48kHz or stereo.

After implementation:
1. Run relevant tests.
2. Run the project build.
3. Run a smoke test.
4. Inspect generated files if audio changed.
5. List changed files.
6. List commands executed.
7. Report assumptions and remaining issues.
```

Give the agent one task at a time while the architecture is changing.
