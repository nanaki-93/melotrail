package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path

/** Creates the complete cleanup provenance required by canonical schema-v4 tests. */
fun canonicalMidiReferences(
    projectRoot: Path,
    partId: String,
    rawReference: String = "midi/raw/$partId.mid",
    cleanReference: String = "midi/clean/$partId.mid"
): MidiReferences {
    val raw = projectRoot.resolve(rawReference)
    val clean = projectRoot.resolve(cleanReference)
    require(Files.isRegularFile(clean)) { "Canonical test clean MIDI is missing: $cleanReference" }
    if (!Files.exists(raw)) {
        Files.createDirectories(requireNotNull(raw.parent))
        Files.copy(clean, raw)
    }
    val cleanup = MidiCleanupOptions()
    val report = MidiQualityReporter().report(partId, raw, clean, cleanup)
    val reportPath = MidiQualityReportStore.write(projectRoot, report)
    val reportReference = projectRoot.relativize(reportPath).toString()
    return MidiReferences(
        raw = rawReference,
        clean = cleanReference,
        cleanup = cleanup,
        quality = reportReference,
        cleanApproval = MidiQualityReportStore.approval(projectRoot, reportReference, report)
    )
}
