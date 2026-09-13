package app.melotrail.application

import app.melotrail.project.ExportedFileKind
import app.melotrail.project.MidiCoreExportSnapshot
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Path

/** The entire read-only contract passed to an independently installed companion. */
data class MidiCoreExportHandoffReference(val manifest: Path, val sha256: String, val snapshotId: String)

class MidiCoreExportHandoff(private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore()) {
    /** Recheck disk state at dispatch, under the same lock as project mutations. No files are written. */
    fun launch(
        session: MidiCoreProjectSession,
        snapshot: MidiCoreExportSnapshot,
        dispatch: (MidiCoreExportHandoffReference) -> Unit,
    ) = MidiCoreProjectWriteCoordinator.withLock(session.root) {
        val current = artifacts.openProject(session.root)
        require(current == session.project) { "The project changed. Reload it before opening TABI." }
        require(current.exportSnapshots.singleOrNull { it.id == snapshot.id } == snapshot && snapshot.isCurrent(current)) {
            "This snapshot is earlier accepted work. Publish a current MIDI package before opening TABI."
        }
        val manifest = snapshot.files.single { it.kind == ExportedFileKind.MANIFEST }.artifact
        val path = artifacts.verify(session.root, manifest).toRealPath()
        dispatch(MidiCoreExportHandoffReference(path, manifest.sha256, snapshot.id))
    }
}
