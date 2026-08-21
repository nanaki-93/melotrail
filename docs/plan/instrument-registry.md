# Instrument Registry and sound-selection plan

## Goal

Turn the current fixed logical-instrument map into a validated, searchable local
catalog. Arrangement code and AI request musical intent; a deterministic resolver
selects an installed instrument. Only the renderer boundary resolves the selected
instrument ID to an engine file such as an SFZ.

## Current state

`InstrumentRegistry.kt` is already a strong security and licensing boundary:

- registry and license schemas are strict;
- paths are relative, project-external, traversal/symlink confined, and read-only;
- SFZ regions and referenced WAV files are parsed and validated;
- MIDI program/channel and the starter drum map are validated;
- commercial provenance hashes the selected SFZ/samples and license evidence;
- the desktop library page exposes a safe, path-free inventory.

Its limitation is semantic: registry v1 must contain exactly the logical keys
`piano`, `bass`, `drums`, `pad`, and `strings`, and each key resolves to one
engine file. Role, instrument identity, category, and renderer identity are
therefore conflated. The arranger can choose “bass” but cannot choose between
Fashionbass and Sneakybass based on musical context.

## Target concepts

### Instrument definition

A v2 catalog entry is conceptually:

```yaml
id: karoryfer-fashionbass
name: Fashionbass
roles: [bass]
affinities:
  profiles: [{ id: lofi, weight: 0.8 }, { id: jazz, weight: 0.7 }]
  moods: [{ id: warm, weight: 0.9 }, { id: relaxed, weight: 0.8 }]
characteristics:
  attack: soft
  tone: warm
  articulations: [fingered]
engine:
  type: sfz
  file: fashionbass.sfz
license:
  type: CC0-1.0
  commercialUse: true
  attributionRequired: false
  source: Karoryfer Samples
  sourceUrl: "<authoritative-source-url>"
  licenseUrl: https://creativecommons.org/publicdomain/zero/1.0/
provenance:
  libraryName: Fashionbass
  libraryVersion: "1.001"
capabilities:
  velocityLayers: 5
  roundRobin: 3
  releaseSamples: true
  playableRange: { low: 28, high: 72 }
```

The exact persisted format may remain JSON to match the current library. Important
separations are:

- `id` is stable, machine-safe, and never derived from a mutable filename/name;
- `name` is display metadata;
- `roles` are functional eligibility, not engine files;
- profile/mood values are weighted affinities, not hard style ownership;
- characteristics use controlled, versioned vocabulary IDs;
- `engine` is a tagged descriptor; MVP supports SFZ, future engines require a new
  validated renderer adapter rather than optional path fields everywhere;
- `license` is an embedded immutable commercial-use/attribution snapshot for that
  instrument; the selected instrument never depends on a mutable global record;
- `provenance` identifies the source library and version independently from the
  license and engine asset hashes;
- capabilities distinguish declared values from values the loader can verify.

Profile/mood affinity IDs are syntactically validated and preserved even when
that profile is not installed yet; they simply do not contribute to today's
score. This allows a library to declare future `jazz` or `ambient` affinity while
Melotrail currently ships only Lo-fi. Sonic characteristic IDs, by contrast,
belong to a versioned vocabulary because the resolver must understand their
meaning before scoring them.

### Embedded license and provenance

`InstrumentLicenseMetadata` contains at minimum:

- normalized license/tool ID such as `CC0-1.0` or `CC-BY-3.0`;
- `commercialUse`;
- `attributionRequired`;
- ready-to-publish `attribution` text when attribution is required;
- source/publisher name and authoritative source URL;
- license/deed URL and optional local license-text hash/reference;
- policy review/version metadata when the license is not a built-in known type.

`InstrumentLibraryProvenance` contains library name/ID, library version, source
release/reference, and optional acquisition/review date. It is not a substitute
for hashing the actual SFZ/samples.

Registry v2 embeds this metadata per instrument even when multiple entries share
the same source library. Implementations may deduplicate full legal text by hash,
but a selected entry must remain self-describing for admission, provenance, and
credits. The existing separate `LICENSES.json` is a v1 compatibility source: its
record is materialized into each legacy descriptor/snapshot and its hash is kept
as evidence, rather than remaining the v2 runtime authority.

### Commercial admission policy

A versioned `InstrumentLicensePolicy` validates normalized license semantics; it
does not trust `commercialUse` alone:

- known CC0 is eligible without required attribution and receives the default
  no-attribution preference;
- supported CC BY is eligible only when commercial use, source, license URL, and
  a nonblank ready-to-publish attribution block are consistent;
- any recognized NonCommercial (`NC`) license is rejected at registry admission/
  import even if conflicting metadata says `commercialUse: true`;
- unknown/custom licenses are `REVIEW_REQUIRED` and unavailable for commercial
  selection until an explicit reviewed policy entry exists;
- contradictory license ID/booleans/attribution fields make the entry invalid.

The initial allowlist should stay deliberately small (CC0, supported CC BY
versions, and verified generated-original/project-owned assets). Expanding it is
a policy change with tests, not an arbitrary registry boolean. This reflects the
official distinction between [CC0 commercial use without required attribution](https://creativecommons.org/publicdomain/zero/1.0/),
[CC BY attribution](https://creativecommons.org/share-your-work/use-remix/cc-licenses/),
and Creative Commons NonCommercial restrictions. It remains evidence automation,
not legal advice.

### Controlled musical criteria

`InstrumentSelectionRequest` contains:

- arrangement role (required);
- composition profile ID/version;
- mood ID/version;
- section type and occurrence purpose;
- desired attack, tone, articulation, or other controlled characteristics;
- required capabilities (playable range, drum note map/articulations, velocity
  behavior, release support where musically required);
- commercial-use/attribution policy;
- optional user-pinned instrument ID;
- optional diversity seed only when explicitly requested/stored.

AI may propose only this controlled request. It never receives or emits registry
paths, SFZ filenames, sample filenames, license file paths, or arbitrary engine
arguments. Unknown trait IDs and unsupported capability requirements fail schema
validation.

## Deterministic resolver

Resolution is code-owned and explainable:

1. Load the configured registry and validate catalog schema/identity uniqueness.
2. Validate each engine descriptor, assets, embedded license/provenance, and detectable
   capabilities. A malformed registry is fatal; individual invalid entries are
   unavailable with diagnostics rather than silently selectable.
3. Apply hard filters: installed/validated, required role, supported engine,
   playable range/note map/capabilities, and admitted commercial-license policy.
4. If the user pinned a compatible ID, select it. If incompatible/unavailable,
   block with a specific recovery action; do not fall back silently.
5. Score remaining candidates using versioned weights for profile affinity, mood
   affinity, section/purpose preference, desired characteristics, capability
   fitness, profile recommendations, and the configured default preference for
   no-attribution instruments. License preference is a tie/weight, not permission
   to choose a musically incompatible candidate.
6. Sort by score and stable instrument ID. Default selection is deterministic.
   Optional variety uses a stored seed and a versioned bounded top-candidate rule.
7. Return a decision with selected ID, resolver version, normalized request,
   candidate IDs/scores/reasons, rejected hard constraints, and registry hash.

No valid candidate is an actionable arrangement/readiness error. The resolver
must explain whether the missing condition is a role, capability, engine, asset,
or license problem.

## Selection lifecycle

There are two distinct moments:

- Suggestion/draft: resolution may be recomputed while profile, mood, desired
  character, installed library, or user choices change.
- Approved arrangement: persist the selected instrument ID and decision evidence.
  Rendering resolves that exact ID only. It must not rerun ranking and choose a
  different instrument because the local library or resolver changed.

An explicit substitution creates a new arrangement revision, records old/new IDs
and reason, invalidates generated MIDI only when capabilities affect generation,
and always invalidates render/mix/master onward. User selection remains the
highest-priority creative authority.

## Roles, generation, and rendering

MIDI generators consume roles plus verified performance capabilities—not SFZ
paths. Examples:

- bass generator constrains notes to selected playable range/articulation policy;
- drum generator uses a verified note map/capability set;
- melody/harmony generators can respect polyphony/range/articulation capabilities.

The renderer receives the approved stable ID, obtains a validated descriptor,
then supplies its private SFZ path to `sfizz_render`. Stem/project metadata store
instrument ID, role, engine type/version, and hashes, not machine paths.

## Registry validation and library UI

The library UI becomes a catalog browser showing name, roles, characteristics,
profile/mood affinities, verified capabilities, engine readiness, license, and
sample count. It supports filtering without exposing paths. Diagnostics distinguish:

- library unconfigured;
- catalog schema invalid;
- entry unavailable/invalid;
- role coverage missing for the selected profile/project;
- renderer engine unavailable;
- commercial/license policy incompatible.
- required attribution incomplete or license requires review/rejection.

Do not require every library to contain exactly the starter five entries. Project
readiness instead checks required arrangement roles against valid entries.

## Registry cutover

- Accept only the canonical registry schema and stable instrument IDs.
- Do not retain a v1 reader, role alias lookup, migration command, or fallback
  for approved arrangements or mix/stem references.
- Retain SFZ/WAV/license/path validation for canonical entries.
- Reject projects whose arrangement or render evidence references superseded
  registry keys; do not rewrite those references during open.

## Provenance and portability

Record catalog schema version/hash, resolver/policy version, normalized request,
candidate scores, selected stable ID, engine type, SFZ/sample asset hashes,
verified capability snapshot, embedded license/source-library snapshot, selection
actor, approval, and
any substitution event.

On another machine, the same stable ID plus matching asset hashes reproduces the
sound. A missing/mismatched ID is explicit; it does not trigger silent selection.

## Usage-based release credits

Credits are generated from the immutable release lineage, never by scanning the
current library. The credits service:

1. resolves the final mix's used stem set after mute/solo/inclusion decisions;
2. maps each used stem to its approved stable instrument/license snapshot;
3. excludes suggested candidates, unused roles, rejected drafts, unavailable
   entries, and CC0/no-attribution instruments;
4. normalizes and deduplicates identical required attribution blocks;
5. sorts deterministically and writes `<export-base>-credits.txt` atomically;
6. records the credits hash and contributing instrument IDs in the release manifest.

If a stem's absence from the final audio cannot be proven, include its attribution
conservatively. A CC0-only release still produces the predictable sibling file
with a single “No instrument attribution required” statement and no CC0 catalog
listing. Missing/contradictory required attribution blocks commercial-ready export.

The text file is human/copy-friendly for publication descriptions. It contains
only required instrument attribution blocks (or the no-attribution statement),
not every dependency, candidate score, AI model, or general provenance detail.

## Testing

- v1 compatibility and v2 schema/round-trip;
- duplicate/case-conflicting IDs and controlled vocabulary validation;
- multiple instruments per role and multi-role instruments;
- path/symlink/SFZ/WAV/license validation retained;
- known CC0/CC BY/generated-original admission, NC rejection, unknown review,
  semantic conflict and missing-attribution rejection;
- declared versus verified capability mismatch;
- hard filters, weighted score reasons, stable tie-break, optional seeded variety;
- user pin, missing pin, no-candidate diagnostics, explicit substitution;
- AI plan cannot contain paths/filenames/unknown traits;
- approved arrangement never re-resolves during render;
- registry/resolver/profile changes stale drafts but not historical approved
  lineage; asset mismatch blocks current render;
- commercial evidence and UI path redaction.
- final used-stem attribution filtering, CC0 omission, deduplication/order,
  no-attribution output, atomic naming/hash, and stale-registry independence.

## Scope

MVP engine support remains SFZ. Automatic library downloads, plugin instruments,
sample tagging through audio analysis, registry editing, online catalogs, and
additional composition profiles are Future work.
