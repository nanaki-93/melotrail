# Task 024 — Compose Desktop Foundation

## Goal

Create a separate Compose Desktop JVM application that depends on the engine module and launches a testable dark workspace shell without starting Spring.

## Dependencies

- Tasks 022–023 typed services.

## Requirements

- Add `desktopApp` to Gradle settings as a thin JVM application subproject.
- Pin a verified stable Kotlin/Compose pair. First test the current stable Compose release with its required Kotlin upgrade against all engine tests; if incompatible, pin the newest compatible Compose version and document why.
- Apply Compose dependencies/plugins only where needed; do not move the engine to Kotlin Multiplatform.
- Add `DesktopMain`, service composition root, `WorkspaceViewModel`, immutable `WorkspaceUiState`, and explicit UI intents.
- Add a dark theme and reusable tokens/components inspired by `../../UI.png`: charcoal surfaces, teal focus/action color, compact cards, and instrument lane colors.
- Add an empty/start workspace, top project header, three-pane responsive shell, operation/status area, and placeholders for the functional panels.
- Keep filesystem dialogs behind an injected `DesktopFileDialogs` interface.
- Use `StateFlow`, `SupervisorJob`, and explicit dispatchers. Composables do not call services/files directly.
- Add `:desktopApp:run`, test, and current-OS packaging configuration. Packaging proof is completed in Task 028.

## Tests

- Gradle dependency/build compatibility and full engine tests.
- View-model initial/loading/open-failure state with fake services.
- Compose smoke test finds project header and core panel semantics tags.
- Manual launch at 1100×720 and 1440×900, including HiDPI scaling.

## Acceptance criteria

- `./gradlew :desktopApp:run` opens a native window without launching Spring or a browser.
- The desktop module calls only public application-service interfaces from the engine.
- CLI and server tasks continue to build and run.
- The UI shell is recognizably aligned with the supplied reference without including unrelated travel/video features.

## Out of scope

- Functional import, arrangement, build, mixing, playback, and cross-platform installer claims.
- Navigation or dependency-injection frameworks.

