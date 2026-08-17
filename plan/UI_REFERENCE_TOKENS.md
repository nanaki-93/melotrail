# Task 092 shared-shell reference tokens

Measured from the 1536 × 1024 page references in `plan/pictures/UI/` at 100%
scale. Values are held in `MusicWorkspaceTokens`; acceptance tolerates ±4 px
on major edges. The shell uses a dark-purple interpretation while retaining the
reference hierarchy; it does not reuse flattened screenshot artwork.

| Element | Reference value |
| --- | ---: |
| outer padding | 16 px |
| top bar | 64 px |
| project / page / context rails | 248 / flexible / 288 px |
| column gap | 12 px |
| page inset | 24 horizontal / 20 vertical px |
| wide / medium / narrow breakpoints | 1180 / 760 / below 760 dp |
| panels | 12–16 px radius, 1 px #4D3B66 border |
| controls | 48 dp minimum target; 6–8 px radii |
| selected/accent | #35244E / #C7A6FF |
| lane colors | piano #59CCC4, bass #86C979, drums #F0B356, pad #AB91EB, strings #F08262 |

The local artwork slot is a deterministic purple gradient placeholder. It
contains no project metadata, clock, network request, weather, location, map,
or destination data.

## Overlay/diff workflow

1. Generate the deterministic Task 092 shell fixtures at wide, medium, and
   narrow sizes:

   ```bash
   ./gradlew :desktopApp:test --rerun-tasks --tests 'app.melotrail.desktop.WorkspaceScreenTest.*shell*'
   ```

   The test writes `desktopApp/build/reports/task-092-{wide,medium,narrow}-shell.png`. Its state
   is entirely in-memory and has no local project files, network, clock, audio
   device, renderer, or worker dependency.
2. Compare the wide capture to the individual page reference's shell. Check
   outer panel edges, top bar, rails, and page boundaries against the table.
3. Pixel-diff colors using a documented tolerance of 8 RGB values. Review text
   and vector icons by eye; do not use the diff as evidence of typography.
4. At 100%, 125%, and 150%, repeat the capture/review with long project and
   part names, an empty project, and a failure banner. Wide uses the top bar
   navigation, medium the compact project rail, and narrow one chooser; there
   must be no duplicate transport or page-level horizontal scrolling.
