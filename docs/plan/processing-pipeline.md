# Import, MIDI, correction, and enhancement pipeline

## Stage graph

```text
SOURCE
  -> EXTRACTED
  -> CLEANED
  -> NORMALIZED
  -> TRANSPOSED
  -> CORRECTED
  -> ENHANCED (optional branch; OFF selects CORRECTED)
  -> ANALYZED
```

The UI may combine Cleaned/Normalized into one readable label, but persisted
stage identities and reports remain separate. Existing physical raw/clean paths
may be retained during migration; logical stage identity is authoritative.

## Stage-runner contract

The Kotlin application owns a persistent, per-project orchestrator:

1. Validate subject, prerequisites, permissions, and project-root paths.
2. Resolve exact upstream artifacts and immutable musical context.
3. Normalize configuration and compute a cache key.
4. Reuse a validated completed run when its cache key/output hash still match.
5. Persist `PROCESSING` before invoking processor/worker/model.
6. Write output to a unique temporary path under the project root.
7. Validate content, metadata, reports, and non-overwrite invariant.
8. Atomically publish a new artifact and completed record.
9. On error, remove/quarantine only the incomplete temp output and persist a
   structured failure. Never delete upstream completed artifacts.
10. Start the next eligible automatic stage or stop at review/input gates.

One orchestrator owns project-stage concurrency. Commands are idempotent by
subject/stage/config. A process crash leaves a `PROCESSING` record that recovery
marks interrupted/failed before retry; it never assumes completion from a file.

## Source and extraction

- Copy the selected source immutably and record hash, media type, original name,
  timestamp, source attestation, and import evidence.
- Direct MIDI is parsed/validated and published as Extracted without rewriting
  the source.
- Eligible WAV/WAVE/MP3 invokes existing inspection/preparation only when
  required, then Basic Pitch transcription. Prepared audio is a derived artifact.
- Initial audio scope remains solo-piano/melody material. Reject or warn on
  unsupported full mixes rather than imply stem/melody separation.
- Verify the current direct-MIDI/audio branch with characterization tests before
  altering it.

## Clean

Reuse worker `midi-clean` and `MidiQualityReport` for structural/event hygiene:

- invalid/orphan events;
- duplicate/retrigger artifacts;
- impossible/short durations and overlaps;
- sustain/control-event cleanup;
- velocity anomalies within conservative bounds;
- parseability/PPQ validity.

The chosen cleanup profile and worker version are in the run configuration. A
quality approval may remain for warnings, but automatic processing can continue
when deterministic acceptance thresholds pass.

## Normalize

Normalization creates consistent inputs without creative rewriting:

- retain or explicitly normalize PPQ using exact tick conversion;
- conservative quantization appropriate to transcription source/profile;
- timing/order canonicalization;
- note range and velocity normalization policies;
- tempo/meter map conformance or explicit warning;
- deterministic report of every changed event.

Do not combine profile groove/humanization into normalization. Swing and
performance looseness happen later.

## Transpose

Inputs are normalized MIDI, project key, detected source key evidence, and any
user confirmation. Behavior:

- compute tonic interval deterministically with enharmonic spelling preserved in
  metadata;
- transpose note pitches within configured playable range, selecting octave
  folds only through an explicit policy;
- validate scale/harmony fit and report outliers rather than silently quantizing
  every expressive non-scale note;
- keep percussion/unpitched channels unchanged;
- preserve timing, duration, velocity, controls, tempo, and meter;
- require user confirmation when source-key confidence is below threshold;
- record semitone interval, octave adjustments, warnings, input/output hashes,
  and algorithm version.

## Technical correction

Correction consumes transposed MIDI and context but is conservative. It may fix
clear transcription mistakes, collisions, range issues, broken note lengths,
velocity outliers, and strongly unsupported detected notes. It must not add
decorative passing notes or redesign phrases.

The initial implementation can be deterministic and rules-based. If AI is later
used, it returns a bounded correction plan with stricter limits than enhancement.
Every edit has reason/category/confidence. Low-confidence musical changes are
suggestions requiring approval or remain unchanged.

Migrate useful strict-plan/validator/applier infrastructure from `MidiAiFix`,
but do not carry forward its isolated MIDI key/chord inference as authority.

## Musical enhancement

Enhancement consumes corrected MIDI plus the exact `MusicalProcessingContext`.
It may improve phrase endings/flow/contour, reduce excessive repetition, adjust
severe chord clashes, or propose tasteful passing notes. It cannot change project
harmony, structure, instrument assignment, or source identity.

Intensity policy:

| Level | Selection/behavior |
| --- | --- |
| Off | select corrected artifact; no model call |
| Subtle | very small edit budget and identity-distance threshold; default |
| Balanced | moderate validated changes with explicit preview |
| Creative | largest bounded budget; still cannot bypass invariants/approval |

Model contract:

- versioned strict JSON schema;
- input/context/artifact hashes and subject IDs echoed and validated;
- code-owned operation vocabulary and numeric bounds;
- no paths, commands, executable content, or direct writes;
- deterministic applier produces draft plus edit report;
- validation includes timing, range, polyphony, chord/scale relationship, edit
  budget, identity distance, and recognizable anchor retention;
- model/provider/version/license/prompt-template version and seed recorded.

The first UI/domain milestone may use a transparent no-op or deterministic mock
for intensity levels. It must not label that behavior as advanced AI.

## Selection, comparison, and bypass

Source, Cleaned, Corrected, and Enhanced artifacts remain addressable. The part
stores an explicit selected branch. Switching to corrected bypasses enhancement
without deleting it. Preview/render caches identify the exact artifact hash so
the transport cannot play a stale version under the wrong label.

Changing project settings/harmony/profile/mood recomputes context hashes:

- key change stales Transposed and later;
- tempo/meter normalization-policy change stales Normalize and later;
- harmony/mood/enhancement-policy change stales Corrected/Enhanced according to
  declared processor dependencies, then downstream arrangement/build;
- display-only name changes do not stale audio/MIDI.

## Failure and recovery

Expose Pending, Processing, Completed, and Failed for every stage. Failure data
has safe code, summary, retryability, and diagnostic reference. UI retry defaults
to the failed stage. “Retry from extraction” is an advanced explicit action.

If Enhancement fails, Corrected remains selectable/ready and the user may choose
Off. If transcription fails, the preserved source remains and the user may retry
after worker setup. A changed source creates a new part or explicit source
revision; it never overwrites the old source in place.

## Worker versus Kotlin placement

| Capability | Initial owner | Reason |
| --- | --- | --- |
| inspect/prepare/transcribe | Python worker | existing audio/Basic Pitch dependencies |
| MIDI clean | Python worker behind Kotlin runner | existing deterministic implementation |
| normalize/transpose | Kotlin | existing JDK MIDI ecosystem and domain context |
| correction plan validation/application | Kotlin | safety, deterministic artifacts |
| local AI inference | model port or worker only if needed | replaceable computation boundary |
| enhancement validation/application | Kotlin | authorship and invariant enforcement |
| durable stage status/cache/retry | Kotlin application | canonical project ownership |

## Validation evidence

Each processor has fixture tests for valid MIDI, worker transcription MIDI,
tempo/meter maps, enharmonic keys, boundary notes, drums, empty/invalid tracks,
and intentional expressive notes. Assertions cover event deltas, hashes, reports,
determinism, source immutability, failure recovery, and cache invalidation.

