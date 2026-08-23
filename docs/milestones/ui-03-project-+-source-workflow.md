# UI-03 — Project + Source Workflow

## Scope

- Redesign Project screen around authoritative key/tempo/meter/chord progressions.
- Redesign Source screen around part cards.
- Show SOURCE / RAW MIDI / CLEAN / AI FIX / AI ENHANCE versions.
- Add A/B preview and stage status.
- Expose transcription/cleanup profiles and validation reports without overwhelming default UI.

## Acceptance criteria

- [ ] User can prepare each source part entirely from the UI.
- [ ] Project harmony is visually identified as authoritative.
- [ ] User can compare every source transformation stage.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
