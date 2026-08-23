# UI-07 — Production Mix Workspace

## Scope

- Add stem-rendering workflow.
- Create production mixer controls.
- Expose dry vs LoFi A/B.
- Show buses/reverb sends at an appropriate advanced level.
- Expose audio critic including melody audibility.
- Add mix approval.

## Acceptance criteria

- [ ] User can produce and compare dry/LoFi mix without CLI.
- [ ] Blocking audio-quality issues are visible.
- [ ] Mix approval is persisted.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
