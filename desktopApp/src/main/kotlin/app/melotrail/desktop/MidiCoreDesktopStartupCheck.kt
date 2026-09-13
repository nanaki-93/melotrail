package app.melotrail.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.Properties

/** Explicit, bounded installed-launcher check. Ordinary launches never create an evidence directory. */
class MidiCoreDesktopStartupCheck private constructor(private val directory: Path) {
    fun createServices(): MidiCoreDesktopServices = MidiCoreDesktopComposition.create(
        preferences = NoOpMidiCoreDesktopPreferences,
    )

    /** Called only after the real window has composed frames and its workspace has closed. */
    fun complete(width: Int, height: Int, showing: Boolean, auditionClosed: Boolean) {
        check(showing && width >= 720 && height >= 620) { "Startup window is not realized at its supported size" }
        check(auditionClosed) { "Startup check did not release the persistent MIDI session" }
        val report = Properties().apply {
            setProperty("status", "READY")
            setProperty("entrypoint", "app.melotrail.desktop.DesktopMainKt")
            setProperty("preferences", "ISOLATED_NO_OP")
            setProperty("window.width", width.toString())
            setProperty("window.height", height.toString())
            setProperty("window.showing", showing.toString())
            setProperty("audition.closed", auditionClosed.toString())
            setProperty("destinations", midiCoreWorkspaceDestinations.joinToString(",") { it.route })
            setProperty("codeSource", Path.of(MidiCoreDesktopStartupCheck::class.java.protectionDomain.codeSource.location.toURI()).toString())
            listOf("java.home", "java.runtime.version", "java.vendor", "os.name", "os.version", "os.arch")
                .forEach { setProperty(it, System.getProperty(it)) }
            setProperty("onscreenCompositorCapture", "NOT_MEASURED")
            setProperty("audiblePlayback", "NOT_MEASURED")
        }
        Files.newOutputStream(directory.resolve("startup.properties"), CREATE_NEW).use {
            report.store(it, "Installed native launcher startup; not human visual or listening approval")
        }
    }

    companion object {
        fun fromArguments(args: Array<String>): MidiCoreDesktopStartupCheck? {
            if (args.isEmpty()) return null
            require(args.size == 2 && args[0] == "--startup-check") {
                "Usage: Melotrail [--startup-check <new-evidence-directory>]"
            }
            val directory = Path.of(args[1]).toAbsolutePath().normalize()
            // Reserve a new directory before changing any process state; never reuse an earlier packet.
            Files.createDirectory(directory)
            val home = Files.createDirectory(directory.resolve("home"))
            System.setProperty("user.home", home.toString())
            return MidiCoreDesktopStartupCheck(directory)
        }
    }
}
