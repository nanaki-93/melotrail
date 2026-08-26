# Transitional commercial provenance test record

> This file remains only because an audio-era commercial-release test reads its
> dated policy metadata. Commercial release, YouTube readiness, audio lineage,
> and monetization review are outside MIDI Core. Delete this file with that code
> and test; it is not active product documentation.

Policy review date: 2026-08-25

Before every release, a maintainer must manually re-read and update this date
after checking YouTube's official [GenAI disclosure policy](https://support.google.com/youtube/answer/14328491)
and [channel monetization policies](https://support.google.com/youtube/answer/1311392).
Do not automate this check over the network or treat a stale date as approval.

As reviewed on the date above, YouTube lists AI-generated music among the
examples requiring disclosure when the content is realistic or meaningfully
AI-altered, and states that disclosure does not by itself limit audience or
monetization eligibility. Its monetization review is channel-wide and expects
original/authentic work rather than generic, repetitive, mass-produced, or
template-like output. Rights, copyright, advertiser suitability, Community
Guidelines, and other program policies still apply. Policies can change.

Melotrail's commercial report is evidence and workflow assistance. It is not
legal advice, copyright clearance, Content ID clearance, or a monetization
guarantee. Processing a work in Melotrail never makes it copyright free.

The former engineering-quality and YouTube-readiness plans have been removed.
This record survives temporarily only for its executable legacy test; it does
not define a MIDI Core release gate or platform decision.

## Release lineage

**Create commercial evidence** writes an immutable, versioned release folder:
`output/releases/<releaseId>/provenance.json`, `commercial-report.md`,
and `youtube-release.json`. The provenance manifest closes the selected source,
the quality-certified source-song/connection/connected-MIDI/critic/approval
chain, MIDI, stage-run, decision, render, mix, master, and release metadata
hashes. A missing, stale, or private-audition melody approval remains a
commercial blocker; it cannot be substituted with selected part MIDI.
It snapshots only the exact instrument stems used by the final persisted mix;
it does not reread the current sound registry during verification.

Every completed build also records a deterministic arrangement-only similarity
fingerprint in `output/release.json`: structure, energy, instrument
entry/exit, bass and drum patterns, transitions, tempo/swing, and density.
When explicit completed-release fingerprints are supplied for comparison, the
review stores per-feature scores and explanations; a high score is an advisory
warning only. The optional re-plan scope is limited to arrangement, groove, and
orchestration. It never changes human melody, harmony, or structure, and it
does not determine YouTube Partner Program eligibility or any policy outcome.

Use `VerifyReleaseLineage(releaseId)` through the local application boundary to
inspect missing, tampered, and unresolved evidence plus safe report references.
Unknown model identity/license, missing attribution, stale artifact hashes, or
missing decision evidence block a commercial-ready claim without changing the
project. The report redacts secrets and unrestricted absolute paths.
The selected production mix must also have a matching current `mix/plan.json`
and `mix/report.json`; any blocking audio-critic finding (such as inadequate
headroom, clipping, or inaudible melody) blocks the commercial-ready claim.

## Signature motif recognizability gate

Before creating commercial evidence, select a source melody phrase as the
signature motif and explicitly confirm it. Melotrail writes a hash-bound debug
report under `motif/<source-sha256>/<input-sha256>/report.json`. It records
source-note lineage for every evaluated piano occurrence, interval-contour and
rhythm similarity, protected-anchor retention, matched-note coverage, and the
configured deterministic thresholds. At least one occurrence must clearly
survive. A missing, stale, or failing gate blocks only the commercial-ready
claim; it never overwrites source MIDI or removes retained project evidence.

## Instrument credits

After commercial evidence is ready, **Export commercially with credits** creates
the selected WAV or MP3 plus its deterministic sibling
`<sanitized-export-base>-credits.txt`. The text is generated only from the
frozen release manifest's final used-stem license snapshots—not the current
library, candidate list, or unused arrangement roles. CC0/owned instruments
are omitted; an all-no-attribution release contains only `No instrument
attribution required.`

The immutable export revision records the audio hash, credits path/hash, used
instrument IDs, attribution-entry hashes, and policy/template versions. If a
required attribution is missing, contradictory, NC, or not admitted, commercial
export remains blocked while private project and audition/export work remains
available. Changing the selected mix or instrument creates a new release
revision; changing the local library cannot rewrite an existing credits file.

## AI-use disclosure draft

`youtube-release.json` is a local human-review draft. It records the selected
model identities, whether each recorded model use has a structured reviewer,
the current disclosure-review state, and `platformApprovalStatus: NOT_REQUESTED`.
It never uploads, contacts YouTube, claims that YouTube approved the work, or
concludes that the work will monetize.

Every recorded model use in a new release manifest requires a structured
AI-use review with a reviewer ID, ISO-8601 review time, disclosure decision,
and concise rationale. Missing review blocks the `COMMERCIAL_EVIDENCE_READY`
result even when a model's license is otherwise admitted. The release owner
must still compare the final upload—including visuals, title, description,
lyrics, and channel context—with the current official policy and complete the
applicable upload disclosure. The resulting metadata is evidence for human
review, not an automated upload action or a monetization conclusion.
