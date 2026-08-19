# Compatibility readers

This is the Task 030 release inventory for every supported persisted or
external compatibility reader. A row is supported only while its active caller,
owner, removal condition, and executable fixture all remain true. Reads are
side-effect free; writing a current project format is always explicit and
atomic.

| Reader and exact contract | Active caller | Owner | Removal condition | Fixture / test |
| --- | --- | --- | --- | --- |
| `ProjectStore` project schemas v1, v2, and v3 (`project.json`) | `DefaultProjectApplicationService.open`, desktop Open Project | Kotlin project-artifact boundary | End of the declared v1–v3 support window; remove all three DTO readers, their fixtures, and README support note in one change | `ProjectStoreWorkflowMigrationTest`, `ProjectV4SchemaTest`, `ProjectApplicationServiceTest` |
| `ProjectStore` early v4 scalar `structure`, `envelope.structureOccurrences`, and part `role` fields | the same `ProjectStore.read` path | Kotlin project-artifact boundary | No supported v4 document contains the pre-canonical fields; remove the legacy v4 DTO/slots and fixture together | `ProjectV4SchemaTest` |
| Provisional v4 `envelope.manifests` stage-run payload and v3 MIDI references mapped by `LegacyV3StageRunMapper` | `ProjectStore.migrateAndSave` | Kotlin stage-run boundary | All supported legacy projects have been explicitly migrated or the v1–v3 support window ends | `StageRunStoreTest`, `ProjectStoreWorkflowMigrationTest` |
| Sound-library registry v1 (`instruments.json` plus `LICENSES.json`) | `InstrumentRegistryLoader.load`, desktop library readiness and renderer setup | Kotlin sound-library boundary | The starter v1 pack is retired and no supported project/release lineage references registry v1 | `InstrumentRegistryTest`, `LocalSoundLibraryInventoryTest` |
| Arrangement v1 (`arrangement.json`, no renderable transition metadata) | arrangement validation/load path and historical build/export read | Kotlin arrangement boundary | All supported historical arrangements have been migrated or the arrangement-v1 support window ends | `ArrangementTest`, `ArrangementRendererTest` |
| Worker version-1 job-response envelope | `WorkerClient` and command-specific Kotlin worker boundaries | Kotlin worker protocol boundary | A documented, coordinated worker protocol migration replaces both sides and fixtures in the same release | `WorkerClientContractTest`, `WorkerResponseMapperTest` |
| Legacy worker MIDI-clean request fields `normalizeVelocity` and `cleanSustain` | worker `/midi-clean` schema | Python worker boundary | The documented non-conservative request compatibility window ends; remove parser branch and worker fixtures together | `worker/tests/test_midi_clean.py` |

Unsupported old Spring REST routes, Spring project storage, browser frontend
fallbacks, CORS/SSE configuration, and in-process worker management are not
compatibility readers. They were removed by Task 028; see
[`SPRING_API_RETIREMENT.md`](SPRING_API_RETIREMENT.md).

The compatibility inventory does not turn stale artifacts into valid output.
Every later workflow stage still checks current hashes, validated paths, and its
own versioned evidence before it uses an artifact.
