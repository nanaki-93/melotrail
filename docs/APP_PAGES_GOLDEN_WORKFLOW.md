# App-pages visual acceptance

Task 090 uses [`plan/pictures/App-pages.png`](../plan/pictures/App-pages.png)
as the visual reference. The Compose fixtures are in-memory: they do not read
projects, contact the worker or model, use the clock, open an audio device, or
require a renderer or sound library.

## Generate deterministic captures

Run the page-fixture tests from the repository root:

```bash
./gradlew :desktopApp:test --rerun-tasks --tests 'app.melotrail.desktop.WorkspaceScreenTest.deterministic*fixture*'
```

The test writes these captures under `desktopApp/build/reports/`:

- `task-090-overview-capture.png`
- `task-090-import-capture.png`
- `task-090-structure-capture.png`
- `task-090-arrange-capture.png`
- `task-090-mix-master-capture.png`
- `task-090-video-preview-capture.png`
- `task-090-export-capture.png`

The focused Import, Structure, Arrange, Mix & Master, Video Preview, and Export
fixtures also write 50%-opacity reference overlays in the same directory. They
are review artifacts, not source-controlled release assets.

Task 101 additionally records the complete window for every current destination
against the numbered references in `plan/pictures/UI/`:

```bash
./gradlew :desktopApp:test --rerun-tasks --tests 'app.melotrail.desktop.WorkspaceScreenTest.Task 101 records a complete 1536 by 1024 window for every destination'
```

It writes these full-window 1536 × 1024 captures under
`desktopApp/build/reports/`:

- `task-101-overview-capture.png`
- `task-101-import-capture.png`
- `task-101-structure-capture.png`
- `task-101-arrange-capture.png`
- `task-101-mix_master-capture.png`
- `task-101-library-capture.png`
- `task-101-video_preview-capture.png`
- `task-101-export-capture.png`
- `task-101-settings-capture.png`

## Review procedure

At the reference 100% viewport, overlay each capture on its matching region in
`App-pages.png`. Major shell, sidebar, card, list, form, preview, timeline, and
footer edges must be within 4 px. Use an 8-RGB-value tolerance only for raster
pixel-diff triage; review text, icons, olive selection, focus, hover, disabled,
error, empty, and long-name states by eye.

Repeat the same fixture review at 100%, 125%, and 150% for wide, medium, and
narrow windows. Include an empty project, long project/source names, an
operation failure, unavailable local dependencies, stale artifacts, a pending
draft approval, and a project with at least five sections. Narrow layout must
retain one navigation control, one project selector/settings entry, one active
page, one feedback surface, and one shared playback session without
page-level horizontal scrolling. Timeline and chip strips may scroll internally.

Before accepting a change, run:

```bash
./gradlew test :desktopApp:test :desktopApp:build
```

Record the exact command result and the generated report paths with the change.
The captures prove deterministic rendering only; the human overlay and keyboard
review remains required for visual acceptance.
