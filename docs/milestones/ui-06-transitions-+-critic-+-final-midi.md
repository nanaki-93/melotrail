# UI-06 — Transitions + Critic + Final MIDI

## Scope

- Expose DensityBudget and optional layer reasoning.
- Create transition inspector.
- Expose EnsembleCohesion status.
- Build whole-song critic panel with clickable issues.
- Add targeted-fix workflow.
- Add recognizability report and final MIDI approval.

## Acceptance criteria

- [ ] Issues navigate to exact sections/bars.
- [ ] No generic unrestricted polish action is the primary workflow.
- [ ] Recognizability is visible before rendering.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
