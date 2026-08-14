# Personal AI Music Arranger — Implementation Task Prompt Template

Copy the prompt below into Qwen3-Coder-30B for **one task at a time**. Replace
`XXX` with a task number. Tasks 022–028 live under `plan/tasks/completed/`;
the new UI, song-creation, and repository-health tasks are 029–037 under
`plan/tasks/`.

```text
You are implementing exactly Task XXX for the Personal AI Music Arranger.

This is a local personal Kotlin/Compose Desktop music workstation. Keep the
implementation small, deterministic, safe, and testable. Do not continue into
another task. The selected task file is the implementation contract; its Goal,
Dependencies, Requirements, Tests, Acceptance criteria, and Out of scope are
binding.

Before coding:
1. Read README.md and plan/AGENT_GUIDELINES.md completely.
2. Find the selected task with:
   rg --files plan/tasks | sort | rg '/XXX-'
   Read that single file completely. If it is not found or more than one file
   matches, stop and report the ambiguity; do not guess.
3. For Tasks 029–037, read plan/PLAN_UI_AND_CREATION.md completely. For desktop
   work also read the relevant sections of plan/PLAN_COMPOSE_DESKTOP.md.
4. Read plan/ARCHITECTURE.md. If the task touches MIDI, SFZ, samples, rendering,
   sound-library location, licenses, drums, or transitions, also read
   plan/SOUND_LIBRARY_BASELINE.md and sounds/README.md. If it changes MIDI-first
   artifacts, read the relevant sections of plan/PLAN_MIDI.md.
5. Inspect plan/UI.png before changing Compose layout, visual tokens, responsive
   behavior, or timeline presentation. It is a visual direction only: do not
   implement its travel, scene, video, weather, image, or location features.
6. Inspect the repository tree and `git status --short`. Preserve all existing
   user changes. Find the current implementation and tests that overlap with
   this task before proposing edits.
7. Run the baseline checks relevant to the selected task before editing:
   - ./gradlew test
   - ./gradlew :desktopApp:test when desktop code exists
   - python3 -m unittest discover -s worker/tests only when worker code changes
   Record pre-existing failures separately; never hide or “fix” an unrelated
   failure.
8. State briefly: repository findings, pre-existing failures, assumptions, and
   the smallest intended file set. Then implement.

Architecture and safety rules:
- The Compose Desktop app is the active product UI. Do not extend or remove the
  old static Spring frontend unless this task explicitly says so.
- Compose is an adapter over typed Kotlin application services. Do not call the
  CLI, parse CLI output, call Spring HTTP, write project files, invoke workers,
  render audio, or perform business orchestration from composables/view models.
- Keep canonical project artifacts as the source of truth. UI state is only
  selection, drafts, display, and operation state; never add a desktop database
  or competing project format.
- Preserve existing CLI behavior and compatibility. When moving behavior into a
  service, add parity tests against canonical artifacts rather than console
  wording.
- Resolve the sound library through one validated injected configuration/locator.
  Do not rely on `Path.of("sounds")`, `/sound`, or process CWD in packaged or
  desktop paths. Preserve the registry’s security/license/sample validation and
  never create a duplicate `instruments/` tree.
- Treat every external input as untrusted: validate extension and actual format,
  IDs, paths, headers, MIDI, registry values, worker output, and model output.
  Do not reveal arbitrary external source paths in persisted project reports.
- Source audio/MIDI is immutable. Derived files must use project-relative,
  validated locations, atomic writes, and output validation. Preserve actual
  sample rate/channel count; work in frames; keep intermediates lossless PCM-24;
  MP3 is final-export-only.
- Audio cleanup must be explicit, conservative, measurable, and reversible.
  Never silently normalize, remove time/silence, alter pitch/tempo, separate
  stems, or overwrite source. Default to inspect-only where the task requires
  consent for cleanup.
- AI is a bounded advisor/planner, never an executor. It may choose only from a
  schema allow-list of logical instruments or measured cleanup candidates. Parse
  and validate JSON; reject invalid output; never execute generated code, shell
  commands, paths, DSP values, MIDI notes, or arbitrary instruments. Retain a
  deterministic fallback and preserve explicit Qwen arrangement approval.
- Keep preview monitoring separate from releases. Never report preview success
  until rendering/decoding/output has actually started; surface renderer,
  library, worker, and device errors with an actionable recovery step.
- Do not introduce a cloud service, database, DI/navigation framework, generic
  plugin system, DAW editor, telemetry, auto-download, source separation, or
  unrelated refactor. Do not implement later task work except for the smallest
  inert compile-safe seam, and report that seam.
- For every task, inspect the changed surface for reproducible bugs, dead
  references, documentation drift, source/artifact safety regressions, and
  leaked resources. Record a narrow follow-up rather than silently expanding
  scope. Task 037 is the only repository-wide audit and legacy-frontend removal
  task; do not delete static frontend files earlier.

Testing and completion:
1. Add or update focused unit/service/view-model/Compose tests required by the
   selected task. Use fakes for worker, renderer, model, and audio device
   boundaries; standard tests must run offline.
2. Run focused tests, then ./gradlew test and relevant changed-module checks.
   When desktopApp changes, run ./gradlew :desktopApp:test :desktopApp:build.
   Run worker tests only when worker code changed. Run packaging/current-OS
   smoke tests only when the selected task requires them.
3. When UI changes, perform the selected task’s manual visual check at 1440×900
   and 1100×720 (plus HiDPI where required). When audio behavior changes, run
   the specified manual A/B/listening smoke and verify source hashes plus output
   WAV/MP3 format rules.
4. Review the final diff for CWD-dependent library access, UI-only orchestration,
   leaked paths, false-success messages, source mutation, task leakage, and
   unrelated user changes.
5. Report: changed files; commands/tests and results; manual results; source /
   artifact validation; assumptions; pre-existing failures; deferred work; and
   remaining limitations. Do not claim optional model, renderer, assets, or OS
   support that you did not actually verify.
```

Start with Task 029. Do not begin Tasks 030–037 until the prior task’s
acceptance criteria and relevant checks are satisfied.
