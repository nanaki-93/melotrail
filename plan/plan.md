# Melotrail — Master Implementation Plan

## Goal

Rename the complete product to **Melotrail**, repair every playback path, align
the Compose Desktop UI closely with `plan/UI.png`, make backend work and
severity visible, and extend the creation pipeline with explicit MIDI repair,
an 80 BPM lo-fi rhythm treatment, and an AI cohesion pass that may safely
transpose, time-adjust, repair, and patch melodies.

The resulting release workflow must preserve enough rights, model, sample, and
generation provenance to support a creator who intends to monetize original
work on YouTube. Melotrail can help demonstrate provenance and flag risks, but
it must never claim to guarantee copyright ownership, Content ID clearance, or
YouTube Partner Program acceptance.

## Confirmed product decisions

- Product, Gradle project, desktop package, CLI, documentation, local repository
  directory, settings, and diagnostics use the Melotrail name.
- The conventional Kotlin root package and Gradle group will be `app.melotrail`.
  The old `ai.music.workstation` namespace remains only in explicit migration
  tests or compatibility readers.
- The second header line to remove is the workflow-status row containing labels
  such as `Project · Complete`, `Prepare · Current`, and `Structure · Blocked`.
- `plan/UI.png` is the visual reference. Exact feature parity is not required,
  but navigation, hierarchy, density, transport placement, and color language
  should remain as close to it as the implemented product scope permits.
- All preview and release players are currently considered broken until proven
  by automated device-boundary tests and manual listening checks.
- The first MIDI lo-fi profile is fixed at 80 BPM with a bounded swing setting.
  The artifact schema must be versioned so tempo and intensity can become user
  controls later without rewriting existing projects.
- AI cohesion may transpose and time-adjust a melody and may repair or patch
  missing/invalid musical material. Source, raw MIDI, and repaired MIDI remain
  immutable; approved changes are published as derived, auditable artifacts.
- YouTube monetization is a release constraint: use only inputs, models, sound
  libraries, and samples with known commercial terms; record transformations;
  and provide an AI-use disclosure reminder for AI-generated music.

## Current-state findings

- Part preview and dry/lo-fi/master playback share one `JvmAudioPlayer` while
  the UI maintains separate preview and playback state. The active sound source
  is therefore ambiguous.
- Prepared-audio preview retry loses the prepared/original choice, and a stopped
  or completed preview cannot be restarted from its transport.
- The UI exposes overlapping transports, workflow actions, readiness messages,
  retries, and status information in multiple panels.
- Progress is detailed for only some operations. Inspection, cleanup,
  transcription, hydration, approval, and parts of preview show generic or no
  backend activity.
- Deterministic MIDI cleanup and quality reporting already exist, but cleanup is
  embedded in import/transcription instead of being a clear creation step.
- Existing build-time LoFi is audio DSP. It does not change MIDI tempo or rhythm
  and must be named distinctly from the new MIDI **Lo-fi Feel** operation.
- Whole-song planning, detailed arrangement, transition MIDI, and a bounded
  arrangement critic exist, but the cohesion work is hidden inside arrangement
  generation and is not reviewable as a separate artifact.
- The Kotlin and desktop test baseline passed on 2026-08-16 with
  `./gradlew test desktopApp:test`. This does not prove real audio-device output.

## Target creation workflow

```text
Project
  -> Import / inspect audio or MIDI
  -> Transcribe audio when required
  -> Repair MIDI
  -> Optional Lo-fi Feel (80 BPM + swing)
  -> Analyze the selected canonical MIDI
  -> Build Structure
  -> AI Cohesion review and approval
  -> Detailed Arrangement review and approval
  -> Generate MIDI and render stems
  -> Mix and optional lo-fi audio texture
  -> Master and export commercial-provenance bundle
```

The workflow-status row is not used as a second menu. Readiness and the next
safe action appear contextually in the active panel and in the unified status
surface.

## Architectural rules

1. **One playback owner.** A single playback-session model owns the selected
   artifact, lifecycle, position, duration, volume, failure, and retry identity.
2. **Immutable sources.** Imported files, raw transcription MIDI, and repaired
   MIDI are never overwritten by lo-fi or AI cohesion transformations.
3. **AI plans; deterministic code edits.** AI returns a strict, validated edit
   plan. Kotlin applies allow-listed MIDI operations and validates the result.
4. **Per-occurrence cohesion.** Structure occurrences may need different
   transposition, timing, or boundary patches even when they reference the same
   source part. Cohesion artifacts are therefore keyed by stable instance ID.
5. **Atomic derived artifacts.** MIDI, audio, reports, and manifests are written
   to temporary files, validated, then atomically published.
6. **Truthful progress.** No UI phase reports success before its backend work,
   artifact validation, and—where applicable—audio-device start have completed.
7. **Semantic color.** Color supplements text/icons and is never the only signal:
   teal/primary for active or success, blue for information, amber for warning,
   violet for loading/AI work, and red for blocking errors.
8. **Commercial evidence, not promises.** Commercial readiness requires known
   licenses and provenance. Platform and copyright decisions remain external.

## Delivery sequence

| Task | Deliverable | Depends on |
| --- | --- | --- |
| 063 | Full Melotrail rename and migration | Current baseline |
| 064 | Playback root-cause instrumentation and unified session | 063 |
| 065 | Single transport and UI duplication cleanup | 064 |
| 066 | Semantic loading, information, warning, and error feedback | 065 |
| 067 | Explicit standard MIDI repair stage | 063 |
| 068 | Fixed 80 BPM lo-fi MIDI feel stage | 067 |
| 069 | AI cohesion plan and deterministic melody transformation | 067–068 |
| 070 | Workflow state, artifact invalidation, and project migration | 066, 069 |
| 071 | YouTube-oriented commercial provenance and export readiness | 070 |
| 072 | End-to-end, visual, playback, packaging, and release acceptance | 071 |

Tasks 064–066 and 067–069 may be developed on separate branches after Task 063,
but Task 070 is the integration gate. Existing future tasks 059–062 are not
prerequisites and must not be silently folded into this scope.

## Commercial and YouTube constraints

- Require a rights attestation for user-provided source material. Transposition,
  timing edits, or AI patching do not make an unlicensed song commercially safe.
- Block the **commercial-ready** designation when a used model, sound library,
  sample, or other asset has unknown, non-commercial, or incompatible terms.
- Persist hashes and identifiers for sources, selected MIDI, AI models, sound
  libraries, samples, transformations, and final exports.
- Generate a human-readable upload checklist reminding the creator that
  AI-generated music should be disclosed using YouTube's AI-use setting.
- Explain that disclosure alone does not prevent monetization, while original
  and authentic content and avoidance of repetitive/mass-produced output remain
  channel-level requirements.
- Re-check platform policy links at release time; policies may change.

Official policy references captured on 2026-08-16:

- [YouTube AI-use disclosure](https://support.google.com/youtube/answer/14328491)
- [YouTube channel monetization policies](https://support.google.com/youtube/answer/1311392)

## Global definition of done

- No active product text, Kotlin package, Gradle identity, bundle display name,
  local repository folder, or newly written preference/log path uses the former
  name; legacy identifiers exist only for migration.
- Every preview and built artifact can play, pause, resume, seek, stop, replay,
  switch source, and fail truthfully through one transport.
- The workflow badge row is removed and the remaining UI is visually compared
  with `plan/UI.png` at wide, medium, and narrow sizes.
- Every long backend operation exposes a named phase and visible state using the
  semantic color system plus textual/icon cues.
- Raw, repaired, lo-fi, and cohesion MIDI artifacts have validation reports and
  deterministic provenance; downstream stale rules are covered by tests.
- AI edits cannot escape allow-listed musical bounds or overwrite source data.
- Commercial export produces a validated master, provenance manifest, license
  summary, source attestation state, and YouTube disclosure checklist.
- Root Kotlin, desktop, worker, end-to-end, and packaging checks pass, followed
  by a documented real-device listening test.

## Explicit non-goals

- Guaranteed YouTube monetization, copyright registration, Content ID
  clearance, or legal advice.
- Uploading directly to YouTube in this milestone.
- Voice cloning, artist imitation, or prompts that request a living artist's
  exact style.
- Destructive source editing or unrestricted model-authored MIDI/filesystem
  operations.
- Variable lo-fi tempo/intensity controls in the first version.

