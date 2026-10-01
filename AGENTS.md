# Melotrail agent instructions

## Read before changing the project

Read [README.md](README.md), [Architecture](docs/ARCHITECTURE.md) and the relevant
workstream's roadmap and queue before changing the project:
- Audio/MIDI: [PLAN-AUDIO.md](PLAN-AUDIO.md) is the only audio roadmap;
  [TASKS-AUDIO.md](TASKS-AUDIO.md) is the only audio implementation queue.
- Video: [PLAN-VIDEO.md](PLAN-VIDEO.md) is the only video roadmap;
  [TASKS-VIDEO.md](TASKS-VIDEO.md) is the only video implementation queue.
Read both pairs for shared-boundary changes. Read the task's relevant owner:
[MIDI contract](docs/MIDI_CONTRACT.md), [UI guideline](docs/UI_GUIDELINE.md),
[Validation](docs/VALIDATION.md), or [TABI video](docs/TABI_VIDEO.md).
Do not resume retired MC/UI/VID plans from Git history.

## Product and musical authority

- Kotlin/JVM owns domain, orchestration, MIDI, storage and Compose Desktop UI.
- MIDI is the only musical representation in the arranger. Logic Pro owns
  instruments, audio rendering, mixing, mastering and release sound.
- Project tempo, meter, key, structure and chord durations are authoritative.
  Chromatic chords are valid; key compatibility is advisory.
- Preserve imported MIDI bytes and protected melody events. Never silently
  overwrite accepted candidates, current MIDI projects or export snapshots.
- Melody analysis and suggested plans cannot become authority without explicit
  confirmation. Melody edits remain separate approved candidates, outside this plan.
- Generation is deterministic for the same inputs/settings/versions/seed and
  targeted by role/occurrence. Include all used dependencies in fingerprints.
- Full-draft playback is allowed before acceptance; export is accepted-only.
- Keep six MIDI destinations and one persistent MIDI player. The MIDI workflow
  has no audio renderer, Python service, sound library, model dependency, mixer
  or publishing page; planned video-only tools/models stay behind its lazy boundary.

## Development and removal

Inspect current code, consumers and tests first. Reuse proven current behavior;
replace a required helper behind tests before deleting its legacy owner. Remove
exclusive tests/resources/config with deleted code. No compatibility modes,
dead adapters, duplicate schemas, old-project migration or archived source trees.
Current MIDI artifacts remain protected even when an unsupported schema is rejected.

Legacy removal can proceed after the current MIDI path is proven; it does not
wait for the old participant/holdout gates. Resolve exact repository-owned data
paths, consumers, symlinks and protected exclusions before deleting old audio
projects, libraries or caches. Never delete external user projects or use broad
workspace cleanup. Preserve supplied UI/TABI/video references and Logic evidence.

Make small task-scoped commits when implementation commits are authorized.
Preserve unrelated working-tree changes, including existing Gradle/toolchain
edits. Add regression tests for every fixed bug. Follow the relevant TASKS-AUDIO
or TASKS-VIDEO dependencies and status; do not add another plan, prompt, inventory
or execution-log document.

## Validation and agent execution

Validation must be **headless and non-interactive**. Do not launch or drive visible
Melotrail, browser, Blender, Logic or Apple-editor windows for tests; do not run
Robot/screen capture, native-window resize/replay, GUI installer smoke tests or
require interactive user walkthroughs. These are removed validation/release gates,
not completed checks. A later explicit user request is needed for any UI session.

Keep unit, integration, CLI, media-file, packaging-inspection, presentation-state,
semantics and offscreen-render tests that need no interactive window. Run applicable
focused checks, `make test`, `make build` and `git diff --check` only through a
verified windowless path. Inspect test wiring first: the current default desktop
suite includes a native-window test. Use a headless filtered invocation when needed
and report excluded tests; never launch a window just to obtain a full-suite pass.
Do not change production/test wiring or delete test sources merely for a docs edit.

For MIDI/workflow/export changes prepare and validate MIDI packages and semantic
re-import without opening Logic. Interactive Logic/editor compatibility and live
UI usability are not release prerequisites and must not be claimed as verified.
Artifact-based listening, artwork/motion/design decisions and release approval
remain human; no agent may infer them from tests. A missing retained human decision
blocks only that gate. Preserve historical UI/Logic evidence without replaying it.
See [non-interactive validation](docs/VALIDATION.md#non-interactive-validation).

Use the bounded workflow and prompt in the relevant workstream queue. Parallel agents are appropriate
only when explicitly requested for an implementation run and file ownership is
separate; one coordinator owns task status and integration.

## TABI video boundary

The planned Video tab is an independent creative workspace inside the Kotlin/
Compose Melotrail application. The user supplies externally finished scene images
and optional ready character/background layers; a motion prompt produces a
complete 3–5 minute silent video. In-app picture generation, outfit/style transfer
and mandatory automatic extraction are outside this delivery, not video-delivery
gates. Preserve imported appearance and expose
missing motion inputs rather than invent artwork.
Generic scenarios remain supported; Tokyo, trains, coffee and TABI are
examples, never required presets or acceptance criteria. It needs no MIDI project,
export, song or soundtrack. Look and shot review are optional refinements.

Keep video projects, assets, jobs, models, provider credentials, media tools and
outputs outside MIDI storage and the six MIDI destinations. Try the selected
local generation workflow first; any hosted fallback requires explicit selection
and a bounded authorized budget. Adding audio in an external Apple editor and
public upload stay outside Melotrail. PLAN-VIDEO's VG1–VG6 features distinguish existing
backend foundations from planned integrated controls and full delivery; TASKS-VIDEO owns
current completion status. Do not describe a planned capability as implemented.
The Swift companion's repository owners are removed; preserve its external evidence
as superseded history and do not revive its soundtrack-led workflow.
