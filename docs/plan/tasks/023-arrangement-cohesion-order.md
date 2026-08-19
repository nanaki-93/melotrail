# Task 023 — Arrangement-before-cohesion dependency migration

## Goal

Reverse the current build dependency so approved arrangement precedes cohesion,
with explicit compatibility, invalidation, and reapproval behavior.

## Why

Cohesion must know the actual roles, instruments, density, drums, bass, dynamics,
and occurrence variations it is connecting.

## Dependencies

Tasks 022 and 022B.

## Existing Code

- `ArrangementApplicationService.requireApprovedCohesion`
- `CohesionApplicationService.kt`, `TransitionCohesion.kt`
- `WorkflowArtifactGraph`, `WorkflowReadModel`, build/render prerequisites
- approved v3 cohesion and arrangement fixtures

## Changes

- Remove target-order cohesion as an Arrangement prerequisite; planners must no
  longer require or invent approved transition artifacts.
- Define arrangement approval output/context required by Cohesion.
- Update artifact dependencies/readiness/navigation to Structure -> Arrange ->
  Cohesion -> Humanization -> Render.
- Dual-read legacy pre-arrangement cohesion and show it as historical/stale when
  target-order inputs differ. Never relabel it arrangement-aware.
- On explicit migration/first new build, invalidate affected cohesion/render
  onward once and require regeneration/reapproval; retain files/evidence.
- Add compatibility path for opening/exporting a previously completed historical
  build without silently rebuilding it.
- After supported projects migrate and historical exports resolve through their
  immutable release manifests, delete the cohesion-before-arrangement prerequisite,
  old dependency graph/readiness branches, duplicate build route, configuration,
  and exclusive tests. Historical artifact data remains; obsolete orchestration
  code does not.

## Files

Arrangement/cohesion/build services, artifact graph/read model, project migration,
desktop navigation/build state, fixtures/docs/tests.

## API / Contracts

Arrangement approval contains exact structure/occurrence/context/plan hashes.
Cohesion requests require that approval identity.

## UI

Arrange no longer blocks on Cohesion. Build explains legacy target-order
regeneration and reapproval instead of showing a generic stale error.

## Backend

Canonical service ordering only; no worker change.

## Python Worker

No change.

## Tests

New order happy path, legacy completed open/export, migration invalidation exactly
once, arrangement rerun invalidates cohesion, no circular dependency, UI readiness.

## Acceptance

- Arrangement can complete with no cohesion record.
- Cohesion cannot run against an unapproved/stale arrangement.
- Historical artifacts remain inspectable/exportable under their recorded lineage.
- Repository search and dependency tests prove there is no callable old-order
  arrangement/cohesion path.

## Out of Scope

Cohesion musical improvements or humanization algorithm.
