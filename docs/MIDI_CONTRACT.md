# Standard MIDI contract

Status: target V1 contract

Authority: accepted input, semantic preservation, validation, and generated
files

## 1. Scope

MIDI is Melotrail's only musical file representation. The source is preserved,
arrangement candidates are MIDI, audition reads MIDI, and export produces MIDI.
Audio is not a fallback or secondary canonical format.

## 2. Accepted input

V1 accepts:

- filename extension `.mid` or `.midi`;
- a valid Standard MIDI File header;
- SMF format 0 or 1;
- positive PPQ timing division;
- at least one safely pairable note stream;
- one fixed effective tempo;
- one fixed effective time signature; and
- one user-selected melody track with one note channel after import resolution.

V1 does not accept:

- SMF format 2;
- SMPTE timing division;
- changing tempo maps;
- changing time-signature maps;
- MPE or other multi-channel-per-note expression as protected melody;
- files whose note pairing or tick ranges cannot be interpreted safely; or
- several source files in one project.

Missing tempo or meter metadata is not necessarily a malformed file. The user
may provide the missing authoritative value before the project becomes ready
for generation.

## 3. Source preservation

Import stores:

- original bytes under the project source directory;
- original filename;
- SHA-256 digest;
- SMF format and PPQ;
- ordered track summaries;
- parsed event counts and supported-event facts;
- unsupported-event findings; and
- selected melody track/channel identity after confirmation.

No import, normalization, generation, or export operation overwrites the source
file. Melotrail may build canonical semantic views but does not claim binary
round-trip identity.

## 4. Canonical semantic model

The MIDI adapter converts supported messages into immutable ordered events.
The model distinguishes:

- note events with start tick, end tick, pitch, onset velocity, release
  velocity when present, and channel;
- control changes;
- pitch bend;
- channel pressure;
- tempo;
- time signature;
- track name;
- marker and cue text;
- lyric/text metadata when retained for reference; and
- unsupported or intentionally omitted messages as findings.

Stable event ordering uses tick, semantic event priority, source track index,
source event index, and a deterministic generated-event key. The exact writer
ordering is covered by golden tests.

Project tick resolution is the accepted source PPQ. Generators use rational
beat/subdivision calculations and must either produce exactly representable
ticks or apply one documented deterministic rounding policy. They cannot
silently change the project's PPQ.

## 5. Note pairing

- Note-on velocity zero is treated as note-off.
- A note is paired within the same track, channel, and pitch.
- End tick must be greater than start tick after the documented minimum-duration
  policy.
- An orphan note-off is a finding unless it makes interpretation ambiguous.
- An unclosed note-on is blocking for the selected melody and advisory for an
  ignored reference track.
- Overlapping notes of different pitches are ordinary polyphony.
- Overlapping note-ons for the same track/channel/pitch are blocking until a
  safe pairing policy is explicitly selected and tested.

The importer never deletes an event merely to make a file pass.

## 6. Event preservation policy

### 6.1 Protected melody

The canonical melody preserves note timing, pitch, and velocity. Supported
source controllers, pitch bend, and channel pressure remain associated with the
melody view when their channel/range is unambiguous.

Program changes are captured as import hints but are not emitted by default;
the destination DAW owns instrument selection. System Exclusive messages are
not copied to generated output in V1. Their presence is reported.

### 6.2 Generated roles

Generated chord, bass, and drum candidates contain note events only, except a
future explicitly tested sustain policy may allow CC64 for chord/keys. They do
not emit program changes, pitch bend, aftertouch, SysEx, or arbitrary
controllers in the MVP.

### 6.3 Reference tracks

Non-selected source tracks are preserved in source identity and may be
auditioned as immutable references. They are not included in the default
arranged export. A later feature must explicitly define any passthrough policy.

## 7. Validation classification

### Blocking

- unreadable/truncated file or invalid chunk structure;
- unsupported SMF format or division;
- unsafe or ambiguous selected-melody note pairing;
- negative/overflowed semantic timing;
- no melody notes after selection;
- unsupported tempo/meter changes;
- authority gaps or overlaps in the intended song timeline;
- chord syntax that cannot be realized in an affected window;
- generated role event outside its occurrence or allowed range;
- digest mismatch for a referenced immutable artifact; or
- semantic mismatch after generated-file re-import.

### Advisory

- polyphony;
- chromatic melody notes or chords;
- unusual pitch range or density;
- repeated note events that are still unambiguous;
- controller or text events omitted by export policy;
- missing original tempo/meter that the user can confirm;
- source program or bank selection; and
- potential melody/accompaniment collision that remains within hard limits.

Advisories are visible evidence. They do not grant a generator permission to
rewrite project authority.

## 8. Authority timing

The project has one tempo and meter at tick zero. Section occurrences form a
contiguous ordered timeline. Each chord event has a start and duration expressed
in musical position and resolved to exact project ticks.

Authoritative harmony may be chromatic. Generators use the resolved chord event
for their current tick window and never substitute a scale-derived chord.

## 9. Output files

### 9.1 Complete song

`complete-song.mid` is SMF format 1 with deterministic tracks:

1. `Conductor`
2. `Melody`
3. `Chords`
4. `Bass`
5. `Drums`
6. optional enabled roles in documented order

The conductor track includes sequence name, one tempo, one time signature, and
validated section markers. Each role track begins at song tick zero even if its
first note occurs later.

### 9.2 Role files

Each per-role file is SMF format 1 containing a conductor track and exactly one
named musical role track. It uses the same PPQ, song origin, tempo, meter,
markers, role channel, and end-of-track boundary as the complete song.

An enabled role with no accepted events is an export blocker. A disabled
optional role is omitted and recorded as disabled in the manifest; no empty
placeholder is written.

## 10. Channel policy

Channel numbers below use the musician-facing 1–16 convention:

- Melody: channel 1
- Chords: channel 2
- Bass: channel 3
- Drums: channel 10
- Optional roles: assigned from the remaining documented channels

Selected melody channel messages are remapped consistently to channel 1 in the
arranged export. The original source remains unchanged. A selected melody that
requires multiple note channels is unsupported in V1.

## 11. Marker and naming policy

- Track names are short, stable ASCII/Unicode text values shown above.
- Section markers use `<ordinal>:<occurrence-label>` with a stable sanitized
  label.
- Duplicate visible section labels remain distinguishable by ordinal and
  manifest occurrence ID.
- Filenames use a sanitized project export name and fixed role filenames.
- Marker behavior is tested in each destination DAW; unsupported marker display
  is not treated as lost musical timing.

## 12. Instrument suggestions

MIDI files contain no program or bank changes by default. The manifest may
include, per role:

- performance-profile ID;
- human-readable category;
- optional General MIDI program suggestion;
- optional free-text Logic Pro search suggestion; and
- register/articulation notes.

Suggestions are not exact patch identifiers and do not claim a particular DAW
library is installed.

## 13. Manifest minimum fields

The JSON manifest contains:

- manifest schema version;
- project and export snapshot IDs;
- export timestamp;
- source filename and digest;
- selected melody track identity;
- project PPQ, tempo, meter, key, and mode;
- ordered section occurrences and chord events;
- role enablement and accepted candidate IDs/digests;
- generator versions and seeds;
- performance profiles and instrument suggestions;
- generated filenames and SHA-256 digests;
- validation summary; and
- application version/build identity when available.

Paths are relative and portable. Absolute local paths, tokens, device names,
and private model configuration are excluded.

## 14. Semantic re-import

Before publishing an export, Melotrail re-imports every generated MIDI file and
checks:

- format and PPQ;
- tempo and meter;
- track count/order/names;
- marker tick positions;
- note pitch/start/end/velocity/channel;
- allowed controllers;
- song end boundary; and
- agreement with the export snapshot.

Binary byte equality is not required. Semantic equality under the writer's
ordering and omission policy is required.
