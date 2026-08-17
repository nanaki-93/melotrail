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

1. Launch a deterministic workspace fixture at 1536 × 1024, 100% scaling.
2. Capture a PNG without local project paths, dependency availability, device
   data, or time-dependent content.
3. Place the capture over `plan/UI.png` at 50% opacity. Check outer panel
   edges, header/footer height, and three-column boundaries against the table.
4. Pixel-diff colors using a documented tolerance of 8 RGB values. Review text
   and vector icons by eye; do not use the diff as evidence of typography.
