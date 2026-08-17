# Task 078 — UI and MIDI Release Acceptance

## Goal

Prove that the redesigned workspace is visually faithful, the guided workflow
is understandable, Lo-fi MIDI is used end to end, and every arranged instrument
remains synchronized in generated MIDI and rendered audio.

## Dependencies

- Task 077 accepted.
- Read `../../PLAN.md` and Tasks 073–077 completely before implementation.

## Scope

This is an acceptance and targeted defect-remediation task. It may fix only
issues discovered by the checks below. Record unrelated findings as narrow
future tasks instead of broadening the milestone.

## Requirements

- Create or finalize an offline end-to-end fixture covering:
  - direct MIDI import and standard preparation;
  - Original and fixed Lo-fi MIDI Feel selection;
  - different source PPQ values;
  - repeated structure occurrences;
  - deterministic and Qwen cohesion/arrangement paths where available;
  - all five instruments;
  - no transition, crossfade, and bridge cases;
  - mix, optional Lo-fi audio texture, master, and release metadata.
- Exercise the supported workflow through application/UI commands without
  editing project files by hand.
- Verify source, raw, and repaired hashes remain unchanged through Lo-fi,
  cohesion, arrangement, rendering, mixing, mastering, and export.
- Verify selected and occurrence artifact identities/fingerprints at each stage.
- Validate canonical PPQ, tempo/meter map, occurrence/transition boundaries,
  total ticks, total seconds, sample rate, channels, PCM depth, total frames,
  and synchronization-report tolerances.
- Prove piano, bass, drums, pad, strings, transitions, visual timeline, and
  rendered stems share the same authoritative song clock and do not accumulate
  drift.
- Verify invalidation is exact after changes to feel, analysis, structure,
  cohesion, arrangement, mix, and audio texture. Stale files remain inspectable
  but cannot appear current or be reused.
- Verify the architecture guard rejects new downstream direct access to
  repaired MIDI where selected/occurrence MIDI is required.
- Complete visual acceptance against `../../UI.png` at 1536 × 1024 and agreed
  responsive viewports. Record every intentional deviation and require explicit
  product approval rather than silently relaxing the reference.
- Complete keyboard, focus, accessible-name, non-color-state, scaling, and
  minimum-window checks.
- Complete real-device playback checks for Original, Lo-fi, occurrence/cohesion
  boundaries, dry mix, Lo-fi audio texture when available, and master.
- Inspect logs/reports for source content, model output, secrets, unsafe paths,
  stale success, and misleading unavailable-dependency claims.
- Produce an acceptance report with commands, results, visual comparisons,
  listening environment, optional dependency availability, known limitations,
  approved deviations, and deferred work.

## Automated commands

- Focused test commands for every repaired finding.
- `./gradlew test`
- `./gradlew :desktopApp:test :desktopApp:build`
- `.venv/bin/python -m unittest discover -s worker/tests` only if worker code or
  its contract changed. Per Future Task 059, do not substitute system Python.
- `./gradlew :desktopApp:packageDistributionForCurrentOS` when native packaging
  is part of the release claim.

Do not report a command as passed unless it ran in this task. Record unavailable
renderer, worker, model, audio-device, or packaging checks explicitly.

## Manual acceptance matrix

- Import one real direct MIDI using only the normal UI; record any ambiguous
  control or unexpected technical choice.
- A/B Original and Lo-fi MIDI at matched volume. Confirm the preview and built
  piano stem both reflect 80 BPM/58% swing when Lo-fi is selected.
- Listen to a multi-section all-instrument song. Inspect the opening, every
  section/transition boundary, repeated sections, final bars, seek positions,
  pause/resume, replay, stop, and source switching.
- Compare the wide UI side by side and by transparent overlay with `UI.png`.
- Complete the primary workflow by keyboard only.
- Launch the current-OS packaged application, open a new project and supported
  legacy project, and repeat a bounded playback/import smoke when packaging is
  claimed.

## Acceptance criteria

- All available automated suites pass and all skipped checks have explicit,
  truthful reasons.
- A normal MIDI import requires one confirmation and one obvious preparation
  action.
- Selecting Lo-fi changes preview, analysis, cohesion input, arranged piano,
  cache identity, reports, and provenance.
- Approved occurrence MIDI is the arranged piano source; stale or rejected
  cohesion is never consumed.
- Every instrument is aligned at each section boundary within one canonical
  tick, rendered fixture onsets are within one audio frame after conversion,
  and no long-fixture drift occurs.
- Every stem and mix has the authoritative total frame count.
- Source/raw/repaired evidence remains immutable.
- The 1536 × 1024 UI meets the Task 077 visual tolerance or has explicit
  approved deviations; responsive and accessibility checks pass.
- Real-device listening confirms audible output and transport behavior for all
  available artifact types.
- The acceptance report is complete and does not claim untested support.

## Out of scope

- Publishing the application or generated media.
- Claiming Windows/Linux, renderer, model, decoder, or audio-device support not
  tested in its native environment.
- Implementing unrelated Future Tasks 060–062.
- New features beyond fixes required to satisfy Tasks 073–077.
