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

## Chords voicing continuity

M04 ranks a bounded legal Chords pool over the complete requested occurrence:
at most 48 legal voicings per chord window and 12 retained lookahead paths per
window. It preserves required chord tones and slash bass semantics before
ranking. A caller may explicitly provide the immediately preceding occurrence's
immutable piano-boundary summary (authority hash, boundary tick, and ordered
voices); it is included in the scoped context and generation fingerprint. The
generator never discovers that input by consulting mutable accepted work.

When complete-draft generation consumes that input, its summary digest is
persisted on the resulting candidate and round-trips through the current project
schema. The first Chords occurrence records no preceding digest; every later
Chords occurrence must match the summary re-derived from the preceding draft
candidate's verified immutable MIDI under current authority. Retry/reuse,
completed-draft validation, audition, assembly, and Use reject missing or
mismatched boundary evidence. A rejected retained candidate and its MIDI bytes
remain inspectable while retry publishes a distinct candidate identity.

When a prior-to-current movement within 12 semitones is available it is used;
otherwise the generator deterministically ranks the remaining legal pool. If
no anchor-safe and bass-spaced legal voicing exists, generation fails with its
ordinary validation evidence rather than altering authority, melody, or chord
identity. Stable score/path/pitch ordering resolves ties; a seed only chooses
among paths within the documented near-best cost bound.

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

M06a persists one versioned confirmed-plan record referencing every authoritative
occurrence in order. Its purpose, phrase/repeat identities, energy, role activity/
density/register settings, shared groove and entry/exit intent are exact
authority inputs. The canonical per-role plan inputs are included in scoped
fingerprints; no label supplies a purpose and no plan record rewrites protected
MIDI, candidates, acceptances or exports. M06b derives a deterministic,
session-only proposal from a current style and occurrence order; propose and
cancel make no project or artifact writes. Only explicit confirmation persists
the plan, after matching the proposal's authority snapshot. Bounded neighbor
dependency resolution remains M07 work. This is the current project schema
version; older project schemas are rejected before writes, with no automatic
migration.

The remaining requirements for M07 are not claims of shipped support:
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

Piano comping uses authored 4/4, 3/4 and 6/8 patterns on the song meter grid,
clipped at exact chord boundaries. Sustained support changes voicing at an offbeat
chord boundary and retains the next bar attack; pulsed patterns keep their authored
attacks and may leave a rest after a chord change. Other meters remain valid musical authority
and import data, but Chords generation and style preview reject them explicitly
until authored comping is available; they never substitute a 4/4 pattern.
