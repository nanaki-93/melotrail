# MIDI Core troubleshooting

Status: target workflow plus migration notes

## Repository is still in migration

The current checkout may still expose worker, audio, render, mix, or release
commands. They belong to the superseded runtime and will disappear during the
ordered cleanup. Do not install Python or a sound library for new MIDI Core
work.

Use the root [plan](../PLAN.md) and target [documentation index](README.md) when deciding
whether a problem belongs to the new product.

## Build or tests fail

Run:

```bash
make test
make build
```

If Gradle reports a Java toolchain problem, verify that JDK 21 is installed and
selected. If a failure comes only from an obsolete worker/documentation/audio
contract during migration, do not add a compatibility fix; resolve it in the
task that removes or replaces its owner.

## Desktop does not start

Run:

```bash
make desktop
```

Capture the Gradle failure and Compose stack trace. The target desktop must not
require a worker health check, Python process, renderer, SFZ path, or network
connection.

## MIDI file is rejected

Confirm:

- extension is `.mid` or `.midi`;
- file is SMF format 0 or 1;
- timing uses PPQ rather than SMPTE;
- tempo and meter do not change;
- exactly one track contains notes and those notes use one MIDI channel; and
- the import report identifies a blocking condition rather than an advisory.

Do not repair or overwrite the source manually inside the project. Preserve the
original and, if necessary, export a simplified MIDI selection from Logic Pro.

## No melody track is available

Inspect track note counts, channel use, pitch ranges, and names. Export the
complete song from Logic Pro with exactly one note-bearing track/channel;
meta-only conductor tracks are allowed. Melotrail protects that melody
automatically. Multi-track or multi-channel melody is outside V1 and must be
simplified in the source DAW.

## Structure or harmony blocks generation

Check that:

- occurrences are ordered and each has a positive whole-bar length;
- section bar counts total the imported melody length exactly;
- chord events cover the intended window;
- every chord symbol parses and can be realized; and
- the melody lies inside the intended occurrence range.

An out-of-key chord is not itself an error. The project harmony is
authoritative.

## MIDI audition is unavailable

- Confirm that a supported local MIDI output or synthesizer is available.
- Stop other playback and retry device selection.
- Reopen the project if the OS removed a device while it was active.
- Use Stop before switching output devices.

Audition failure must not block editing or export once the arrangement is
otherwise valid. It must not trigger the old audio renderer.

## Candidate is stale

Inspect which authority hash member changed. Regenerate only the affected role
and occurrence, then explicitly accept the new candidate. Do not copy or rename
an old candidate file to make it current.

## Candidate generation is rejected

Open its validation report and distinguish hard role violations from musical
advisories. Change the scoped pattern/profile/seed or correct invalid authority.
Do not weaken global validation to admit one result.

## Export fails

Check:

- every enabled core role has a current accepted candidate;
- referenced source/candidate digests match;
- no accepted candidate is stale;
- destination is writable and does not require silent overwrite;
- staged files pass semantic re-import; and
- manifest paths remain relative.

A failed staged directory is diagnostic output only and must not be marked as a
complete export.

## DAW import differs

Use [DAW compatibility](DAW_COMPATIBILITY.md):

- import at song start;
- record exact DAW/macOS versions;
- check whether the DAW adopted or retained tempo;
- compare track names, channels, first/last ticks, and role files;
- assign instruments manually; and
- classify the result as pass, conditional pass, or fail.

Preview timbre differences are expected. Timing, note, role, or stuck-note
differences are not.

## Old audio project does not open

This is intentional. Old audio projects are unsupported and are not migrated.
Repository-owned old project data is deleted during the cleanup phase after
exact targets are verified.

If a melody is worth retaining, export or locate its original Standard MIDI
source outside the destructive cleanup target and create a new MIDI Core
project.
