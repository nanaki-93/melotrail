# UI-001 reference-to-target region map

This map turns the nine design references into six MIDI-only page targets. It
uses the native 1536 × 1024 visual measurements in
[UI-001-reference-measurements.json](UI-001-reference-measurements.json).
Reference imagery and names are not product content, runtime assets or target
goldens.

## Shared shell

At 1536 × 1024, the target is a 64-pixel top band, a 224-pixel project rail,
24-pixel content inset, 16-pixel column/gap rhythm, page-specific inspector and
80-pixel persistent dock. The source images place their header boundary at
62 pixels and rails from 221 to 228 pixels; the target rounds those measurements
to the above reusable logical geometry. The dock is outside the scrollable page,
even though the source images embed a transport in different page panels.

At 1280 × 900, retain a three-column shell with a 196-pixel rail and a 320-pixel
maximum inspector (352 for the Project summary only); never shrink the full UI
bitmap. At 720 × 900, use 56-pixel top band, 16-pixel content inset, compact
navigation, inspector drawer/disclosure and 96-pixel two-row-at-most dock. The
musical timeline may scroll horizontally; ordinary page content may not.

## MIDI Core pages

| Target page | Source geometry preserved | MIDI-only focal content | Explicit substitutions |
| --- | --- | --- | --- |
| Project | `01`: 228-pixel rail, 458-pixel right summary, metric strip and structure/role overview | Current project title, five factual metric tiles, bar-proportional structure, truthful role evidence and readiness inspector | One honest last-opened project at most; no avatar, artwork, fake recent projects, video stage, track count beyond source facts, or audio duration claim. |
| MIDI | `02`: 223-pixel rail, 381-pixel inspector, central import well and dense table | Single SMF import, immutable source card, verified note lane, track/channel facts and findings inspector | No audio import, process toggles, multi-file queue, Clear All, source replacement or unavailable drag/drop claim. |
| Structure & Harmony | `03`: 228-pixel rail, 390-pixel inspector, 98-pixel section strip and compact table | Whole-bar occurrence strip, BPM/meter/key settings row, compact section table and exact chord-window inspector | No per-section tempo/key, inferred structure, AI suggestion, fake preview image or unimplemented drag interaction. |
| Arrange | `04` plus `07`: 222-pixel rail, 332-pixel inspector, 64-pixel section strip, 52-pixel lanes, compact gallery and top-right CTA | Four factual Melody/Chords/Bass/Drums lanes, five named styles, Create full draft, selected-section progress/repair inspector | No mixer, instrument selector, audio waveform, Pad/Strings/Lead/FX, intensity sliders, AI settings, scene artwork or second transport. |
| Review | `04` is the declared source because no dedicated Review picture exists; preserve its 332-pixel inspector and timeline hierarchy | Same map/lanes, explicit Draft versus Accepted label, Use this draft, guarded undo and contextual exception detail | The reference’s Generate Arrangement becomes Use this draft; draft review never appears accepted/exportable and uses the shared dock only. |
| Export | `09`: 228-pixel rail, 407-pixel inspector, compact summary tiles and 82-pixel action footer | Complete-song/roles/manifest package summary, readiness/file table, immutable destination, export result and Logic checklist | No audio/video mode, bitrate, sample rate, browse/overwrite fiction, release metadata, preview scene or video option. |

## Component-only sources

`06-mix-master.png` supplies only tight border, panel, icon/label and compact
control rhythm. `07-library.png` supplies bordered selected-gallery-card
composition for arrangement styles and Review candidates. `10-settings.png`
supplies grouped compact control rows for existing player/contextual options; it
does not create a Settings route. `08-video-preview.png` remains input to the
future-only video specification and supplies no present runtime control.

## Objective checks used by UI-017

- Shell partitions are within 8 logical pixels, and content/text-grid anchors
  within 4 pixels, of the documented responsive target.
- Flat canvas/surface/border/primary/text samples are compared independently;
  opaque samples allow an absolute per-channel delta of 8 (12 for a border
  anti-alias sample).
- Panels use 8-pixel and compact controls 6-pixel corner radii, each within one
  pixel. A Material-pill silhouette fails regardless of image-diff allowance.
- Text rasterization has only a one-pixel declared glyph-perimeter allowance;
  it cannot mask geometry, color, border or control changes.
- Versioned expected target captures are distinct from these references. A
  12-pixel panel shift, wrong primary color, wrong radius and implicit baseline
  update each receive a deliberately failing comparator test.

The palette/type starting values, responsive targets and complete machine-
readable policy are part of the companion JSON. UI-002 may introduce a bundled
font only after recording its redistributable license and deterministic fallback.
