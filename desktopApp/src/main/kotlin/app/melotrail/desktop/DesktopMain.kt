package app.melotrail.desktop

/** Compose Desktop launcher for the sole MIDI Core desktop graph. */
fun main(args: Array<String>) = MidiCoreDesktopEntrypoint.run(MidiCoreDesktopStartupCheck.fromArguments(args))
