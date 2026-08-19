# Target composition domain model

## Aggregate boundary

Keep one portable, file-backed `Project` aggregate. The next explicit schema is
v4. `ProjectStore.open` continues to read legacy versions without rewriting;
explicit save/migration writes v4 atomically.

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
bridge. A part may use any known section type. Unknown legacy IDs remain visible
and block unsupported processing instead of being rewritten.

## Composition profile and mood

### Composition profile

Persist `CompositionProfileRef(id, version)`. Resolve it from a catalog bundled
with the app. The resolved typed definition may include:

- display metadata and supported features;
- tempo/meter defaults and UI constraints;
- supported mood IDs and default mood;
- chord-quality preferences (suggestions, never arbitrary replacement);
- arrangement role definitions and suggested instrument IDs;
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
instrument suggestions, accompaniment/transition behavior, and correction/
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

## Song parts and structure

### Song part

Evolve current `Part` compatibly:

- persistent ID and user-facing name;
- `SectionTypeId` instead of free role;
- immutable original-source reference and source attestation/import evidence;
- optional source-key analysis/confirmation;
- latest stage selections and approvals;
- reference to a per-part stage manifest;
- current analysis derived from the selected downstream MIDI.

The source is not “a complete song”; it is the musician's melody/performance.
The existing `MidiReferences` fields remain readable and map into initial stage
records during v3 migration.

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

## v3 to v4 mapping

| v3 field/concept | v4 mapping |
| --- | --- |
| project name | composition settings name |
| missing key/scale/BPM/meter | explicit incomplete setup; do not invent silently |
| `Part.role` | recognized built-in `SectionTypeId`; preserve unknown custom ID |
| source/raw MIDI | source/extracted artifact records |
| clean MIDI + approval | cleaned/normalized completed run and approval evidence |
| approved AI fix | legacy corrected/enhanced branch with original hashes retained |
| Lo-fi Feel | legacy humanization/groove artifact; keep selected behavior until rerun |
| analysis | derived record bound to selected artifact hash |
| `structure: List<String>` | stable occurrences assigned deterministically during explicit migration |
| cohesion refs | legacy pre-arrangement cohesion; mark for reapproval if target dependency changes |
| arrangement/mix/master refs | retain and validate; invalidate only when v4 input hash/context differs |

Legacy projects without composition settings open in a migration/setup-required
state. The user supplies missing creative decisions before new v4 stages run.

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

