package ai.music.workstation.desktop

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeReadinessTest {
    @Test fun `capabilities require only their declared dependencies`() {
        val ready = readiness()
        RuntimeCapability.entries.forEach { assertTrue(ready.capability(it).available, "$it should be ready") }

        assertFalse(readiness(worker = unavailable("worker down")).capability(RuntimeCapability.AUDIO_IMPORT).available)
        assertTrue(readiness(worker = unavailable("worker down")).capability(RuntimeCapability.MIDI_PREVIEW).available)
        assertFalse(readiness(transcription = unavailable("Basic Pitch missing")).capability(RuntimeCapability.AUDIO_IMPORT).available)
        assertFalse(readiness(library = unavailable("registry invalid")).capability(RuntimeCapability.MIDI_PREVIEW).available)
        assertFalse(readiness(samples = unavailable("samples missing")).capability(RuntimeCapability.ARRANGEMENT_RENDER).available)
        assertFalse(readiness(renderer = unavailable("renderer missing")).capability(RuntimeCapability.BUILD_SONG).available)
        assertFalse(readiness(audio = unavailable("device missing")).capability(RuntimeCapability.SOURCE_PREVIEW).available)
        assertTrue(readiness(audio = unavailable("device missing")).capability(RuntimeCapability.BUILD_SONG).available)
    }

    @Test fun `checking is never ready and preserves a stable status`() {
        val readiness = RuntimeReadiness.checking()
        RuntimeDependency.entries.forEach { assertEquals(DependencyStatus.CHECKING, readiness.dependency(it).status) }
        RuntimeCapability.entries.forEach { assertFalse(readiness.capability(it).available) }
    }

    @Test fun `local service reports missing worker and audio separately`() = runTest {
        val readiness = LocalRuntimeReadinessService(
            workerProbe = { ai.music.workstation.worker.WorkerRuntimeStatus(false, false) },
            libraryRoot = { null }, environment = emptyMap(), audioOutputProbe = { false }
        ).check()
        assertEquals(DependencyStatus.UNAVAILABLE, readiness.worker.status)
        assertEquals(RecoveryAction.START_WORKER, readiness.worker.recoveryAction)
        assertEquals(DependencyStatus.UNAVAILABLE, readiness.transcription.status)
        assertEquals(DependencyStatus.UNAVAILABLE, readiness.audioOutput.status)
        assertEquals(RecoveryAction.CHECK_AUDIO_OUTPUT, readiness.audioOutput.recoveryAction)
    }

    @Test fun `executable renderer is ready without a version command`() = runTest {
        val renderer = Files.createTempFile("sfizz_render", "")
        try {
            assertTrue(renderer.toFile().setExecutable(true))
            val readiness = LocalRuntimeReadinessService(
                workerProbe = { ai.music.workstation.worker.WorkerRuntimeStatus(false, false) },
                libraryRoot = { null },
                environment = mapOf("SFZ_RENDERER_PATH" to renderer.toString()),
                audioOutputProbe = { false }
            ).check()

            assertEquals(DependencyStatus.READY, readiness.renderer.status)
            assertEquals("SFZ renderer ready", readiness.renderer.detail)
            assertTrue(readiness.renderer.diagnostics.isEmpty())
        } finally {
            Files.deleteIfExists(renderer)
        }
    }

    @Test fun `configured renderer version is included in diagnostics`() = runTest {
        val renderer = Files.createTempFile("sfizz_render", "")
        try {
            assertTrue(renderer.toFile().setExecutable(true))
            val readiness = LocalRuntimeReadinessService(
                workerProbe = { ai.music.workstation.worker.WorkerRuntimeStatus(false, false) },
                libraryRoot = { null },
                environment = mapOf("SFZ_RENDERER_PATH" to renderer.toString(), "SFZ_RENDERER_VERSION" to "1.2.3"),
                audioOutputProbe = { false }
            ).check()

            assertEquals("1.2.3", readiness.renderer.diagnostics["version"])
        } finally {
            Files.deleteIfExists(renderer)
        }
    }

    private fun readiness(
        worker: DependencyReadiness = available(), transcription: DependencyReadiness = available(), library: DependencyReadiness = available(),
        samples: DependencyReadiness = available(), renderer: DependencyReadiness = available(), audio: DependencyReadiness = available()
    ) = RuntimeReadiness.of(
        RuntimeDependency.WORKER to worker, RuntimeDependency.TRANSCRIPTION to transcription,
        RuntimeDependency.SOUND_LIBRARY to library, RuntimeDependency.SAMPLES to samples,
        RuntimeDependency.RENDERER to renderer, RuntimeDependency.AUDIO_OUTPUT to audio
    )
    private fun available() = DependencyReadiness(DependencyStatus.READY, "ready")
    private fun unavailable(detail: String) = DependencyReadiness(DependencyStatus.UNAVAILABLE, detail)
}
