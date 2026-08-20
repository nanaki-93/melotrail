# Melotrail production sound library

`instruments.json` is the checked-in registry-v3 catalog for the local production library. It contains 249 stable, user-facing SFZ presets from the imported CC0 packs, organized beneath `libraries/` by source pack:

- Karoryfer: Emily Guitar, Fashion Bass, Gogodze Phu II, Bigcat Cello, Pasta Bass, Shiny Guitar, and Sneaky Bass
- Versilian: VCSL 1.2.2 RC, VCSL Keys, and Virtuosity Drums 0.925

The samples and vendor source files remain local, are excluded from Git, and are never downloaded, copied, or changed by the application. The catalog supports native 44.1 kHz and 48 kHz WAV/FLAC assets. SFZ includes, macros, inherited regions, and vendor-relative sample paths are validated before a preset becomes available to the app.

## Selection and rendering

Every preset has a stable ID, musical roles, category, source-library provenance, and license metadata. A small curated automatic pool is used for default role resolution; the remaining presets are `manual-only` and become eligible when their stable ID is pinned in Arrange. The desktop Library page exposes all validated entries for browsing and filtering.

Arrangement approval records stable IDs and provenance for every structure occurrence. Rendering revalidates the registry and uses those approved assignments, never a filesystem path from project data.

## Maintaining the import

Run this local-only command after adding the ten source folders to `sounds/production/`:

```bash
python3 tools/curate_sound_library.py --apply
```

It moves the known pack roots into `sounds/libraries/`, removes Finder metadata, creates the Shiny Guitar compatibility wrapper, and regenerates `instruments.json`. It refuses to overwrite an already normalized pack.

Select this `sounds/` directory in the desktop Settings panel, or set `MUSIC_SOUNDS_ROOT` to its absolute path. The renderer requires a locally installed `sfizz_render`; Melotrail validates its output and publishes PCM-24 stems without mutating the library.
