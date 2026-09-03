# MIDI workspace visual specification

Status: implemented target design contract; MC-048G owns the song-map Arrange
cutover and its wide/compact/accessibility validation

Authority: visual language and visual acceptance for the Compose Desktop MIDI
workspace

## 1. Purpose

Melotrail is a focused MIDI-arrangement workstation. Its desktop visual design
makes current musical authority, candidate decisions, and export safety easier
to understand; it does not imitate a DAW or revive the rejected audio product.

The temporary images under `docs/pictures/UI` are visual-language reference
only. Their useful qualities are a calm dark workstation, compact navigation,
clear primary-action hierarchy, dense but readable panels, and persistent
context. Their branding, audio waveforms, video art, sound library, mixer,
mastering, publishing, and settings workflows are out of scope.

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
  generated scenic image, simulated mixer meter, or video control as a visual
  substitute.

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
| Review | shared song map -> complete-draft listen/use decision | selected-section lifecycle/diff/repair evidence and playback controls |
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

MC-048B produces target visual-regression fixtures for the six destinations at
1280 × 900 wide and 720 × 900 compact widths, including representative ready
and blocked states. The wide shell includes the compact navigation rail, fluid
work area, and musical context rail; compact layouts retain horizontally
scrollable navigation and place the page first.
The fixtures are target test assets, not the old image set. MC-051 removes the
old `docs/pictures` readers and assets only after target fixture integrity,
accessibility, desktop smoke, and consumer scans pass.
