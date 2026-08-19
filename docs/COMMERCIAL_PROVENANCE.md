# Commercial provenance and YouTube release check

Policy review date: 2026-08-17

Before every release, a maintainer must manually re-read and update this date
after checking YouTube's official [AI-use disclosure policy](https://support.google.com/youtube/answer/14328491)
and [channel monetization policies](https://support.google.com/youtube/answer/1311392).
Do not automate this check over the network or treat a stale date as approval.

As reviewed on the date above, YouTube identifies AI-generated music as content
to disclose through the AI-use workflow. Disclosure does not itself limit
monetization eligibility. Monetized channels are evaluated for original,
authentic, non-repetitive/non-mass-produced content. Policies can change.

Melotrail's commercial report is evidence and workflow assistance. It is not
legal advice, copyright clearance, Content ID clearance, or a monetization
guarantee. Processing a work in Melotrail never makes it copyright free.

## Release lineage

**Create commercial evidence** writes an immutable, versioned release folder:
`output/releases/<releaseId>/release-manifest.json`, `commercial-report.md`,
and `youtube-upload-checklist.md`. The manifest closes the selected source,
MIDI, stage-run, decision, render, mix, master, and release metadata hashes.
It snapshots only the exact instrument stems used by the final persisted mix;
it does not reread the current sound registry during verification.

Use `VerifyReleaseLineage(releaseId)` through the local application boundary to
inspect missing, tampered, and unresolved evidence plus safe report references.
Unknown model identity/license, missing attribution, stale artifact hashes, or
missing decision evidence block a commercial-ready claim without changing the
project. The report redacts secrets and unrestricted absolute paths.

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
