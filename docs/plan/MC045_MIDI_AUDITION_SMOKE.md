# MC-045 MIDI audition smoke record

MIDI audition is a non-authoritative local preview. Melotrail sends the
authoritative MIDI timing, meter, channels, and note events to either its
managed built-in synthesizer or an explicitly selected external MIDI receiver.
The endpoint and any downstream DAW own the audible timbre. Audition never
writes WAV, MP3, rendered audio, sound-library data, or project mutations.

## Supported output behavior

- **Built-in synthesizer** is the audible fallback when no dedicated device is
  selected. The app opens and closes that device with the audition session.
- Receiver-capable local devices are discovered on scope selection and can be
  selected from Review. A live switch resumes only the transient audition at
  the adapter's current tick with the selected loop, mute, and solo settings.
- If a device is unavailable or lost, the transport closes its sequencer,
  transmitter, and receiver, sends best-effort all-notes-off messages, leaves
  project data unchanged, and gives the user a reconnect-or-select-another-
  device retry action.
- Preview timbre is deliberately not a Melotrail authority or export promise.
  Choose instruments, audio rendering, mixing, and mastering in Logic Pro after
  MIDI export.

## Local smoke procedure

1. Start the desktop app with `./gradlew :desktopApp:run` and open a MIDI Core
   project that has an auditionable source or accepted arrangement.
2. In Review, start playback, seek, pause/resume, enable/clear a loop, and
   toggle one mute and solo. Confirm no hanging note remains after each stop.
3. Choose a discovered receiver, then choose **Use built-in synthesizer**. While
   playing, verify each switch continues from the same musical tick and only
   the audition session is replaced.
4. Disconnect or make the chosen receiver unavailable when possible. Verify
   the actionable error, reconnect or select the built-in fallback, retry, and
   confirm the source, project revision, and accepted candidates are unchanged.
5. Close the desktop window while playback is active. Confirm output stops
   cleanly and reopening the project shows no transport-side project mutation.

## Recorded local JVM smoke

Date: 2026-08-28

The local JVM enumerated receiver-capable `Gervill`, `Real Time Sequencer`, and
`Logic Pro Virtual In` devices. The original direct default-receiver
NOTE_ON/NOTE_OFF smoke completed with `DEFAULT_MIDI_OUTPUT_SMOKE=PASS`, but it
proved message routing rather than audible output. On this host,
`MidiSystem.getReceiver()` resolved to `Logic Pro Virtual In`; with Logic not
monitoring that port, the app reported playback while producing no sound.
`Logic Pro Virtual Out` was correctly excluded because it exposes no receivers.

## MC-048B correction

Date: 2026-09-03

The no-selection path now calls `MidiSystem.getSynthesizer()`, opens the managed
device, obtains its receiver, and closes both receiver and device with the
session. On the same macOS host this resolved to `Gervill`
(`com.sun.media.sound.SoftSynthesizer`); it opened successfully and returned a
`com.sun.media.sound.SoftReceiver`. A direct NOTE_ON/NOTE_OFF through that
receiver completed with `BUILT_IN_SYNTH_NOTE_SMOKE=PASS`, and device closure was
confirmed. Regression coverage also proves device open/receiver/close ownership
and the real import-to-Play workflow. Explicit
external receivers remain selectable. This corrects the earlier smoke's false
assumption that the JVM default receiver was necessarily audible.
