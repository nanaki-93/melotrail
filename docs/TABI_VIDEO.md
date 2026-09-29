# TABI video

Owner: video product, asset and media contract. Current delivery is organized as
**VG1–VG6** in [PLAN](../PLAN.md#6-video-generation-features) and
[TASKS](../TASKS.md). Backend/runtime foundations exist; the integrated Video tab
and complete generation flow remain planned.

This document preserves supplied artwork, scoped artistic decisions and runtime
measurements. Older task IDs, commands and dated status statements below identify
their original evidence; they are not active queue dependencies or authorization
to resume an experiment. TASKS alone owns current work. Production-pilot reviews
now precede app development: **VG2-07** character, **VG5-05** combined 60 seconds,
and **VG5-07** full pilot/editor handoff. Later **app** gates remain VG6-02 and
VG6-06; none of these new/later gates is passed. VG3 design permission/approval is
still separate. Earlier “next VG4-01” or app-first statements are historical.

## Production-first workflow (2026-09-28)

The production order remains **character tests → moving scenery join → combined
60-second proof → full pilot → review/editor handoff → reusable app
functionality**. The original pilot default was 240 seconds; the current requested
pilot target is **180 seconds / 5,400 frames**. This section defines the production
recipe; PLAN owns the roadmap and TASKS owns executable rows, dependencies and
status. Updating these documents
does not authorize a live run, new artwork, downloads, spending or limit changes.

### Starting point and next creative proof

**Source pool for the TABI video tests (current request):** read existing
`docs/pictures/video/tabi-assets/scenario/` and sibling TABI asset folders,
without altering originals. For the isolated character test, use
`scenario/tabi-quiet-ride-through-tokyo.png` (SHA-256
`01e4db852ceed7bc4d17708c3d181103d0699e5caed54bbc44eda1d83932473e`)
and the accepted corrected leaf/window bundle as the original-appearance
reference. The three 5600×1080 `scenario/tokyo-parallax-{far,middle,near}`
PNGs have the same bytes as the pinned prepared planes below; they are usable
**scenery references**, not proof of extended coverage. Consult
`train-actions/` for TABI pose/style cues; a flat action illustration is not
an aligned transparent pose or clean backing. The drinking image has a handled
mug unlike the lidded scene cup. The subsequent artifact-specific user decision below approves the appearance
of `train-actions/28`–`30` as a **replacement baseline**. Using this source
directory alone did not authorize a new render. The isolated fixed-scene kit's
technical sweep is recorded below; moving-footage review remains pending.

Order of tests: validate the selected replacement subject/plate/occlusion
kit and its full translated/filter/shutter-sampled support and contact; import
and reopen it in a private current-schema project; then seek a separate bounded
admission for one 5–10-second breathing clip using the proven seed/control/range.
After artifact-specific review, test a moving scenery join using existing planes
and the **static-look-only** v5 near section in
`scenario/tokyo-clockfront-near-v5/` if its moving overlap and far authored
coverage pass. Combine only reviewed components later. Keep the original scene,
accepted corrected layers, planes, four MP4s and earlier candidates unchanged;
new output must use fresh ignored destinations. This is a source-selection
instruction for preparation/tests, not approval of an appearance, moving seam,
new take, 60-second corridor or live-work budget.

Retain the positively reviewed 30-second blink/parallax MP4 without regenerating
or overwriting it. Its full decoded review is complete. The corrected leaf/window
source bundle is accepted for future work, but no moving video uses it yet.
The approved blink is the existing open/closed crossfade, not articulated three-pose
lids. Dependency scoping and proposal persistence are implemented; they neither
add character motion nor prove interrupted-render recovery. No full video or
integrated Video UI exists.

Next is a **new 5–10-second isolated TABI movement beyond blinking**, with the rest
of the scene fixed. Keep a deliberately small kit: retain the blink, prove one
compatible gentle movement/glance and its clean return before expanding to more
idle/action variants. Do not make drinking/eating or a 24-pose library prerequisites.
A pan is not character action. If the needed artwork/control is missing, expose
that gap and scope its preparation or narrow implementation, not a fake result.

The scene has a hand-on-cheek pose and a lidded paper cup; the supplied drinking
reference has a different pose and handled mug. Action references are not registered
frames that can simply be pasted/crossfaded into the scene. Match the actual
perspective, scale, clothes, props and entry/exit pose. Head/body/gill motion may
reveal previously hidden cabin/window pixels and needs compatible clean plates,
subject/pose layers, moving eye masks and foreground occlusion. The accepted
fixed-head leaf mask is not automatically a moving-head matte. Keep originals and
accepted preparations unchanged; review new derivatives and new exposed edges.

Use the selected local ComfyUI route for separately authorized external artwork
preparation or bounded isolated action experiments. Retain preferred RealESRGAN
finishing once per selected static asset, not a new per-frame repainting pass.
Do not make whole-scene I2V the long-video engine: earlier native footage already
showed eye/architecture deformation. No hosted fallback, model/node installation
or new in-app image-generation feature is implied.

### VG2-06 protected movement candidate (2026-09-28)

Read-only Step 1.1 inspection selected a **gentle seated inhale/exhale**, using
bounded whole-subject breathing at no more than 2 px vertical displacement in the
1920×1080 prepared viewport. TABI's hand remains against the cheek, moving with
head/body as one cutout; the paper cup remains on the table. This is a candidate,
**not** a prepared motion, reviewed artwork, rendered clip or authorized live job.
The moving support is the union of neutral and displaced subject alpha including
resampling/shutter fringe; cabin, seat, window, scenery, tabletop, cup, notebook and
pen must remain fixed outside it. Declare pixel tolerance for entry/end alignment
before testing; first and last frames must return to the neutral fixed composition
while intermediate frames show non-blink subject displacement. Verify contact at
cheek/hand, feet/seat and table occlusion throughout. Reject a whole-scene pan and
`train-actions/01-drinking-coffee.png` (different pose and handled mug).

Missing: the accepted corrected bundle has no scene-registered **transparent
whole-subject cutout** or opaque **clean cabin/seat/window plate** behind every
exposed pixel in the swept support. A corresponding fixed table/seat/window
foreground/occlusion matte is needed where the moving cutout passes behind them;
existing window/eye masks are not subject mattes. Step 1.2 must derive and measure
these on a *new* bundle; the existing importer checks opaque coverage and alpha,
not artistic correctness or clean erased-subject pixels. Current compositor
breathing translates the entire subject including the hand and seated lower body;
if the foot or tabletop edges slip, a narrower control/kit is a prerequisite, not
permission to hide changed pixels. Its seeded periodic phase does not guarantee a
neutral first or last frame; verify the exact seed and range in Step 2.1, or scope
a timing-control prerequisite. Do not submit a native render on these inputs.

SHA-256 input/protection pins (paths relative to repository root; the accepted
bundle's PNGs and preparation receipt are individually pinned, not a directory
name or mutable metadata such as `.DS_Store`):

| Path | SHA-256 |
| --- | --- |
| `docs/pictures/video/tabi-assets/scenario/tabi-quiet-ride-through-tokyo.png` | `01e4db852ceed7bc4d17708c3d181103d0699e5caed54bbc44eda1d83932473e` |
| `build/tabi-leaf-mask.ey9rmD/prepared/preparation.json` | `d63fa5d92d9d2ff8276287373a7f911b91d09265c480e15655def9eabd37b99f` |
| `build/tabi-leaf-mask.ey9rmD/prepared/cabin-foreground.png` | `e85e3d7fa1a4bddff409affbf3fec1c2a926f893adc19d0e0dba04c06713c7e4` |
| `build/tabi-leaf-mask.ey9rmD/prepared/cabin-occlusion.png` | `94e4e61205ee9795d69acdfd8880cd1c517c94f17345b23b7e4c82949739d954` |
| `build/tabi-leaf-mask.ey9rmD/prepared/eye-support.png` | `959235c93249ef29493514c8f2fa858a2c0ec1a7db6ef0848533add67df7f6d8` |
| `build/tabi-leaf-mask.ey9rmD/prepared/eyes-closed.png` | `988bb0ead1c49a989111594c23653671adef89e6bc0d797e53ca0527f58b5df1` |
| `build/tabi-leaf-mask.ey9rmD/prepared/eyes-open.png` | `e61431efbd3d410678b921f759ef4dca197f10df0bea8ecc17325e41e5633aed` |
| `build/tabi-leaf-mask.ey9rmD/prepared/far-plane.png` | `b91e588f20d8b0f1a611c1d59700682408cad03222c59e1221c549b2d392491f` |
| `build/tabi-leaf-mask.ey9rmD/prepared/finished-scene.png` | `23d3ff7ef3a1f9a19fc5ba6ee3557bfd0e0fd8d389837b40078ef9fc662c041a` |
| `build/tabi-leaf-mask.ey9rmD/prepared/fixed-scene-1080p.png` | `82077aa0f594baca3190a724a3f99828f22ded059d5b4e4bcb2df652f6205d16` |
| `build/tabi-leaf-mask.ey9rmD/prepared/middle-plane.png` | `37a388aeaf0ac638120fcf68c923c7e77e5acccccd285373d0a460c1562e6282` |
| `build/tabi-leaf-mask.ey9rmD/prepared/near-plane.png` | `f26a6c3de329e80338d60779ce58a436cd9363600d66f0d20db4269a5d6c8cee` |
| `build/tabi-leaf-mask.ey9rmD/prepared/window-mask-1080p.png` | `a85f21cb5687b9f1e9f1b4b90102e89de97bf683076de500108af3a2b03e971f` |
| `build/tabi-blink-retry.gc18u7/prepared/eyes-open.png` | `e61431efbd3d410678b921f759ef4dca197f10df0bea8ecc17325e41e5633aed` |
| `build/tabi-blink-retry.gc18u7/prepared/eyes-closed.png` | `988bb0ead1c49a989111594c23653671adef89e6bc0d797e53ca0527f58b5df1` |
| `build/tabi-blink-retry.gc18u7/prepared/eye-support.png` | `959235c93249ef29493514c8f2fa858a2c0ec1a7db6ef0848533add67df7f6d8` |
| `docs/pictures/video/tests/tabi-blink-8s-1080p.mp4` | `e5d3376c9abf01ee820ccf93176a7e28b6db586acdacca8ab02f6cc34b5582c2` |
| `docs/pictures/video/tests/tabi-tokyo-blink-parallax-20s-1080p.mp4` | `7136e45800aebc577e4529a36cc3007b05117199db611e6a3a76a7edb686970f` |
| `docs/pictures/video/tests/tabi-tokyo-continuity-30s-1080p.mp4` | `abb990dab8542e30113fc0fe9ee89de5275e5c7c79167916f2f5ae4d755c81ea` |
| `docs/pictures/video/tests/tokyo-parallax-20s-1080p.mp4` | `30364b7e3a50b88ce217504f5f7e7805c7fa07f1cd16e113f7f12be0e30d20f7` |

No files in these input/protection paths were edited, no existing take or selection
was changed, and no new generation/encode/decode was attempted in this inspection.

### VG2-06 preparation preflight (2026-09-28)

The ignored read-only check `node build/vg2-character-input-a1/check-inputs.cjs`
records decoded alpha and interior-pixel witnesses at
`build/vg2-character-input-a1/input-probe.json`, checking the Step 1.1 source,
corrected bundle and four review-video pins. In the 1920×1080 top-left-origin
viewport, the accepted fixed-scene and finished-scene each have 2,073,600 opaque
pixels. At head (600,320), cheek/hand (720,540), coat (560,690) and tail
(310,840), both still contain the subject's identical RGB; the cabin foreground
also retains those pixels. The cabin occlusion is opaque and the eye support is
black at these witnesses, not a clean moving-subject matte. Reusing either still
as a clean plate fails the probe's negative checks; a missing or transparent
backing is also rejected. Misalignment rejection cannot yet be tested
against a real registered subject. These samples locate the missing
artwork, **not** a full silhouette, swept-support, alignment or visual-quality
measurement. No pixel outside support can yet be certified for moving footage.

A genuinely clean cabin/seat/window plate behind the translated subject, a
registered transparent whole-subject cutout including the cheek-contacting hand,
and separated stationary foreground/occlusion require supplied or separately
admitted external finishing. The current flat image has no observations of
hidden seat/cabin pixels. No geometry/pivot is asserted as motion-ready, and no
new private project/import was created: successful opaque import of this painted
still would misrepresent motion readiness. Obtain bounded artwork-job admission or
supply finished aligned layers before completing Step 1.2's full swept-support
assertions and production import/reopen. No live generation/render/encode/decode,
new take or selection occurred; prior art, accepted preparation and four MP4s
remain unchanged.

### External breathing artwork candidates (2026-09-29)

At the user's explicit request, the Codex/Pi image-generation tool produced new
artwork drafts under `docs/pictures/video/tabi-assets/train-actions/`, without
changing the accepted bundle:

- `13-breathing-clean-cabin-candidate.png`: opaque empty-cabin/seat inpaint.
- `14-breathing-tabi-transparent-candidate.png`: generated transparent isolated
  TABI, for silhouette guidance only; it is not a pixel-identical original.
- `15-breathing-stationary-occlusion-guide.png`: generated extraction guide,
  **not** a usable occluder: it includes the entire bench and window rim over TABI.
- `16-breathing-source-rgb-cutout-guide.png`: original fixed-scene RGB with the
  generated character alpha; source color is exact, silhouette alignment is not.
- `17-breathing-table-cup-occlusion-candidate.png`: original RGB with a restricted
  portion of the generated table/cup alpha; seat/window overpaint is removed,
  but the mask still overlaps the candidate subject at 37,613 pixels.

The tool served 1672×941 images; these five derivatives are resampled to the
1920×1080 viewport. A scratch-only [static stack](../build/vg2-character-art-a1/table-only-stack.png)
places the source-RGB character over the inpaint and table/cup layer. The plate
is opaque at all 2,073,600 pixels and differs from the character at the four
former interior witnesses, but it also redraws 1,138,452 of 1,740,827 pixels
outside the candidate subject matte by more than 3 RGB levels. In particular
its main window frame shifts left; it is **not a registered, clean motion
backing**. One targeted generative correction still shifted the frame; a
localized composite showed exterior architecture on the cabin flower poster
and was rejected, retained only in ignored scratch. Generated originals and
measurements remain under `.pi/generated-images/` and
`build/vg2-character-art-a1/`; the source, accepted preparation and four videos
were rehashed unchanged. No motion rendering or production import/reopen was
performed. These are unreviewed artwork candidates, **not** completion of VG2-06
Step 1.2. Accurate frame/window restoration, subject-edge/occlusion cleanup and
swept-support/neutral-composite validation still precede a live attempt.

#### User-requested cutout and notebook revision (2026-09-29)

The user spotted table pixels in `16`, TABI pixels in `17`, and notebook sketches
facing the viewer instead of TABI. Keep the original files 16/17 as superseded
candidate evidence. New derivatives in the same `train-actions/` folder are:

- `16-breathing-source-rgb-cutout-guide-v2.png`: original-RGB **visible** TABI
  selection, excluding the tabletop/cup mask; no invented body behind the table.
- `17-breathing-table-cup-occlusion-candidate-v2.png`: source-pixel table, corrected
  notebook and paper cup, excluding the hand and lower-body regions of TABI.
- `19-breathing-book-facing-tabi-scene-candidate.png`: preserved full source scene
  with only notebook-page ink redrawn; tower base now points toward seated TABI
  at the back of the page and tip toward the near edge. The physical page, pen,
  cup and remaining scene are untouched.
- `20-breathing-book-facing-tabi-clean-cabin-candidate.png`: the same corrected
  page interior applied to the earlier empty-seat *candidate*, not an accepted
  backing plate.

The book drawings were edited by the Pi image-generative tool from a close-up;
only its page-interior edit was composited into the source. Exact pixel comparison
shows 30,162 changed pixels, all confined to the book-page region; the orientation
is a visual judgment from the [close-up](../build/vg2-character-art-a1/book-corrected-closeup.png).
A manually traced stationary table/cup mask, with original RGB, replaced the
incorrect generated foreground mask. The retained recipe reproduced both missing
v2 PNGs on escalation (cutout SHA-256 `c1e0e22310565d7ee7294ca4097c031102ef6c54ccdea4ebcc789fb8cedc7367`,
foreground SHA-256 `f43bdb5012acfc0a77ab9cbed29e3f7d31a527942c256c90ebb6e5083ac9cfe4`).
`node build/vg2-character-art-a1/check-corrections.cjs` now checks the current
derivatives rather than assuming the superseded guides retain old failure pixels:
wood at (430,815) is absent in the subject, and TABI at (450,860) and (720,740)
is absent in the foreground. Mutated subject RGB, a missing subject witness and
transparent backing reject. The 1:1 full-viewport cutout is at (0,0), with no
rotation and no used pivot; the conservative translated support covers 338,677
pixels for ±2 px vertical travel plus 3 px fringe. The opaque generated plate
redraws **1,703,062 pixels outside that support** against the book-corrected
scene (first mismatch at 0,0); the registration negative check rejects it.
Machine output: `build/vg2-character-art-a1/escalation-check.json`. The [rough revised stack](../build/vg2-character-art-a1/cutout-v2-stack.png)
is **not** a motion proof: the generated subject edge and empty-seat/window plate
remain unregistered; swept support, original-pixel neutrality and moving contact
still need inspection. A scene-registered clean plate and corrected subject edge
are not present; obtain bounded artwork-finishing admission or supplied finished
layers before claiming swept-support coverage and import/reopen. The accepted
original scene, corrected leaf bundle and four MP4s were rehashed unchanged.
No production import/job/take or artistic approval.

#### Bounded registered-plate and Tokyo parallax artwork study (2026-09-29)

The user explicitly requested finishing the missing artwork and retaining
parallax, citing `scenario/panorama-style.png` as the look and
`scenario/inspiration-tokyo/` as Tokyo architecture reference. A cropped,
cyan-marked subject removal was edited with the Pi image-generative tool; the
served 1254×1254 inpaint is retained as
`train-actions/23-breathing-inpaint-crop-source.png`. Only the 1920×1080
source-registered subject selection receives its hidden cabin/seat pixels;
the rest of the book-corrected scene is byte-derived without redraw:

- `train-actions/26-breathing-registered-backing-v2-candidate.png` is an opaque
  localized backing (309,702 RGB pixels differ from the selected still);
  **zero differences outside the measured ±2 px vertical plus 3 px fringe
  support**, unlike candidate 20's 1,703,062 outside-support mismatches.
- `train-actions/27-breathing-registered-subject-v2-candidate.png` keeps the
  original subject appearance and solves edge colors for neutral stacking with
  26 and the fixed table/cup layer 17-v2. The static ±2/0/+2 pixel-shift
  stills/measurements are in ignored
  `build/vg2-character-art-a1/registered-v2-sweep-check.json` and adjacent
  `registered-v2-shift-*.png`. All three test stills change zero pixels outside
  conservative support; at zero displacement, 2,111 pixels differ by more than
  eight RGB levels, so this is **not** a pixel-perfect neutral restoration.
- The earlier local backing/subject 21/22 and whole-scene redraw 13/20 remain
  retained drafts, not selected ready assets. Visible ghost edges in the
  *backing itself* around the poster, seat and window and subject/table contact
  remain on direct visual inspection. Static masks do not prove shutter-filtered
  motion, a neutral entry/exit phase or acceptable 2 px exposure. **Step 1.2
  remains BLOCKED before production import/reopen.** The ignored
  `node build/vg2-character-art-a1/check-registered-kit.cjs` probe now explicitly
  rejects the measured 2,111 neutral pixels over eight RGB levels (negative
  misaligned-pixel case), despite zero outside-support differences; receipt:
  `build/vg2-character-art-a1/registered-v2-sweep-check.json`. A static
  support-only pass is not moving-input approval. No media or model video job.

For exterior parallax, the already validated 5600×1080 far/middle/near art was
copied *byte-identically* from the accepted preparation to
`scenario/tokyo-parallax-{far,middle,near}-5600x1080.png` (original pins above).
These three separate depth planes, not the flat picture or cabin backing, are
the rigid parallax inputs of the 30-second reference; no layer or motion approval
was inferred from the copies. A separate
`scenario/tokyo-parallax-addition-study.png` is a **flat, opaque style study**
generated with `panorama-style.png` and the Asakusa/Ginza references: red temple
roof, low shops, cherry blossoms and pale clock facade. It is not a transparent
plane, joined tile, verified match to the existing skyline or usable 60/240-second
coverage. The current 5600-pixel layers do not meet the previously measured
240-second extent bounds (far 6,909 / middle 13,058 / near 18,747 px, plus overlap
and filter margin); that corridor remains VG4 work after character review.
Original art, accepted mask, existing depth planes and four MP4 hashes were
rechecked unchanged. No production import, job, take, character-motion approval
or full-corridor claim.

#### Registered cabin artwork-finishing attempt (2026-09-29)

The user authorized one Pi image-edit attempt and one targeted correction, with
no video render. The first edit cleaned the existing 1254×1254 inpaint crop,
using the cyan removal guide and selected scene for geometry/style; the correction
used the resulting registered backing's *actual visible seam* as its edit target.
Both generated PNGs and all local composites are preserved under ignored
`build/vg2-character-art-a2/`. The generated originals were not modified and no
new ready asset was published to `train-actions/`.

The scratch `try-kit.cjs` reprojects each edit only inside the measured character
selection at 1920×1080, solves original-look subject edge colors, checks opaque
backing and unchanged RGB outside the conservative ±2 px/3 px support, and writes
neutral/±2 px review stills. The first attempt's neutral still has **2,544**
pixels with channel delta >8; the targeted correction has **2,287** (prior
candidate 26/27: 2,111). Both change zero backing pixels outside support.
Direct inspection of `corrected-backing.png` still shows a TABI-shaped patch
crossing the poster, wall, window and bench; it is not clean erased-subject art.
A neutral still can visually conceal those backing seams behind TABI; the ±2 px
still is not filtered-motion/occlusion, contact or endpoint proof. Receipts:
`candidate-measure.json`, `corrected-measure.json`. Model-reported output was
1254×1254 opaque for each edit, with low/medium reported quality respectively;
this is not a claim of native 1080p art or model precision. Protected source,
accepted preparation and four review-video hashes were rechecked unchanged by the
input probe and direct hashes. No image was imported into a private project;
no media/model-video job, new take or artistic approval occurred.

The two-call artwork budget is exhausted. **Step 1.2 remains BLOCKED** on a
properly registered ghost-free plate, finished subject edge and occlusion.
Obtain externally finished layers or a fresh, explicitly bounded artwork-finishing
admission before repeating swept-quality validation and production import/reopen.
Do not repair this by relaxing the neutral check or by retouching protected art.

#### New full-cabin replacement study (2026-09-29)

After the first bounded edit/correction failed, the user authorized another Pi
image-generation attempt and explicitly allowed a full cabin redraw. The one
successful new edit used the book-corrected scene, cyan character-removal guide
and earlier empty-cabin crop as references. Its opaque model output is
**1672×941** (backend-reported medium quality); no claim of native 1080p detail.
It was scaled once to 1920×1080. No second correction call was used; one bad
local reference path was rejected before a generation request. The new scene is
**not registered to the old approved scene**. It changes 1,741,839 backing RGB
pixels outside the old subject support by at least one level, including exterior
buildings, cabin wall and table, and thus cannot pass the original 26/27 neutral
comparison. This is a newly drawn creative candidate, not a hidden change to
accepted artwork.

Review three non-destructive stills in `docs/pictures/video/tabi-assets/train-actions/`:

- `28-breathing-rebuilt-cabin-review-candidate.png`: clean empty-seat, opaque
  1920×1080 background with the generated exterior.
- `29-breathing-rebuilt-neutral-review-candidate.png`: that background with the
  existing source-RGB TABI subject and a fixed table/cup mask colored from the
  new backing. This is a *new* baseline, not the old scene restored.
- `30-breathing-rebuilt-parallax-review-candidate.png`: static composite of TABI
  and the unchanged, hash-pinned far/middle/near depth planes through a **new
  provisional window polygon**. This is neither joined scenery nor moving video.

Scratch `build/vg2-character-art-a3/` retains the unmodified tool result, guide,
`full-backing.png`, `full-subject.png`, `full-foreground.png`, `new-window-mask.png`,
`parallax-backing.png`, ±2/0 static stills, source recipe and receipts. The matte
trims 8,341 spurious alpha pixels below 8, retaining 309,708 subject pixels. All
2,073,600 backing pixels are opaque; interior source-RGB pixels at fully opaque
subject/clear foreground remain unchanged. Relative to the **new** resting still,
the ±2 px views change 309,485/309,137 pixels inside support and zero outside;
static parallax views change 309,491/309,141 inside and zero outside. This
self-comparison is expected by construction and is **not** neutral fidelity to
the old approved picture, filtered motion quality, clean contact or endpoint proof.
The poster, suitcase, table/book and window are visibly redrawn; the new window
opening/matte and exterior parallax alignment still need review and full-trajectory
validation. The full-cabin backing has no obvious TABI-shaped ghost on still
inspection; that observation cannot approve the replacement's artistic look.

Source, accepted leaf preparation, existing parallax planes and all four review
MP4s remain unchanged. No current-schema project/import, render, encode, new
take or acceptance occurred. **Step 1.2 stays blocked** until the user decides
whether to adopt this replacement appearance and the new kit passes contact,
shutter-swept/matte/coverage, timing and production import/reopen checks. If the
user rejects the redrawn scene, the old-scene registration blocker remains; do not
relabel a new neutral baseline as a fix for it.

#### VG2-06 appearance gate recheck (2026-09-29)

Read-only command `python3 build/vg2-character-gate-20260929/check-gate.py`
records 20 protected hashes (all unchanged) and ten **unselected** candidate hashes
in ignored `build/vg2-character-gate-20260929/preflight.json`. No explicit user
approval identifies the replacement appearance **and** artifact hashes for
`train-actions/28`–`30`; permission to redraw did not approve that output.
Neither a finished registered original-look cutout/ghost-free opaque plate with
stationary occlusion nor a selected replacement kit was supplied. Earlier 26/27
neutral edge error (2,111 pixels over channel delta 8) and a2 correction (2,287)
remain failed original-route witnesses. The a3 replacement changes 1,741,839
backing pixels outside the old support; its static ±2 px comparisons use its own
new baseline. These cannot establish original fidelity or appearance approval.

A complete decoded-alpha/viewport/transforms and filtered/shutter-sampled swept
support check, baseline comparison, cheek/hand, eyes/gills, foot/seat, table and
stationary-geometry inspection, and negative tests for painted or missing backing,
misregistration and support-only self-comparison require a *selected, finished*
kit. This preflight does **not** assert those tests passed. Step 2.1 and therefore
Step 2.2 remain blocked; no project import, model job, media work or take was
performed. Supply finished original-appearance layers or explicitly approve the
replacement scene with exact artifact hashes, then validate against the appropriate
baseline before importing.

#### Subsequent replacement-appearance decision

After the three replacement review stills were named with their hashes, the user
said “yes, i approve these.” This selects the **new** cabin appearance in
`train-actions/28-breathing-rebuilt-cabin-review-candidate.png` (SHA-256
`922ff5afd87417c9ba653b327d4b00007670981c99d1d927c20f4ea596769e14`),
`29-breathing-rebuilt-neutral-review-candidate.png` (`2d3a8ca5e7b8b55828c44f080fd31efd0e825330560901f5eb1bdd1356ba7e7a`)
and `30-breathing-rebuilt-parallax-review-candidate.png` (`2322f28268921172f74dea3517a63b670e16205e3b98e8164287ddc2cbd2921b`).
All three hashes were rechecked. This decision supersedes only the earlier
**missing appearance decision**: new neutral art is the replacement baseline,
not proof of restoration of the original. The first direct-Canvas input sweep
missed the production compositor's `subjectCanvas` self-alpha clip. When tested
with that actual operation, the unmodified a3 cutout fails the approved neutral
by 3,432 pixels >8/255 (max 48). Retain the failed witness at
`build/vg2-character-approval-20260929/production-compositor-check.json`;
the old sweep's exact-neutral claim is withdrawn. A new scratch-only cutout
`full-subject-alpha-compensated.png` (SHA-256
`c8097fc7b39631c0c19bb359c1ed36079e8a965a0935dc3879fe9f87c6ad0865`)
compensates alpha before the compositor self-clip, without repainting source
RGB or editing approved stills. A binary `full-foreground-coverage.png`
(SHA-256 `0dbc32a26c4fb0ff4f8f388e041d95e9f038509850f2772ade1545a4f07f81fa`)
selects the unchanged a3 stationary foreground for the production occluder.
Both new layers are in ignored `build/vg2-character-approval-20260929/`;
`production-compositor-repaired.json` checks the repaired neutral at both
endpoints: zero pixels >8/255, maximum two channel levels.

The **selected isolated kit** is now 28 as opaque plate, 29 as user-approved
neutral, the new alpha-compensated subject, unchanged a3 foreground and new
foreground coverage mask. Still 30 and the provisional window mask remain
appearance references only. No scenery changes in this isolated character
proof, so there are no moving-scenery shutter samples; the production control
samples the subject once per frame. `node build/vg2-character-approval-20260929/measure.cjs`
measures the actual self-clip and occluder-mask Canvas order at every 217
fractional subject positions: all outputs opaque, zero differences outside
328,526 conservative swept/filter-support pixels, first and last neutral
separately within two 8-bit channel levels of 29, 309,645 changed interior
pixels at peak frame 54, displacement ≤2 px. It rejects missing/transparent
or painted-subject backing, 3-px subject misregistration and self-comparison
mistaken for original-scene fidelity (1,741,839 differences outside support). Compositing the invalid painted
neutral as backing produces 3,384 pixels above the predeclared 8/255 threshold;
a 3-px misplaced subject produces 133,695 such pixels. These are failure
witnesses, not alternatives to the clean plate.
Receipt: `build/vg2-character-approval-20260929/measurement.json`.

Five 1:1 0/1/2-px still strips (`contact-repaired-*.png` in that scratch
folder) were inspected: cheek/hand and eyes/gills move together, feet still
meet the seat without a visible ghost gap, and table, notebook, cup, poster
and window remain intact. This is technical input-kit evidence, **not** user
approval of animated motion; slight 2-px foot/seat relative travel and a
mostly translucent source matte remain moving-review limitations. Step 2.1
passes only for this fixed-scene kit. Step 2.2 must import/reopen these exact
five inputs in a new private project; any moving parallax/window proof and
separately admitted video execution remain open. No prior scene or take was
replaced. The selected-kit measurement and repaired compositor commands passed
on recheck; focused JVM import/asset checks, `make test`, `make build`, diff check
and 20/20 protected pins passed (logs under the ignored approval scratch root).
The older missing-decision preflight remains historical.

#### VG2-06 private-project integrity check (2026-09-29)

The selected **five-input isolated fixed-scene kit** was copied through the
production asset importer to new immutable `vg2-neutral`, `vg2-plate`,
`vg2-subject`, `vg2-foreground` and `vg2-occlusion` references in ignored
`build/vg2-character-import-20260929/final/project/`. Production project and
prepared-scene stores plus the asset importer reopen schema-5 revision 6; the scene
record is `vg2-isolated-breathing` v1, descriptor SHA-256
`22124d90f9c89a6f53cb2f161d81952cf7833a70250d43adfa1bbe4b841f0fe9`.
`final/import-receipt.json` records each original/descriptor digest, decoded
1920×1080 alpha counts and identity/unit geometry. All imported originals have
exactly the bytes of the selected pinned sources; no original/source reference,
review MP4, existing project or selected take was rewritten. The look selector
reconstructs the neutral reference (there is no persisted look record for this
path). The subject, plate, fixed foreground and binary occlusion retain their
separate roles/pins. Stale revision, wrong-space placement, malformed rotation,
missing backing and a changed original in an independent fixture copy reject
without appending another prepared scene or take. The runnable command and
negative results are in the ignored host/receipt beside the private project.
Earlier failed and diagnostic project folders in that scratch root remain as
non-authoritative evidence.

**Important control limitation:** with the selected full-viewport 1920×1080
subject image placed at (0,0), production preparation derives no `TRANSLATE_Y`
capability: the full declared bounds cannot move even 2 px without leaving the
viewport. An explicit −2..2 request returns an actionable `UNSUPPORTED_MOTION`
rejection; it was not converted into a successful preparation. The imported
prepared scene omits that request and proves *only import/reopen integrity*, not
breathing admission. Before a real motion request, a newly derived lossless
subject crop with viewport clearance needs its own approved-neutral and complete
filtered sweep revalidation and new private import. Do not claim the current
scene is executable, change production bounds to suppress rejection, or infer
artistic/motion approval. No renderer, encoder, model or take ran; live admission
remains separate.

#### Side-on Tokyo district-variety study (2026-09-29)

The user requested a more visually varied exterior during a future video, using
`scenario/inspiration-tokyo/`. One Pi image generation and one targeted correction
produced [a new lateral city-panorama still](pictures/video/tabi-assets/scenario/tokyo-sideon-district-variety-study.png)
(SHA-256 `fadd6ada400114dc4c89857be0cc59970f286bf7e175d6c6c557f5fc9f5df879`,
2172×724 opaque, medium model-reported quality). Selected references were the
illustrated `panorama-style.png`, the existing far plane for palette/horizon,
`IMG_1842.webp` for the Asakusa gate cue and `IMG_1845.jpeg` for Ginza clock-front
architecture. They are style/architecture references, not pixels pasted from
photos or claims about publishing rights. The initial canal led into a frontal
vanishing point; the correction replaces it with a lower horizontal water band,
side-profile bridge and lateral buildings. Both model originals remain in
ignored `build/vg4-tokyo-variety-a1/`, not over the earlier clock/temple study.
The new section progresses from low older shops through bridge/water to taller
cream clock-front/brick blocks instead of repeating the existing temple roofs.

Three [window-position stills](../build/vg4-tokyo-variety-a1/window-position-0.png)
(`window-position-0.png`, `window-position-1050.png`,
`window-position-2090.png` in the same scratch directory) sample different parts
of **one flat opaque image** against the *unapproved* replacement cabin from the
prior study. A first scratch Canvas `destination-in` preview changed 1,255 pixels
outside the nominal mask due to an edge fringe. The scratch recipe now composites
using the exact measured mask alpha; the receipt `static-preview-report.json`
checks zero outside-mask differences, fully opaque results and zero missing
window samples at all three positions. This is static presentation only, not a
production renderer fix, decoded video or coverage/continuity validation.

This artwork does **not** extend the pinned far/middle/near planes or prove that
the new district joins them: it lacks shared overlap pixels, separated depth,
full-trajectory coverage and a moving offscreen seam check. Its native 2172-pixel
width cannot be counted as a 60- or 240-second corridor by resizing. Newer cabin,
scene appearance, route/style fit and actual moving variety require user review.
No render, production import, take, accepted scenery replacement or VG4-05
completion is claimed. The protected original scene, corrected leaf bundle,
three approved plane bytes and four existing MP4s remain unchanged.

Follow-up after the user's agreement to tiled depth sections: ignored
`build/vg4-60s-a1/` contains one 2172×724 generated *near-depth cutout*
(SHA-256 `8394912c143796dd5c73b4844cfe842e4bf2eae1d3b45a18422b9ca7fc840463`).
The image tool reported transparency; the decoded pixels confirm 755,422 fully
transparent pixels, 816,987 partly transparent pixels and only 119 exactly
opaque pixels, so local preparation snapped only alpha below 8 / above 249.
An initial hand-traced cutout from the flat study was rejected for obvious
polygonal sky. The prepared **candidate**, not a replacement for an accepted
plane, is `model-near-section-60s-candidate.png` (6000×1080, SHA-256
`0b97234fc23ee541b9b86ab1c73d3f7a0e72fd8f647486c61e5b637643b76292`).
It preserves the current near layer's decoded pixels through x=4127; the
middle-depth streets stay visible across an intentional 532-pixel near-plane
pause, then the bridge → cream clock-front art enters from x=4660. Its last
alpha≥8 column is x=5843. The early switching sample is frame 810 (27s),
when the changed region is outside the viewport. Three shutter-position
window comparisons at that same camera position had no opacity gaps and at
most two levels of 8-bit Canvas round-trip difference; five still positions
at 27, 38, 46, 53 and 60 seconds changed nothing outside the **unapproved**
window mask. User review found a translucent, doubled façade at frame 1380
(46s). The global 90-pixel alpha ramp across the cropped building was the
cause; removing only that ramp exposed a hard vertical building cut. The
revised scratch cut begins at the open bridge instead, with no alpha ramp or
new near façade in the previously shaded area. A regression checks 431
opaque cutout samples stay opaque, zero changed background pixels in the
reported frame-1380 zone, and 334,073 distinct new-near window pixels at
frame 1799. The old rejected still is retained as
`window-frame-1380-rejected-shade.png` in scratch. **User decision,
2026-09-29:** after reviewing revised `window-frame-1380.png` (SHA-256
`47410cd5a6117f85d45848dfbbb0c1099b273413ca06cfa43730ddb748c43fbc`),
the user said “yes, this one is ok, i confirm the quality of the backgrounds”.
This was an initial static-background response, not moving-seam, full-route,
character-kit or video-motion approval. **The user subsequently rechecked the
same still and reported that its lower-right terrace was not blended with the
middle-depth house.** Thus the specific frame-1380 still is not clean or
accepted; keep its prior hash/decision as historical scoped feedback, not a
current quality gate.

The scratch-only correction preserves the previously reviewed source and
stills. Local v2–v4 masks removed the terrace at 46 seconds but introduced
floating tree fragments or a hard façade cut at later positions; these are
rejected studies, not scenery approvals. One targeted edited **source-island
cutout** (SHA-256
`d2caeacead907fd30dc304db32f9886890ae0e88b11287afe41763d645a3b1dd`)
keeps a complete clock-front district and rooted trees, with no leading bridge
or terrace. Its derived v5 near section was prepared at
`build/vg4-60s-a1/model-v5-near-section-60s-candidate.png` (SHA-256
`c1114749dc6b1ae704d448231320c13ef3e3ba092b71ecab7394500b6fc9a1e0`,
6200×1080). It preserves original near pixels through x=4127; first new
alpha≥8 at x=4956, last at x=6019. The transparent near pause is backed by
existing *painted middle streets*, not counted as new near scenery. At the
same frame-810 shutter samples the overlap remains within two 8-bit channel
levels of Canvas rasterization. Static `window-frame-1380-v5.png` leaves all
13,352 inspected lower-right mask pixels identical to the unextended scene;
`window-frame-1470-v5.png`, `window-frame-1590-v5.png`,
`window-frame-1680-v5.png` and `window-frame-1799-v5.png` sample arrival and
clock progression without a pasted terrace. Pixel checks also confirm native
old-pixel overlap, opaque source interiors and late new-art visibility.

**User decision (2026-09-29):** after viewing the v5 46/53/60-second stills,
the user said “the v5 version is ok” and asked to save it under `scenario/`.
This confirms the **static look/quality** of v5, replacing the earlier
terrace-defective still as the scenery-art reference; it does not approve a
moving seam, the replacement cabin or a 60/180/240-second video. Two
byte-identical copies now live in
[`scenario/tokyo-clockfront-near-v5/`](pictures/video/tabi-assets/scenario/tokyo-clockfront-near-v5/):
`tokyo-clockfront-cutout-v5-source-2172x724.png` (source-island SHA-256
`d2caeacead907fd30dc304db32f9886890ae0e88b11287afe41763d645a3b1dd`)
and `tokyo-near-section-v5-candidate-6200x1080.png` (derived section SHA-256
`c1114749dc6b1ae704d448231320c13ef3e3ba092b71ecab7394500b6fc9a1e0`).
The approved static sample at frame 1380 is pinned SHA-256
`b3c969d84b2ae3de9f7636453c330a1bc82d48075c27ede1c10fa75febdfc449`;
the frame-1590/1799 samples remain in ignored scratch for audit. The previous
candidate and protected planes were not overwritten.

The earlier preflight's far *authored* extent is only 2500 px; it still needs
82 more painted pixels for 60 seconds, regardless of its 5600-pixel opaque
image bounds. The existing middle extent is sufficient at this placement,
but coherent far/middle/near district art, exact full trajectory/shutter,
user-approved character kit, moving seam test and motion-specific user review
are still outstanding. No production import, model motion job, render, encode, take or
change to protected planes occurred. The user-requested 180-second journey
requires about 14,420 px of authored near extent plus filter/join margin;
this scratch strip does not cover it (nor the plan's 240-second pilot).

### Background corridor and layered composition

Compose fixed cabin/table, rigid far/middle/near scenery, separately validated TABI
motion and explicit foreground occlusion on one shared clock. Keep the approved
scenery speed/scale/style. Inspect unused supplied pixels first, then prepare only
the first coherent extension. Prove one offscreen handoff in a moving test before
expanding the library. Independently generated images are not inherently tileable:
retain exact shared overlap pixels and validate them in the actual viewport/shutter
samples, along with opaque backing and measured alpha. No wrap, stretching,
reversal, transparent-padding coverage or silent slowdown.

First establish 60-second coverage; after combined review, extend the same method
to the full 180-second pilot corridor. At the approved speed and scale, schedule
about 18–36 distinct Tokyo views/points of interest, spaced about 5–10 seconds
apart with quieter authored travel between them. Preserve the warm side-on style;
no pasted terrace, translucent façade, hard-cut fragment or repeated landmark
counts as a new view. Read-only horizontal lower bounds at the current placement,
window right edge 1920 and camera speed `480/599` pixels/frame are:

| Plane | Current x / depth | Minimum total extent for 180s | Earlier 240s bound |
| --- | --- | ---: | ---: |
| Far | 780 / 1 | 5,467 px | 6,909 px |
| Middle | 400 / 2 | 10,173 px | 13,058 px |
| Near | 480 / 3 | 14,420 px | 18,747 px |

Calculation: `ceil(1920 - x + depth * 480 * (frames - 1) / 599)` with
5,400 frames for 180 seconds; the earlier 240-second bounds use 7,200 frames.
These are not additional-pixel counts or full coverage admission. Add overlap/
filter margins and verify every trajectory/shutter position. Use bounded tiles
rather than raising image-size limits. The renderer currently loads all supplied
images; 300-frame chunks alone do not bound asset memory. Measure the selected
kit and scope loading changes only if necessary, without dropping consumed pins.

### Supervised production runner, then the app

Reuse current project/job/media services through a thin owned harness, not a
second ledger, scheduler or permanent alternate product. Finish only the execution
binding, continuation/checkpoint and encoding boundaries needed for this film.
Defer app caller integration/short-shot retirement, Video UI and general workflow
work until the full pilot has been reviewed. Preserve existing implementations.

The preferred first assembly route is verified absolute chunks of at most 300
frames → retained PNGs → one immutable numbered sequence → one final silent
H.264 encode. The pinned FFmpeg build supports image2 but not concat; joining MP4s
or switching tools is not an assumed capability. Prove any alternate path separately.
Keep exact trim/support ranges, continuous timestamps and shared blink/effect/random
state. Resume only from verified completed-chunk evidence without rerendering those
chunks; reconcile uncertain work and retain incomplete attempts. Current completed-
output recovery does not already supply this boundary or abrupt-crash recovery.

The recorded 30-second run took 504.265s and retained 2,802,271,515 source-PNG bytes.
Straight-line four-minute extrapolations are about **67 minutes and 22.4 GB of
source PNGs**; they are retained historical estimates, not measurements or an
admitted budget for the three-minute target. Encoder copies, assets, decoded review,
outputs and the free reserve are extra. Measure composition, encode and review
separately at 60 seconds; bound disk as well as RAM. Do not retain all decoded
review frames merely to inspect every frame when bounded streaming can be verified.

The existing preview attempt has a shared **900-second** deadline, not 900 seconds
per chunk. Full production needs a newly admitted finite batch budget with stage
and cumulative time/storage limits; this document grants no increase. Retain
2-GiB native memory enforcement and the 10-GiB free-disk reserve. No automatic
resource retries, unrelated process termination, cache purges or quality reduction.

After the character and seam decisions, produce the combined 60-second proof with
corrected source masks, approved character motion and extended scenery. Verify
full decoding, joins/resources and restart after a completed chunk while further
work remains. Obtain normal-speed user review before full corridor preparation
and the separately admitted 180-second/5,400-frame silent 1080p30 pilot with
moving far/middle/near scenery, distinct passing views and a pinned schedule of
reviewed in-cabin TABI actions beyond blinking across the full cut. The isolated
action proof is not a 180-second hold/loop approval; prepare/review additional
compatible motion inputs if needed. Inspect quiet stretches and all joins; watch the
entire film and test editor import/playback. Only then implement the reusable app
workflow around this proven recipe. This is production-method acceptance, not
rights clearance, monetization, color certification or VG6 app/release approval.
The eventual app remains generic; TABI/Tokyo is this pilot, not a required preset.

## Outcome and scope

**Current delivery scope (pilot target revised to 180 seconds):** the user creates
all finished picture assets outside
Melotrail. The app accepts a **finished scene image, with optional separate
character/background layers**, then generates motion and a complete continuous
180–300 second silent video (default 180s), 1920×1080 H.264 MP4 at 30 fps. No in-app
image generation, outfit/style transfer, inpainting or new-background creation is
offered in this delivery. Missing motion-ready artwork is supplied externally;
separately authorized pilot preparation does not add those app features. Audio
editing and public upload remain external.

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

## User-reviewed Tokyo feasibility clip (2026-09-27)

The project user reviewed the exact pilot below in the current conversation:
“I like this direction, aside the quality that should be more high for youtube,
the animation is quite good.” This accepts its **creative/motion direction only**;
resolution/detail and publication quality remain unaccepted. The user requests
better ComfyUI quality and a reusable workflow for different TABI scenarios.

- Artifact: `build/tabi-tokyo-lowres.DR8zjZ/tabi-tokyo-reading-pilot-768x448.mp4`;
  SHA-256 `f905254ccc3c85769f6591031a6336ec78bed2345285db8d869003b96f366536`.
- Source: [Tokyo train scenario](pictures/video/tabi-assets/scenario/tabi-quiet-ride-through-tokyo.png),
  SHA-256 `01e4db852ceed7bc4d17708c3d181103d0699e5caed54bbc44eda1d83932473e`.
  TABI action images and [Tokyo inspiration](pictures/video/tabi-assets/scenario/inspiration-tokyo/)
  informed the prompt; they were **not additional model image inputs**.
- Candidate: `b208cc700`, with an ignored scratch host wrapper around the production
  setup, runtime, job coordinator/store, backend and media probe. No product source
  or model/runtime pins changed. This was not an in-app Video workspace run.
- Profile: `comfyui-ltx23-v1`, LTX-2.3 distilled 1.1 Q4, Gemma 3;
  bundled graph SHA-256 `c1d27b28313ba882659c0a7f52d94e666cabce45bfa5ce0fa3d256cc58b79bc7`.
  One attempt produced native 768×448 H.264, 129 frames at 25 fps, 5.16 seconds,
  zero audio streams, with full decode checks. Runtime elapsed: 242,982 ms.
- Receipts: `build/tabi-tokyo-lowres.DR8zjZ/pilot-result.json` and
  `output/media-proof/video-media-probe-report.json` beneath that same run root.
  Existing memory-pressure, swap and time safeguards remained enabled; owned
  processes stopped and original inputs stayed unchanged. An earlier 1024×576
  attempt stopped on critical host memory pressure and produced no clip; its
  failure remains in `build/tabi-tokyo-pilot.wXLu35/segment-01/failure.json`.

A 20–30-second pilot remains the next user-desired visual target, not an existing
result. This short directional approval does not pass VG2-03's controlled-preview
proof, VG6-02's three app clips, full-length continuity, other-scenario quality,
asset/model rights, YouTube monetization, or any release gate. It does not authorize
new model downloads, runtime changes or unbounded inference. Upscaled footage must
remain labeled as upscaled; increasing export dimensions alone proves no new detail.

### Authorized finishing comparison (2026-09-27)

At the user's request, the same approved-direction clip was processed into two
[review videos](../build/video-test-archive-2026-09-28/previous-tests/quality-ab-2026-09-27): **A**, ordinary Lanczos
resizing; **B**, the installed RealESRGAN x2plus through a finishing-only ComfyUI
graph, followed by Lanczos resizing. Both decode completely as silent 1920×1080
H.264, 129 frames, 25 fps, 5.16 seconds, with the original frame timestamps. The
matched 16:9 crop removes eight source pixels from each of the top and bottom;
neither version stretches the picture. Originals remain hash-identical.

B processed each source frame once, separately, into 1536×896 before cropping
and final resizing; this is **upscaled footage, not native 1080p generation**.
The finishing run took 199,837 ms, with existing runtime safeguards unchanged,
normal recorded memory pressure, no recorded swap growth and owned runtime
stopped afterward. No LTX/Gemma inference, downloads or hosted services ran.
[Comparison receipt](../build/video-test-archive-2026-09-28/previous-tests/quality-ab-2026-09-27/comparison.json)
records output hashes, model/graph pins, crop/encoding settings and limitations;
the same directory contains three labeled 1:1 detail comparisons and decode
receipts. Scratch job ledgers/wrappers remain in `build/tabi-upscale-ab.vJICSz/`.

The initial agent still review found sharper character outlines and clothing
detail in B, with changed fine texture; existing city geometry was not repaired.
Both files report a BT.709 matrix and limited range but unspecified
transfer/primaries tags, so these are review experiments, not publication masters.
No visual gate, longer continuity, other-scenario proof or product integration is
claimed.

**Subsequent user review, 2026-09-27:** “the B is sharper and better quality”, but
the user reports background flickering and wrong/sloppy movement of exterior
objects. This identifies B as the preferred finishing treatment, **not acceptance
of background motion or final publication quality**. Reviewed artifact:
`build/video-test-archive-2026-09-28/previous-tests/quality-ab-2026-09-27/B-realesrgan-x2-1080p.mp4`, SHA-256
`49f352ac5382e844e7b59677e7c6b30348d6f5e371bd64f4d739399ec7a5b874`.
The historical comparison receipt retains its pre-review state.

Read-only inspection of the original decoded frames confirms malformed passing
pole/support geometry at 2.00 seconds (frame 51, one-based) and a smeared pole and
crossarm at 5.12 seconds (frame 129), before enhancement. Source frames remain in
`build/tabi-upscale-ab.vJICSz/source-frames/`. The whole-scene I2V graph consumes
one starting image and text; its prompt already asks for rigid buildings, but it
has no window mask, explicit depth planes or rigid-object trajectories. The
per-frame upscaler may accentuate fine-detail shimmer; its incremental temporal
contribution has not been measured. This inspection launched no new inference,
download, upload or media modification. Background correction remains unproven.

### User-requested exterior style studies (2026-09-27)

Five still-image alternatives and the original reference are in
[Tokyo background styles](../build/video-test-archive-2026-09-28/previous-tests/tokyo-background-styles-2026-09-27/00-comparison.jpg),
with a separate [exterior closeup comparison](../build/video-test-archive-2026-09-28/previous-tests/tokyo-background-styles-2026-09-27/00-exterior-comparison.jpg).
Full-size PNGs retain the source's 1664×936 dimensions and sRGB profile. A static
main-window selection protects TABI, the cup and cabin; pixels outside that
selection are verified unchanged. Smaller left windows remain original.

Options are posterized pastel, soft painted wash, minimal geometric Tokyo,
ink/warm-paper wash and retro pixel. Four are local image-processing studies;
**03 is a procedurally drawn, simplified Tokyo interpretation**, retaining the
Skytree motif but reinterpreting secondary buildings and omitting wire/pole clutter.
These are not model-generated restyles. The directory's `comparison.json` records
methods, hashes and limitations. Original artwork and A/B videos are untouched;
no model, download or hosted service was used. This user-requested artwork
exploration adds no in-app synthesis feature. The still studies alone do not
establish reduced flicker, rigid movement, independent depth layers or offscreen
scenery coverage. Their original machine receipt retains its pre-selection state.

### Selected geometric-Tokyo motion pilot (2026-09-28)

The user selected **03 — Minimal geometric Tokyo**: “i want to go with Minimal
geometric Tokyo, try with that”. Selected PNG SHA-256:
`eb1a779ff6b93ff83d05d0186d8bd3c06aadc786989bb6a086230f7be1d658d5`.
This authorizes the bounded style/motion trial, not approval of its generated video.

One local I2V submission produced a new 768×448, 129-frame, 25-fps, 5.16-second
silent H.264 clip. The pinned LTX-2.3 distilled Q4/Gemma profile, seed `20260914`,
eight-step sampler and native dimensions/cadence match the earlier pilot. Both
the starting exterior artwork and exterior prompt changed: slow, steady travel,
flat fills, rigid simple architecture and no added poles/wires or realistic detail.
This is not a single-variable comparison or a masked/controlled-scenery render.

After stopping the generation runtime, 129 separate RealESRGAN x2 frame jobs
applied the preferred B treatment. The final derivative crops 16 pixels from
each top/bottom edge at x2, then uses Lanczos to 1920×1080, with explicit square
pixels/16:9. There is no interpolation, looping or speed change. Generation-wrapper
elapsed time was 229,961 ms; finishing-wrapper elapsed time was 195,380 ms.
Recorded pressure stayed normal; maximum sampled host swap during generation was
78.19 MiB. Existing safeguards remained active and both owned runtimes stopped.
No new models, downloads or hosted service were used; originals remain unchanged.

- [B-finished 1080p review clip](../build/video-test-archive-2026-09-28/previous-tests/minimal-geometric-tokyo-pilot-2026-09-28/minimal-geometric-tokyo-B-1080p.mp4),
  SHA-256 `ecd890f20ad22e5cf750f5fd3696dd571519c9fe75a33b0416b5214129448658`.
- [Native clip](../build/video-test-archive-2026-09-28/previous-tests/minimal-geometric-tokyo-pilot-2026-09-28/minimal-geometric-tokyo-native-768x448.mp4),
  SHA-256 `11a950124eb2178ca4873320d517ab161ab1bc3c82141a1cd56d3ea8fa3db09f`.
- [Receipt](../build/video-test-archive-2026-09-28/previous-tests/minimal-geometric-tokyo-pilot-2026-09-28/pilot-result.json)
  and [sampled frames](../build/video-test-archive-2026-09-28/previous-tests/minimal-geometric-tokyo-pilot-2026-09-28/review-frames.jpg).
  Both videos pass complete decoding and exact 0.04-second timestamp checks.
  The receipt discloses native unspecified SAR, final explicit square pixels,
  remaining color-tag limitations and upscaled rather than native 1080p detail.
  Scratch job ledgers/wrappers: `build/tabi-geometric-pilot.PQ2JOB/`.

Inspected samples retain the simplified skyline without the earlier large
pole-smearing artifact, but some tree-canopy and roof/window shapes still soften
or deform. No flicker reduction was measured; these initial agent observations
were not acceptance.

**Subsequent user feedback, 2026-09-28:** the user calls this clip “a good starting”
but reports problems in TABI's eyes and blurring/sloppy shapes around buildings,
and requests a more distinct background. This is preliminary directional
feedback, **not approval of eye animation, background coherence or final quality**.
It refers to the B-finished clip and hash above; the machine receipt retains its
pre-review state. No long-continuity or VG2/VG6 gate is passed.

Matched native/RealESRGAN crop inspection shows eye-contour and iris-shape changes
during the blink/gaze transition around 2.40–2.88 seconds (one-based frames 61,
69 and 73), already present before sharpening. Native samples also retain softened
or deformed building/tree shapes. This does not quantify sharpening's incremental
temporal effect. The local inspection sheet is
`build/tabi-geometric-review.hY6Vx0/eye-comparison.png`; no new inference or original
media modification was performed for this review. Corrected motion and any more
distinct artwork still need separate user review.

### Local ComfyUI panorama artwork proof (2026-09-28)

The user authorized Tokyo-reference background/parallax tests and explicitly
selected continuing **ComfyUI**, rather than Codex image generation, to exercise
the intended local workflow. One fresh, bounded artwork attempt used the already
installed FLUX.2 Klein 4B local FP16-dequantized derivative, Qwen 3 4B and FLUX.2
VAE. This is standalone asset preparation, not an in-app image-generation feature
or a resumption of the retired reference-image trial. No download, hosted request,
new custom node, production-source edit or runtime-profile change was made.

The scratch API graph binds three actual image references: an exterior-only
`(820, 0, 1664, 512)` crop of selected style 03, plus
`scenario/inspiration-tokyo/IMG_1842.webp` (Asakusa architecture) and
`scenario/inspiration-tokyo/IMG_1845.jpeg` (Ginza architecture), under the asset kit.
Each is aspect-preservingly encoded at 0.25 megapixel, rounded to multiples of 16.
Four Euler steps, CFG 1 and seed `20260928` produced **one native 1536×512 opaque
PNG**, without AI upscaling. The existing owned `ComfyVideoRuntime`,
`LocalVideoBackend`, `VideoJobCoordinator` and `VideoJobStore` performed the job;
the caller remains a scratch wrapper, not the planned Video UI.

- [Native panorama](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-2026-09-28/panorama-native-1536x512.png),
  SHA-256 `f4edca0eb1337aa2723b9b73cda2eaf06a51f9d7e22c0b73e848241598ebd66a`.
- [Residential/temple context](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-2026-09-28/scene-start.png)
  and [clock-tower context](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-2026-09-28/scene-end.png)
  are **static spatial views**, not animation frames. Ordinary resizing and the
  previous main-window mask place the generated plate into the original scene;
  every pixel outside the mask is verified exact. TABI, cup, cabin and smaller
  left windows stay static and unchanged.
- [Receipt, input/model pins and resource facts](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-2026-09-28/result.json).
  The adjacent `evidence/` retains the API graph, prompt, typed job ledger,
  style crop, host samples and wrapper. Scratch: `build/tabi-comfy-panorama.RsbQ3F/`.

The single attempt completed; runtime-wrapper elapsed time was 75,468 ms,
including startup and post-run verification. All original/model pins were
verified unchanged and the owned runtime stopped. Of 34 recorded host samples,
32 were NORMAL and **two were WARNING**; none were CRITICAL, and no swap increase
was recorded. Maximum sampled owned-process-tree RSS was 14,008,844,288 bytes;
this excludes some Metal/unified-memory allocations. An initial normal-only
summary was corrected from the full sample log, with no repeated inference;
three scratch reporting regressions cover retained warnings, intermediate swap
peaks and rejection of critical/unknown/missing samples.

The image has distinct residential, temple and clock-tower sections, but fine
roof/window details remain irregular. **User artistic review is pending.** This
is one flattened plate, not independent depth layers, a seamless tile, adequate
180–300-second coverage or a parallax/eye-motion proof. Layer preparation,
occlusion/coverage validation and a bounded controlled-motion test remain next;
no VG2/VG6 acceptance or integrated application capability is inferred.

### More characteristic Tokyo artwork revision (2026-09-28)

Reviewing the first panorama, the user said: “yes, but i'd like something more
detailed and characteristic, not anonymous or oboring”. This requests refinement,
not final look or motion approval. The next local ComfyUI candidate adds tiled
roofs, balconies, laundry, bookshop shelves, vending machines, bicycles, lanterns,
more articulated cherry trees and distinct storefronts, with stronger linework.

The still uses the same installed Klein/Qwen/FLUX.2 VAE models, four Euler steps,
CFG 1 and seed `20260928`. Native resolution is now **1920×640**, not an upscale.
Four reference images are actually bound: the original illustrated scenario's
exterior crop `(820, 0, 1664, 512)`, the same Asakusa/Ginza photos, and a
`(0, 0, 320, 620)` facade crop of `inspiration-tokyo/IMG_1844.webp`. The original
illustration replaces the very flat geometric style reference. Prompt, references
and resolution changed together; no single-variable quality claim is made.

The initial submission produced no image: persisting ComfyUI's node-local progress
as whole-job progress triggered `PERSISTENCE_FAILED: Known video progress cannot
become unknown or move backwards`. Its wrapper stopped the owned runtime and its
ledger remains at the last ACTIVE/100 observation; that historical state is not
rewritten. **VG2-04 fixes the adapter**, not the store invariant: whole-workflow
progress stays unknown while running, and verified output history establishes
completion. Node-reset/reconnect/durable-publication regressions failed before
and pass after the repair. One corrective retry used identical graph/prompt/
dimensions/seed, with repaired client source/bytecode included in its pins.

- [Detailed native panorama](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-detailed-2026-09-28/panorama-native-1920x640.png),
  SHA-256 `16c276238e2c62d0869c815c51d343bc2f9b680bbdee934a15f246fc810d362a`.
- Static context views: [shops](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-detailed-2026-09-28/scene-start.png),
  [temple](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-detailed-2026-09-28/scene-middle.png),
  [clock-tower district](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-detailed-2026-09-28/scene-end.png).
  All pixels outside the prior window mask remain exact; TABI/cup/cabin are static.
- [Receipt](../build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-detailed-2026-09-28/result.json)
  records **two submissions: one failed, one successful**, preserves both requests
  and host samples, and includes the progress repair patch. Scratch retry:
  `build/tabi-comfy-panorama-detail-retry.1lv1ok5y/`.

The successful retry's runtime-wrapper time was 95,567 ms. It recorded 56 NORMAL
and two WARNING samples, no CRITICAL samples, and about 702.07 MiB maximum sampled
swap increase; maximum sampled owned-process RSS was 17,200,971,776 bytes, not a
complete Metal/unified-memory measurement. The failed attempt separately recorded
two warnings and about 2.52 GiB maximum sampled swap increase. Both runtimes stopped;
model/source/prior-output pins remain unchanged. No download, hosted generation,
new model, custom node or runtime-limit relaxation was used.

**At publication, look review was pending; this was not motion-ready.** Small human figures appeared
despite the no-people prompt, signage is not verified typography, and the larger
foreground buildings/street perspective depart from the requested shallow side-on
elevation. These must be resolved for the selected motion treatment. This opaque
plate provides neither separate depth layers nor a parallax/eye-motion proof,
full-duration scenery coverage, app integration or VG2/VG6 artistic acceptance.

### Layered Tokyo preparation and blocked parallax render — 2026-09-28

The user subsequently approved the richer **direction** (“yes, it's really a good
direction, i'd like to continue in that way”) and authorized the staged plan (“go
with this plan”). That is not approval of final layers, motion or release quality;
the earlier receipt remains unchanged.

New review artifacts: [start composition](../build/video-test-archive-2026-09-28/previous-tests/tokyo-layer-preparation-2026-09-28/scene-start.png),
[end composition](../build/video-test-archive-2026-09-28/previous-tests/tokyo-layer-preparation-2026-09-28/scene-end.png),
[middle matte](../build/video-test-archive-2026-09-28/previous-tests/tokyo-layer-preparation-2026-09-28/middle-matte-review.jpg),
[near matte](../build/video-test-archive-2026-09-28/previous-tests/tokyo-layer-preparation-2026-09-28/near-matte-review.jpg)
and [receipt/layer pins](../build/video-test-archive-2026-09-28/previous-tests/tokyo-layer-preparation-2026-09-28/result.json).
**These are preparation stills, not video frames. No new video was produced.**

Four bounded ComfyUI Klein submissions produced native 1920×640 stills. The first
far-plane candidate retained foreground architecture and was rejected for that
role. One admitted correction and the two other initial roles used actual cropped
references from the approved detailed panorama, encoded at 0.25 MP, with four
Euler steps/CFG 1. All four jobs completed; the rejection was artistic, not a
runtime failure. Three separate RealESRGAN x2 jobs then finished the usable stills
at 3840×1280. No frame-by-frame AI redraw, extra model, download, hosted service or
Codex generation call was used.

The kit contains an opaque far plate and genuinely transparent middle/near planes,
prepared by local chroma matting, explicit cropping and aspect-preserving resizing.
These are new style-matched drawings, not faithful automatic extraction of the flat
panorama. The far crop excludes a second generated tower. The middle plane reveals
the temple; the near plane supplies shops/branches. Generated signage and recurring
shop motifs remain limitations, especially before any longer travel. TABI/cabin
are the original artwork resized once with Lanczos to 1920×1080; pixels outside
the resized main-window mask are exact in the static checks. Small left windows
remain unchanged. Matte/scale/style approval is still open.

VG1-02 adds measured transparent-overlay admission without counting overlay bounds
as opaque coverage. Two JVM regressions and one Node regression failed before the
change; focused suites and 24 Node tests now pass, including real importer fixtures
with three depth planes and exact foreground pixels. The full run additionally
exposed two old hand-written coordinator fixtures lacking measured alpha; corrected
fixture counts and explicit missing-measurement rejection now pass. `make test`,
`make build` and `git diff --check` passed after that repair. The real prepared project
and typed 150-frame descriptor also passed preparation. The supplied visible crop covers
the entire planned trajectory and three shutter samples; padding stays behind the
fixed cabin. An initial placement failing the mask's resampling fringe was retained
and corrected before any render submission.

The **one** durable controlled-media attempt then failed memory admission before
launching Node/FFmpeg: required **2,147,483,648 free bytes**, reported
**830,046,208 bytes** (about 792 MiB). Its `FAILED` ledger and private request survive;
there are zero rendered frames and no MP4. No memory override, reduced safeguard or
automatic retry was used. A later host snapshot showed recovered free pages, but is
not a render pass or authorization to retry. At that point fresh bounded retry
authorization and successful admission were needed; the subsequent authorized
retry and repair are recorded below. Scratch/evidence: `build/tabi-parallax.TQIWbn/`.

The four generation runs recorded seven WARNING samples in total, no sampled
CRITICAL pressure, and at most about 8.63 MiB per-run sampled swap growth. Finishing
and the refused render recorded NORMAL samples only. The receipt preserves each
run's full counts/peaks; RSS is not complete unified-memory usage. Owned runtimes
stopped and previous source/publication hashes were reverified unchanged.

The scratch harness uses the production importer, typed descriptor, job coordinator
and durable media stage. The application convenience scenery compiler still emits
one coverage plane; it was not used or represented as integrated three-plane UI.
VG2-03, moving-pixel review, 20–30-second continuity, blink and full-duration delivery
remain open. This standalone artwork preparation does not change the product's
externally finished-artwork input contract.

### Five-second parallax and memory repair — 2026-09-28

The user then authorized a retry and a solution without reducing quality if resources
still blocked it. The [five-second review clip](../build/video-test-archive-2026-09-28/previous-tests/tokyo-parallax-5s-2026-09-28/tokyo-parallax-5s-1080p.mp4)
now exists, with [MP4-decoded review frames](../build/video-test-archive-2026-09-28/previous-tests/tokyo-parallax-5s-2026-09-28/review-frames.jpg)
and [receipt/evidence](../build/video-test-archive-2026-09-28/previous-tests/tokyo-parallax-5s-2026-09-28/result.json).
It is **1920×1080, 150 frames, 30 fps, five seconds, silent H.264, square pixels**.
SHA-256: `3e104ea878eee9b4f4afbe76bb5c5b182495b44b4cb3dce156da33730a241aa9`.
The three prepared ComfyUI/RealESRGAN plates were reused: no additional inference,
upscaling, model, download, provider substitution or artwork regeneration.

The first same-limit retry passed admission but its Node process exceeded the
2,147,483,648-byte native ceiling at 2,159,640,576 bytes. It stopped after 23 PNGs,
without an MP4 or take. The whole-chunk synchronous render/write loop prevented
native-finalizer/event-loop service between frames. VG2-05 adds a macrotask yield
between PNGs, retaining identical drawing and three-sample shutter calculations.
Regression tests also cover asynchronous cancellation, including cancellation on
the final yield before receipt publication. Limits and publication safeguards were
not weakened. The two original regressions and final-yield regression failed before
their repairs; all 27 Node checks now pass, alongside focused JVM boundaries.

The first repaired run succeeded in 81,384 ms. A final bounded run after the
callback-cancellation guard succeeded in 72,973 ms, binding evidence to the exact
final renderer. Both are retained as separate **UNREVIEWED** immutable takes; no
accepted output or failed ledger was replaced. Scratch:
`build/tabi-parallax-retry.2Ixv0Z/`, `build/tabi-parallax-fixed.cxfiro/`, and
`build/tabi-parallax-final.ScZPRv/`. Final Node sampled peak: **448,675,840 bytes**
(about 428 MiB); sampled JVM-plus-children peak: 1,295,302,656 bytes. All 38 final
samples were NORMAL with no sampled swap increase. The previous successful run
sampled a 457,244,672-byte Node peak. RSS is not full unified-memory usage; sampling
can miss peaks, and the unchanged production supervisor owns enforcement.

Quality-preservation evidence is concrete rather than a lowered-resolution workaround:
all 150 final source PNGs match the first successful repair byte-for-byte, and the
first 23 match the unmodified renderer's retained partial frames. Each frame exactly
preserves 1,351,577 pixels outside the main-window mask from the once-resized original.
Selected unobscured start/end patches measure the intended 60/120/180-pixel leftward
far/middle/near shifts. All 150 rendered and MP4-decoded frames are distinct; full
decode and presentation timestamps verify cadence and duration. This proves rigid
scenery motion and fixed source artwork, not automatic appearance or action control.
H.264/yuv420p remains lossy; the receipt records compression error separately. The
MP4's color matrix is unspecified, so it is not a color-certified master.

Selected decoded stills were inspected, not substituted for normal-speed user review.
Perceived depth/speed, shimmer, matte edges and artistic acceptance remain open.
Only the large right window moves; small left panes stay original. The kit's generated
signs and recurring shop motifs remain limitations. The first preflight pin check
noticed externally changed Finder `.DS_Store` metadata; that non-artwork exclusion is
explicitly recorded. Actual supplied images, prepared layers, previous media and
failed-run evidence were reverified unchanged. Full test/build/diff gates and exact
final source/docs pins are retained with the scratch validation logs.

This closes the narrow resource repair, **not VG2-03 or VG6 visual acceptance**.
The convenience compiler still emits one coverage plane; this production-service
harness is not integrated three-plane UI. Review this five-second output before
20–30-second continuity, test blink separately, then consider combining them.
No longer run, rights/commercial clearance, release or full-duration approval is implied.

### Twenty-second faster Tokyo test (2026-09-28)

After reviewing the five-second clip above (SHA-256
`3e104ea878eee9b4f4afbe76bb5c5b182495b44b4cb3dce156da33730a241aa9`), the user said
“i like the parallax,” but found the movement slow and the background tower unlike
the other artwork. They requested a longer test with more passing scenery. This
approves the parallax direction, **not** the existing tower, speed or final release.
The follow-up is now rendered: [20-second MP4](pictures/video/tests/tokyo-parallax-20s-1080p.mp4)
and [decoded review stills](../build/video-test-archive-2026-09-28/previous-tests/tokyo-parallax-20s-2026-09-28/review-frames.jpg).
SHA-256: `30364b7e3a50b88ce217504f5f7e7805c7fa07f1cd16e113f7f12be0e30d20f7`.
It is silent 1920×1080 H.264/yuv420p, square pixels, 600 frames at 30 fps, 20 seconds.
Far/middle/near travel is 480/960/1440 pixels: **1.99× the earlier speed**, with
three shutter samples and no loop, stretch, repeated-frame fill or interpolation.
Only the large right window moves; TABI/cabin and smaller left panes remain fixed.

Three wider **3072×512 native ComfyUI drawings** supply the scenery, using the
installed Klein FP16-dequantized derivative/Qwen/VAE route. These are new paintings,
not extraction of the approved flat panorama. The illustrated panorama supplies
style; `inspiration-tokyo/IMG_1843.webp` supplies Tokyo Tower structure only.
The first far candidate clipped the antenna. The single admitted artistic correction
is selected, with a complete red/ivory illustrated tower. Its native x=0..1600 crop
excludes an unwanted secondary distant tower outside the intended view. Selected
far/middle/near paintings each received one RealESRGAN x2 finish to **6144×1024**.
Aspect-preserving placement, measured chroma mattes and supplied-pixel coverage
checks precede rendering. The temple, clock tower, different roofs, shopfronts and
cherry branches pass at distinct depths; generated lettering remains unverified.

Local preparation caught chroma matting erasing part of a genuinely green roof.
An explicit roof-interior keeper restores the original source RGBA, not invented
pixels. Its first non-inset candidate was refused for including 75 key-background
pixels; the final four-pixel inset passes that unchanged guard. Two scratch
regressions demonstrate the old damage and reject admitting green backing. Original
paintings, faulty preparation and refused keeper remain retained. This is external
artwork preparation, not an in-app generation/extraction capability.

One production-service controlled attempt succeeded in **325,083 ms** through the
same renderer, with absolute chunks `[0,300)` and `[300,600)` and exactly matching
continuation state. The take `take-e9f8dce9-69e7-4160-88ee-a3cba71d28a8` remains
**UNREVIEWED**. All 600 lossless frames preserve **1,351,577 pixels outside the
window** exactly. Selected rendered patches independently measure the planned
motion. Full MP4 decoding verifies 600 distinct frames, uniform timestamps and no
audio. Exact pixel preservation is a source-PNG claim: H.264 is lossy (minimum
source-versus-decoded RGB PSNR 35.48 dB; maximum fixed-region temporal RGB MAE 2.62).
Color matrix is unspecified; this is not a color-certified master.

The renderer's sampled Node peak was **494,387,200 bytes (~471 MiB)** under the
unchanged 2-GiB native ceiling; all 162 render samples were NORMAL with no sampled
swap increase. Artwork generation had two WARNING samples in the middle-plane job
and about 3.27 GiB host-global swap growth; no sampled CRITICAL. RSS samples are
not complete unified-memory measurements or exhaustive peaks. The new request's
7-GiB staging / 1-GiB output budget was sized before rendering for 600 frames, with
the same 900-second native deadline and 10-GiB free-disk reserve. No resource failure,
automatic replay, model download, hosted/Codex request or production code change.

Receipts, pins, prompts, graphs, prepared art, decoded proofs and validation are in
`build/video-test-archive-2026-09-28/previous-tests/tokyo-parallax-20s-2026-09-28/`; complete source/decoded frame
sequences remain in `build/tabi-tokyo-long.58RtOJ/`. Prior source/publication bytes
were reverified, excluding only explicitly irrelevant Finder metadata.
This is a typed production-service harness, not integrated app/UI support. Review
speed, tower style, scale, variety, matte edges, occlusion and shimmer at normal
speed. This 20-second continuity proof does not establish restart recovery,
30 seconds, blink, 180–300-second delivery, rights or release acceptance.
Current scope/status remains VG2-03 in TASKS; no further generation starts without
a separately bounded continuation.

**Subsequent user feedback and blink scope (2026-09-28):** “this test look better”.
The exact 20-second hash above is the reference for the isolated blink study, not
a full-duration/release approval. The user authorized a short blink-only test and
cleaning `docs/pictures/video/tests/` to video results. Its 563 original files were
moved byte-for-byte to `build/video-test-archive-2026-09-28/previous-tests/`; only
the current MP4 remains at the review folder root. No source, previous video or
failed evidence was deleted; historical machine receipts keep their original
paths. `build/tabi-blink.0GtJyK/archive-{admission,result}.json` records relocation.
Current documentation links point at the new locations. That ignored archive is
local evidence and must be preserved when cleaning build output.

Blink preparation first needs matched eye artwork. TABI's pose, head, hands, clothes,
props and scenery stay fixed; only tightly bounded eyes may change. The current
compositor blends one supplied pose with the open subject, not a three-pose eyelid
sequence. Inspect candidate half/closed art before claiming natural lid movement;
missing alignment or controls block that next step, not grounds for whole-scene
I2V or invented approval. No integrated UI or in-app picture synthesis is added.

### Blink artwork and guarded render refusal (2026-09-28)

[Open/closed eye comparison](../build/tabi-blink.0GtJyK/prepared/pose-review.jpg)
is **static artwork preparation, not video**. Three local Klein still submissions
produced native 1024×1024 crops. The first closed candidate added unwanted eyelashes
and purple shading. The one admitted correction provides simple closed lid lines.
The half-closed candidate shifts iris/gaze geometry and adds lid-outline ovals;
it is retained but not selected or described as a matched pose. Only the corrected
closed crop was RealESRGAN-finished to 2048×2048, then aspect-preservingly resized
and confined to the original eye regions. No generated mouth, forehead, headphones,
body or background enters the scene. Local boundary colour matching and feathering
are disclosed in the preparation receipt; **2,064,229 outside-support pixels**
remain exactly equal in the static composition. Eye art remains UNREVIEWED.

An eight-second / 240-frame eyes-only test was prepared through the production
importer, typed descriptor, job store/coordinator and controlled media stage. It
selects the existing **open/closed pose crossfade**, not an articulated three-pose
lid animation. Head/body/props/scenery are fixed; no half pose is silently substituted.
The single admitted request was refused **before native launch**:
`Controlled memory admission requires 2147483648 free bytes; available capacity is 1619968000.`
The FAILED attempt is `attempt-74a82f7a-0b81-478a-9468-94249d8f565a`. There are
**zero generated PNG frames, no blink MP4 and no imported take**. A predicted blink
schedule is only unexecuted request data, not motion evidence. The prepared artwork
is retained for a fresh explicitly bounded retry without regenerating it. No memory
limit reduction, automatic replay, cache purge or unrelated process termination.

Three generation runs recorded six WARNING samples in total, no sampled CRITICAL;
maximum per-job sampled host-global swap increase was approximately 1.64 GiB.
Finishing and the refused render samples were NORMAL; all owned wrappers stopped.
Sampled RSS is not an exhaustive or complete unified-memory peak. This refusal is
an admission result, not a measured renderer peak or proof that an eight-second
blink cannot fit. Inputs, model/runtime pins and previous artifacts remain intact.

`build/tabi-blink.0GtJyK/` retains art, requests, failed ledger, resources and gate
receipts. Four scratch checks cover static-eye confinement, pose alpha alignment,
prior-source identity, archive preservation and zero-output refusal; focused JVM,
27 Node checks, full test/build and diff gates are recorded there. No production
code changed. The review directory still contains only the unchanged 20-second
MP4; static studies/logs remain outside it. Next: review the closed-eye art and
admit a fresh attempt only when memory permits. A true half-lid sequence remains
an explicit artwork/control gap, not a completed animation capability.

### Eight-second isolated blink retry (2026-09-28)

The user explicitly requested a retry. [Watch the eight-second blink test](pictures/video/tests/tabi-blink-8s-1080p.mp4)
— one blink around **3.5 seconds**, with head, clothing, hands, props and scenery
fixed. SHA-256: `e5d3376c9abf01ee820ccf93176a7e28b6db586acdacca8ab02f6cc34b5582c2`.
This is silent 1920×1080 H.264/yuv420p, 240 frames at 30 fps, square pixels and
uniform timestamps. No new artwork generation or RealESRGAN pass was needed:
the prepared open/closed pixels, control/seed, timing, runtime and encoding are
unchanged. A new private project/request preserves the original failed attempt.

Three preflight NORMAL samples reported 19.26–20.05 GB free before launch. The
production 2-GiB memory admission and native enforcement were not lowered;
900-second native deadline, 4-GiB staging, 1-GiB output and 10-GiB free reserve
remain. One attempt, `attempt-f6b5d9a2-a45a-4d5c-9079-18081219f2b6`, completed in
**106,792 ms**. Its take `take-f5ea025a-6f74-406b-9caf-9f63785d4087` is **UNREVIEWED**,
not automatically selected. Sampled Node peak: **302,628,864 bytes (~289 MiB)**;
55 NORMAL samples, no sampled swap increase and owned wrapper exited. Sampling is
not an exhaustive or full unified-memory peak.

All 240 lossless compositor frames preserve **2,064,229 non-eye pixels exactly**.
Frames 101–107 change; peak closure is frame 104 / 3.467s. There are eight distinct
source images including the original open-eye state and 233 intentional static
holds. This is an isolated action study, not 240 newly generated motion states or
a strategy for filling a long video. Full decoding separately confirms the stream,
cadence, duration and visible eye change. H.264 remains lossy: minimum RGB PSNR
35.48 dB, maximum fixed-region temporal RGB MAE 2.43; its 107 distinct decoded
images include compression differences, not additional motion. Color matrix remains
unspecified; this is not a color-certified master.

[Decoded blink details](../build/tabi-blink-retry.gc18u7/decoded-review/blink-decoded-review.jpg)
show the supplied closed lines at the peak, and **crossfade/iris ghosting in the
intermediate states**. This is the disclosed existing open/closed blend, not a
matched half-lid pose or anatomically articulated eyelid movement. Normal-speed
human review is still required; do not infer naturalness from pixel checks.

Scratch evidence lives in `build/tabi-blink-retry.gc18u7/`: exact request/quality
parity, source/decoded proofs, resources, ledgers and final validation. The scratch
comparison initially failed to account for new import timestamps/source locations;
a second check assumed a nonexistent metadata field. The already-authorized native
attempt started while those bookkeeping checks were corrected. Production pin and
resource checks remained enforced; the corrected comparison rehashes both import
descriptors, permits only verified metadata changes and checks all other values.
Three scratch checks reject pixel/control drift. No second native attempt or
production change followed. Focused JVM, 27 Node checks, full test/build and diff
gates are retained. Old artwork, archive and refused ledger were reverified intact.
Only the new MP4 was added beside the parallax result in the review folder.

This delivers the bounded retry, not blink approval, combined motion, three-pose
support, longer continuity, UI integration or release acceptance. Await review
before admitting any next render.

**Subsequent user review (2026-09-28):** “ok, the blink is well made, we can
continue with the next step”. This approves the blink in the exact eight-second
MP4 above for the next combined experiment. It is genuine scoped human approval,
not inferred from tests; the earlier UNREVIEWED receipt and take snapshot remain
historical and unchanged, with no automatic take selection. The existing
open/closed crossfade remains the implementation; approval does not create
three-pose control or approve an unseen combined/full-length result.

The admitted next step is one 20-second combination of these eyes and the existing
three-plane Tokyo parallax, retaining supplied artwork, speed, 1080p30, shutter
sampling and safeguards. Only eye-support alpha is removed from the fixed cabin
foreground/occlusion so the eye layer can be seen. No new painting, finishing,
head/body motion or longer render is implied. Review event and admission:
`build/tabi-combined.e6gsws/`. Combined motion still needs its own output and review.

### Twenty-second combined blink and parallax (2026-09-28)

[Watch the combined test](pictures/video/tests/tabi-tokyo-blink-parallax-20s-1080p.mp4).
SHA-256: `7136e45800aebc577e4529a36cc3007b05117199db611e6a3a76a7edb686970f`.
The approved blink method now accompanies the existing three-plane Tokyo parallax,
with blink peaks at **3.467, 10.467 and 19.4 seconds**. Head, body, hands, clothing,
props and cabin remain fixed. The earlier two videos remain intact; the review
folder contains videos only. [Decoded review sheet](../build/tabi-combined.e6gsws/decoded-review/combined-review.jpg)
and [eye details](../build/tabi-combined.e6gsws/decoded-review/eye-review.jpg) stay in scratch.

No artwork was generated, refinished or resampled. The supplied scenery planes and
open/closed eye images were copied byte-for-byte. Only the 9,371-pixel binary eye
support was cut from cabin foreground/occlusion alpha, letting the separately
rendered eyes remain visible; all RGB and other alpha pixels stay unchanged.
The eye and window supports are disjoint. Three preparation checks and the typed
production importer validate the layering. The seed/control from the approved
blink drives absolute-time pose blending across the 20-second request; the scenery
uses the same 480/960/1440-pixel travel, three shutter samples and 0.5 shutter fraction.
This is the same open/closed crossfade, not new three-pose articulation or whole-scene I2V.

One controlled attempt `attempt-81d482e9-5e4f-4963-8501-52a2dd73f33d` succeeded
in **331,992 ms**, producing take `take-1e4d9b40-4618-4f61-b56c-28b70bc4ec26`,
**UNREVIEWED** and not selected. Both absolute chunks `[0,300)` and `[300,600)`
carry exactly matching scenery continuation. The second blink occurs after the
chunk boundary on its original absolute clock, not a restarted local clock.
Full decode confirms 600 distinct frames, 20 seconds, 30 fps, 1920×1080 H.264/yuv420p,
square pixels and no audio. No loop, repeated-frame fill or per-frame AI redraw.

All **2,064,229 non-eye pixels in every source frame** exactly match the earlier
20-second parallax's corresponding source frame. All first 240 eye regions exactly
match the approved eight-second blink's source frames. Across all 600 frames,
**1,342,206 pixels outside eyes and window** remain fixed, and the eyes follow the
specified absolute blend within two RGB levels of independently computed rounding.
The 600 complete source frames are distinct because scenery keeps moving during
open-eye holds. H.264 is lossy: minimum RGB PSNR 35.48 dB and maximum fixed-region
temporal RGB MAE 2.62. Exact parity/preservation concerns source PNGs, not encoded
pixels. Color matrix is unspecified; this is not a color-certified master.

Sampled Node peak: **562,118,656 bytes (~536 MiB)**, under unchanged 2-GiB native
enforcement. All 166 render samples were NORMAL, with no sampled host-global swap
growth; the owned wrapper exited. Sampling is not an exhaustive/unified-memory
peak. The 900-second native / 1020-second wrapper, 7-GiB staging, 1-GiB output and
10-GiB free reserve stayed unchanged from the admitted 20-second configuration.
No resource failure, retry, download, provider change, ComfyUI or finishing job.

Exact inputs, human blink review event, preparation, requests, source/decode proofs,
resources and focused JVM/27 Node/three preparation/full test/build/diff gates are
retained in `build/tabi-combined.e6gsws/`. Original artwork, both prior videos,
archive and failed evidence are reverified unchanged; no production code changed.
This is a production-service harness, not integrated UI. Inspect the combination
at normal speed before a separately bounded 30–60-second coverage/recovery test.
Neither that longer test nor three-minute delivery, rights or release is approved
by this result or by the earlier isolated-blink approval.

**Subsequent combined-test approval (2026-09-28):** the user says “perfect, we can
continue with the next step”, referring to the exact combined MP4 above. This is
human approval of that combination, not of unseen longer footage or release.
Sealed historical take/project receipts remain unchanged; the new review event is
in `build/tabi-continuity.0lyIo2/`.

The admitted bounded test is 30 seconds at the same pixel speed. Measured coverage
finds previously unused near-strip pixels sufficient after a 480-pixel rightward
starting reframe; the initial near crop changes, but artwork is neither rescaled,
looped nor regenerated. All 900 frames/three shutter samples stay within supplied
pixels. Sixty seconds still needs additional scenery. The existing backend can
recover a sealed completed publication after process restart, but cannot resume
interrupted partial chunks. This run therefore admits one render followed by a
fresh-JVM completion-recovery/import check, not a mid-render crash/resume claim.

### Thirty-second continuity and completion recovery (2026-09-28)

Human-review draft:
[`tabi-tokyo-continuity-30s-1080p.mp4`](pictures/video/tests/tabi-tokyo-continuity-30s-1080p.mp4),
SHA-256 `abb990dab8542e30113fc0fe9ee89de5275e5c7c79167916f2f5ae4d755c81ea`.
Evidence: `build/tabi-continuity.0lyIo2/`. No new generation/finishing or production
source change. Prior three review videos and the563-file archive remain protected.

- One request/attempt,900 source frames, three300-frame absolute chunks; render to
  handoff504,265ms. Same speed/scale; near starting placement shifts480px right,
  exposing unused supplied pixels. Camera travel720.4007px, middle1440.8013px,
  near2161.2020px. No wrap, loop, stretching or repeated-frame duration fill.
- All900 source frames distinct;1,342,206 fixed pixels/error0. First600 eye regions
  exactly match the approved combination. First599 frames'254,400 top-window pixels
  exactly match prior far/middle motion. At frame599 the previous20-second endpoint
  clamps its forward shutter sample, whereas this continuing shot correctly does
  not:9,344 top-window pixels differ (max27RGB, mean0.094RGB). A scratch comparison
  initially rejected this; `check-endpoint.cjs` verifies the precise endpoint cause.
  No renderer change or replay. Near patch movement0→150:361px versus360.601px
  expected. Blink peaks3.467/10.467/19.4/23.733s, same open/closed crossfade.
- **Real process restart, limited to completed-output recovery:** JVM5988 stopped
  normally after sealed media completion, before coordinator success/take import.
  Fresh JVM9882 recovered the same attempt in11,865ms and imported one UNREVIEWED,
  unselected take. All900 source PNGs, MP4 and completion receipt remained unchanged.
  Fail-closed renderer/encode launch guards reported zero calls. This is not an
  abrupt crash, interrupted-chunk resume, application UI or full-duration proof.
- Production media validation and full probe passed:900 frames,30s,1080p/30fps,
  H.264/yuv420p/SAR1:1, no audio, uniform512-tick PTS at1/15360 time base.
  **Additional independent PNG extraction hit its unchanged180-second deadline.**
  It retained893 complete/readable/distinct decoded frames0–892 (1,956,788,121bytes),
  all inspected against source. Frames893–899 have not received that pixel review.
  No automatic decode retry or limit increase. The MP4 is a review draft, not a
  fully validated or accepted release artifact. Color matrix remains unspecified.
- Partial-prefix metrics only: minimum RGB PSNR35.2817dB, maximum mean RGB
  error2.8130, maximum fixed-region temporal RGB MAE2.6156. These are not metrics
  for all900 encoded frames; exact preservation claims concern source PNGs only.
- Render250 NORMAL samples, Node peak564,723,712bytes; owned-tree peak1,532,084,224.
  Recovery8 NORMAL samples, owned-tree peak949,551,104bytes, no sampled Node process.
  No sampled swap growth. Sampling is not exhaustive/unified-memory measurement.
  Native memory remains2GiB; render900s/wrapper1020s, recovery wrapper240s,
  staging7GiB/output1GiB,10GiB free reserve. Review2GiB/180s/3GiB stays unchanged.
- `check-dispositions.json` preserves the prelaunch encoded-byte-count comparison,
  endpoint assumption, review log-name collision and wrong scratch metadata lookup.
  The latter two stopped before decoder launch; one FFprobe and one FFmpeg PNG
  extraction were actually executed. After the decoder timeout, no native retry ran.

**Still pending:** normal-speed human review of this draft and fresh authorization
for bounded completion of the remaining independent decoded-frame check. Software
focused/full/build/diff gates are recorded separately in scratch; passing them does
not erase the media-review timeout. More scenery is required for60 seconds; no
180–300-second, mid-render recovery, reusable action kit, UI, rights or release
acceptance is established. This video remains silent and independent of MIDI.

**Subsequent user feedback (2026-09-28):** “I think is a great result, just a thing
not from this run, around Tabi leaves in the window are not crop correctly. And
then you can start with the next step, don't regenerate this video”. This is a
positive review of the exact30-second MP4 with a known pre-existing mask issue,
not an assertion that the cutout is correct or that release gates pass. Playback
speed was not explicitly reported. The video remains unchanged; no take is
silently selected and historical sealed review states are not rewritten.
`build/tabi-tail-review.rT83Xd/user-review.json` records the complete scoped event.
VG1-03 tracks localization and correction of the window edge around TABI's
leaf/frond silhouette in future preparation only, without redrawing the character
or regenerating this video. Exact defective pixels/root cause still need inspection.
The admitted immediate next step is one bounded read-only tail decode with verified
overlap to complete the missing seven frame checks, under the same native limits
and a3-GiB decoded-staging cap counting both old and new files.

**Tail-review completion (2026-09-28):** fresh evidence in
`build/tabi-tail-review.rT83Xd/` completes independent decoded-pixel coverage of the
unchanged MP4. One read-only output-side seek to29s extracted frames870–899 in
9.885s; all23 overlap frames exactly match the earlier decoded RGB, and seven new
frames complete900 distinct decoded frames. No video was rendered or encoded;
no art, masks, source PNGs, take/project records or old timeout receipts changed.
The existing full900-frame probe remains pinned rather than repeated. Review:
[decoded tail](../build/tabi-tail-review.rT83Xd/review/tail-review.jpg).

Full900-frame metrics remain PSNRmin35.2817dB, RGB MAEmax2.8130 and fixed temporal
MAEmax2.6156. New staging67,031,353bytes; aggregate with the preserved prefix
2,023,819,474bytes, below the unchanged3-GiB cap. Native2-GiB/180-second guard and
10-GiB free reserve retained; wrapper10.290s of215s. Six NORMAL samples, no sampled
swap growth; FFmpeg sampled peak69,795,840bytes. Sampling is not an exhaustive peak.
Three scratch regressions check overlap/ordinal/gap/repeated-frame failures; current
software gates are in the new scratch final-gate logs. VG2-03 is now REVIEW for
technical integration assessment, not waiting on this decode or repeated user
feedback. **The reported leaf/window-mask defect remains open under VG1-03.**
Positive direction approval and complete decoded coverage do not resolve that
visual defect, establish playback speed, or approve a release. Source-mask repair
for future footage is next; this video must not be regenerated. Longer duration,
partial-render recovery, integrated UI and rights remain separate gates.

### Leaf/window mask correction (2026-09-28)

User authorization: “ok, correct it”, referring to the source-mask correction for
future footage, not regeneration of the existing video. New bundle:
`build/tabi-leaf-mask.ey9rmD/prepared/`.
[Before/after still comparison](../build/tabi-leaf-mask.ey9rmD/prepared/review/leaf-mask-comparison.png)
shows start/middle/end scenery positions and light/dark/green diagnostic backdrops;
these are **static preparation composites, not rendered or decoded video frames**.
[Full starting still](../build/tabi-leaf-mask.ey9rmD/prepared/review/context-start.png).

Inspection against the supplied1664×936 original found an oversized, coarse gill
polygon inherited from the earlier still study. It retained a wedge of original
exterior above the small right-side fronds and missed part of the middle frond's
dark outline. The still-study code itself described that mask as not animation-ready;
this is a preparation defect, not a reproduced compositor bug.

A curated cubic silhouette follows the original outline. The patch is confined to
viewport box `[854,358,910,486]`;1,164 mask pixels change (960 release exterior,
204 increase foreground protection). The mask is rasterized locally at8x native
resolution, resized to the existing viewport and snapped only at <=3/>=252 to remove
sub-1.2% alpha ringing. No RGB painting, new leaf geometry/artwork generation,
model execution or whole-mask erosion. Original RGB is retained byte-for-byte in
foreground/occlusion layers; all eye/scenery assets remain exact. Window bounds
are unchanged, and2,072,436 pixels outside the changed mask remain identical in
paired static compositions. Foreground and occlusion alpha are updated together,
with the existing9,371-pixel eye cutout preserved and no double feathering.

The bundle contains the corrected window mask, cabin foreground/occlusion and
starting composition plus unchanged required artwork. A new private production
asset/prepared-scene import passed in5.32s, with measured alpha matching actual
pixels and all eight consumed references matching prepared hashes. No job, take,
video render/encode or current-project update occurred. Six scratch checks cover
background removal, clipped-outline restoration, exact unaffected/RGB pixels,
eye cutouts/single antialiasing, geometry rejection and exact static starting crop.
The old masks fail the first two; the initial candidate's alpha-ring and Pillow
identity-transform failures are preserved. Its one correction passes without
weakening assertions. Software final gates are retained alongside the bundle.

**Status: VG1-03 REVIEW.** Agent still inspection and pixel tests do not supply
human visual approval or corrected-motion evidence. Existing videos—including
30-second SHA `abb990dab8542e30113fc0fe9ee89de5275e5c7c79167916f2f5ae4d755c81ea`—
remain unchanged and intentionally retain their historical mask. Future renders
must bind the new assets/descriptor rather than reuse old request fingerprints.
No production code, optional setup, MIDI, archive or release policy changed.

### Corrected-mask acceptance and 60-second prerequisites (2026-09-28)

Subsequent project-user decision: “ok, we can continue with the next step”, in
response to the corrected-mask still delivery. This accepts the new source bundle
for the next step; it is not a detailed per-edge review report. The append-only
scoped event is `build/tabi-next-preflight.iMUH0P/mask-user-review.json`, binding
comparison SHA `1637fcf810064a5a254f98617718f62065a98c18f27fb95b34f5c3dba17a0f35`
and preparation SHA `d63fa5d92d9d2ff8276287373a7f911b91d09265c480e15655def9eabd37b99f`.
VG1-03 closes for source preparation. The original sealed PENDING review state
remains historical; no take is selected and no corrected video has been rendered.
The existing30-second MP4 and other three review videos remain unchanged.

A read-only extent check evaluates the same480/599 camera pixels/frame and three
shutter samples over1,800 frames; production `cameraAt` independently corroborates
the arithmetic. It does not invoke the renderer or claim new moving evidence.

| Plane | Existing prepared width | Minimum width for60s at current placement | Missing horizontal pixels |
| --- | ---: | ---: | ---: |
| Far | 2,500 | 2,582 | 82 |
| Middle | 4,800 | 4,404 | 0 |
| Near | 3,648 | 5,765 | 2,117 |

The near source extent ends at frame919 (~30.63s); far ends at1698 (56.6s).
Another integer starting reframe alone still cannot fit: minimum far/near widths
would be2,503/5,386. These widths are geometric lower bounds, not seamless artwork
or full alpha/occlusion validation; filtering/overlap margins require extra space.
Check unused supplied pixels first; any new artwork must preserve the approved
style and scale. Do not stretch, repeat, slow down or count transparent padding as
scenery. The middle layer can be retained subject to complete admission checks.

The prior30-second media attempt measured504.265s. Rough linear scaling gives
1,008.53s for60 seconds, above the unchanged900s whole-attempt deadline. This is
not a measurement or a guarantee of failure, and does not authorize a cap increase.
Existing300-frame invocations share that deadline. Completed-output recovery is
not partial-render resume; continuous planning/checkpoint integration remains
VG4/VG5 work. A safe longer run needs those prerequisites, adequate artwork and
separately bounded admission rather than blindly doubling the old request.

Evidence: `build/tabi-next-preflight.iMUH0P/coverage-result.json`, seven extent checks
and `camera-proof.json`. The initial camera comparison failed because exact contact
at frame1697+1/6 rounds just outside the far edge in double precision. That failure
is retained; sub-nanopixel contact error is recorded separately from missing art.
The rational far deficit begins at1698. This comparison tolerance changes no
production coverage guard and grants no render admission. Fresh focused, Node,
full test/build/diff outcomes are retained in this root's `final-*.log` files.
No model/media job, new asset import,
new take, provider/runtime change or production edit was made. Rights, full-motion
quality, application delivery and release remain separate gates.

### Scoped continuous-plan preparation dependencies (2026-09-28)

After “ho ahead with this.”, VG4-03 implements one bounded prerequisite to longer
rendering in `VideoAssemblyPlanner.kt`, not a new video. The planner previously
bound all preparation tools to every chunk, even when a chunk did not consume
the affected component. Planning requests can now explicitly assign each
preparation dependency to existing component keys; shared or unspecified ownership
remains scene-global. A supplied declaration set must account for every dependency
exactly once, and empty, duplicate, unknown or wrong-kind targets reject.

Resolved pins use the existing assembly dependency lists. Unused component changes
preserve work fingerprints; pose/effect changes affect their action's consuming
chunks, including temporal support and particle tails. Base/shared dependencies
still invalidate globally. Version, SHA and project-owned artifact location remain
bound; completed takes retain their original identity. Ownership is a typed caller
input, not inferred metadata or an integrated UI control. Full artifact-verified
readiness and plan persistence remain unfinished.

Planner semantics are version 2 (assembly schema remains 1). Both version tags are
mandatory in serialized assemblies; old/missing identities fail closed, without
migration or rewriting media/prepared scenes. The current controlled-render request
and prepared-scene formats are unchanged. Evidence in
`build/vg4-dependency-scope.s6Nk20/` includes three dependency regressions failing
before the fix, a separate missing-version regression, all 30 passing planner
checks, 27 Node tests and focused/full test/build/diff logs. The initial JSON test
fixture's omitted-default failure is retained rather than hidden.

This closes VG4-03 only. VG4-01 still needs persistence/application integration and
replacement of short-shot planning; VG4-02 and VG5 still own continuous execution
and checkpoint/resume. There is no new 60-second output, scenery preparation or
native recovery claim. All four review videos and the accepted leaf-mask bundle
remain unchanged; limits were not increased and no model/media job or commit ran.

### Continuous-plan proposal persistence (2026-09-28)

After “go for it”, VG4-04 completes a bounded persistence prerequisite split from
VG4-01. `VideoAssemblyStore` saves the existing immutable proposal rather than
introducing a second planning format. Descriptors live at
`assemblies/<id>/v<version>/assembly.json`, bounded to 1 MiB, with assembly schema 1
and planner 2 unchanged. Project `assemblyVersions` records pin the descriptor,
prepared scene, finished original and provenance fingerprint. Source text, exact
frame clock/seed, action schedule, support ranges, dependencies and chunk/work
fingerprints survive independent store reopening at 180, 240 and 300 seconds.
These are synthetic planning tests, not generated footage.

Prepared-scene and assembly publication share `VideoProjectStore`'s existing lock,
optimistic revision guard, confined path checks and atomic document publication.
Old records cannot be changed or removed, including via ordinary project saves.
Saving and reopening verify actual prepared-scene/image facts and source identity;
hashes or filenames alone do not bypass that validation. Stale/concurrent writes,
symlinks, foreign projects/sources and descriptor/record drift reject. A descriptor
left by failed document publication is retained; only its exact bytes may be reused,
never overwritten with a different proposal. This is descriptor publication recovery,
not renderer checkpoint or abrupt-crash recovery evidence.

**Current Video project schema is now 5.** Prior schema-4 projects, including private
historical test projects, reject without migration. Their documents, media and sealed
receipts remain untouched; there is no claim they reopen through the new store.
Unsupported/missing assembly versions also reject. MIDI, prepared-scene, renderer,
job/request formats and resource limits are unchanged. Persistence does not confer
executable readiness, artistic approval, take acceptance or selection.

Evidence: `build/vg4-plan-store.7MaJjG/`. All 14 new persistence tests, existing
prepared-scene/project tests, 30 planner tests, focused checks, 27 Node tests, full
JVM tests (789 root +239 desktop, zero failures/errors/skips), build and diff checks
pass. No check failed or implementation repair was needed; an additional decoded
image-facts test extends the initial 13-case pass. Post-documentation checks and
protected/source hashes are separate receipts in that root. No model or real media
job ran, no take was created, and the four review videos and accepted corrected
mask remain unchanged. VG4-01 still owns caller integration and retirement of
short-shot/repeat planning; VG4-02/VG5 own continuous execution, checkpoints and
complete export. Scenery extension and a separately admitted longer run remain
necessary; this step produces no new motion or integrated Video UI.

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

Follow the [production-first workflow](#production-first-workflow-2026-09-28):
validate scene-compatible character motion and rigid scenery separately, combine
at 60 seconds, then produce/review the full pilot before general app integration.
Use the external Node/Canvas compositor for supported continuous motion. The
selected ComfyUI/LTX route remains a separate measured short-I2V option consuming
one composed image, not independent layers or proven long-form action control.
No model download, provider switch or hosted upload is automatic.

The product remains one continuous 180–300-second scene at 30 fps, default 180.
Reuse of small motion patterns is allowed; whole-clip repeat-to-fill is not.
Validate complete trajectory/shutter coverage before a full render and carry
absolute state/seed through bounded chunks. Record fresh action footage,
procedural motion and reused components without summing overlapping layers into
false unique seconds. Short success never proves full-length quality.

The existing 5/20/30-second evidence is retained, not automatically rerun. New
character/scenery changes receive scoped moving tests and the pilot's explicit
human gates. Optional look/take refinement in the eventual app is unchanged;
unreviewed is not approved, rejected work is not silently selected, and AI seeds
are traceability inputs rather than promises of deterministic model output.

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

First establish the production recipe outside the unfinished app: VG2-07 reviews
the isolated character test, VG4-05 records the scenery-join decision, VG5-05
reviews the combined 60-second proof, and VG5-07 reviews the full 180-second pilot
and editor handoff. Record exact artifacts, reviewer/date, normal-speed observations
and unresolved defects. These decisions unlock later app work, not release, rights
or new unseen clips. None is inferred from the existing blink/parallax approvals.

For the later **app** gate, a real user reviews three real 20–30-second clips from externally finished
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
