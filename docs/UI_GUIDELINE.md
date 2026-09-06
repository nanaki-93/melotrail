# UI guideline

Owner: visual language, composition and interaction. This consolidates the old
workspace guideline and mockup redesign documents. [PLAN](../PLAN.md) defines
product behavior; [TASKS](../TASKS.md) assigns delivery. Existing shell/theme/
primitive work is retained and improved.

## References and interpretation

All nine PNGs in `pictures/UI` were inspected. They are 1536 × 1024 references.
There is no 05 image or dedicated Review image. Preserve originals; they are
design inputs, not executable UI fixtures or runtime artwork. Existing measured
values and source digests are retained in
[reference measurements](pictures/UI/reference-measurements.json).

| Reference | Adaptation |
| --- | --- |
| [01 Project](pictures/UI/01-dashboard-overview.png) | Compact factual metrics, section strip, real role overview and next action; current/last project only until history is implemented |
| [02 Import](pictures/UI/02-import.png) | One MIDI import well, dense source/track facts and findings; remove audio queue and processing switches |
| [03 Structure](pictures/UI/03-structure.png) | Colored sections, compact editable rows and chord-duration inspector; tempo/key stay project-wide |
| [04 Arrange](pictures/UI/04-arrange.png) | Dominant aligned MIDI lanes, section strip, top-right action and selected-section inspector; also the Review composition |
| [06 Mix/Master](pictures/UI/06-mix-master.png) | Control/border/icon density only; no mixer or production page |
| [07 Library](pictures/UI/07-library.png) | Style/candidate gallery cards and selection states; no sound library |
| [08 Video](pictures/UI/08-video-preview.png) | Optional companion preview stage and scene timeline only |
| [09 Export](pictures/UI/09-export.png) | MIDI package summary, file facts, destination/result and readiness; no codec/loudness controls |
| [10 Settings](pictures/UI/10-settings.png) | Compact grouped rows for actual playback/preferences; no fake account or model settings |

Retain Melotrail's identity. Use bars/beats instead of ambiguous screenshot time
labels. Replace waveforms with verified MIDI notes/hits. Role count is four:
Melody, Chords, Bass, Drums. A static owned rail illustration is optional, subdued
and explicitly decorative; never substitute it for musical evidence. TABI video
art belongs to the companion. Do not extract logos/avatars from screenshots.

## Layout targets

| Property | Target |
| --- | --- |
| Header | 64 dp wide, 56 dp compact; name plus concise operation/next action |
| Navigation | 224 dp at reference size, 196 dp at 1280; six destinations, one navigation owner |
| Reference inspector | Project 458, MIDI 381, Structure 390, Arrange/Review 332, Export 407 dp; shrink or disclose when main content is crowded |
| Content | 24 dp wide inset, 16 dp rhythm; main content is fluid |
| Panels/controls | 8/6 dp radii; subtle 1 dp borders; no pill defaults for primary controls |
| Collapsed player | Target 80 dp wide, maximum 112 dp compact, outside page scrolling |
| Section/role lanes | Arrange section strip about 64 dp; four role lanes about 52 dp each |
| Hit bounds | At least 48 dp for interactive targets, even if visual content is smaller |

At 1440+ use three columns. At 1100–1439 reduce rail/inspector or disclose the
inspector before squeezing the musical workspace. At 720–1099 use a main column,
compact keyboard-reachable navigation, contextual inspector disclosure and a
horizontally scrollable musical timeline. Never scale the whole interface as a
bitmap. Also test 1024×768 and 1280×720 resizing and native display scaling.

The first Arrange viewport at 1280×900 shows map, four lanes, style choice and
Create full draft. At compact width these remain reachable without hiding the
player or primary action behind it. No duplicate generic context rail alongside
a page's selected-section inspector. Persistent action rows must not trap focus
or obscure the final editable row.

## Visual language

Use current `WorkspaceTheme`, `WorkstationPrimitives` and `WorkspaceShellFrame`
as the foundation. Replace their remaining legacy branches, not the entire
system. The measured palette is canvas `#0B131E`, surface `#101923`, raised
surface `#151E2A`, border `#26303E`, primary `#594080`, selected `#2C2442`,
focus `#D4B8FF`, text `#F1F2F4` and secondary text `#AFB3BE`.

Role colors: Melody `#D77A9E`, Chords `#9CAA61`, Bass `#5A9DD2`, Drums `#D79A43`.
Keep role identity distinct from status/severity. Normal text contrast targets
4.5:1; large text and essential control/focus boundaries 3:1. Raise contrast
where a screenshot is less readable and document the deliberate deviation.

Typography: page title 24/30, section title 14/20, body 13/18, metadata 11–12/16,
with tabular time/bar values. Avoid uniformly bold text. The current system-sans
fallback needs a pinned font/rasterization setup for regression captures; bundle
a font only with its redistribution license. Use vector icons with accessible
names rather than platform-dependent emoji or icon-only unexplained buttons.

Reserve cards for selectable styles/candidates or genuine content groups.
Headings, short facts and sequential controls do not all need a separate card.
Primary actions have one consistent violet treatment. State includes text/icon,
not just color: not generated, planned rest, preparing, draft, accepted, stale,
needs attention. Technical IDs/hashes/paths belong in details.

## Page contracts

**Project:** empty Create/Open state; then current name, confirmed BPM/key/meter,
source-derived duration, section count and real arrangement progress. Factual
section/role overview leads to the next incomplete decision. No invented recent
projects, sample metrics or video stage.

**MIDI:** one chooser/import well before import; source identity, protected note
lane and compact track/channel/range/expression facts afterward. Findings
explain severity and the next correction. Distinguish source suggestions from
confirmed authority; show last note and file end when M03 adds those facts.
A “drop file” instruction exists only if working native drop is tested.

**Structure & Harmony:** section strip, compact settings row, editable section
rows, bar total and explicit chord-duration spans over the source. A selected
section owns its inspector. Display unsaved changes, phrase suggestions and
invalidation before confirmation. Reorder changes structure over the fixed
melody; it must never pretend to move the melody. Musical-purpose and plan edits
have clear ownership and confirmation.

**Arrange:** full song map and four aligned lanes dominate. Five musically
named style cards (Open Sky, Late Night, Steady Road, Rising Room, Wide Bridge)
start previews. Create full draft sits near that choice. One inspector shows
selected-section intent, role activity, findings and repair. Real progress,
cancel/retry replace the active action area. Plans and drafts are visibly
separate; advanced settings do not become first-run prerequisites.

**Review:** same timeline/selection; clear Draft or Accepted label; Play complete
draft, Use this draft and guarded Undo. Compare repairs with the same melody
and loop position. Findings link to exact bars and roles. Reject/restore/lock
and semantic diffs appear in exception details, not a second primary workflow.

**Export:** show accepted readiness and rests, immutable snapshot/package facts,
complete/role/manifest file list, real export progress/result and reveal action.
Instructions: import at song origin, confirm Logic tempo/meter behavior, assign
instruments and check role alignment. No disabled audio/video format selector.
Future companion launch appears only after its integration actually works.

## Interaction and data

One geometry function maps ticks to positions for bars, section boundaries,
chords, notes, playhead and loops. Notes come from verified application read
models. Use global song pitch coordinates or clearly labeled per-lane scales;
never imply a misleading melody/piano register comparison.

Clicking/keyboard-selecting a section keeps style and scroll, selects that
occurrence and loops the shared player. Looping a boundary-crossing source note
uses the documented audition policy; it does not modify the stored note.
Observe actual sequencer position; no decorative advancing clock. Changing page
does not reset a valid session. Closing a project or invalidating playback stops
safely. One-bar preview loops real content; do not fabricate extra source bars.

Play/pause, stop, position and loop stay visible. Output choice, boundary seek,
role mute/solo and device recovery live in an expandable panel. Keyboard focus
is visible, retained predictably after operations, and never lost behind a
modal used for ordinary generation/acceptance. Blockers name the affected scope
and next safe action. Unknown information is “—”, never plausible sample data.

## Visual proof

Capture all six pages in ready and relevant empty/blocked/error/progress states
at 1536×1024, 1280×900 and 720×900; inspect actual PNGs. Use a multi-section real
fixture with a draft, acceptance, rest and stale scope. Pin font, density, theme,
clock and seed. Store small approved target goldens in test resources; build
output owns actual/diff artifacts. Original mockups remain separate design inputs.

Check shell geometry within 8 dp and content alignment within 4 dp of the
adapted targets; inspect palette, radii and role-lane alignment independently.
Define image tolerances from the pinned renderer, with only narrow documented
text-edge allowances. Prove wrong color, 12-pixel panel shift, missing lane and
pill-radius regressions fail. Never auto-update expected images during tests.
User review scores hierarchy, fidelity and usability after seeing all six pages;
image writing or dimension checks alone are not visual approval.
