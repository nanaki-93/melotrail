# Task 101 — Code Hygiene Audit and Safe Cleanup

## Goal

Reduce duplication and remove only Kotlin and Python code that is demonstrably
unused, obsolete, or deprecated, without changing Melotrail behaviour.

## Dependencies

- None.

## Requirements

- Establish a baseline with repository status, compilation, root tests, desktop
  tests, and worker tests as applicable. Record pre-existing failures instead
  of absorbing them into this task.
- Build a symbol-level audit for production Kotlin and Python code. Classify
  each candidate as retained, deduplicated, deprecated-but-required for a
  compatibility contract, or safely removable. Include duplicated models such
  as the two `Project`/error-reporting areas only when actual responsibility
  overlap is proven.
- Remove dead compatibility shims, stale comments, imports, unreachable
  branches, and obsolete helpers only after all references (including tests,
  serialization, reflection, Spring, and documentation) are checked.
- Extract or consolidate repeated logic only when the resulting owner is
  clearer and the public/serialized behaviour is unchanged. Prefer small typed
  helpers over broad utility classes.
- Do not delete an API merely because it is not used by the desktop app: check
  the CLI, optional Spring API, tests, configuration, and project migration
  paths first.
- Add characterization tests before changing code where the current behaviour
  is meaningful but insufficiently covered.

## Tests

- Focused regression tests for every deduplicated behaviour and every retained
  compatibility path touched by the change.
- `./gradlew test` for root Kotlin changes.
- `./gradlew :desktopApp:test :desktopApp:build` if desktop source changes.
- `.venv/bin/python -m unittest discover -s worker/tests` if worker source
  changes.

## Acceptance criteria

- The task produces an auditable removal/retention record in its implementation
  report and leaves no unused imports or stale references in changed areas.
- All affected checks pass, or pre-existing failures are separately reported.
- No project artifact layout, wire payload, source immutability rule, or
  supported migration behaviour changes unintentionally.

## Out of scope

- Moving the Kotlin Python-worker boundary (Task 102).
- Redesigning UI controls, product workflow, or domain models without audit
  evidence.
