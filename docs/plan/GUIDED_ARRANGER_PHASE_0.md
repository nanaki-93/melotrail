# Guided arranger Phase 0: GarageBand handoff

**Status:** Active preparation contract for GA-001 and GA-002<br>
**Roadmap authority:** [`../../PLAN.md`](../../PLAN.md)

## 1. Is a finished WAV enough?

No. A finished GarageBand WAV is required as the overall musical reference, but
it is only one part of Phase 0. It cannot:

- prove that a green Apple Loop contains recoverable MIDI;
- provide known note events for an extractor comparison;
- verify exact section/bar placement;
- separate a drum, electric-piano, melody-rendering, or mixing failure;
- allow a later agent to rebuild or re-export the reference.

Phase 0 therefore collects two independent packages:

1. **Musical reference:** the real C-major song, its full mix, aligned stems,
   five native melody User Loops, and project metadata.
2. **Technical round trip:** agent-authored MIDI with already-known events,
   imported into GarageBand and returned as green, blue/audio, and plain AIFF
   examples.

The musical package answers “does this sound like the target?” The diagnostic
package answers “what did GarageBand actually store, and can Melotrail recover
it correctly?” Neither can replace the other.

## 2. Responsibilities

### The user prepares in GarageBand

- the golden arrangement and listening reference;
- the five green Software Instrument User Loops;
- frame-aligned melody, electric-piano, and drum stems;
- the returned Apple Loop/audio files made from agent-supplied MIDI seeds;
- the real human listening decisions.

### Agents prepare in the repository

- the ignored intake workspace, schemas, and validator;
- deterministic Standard MIDI seeds and canonical event JSON;
- immutable baseline/hash capture;
- safe container/chunk inspection and semantic comparison;
- randomized, loudness-matched listening packages;
- pending evidence forms, never fabricated human approval.

Agents must stop at every checkpoint in section 8. They cannot create a real
GarageBand export, confirm what the user heard, or write `reviewer = user`.

## 3. Local workspace

GA-001A adds `/local-fixtures/` to `.gitignore`. The user-owned Phase 0 package
uses this layout:

```text
local-fixtures/garageband/phase0/
├── project/
│   └── melotrail-golden.band.zip
├── song/
│   ├── reference-mix.wav
│   ├── reference-premaster.wav
│   └── stems/
│       ├── melody.wav
│       ├── electric-piano.wav
│       └── drums.wav
├── apple-loops/
│   └── native/
│       ├── intro-4b-75-green.<actual-extension>
│       ├── verse-4b-75-green.<actual-extension>
│       ├── chorus-8b-75-green.<actual-extension>
│       ├── bridge-4b-75-green.<actual-extension>
│       └── outro-4b-75-green.<actual-extension>
├── roundtrip/
│   └── returned/
│       ├── seed-4b-75-green.<actual-extension>
│       ├── seed-4b-75-blue.<actual-extension>
│       ├── seed-4b-75-share.aif
│       └── seed-8b-80-green.<actual-extension>
└── metadata/
    ├── song.json
    ├── capture.json
    └── SHA256SUMS
```

Use the extension GarageBand actually creates; do not rename `.caf` to `.aif`
or vice versa. The `.band` archive is strongly recommended for recovery and
re-export, but Melotrail will not parse GarageBand project internals.

Generated reports and listening bundles use a unique run directory:

```text
build/guided-arranger/phase0/<run-id>/
```

No command in this roadmap may clean `local-fixtures/`, `data/audio/input`, an
accepted output, or a user project.

## 4. Musical reference package

### 4.1 Project facts

- Key: C major.
- Meter: 4/4.
- Tempo: 75 BPM.
- Structure: Intro -> Verse -> Verse -> Chorus -> Bridge -> Verse -> Outro.
- Audible roles: melody, electric piano, and drums.
- Bass and strings: off.
- Musical length: 32 bars, or exactly 102.4 seconds before an effects tail.

| Occurrence | GarageBand half-open bar range | Chords |
| --- | --- | --- |
| Intro | `[1, 5)` | Cmaj7 \| Am7 \| Fmaj7 \| G |
| Verse 1 | `[5, 9)` | C \| G/B \| Am7 \| Fmaj7 |
| Verse 2 | `[9, 13)` | C \| G/B \| Am7 \| Fmaj7 |
| Chorus | `[13, 21)` | F \| G \| C \| Am7 \| F \| G \| C \| C |
| Bridge | `[21, 25)` | Am7 \| Em \| Fmaj7 \| G |
| Verse 3 | `[25, 29)` | C \| G/B \| Am7 \| Fmaj7 |
| Outro | `[29, 33)` | Fmaj7 \| G \| Cmaj7 \| C6 |

### 4.2 Required files

1. `reference-mix.wav`: the complete mix the user wants Melotrail to approach.
2. `melody.wav`, `electric-piano.wav`, and `drums.wav`: full-timeline stems
   exported from the same start and end.
3. Five actual green User Loop files created from the isolated melody sections
   with **File > Add Region to Loop Library**, not Share/Export.
4. `melotrail-golden.band.zip`: recommended local recovery copy.
5. `song.json` and `capture.json`: completed from the templates created by
   GA-001A.

`reference-premaster.wav` is recommended: retain creative track effects, but
disable master limiting/normalization. It makes mix problems easier to
distinguish from final limiting.

### 4.3 Export rules

- Prefer uncompressed 44.1 kHz, 24-bit stereo WAV for the reference and stems.
- Turn export normalization off and record the actual setting in `capture.json`.
- Export every stem over the identical full-song range, beginning at bar 1.
- Keep the original export even if GarageBand trims it. Record the expected
  musical start, musical end, rendered end, and any effects tail; let the intake
  tool create a separate aligned derivative when needed.
- Do not add a click or sync transient to the musical stems merely to make a
  validator pass.
- Preserve all source files byte-for-byte after capture.

At intake, the agent reports sample rate, bit depth, channel count, frame count,
duration, peak, and SHA-256 for every file. The user confirms that the reported
roles and GarageBand loop colors match what was created.

## 5. Five native melody User Loops

Create one green Software Instrument User Loop for each unique section:

| Filename stem | Bars | Expected grid duration at 75 BPM |
| --- | ---: | ---: |
| `intro-4b-75-green` | 4 | 12.8 s |
| `verse-4b-75-green` | 4 | 12.8 s |
| `chorus-8b-75-green` | 8 | 25.6 s |
| `bridge-4b-75-green` | 4 | 12.8 s |
| `outro-4b-75-green` | 4 | 12.8 s |

For each section:

1. solo the Software Instrument/MIDI melody region;
2. verify that its region boundary is the exact whole-bar span;
3. do not bounce it before creating the green User Loop;
4. use **File > Add Region to Loop Library** or drag the region to the Loop
   Browser;
5. copy the actual resulting User Loop file into `apple-loops/native/`;
6. do not change its suffix or re-encode it.

These native loops prove the realistic product flow. They do not provide
independent ground truth for every event, which is why section 6 is separate.

## 6. Known-MIDI GarageBand round trip

GarageBand cannot normally export a Standard MIDI file, so Melotrail creates
the ground truth *before* GarageBand receives it.

GA-002A creates these checked-in, deterministic inputs:

```text
src/test/resources/fixtures/garageband/roundtrip-4b-75/
├── source.mid
└── expected-events.json

src/test/resources/fixtures/garageband/roundtrip-8b-80/
├── source.mid
└── expected-events.json
```

The 4-bar seed covers repeated notes, rests, several velocities, intentional
off-grid timing, and a note ending exactly at the section boundary. The 8-bar
seed covers register changes, one controlled overlap, CC64, pitch bend,
tempo/key/meter metadata, and a final exact-boundary event.

### User round-trip steps

1. Import `source.mid` into a new GarageBand Software Instrument track.
2. Set the project to the seed's declared tempo, key, and 4/4 meter.
3. Do not edit, quantize, transpose, humanize, or change note velocities.
4. Select the exact 4- or 8-bar region and create a green User Loop.
5. Copy the actual result to `roundtrip/returned/` with its real extension.
6. For the 4-bar seed, also create an audio region from the same performance,
   add that audio region as a blue User Loop, and make one plain Share-export
   AIFF.
7. Record GarageBand/macOS versions, instrument/preset, effects, operations,
   filenames, and colors in `capture.json`.

Melotrail then compares:

- the extracted SMF against the exact payload stored in the container; and
- the extracted semantic events against `expected-events.json`.

Comparison uses rational beat positions so a harmless PPQ conversion is visible
without being misreported as a musical timing change. Missing controllers or
metadata are recorded as GarageBand behavior. Melotrail can claim exact recovery
only for events that are actually present in the returned loop.

## 7. What may enter Git

The user's `.band` project, original song, stems, and five native melody loops
remain ignored and local. Commit only:

- schemas, validators, tools, and generated ground-truth MIDI/JSON;
- relative IDs, hashes, format reports, and human-supplied review records;
- a small diagnostic GarageBand binary fixture only when the user explicitly
  confirms that it is redistributable and the task adds a narrow allow-list.

If redistribution is unclear, keep real GarageBand binaries local and use them
only in opt-in live tests. Synthetic fixtures remain the ordinary offline-CI
inputs. Never use `git add -f` merely because a task wants a binary fixture.

## 8. Human checkpoints

### H0-01 — Golden assets ready

The agent first creates the templates, seed inputs, and validator, then stops.
The user supplies the musical package in sections 3 through 5. No agent may
invent missing files or approve a substitute final WAV as the full package.

### H0-02 — Musical target approved

The agent packages randomized, loudness-matched comparisons. The user scores
the GarageBand reference and explicitly states whether it is the target. File
existence, metrics, or an agent's opinion cannot satisfy this checkpoint.

### H0-03 — GarageBand round trip returned

The user follows section 6 and supplies the returned green, blue, and plain-AIFF
files. The agent hashes and inspects them without modifying them.

### H0-04 — Capture confirmed

The agent presents actual containers, durations, hashes, metadata conflicts,
and alignment findings. The user confirms that each reported origin matches the
GarageBand action that created it.

### H0-05 — Import route accepted

The agent presents the GA-002 architecture decision: exact embedded MIDI where
proved, and transcription/review otherwise. The user accepts or rejects that
route before Phase 1 begins.

## 9. Phase 0 completion checklist

Phase 0 is complete only when:

- current Melotrail output and its artifact manifest are hash-bound;
- the local GarageBand project can be recovered and re-exported;
- the reference mix and three stems are readable and documented;
- alignment stems have a known common start, sample rate, and frame span;
- the 102.4-second musical end and any additional effects tail are distinct;
- all five native green melody loops are present and immutable;
- the diagnostic 4-bar and 8-bar round trips have known source events;
- real files are classified by bytes/chunks, not suffix or their claimed color;
- corrupt/truncated controls publish no partial output;
- the user has approved the musical target and accepted the import route;
- no agent-authored record claims to be a human decision.

The detailed commit-sized implementation order is in
[`GUIDED_ARRANGER_TASKS.md`](GUIDED_ARRANGER_TASKS.md).
