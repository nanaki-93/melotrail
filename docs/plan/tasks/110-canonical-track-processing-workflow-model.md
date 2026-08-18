# Task 110 — Canonical Track-Processing Workflow Model

## Goal

Define and implement one typed workflow/artifact graph for import, conversion,
MIDI cleaning, optional AI fix, optional Lo-fi Feel, analysis, Structure,
Cohesion, and Arrangement before changing individual processors.

## Dependencies

- None.

## Requirements

- Inventory the existing project schema, preparation references,
  `WorkflowArtifactGraph`, `WorkflowReadModel`, desktop creation progress, and
  application-service commands. Record which existing artifacts can be reused
  and which overlapping meanings must be retired or renamed.
- Define one per-part selection chain: raw MIDI, cleaned MIDI, optional approved
  AI-fixed MIDI, optional Lo-fi Feel MIDI, then analysis. A downstream service
  must resolve this through one typed boundary rather than selecting files by
  existence or duplicating precedence rules.
- Define canonical, project-relative references and fingerprints for AI-fix
  draft/approved artifacts and Cohesion boundary artifacts. Keep current safe
  paths where they already satisfy the contract.
- Extend invalidation so a change at any selection point marks analysis,
  Structure-dependent Cohesion, Arrangement, generated MIDI/stems, mix, master,
  and release stale as appropriate. Retained files remain inspectable only.
- Represent optional branches explicitly: `skip AI fix`, `approved AI fix`,
  `current feel`, and `Lo-fi Feel`. A missing optional artifact is not a
  readiness error when its branch is not selected.
- Preserve supported legacy reads. If serialized references cannot evolve
  compatibly, add a versioned, explicit atomic migration; project open alone
  must remain read-only.
- Update workflow snapshots/readiness to expose the exact next action and
  prerequisite without embedding presentation strings in the domain model.

## Tests

- Unit-test every invalidation edge and each optional-branch selection.
- Test legacy reads and any explicit migration for no partial writes, idempotent
  retry, and rejection of unsafe or stale references.
- Test that file existence alone cannot mark a stage current and that a skipped
  optional stage allows progression.
- Update workflow/creation-progress tests for the complete ordered sequence.

## Acceptance criteria

- Every later task can ask one typed resolver for the current MIDI input, and
  the workflow reports one correct next action for every supported state.
- No imported or accepted upstream artifact is overwritten during selection,
  invalidation, project open, or migration.

## Out of scope

- Implementing transcription, MIDI transformations, AI plans, or UI redesign.
