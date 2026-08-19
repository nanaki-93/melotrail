# Task 022B — Metadata-driven Instrument Registry and resolver

## Goal

Evolve the validated sound library into a multi-instrument catalog and resolve
Task 022 sound intent to an explainable, reproducible installed instrument.

## Why

Registry v1 safely maps exactly one piano/bass/drums/pad/strings key to one SFZ.
It cannot choose between Fashionbass and Sneakybass using profile, mood, section,
role, desired character, capabilities, and license policy.

## Dependencies

Task 022.

## Existing Code

- `InstrumentRegistry.kt`, `sounds/instruments.json`, `sounds/LICENSES.json`
- `sounds/README.md`, registry/security/license tests
- `LocalSoundLibraryInventory.kt`, Library UI and runtime readiness
- `SfizzInstrumentRenderer.kt`, `StemRenderingMixer.kt`
- `CommercialProvenanceService`

## Changes

- Add registry v2 `InstrumentDefinition`: stable ID/name, eligible roles,
  weighted profile/mood affinities, controlled attack/tone/articulation traits,
  tagged engine descriptor, embedded license/source-library provenance, and
  declared/verified capabilities including playable range, velocity layers,
  round robin, release samples,
  articulations, polyphony/note map where applicable.
- Validate and preserve well-formed future profile/mood affinity IDs even when
  their catalogs are not installed; they score neutrally until available.
  Unknown versioned sonic-character vocabulary IDs make an entry unavailable
  because the resolver cannot interpret them safely.
- Replace v2 `licenseRef` authority with embedded normalized license metadata:
  license ID, commercial-use/attribution flags, ready-to-publish attribution when
  required, source name/URL, license URL/text evidence, policy review/version,
  plus library ID/name/version/source provenance. Retain strict relative-path,
  symlink, SFZ/WAV, MIDI, and license validation.
- Treat `LICENSES.json` as v1 compatibility input: materialize the referenced
  record into each legacy descriptor and preserve registry/license hashes. Do not
  silently invent commercial or attribution terms.
- Add versioned license admission policy. Known CC0/verified owned entries are
  eligible without attribution; supported CC BY entries require complete
  attribution metadata; recognized NC licenses are rejected even if a boolean
  claims commercial use; unknown/custom terms require explicit review. Semantic
  conflicts make the entry unavailable.
- Use `PREFER_NO_ATTRIBUTION` as the default resolver preference after musical
  hard constraints/fit. It must not override a compatible user pin or select a
  musically/capability-incompatible CC0 entry.
- Verify detectable capabilities from assets and distinguish verified from
  declared-only values.
- Permit multiple instruments per role and multi-role instruments. A malformed
  catalog is fatal; an invalid individual entry is unavailable with diagnostics.
  Readiness checks required role coverage instead of exactly five registry keys.
- Implement a versioned resolver: hard-filter validated installed candidates by
  role/engine/capability/range/license, honor a compatible user pin, score
  profile/mood/section/trait/capability fit, then tie-break by stable ID. Optional
  variety requires a stored seed and versioned bounded policy.
- Return/persist `InstrumentSelectionDecision`: normalized request, registry and
  resolver versions/hashes, candidate IDs/scores/reasons/rejections, selected ID,
  actor, timestamp, and optional seed.
- Freeze the selected stable ID on arrangement approval. Rendering resolves that
  exact ID to a private engine descriptor and never re-runs scoring or silently
  substitutes. Missing/mismatched IDs/assets require explicit substitution and a
  new arrangement revision.
- Continue reading registry v1. Preserve `piano`, `bass`, `drums`, `pad`, and
  `strings` as legacy stable instrument IDs, derive their roles, use neutral
  affinities, and never rename/move their SFZ/sample files.
- After v1 fixture migration and all generators/renderers use stable IDs plus
  roles, delete the exact-five-key loader branch, fixed `LogicalInstrument`
  runtime enum, direct role-to-SFZ lookups, duplicate inventory projection, and
  exclusive tests/config/docs. Retain only the pure registry-v1 input mapper
  while v1 remains a supported import format.
- Update Library UI with safe metadata search, verified capability/license/engine
  status, per-entry diagnostics, and project role coverage. Update Arrange UI
  with suggestion reasons, pin/override, no-candidate recovery, and explicit
  substitution. Never expose paths/filenames to the planner or portable DTOs.
- Feed verified selected-instrument capabilities to MIDI generators; engine paths
  remain confined to registry/renderer adapters.
- Record registry/decision/asset/license/substitution evidence for Task 027.

## Files

Registry domain/schema/loader/resolver/renderer, sound-library JSON/docs, local
inventory/readiness, arrangement assignments/generator capability adapters,
Library/Arrange UI, commercial evidence, migration fixtures, and tests.

## API / Contracts

Add path-free catalog summaries, `ResolveInstrument(request)`, immutable decision,
assignment, license-admission result, availability, and substitution commands.
Stable instrument ID is the only renderer lookup key exposed outside the registry
boundary.

## UI

Show name/roles/traits/affinities/verified capabilities/license/engine readiness,
commercial/attribution admission, source-library version, suggestion score reasons,
user pin, and explicit substitution. Absolute paths and engine filenames stay hidden.

## Backend

The resolver is deterministic Kotlin application/domain code. The configured
library root remains machine-local; project decisions store IDs/hashes only.

## Python Worker

No change. Engine support remains the validated local SFZ renderer boundary.

## Tests

Registry v1/v2, duplicate/case-conflicting IDs, multiple/multi-role candidates,
path/symlink/SFZ/WAV/license and capability validation, declared/verified mismatch,
embedded license round-trip, v1 materialization, CC0/CC BY/owned admission, NC
rejection, unknown-review and semantic conflicts/missing attribution, default
no-attribution preference, future inactive affinity IDs, unknown trait vocabulary,
hard filters, score
reasons/stable tie-break/optional seed, AI path rejection,
pin/no-candidate/substitution, approved no-re-resolution, cross-machine missing/
hash mismatch, legacy stem aliases, UI redaction, and commercial evidence.

## Acceptance

- `Lo-fi + Nostalgic + Verse + Bass + Soft` deterministically resolves an
  explainable validated candidate without exposing a filename to the planner.
- Multiple bass instruments coexist and user-pinned compatible IDs win.
- CC0 is preferred by default when musically suitable, supported CC BY remains
  selectable/traceable, and NC entries cannot enter the available catalog.
- Approved songs never change timbre merely because registry contents/resolver
  weights change; missing assets block until explicit substitution.
- Existing starter registry and projects remain readable/renderable.
- One registry/resolver/renderer runtime path remains; v1 is migration input, not
  a parallel selectable implementation.

## Out of Scope

Bundling/downloading libraries, online catalogs, registry editing UI, legal advice,
plugin engines, audio-based auto-tagging, additional profiles, or sample acquisition.
