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

### Companion manifest boundary (V02a)

The independently built `companion/` package now defines schema v1 records for
asset ID/version, SHA-256-pinned relative media path, type, source/creator and
creation provenance, rights/permitted uses, geometry (including alpha, mask,
pivot, anchors and compatible scene/identity versions), and a proposed/approved/
rejected decision record. The library validates each media file's presence,
digest and declared dimensions before a caller can resolve exact approved pins.
It rejects traversal outside the selected library; it never reads or writes a
Melotrail MIDI project. The manifest records no production asset or human
approval: V02b/V02 still own import, real geometry/identity inspection and the
user's coherent-kit decision.

### Local import and pilot-kit inspection (V02b)

The companion imports a selected regular local file by copying it to a new
`originals/<asset-id>/<version>/` path in the user-selected external library.
It never alters that selected source, replaces an existing original, or mutates
a manifest; the returned record remains **proposed**. Dimensions, timing and
still-image alpha are measured from imported bytes. An alpha channel whose pixels
are all opaque is reported as absent, so a declared cutout cannot pass on its
container metadata alone. The companion validates mask identity/type/size,
normalized pivots and anchors, and requested scene/identity-version compatibility
before a kit can be ready for composition.

An asset record may carry factual unresolved TABI identity differences (for
example the sheet's versus train scene's forehead-star color). Kit inspection
surfaces them with the affected asset and makes the kit non-ready; it does not
average designs, promote an asset, or substitute a new version. The separate V02
human approval/rights gate remains required for production media.

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

### Resumable job ledger (V03a)

The companion now has a provider-neutral schema-v1 job ledger stored separately
from the MIDI project and asset manifest. Each request freezes provider/model,
options, prompt, approved reference pins, optional seed, estimated cost and
attempt cap; its SHA-256 fingerprint supplies an attempt-specific idempotency
key. A duplicate fingerprint returns the existing durable job rather than
submitting again. A stable sibling lock serializes each ledger mutation and its
provider call across coordinator instances and local processes, including
symlinked directory aliases. Lock contention fails after five seconds without
submitting; a caller may retry later. The OS releases ownership after exit, and
the lock file remains in place so atomic ledger replacement cannot split locks.
Provider adapters must bound their own call durations and must not reenter a
mutation on the same ledger. Only the coordinator can publish ledger updates.

Admission requires a known non-negative estimate in the budget currency and
reserves every prior attempt at its actual cost when known (otherwise its
estimate). A request that would exceed the immutable batch ceiling is rejected
before the provider interface is called. Each immutable batch also has a bounded
in-flight limit (one by default). The ledger records `submitting` before
submission. If a provider does not return a job ID, it becomes
`submissionUncertain`; restart recovery neither polls nor retries it. Jobs with
an ID are polled in place with persisted rate-limit backoff; cancellation is
recorded as a request and never claims a refund. Only a recorded failed or
cancelled attempt may start the next bounded attempt.

V03a supplies no provider adapter, credentials, network client or live request.
Its contract is exercised with an owned fake provider. V03b owns a selected API,
secure configuration, quarantined output download and manual clip import.

### Runway adapter and manual import (V03b)

The companion uses the reviewed Runway Dev REST request shape only: HTTPS
`POST /v1/image_to_video`, bearer credentials from `RUNWAYML_API_SECRET`,
`X-Runway-Version: 2024-11-06`, `gen4.5`, one approved PNG/JPEG/WebP still as a
data URI, `1280:720`, and five seconds. The token is neither Codable nor written
to the V03a ledger or diagnostics. The provider adapter is injected through the
existing coordinator, so its durable `submitting` state is written before the
HTTP call; a transport timeout remains `submissionUncertain` and cannot be
blindly resubmitted. Task polling maps Runway rate limiting to the persisted
V03a backoff mechanism.

A successful provider task yields only an HTTPS output URL. Its bytes must enter
an external digest-addressed quarantine before any asset record: a declared
content-length mismatch, empty/oversized response, digest mismatch, or unreadable
video is discarded without publication. A digest-valid clip remains unapproved
review evidence. `melotrail-tabi-animation import-manual-clip` provides the
separate owned/user-provided local-video route; it copies through the immutable
asset-library import boundary and returns a proposed record, never changing the
source or an existing manifest. Neither path makes a paid request or auto-approves
identity/loop quality.

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

### Deterministic soundtrack timing (V04a)

The companion first opens one regular local finished-soundtrack file against a
caller-pinned SHA-256 digest and records its exact single audio-track duration. It may
then read only a caller-digest-pinned `melotrail-midi-export` manifest whose
schema/validation status, fixed PPQ/tempo, and gap-free section ticks are valid.
The manifest stays a section-timing suggestion; it is never rendered as audio
and no MIDI project, snapshot, manifest, or soundtrack bytes are written.

The read-only caller `melotrail-tabi-animation plan-timing <request.json>` prints
the shared plan as JSON. Its request contains `soundtrackPath`, `soundtrackSHA256`,
optional paired `manifestPath`/`manifestSHA256`, integer `frameRate`, and
`alignment` with `leadIn`/`tail` objects (`numerator`, `denominator`). Decoded
rationals are validated and normalized; changed soundtrack bytes reject planning.

The caller must state `leadIn` and `tail` as non-negative rational durations.
With a manifest, `leadIn + final MIDI tick time + tail` must equal the measured
soundtrack duration exactly or planning rejects the mismatch. This makes a
changed tempo, shorter/longer bounce, or unconfirmed decay explicit instead of
trimming, stretching, or shifting music. Without a manifest, lead-in and tail
must both be zero and the whole soundtrack is one timing scene.

At an integer output frame rate, every scene end uses `ceil(exact seconds ×
fps)`; each next start reuses that prior frame end. The resulting half-open
frame ranges are contiguous and the last range ends at the one deterministic
ceil-rounded soundtrack boundary. V04 composition, V05 preview, and V06 output
must consume this plan rather than independently rounding timestamps.

### Deterministic scene composition (V04)

The companion's `plan-scenes` caller combines that timing plan with a selected
external asset-library manifest. Every referenced asset must be an approved,
digest-validated, scene/identity-compatible pin. Each timing scene names one
interior, one window mask, one or more scrolling scenery layers, an optional
approved action clip and its bounded source-frame loop schedule. Scenery layers
carry the window-mask pin that clips them, so a renderer cannot place scrolling
content over the train interior. Reusing a pinned clip is allowed, but two
adjacent scenes may not select the same action episode.

Crossfades are explicit visual overlaps in the ending frames of a timing scene;
they never alter its rational boundaries, move later scenes or modify the
soundtrack. The plan exposes renderer-neutral frame facts (asset pins, integer
parallax phase, action source frame and rational opacity). Preview and encoding
must revalidate pinned asset bytes and consume those facts rather than choose
their own scene timing. This is only deterministic composition from approved
assets; it does not approve real visuals, loops, identity, rights or an A/V
pilot.

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

The editor keeps those regions reachable at 1536×1024, 1280×900 and 720×900:
at compact width, the same real preview, scrollable selected-scene inspector,
transport and horizontal scene strip stack rather than scale into clipped
controls. Space toggles local preview, Left/Right seek one shared frame,
Home/End seek the soundtrack extent and Escape stops local preview; active text
fields keep their normal editing keys. Labels name the preview, scene timeline,
inspector, transport and edit actions for keyboard and accessibility clients.
These controls affect only the existing shared composition plan and one
soundtrack player; they do not submit, cancel or imply approval of provider jobs.

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

`companion/` is an independent Swift package with no third-party or MIDI
dependencies. The README owns the native Swift build/run commands. Host checks on
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
The V01 production-preset and packaging decision follows below. Encoding an
actual episode, including its final dimensions and publish staging, remains V06.

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

## V01 decision record — 2026-09-09

This is a conservative dependency and pilot decision, not an authorization to
spend money, send media to a provider, or distribute a video. The companion stays
at [`companion/`](../companion/), independently built with Swift Package Manager.
It is not included in the MIDI Gradle build and has no MIDI Core runtime,
schema, project-path or export dependency.
The only currently proved local media configuration is the
owned spike: AVFoundation's OS-supplied ProRes 422 `apcn` video and PCM audio in
MOV, 320x180 at 30 fps for one second. Its decoded first/middle/final frames,
streams, duration and 44,100 PCM samples are checked by the existing regression.

| Decision | Selected value and limit | Evidence / consequence |
| --- | --- | --- |
| Companion package | Swift tools 6.0 package, macOS 14+; host proof used Swift 6.3.3 on macOS 26.6.2 (25G83) | `swift build -c release` creates a separate native executable. No app bundle, signing, notarization, installer, or redistribution right is claimed yet. V06 owns a real delivery package. |
| Local encoder | Apple AVFoundation; `apcn` ProRes 422 plus PCM in QuickTime MOV | The encoder is an OS framework, not a pinned or redistributed binary. The above short fixture is the codec proof; FFmpeg is not selected. Its OS/SDK agreement and the installed OS build must be recorded for every later actual encode. |
| Pilot source/master preset | User-selected, immutable finished Logic PCM WAV at 44.1 kHz; one 16:9, 1920x1080, 30 fps ProRes 422/PCM MOV master | The exact 320x180/mono fixture is proven. Stereo source handling, 1920x1080 output and actual full-song duration are V04/V06 validation obligations, so this target is not a claim that a production master has already encoded. No H.264/AAC, alpha, HDR, codec conversion, audio normalization, trimming, stretching or remastering is selected or promised. |
| Provider | Runway Dev REST `POST /v1/image_to_video`, API version `2024-11-06`, model `gen4.5` | Runway's current guide shows a bearer-authenticated task request with one `promptImage`, `promptText`, `1280:720` ratio and five-second duration, returning a task ID. V03 must persist/query that ID rather than blindly retry. Only an approved still may be sent; the finished soundtrack remains local. |
| Generation preset | One 1280x720, five-second, image-to-video micro-action per request; no generated audio | The documented example establishes this request shape, not character continuity, start/end-frame conditioning, seed reproducibility, matte/alpha, or a complete scene. Composition/parallax supplies the rest of the 30–60 second scene. |

### Provider constraints and rights gate

The Runway facts were rechecked on 2026-09-09 without an account, credential,
upload, request or spend. Its API documentation currently lists `gen4.5` at 12
credits/second and developer credits at US$0.01 each: one five-second request is
60 credits (US$0.60 before tax). ProRes/PNG sequence output would add five
credits/second, so it is excluded from the generation request; the companion's
local ProRes master is separate. Prices, model availability and API terms can
change, and the V03 admission screen must re-read them before any submission.
[Runway API guide](https://docs.dev.runwayml.com/guides/using-the-api/),
[pricing](https://docs.dev.runwayml.com/guides/pricing/).

Runway's terms require the user to hold the necessary rights, licences and
permissions for every submitted input and describe additional obligations when
API functionality is exposed to end users (including its then-current branding
requirements). Its privacy policy treats submitted prompts, images, music/audio,
video and associated metadata as user content. Therefore the supplied TABI and
train pictures remain reference-only: they are not cleared production assets or
permission to submit them to a provider. The same is true of any finished Logic
bounce until its owner confirms the rights listed below. [Runway terms](https://runway.com/terms-of-use),
[privacy policy](https://runway.com/privacy-policy).

Before a paid request, the user must provide all of the following:

- Written confirmation that the selected finished bounce can be used, synced and
  locally distributed, including its composition/master/performance/sample rights.
- Provenance and permitted provider/distribution use for every reference image,
  approved TABI/train asset and any third-party visual; either clear the supplied
  references or replace them with owned assets.
- An account owner who accepts the then-current provider terms/privacy handling,
  confirms the actual credit balance/tax, and accepts any API attribution or
  end-user terms that apply to the intended companion distribution.
- A human approval of the TABI identity and the one approved still for each
  submitted micro-action. A provider result is proposed media, never automatic
  approval or proof of continuity.

### Bounded paid-pilot proposal — WAITING_USER

Propose exactly four actions (blink/breathe, look out, writing, and steam), one
five-second `gen4.5` request each, with at most one retry per action. That is at
most eight requests / 40 generated seconds / 480 credits / **US$4.80 before tax**.
There is no auto-billing, upscale, professional-output surcharge, second provider,
long-form generation, upload or public release in this proposal. Rejected clips
still consume their attempt; if none is approved, the pilot stops rather than
expanding the budget. The actual tax-inclusive payment ceiling and the rights
inputs above are absent, so paid V03 work is **WAITING_USER**. Independent
owned-fixture work remains allowed.

The provider account tier and actual balance are unverified. V03 must read its
current task/concurrency and daily generation limits, using one in-flight pilot
request at a time even if the account permits more.
[Runway usage tiers](https://docs.dev.runwayml.com/usage/tiers/).

V03b recovery validates staged ISO movie bytes using their container header,
rather than treating the temporary suffix as the media format. Quarantine keeps
the matching MOV/MP4 suffix without transcoding and rejects partial HTTP responses.
The Runway request preset was rechecked against the
[official API guide](https://docs.dev.runwayml.com/guides/using-the-api/)
on 2026-09-09; no live request was made. The
[input contract](https://docs.dev.runwayml.com/assets/inputs/) caps encoded still
data URIs at 5 MB and Gen-4.5 input ratios at 0.5–2.0; these are checked before
job admission. Prepared requests freeze the bounded bytes and verify their digest
against the approved asset before the ledger reserves an attempt. The HTTPS collector enforces the byte cap during reception and
rejects insecure redirects; quarantine rejects symlink digest destinations.
