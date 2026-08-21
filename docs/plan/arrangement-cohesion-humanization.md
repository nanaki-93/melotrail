# Structure, arrangement, cohesion, and humanization

## Target ownership and order

```text
selected part melodies + harmony + structure occurrences
  -> arrangement plan and occurrence variations
  -> generated role MIDI
  -> cohesion boundary/continuity plan and artifacts
  -> seeded humanization
  -> renderable occurrence timeline/stems
```

This deliberately changes the current dependency, where arrangement requires
approved cohesion. The requested target needs cohesion to understand actual
instrumentation, density, drums, bass, dynamics, and repeated-section variation.

## Structure

Persist `StructureOccurrence` IDs instead of deriving A1/A2 identity from list
position. An occurrence references a part and may hold a label and approved
variation override. Reordering changes position only. Removing an occurrence
invalidates only its downstream occurrence/boundary artifacts.

The same selected part melody is resolved once and reused. Occurrence variation
is an overlay/plan and never writes into the source/corrected/enhanced part MIDI.

## Arrangement model

Retain `GlobalSongPlanner`, `SectionVariation`, `DetailedArrangement`, generation
approval, timeline assembly, and deterministic generators. Adapt inputs to use:

- full musical context and structured harmony;
- stable occurrence IDs;
- profile ID/version and resolved policies instead of a free style string;
- arrangement roles instead of fixed instrument names;
- controlled instrument-selection requests and approved stable-ID assignments
  from the registry resolver;
- user-selected roles/instruments as authoritative constraints.

### Roles and instruments

MVP role vocabulary:

- melody
- harmony
- bass
- drums
- counter-melody
- texture
- ambience

A role defines musical responsibility and generation constraints. An instrument
definition has a stable ID/name, eligible roles, weighted profile/mood affinities,
controlled attack/tone/articulation traits, tagged engine descriptor, license
and source-library provenance snapshot, and declared/verified capabilities such
as range, velocity layers,
round robin, release samples, and drum map. Multiple instruments may serve Bass;
one instrument may support multiple roles.

The Lo-fi profile recommends selection criteria for felt piano, Rhodes, electric
piano, muted guitar, bass, drums, pad, vibraphone, and atmospheric layers where
validated resources exist; it cannot make unavailable/unlicensed instruments
appear selectable. Profile/mood metadata are affinities rather than exclusive
style gates.

The deterministic resolver first applies role, engine, capability, playable-range,
and commercial-license admission constraints. Recognized NC entries are rejected;
supported CC BY requires complete attribution; CC0 is the default preference
after musical fit. It then scores profile affinity, mood affinity,
section/purpose, requested characteristics, and profile recommendations. Stable
instrument ID breaks ties. User-pinned compatible IDs win; unavailable pins and
no-candidate results block with actionable diagnostics instead of falling back.
Any optional diversity selection uses a persisted seed and resolver version.

AI planners may request `Role: Bass`, `Profile: Lo-fi`, `Mood: Nostalgic`, and
`Attack: Soft` through controlled IDs. They never see/return SFZ or sample paths.
Code persists the normalized request, candidates/scores/reasons, selected stable
ID, registry hash/version, resolver version, and selection actor. After arrangement
approval, rendering resolves that exact ID and never ranks candidates again.

The implementation maps the current PIANO/BASS/DRUMS/PAD/STRINGS logical stems to
new roles as part of the canonical model change. No legacy project adapter or
reader is retained. Registry keys describe installed instruments rather than a
project-file compatibility contract.

### Planner boundaries

The deterministic or AI global planner may propose section purpose, energy,
density, role entry/exit, and variation. It must echo input/context hashes,
respect chosen roles and user-pinned instruments, and use a profile-supplied
controlled characteristic vocabulary. It does
not alter core melody/harmony or create transition artifacts.

Detailed arrangement then produces validated occurrence role plans. MIDI
generators use structured section chords and meter rather than only inferred
major/minor triad strings. They consume verified selected-instrument capabilities
(range, articulation/note map) rather than engine paths. Existing generators are
generalized one role at a time; no wholesale rewrite is required.

## Cohesion

Cohesion consumes the approved arrangement and ordered occurrences. For every
adjacent pair it considers:

- outgoing/incoming melody phrase evidence;
- exact section harmony/key/meter/tempo;
- role/instrument activity and density;
- dynamics/energy and variation plans;
- available transition vocabulary from profile/mood;
- upstream hashes and boundary ID.

It may propose/render pickups, drum fills, bass/chord movements, sustained
textures, dynamics/automation, instrument continuity, and phrase connections.
The core melody remains unchanged by default. Any rare melody-boundary edit must
be separately identified, bounded, previewable, and approved.

Reuse the current path-free strict transition plan, deterministic bridge render,
hash validation, per-boundary review, aggregate approval, and preview model.
Replace simplistic natural-note key mapping and isolated chord summaries with
the structured context. Meter/tempo mismatch should have an explicit adaptation
policy or block; never silently pretend all input is C/4/4.

### Dependency cutover

1. Teach arrangement to run without `requireApprovedCohesion` and remove readers
   for old approved-boundary project data.
2. Persist v4 arrangement and occurrence hashes.
3. Generate cohesion only against arrangement context; old pre-arrangement
   cohesion project data is unsupported and is not loaded.
4. Make render require approved target-order cohesion or an explicit cohesion
   bypass policy.
5. Remove transition ownership from arrangement prompts/plans after fixtures
   prove parity.

Because input identity changes, current projects require explicit approval of
new cohesion evidence.

## Humanization

Humanization is a first-class deterministic transform after cohesion. It receives
the resolved profile/mood parameters, role/occurrence plan, tempo/meter, and a
stored seed. It can control:

- note start offsets;
- velocity variation;
- note-duration variation;
- chord-note staggering;
- drum timing and velocity;
- bass timing relationship;
- swing/groove templates;
- boundary-sensitive amount and role-specific bounds.

Algorithm requirements:

- deterministic for identical input/config/seed/version;
- role-aware bounds and collision/minimum-duration checks;
- bar/section anchors protected from drift;
- no note creation/deletion or pitch change in MVP;
- output report with per-role aggregate and exact edits;
- bypass produces a selection of cohesive input, not a copied fake artifact;
- regenerate chooses and stores a new seed explicitly.

The existing `MidiLoFiFeel` transform may be replaced by profile/mood
humanization. The canonical implementation does not retain a legacy-project
preset or read historical project artifacts.

## Approvals and invalidation

- Changing a part's selected melody invalidates its arrangement usage,
  neighboring cohesion boundaries, humanization, render, and later artifacts.
- Reordering occurrences invalidates affected arrangement sequence/boundaries,
  not source part processing.
- Changing a role/instrument/density invalidates arrangement approval and its
  cohesion/humanization/render dependents.
- Changing only mix gain/pan does not invalidate arrangement or rendered stems.
- Humanization seed/config changes invalidate render onward.

Draft, rejected, and stale artifacts from the current schema remain evidence.
Only approved and current hashes feed the next stage.

## Test approach

- Repeated occurrence identity survives insert/reorder/delete and project reload.
- Arrangement plans honor user constraints and are independent of cohesion.
- Project files below v4 and old boundary shapes are rejected without mutation.
- Cohesion produces exactly `n - 1` boundary records, includes arrangement
  context, preserves melody, and rejects mismatched hashes/unsafe plans.
- Humanization is seed-reproducible, bounded by role/mood/profile, and does not
  drift anchors or corrupt MIDI.
- End-to-end fixtures cover only canonical ordering and the intentional
  invalidation/reapproval boundary.
