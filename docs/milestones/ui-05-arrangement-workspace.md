# UI-05 — Arrangement Workspace

## Scope

- Create global energy/density plan visualization.
- Expose Qwen producer plan and deterministic baseline.
- Visualize instrument timeline.
- Expose incremental ArrangementState.
- Add per-role generate/validate/accept/reject controls.
- Expose pattern/density/register overrides.
- Add core arrangement review/approval.

## Acceptance criteria

- [ ] UI reflects incremental generation instead of one generic arrange operation.
- [ ] User sees which roles are accepted, under review or locked.
- [ ] Optional layers remain locked until core approval.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
