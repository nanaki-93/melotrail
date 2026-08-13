# Compose Desktop Task Prompt Template

Copy this into your coding agent for one task at a time. Replace `XXX` with a
task number from 022 through 028. The selected task file is the implementation
contract; do not copy its goal and requirements into the prompt again.

```text
You are implementing Task XXX from the Compose Desktop UI plan for the Personal AI Music Arranger.

This is a local personal project. Keep the implementation simple.
Implement only this numbered task. Do not continue into the next task.

Before coding:
1. Read README.md.
2. Read plan/AGENT_GUIDELINES.md, plan/ARCHITECTURE.md, and plan/PLAN_COMPOSE_DESKTOP.md completely.
3. Read the single file matching plan/tasks/XXX-*.md completely. Treat its Goal,
   Requirements, Tests, Acceptance criteria, and Out of scope sections as binding.
4. If the task touches instruments, SFZ, samples, licenses, MIDI generation,
   rendering, drums, or transitions, also read plan/SOUND_LIBRARY_BASELINE.md.
5. If the task changes a MIDI-first workflow or artifact contract, read only the
   relevant sections of plan/PLAN_MIDI.md and the completed task that introduced
   that behavior.
6. For Task 024 or later, inspect plan/UI.png before changing layout or styling.
7. Inspect the current repository tree and Git status. Preserve existing user
   changes and do not modify unrelated files.
8. Find current implementations and tests that overlap with the selected task.
   In particular, inspect ArrangementProjectCommands before extracting behavior;
   do not assume the task plan exactly matches code that may have changed.
9. Run the baseline tests that are available before editing:
   - ./gradlew test
   - ./gradlew :desktopApp:test when desktopApp already exists
   - Python worker tests only when the selected task changes worker behavior
10. State briefly what you found, any pre-existing failures, and the smallest
    intended file set before coding.

Implementation rules:
- Prefer existing libraries and patterns.
- Do not rewrite working code unnecessarily.
- Keep changes small and reviewable.
- Add tests for new behavior.
- Do not introduce unrelated refactors.
- The CLI and Compose Desktop app are adapters over the same typed application
  services. Never make the desktop app execute CLI commands, parse CLI output,
  call the Spring API, or duplicate engine orchestration.
- Keep application-service request/result/progress types independent of CLI,
  Compose, Spring, and static-web DTOs.
- Keep filesystem, worker, rendering, mixing, and release side effects out of
  composables and view models. UI code renders state and sends intents.
- Preserve CLI commands and canonical artifact compatibility. Add parity tests
  when moving existing CLI behavior into services.
- Keep project artifacts as the source of truth. Do not add a desktop-only
  project database or a second project format.
- Preserve explicit Qwen draft validation and approval. Never approve a model
  result implicitly.
- Never execute AI-generated code.
- Keep audio processing lossless until final MP3 export.
- Never assume 48kHz or stereo.
- Reuse the existing sounds/ starter library; do not create a duplicate instruments/ tree.
- Never overwrite source MIDI/audio. Keep atomic writes and validate outputs
  before reporting success.
- Do not add scene/video/location features from the visual reference; they are
  explicitly outside this UI plan.
- Do not implement work assigned to a later numbered task unless a minimal
  compile-safe seam is required by the current task. If so, keep it inert and
  report it.

After implementation:
1. Run relevant tests.
2. Run ./gradlew test and the relevant build/check for every changed module.
3. If desktopApp exists or changed, run ./gradlew :desktopApp:test
   :desktopApp:build and perform the selected task's desktop smoke test.
4. For Task 028, also run the Compose current-OS packaging task and launch the
   packaged application; do not claim packages for untested operating systems.
5. Run Python worker tests only if worker code changed.
6. Inspect canonical generated files and source hashes if project/audio behavior changed.
7. Review the diff for accidental UI-only business logic, duplicated orchestration,
   unrelated edits, and changes belonging to later tasks.
8. List changed files and commands executed.
9. Report automated and manual results, assumptions, pre-existing failures,
   deferred later-task work, and remaining issues.
```

Give the agent one task at a time while the architecture is changing. Start with
Task 022 and satisfy its gate before moving to Task 023.
