# TABI companion media spike

Independent macOS 14+ Swift package; no external package or MIDI/Gradle dependency.
Build and run on the host with Xcode command-line tools. An agent sandbox may
reject AVFoundation encoding even when the host supports it.

```sh
./companion/scripts/verify-boundary.sh
./companion/scripts/test.sh
swift build --package-path companion -c release
swift run --package-path companion melotrail-tabi-spike
```

The executable prints its unique temporary output path. It creates a one-second
320×180/30fps owned gradient with a mono 44.1kHz PCM test tone, encodes ProRes 422
MOV, verifies byte-preserved soundtrack samples and decoded preview timestamps,
and saves first/middle/final PNGs. Each run allocates a fresh companion-owned temporary directory; no input or output
path arguments are accepted.
The scheduler runs the same checks through `node tools/companion-check.mjs`
from the repository root; no companion Gradle shim is needed.
Build caches are ignored. The tone is a test fixture, not a MIDI audio renderer.

This proves the local media boundary, not a video editor or generative TABI pilot.
Encoder facts, provider research and remaining decisions belong to
[the TABI video contract](../docs/TABI_VIDEO.md#verified-companion-spike--2026-09-08).

## Deterministic scene composition

V04 extends V04a's digest-pinned soundtrack/frame plan with a declarative scene
plan. One composition input is required for each timing scene in its exact
order. It pins an approved interior, window mask, scrolling scenery layers and
optional approved action clip. Scrolling layers explicitly name the window mask
that clips them; action loops use bounded integer source-frame schedules; and
adjacent scenes cannot select the same action episode. A crossfade is an
explicit visual overlap in the outgoing timing frames, so it does not move a
later scene or shorten/stretch the soundtrack.

```sh
swift run --package-path companion melotrail-tabi-animation plan-scenes request.json
```

The request contains a V04a `timing` request plus `assetLibraryPath`,
`assetManifestPath`, `sceneVersion`, `identityVersion`, and a scene treatment
for every returned timing-scene ID. The command only reads pinned inputs and
prints JSON; encoding remains a later workstream. Consumers must run
`SceneComposer.validatePinnedAssets` before using a retained plan, which rejects
changed asset bytes.

## Real scene preview and transport

V05a adds `ScenePreviewStage`. It composites actual approved stills, masks and
action-clip source frames from a `SceneCompositionPlan` at an explicit output
geometry, and owns exactly one `AVPlayer` for the digest-pinned finished
soundtrack. Seeking chooses a plan-owned output frame with zero tolerance;
both public current-frame snapshots and optional playback observations derive
from the player's real time using that same frame rate and frame ranges. It does
not render MIDI, synthesize audio, encode video, or substitute placeholder frames.

The read-only caller can render one real preview frame without publishing an
image. Its JSON request wraps the existing scene request and the exact output
geometry:

```sh
swift run --package-path companion melotrail-tabi-animation preview-frame preview-request.json 42
```

```json
{
  "composition": { "...": "the V04 plan-scenes request" },
  "outputWidth": 1920,
  "outputHeight": 1080
}
```

It uses the one soundtrack player to seek to the selected frame, then prints
that frame's soundtrack time, scene IDs and actual rendered dimensions. V05b
adds the first editor controls; V06 consumes the same plan and output
geometry for encoding.

## Native scene editor

V05b provides a visible, resizable macOS editor for an existing composition
request. It opens the digest-pinned finished soundtrack and approved scene
assets already named by `SceneCompositionRequest`; it does not write that
request, the soundtrack, asset library, MIDI project, or an output video.

From the repository root, build and launch the release editor with:

```sh
make video VIDEO_REQUEST="/path/to/composition-request.json"
# Optionally display a persisted animation job ledger:
make video VIDEO_REQUEST="/path/to/composition-request.json" \
  VIDEO_JOBS="/path/to/animation-jobs.json"
```

The equivalent direct Swift commands are:

```sh
swift build --package-path companion -c release
"$(swift build --package-path companion -c release --show-bin-path)/melotrail-tabi-editor" \
  /path/to/composition-request.json [animation-jobs.json]
```

The window first reports loading or request errors, then shows rendered scene
thumbnails, the real preview, a selected-scene inspector, and play/pause/seek
transport. Scene selection seeks the same sole V05a AVPlayer clock used by
preview playback. `Stop preview` and `Restart preview` affect that local player
only; they never submit or cancel a provider job. Closing the window pauses
transport and removes the frame observer/player.

Pass an existing persisted animation ledger as an optional second argument to
show its real latest attempt state, actual/estimated cost, failure, and an
explicit `progress unknown` label for in-flight work. The editor makes no
provider call, cancellation request, retry, paid submission, or approval.

```sh
swift run --package-path companion melotrail-tabi-editor \
  /path/to/composition-request.json /path/to/animation-jobs.json
```

`Save session` writes the current request, selected scene, crop/motion/
crossfade edits, exact asset-manifest digest, and resolved asset pins to the
companion-owned Application Support session store. `Open saved session`
revalidates those inputs and restores edits on the existing soundtrack player,
keeping its current position; a changed soundtrack,
manifest, asset byte, malformed document, or unsupported session schema is
rejected while the active preview remains available. A failed refresh rolls back
the edits before any visible controls change. The session store never
writes the soundtrack, MIDI project/export, asset library, source request, or
accepted video output.

`./companion/scripts/test.sh` also launches that release executable with a fresh
owned composition fixture. It drives the visible scene/play/seek controls across
a boundary, captures the actual AppKit editor and input-error windows, and writes
transport/player/close observations under the ignored
`companion/.build/v05b-editor-evidence.*` directory printed by the check.

## Asset-library manifest

V02a adds a versioned JSON manifest for a user-selected asset-library directory,
not the Melotrail MIDI project or this repository. Each relative media path is
pinned by SHA-256 with provenance, rights, geometry, approval and exact
asset-ID/version records. A caller opens `AssetLibrary`, validates every entry,
then requests its exact approved pins; it never substitutes a newer or proposed
version. The regression creates and removes tiny owned PNG fixtures and covers
missing media, changed bytes, declared dimensions and approval selection. It
does not import production assets or record a human TABI identity approval.

Manifest snapshots publish to new paths atomically; existing snapshots are never overwritten.

### Local pilot-kit import and inspection

V02b adds a companion-only local import boundary. It copies a selected regular
file into `originals/<asset-id>/<version>/` beneath a user-selected asset library
and returns a proposed `AssetRecord` on standard output; it never changes the
selected source, an existing original, or a manifest snapshot. The library must
be outside this repository for real media. Publish the returned record only in a
new immutable manifest after review.

```sh
swift run --package-path companion melotrail-tabi-assets import /path/to/library request.json
swift run --package-path companion melotrail-tabi-assets inspect \
  /path/to/library /path/to/library/pilot-kit.json train-v1 tabi-v1 \
  tabi-seat@v1 tabi-seat-mask@v1 train-window-light@v1
```

The import request supplies provenance, rights, pivot/anchors, optional mask,
scene/identity compatibility and any observed identity differences. Dimensions,
duration/frame rate and actual still-image transparency are measured from the
copied bytes. Inspection reports invalid media, mask-size/type mismatches,
scene/identity incompatibility and unresolved TABI identity differences. An
unresolved difference is deliberately not an approval: it returns a non-ready
report for human review. No production media, paid generation, or identity
approval is bundled with this package.

## Animation adapter and manual clips

V03b provides a narrowly configured Runway Dev REST adapter: API version
`2024-11-06`, `gen4.5`, one approved PNG/JPEG/WebP still, `1280:720`, and five
seconds. It reads `RUNWAYML_API_SECRET` only from secure process configuration;
credentials and provider payloads are not written to the job ledger. A caller
must use the V03a coordinator so admission is persisted before submission and
uncertain submissions are never blindly retried. The adapter does not issue a
request merely by being constructed.

Provider output is first downloaded into an external, digest-addressed
quarantine, where partial, oversized, digest-mismatched, and unreadable clips
are discarded. A quarantined clip is still proposed evidence, not an approved
asset. An owned manual clip may instead use the real companion caller below; it
preserves the selected source and returns a proposed `AssetRecord`, which must
be published only in a new manifest after human review.

```sh
swift run --package-path companion melotrail-tabi-animation import-manual-clip \
  /path/to/library request.json
```

## Encoder process boundary

V06a adds a companion-only process runner for trusted encoder adapters: argv
arrays, bounded job-local input snapshots, coalesced progress, timeout/cancel,
disk limits and atomic publication to a new collision-safe filename. Pre-cancelled
jobs never launch; a dedicated process group contains ordinary child processes.
Surviving children prevent publication and are stopped before job cleanup. Native
regressions use a child fixture executable and owned MOV bytes. Actual episode
encoding and stream/preview parity validation use the V06 API below; no provider is called.

## Local episode encode

V06 encodes the existing resolved composition with the exact `ScenePreviewStage`
renderer and its digest-pinned finished soundtrack. The only delivery preset is
ProRes 422/PCM MOV at the composition's integer frame rate. The soundtrack is
decoded to 32-bit float PCM, with source/output sample digests verified exactly; it is never created from MIDI,
trimmed, stretched, normalized, or mastered. The staged result must decode with
one ProRes video stream and one PCM audio stream, match the declared geometry and
frame cadence, retain the soundtrack timeline within one output frame, and keep
first/final decoded pixels within the documented ProRes fidelity bound of the
same preview renderer before it can be published. Both images are compared as
sRGB RGB over black (mean absolute channel error ≤32 on a 0–255 scale).
Transparent regions are flattened onto initialized black video pixels. Output
dimensions must be even and fit within a 3840×2160 pixel budget.

```sh
swift run --package-path companion melotrail-tabi-animation encode episode-request.json
```

```json
{
  "composition": { "...": "the V04/V05 composition request" },
  "outputWidth": 1920,
  "outputHeight": 1080,
  "outputDirectory": "/a/user-selected/output-directory",
  "outputFileName": "tabi-journey.mov"
}
```

The companion stages work only in its UUID directory below the selected output
directory, validates before collision-safe no-overwrite publication, and writes
a compact adjacent `.provenance.json` technical report with output, soundtrack
and composition digests, decoded PCM sample digest, channels/sample rate and
exact asset pins. Both names must be available;
the report is reserved before the video becomes complete. The native API accepts
bounded input/output/disk/time limits and cancellation, and reports encoding,
finalizing, validating and publishing phases. Progress callbacks run synchronously
and must return promptly. Audio/video samples are fed in timeline order. It never uploads, edits
the soundtrack/assets/MIDI project, or labels a failed staging file complete.
