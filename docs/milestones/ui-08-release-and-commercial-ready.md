# UI-08 — Release & Commercial Ready

## Scope

- Create release checklist screen.
- Show provenance and license state.
- Show integrated loudness / true peak / dynamics.
- Show recognizability and melody audibility.
- Show release-similarity report.
- Expose WAV/MP3/provenance/YouTube metadata exports.

## Acceptance criteria

- [ ] Commercial Ready is clearly distinct from Build Success.
- [ ] All blocking gates are explainable.
- [ ] User can export release assets from one screen.

## Implementation rules

- Read AGENTS.md and current UI implementation first.
- Reuse existing API/domain behavior rather than duplicating it in the browser.
- Do not change music-generation algorithms unless a missing UI contract exposes a real backend defect.
- Keep the existing personal-use scope.
- Preserve the dark, soft workstation visual direction.
- Add frontend tests and backend contract tests where APIs change.
- Stop after this milestone.
