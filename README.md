# Melotrail

A local Kotlin/Compose Desktop application with two independent creative workflows:

- **Audio composition via MIDI:** arrange an existing protected melody with Chords,
  Bass and Drums, then finish instruments and sound in Logic Pro.
- **Video generation:** the planned Video workspace turns externally finished
  artwork and a motion prompt into a complete 3–5-minute silent video. Add music
  later in an external Apple editor.

## Current state

The MIDI workflow is implemented: explicit chord durations/source-end padding,
melody-aware piano, coordinated arrangement plans, targeted repairs, six pages,
one persistent player and immutable accepted-only MIDI export. Listening,
non-interactive technical verification and release approval remain pending.
Live UI walkthroughs and interactive Logic/editor checks are no longer delivery
gates; their removal is not a compatibility or usability pass. The original
5/10 feedback is not an improved score simply because tests pass.

Video has independent project/asset/job storage, prepared-scene validation, an
owned ComfyUI adapter, pinned media supervision and bounded controlled motion.
The Video tab, moving playback and complete 3–5-minute export remain planned.

**Current priority (clarified 2026-10-02): animate TABI's activities and build
at least three minutes of Tokyo travel, ready for the user's six-minute repeat.**
Keep the visual style of the successful
[30-second continuity video](docs/pictures/video/tests/tabi-tokyo-continuity-30s-1080p.mp4)
and [20-second parallax video](docs/pictures/video/tests/tokyo-parallax-20s-1080p.mp4).
Drinking, reading, gentle breathing/head motion and looking outside are required
parts of this pilot. A blink-only film does not meet the clarified request.

First prove those actions in short continuous motion tests using compatible
scene-matched artwork, then combine them with the extended Tokyo corridor in one
180-second film. Keep the original character/outfit, cabin/camera, warm painted
style and rigid parallax. Add same-style Tokyo points of interest and quiet travel;
new TABI assets belong in `docs/pictures/video/tabi-assets/`, new scenery in its
`scenario/` folder. The old wave/Blender route remains deferred.

Three new full-scene action reference candidates (drink, read, watch) and a Tokyo
Station panorama candidate are saved in the requested folders. These are static
references, not registered animation layers or moving approval. Current controls
cover blink and limited breathing/head gestures; continuous drink/read motion,
compatible masks/backing and full scenery coverage still need proof.

Target exactly 180 seconds/5,400 frames first. Design and check the last-to-first
join for character/props, blink/breath phase, scenery and travel speed, so the user
can duplicate the completed segment to six minutes in an external editor. This
explicit whole-segment repeat is permitted; filling the initial three minutes
with the old short clip is not the request. Future app integration follows the
proven workflow. See [current plan](PLAN-VIDEO.md#selected-route--tabi-actions-and-a-repeatable-three-minute-tokyo-film-2026-10-02),
[active action tasks](TASKS-VIDEO.md#required-actions-and-three-minute-tokyo-2026-10-02),
and [asset details](docs/TABI_VIDEO.md#required-action-and-duration-clarification-2026-10-02).

Planning is split into two independent workstreams:
- **Audio/MIDI:** [PLAN-AUDIO](PLAN-AUDIO.md) describes CORE and AC1–AC5;
  [TASKS-AUDIO](TASKS-AUDIO.md) owns audio dependencies and status.
- **Video:** [PLAN-VIDEO](PLAN-VIDEO.md) describes CORE and VG1–VG6;
  [TASKS-VIDEO](TASKS-VIDEO.md) owns video dependencies and status.

Existing code, task states and evidence are preserved. There is no automatic
replay of old tasks or scheduler configuration. The split does not authorize
implementation, inference, downloads or paid generation.

## Run and validate

For a user-requested app session, `make desktop` starts the MIDI workspace.
Validation must not open an interactive window: use the
[headless test/build invocation](docs/VALIDATION.md#non-interactive-validation)
and `git diff --check`. The current unfiltered desktop suite contains a real-window
test, so plain `make test`/`make build` are not the non-interactive validation path.

Use JDK 21 and Kotlin 2.2.21 as configured by Gradle. The MIDI workflow needs no
Python service, sound library, video model or provider credentials.

`make video` invokes the same desktop application with `--video`, without JSON or
Swift tools. **The current launcher rejects that option.** Feature VG3 implements
the actual route and workspace; use `make desktop` for the supported MIDI path.
The Swift companion and soundtrack handoff have no active runtime/build owners.

For controlled-motion checks, with the explicitly configured Node/Canvas runtime,
generate the owned fixtures first:

```bash
./gradlew :test --tests '*VideoMotionDescriptorFixtureTest'
MELOTRAIL_MOTION_FIXTURE_ROOT="$PWD/build/video-motion-fixtures" \
  node --test tools/video-motion/render.test.cjs tools/video-motion/scenery.test.cjs
```

Live-window, foreground-capture, GUI install/startup and interactive editor tests
are removed from required validation. Keep headless package/service/offscreen checks
and human review of supplied musical/video artifacts, without claiming live usability
or editor compatibility. Historical receipts remain in [Validation](docs/VALIDATION.md)
and [TABI video](docs/TABI_VIDEO.md). Headless media/model probes still require a
separately admitted bounded request; ordinary tests do not install or load models.

## Musical input and output

Input: one SMF 0/1 MIDI file, one note-bearing track/channel, fixed tempo/meter;
additional meta-only tracks are allowed. Sections use whole bars; chord durations
are explicit and may be sub-bar. Source extent and optional trailing-silence
padding are confirmed without changing original events.

Output: a new immutable complete-song MIDI file, aligned active-role files and a
manifest. Import at song start in Logic Pro, confirm tempo/meter and assign
instruments. Logic handles audio rendering, mixing and mastering. GarageBand and
in-app audio import/transcription are outside the supported delivery.

`musicalEvaluation`, `musicalComparison` and `prepareLogicMatrix` prepare current
review packages in new directories; they cannot award listening or Logic approval.
Use the [evaluation procedure](docs/VALIDATION.md#musical-evaluation) and
[Logic procedure](docs/VALIDATION.md#logic-pro-procedure).

## Video target

A finished PNG/JPEG scene is the primary input, with optional externally prepared
character/background layers, poses, masks and scenery. Motion capabilities are
validated rather than invented. The target is one continuous 180–300-second
1920×1080 H.264 MP4 at 30 fps (default 180 seconds), with zero audio streams. Local ComfyUI and controlled
motion foundations remain; the selected baseline reuses the existing controlled
compositor. Blender experiments are deferred. Hosted fallback remains optional
and needs explicit authorization.
No in-app image generation, outfit transfer, short-clip repeat-to-fill, MIDI
dependency, soundtrack synchronization or public upload is part of this delivery.
The user explicitly plans to duplicate the completed three-minute segment externally
to six minutes; the pilot includes a last-to-first join check for that purpose.
Separately authorized external artwork preparation for the pilot does not change
that app boundary. A successful production harness is not VG6 app/release proof.

## Documentation

- [PLAN-AUDIO](PLAN-AUDIO.md): audio/MIDI outcomes, baseline and delivery sequence.
- [TASKS-AUDIO](TASKS-AUDIO.md): audio steps, dependencies, status and execution rules.
- [PLAN-VIDEO](PLAN-VIDEO.md): video outcomes, baseline and delivery sequence.
- [TASKS-VIDEO](TASKS-VIDEO.md): video steps, dependencies, status and execution rules.
- [Architecture](docs/ARCHITECTURE.md): runtime, storage and dependency boundaries.
- [MIDI contract](docs/MIDI_CONTRACT.md): protected input, authority and export.
- [UI guideline](docs/UI_GUIDELINE.md): existing MIDI design and planned Video flow.
- [Validation](docs/VALIDATION.md): automated/manual procedures and retained evidence.
- [TABI video](docs/TABI_VIDEO.md): video contract, assets and artistic/runtime evidence.

Supplied UI/video references and Logic captures remain in `docs/pictures` and
`docs/checks`. Historical evidence is preserved, not inherited as a current pass.
License: MIT.
