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
one persistent player and immutable accepted-only MIDI export. Listening, current
Logic checks, visual acceptance and release approval remain pending. The original
5/10 feedback is not an improved score simply because tests pass.

Video has independent project/asset/job storage, prepared-scene validation, an
owned ComfyUI adapter, pinned media supervision and bounded controlled-motion
rendering. Controlled blink/parallax has reached a reviewed 30-second pilot;
continuous-plan proposals and guarded persistence exist. The Video tab, in-app
moving playback, executable full-duration workflow and complete silent export
are **not yet delivered**.

**Current priority: produce the video before building the app flow.** Next is a
scene-compatible 5–10-second TABI character test, then a scenery-join test, a
combined 60-second proof and one complete 180-second/5,400-frame Tokyo
train-window pilot with moving depth-plane parallax, distinct passing views
about every 5–10 seconds, quieter scenery between them and TABI actions inside
the cabin. Review the full film
and editor handoff before implementing reusable future-video functionality.
Reuse current services through bounded production tooling; preserve existing
videos and the accepted corrected mask. This documentation update starts no
model/render job or implementation. See [production order](PLAN.md#7-step-by-step-delivery-order)
and [production recipe](docs/TABI_VIDEO.md#production-first-workflow-2026-09-28).

[PLAN](PLAN.md) describes features CORE, AC1–AC5 and VG1–VG6.
[TASKS](TASKS.md) is the fresh dependency/status queue. Existing code is reused;
there is no automatic replay of old tasks or scheduler configuration. The planning
reset does not authorize implementation, inference, downloads or paid generation.

## Run and validate

```bash
make desktop
make test
make build
git diff --check
```

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

Native installation, foreground capture and optional media/model host probes are
separate checks, not promises made by a normal test pass. See
[Validation](docs/VALIDATION.md) and [TABI video](docs/TABI_VIDEO.md) for their
contracts and retained evidence. Inference requires a separately admitted bounded
request; ordinary startup/tests do not install or load models.

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
motion are selected; hosted fallback is optional and needs explicit authorization.
No in-app image generation, outfit transfer, whole-clip repeat-to-fill, MIDI
dependency, soundtrack synchronization or public upload is part of this delivery.
Separately authorized external artwork preparation for the pilot does not change
that app boundary. A successful production harness is not VG6 app/release proof.

## Documentation

- [PLAN](PLAN.md): feature outcomes, current baseline and delivery sequence.
- [TASKS](TASKS.md): bounded steps, dependencies, acceptance and execution rules.
- [Architecture](docs/ARCHITECTURE.md): runtime, storage and dependency boundaries.
- [MIDI contract](docs/MIDI_CONTRACT.md): protected input, authority and export.
- [UI guideline](docs/UI_GUIDELINE.md): existing MIDI design and planned Video flow.
- [Validation](docs/VALIDATION.md): automated/manual procedures and retained evidence.
- [TABI video](docs/TABI_VIDEO.md): video contract, assets and artistic/runtime evidence.

Supplied UI/video references and Logic captures remain in `docs/pictures` and
`docs/checks`. Historical evidence is preserved, not inherited as a current pass.
License: MIT.
