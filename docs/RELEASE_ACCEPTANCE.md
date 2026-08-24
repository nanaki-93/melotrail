# Release acceptance record

Review date: 2026-08-24

Status: **NOT APPROVED FOR RELEASE**

This is a living evidence gate, not a release declaration. QP-001 through
QP-004 have code-owned evidence; QP-005–QP-018 remain incomplete, and the current
four-source song has documented timing, unresolved-chromatic/harmony, sustain-tail,
monophony, shared-groove, voice-leading, arrangement, Cohesion, low-end, and
bypass defects. A successful build or package cannot override those musical
findings.

## Policy review

The official YouTube
[channel monetization policies](https://support.google.com/youtube/answer/1311392?hl=en)
and [GenAI disclosure guidance](https://support.google.com/youtube/answer/14328491?hl=en)
plus the official
[recommended upload encoding settings](https://support.google.com/youtube/answer/1722171?hl=en)
and [partner music-video encoding specification](https://support.google.com/youtube/answer/6039860?hl=en)
were reviewed on 2026-08-24. See
[`COMMERCIAL_PROVENANCE.md`](COMMERCIAL_PROVENANCE.md) and
[`plan/YOUTUBE_READINESS.md`](plan/YOUTUBE_READINESS.md). Melotrail does not
guarantee rights clearance or monetization.

## Automated gates

Run from a clean tree for every release candidate:

| Command | Required result |
| --- | --- |
| `make test` | All Kotlin root/desktop tests pass; skipped opt-in tests identified |
| `make worker-test` | All offline worker tests pass |
| `make build` | Full Gradle build and documentation coverage pass |
| `python3 tools/check_documentation_coverage.py --repository .` | Checked-in production source inventory is current |
| `git diff --check` | No whitespace/patch errors |

Documentation-consolidation verification on 2026-08-24:

- `make test` — passed;
- `make worker-test` — 43 tests passed;
- `make build` — passed, including documentation coverage;
- local Markdown link audit — passed;
- `git diff --check` — passed after removing Markdown trailing whitespace.

These results prove repository correctness under their contracts, not musical
listening quality. The remaining QP roadmap and its human evidence gates are
incomplete, so release status remains **NOT APPROVED**.

## Composition-quality blockers

Release approval remains withheld until [`plan/QUALITY_GATES.md`](plan/QUALITY_GATES.md)
is satisfied and QP-017 records evidence for the intended release:

- beat/downbeat mapping and canonical whole-bar or explicit pickup timing;
- sustain-aware chord-boundary release and one accepted full-song groove map;
- confirmed mode-aware key transposition;
- globally monophonic, scale/harmony-compatible approved full melody;
- exact canonical melody consumption by arrangement through export;
- expressive section contrast and accepted generated-role validation;
- smooth cross-section pad/string voice leading and phase-coherent drums/bass;
- boundary-local Cohesion with improving before/after metrics;
- controlled kick/bass interaction and selected-master/lossy-preview true peak;
- no unresolved source/full-song hard blocker or critical issue;
- renderer-backed, loudness-matched listening comparisons;
- original sources and known-good candidates unchanged.

## Production and listening gates

Record date, listener, OS, renderer/model/library versions, audio device, output
level, project/context hashes, compared artifacts, decision, and reason.

Required listening comparisons:

1. selected source vs prepared section;
2. hard concatenation vs connected canonical full melody;
3. full melody alone vs core arrangement;
4. core vs Cohesion;
5. pre/post targeted polish;
6. grid-only accompaniment vs source-groove-aligned accompaniment;
7. pad/string section boundaries before/after global voice-leading;
8. kick/bass interaction before/after processing;
9. dry vs production mix vs selected master vs decoded lossy preview.

The selected production output must also have decoded finite non-silent audio,
correct duration/format, current mix plan/report, no unresolved clipping/true-
peak, melody-audibility, masking/kick-bass overlap, pumping, stereo, loudness, or
lossy-preview blocker, and matching stems/master/export hashes.

The current `-14 LUFS` integrated and `-1 dBTP` references are versioned
Melotrail production policy, not official YouTube mandates. The lossless master
remains canonical; local lossy previews are regression evidence because YouTube
performs its own transcode.

## Commercial-evidence gates

- source rights attestations are complete;
- used instruments/samples/models have admitted commercial terms;
- required attribution is generated from frozen used-stem lineage;
- selected signature motif remains recognizable;
- cross-release similarity has a human originality decision when applicable;
- AI-use metadata reflects the selected generative stages;
- title, description, lyrics, visuals, thumbnail, and channel context receive a
  separate human policy/rights review;
- commercial evidence is labeled as evidence, not platform approval.

## Platform and dependency gates

- Real Basic Pitch transcription is verified only for the claimed isolated
  source route.
- Real local Qwen output is schema-valid, reviewed, and license-identified.
- The validated sound library and every sample required by selected instruments
  are present and hash-matched.
- `sfizz_render` produces every expected stem.
- MIDI/audio preview actually starts and is audible.
- The current-OS package builds, installs, launches, creates/opens a canonical
  project, and rejects an unsupported project without rewriting it.
- Keyboard, focus, screen-reader labels, contrast, and wide/medium/narrow layout
  receive real review in addition to Compose tests.

Unverified dependencies remain explicitly unverified. Do not infer Windows,
Linux, renderer, model, audio-device, package, policy, rights, or listening
support from offline fakes.

## Sign-off

| Owner | Decision | Date |
| --- | --- | --- |
| Engineering | Withheld pending QP roadmap and clean validation | — |
| Composition/listening | Pending | — |
| Production/audio | Pending | — |
| Rights/policy | Pending | — |
| Release owner | Pending; do not publish/upload | — |
