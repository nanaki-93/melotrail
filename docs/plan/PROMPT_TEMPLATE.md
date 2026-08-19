# Melotrail — Implementation Task Prompt Template

Use this prompt for one implementation task at a time. Replace `XXX` with the
task number and make sure exactly one matching task contract exists under
`s` or `/future-tasks/`. A deferred future task must be explicitly
promoted before implementation.

```text
You are implementing exactly Task XXX for the Melotrail.

This is a local Kotlin/Compose Desktop music workstation with a separate Python
HTTP worker. Keep the implementation small, deterministic, safe, and testable.
Do not continue into another task. The selected task contract's goal, scope,
requirements, tests, acceptance criteria, and exclusions are binding.

Before coding:
1. Read README.md completely.
2. Find the selected contract with:
   rg --files docs/plan/tasks docs/plan/future-tasks 2>/dev/null | sort | rg '/XXX-'
   Read the one matching file completely. If zero or multiple files match, stop
   and report the ambiguity. Do not implement a future contract unless the user
   has explicitly promoted it.
3. Read only the current operational docs relevant to the task:
   - docs/TROUBLESHOOTING.md for desktop setup, readiness, import, or packaging;
   - worker/README.md for Python worker, inspection, cleanup, or transcription;
   - sounds/README.md for MIDI, SFZ, samples, rendering, or licenses.
4. Inspect the repository tree and git status. Preserve existing user changes.
   Find the current implementation and tests overlapping the task before
   proposing edits; treat code and tests as the architecture source of truth.
5. Run the smallest relevant baseline before editing. Normally use:
   - ./gradlew test for root Kotlin changes;
   - ./gradlew :desktopApp:test for desktop changes;
   - .venv/bin/python -m unittest discover -s worker/tests for worker changes.
   Record pre-existing failures separately and do not fix unrelated failures.
6. State the repository findings, assumptions, and smallest intended file set,
   then implement one primary concern plus its direct tests.

Architecture and safety rules:
- Compose Desktop is the product UI. Spring is an optional local JSON API and
  must not regain a browser frontend or static fallback.
- Keep Compose as an adapter over typed Kotlin application services. Do not put
  file writes, worker calls, rendering, transport parsing, or business orchestration
  in composables.
- Canonical project artifacts are the source of truth. Do not add a competing
  database or project format. Preserve supported legacy project reads unless
  the contract explicitly changes them. Project migration must be in memory or
  an explicit atomic save; open must not partially rewrite a project. Treat
  stale artifacts as inspectable evidence, never completion.
- Resolve the sound library through the validated locator/settings boundary.
  Do not depend on process CWD or create a second instrument tree.
- Validate all external input: extensions and actual formats, identifiers,
  paths, MIDI, registry values, worker responses, and model output.
- Source audio and MIDI are immutable. Use validated project-relative derived
  paths, atomic publication, and output validation. Preserve sample rate and
  channels, work in frames, keep intermediates lossless, and make MP3 a final
  export only.
- Audio cleanup must be explicit, conservative, measurable, and reversible.
  Never silently normalize, remove time, alter pitch or tempo, separate stems,
  or overwrite a source.
- AI is a bounded planner/advisor, never an executor. Parse and validate strict
  JSON against schemas and allow-lists. Never execute generated code, commands,
  paths, DSP values, or arbitrary instruments. Keep deterministic behavior and
  explicit approval where the current workflow requires it.
- Preview is not a release artifact. Report success only after the applicable
  decode, render, and output operation has actually started or completed, and
  expose dependency failures with a useful recovery action.
- Do not add cloud infrastructure, telemetry, auto-downloads, a generic plugin
  system, a DAW editor, or unrelated refactors. If another concern is found,
  record a narrow follow-up instead of expanding this task.

Testing and completion:
1. Add focused tests at the affected boundary. Use fakes for worker, renderer,
   model, filesystem, and audio-device boundaries where appropriate; automated
   tests must remain offline.
2. Run focused tests, then the relevant full module checks. For desktop changes,
   run ./gradlew :desktopApp:test :desktopApp:build. Run worker tests only when
   worker code changes. Run packaging only when the contract requires it.
3. For UI changes, perform the contract's visual and keyboard checks. For audio
   changes, perform its specified listening/A-B smoke and verify source hashes
   and output format when those checks are available.
4. Review the final diff for unrelated user changes, source mutation, unsafe
   paths, CWD assumptions, false-success states, leaked resources, and docs that
   no longer match behavior.
5. Commit only if the user or repository workflow requests a commit. Stage
   explicit files only; never stage unrelated or pre-existing changes.
6. Report changed files, commands and results, manual checks, assumptions,
   pre-existing failures, deferred work, and unverified optional dependencies.
   Never claim renderer, model, sample, audio-device, package, or OS support that
   was not actually verified.
```
