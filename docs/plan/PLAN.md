# Melotrail — Simplification, Worker Consolidation, and Documentation Plan

## Outcome

Deliver a smaller, guided Compose Desktop workflow with a prominent **New
Project** action, a clean source-import path, a dark cinematic theme based on
`docs/pictures/UI/example.png`, and semantic colours that distinguish track
types and operational states. Consolidate Kotlin code that is specific to the
separate Python HTTP worker under `app.melotrail.worker`, remove verified dead
or obsolete code, and document the product and its callable functions.

The existing project artifacts, typed application services, source immutability,
worker protocol, MIDI repair approval, and provenance rules remain the source
of truth. This plan changes presentation and package ownership; it does not
turn the desktop application into a browser UI or silently weaken workflow
validation.

## Reference interpretation

`docs/pictures/UI/example.png` is a visual reference, not an asset to embed.
Use its near-black/navy canvas, layered dark panels, restrained teal primary
action colour, warm amber secondary highlight, and coloured instrument lanes.
Do not copy its travel imagery, names, timestamps, weather, or location
content. Reuse the existing no-metadata local scene placeholder where artwork
is needed.

## Product decisions used by this plan

- The normal happy path is one clear next action at a time. Advanced processing
  and diagnostics remain available from an explicit secondary details view;
  they are not shown before an import succeeds.
- Import derives a safe stable part ID from the filename and lets the user
  rename the role later. Required source-rights/provenance information is still
  collected at the appropriate confirmation step; it is not discarded to make
  the screen shorter.
- Direct MIDI and source audio remain two choices because they lead to
  materially different safe workflows. Audio is explicitly described as the
  eligible solo-piano transcription route.
- Lo-fi feel, audio cleanup, repair parameters, and planner diagnostics move
  out of the initial import surface. The backend capability is retained and is
  reached only when its workflow step is current.
- “Every function” means every non-trivial Kotlin and Python function/method in
  production code receives useful maintainers’ documentation or an approved
  local exemption. Trivial data accessors, generated code, overrides whose
  contract is inherited, and test helpers are inventoried but do not need
  redundant prose.

## Sequenced tasks

| Order | Task | Primary deliverable | Depends on |
| ---: | --- | --- | --- |
| 1 | [101 — Code hygiene audit and safe cleanup](tasks/completed/101-code-hygiene-audit-and-cleanup.md) | Verified removal/refactor of obsolete code | — |
| 2 | [102 — Kotlin Python-worker package consolidation](tasks/completed/102-kotlin-python-worker-package-consolidation.md) | One `worker` ownership boundary | 101 |
| 3 | [103 — Track-process workflow documentation](tasks/completed/103-track-process-workflow-documentation.md) | User-facing track workflow | 102 |
| 4 | [104 — MIDI import-process documentation](tasks/completed/104-midi-import-process-documentation.md) | Accurate direct-MIDI and audio-to-MIDI guide | 102 |
| 5 | [105 — Function documentation coverage](tasks/105-function-documentation-coverage.md) | Documented Kotlin/Python callable code | 102 |
| 6 | [106 — Theme and semantic colour system](tasks/106-theme-and-semantic-colour-system.md) | Reference-derived accessible design tokens | 101 |
| 7 | [107 — Visible project actions and simpler navigation](tasks/107-visible-project-actions-and-simpler-navigation.md) | Labeled New Project and reduced shell actions | 106 |
| 8 | [108 — Guided import experience](tasks/108-guided-import-experience.md) | Step-by-step import and minimal initial detail | 103, 104, 106, 107 |
| 9 | [109 — Workspace option reduction and release verification](tasks/109-workspace-option-reduction-and-release-verification.md) | Simplified pages, updated docs, release evidence | 105, 106, 107, 108 |

## Cross-cutting implementation rules

- Preserve supported project reads and perform only explicit, atomic project
  saves. Never mutate `source/` files during cleanup, transcription, MIDI
  repair, or UI migration.
- Keep Compose as a presentation adapter over typed application services. No
  worker calls, file writes, or workflow decisions belong in composables.
- Retain existing accessibility semantics and keyboard paths. A colour must
  reinforce a text/icon/label state, never be the only carrier of meaning.
- For every removed or relocated symbol, update tests, imports, documentation,
  and public/package references in the same task. Do not remove code based only
  on a name search; establish reachability first.
- Work one task contract at a time. Run the smallest relevant tests before and
  after each change, then the required module checks for that contract.

## Completion evidence

Before closing the plan, run root Kotlin tests, desktop tests and build, and
Python worker tests when affected. Complete the visual checks at 100%, 125%,
and 150% scaling and verify wide, medium, and narrow layouts. The final docs
must link to the actual workflow and MIDI guide and contain no stale screen
labels or package paths.
