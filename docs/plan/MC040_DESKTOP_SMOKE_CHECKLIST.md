# MC-040 focused desktop smoke checklist

This checklist covers local desktop behavior that a headless Compose test cannot
observe. It requires no DAW and creates no export outside the selected MIDI Core
project folder. Final MIDI-device lifecycle evidence remains owned by MC-045.

## Setup

1. Start the desktop application with `./gradlew :desktopApp:run`.
2. Create a new empty MIDI Core project folder and choose one valid `.mid` or
   `.midi` file with the native file chooser.
3. Keep the source file outside the project folder so import preservation is
   observable.

## Smoke path

1. Confirm the navigation exposes exactly Project, MIDI, Structure & Harmony,
   Arrange, Review, and Export; no audio, mix, master, library, video, release,
   worker, or settings route is reachable.
2. Create the project, import the source, select one track/channel as the
   protected melody, and confirm fixed tempo, meter, key, and mode.
3. Save one contiguous occurrence and complete its explicit chord coverage.
4. Generate and explicitly accept one Chords, Bass, and Drums candidate for the
   occurrence. Verify each action remains responsive and that cancellation, when
   offered, leaves the last known-good state visible.
5. In Review, start and stop a candidate or accepted-arrangement MIDI audition.
   If no local MIDI output is available, verify the page reports a recoverable
   device blocker and the project remains unchanged.
6. In Export, verify the project-owned `exports/` directory, exact MIDI and
   manifest filenames, immutable snapshot explanation, hashes, and DAW import
   guidance. Publish once, reveal the package folder, then publish again and
   verify the first package remains present under its original snapshot ID.
7. Close and reopen the project through Project. Verify authority, accepted
   candidates, and the latest export snapshot are restored.

## Record

Record the operating-system version, the observed chooser/device behavior, any
recoverable error text, project and export snapshot paths, and the reviewer/date
in the MC-040 execution-log entry. Do not record DAW import results here; that
evidence belongs to MC-048.
