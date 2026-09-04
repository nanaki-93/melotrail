# Future feature proposal — Create Video

Status: specified for future consideration; NOT approved for implementation

Requested: 2026-09-05. UI-018 refines this document; the UI execution prompt does
not execute the VID tasks below.

Parent: [UI redesign plan](UI_MOCKUP_REDESIGN_PLAN.md) under
[PLAN.md](../../PLAN.md). Reference: `docs/pictures/UI/08-video-preview.png`.

## 1. Proposed function

After finishing sound production in Logic Pro, the musician can create a simple
music video from a finished soundtrack and owned visuals, using the Melotrail
song's sections as an optional scene-timing guide.

The proposed action is **Create Video**. A finished video—not a MIDI audition,
screen recording of the app, or falsely labeled image—is the eventual output.
The first version uses static images with bounded pan/zoom and simple cuts or
crossfades. It does not promise generative animation from a prompt.

No such command is currently implemented. Do not add a no-op `createVideo()`
function, nullable renderer, hidden route, feature-flag stub, or disabled export
choice during UI redesign.

## 2. Product decision before implementation

Current AGENTS, architecture and quality contracts exclude video and audio
ingestion from MIDI Core. This proposal does not override that boundary.

Recommended future isolation: a separately packaged, optional Kotlin/JVM video
companion consuming an immutable MIDI export package plus a user-selected
finished Logic Pro soundtrack. MIDI Core must remain independently installable,
offline, buildable and usable without a video encoder or audio-production
dependency. A later capability-checked launch from Export can open the companion
only once that integration really exists and is approved.

The companion may package an already finished soundtrack into video; it must
not render MIDI to audio, alter the mix/master, ingest audio into the MIDI
project, transcribe it, or publish to online platforms. Its job file and video
assets live outside the MIDI project and never modify `project.json`, source,
candidates, acceptances or immutable export snapshots. Do not reuse the obsolete
selected-master/release/video implementation to avoid this boundary.

VID-000 must obtain explicit user approval and revise applicable root/agent/
architecture/functional/cleanup/quality contracts consistently before code is
written. If the user instead wants video inside the main application, stop for
that architecture decision; it is a material change, not a visual-detail choice.

## 3. User journey and mockup adaptation

1. Start a video job in the optional companion; optionally choose a verified
   Melotrail export snapshot for title, section labels and timing.
2. Select the musician's finished soundtrack and verify that its duration is
   usable. Explain that MIDI contains no finished audio.
3. Choose owned still images, a solid background, or approved visual assets.
4. Build a scene list from the exported sections or author scene durations in
   the video job. Such edits change video timing only, never musical authority.
5. Choose a supported aspect preset and preview framing, soundtrack and scene
   transitions together. Optional title/credit text is video-only metadata.
6. Choose a new output filename; create the video with real progress and cancel.
7. Validate output, then reveal the finished video and a small technical report.

Reproduce the mockup's large preview stage, section-aligned thumbnail timeline,
selected-scene border, transition cards, right settings inspector, dark violet
primary export action, and compact playback. Retain Melotrail identity and
actual file information. The companion has one preview transport of its own;
it must not run the MIDI Core synthesizer concurrently as the soundtrack.

Remove the mockup's claim that audio comes from Melotrail Mix & Master. Omit
unsupported formats/transitions/animations instead of rendering fake controls.
No built-in sample avatar, art pack, cloud account, publishing, copyright score,
or monetization promise is implied.

## 4. Conceptual application contract

This is pseudocode for the future application boundary, not production API code
to add now. Concrete codecs and dependency choice remain VID-000 decisions.

```text
CreateVideoRequest
  jobId
  optionalExportSnapshotReference { snapshotId, manifestDigest }
  soundtrack { selectedLocalFile, digest, duration }
  visuals[] { selectedLocalFile or solidColor, digestWhenFile }
  scenes[] { sceneId, visualId, start, duration, crop, motion, transition }
  outputPreset { aspect, resolution, frameRate, container, codecs }
  destination { newOutputFile, collisionPolicy = refuse }

createVideo(request, cancellation, onProgress)
  -> Completed { immutableOutput, digest, duration, validationReport }
   | Cancelled { previousOutputsPreserved }
   | Rejected { code, affectedSceneOrFile, nextAction }
   | Failed { code, recoveryAction }
```

Inputs are reverified before admission. Source/media changes after selection
produce a typed stale-input failure rather than quietly using different bytes.
The job's schema is separate from the MIDI Core schema. Job persistence is
atomic and paths are validated; external selected inputs remain read-only.
Future public API names and async shape are selected at implementation time.

### Timing and soundtrack policy

- MIDI tick durations convert using snapshot PPQ and its fixed tempo. Frame
  placement uses one documented deterministic rounding policy, with end-boundary
  correction so accumulated rounding cannot add/remove whole scenes.
- A Logic bounce may include leading silence, a tail, or tempo edits. Never
  assume it matches the MIDI duration. Present mismatch and require an explicit
  video-only alignment/offset or final-scene-hold decision. First version may
  reject unsupported mismatches with a clear action.
- Do not silently trim, stretch, normalize, fade, mix, master, or regenerate
  the supplied soundtrack. Any encoding conversion required for the selected
  video format must be disclosed and covered by the approved future contract.
- A crossfade consumes a defined overlap; scene starts and total output remain
  deterministic. Test very short sections, final tails, and frame rounding.
- The still-image scene list determines visual frames; it cannot rewrite the
  song's section authority or candidate membership.

### Output and job safety

- Stage output beside the chosen destination, validate it, then publish without
  silently overwriting an existing file. Clean only this job's known temporary
  files; preserve prior complete videos and all input files.
- Probe actual duration, dimensions, frame rate, stream presence and readable
  first/final frames. Preserve the selected soundtrack according to the approved
  encoding policy and test A/V synchronization.
- Progress is based on actual encoder work, not a cosmetic timer. Cancellation
  terminates only the owned encoder process/session and leaves no file presented
  as complete. Log stderr/errors proportionally without leaking private paths
  into shareable reports.
- Encoder discovery/version/license/distribution, input size/duration bounds,
  available disk space and OS packaging require an explicit capability spike.
  Do not promise H.264/MP4 or any other codec before that spike proves support.
- Preview and final output must agree on crop, scene timing, transitions and
  aspect. A still image alone is not proof of working video export.

## 5. Future task backlog — not part of the UI execution loop

Every VID task depends on its predecessor, uses exactly one task commit, and
follows the same test/evidence/manual-pause discipline as the UI task suite.
These tasks are proposals until VID-000 is explicitly authorized after MC-060.

### VID-000 — Approve the feature and prove the media boundary

- **Depends on:** MC-060 DONE, UI-018 specification, separate user request to
  implement video.
- **Work:** confirm companion versus in-app ownership; soundtrack/visual rights,
  supported inputs, target presets, distribution strategy, dependency/license
  and resource limits. Run an isolated encoder/preview capability spike with
  owned media. Update the authoritative contracts and create a VID execution
  ledger only after explicit scope approval.
- **Gate:** output can be decoded with proven first/end frames and soundtrack
  sync; MIDI Core still has no encoder dependency. Pause on missing approval or
  an unproven redistribution/codec choice.
- **Commit:** `video: VID-000 approve the optional video boundary`.

### VID-001 — Implement immutable input and video-job contracts

- **Work:** separate companion job schema/store, verified read-only MIDI manifest
  reader, user-selected soundtrack/visual admission, typed errors and explicit
  duration-mismatch policy. No reads from mutable accepted pointers.
- **Tests:** valid/corrupt/stale manifest, modified inputs, missing media, path
  confinement, atomic reopen/save, oversized inputs, byte-preserved MIDI project.
- **Gate:** a job reopens safely with every source identity intact and requires
  no legacy audio/release schema or service.
- **Commit:** `video: VID-001 add safe video job inputs`.

### VID-002 — Implement deterministic scene timing

- **Work:** snapshot sections to scene suggestions, stable scene IDs, video-only
  order/duration edits, explicit alignment, crop/motion and bounded transition
  model. One shared scene/time/frame calculation for preview and output.
- **Tests:** fixed-tempo conversion, unusual PPQ, frame rounding, overlap, short
  scene, soundtrack tail, offset, exact total and unchanged musical authority.
- **Gate:** same job/settings yield the same scene/frame plan without audio DSP.
- **Commit:** `video: VID-002 implement scene timing and layout`.

### VID-003 — Build the reference-faithful video editor and preview

- **Work:** optional companion Compose shell, `08-video-preview.png` stage,
  thumbnail strip, scene selection, supported settings and transition cards,
  one soundtrack preview transport and genuine unavailable-capability messages.
- **Tests:** real-file admission, preview sync, scrubbing, crop/aspect accuracy,
  keyboard/compact layout, malformed media recovery and video-job-only mutations.
- **Gate:** preview is real and matches the deterministic scene plan; no fake
  thumbnails, unsupported controls or simultaneous MIDI synthesizer soundtrack.
- **Commit:** `video: VID-003 build the video preview workspace`.

### VID-004 — Implement encoding, progress and cancellation

- **Work:** approved optional encoder adapter, bounded command/API construction,
  safe file arguments, one owned background job, real progress, timeout,
  cancellation, disk-failure handling and no-overwrite staging/publication.
- **Tests:** short owned-media encode/decode, spaces/Unicode/untrusted filenames,
  cancel at different stages, crash, missing encoder, disk error, collision,
  previous-output and all input immutability, resource/process cleanup.
- **Gate:** a playable complete file is created without modifying source music
  or falsely labeling partial output complete.
- **Commit:** `video: VID-004 create videos safely`.

### VID-005 — Integrate optional launch and validate output

- **Work:** capability-checked Create Video launch/handoff only after the
  companion exists; user explicitly selects soundtrack; validated snapshot
  identity carried into the job. Add output probe/report/reveal; absent companion
  leaves MIDI export fully functional and explains availability honestly.
- **Tests:** optional integration installed/absent, stale snapshot, no core
  dependency, final frame/duration/stream/sync checks, cancellation, privacy,
  independently installable/buildable MIDI Core.
- **Gate:** MIDI-only workflow is unchanged; video is an opt-in downstream action,
  not a new source-import or mandatory export stage.
- **Commit:** `video: VID-005 integrate optional video handoff`.

### VID-006 — Prove visuals, playback and delivery

- **Work:** native-platform build/install test; user review of several owned
  videos, including wide/portrait/square if the approved presets support them;
  soundtrack synchronization, scene boundaries, final frame and restart tests;
  compare editor graphics to `08`; record known limitations and instructions.
- **Tests:** full companion tests/build, MIDI Core `make test` / `make build`,
  independent install with companion absent, video golden-frame/scene checks.
- **Manual gate:** genuine user visual/video/A/V acceptance; no agent-generated
  subjective pass, no website publishing performed.
- **Commit:** `video: VID-006 validate the video creator`.

## 6. Completion boundaries

UI-018 is complete when this specification and its future backlog are coherent.
That does not mark any VID task complete. The optional video feature is complete
only after VID-000–VID-006, real output validation and user acceptance. Until
then, the active MIDI Core runtime continues to exclude video and audio
production, and its legacy video cleanup tasks remain mandatory.
