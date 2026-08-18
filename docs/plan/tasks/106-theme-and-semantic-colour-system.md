# Task 106 — Theme and Semantic Colour System

## Goal

Replace the current purple-first workspace palette with an accessible dark
cinematic theme derived from `docs/pictures/UI/example.png`, while giving
different kinds of information distinct semantic colours.

## Dependencies

- Task 101 accepted.

## Requirements

- Update `MusicWorkspaceTokens`, Material colour roles, and shared component
  colour helpers as one design system. Use a near-black/navy canvas, dark
  layered panels, teal primary/focus/active states, and restrained warm accent
  colours informed by the reference image.
- Preserve and standardize distinct lane colours for piano, bass, drums, pad,
  and strings. Apply these consistently to lane badges, arrangement/timeline
  blocks, meters, and legends, with a text/icon equivalent.
- Define semantic state roles for success/ready, warning/review, error/blocked,
  information, disabled, selected, progress, and focus. Replace ad-hoc direct
  colour use in desktop UI with these roles where applicable.
- Meet readable contrast for normal text and interactive states on their actual
  surfaces. Focus, selected state, and disabled state must remain distinguishable
  without relying on hue alone.
- Do not embed or reproduce screenshot-specific scene/travel artwork. Keep the
  deterministic local scene placeholder free of project metadata.
- Update `docs/plan/UI_REFERENCE_TOKENS.md` and the palette fixture to match
  the accepted values and describe the visual comparison process.

## Tests

- Token/component tests for Material role mapping, lane mapping, and semantic
  state mapping.
- Compose visual captures at wide, medium, and narrow breakpoints plus manual
  contrast review at 100%, 125%, and 150% scale.

## Acceptance criteria

- The desktop feels visually aligned with the supplied reference’s hierarchy,
  without copying its content, and operational/instrument information is easier
  to scan through consistent semantic colour.

## Out of scope

- User-selectable themes, remote imagery, or a redesign of workflow behaviour.
