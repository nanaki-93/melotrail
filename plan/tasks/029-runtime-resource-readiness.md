# Task 029 — Runtime Resource Locator and Readiness

## Goal

Make the desktop app locate the starter sound library independently of its
working directory and show an accurate, actionable readiness state before
previewing or building.

## Requirements

- Introduce one engine-level, injectable sound-library locator/configuration
  boundary. It returns an absolute normalized path plus its source: explicit
  `MUSIC_SOUNDS_ROOT`, validated desktop preference, development discovery, or
  packaged/local-install location. Do not scatter `Path.of("sounds")` defaults.
- Pass the resolved locator to all registry loaders, renderer instances,
  generators, transition generation, previews, and build composition. Preserve
  the existing registry/license/sample/path-traversal validation.
- Add an explicit user-managed desktop preference for a sound-library path; it
  stores only the selected absolute root after validation and may be cleared.
  Environment configuration wins over the preference. A failed candidate must
  not replace a last-known-good configuration.
- Expand `RuntimeReadiness` into independently testable worker, transcription,
  sound-library, renderer, and playback/output-device states. Renderer readiness
  is not green merely because an executable exists: it must also state which
  validated library it will render.
- Add a compact readiness area and recovery action in the desktop header/status
  panel. When the library is absent, show the checked path, setup instruction,
  and folder-selection action. Never show the raw `/sound`/`sounds` exception as
  the only explanation.
- Preflight preview and Build Song with these typed states. Disable only the
  affected actions: source-audio playback must not require a renderer.
- Update package/resource setup and `sounds/README.md` so a user can install or
  select the 25 approved samples after a clean checkout/package. Do not add
  network downloads or duplicate `instruments/`.

## Tests

- Locator precedence, normalization, invalid paths, CWD-independent discovery,
  and no fallback after an invalid explicit environment value.
- Registry validation remains unchanged for a good library and still rejects
  missing/escaped/invalid samples.
- Readiness combinations and view-model enable/disabled reason tests.
- Desktop UI test for the visible library status and recovery action.

## Acceptance criteria

- Launching from a directory without `sounds/` no longer produces an opaque
  library-root exception; it resolves the configured library or explains how to
  select/install one.
- Preview/build receive the same library configuration and use no implicit CWD.

## Out of scope

Downloading instruments, changing sample licenses, bundling arbitrary third
party libraries, or changing musical rendering.
