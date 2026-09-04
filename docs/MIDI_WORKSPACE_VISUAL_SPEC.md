# MIDI workspace visual specification

Status: existing MIDI workspace contract plus planned mockup-faithful revision;
UI-000–UI-019 are not yet implemented. MC-048I's observed-musician gate remains
pending and must use the redesigned build after that insertion.

Authority: visual language and visual acceptance for the Compose Desktop MIDI
workspace

## 1. Purpose

Melotrail is a focused MIDI-arrangement workstation. Its desktop visual design
makes current musical authority, candidate decisions, and export safety easier
to understand; it does not imitate a DAW or revive the rejected audio product.

The retained images under `docs/pictures/UI` are the user's design references.
Root Plan 7.7 requires a close match to their composition, density, typography,
compact rectangular controls, panel details and musical timeline, not merely
their dark mood. The [detailed adaptation](plan/UI_MOCKUP_REDESIGN_PLAN.md)
maps every supplied image to real functions. Their account/branding, audio
waveforms, sound library, mixer, mastering, publishing and settings workflows
are not product authority. Video is a separately gated future specification.

## 2. Visual foundations

- Use a deep navy/near-black canvas with layered, opaque dark surfaces and
  restrained hairline borders.
- Use a violet primary accent for the active destination and primary actions.
  Use warm highlights and role colours only as secondary semantic cues.
- Pair every colour state with text, iconography, or position. Selection,
  warnings, blockers, progress, and acceptance must be understandable without
  colour perception.
- Use a compact type scale, an 8dp-based spacing rhythm, and deliberate panel
  hierarchy. Avoid turning every field into an equally elevated card.
- Use actual MIDI evidence: note events, section durations, chord windows,
  candidate findings, hashes, and export files. Never use an audio waveform,
  scenic image, simulated mixer meter, or video control as a visual substitute.
  An owned/licensed static rail illustration may be purely decorative, with
  provenance and a gradient fallback; it must not displace evidence or imply
  video playback. No screenshot art is automatically a reusable production asset.

## 3. Workspace shell

At wide desktop sizes, show a compact project header, a persistent six-item
navigation rail, a fluid work area, and a contextual inspector when that
context materially helps the current decision. At smaller widths, move context
into the page and preserve an ordered, keyboard-accessible navigation path.

One compact player dock belongs to this shell, outside page scrolling at both
sizes. It shows the current target and section, source/current/accepted target
switches where valid, play/pause, stop, position, and loop. The current target
may be an ephemeral arrangement-style preview. Output, boundary seek,
mute/solo, and recovery expand from the dock rather than duplicating controls
in MIDI or Review.

The only destinations are Project, MIDI, Structure & Harmony, Arrange, Review,
and Export. Device and export preferences remain small contextual dialogs, not
top-level pages.

## 4. Page composition

| Destination | Dominant evidence | Supporting context |
| --- | --- | --- |
| Project | project/readiness summary and next safe action | location, recent projects, authority facts |
| MIDI | immutable-source import and track/channel facts | MIDI-event preview, findings, audition |
| Structure & Harmony | ordered section timeline and chord windows | bar totals, authority validity, invalidation impact |
| Arrange | shared song map -> named style gallery -> instant bounded MIDI preview -> complete-draft action | selected-section inspector, scoped draft progress, contextual repair, advanced role adjustment |
| Review | shared song map -> play complete draft -> use this draft | selected-section lifecycle/diff/repair evidence, latest-batch undo, and Export readiness |
| Export | readiness checklist and immutable package result | files, hashes, validation, Logic Pro guidance |

Section and role views may use a compact MIDI timeline or piano-roll-style
event display only when it is computed from the current project state. Visual
decoration must not imply that Melotrail renders audio or owns instrument
selection.

## 5. Interaction and accessibility

- Primary action, blocker, selected scope, asynchronous progress, cancellation,
  and completion have one clear visual treatment per state.
- Interactive controls expose stable labels and semantics, maintain visible
  keyboard focus, and meet the workspace minimum hit target.
- Text, focus outlines, selected states, and disabled-action explanations must
  remain legible at wide and compact desktop widths.
- Long tables and timelines may scroll deliberately; page layout must not hide
  critical candidate or export information behind clipped panels.
- Do not render every section, profile, pattern, alternative, lifecycle action,
  output device, and transport command as simultaneous buttons. Arrange shows
  four to six named style choices as its primary gallery; profile and pattern
  selectors stay behind targeted advanced adjustment. Use the shared,
  horizontally scrollable bar-proportional song map for section selection;
  every duplicate section label includes its occurrence number and every role
  state has text as well as colour. Use compact selectors,
  one primary action per step, and progressive disclosure.
- Successful candidate generation and review decisions remain visibly selected
  in the same scope without asking the musician to refresh evidence.

## 6. Validation and reference retirement

The target fixture matrix captures all six destinations at 1280 × 900 wide and
720 × 900 compact widths through the real create/import/authority/preview/
draft/use/undo/re-use/export workflow, plus compact blocked states. The wide
shell includes the compact navigation rail, fluid work area, and musical
context rail; compact layouts retain horizontally scrollable navigation and
place the page first. Fixture coverage checks visual continuity, but the
separate observed-session rubric checks musician comprehension.
These existing generated captures do not yet prove pixel-level fidelity.
UI-001 freezes measured targets; UI-017 adds deterministic versioned expected
images, actual/expected/diff comparison, independent geometry checks and
deliberately failing comparator tests. UI-019 requires user visual approval.
Add 1536 × 1024 reference-size captures while retaining both existing sizes.
Use the page/state, contrast, hit-target and fidelity rubric defined by the
linked redesign plan; do not accept image-write success as visual regression.

The target expected images are not the original mockups. MC-051 removes legacy
executable image readers and obsolete assets after consumer/fixture checks, but
preserves the nine `docs/pictures/UI` inputs as design-only references under
root Plan 7.7. Production code and normal golden tests do not load them.

UI-001's versioned [reference measurements](plan/ui-evidence/UI-001-reference-measurements.json)
and [six-page region map](plan/ui-evidence/UI-001-region-map.md) are the
measurable implementation contract. They define page-specific inspector widths,
responsive geometry, palette/type targets, deliberate MIDI substitutions and
the future expected/actual/diff policy. The originals remain design-only input;
the JSON does not license a direct source-image comparison or an unsupported
route.
