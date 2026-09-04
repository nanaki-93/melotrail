# Mockup-faithful MIDI workspace redesign

Status: implementation plan; no redesigned UI or video implementation is claimed

Requested: 2026-09-05

Authority: a visual delivery workstream under [PLAN.md](../../PLAN.md), not a
second product roadmap. Implement with [ordered tasks](UI_MOCKUP_TASKS.md) and
the [serial execution prompt](EXECUTE_UI_MOCKUP_TASKS_PROMPT.md). Record work in
the [UI execution log](UI_MOCKUP_EXECUTION_LOG.md).

## 1. Outcome

Make Melotrail look as close as practical to the nine supplied pictures in
`docs/pictures/UI`, while every visible working control represents an actual
MIDI Core capability. This is a reconstruction of their composition, density,
surface treatment, typography, iconography, and musical timeline—not another
generic dark theme applied to the current forms.

Keep Kotlin/Compose Desktop, the six destinations, deterministic generation,
immutable melody/candidates, and accepted-only Logic Pro export. Preserve the
whole-song-first flow already implemented by MC-048D–MC-048H.

Video creation is a **future feature specification**, not a hidden seventh page,
disabled render button, or revived audio pipeline. Its proposed function and
separately gated implementation backlog are in
[Future video creator](FUTURE_VIDEO_CREATOR.md).

## 2. What was inspected

The planning baseline is commit `bdf0080` plus the existing dirty working tree,
not a clean release. MC-048H is recorded DONE. MC-048I has uncommitted automated
preparation and is AWAITING_HUMAN; five genuine usability sessions are still
missing. Other Chords/validation work and a compiler-session deletion must be
preserved. These are observations, not authorization to commit those changes.

Inspected all nine reference PNGs, all required product contracts, the task
ledger, current generated wide Project/Structure/Arrange/Review captures,
focused shell/theme/song-map/Arrange implementation, workspace intents/state,
audition position handling, and screenshot-test behavior. Generated captures
are evidence of the present checkout, not accepted design baselines.

### Current implementation and gaps

| Area | Real owner / existing behavior | Work needed |
| --- | --- | --- |
| Shell | `MidiCoreWorkspaceShell.kt`; six routes, wide/compact layout, one dock | Replace large numbered pills, flat full-height rails, weak header contrast, and repeated context with the reference's compact rail/header/panels |
| Theme | `WorkspaceTheme.kt`; shared target and transitional legacy tokens | Explicit rectangular control shapes, darker violet CTA fill, lighter typography, thin borders, stable fonts/icons, distinct role palette |
| Project | `MidiCoreProjectPage.kt`; create/open/last project/readiness | Dashboard metrics and musical overview; no invented recent-project collection |
| MIDI | `MidiCoreMidiPage.kt`; immutable import and track facts | One-file import well, dense source/track table, real read-only MIDI view, findings inspector |
| Authority | `MidiCoreStructureHarmonyPage.kt`, `MidiCoreAuthorityDrafting.kt` | Timeline plus compact editable section rows and contextual harmony; preserve BPM/bar/progression semantics |
| Arrange | `MidiCoreArrangePage.kt`; five styles, preview, draft, scoped repair | True wide main/inspector layout, style cards rather than full-width buttons, visible full-draft CTA, real role lanes |
| Review | `MidiCoreReviewPage.kt`; draft play/use, undo, exceptions | Arrangement-first comparison/review surface without repeated large action cards |
| Song map | `MidiCoreSongMap.kt`; occurrence/status projection | Replace tall rounded buttons; align bar scale with lane cells; separate selected, looped, playing, stale, and accepted states |
| Playback | `MidiAudition.kt`, adapter, workspace state | Current position is sampled at transport boundaries; add a real bounded position observation for a moving playhead, not a fake animation |
| Export | `MidiCoreExportPage.kt`; immutable project-owned package, reveal, guidance | Reference-like package summary/file grid/action footer; no fictitious format/destination options |
| Visual evidence | `MidiCoreVisualRegressionTest.kt`, `MidiCoreFocusedWorkflowTest.kt` | Current tests write PNGs and check existence/dimensions/semantics, not accepted-image similarity; add deterministic fixtures and actual comparison failures |

The current source does not expose a reusable read-only note-lane view for all
source/draft/accepted scopes. Add a verified application read model; do not
parse MIDI inside a composable or start playback merely to obtain notes.

## 3. Reference-to-product mapping

Reference images are 1536 × 1024. There is no `05-*.png` and no dedicated Review
mockup; do not invent an unseen reference. Review inherits Arrange's layout.

| Supplied picture | Preserve closely | Adapt to real Melotrail functions |
| --- | --- | --- |
| `01-dashboard-overview.png` | Current-project heading, five metric tiles, section strip, role overview, right summary/action stack | Project dashboard: sections, four musical roles when available, duration, confirmed BPM/key; one last-opened project, real readiness and MIDI evidence |
| `02-import.png` | Dashed import well, compact table, status badges, right findings/settings hierarchy | A single `.mid/.midi` source; automatic melody protection; track/channel facts and validation. No audio upload, processing toggles, multi-file queue, Clear All, or destructive source replacement |
| `03-structure.png` | Colored rectangular sections, row/table density, compact row actions, selected-section inspector | Named whole-bar rows, exact total, project-level BPM/meter/key, per-occurrence progression and invalidation preview; no per-section tempo/key or inferred structure |
| `04-arrange.png` | Dominant timeline, aligned colored role lanes, top-right action, narrow inspector, compact transport | Melody/Chords/Bass/Drums only; MIDI notes/hits, five existing styles, Create full draft, scoped progress/repair. No mixer sliders, instruments, AI settings, transitions, Pad/Strings/Lead/FX |
| `06-mix-master.png` | Thin panel edges, icon/label rhythm, compact controls | Component styling only; no Mix/Master page, EQ, gain, pan, bus, LUFS, or meters |
| `07-library.png` | Bordered gallery cards, selected card state, compact descriptive tags | Arrange style gallery and Review candidate cards; no sound catalog, favorites, downloads, fake search, or sample previews |
| `08-video-preview.png` | Large visual stage, scene strip, settings inspector, transition cards | Future video specification only; current right-side preview frames show MIDI evidence, never pretend to play a video |
| `09-export.png` | Summary tiles, grouped settings/file facts, location row, prominent action footer, right checklist | One complete MIDI package containing complete song, role files, manifest; immutable destination and Logic Pro instructions. No audio/video switches, bitrate, loudness, artist/release metadata |
| `10-settings.png` | Compact label/control rows, grouped panels, About/help treatment | Existing output-device options in the player; small contextual project/help details. No standalone Settings route or invented autosave, account, telemetry, model, or performance switches |

### Intentional visual differences

- Use the existing Melotrail identity, not “AI Music Workstation”, the mockup's
  account/avatar, or sample project names/data in production.
- Keep one navigation owner. The mockup duplicates routes in the header and
  sidebar; Melotrail uses the sidebar at wide sizes and a compact navigation
  surface at narrow sizes. The top band holds project context and real actions.
- Keep one persistent MIDI dock. The preview frame is not another transport.
- Replace waveforms with actual note rectangles/percussion hits in the same
  lane geometry. A visualization is read-only; this is not a piano-roll editor.
- The right scene-sized frame becomes a protected-melody/selected-section
  visualization with real bar/chord context. Video imagery belongs to the
  separately approved future feature.
- A static atmospheric rail illustration is permitted as decoration only:
  muted below navigation, without a playback badge or implied video output.
  Use an owned/licensed asset, record provenance, and retain a gradient fallback.
  Do not extract the sample avatar, logo, or artwork from a screenshot as a
  production asset without verified reuse permission. This narrow decorative
  allowance does not permit art to replace musical evidence.
- Use existing recent-project support honestly: one last-opened entry until a
  separately scoped history feature is approved. Display unknown facts as “—”,
  not plausible sample values. Duration requires source PPQ and a known tempo;
  distinguish source suggestions from confirmed authority.

## 4. Design system and geometry

These are proposed target values based on visual inspection, not an assertion
of pixel measurements already performed. UI-001 measures and freezes the exact
values against the originals. Do not reuse the legacy `Reference` token values
as if they were correct: different mockups have different center/right widths.

### Reference-sized desktop: 1536 × 1024 logical pixels

- Top band: approximately 62–64 dp; logo/project context aligned with the rail.
- Left rail: approximately 220–228 dp; compact project summary, six 48-dp hit
  rows with a 38–40-dp visual selection treatment, optional decorative lower
  region, and honest local-app footer.
- Right inspector: approximately 330–366 dp on workflow pages; Project may use
  approximately 440 dp to reproduce the Overview balance.
- Main work area: flexible remaining width, normally around 860–910 dp on
  Arrange/Structure; 24–28 dp inner horizontal inset, 16–20 dp vertical gaps.
- Dock: approximately 72–88 dp high in wide layouts; outside page scroll. Main work area ends
  above it. Expanded options have a bounded, independently scrollable surface.
- Panels: 6–10 dp corner radius, subtle 1-dp border, low-contrast navy shading;
  no default Material pill silhouette for navigation, maps, or primary buttons.
- Section strip: roughly 76–96 dp high. Role lane: roughly 52–60 dp including
  spacing. Four role lanes, timeline and primary action fit in the first
  Arrange/Review viewport without opening advanced details.
- Compact visual controls: 32–40 dp with separate non-overlapping hit bounds
  of at least the existing 48 dp. No accessibility reduction to copy tiny icons.

### Starting palette and type targets

| Token | Proposed target | Usage |
| --- | --- | --- |
| Canvas | `#080F18` | Near-black blue workspace |
| Surface / raised | `#101923` / `#151E2A` | Subtle panel hierarchy |
| Border | `#26303E` | Hairlines rather than bright outlines |
| Primary fill / selected | `#594080` / `#2C2442` | Dark violet CTA with near-white text / navigation |
| Accent / focus | `#B18ADE` / `#D4B8FF` | Fine highlights and visible focus |
| Primary / secondary text | `#F1F2F4` / `#AFB3BE` | Neutral rather than uniformly lavender text |
| Melody / Chords / Bass / Drums | rose / olive / blue / amber | Stable role identity, always with text/icon |
| Section families | muted olive, amber, violet, rose, blue | Stable identity distinct from validation severity |

Choose final colors by sampling flat reference areas, then validate the actual
text/background pairs. Target 4.5:1 normal text and 3:1 large text and essential
control/focus boundaries; raise contrast where needed and document deviations.
Do not lower contrast solely to imitate low-contrast reference labels.

Use a bundled, redistributable sans family or a proven deterministic existing
font. Suggested scale: page title 24/30, project title 26/32, section title
14/20, body 13/18, metadata 11–12/16, tabular time/number labels. Avoid heavy
bold text everywhere. Record font license and fallback behavior. Use existing
Compose icon resources or owned vector icons; no emoji/Unicode glyphs whose
appearance varies by machine. Preserve the existing application icon.

### Responsive rules

| Width | Layout contract |
| --- | --- |
| 1440 dp and above | Full three-column composition, reference-proportional density |
| 1100–1439 dp | Narrower rail/inspector with sufficient main width; move inspector to an accessible drawer when it would crowd content |
| 720–1099 dp | One main column, compact navigation, contextual inspector drawer/disclosure, horizontal musical timeline; two-row dock at most |

Maintain the existing 1280 × 900 and 720 × 900 gates, and add the reference-size
1536 × 1024 gate. Exercise 1440 × 900, 1024 × 768, and a short 1280 × 720 window
for resizing. Geometry is in logical pixels; additionally test native macOS
display scaling. Never shrink the whole UI bitmap to fit. Tables may transform
to labeled rows and timelines may scroll; unrelated page content must not gain
horizontal overflow. Restore page scroll and selected context across navigation.

## 5. Page compositions

### Project

Project heading and one next-safe-action button; five compact factual metrics;
bar-proportional structure strip; read-only four-role overview when evidence
exists. Right: real project facts, readiness, and contextual shortcuts. Empty
state keeps the same shell but focuses on Create/Open. Move filesystem paths,
IDs, hashes, reload, and close into appropriate details/actions. Do not bury
create/open or conceal recovery messages.

### MIDI

One large import well and chooser before import. Only say “drop a MIDI file” if
native drop behavior is implemented and tested. After import, replace the well
with immutable source identity and an inspectable note-lane panel. Dense table:
track, channel, notes, range, duration, expression, protection status. Right:
blocking/advisory findings and the next safe action. The source-audition action
selects the shared dock; no duplicate play/pause/stop group.

### Structure & Harmony

Top section strip; a compact settings row for project BPM, meter and key;
ordered section rows containing name, bars, derived bar range, duplicate/move/
remove; selected-section progression and chord-window preview in context.
Preserve the existing exact-window behavior for unchanged progressions. Keep
unsaved drafts visible, show exact total mismatch and invalidation before save,
and preserve keyboard alternatives to any drag interaction. Add-section and
save actions must not scroll out of reach on the normal first viewport.

### Arrange

Header with **Create full draft** in the reference's top-right action position;
shared rectangular section strip and four aligned role lanes are the dominant
surface. Immediately reachable five-card style gallery, using the existing
Open Sky, Late Night, Steady Road, Rising Room, and Wide Bridge catalog labels
(resolve exact capitalization from the catalog). Selection starts the existing
ephemeral preview. No hidden click is required before previewing a style.

Right: selected section, all three generated-role states, current style,
Regenerate section and Adjust roles. Progress/cancel/retry replace the active
CTA area when appropriate. Put profile/pattern controls in explicit advanced
repair; don't copy nonexistent intensity/complexity sliders. A one-bar section
currently cannot host a two-to-four-bar style preview: explain this honestly
and keep valid draft generation/source audition available. Do not alter the
musical contract merely to fill a preview pane.

### Review

Use the same map/lanes and selected-section inspector as Arrange. Clear
“Draft” versus “Accepted arrangement” labels, one primary **Use this draft**
when valid, a secondary action selecting draft playback in the dock, and latest
batch undo when valid. Right-side exception details contain candidate choice,
semantic diff, findings, lock/reject/restore, and scoped repair. A comparison
never presents draft material as accepted or exportable. Do not invent a
generic undo/redo stack when only guarded draft acceptance undo exists.

### Export

Three summary tiles describe **Complete song**, **Individual roles**, and
**Manifest** as parts of one package, not selectable audio export modes. Main:
acceptance readiness, actual filenames and validation, immutable location,
single export action, completed snapshot/reveal-folder result. Right: authority
summary and Logic Pro instructions. Hashes are available in details; sample
rate, bitrate, audio format and video options are absent. Use the actual
project-owned destination and fresh-snapshot collision policy, not a fake Browse
or overwrite control. A future video specification is not a shipped feature.

## 6. Implementation boundaries

- Reuse current lifecycle/import/authority/generation/review/export use cases.
  Do not redesign persistence or change generator output for a visual task.
- Add one immutable, verified MIDI visualization read model through application
  services. UI projection can cache by source/candidate hashes, authority and
  revision. Verify stale/missing evidence before calling a lane current.
- Reuse one tick-to-x/bar/time projection across map, lane, playhead and loop;
  all occurrences are half-open ranges `[start, end)`. The final end is an
  explicit stopped/completed state, not a second playing section.
- One audition owner samples the real output position while playing. Stop
  sampling on pause/stop/close/device loss; ignore old-session updates; bound
  publication/history. Never run one timer per page or invent playback progress.
- Prefer small target presentation files for theme, primitives, timeline,
  inspector, dock and fixture helpers. Do not add more unrelated responsibility
  to the 1,825-line workspace reducer or 827-line authority page.
- Legacy UI remains unreachable deletion scope. Its code and image-measurement
  tests are removed by MC-051, not revived for apparent screenshot similarity.
- Retain the nine requested `docs/pictures/UI` originals as design-only inputs.
  No production path depends on them. This explicitly revises their previous
  deletion disposition; other obsolete assets retain their cleanup owners.

## 7. Visual acceptance, not just screenshot generation

Two different comparisons are required:

1. **Reference fidelity review:** at 1536 × 1024, compare each implemented page
   beside its mapped original. Measure top/left/right geometry, control shapes,
   spacing, typography, palette, and focal content. Mark intentional MIDI
   substitutions explicitly. Raw whole-image pixel equality to the originals
   would incorrectly reward copying unsupported controls and artwork.
2. **Regression gate:** compare deterministic captures to versioned accepted
   MIDI-only baselines. Pin clock, IDs, project labels, data, fonts, scroll,
   density and playback position; remove temporary paths from visible fixtures.
   Tests must fail on real visual drift, not merely write another PNG.

UI-001 freezes measurement regions and budgets before implementation. Starting
budgets: major panel/rail boundaries within 8 logical pixels of the adapted
target; control/text-grid spacing within 4; explicit expected corner radii and
flat color samples. Text anti-aliasing allowance must be narrow, documented,
and independent of layout checks. Do not claim an unmeasured “95% match”.

For every page, score shell proportions, typography, surfaces/colors,
controls/icons, and focal composition from 1–5 against its reference. Final gate:
every dimension at least 4, no clipping/overlap/illegible primary text, all
functional/accessibility gates passing, and explicit user visual approval.
Human scores are not fabricated by an agent. Geometry and approved baseline
tests supply separate objective evidence.

The suite must retain actual/expected/diff output for failures and prove itself
with deliberate shifted-panel, wrong-radius, and wrong-color test cases.
Golden updates are reviewed design changes, never an automatic response to a
failure. All six pages need no-project/ready states at three key sizes; add
scrolled, busy, blocked, stale, device-loss, long-label and expanded-inspector
states where applicable. Capture Arrange at the top as well as after scrolling;
the current scrolled-only capture cannot prove a visible primary action.

## 8. Sequence, acceptance and scope gates

The new insertion is:

`MC-048H DONE -> UI-000 … UI-019 -> refreshed MC-048I observations -> MC-049 -> MC-050 … MC-060`

Existing MC-048I preparation is preserved and revalidated after the visual
changes. It is not marked DONE, deleted, or bypassed. This insertion deliberately
avoids evaluating musicians against a UI that is about to be replaced. Only one
implementation task is active; the paused human-evidence task is not an active
code task. This narrow ordering exception is recorded in the root plan and core
execution suite.

Run focused tests and `make test` / `make build` per executable task. For MIDI
export or workflow changes, run and record the applicable manual Logic Pro
checks required by AGENTS and the DAW contract before task completion; no
old compatibility result is silently promoted to evidence for a changed build.
Style/color-only work does not itself change MIDI semantics.

The UI execution prompt stops after UI-019 and reports the handoff to MC-048I;
it does not automatically start destructive cleanup or future video tasks.
The future VID sequence requires a separate explicit approval of its product
boundary and dependencies after MC-060. UI-018 delivers its design contract
only. Every completed UI task has exactly one reviewed task commit.

## 9. Risks and controls

| Risk | Control |
| --- | --- |
| Another palette-only redesign | Measured reference regions, page composition tasks, actual baseline comparison, final user visual gate |
| Matching fake mockup functionality | Mapping table and absence tests; no control without an intent and supported use case |
| Current uncommitted work mixed into task commits | UI-000 baseline/hunk ownership; stop on ambiguous overlaps; never blanket stage or stash |
| Shared theme breaks legacy readers | Inspect both caller graphs, scope target replacements, retain only still-owned legacy code until MC-051 |
| Tiny controls copied at expense of usability | Retain non-overlapping 48-dp hit targets, contrast and keyboard gates |
| Pretty but incorrect timeline | Shared exact scale, verified notes, half-open boundaries, real player clock, stale-state regressions |
| Mockup scenery dominates real evidence | Decoration confined to rail; central/right musical panels remain factual; video remains gated |
| Golden tests approve their own output | Fixed baseline update procedure, independent geometry tests, deliberately failing comparator cases, human review |
| UI work erases manual acceptance obligations | Preserve MC-048I/049/060 and current evidence; rerun required checks against redesigned build |

Completion means a reference-faithful, functional and approved MIDI UI, a complete
future video specification, and a clean per-task audit trail. It does not mean
that video rendering or overall MIDI Core product acceptance has shipped.
