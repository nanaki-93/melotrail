# TABI video

Owner: video product, asset and media contract. Current delivery is organized as
**VG1–VG6** in [PLAN](../PLAN.md#6-video-generation-features) and
[TASKS](../TASKS.md). Backend/runtime foundations exist; the integrated Video tab
and complete generation flow remain planned.

This document preserves supplied artwork, scoped artistic decisions and runtime
measurements. Older task IDs, commands and dated status statements below identify
their original evidence; they are not active queue dependencies or authorization
to resume an experiment. TASKS alone owns current work. The current early visual
gate is **VG6-02** and full-video/editor acceptance is **VG6-06**; neither is passed.
Video-flow design permission/approval is separately required by VG3.

## Outcome and scope

**Current scope, 2026-09-16:** the user creates all finished picture assets outside
Melotrail. The app accepts a **finished scene image, with optional separate
character/background layers**, then generates motion and a complete continuous
180–300 second silent video (default 240s), 1920×1080 H.264 MP4 at 30 fps. No image
generation, outfit/style transfer, inpainting or new background artwork is required
or offered in this delivery. Audio editing and public upload remain external.

The planned Video tab needs no MIDI project or soundtrack. It owns separate
projects, assets, jobs, models and media outputs, loaded lazily. Missing video
tools cannot block MIDI. The superseded Swift handoff has been removed from active wiring.

1. Open Video and import a finished PNG/JPEG scene.
2. Optionally import ready transparent character/pose images, clean backgrounds,
   extended scenery and foreground/masks. Clothing and style are already drawn.
3. Enter a motion prompt, choose duration and optionally place supplied layers,
   anchors or motion controls visually. No JSON or node editor is required.
4. Generate a short preview or complete video using supported local video action
   generation and controlled motion. Preserve the uploaded look; do not generate
   another picture as a prerequisite. Explain missing motion inputs before work.
5. Preview moving results, adjust/retry affected work, retain prior versions and
   export a new silent MP4. Add music in the external Apple editor.

A finished scene is valid input to the measured ComfyUI image-to-video path;
independent character/environment controls may need externally prepared layers
or poses. Flat-image camera movement does not prove character animation or
continuous 3–5 minute coherence. Validate actual alpha, alignment, depth,
occlusion, anchors and scenery coverage before enabling controlled motions.
Missing clean plates or travel coverage request more external artwork, never
unrequested synthesis, stretching, freezing or whole-clip repetition.

The earlier Character/Outfit/City inspiration synthesis and automatic look
creation are outside the delivery. Their failed experiments are not prerequisites
or permission for further inference. The reported archive
`~/.codex/melotrail-video-sequential/evidence/V18b1/updated-assets-f96a7f3` is
unavailable and no verified backup was supplied; its historical identities remain
unauthenticated. Recovery/reconciliation is not a current delivery requirement,
and no historical pass is inferred. Current input/runtime integrity still matters.

Reuse existing project, import, prepared-scene, ready-asset and prompt contracts.
VG1 verifies them, VG2 completes durable previews, VG3 exposes the application flow,
and VG4–VG6 deliver continuous rendering/export and real acceptance. Existing
uncommitted preview work is not a completed product or artistic approval.

The source picture controls appearance; prompts/controls describe motion. Requests
for different outfits, cities or style require replacement artwork. Preserve all
original bytes, immutable results and exact consumed pins; unused inspiration is
context only. Unsupported actions stay visible. Tokyo, trains, coffee and TABI
are examples, never required presets. Preserve the approved motion/steam checkpoint
and latest charcoal/stone v6 artistic choice without inferring new visual approval.

## Current TABI asset kit

The user confirmed **this entire `docs/pictures/video/` tree, including all
subfolders**, as the source collection for video work, not just the kit linked
below. Select inputs appropriate to each requested video and pin their actual
paths/bytes. Preserve originals; derived files and outputs belong in separate
video-project/evidence storage. Existing inspiration-only references are not
silently promoted to finished artwork or licensed production assets. Validate
alpha, geometry, poses/masks and scenery coverage before promising independent
motion. Missing inputs are actionable capability gaps, not reasons to recover
old archives. Use small owned fixtures for technical tests and current selected
assets for fresh moving-video evidence; VG6 still needs real user decisions.

The user refreshed the supplied artwork in commit
`f96a7f36430d45c576de511be94480e31762570b`, merged into the video implementation
branch. Use the [character profile](pictures/video/tabi-assets/character-profile/tabi-character-profile.png),
[expressions](pictures/video/tabi-assets/emotions),
[actions](pictures/video/tabi-assets/train-actions),
[walking poses](pictures/video/tabi-assets/walking),
[outfits](pictures/video/tabi-assets/fits) and
[country artwork](pictures/video/tabi-assets/country-outfits) as selected inputs.
The approved style reference remains the
[charcoal/stone v6 banner](pictures/video/tabi-eki-channel-banner-charcoal-stone-v6-upload.jpg).
These supplied pictures still need ready-asset validation before being treated as
separate motion-ready layers; their presence does not prove animation readiness.

## Historical references

The descriptions below record earlier artwork. Links to removed images point to
the pre-refresh Git revision; use the current kit above for new work.

### What the earlier references established

[Character identity](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/tabi.png) is the cosmic axolotl traveler sheet;
it still contains “Moki”. When a request selects these references, new output uses
**TABI**. The character has an
indigo/violet body, pink-purple feathery gills, dark glossy eyes, cheek/star
markings, headphones, travel jacket/scarf, satchel and travel accessories.

The [morning train scene](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/Morning%20Lo-Fi%20Train%20Ride%20with%20Tabi.png)
and the three other supplied train scenes under `pictures/video` establish
composition and mood: TABI at left, three-quarter view toward the right, fixed
window/train/table geometry, passing Japanese city/countryside, layered depth,
soft light and cozy cinematic 2D illustration. They are optional source references,
not already separated, rigged, licensed-for-redistribution animation assets and
not the product's universal content contract.

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
When a prompt selects TABI references, V24 checks their identity traits in the
actual generated result. No trial below is user-approved yet.
Use the original train scenes for layout, and the pastel pair for softness and
calm poses. Preserve the station's memorable arch, bench, lamps, central TABI EKI
sign, train, plants and suitcase when it is used as the channel banner. Simplify
surface texture and secondary writing without flattening the scene into an empty
platform. Reduce glossy materials and heavy amber lighting.

## Preserved artistic exploration and reusable example brief

This section is reference-specific artistic history, not a required generation
preset or normal-workflow prompt. Removed tracked images link to their retained
Git revision; other absent originals remain filename-only historical context.
For current work, use the refreshed TABI kit and selected charcoal/stone v6 banner
listed above.

Four proposed single-scene trials were generated on 2026-09-06 with the built-in
image tool before expanding the asset kit. They compare the same seated listening
pose, camera and train/window layout while varying the art medium and restrained
palette: [Paper Moon Railway](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/style-trials/paper-moon-railway.png),
[Apricot Quiet](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/style-trials/apricot-quiet.png),
[Lilac Sunday](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/style-trials/lilac-sunday.png) and
[Moonmilk Express](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/style-trials/moonmilk-express.png). They remain
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

1. [Step 1](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/banner-funky-steps/tabi-banner-real-funky-step-1.png):
   portable record player, record sleeves, lava lamp, backpack pins and sunglasses.
2. [Step 2](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/banner-funky-steps/tabi-banner-real-funky-step-2.png):
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
`pictures/video/ideas` (`inspiration`) as inspiration-only
material. Borrow only broad fashion attitude, tangible beatnik/pop accessories,
flat palette and minimal-shading principles; do not reproduce their artwork,
embedded text or watermarks.

The latest proposed [green-coat revision](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/banner-funky-steps/tabi-banner-step-2-green-coat-master.png)
removes Tabi's beret and sunglasses, restores the visible face, and replaces the
brown jacket and scarf with a roomy emerald coat with a high pink collar, cuffs
and trim. It also reduces the plant density and retains exactly two crescent
motifs: the tall poster at far left and the lower-right luggage. The central
sign, record sleeve and train no longer carry moons. The exact **TABI EKI** sign,
11:11 clock, platform 7, music objects, train and Step 2 composition remain.
Review the [2560 × 1440 PNG](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/banner-funky-steps/tabi-banner-step-2-green-coat-2560x1440.png)
or compact [upload JPEG](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/banner-funky-steps/tabi-banner-step-2-green-coat-upload.jpg).
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
emblem without the station artwork: [A — Soul Loop](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/signature-concepts/tabi-eki-signature-a-soul-loop.png)
uses connected soul-script lettering and a headphone portrait;
B — Tall Wiggle (`tabi-eki-signature-b-tall-wiggle.png`)
uses condensed organic capitals and a simple headphone-free stamp;
C — Soft Bounce (`tabi-eki-signature-c-soft-bounce.png`)
uses rounded stacked bubble letters and a three-quarter headphone portrait; and
D — Liquid Station (`tabi-eki-signature-d-liquid-station.png`)
uses dense liquid lettering with Tabi nested in a vinyl-record badge. These are
raster direction studies, not approved logos or font identifications. The four
supplied typography images are preserved under `pictures/video/ideas`
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

For the current bounded V11 trial, the user selected the compact v3 upload
banner as the highest-priority style, palette and TABI-appearance reference.
The character sheet is secondary anatomy evidence, and the style-trial images
contribute Tokyo-window composition only; they do not override the banner's
colors, clothing or rendering. This precedence belongs to that selected trial,
not to the generic assets-and-prompt product contract.

A fifth proposed [Paper Moon with Lilac window](https://github.com/nanaki-93/melotrail/blob/6f489d9ece4441a9e3b0e10dc1bf0c09d89c868d/docs/pictures/video/style-trials/paper-moon-lilac-window.png)
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

## Retired TABI pilot kit (preserved reference history)

The following kit was proposed for the superseded soundtrack companion. It is
preserved to explain existing artwork and may inform a future prompt when the
user selects these assets. It does not define required inputs, duration, scene
content or acceptance for the new Video tab.

| Group | Pilot contents | Required production properties |
| --- | --- | --- |
| Identity | One approved front/side/three-quarter reference set | Stable proportions, costume, markings, palette, scale and exclusions |
| Character | Seated listening, reading, sipping and looking out | Separate transparent files or masked approved clips; stable pivot/scale |
| Interior | One fixed train/table/window composition | Empty character area, foreground occlusion and window mask |
| Scenery | Abstract Tokyo journey, then day/dusk variants within the chosen palette | Sparse skyline/midground/foreground layers suitable for seamless scrolling |
| Props | Notebook/pencil, cup, ticket, camera and bag | Transparent independent files with placement anchors |
| Atmosphere | Reflections, light, steam; rain only if needed | Separate layers and bounded opacity/motion |
| Animation | Blink/breathe, writing/hand action, glance, steam | Short validated loops/takes with consistent camera and identity |

This historical proposal targeted a 16:9 1920×1080 soundtrack-bearing output.
The replacement instead exports a silent H.264 MP4 at the cadence proved by the
selected V11/V12 workflow. All generated source outputs remain stored in their
original quality.

Later library expansion may include the original requested reading/sleeping/
smiling/curious/surprised/photographing/ticket/map/postcard/waving/headphone/
stretching/luggage/walking/yawning/weather-watching actions; front/side/back
views; table/window/aisle/tunnel/station/reflection/light plates; Tokyo landmarks,
Fuji, countryside/coast, blossoms, rain, snow and autumn; and steam/page/eye/
gill/tail/cable micro-motion. Add only assets useful to an episode, not a Cartesian
product of every pose, weather and camera angle.

## Planned independent asset and project contract

Each imported reference has an ID/version, local immutable file/digest, optional
role, original source,
creator/license or user ownership statement, permitted uses, creation/provider/
model/prompt/reference provenance when generated, dimensions/frame rate/duration,
alpha/mask facts, compatible request/look identity and review state. Subject,
environment, style and complete-scene roles are optional; one asset is sufficient.

Keep originals, approved derivatives and generated takes distinct. A revised
asset receives a new identity; jobs pin approved versions. Validate missing or
changed media on reopen and before encoding. Large media lives in user-selected
video-project storage outside the source repository and all MIDI roots; small
owned test media may be checked in. A thumbnail is a cache, not the canonical
asset. A video project also versions the free-form brief, optional shot overrides,
looks, attempts, immutable takes, selection/assembly and export snapshots.

### Historical companion manifest boundary (superseded V02a)

The independently built `companion/` package defined schema v1 records for
asset ID/version, SHA-256-pinned relative media path, type, source/creator and
creation provenance, rights/permitted uses, geometry (including alpha, mask,
pivot, anchors and compatible scene/identity versions), and a proposed/approved/
rejected decision record. The library validates each media file's presence,
digest and declared dimensions before a caller can resolve exact approved pins.
It rejects traversal outside the selected library; it never reads or writes a
Melotrail MIDI project. The manifest records no production asset or human
approval: V02b/V02 still own import, real geometry/identity inspection and the
user's coherent-kit decision.

### Historical companion import and pilot-kit inspection (superseded V02b)

The companion imported a selected regular local file by copying it to a new
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

## Current continuous-animation strategy

Use the selected owned ComfyUI/LTX API for measured short image-to-video/action
takes and the external Node/Canvas compositor for supported continuous motion.
They share Kotlin project/job orchestration, but remain different execution stages.
The measured I2V route consumes one composed image, not independent inspiration
roles. Missing setup is explicit; no model download or hosted upload is automatic.

The primary output is one continuous 180–300-second scene at 30 fps, with evolving
scenery and supported occasional character actions. Reuse of small motion patterns
is allowed; repeating a complete short clip to fill the duration is not. The old
short-shot/whole-footage reuse planner is being replaced in VG4, not offered as
another primary mode. Record fresh action footage, procedural motion and reused
components honestly without summing overlapping layers into false unique seconds.

Validate scenery coverage for the complete trajectory before render. Derive rigid
depth-layer motion and occlusion from a shared camera path; join supplied scenery
outside the visible area. Missing clean plates, poses or travel coverage require
external artwork. Never silently stretch, freeze, wrap or reverse the scene.

Render bounded chunks with absolute frames and shared state/seed, preserving
blink phase, particle age, scenery position and random sequence on resume. Include
and trim temporal boundary support explicitly. Do not hold a whole 1080p cut in
RAM. Follow the measured ladder: retained five-second reference, fresh 20–30-second
previews, 60-second continuity/resource check, then actual 180–300-second output.
Short success does not prove full-length quality or a four-minute inference estimate.

Quality review checks actual moving output against the supplied appearance and
motion request: coherent objects, requested actions/effects, temporal stability,
continuity and joins. Look/take review is optional before a draft; unreviewed is
not approved, and rejected takes cannot silently become selected production work.
AI seeds are traceability inputs, not promises of deterministic model output.

## Local profile and bounded probe preparation (V11a)

V11a introduced `video/local-profile.json` as a schema-v1 unpinned candidate.
V11 now pins the released `draw-things-cli` bytes/source revision, its observed
single-image CLI capability, and the exact eight-file FLUX/LTX dependency bundle.
The profile remains explicitly `CANDIDATE_UNVERIFIED`: those setup pins contain
no runtime measurement, quality result or selection claim themselves. The first
host observation is recorded below; reference-conditioned video completion is
still unproven.

`videoLocalProbe` accepts one schema-v1 JSON request through the absolute
`-PvideoProbeRequest` path. The request pins a regular non-symlink tool file,
every model artifact and at least one reference by lowercase SHA-256. Reference
roles are optional (`SUBJECT`, `CHARACTER`, `ENVIRONMENT`, `STYLE` or
`COMPLETE_SCENE`), as are profile-specific reference bindings. The prompt remains
free-form. A request may contain at most two
candidate video profiles and three explicitly named output takes per profile.
The report and take paths must be distinct, absent children of one absolute
output directory, and must not contain or overlap any pinned input.
Tool, model and reference pin paths, the output directory, report path and every
take path must be absolute and contain no `.` or `..` components. Preparation
rejects these components before resolving or hashing that path; it never removes
them lexically, which could change the target when an earlier component is a symlink.
Every not-yet-existing path component (including the output directory) must use
only ASCII letters, digits, dots, underscores or hyphens. Collision checks treat
these names as case-insensitive even on case-sensitive volumes, including
ancestor/descendant conflicts. New Unicode names, including composed/decomposed
equivalents, are rejected rather than guessing a filesystem's Unicode rules.
Existing directory names and input filenames may contain Unicode; aliases of
existing prefixes are checked by native file identity without writable probes.
Resolved containment still rejects escapes into distinct directories, and the
report preserves requested output spelling.

Preparation reads and hashes those inputs, validates the unused output names and
prints a JSON report with `status: "NOT_RUN"`, null measurements and no selected
profile. It creates no directory or file, launches no executable, downloads
nothing and contacts no provider. Missing setup, changed pins, unknown bindings,
existing outputs and unsupported schema/backend/profile IDs fail with the exact
item that needs correction. Running the task without a request is intentionally
an actionable failure:

```sh
./gradlew :videoLocalProbe
```

This owned-fixture command reproduces a complete preparation without installing
or invoking a model. It also serves as the request schema example; replace the
three stub paths and hashes only after the explicit V11 setup choice:

```sh
probe_root=$(mktemp -d "${TMPDIR:-/tmp}/melotrail-v11a.XXXXXX")
printf '%s' 'owned tool stub' > "$probe_root/tool.bin"
printf '%s' 'owned model stub' > "$probe_root/model.bin"
printf '%s' 'owned reference stub' > "$probe_root/reference.png"
tool_sha=$(shasum -a 256 "$probe_root/tool.bin" | awk '{print $1}')
model_sha=$(shasum -a 256 "$probe_root/model.bin" | awk '{print $1}')
reference_sha=$(shasum -a 256 "$probe_root/reference.png" | awk '{print $1}')
request="$probe_root/request.json"
output="$probe_root/planned-output"
cat > "$request" <<JSON
{
  "schema": "melotrail-local-video-probe-request",
  "version": 1,
  "profileId": "draw-things-local-candidate-v1",
  "backendId": "draw-things-cli",
  "tool": {
    "id": "draw-things-cli",
    "path": "$probe_root/tool.bin",
    "sha256": "$tool_sha"
  },
  "prompt": "Animate the supplied subject turning toward a softly moving landscape.",
  "references": [
    {
      "id": "subject-reference",
      "pin": {
        "id": "reference-image",
        "path": "$probe_root/reference.png",
        "sha256": "$reference_sha"
      },
      "role": "SUBJECT"
    }
  ],
  "profiles": [
    {
      "profileId": "ltx-2.3-distilled-candidate",
      "modelPins": [
        {
          "id": "model-bundle",
          "path": "$probe_root/model.bin",
          "sha256": "$model_sha"
        }
      ],
      "referenceBindings": ["subject-reference"],
      "takes": [
        {"id": "take-1", "outputPath": "$output/take-1.mp4"}
      ]
    }
  ],
  "outputDirectory": "$output",
  "reportPath": "$output/preparation-report.json"
}
JSON
./gradlew :videoLocalProbe -PvideoProbeRequest="$request"
```

## Pinned local execution and current decision (V11)

The released Draw Things CLI `v1.20260430.0` is pinned by executable SHA-256
`7e5fb3af7dd99916d7671354a11fd40182bc6a0d16e7faf06fda7740c24915cd`
and official source revision
`a187a319a7c13a97d4100d4a824cf9e2747094b2`. Its `generate` command accepts
one `--image`. The aliases `--init-image` and `--input-image` merge into that
same single value; the released source passes no reference/control hints or
mask to generation. It therefore has no native CLI representation for several
independent uploaded reference roles. A one-image request can be measured, but
must not be described as multi-reference conditioning. A separately measured
composite input or supported lower-level API could be assessed later; neither
is part of this proof.

A request with an `execution` block runs one profile through the V12a pinned
process supervisor. It must pin the release/source revision, explicit models
directory, all eight exact FLUX.2 klein 4B and LTX-2.3 22B distilled 1.1 model
files, one reference binding, fixed stage settings and seeds, an absent output
directory, and direct-child PNG/keyframe, MP4/take and JSON/report paths. The
runner first generates a reference-conditioned FLUX keyframe, then passes that
exact generated PNG as the sole image input to each LTX I2V take. It runs at
most two prepared profiles and three takes per profile, although this released
route executes one profile per preserved request. Every invocation includes
`--offline`, `--no-download-missing` and `--disable-preview`; no configuration
file, LoRA, control, hosted provider or automatic download is admitted. A fixed
inline `JSGenerationConfiguration` supplies every field from the pinned source
schema before the explicit stage flags, so mutable recommended settings cannot
introduce optional models or change sampler/shift behavior.

The real profile fixes FLUX and LTX dimensions, steps, CFG, strength, sampler,
shift policy and seed in the request. The pinned released configuration uses
FLUX sampler 16, shift 3, fixed shift and clip-skip 2; LTX distilled uses sampler
19, shift 5, fixed shift and clip-skip 1. The bounded trial deliberately disables
the official LTX 1280 × 768 two-pass hires-fix/upscaler path and records its
single-pass 1024 × 576 deviation. LTX output uses H.264 MP4, 129 frames for each
5.16-second take, and the pinned CLI source's 25 fps LTX-2.3 cadence. These are requested
settings until the resulting file is independently inspected. The CLI's LTX
path can generate synchronized audio and writes it when present, so a silent
prompt cannot establish a silent stream. V12 owns stripping/encoding; the V11
report keeps decoded frame count, actual cadence/duration and audio presence
unset until the coordinator's read-only host inspection records them.

The runner creates one owner-only output directory, never reuses it, writes its
report with create-new semantics, and preserves successful and partial stage
artifacts on failure. A keyframe failure prevents all video launches; a take
failure prevents later takes. An optional absolute `cancellationFile` is watched
during each stage and cancels the owned process group through V12a when it
appears, allowing the host operator to stop on memory pressure or swap growth.
Multiple reference bindings produce a `FAILED` capability report with no process
launch and no reference marked consumed.

Supervised stage wall time is recorded for each invocation or failed launch
attempt. Cold/warm model-load time, peak memory and peak swap are explicitly unavailable from the supervisor
and require host evidence; baseline swap must not be attributed to the trial.
Successful processes report `COMPLETED_UNREVIEWED`, keep `selectedProfileId`
null and recommend against selecting this released CLI for the multi-reference
contract without further proof. Process success grants no visual approval. The
candidate status is not a selected claim. The first real host request,
`~/.codex/melotrail-video-sequential/evidence/V11/host-probe/cold-request-1.json`,
produced a FLUX keyframe in 46.913 seconds of supervised wall time (44.55 seconds
reported internally by the CLI), then failed the first 1024 × 576, 129-frame LTX
take. The host observer cancelled on native memory-pressure level 2; no later
take launched and no MP4 was produced. Baseline and peak swap were both
4,003,662,397 bytes, so observed swap growth was zero. Host nonfree memory peaked
at 51,476,217,856 bytes, including other applications and caches; this is not
LTX process RSS. No denoising progress or model-load boundary was captured in
the LTX stdout/stderr, and the failure cannot be attributed specifically to
weights, sampling or decoding. The actual stage report is
`host-probe/cold-run-1/probe-report.json`; host measurements and the cancellation
reason are in `host-probe/cold-request-1-observation/{status.json,memory.jsonl}`
under the same V11 evidence root. The keyframe has no human visual approval.

The recorded cancellation also exposed a cleanup-reporting defect: a raced
Darwin group signal returned EPERM after SIGTERM, but the supervisor retained
that error even after confirming group completion and reaping the leader. The
repair retains EPERM until bounded native cleanup establishes leader exit,
scoped group removal, both output EOFs and final reaping. An unconfirmed cleanup
still reports the original permission failure. This does not turn the cancelled
video into a successful take.

After repair validation, the recommendation is **at most one further diagnostic
LTX take**, using the same installed models at **576 × 320, 129 frames, 25 fps
(requested 5.16 seconds)**. Keep the existing 1024 × 576 banner-conditioned FLUX
stage and all seeds, 8 LTX steps, CFG 1, strength 1, sampler 19, shift 5, fixed
shift, single-pass `hiresFix: false` and disabled optional processing unchanged.
Use fresh output/cancellation paths and one take only. This diagnostic's 9:5
aspect ratio is close to, but not the final 16:9 delivery target. Its spatial
pixel/latent area is 31.25% of the failed take's; this is not an estimate of total
memory reduction. The pinned CLI source accepts dimensions in multiples of 64
(`DrawThingsCLI.swift:3003–3016`), and its LTX path derives spatial latents at
1/32 resolution and temporal latents as `(frames - 1) / 8 + 1`
(`LocalImageGenerator.swift:5177–5184`). The pinned model catalog retains
8 steps and guidance 1 (`ModelZoo.swift:864–876`). These support a bounded
smaller spatial diagnostic, not an official quality recommendation or a
guarantee that the unchanged weight-loading requirement fits this host.

The second request did not start inference: the source pin guard rejected the
installed Gemma checkpoint because its bytes had changed. The first CLI run had
converted the Qwen and Gemma checkpoints into small SQLite metadata files plus
adjacent `-tensordata` stores. This is released preprocessing behavior:
[`LocalImageGenerator.swift`](https://github.com/drawthingsai/draw-things-community/blob/a187a319a7c13a97d4100d4a824cf9e2747094b2/Libraries/LocalImageGenerator/Sources/LocalImageGenerator.swift#L4011)
requests text-encoder external storage, and
[`TensorData.makeExternalData`](https://github.com/drawthingsai/draw-things-community/blob/a187a319a7c13a97d4100d4a824cf9e2747094b2/Libraries/SwiftDiffusion/Sources/TensorData.swift#L67)
writes tensors into the adjacent store, changes the checkpoint and vacuums it.
A transformed digest is derived evidence, never an upstream model pin. Preserve
the converted pairs before restoring the exact approved downloads. That pin
failure consumed no second LTX inference attempt.

Each new probe now stages the complete eight-file bundle into its owner-only
`.models` directory using independent, create-new **1 MiB streaming copies**.
No links or installed sidecars are copied. Every copied file is checked against
its original pin before any CLI launch; both stages receive only this private
models directory. Empty `custom.json`, `custom_textual_inversions.json` and
`custom_lora.json` registries shadow ModelZoo's internal custom-file fallback;
all required exact model filenames must still exist before each stage. The
fixed generation override continues to disable optional model consumers.
Installed source files are never passed as writable model paths.

Admission requires twice the 46,321,545,216-byte bundle plus 4 GiB free:
**96,938,057,728 bytes**. During stages a metadata-only watchdog cancels on less
than 4 GiB free, more than 64 private files, a non-regular model artifact, or
private logical size above that admission allowance. This is a sampled guard,
not a filesystem quota; other applications can consume space between samples.
`.model-staging.json` records original pins and copying policy before copying.
The final report records each verified copy, final installed-source digests,
private file sizes/digests (including sidecars), and separate copy/verification
and final-audit wall times. A sidecar's `sourceModelId` associates its filename
with the copied checkpoint; it does not certify an upstream artifact hash.
All private copies, metadata, partial outputs and sidecars remain in the job on
success, failure or cancellation. No cache reuse or warm-load claim is made;
a later probe copies originals again. Abrupt JVM/host termination can leave the
initial manifest and partial files without a final audit; these remain evidence.
The two stage dimensions are validated independently, so the unchanged
1024 × 576 keyframe and 576 × 320 diagnostic video require no contract change.

Keep the host stop threshold at pressure level ≥2 or swap growth ≥2 GiB, without
closing user applications. Do not start while pressure is elevated, and do not
automatically retry after another pressure cancellation. If it fails, record
that this installed workflow has not completed a five-second shot within the
current host guard; do not infer universal local infeasibility. `--memory-saver`
and `--weights-memory` in the pinned CLI are LoRA training options, not generation
flags. No model change, download, tiled setting, hosted fallback or paid trial is
selected by this recommendation.

### Measured local decision — 2026-09-14

The single pending lower-resolution diagnostic has now run after both repairs
and restoration of all eight approved originals. Request
`host-probe/lower-memory-request-3.json` copied and verified the bundle in
41.551 seconds. FLUX produced the 1024 × 576 keyframe in 14.252 seconds of
supervised wall time (11.97 seconds reported by the CLI). The 576 × 320,
129-frame LTX stage was cancelled after 23.254 seconds when macOS memory
pressure reached level 2. The final source/derived-artifact audit took 35.361
seconds; the overall observed command took 135.221 seconds. Baseline and peak
swap were both 3,978,496,573 bytes, with zero observed growth. These are separate
stage, copy, audit and whole-command measurements, not model-load timings.

No MP4 was produced. Cancellation completed without the earlier cleanup error,
and all eight installed source files still matched their approved SHA-256 pins.
Qwen/Gemma conversion and tensor sidecars were confined to the job's private
model copies. The report is `host-probe/lower-memory-run-3/probe-report.json`;
the host trace is `host-probe/lower-memory-request-3-observation/` under the V11
evidence root. Request 2 failed preflight and did not execute a video model;
requests 1 and 3 are the two actual LTX attempts.

**Decision:** do not select this LTX-2.3 configuration for the current 48 GiB Mac
workload. Both bounded resolutions reached the unchanged pressure stop. This
is a measured negative result, not proof that every local model or workload
fails. The bundled video profile stays unverified/unselected; successful local
keyframes do not establish video or multi-reference feasibility. No further
LTX retry is automatic. Decoded duration/cadence, audio stream count, temporal
quality, cold/warm video timing and a four-minute rendering estimate remain
unavailable because no video completed. The keyframes have not received user
visual approval. `measured-decision.json` in the V11 evidence root pins the
actual report and observer record.

A smaller [Wan2.2 TI2V 5B](https://huggingface.co/Wan-AI/Wan2.2-TI2V-5B)
workflow is an untested local alternative requiring a separate explicit model
setup choice; the released Draw Things CLI's one-image input limitation still
needs an independently measured solution for multiple reference assets.
An optional hosted proposal is Runway `gen4.5` image-to-video using the local
keyframe. Its [published API pricing](https://docs.dev.runwayml.com/guides/pricing/)
on this check is $0.12 per generated second: a pilot of at most three five-second
clips has a $1.80 base generation estimate, and 240 generated seconds would be
$28.80 before retries, taxes, upscaling or other services. This is a proposal,
not a provider selection, quality guarantee or authorized spend. No asset upload
or paid job may run without explicit selection and a bounded budget. Independent
engineering can continue while the next generation workflow is chosen.

## Bounded native media-process supervision (V12a)

`VideoMediaProcess` is the lazy macOS-arm64 process boundary used by later video
media work. Calling it pins the actual executable bytes with lowercase SHA-256,
rejects symbolic executable files and raw `.`/`..` path components, and creates
a new owner-only working directory for that invocation. An existing directory is
never reused, so previous jobs and supplied input bytes are outside the adapter's
write surface. Arguments are passed as an argv array directly to `posix_spawn`;
there is no shell parsing.

The Darwin launch creates a new process group atomically with
`POSIX_SPAWN_SETPGROUP` and closes unlisted descriptors with
`POSIX_SPAWN_CLOEXEC_DEFAULT`. Nonblocking native reads on the supervisor thread
keep stdout and stderr flowing, with a bounded read budget per polling turn.
Each has a caller-selected limit of 64 bytes through 4 MiB; exceeding a
limit terminates the group while retaining bounded beginning/end diagnostics.
The supervisor observes direct-child exit with Darwin `waitid(WNOWAIT)` and
retains that waitable child until every signaling decision is finished. A bounded
`libproc` query lists only the owned process group, excluding the exited leader;
an early parent exit with a remaining descendant terminates that group and fails
honestly. There is no global process or descendant discovery. The final `waitpid`
reaps only the direct child, after permanently disabling further group signals,
so a recycled leader PID/group number cannot be targeted by later cleanup.
Cancellation is serialized with launch and records a stop request; the supervisor
alone signals and reaps. Runtime deadlines are bounded to 24 hours. Shutdown has
a separate fixed three-second budget and escalates from `SIGTERM` to `SIGKILL`
after 200 ms. Missing EOF or unconfirmed group termination cannot extend that
budget. Cleanup always closes its descriptors without background drain threads,
preserves the original cancellation/deadline/output failure, and attaches explicit
cleanup diagnostics when termination or reaping cannot be confirmed. Interrupted
supervision completes the same bounded cleanup and restores the interrupt flag.
Only the negative process-group ID returned by the owned spawn is signaled.
After the owned group has no remaining live work, that group number is never
signaled again. Every pipe descriptor has
one close owner, including failed launches; closed descriptor numbers are never
retried. Native regression hooks can withhold EOF/completion observations or
observe descriptor closure/lifecycle, but native launch, reads, signals and waits remain
real. These hooks make bounded failure paths reproducible without claiming to
reproduce an unkillable kernel process.

The native bridge is [JNA 5.17.0](https://github.com/java-native-access/jna/tree/5.17.0)
(`net.java.dev.jna:jna:5.17.0`), loaded only when this adapter is called. The
version is pinned from Maven Central (the resolved 2,002,589-byte jar has SHA-256
`b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0`); its tagged
[license](https://github.com/java-native-access/jna/blob/5.17.0/LICENSE) offers
Apache-2.0 or LGPL-2.1-or-later terms. The binding follows the
installed Apple SDK ABI where `posix_spawnattr_t` and
`posix_spawn_file_actions_t` are opaque pointers passed by address. This layer
supervises a trusted, selected local executable; it is not a sandbox for hostile
binaries and makes no codec, FFmpeg distribution, network, performance or visual
quality claim. V12 owns the pinned FFmpeg distribution and real decode, seek,
frame-access and silent-encode proof.

## Pinned ComfyUI setup and owned server boundary (V17a)

`LocalVideoSetup` now reads the bundled `comfyui-ltx23-v1` profile only when the
video workspace asks for setup. It verifies the separate installation under
`~/Library/Application Support/MelotrailVideo`: ComfyUI 0.35.0 at commit
`40c4fcdf513a4523e39d54a9d391908af8df8171`, ComfyUI-GGUF at commit
`6ea2651e7df66d7585f6ffee804b20e92fb38b8a`, Python 3.12.0, torch 2.14.0,
frontend 1.51.10, GGUF 0.19.0, the workflow provenance/model-path files and the
five exact LTX/Gemma/upscaler files. The profile also pins the complete ComfyUI
and enabled GGUF Python/native import-source sets (831 and 9 files, approximately
12 MB), including paths, sizes and content digests. Changed or added executable
sources are corrupt even when Git HEAD is unchanged. The source sets, Python
executable, package metadata and workflow configuration are checked again at
launch; large model hashes are checked only by explicit setup. File size and
SHA-256 mismatches are corrupt setup, not a request to substitute another file. The resolved regular
Python executable is pinned while `VIRTUAL_ENV`, the exact venv `PYTHONPATH` and
offline flags preserve the installed environment. A private bytecode-cache prefix
and disabled bytecode writes prevent existing source-tree caches from substituting
stale executable bytecode and keep installed source files read-only. Setup reports every missing or
corrupt component, current component terms, and three explicit choices: reuse
the verified installation, separately install/repair the disclosed pins, or
leave video unavailable. It performs no repair or download.

`ComfyVideoRuntime` starts the verified `main.py` directly through V12a's native
process-group supervisor; the retained Python monitor is evidence and is not a
second production supervisor. A fresh private session owns its input, output,
temporary and user directories. The server listens only on `127.0.0.1`, disables
API nodes and auto-launch, disables all installed custom nodes except the pinned
GGUF node, and refuses an occupied port rather than accepting its health response.
Readiness requires both a live owned child and its random per-launch response
marker on `/system_stats`. The bootstrap adds the marker inside that child's
HTTP application; the probe never sends it to the listener. A competing listener
that binds after the port precheck cannot satisfy readiness with generic stats.
Stop, startup failure,
readiness timeout, session timeout, three lost health checks, critical/unknown
memory pressure, 8 GiB additional swap and the 20-minute single-inference bound
all cancel and reap only that process group. Resource-sampler exceptions fail
closed, and health-probe exceptions count toward the three consecutive failures.
A shutdown wait expiring retains ownership and blocks every restart until a later
stop/close confirms completion. Explicit or suppressed native supervision failure
also retains ownership and reports uncertain cleanup; ordinary completed exit
failures have already been reaped. Inference admission and stop share one lock.
Warning pressure is observed but is not itself a stop. These are measured safety bounds from the M5 Pro / 48 GiB
trials, not a memory guarantee and not an OS-control change.

This boundary does not submit a workflow. The retained graph supports a short
LTX-2.3 image-to-video take from one already composed image and uses Gemma 3,
not T5. It does not establish multi-reference conditioning, automatic subject
layers/masks/anchors, guided complex actions, arbitrary-scene quality or a
complete 3–5 minute video. V17b owns API jobs. Automatic reference-conditioned
artwork preparation is now deferred; finished external artwork supplies the input. The Video tab and normal setup UI
remain planned in VG3, so this lazy adapter adds no MIDI startup dependency.

## Production ComfyUI short-shot probe (V17)

The opt-in `comfyVideoProbe` command binds one digest-pinned, already composed
image plus its free-form prompt to the bundled generic API graph. Named slots
bind node 4's image, node 20's prompt, nodes 21–24's width, height, frame count
and FPS, and node 16's MP4 output. No reference path or TABI prompt is embedded
in the graph. The graph retains the tested LTX-2.3 distilled Q4 model, Gemma 3
encoder, eight-step sigma schedule and 512/64 spatial plus 128/32 temporal tiled
decode settings. The existing external workflow/profile pins remain unchanged.

```sh
./gradlew :comfyVideoProbe \
  -PcomfyVideoRequest=/absolute/path/to/owned-request.json
```

The request file and its `inputs` directory must be owner-controlled regular
paths outside every supplied protected MIDI root. Its output names an absent
direct sibling. The command rejects collisions, symlinks, traversal, changed
input digests, unsafe dimensions, a non-five-second request and an invalid LTX
frame count before launch. It then uses `LocalVideoSetup`, `ComfyVideoRuntime`,
`LocalVideoBackend`, `VideoJobStore` and `VideoJobCoordinator`, reconstructs the
coordinator while the durable attempt is active, and publishes one immutable
result. `VideoMediaProbe` fully decodes that result and checks frame count,
dimensions, cadence and zero audio before writing `result.json`. The report also
records timings, sampled process-tree RSS, host-global pressure/swap, terminal
cancellation preservation, owned runtime stop and before/after setup/input pins.
After the successful result releases the one-inference lease, a second sequential
request with the same prompt, image, settings, seed and graph bytes is cancelled
immediately through the production adapter and reconciled by a reconstructed
coordinator. It must reach `CANCELLED` without an output and without changing the
earlier published result. The separately owned graph copy changes only that
request's durable path identity; it is not another workflow or tuning profile.

The selected external workflow's earlier 1024×576, 129-frame, 25 fps trial took
396.112 seconds with sampled peak process RSS of 22,920,757,248 bytes and no
additional host swap; the 768 comparison took 214.294 seconds. Those retained
measurements selected this bounded configuration but do not prove the production
adapter command. Its generated report owns the fresh measurement after the host
run. Neither result proves automatic multi-reference composition, subject layers,
masks or anchors, complex-action control, arbitrary-scene quality, or a complete
180–300 second video. Ready-asset motion work and later visual/full-video gates
own the current delivery; automatic artwork preparation V18b is deferred.

## Deferred reference-image host experiment (V18b1)

As of 2026-09-16 picture generation is outside the delivery scope. This section
retains the failed experiment and its provenance; it is not an active prerequisite
or permission for further inference. Uploaded finished artwork replaces this stage.


The opt-in `videoReferenceImageProbe` pins the separately installed FLUX.2 Klein
4B distilled model, 8,044,982,048-byte Qwen 3 4B encoder and 336,211,292-byte
FLUX.2 VAE by exact SHA-256. The original 4,070,624,520-byte approved FP8 Klein
file stays immutable beside a 7,751,105,920-byte local FP16 dequantized derivative
and its pinned conversion receipt. Both model files and their derivation are
verified before and after the host probe. It also pins the V17 runtime profile,
complete ComfyUI executable source set and the three core node files used by the
graph. Before runtime start it verifies every model, source, graph, request and
input pin. Ordinary tests parse and exercise only the graph, profile, path and
ledger contracts; they do not load these models.

The v2 host profile retains 512×512 output, 0.25-megapixel aspect-preserving
reference encoding, four Euler steps, CFG 1, batch size one and one owned inference
at a time. A subject pass accepts at most two identity/pose/expression images and
one outfit image. A scene pass accepts the retained subject result, one scenery
image and one optional style image. A single complete-scene image uses one direct
pass. Missing groups omit their graph nodes; overflow and conflicting roles fail
before upload. No collage, filename, crop, pixel mask or manual region supplies
conditioning.

With the base subject shared by the scenery-only case, the six-case comparison
requires ten unique submissions: four subjects, five scenes and one complete
scene. Subject reuse binds ordered image bytes/roles, exact prompt, guidance,
seed, settings, graph and profile pins, independently of case labels or source
paths. Admission accounts for both same-run reuse and verified prior outputs.
The user authorized a fresh v2 trial of at most **24 additional attempts** with
updated assets from commit `f96a7f36430d45c576de511be94480e31762570b`; its new trial
ID, directory and ledger are separate from the exhausted twelve-attempt trial.
Each reservation precedes upload and counts failures or interruption across
invocations. The old ledger cannot be expanded, reset or migrated. Verified
completed stages resume by fingerprint and output digest; changed or missing
output bytes fail instead of being regenerated silently. Each invocation uses a
fresh output directory, quarantines every subject intermediate and final PNG without
overwrite, preserves the exact original prompt beside separate stage guidance,
and records all transitive image pins. Cancellation and the 1,200-second stage
limit use the V17 owned runtime and stop only its session.

The first native subject attempt failed with `Undefined type Float8_e4m3fn`,
produced no image and consumed one of the twelve attempts. Its owned runtime
stopped with normal memory pressure and no additional swap. A tiny synthetic
weight reproduced the failure in the installed MPS FP8 dequantization operation;
CPU dequantization passed. The separately executed CPU conversion materialized
`FP16(weight) × FP16(weight_scale)` for 80 FP8 tensors and copied 69 BF16 tensors
byte-exactly. Independent numerical checks and tensor readback passed; conversion
took 16.47 seconds, peaked at 349,388,800 bytes process RSS and added no swap.
The ordinary loader now selects these dequantized weights using the existing
`--fp16-unet` runtime. This remains the same Apache-2.0 source model, without a
new download or runtime source change. It does not restore precision or establish
whole-model forward equivalence after removing mixed-quantization wrappers and
their input scales. Stage fingerprints include the changed graph, original and
derived model pins, and conversion provenance.

The completed historical v1 host trial on 2026-09-16 produced all six 512×512
comparison outputs. Its twelve-attempt allowance is exhausted: one failed FP8 attempt and eleven
successful FP16 stages. The final batch reused two verified base stages, peaked
at 17,565,138,944 bytes of owned-process RSS and added 262,144 bytes of host swap;
pressure samples included NORMAL and WARNING. The earlier base run added about
470 MB of swap. All nine selected originals remained byte-exact and the owned
runtime stopped. Focused checks, 794 tests, build and whitespace checks passed.

**Execution passed; reference-conditioning acceptance failed.** The selected cat
identity still produced a Tabi-like character; the green outfit did not transfer;
changing scenery retained the train-window composition; the closed-eye reference
left the eyes open; and the selected warm banner style was lost. Outfit/pose
changes also altered unrelated scene details. The comparison planner additionally
regenerated an unchanged subject for the scenery-only case instead of reusing its
exact bytes. Independent review therefore keeps V18b1 incomplete. Capacity of
three bound image slots per pass is measured execution capacity, not reliable
control of three independent reference roles. No human artistic approval is
inferred. The as-tested graph and v1 profile remain pinned in the retained trial
evidence at
`~/.codex/melotrail-video-sequential/evidence/V18b1/reference-proof-20260916`.

The v2 retry used byte-exact assets from user commit `f96a7f364` and the explicitly
selected charcoal/stone v6 banner. It completed all six 512×512 comparisons in
ten unique submissions, reusing the exact base subject for changed scenery.
Graph, guidance, model and source pins remained unchanged. The comparison batch
peaked at 17,074,372,608 bytes owned-process RSS and added 402,980,864 bytes host
swap, with NORMAL/WARNING pressure samples. The owned runtime stopped and all
selected originals remained byte-exact. Focused checks, 795 tests, build and
whitespace checks passed; these do not establish reference fidelity.

**Updated-asset role fidelity still fails:** the cat request remains Tabi-like,
Japan denim/orange workwear and coffee/sleeping poses do not transfer reliably,
Singapore scenery retains Tokyo, and the complete v6 reference loses its indoor
setting. Five single-reference diagnostics with a simpler prompt preserve the
cat, outfit, coffee pose and Tokyo scene much better; the character sheet still
adds unwanted marks. Both prompt and reference count changed in those diagnostics,
so they suggest an interaction rather than isolate its cause. A final two-stage
retry changed only the base prompt to shorter warm-light wording: lighting and
texture improved, but clothing still mixed and the coffee pose was absent.

The new ledger consumed **17 of 24** authorized additional attempts, all successful
native stages; seven remain unused. The original twelve-entry ledger is unchanged.
The as-tested v2 profile retains its pre-trial limitations; these measured results
and complete output/role receipts are retained at
`~/.codex/melotrail-video-sequential/evidence/V18b1/updated-assets-f96a7f3`.
V18b1 remains incomplete and is now deferred. Single-reference fidelity is not independent role control,
and no animation or human artistic approval is inferred.

This profile emits complete still compositions only. It
does not emit semantic layers, masks, landmarks, pose data, effect anchors,
motion, video, UI behavior or artistic approval.

## Selected video-only media runtime (V12)

V12 selects FFmpeg 9.0.1 for macOS arm64 as an explicitly and separately
installed local tool set. It is not bundled with the application or loaded by
the MIDI workflow. The selected source is official FFmpeg commit
`bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa`, reached through its GitHub
repository commit archive after the release host did not return bytes. That
archive's SHA-256 is
`fb1931fd4eb29297ee1c1017a24f800c4d8fbea35b4f2aaeb28308a48a9149b4`.
The annotated `n9.0.1` tag resolves to this commit. This is a minimal static
LGPL-2.1-or-later configuration: GPL, nonfree components, network access and
autodetection are disabled. It admits only the required local file/pipe
protocols, MOV/image2 inputs, MP4/image2/null outputs, native
H.264/PNG/MJPEG/raw-video decoding, PNG/raw-video and `h264_videotoolbox`
encoding, selected video filters, zlib and Apple system frameworks. The tools
remain outside the repository and application package. V27, rather than this
320×180 technical proof, owns the final export dimensions and delivery preset.

The recorded configure arguments are:

```text
--prefix=/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/ffmpeg/9.0.1-melotrail-1
--disable-everything --disable-autodetect --disable-network --disable-gpl
--disable-nonfree --disable-version3 --disable-doc --disable-debug
--disable-shared --enable-static --disable-avdevice --disable-ffplay
--enable-ffmpeg --enable-ffprobe --enable-protocol=file,pipe
--enable-demuxer=mov,image2 --enable-muxer=mp4,image2,null
--enable-decoder=h264,png,mjpeg,rawvideo
--enable-encoder=h264_videotoolbox,png,rawvideo --enable-parser=h264,png
--enable-filter=scale,format,fps,trim,setpts,select,testsrc2,color,null
--enable-videotoolbox --enable-zlib
```

The installed `bin` directory contains regular, non-symbolic `ffmpeg` and
`ffprobe` executables plus `melotrail-video-tools.json`. That schema-v1 manifest
records the distribution ID `ffmpeg-9.0.1-macos-arm64-melotrail-1`, the
separate-install strategy, source revision/URL/archive digest, exact executable
SHA-256 values, exact configure arguments and installed license/notices.
The installed `ffmpeg` is 4,636,440 bytes with SHA-256
`3eec1c025127efed8f81259f833081920e57f809c9558ef1528d03d1e600b9e9`;
`ffprobe` is 4,443,016 bytes with SHA-256
`666c4ecdff7d14153d53e35cd83f0b0f37bffb7250080e994b90b59c575cd264`.
`otool -L` reports only Apple system libraries/frameworks: libSystem, zlib,
VideoToolbox, CoreFoundation, CoreMedia, CoreVideo and CoreServices. Installed
copies of `COPYING.LGPLv2.1` and `LICENSE.md` accompany the separate tool set;
future redistribution requires matching source and notices.
`VideoMediaProbe` checks the selected source and required disabled/enabled
capabilities, then delegates every launch to the V12a supervisor with the
recorded executable hash. A mismatched or missing binary, manifest, version or
configure argument fails before media work.

The probe accepts only a regular, non-symbolic local input `Path`; FFmpeg and
ffprobe also receive a `file,pipe` protocol whitelist. It creates one new
owner-only evidence directory and separate private working directory per
process. Existing output directories are rejected and supplied input bytes are
checked again after success. The bounded sequence records tool identity, reads
metadata while counting decoded frames, fully decodes the owned fixture, saves
the first and one-second seek frames as PNG, strips every non-video stream while
encoding H.264 through VideoToolbox, then independently counts and fully decodes
the silent MP4. The JSON report retains executable/source pins, build options,
notices, metadata, artifact hashes and elapsed time for each successful owned
process. Cancellation, crash, timeout and bounded diagnostics retain V12a's
typed failures; a native or filesystem `ENOSPC` diagnostic is surfaced as disk
exhaustion without replacing the input or an existing output.

The real host command is:

```sh
./gradlew :videoMediaProbe \
  -PvideoToolsDirectory="/absolute/path/to/tools"
```

It uses a collision-free directory under `build/video/media-probe/`. To retain
evidence at another fresh location, add
`-PvideoMediaProbeOutput=/absolute/path/to/new-output`. The checked-in owned
fixture is an authored three-second, 320×180, 24 fps silent H.264 MP4 with 72
moving frames. Its 15,447 bytes have SHA-256
`bc474eec8f0de89ed765aac902d0681b016ab9f59e8219e63c1404d42e1b3d35`.
Success requires exact metadata and frame counts for both the
fixture and re-encode, full decode of both files, decodable first/seek PNGs with
the authored block at different horizontal positions, and no audio stream. The
fixture is technical evidence only; it contains no user or character asset and
awards no visual acceptance.

Measured host result on 2026-09-14: the real `videoMediaProbe` passed on this
macOS arm64 host using the pinned tools above and an output path containing
spaces, accented Latin and Japanese characters. All nine supervised operations
returned zero. Both input and encoded output decoded 72 frames at 24 fps,
320×180, exactly three seconds, with zero audio streams. The first and one-second
seek PNGs decoded successfully; their pixels showed the authored orange block
moving horizontally. The input SHA-256 stayed unchanged. VideoToolbox encoding
took 143 ms in this bounded fixture run, which is not a full-cut performance
estimate. The report, extracted frames, encoded silent MP4 and coordinator frame
inspection are retained in
`~/.codex/melotrail-video-sequential/evidence/V12/validation-1/`.
Focused lifecycle/error tests, `make test`, `make build` and `git diff --check`
passed on that unchanged candidate. Generated-video fidelity, installed-app
integration and final 1080p/3–5 minute delivery remain later tasks.

## VG02 preview continuation boundary

Controlled preview production wiring now has owned fixture coverage for sequential
150/600/900-frame jobs at 30 fps, measured immutable publication without selection,
import cancellation and reconstructed exact recovery without native relaunch.
The opt-in host check also retains source/published decoded frame and timestamp
evidence, including chunk boundaries and authored blink phases. These synthetic
regressions do not establish a real native preview run or artistic acceptance.
The native ladder remains gated on explicit paths and bounded authorization;
see [Validation](VALIDATION.md#vg02-controlled-preview-proof) and current TASKS.
Flat-image I2V remains the measured 129-frame/25-fps (5.16-second) short route.
Neither route establishes long generative coherence, a Video UI or full delivery.

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

The next two subsections preserve the superseded companion's job/provider
evidence. V16/V25 must re-establish the applicable behavior in the new Kotlin
video boundary; the records below are not current implementation claims.

### Historical resumable companion ledger (superseded V03a)

The companion had a provider-neutral schema-v1 job ledger stored separately
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

### Historical Runway companion adapter (superseded V03b)

The companion used the reviewed Runway Dev REST request shape only: HTTPS
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

## Historical soundtrack-to-scene workflow (superseded)

The following V04/V07 workflow describes preserved Swift evidence only. The new
Video tab has no soundtrack or MIDI input and does not port these steps.

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

### Historical deterministic soundtrack timing (superseded V04a)

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

### Historical optional Export handoff (superseded V07a)

The following describes removed behavior, not current installation instructions.
The MIDI Export page used to probe a separately installed `melotrail-tabi-editor`.
Its bounded `--capabilities` response must be exactly
`melotrail-tabi-export-handoff-v1-manifest-v2`. Missing, incompatible or
unresponsive installations leave ordinary MIDI export available without a video
action. A compatible installation adds **Open in TABI…** to a saved snapshot;
earlier accepted work requires a new current export before launch.

The removed historical installer built only the Swift release editor and copies its executable into a new
`~/Applications/Melotrail TABI/` directory. It refuses an existing destination.
For another new installation directory, pass its absolute path to the script
and set `MELOTRAIL_TABI_EXECUTABLE` to that directory's `melotrail-tabi-editor`
when starting the MIDI app. Reopen Export after installation. This is an unsigned
local executable installation, not a signed/notarized app bundle. No companion
target or media dependency enters the Kotlin build/package.

Launch reopens and verifies the MIDI project and every referenced artifact,
checks the selected snapshot against disk/current accepted work, then passes
only `--midi-export <absolute manifest path> <SHA-256> <snapshot ID>` as literal
process arguments. The working directory is temporary storage. No handoff file,
soundtrack reference, project revision or snapshot rewrite is stored in the
MIDI project. The companion checks both manifest digest and snapshot identity
at intake and again when confirming/saving/opening; it never reads project state.
Currentness is checked at dispatch; subsequent MIDI edits do not alter the
immutable snapshot already selected in the companion.

The native intake requires a separately selected finished soundtrack and an
explicit **Confirm timing** action. Lead-in/tail accept exact non-negative seconds
(up to six decimal places) or rational fractions such as `441/44100`.
**Use remaining duration as tail** proposes the exact remainder after the entered
lead-in and MIDI timeline; it still requires explicit timing confirmation.
The existing timing planner rejects duration
mismatches rather than shifting, trimming or stretching music. Soundtrack bytes
are pinned at selection and rechecked before each dependent action. **Save timing
request…** creates a new JSON request in companion storage for the existing
scene-preparation workflow. Use that request as the `timing` object of a prepared
`SceneCompositionRequest` with approved asset pins. **Open prepared composition…**
checks the exact timing inputs and opens the existing editor; another soundtrack
or export cannot silently replace the handoff. This intake does not generate or
approve assets, start paid jobs, encode automatically or publish video.

Timing requests and editor sessions reject MIDI-project descendants and snapshot
directories, including symlink aliases. Timing publication refuses existing files;
session saves replace only their own companion document. All selected inputs
remain protected. Companion intake and installed-launch captures are
technical fixtures; the full-song production pilot and human decisions stay V07.

### Historical deterministic scene composition (superseded V04)

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
last decodable frames and every join. The published file is 1920×1080 H.264 with
square pixels, constant 30 fps, the chosen 180–300 second duration and zero audio
streams. Render controlled motion at that cadence; disclose conversions for native
action takes rather than calling duplicated/upscaled frames native 1080p motion. Strip any generated audio
during normalization; do not add audio editing. Record native versus upscaled
resolution. Byte identity is not promised after codec conversion; input identity,
selected trim ranges and exact frame duration are preserved and tested.

Reference 08 guides the planned in-app preview, take strip, inspector and
restrained export action. Keep those regions reachable at 1536×1024, 1280×900
and 720×900. Preview uses moving decoded frames, suppresses source audio, owns
one video session and does not create or substitute a MIDI player. Entering Video
pauses MIDI while retaining its position. These controls remain planned until
their VG3/VG5 tasks pass.

## YouTube and commercial intent

AI-assisted original work can be eligible for monetization, but using owned
assets or AI does not guarantee eligibility. YouTube evaluates originality and
channel-level repetitive/inauthentic content; superficially varying the same
mass-produced episode template is a poor product strategy. A distinctive TABI
journey and original music support the creative goal, not a guaranteed outcome.
[YouTube monetization policy](https://support.google.com/youtube/answer/1311392?hl=en).

Check disclosure against the actual finished publication. YouTube distinguishes
non-realistic animation from realistic synthetic content and explicitly lists
AI-generated music among disclosure examples. Disclosure itself does not remove
monetization eligibility. Do not automatically classify all animated videos or
all algorithmically arranged MIDI the same way; record the user's actual use.
[YouTube AI disclosure guidance](https://support.google.com/youtube/answer/14328491?hl=en).

These sources were checked on 2026-09-06; review again before publication.
Provider commercial-use terms, rights to source/reference assets and any added music,
YouTube Partner Program eligibility and editorial quality are separate decisions.
Keep provenance and credits proportional; do not recreate a policy-scoring engine
or promise copyright exclusivity/revenue. Melotrail does not upload to YouTube;
publication stays under user control.

## Pilot acceptance

A real user first reviews three real 20–30-second clips from externally finished
artwork at VG6-02: a base motion request, a materially different motion prompt with the same
artwork and a replaced finished scene/layer. No generated-look gate is required. Review reference fidelity, prompt adherence, requested
motion/style, object coherence and temporal stability. The chosen content has no
mandatory TABI, train, prop, location or camera behavior.

For VG6-05/06, start with externally finished artwork, optional ready layers and a motion prompt in the app,
then generate, assemble, export and watch one complete 3–5 minute silent video.
Review every join, unique/reused footage, prompt adherence and reference fidelity,
then import/play it in the user's chosen Apple editor. Record actual local
resources or authorized spend, retries, tool/model versions, hashes and feedback.
Tests cannot award either visual decision, and soundtrack sync is outside scope.


## Preserved superseded Swift companion evidence

Everything below records the retired V01–V07 implementation and its measurements.
It does not define the replacement product, satisfy V24/V33, or establish that
the planned Video tab, local profile or controls exist.

### Verified companion spike — 2026-09-08

The removed `companion/` package was an independent Swift package with no third-party or MIDI
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

### V01 decision record — 2026-09-09

This is a conservative dependency and pilot decision, not an authorization to
spend money, send media to a provider, or distribute a video. The companion's repository package has been removed; retained external evidence
is historical and not part of the current build.
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
