# Task 129 — Stage comparison and diagnostic reports

## Goal

Make quality changes and regressions inspectable through persisted, typed
reports and existing desktop review surfaces without adding a CLI or new product.

## Dependencies

Tasks 121–128.

## Public contracts

- Add a read-only `StageComparisonService.compare(root, before, after)` that
  accepts only current canonical artifact references or explicitly requested
  historical evidence and returns a versioned `StageComparisonReport`.
- Report input/output/context hashes, stage identities, note additions/deletions/
  modifications, pitch/range, chord fit, anchor preservation, timing, velocity,
  duration, density, role/occurrence metrics, edit-budget usage, warnings, and
  validator/critic issue deltas where applicable.
- Use stable typed reason/status codes. Human-readable desktop copy is derived by
  the adapter and is not persisted as workflow truth.

## Required behavior

- Reuse canonical MIDI readers, mutation reports, role reports, and critic
  metrics; do not create parallel metric implementations.
- Persist the comparison report alongside the owning stage evidence and bind it
  to exact hashes. Historical comparison is read-only and visibly marked stale;
  it never changes selection/readiness.
- Extend existing review snapshots/cards for AI Fix, per-track Enhance, Cohesion,
  Full-Song Critic/Enhance, and Humanization with the smallest relevant metric
  subset and links/actions already supported by the desktop workflow.
- Keep reports bounded and deterministic: sort by occurrence/role/tick/note ID,
  cap detail rows at 500, and retain aggregate totals plus a truncation marker.
- Do not add `melotrail debug compare`, a REST endpoint, telemetry, a database,
  or a dedicated diagnostics screen.

## Tests and manual checks

- Compare identical artifacts, timing-only changes, pitch edits, additions,
  deletions, repeated occurrences, and truncated reports.
- Reject paths outside the project and hash mismatches.
- Report hashes and serialized ordering are stable across reruns/relocation.
- Desktop state distinguishes current, stale historical, failed, bypassed,
  no-op, draft, and approved evidence.
- Manually verify keyboard access and readable labels in affected existing cards.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Each MIDI mutation stage exposes before/after evidence with one metric meaning.
- Diagnostics cannot make a workflow stage complete or select an artifact.
- No new command-line, server, or standalone diagnostic surface exists.

## Exclusions

Do not add audio waveform/spectral comparison or subjective quality ratings.
