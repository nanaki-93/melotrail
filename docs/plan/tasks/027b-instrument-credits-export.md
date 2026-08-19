# Task 027B — Usage-based instrument credits export

## Goal

Generate a deterministic `<export-base>-credits.txt` containing only attribution
required by instruments whose stems contribute to the exported song.

## Why

Commercial/YouTube publication needs copy-ready credits without listing every
installed, suggested, rejected, unused, or CC0 instrument. Credits must remain
correct even if the local sound library changes later.

## Dependencies

Tasks 022B, 026, and 027.

## Existing Code

- `CommercialProvenanceService`, release manifests/reports/checklists
- `BuildApplicationService`, mix/master/export artifacts and resolved stem state
- embedded instrument license/provenance and admission decisions from Task 022B
- Export UI and atomic project/output publication helpers

## Changes

- Add `ReleaseCreditsService` that consumes only an immutable, validated release
  manifest—never the live registry or candidate list.
- Derive the used-instrument set from final resolved mix lineage after mute/solo/
  stem-inclusion decisions. Exclude unused arrangement roles, candidate scores,
  rejected/stale assignments, and stems proven absent from the final audio. If
  zero contribution cannot be proven, include attribution conservatively.
- For each used instrument, require its frozen license/source-library snapshot and
  admitted policy result. Exclude CC0/no-attribution entries from credit blocks.
- Normalize line endings/whitespace without rewriting legally significant text;
  deduplicate identical ready-to-publish attribution blocks and sort them by a
  stable attribution/instrument key.
- Atomically publish `<sanitized-export-base>-credits.txt` beside each WAV/MP3.
  Record credits artifact ID/path/hash, contributing instrument IDs, policy/
  template version, and audio export ID/hash in the release manifest.
- For a CC0-only/owned release, emit the predictable file with one stable
  `No instrument attribution required.` statement and no CC0 catalog listing.
- Block commercial-ready export if a used attribution-required instrument lacks
  complete/consistent attribution. Preserve private audition/project artifacts.
- Regeneration uses frozen release snapshots, so a registry/library update cannot
  change old credits. A new selection/substitution/mix creates a new release/
  credits revision.
- Keep the text file narrowly copy-friendly: required instrument attribution (or
  the no-attribution statement), not AI/model/provenance diagnostics.

## Files

Add release credits domain/service/template/store and tests; modify export/release
manifest/readiness, desktop Export page, relevant commercial docs and acceptance.

## API / Contracts

`GenerateReleaseCredits(releaseId, audioExportId)` returns immutable
`ReleaseCreditsArtifact(id, relativePath, sha256, usedInstrumentIds,
attributionEntryHashes, policyVersion)`. It rejects stale/tampered/incomplete
release lineage and never accepts caller-supplied free-form credits.

## UI

Preview exact required attribution and output filename before commercial export;
download/copy/open the generated text afterward. Show CC0 in provenance details,
not the required-credit list. Link missing attribution to the offending selected
instrument and explain that private export/work remains available where allowed.

## Backend

Canonical application service derives/publishes credits atomically and pairs them
with the exact audio export hash. A retained REST adapter calls this service.

## Python Worker

No change. Credits are deterministic text/provenance output in Kotlin.

## Tests

CC0-only statement, one/multiple CC BY entries, mixed CC0/CC BY, duplicate
attribution dedup/order, unused candidate/role exclusion, mute/solo/zero-contribution
policy, conservative uncertain inclusion, missing/contradictory attribution block,
NC/unknown admission impossibility, filename sanitization/collision, atomic failure,
audio/credits hash pairing, live-registry mutation independence, substitution and
mix invalidation, UI copy/preview, path/redaction safety.

## Acceptance

- `my-song.wav`/MP3 commercial export has a hash-paired
  `my-song-credits.txt` generated from exact final used-instrument lineage.
- CC0 instruments and unused/candidate instruments are not listed.
- Every used CC BY instrument contributes its complete required attribution once.
- Missing attribution blocks commercial-ready status without deleting work.
- Reopening or changing the live library cannot change approved release credits.

## Out of Scope

Legal advice, platform upload/description editing, credits for non-instrument
assets unless a later generic credits task adds them, DRM, or rights adjudication.
