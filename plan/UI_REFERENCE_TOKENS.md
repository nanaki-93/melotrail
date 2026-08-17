# Task 077 UI reference tokens

Measured from `plan/UI.png` at 1536 × 1024 and 100% scale. Values are held in
`MusicWorkspaceTokens.Reference`; acceptance tolerates ±4 px on major edges.

| Element | Reference value |
| --- | ---: |
| outer padding | 16 px |
| header | 56 px |
| left / center / right columns | 254 / 698 / 533 px |
| column gap | 12 px |
| footer | 104 px |
| wide / medium breakpoints | 1180 / 760 dp |
| panels | 12–16 px radius, 1 px #253845 border at 78% opacity |
| controls | 48 dp minimum target; 6–8 px radii |
| lane colors | piano #59CCC4, bass #86C979, drums #F0B356, pad #AB91EB, strings #F08262 |

The right-side travel content is a deterministic visual-only placeholder. It
contains no project metadata, clock, network request, weather, location, map,
or destination data.

## Overlay/diff workflow

1. Generate the deterministic Task 082 fixture at 1536 × 1024 and 100% scale:

   ```bash
   ./gradlew :desktopApp:test --rerun-tasks --tests 'app.melotrail.desktop.WorkspaceScreenTest.reference center fixture captures the deterministic workstation at 1536 by 1024'
   ```

   The test writes `desktopApp/build/reports/task-082-reference-1536x1024.png`. Its state
   is entirely in-memory and has no local project files, network, clock, audio
   device, renderer, or worker dependency.
2. Place that capture over `plan/UI.png` at 50% opacity. Check outer panel
   edges, header/footer height, and three-column boundaries against the table.
3. Pixel-diff colors using a documented tolerance of 8 RGB values. Review text
   and vector icons by eye; do not use the diff as evidence of typography.
4. At 100%, 125%, and 150%, repeat the capture/review with long project and
   part names, an empty project, and a failure banner. Also inspect medium and
   narrow widths: there must be one navigation row, no duplicate transport,
   and no page-level horizontal scrolling.
