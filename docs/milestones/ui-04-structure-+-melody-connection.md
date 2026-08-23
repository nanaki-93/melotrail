# UI-04 — Structure + Melody Connection

## Scope

- Build draggable section-instance timeline.
- Show A1/A2/B1/etc rather than only source-part IDs.
- Add boundary inspector.
- Expose melody-connection strategies, changes and critic results.
- Add solo source-song preview and source approval gate.

## Acceptance criteria

- [ ] Connected solo source song can be created, reviewed and approved in UI.
- [ ] Repeated sections retain independent occurrence identity.
- [ ] Boundary modifications are inspectable.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
