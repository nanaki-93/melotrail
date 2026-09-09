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

## Encoder process boundary

V06a adds a companion-only process runner for trusted encoder adapters: argv
arrays, bounded job-local input snapshots, coalesced progress, timeout/cancel,
disk limits and atomic publication to a new collision-safe filename. Pre-cancelled
jobs never launch; a dedicated process group contains ordinary child processes.
Surviving children prevent publication and are stopped before job cleanup. Native
regressions use a child fixture executable and owned MOV bytes. Actual episode
encoding and stream/preview parity validation remain V06; no provider is called.
