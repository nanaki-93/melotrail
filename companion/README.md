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
that frame's soundtrack time, scene IDs and actual rendered dimensions. V05
owns the editor controls; V06 will consume the same plan and output geometry
for encoding.

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
encoding and stream/preview parity validation remain V06; no provider is called.
