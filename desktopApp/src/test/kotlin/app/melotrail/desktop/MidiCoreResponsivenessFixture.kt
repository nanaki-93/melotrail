package app.melotrail.desktop

import app.melotrail.application.*
import app.melotrail.audition.*
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.music.core.ProjectMeter
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.awt.EventQueue
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.FutureTask
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.math.ceil
import kotlin.test.assertEquals

/** Owned SMF, real desktop service graph and real Swing/IO dispatchers. Only the output is silent. */
internal class MidiCoreResponsivenessFixture(
    val bars: Int = 64,
    val numerator: Int = 4,
    val denominatorExponent: Int = 2,
    val notesPerBar: Int = 4,
    val sectionBars: Int = 8,
    val longNames: Boolean = false,
    decorate: (MidiCoreWorkspaceUseCases) -> MidiCoreWorkspaceUseCases = { it },
) : AutoCloseable {
    val directory: Path = Files.createTempDirectory("melotrail-u07-")
    val projectRoot: Path = directory.resolve("project")
    val source: Path = directory.resolve("owned-source.mid")
    val ticksPerBar: Long = 480L * 4 * numerator / (1L shl denominatorExponent)
    val output = SilentMeasuredMidiOutput()
    private val controller = MidiAuditionController(output)
    private val services = MidiCoreDesktopComposition.create(
        preferences = NoOpMidiCoreDesktopPreferences,
        logger = NoOpDesktopOperationLogger,
    )
    private val useCases = decorate(object : MidiCoreWorkspaceUseCases by services.workspace {
        override val audition: MidiAuditionPort = controller
    })
    val workspace = onEdt { MidiCoreWorkspaceViewModel(useCases) }

    fun prepare() {
        require(bars % sectionBars == 0 && ticksPerBar % notesPerBar == 0L)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, 0xa1.toByte(), 0x20), 3), 0))
        track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(numerator.toByte(), denominatorExponent.toByte(), 24, 8), 4), 0))
        val step = ticksPerBar / notesPerBar
        repeat(bars * notesPerBar) { index ->
            val pitch = 84 + listOf(0, 4, 7, 4)[index % 4]
            val tick = index * step
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 96), tick))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), tick + step))
        }
        MidiSystem.write(sequence, 1, source.toFile())
        val name = if (longNames) "A very long song name — café 夜明け ".repeat(6).take(120) else "U07 owned $bars-bar fixture"
        apply(MidiCoreWorkspaceIntent.CreateProject(projectRoot, name))
        apply(MidiCoreWorkspaceIntent.ImportSource(source))
        // Imported suggestions are not authority. Exercise the same explicit edit/confirm
        // boundary as a musician; the workspace intentionally starts with a 4/4 draft.
        val meter = ProjectMeter(numerator, denominatorExponent)
        onEdt {
            workspace.accept(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(
                workspace.state.value.authority.draft.copy(meter = meter),
            ))
        }
        apply(MidiCoreWorkspaceIntent.ConfirmAuthority)
        assertEquals(meter, workspace.state.value.authority.confirmed?.meter,
            "The fixture must confirm its declared meter before laying out sections")
        val label = if (longNames) "Long repeated section — café 夜明け ".repeat(4).take(112) else "Section"
        apply(MidiCoreWorkspaceIntent.ReplaceStructure(
            listOf(ProjectSectionDefinition("section", label)),
            List(bars / sectionBars) { index ->
                MidiCoreBarOccurrencePlacement("section-$index", "section", "$label ${index + 1}", sectionBars)
            },
        ))
        apply(MidiCoreWorkspaceIntent.ReplaceHarmony(List(bars / sectionBars) { index ->
            AuthoritativeChordEvent("chord-$index", "section-$index", "C",
                index * sectionBars * ticksPerBar, (index + 1) * sectionBars * ticksPerBar)
        }))
    }

    fun confirmPlan() {
        apply(MidiCoreWorkspaceIntent.ProposeArrangementPlan("steady-road"))
        apply(MidiCoreWorkspaceIntent.ConfirmArrangementPlan)
    }

    fun start(intent: MidiCoreWorkspaceIntent): Long = onEdt {
        workspace.accept(intent)
        workspace.state.value.operation.id
    }

    fun await(id: Long): MidiCoreWorkspaceState = runBlocking {
        withTimeout(120_000) { workspace.state.first { it.operation.id == id && !it.operation.active } }
    }

    /** Wall time to published terminal UI state; excludes later paint and cannot measure sound. */
    fun apply(intent: MidiCoreWorkspaceIntent): Double {
        val startedAt = System.nanoTime()
        val state = await(start(intent))
        assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, state.operation.phase,
            "$intent: ${state.operation.message}; ${state.blockers}")
        return elapsedMs(startedAt)
    }

    fun awaitSourceProjection(): MidiCoreWorkspaceState = runBlocking {
        withTimeout(30_000) { workspace.state.first { it.visualEvidence?.source is MidiCoreVisualEvidence.Available } }
    }

    fun assertSourcePreserved() {
        assertEquals(U07Evidence.sha256(Files.readAllBytes(source)),
            U07Evidence.sha256(Files.readAllBytes(projectRoot.resolve("source/original.mid"))))
    }

    override fun close() {
        onEdt { workspace.close() }
        services.audition.close()
        assertEquals(0, output.activeSessions, "Closing the workspace releases the sole output")
        // Only this fixture's newly created temporary directory is eligible for cleanup.
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}

/** Keep the production single-session controller; substitute only the physical receiver. */
internal class SilentMeasuredMidiOutput : MidiAuditionOutput {
    @Volatile var activeSessions = 0
        private set
    @Volatile var maximumSessions = 0
        private set
    @Volatile var lastPlan: MidiAuditionPlaybackPlan? = null
        private set

    override fun open(plan: MidiAuditionPlaybackPlan, listener: MidiAuditionOutputListener): MidiAuditionOutputSession {
        lastPlan = plan
        activeSessions++
        maximumSessions = maxOf(maximumSessions, activeSessions)
        check(activeSessions == 1) { "Preview output sessions overlapped" }
        return object : MidiAuditionOutputSession {
            var tick = plan.startTick
            var closed = false
            override fun play() = Unit
            override fun pause() = Unit
            override fun stop() = Unit
            override fun positionTick() = tick
            override fun seek(tick: Long) { this.tick = tick }
            override fun setLoop(loop: MidiAuditionLoop?) = Unit
            override fun setMutedRoles(roles: Set<MidiExportRole>) = Unit
            override fun setSoloRoles(roles: Set<MidiExportRole>) = Unit
            override fun close() { if (!closed) { closed = true; activeSessions-- } }
        }
    }
}

internal fun <T> onEdt(block: () -> T): T {
    if (EventQueue.isDispatchThread()) return block()
    val task = FutureTask(block)
    EventQueue.invokeAndWait(task)
    return task.get()
}

internal fun elapsedMs(startNanos: Long): Double = (System.nanoTime() - startNanos) / 1_000_000.0

internal object U07Evidence {
    val root: Path = Path.of("build/test-results/u07").toAbsolutePath()
    private val json = Json { prettyPrint = true }

    fun write(path: Path, value: JsonElement) {
        Files.createDirectories(path.parent)
        Files.writeString(path, json.encodeToString(JsonElement.serializer(), value))
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun repository(): Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().let {
        if (Files.isRegularFile(it.resolve("settings.gradle.kts"))) it else it.parent
    }.also { check(Files.isRegularFile(it.resolve("settings.gradle.kts"))) }

    fun command(vararg args: String): String {
        val process = ProcessBuilder(*args).directory(repository().toFile()).redirectErrorStream(true).start()
        val result = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Evidence command failed: ${args.first()}: $result" }
        return result.trim()
    }

    fun identity(): JsonObject = buildJsonObject {
        put("recordedAt", Instant.now().toString())
        put("base", command("git", "rev-parse", "HEAD"))
        put("gitDiffSha256", sha256(command("git", "diff", "--binary", "HEAD").encodeToByteArray()))
        // Git diff omits new untracked source. Bind every relevant input byte as well.
        val paths = command("git", "ls-files", "--cached", "--others", "--exclude-standard", "--",
            "src", "desktopApp/src", "docs/UI_GUIDELINE.md", "docs/VALIDATION.md", "docs/pictures/UI",
            "build.gradle.kts", "desktopApp/build.gradle.kts", "settings.gradle.kts", "gradle.properties")
            .lines().filter(String::isNotBlank).distinct().sorted()
        val inputs = paths.joinToString("\n") { path ->
            val file = repository().resolve(path)
            "$path ${if (Files.isRegularFile(file)) sha256(Files.readAllBytes(file)) else "DELETED"}"
        }
        put("inputTreeSha256", sha256(inputs.encodeToByteArray()))
        put("inputFiles", inputs)
    }

    fun machine(): JsonObject = buildJsonObject {
        listOf("os.name", "os.version", "os.arch", "java.runtime.version", "java.vm.name", "java.vendor")
            .forEach { put(it, System.getProperty(it)) }
        put("processors", Runtime.getRuntime().availableProcessors())
        put("maximumHeapBytes", Runtime.getRuntime().maxMemory())
        put("vmArguments", ManagementFactory.getRuntimeMXBean().inputArguments.joinToString(" "))
        if (System.getProperty("os.name") == "Mac OS X") {
            put("hardwareModel", command("/usr/sbin/sysctl", "-n", "hw.model"))
            put("cpu", command("/usr/sbin/sysctl", "-n", "machdep.cpu.brand_string"))
            put("physicalMemoryBytes", command("/usr/sbin/sysctl", "-n", "hw.memsize"))
        }
        put("fileStoreType", Files.getFileStore(root.parent.also { Files.createDirectories(it) }).type())
        put("diskPolicy", "Fresh owned temporary project per sample; OS filesystem cache and JVM JIT are not flushed")
        put("physicalDiskCache", "UNMEASURED")
    }

    fun p95(samples: List<Double>): Double {
        require(samples.isNotEmpty() && samples.all { it.isFinite() && it >= 0 })
        return samples.sorted()[ceil(samples.size * 0.95).toInt() - 1]
    }

    fun series(samples: List<Double>, targetMs: Double? = null): JsonObject = buildJsonObject {
        put("unit", "milliseconds")
        put("samples", JsonArray(samples.map(::JsonPrimitive)))
        put("count", samples.size)
        put("p95NearestRank", p95(samples))
        put("maximum", samples.max())
        if (targetMs != null) {
            put("targetP95", targetMs)
            put("targetStatus", if (p95(samples) <= targetMs) "WITHIN_TARGET" else "MISSED_REQUIRES_PROFILING_AND_BUDGET_DECISION")
        }
    }
}
