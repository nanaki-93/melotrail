# Shared shell and Task 106 semantic colour tokens

Measured from the 1536 × 1024 page references in `docs/pictures/UI` at 100%
scale. Values are held in `MusicWorkspaceTokens`; acceptance tolerates ±4 px
on major edges. Task 106 changes the visual language to a near-black/navy
cinematic workspace with teal interaction states and restrained warm accents.
It follows the hierarchy of `docs/pictures/UI/example.png`; it does not copy
its travel content, illustration, metadata, or scene artwork.

| Element | Reference value |
| --- | ---: |
| outer padding | 16 px |
| top bar | 64 px |
| project / page / context rails | 248 / flexible / 288 px |
| column gap | 12 px |
| page inset | 24 horizontal / 20 vertical px |
| wide / medium / narrow breakpoints | 1180 / 760 / below 760 dp |
| panels | 12–16 px radius, 1 px #36515B border |
| controls | 48 dp minimum target; 6–8 px radii |
| canvas / surface / elevated | #07111A / #0C1821 / #10232D |
| selected / primary / focus | #123840 / #66D7C8 / #91F5E8 |
| warm accent | #F4BC64 |
| lane colors | piano #59CCC4, bass #86C979, drums #F0B356, pad #AB91EB, strings #F08262 |
| state roles | ready #7BDBA5; review #F0B356; blocked #FFB4AB; information #8AB4F8; disabled #89A0A4; progress #62CFE0 |

`MusicWorkspaceThemeShowcase` is the deterministic palette fixture. It exposes
canvas, layered panels, selected, primary, review, and blocked swatches along
with text/icon equivalents for ready, review, blocked, selected, and focus.
`instrumentLanes` gives piano, bass, drums, pad, and strings a stable colour,
name, and icon; badges, arrangement/timeline blocks, mix meters, and legends
must use that shared mapping rather than inventing another lane colour.

## Action reduction

Each page exposes one visible current action and its semantic-state explanation:
ready uses `ready`, review uses `review`, and blocked/stale recovery uses
`blocked`. Optional configuration and inspectable evidence use a labelled
**More options** disclosure with the normal control token and never replace the
primary action. The retained disclosures are workflow pages on Overview,
prepared-part choice on Structure, planner/instrument controls on Arrange,
listening/build options on Mix & Master, release options on Export, local
filters/layout on Library, timeline evidence on Video Preview, and runtime or
build details on Settings.

The local artwork slot is a deterministic navy placeholder. It
contains no project metadata, clock, network request, weather, location, map,
or destination data.

## Overlay/diff workflow

1. Generate the deterministic shell fixtures at wide, medium, and narrow sizes:

   ```bash
   ./gradlew :desktopApp:test --rerun-tasks --tests 'app.melotrail.desktop.WorkspaceScreenTest.*shell*'
   ```

   The test writes `desktopApp/build/reports/task-092-{wide,medium,narrow}-shell.png`. Its state
   is entirely in-memory and has no local project files, network, clock, audio
   device, renderer, or worker dependency.
2. Compare the wide capture to the reference hierarchy: near-black canvas,
   layered panels, teal focus/active states, and limited warm emphasis. Do not
   compare or reproduce the reference's scene content.
3. At 100%, 125%, and 150%, review normal text and every interactive state on
   its actual surface. Confirm selected has both a distinct surface and border,
   focus has a visible focus colour, and disabled uses both muted text and a
   muted surface rather than hue alone. The token tests enforce at least 4.5:1
   for normal semantic text and 7:1 for primary text on canvas.
4. Repeat the wide, medium, and narrow review with long project and part names,
   an empty project, and a failure banner. Verify lane name/icon equivalents in
   addition to colour. Wide uses the top bar navigation, medium the compact
   project rail, and narrow one chooser; there must be no duplicate transport
   or page-level horizontal scrolling.
