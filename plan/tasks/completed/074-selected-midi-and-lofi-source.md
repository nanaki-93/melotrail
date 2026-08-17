# Task 074 — Selected MIDI and Lo-fi Source Truth

## Goal

Make the user's Original/Lo-fi MIDI selection the validated source used by
analysis, preview, cohesion input, arrangement, piano rendering, cache keys,
reports, and provenance.

## Dependencies

- Task 073 accepted.
- Read `../../PLAN.md` and Task 073 completely before implementation.

## Scope

This task owns selected-part MIDI resolution, the fixed Lo-fi transform's
correctness, downstream source identity, Lo-fi selection/re-analysis behavior,
and focused UI controls. Per-occurrence cohesion selection and the complete
multi-instrument timeline refactor remain Task 075.

## Requirements

- Add a single typed `SelectedMidiArtifactResolver` or equivalently named
  boundary returning:
  - normalized project-confined path and project-relative reference;
  - part ID and selected artifact kind;
  - profile/version where applicable;
  - SHA-256 fingerprint;
  - PPQ and tempo/meter summary;
  - repair/Lo-fi freshness evidence.
- Reject absolute or escaping paths, symlink escapes, missing/malformed MIDI,
  stale reports, fingerprint mismatches, unapproved repairs, and a selected
  Lo-fi reference without current evidence.
- Preserve supported legacy reads without treating legacy/unknown evidence as
  current Lo-fi input.
- Replace downstream direct reads of `MidiReferences.clean` with the resolver
  wherever the semantic intent is “currently selected MIDI.” Repair, quality
  reporting, raw/repaired A/B, and migration code may continue to address
  explicit artifacts.
- Fix piano MIDI/stem assembly and render-cache fingerprints to use the resolved
  selected MIDI. Do not hash repaired MIDI as a proxy for selected input.
- Ensure analysis, preview, Melody Cohesion input, arrangement validation,
  render reports, stale checks, and commercial provenance use the same typed
  identity.
- Validate `lofi-80-swing-v1` end to end:
  - exactly one 80 BPM tempo event at tick zero;
  - 58% eighth-note swing on eligible offbeats only;
  - downbeats remain fixed;
  - time-signature events are preserved;
  - note identity, ordering, positive duration, legal bounds, and pairing are
    preserved;
  - program changes, controllers, sustain, multi-track events, and end markers
    survive where semantically applicable;
  - collision repairs are bounded, counted, and reported;
  - output is a supported valid Standard MIDI file and publication is atomic.
- Keep raw and repaired MIDI immutable. The Lo-fi file and report remain
  separate derived artifacts.
- Replace the selected-part Lo-fi controls with an Original / Lo-fi MIDI Feel
  segmented choice showing `80 BPM` and `58% swing` explicitly.
- A pending feel choice exposes one `Apply and re-analyze` primary action.
  Confirmation may orchestrate selection and analysis, but failures must retain
  last-known-good artifacts and provide one safe retry.
- Raw/repaired/Lo-fi A/B selection must delegate to the single persistent
  playback session with shared monitor volume and truthful readiness.
- Continue to call final audio DSP `Lo-fi audio texture` everywhere.

## Tests

- Resolver tests for Original and Lo-fi selection, legacy projects, approval
  thresholds, stale evidence, malformed files, path escape, and hash mismatch.
- Transformer tests for PPQ variants, tempo maps, meters, chords, sustain,
  controllers, multiple tracks, collision cases, boundary notes, determinism,
  and atomic rollback.
- Static/architectural regression test preventing new downstream direct reads
  of `MidiReferences.clean` outside allow-listed repair/report/resolver code.
- End-to-end fixture: import -> repair -> select Lo-fi -> analyze -> arrange ->
  build. Assert the piano render input hash equals the Lo-fi artifact hash and
  its timing is 80 BPM with expected swung offbeats.
- Switch back to Original and prove repaired MIDI becomes authoritative while
  exactly the documented descendants become stale.
- View-model/Compose tests for segmented selection, one apply action, progress,
  retry, A/B artifact identity, and distinct audio-texture terminology.
- Verify source/raw/repaired hashes before and after every transform test.
- Run focused tests, then `./gradlew test :desktopApp:test :desktopApp:build`.
  Worker tests are required only if worker behavior changes, using
  `../../../.venv/bin/python` per Future Task 059's environment contract.

## Acceptance criteria

- Selecting Lo-fi MIDI changes the artifact used by analysis and piano render,
  not only project metadata or preview.
- Preview, analysis, cohesion input, rendering, caching, and provenance agree
  on one artifact identity and hash.
- The fixed Lo-fi output is deterministic, valid, audibly distinct, and fully
  described by a current report.
- Switching to Original restores repaired MIDI everywhere.
- Source, raw, and repaired MIDI never change.
- The normal selected-part UI presents one unambiguous apply/re-analysis action.

## Manual check

- A/B Original and Lo-fi MIDI at matched volume on a real audio device. Verify
  tempo and swing in preview and the built piano stem, including replay, pause,
  seek, and stop.

## Out of scope

- Variable tempo, swing, humanization, or intensity controls.
- Full cohesion workflow and occurrence-specific source selection.
- Refactoring all generated-instrument timing; Task 075 owns it.
- Final Lo-fi audio texture DSP changes.
