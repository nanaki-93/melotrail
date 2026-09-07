# MIDI contract

Owner: supported input, preserved semantics and output. Current behavior is
separated from planned changes below. [Architecture](ARCHITECTURE.md) owns
persistence; [Validation](VALIDATION.md) owns proof.

## Current input

One `.mid` or `.midi` Standard MIDI file per project: SMF 0 or 1, positive PPQ,
exactly one note-bearing track using one note-bearing channel, with optional
additional meta-only tracks. One fixed effective tempo and one fixed meter.
Missing tempo/meter may be confirmed by the user before generation.

Reject format 2, SMPTE division, changing tempo/meter maps, multiple sources,
extra note-bearing tracks/channels, MPE, unsafe pairing, invalid/overflowed
ranges or a source with no complete notes. Explain the actual cause. Polyphony,
chromatic notes/chords, unusual density and controller use are not corruption.

Import preserves original bytes, filename, SHA-256, format/PPQ, ordered track
facts and findings. It records the last protected-note release separately from
the source end-of-track extent; neither fact rewrites or trims source events.
It atomically protects the only melody track/channel.
There is no in-project source switch or silent source repair.

## Semantic events and pairing

Preserve supported melody note start/end, pitch, onset velocity, release
velocity where represented, controllers, pitch bend and channel pressure with
unambiguous channel/range ownership. Retain tempo, meter, track names, markers,
cues and supported text as semantic/reference evidence. Unsupported messages
produce findings. Program/bank changes are hints, omitted from generated exports
by default; SysEx is not copied to arranged output.

Note-on velocity zero is note-off. Pair by source track/channel/pitch. Unclosed
note-on and overlapping same-pitch note-ons without a safe pairing policy are
blocking. Orphan note-off is a finding unless interpretation becomes unsafe.
Different-pitch overlap is valid polyphony. Notes have positive duration; do not
delete events to pass validation. Musical analysis does not mutate this stream.

Canonical ordering is stable by tick, semantic priority and original event
identity, with stable keys for generated events. Writer goldens define actual
same-tick ordering. Preserve source PPQ. Rational timing must be exactly
representable or use the one documented deterministic adapter rounding policy;
generators reject unsupported grids rather than silently changing PPQ.

Generated Chords/Bass/Drums candidates contain notes only. No arbitrary
controllers, program changes, bend, aftertouch or SysEx. Future generated sustain
requires a separately tested policy. Audition device/timbre choices are never
export authority.

## Authority and currentness

Tempo is a fixed microseconds-per-quarter value. BPM entry converts using
`round(60,000,000 / BPM)` within the valid MIDI range. Meter, key/mode, ordered
occurrences and explicit chord windows have one authoritative interpretation.
Chromatic harmony is valid; generators cannot substitute a scale-derived chord.

Section entry uses positive whole bars whose total exactly matches the confirmed
arrangement end. Source end and arrangement end are distinct: a musician may
explicitly pad trailing silence to the next bar, or cancel that padding before
sections are defined. The arrangement end cannot precede any preserved source
event; padding does not add, shift, repeat, or otherwise rewrite melody data.
An occurrence audition uses its confirmed arrangement boundary, so a final
padded section plays its intended trailing silence without altering source bytes.

Harmony is persisted as canonical gap-free tick windows, edited as one chord
row per symbol plus a positive rational duration in quarter-note beats. A
duration must resolve exactly at the imported PPQ; the editor rejects hidden
rounding, gaps, overlaps, and totals outside the section. Reopening unchanged
rows retains their original event identities and tick windows exactly. Legacy
progression shorthand may seed editable rows but is never authority.

Every candidate records role/occurrence, source/authority identity, generator,
pattern/profile versions, seed, artifact digest and validation evidence. Used
upstream dependencies are explicit. Generation creates a new artifact;
acceptance changes pointers. Locked/stale/missing/rejected work cannot be silently
admitted by a full-draft operation. A complete draft can be auditioned before use;
only currently accepted scopes may be exported.

## Validation categories

Blocking: unreadable/unsupported SMF, unsafe pairing, invalid timing, authority
gaps/overlaps, unrealizable chords, generated notes outside role/range/boundary,
violations of hard role policy, stale/digest-mismatched evidence or failed
semantic re-import. A generation failure stays a failure.

Advisory: unusual density/range, chromaticism, intentionally omitted reference
messages, missing source metadata to confirm, and musical tensions inside hard
limits. Report location and musical context. Existing exact protected-anchor
collision blocking remains until a specific tested musical-policy change;
M02/M04 improve the treatment of proximity/tension without relaxing integrity.

## Read-only melody and harmony evidence

`melody-harmony-context-v1` derives deterministic, versioned context from the
selected protected melody view and already-confirmed chord windows. It does not
write a project, mutate source events, edit harmony, or classify a candidate as
accepted/rejected. The model exposes active (key-held) and sounding notes,
CC64-supported sustain extension, rests, register, metrical prominence,
bar/beat/pickup location, phrase/rest hints, exact repeated-motif hints and
protected anchors for each beat, bar, phrase and chord window.

Tension evidence identifies its chord window, protected note, tick span,
bar/beat, overlap duration, metrical weight, compound interval above a fixed
root reference and nearest chord-tone distance. A sustained accented non-chord
tone, short passing/neighbor candidate and held suspension remain advisory
musical evidence, not automatic chord correction or a quality verdict.
Chromatic chords remain authoritative. A non-zero pitch bend has no declared
semitone range in the current contract; analysis reports that limitation and
does not assert exact acoustic consonance for the affected overlap. CC64 is the
only sustain interpretation: an unclosed pedal is represented only to the known
analysis boundary.

## Export package

Each new immutable snapshot contains `complete-song.mid`, aligned role files
`melody.mid`, `chords.mid`, `bass.mid`, `drums.mid`, and `manifest.json` for the
current four-role workflow. Files use the same PPQ, fixed tempo/meter, song
origin and end boundary. Individual role files must not shift their first note
to tick zero. Initial silence is meaningful.

| Track in complete SMF 1 | Musician-facing MIDI channel |
| --- | --- |
| Conductor | Meta only |
| Melody | 1 |
| Chords | 2 |
| Bass | 3 |
| Drums | 10 |

Each role file is SMF 1 with conductor plus its named musical track. Consistently
remap melody channel messages to channel 1 while preserving original source
bytes. No default program/bank changes; instrument suggestions live in the
manifest. Marker text uses `<ordinal>:<occurrence-label>` and exact boundary
ticks. Duplicate names remain distinguishable. Marker display in Logic is
best effort; note/bar alignment is mandatory.

Manifest fields: schema/build/snapshot/project IDs, source and candidate hashes,
PPQ/tempo/meter/key, occurrences and chord windows, role presence, generator
versions/seeds/profiles, instrument suggestions, validation and file digests.
Use relative portable paths. Exclude credentials, private device/config values
and absolute local source paths.

Stage all files, re-import them, compare with the frozen accepted snapshot, then
publish atomically without overwriting an existing package. Check format/PPQ,
track order/names, channels, tempo/meter, marker ticks, every note field, allowed
expression and exact end boundary. Byte-identical round-trip is unnecessary;
semantic identity under the documented omission/remapping policy is required.

## Planned contract extensions

These are requirements for M06/M07, not claims of shipped support:
- **Arrangement plan:** record versioned purpose, phrase/repeat relationships,
  role activity and musical settings. Include consumed boundary/groove/neighbor
  information in scoped fingerprints. Invalidation follows those dependencies.
- **Planned rests:** each scope is a validated candidate or an explicit plan
  rest. Assembly and atomic use/undo preserve that distinction. Export silence
  in inactive sections while retaining global origin/end. Omit a generated role
  inactive for the entire song and list it as inactive in the manifest; Melody
  always remains. An active scope missing valid output blocks export. Never
  write a placeholder to hide failure. Test changed track counts and role-file
  omission in Q02 before claiming compatibility for this extension.

A changed generator/catalog invalidates applicability of its earlier musical
rating; a changed export policy requires fresh relevant Logic evidence. Stored
old artifacts remain inspectable and unchanged even when no longer current.
