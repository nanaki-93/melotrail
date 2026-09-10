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

## Planned rests in drafts and acceptance

Project schema version 4 records an intentional inactive role/occurrence as a
typed planned-rest selection containing its exact scoped authority hash. A
complete draft selects exactly one validated candidate or planned rest for
every generated-role scope. Failed, missing, stale, or digest-mismatched output
cannot be converted to a rest. Draft audition assembles planned rests as silence
at their original song position while retaining the common song origin and end.

Whole-draft Use atomically applies mixed candidate/rest selections, and guarded
Undo restores the prior candidate/rest selection for every affected scope or
changes none. Locked candidates and locked rests block a different batch
selection. Downstream Bass/Drums candidates record any same-draft upstream rests
they consumed and revalidate those rest hashes against the confirmed plan.
Removing a section retains its prior candidate/rest selections and history as
stale evidence; these cannot satisfy any current occurrence during assembly.
Section saves and plan confirmations share one revision-checked write transaction.
A whole-occurrence Chords rest breaks piano-boundary continuity; the next active
Chords scope starts without a boundary inherited across that silence.

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
immutable piano-boundary summary (source Chords scope hash, boundary tick, and
ordered voices); it is included in the scoped context and generation fingerprint.
The generator never discovers that input by consulting mutable accepted work.

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

Each new immutable snapshot contains `complete-song.mid`, `melody.mid`,
`manifest.json`, and aligned `chords.mid`, `bass.mid`, and `drums.mid` files for
generated roles that are active in at least one occurrence. A generated role that is an accepted planned rest
for the entire song is omitted from both `complete-song.mid` and its individual
role file; its manifest entry has `enabled: false` and `activity: "inactive"`.
Melody is always present. A generated role that is inactive only in some
occurrences remains present, with its intentional silence at those original
song positions. Active scopes still require one valid accepted candidate: a
missing or failed output cannot be exported as silence.

Every emitted MIDI file uses the same PPQ, fixed tempo/meter, song origin and
authoritative end boundary. Individual role files must not shift their first
note to tick zero. Initial silence is meaningful, including when a role enters
after an introductory planned rest.

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

The current export manifest schema is version 2. The companion timing reader
accepts that version; unsupported manifests are rejected without rewriting them.

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
the plan, after matching the proposal's authority snapshot. A confirmed-plan
edit has a write-free affected-scope preview and requires a separate explicit
confirmation. It marks only candidates whose scoped plan fingerprint changed
(and their declared accepted dependents) stale; it preserves candidate MIDI,
acceptance references, locks and export snapshots unchanged for inspection.
A no-op edit is write-free. Its confirmation is one serialized project-state
transaction with candidate review and snapshot capture, so a stale concurrent
write is rejected rather than being overwritten. Bounded neighbor dependency
resolution remains M07 work. This is the current project schema version; older
project schemas are rejected before writes, with no automatic migration.

A changed generator/catalog invalidates applicability of its earlier musical
rating; a changed export policy requires fresh relevant Logic evidence. Stored
old artifacts remain inspectable and unchanged even when no longer current.

Piano comping uses authored 4/4, 3/4 and 6/8 patterns on the song meter grid,
clipped at exact chord boundaries. Phrase-aware comping rule v1 adds: dense
protected-melody bar fragments (including CC64-supported sounding duration) and
an active sustained choice select one held support shape for the whole metrical
fragment, selected pulsed patterns answer only in an unoccupied melody span, and
the first fully empty metrical bar immediately after a phrase rests when another
phrase follows. This explicit rest preserves the preceding voicing for subsequent
harmony; it does not turn failed generation into a rest. Additionally,
a phrase's final beat suppresses a pulsed answer attack (the final dotted-quarter
pulse in 6/8). Support changes voicing
at an offbeat chord boundary and retains the next bar attack; pulsed patterns keep
their authored attacks and may leave a rest after a chord change. Other meters
remain valid musical authority and import data, but Chords generation and style
preview reject them explicitly
until authored comping is available; they never substitute a 4/4 pattern.

M08a binds Bass and Drums to the same confirmed shared-groove record already
included in their role-scoped plan fingerprints. Complete-draft generation
keeps the existing Chords → Bass → Drums order: Bass consumes Chords evidence,
and Drums consumes Chords plus already-generated Bass evidence. It never waits
for a downstream candidate. Bass keeps its approaches
inside the current chord window, leaves one support attack per harmony window
under held protected melody even at reduced density. Density removes optional
motion first; if those support attacks exceed the approved density ceiling,
validation rejects the incompatible policy rather than silently omitting a chord
window. Zero density remains an explicit rest. Bass rejects low-end candidates that cannot retain
the existing Chords/Bass separation. Shared groove changes only bounded Bass
attack density and optional Bass-derived off-beat Drum kicks; it never rewrites
an authored drum groove, protected melody, authority timing, or accepted MIDI.
Quarter, eighth and sixteenth intent use the authoritative 3/4 or 6/8 grid,
not a substituted 4/4 bar. In 6/8, optional off-beat kick support is measured
against the two dotted-quarter pulses while retaining exact eighth/sixteenth
positions. Coordination rule version is part of Bass/Drums draft generator identity.

M08b realizes a style-selected Drum fill only at a confirmed phrase boundary
where the current exit or next section entry requests a pickup. Harmony changes
alone never request a fill; either the current or next quiet Intro/Outro intent
suppresses fills, and a quiet next section also suppresses final-bar added
Bass-derived kicks. A repeated plan family may choose one different,
complete compatible authored groove; it never decimates or rewrites individual
authored hits. Phrase/neighbor/repeat inputs and the Drum transition-rule version
remain in the scoped fingerprint, so only the declared affected Drum scopes go
stale when a transition intent changes.

M09a defines six `musical-repair-v1` plan adjustments. `leave more melody
space` sets the selected Chords scope to sparse and subtracts 20 density points;
`simplify piano` does the same with 35 points; and `lower piano register` selects
the low Chords register. `reduce bass movement` sets only selected Bass sparse
and subtracts 25 points; `calmer drums` sets only selected Drums sparse and
subtracts 30 points. `smooth the transition` sets the selected occurrence's
entry to gradual and exit to hold, so its bounded previous/current/next role
scopes may change according to their declared neighbor inputs. All reductions
floor at zero. A repair first creates exactly the resulting scoped invalidation
preview without writing project state. It preserves source bytes, chord authority,
candidate/export artifacts, acceptance references and locks. The later M09
candidate/acceptance workflow is the only path that can publish a new candidate
or alter an accepted selection.

M09 offers at most three timing/pitch-distinct alternatives for an isolated
scope. Velocity-only variants and results identical to its audible baseline
are omitted. Dependency-spanning repairs instead offer one coherent set, with
one candidate per affected role/occurrence (maximum nine scopes). Required
members may retain the baseline's notes while refreshing dependency evidence;
`matchesBaseline` identifies them. An entirely baseline-identical set is not a
new choice. Oversized impacts and planned-rest scopes return full-draft guidance
before any plan write. This is a bounded local repair, not a whole-song rewrite.

Preview is write-free; Apply confirms the exact plan change and generates in
occurrence order, Chords → Bass → Drums. It includes affected neighbors, binds
new upstream candidate IDs, and carries the verified preceding immutable piano
boundary using the full-draft evidence helper. Rejected/stale history remains
inspectable. Filtered review never bypasses project-wide artifact integrity.
The accepted pre-repair candidate remains the same-position, melody-inclusive
A/B baseline. Audition preserves all supported protected melody events and
cannot write project state. Unusable results give an explicit same-scope reason.
A failed or cancelled Apply retains the confirmed settings for retry; it does
not apply the plan adjustment again. A changed plan requires a new preview.

Use validates one reviewed candidate per repair scope and accepts the entire
set in one revision. The underlying bounded candidate-batch API rechecks the
revision, locks, status, authority, artifacts, dependencies and complete accepted
piano boundary chain under the project write lock. Individual acceptance cannot
use unmatched upstream work; missing/mismatched batch evidence changes nothing.
Unrelated accepted references and all source/candidate bytes remain preserved.

Generator identity binds repair policy, intent ordinal under that version,
explicit register offset, style, pattern/profile, comping and coordination
versions. Lower piano uses a −12-semitone voicing-center preference, preserving
legal ranges and chord identity; constraints may yield no distinct result.
Changing the intent mapping requires a new repair-policy version. Q01 listening
and Q02 Logic checks remain human evidence, not a consequence of these tests.
