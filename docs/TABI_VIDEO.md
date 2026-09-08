# TABI video companion

Owner: future video product/asset/media contract. Delivery is V01–V07 in
[TASKS](../TASKS.md), under [PLAN](../PLAN.md). No video feature, model choice,
paid generation, publishing action or monetization result is implemented here.

## Outcome and scope

Create an original, calm animated train journey starring TABI, with the
musician's finished music. Start from a reusable asset library; generate short
controlled animation takes and compose them into a coherent full-song video.
A deliverable is a playable video with the real soundtrack, not a still mockup.

Recommend a separately installed companion, preferably in a separate repository.
Kotlin may own its job orchestration and Compose editor. Provider calls and an
encoder are companion-only adapters. The MIDI application remains independently
buildable/installable, offline and free of audio/video-production dependencies.
No old Melotrail audio project, selected-master record, renderer, commercial
policy service or publishing branch is reused.

Input is a finished Logic Pro bounce plus owned/usable visuals and, optionally,
a verified immutable MIDI export manifest for title and section timing. MIDI has
no finished instrument sound. The companion never renders MIDI, transcribes,
remasters or changes the music, and never writes the MIDI project or snapshot.

## What the existing references establish

[Character identity](pictures/tabi.png) is the cosmic axolotl traveler sheet;
it still contains “Moki”. New output uses **TABI**. The character has an
indigo/violet body, pink-purple feathery gills, dark glossy eyes, cheek/star
markings, headphones, travel jacket/scarf, satchel and travel accessories.

The [morning train scene](pictures/video/Morning%20Lo-Fi%20Train%20Ride%20with%20Tabi.png)
and the three other supplied train scenes under `pictures/video` establish
composition and mood: TABI at left, three-quarter view toward the right, fixed
window/train/table geometry, passing Japanese city/countryside, layered depth,
soft light and cozy cinematic 2D illustration. They are source references, not
already separated, rigged, licensed-for-redistribution animation assets.

There are visible differences between the sheet and the newer scene, including
forehead-star color, body proportions and accessory arrangement. V02 resolves
these into one approved identity version; do not average them unpredictably.
Use the sheet for identity and the scenes for layout until that approval.
Style: drawn/painterly 2D, rich navy/violet with warm amber/peach highlights,
restrained grain/reflection. Exclude photorealism, 3D redesign, unstable text,
extra characters, deformed anatomy and changing train geometry.

The retired asset prompt requested 24 poses and many plates/props/views at once.
That is a long-term library wish list, not the first batch. The retired future
feature offered only static pan/zoom. This plan instead combines true limited
character animation with deterministic scene motion.

## Small pilot asset kit

First prove a 30–60 second scene with the approved kit, then one complete song
(target a normal 3–5 minute track if available; use actual soundtrack duration).
Do not generate an hour of video or all possible character views first.

| Group | Pilot contents | Required production properties |
| --- | --- | --- |
| Identity | One approved front/side/three-quarter reference set | Stable proportions, costume, markings, palette, scale and exclusions |
| Character | Seated listening, holding cup, writing and looking out | Separate transparent files or masked approved clips; stable pivot/scale |
| Interior | One fixed train/table/window composition | Empty character area, foreground occlusion and window mask |
| Scenery | Day, dusk and night versions of one journey | Skyline/midground/foreground layers suitable for seamless scrolling |
| Props | Notebook/pencil, cup, ticket, camera and bag | Transparent independent files with placement anchors |
| Atmosphere | Reflections, light, steam; rain only if needed | Separate layers and bounded opacity/motion |
| Animation | Blink/breathe, writing/hand action, glance, steam | Short validated loops/takes with consistent camera and identity |

First output target: 16:9, 1920×1080, 30 fps. H.264/AAC in MP4 is a candidate
delivery preset, conditional on V01 proving the installed encoder, distribution
terms and preview/output support. Do not promise alpha-video or start/end-frame
conditioning unless the chosen provider supports it; a matte/masked pipeline
may be required. All generated source outputs remain stored in original quality.

Later library expansion may include the original requested reading/sleeping/
smiling/curious/surprised/photographing/ticket/map/postcard/waving/headphone/
stretching/luggage/walking/yawning/weather-watching actions; front/side/back
views; table/window/aisle/tunnel/station/reflection/light plates; Tokyo landmarks,
Fuji, countryside/coast, blossoms, rain, snow and autumn; and steam/page/eye/
gill/tail/cable micro-motion. Add only assets useful to an episode, not a Cartesian
product of every pose, weather and camera angle.

## Asset-library contract

Each asset has an ID/version, local immutable file/digest, type, original source,
creator/license or user ownership statement, permitted uses, creation/provider/
model/prompt/reference provenance when generated, dimensions/frame rate/duration,
alpha/mask facts, pivot/placement, compatible scene/identity version and approval
state (proposed, approved, rejected).

Keep originals, approved derivatives and generated takes distinct. A revised
asset receives a new identity; jobs pin approved versions. Validate missing or
changed media on reopen and before encoding. Large media lives in user-selected
library storage outside the source repository; small owned test media may be
checked in. A thumbnail is a cache, not the canonical asset.

Generate one asset or small coherent batch at a time. Inspect real transparency,
edge matte, perspective, color, scale and character identity. A collage, opaque
checkerboard or pretty but geometrically incompatible cutout does not pass.
Render text/signage separately from generated scenery where stable spelling is
required; do not rely on a model reproducing readable route labels every frame.

## Generative animation strategy

Use one provider initially. V01 checks current API availability, authentication,
reference-image controls, allowed durations/resolution, commercial-use terms,
privacy/retention, model versioning, seeds, price and job/cancel behavior.
Do not equate access through an installed creative connector with a stable API
that may be embedded in a shipped application. Keep a manual clip-import path
if provider integration is unavailable.

Prefer image-to-video from an approved composed keyframe or locked references.
Generate small micro-actions; keep the camera and train geometry fixed. Use
layered/parallax motion for distant scenery and reflections so most of a long
song does not require new model frames. Use model animation where it adds life,
not to recreate the entire set at every section boundary.

Quality review checks first/middle/last frames and real playback: gills, face,
star, clothing, hands, accessories, no spontaneous objects/text, consistent
lighting, flicker/warping and loop seam. Matching first/last images is insufficient
if the intervening animation drifts. Do not reverse an irreversible action such
as writing or drinking just to manufacture a ping-pong loop.

Record requested and actual clip duration; reuse/trim visual clips only within
explicit scene policy. Rejected takes remain excluded from production. Provider
seeds may help traceability but do not promise identical video on rerun.
Determinism applies to composition from pinned assets, not cloud generation.

## Cost and job control

Before submitting, prepare a reviewable batch: exact model/options, references,
clip count/durations, estimated cost, maximum retries and cumulative budget.
Reuse an already authorized batch budget; only a new scope or exceeded ceiling
requires a new decision. Unknown cost prevents unattended submission.

Persist each request fingerprint, provider job ID, status, cost estimate/actual,
outputs and failure before moving to the next stage. On timeout or restart,
query the existing job. An uncertain paid request must not be blindly retried.
Limit concurrency and polling, back off rate limits, bound retries and support
cancel without claiming a provider refunded work already started. Credentials
stay in secure configuration, not project manifests or shareable logs.

Useful pilot measurements: accepted seconds per generated second, identity/loop
reject rate, actual cost per accepted clip and per finished video, asset reuse,
encoding time/disk use and human correction time. Choose an expansion budget
from measured results; no cost-per-minute promise is justified yet.

## Music-to-scene workflow

1. Select the finished soundtrack; hash/probe it and show real duration.
2. Optionally read a verified MIDI manifest for section names and times. For a
   fixed tempo, seconds = ticks × microseconds-per-quarter / PPQ / 1,000,000.
3. Confirm any bounce lead-in, tail, offset or changed-tempo mismatch. Section
   timing is a suggestion; do not stretch/trim audio or assume equal durations.
4. Choose approved assets and propose an episode arc: departure, developing
   scenery/daylight, a highlighted arrival/landmark and an intentional ending.
5. Preview scene pacing against the music. Section boundaries guide major
   changes; avoid a cut, gesture or visual pulse on every beat.
6. Generate only missing approved micro-actions within the budget, then review.
7. Preview the entire song, encode to a new destination and validate output.
8. Deliver video plus a small local package of thumbnail/title/description/
   credits suggestions and provenance. The user reviews and uploads.

The scene plan owns layers, crop/position, window mask, parallax speeds,
loop/action schedules, scene start/duration and transition overlaps. Use one
rational time/frame rounding policy and exact final-boundary correction.
Preview and encoder consume the same plan. Crossfades consume explicitly
modeled overlap; they cannot shorten the soundtrack or silently shift later scenes.

Keep natural breathing, sparse gestures, varied scenery and a recognizable
journey. A static still can be a fallback, but it does not fulfill the planned
animated-pilot gate. A few repeated loops across one music video may suit the
format; episodes still need meaningful original visual/music variation.

## Encoding and local delivery

Video jobs have a schema/store separate from MIDI projects. External inputs are
read-only; outputs are staged and validated before atomic no-overwrite publication.
Cancel terminates only the owned process and removes only known job temporaries.
Bound source size/duration, disk use, timeout and resource concurrency. Capture
actual encoder progress and useful redacted errors, not a simulated percentage.

Validate streams, codec/container, dimensions, frame rate, total duration, first/
last decodable frames, scene boundaries, crop parity, audio timeline and A/V sync.
Audio encoding conversion must be explicit; no EQ, normalization, mastering,
trimming or time stretch. Byte identity is not promised after codec conversion;
source identity and timing are preserved and tested under the chosen policy.

Reference 08 guides the companion's real preview stage, scene strip, settings
inspector and restrained export action. The companion has its own soundtrack
transport and never concurrently substitutes the MIDI synth as the soundtrack.
Optional launch from MIDI Export appears only after an independently working
companion exists; its absence leaves MIDI export unaffected.

## YouTube and commercial intent

AI-assisted original work can be eligible for monetization, but using owned
assets or AI does not guarantee eligibility. YouTube evaluates originality and
channel-level repetitive/inauthentic content; superficially varying the same
mass-produced episode template is a poor product strategy. A distinctive TABI
journey and original music support the creative goal, not a guaranteed outcome.
[YouTube monetization policy](https://support.google.com/youtube/answer/1311392?hl=en).

Check disclosure against the actual video and soundtrack. YouTube distinguishes
non-realistic animation from realistic synthetic content and explicitly lists
AI-generated music among disclosure examples. Disclosure itself does not remove
monetization eligibility. Do not automatically classify all animated videos or
all algorithmically arranged MIDI the same way; record the user's actual use.
[YouTube AI disclosure guidance](https://support.google.com/youtube/answer/14328491?hl=en).

These sources were checked on 2026-09-06; review again before publication.
Provider commercial-use terms, rights to source/reference assets and music,
YouTube Partner Program eligibility and editorial quality are separate decisions.
Keep provenance and credits proportional; do not recreate a policy-scoring engine
or promise copyright exclusivity/revenue. No direct upload or YouTube account
integration in the first companion; user-controlled publication is sufficient.

## Pilot acceptance

A real user approves character identity and the finished full-song video.
Require stable TABI/train design, visible restrained animation, no distracting
flicker/loop seams, coherent progression/ending and correct soundtrack sync.
Record actual spend/retries, output hashes/technical checks, source ownership,
remaining disclosure/credits decisions and the user's review. Confirm the MIDI
app still installs and exports with the companion absent. Only then consider
long compilations, larger libraries, new providers or additional aspect ratios.


## Verified companion spike — 2026-09-08

`companion/` is an independent Swift package with no third-party dependencies
or Gradle/MIDI consumer. Its README owns the build/run commands. Host checks on
macOS 26.6.2 (25G83), Swift 6.3.3, prove Apple AVFoundation ProRes 422 (`apcn`)
encoding and passthrough MOV muxing: 320×180, 30fps, exactly one second of video
and mono 44.1kHz PCM. The probe decodes frames at 0, 0.5 and 29/30 seconds,
checks their actual timestamps against the audio timeline, and compares all
44,100 soundtrack samples byte-for-byte with the generated owned WAV. It saves
those preview PNGs alongside the MOV in a fresh companion-owned temporary
directory. No caller-supplied input/output paths are accepted; repeated runs
preserve earlier outputs.
This is timestamp/decoded-media proof; real interactive playback belongs to V05a.
The earlier worker `Cannot Encode` failure did not reproduce on the host.

The codec is supplied by the OS framework, with no separately versioned or
redistributed encoder binary in this repository. FFmpeg/ffprobe are absent from
PATH and are not selected. Apple SDK use is governed by the
[Apple developer agreements](https://developer.apple.com/support/terms/);
this spike makes no open-source codec or future app redistribution-rights claim.
Production codec/preset selection and signed native packaging remain V01/V06 work.

Read-only provider research: Runway documents `POST /v1/image_to_video` with
`gen4.5`, `promptImage` URL/data URI, text, a `1280:720` five-second example,
bearer authentication and `X-Runway-Version: 2024-11-06`. Submission returns a
task ID for polling. A future adapter sends only an approved still; the finished
Logic soundtrack stays local. [Runway API guide](https://docs.dev.runwayml.com/guides/using-the-api/).
Its current base rate is 12 credits/second at US$0.01/credit: a five-second clip
is US$0.60 before tax and optional output-format surcharges.
[Runway pricing](https://docs.dev.runwayml.com/guides/pricing/).
No credentials, uploads, generation requests or spending were used in this proof.
V01 still owns the bounded paid-pilot proposal, source rights, current provider
terms/privacy/account limits and distribution decisions; V03 owns persisted jobs,
secure credentials, cancellation, budget admission and quarantined downloads.
