# MC-048 final destination-DAW matrix

Status: Logic Pro evidence complete

This is the reproducible export set for the final Logic Pro compatibility gate.
It was generated from the current MC-047 commit plus the
MC-048 matrix-preparation test on 2026-08-28. The packages are intentionally
ignored build output: do not edit them or overwrite them. Recreate them with
the focused exporter test if a package is lost.

## Package evidence

Root: `/Users/marcoandreose/DEV/lab/melotrail/build/mc048-daw-matrix`

Every package contains `complete-song.mid`, `melody.mid`, `chords.mid`,
`bass.mid`, `drums.mid`, and `manifest.json`. Each manifest identifies its
source fixture, stores SHA-256 for every generated MIDI file, and records a
passed semantic re-import of all five MIDI files. The hash below is the
SHA-256 of that manifest itself.

| Package directory | Source fixture (SMF) | Expected complete/role end tick | Manifest SHA-256 |
| --- | --- | ---: | --- |
| `smf0-melody` | `smf0-melody.mid` (0) | 480 | `140306ea5ab542406e9445cbc3db57cfb5142ad22ba1d4d3bb4993d0fb1a44ca` |
| `smf1-reference-tracks` | `smf1-reference-tracks.mid` (1) | 480 | `a9bc0bc7648062e2557f5c6206f8d8cbef592a9c31e3b88f5458474813eb278d` |
| `pickup-timing` | `pickup-timing.mid` (1) | 960 | `1bf0ab41456600a1f205ca1ba362aeb162647d3c673cc3b07e5769e8fb359740` |
| `sub-bar-harmony` | `sub-bar-harmony.mid` (1) | 1920 | `6d31ac90f677f85ad56c66b28c61c9697d53ebdaf19ebc11918267195caa001c` |
| `expressive-controller-pitch` | `expressive-controller-pitch.mid` (1) | 480 | `bec84753b254a6ab8480b6ad50116ce8515fcddba642087c6b096c97289f0d8f` |
| `complete-arrangement-boundary` | `final-boundary-note.mid` (0) | 1920 | `fc35d0c091ca5ecf060592e5e167cd7a02a6d978d41876ea81e6ece6a1c7e725` |

The expected authority in every package is 120 BPM, 4/4, PPQ 480, C major,
and one `1:Verse` marker at tick 0. Complete files have the track order
`Conductor`, `Melody`, `Chords`, `Bass`, `Drums`; role files have `Conductor`
plus their named role. The role channel contract is Melody 1, Chords 2, Bass
3, and Drums 10. Instrument choices are destination-DAW decisions; the
manifest contains only advisory search suggestions and no patch automation.

## Required matrix

For each package, run the following in the current installed Logic Pro. Record
the exact application and macOS versions.

1. From a clean project, import `complete-song.mid` at bar 1. Explicitly record
   whether Logic adopts or retains the imported 120 BPM/4/4 settings.
2. From clean projects at the same origin, import each of `melody.mid`,
   `chords.mid`, `bass.mid`, and `drums.mid`.
3. For complete and role imports, verify the named tracks, role/channel
   separation, 120 BPM/4/4, first note, final note/end boundary, and bar/beat
   alignment. Spot-check melody note timing and velocity. For
   `sub-bar-harmony`, check the C-to-G7 change at tick 960 (beat 3 of the
   four-beat bar).
4. Confirm no unexpected controller or program changes; the expressive fixture
   preserves selected-melody CC64 and pitch data. Assign a drum instrument if
   needed and confirm the channel-10 kit pieces play correctly.
5. Check `1:Verse` marker display, recording a cosmetic marker-loss finding if
   the DAW does not show it. Marker presentation is best-effort; timing or
   boundary loss is a failure.
6. Use mute/solo and a full playback pass to check for stuck or truncated
   notes. Save, close, and reopen the DAW project to ensure no import error is
   exposed.

Classify the Logic Pro result as `PASS`, `CONDITIONAL PASS`, or `FAIL`.
A conditional pass must name the stable user action (for example, choosing
Logic's tempo-import behavior); a timing, note, channel, role-separation,
stuck-note, or file-open failure is a failure.

## Received evidence — Logic Pro run

On 2026-08-28 the reviewer reported no import error when importing each role
separately or the complete song together. The following screenshots are Logic
Pro captures (the exact application version is not visible) and correspond to
the complete-file fixture imports. They visibly show 120 BPM, 4/4, distinct
named Bass/Chords/Drums/Melody tracks, and no import error dialog.

| Fixture | Screenshot | SHA-256 |
| --- | --- | --- |
| `smf0-melody` | `docs/checks/smf0-melody.png` | `8d3b92b762098825a5a4acd5c9bc21e13b4e202322ab8cabb5e66bf4cc7011bd` |
| `smf1-reference-tracks` | `docs/checks/smf1-reference-tracks.png` | `00bde2e596db606e37c0317155552e147c883c7ebbb33d9032d9484062d8b266` |
| `pickup-timing` | `docs/checks/pickup-timing.png` | `81b2232f0eccaca9a9475f7658ebb26f37237811a687affe55cf59f789805ef2` |
| `sub-bar-harmony` | `docs/checks/sub-bar-harmony.png` | `8913576ef89b14a46396f49df3275a64afebadfb42db0eb21d34ab27f3a3db41` |
| `expressive-controller-pitch` | `docs/checks/expressive-controller-pitch.png` | `b8d9b703c9670f63f180a8616b7e9173c4961efbace1905310f8e64624faa384` |
| `complete-arrangement-boundary` | `docs/checks/complete-arrangement-boundary.png` | `0bb012b3957ddfd6031169aac773f08b57bc140297b352b443d68355f23168b8` |

### Logic Pro follow-up, 2026-08-28

The reviewer confirmed Logic Pro **12.3.1**, successful full playback, and
successful save, close, and reopen with no error. Together with the earlier
report that all six complete files and individual role files imported without
error, this establishes a **Logic Pro pass** for the final matrix. The imported
`1:Verse` marker display remains unassessed: creating a new Logic marker
produced the default label `Marker 1`, which does not test the imported marker.
This is documented best-effort metadata, not evidence of a timing, note,
role-separation, channel, playback, save/reopen, or file-open failure.

On 2026-08-28 the user authorized a Logic Pro-only product scope. GarageBand is
therefore unverified and outside the supported-destination claim; no GarageBand
matrix is required for this release.

## Result template

Use this compact form for future Logic Pro export changes:

```text
Logic Pro version:
macOS version:
Import method and any required action:
smf0-melody: complete / roles / tempo-meter / tracks-channels / boundaries / marker / playback =
smf1-reference-tracks: complete / roles / tempo-meter / tracks-channels / boundaries / marker / playback =
pickup-timing: complete / roles / tempo-meter / tracks-channels / boundaries / marker / playback =
sub-bar-harmony: complete / roles / tempo-meter / tracks-channels / boundaries / marker / playback =
expressive-controller-pitch: complete / roles / tempo-meter / tracks-channels / boundaries / marker / playback =
complete-arrangement-boundary: complete / roles / tempo-meter / tracks-channels / boundaries / marker / playback =
Evidence path or screenshots (if available):
Reviewer/date:
```

Do not treat the earlier MC-009 DAW spike as a substitute: this matrix covers
the final exporter, all six source fixtures, role imports, and current DAW
versions.
