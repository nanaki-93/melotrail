# TABI video

Owner: planned video product, asset and media contract. Delivery is V10–V33 in
[TASKS](../TASKS.md), under [PLAN §9](../PLAN.md#9-video-generation-from-assets-and-a-prompt).
The integrated Video tab, model profile, media runtime and generation controls
are not implemented yet. This document specifies their target behavior and
preserves clearly marked artistic references and superseded Swift evidence.

## Outcome and scope

Add an independent **Video** tab to the Kotlin/Compose Melotrail application.
One or more generic reference assets plus a user-written free-form prompt drive
generation of short clips, review and assembly into a complete **180–300 second
silent video** (default 240 seconds). The result is a 1920×1080 H.264 MP4 with
zero audio streams for later audio work in the user's external Apple editor.

Video needs no MIDI project, export, song or soundtrack. It never renders MIDI,
edits audio, writes a MIDI project or adds a seventh MIDI destination. Its project,
assets, jobs, models, media tools and outputs use separate storage and runtime.
The app loads those services lazily so missing video setup cannot block MIDI
startup, audition or export. The old Swift companion and soundtrack-led handoff
are superseded and remain only as historical code/evidence until V30–V31.

The planned normal workflow is:

1. Open Video and create or open an independent video project.
2. Import one or more PNG/JPEG references and assign optional subject, environment,
   style or complete-scene roles. Preserve source bytes and show what each request
   consumes; no particular role combination is mandatory.
3. Enter a free-form prompt and choose 3–5 minutes. Optional action, motion, style,
   look and per-shot controls refine the request without becoming prerequisites.
4. Choose the explicit local setup and click **Generate video**. The app prepares
   needed looks and shots, records honest work estimates and retains results.
5. Play the moving draft in the tab; keep, reject or regenerate clips, inspect
   joins and unique/reused footage, and review the complete silent cut.
6. Export to a new silent MP4 and reveal it in Finder.

Tokyo, trains, coffee and TABI are examples only. They must never become a required
preset, default prompt injection, action list or acceptance gate. Any supported
references and prompt use the same workflow. Try one measured local backend first;
a hosted fallback is optional and requires explicit selection, upload disclosure
and a bounded authorized budget. No model download, paid job or upload occurs from
opening the app.

## What the existing references establish

[Character identity](pictures/tabi.png) is the cosmic axolotl traveler sheet;
it still contains “Moki”. When a request selects these references, new output uses
**TABI**. The character has an
indigo/violet body, pink-purple feathery gills, dark glossy eyes, cheek/star
markings, headphones, travel jacket/scarf, satchel and travel accessories.

The [morning train scene](pictures/video/Morning%20Lo-Fi%20Train%20Ride%20with%20Tabi.png)
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
preset or normal-workflow prompt. Some earlier reference and trial files were not included in the supplied Git
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

## Generative animation strategy

V11 measures one local reference-conditioned image/video workflow on the user's
Mac, then V17 connects that selected backend. It must consume the selected
references and free-form prompt through an automatable interface and produce
moving 5–10 second shots. A particular character, scene, action, camera move or
style is never required. Missing local setup is explicit; the app does not
download a model automatically or silently send references to a hosted service.

Generate short shots and assemble them to the chosen 180–300 second duration.
Unique footage is the default. An explicit reuse mode may repeat only takes the
user marks repeatable, with generated and repeated seconds shown separately.
Never reverse requested action or motion to manufacture a loop. Look review and
shot-level overrides are optional; the primary Generate video action may create
unreviewed draft intermediates while recording their status honestly.

Quality review checks real playback against the actual references and prompt:
recognizable requested subjects/traits, scene/action/motion/style compliance,
coherent objects, temporal stability, joins and any requested continuity.
Matching first/last images is insufficient if the intervening animation drifts.

Record requested and actual clip duration; reuse/trim visual clips only within
explicit scene policy. Rejected takes remain excluded from production. Provider
seeds may help traceability but do not promise identical video on rerun.
Determinism applies to composition from pinned assets, not cloud generation.

## Local profile and bounded probe preparation (V11a)

The bundled `video/local-profile.json` is a schema-v1 candidate profile for the
`draw-things-cli` backend identity and the LTX-2.3 distilled image-to-video
candidate. Both are explicitly `CANDIDATE_UNVERIFIED`: the resource contains no
tool or model hashes, capability flags, measurements or selection claim. A later
V11 host trial must pin the installed released tool and every model dependency,
run real reference-conditioned inference, measure it and record the selection.

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

The MIDI Export page probes a separately installed `melotrail-tabi-editor`.
Its bounded `--capabilities` response must be exactly
`melotrail-tabi-export-handoff-v1-manifest-v2`. Missing, incompatible or
unresponsive installations leave ordinary MIDI export available without a video
action. A compatible installation adds **Open in TABI…** to a saved snapshot;
earlier accepted work requires a new current export before launch.

Install the companion independently on macOS 14+:

```bash
sh companion/scripts/install.sh
```

This builds only the Swift release editor and copies its executable into a new
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
square pixels, a constant cadence selected from measured V11/V12 evidence, the
chosen 180–300 second duration and zero audio streams. Strip any generated audio
during normalization; do not add audio editing. Record native versus upscaled
resolution. Byte identity is not promised after codec conversion; input identity,
selected trim ranges and exact frame duration are preserved and tested.

Reference 08 guides the planned in-app preview, take strip, inspector and
restrained export action. Keep those regions reachable at 1536×1024, 1280×900
and 720×900. Preview uses moving decoded frames, suppresses source audio, owns
one video session and does not create or substitute a MIDI player. Entering Video
pauses MIDI while retaining its position. These controls remain planned until
their V20–V28 rows pass.

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

A real user first reviews one generated look and three real clips at V24: a base
assets/prompt request, a materially different prompt with the same assets and a
changed-reference request. Review reference fidelity, prompt adherence, requested
motion/style, object coherence and temporal stability. The chosen content has no
mandatory TABI, train, prop, location or camera behavior.

At V33, start only with user-chosen references and a free-form prompt in the app,
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

### V01 decision record — 2026-09-09

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
