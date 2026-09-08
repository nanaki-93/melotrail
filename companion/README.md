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
Build caches are ignored. The tone is a test fixture, not a MIDI audio renderer.

This proves the local media boundary, not a video editor or generative TABI pilot.
Encoder facts, provider research and remaining decisions belong to
[the TABI video contract](../docs/TABI_VIDEO.md#verified-companion-spike--2026-09-08).
