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

The newer pastel station (`pastel-tabi.png`) and
listening pose (`patel-tabi-music.png`) show dusty lilac TABI,
blush gills, a pale sage scarf, seated stillness and headphones worn for music.
The second filename is actually `patel-tabi-music.png`; preserve the supplied file.
Their station surroundings remain detailed, amber-lit and strongly textured.
The user's latest direction goes further: simple, cozy, zen, pastel, few colors,
and scenery interpreted as art rather than naturalistic rendering.

This direction supersedes the previous rich neon/navy palette. Recoloring TABI
to fit a scene palette is explicitly allowed during style exploration. Preserve
recognition through the axolotl silhouette, feathery gills, face, forehead star,
curled tail and headphones. Jacket/scarf/accessory details may be simplified.
V02 resolves proportions, markings, palette and costume into one approved
identity version after visual comparison. No trial below is user-approved yet.
Use the original train scenes for layout, and the pastel pair for softness and
calm poses. Preserve the station's memorable arch, bench, lamps, central TABI EKI
sign, train, plants and suitcase when it is used as the channel banner. Simplify
surface texture and secondary writing without flattening the scene into an empty
platform. Reduce glossy materials and heavy amber lighting.

## Pastel style exploration and reusable generation brief

Some earlier reference and trial files were not included in the supplied Git
commits and are absent locally. Their filenames and design decisions are retained
below as historical context; only available assets are linked. Use the retained
v3 banner and style trials for review until missing originals are recovered.

Four proposed single-scene trials were generated on 2026-09-06 with the built-in
image tool before expanding the asset kit. They compare the same seated listening
pose, camera and train/window layout while varying the art medium and restrained
palette: [Paper Moon Railway](pictures/video/style-trials/paper-moon-railway.png),
[Apricot Quiet](pictures/video/style-trials/apricot-quiet.png),
[Lilac Sunday](pictures/video/style-trials/lilac-sunday.png) and
[Moonmilk Express](pictures/video/style-trials/moonmilk-express.png). They remain
proposed style examples until the user chooses or rejects a direction. The prompts
below reproduce their intent; they are not an authorization for paid jobs. Attach
the actual reference images when using them in an image tool because filesystem
paths alone do not transfer images.

The first channel-banner trial (`tabi-channel-banner-master.png`)
was rejected because it removed too much of the station and omitted the channel
name. The revised banner master (`tabi-channel-banner-v2-master.png`)
preserves the station composition and exact central **TABI EKI** sign. Its
upload-sized PNG (`tabi-channel-banner-v2-2560x1440.png`) and compact
upload JPEG (`tabi-channel-banner-v2-upload.jpg`) are 2560 × 1440.
TABI's face, headphones and name remain in the centered cross-device area. This
revision is proposed pending user review.

The proposed funky banner variant (`tabi-channel-banner-v3-funky-master.png`)
keeps the v2 composition and adds restrained rhythm ribbons, rounded color blocks,
checkerboard flooring, train-stripe motifs and small sparkles from the locked
pastel palette. Its 2560 × 1440 PNG (`tabi-channel-banner-v3-funky-2560x1440.png`)
and compact upload JPEG (`tabi-channel-banner-v3-funky-upload.jpg`)
were rejected because “funky” meant real physical objects, introduced gradually,
rather than abstract graphic decoration.

Three replacement banner trials add tangible music objects cumulatively while
preserving the v2 composition and **TABI EKI** sign:

1. [Step 1](pictures/video/banner-funky-steps/tabi-banner-real-funky-step-1.png):
   portable record player, record sleeves, lava lamp, backpack pins and sunglasses.
2. [Step 2](pictures/video/banner-funky-steps/tabi-banner-real-funky-step-2.png):
   adds a vintage radio, small disco ball, vinyl stack, hand percussion and a
   patterned ceramic pot.
3. Step 3 (`tabi-banner-real-funky-step-3.png`):
   adds a small analog synthesizer, guitar case, spare headphones, mushroom lamp,
   instant camera and floor cushions.

All three remain proposed until the user selects the preferred object density.

A further proposed funky Tabi variant (`tabi-banner-step-2-funky-tabi-master.png`)
uses Step 2 as its composition and object-density starting point. Tabi gains a
deep-plum beret, coral cat-eye sunglasses, a lilac striped scarf, brighter
headphones and small animal-print accents on the jacket and luggage. The whole
scene adopts a flat, lightly textured peach, lemon, yellow-green, coral, orange
and plum treatment with restrained shading. The station, train, plants, record
player, radio, disco ball, lava lamp, luggage, platform 7, 11:11 clock and exact
**TABI EKI** sign remain visible. Use the 2560 × 1440 PNG (`tabi-banner-step-2-funky-tabi-2560x1440.png`)
or compact upload JPEG (`tabi-banner-step-2-funky-tabi-upload.jpg`)
for review. This variant remains proposed pending explicit approval.

The supplied visual references are preserved under
`pictures/video/inspiration/` (`inspiration`) as inspiration-only
material. Borrow only broad fashion attitude, tangible beatnik/pop accessories,
flat palette and minimal-shading principles; do not reproduce their artwork,
embedded text or watermarks.

The latest proposed [green-coat revision](pictures/video/banner-funky-steps/tabi-banner-step-2-green-coat-master.png)
removes Tabi's beret and sunglasses, restores the visible face, and replaces the
brown jacket and scarf with a roomy emerald coat with a high pink collar, cuffs
and trim. It also reduces the plant density and retains exactly two crescent
motifs: the tall poster at far left and the lower-right luggage. The central
sign, record sleeve and train no longer carry moons. The exact **TABI EKI** sign,
11:11 clock, platform 7, music objects, train and Step 2 composition remain.
Review the [2560 × 1440 PNG](pictures/video/banner-funky-steps/tabi-banner-step-2-green-coat-2560x1440.png)
or compact [upload JPEG](pictures/video/banner-funky-steps/tabi-banner-step-2-green-coat-upload.jpg).
The cheetah gallery reference is preserved as inspiration-only material; its
characters, layout and individual artwork are not part of the TABI identity.

The current proposed funky-sign revision (`tabi-banner-green-coat-funky-sign-master.png`)
keeps that sparse green-coat scene, closes Tabi's eyes in a calm listening pose,
restores the richer peach/lemon/yellow-green/lilac/plum balance, and adds a small
closed-eye Tabi-head emblem in the station sign's crown. The exact **TABI EKI**
lettering uses a more playful rounded 1970s-inspired display treatment while
remaining clearly readable. The earlier two-moon limit and simplified plant
density remain unchanged. Review the 2560 × 1440 PNG (`tabi-banner-green-coat-funky-sign-2560x1440.png`)
or compact upload JPEG (`tabi-banner-green-coat-funky-sign-upload.jpg`).
The emblem and display lettering remain raster concepts pending explicit user
approval and later deterministic reconstruction if adopted as reusable branding.

Four isolated signature studies explore the **TABI EKI** wordmark and Tabi-head
emblem without the station artwork: [A — Soul Loop](pictures/video/signature-concepts/tabi-eki-signature-a-soul-loop.png)
uses connected soul-script lettering and a headphone portrait;
B — Tall Wiggle (`tabi-eki-signature-b-tall-wiggle.png`)
uses condensed organic capitals and a simple headphone-free stamp;
C — Soft Bounce (`tabi-eki-signature-c-soft-bounce.png`)
uses rounded stacked bubble letters and a three-quarter headphone portrait; and
D — Liquid Station (`tabi-eki-signature-d-liquid-station.png`)
uses dense liquid lettering with Tabi nested in a vinyl-record badge. These are
raster direction studies, not approved logos or font identifications. The four
supplied typography images are preserved under `pictures/video/inspiration/`
and contribute only broad period traits; their phrases, glyph drawings, layouts
and watermark are not to be reproduced.

Four additional face-only line studies test a simplified Tabi symbol without
wording or scenery: [A — Quiet Bloom](pictures/video/face-icon-concepts/tabi-face-line-a-quiet-bloom.png)
is the balanced headphone-free portrait; [B — Listening Halo](pictures/video/face-icon-concepts/tabi-face-line-b-listening-halo.png)
retains minimal headphones; [C — Side Drift](pictures/video/face-icon-concepts/tabi-face-line-c-side-drift.png)
uses an asymmetric three-quarter headphone view; and [D — One-Line Rhythm](pictures/video/face-icon-concepts/tabi-face-line-d-one-line-rhythm.png)
reduces the gills and head to a near-continuous flowing contour. All use only
deep-plum linework on cream and remain raster direction studies pending selection
and deterministic vector reconstruction.

The user selected **B — Listening Halo** as the icon direction for the next
banner comparison. Four otherwise matched raster banners place that line icon
in the sign crown and test the earlier lettering directions in context:
A — Soul Loop (`tabi-banner-listening-halo-a-soul-loop.png`),
B — Tall Wiggle (`tabi-banner-listening-halo-b-tall-wiggle.png`),
C — Soft Bounce (`tabi-banner-listening-halo-c-soft-bounce.png`)
and D — Liquid Station (`tabi-banner-listening-halo-d-liquid-station.png`).
The icon selection is authoritative for this comparison; the lettering and
combined banner remain proposed until the user chooses a font direction.

A subsequent proposed Soul Loop detail revision (`tabi-banner-soul-loop-cheetah-trim-pink-lamp-master.png`)
removes the headphone-cup star, left wall lantern and disco ball; replaces the
central dome lamp with a pink pleated paper pendant; changes the coat's pink
collar, cuffs and piping to a restrained golden-coral cheetah print; and removes
the logo medallion so the Listening Halo symbol appears as inverted cream
linework directly on the plum sign. The forehead star, exact **TABI EKI** name,
11:11 clock, platform 7 and two approved crescent placements remain. Review the
2560 × 1440 PNG (`tabi-banner-soul-loop-cheetah-trim-pink-lamp-2560x1440.png`)
or compact upload JPEG (`tabi-banner-soul-loop-cheetah-trim-pink-lamp-upload.jpg`).

The current [TABI EKI channel banner v3](pictures/video/tabi-eki-channel-banner-final-v3-master.png)
uses the tightened dusty-lilac sign without side sprigs, the small Listening Halo
icon and warm-yellow **TABI EKI** lettering. Tabi's headphone band and rim echo
the pendant lamp's dusty pink family, while the ear-cup face matches the bench
metal's deep plum. Broad station surfaces use cream, pale peach and dusty blush
instead of the earlier saturated orange cast. This revision uses the sharper
pre-sign-exploration station artwork as its drawing-quality and geometry master,
then transfers the selected character, costume, sign, lamp and palette decisions
onto it. Small props, train panels, luggage, bench scrollwork and character details
therefore retain crisp contours, coherent internal lines and controlled paper
grain. Use the [2560 × 1440 PNG](pictures/video/tabi-eki-channel-banner-final-v3-2560x1440.png)
or compact [upload JPEG](pictures/video/tabi-eki-channel-banner-final-v3-upload.jpg).

A fifth proposed [Paper Moon with Lilac window](pictures/video/style-trials/paper-moon-lilac-window.png)
combines Paper Moon's character, cabin and tactile paper treatment with Lilac
Sunday's pale limited-print Tokyo scenery. It is a hybrid comparison candidate,
not a channel geography commitment or an approved production master. The channel
theme is travel through music across imagined and varied places; shared branding
must not use Japan-specific landmarks, scripts or symbols as its defining setting.

Copy the common brief and append exactly one trial below per generation:

```text
Create one finished 16:9 illustration exploring a distinctive visual language
for TABI's cozy music-and-travel channel. Deliver a single scene, without a
collage, labels or palette swatches. Use the highest supported native quality;
retain the original output for later animation work.

References: tabi.png establishes recognizable axolotl anatomy; pastel-tabi.png
and patel-tabi-music.png establish gentle character colors, resting posture
and quiet listening; the earlier train images establish the window composition.
Translate these references into the art medium specified in the selected trial.

Scene: fixed eye-level three-quarter view inside a softly rounded train cabin.
TABI sits in the left third, eyes peacefully closed, headphones on, small paws
resting in the lap, curled tail visible. A large rounded rectangular window
fills the right half. A simple table holds one cup and one closed notebook.
Outside, suggest Tokyo through a few rounded architectural silhouettes and
one simplified recognizable tower. Treat buildings, sky and distance as painted
shapes; let the architecture feel like a quiet imagined travel illustration.
Keep the same composition and pose across all four trials.

Character: retain TABI's axolotl silhouette, soft branching gills, recognizable
face, one forehead star, curled tail and round headphones. Adapt body, gills,
scarf and coat to the selected palette. Simplify tiny costume decorations;
use matte surfaces and soft tonal separation so TABI remains clearly readable.

Signature visual grammar: repeat rounded window corners in the headphone cups,
cup handle and architecture; place TABI's small star motif sparingly on the
forehead and headphone badge; give every object a gentle, slightly imperfect
handmade contour. Keep plenty of breathing room and broad quiet color fields.

Use only the selected four-color family with subtle tonal variations. Suggested
balance: 65 percent light base, 25 percent supporting color, 8 percent darker
structural color and 2 percent accent. Hex values are visual targets, not a
promise of exact generated pixels. Use muted dark color only for essential
features. Favor diffuse light, restrained texture and clear large shapes.

Avoid neon, rainbow accents, photographic detail, 3D rendering, glossy toy
surfaces, heavy black outlines, dramatic bloom, dense foliage, brick textures,
crowded shelves, ornate signs, extra passengers, floating decorations, readable
text, logos and watermarks. The mood is warm, quiet, contemplative and welcoming.
```

| Trial | Palette targets | Append to common brief |
| --- | --- | --- |
| A — Paper Moon Railway | Oat `#F2EBDD`, sage `#B9C6B2`, dusty mauve `#A597AD`, warm charcoal `#625F59` | Flat illustration built from a few overlapping cut-paper shapes, gently irregular edges, subtle paper fibers and shallow contact shadows. Oat cabin, sage city silhouettes, dusty mauve TABI with pale oat gills. Three broad exterior layers. Airy, tactile and quietly playful; all elements including TABI share the paper treatment. |
| B — Apricot Quiet | Cream `#F4EBDD`, pale apricot `#E6C2AD`, clay taupe `#B4A198`, muted umber `#6D625D` | Matte gouache illustration with broad opaque brush shapes and soft dry-brush edges. Cream TABI with pale apricot gills and taupe scarf, separated from the cream cabin by taupe seat and soft shading. Architecture dissolves into a few warm blocks. Diffuse afternoon light, minimal contours, gentle painted depth. |
| C — Lilac Sunday | Rice paper `#F0EBE2`, mist lilac `#C1B8CE`, faded blue `#A7B7C1`, deep plum-gray `#665F73` | Limited-ink print illustration with restrained risograph-like grain and softly imperfect ink coverage. Flat shapes, sparse plum-gray pencil contours, no registration ghosting on face or hands. Lilac TABI, rice-paper gills, faded-blue cabin and graphic Tokyo silhouettes. Large unprinted areas and one small pale sun. |
| D — Moonmilk Express | Milk `#EEEAE2`, fog `#CEC8D5`, dusty lavender `#A69AAF`, dusk plum `#696173` | Nearly monochrome pastel illustration using smooth chalky shapes, broad atmospheric bands and extremely sparse soft contours. Fog-colored TABI with lavender gills and dusk-plum headphones. A pale moon, a single stylized tower and two bands of buildings. Dreamlike proportions, spacious composition, cozy low-contrast evening with a readable face. |

Recommendation for first comparison: A has a clear repeatable shape language
and useful separable layers; D tests the most restrained palette. Distinctiveness
comes from a consistent combination of character, shapes, palette and motion.

After a direction is chosen, retain its composition and identity for three
action studies: reading an open book, lifting the cup for a sip, and glancing
outside. Then test the matching station-bench scene from the pastel references:
one simple arch, bench, train silhouette and suitcase, with generous open space.
Keep the station as an optional departure/arrival scene. These studies validate
the style beyond one attractive frame before deriving the production assets.

Apply the chosen style to the kit below. Create separate full-resolution assets
from an approved master: cabin with transparent window opening, empty seat area,
foreground table/occlusion, character actions, independent props and exterior
layers. Keep a common canvas, placement anchors and window mask. Exterior-only
plates contain no cabin/window frame; scenery scrolls behind the fixed opening.
Keep paper/print grain stable during animation. Check actual layer alignment,
alpha and scrolling seams; an image prompt cannot guarantee rig-ready output.
Day/evening variations shift values within the same palette instead of adding
new saturated hues. Use slow scenery drift, occasional blinks and gentle
breathing; reserve larger gestures for reading, sipping and looking outside.

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
| Character | Seated listening, reading, sipping and looking out | Separate transparent files or masked approved clips; stable pivot/scale |
| Interior | One fixed train/table/window composition | Empty character area, foreground occlusion and window mask |
| Scenery | Abstract Tokyo journey, then day/dusk variants within the chosen palette | Sparse skyline/midground/foreground layers suitable for seamless scrolling |
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
At the V01 decision, the proved local media configuration was the
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

V06 validation update — 2026-09-10: the selected ProRes 422/PCM MOV path now
encodes resolver-backed owned scenes at 320×180 mono/one second and 1920×1080
stereo/twelve seconds, 30 fps. The native writer delivers 32-bit float PCM and
compares decoded source/output PCM sample digests exactly. First/final decoded
frames, streams, timing, provenance, cancellation and collision-safe paired
publication are checked. Evidence lives at
`~/.codex/melotrail-terra/v06-repair-evidence/`; see [Validation](VALIDATION.md).
This extends the short V01 codec proof without selecting another preset or
claiming a production full-song pilot, approved TABI assets, Logic listening
approval, signing/distribution rights, generation budget or public upload.

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
