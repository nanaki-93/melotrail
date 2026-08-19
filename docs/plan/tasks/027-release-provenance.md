# Task 027 — Stage lineage and commercial release provenance

## Goal

Close stage artifacts, user decisions, AI/model details, seeds, assets, mix/master,
and outputs into a verifiable selected release lineage.

## Why

Commercial publication needs clear creation/transformation evidence and honest
unresolved-dependency reporting without a complex rights platform.

## Dependencies

Tasks 011 and 026; integrate earlier decision/run records throughout.

## Existing Code

- `CommercialProvenanceService`, source attestations/import evidence
- artifact hashes, model/sample/license dependencies, release report/checklist
- older `provenance/ProvenanceLog` and diagnostic redaction tests

## Changes

- Make stage manifest the common transformation source for commercial evidence;
  migrate useful generic-log fields and avoid a third log.
- Record settings/harmony/part/structure/arrangement/selection/approval/mix
  decision revisions plus processor/model/version/license/template/schema/seed.
- Build release manifest closing over exact selected source-to-export artifact
  graph and dependency hashes.
- Add lineage/hash/tamper validator, replay metadata, unresolved evidence list,
  and truthful commercial-ready result.
- Preserve rejected/failed/stale history without including it in selected lineage.
- Redact absolute paths/secrets/private raw model data in portable reports/logs.
- Unknown/fake model identity or license blocks commercial-ready status; never
  fabricate a zero hash/known license.

## Files

Commercial provenance/stage manifest/project refs/report rendering/redaction,
Export UI/readiness, docs/fixtures/tests; retire generic log only after migration.

## API / Contracts

`VerifyReleaseLineage(releaseId)` returns closed/missing/tampered dependencies and
safe report refs. Release manifests are versioned and immutable.

## UI

Export displays evidence status/unresolved actions and report download without
claiming legal ownership or monetization guarantee.

## Backend

Canonical project evidence only; adapters expose redacted DTOs.

## Python Worker

Worker/command/library versions from results/capabilities enter run evidence.

## Tests

Lineage closure, source/decision/run dependencies, tampering, unknown model/
license, seed replay metadata, failure/retry, redaction, legacy provenance mapping.

## Acceptance

- Every claimed commercial-ready export has hash-validated selected lineage.
- Missing evidence blocks the claim but never deletes/locks the project.
- Reports contain no secrets or unrestricted absolute paths.

## Out of Scope

DRM, copyright adjudication, platform monetization prediction, blockchain.

