# Target composition domain model

## Aggregate boundary

Keep one portable, file-backed `Project` aggregate. Schema v4 is the only
supported format. `ProjectStore.open` rejects missing-version, v1–v3, and
superseded v4 documents without rewriting; canonical saves remain atomic.

```text
Project
├── identity and schema version
├── composition settings
├── harmony by section type
├── song parts
├── structure occurrences
├── arrangement configuration
├── workflow selections/approvals
└── references to stage and provenance manifests
```

Large histories, raw model evidence, quality reports, and per-run records remain
project-relative sidecars referenced by hash.

## Musical primitives

### Pitch class and key

`PitchClass` uses a canonical chromatic value (0–11) plus an optional preferred
spelling for display/export. This permits C#/Db equality without losing a user's
Eb spelling. Validate spellings; never use the label as the identity.

`ScaleMode` starts with `MAJOR` and `NATURAL_MINOR` as executable behavior. Its
ID-based representation must permit future modes without changing `MusicalKey`.
Unsupported modes fail profile/processor validation rather than silently being
treated as major.

`MusicalKey` contains:

- tonic: `PitchClass`
- mode: `ScaleModeId`
- optional user spelling preference

Source-key analysis is separate evidence: detected key, confidence, algorithm
version, and user confirmation. Project key is always the creative authority.

### Tempo and meter

- `Tempo`: finite positive BPM with profile-supplied UI recommendation/range;
  core validation must not impose a lo-fi range.
- `TimeSignature`: positive numerator and denominator restricted to valid note
  units. The Lo-fi profile may initially expose only 4/4 in simple UI, while the
  model and timing math support other meters.
- Tempo maps and mid-song meter changes are Future scope. The v4 project stores
  one project-level tempo and meter, and processors reject incompatible inputs
  or return explicit conformance warnings.

### Chords and progressions

`ChordQuality` MVP values:

```text
MAJOR, MINOR, DOMINANT_7, MAJOR_7, MINOR_7,
MAJOR_9, MINOR_9, ADD_9, SUS_2, SUS_4
```

`ChordEvent` contains a stable event ID, root, quality, and position/order. A
default one-measure duration is implied in MVP. Reserve optional structured
fields for duration, bass pitch/slash chord, inversion, extensions/alterations,
and substitution provenance; do not encode them inside a display string.

`ChordProgression` has a stable ID, section type ID, ordered events, and optional
name. Display strings such as `Dm9 | Bbmaj7 | Fmaj7 | Cadd9` are formatting only.

### Section types

Use a validated `SectionTypeId` rather than a closed enum. Built-ins include
`intro`, `verse`, `chorus`, `bridge`, and `outro`; profiles may contribute labels
and defaults later. MVP requires editable progressions for verse, chorus, and
bridge. A normalized future section ID remains explicit and blocks unsupported
processing rather than being guessed into a built-in type; this is forward
extensibility, not a reader for an old project shape.

## Composition profile and mood

### Composition profile

Persist `CompositionProfileRef(id, version)`. Resolve it from a catalog bundled
with the app. The resolved typed definition may include:

- display metadata and supported features;
- tempo/meter defaults and UI constraints;
- supported mood IDs and default mood;
- chord-quality preferences (suggestions, never arbitrary replacement);
- arrangement role definitions and instrument-selection criteria/weights;
- density, swing, velocity, timing, and humanization bounds;
- enhancement tolerance/change-budget defaults;
- cohesion transition vocabulary/bounds;
- optional audio style-processing presets;
- processor policy versions.

The first entry is `lofi` with an explicit version. Core domain and UI code use
profile capabilities, not `if (style == "lofi")`.

### Mood

Persist `MoodRef(id, definitionVersion)` and resolve a `MoodDefinition` with
typed modifiers such as density, rhythmic looseness, swing, velocity center,
humanization, chord-extension preference, tension, phrase complexity,
sound-character preferences, accompaniment/transition behavior, and correction/
enhancement tolerance.

Initial IDs may be warm, nostalgic, melancholic, dreamy, relaxed, and dark.
Undefined modifiers use neutral profile values. A mood may contribute a short
model-facing label only after its structured parameters are resolved.

Profile and mood combine through explicit bounded rules:

```text
resolved parameter = profile base + mood modifier, clamped to profile bounds
```

Store the resolved parameter snapshot/hash in each processing run so later
profile updates do not make past outputs irreproducible.

## Instrument registry and selection

An arrangement role is not an instrument and an instrument is not an engine
file. The target sound-library domain uses:

- `InstrumentId`: stable registry identity such as `karoryfer-fashionbass`;
- `InstrumentDefinition`: display name, eligible role IDs, weighted profile/mood
  affinities, controlled sonic characteristics, engine descriptor, embedded
  `InstrumentLicenseMetadata`, `InstrumentLibraryProvenance`, and declared/
  verified capabilities;
- `InstrumentSelectionRequest`: role plus profile, mood, section/purpose, desired
  characteristics, hard capability/license requirements, and optional user pin;
- `InstrumentSelectionDecision`: resolver/policy version, registry hash,
  normalized request, candidate scores/reasons, selected ID, and selection actor;
- `RoleInstrumentAssignment`: arrangement role/occurrence scope, selected stable
  ID, approval state, and decision reference.

The registry may contain multiple instruments per role and instruments may serve
multiple roles. Profile/mood tags are affinities, not a rule that an instrument
can only be used in those styles. Characteristic/capability values use versioned
controlled IDs/fields, not arbitrary model prose.

Engine descriptors are a tagged type. MVP implements `sfz` with a project-external,
registry-relative file that only the validated loader/renderer may resolve.
Capabilities include playable range, supported articulations/note map, velocity
layers, round-robin count, and release samples; loaders mark which claims were
verified from assets versus merely declared.

`InstrumentLicenseMetadata` embeds normalized license ID, commercial-use and
attribution requirements, ready-to-publish attribution text when required,
source name/URL, license URL, and optional reviewed-policy/legal-text evidence.
`InstrumentLibraryProvenance` embeds library ID/name/version and source-release
evidence. Registry v2 instrument entries are self-describing; v1 `LICENSES.json`
records are materialized by the compatibility adapter and retained by hash.

`InstrumentLicenseAdmission` is a versioned decision:

- `ALLOWED_NO_ATTRIBUTION` for known CC0/verified owned assets;
- `ALLOWED_WITH_ATTRIBUTION` for supported CC BY with complete metadata;
- `REJECTED_NONCOMMERCIAL` for any recognized NC license;
- `REVIEW_REQUIRED` for unknown/custom terms.

Declared booleans cannot override known license semantics. Contradictory license
ID/commercial/attribution metadata makes the instrument unavailable. The default
resolver preference favors no-attribution candidates after hard musical fit; a
user may explicitly choose an admitted CC BY instrument.

Unknown well-formed future profile/mood affinity IDs round-trip but are inactive
until the corresponding catalog definition exists. Unknown sonic characteristic
vocabulary IDs cannot be scored and therefore make that entry unavailable until
supported.

Approved projects persist stable instrument IDs and decision/registry hashes,
never filenames or absolute library paths. A missing/mismatched selected ID is a
render-readiness error requiring explicit substitution, not permission to
silently re-run selection. See
[instrument-registry.md](instrument-registry.md) for resolution/migration.

A release stores `ReleaseInstrumentUsage` derived from the exact stems included
in its final mix lineage. Required attribution blocks are normalized/deduplicated
into an immutable `ReleaseCreditsArtifact` with export-relative path/hash. Unused
candidates/roles and no-attribution instruments do not create credit entries.

## Song parts and structure

### Song part

Replace the current `Part` shape at the canonical cutover:

- persistent ID and user-facing name;
- `SectionTypeId` instead of free role;
- immutable original-source reference and source attestation/import evidence;
- optional source-key analysis/confirmation;
- latest stage selections and approvals;
- reference to a per-part stage manifest;
- current analysis derived from the selected downstream MIDI.

The source is not “a complete song”; it is the musician's melody/performance.
Only the canonical `MidiReferences` shape is readable. Superseded fields and
initial-stage mappings are deleted rather than migrated.

### Structure occurrence

Replace `List<partId>` storage with:

- stable occurrence ID generated once;
- part ID;
- optional user label (for example Verse A);
- optional variation preset/overrides;
- order determined by list position.

Repeating a part creates another occurrence that references the same part. It
never duplicates the part's original/corrected/enhanced MIDI. Reorder retains
occurrence ID and reviewed variation settings.

## Processing context

Every musical correction, enhancement, arrangement, cohesion, or humanization
request receives a `MusicalProcessingContext` snapshot:

- schema/context version;
- project ID/name only where needed for display, not model control;
- project key and scale;
- tempo and time signature;
- section type and exact chord progression/timing;
- profile ID/version and resolved parameters hash;
- mood ID/version and resolved parameters hash;
- source part and/or occurrence IDs;
- upstream artifact ID/hash;
- enhancement intensity or stage-specific controls;
- seed, pipeline version, and processor policy version.

Serialization is deterministic so the context hash participates in caching and
provenance. Model prompts are adapters derived from this structure; prompt text
is never the only musical contract.

## Artifacts, runs, and selections

`ArtifactRef`:

- artifact ID and kind;
- project-relative path;
- SHA-256 and media/MIDI metadata;
- producing run ID and upstream artifact IDs;
- creation timestamp.

`StageRunRecord`:

- run/stage/subject IDs;
- `PENDING | PROCESSING | COMPLETED | FAILED`;
- input artifact IDs/hashes;
- normalized configuration/context hashes;
- processor name/version and optional model identity/license;
- optional seed;
- start/end timestamps;
- completed output refs or structured failure information;
- validation/report refs.

Only `COMPLETED` runs may have selectable output artifacts. `FAILED` runs retain
input refs and errors, never a partial output selection.

Selection is explicit per branching stage. Enhancement `OFF` resolves to the
corrected artifact. `SUBTLE`, `BALANCED`, and `CREATIVE` point to validated
enhancement artifacts. Changing a selection invalidates only dependents whose
input/context hashes change.

## Unsupported project disposition

No v3-to-v4 mapper exists. Missing-version, v1–v3, early scalar-structure v4,
provisional manifest v4, and other superseded project shapes fail admission.
The application does not infer creative settings, assign migrated occurrence
IDs, publish stage-run records, or expose a migration action for them. Current
schema-v4 projects may still have incomplete Setup/Harmony and must collect those
explicit user decisions through the normal canonical workflow.

## Core invariants

1. Original sources are immutable and cannot be selected as a derived result.
2. Every selected derived artifact has a valid completed producing run and hash.
3. A part has one section type and one current artifact per selection boundary.
4. Harmony event order/identity is stable across UI reorder.
5. Structure occurrence identity is stable across reorder and repetition.
6. Project key is authoritative; detected source key is evidence.
7. Processor output cannot overwrite any input path.
8. Context/configuration changes alter cache keys and stale exact dependents.
9. Unknown profile/mood/section versions are preserved and surfaced, not
   replaced with current defaults.
10. Commercial-ready output requires complete selected-lineage provenance.
