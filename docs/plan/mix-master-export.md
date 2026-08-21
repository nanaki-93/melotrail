# Render, mix, style processing, master, and export

## Target sequence

```text
approved arrangement
 -> approved/bypassed cohesion
 -> approved/bypassed humanization
 -> generated occurrence MIDI/timeline
 -> rendered role stems
 -> persisted mix
 -> dry mix
 -> optional profile style processing
 -> master
 -> export and release manifest
```

## Reuse decisions

Keep the current sfizz/sample rendering boundary, instrument registry/license
checks, stem publication/hashing, persisted gain/pan/mute/solo settings,
deterministic mixing, worker mastering, MP3 export, and commercial readiness
checks. These are established capabilities and should change only at their
inputs or profile-policy seams.

## Render handoff

The renderer receives an immutable build manifest with:

- project/structure/occurrence IDs and hashes;
- exact approved arrangement, cohesion, and humanization run IDs;
- approved role-to-stable-instrument assignments, selection decision/registry
  hashes, engine type, verified capability snapshot, and asset/license hashes;
- tempo, meter, sample rate, bit depth, channel layout;
- expected role/stem IDs and timeline length.

Each stem artifact records role and instrument separately. Canonical role and
instrument IDs are used directly; old mix-setting aliases are not read. Missing
required renderers or assets fail Render without invalidating upstream MIDI. The renderer resolves
the exact approved stable instrument ID to its private engine descriptor; it must
not invoke the selection resolver or substitute a newly available candidate.

## Mix

Mix remains a user-authored configuration. Profile/mood may suggest initial
levels/pan, but a later profile update never overwrites saved user settings.

- Gain, pan, mute, and solo continue to operate on available stems.
- A mix revision records input stem hashes and normalized settings hash.
- Re-rendered unchanged stem IDs may reuse settings; changed/removed current
  roles require explicit review.
- The dry mix is reproducible from exact stems/settings/engine version.
- Mix processing must not be called AI correction or melody enhancement.

## Optional style processing

Move the current fixed Bedroom Lo-fi DSP behind a `StyleProcessingPolicy`
resolved from `CompositionProfile`. It is optional and independently bypassable.
For Lo-fi, tape/vinyl/noise/filter/saturation parameters are finishing texture,
not the primary definition of the style.

The style-processing run stores preset/policy version and all effective DSP
parameters. Disabling it selects the dry mix directly; it does not create a
duplicate “processed” file. Future profiles may define no audio style processing.

Review the current worker “repair” call and give it a precise, measurable role.
If it is format conditioning or safety limiting, name and test that behavior. Do
not retain a mandatory opaque repair stage solely because it exists.

## Master

Mastering consumes either dry mix or selected style-processed mix and owns final
loudness/peak/format conditioning. Store target/output measurements, worker/
algorithm version, input/output hashes, and warnings. Mastering never changes
project MIDI/harmony/arrangement selections.

## Export

Exports reference, rather than replace, the approved master. Each export records
format, codec/version, bitrate/sample settings, hash, timestamp, and selected
release lineage. WAV and MP3 remain initial outputs. Video preview remains an
adapter over an exact audio artifact.

Each audio export has a deterministic sibling `<export-base>-credits.txt` derived
from the immutable release manifest's final used-stem set. The generator maps
included stems to approved instrument license snapshots, excludes installed
candidates/unused roles/CC0 instruments, deduplicates identical attribution
blocks, sorts them deterministically, and atomically publishes/hash-records the
file. A CC0-only export contains only a no-instrument-attribution-required
statement. Missing required attribution blocks commercial-ready export.

“Used” means a rendered instrument stem included by the final resolved mix
(including solo/mute behavior). If zero contribution cannot be proven reliably,
include its attribution conservatively. Re-reading a changed live registry is
forbidden; credits use the frozen release license/provenance snapshots.

Commercial-ready is a validation result, not a legal guarantee. It requires
source attestations, known selected artifact lineage, sound/model dependency
evidence, and no stale required stage. Missing evidence blocks the label but
does not block private audition/export where current policy permits.

## Failure and cache behavior

- Renderer failure retries only missing/failed stems when manifest inputs match.
- Mix setting change reruns dry mix onward, not render.
- Style policy change reruns style processing/master/export.
- Master parameter change reruns master/export.
- Codec/export change reruns only export.
- Credits-policy/license-usage change reruns credits/release verification, not
  audio, unless the selected instrument or mix also changed.
- All outputs are atomically published; partial audio is not selected.

## Acceptance

Automated tests validate hashes, caching, canonical settings persistence,
clipping/silence bounds, worker protocols, and failure recovery. Manual release
acceptance must still render and listen to representative Original/Corrected/
Enhanced, cohesion, humanized, stem, dry, style-processed, and mastered artifacts
on documented equipment, then test the packaged app.

Every commercial-ready acceptance case also verifies that its credits artifact
is hash-paired to the audio, contains every and only required used-instrument
attribution, and remains reproducible after live registry changes.
