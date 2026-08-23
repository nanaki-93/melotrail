# UI-02 — Application Shell & Navigation

## Scope

- Refactor navigation to Project / Source / Structure / Arrange / Mix / Release.
- Add persistent project header and worker/job status.
- Create stage-aware routing and locked navigation states.
- Apply soft dark creative-workstation visual system.

## Acceptance criteria

- [ ] The new navigation follows the actual production workflow.
- [ ] User always knows project, current stage and blocking status.
- [ ] Travel/train concepts are not hard-coded into app navigation.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
