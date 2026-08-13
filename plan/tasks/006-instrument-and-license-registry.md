# Task 006 — Instrument and License Registry

## Goal

Validate and enrich the existing `sounds/` starter library so the small logical instrument allow-list resolves safely to local SFZ assets with explicit commercial-use and attribution data.

## Dependencies

- Task 004 provides the MIDI-first project format.
- Task 005 defines musical analysis but is not modified by this task.

## Existing asset baseline

Read `plan/SOUND_LIBRARY_BASELINE.md` before implementation. Do not create a second `instruments/` library.

The workspace already contains:

- `sounds/instruments.json` and `sounds/LICENSES.json`;
- SFZ definitions for piano, bass, drums, pad, and strings;
- 25 local WAV samples referenced by those SFZ files;
- a `starter-generated` commercial-use/no-attribution declaration;
- 44.1 kHz, mono, PCM-16 sample sources.

The sample WAV files are currently excluded by the repository-wide `*.wav` ignore rule. Treat local asset portability as an explicit deliverable rather than assuming a fresh clone has the samples.

## Supported scope

The only initial logical instruments are:

```text
piano
bass
drums
pad
strings
```

Do not add aliases or arbitrary model-selected names.

## Registry contracts

Load and preserve `sounds/instruments.json`. Enrich it to a validated shape conceptually equivalent to:

```json
{
  "version": 1,
  "workingSampleRate": 44100,
  "instruments": {
    "piano": {
      "engine": "sfz",
      "path": "piano/piano.sfz",
      "licenseId": "starter-generated",
      "midiProgram": 0
    },
    "drums": {
      "engine": "sfz",
      "path": "drums/drums.sfz",
      "licenseId": "starter-generated",
      "midiChannel": 10,
      "noteMap": {
        "kick": 36,
        "snare": 38,
        "clap": 39,
        "closedHat": 42,
        "openHat": 46
      }
    }
  }
}
```

Before accepting `midiChannel`, define whether registry values are zero-based (`0..15`) or one-based (`1..16`). Preserve human-readable channel 10 only if the loader converts it explicitly to zero-based channel 9 for MIDI events. Do not leave the convention implicit.

Validate and enrich `sounds/LICENSES.json`. Preserve `starter-generated` and require each library entry to contain:

- display name and source URL/reference;
- license identifier or an explicit `generated-original` provenance designation, plus optional license text path;
- `commercialUse`;
- `attributionRequired` and attribution text;
- redistribution status;
- optional notes and acquisition/generation date.

If any existing file is actually from a downloaded third-party library rather than generated locally, correct its provenance before using it for commercial output. Do not group third-party assets under `starter-generated`.

## Kotlin responsibilities

- Add small serializable registry/license models and a loader/validator.
- Use `sounds/` as the default instrument-library root and allow only an explicit local configuration override.
- Resolve registry-relative paths against that root.
- Parse the supported SFZ subset used by this pack and validate every `sample=` reference against path traversal, symlink escape, and file existence.
- Read every referenced WAV header and validate RIFF/WAVE PCM, positive sample rate/channels/frame count, finite decoded samples when decoded, and sample rate matching `workingSampleRate`.
- Reject absolute paths, `..` traversal, symlink escape, missing SFZ/sample files, missing license IDs, unknown engines, invalid programs/channels, and drum-map values that disagree with `drums.sfz`.
- Represent instrument names with an allow-listed type or equivalent validated value.
- Expose logical names—not filesystem paths—to deterministic and Qwen planners.
- Provide lookup returning a validated local descriptor only at the rendering boundary.
- Choose and document one simple asset-portability policy: a local copy/install step, or a narrowly scoped Git exception for starter samples whose redistribution rights are confirmed. Do not add application-managed network downloads.

## CLI

Add:

```bash
music-cli licenses ./projects/song-001
```

The command inspects the approved/current arrangement and reports:

- logical instrument;
- license;
- commercial-use status;
- required attribution;
- missing/unverified registry state.

Return a non-zero failure when a used instrument is missing, has no license record, or is explicitly non-commercial for a project marked for commercial export. Do not block ordinary personal experimentation unless the configured policy requires it; report clearly.

## Tests

- Valid registry and license cross-reference.
- All five logical names.
- Unknown instrument/engine.
- Duplicate or case-conflicting logical names.
- Absolute path, traversal, and escaping symlink.
- Missing asset and missing license.
- Invalid channel and drum note map.
- Missing/malformed SFZ sample reference and sample-path escape.
- WAV wrong rate, unsupported encoding/channel count, empty file, and missing sample.
- License report for attribution, commercial, non-commercial, and unused entries.
- Planner-facing metadata contains names only and never registry paths.
- Fresh-checkout/local-setup behavior matches the documented asset-portability policy.

Manual smoke test:

- Validate the existing piano, bass, drums, pad, and strings SFZ/sample trees.
- Display the `starter-generated` license output for all five instruments.
- Confirm a deliberately invalid path is rejected before renderer invocation.
- Verify the documented local setup produces the same complete 25-sample inventory on a clean workspace.

## Acceptance criteria

- Instrument resolution is local, deterministic, and path-safe.
- Qwen cannot receive or return accepted filesystem paths.
- Every used instrument has a validated license record.
- All five current SFZ definitions and their sample WAV files pass registry validation.
- The MIDI channel convention and current drum map are explicit and tested.
- A fresh workspace has a documented, non-network-automatic way to obtain the local sample files.
- Registry errors are actionable and occur before rendering.
- No unverified or incorrectly attributed third-party sample library is added to Git.

## Out of scope

- Downloading assets automatically or replacing the current starter sounds merely to satisfy this task.
- Plugin discovery, VST hosting, or a general instrument catalog.
- Rendering MIDI.

## Completion report

Report schema, `sounds/` root/configuration, all SFZ/sample validation results, asset-portability policy, license provenance, MIDI channel convention, changed files, tests/build commands, manual validation, assumptions, and missing mappings/assets (currently crash/cymbal).
