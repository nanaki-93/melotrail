# Task 029 — Sound-Library Locator

## Goal

Remove working-directory-dependent sound-library discovery from the engine and
provide one injectable, validated absolute location to every consumer.

## Dependencies

- Task 028 completed.

## Requirements

- Add UI-neutral `SoundLibraryLocation` and `SoundLibraryLocator` types in the
  root module. A successful result includes the normalized absolute root and
  discovery source; a failure lists checked candidates without exposing secrets.
- Resolve in this order: nonblank `MUSIC_SOUNDS_ROOT`, an injected configured
  path, then explicit development/bundled candidates. An invalid environment
  override must fail clearly rather than silently use another library.
- Inject the resolved root into registry/renderer/generator/transition
  composition. Remove production uses of implicit `Path.of("sounds")`; tests may
  construct explicit fixture roots.
- Preserve all registry, license, sample, symlink, and path-traversal validation.

## Tests

- Precedence, normalization, missing/invalid candidates, invalid explicit
  override, discovery outside repository CWD, and consumer injection.
- Existing registry/renderer tests remain green.

## Acceptance criteria

- Engine rendering code receives one absolute validated root and never derives
  it from process CWD.

## Out of scope

Desktop preferences/UI, packaging assets, renderer readiness, or downloading
samples.
