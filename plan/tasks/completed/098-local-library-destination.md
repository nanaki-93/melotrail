# Task 098 — Local Library Destination

## Goal

Implement the distinct Library destination inspired by
`../../pictures/UI/07-library.png` as a truthful browser over the validated local
sound-library locator, instrument registry, samples, and licenses.

## Dependencies

- Task 097 accepted.

## Requirements

- Replace the Task 092 interim Library body with a focused local inventory page
  using the reference hierarchy: header, supported type tabs, search/filter
  controls, category/filter rail, grid/list content, selection, detail/context
  rail, and library readiness/recovery.
- Introduce or extend a typed application/read-model boundary that returns only
  validated local inventory from the configured sound root and instrument
  registry. Composables must not walk the filesystem, parse registries, or
  resolve samples.
- Keep the validated sound-library locator/settings boundary authoritative.
  Do not depend on process CWD, add a second instrument tree, or rewrite the
  registry during browsing.
- Show real instrument name, role/category, validated SFZ/sample availability,
  license/source metadata where available, and readiness/warnings. Never use
  the mockup's item counts, sizes, dates, tags, waveforms, or durations as data.
- Implement local search, supported type/category filters, grid/list selection,
  and details as deterministic UI projections over the read model.
- Preview is allowed only through an existing or newly explicit typed local
  preview boundary with validated artifacts and the one shared playback
  session. If no safe preview exists, omit the play action and explain recovery
  through readiness/details.
- Keep sound-root choose, clear, validate, and refresh available through
  Settings/recovery without duplicating persistence.
- Omit Add Item, Download, favorites, remote catalog, storage quota, fake
  pagination, and Insert to Project until separate typed contracts exist.
- Match the reference card/grid density, filtering structure, selected card,
  context rail, purple states, and responsive layout using shared components.

## Verification

- Application/read-model tests use temporary validated/invalid library
  fixtures and cover registry entries, missing SFZ, missing samples, invalid
  root, licenses, filtering inputs, stable IDs, and deterministic ordering.
- Compose tests cover unconfigured, invalid, empty, populated, partially
  missing, selected, searching, filtering, list/grid, long name, and failed
  refresh states.
- Assert composables do not perform direct filesystem access and all displayed
  inventory originates in the typed read model.
- Interaction tests cover search, filters, layout choice, selection, details,
  optional preview, settings route, and recovery.
- Assert mockup-only store/download/favorite/quota/pagination/insert controls are
  absent.
- Capture and overlay a full 1536 × 1024 populated fixture against
  `../../pictures/UI/07-library.png`, plus an empty/unconfigured fixture; document
  truthful differences.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Library is independently routable and shows only validated local content.
- Missing configuration or samples produce actionable local recovery rather
  than fake catalog data.
- Browsing never mutates the project, source library, or registry.

## Out of scope

Stores, downloads, remote content, user uploads, favorites, quotas, automatic
sample installation, project insertion, new sound formats, or a second sound
library.
