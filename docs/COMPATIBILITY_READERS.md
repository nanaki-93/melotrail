# Transitional compatibility reader inventory

> This inventory describes readers in the superseded runtime. MIDI Core does
> not retain legacy schema or audio compatibility branches. Delete this file
> when those readers and their tests are removed.

Reviewed: 2026-08-24

Status: retained operational contract

This is the Task 030 release inventory for supported non-project compatibility
readers. Project schemas have no compatibility window: schema v4 is the sole
accepted `project.json` format, and unsupported documents fail without writes.
A row below is supported only while its active caller, owner, removal condition,
and executable fixture all remain true.

| Reader and exact contract | Active caller | Owner | Removal condition | Fixture / test |
| --- | --- | --- | --- | --- |
| Sound-library registry v1 (`instruments.json` plus `LICENSES.json`) | `InstrumentRegistryLoader.load`, desktop library readiness and renderer setup | Kotlin sound-library boundary | The starter v1 pack is retired and no supported project/release lineage references registry v1 | `InstrumentRegistryTest`, `LocalSoundLibraryInventoryTest` |
| Arrangement v1 (`arrangement.json`, no renderable transition metadata) | arrangement validation/load path and historical build/export read | Kotlin arrangement boundary | All supported historical arrangements have been migrated or the arrangement-v1 support window ends | `ArrangementTest`, `ArrangementRendererTest` |
| Worker version-1 job-response envelope | `WorkerClient` and command-specific Kotlin worker boundaries | Kotlin worker protocol boundary | A documented, coordinated worker protocol migration replaces both sides and fixtures in the same release | `WorkerClientContractTest`, `WorkerResponseMapperTest` |
| Legacy worker MIDI-clean request fields `normalizeVelocity` and `cleanSustain` | worker `/midi-clean` schema | Python worker boundary | The documented non-conservative request compatibility window ends; remove parser branch and worker fixtures together | `worker/tests/test_midi_clean.py` |

Unsupported old Spring REST routes, Spring project storage, browser frontend
fallbacks, CORS/SSE configuration, and in-process worker management are not
compatibility readers. They were removed by Task 028; see
[`SPRING_API_RETIREMENT.md`](SPRING_API_RETIREMENT.md).

The remaining non-project compatibility inventory does not turn stale artifacts into valid output.
Every later workflow stage still checks current hashes, validated paths, and its
own versioned evidence before it uses an artifact.
