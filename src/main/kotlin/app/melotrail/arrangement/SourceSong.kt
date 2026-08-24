package app.melotrail.arrangement

import app.melotrail.harmony.ChordQuality
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

/**
 * Inspectable, MIDI-first source arrangement assembled from the currently
 * selected per-part MIDI. The sidecar deliberately retains occurrence context
 * that Standard MIDI Files cannot express without application-specific tags.
 */
@Serializable
data class SourceSong(
    val version: Int = VERSION,
    val contextSha256: String,
    val canonicalPpq: Int,
    val meterNumerator: Int,
    val meterDenominator: Int,
    val assembledMidi: WorkflowArtifactReference,
    val sections: List<SourceSongSection>,
    val fullMelody: SourceSongFullMelody
) {
    init {
        require(version == VERSION) { "Unsupported source-song version: $version" }
        require(SHA_256.matches(contextSha256)) { "Source-song context fingerprint is invalid" }
        require(canonicalPpq > 0 && meterNumerator in 1..32 && meterDenominator in setOf(1, 2, 4, 8, 16, 32)) { "Source-song timing is invalid" }
        require(sections.isNotEmpty()) { "Source song must contain at least one section" }
        require(sections.map { it.instance.instanceId }.distinct().size == sections.size) {
            "Source-song section instance IDs must be unique"
        }
        require(sections.first().startBar == 0L && sections.first().startTick == 0L) {
            "Source song must begin at bar and tick zero"
        }
        require(sections.zipWithNext().all { (left, right) ->
            left.endTick == right.startTick && left.endBar == right.startBar
        }) { "Source-song section bounds must be contiguous" }
        fullMelody.requireValid()
        require(fullMelody.grooveMap.ppq == canonicalPpq && fullMelody.grooveMap.meterDenominator == meterDenominator) {
            "Source-song full melody has incompatible grid evidence"
        }
        require(fullMelody.occurrences.map(FullMelodyOccurrenceWindow::occurrenceId) == sections.map { it.instance.instanceId } &&
            fullMelody.occurrences.zip(sections).all { (window, section) -> window.startTick == section.startTick && window.endTick == section.endTick && window.sectionRole == section.sectionRole }) {
            "Source-song full melody does not match its structured sections"
        }
    }

    companion object {
        const val VERSION = 2
        const val PROCESSOR_VERSION = "2"
        internal val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

/** One repeated structure occurrence together with its canonical musical context. */
@Serializable
data class SourceSongSection(
    /** The persisted occurrence identity used by arrangement and later stages. */
    val instance: SectionInstance,
    val sourcePartId: String,
    val sectionRole: SectionTypeId,
    /** One-based count within [sourcePartId], so A A B C B becomes A1/A2/B1/C1/B2. */
    val occurrenceNumber: Int,
    val startBar: Long,
    val endBar: Long,
    val startTick: Long,
    val endTick: Long,
    val sourceMidi: SourceSongMidiInput,
    val canonicalHarmony: List<SourceSongHarmonySpan>,
    /** Accepted source feel for this occurrence, or an explicit authoritative-grid fallback. */
    val groove: SourceSongGrooveEvidence = SourceSongGrooveEvidence.gridFallback()
) {
    init {
        require(instance.partId == sourcePartId) { "Source-song section source part must match its instance" }
        require(instance.instanceId.isNotBlank()) { "Source-song section requires a persisted occurrence ID" }
        require(occurrenceNumber > 0) { "Source-song occurrence number must be positive" }
        require(startBar >= 0 && endBar > startBar && startTick >= 0 && endTick > startTick) {
            "Source-song section bounds are invalid"
        }
        require(sourceMidi.partId == sourcePartId) { "Source-song MIDI source part must match its section" }
        require(canonicalHarmony.isNotEmpty()) { "Source-song section requires canonical harmony" }
        require(canonicalHarmony.all { it.occurrenceId == instance.instanceId && it.startTick >= startTick && it.endTick <= endTick }) {
            "Source-song harmony is outside its section"
        }
        groove.requireValid()
    }
}

/** Immutable prepared MIDI evidence copied into one source-song occurrence. */
@Serializable
data class SourceSongMidiInput(
    val partId: String,
    val projectRelativePath: String,
    val sha256: String,
    val ppq: Int,
    val kind: String,
    /** QP-005 binds the one-track candidate to its controller-aware reduction report. */
    val preparationReport: WorkflowArtifactReference? = null,
    /** QP-006 binds an occurrence-local harmony candidate to its QP-005 input and authority evidence. */
    val harmonyFitReport: WorkflowArtifactReference? = null
) {
    init {
        require(partId.isNotBlank() && projectRelativePath.isNotBlank() && SourceSong.SHA_256.matches(sha256) && ppq > 0 && kind.isNotBlank()) {
            "Source-song MIDI input is invalid"
        }
        if (kind == "MONOPHONIC_PREPARED") require(preparationReport != null) {
            "Monophonic prepared source MIDI requires its preparation report"
        }
        if (kind == "HARMONY_FITTED") require(preparationReport != null && harmonyFitReport != null) {
            "Harmony-fitted source MIDI requires monophonic and harmony-fit reports"
        }
    }
}

/** One authoritative harmony span carried into source-song evidence. */
@Serializable
data class SourceSongHarmonySpan(
    val occurrenceId: String,
    val bar: Long,
    val startTick: Long,
    val endTick: Long,
    val rootChromatic: Int,
    val rootSymbol: String,
    val quality: ChordQuality
) {
    init {
        require(occurrenceId.isNotBlank() && bar >= 0 && startTick >= 0 && endTick > startTick) {
            "Source-song harmony span is invalid"
        }
        require(rootChromatic in 0..11 && rootSymbol.isNotBlank()) { "Source-song harmony chord is invalid" }
    }
}

/** Fully resolved input for a deterministic source-song assembly. */
data class SourceSongAssemblyRequest(
    val root: Path,
    val contextSha256: String,
    val canonicalPpq: Int,
    val tempoBpm: Double,
    val meterNumerator: Int,
    val meterDenominator: Int,
    val sections: List<SourceSongSection>
) {
    /** Reject incomplete or non-authoritative input before any artifact is written. */
    fun requireValid() {
        require(SourceSong.SHA_256.matches(contextSha256) && canonicalPpq > 0) { "Source-song assembly identity is invalid" }
        require(tempoBpm.isFinite() && tempoBpm in 20.0..400.0) { "Source-song tempo is invalid" }
        require(meterNumerator in 1..32 && meterDenominator in setOf(1, 2, 4, 8, 16, 32)) { "Source-song meter is invalid" }
        require(sections.isNotEmpty() && sections.first().startTick == 0L && sections.first().startBar == 0L && sections.zipWithNext().all { (left, right) ->
            left.endTick == right.startTick && left.endBar == right.startBar
        }) { "Source-song assembly sections are invalid" }
    }
}

/** Content-addressed paths prevent a valid source-song candidate from being silently replaced. */
object SourceSongArtifactPaths {
    /** Return the canonical source-song MIDI location for one musical context. */
    fun midi(contextSha256: String): String = "source-song/v${SourceSong.VERSION}/${context(contextSha256)}/source-song.mid"

    /** Return the canonical inspectable sidecar location for one musical context. */
    fun metadata(contextSha256: String): String = "source-song/v${SourceSong.VERSION}/${context(contextSha256)}/source-song.json"

    /** Validate the hash segment used in a content-addressed artifact path. */
    private fun context(value: String): String {
        require(SourceSong.SHA_256.matches(value)) { "Source-song context fingerprint is invalid" }
        return value
    }
}

/** Result of persisting a source song and its metadata sidecar. */
data class SourceSongArtifact(val song: SourceSong, val metadataPath: Path)

/**
 * Copies selected source MIDI into occurrence-local tracks at their canonical
 * timeline offsets. Tempo and meter are replaced only by declared project
 * settings; no inferred source harmony is ever used.
 */
class SourceSongAssembler {
    /** Assemble or verify the immutable source-song artifact for [request]. */
    fun assemble(request: SourceSongAssemblyRequest): SourceSongArtifact {
        request.requireValid()
        val root = request.root.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "Source-song project root is missing" }
        val midiRelative = SourceSongArtifactPaths.midi(request.contextSha256)
        val metadataRelative = SourceSongArtifactPaths.metadata(request.contextSha256)
        val midiPath = root.resolve(midiRelative).normalize()
        val metadataPath = root.resolve(metadataRelative).normalize()
        require(midiPath.startsWith(root) && metadataPath.startsWith(root)) { "Source-song artifact escapes its project root" }
        require(Files.exists(midiPath) == Files.exists(metadataPath)) {
            "Source-song artifact is incomplete; preserving existing evidence for inspection"
        }
        Files.createDirectories(requireNotNull(midiPath.parent))

        val built = buildSequence(request, root)
        val temporaryMidi = Files.createTempFile(midiPath.parent, ".source-song-", ".mid")
        try {
            MidiSystem.write(built.sequence, 1, temporaryMidi.toFile())
            validateWrittenMidi(temporaryMidi, request, built.fullMelody)
            val midiSha256 = sha256(temporaryMidi)
            val song = SourceSong(
                contextSha256 = request.contextSha256,
                canonicalPpq = request.canonicalPpq,
                meterNumerator = request.meterNumerator,
                meterDenominator = request.meterDenominator,
                assembledMidi = WorkflowArtifactReference(midiRelative, midiSha256),
                sections = request.sections,
                fullMelody = built.fullMelody
            )
            publishOrVerify(temporaryMidi, midiPath, midiSha256, "MIDI")
            val serialized = JSON.encodeToString(SourceSong.serializer(), song)
            publishOrVerifyText(serialized, metadataPath, song)
            return SourceSongArtifact(song, metadataPath)
        } finally {
            Files.deleteIfExists(temporaryMidi)
        }
    }

    /** Create one conductor and one controller-free full melody track while retaining every occurrence's lineage. */
    private fun buildSequence(request: SourceSongAssemblyRequest, root: Path): BuiltSourceSong {
        val output = Sequence(Sequence.PPQ, request.canonicalPpq)
        val conductor = output.createTrack()
        conductor.add(MidiEvent(tempoMessage(request.tempoBpm), 0))
        conductor.add(MidiEvent(meterMessage(request.meterNumerator, request.meterDenominator), 0))
        val melody = output.createTrack()
        melody.add(MidiEvent(trackName("full-melody"), 0))
        val lineage = mutableListOf<FullMelodyNoteLineage>()
        val windows = mutableListOf<FullMelodyOccurrenceWindow>()
        request.sections.forEach { section ->
            val marker = markerText(section)
            conductor.add(MidiEvent(markerMessage(marker), section.startTick))
            val sourcePath = root.resolve(section.sourceMidi.projectRelativePath).normalize()
            require(sourcePath.startsWith(root) && Files.isRegularFile(sourcePath)) {
                "Source-song MIDI for '${section.instance.instanceId}' is missing"
            }
            require(sha256(sourcePath) == section.sourceMidi.sha256) {
                "Source-song MIDI for '${section.instance.instanceId}' changed after selection"
            }
            verifyPreparationEvidence(root, section.sourceMidi)
            val source = try {
                MidiSystem.getSequence(sourcePath.toFile())
            } catch (error: Exception) {
                throw IllegalArgumentException("Source-song MIDI for '${section.instance.instanceId}' is malformed", error)
            }
            require(source.divisionType == Sequence.PPQ && source.resolution == section.sourceMidi.ppq) {
                "Source-song MIDI for '${section.instance.instanceId}' has unsupported timing"
            }
            require(request.canonicalPpq % source.resolution == 0) {
                "Source-song PPQ cannot exactly represent '${section.instance.instanceId}'"
            }
            val scale = request.canonicalPpq / source.resolution
            val expectedLength = section.endTick - section.startTick
            require(Math.multiplyExact(source.tickLength, scale.toLong()) == expectedLength) {
                "Source-song timing does not match canonical bounds for '${section.instance.instanceId}'"
            }
            val notes = sourceNotes(source)
            val fitted = harmonyFittedNotes(root, section.sourceMidi)
            val fittedAnchorIds = fittedAnchorIds(root, section.sourceMidi)
            if (fitted != null) require(fitted.map(HarmonyFittedMelodyNote::noteId).size == notes.size) {
                "Harmony-fit note lineage does not match '${section.instance.instanceId}'"
            }
            notes.forEachIndexed { index, note ->
                val start = Math.addExact(section.startTick, Math.multiplyExact(note.startTick, scale.toLong()))
                val end = Math.addExact(section.startTick, Math.multiplyExact(note.endTick, scale.toLong()))
                require(start >= section.startTick && end <= section.endTick && end > start) { "Source-song note crosses '${section.instance.instanceId}' bounds" }
                melody.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, MELODY_CHANNEL, note.pitch, note.velocity), start))
                melody.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, MELODY_CHANNEL, note.pitch, 0), end))
                val sourceNoteId = fitted?.get(index)?.noteId ?: "n-${index.toString().padStart(5, '0')}"
                lineage += FullMelodyNoteLineage("fm-${section.instance.instanceId}-${index.toString().padStart(5, '0')}", section.instance.instanceId,
                    section.sourcePartId, sourceNoteId, start, end, note.pitch, note.velocity, sourceNoteId in fittedAnchorIds)
            }
            windows += FullMelodyOccurrenceWindow(section.instance.instanceId, section.sectionRole, section.sourcePartId, section.startBar, section.endBar,
                section.startTick, section.endTick, section.startTick, section.startTick, section.startTick, section.endTick, section.endTick, section.endTick,
                marker, section.sourceMidi.sha256, section.sourceMidi.preparationReport, section.sourceMidi.harmonyFitReport, section.groove)
        }
        val orderedLineage = lineage.sortedWith(compareBy<FullMelodyNoteLineage> { it.startTick }.thenBy { it.endTick }.thenBy { it.id })
        require(orderedLineage.zipWithNext().all { (left, right) -> left.endTick <= right.startTick }) { "Source-song full melody is not globally monophonic" }
        conductor.add(MidiEvent(endOfTrack(), request.sections.last().endTick))
        melody.add(MidiEvent(endOfTrack(), request.sections.last().endTick))
        val full = SourceSongFullMelody(melodyTrackName = "full-melody", occurrences = windows, noteLineage = orderedLineage,
            maximumPolyphony = maximumPolyphony(orderedLineage), controllerPolicy = FullMelodyControllerPolicy.CONTROLLER_FREE_CANONICAL_OUTPUT,
            grooveMap = FullSongGrooveMapBuilder.build(request.canonicalPpq, request.meterDenominator, windows))
            .also(SourceSongFullMelody::requireValid)
        return BuiltSourceSong(output, full)
    }

    /** Require QP-005/QP-006 evidence before a prepared melody can enter the assembled source song. */
    private fun verifyPreparationEvidence(root: Path, source: SourceSongMidiInput) {
        if (source.kind !in setOf("MONOPHONIC_PREPARED", "HARMONY_FITTED")) return
        val reference = requireNotNull(source.preparationReport)
        val reportPath = root.resolve(reference.file).normalize()
        require(reportPath.startsWith(root) && Files.isRegularFile(reportPath) && reportPath.toRealPath().startsWith(root.toRealPath())) {
            "Source-song monophonic preparation report is missing"
        }
        require(sha256(reportPath) == reference.sha256) { "Source-song monophonic preparation report changed after selection" }
        val report = JSON.decodeFromString(MonophonicMelodyPreparationReport.serializer(), Files.readString(reportPath))
        report.requireValid()
        require(report.status == MelodyPreparationStatus.COMPLETED && report.partId == source.partId) {
            "Source-song monophonic preparation report does not bind its part"
        }
        if (source.kind == "MONOPHONIC_PREPARED") require(report.output?.path == source.projectRelativePath && report.output.sha256 == source.sha256 && report.output.ppq == source.ppq) {
            "Source-song monophonic preparation report does not bind its MIDI input"
        }
        if (source.kind == "HARMONY_FITTED") verifyHarmonyFitEvidence(root, source, report)
    }

    /** Require QP-006 report evidence to bind its output to the verified QP-005 candidate. */
    private fun verifyHarmonyFitEvidence(root: Path, source: SourceSongMidiInput, preparation: MonophonicMelodyPreparationReport) {
        val reference = requireNotNull(source.harmonyFitReport)
        val reportPath = root.resolve(reference.file).normalize()
        require(reportPath.startsWith(root) && Files.isRegularFile(reportPath) && reportPath.toRealPath().startsWith(root.toRealPath())) {
            "Source-song harmony-fit report is missing"
        }
        require(sha256(reportPath) == reference.sha256) { "Source-song harmony-fit report changed after selection" }
        val report = JSON.decodeFromString(MelodyHarmonyFitReport.serializer(), Files.readString(reportPath))
        report.requireValid()
        require(report.status == MelodyHarmonyFitStatus.COMPLETED && report.context.partId == source.partId &&
            report.monophonicPreparationReport == source.preparationReport && report.input == preparation.output &&
            report.output?.path == source.projectRelativePath && report.output.sha256 == source.sha256 && report.output.ppq == source.ppq) {
            "Source-song harmony-fit report does not bind its MIDI input"
        }
    }

    /** Reparse the written file and enforce its single-track canonical full-melody contract. */
    private fun validateWrittenMidi(path: Path, request: SourceSongAssemblyRequest, fullMelody: SourceSongFullMelody) {
        val sequence = try { MidiSystem.getSequence(path.toFile()) }
        catch (error: Exception) { throw IllegalArgumentException("Assembled source-song MIDI is malformed", error) }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == request.canonicalPpq) {
            "Assembled source-song MIDI has invalid PPQ timing"
        }
        require(sequence.tickLength == request.sections.last().endTick) { "Assembled source-song MIDI has an invalid duration" }
        require(sequence.tracks.size == 2) { "Assembled source-song MIDI must contain one conductor and one full melody track" }
        val conductor = sequence.tracks.firstOrNull() ?: throw IllegalArgumentException("Assembled source-song MIDI has no conductor track")
        require(conductor.anyMeta(TEMPO, 0L) && conductor.anyMeta(TIME_SIGNATURE, 0L)) {
            "Assembled source-song MIDI is missing canonical tempo or meter"
        }
        require(fullMelody.occurrences.all { occurrence -> conductor.anyMeta(MARKER, occurrence.startTick, occurrence.markerText) }) {
            "Assembled source-song MIDI is missing a canonical section marker"
        }
        val melody = sequence.tracks[1]
        require(trackName(melody) == "full-melody" && (0 until melody.size()).none { index ->
            (melody[index].message as? ShortMessage)?.command == ShortMessage.CONTROL_CHANGE
        }) { "Assembled source-song MIDI has non-canonical melody controller state" }
        val notes = sourceNotes(sequence, melody)
        require(notes.size == fullMelody.noteLineage.size && maximumPolyphony(fullMelody.noteLineage) == 1 && notes.zipWithNext().all { (left, right) -> left.endTick <= right.startTick }) {
            "Assembled source-song MIDI violates canonical melody monophony"
        }
    }

    /** Publish one candidate once, or prove an immutable existing candidate is identical. */
    private fun publishOrVerify(temporary: Path, target: Path, expectedSha256: String, label: String) {
        if (Files.exists(target)) {
            require(Files.isRegularFile(target) && sha256(target) == expectedSha256) {
                "Existing source-song $label differs for this musical context; preserving the known-good candidate"
            }
            return
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target)
        }
    }

    /** Publish the sidecar once, or prove its structured content is identical. */
    private fun publishOrVerifyText(serialized: String, target: Path, expected: SourceSong) {
        if (Files.exists(target)) {
            val existing = JSON.decodeFromString(SourceSong.serializer(), Files.readString(target))
            require(existing == expected) { "Existing source-song metadata differs for this musical context; preserving the known-good candidate" }
            return
        }
        val temporary = Files.createTempFile(requireNotNull(target.parent), ".source-song-", ".json")
        try {
            Files.writeString(temporary, serialized)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Compare a MIDI meta-message type without exposing raw implementation details to callers. */
    private fun MetaMessage.typeIs(type: Int): Boolean = this.type == type

    /** Report whether a track contains one required conductor event at its exact tick. */
    private fun javax.sound.midi.Track.anyMeta(type: Int, tick: Long): Boolean =
        (0 until size()).map(::get).any { event -> event.tick == tick && (event.message as? MetaMessage)?.typeIs(type) == true }

    /** Report whether a track contains an exact text marker at one authoritative occurrence boundary. */
    private fun javax.sound.midi.Track.anyMeta(type: Int, tick: Long, text: String): Boolean =
        (0 until size()).map(::get).any { event -> event.tick == tick && (event.message as? MetaMessage)?.let { it.typeIs(type) && it.data.toString(Charsets.UTF_8) == text } == true }

    /** Encode the authoritative project tempo as a Standard MIDI File meta event. */
    private fun tempoMessage(bpm: Double): MetaMessage {
        val micros = (60_000_000.0 / bpm).toLong()
        require(micros in 1..0xFFFFFF) { "Source-song tempo cannot be encoded" }
        return MetaMessage().also { it.setMessage(TEMPO, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }
    }

    /** Encode the authoritative project meter as a Standard MIDI File meta event. */
    private fun meterMessage(numerator: Int, denominator: Int): MetaMessage = MetaMessage().also {
        it.setMessage(TIME_SIGNATURE, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4)
    }

    /** Give each copied track a human-inspectable occurrence/source identity. */
    private fun trackName(value: String): MetaMessage = MetaMessage().also {
        val bytes = value.toByteArray(Charsets.UTF_8)
        it.setMessage(TRACK_NAME, bytes, bytes.size)
    }

    /** Read the one human-inspectable track name carried by a conductor or canonical melody track. */
    private fun trackName(track: javax.sound.midi.Track): String? = (0 until track.size()).map(track::get).firstNotNullOfOrNull { event ->
        (event.message as? MetaMessage)?.takeIf { it.type == TRACK_NAME }?.data?.toString(Charsets.UTF_8)
    }

    /** Encode a stable occurrence/role/part marker on the conductor track. */
    private fun markerMessage(value: String): MetaMessage = MetaMessage().also {
        val bytes = value.toByteArray(Charsets.UTF_8)
        it.setMessage(MARKER, bytes, bytes.size)
    }

    /** Build the marker text persisted in both MIDI and the canonical full-melody sidecar. */
    private fun markerText(section: SourceSongSection): String =
        "occurrence=${section.instance.instanceId};role=${section.sectionRole.value};part=${section.sourcePartId}"

    /** Preserve trailing silent duration for every source occurrence track. */
    private fun endOfTrack(): MetaMessage = MetaMessage().also { it.setMessage(END_OF_TRACK, byteArrayOf(), 0) }

    /** Parse source note pairs while ignoring source controllers and meta events that cannot enter the canonical melody. */
    private fun sourceNotes(sequence: Sequence, track: javax.sound.midi.Track? = null): List<SourceNote> {
        val tracks = track?.let(::listOf) ?: sequence.tracks.toList()
        val active = mutableMapOf<Triple<Int, Int, Int>, java.util.ArrayDeque<Pair<Long, Int>>>()
        val notes = mutableListOf<SourceNote>()
        tracks.forEachIndexed { trackIndex, current ->
            (0 until current.size()).forEach { eventIndex ->
                val event = current[eventIndex]
                val message = event.message as? ShortMessage ?: return@forEach
                val key = Triple(trackIndex, message.channel, message.data1)
                when {
                    message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> active.getOrPut(key) { java.util.ArrayDeque() }.addLast(event.tick to message.data2)
                    message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                        val queue = active[key]
                        val start = if (queue.isNullOrEmpty()) null else queue.removeFirst()
                        if (queue?.isEmpty() == true) active.remove(key)
                        require(start != null && event.tick > start.first) { "Source-song MIDI has malformed note pairs" }
                        notes += SourceNote(start.first, event.tick, message.data1, start.second)
                    }
                }
            }
        }
        require(active.isEmpty()) { "Source-song MIDI has unclosed notes" }
        return notes.sortedWith(compareBy<SourceNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch })
    }

    /** Read the QP-006 report whose note IDs and protected anchors bind one harmony-fitted source MIDI file. */
    private fun harmonyFittedNotes(root: Path, source: SourceSongMidiInput): List<HarmonyFittedMelodyNote>? {
        if (source.kind != "HARMONY_FITTED") return null
        val reference = requireNotNull(source.harmonyFitReport)
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && sha256(path) == reference.sha256) { "Source-song harmony-fit report is missing or stale" }
        return JSON.decodeFromString(MelodyHarmonyFitReport.serializer(), Files.readString(path)).also(MelodyHarmonyFitReport::requireValid).outputNotes
    }

    /** Return protected QP-006 source-note IDs only after the report has already passed its lineage checks. */
    private fun fittedAnchorIds(root: Path, source: SourceSongMidiInput): Set<String> {
        if (source.kind != "HARMONY_FITTED") return emptySet()
        val reference = requireNotNull(source.harmonyFitReport)
        val path = root.resolve(reference.file).normalize()
        val report = JSON.decodeFromString(MelodyHarmonyFitReport.serializer(), Files.readString(path))
        report.requireValid()
        return report.protectedAnchorNoteIds.toSet()
    }

    /** Count full-melody sounding intervals with note-offs ordered before equal-tick note-ons. */
    private fun maximumPolyphony(notes: List<FullMelodyNoteLineage>): Int {
        var active = 0
        var maximum = 0
        notes.flatMap { listOf(it.startTick to 1, it.endTick to -1) }.sortedWith(compareBy<Pair<Long, Int>> { it.first }.thenBy { it.second }).forEach { (_, delta) ->
            active += delta; maximum = maxOf(maximum, active)
        }
        return maximum
    }

    private data class BuiltSourceSong(val sequence: Sequence, val fullMelody: SourceSongFullMelody)
    private data class SourceNote(val startTick: Long, val endTick: Long, val pitch: Int, val velocity: Int)

    /** Fingerprint a local MIDI artifact without loading it fully into memory. */
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").let { digest ->
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val JSON = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false; prettyPrint = true }
        const val TEMPO = 0x51
        const val TIME_SIGNATURE = 0x58
        const val END_OF_TRACK = 0x2F
        const val TRACK_NAME = 0x03
        const val MARKER = 0x06
        const val MELODY_CHANNEL = 0
    }
}
