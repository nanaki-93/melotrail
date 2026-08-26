# Logic Pro and GarageBand compatibility

Status: target support contract; manual results must be recorded during the
compatibility spike

Authority: DAW support claims and acceptance procedure

## 1. Supported relationship

Melotrail exports Standard MIDI files rather than DAW project files.

- Logic Pro is a supported MIDI source and destination. It can import/open and
  export Standard MIDI format 0 and 1 files.
- GarageBand for Mac is a supported MIDI destination. It can import a MIDI file
  into one or more software-instrument tracks.
- GarageBand-to-Melotrail MIDI round-tripping is not supported because
  GarageBand's documented song export is audio-oriented.
- Melotrail does not create `.logicx` or `.band` projects and does not automate
  either application.

Official behavior references:

- [Standard MIDI files in Logic Pro](https://support.apple.com/guide/logicpro/standard-midi-files-lgcpdf6a3851/mac)
- [Import audio and MIDI files in GarageBand on Mac](https://support.apple.com/guide/garageband/import-audio-and-midi-files-gbndd01649ed/mac)
- [Export songs from GarageBand on Mac](https://support.apple.com/guide/garageband/export-songs-to-disk-or-icloud-gbnd7cbf5ed9/mac)

These links describe application capabilities. A Melotrail release still needs
its own recorded manual verification.

## 2. Compatibility promise

For a package that passes Melotrail validation:

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

Section markers are best-effort UI metadata because DAWs may display Standard
MIDI markers differently. Marker loss is a compatibility finding; tick/bar
misalignment is a blocker.

## 3. Logic Pro workflow

### Input to Melotrail

1. In Logic Pro, prepare the MIDI regions that should become one source file.
2. Export the selection as a Standard MIDI file.
3. Import that file into Melotrail.
4. Select the intended melody track and confirm project authority.

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

## 4. GarageBand workflow

1. Create a GarageBand project.
2. Drag `complete-song.mid` from Finder to the empty area below existing tracks,
   or import role files at the same project origin.
3. Confirm that GarageBand creates one or more software-instrument tracks.
4. Assign instruments from GarageBand's Library.
5. Confirm tempo, meter, track alignment, drum interpretation, and song length.

The product does not tell the user to export the GarageBand song back as MIDI.
GarageBand's documented disk export produces AAC, MP3, AIFF, or WAVE and is part
of the user's downstream production workflow, not Melotrail input.

## 5. Manual compatibility matrix

Run the matrix for each release that changes MIDI import, timing, event policy,
track assembly, or export:

### Fixtures

- SMF 0 melody with velocities and rests.
- SMF 1 melody plus reference tracks.
- pickup note before the first full bar.
- sub-bar chord changes.
- sustained melody with CC64/pitch data covered by the selected policy.
- complete arrangement with fills and notes ending at the song boundary.

### Checks in each DAW

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

## 6. Evidence record

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

## 7. Unsupported claims

Do not claim:

- perfect preservation of DAW regions, articulation sets, plugins, patches,
  automation, or mixer state;
- compatibility with GarageBand iOS unless separately tested;
- automatic sound matching;
- direct installation of Logic/GarageBand patches;
- identical playback between Melotrail preview and either DAW; or
- compatibility with an untested DAW version merely because it supports MIDI.
