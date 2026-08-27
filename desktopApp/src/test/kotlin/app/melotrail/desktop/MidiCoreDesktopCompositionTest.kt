package app.melotrail.desktop

import app.melotrail.application.MidiCoreAcceptedSongAssembly
import app.melotrail.application.MidiCoreAuthoritativeHarmony
import app.melotrail.application.MidiCoreCandidateGeneration
import app.melotrail.application.MidiCoreCandidateReview
import app.melotrail.application.MidiCoreMelodySelection
import app.melotrail.application.MidiCoreMidiPackageExporter
import app.melotrail.application.MidiCoreMusicalAuthority
import app.melotrail.application.MidiCoreProjectLifecycle
import app.melotrail.application.MidiCoreSourceImport
import app.melotrail.application.MidiCoreStructureTimeline
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.Test

class MidiCoreDesktopCompositionTest {
    @Test
    fun `target composition wires every MIDI Core use case without starting external services`() {
        val services = MidiCoreDesktopComposition.create(
            artifacts = MidiCoreArtifactStore(),
            dialogs = NullMidiCoreDesktopFileDialogs,
            preferences = NoOpMidiCoreDesktopPreferences,
            logger = NoOpDesktopOperationLogger,
        )

        assertIs<MidiCoreProjectLifecycle>(services.project)
        assertIs<MidiCoreSourceImport>(services.sourceImport)
        assertIs<MidiCoreMelodySelection>(services.melodySelection)
        assertIs<MidiCoreMusicalAuthority>(services.authority)
        assertIs<MidiCoreStructureTimeline>(services.structure)
        assertIs<MidiCoreAuthoritativeHarmony>(services.harmony)
        assertIs<MidiCoreCandidateGeneration>(services.generation)
        assertIs<MidiCoreCandidateReview>(services.review)
        assertIs<MidiCoreAcceptedSongAssembly>(services.assembly)
        assertIs<MidiCoreMidiPackageExporter>(services.export)
        assertEquals(NoOpMidiCoreDesktopPreferences, services.preferences)
        assertTrue(services.audition.state.sessionId == null)
        assertFalse(services.audition.state.isClosed)
        services.audition.close()
        assertTrue(services.audition.state.isClosed)
    }

    @Test
    fun `default main delegates to target entrypoint and target graph has no worker construction`() {
        val mainSource = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/DesktopMain.kt"))
        val defaultMain = mainSource.substringAfter("fun main()").substringBefore("/**")
        assertTrue(defaultMain.contains("MidiCoreDesktopEntrypoint.run()"))
        assertFalse(defaultMain.contains("WorkerClient"))
        assertFalse(defaultMain.contains("WorkspaceViewModel"))
        assertFalse(defaultMain.contains("DefaultArrangementApplicationService"))

        val targetSource = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreDesktopComposition.kt"))
        assertFalse(targetSource.contains("WorkerClient"))
        assertFalse(targetSource.contains("DefaultBuildApplicationService"))
        assertFalse(targetSource.contains("DefaultMixApplicationService"))
        assertFalse(targetSource.contains("LocalQwen"))
    }

    private fun sourceFile(relativePath: String): Path = sequenceOf(
        Path.of(relativePath),
        Path.of("desktopApp").resolve(relativePath),
    ).first { Files.isRegularFile(it) }
}

private object NullMidiCoreDesktopFileDialogs : MidiCoreDesktopFileDialogs {
    override suspend fun chooseProjectDirectory(): Path? = null
    override suspend fun chooseNewProjectDirectory(): Path? = null
    override suspend fun chooseMidiSource(): Path? = null
    override suspend fun chooseExportDirectory(): Path? = null
}
