# MIDI Core functional specification

Status: target behavior; not a claim that the current audio-era runtime already
implements it

Authority: user-visible functions and acceptance behavior

## 1. Product actor

The primary actor is a musician with an existing MIDI composition or melody who
wants arrangement assistance before completing sound design and production in
Logic Pro.

The musician remains responsible for musical authority and every acceptance
decision. Melotrail provides deterministic alternatives, evidence, MIDI
audition, and safe export.

## 2. Project functions

### F-PROJ-001 — Create a MIDI project

The user can choose a project name and repository-local or user-selected
project directory. Creation writes the current MIDI-only schema atomically and
does not require a worker, network connection, model, sound library, or DAW.

### F-PROJ-002 — Open a MIDI project

The user can reopen a current project with source identity, authority,
candidates, acceptances, and export history intact. Missing or digest-mismatched
artifacts are reported before mutation.

### F-PROJ-003 — Reject legacy audio projects

Opening an old audio/schema-v4 project produces a concise unsupported-project
message. Melotrail does not auto-migrate it or start an old runtime. Repository-
owned old projects may be deleted during cleanup.

### F-PROJ-004 — Save safely

Every project-state change is validated and written atomically. A failed write
leaves the last known-good project readable.

## 3. MIDI source functions

### F-MIDI-001 — Import Standard MIDI

The user can import one `.mid` or `.midi` SMF format 0 or 1 file using PPQ
division. It contains the complete song as exactly one note-bearing track on
one note-bearing channel; additional tracks cannot contain notes. The original
bytes, filename, digest, header facts, track summaries, and import report are
preserved.

### F-MIDI-002 — Inspect tracks

The MIDI page shows track name, index, channels, note count, pitch range,
duration, controller presence, and likely role hints without claiming that a
heuristic is authoritative.

### F-MIDI-003 — Protect the melody automatically

Import atomically identifies and protects the only note-bearing track/channel.
Files with zero or multiple note-bearing tracks, or multiple note-bearing
channels in the melody track, are rejected with an actionable explanation.
There is no manual selection or in-project source-switching step.

### F-MIDI-004 — Validate input

The application separates blocking structural errors from musical advisories.
It does not reject valid polyphony, chromatic notes, or unusual density merely
because they are unconventional.

### F-MIDI-005 — Preserve source identity

Import and later operations never overwrite the source file. Semantic source
views point back to the preserved source digest and selected track identity.

## 4. Musical-context functions

### F-AUTH-001 — Set fixed tempo and meter

The user enters tempo in beats per minute (BPM) and chooses one time signature.
The application converts BPM to the nearest valid Standard MIDI
microseconds-per-quarter value for exact persistence and export. Unsupported
maps are reported during import rather than flattened silently.

### F-AUTH-002 — Set project key and mode

The user confirms project key and mode. Detection may provide an advisory
suggestion but cannot become authoritative without confirmation.

### F-AUTH-003 — Define sections and occurrences

The user builds one ordered list using only musician-facing section names and
positive whole-bar lengths. Stable definition and occurrence identities remain
internal. The application derives contiguous start/end ticks from confirmed
meter and PPQ. The section bar total must exactly equal the imported melody
length.

### F-AUTH-004 — Define authoritative harmony

The user writes one chord progression per saved section occurrence, with chord
symbols in playing order separated by `|`. The application deterministically
maps those symbols to gap-free windows across the section; repeating a symbol
extends that harmony across additional equal slots. Persisted chord events keep
their exact durations when the progression is unchanged. Valid chromatic chords
are accepted. Syntax or realizability errors block generation only in the
affected scope.

### F-AUTH-005 — Preview authority impact

Before an authority edit invalidates accepted work, the UI shows which
candidates and exports will become stale. The edit never deletes their files
silently.

## 5. Audition functions

### F-PLAY-001 — Control MIDI playback

The user can play, pause, stop, seek, and loop an available MIDI view without
rendering an audio file. With no external receiver selected, the desktop opens
and owns an audible built-in synthesizer for preview.

### F-PLAY-002 — Audition scopes

The user can audition source melody, one candidate, one section occurrence, one
role, or the currently accepted full arrangement.

### F-PLAY-003 — Mute and solo

The user can mute or solo roles during review. Playback state is session UI
state and does not change exported MIDI unless the user explicitly disables a
role in arrangement authority.

### F-PLAY-004 — Handle device failure

Unavailable MIDI output, synth initialization failure, or device loss produces
a recoverable message and resource cleanup. Project and acceptance state remain
unchanged.

## 6. Arrangement functions

### F-ARR-001 — Generate chord candidates

The user can generate multiple chord/keys accompaniment alternatives for a
selected occurrence. Each uses authoritative harmony, a complete curated rhythm
pattern, bounded voicing, and melody/bass space rules.

### F-ARR-002 — Generate bass candidates

The user can generate multiple bass alternatives using authoritative harmony,
selected performance profile, occurrence purpose, phrase position, melody
activity, and accepted rhythmic context.

### F-ARR-003 — Generate drum candidates

The user can generate multiple complete groove alternatives using section
energy, phrase position, fill policy, and accepted bass context. Density chooses
an authored variant rather than deleting arbitrary hits.

### F-ARR-004 — Generate deterministically

The same authority snapshot, settings, generator version, and seed produce the
same semantic MIDI events and validation result.

### F-ARR-005 — Validate every candidate

Generation returns either a candidate with its report or a scoped rejection.
Rejected output cannot become current merely because a MIDI file exists.

### F-ARR-006 — Regenerate narrowly

The user can regenerate one role in one occurrence. Unrelated accepted
candidates remain current and unchanged.

### F-ARR-007 — Offer performance profiles

Profiles express MIDI performance intent such as sustained/sub-like or
muted/plucked bass. They do not claim to reproduce an exact audio patch.

Arrange presents these controls as one guided decision: choose a section and
role, choose its performance/rhythm feel, then generate the next immutable
alternative. Seeds advance automatically from the saved scope. A successful
generation immediately shows the new alternative and its validation summary;
there is no separate refresh step or duplicate generate/regenerate action.

## 7. Review functions

### F-REV-001 — Compare alternatives

The Review page selects one alternative at a time for the chosen role and
occurrence and exposes its seed, pattern/profile, validation findings, and an
optional comparison with another alternative.

### F-REV-002 — Accept a candidate

Acceptance records an immutable candidate reference after verifying its digest
and authority hash. The prior accepted candidate remains recoverable.

### F-REV-003 — Reject a candidate

Rejection prevents accidental selection without deleting evidence required by
the current project policy.

### F-REV-004 — Lock accepted work

A lock prevents broad regeneration from changing the accepted reference. The
user must explicitly unlock the same role/occurrence to choose another result.

### F-REV-005 — Detect stale work

Authority edits mark dependent candidates stale. Stale candidates remain
inspectable but cannot be exported as current.

### F-REV-006 — Assemble the song

The application creates a review view from protected melody plus currently
accepted role candidates using exact occurrence boundaries. Assembly does not
rewrite source or candidate artifacts.

Review's primary sequence is Play alternative -> Accept -> Continue to the next
unfinished section/role. Acceptance and other lifecycle mutations refresh the
same scope immediately. Reject, restore, lock, unlock, comparison, device
choice, and detailed transport controls appear only when valid or explicitly
expanded. Full-arrangement playback remains unavailable until all required
section/role acceptances exist.

### F-REV-007 — Optional melody connection

After MVP, the user may request a versioned candidate containing bounded
connection notes or edits. The UI shows an event-level diff and requires
explicit approval. The imported melody remains available unchanged.

## 8. Export functions

### F-EXP-001 — Capture an export snapshot

Export binds source, authority, accepted candidates, role settings, and
generator versions to one immutable snapshot ID before writing files.

### F-EXP-002 — Export a complete song

The application writes a deterministic SMF format 1 file with conductor/meta,
melody, chords, bass, and drums tracks. Optional roles appear only when enabled.

### F-EXP-003 — Export individual roles

The application writes separate role MIDI files that start at the same song
origin and carry enough tempo/meter metadata for predictable DAW placement.

### F-EXP-004 — Export a manifest

One JSON manifest describes musical authority, source/candidate hashes, file
digests, role presence, performance profiles, instrument suggestions,
validation summaries, and schema/generator versions.

### F-EXP-005 — Validate generated files

Every generated MIDI file is re-imported and semantically checked before the
staged package becomes visible as complete.

### F-EXP-006 — Prevent silent overwrite

If a target package already exists, the user chooses a new destination or
explicit replacement policy. A failed export leaves no package marked current.

### F-EXP-007 — Support the Logic Pro destination

The package passes the manual Logic Pro matrix in `DAW_COMPATIBILITY.md`.
GarageBand is not a supported destination and has no compatibility claim.

## 9. UI functions

### F-UI-001 — Focused navigation

Only Project, MIDI, Structure & Harmony, Arrange, Review, and Export are
top-level destinations. Obsolete audio-production destinations are removed.

### F-UI-002 — Explain blockers

Each page states why its next action is unavailable and links the explanation
to the exact missing or invalid authority, candidate, device, or export state.

### F-UI-003 — Remain responsive

Import, generation, validation, and export run without blocking Compose event
handling. Progress and cancellation are scoped to the active operation.

### F-UI-004 — Preserve accessibility

Primary actions, status, candidate selection, transport, and navigation expose
stable text/semantics for keyboard use and UI tests.

### F-UI-005 — Recover after restart

Persisted project state, not ephemeral page state, determines which workflow
actions are complete after reopening.

### F-UI-006 — Present a focused MIDI workspace visual system

The desktop uses one responsive, accessible visual language across Project,
MIDI, Structure & Harmony, Arrange, Review, and Export. It presents real
project evidence—MIDI events, authority timing, candidate state, and export
validation—with clear hierarchy and never substitutes audio waveforms, video
art, mixers, libraries, or settings pages. The visual contract is defined in
`docs/MIDI_WORKSPACE_VISUAL_SPEC.md`.

## 10. System functions

### F-SYS-001 — Operate locally

The deterministic MVP opens, arranges, auditions, and exports without a network
connection or model service.

### F-SYS-002 — Run without Python

The final application and test/build workflow contain no Python runtime,
requirements, worker process, or Python-owned MIDI behavior.

### F-SYS-003 — Record provenance proportionally

Project and manifest evidence identifies inputs, generators, seeds, candidates,
and output hashes. It does not contain an audio-release or platform-policy
system.

### F-SYS-004 — Fail safely

Cancellation, crash, invalid MIDI, disk failure, stale completion, or audition
failure cannot replace the source, accepted candidate, last known-good project,
or last complete export.

## 11. Post-MVP functions

The following are excluded from mandatory implementation tasks until the MVP
passes every gate:

- pad generation;
- constrained Qwen pattern suggestions;
- variable tempo or meter;
- multiple source MIDI files;
- direct DAW automation;
- advanced MIDI editing; and
- enhanced preview instruments.

Adding one requires an explicit plan revision. It is not valid to implement it
opportunistically inside a core task.
