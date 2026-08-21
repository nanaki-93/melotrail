# Task 122 — AI Fix canonical context cutover

## Goal

Make AI Fix repair the selected corrected MIDI under declared project harmony
instead of treating inferred MIDI harmony as authority.

## Dependencies

Tasks 119 and 121.

## Required changes

- Replace the context assembled by `MidiAiFixInputFactory` with the part-repair
  projection from `MusicalAuthorityBuilder`.
- Include declared key/scale, tempo, meter, occurrence and harmonic timelines,
  current selected input hash, analyzed discrepancies, melody identity/anchors,
  and the existing repair intensity/limits.
- Label inferred key/chords as diagnostic observations. Prompt and DTO names must
  make clear that declared values win when they disagree.
- Require the model response to echo input/context schema versions and hashes.
  Reject stale, missing, unknown, or mismatched values before applying edits.
- Validate every pitch edit against the active chord/scale, stable note identity,
  existing AI Fix budgets, MIDI range, and anchor rules. Preserve tempo, meter,
  duration, and source MIDI.
- Convert accepted changes to the shared `MidiMutationReport` while preserving
  existing draft, approval, bypass, provenance, and audit data needed for
  supported project reads.
- Remove inference-authority branches and exclusive validators after callers and
  fixtures migrate. Inference may remain only as bounded diagnostics.

## Failure behavior

A context conflict is reportable, not fatal, when canonical settings are valid.
Malformed model output, a stale hash, an out-of-budget edit, an anchor edit, or
an unresolved harmonic position rejects the candidate atomically and leaves the
current selection unchanged.

## Tests

- Declared A minor remains effective when analysis infers C major.
- Repeated occurrences receive the correct chord at identical local ticks.
- Prompt/input snapshots include canonical context and stable hashes.
- Stale-hash, chord-clash, anchor, range, and budget violations are rejected.
- Bypass and approved-selection precedence remain unchanged.
- Canonical v4 fixtures retain current AI Fix evidence; no superseded AI Fix
  project shape remains readable.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- AI Fix has no code path that silently replaces declared harmony.
- Every accepted edit has canonical harmonic evidence and a shared mutation
  record.
- Failed or rejected candidates never become analysis input.

## Exclusions

Do not broaden AI Fix into stylistic enhancement or alter MIDI Feel.
