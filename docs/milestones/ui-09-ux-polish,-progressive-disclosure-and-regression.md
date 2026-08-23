# UI-09 — UX Polish, Progressive Disclosure & Regression

## Scope

- Add basic/advanced modes where helpful.
- Hide low-level technical metrics until requested.
- Add loading/progress/error/retry states for long jobs.
- Add undo/confirmation around destructive workflow actions.
- Add UI regression tests for all stage gates.
- Document full UI workflow.

## Acceptance criteria

- [ ] A first-time personal user can build a song without consulting CLI docs.
- [ ] Advanced diagnostics remain available.
- [ ] UI cannot bypass core workflow gates accidentally.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
