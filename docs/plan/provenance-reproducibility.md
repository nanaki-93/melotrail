# Provenance, reproducibility, and recovery

## Goal

Keep a clear, portable record of the musician's inputs and decisions and every
deterministic or AI-assisted transformation used to produce an export. This is
not DRM, ownership adjudication, or a monetization guarantee.

## Reuse and consolidation

Extend `CommercialProvenanceService` and the existing artifact hashes/source
attestations. Do not build a third disconnected log. Migrate useful fields from
the older generic `provenance` package, then make the stage manifest the common
source for workflow and release evidence.

## Evidence model

### Artifact lineage

Every derived artifact records:

- stable ID/kind/project-relative path;
- hash and format metadata;
- producing run ID;
- upstream artifact IDs/hashes;
- subject part/occurrence/project ID;
- timestamp and validation report.

### Creative decisions

Record revisions/hashes for project settings, structured harmony, part section
assignment/source-key confirmation, structure occurrences, arrangement roles and
instrument choices, enhancement intensity/selection, cohesion approvals,
humanization configuration/seed, mix settings, master/export configuration.

### Processor evidence

Record processor and pipeline version, normalized configuration/context hashes,
deterministic applier version, model provider/name/version when used, dependency
license/status, prompt-template/schema version, seed, started/completed timestamps,
and failure/approval state.

Do not store secrets, unrestricted absolute machine paths, private reasoning, or
unbounded raw prompts/responses in portable commercial reports. Retain bounded
model request/response evidence only where useful for reproducibility/audit and
redact it through existing diagnostic policy.

## Manifest layout

A practical v4 layout is:

```text
project.json
processing/
  parts/<part-id>/runs.jsonl-or-versioned-json
  project/runs.jsonl-or-versioned-json
provenance/
  decisions/<revision>.json
  release/<release-id>.json
artifacts/...
```

The implementation task must choose versioned JSON or an append-safe ledger
based on current atomic-store utilities. Never append non-atomically to the only
copy. A compact indexed snapshot may point to immutable per-run JSON records.

## Reproducibility contract

A run is reproducible where practical when input artifact hashes, context and
configuration snapshots, processor/model version, external asset hashes, and
seed are available. Re-running creates a new run/artifact and compares hashes;
it never overwrites the historical result.

For nondeterministic model providers, record enough to reproduce the deterministic
application of the accepted plan even when inference cannot be repeated byte for
byte. Never claim exact reproducibility if the provider/model is unavailable or
unversioned.

## Approval and selection events

Draft generation, approval, rejection, bypass, and selected-artifact change are
separate events with actor (`user` or deterministic system), timestamp, input/
output hashes, and reason where supplied. AI never self-approves creative
enhancement. Automatic deterministic stages can satisfy code-owned validation
gates, with warnings still reviewable.

## Failure/recovery evidence

Failures preserve stage, inputs, safe error code/summary, processor version,
attempt number, timestamp, and retry link. Partial temp paths are not portable
evidence. On startup, interrupted `PROCESSING` records become recoverable failed
attempts. A retry references the previous attempt and may reuse upstream data.

## Commercial release manifest

The release manifest closes over the exact selected lineage:

- original-source hashes/attestations;
- current settings/harmony/structure/arrangement decision revisions;
- selected per-part and project-stage artifacts/runs;
- sound-library/sample/model dependencies and known licenses;
- master/export hashes/configuration;
- unresolved warnings and readiness result.

Generated reports use relative identifiers and redacted source categories where
needed. They state evidence and unresolved dependencies; they do not promise
copyright ownership or platform monetization.

## Retention and privacy

Project cleanup may remove unselected caches only through a future explicit,
recoverable policy. Sources, approved outputs, accepted plans, and release
lineage are retained. Diagnostics follow existing redaction and must not expose
absolute paths, credentials, source names marked sensitive, or raw model data.

## Verification

- lineage closure and tamper/hash mismatch tests;
- deterministic seed/config replay tests;
- unknown model/license blocks commercial-ready status;
- failure/retry links survive reopen;
- upstream selection changes create new release lineage and stale old readiness;
- redaction tests for logs/reports/API DTOs;
- legacy provenance/source attestation migration fixtures.

