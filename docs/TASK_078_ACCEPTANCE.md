# Task 078 — UI and MIDI Release Acceptance Record

Review date: 2026-08-17  
Status: **NOT APPROVED FOR RELEASE**

This is a truthful evidence record for Task 078, not a release declaration.
The offline Kotlin and Compose checks passed. The renderer/model/device and
interactive visual gates below were not available in this run, so their support
is not claimed.

## Finding repaired in this task

Piano stem assembly previously selected a single MIDI artifact by part ID. That
could render a shared source for repeated structure occurrences even after
per-occurrence cohesion had been approved. `StemRenderingMixer` now resolves
the current occurrence MIDI for the approved arrangement, records every
occurrence identity/path/hash in its render fingerprint, and rejects a
cohesion/arrangement identity mismatch. Before cohesion is approved, the
existing selected-MIDI fallback remains available for compatible legacy/unit
arrangements.

`StemRenderingMixerTest` supplies two repeated `A` occurrences whose second
approved cohesion artifact is transposed. The rendered piano input contains
both the original and transposed pitches, and SHA-256 values for `source/A.mid`
and `midi/clean/A.mid` remain unchanged. `SelectedMidiArchitectureTest` guards
that piano rendering retains the occurrence-MIDI resolver rather than reducing
repeated occurrences to a single part artifact.

## Automated evidence

| Command | Result |
| --- | --- |
| `./gradlew :test --tests app.melotrail.arrangement.StemRenderingMixerTest --tests app.melotrail.arrangement.SelectedMidiArchitectureTest --rerun-tasks` | Passed. Focused occurrence-MIDI rendering, source/clean immutability, render format/frame validation, cache freshness, and architecture guard. |
| `./gradlew test :desktopApp:test :desktopApp:build --rerun-tasks` | Passed. Root Kotlin tests, Desktop Compose tests, and Desktop build completed. |

The full run emitted existing compiler warnings (deprecated `toChar`, redundant
JSON creation, named-parameter mismatches, and unchecked serializer casts); it
had no test or build failure. An earlier attempted focused command failed only
because its root-test filter was applied to `:desktopApp:test`, where those test
classes do not exist; rerunning the root task explicitly passed.

Existing offline coverage retained by the full suite includes direct MIDI and
preparation boundaries, fixed Original/Lo-fi MIDI selection, selected-MIDI
hash/freshness validation, deterministic and strict-schema Qwen planning,
repeated cohesion occurrences, canonical timing/PPQ reports, transition cases,
PCM-24 master/release metadata, stale-artifact validation, and Desktop
semantics/keyboard models. The added regression closes the missing handoff from
approved occurrence MIDI to the piano render input.

## Manual and optional-dependency matrix

| Check | Result / recovery action |
| --- | --- |
| Renderer-backed all-instrument, mixed-PPQ, no-transition/crossfade/bridge fixture; onset/frame A/B | Not run. Configure a validated local `sfizz_render` and the complete approved local sound pack, then capture source/raw/repaired hashes and synchronization reports before and after build. |
| Original/Lo-fi preview and rendered-piano A/B | Not run. Requires renderer and a real output device; verify fixed 80 BPM / 58% swing at matched monitor volume. |
| Dry mix, Lo-fi audio texture, master, transport, seek, pause/resume, replay, and source switching | Not run. Requires real audio output and the worker for relevant stages. Record device, OS, volume, listener, and result. |
| Live Qwen planning | Not run. No local LM Studio/Qwen model was invoked; strict JSON fixture tests are offline only. |
| 1536x1024 overlay against `plan/UI.png`, responsive viewports, scaling, keyboard-only, focus, and assistive-technology checks | Not run interactively. Automated Desktop tests passed but do not replace visual, keyboard, or accessibility review. No intentional visual deviation is approved. |
| Current-OS package and installed new/legacy project smoke | Not run for this task. Packaging is not claimed; run `./gradlew :desktopApp:packageDistributionForCurrentOS`, install the DMG, then smoke new and legacy opens before making a package claim. |
| Worker test suite | Not run: no worker code or worker contract changed in this task. If it changes, run `.venv/bin/python -m unittest discover -s worker/tests`. |

## Limits and deferred work

No source, raw MIDI, repaired MIDI, sample-library content, worker behavior,
audio DSP, project format, or package was changed. There are no approved visual
deviations. Release acceptance remains withheld until every manual row above is
executed with its actual dependency and recorded here; Windows and Linux are
not claimed.
