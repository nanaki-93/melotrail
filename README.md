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

**Current priority (updated 2026-10-02): return to the successful Tokyo
blink/parallax recipe.** The user selects the existing
[30-second continuity video](docs/pictures/video/tests/tabi-tokyo-continuity-30s-1080p.mp4)
and [20-second parallax video](docs/pictures/video/tests/tokyo-parallax-20s-1080p.mp4)
as the visual baseline. Their hashes, original assets, ComfyUI graphs and render
settings have been recovered. ComfyUI made the scenery paintings; the existing
Node/Canvas compositor moved the depth layers and blended the supplied eye poses,
with FFmpeg encoding the silent video. TABI's body, hands and the cabin stayed fixed.

The later arm/Blender rig route is deferred; its pending encode was cancelled
before admission. Preserve its evidence without treating it as the next step.
First check the recovered recipe on current tools in a new short output using
the accepted leaf/window-mask correction. Then extend scenery for 60 seconds,
prove the bounded runner, and scale the accepted recipe toward 180 seconds.
New character activities remain separate optional experiments after the baseline
is stable. Different cities reuse the cabin/TABI and replace exterior artwork;
future app integration follows the proven video workflow.

The existing scenery reaches about 30 seconds at the approved speed. Longer
coverage, current-runtime output and the complete film still need proof. This
recovery starts no model or media job and leaves the two reference videos intact.
See [current route](PLAN-VIDEO.md#selected-route--recover-the-blinkparallax-baseline-2026-10-02),
[recovery tasks](TASKS-VIDEO.md#baseline-recovery-2026-10-02), and
[recipe/evidence](docs/TABI_VIDEO.md#selected-blinkparallax-baseline-2026-10-02).

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
No in-app image generation, outfit transfer, whole-clip repeat-to-fill, MIDI
dependency, soundtrack synchronization or public upload is part of this delivery.
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
