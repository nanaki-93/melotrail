# Task 100 — Settings Destination

## Goal

Replace the interim/dialog-only settings experience with a focused Settings
destination inspired by `../pictures/UI/10-settings.png`, containing only real,
locally persisted, validated configuration and runtime information.

## Dependencies

- Task 099 accepted.

## Requirements

- Replace Task 092's interim Settings body with one stable typed destination.
  Wide/medium layouts use the page/context-rail structure; narrow may use a
  full-height sheet while keeping one Settings semantics root.
- Organize only supported settings into clear sections/tabs, including:
  - sound-library root choose/clear/validate/refresh;
  - renderer, samples, worker, audio-output, and optional exporter readiness
    where current runtime models provide it;
  - actionable local recovery guidance;
  - actual application version/build/platform information when available from
    local build/runtime metadata;
  - existing safely persisted preferences and migration state.
- Preserve the validated locator and preference-store boundaries. Settings may
  retain paths/preferences only; project and audio data remain canonical in the
  selected project/library locations.
- Choosing a new root publishes the preference only after validation according
  to the current contract. Cancel or failure preserves the previous valid
  setting and provides visible feedback.
- Make clear/reset actions explicit, narrowly scoped, and confirmed when they
  remove a meaningful preference. Never implement a broad Reset All action
  without a typed list of recoverable targets.
- Keep Settings reachable from the shared gear and navigation. Opening,
  changing, dismissing, or leaving Settings must preserve the prior project,
  workflow state, and shared playback session.
- Omit telemetry, analytics, crash uploads, update checks, changelog/community
  links, autosave, backups, themes, language, model downloads, audio-device
  selection, and notification toggles unless separate application and
  persistence contracts already exist. Do not show decorative working toggles.
- Match the reference multi-column card hierarchy, tab treatment, selected
  navigation, About/context rail, typography, and responsive stacking through
  shared components.

## Verification

- Settings/application tests cover current valid root, invalid root, missing
  registry/SFZ/samples, choose cancel, choose success, validation failure,
  clear/confirm, refresh, environment override precedence, and former
  preference-node migration.
- Compose tests cover ready, unconfigured, partial, failed, long path, mutating,
  runtime unavailable, and About information states.
- Navigation tests prove gear entry, direct Settings route, Escape/back,
  preserved originating destination, and preserved playback/project state.
- Assert unsupported telemetry/update/cloud/autosave/theme/device controls are
  absent.
- Assert no settings action writes project/audio data or introduces a CWD
  dependency.
- Capture and overlay a full 1536 × 1024 fixture against
  `../pictures/UI/10-settings.png`; document omitted unsupported sections.
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Settings follows the reference structure while every visible control has
  real local behavior.
- Sound-library and runtime recovery remain available without competing state
  or duplicated persistence.
- Navigation and configuration changes do not disrupt the active project or
  playback session.

## Out of scope

Telemetry, cloud services, update infrastructure, crash upload, model/sample
downloads, new autosave/backup systems, internationalization, theming, audio
device management, or arbitrary preference reset.
