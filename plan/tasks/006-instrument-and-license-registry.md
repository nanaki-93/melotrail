# Task 006 — Instrument and License Registry

## Goal

Resolve a small allow-list of logical instrument names to local SFZ assets while tracking commercial-use and attribution requirements.

## Dependencies

- Task 004 provides the MIDI-first project format.
- Task 005 defines musical analysis but is not modified by this task.

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

Create `instruments/instruments.json` with versioned entries conceptually equivalent to:

```json
{
  "version": 1,
  "instruments": {
    "piano": {
      "engine": "sfz",
      "path": "piano/piano.sfz",
      "licenseId": "example-piano",
      "midiChannel": 0
    },
    "drums": {
      "engine": "sfz",
      "path": "drums/lofi-kit.sfz",
      "licenseId": "example-drums",
      "midiChannel": 9,
      "noteMap": {
        "kick": 36,
        "snare": 38,
        "closedHat": 42,
        "openHat": 46,
        "crash": 49
      }
    }
  }
}
```

Create `instruments/LICENSES.json` with versioned entries containing:

- display name and source URL/reference;
- license identifier and optional license text path;
- `commercialUse`;
- `attributionRequired` and attribution text;
- optional notes and acquisition date.

Do not commit third-party SFZ/sample assets unless their redistribution rights are verified and documented.

## Kotlin responsibilities

- Add small serializable registry/license models and a loader/validator.
- Resolve registry-relative paths against the configured instrument-library root.
- Reject absolute paths, `..` traversal, symlink escape, missing SFZ files, missing license IDs, unknown engines, invalid channels, and invalid drum notes.
- Represent instrument names with an allow-listed type or equivalent validated value.
- Expose logical names—not filesystem paths—to deterministic and Qwen planners.
- Provide lookup returning a validated local descriptor only at the rendering boundary.

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
- License report for attribution, commercial, non-commercial, and unused entries.
- Planner-facing metadata contains names only and never registry paths.

Manual smoke test:

- Configure one piano and one bass SFZ.
- Validate the registry and display license output.
- Confirm a deliberately invalid path is rejected before renderer invocation.

## Acceptance criteria

- Instrument resolution is local, deterministic, and path-safe.
- Qwen cannot receive or return accepted filesystem paths.
- Every used instrument has a validated license record.
- Registry errors are actionable and occur before rendering.
- No large or unlicensed sample library is added to Git.

## Out of scope

- Downloading assets automatically.
- Plugin discovery, VST hosting, or a general instrument catalog.
- Rendering MIDI.

## Completion report

Report schema, configured instrument root, asset/license provenance, changed files, tests/build commands, manual validation, assumptions, and missing local instruments.
