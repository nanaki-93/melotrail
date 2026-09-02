# Logic Pro compatibility

Status: supported-destination contract; manual results must be recorded for
each release that changes export semantics

Authority: DAW support claims and acceptance procedure

## 1. Supported relationship

Melotrail exports Standard MIDI files rather than DAW project files.

- Logic Pro is the supported MIDI source and destination. It can import/open
  and export Standard MIDI format 0 and 1 files.
- GarageBand is unverified and is not a supported destination. Melotrail makes
  no GarageBand import, playback, marker, instrument, or round-trip claim.
- Melotrail does not create `.logicx` projects or automate Logic Pro.

Official behavior references:

- [Standard MIDI files in Logic Pro](https://support.apple.com/guide/logicpro/standard-midi-files-lgcpdf6a3851/mac)

These links describe application capabilities. A Melotrail release still needs
its own recorded manual verification.

## 2. Compatibility promise

For a package that passes Melotrail validation and the recorded Logic Pro
matrix:

- the complete file imports without a MIDI parsing error;
- melody, chords, bass, and drums appear as distinct tracks;
- track names remain identifiable;
- notes begin at their intended musical positions;
- tempo and meter match the manifest;
- drums are interpreted from channel 10 or can be assigned to a drum instrument
  without event repair;
- role files align when imported at song start;
- no stuck notes are heard during a full playback pass; and
- the user can choose destination instruments without deleting embedded patch
  automation.

Section markers are best-effort UI metadata because Logic Pro may display
Standard MIDI markers differently. Marker loss or unassessed display is a
compatibility finding; tick/bar misalignment is a blocker.

## 3. Logic Pro workflow

### Input to Melotrail

1. In Logic Pro, prepare the complete song as exactly one note-bearing melody
   track on one MIDI channel. A separate conductor track may contain metadata.
2. Export it as one Standard MIDI file.
3. Import that file into Melotrail.
4. Confirm project authority and enter the ordered section lengths in bars;
   Melotrail protects the only melody track automatically.

The user is responsible for applying Logic region parameters when they expect
those non-event parameters to be baked into the exported MIDI.

### Output from Melotrail

1. Open a new or existing Logic Pro project with the intended sample rate and
   production setup.
2. Import or open `complete-song.mid` at bar 1.
3. Confirm tempo/meter behavior before choosing whether Logic adopts or retains
   project settings.
4. Assign software instruments to Melody, Chords, Bass, and Drums.
5. Compare against the Melotrail manifest and, when useful, the separate role
   files.

Logic patch assignment is a user decision. Melotrail's manifest suggestions are
search hints, not a patch-loading API.

## 4. Manual compatibility matrix

Run the matrix for each release that changes MIDI import, timing, event policy,
track assembly, or export:

### Fixtures

- SMF 0 one-bar melody with a note ending at the song boundary.
- SMF 1 melody plus a meta-only conductor track.
- one-, two-, and three-bar complete-song melody inputs.
- sub-bar authoritative chord changes over a whole-bar structure.
- sustained melody with CC64/pitch data covered by the protected policy.
- complete arrangement with fills and notes ending at the song boundary.

### Checks in Logic Pro

- application name and exact installed version recorded;
- macOS version recorded;
- complete file imports;
- each role file imports;
- track count and names match;
- tempo and time signature match;
- first note and final note/end boundary match;
- section boundaries align to expected bars/beats;
- melody note count/timing/velocity spot-check passes;
- generated roles contain no unexpected controllers/program changes;
- drums are assignable and play expected kit pieces;
- mute/solo playback reveals no stuck or truncated notes; and
- re-saving/closing the DAW project does not expose an import error.

### Result classification

- **Pass:** musical timing and event content are correct; cosmetic metadata may
  differ only where explicitly documented.
- **Conditional pass:** a stable, short user action is required, such as choosing
  whether to import tempo. The action is documented in the export UI.
- **Fail:** notes, channels, timing, duration, role separation, or import safety
  is incorrect.

A conditional pass cannot be silently promoted to pass.

## 5. Evidence record

Each compatibility run records:

```text
Melotrail build:
Fixture/export snapshot:
macOS version:
DAW and version:
Import method:
Complete file result:
Role file result:
Tempo/meter result:
Track/channel result:
Marker result:
Playback result:
Required user action:
Evidence location:
Reviewer/date:
```

Screenshots or DAW project files are test evidence only when licensing and size
permit them to be checked in. A written result plus exported fixture hashes is
mandatory.

## 6. Unsupported claims

Do not claim:

- perfect preservation of DAW regions, articulation sets, plugins, patches,
  automation, or mixer state;
- compatibility with GarageBand, including GarageBand for Mac or iOS;
- automatic sound matching;
- direct installation of Logic Pro patches;
- identical playback between Melotrail preview and Logic Pro; or
- compatibility with an untested DAW version merely because it supports MIDI.
