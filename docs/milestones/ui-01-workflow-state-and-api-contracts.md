# UI-01 — Workflow State & API Contracts

## Scope

- Introduce a backend workflow-state DTO/API.
- Expose explicit stage statuses and approval states.
- Expose artifact/version IDs for each pipeline stage.
- Make long-running stage operations job-aware.
- Remove frontend assumptions based only on file existence.

## Acceptance criteria

- [ ] UI can render the entire current project pipeline state from one normalized contract.
- [ ] Locked/ready/running/failed/approved states are explicit.
- [ ] No major screen has to infer stage status heuristically.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
