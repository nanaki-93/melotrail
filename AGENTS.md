# Melotrail agent instructions

## Read before changing the project

Read [PLAN.md](PLAN.md), [README.md](README.md), [TASKS.md](TASKS.md) and
[Architecture](docs/ARCHITECTURE.md). Read the task's relevant owner:
[MIDI contract](docs/MIDI_CONTRACT.md), [UI guideline](docs/UI_GUIDELINE.md),
[Validation](docs/VALIDATION.md), or [TABI video](docs/TABI_VIDEO.md).
PLAN is the only roadmap; TASKS is the only implementation queue. Do not resume
retired MC/UI/VID plans from Git history.

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
- Keep six MIDI destinations and one persistent MIDI player. No audio renderer,
  Python service, sound library, model dependency, mixer or publishing page.

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
edits. Add regression tests for every fixed bug. Follow TASKS dependencies and
status; do not add another plan, prompt, inventory or execution-log document.

## Validation and agent execution

Run applicable focused tests, `make test`, `make build` and `git diff --check`.
For MIDI/workflow/export changes prepare and complete applicable Logic checks
from Validation; record real user evidence before release. Musical scores and
visual/video approvals cannot be fabricated by an agent or inferred from tests.
A missing human decision blocks that gate, not independent development work.

Use the bounded workflow and prompt in TASKS. Parallel agents are appropriate
only when explicitly requested for an implementation run and file ownership is
separate; one coordinator owns task status and integration.

## TABI video boundary

The planned optional companion has separate assets, jobs, provider/encoder
runtime and finished soundtrack input. It must not render MIDI audio, alter the
mix/master or write the MIDI project. Video implementation is a separate chosen
workstream; do not revive the legacy release pipeline. Paid generation needs a
bounded authorized budget. Public upload requires explicit user authorization.
