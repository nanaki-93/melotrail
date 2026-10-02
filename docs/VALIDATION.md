# Validation and evidence

Owner: automated checks, real listening/visual acceptance and Logic Pro evidence.
Implementation status is in [TASKS-AUDIO](../TASKS-AUDIO.md) for
[PLAN-AUDIO](../PLAN-AUDIO.md) features AC1–AC5 and
[TASKS-VIDEO](../TASKS-VIDEO.md) for [PLAN-VIDEO](../PLAN-VIDEO.md) features VG1–VG6. Older task IDs and dated status statements below label retained evidence,
not executable queue rows or current-build approval. Do not replay them. The old
participant/holdout queues are superseded; incomplete gates are not passes.

## Non-interactive validation

Current policy: required validation is **headless and non-interactive**. This
supersedes every older requirement below for a visible/capturable desktop, native
window replay/resize, GUI installation/startup smoke, live user journey or Logic/
Apple-editor import/play session. Those gates are removed, not passed. Retain
historical procedures, failed captures, receipts and media as evidence only; do
not replay them. A later explicit user request is required for any UI session.

Keep unit/integration, CLI/file/media, package-inspection, offscreen rendering and
presentation/keyboard/accessibility-semantics checks. The app UI is still part of
the product. Do not call offscreen results live usability, installed-GUI startup
or editor-compatibility proof. Human listening, artwork/motion/mockup decisions
and release approval remain based on supplied artifacts; agents neither open a
player/editor for these nor fabricate the user's decision. Headless media/model
work retains all existing authorization, resource and no-overwrite safeguards.

The current default `:desktopApp:test` includes `MidiCoreNativeResponsivenessTest`,
which opens a real window even without screen-capture mode. Do not run unfiltered
`make test`/`make build`. For the currently inspected tree, use a temporary Gradle
init script to exclude that class and enforce headless JVM tests, without changing
tracked build wiring or deleting tests:

```bash
HEADLESS_INIT=$(mktemp "${TMPDIR:-/tmp}/melotrail-headless.XXXXXX")
printf '%s\n' \
  'allprojects {' \
  '  tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {' \
  '    systemProperty "java.awt.headless", "true"' \
  '    exclude "**/MidiCoreNativeResponsivenessTest*"' \
  '  }' \
  '}' > "$HEADLESS_INIT"
make GRADLE="./gradlew -I $HEADLESS_INIT" test
make GRADLE="./gradlew -I $HEADLESS_INIT" build
git diff --check
```

Inspect new/changed test wiring before reuse; do not enable live-E2E environment
flags or invoke native capture/install/desktop launch tasks. Report the excluded
class and actual executed/cached results; this is a filtered headless pass, not a
pass of the original unfiltered suite. If another test needs a window, stop and
scope its headless replacement or disclose its exclusion. Test names containing
UI/Native do not alone justify exclusion: receipt and offscreen tests stay useful.
AC4-02 and AC5-05 are retired from the audio queue, not completed. AC5-04/06 and
VG3/VG6 retain only the non-interactive validation scope in the current queues.
This documentation change does not remove test sources or implement new runners.

## Production-first video gates (2026-09-28)

Updated 2026-10-01: prove a standalone coherent 2D/2.5D rig, two readable actions
and description-driven short reuse before minimum production binding. Then prove
a complete Tokyo film and a second-city method check before app functionality. The user's
selected series keeps TABI in the train, changes the city exterior and gives each
episode a materially different small-activity sequence. The roadmap is
[PLAN-VIDEO](../PLAN-VIDEO.md#6-step-by-step-delivery-order); dependencies/status remain in
[TASKS-VIDEO](../TASKS-VIDEO.md#production-first-priority-2026-09-28), with
[recipe/current evidence](TABI_VIDEO.md#tabi-train-series-recipe-2026-09-30) in TABI.
Fixture-proven control and static-look approval are not actual-kit or moving passes.
This update grants no artwork/model/media budget, live admission or UI permission;
new review decisions below remain pending and generic projects remain supported.

| Production gate | Required evidence and real decision |
| --- | --- |
| VG2-12–18/07 · Rigged wave | Approve coherent parts/neutral, prove rig support/joints and guarded colour/media handling, then produce one newly admitted five-second/150-frame wave. Fully decode; user reviews normal-speed identity, arm volume/texture, joint attachment, props/frond/contact and return. No whole-arm swaps, ghost fades or static-only acceptance. Historical VG2-08/11 failures stay blocked; no production import in this standalone gate. |
| VG2-09/10 · One additional activity | Validate only the selected small non-blink motif's compatible inputs/control and actual contact/entry/return, produce its separately admitted moving proof and record exact user review. A static reading/listening pose or unsupported sip/page turn is not a second demonstrated activity. |
| VG4-05 · Scenery join | Validate window/frond/foreground mattes against the actual cabin and every allowed pose; the old fixed-head mask is not automatically reusable. Prove a moving offscreen handoff with matching overlap, rigid travel, no seam/hole/wrap, whole requested/shutter coverage and supplied 60-second extents. Account for all selected exterior apertures. Record the user's seam decision; static art/width alone is insufficient. |
| VG4-08, VG2-19/20 · Standalone description/reuse | Two supported scene descriptions produce different 20–30-second action sequences using unchanged rig/art/action versions, without manual keyframe edits. Bound scenery coverage; reject missing actions/props/viewpoints, ambiguity and conflicts. User checks both exact clips against the descriptions. This is not production/import/full-film/app proof. |
| VG4-07/09/02 · Executable episode | After short proof and explicit runtime choice, persist/compile the proven rig/action semantics through existing owners; pin activity choice/order/timing/rests/returns, rig/parts/capabilities/versions and scoped dependencies. Prove production import/support, exact process/decoder ownership, absolute chunk/state parity, input invalidation and 180/240/300s fixtures. Do not pretend the held-pose contract already supports a rig. |
| VG5-03/05 · Combined 60 seconds | Scene/pose-compatible masks, two reviewed non-blink activities/returns, deliberate quiet intervals and extended Tokyo scenery in one silent 1080p30 result. Fully decode/check timing/action/scenery/chunk joins, measure stages and aggregate resources, then record normal-speed review. Prove restart after a verified completed chunk while later work remains, not reconciliation of a finished MP4. |
| VG4-06, VG5-06/07 · Complete Tokyo pilot | Complete supplied corridor/alpha/shutter coverage and a newly admitted 180s/5400-frame batch. Verify continuous moving parallax, distinct passing views about every 5–10 seconds with quiet travel, and the pinned varied two-activity schedule inside the fixed cabin. Isolated tests do not approve long holds/loops. Fully decode; inspect timestamps/first/last/actions/all joins and disclosed reuse. User reviews the supplied whole film without a required editor session. No production-integration/release pass follows. |
| VG5-08/09/10 · Different episode proof | After full-film review, prepare one selected second-city pack/script as new pins, validate every visible exterior aperture and any new motif, then produce/review a separately admitted 20–30-second reuse clip through the same owners. Change exterior plus activity choice/order/timing/quiet intervals, retaining compatible cabin/TABI/props. A city rename/reseed or Tokyo-art substitution fails; no regenerated/overwritten first film. This is not second-city full coverage/film approval. |
| VG6-05/06 · Full next-city production film | After separate headless app-integration/early/package gates, validate the selected second city's complete corridor/sequence/contact, admit a new full batch, produce/decode one real 180–300s export through production services and obtain whole-artifact review. No live UI/editor test. The short reuse clip, first harness film and planning arithmetic cannot substitute. |

Keep source, rendered lossless PNG and decoded H.264 claims distinct. New motion
may require new support/mattes and clean plates; declare those regions rather than
weakening the old fixed-pixel checks or claiming the old mask follows a moving head.
Use one declared single-pass alpha policy for replacement poses. Preserve approved
29 and all old comparisons; a changed neutral/composed appearance needs an explicit
new artifact-specific baseline decision, not a loosened threshold or hidden alpha
switch. For moving backgrounds, check the neutral cutout and time-appropriate
composition/contact, not equality of entire first/last frames containing different
scenery. Bind new derivatives to new descriptors/requests in a fresh current-schema project.
Never regenerate/overwrite the approved 30-second video, replace historical inputs,
migrate old projects or rewrite sealed review/timeout receipts.

The minimal runner must reuse existing intent/claim/job/process ownership and
verify completed chunk hashes/state before reuse. Retain incomplete/uncertain work;
no automatic retry or partial-chunk/abrupt-crash recovery claim without proof.
The pinned FFmpeg has image2/H.264, not concat. Validate the numbered PNG sequence
and final output; a different assembly/tool route requires separate testing.

VG2-06 technical character output (2026-09-29): after one memory-only
pre-launch refusal, a separately authorized retry sealed a 217-frame isolated
breathing MP4. Corrected, separately admitted independent source/full-decoded
checks and production take import passed; exact review copy SHA-256
`b73b6490a9fc4db98cd5b0581e46ac74e9a556f2941567253c778c0030af7bcb`
at `docs/pictures/video/tests/vg2-breathing-cropped-217-20260929.mp4`.
The 7.233-second 1080p30 H.264 file has one video stream, zero audio streams,
uniform PTS and one unselected UNREVIEWED private take. See [TABI evidence](TABI_VIDEO.md#vg2-06-request-gate-fixture-only-2026-09-29)
and the ignored `build/vg2-character-request-20260929/live-217/` receipts for
initial receipt-name/import-argument refusals and subsequent verification.
These are engineering checks, not VG2-07 normal-speed motion/edge/contact review.

VG2-08 static wave candidate response (2026-09-30, project user): “i like the
candidates, you can continue with it” follows delivery of transparent wave
`train-actions/36-wave-fixed-lower-alpha-review-candidate.png` (SHA-256
`e0d146605258fa1c200c08df4c29d403c4492bf19beec0ecfcfb0bcb3a88e1b7`)
and its cabin review `37-wave-fixed-lower-review-candidate.png` (SHA-256
`d503b6c65c83091de42cd9b9fd7909906aa824fe86e7d60d504145fa5f46444a`).
This permits further static artwork work on that look; it does **not** approve
a nonexistent registered midpoint, neutral return, action motion or render
budget. Production-order static compositing preserves the neutral lower-body
pixels within two channel levels, but full moving contact and an executable
non-ghosted pose transition remain unproven; the two new transparent midpoint
edits and the local warp were rejected on visible registration/seam defects.
There is no new video, import, take or VG2-07 action acceptance. The user's
subsequent inspection notes that 36/37's hand does not differ in position
from 34. Confirmed: 36's upper-hand pixels equal its source wave guide 35
exactly throughout [835,430,960,570]. Only the seated lower body was replaced;
37 is a static composite. Neither asset is a distinct midpoint or proof of
neutral-to-wave motion; the user's favorable look response must not be cited
as such.

Subsequent user-requested image preparation supplies a distinct **unreviewed
static midpoint**: `train-actions/40-wave-halfway-seated-alpha-candidate-v2.png`
(SHA `7039302f…252d11e`), scene review `41-…-v2` (`1e24238c…4f2e72c`)
and comparison `42-…-v2` (`022343e6…63d5619`). Scratch
`build/vg2-wave-assets-a1/asset-proof.json` checks changed hand pixels and
exact lower-body/outside-action preservation; its negative witnesses reject
35/36 as midpoint substitutes. This is not user approval, complete moving
registration, production pose/control delivery, a rendered take or VG2-07
acceptance. See [TABI artwork continuation](TABI_VIDEO.md) for precise assets,
generation/postprocessing and remaining limits. No new video is authorized.

**Subsequent static midpoint decision** (project user; recorded
2026-09-30T02:04:42Z): “yes, it looks right” approves the halfway appearance
shown by 40-v2, 41-v2 and 42-v2 above; their full SHA-256 pins in TABI video
were rechecked unchanged. This closes that static-look wait only. It is not
approval of unversioned drafts, complete moving registration/contact/return,
an implemented pose control, unseen footage, automatic take selection or a
native render budget. No new generation or video execution is admitted.
VG2-07 still requires review of a future revised moving artifact.

**Subsequent admitted fixture-only control work (2026-09-30):** the user says
“ok continue with it”. `POSE_REPLACE` now holds supplied registered cutouts at
explicit absolute frames with neutral entry/return, no cross-dissolve or
invented interpolation, alpha applied once and unchanged legacy blink behavior.
Production-imported synthetic fixtures pass exact return, planted lower pixels,
new hand support, foreground occlusion, translucent-edge and split-range checks.
Timing/pose dependencies are fingerprinted; unsafe/missing/rejected poses,
misregistration and conflicting controls are rejected. Runtime manifest 4/tool
1.2.0 and the strict Kotlin receipt consumer agree; old/unknown receipts reject.
Focused JVM, Node motion/scenery 31/31, complete `make test`, `make build` and
diff checks pass after the two bounded repairs recorded in TASKS-VIDEO.

Actual TABI input readiness still fails. Scratch
`build/vg2-wave-control-a1/static-kit-proof.json` checks 29 protected plus three
approved v2 pins unchanged and exact lower **source RGBA** at y≥760. However,
neutral→halfway differs above 8/255 at 80,646 head/frond, 20,049 eye and 2,685
mouth-region RGBA pixels; halfway→wave has zero changes in those regions. These
partly overlapping regions are separate comparisons, not summable unique pixels.
Neutral's legacy-alpha crop also fails single-pass approved-29 neutrality at
3,017 pixels above 8/255 (max 46), while legacy self-clipping matches within two
levels. Published halfway/wave lower cabin pixels differ from 29 at 384 pixels
above 8/255 (max 21) despite identical source lower RGBA. These are retained
rejection witnesses, not grounds to loosen the neutral baseline or mix alpha
policies. A matching-head, consistent-alpha kit and actual-kit import/contact/
return proof remain necessary. VG2-08 is BLOCKED and VG2-07 WAITING_USER. No
real-artwork render/encode/decode, new model call, take selection or video
replacement is authorized or claimed; future footage still needs its own budget
and real user motion review.

**New matching-kit static appearance decision** (project user; recorded
2026-09-30T13:00:40Z): **“i approve the new assets”**, responding to the new
neutral→halfway→wave comparison and the request to approve its left-column
neutral as the return baseline. This approves static kit 45/46/47 and explicitly
selects composed neutral **48** as the new return appearance baseline, as shown
by **49**. Exact SHA-256 pins (under `docs/pictures/video/tabi-assets/train-actions/`):

| Artifact | SHA-256 |
| --- | --- |
| `45-matching-neutral-single-pass-alpha-candidate.png` | `33a02b0a603adb42c43512b01531906aa41351e2c6e0678283b4e90e1437e3a9` |
| `46-matching-halfway-single-pass-alpha-candidate.png` | `1523f8575723c7979fc26090c219e3ed2c64b659189400deea9958f47af75985` |
| `47-matching-wave-single-pass-alpha-candidate.png` | `5e3890233ad57764b0c7b7a0279934d4e1dc9dfd778184f961c0195543660dc5` |
| `48-matching-neutral-return-baseline-review.png` | `c6dc10f9d03a0a0548645f62594d3f36a6ba5a772e3a0717fb72e96c1a81865b` |
| `49-matching-neutral-halfway-wave-comparison.png` | `9097314133cb6a7c427495e1750984f71aef451b6020407e5ad475a074227d5c` |

Hashes were freshly verified; the scoped event is
`build/vg2-08-kit-import-WrIrzi1D/user-artwork-approval.json`. The date is the
recording time, not an invented exact message timestamp. Old 29/art/videos and
candidate/rejection receipts remain unchanged. 43/44 are supporting source/guide
assets, not standalone production poses. No detailed per-edge human report,
normal-speed motion/cadence/contact review, actual-kit readiness, accepted take,
render budget or release decision follows from this appearance approval. VG2-08
still needs production import/support/occlusion/return proof and a separately
admitted preview; VG2-07 still needs the exact moving artifact's real user review.

**Subsequent exact preview execution decision** (2026-09-30, project user):
“yes, go with the generation, save the file in  @docs/pictures/video/tests”.
This approves the one-attempt local held-pose packet SHA
`2d86850934a47b4a0b1335884b5e70cb3956fc3af4f3d92e834ba55e9e42fb95`,
not unseen appearance/motion, a take selection or further repair/retry. The new
[review MP4](pictures/video/tests/vg2-matching-wave-held-217-20260930-fPyZ5vyR.mp4)
SHA `ef7cb3a041a6e32c8c253542ed2da5b221ed4a0f4fa8e09c7f340b1e957366a6`
passes actual source/full-decoded checks: 217 silent square-pixel 1080p30 H.264
frames at uniform PTS; exact three approved lossless supplied stacks and neutral
return 48; measured decoded worst RGB MAE whole/fixed/arm
3.138319/3.099911/4.189701 ≤12. One immutable private take stays
UNREVIEWED/unselected; only its project revision/takeVersions append changes.

**Execution-budget audit FAILS separately:** production encoder validation (2),
independent decode (2) and production import validation (3) perform **seven**
full MP4 decoder traversals, versus the packet's ceiling **four**. FFprobe
`-count_frames`/`-show_frames` are decoder passes, not free metadata-only checks.
The frozen count was not enforced; do not retroactively redefine/raise it or
claim ordinary green tests establish compliance. File/take/receipts remain
preserved. `build/vg2-08-wave-destinations-fPyZ5vyR/decode-pass-admission-audit.json`
and expected failing `decode-pass-regression.log` retain the concrete breach;
`execution-preservation-audit.json` verifies remaining pins. No further native
work, retry or code repair is authorized. VG2-08 is BLOCKED on a newly bounded
non-live count-guard prerequisite; VG2-07 remains WAITING_USER for actual
normal-speed gesture/cadence/identity/frond/props/contact/return review of this
file. This generation permission is **not** that moving acceptance. Full details
and actual limitations are in [TABI video](TABI_VIDEO.md#vg2-08-admitted-new-held-pose-video--technical-pass-decoder-count-breach-2026-09-30).

**Qualified wave feedback and new artwork allowance** (2026-09-30, project user):
“the animation is ok, but there's no enough frames, you need to create more assets
in the middle to make the animation smoother and fluid”, on exact preserved
`ef7cb3a0…7366a6` MP4 above. Direction is acceptable, but fluidity is a requested
refinement; no explicit playback speed, per-edge/contact sign-off, take selection
or final moving acceptance is inferred. “yes, i approve the new generations”
approves the subsequent artwork-only **4 initial + at most 2 shared corrections**
proposal, not unseen final appearances, count-guard implementation or rendering.
All six Pi calls are used; fresh owner `build/vg2-08-fluid-assets-1TFP896e/` records
scope/source/prompt specs and retained failed variants. New static 51–54 (registered
1920×1080 cutouts), 55–58 scene reviews and
[comparison 59](pictures/video/tabi-assets/train-actions/59-wave-seven-pose-static-comparison.png)
(`7dff03003bfa600aa82e77c1c47bc9e5382628b16f5e76cc19ff425a0421e207`)
require appearance review. Static zero common-region drift/neutral-48 parity,
distinct-state checks and a retained 462→0 wrist-clipping regression pass; 1,177
existing pins stay unchanged at publication (final disposition permits only
three task-owned documentation updates; 1,174 other pins stay exact). These do
not prove full imported support/mattes,
cloth/scale/anatomy continuity or a fluid moving sequence. Current project/take/
selection/video remain untouched. VG2-08's independent 7/4 decoder-count blocker
and VG2-07's revised moving review remain open; future repair/render permission
must be separate. Full pins/limitations are in [TABI video](TABI_VIDEO.md).

**Subsequent static approval** (user, recorded 2026-09-30T18:14:05Z):
“ok, i approve these poses and assets” closes the 51–54/55–59 static appearance
wait only. All nine publication hashes match; external approval receipt is
`build/vg2-08-fluid-assets-1TFP896e/user-static-artwork-approval.json`, binding
publication `11c17946…fab3b82` and comparison `7dff0300…421e207`. No moving
acceptance, source import/take selection or native budget is inferred.

**Subsequent non-live repair instruction:** “yes, go for it” answers separate
count-guard repair, not rendering. New owner
`build/vg2-08-decoder-guard-NXlF6eF2/` preserves 3,471 current file pins and uses
only scratch counter/client helpers and passive production-call-graph spies.
Actual production encoder/probe plus wrapper argv shapes reserve **2+2+3=7**
on one shared authority; old four rejects before socket/init, eighth before spy.
Final compile and **10/10** functional tests pass after both bounded fixture
repairs (missing prepared pin/project mismatch), with all real media/jobs/imports/
take mutations zero. Seven is a passive fixture ceiling, not live admission or
retroactive budget widening. No production validation/check is removed.

**Independent exact-owner check fails:** retained `socket-ownership-review.json`
and expected failing log reproduce a substituted canonical socket forging an
allowed/public-policy-hash reply: **one spy delegation, zero real reservations,
zero native delegations**. Server-side inode checks do not bind the client's
receiver. No artistic or safety pass may be inferred from the earlier green
functional tests/`independent-review.json`. **VG2-08 BLOCKED**, initial + two
repairs exhausted. One extra explicitly bounded non-live endpoint-identity/
authenticated-response repair is needed; no further implementation/media work
starts. A fresh guarded seven-pose input/preview packet and separate native
budget still follow afterward. Historical 7/4 breach, valid MP4/unselected take,
old helpers and approved artwork remain untouched. Full/focused/Node/build/diff
and protection results are recorded separately from this failure; see
[TABI details](TABI_VIDEO.md#subsequent-static-approval-and-bounded-decoder-guard-failure-2026-09-30).

### Standalone Blender feasibility checks (2026-10-01)

VG2-11, separately user-requested; not VG2-08 guard repair or native admission.
Owner `build/vg2-blender-feasibility-owO1rohL/` pins Blender 5.2.2 LTS and ten
source images. Non-rendering preparation plus an independent .blend reopen check
verify exact source hashes/dimensions, straight alpha, fixed camera/transforms,
foreground coverage and one visible supplied pose at all 180 proposed frames.
Initial reopen checker failed on unnormalized saved relative paths; one local
checker repair passes without changing the scene. Failed/final logs remain.
All 3,527 existing file pins match before documentation; no media, project,
take, production source or old helper is changed. Full test/build/diff and final
preservation receipts remain separate from this narrow technical scene proof.

The proposed six-second comparison has no launch authority. Before execution,
validate exact direct-child supervision, phase/global resources and operation
counts, destination ownership/no overwrite, source-state/alpha/support checks,
then freeze/admit the executable packet. Count its FFprobe frame-count/PTS scan
as one real traversal and its FFmpeg full decode as another; zero imports.
Check all rendered/decoded frames and real normal-speed appearance/cadence.
The saved scene alone proves no pixels, fluid rig, performance, full-film/app
readiness or approval. Historical VG2-08 7/4/socket-owner failures remain blocked.

### Blender packet phase-dispatch rejection (2026-10-01)

User “yes, go for it” approves **non-live packet preparation only**. Owner
`build/vg2-blender-packet-mBMDglBy/` pins 3,545 prior files and contains exact
seven-stack/coverage/contact proof, a separate 17-strip VSE .blend/check,
13/13 direct-counter/resource/path data tests and 6/6 numeric pixel tests.
Owned non-media sandbox fixtures reject outside writes, symlink escapes, fork
and network; no actual video/codec/model delegation occurs. Both local API repairs
are exhausted, with initial/repair-1 logs retained. Selected executables and
10,243 runtime files/inventories plus all native/verifier vectors are frozen by
packet SHA `be2efe5958ab7ca7dde9de599090d500a5132bf051e33628e026f2b7e5335b5b`.

**Independent exact-candidate failure:** `review_packet.py` rejects phases missing
from the budget. `phase_regression.py` exercises actual supervisor dispatch with
data-only callbacks: operations use `validation`; admitted-scope phase key is
`validationAndCopy`. Actual KeyError follows three spies and one reserved probe,
**zero native launches**. Retain `phase-dispatch-review.json` and expected failing
`phase-regression.log`; pin preflight/ordinary green tests do not establish run
readiness. VG2-11 BLOCKED; no further automatic repair or native admission.
One extra explicitly scoped non-live successor must align one current phase
contract, including publication, preserve failed packet/limits, prove this
regression, freeze and independently review before separate execution approval.
The 4-GiB aggregate applies to directly owned coordinator/sandboxed processes;
shared macOS VideoToolbox service RSS cannot be individually attributed. Disclose
that limitation, retaining host pressure/swap enforcement. No old four ceiling,
VG2-08 socket/count failure, static/moving approval or full-film/app gate changes.

### Blender extra repair and pre-media resource refusal (2026-10-01)

Exact user event: “yes, I want you to with the repair and also start rendering”.
Owner `build/vg2-blender-phase-3noEBfTw/` preserves failed predecessor and 3,598
prior pins. The one-extra phase repair passes four actual-supervisor/data-child
regressions: old KeyError reproduced, all six exact successor commands dispatched
to spies, one shared 120s validation/copy deadline, independent 900s expiry and
no phase alias/budget widening. Guard 13/13, numeric pixel 6/6, unchanged .blend
reopen, exact-candidate review, focused/Node/full test/build/diff checks pass.
Final packet `f4e5f829de8331820c3c6c7be12bf59f8e356e79d34dc62676405ed080253afa`
binds 10,243 runtime files and unchanged content/limits. Separate live receipt
records the real repair-and-render event, not fabricated moving acceptance.

Authorized `preview.py run` exits 1 on `No NORMAL/free-memory admission`, before
work-root/claim creation or Blender/encode/decode. No output frames, MP4, take,
project mutation or review copy. The original refusal sample was not persisted.
Later read-only diagnostic only: NORMAL, zero swap, free RAM 407,486,464 bytes
(<3-GiB requirement), free disk 240,287,637,504 bytes. Preserve
`live-launch.log`, `execution-refusal.json` and admission; do not relabel the
later observation as the launch reading or infer failed render performance.
No automatic retry/limit relaxation. VG2-11 WAITING_USER for a new bounded attempt
after resources improve. 3,595 prior non-document pins stay exact; only these
three documentation dispositions change. Technical repair pass is separate from
resource admission and still-pending actual Blender/media/moving evidence.

### Blender renewed attempt: source pass, encoded transfer mismatch (2026-10-01)

User “i free some memory, can you try again” admits one fresh bounded launch of
unchanged packet `f4e5f829…253afa`. Append-only retry receipt/pins/logs at
`build/vg2-blender-phase-3noEBfTw/` preserve the prior pre-media memory refusal.
Three actual NORMAL/no-swap/≥3-GiB-free samples pass. Blender renders 180 frames
in **18.572s**; every lossless frame passes reviewed-still maximum RGB delta **1**,
exact fixed/contact regions between states, exact holds and neutral entry/return.
Seven distinct source states are not native articulated motion or human approval.

Encode succeeds; saved full FFprobe count/PTS scan confirms one silent 1080p30
H.264/yuv420p stream, SAR1:1, 180 frames, six seconds and uniform 512-tick PTS
at 1/15360. **Actual validation fails:** transfer `iec61966-2-1` (sRGB), required
`bt709`; matrix/primaries bt709. Retained draft `live-comparison/preview.mp4`:
7,903,472 bytes; SHA `957e5160ecd9f183bcd695698b2bcfc64de6a052afa3c772f89599f5b0ee77ff`.
No visual color/transfer correctness is inferred and the contract is not relaxed.
One full MP4 traversal used, out of two; second full decode, decoded-pixel checks,
publication/import do not run. Total launch **39.378s**, sampled peak owned RSS
**1,126,596,608 bytes**, peak new storage **681,994,213 bytes**. Failure is not a
resource-budget breach. No automatic retry, fallback or code/art/project edit.

Read-only `retry-1-result-audit.json` reproduces actual `media_facts` rejection
using saved probe JSON and rehashes every source frame; zero extra native calls.
Preserve outputs and all old evidence. VG2-11 BLOCKED pending separately admitted
encoding-only color handling and validation using saved source frames; do not
blindly retag, lower the check or regenerate frames unnecessarily. 3,646 prior
non-document pins remain exact, with only these three documentation dispositions
changed. Ordinary focused/full/build/diff checks remain separate from this media
failure, actual normal-speed user review and full-film/app/release acceptance.

### Actual user rejection of Blender arm motion (2026-10-01)

User rejects draft SHA `957e5160ecd9f183bcd695698b2bcfc64de6a052afa3c772f89599f5b0ee77ff`:
inconsistent arms and a clipped-arm effect; requests rethinking asset-plus-scene-
description animation. Exact quote and artifact hash are recorded in
`build/vg2-blender-phase-3noEBfTw/user-motion-rejection.json`. No viewing speed,
exact frame indices or additional approval is inferred. This is genuine negative
moving evidence, independent of historical static appearance approval and the
still-failing color-transfer contract. Pixel-fidelity/hold/contact tests do not
measure anatomical/cloth consistency through motion; their passing results
cannot supersede the user rejection. No frame-local clipping diagnosis is claimed.

Prior suggestion to fix only encoding is superseded as the next creative step.
Preserve draft/frames/source artwork and all failures; do not relabel them as
approved animation or renew generation budgets. Discuss coherent prepared
character geometry/rig versus generative tradeoffs before scoping another proof.
Any chosen route needs real moving identity, arm-volume/texture, shoulder/elbow/
wrist attachment, occlusion and entry/return review, not static comparisons alone.
Rigging, hidden artwork preparation and new controls need separate authorization;
none are inferred from “rethink”. No native/artwork/app/project mutation here.

### Route-1 rig acceptance procedure (2026-10-01)

The user selects a prepared 2D/2.5D rig and proof before Melotrail integration;
this is a method/planning decision, not artifact approval or execution admission.
The previous seven-pose rejection and colour/count/socket failures are unchanged.
Apply the current [route tasks](../TASKS-VIDEO.md#route-1--standalone-rig-proof-tasks):

- **Preparation:** record exact source/derived pins, provenance, measured alpha,
  common view/scale, anchors, hidden overlaps and backing. Review assembled neutral
  and extremes; neutral 48 remains the reference unless explicitly replaced by a
  new approved candidate. Preserve all old originals and acceptance decisions.
- **Rig:** evaluate every frame, including in-betweens, for attachment/support,
  bounded angles/scale, stable limb lengths, no mesh inversion or unexpected
  texture drift, explicit occlusion and planted contact. Reopen must reproduce
  the same absolute-frame state. Freeze numerical tolerances and protected/moving
  regions before tests; include missing-overlap/clipped-joint negatives. A rig
  transform is not required to equal an unrelated historical pose's pixels.
- **Media:** independently establish sRGB input and actual Rec.709 conversion
  with known transfer-function witnesses. Compare decoded and reference pixels
  in the same declared colour space, not just tags or unmatched transfer codes.
  Enforce silent 1920×1080 H.264/yuv420p, SAR1:1, 30fps, expected frame count and
  uniform PTS. Keep source versus decoded proofs separate. A two-traversal
  standalone packet has no production import; extra traversals need explicit
  budget, not relabelling FFprobe scans as metadata. Test guards before native work.
- **Moving quality:** user watches the new five-second neutral/lift/wrist/lower/
  neutral gesture at normal speed and can frame-step shoulder/elbow/wrist/cuff,
  fronds and prop edges. Reject arm length/volume changes, texture or silhouette
  pops, clipping, rubbery bends, ghosts and abrupt returns even when numeric
  checks pass. Record exact artifact, date, reported viewing speed and decision;
  do not invent ratings, frame indices or observations.
- **Reuse:** separately review the second action and then both description-driven
  clips. Freeze identical rig/art/action versions across the pair; vary only the
  declared supported script parameters. Show original text and resolved events.
  Test unknown/ambiguous/conflicting requests without rendering. A manually
  translated request is not evidence of an automatic text compiler. Validate
  absolute chunk/state parity without silently re-rendering extra footage.

VG2-13 preparation decision (project user, subsequent to the VG2-12 inspection):
the user states they generated the TABI assets in
`docs/pictures/video/tabi-assets/` with ChatGPT and authorizes local derivative
parts preparation with up to **two image attempts per named missing asset**.
The eight targets and 16-call aggregate ceiling are recorded in
[TABI video](TABI_VIDEO.md#vg2-13-source-decision-and-bounded-preparation-authorization)
and [TASKS-VIDEO](../TASKS-VIDEO.md#route-1--standalone-rig-proof-tasks).
This is a user-reported source/derivative-use decision for the local proof,
not independent licensing or commercial/publication clearance, a new-part
appearance approval, an admitted media run or a technical validation pass.
VG2-14 still requires review of the actual assembled neutral and extremes.

VG2-13 static handoff (2026-10-01; **human decision pending**): exact review
artifact is [assembled v2](pictures/video/evidence/VG2-13/continuation-20261001-153813Z/states/assembled_review_v2.png),
SHA-256 `3d132f82d2a099b9495e7ed70837442e919146be7cab8659fe4c267e3d2e8825`.
Technical source/part/tool pins and results are under
`pictures/video/evidence/VG2-13/assembly-20261001T162141Z/checks/`;
`kit-freeze-final.json` binds the final candidate. Four static states have opaque
output, constant limb lengths, supported joint-centre samples and exact pixels
outside the declared support region. The first sheet's donor contamination and
clipped cheek remain retained with negative checks and the corrected atlas.
These checks do not establish full joint-edge continuity, all-frame swept support,
hand-opening motion, cloth/anatomy quality or appearance approval. The reviewer
must explicitly accept the changed neutral versus original 48 and inspect the
new arm/cuff/hand proportions, exposed coat/cheek and raised/wrist extremes.
No reviewer/date/decision is recorded yet. No Blender or media run is admitted by
this static handoff; VG2-14 still gates the later rig proof.

VG2-14 actual user decision (2026-10-01): **REJECTED**, for the v2 artifact
and SHA immediately above. Verbatim feedback: “the assembled_review_v2.png is not
good enough, around the hand there's some slop and clip around the arm”. Retained
receipt: `pictures/video/evidence/VG2-14/rejection-20261001T170716Z/review.json`.
The earlier pending status is superseded; no viewing speed, exact pixels, pose
subset or numeric ratings were supplied. Preserve prior fixture/static passes,
but do not infer complete contour quality or acceptance from joint-centre and
opaque-output checks. The next candidate must address the reported hand/arm
boundaries with source comparisons and full-edge/overlap regressions before a
new human decision. No rig evaluation, video or new asset creation is authorized
by this review record; the queue requests a finite extension of the exhausted
local derivative allowance. Old artifact bytes and consumed budgets are unchanged.

VG2-13 local-repair evidence (2026-10-02, user local date):
`pictures/video/evidence/VG2-13/repair-20261001T172254Z/` retains the admission,
source-localization ZIP, initial v3 and intermediate v4 candidates/helper snapshots,
selected v5, per-state facts and immutable handoff pins. New canonical regressions
are `tools/video-motion/vg2-contour-repair.test.cjs`: real old hand-contour clipping,
orphan cuff outlines, diagonal sleeve clipping, mistaken warm-outline removal,
backing/planted-contact preservation, allowance accounting and no-overwrite/path
guards. The initial test expected a case alias to reach collision detection; the
guard already rejected uppercase names earlier, so the expectation was corrected,
retaining the failing log. The final seven regressions and four-state technical
checks pass; source skin/selected outline/opaque RGB losses and cuff debris are zero.
The selected review SHA-256 is
`440c015c4e2ef4c4e14294f427f089c402aef2e3943120a3c48e46c3b9bdcfbc`.
No human decision exists for this changed neutral, raised poses or inner
sleeve/backing junction. This is a static-review candidate, not an accepted rig
or moving video. All previous rejections and motion/colour failures remain.
Consumption is 3 image attempts / 15 derivative files / 5 static passes, preserving
the original unused pass and two-pass extension in explicit reconciliation. No new
image call, Blender, media, production import or selected take. Focused/Node and
filtered make-test/build receipts live in `checks/`; the native-window test is
excluded, not passed. Scoped inspection scratch is bundled and deleted; the
temporary headless Gradle init is removed by the validation script's exit trap.

VG2-13 connected-wrist evidence (2026-10-02, user local date):
`pictures/video/evidence/VG2-13/wrist-repair-20261001T175547Z/` preserves the user's
v5 rejection, bounded correction admission, two raw built-in image edits and exact
prompts, four exported layers, three static candidates and prior-helper snapshots.
Selected review SHA-256 is `98beb200f181dd815791381381693f5a48ca9fba5ab1234d0dd1c4299bf1a968`.
The new `tools/video-motion/vg2-wrist-attachment.test.cjs` checks the entire authored
wrist bridge rather than only its centre; a cut-wrist negative keeps that centre
opaque but fails the full region. Actual v1/v2 negatives retain the overlay patch,
faint protected-pixel mismatch and cuff gap. Rear fabric overlap closes the gap
while preserving the supplied forearm and drawing the wrist/front cuff once.
The first negative test fixture omitted native ImageData dimensions; correcting
that test setup does not modify artwork or erase its failing receipt. Exports
retain alpha coverage and native registration. Headless focused/Node/make-test/
build receipts are in `checks/`; the native-window class is excluded, not passed.
Tests cannot approve anatomy, the inherited inner sleeve/body junction, differing
cuff drawings or their moving transition. VG2-14 remains a human appearance gate.

VG2-14/15 continuation and data evidence (2026-10-02): the user's instruction
“you can continue with the point 2 of the list” selects connected-wrists v3 for
the rig proof. Exact source decision:
`pictures/video/evidence/VG2-14/proceed-20261002T010147Z/decision.json`.
It records the changed neutral/extremes and known sleeve/cuff limitations without
fabricating moving approval. Standalone scene/checks are in
`pictures/video/evidence/VG2-15/rig-20261002T010147Z/`; selected scene is
`rig/tabi-wave-v3.blend`. All 150 data frames survive save/reopen and absolute-frame
reordering, with stable lengths/UV/depth/contact and identical return. Complete
joint regions and sleeve/wrist corridors pass; 1,134,801 texture-boundary samples
remain inside the camera. Retained negatives cover the actual 120-frame initial
cuff seam, a detached hand that fooled alpha-only centres, sleeve clipping, bad
UVs/inversion/return, missing parts, wrong pins, unsupported poses and conflicting
controls. Existing hidden fabric closes the seam, and part-to-joint registration
closes the checker gap. A pinned bytecode change interrupted the second build;
the third disables embedded-Python bytecode writes and passes. All three bounded
data attempts used 8.09 seconds and at most 282,542,080 bytes sampled aggregate
RSS, with zero rendered frames or media traversals. Headless focused, Node,
Python and make checks are retained beside the evidence; the native-window test
is excluded. Renderer colour/alpha, hand/cuff switches and moving quality remain
unproved; this does not authorize new media or further action expansion.

VG2-16 non-live evidence (2026-10-02):
`pictures/video/evidence/VG2-16/packets-20261002T011754Z/` has separate colour and
wave proposals with exact commands, output owners, immutable counters, aggregate
resource/phase limits, source/rig/installed-runtime hashes and OS scratch cleanup.
Independent reviews exercise all six real supervisor dispatches using child spies;
no native tools run. Source-to-Rec.709 and inverse-to-sRGB formula tests include a
blind-retagging negative. Eighteen supervision checks cover exhausted/changed/
concurrent operations, cancelled/failed delegates, aliases, replaced receipts,
preflight failure, shared deadlines and canonical runtime inventory ordering.
The initial inventory-order refusal and packets are retained; only ordering was
corrected, not the equality requirement. Colour and wave still need separately
bound native admissions and actual pixel/media checks. The user explicitly selects
local media/frame retention with only scripts, checks and hashes committed.
Focused JVM checks, 74 Node tests and 18 direct supervision checks pass. Headless
`make test` (2m36s) and `make build` (2m41s) each report one executed and 13
up-to-date tasks. `MidiCoreNativeResponsivenessTest` was explicitly excluded; no
interactive window or media tool ran. The final diff whitespace check passes.

A failed wave blocks action-library expansion, longer films and app integration,
not independent MIDI/fixture work. Each new colour test/render has its own finite
admission; historical attempts and budgets cannot be reused. Longer films retain
separate scenery, restart, resource, whole-cut and editor gates. No rendered rig
movement has yet passed this procedure. The VG2-15 data-only pass above is narrower.

### Episode reuse validation procedure (2026-09-30)

1. Record the selected city/story and exact references; inspect existing material
   before authoring/supplying only missing finished city/activity assets. Pin
   reused compatible cabin/TABI/props independently from changed exterior/poses.
   Kyoto/Madrid/Rome inspiration is not a ready registered train corridor.
2. Recheck decoded alpha/geometry, all exterior apertures (including previously
   static small left panes), scenery depth/overlap/shutter coverage, subject/prop
   seams and foreground/frond contact for every allowed pose. No undocumented
   automatic mask adaptation, uncovered padding or fresh artwork synthesis.
3. Pin the materially different activity choice/order/timing/rests/neutral returns
   in the existing assembly contract after VG4-07. Verify legal holds, conflicts,
   consumed dependencies and absolute boundary/resume parity. Preserve rejection
   witnesses; do not claim held artwork is native articulated 30-fps animation.
4. For each newly admitted actual clip/batch, retain source-PNG checks separately
   from H.264 decode checks. Check frames/PTS/duration/streams/square pixels,
   neutral returns, each action/scenery/chunk join, no warp/ghost/wrap/full-clip
   repeat and expected time-appropriate contact/occlusion. Record per-stage and
   whole-batch elapsed time, peak/native pressure and total retained/staged disk.
5. Obtain artifact-specific normal-speed feedback: city distinctness, stable
   cabin/character/props, activity readability/cadence and different story rhythm.
   The second-city short proof does not imply a second full film, full corridor
   or any third-city approval. No automatic take selection or overwritten media.
6. Before a complete later episode, prove its full 180–300s scenery/action plan,
   obtain a new exact finite budget and full-film/editor decision through the
   same workflow. For this series VG6's full app film uses the selected second
   city; generic product scenarios remain supported. Rights/audio/upload stay
   separate and externally finished music is added only in the Apple editor.

The existing 900-second preview deadline is shared across invocations. A full
production batch needs explicit finite per-stage and cumulative time/storage
admission; this update changes none. Keep 2-GiB native memory and 10-GiB free-disk
reserve safeguards, counting retained outputs and encode/decode staging together.
Source-PNG/time extrapolations in TABI video are planning arithmetic, not measured
full-run budgets. No native/model job, download or paid request starts from this
schedule. Rights, color, monetization and final release remain separate decisions.

## Final manual review

The implemented MIDI workflow still needs manual acceptance. AC4–AC5 refresh and
review its evidence against the current build. After the production gates above,
VG3 still requires design permission/approval before UI, and VG6 owns separate
headless production-integration, early/full-video artifact, package and release
review. Standalone pilot results cannot substitute for that later production
integration; a visible GUI is not a validation prerequisite.

**Retained MIDI handoff · 2026-09-13 (original label Q03b).** This packet used
implementation `c20aecf583`; it does not establish current-build success or the
planned Video workspace. Preserve useful fixtures/forms, refresh applicable outputs
through current services and never rewrite the old packet. Its local directory is
`~/.codex/melotrail-terra/final-review-2026-09-13/`; paths below are relative to it.
Start with its identity/check receipt (`verification.json`).
The DMG (`Melotrail-1.0.0.dmg`) passed the isolated installation/startup
procedure below. Preserve the packets; fill copies of their forms.

| Gate | Open / do next |
| --- | --- |
| AC5-04 · MIDI presentation | Refresh six-page offscreen comparisons, semantics and service timing (retained starting point: `u07/visual-review/index.html`). No native-window capture, live usability or acoustic-onset session. Historical wallpaper failures stay failures; AC5-05 is removed, not passed. |
| AC5-01/02/03 · Music | Retained development comparisons (`q01-development/review.md`), set requirements (`q01-evaluation/review.md`), intake (`song-intake-template.json`) and blank scores (`score-template.json`) inform fresh preparation. Five owned/licensed full songs, including three unseen, and real scores are still needed. Supply current MIDI projects, ownership/exposure and settings before freezing; no scores are invented. |
| AC4-01 · MIDI handoff | Refresh the current matrix and verify new hashes, complete/separate-file alignment, controllers and endings through semantic re-import. Retained starting point: `q02-logic-matrix/review.md`. No Logic session; AC4-02 is removed, not passed, and current editor compatibility is unverified. |
| VG6-01/02 · Early video | After VG3 integration, invoke the app's production services headlessly with finished artwork/layers and motion prompts. Review supplied real 20–30s clips: base motion, changed motion with the same artwork and replacement artwork. Actual artifact feedback remains required, not a live app walkthrough. |
| VG6-05/06 · Complete video | After early approach, package and headless integration proof, generate one real 3–5-minute silent video through production services and obtain whole-artifact review. Use the prepared second city/different script with full corridor/contact/sequence readiness and a new budget; generic scenarios remain supported. Review fidelity, cadence, joins and reuse. No interactive editor test, short-proof substitution, whole-clip repeat or synthetic acceptance. |
| AC5-07 · MIDI release | After current listening, semantic MIDI, offscreen visual and package-inspection checks, record the actual release decision and final build, disclosing omitted live UI/editor/installed-startup checks. Musical quality remains unmeasured against the original 5/10 feedback. |

The removed Swift companion demo is superseded historical evidence; it is not
the V24/V33 product flow and needs no new review. To inspect that old packet, launch
`/Users/marcoandreose/.codex/melotrail-terra/final-review-2026-09-13/companion/app/melotrail-tabi-editor`
with `/Users/marcoandreose/.codex/melotrail-terra/final-review-2026-09-13/companion/fixture/composition-request.json`.
Only paths in this fixture copy were relocated; source/asset/soundtrack bytes
are unchanged and its old release caller was rechecked. The preserved installer,
MIDI handoff and soundtrack timing behavior are evidence of the retired design,
not requirements or setup steps for the planned independent Video tab.

The final packet retains 66 unchanged pinned image comparisons, 28 real-window
frame replays, 20-sample timing records, source/build/runtime identities and
hash-verified MIDI/video artifacts. Automated checks do not establish acoustic
onset, musical scores, foreground screen capture or human artistic approval.
Historical packets and failed checks remain preserved. Current MIDI, production-
pilot and later VG3/VG6 human gates are listed in TASKS-AUDIO and TASKS-VIDEO;
automated agents cannot
complete them or retry absent human evidence as an implementation failure.

## Scoped Tokyo motion-direction review (2026-09-27)

The project user likes the animation direction of the 5.16-second Tokyo pilot but
explicitly requests higher quality for YouTube. The exact artifact, SHA-256,
source/model/build identities, technical receipts and quoted decision are recorded
in [TABI video](TABI_VIDEO.md#user-reviewed-tokyo-feasibility-clip-2026-09-27).
This is directional approval of that clip only, not final-quality acceptance. It
was generated through production backend services with a scratch host wrapper,
not through the planned app flow. No 20–30-second result or generalization to other
scenarios is established; VG2-03, VG6-02 and VG6-06 remain open. The original
pre-review machine receipt stays unchanged; this records the subsequent human
feedback without rewriting its historical state.

The subsequent user-authorized
[finishing A/B comparison](TABI_VIDEO.md#authorized-finishing-comparison-2026-09-27)
adds Lanczos-only and RealESRGAN x2 review versions in
`build/video-test-archive-2026-09-28/previous-tests/quality-ab-2026-09-27/`. Both pass full decoding and
frame-timestamp checks: 1920×1080 H.264, 129 frames at the original 25 fps,
5.16 seconds, zero audio streams. These are matched-crop upscales, not new motion
or native-resolution generation. Original hashes are preserved, runtime safeguards
remained active and the owned runtime stopped. In subsequent feedback the user
prefers B as “sharper and better quality”, but reports flickering and wrong/sloppy
background-object movement. This is a scoped finishing preference, not background
motion or final-quality acceptance. The exact B hash and original-frame defect
observations are recorded in TABI video; the pre-review machine receipt remains
unchanged. Corrected background motion still needs new evidence and user review;
no acceptance gate is passed.

On 2026-09-28 the user selected Minimal geometric Tokyo for a bounded trial.
The [new motion pilot](TABI_VIDEO.md#selected-geometric-tokyo-motion-pilot-2026-09-28)
provides native and B-finished versions, both fully decoded: 129 frames, 25 fps,
5.16 seconds, H.264 and zero audio streams. The upscaled 1080p derivative also
passes explicit square-pixel/16:9 checks. The artwork and exterior prompt both
changed; no isolated causal result or controlled rigid-motion proof is claimed.
Sampled frames still show some deformation. Subsequent user feedback calls the
clip a good starting point but identifies eye-animation faults and blurred/sloppy
building shapes, and asks for a more distinct background. This is not final-quality
acceptance; corrected eye/background motion remains unproven and requires a new
review. Native/upscaled eye observations are recorded in TABI video. Source/prior
artifacts and historical machine receipts remain unchanged.

The user subsequently selected ComfyUI, not Codex/hosted image generation, for
new panorama/parallax experiments. The
[local artwork proof](TABI_VIDEO.md#local-comfyui-panorama-artwork-proof-2026-09-28)
produced one native 1536×512 opaque panorama through the installed Klein model
and existing production ComfyUI/job adapters, with three actual bound references.
Its native PNG hash is
`f4edca0eb1337aa2723b9b73cda2eaf06a51f9d7e22c0b73e848241598ebd66a`.
Two additional static context previews preserve pixels outside the existing
window mask; they are not moving-video evidence. The single attempt stopped
cleanly, preserved original/model pins and recorded two WARNING-pressure samples,
no CRITICAL samples and no sampled swap increase. This standalone asset experiment
adds neither in-app picture generation nor a finished layer kit. Its look remains
unapproved; separate depth layers, coverage/occlusion and actual parallax/eye
motion still require proof. Current VG2/VG6 gates stay open.

The user's next feedback requests a more detailed, characteristic background,
not an anonymous/boring one; the first look is not final-approved. The
[detailed revision](TABI_VIDEO.md#more-characteristic-tokyo-artwork-revision-2026-09-28)
provides a native 1920×640 PNG and three static context views, not a motion test.
Native SHA-256: `16c276238e2c62d0869c815c51d343bc2f9b680bbdee934a15f246fc810d362a`.
Two submissions are disclosed: the first stopped without output after exposing
node-local ComfyUI progress incorrectly persisted as whole-job progress; the
second succeeded after VG2-04's regression-tested client repair. Store invariants,
failed evidence and resource limits remain unchanged. Both runs recorded warnings
and swap growth, but no sampled critical pressure; the detailed receipt retains
actual values and stopped-runtime/source-preservation evidence. Small generated
figures, unverified signage and stronger-than-requested street perspective remain
limitations. At that publication, user look approval, independent layers and
parallax/eye motion were unproven; no artistic/app/full-duration gate is inferred
from that repair.

On 2026-09-28 the user approved the detailed panorama's **direction**, not final
motion quality, and authorized the three-plane/five-second plan. The
[layer preparation and refusal evidence](TABI_VIDEO.md#layered-tokyo-preparation-and-blocked-parallax-render-2026-09-28)
now contains three separate prepared planes and static start/middle/end compositions.
Four ComfyUI still submissions (one rejected for its far-layer role, followed by one
correction and two other initial roles) and three RealESRGAN still finishes completed.
VG1-02's measured-alpha/opaque-coverage repair passes focused JVM checks and 24 Node
tests; transparent rectangles cannot conceal missing opaque backing. Supplied-pixel
coverage and static TABI/cabin preservation are checked, not inferred from prompts.

**At the layer-preparation publication, the requested five-second video did not
exist.** One durable controlled-media attempt was refused before native launch: 830,046,208 free bytes reported versus
2,147,483,648 required. Zero frames and no MP4 were produced. The failed ledger,
source pins and all previous outputs remain intact; there was no limit override or
automatic retry. Subsequent recovered free pages do not approve or execute another
attempt. The prepared layers and motion still require user review; signage, recurring
shop motifs and unchanged left window panes remain disclosed limitations. The typed
three-plane harness is not an integrated application workflow. VG2/VG6 motion,
20–30-second continuity, blink, long-duration, rights and release gates remain open.

The user subsequently authorized a retry and a quality-preserving resource solution.
The [five-second native result](TABI_VIDEO.md#five-second-parallax-and-memory-repair-2026-09-28)
is now available for review. The same-limit retry first stopped at the native 2-GiB
RSS ceiling after 23 PNGs, producing no video. VG2-05's per-frame event-loop service
and final-yield cancellation guard pass 27 Node tests (three regressions failed
before repair), focused JVM boundaries and native controlled rendering. Two repaired
runs completed; the final source run produced 150 distinct decoded frames at uniform
30-fps timestamps, silent 1920×1080 H.264 with 1:1 pixels and five seconds. Final
MP4 SHA-256: `3e104ea878eee9b4f4afbe76bb5c5b182495b44b4cb3dce156da33730a241aa9`.
The source frames are byte-identical across the two successful candidates and the
first 23 match the failed original renderer. Every source frame exactly preserves
all 1,351,577 pixels outside the window mask. This is not a lossless-H.264 claim;
compression measurements are retained. Final sampled Node RSS peaked at 448,675,840
bytes under the unchanged 2,147,483,648-byte limit, with NORMAL samples and no sampled
swap increase. Original art/prior results and all attempt ledgers remain unchanged;
Finder metadata exclusions are disclosed. No additional model or finishing submission.

Both imported takes remain UNREVIEWED. Selected decoded still inspection and measured
rigid travel do not certify normal-speed user approval, good matte edges or absence
of perceived shimmer. VG2-03 still awaits real motion feedback and its longer native
ladder/recovery evidence; blink, full-duration, UI, rights and release gates remain
open. Final source/docs identity and focused/full test/build/diff logs are retained in
`build/tabi-parallax-final.ScZPRv/`. This supersedes only the earlier missing-five-second
artifact/authorization wait, not human acceptance requirements.

User feedback, 2026-09-28, on the final five-second MP4 identified above: likes the
parallax, requests faster movement and more passing scenery, and rejects the
background tower's stylistic mismatch. Record this as qualified direction approval,
not final-take, longer-duration or release acceptance. The separately bounded
[20-second follow-up](TABI_VIDEO.md#twenty-second-faster-tokyo-test-2026-09-28) is now
rendered: SHA-256 `30364b7e3a50b88ce217504f5f7e7805c7fa07f1cd16e113f7f12be0e30d20f7`.
One durable controlled attempt produced 600 distinct source and decoded frames at
1920×1080 / 30 fps, silent, square pixels, uniform 512-tick PTS at timebase 1/15360.
Its two 300-frame chunks carry exactly matching scenery state. All source frames
preserve 1,351,577 outside-mask pixels exactly; H.264 does not preserve them losslessly.
Measured patch travel agrees with the 480/960/1440-pixel trajectory (~1.99× speed).
The 325,083-ms run stayed under the existing 2-GiB native limit; sampled renderer
peak 494,387,200 bytes, 162 NORMAL samples, no sampled swap increase. Middle artwork
generation recorded two WARNING samples and ~3.27 GiB host-global swap growth;
other sampled jobs were NORMAL, with no sampled CRITICAL. Sampled RSS is not an
exhaustive or unified-memory peak. Failed artwork/preparation candidates are retained.
Scratch roof-matte regressions, focused JVM checks, 27 Node checks, full test/build
and diff gates are recorded under `build/tabi-tokyo-long.58RtOJ/` and in the new
publication's validation receipt. No production code changed for this follow-up.
The new take remains UNREVIEWED. Its speed, tower, scale, mattes and perceived
shimmer need normal-speed human review; 30-second/recovery, blink, app/UI,
full-duration, rights and release gates remain open.

Subsequent user feedback says the exact 20-second test above looks better, then
authorizes the isolated blink study and review-folder cleanup. This is direction
approval, not acceptance of unseen eye poses or a complete video. All 563 old
review files were moved without deletion and hash-reverified at
`build/video-test-archive-2026-09-28/previous-tests/`. The unchanged current MP4 is
now `docs/pictures/video/tests/tokyo-parallax-20s-1080p.mp4`. Relocation receipts are
under `build/tabi-blink.0GtJyK/`; historical receipts were not rewritten. Keep the
ignored archive when cleaning build output. Blink artwork/motion require separate
inspection and human review; no automatic take selection follows this feedback.

The [blink preparation/refusal](TABI_VIDEO.md#blink-artwork-and-guarded-render-refusal-2026-09-28)
provides static open/closed art only: three ComfyUI still jobs (one admitted closed
correction), one selected RealESRGAN finish, and rejected half-pose gaze geometry.
Preparation preserves 2,064,229 non-eye pixels exactly, but this is not a moving-frame
or accepted-pose claim. One eight-second controlled request was refused before native
launch: 1,619,968,000 available bytes versus the unchanged 2,147,483,648 requirement.
The attempt is FAILED, with zero frames, no new video/take and no automatic retry.
Artwork generation recorded six WARNING samples, no sampled CRITICAL, maximum
per-job host-global swap increase ~1.64 GiB. Owned wrappers stopped. Archive/static
preparation checks and focused/Node/full test/build/diff gates are retained in
`build/tabi-blink.0GtJyK/`; no production code changed. The closed-eye comparison is
unreviewed; natural three-pose lid motion remains unsupported by the current blend
control. A new native admission and human motion review remain necessary.

The subsequent explicitly authorized [eight-second retry](TABI_VIDEO.md#eight-second-isolated-blink-retry-2026-09-28)
succeeded without new artwork/finishing: MP4 SHA-256
`e5d3376c9abf01ee820ccf93176a7e28b6db586acdacca8ab02f6cc34b5582c2`.
Full decode verifies 240 silent 1920×1080 H.264/yuv420p frames, square pixels,
30 fps and eight seconds. All lossless source frames preserve 2,064,229 non-eye
pixels exactly. Seven frames change around 3.5 seconds; 233 deliberately static
holds are not new motion. One attempt completed in 106,792 ms, with all 55 resource
samples NORMAL, no sampled swap growth and Node sampled peak 302,628,864 bytes
under the unchanged 2-GiB cap. Owned wrapper exited; previous failure/archive/art
remain pinned. Scratch metadata-comparison failures and their correction are
retained; no second attempt or production change. Focused JVM, 27 Node, three
scratch parity checks and full test/build/diff gates are recorded in
`build/tabi-blink-retry.gc18u7/`. H.264 is lossy (fixed-region temporal RGB MAE
up to 2.43); exact preservation applies only to source PNGs. Intermediate states
show the disclosed crossfade ghosting, not articulated half-lid motion. The take
remains UNREVIEWED; naturalness, combined motion and broader delivery gates stay open.

**User blink approval (2026-09-28):** “ok, the blink is well made, we can continue
with the next step”, referring to eight-second MP4
`e5d3376c9abf01ee820ccf93176a7e28b6db586acdacca8ab02f6cc34b5582c2`.
This accepts that blink for the next combined test; it does not accept unseen
combined motion, longer continuity or release. Sealed receipts/project snapshots
retain their original review status and are not automatically selected. The new
scoped event/admission lives in `build/tabi-combined.e6gsws/`. One 20-second
combination with the current parallax is authorized, using unchanged art/speed/
quality and native safeguards, not new generation or a full-duration run.

The [20-second combination](TABI_VIDEO.md#twenty-second-combined-blink-and-parallax-2026-09-28)
now exists: SHA-256 `7136e45800aebc577e4529a36cc3007b05117199db611e6a3a76a7edb686970f`.
One attempt completed in 331,992 ms; full decode verifies 600 distinct silent
1920×1080 H.264/yuv420p frames, 30 fps, square pixels and 20 seconds. Every source
frame matches the prior parallax outside the eyes (2,064,229 pixels); the first
240 eye regions exactly match the approved blink. All 1,342,206 pixels outside
both moving regions remain fixed. Blink peaks at 3.467s / 10.467s / 19.4s preserve
absolute timing across two 300-frame chunks with matching scenery state. No new
artwork/resizing/finishing: only binary eye-support alpha cutouts in the cabin
foreground/mask. Sampled Node peak 562,118,656 bytes, 166 NORMAL samples, no sampled
swap increase, unchanged 2-GiB cap and owned wrapper exited. H.264 remains lossy;
exact preservation is source-PNG evidence, not an encoded-pixel guarantee. Current
focused JVM, 27 Node, three preparation checks and full test/build/diff gates,
protected prior hashes and the new UNREVIEWED take are in `build/tabi-combined.e6gsws/`.
No production change or automatic take selection. The user approved the preceding
isolated blink, not yet this combined artifact; normal-speed combination review,
30–60-second coverage/recovery, full-duration and release gates remain open.

**User combined-test approval (2026-09-28):** “perfect, we can continue with the
next step”, referring to MP4 `7136e45800aebc577e4529a36cc3007b05117199db611e6a3a76a7edb686970f`.
This approves that combined test, not longer footage or release. The new scoped
event/admission is in `build/tabi-continuity.0lyIo2/`, preserving old sealed records.
A 30-second run can use previously unseen supplied near scenery via an explicitly
disclosed starting reframe, with unchanged speed/scale. Its restart check is limited
to a fresh process recovering completed published media before take import. Partial
render checkpoints/crash resume remain unsupported and are not satisfied by this test.

**Thirty-second continuity/completion recovery (2026-09-28):** review draft
`docs/pictures/video/tests/tabi-tokyo-continuity-30s-1080p.mp4`, SHA-256
`abb990dab8542e30113fc0fe9ee89de5275e5c7c79167916f2f5ae4d755c81ea`.
Scratch `build/tabi-continuity.0lyIo2/`. One attempt;900 distinct source frames,
three absolute300-frame chunks/exact state continuation, same speed/scale plus
480px near-layer starting reframe. First600 eye regions equal the approved combined
clip;1,342,206 fixed pixels/error0. Top-window source prefix equals frames0–598;
frame599's old endpoint shutter clamp does not apply to the continuing shot. Failed
scratch comparison and explicit endpoint regression are retained.

JVM5988 exited normally after sealed output but before coordinator success/import;
JVM9882 recovered that exact attempt and imported one unselected UNREVIEWED take.
900 PNGs/media/completion receipt stayed unchanged; renderer/encode launch guards
had zero invocations. Render/handoff504,265ms; recovery11,865ms. This is real fresh-
process **completion recovery**, not abrupt-crash or mid-render checkpoint resume.
Production media validation and full probe verified silent1080p/30fps/H.264/yuv420p,
SAR1:1,30s/900frames and uniform PTS. **Independent PNG extraction timed out at180s**,
leaving893 complete readable frames0–892, all distinct/compared,1,956,788,121bytes.
Encoded frames893–899 remain independently uninspected; no retry/cap increase.
Prefix-only PSNRmin35.2817dB, mean RGB error max2.8130, fixed temporal MAEmax2.6156.
Do not describe this as a complete900-frame decoded-pixel review or certified master.
Render250/recovery8 samples all NORMAL, no sampled swap growth; Node sampled peak
564,723,712bytes. Memory2GiB, render900s/wrapper1020s,7GiB staging/1GiB output and
10GiB reserve unchanged; recovery wrapper240s; review2GiB/180s/3GiB. RSS samples
are not exhaustive/native-unified-memory peaks. Retained scratch log/metadata
failures did not relaunch rendering; one native PNG extraction hit its guard.
Focused/full/build/diff software gates are separate from this incomplete media check.
Normal-speed user review and fresh bounded decode authorization remain pending;
full duration, partial-render recovery, integrated UI and release remain open.
See [detailed evidence](TABI_VIDEO.md#thirty-second-continuity-and-completion-recovery-2026-09-28).

**User30-second review (2026-09-28):** user calls the exact MP4 above “a great
result”, reports a pre-existing incorrect window cutout around TABI's leaves, and
says “don't regenerate this video”. Full quote/scope:
`build/tabi-tail-review.rT83Xd/user-review.json`. Positive direction approval with
that defect is not a clean-mask or release pass; playback speed was not explicitly
reported. VG1-03 owns future edge localization/repair. Current artwork, video,
project/take snapshots and earlier timeout receipts remain unchanged.
The user authorizes one bounded read-only extraction of frames870–899 to corroborate
23 overlaps and inspect the missing seven. No new video/render/encode, mask edit,
model job or automatic retry; memory2GiB/deadline180s, wrapper215s, aggregate old+new
decoded staging3GiB and10GiB free reserve. Fresh evidence stays separate from the
sealed incomplete-review receipt.

**Tail review completed (2026-09-28):** `build/tabi-tail-review.rT83Xd/` records one
successful9.885s bounded read-only decode, not video regeneration. Exact RGB parity
on23 overlap frames corroborates ordinals870–892; frames893–899 complete900 distinct
decoded frames compared against source. Original full probe and all earlier
artifacts/timeout receipts remain unchanged. MP4 SHA remains
`abb990dab8542e30113fc0fe9ee89de5275e5c7c79167916f2f5ae4d755c81ea`.
Full-frame metrics: PSNRmin35.2817dB, mean RGB error max2.8130, fixed temporal
MAEmax2.6156. New67,031,353bytes/aggregate2,023,819,474bytes below3GiB. Six NORMAL
samples, no sampled swap growth; FFmpeg sampled peak69,795,840bytes. Same2-GiB,
180-second native/215-second wrapper and10-GiB reserve. Three scratch regressions
reject shifted overlap/missing endpoint/repeats; focused, Node, full test/build/diff
gates are recorded separately. No production changes or new video/take/artwork.
The earlier incomplete-review receipt is historical, not edited into a pass.
Current decoded coverage is complete; **VG1-03's leaf/window cutout defect is not
fixed**, and user-positive feedback with that caveat is not release approval.
VG2-03 is REVIEW for integration assessment. Playback speed, full duration,
mid-render recovery, app/UI and release evidence remain separate/unproven.

**VG1-03 source-mask correction (2026-09-28):** user says “ok, correct it”; scope
is new input layers, not remaking the current video. Evidence/bundle:
`build/tabi-leaf-mask.ey9rmD/`, `prepared/`. Original-image inspection reproduces
both retained old-background wedges and clipped dark frond outline in the old
coarse polygon. A local cubic contour corrects1,164 mask pixels (960 release more
exterior;204 restore protection); all occluder RGB, eyes, scenery and unaffected
pixels remain exact. Window support bounds do not expand. The window mask,
foreground/occlusion alpha and starting composition are updated as one bundle;
9,371 eye-cutout pixels and single effective antialiasing are preserved.

Six scratch checks pass; the old mask fails background/outline regressions. A
third before-regression catches Pillow RGBA identity-affine color round-tripping;
the corrected static start uses an exact crop. The initial candidate/failures remain
retained. Production asset/prepared-scene import passed in5.32s in a new private
project; all eight references/hashes and measured alpha match actual PNGs. Zero
jobs/takes/model runs/video renders/encodes. Focused, Node and full test/build/diff
software gates are separate final-gate logs. The new comparison images are static
preparation checks, not video-frame/native-motion evidence. Human visual review
is pending (VG1-03 REVIEW); no release or corrected-motion approval is inferred.
All four existing MP4s and historical sealed evidence remain unchanged, including
30-second SHA `abb990dab8542e30113fc0fe9ee89de5275e5c7c79167916f2f5ae4d755c81ea`.
See [comparison and method](TABI_VIDEO.md#leaf-window-mask-correction-2026-09-28).

**Subsequent VG1-03 user acceptance / longer-run preflight (2026-09-28):** the
project user says “ok, we can continue with the next step” after corrected-mask
still delivery. Acceptance is scoped to using that source bundle in future work,
not a detailed per-edge assessment or unseen moving video. New event:
`build/tabi-next-preflight.iMUH0P/mask-user-review.json`; comparison SHA
`1637fcf810064a5a254f98617718f62065a98c18f27fb95b34f5c3dba17a0f35`, preparation SHA
`d63fa5d92d9d2ff8276287373a7f911b91d09265c480e15655def9eabd37b99f`.
VG1-03 is DONE for source preparation. Prior sealed review states, all four MP4s,
takes and selections remain untouched. No corrected-motion approval is claimed.

Read-only60-second analysis in the same scratch root finds insufficient supplied
near/far extents at unchanged speed: at least2,117/82 additional prepared-scale
pixels respectively at current placement, before seam/filter margins. Middle
extent is sufficient. This is not a full alpha/trajectory admission. The measured
504.265s30-second attempt scales roughly to1,008.53s, above the existing900s
whole-attempt ceiling; that extrapolation is a risk flag, not new native evidence.
No cap was raised, no job launched and no partial-resume claim added. VG4/VG5
implementation dependencies, coherent additional artwork and a new bounded run
still precede60-second execution. See
[method and limitations](TABI_VIDEO.md#corrected-mask-acceptance-and-60-second-prerequisites-2026-09-28).

**VG4-03 scoped planning dependencies (2026-09-28):** user authorizes continuation
with “ho ahead with this.”. Current HEAD remains `b208cc70068eda86c2a236f1cd242226b92915db`
plus the preserved working tree and this bounded planner/domain/test repair.
Evidence: `build/vg4-dependency-scope.s6Nk20/`. Three dependency checks fail before
selection is scoped; explicit ownership now binds only consuming global/action
components, including support frames and effect tails. Shared/unspecified pins
remain global. Complete declarations and valid typed namespaces are required;
unused art is never inferred from a filename. Used version/hash/artifact locations
remain pinned and completed takes are retained without rebinding.

Assembly planner version 2 requires explicit schema/planner tags on serialization;
a separate before-failing check proves missing tags reject after the repair. The
first version-tampering test changed no field because default JSON omitted it;
that failure is retained, with an explicit-default fixture correction. There is
no prepared-scene/media-request schema change or migration. All 30 planner tests,
focused checks (2 executed/4 up-to-date), Node 27/27, full tests (4 executed/10
up-to-date), build (14 up-to-date) and diff check pass. Post-documentation gates
and final source/protected hashes are separate receipts in the same root.

VG4-03 is DONE for this pure planner boundary. Plan persistence, short-shot caller
replacement, actual checkpoint/resume, additional scenery and native 60-second
proof are not delivered by these tests. No real media/model job, new take, change
to the approved mask/videos or resource-limit increase occurred. MIDI and all
unrelated source WIP are preserved. See
[scope and limitations](TABI_VIDEO.md#scoped-continuous-plan-preparation-dependencies-2026-09-28).

**VG4-04 continuous-plan proposal persistence (2026-09-28):** “go for it” admits a
bounded store slice; evidence is `build/vg4-plan-store.7MaJjG/`. `VideoAssemblyStore`
uses the existing guarded project publisher, now shared with prepared scenes.
Fourteen new tests establish exact 180/240/300-second proposal reopening, append-only
records, CAS under independent writers, immutable orphan reuse after injected
publication failure, actual prepared image verification, source/descriptor/record
mismatch rejection, version/byte bounds and symlink/MIDI-root confinement. Those
planning durations are not rendered media. The initial 13-case pass and later
14-case pass remain separate reports; no check failed or repair was needed.

Video project schema 5 is current; prior schema 4 rejects without migration and its
files are preserved. Assembly schema 1/planner 2 remain unchanged and required.
No historical user/test project was rewritten to satisfy these tests. Failed
publication recovery here concerns a descriptor and project document, not native
rendering, partial chunks or abrupt crash recovery. Proposal persistence does not
establish full artifact-bound executable readiness or artistic acceptance.

Final focused tests pass in48s (2 executed/4 up-to-date); all27 Node checks pass.
`make test` passes in4m32s (4 executed/10 up-to-date), with789 root +239 desktop
JUnit cases and zero failures/errors/skips; `make build` passes (14 up-to-date),
as does diff check. Post-documentation test/build/diff results and final protected
hash verification are retained separately. VG4-04 is DONE; application caller
integration and short-shot retirement, continuous compilation, checkpoint/resume,
scenery extension, longer native output, UI and release remain uncompleted gates.
No model/real media job, new take/selection, limit increase or commit occurred;
approved videos, the accepted corrected mask and unrelated WIP are preserved.
See [persistence boundary](TABI_VIDEO.md#continuous-plan-proposal-persistence-2026-09-28).

## Historical V18b1 evidence and current probe isolation

The reported archive `~/.codex/melotrail-video-sequential/evidence/V18b1/updated-assets-f96a7f3`
is unavailable and no verified backup was supplied. Original checksums, ledgers
and failed-candidate contents were not authenticated. Per the latest user
instruction, archive recovery, historical hash reconciliation and reviews that
require its unavailable contents are retired delivery prerequisites; this is not
archive recovery or a V18b1 pass. V19r2 closes the bounded current-tree isolation
check. That historical admission does not certify the current working tree;
CORE-01 identifies and validates the candidate used by the fresh feature queue.
Current candidate, source, asset and model integrity checks remain required,
as do new implementation checks, technical review and human acceptance gates.

The user-designated current video inputs are `docs/pictures/video/` and all
subfolders. Select suitable files, record current relative paths and digests,
validate motion capabilities and preserve originals. Existing inspiration-only
labels and production-rights requirements remain; availability alone approves
neither independent animation nor visual quality. Technical regressions use small
owned fixtures. Real video checks create fresh evidence from selected current
assets, with explicit missing-input findings instead of historical archive waits.
The deferred reference-image resource/test owners and probe registration are
verified absent from the present tree only; this does not authenticate their
historical identities. Preserve the legitimate local, media and ComfyUI probes.

## Required automated checks

Run the filtered test/build commands in
[Non-interactive validation](#non-interactive-validation), plus `git diff --check`.

Run focused suites for the task as well. `make build` includes Gradle check;
current document links and retained-reference integrity are JVM tests. No
function-by-function JSON inventory or Python documentation gate is required.
Use the headless invocation above, not unfiltered defaults.
Report actual executed versus cached checks and every exclusion. A clean release
check uses an isolated candidate and package/dependency inspection, without
starting a GUI; headless service composition must need no legacy worker, sound
library, external model or network prerequisite for MIDI.

| Boundary | Required evidence |
| --- | --- |
| Import | SMF 0/1, metadata defaults/maps, malformed input, velocity-zero note-off, unsafe pairing, polyphony, source bytes/digest unchanged |
| Authority | Exact PPQ/tempo/meter, chromatic chords, unequal/sub-bar windows, no gaps/overlaps, repeat identity, explicit extent/padding, scoped invalidation |
| Generation | Fixed-version determinism, legal ranges/harmony/boundaries, meter-aware patterns, melody space, complete grooves, bounded search, meaningful alternatives |
| Whole song | Plan intent across sections, neighbor/repeat dependencies, explicit rests versus failures, bass/kick coordination, ending and no global rewrite |
| State | Atomic writes, locks, no partial batch use/undo, stale async completion, scoped cancellation/retry, reopening, confined paths and artifact identity |
| Audition | Real synth/default endpoint, single session, play/pause/seek/loop/mute/solo, no stuck notes/resources, latest-wins preview, zero project writes |
| Export | Accepted-only snapshot, shared origin/end, channels/track names, allowed expression, manifest digests/privacy, semantic re-import, no overwrite |
| UI | Headless real-service/presentation-state workflow, exact lane data, focus/keyboard semantics, offscreen layouts at supported sizes and image-comparison failures; no live-window usability claim |
| Cleanup | Actual dependency paths scanned, no legacy routes/schema/audio/worker/Python runtime, verified data deletion and measured reduction |

Keep deterministic fixtures small and owned. Test actual outcomes/invariants;
source-text absence scans supplement behavior tests rather than replace them.
Every fixed bug receives a regression that would fail before the fix.

### Video feature validation

Video validation is independent of the MIDI/Logic gates and requires no MIDI
project, export, song or soundtrack. Ordinary tests use owned fixtures and fake
backends. They must prove isolated project storage, immutable reference/take/
export records, prompt and asset binding, bounded job recovery, no automatic
downloads or cloud fallback, actual moving-frame preview, exact assembly math,
and a decodable 1920×1080 H.264 MP4 lasting 180–300 seconds with zero audio
streams. Missing video tools, models or credentials cannot break MIDI startup,
audition or export. The app-level Video tab leaves all six MIDI destinations and
the one persistent MIDI player intact.

Retained local measurements cover short image-to-video, not full-length coherence.
VG6-01/02 requires three real 20–30-second clips through the app's production
services invoked headlessly, from finished artwork with
optional ready layers: base motion, contrasting motion using the same artwork,
and replaced artwork. No generated-look review is required. VG4/VG5 independently
prove exact continuous plans and bounded encoding, including a real 60-second
continuity/resource run before complete 180–300-second output. Subtle-motion reuse
is allowed; whole-clip repeat-to-fill is not. Validate cadence at 30 fps and disclose
native versus converted/upscaled action footage.

VG6-03/04 checks packaging and headless service/presentation integration with
owned/fake inputs, not installed-GUI behavior. VG6-05/06 requires the user to
review a real 180-second silent artifact and every join at normal speed (retaining
180–300-second support), verifying prompt/reference fidelity and repetition.
No interactive Apple-editor session is required. Technical checks, file decodability
and synthetic clips cannot supply the human artistic/release decision.

Tokyo, train, coffee and TABI are optional examples. Validation follows the
actual prompt and selected references; it never requires those contents. Local
generation is tried first. OPTIONAL VG-OPT-01 stays out of automatic selection until
the user explicitly chooses the hosted fallback after local evidence; any live
provider request also needs a reviewable capped budget.

### VG02 controlled preview proof

The opt-in `:videoPreviewProbe` uses production artwork import, preparation,
durable generation, controlled rendering, guarded publication and reconstructed
recovery. Ordinary `VideoPreviewHostCheckTest` cases replace external process
responses only; they are **synthetic wiring evidence**, not real MP4 decoding.
The 320×180 owned blink fixture is not 1080p or full-length delivery.

Native execution remains **WAITING_USER**: select canonical tools-directory,
Node-executable, Canvas-manifest and fresh output paths, and authorize the
150/600/900-frame ladder under its unchanged per-job limits (600 seconds, 4 GiB
native memory, 2 GiB staging, 2 GiB output, 512 MiB disk reserve, one process and
one attempt). No inference, download, hosted provider or automatic retry is part
of this check. Historical installed paths are not renewed authorization.

After admission, use `./gradlew :videoPreviewProbe` with all four properties:
`-PvideoToolsDirectory`, `-PvideoNodeExecutable`, `-PvideoCanvasManifest`, and
`-PvideoPreviewOutput`. The output must be an absent child under `build/vg2/`.
Retain the exact source manifest/Git-and-diff identity alongside command logs;
retain the output's budgets, single job ledger, pinned requests, source and take
files, per-job result/failure receipts and `frames-*/` evidence. That evidence
checks independent source/published full decode, stream/viewport/count/duration,
every rational presentation timestamp, first/middle/final frames, both sides of
300-frame boundaries and the authored blink phases. Recovery rejects any native
launch and must leave jobs, takes, selections and revisions unchanged. Inspect
the retained moving media separately: technical checks grant no human approval.
No native preview ladder receipt is claimed for the VG02 continuation yet.

### Q03a clean native installation and startup

**Historical procedure, removed from current validation gates.** Do not execute
its GUI launch steps under the current non-interactive policy. Retained outcomes
are not reinterpreted as current package or startup proof.

Recovery on 2026-09-13 **PASS**: clean uncached architecture and 22 focused
startup/install/six-page/frame checks, then 581 full tests, build and the actual
private DMG installation. Evidence: `~/.codex/melotrail-terra/final-engineering-recovery-evidence/`
(`q03-clean-architecture.log`, `q03-focused.log`, `q03-repaired-test.log`,
`q03-build.log`, `q03-native-install.log`, `q03-native-install/native-install.json`).
The installed launcher used bundled Adoptium 21.0.11+10-LTS on macOS 26.6.2/arm64,
opened an 802×621 native window, exposed all six routes and closed its MIDI session.
Every installed payload byte/symlink and protected input remained unchanged.
Current MIDI production Kotlin: 73 files / 27,702 lines, 62.4% fewer lines than
the recorded 73,677-line baseline; the then-independent Swift companion is excluded. The
installed MIDI image is 182,911,648 bytes; no package-size reduction is inferred.

The first full run exposed a companion early-exit race: 150 ms could expire
before a failing process exited on a loaded host. The bounded observation is now
two seconds, with delayed-crash and persistent-GUI regressions. This detects early
failure, not later crashes or application readiness. The original failure log/XML
are retained; focused repair checks and the full rerun pass. Independent review
resolved the finding. Human visual, listening, Logic and release gates stay pending.

On the graphical macOS coordinator, use the isolated candidate checkout with
fresh build outputs and the configured JDK 21/Kotlin 2.2.21 toolchain. Keep older
evidence outside this checkout before cleaning; never clean a user project or
reuse an earlier evidence destination. Run from the repository root:

```bash
Q03_EVIDENCE="$(mktemp -d "${TMPDIR:-/tmp}/melotrail-q03a.XXXXXX")"
./gradlew clean
./gradlew :test --no-build-cache --rerun-tasks --tests '*TargetArchitectureRulesTest'
./gradlew :desktopApp:test --no-build-cache --rerun-tasks \
  --tests '*MidiCoreDesktopStartupCheckTest' --tests '*MidiCoreNativeInstallCheckTest' \
  --tests '*MidiCoreDesktopCompositionTest' --tests '*MidiCoreFocusedWorkflowTest' \
  --tests '*MidiCoreNativeResponsivenessTest'
make test
make build
./gradlew :desktopApp:nativeInstallSmoke --no-build-cache \
  -PnativeInstallDirectory="$Q03_EVIDENCE/native-install"
git diff --check
```

Retain the command logs and executed/cached task outcomes alongside
`native-install/native-install.json`. Also retain the fresh six-page workflow
captures/packages in `desktopApp/build/test-results/midi-core-focused-workflow/`
and real-window frame replays in `desktopApp/build/test-results/u07/native/`.

`nativeInstallSmoke` packages the DMG, mounts it read-only, copies its app into a
new private installation directory, compares every payload byte/symlink, detaches
the image, then invokes that installed native executable. It uses an empty
working directory, a restricted environment and the explicit `--startup-check`
argument, which isolates preferences/logs and exits after the production shell
has crossed frame boundaries and released its sole MIDI session. Success requires
the bundled JVM and installed application classes, a realized supported-size
window, a complete startup report and a zero process exit within 60 seconds.
The ordinary launcher still opens the interactive workspace.

The report binds Git/diff/input hashes (including new uncommitted source), actual
macOS/JVM identity, DMG and installed-file hashes/sizes, dependency JARs, current
source counts against the recorded 73,677-line historical baseline, and unchanged protected MIDI,
UI/TABI references and Logic captures. Tracked payload, source-line reduction and
package size are separate measures; no historical package-size delta or new data
deletion is inferred. Failed checks retain INCOMPLETE evidence and diagnostic logs.
The existing real-service workflow tests own the full six-page MIDI path; startup
alone does not establish it. Acoustic playback and foreground compositor pixels
remain NOT_MEASURED here. `nativeDesktopCapture` and human review remain the final
U07/Q03 gates; Q03b owns the integrated review handoff.

## Preserved superseded companion preflight evidence

This entire section records technical checks for retired V01–V07 Swift code.
Its external evidence remains after V31 removed the repository owners. The
historical commands below are not runnable in this checkout. None of it validates, configures or constrains the planned Video tab,
and it cannot satisfy V24 or V33.

V07a recovery (2026-09-13) passed 572 JVM tests, required build, focused
export/launcher/UI checks and the separate native regression/release/install
check. Evidence is in `~/.codex/melotrail-terra/final-engineering-recovery-evidence/`
(`v07-focused.log`, `v07-test.log`, `v07-build.log`, `v07-companion-final.log`
and `v07-companion/`). The original two shortened exact-text assertions were
corrected. Image inspection also caught transparent/blank intake controls;
opaque rendering, readable native buttons and a dark appearance now have pixel
and Aqua-host regression coverage. Independent review resolved the contrast
finding. Three-size MIDI captures and fresh native intake images were inspected;
this is technical rendering evidence, not the final human visual decision.

Historical V07a coordinator checks were `MidiCoreMidiPackageExporterTest`,
`MidiCoreCompanionLauncherTest` and `MidiCoreExportPageTest`, plus the removed
companion check and the required MIDI `make test`, `make build`
and `git diff --check`. The export tests exercise the real published snapshot:
successful/failed launch, changed project revision/acceptance, unrecorded snapshot
and corrupted manifest all preserve project/source/candidate/export bytes.
The process tests cover absent/incompatible/removed installations, bounded
timeout/output, crash and literal Unicode/spaced arguments. Export UI tests use
the shell caller, including absent, current, stale and launch-error states.

Inspect the fresh `desktopApp/build/test-results/midi-core-export-handoff/`
captures at 1536×1024, 1280×900 and 720×900 (the test module's working directory
owns the `build` path). The native check retains `export-handoff.png` beside
its existing editor fixtures, plus `handoff/handoff-window.png` and
`handoff/handoff-observations.json` under its fresh `.build/v05b-editor-evidence.*`
directory. It separately installs the release executable into an owned new
directory, refuses a second install there, launches its actual handoff entrypoint
and captures a rejected snapshot identity. Native control regressions select
the owned finished soundtrack, reject a timing mismatch, confirm alignment,
save a new timing request and open the existing asset-backed editor session.
Changed manifest/soundtrack bytes and MIDI-project/snapshot/symlink save targets
must reject without writing musical inputs. These are technical fixture checks;
final Logic listening, foreground captures and production TABI review remain
deferred to the Q03b/manual handoff. No new Logic sound or artistic approval is
inferred from optional launch behavior.

V01's companion checks were separate from the MIDI application. The historical
coordinator dispatched the boundary check, native
regressions and Swift release build. No Gradle companion shim is needed. The
coordinator also runs normal MIDI `make test`/`make build`; the root settings do
not include the companion.
Record the actual macOS/Swift build, output directory, elapsed encode time and
bytes for any new encode; do not reuse the owned one-second fixture as full-song
evidence. The short probe must decode its
first/middle/final frames, one video and one audio stream, one-second duration,
and the shared PCM timeline.

The coordinator must also confirm that the regression's fresh temporary outputs
are outside the repository and that a failed/extra command-line path is rejected.
These checks prove only the declared local fixture configuration. A 1920x1080
master, stereo Logic bounce, H.264/AAC delivery, interactive playback, signed
distribution, provider submission and final A/V pilot require their assigned
V04–V07 evidence; no test substitutes for rights, budget or human visual review.

V02a native checks cover manifest reload, exact approved versions, missing/changed
media, dimension mismatches, duplicate/missing pins, traversal/symlink escapes and
no-overwrite manifest publication. Production rights/identity approval remains V02.

V02b native regressions cover immutable local imports, measured pixel alpha,
mask dimensions/type, normalized placement and scene/identity review findings.
A 30-second import watchdog catches nonterminating filesystem ancestry walks;
owned Git directory/file markers, bare repositories, symlink aliases with nonexistent children and
symlinked originals must reject before writes. Production asset approval remains V02.

V06a native regressions use real owned child processes: Unicode/spaced paths,
collision and source preservation, crash/disk errors, output limits, empty output,
timeout/cancel (including before launch), finite limits and staging cleanup.
Input-mutating encoders cannot change originals; parent-exit/timeout/cancel
fixtures stop real writing descendants. Noisy diagnostics/progress stay bounded,
blocked callbacks cannot stall supervision, and credentials are redacted. This tests
the process/publication boundary; V06 still owns episode codec/parity validation.

V06's native episode regressions encode owned 320×180 mono (one second) and
1920×1080 stereo (twelve seconds) compositions to ProRes 422/PCM MOV at 30fps.
Decode first/final frames, normalize
both images to sRGB RGB over black, and require mean absolute channel error ≤32
on the 0–255 scale. Verify one video/audio stream, dimensions/cadence, continuous
PCM timestamps, duration and A/V drift within one frame. Compare decoded
32-bit float PCM source/output sample digests exactly, retaining channel count
and sample rate in provenance. Transparent pixels must
not expose uninitialized encoder memory. Video and audio are fed in timeline
order, with output-size/disk/deadline/cancel checks through finalization.
Late cancellation, tiny timeout/output limits, insufficient disk, stale assets,
Unicode/spaced names and video-only/report-only collisions must preserve sources
and previous outputs and leave no completed failed delivery. Provenance records
the output/soundtrack/composition digests and exact asset pins; the report is
reserved before the video publication point. Set `MELOTRAIL_EPISODE_EVIDENCE` to
an external directory to retain fresh MOV/report pairs from the native check.
Repair evidence: `~/.codex/melotrail-terra/v06-repair-evidence/` (native log,
retained outputs and MIDI test/build logs). These owned technical
fixtures do not approve a full-song 1080p master, stereo Logic bounce, production
TABI identity, musical quality, rights or upload; those remain V07/user gates.

V03a uses fake providers only. Real concurrent processes and threads exercise identical
requests, independent requests, in-flight/cost rejection, preserved provider IDs
and directory aliases against one ledger; durable job and provider-call counts
must agree. Sequential checks cover unknown quotes, retries, cancellation,
uncertain submission and reopen. No paid request or production approval is made.

V03b checks use an injected HTTP transport and owned video bytes. Valid owned MOV/MP4
content must survive a `.tmp` staging name through quarantine and manual import;
invalid clips, HTTP partial/error responses, size/digest mismatches and repository
aliases reject without publishing staged files. Credentials remain absent from
output-host requests and errors. A custom URLProtocol exercises the concrete
URLSession collector: reject HTTPS downgrades before following them, reject
insecure final URLs, and abort declared or streamed oversize bodies. Matching
hash symlinks reject; oversized/invalid-ratio stills never reserve budget or call
HTTP. Source-swap fixtures prove preparation rejects changed bytes before ledger
creation and freezes verified upload bytes against later file replacement.
No live provider request is part of validation.

V04a native regressions use the owned one-second MOV as a finished-soundtrack
fixture plus a temporary digest-pinned fixed-tempo MIDI export manifest. They
prove repeatable rational section/frame ranges, explicit lead-in/tail, absent
manifest fallback, digest rejection, shorter/longer bounce and changed-tempo
mismatches, and byte preservation for the soundtrack, manifest, and a protected
project sentinel. Decoded rational validation, overlapping/gapped manifests and changed soundtrack
rejection are also covered; the read-only `plan-timing` CLI prints the shared JSON plan.
This is timing-plan evidence only: composition, preview,
encoder parity, and human A/V approval remain V04–V07 work.

V05b's release-window regression launches the native editor with an owned
composition request, selects a scene, plays across a boundary, seeks and closes.
It verifies player/observer cleanup and captures both the usable window and an
invalid-input window. Capture guards reject clipped inspector/transport/scene
controls. The Swift-owned loading window disables AppKit release-on-close;
this launch/close path covers the recovered release-only ownership crash.
Evidence: `~/.codex/melotrail-terra/v05b-repair-evidence`; fixtures are technical
media, not production TABI identity approval. Final human visual approval remains pending.

V05c exercises real crop/motion/crossfade controls and compares rendered pixels
at boundaries against the shared renderer. An independent bitmap oracle proves
crop moves the complete scene, including its mask, together. Minimum-window
capture verifies the inspector viewport and scrolls every editing field/button
fully into view. Before/after crop and motion PNGs are retained with the owned
fixture. Invalid edits preserve the accepted plan, source bytes and sole player.
Evidence: `~/.codex/melotrail-terra/v05c-repair-evidence`.

V05d drives native save/open and local Stop/Play/Restart controls. It compares
saved/reopened pixels at the same soundtrack frame, checks transactional restore
rollback on refresh failure, and retains the original player. Reopen rejects
changed soundtrack/asset bytes and malformed documents, then recovers after
repair. Save rejects unrelated files, protected inputs and symlink aliases.
Owned ledger fixtures distinguish actual/estimated costs and explicitly label
uncertain submission progress. Evidence:
`~/.codex/melotrail-terra/v05d-repair-evidence`; no provider requests or production
approvals are part of this check. V05 completes the technical keyboard/UI gate below.

V05's release-editor check uses the same owned multi-scene request at
1536×1024, 1280×900 and 720×900. It retains
`editor-1536x1024.png`, `editor-1280x900.png`, `editor-720x900.png`,
`editor-controls.png`, `editor-window.png` and `editor-observations.json` under
the fresh companion `.build/v05b-editor-evidence.*` output. The JSON records
each preview/inspector/scene-strip/transport rectangle, whether the compact
stacked layout applied, real keyboard frame seeking, the last preview frame and
the exact final audio-tail end frame. The release check drives scene selection, play and seeking; the native
controller regression drives crop/motion/crossfade editing, save/reopen and
field-safe keyboard handling; it also checks shared-resolver pixels at scene boundaries and source
digests. These are owned-fixture engineering measurements, not production TABI
identity, artistic, rights, listening or paid-pilot approval.
Repair evidence is retained at `~/.codex/melotrail-terra/v05-repair-evidence/release`.
The captures use exact content sizes in points (Retina PNGs use backing pixels),
with an evidence-only override of the screen-height cap. The adapted reference-08
geometry is checked within 8 points: 20-point side margins, a 280-point right
inspector at wide sizes, and a full-width 150-point inspector below the preview
at 720 points. Output frame dimensions remain unchanged on resize. Inspector
controls retain their native field editor during seeks; real typing and arrow
function-key modifiers are exercised. The owned one-second fixture ends at
frame boundary 30, with final preview frame 29 at 30 fps; it is not a production
TABI episode or a listening approval.

## Scheduled runner checks

For runner changes, run `node --test tools/terra-runner.test.mjs
tools/terra-throughput.test.mjs` in addition to the application gates. Fixtures
use real Git worktrees, commits and child processes with fake model/build
executables. They must prove shared task/time/token bounds, preserved retries,
implementation-session reuse, fresh review, bounded evidence, disjoint parallel
ownership, serial integration and validation of the combined candidate. A live
CLI smoke check must confirm persistence/resume when its argument wiring changes.
Keep full transcripts outside model input; review only the current candidate's
explicit completed check logs. Passing runner checks does not approve MIDI music,
Logic behavior or visual output.

Finding recovery additionally proves ordered partial repairs without premature
integration; one fresh session and fixed file scope per finding; independent
acceptance resolution; and preservation of the original implementation, diff,
check errors and usage history. Regressions reject no-progress loops, malformed
or omitted findings, scope/checkpoint changes, unauthorized/human recovery,
exhausted time/tokens and replay after an interrupted attempt. Evidence for A03
is retained at `~/.codex/melotrail-terra/finding-recovery-evidence`.

## Musical evaluation

The user's 2026-09-06 **5/10 average** is qualitative baseline feedback: timing
good, piano/melody fit inconsistent, full-song structure difficult. No case-level
scores or identified bad bars were supplied. Do not invent them.

M01 creates owned development fixtures: held/close melody-piano intervals,
short passing tones, expressive/pedaled melody, low register, dense/sparse phrases,
unequal harmonic rhythm, repeated chorus/bridge, 3/4, 6/8, pickup/silence and
final boundaries. Freeze authority/style/seed and baseline output hashes.
Use these to locate bugs; they cannot be renamed as unseen acceptance songs.

At each meaningful engine milestone, provide a short baseline/new MIDI pack
with the same melody, harmony, timing and preview/Logic instrument mapping.
Include isolated piano+melody and full arrangement loops at identified bars.
Record the instrument setup; differing patches or role levels can confound
judgment. Alternate/blind A/B ordering where practical, and retain failed takes.

Q01 evaluates five varied full-song projects, at least three unseen before the
final evaluation starts. User-owned or clearly licensed material only. Freeze
hashes and authority before generation. No tuning to the unseen set before its
first scores. If fixes are made from a failed case, reclassify it as development
and replenish the unseen minimum for the next final evaluation.

Score **1–10** for piano/melody fit, bass support, drums/groove, role interaction,
section development/transitions, usefulness of repair alternatives and overall
readiness to continue in Logic. Give a reason and exact bars for a low score.
Melody preservation is an automated exact invariant, not a taste rating.

Proposed release thresholds:

- Zero changed protected melody events and zero unresolved structural blockers.
- Median overall **and** piano/melody-fit scores at least 8/10; every song's
  overall and piano/melody-fit score at least 7/10.
- No core-role, interaction or structure score below 6/10; no severe clash,
  broken transition or timing fault requiring external MIDI repair to proceed.
- Median first-draft-to-Use time at most ten minutes, excluding authority entry
  and Logic instrument selection; record those excluded times separately.
- Same final engines pass all cases; averages cannot hide a failed song.

These thresholds are targets, not observed results. The user is the minimum
reviewer for this personal product; another musician's feedback is useful, not
a development blocker. A failure gets a scoped task and regression, not an
unbounded generator rewrite or lowered acceptance threshold.

Compact evidence record (JSON or table in ignored output; summarize real results
here at release): source/authority hashes, ownership, seen/unseen designation,
build/engine/style versions, seed, candidate/snapshot IDs, instrument mapping,
bar range, all scores/reasons, repair count/time, reviewer/date and decision.

### Frozen musical evaluation commands (Q01a)

`musicalEvaluation` is a local Kotlin command using the real draft → Use →
accepted-only exporter in newly created evaluation projects. It never confirms
analysis/style proposals or writes a supplied project. Prepare source, exact
authority and a confirmed arrangement plan in the app first. Freeze the complete
supplied set before generating final candidates; do not tune unseen songs before
their first ratings. New directories require an existing parent and must be
outside supplied projects, previous evaluations and frozen sets.

```bash
./gradlew musicalEvaluation --args="freeze '/path/to/request.json' '/path/to/new-frozen-set'"
./gradlew musicalEvaluation --args="export '/path/to/new-frozen-set' '<printed-sha256>' '/path/to/new-evaluation'"
./gradlew musicalComparison --args="'/path/to/new-development-comparison'"
```

The first command prints the SHA-256 of `frozen-set.json`; retain it separately
and pass that exact hash to export. Freeze copies the verified source/import
report, exact supplied project document and a current-schema input project
containing its original authority/plan without decisions or output history.
Optional baselines are copied from an explicitly named existing accepted export
snapshot of the supplied project. They must cover all active scopes with the
same source and confirmed authority, and retain a saved full draft binding those
exact candidates to the requested generation seed and style. Mismatched or
unverifiable baseline settings reject freeze/export; choose the original settings
or omit that baseline. Unavailable baselines are labeled, never regenerated by
an old engine. Source/project/settings hashes, compiled engine
and runtime-library hashes, catalog versions, build label, style, seed, loops,
instruments, ownership and exposure declarations bind the set. Export rejects
changed files, settings, versions, symlinks and existing destinations.

Request JSON has `schemaVersion: 1`, `evaluationId`, `buildIdentity` (record the
actual commit and whether it has uncommitted changes), and `cases`. Each entry
has `projectDirectory` (absolute or relative to the request file) and `case`:

Use `MidiCoreEvaluationCase` in
`src/main/kotlin/app/melotrail/application/MidiCoreMusicalEvaluationModel.kt`
for the exact case fields. Record actual ownership/exposure, full-song/synthetic/
tuned flags, variety, style/seed, inclusive piano+melody/full-arrangement bar loops,
one real patch/level for each of melody/chords/bass/drums, and an optional
`baselineSnapshotId`.

Classifications are
`FINAL_UNSEEN`, `FINAL_SEEN`, `DEVELOPMENT`; ownership is `USER_OWNED` or
`CLEARLY_LICENSED`. Synthetic, partial or tuned material must be development.
Identical note streams cannot count twice as final songs or appear in both sets,
including MIDI with changed metadata or channel wrappers. Development-only
variants remain allowed. The command validates declarations and integrity; it
cannot establish rights, full-song variety or unseen history on a human's behalf.

Output separates `final/<case>` and `development/<case>`. Each case contains
`current/`, optional byte-preserved `baseline/`, the isolated `project/` and blank
`scores.json`; copied frozen inputs stay under `frozen/`. `evaluation.json`
records package digests, candidate/snapshot IDs, role-generator versions and any
failed attempts. Source preservation and semantic re-import use the existing
exporter. The same frozen inputs/runtime produce identical MIDI, package
manifests and score templates; working-project candidate/draft timestamps are
provenance and may differ. The fixed package timestamp is not a listening date.

`review.md` gives piano+melody/full-song loops, the recorded instrument setup,
alternating disclosed A/B order and missing-song counts. Fill all seven 1–10
scores, reasons/exact bars, repair count/time, excluded authority/Logic setup
times, reviewer/date and decision in each score form. `scores` rates current;
`baselineScores` rates the supplied baseline, when available. Preparation's automatic
Use authorizes only its disposable copy and does not measure human review time
or approve the musician's arrangement. Exit code 0 means preparation ran, even
if songs/ratings are missing; 1 rejects a request or changed input, and 2 retains
generation failures in the published report. No failed case is silently omitted,
retried with another seed or replaced with a planned rest.

No owned final songs were supplied for this slice: **five final songs and all
three unseen songs remain missing**. The only bundled request is the empty
`src/test/resources/fixtures/q01-evaluation/empty-request.json`. The M01 command
uses its existing immutable baseline resources and synthetic fixtures, with
runtime hashes in `versions.json`; it cannot populate the final set. Reclassify
a tuned final case as development and replenish the unseen minimum for a new
evaluation. Real ratings remain Q01; actual Logic import/play/reopen remains Q02.

Q01a recovery checks and generated packets are retained at
`~/.codex/melotrail-terra/q01a-q02a-repair-evidence/`. Focused evaluation,
comparison, exporter and draft regressions, `make test`, `make build`, diff check
and independent review validate preparation. `q01-frozen/` and `q01-evaluation/`
record the empty supplied set; `q01-development/` contains synthetic M01 pairs.
After Q02a changed export filtering, `q01-current-frozen/`,
`q01-current-evaluation/` and `q01-current-development/` repeat preparation on
the combined implementation; earlier packets retain their original runtime identity.
No final-song freeze or human score is claimed.

## UI and performance acceptance

**Scope:** the live-window, acoustic-onset and interactive walkthrough requirements
in the retained procedure below are superseded by the non-interactive policy.
Current checks use offscreen images/semantics and service timing only. Historical
unmeasured/failing results remain unchanged; no replacement usability pass is claimed.

Follow [UI guideline](UI_GUIDELINE.md). Six ready pages plus relevant empty,
blocked/error/progress states at 1536×1024, 1280×900 and 720×900. Test short-window
resize and native scaling. Record actual/expected/diff paths, font/density,
geometry/contrast/focus results and the approved baseline change when applicable.
A comparator must demonstrably reject changed layout/colors/lanes/radii.

User review scores hierarchy, reference fidelity and usability at least 4/5 for
each page, with exact comments for deviations. Passing screenshots is not passing
musician comprehension. Record actual completion of create/import/context/style/
draft/repair/use/undo/reuse/export without project-file edits.

Targets measured on a recorded reference machine using a fixed 64-bar,
four-role, 4/4 fixture and an additional 3/4/6/8 smoke set:

- Authority-ready draft playback: at most three primary actions.
- Section repair: select section + action; role repair at most three actions.
- Preview plan preparation p95: cold ≤1,000 ms; warm ≤300 ms, at least 20 samples.
- Measure actual audible onset separately after output warm-up; target ≤300 ms
  warm / ≤1,000 ms cold. In-memory preparation is not acoustic-onset evidence.
- Full 64-bar draft generation target p95 ≤10 seconds; instrument separately
  from UI wait time. Capture event count, disk state and cancellation latency.
- Long work stays off the Compose event thread. The collapsed player remains
  visible and within its height budget; rapid previews never overlap sessions.

Targets missing on the reference machine require profiling and an explicit
budget decision with evidence. Agents must not claim measurements they did not run.

### U07b responsiveness and visual-review preparation

**Historical procedure:** the graphical-host commands and mandatory capture gate
below are retired, not instructions for current runs. Use the headless selectors
and exclusions described above; retain old artifacts without replaying UI tests.

Historical recovery evidence: `~/.codex/melotrail-terra/u07b-repair-evidence/u07/`.
Use the current Q03b packet linked above for final review; the earlier packet
does not establish the current build.
Open `visual-review/index.html` for all six pages: 66 unchanged U07a target/
actual/difference comparisons at three guideline sizes, original references,
and blank per-page scores. **Human visual approval remains U07; acoustic onset
is UNMEASURED** because the real audition controller uses a silent output adapter.
No golden, musical score or Logic result is changed by these checks.

Run on a graphical host with the configured JDK before `make test`, `make build`
and `git diff --check`; force fresh measurements with these desktop selectors:

```bash
./gradlew :desktopApp:test --rerun-tasks \
  --tests '*MidiCoreResponsivenessTest' --tests '*MidiCoreNativeResponsivenessTest' \
  --tests '*MidiCoreVisualReviewTest' --tests '*MidiCorePinnedVisualTest' \
  --tests '*MidiCoreFocusedWorkflowTest'
```

`responsiveness.json` retains 20 raw samples and nearest-rank p95 for cold/warm
preview preparation (targets 1,000/300 ms), 64-bar draft generation (10,000 ms),
UI completion and cancel-to-terminal latency. Cold means a fresh service graph
and preview cache, not flushed OS/JIT caches. Generation includes validation and
publication; UI completion includes IO/state delivery but excludes paint. An EDT
heartbeat records queue delay, and use-case/projection assertions reject UI-thread
work. Cancellation follows one published scope; source/candidates, acceptances
and export snapshots survive without publishing an incomplete draft. Separate
3/4 and 6/8 smoke cases explicitly confirm meter; rapid previews prove latest-wins
and one output session. Missed budgets fail after writing evidence.

`native/observations.json` records 28 Skia frame replays from a real window
with a 256-bar, 8,192-note, 64-occurrence song and long Unicode names. It requests
1280×720, 1024×768, 720×900 and back to wide; actual client size and density are
recorded. Only excess beyond usable display/frame bounds is reduced. Checks cover
one player/inspector, lane alignment, 48 dp transport controls, selection surviving
resize and scrolled/focused draft creation. Full-client layer bounds and image
size/header/footer/palette guards remain enforced. Replay verifies rendered frame
content; its `onscreenCompositorCapture` is explicitly `NOT_MEASURED`.

Actual desktop pixels require a separate fresh check on a visible, capturable desktop:

```bash
./gradlew :desktopApp:nativeDesktopCapture
```

This is mandatory during the final U07/Q03 manual visual/release review, independently of
`make test`. It uses Robot with the same guards, **without fallback**, writing only
`native-screen/`; failures retain `last-capture.png` and INCOMPLETE observations.
By the user's final-review order it is not a prerequisite for Q01a/Q02a/Q03a/
Q03b or other independent engineering; its failure is retained for final review. Earlier U07b
screen captures remain historical: subsequent scheduler runs captured wallpaper,
so they cannot establish current compositor success. Recovery evidence is in
`~/.codex/melotrail-terra/q01a-q02a-repair-evidence/`; the explicit screen check
still fails on this host, while real-window frame replay passes. Human visual
approval remains outstanding.

Reports include exact input-file hashes, Git identity and actual machine facts;
verify matching `inputTreeSha256` across timing, meter, native and review reports.
The repair host is Apple M5 Pro / 48 GiB, macOS 26.6.2, JDK 21.0.11, APFS,
with native density 2. Interrupted measurements remain INCOMPLETE. Consult the
retained raw reports for timings and actual display-constrained dimensions.

M08a repair evidence is retained at
`~/.codex/melotrail-terra/m08a-repair-evidence`. Reduced-density held-melody
regressions keep Bass attacks at ticks 0 and 960 across C/F windows at densities
0.25, 0.5 and 1.0 for moving and sustained-root patterns. An incompatible 0.01
ceiling produces a density finding; zero density stays silent. Core/desktop
checks also cover chord spacing, approach resolution, shared groove in 3/4 and
6/8, deterministic role fingerprints and the existing full-draft caller. Planned steady grooves preserve intro/outro
zero-support limits; pickup advisories share generator eligibility, including
restrained and compound-meter cases.
These are engineering checks. Full-song M08 comparison packages and Q02 Logic
listening remain separate pending work; no musical score is inferred.

M08b adds deterministic Drum checks for a two-bar phrase pickup into the next
section, a one-bar harmony edge without inferred fill, quiet current/next
Intro/Outro pickup suppression, and repeat-family whole-groove variation. The coordinator still
owns execution of these focused checks plus the required full test/build gates;
these automated checks do not establish a listening or Logic result.

U05a captures at `~/.codex/melotrail-terra/u05a-implementation-evidence`
show the reference-adapted four MIDI lanes, five style choices and full-draft
CTA at 1536×1024 and 1280×900. Tests check entire style-card bounds before
scrolling, including the 24-point gap above the persistent player; 720×900
checks selection and CTA reachability by scrolling. The selected-section
inspector distinguishes accepted planned rests from rests in an unaccepted draft,
and current accepted candidates retain precedence over a simultaneous draft rest.
Full workflow/preview repair behavior remains U05b/U05; these captures are
engineering evidence, not a recorded human visual approval.

M08's retained baseline/current comparison packages are at
`~/.codex/melotrail-terra/m08-repair-evidence/comparison-packages/review.md`.
An explicit uncached run of `MidiCoreComparisonHarnessTest`,
`MidiCoreBassGeneratorTest` and `MidiCoreDrumGeneratorTest` generated three
pairs: repeated chorus/bridge, 3/4 and 6/8. The coordinator verified all 30 MIDI
file hashes, six export-manifest hashes and matching protected-source hashes.
Frozen baseline metadata retains its historical authority hash; the harness
compares current-contract authority without rewriting the baseline files.
Each current package has `coordination.json` with terminal role states, planned
rests and exact role-generator versions. The four-bar case preserves melody
and all MIDI track end markers at tick 7680 while its final Bass/Drums scopes
are planned rests from tick 5760. Focused/full/build logs and digest verification
are retained beside the packages. Human A/B ratings and Logic playback remain
Q01/Q02; these fixtures do not establish musical acceptance.

M09 repair evidence is retained at `~/.codex/melotrail-terra/m09-repair-evidence`.
The real-service owned two-occurrence workflow previews without writes, repairs
all affected neighboring scopes, checks immutable piano-boundary hashes and
rejects unaccepted dependencies, tampered boundary metadata and colliding batch
history without partial writes. Complete Use commits once; lower-register
intent/offset identity survives publication, acceptance and reopen. Source and
prior candidate bytes remain identical. The retained project contains baseline
and repaired accepted-only export snapshots for equal-position comparison.
Core tests reject cosmetic alternatives and stale/rejected choices; the full
suite also covers single-scope repairs, limits, locks, cancellation and audition.
Retry coverage verifies one plan confirmation and identical generation settings
across repeated attempts after a no-result failure.
These checks do not score musical usefulness. For Q01/Q02, compare the snapshots
with identical Logic instruments, verify melody/role origins, piano continuity
at bar 2, lower-register intent and exact end at tick 3840, then save/reopen.
Arrange buttons and final UI flow remain U05b/U05; musical and Logic approvals
remain pending human evidence.

U05b recovery evidence is retained at `~/.codex/melotrail-terra/u05b-repair-evidence`.
Focused StylePreview, ArrangePage, Workspace and real-workflow checks execute
with `--rerun-tasks`; full test and build runs also force execution. The one-bar
fixture loops its real 1920-tick window without project writes. Three-size
Arrange captures show scoped repair preview, Apply/Cancel and the persistent
player. The failed-Apply regression distinguishes a saved plan from unaccepted
alternatives and dispatches the same repair on Retry. These are technical checks;
full Arrange completion remains U05 and human musical/Logic approval stays pending.

U05 recovery evidence is retained at `~/.codex/melotrail-terra/u05-repair-evidence`.
Uncached focused checks and full test/build execution cover a real three-action
ready-authority → playing draft path, keyboard Enter on Confirm plan & create,
plan-confirmation cache parity, changed-plan silence and protected melody.
The intro regression uses three separate occurrences, rather than mistaking one
three-bar section for an intro. Injected post-confirm generation failure retains
the saved plan and same draft retry; cancellation at preparation and playback
boundaries stops both the actual transport and displayed state. A deterministic
clock reproduces the intermittent repair-batch failure at mixed fractional
timestamp precision; both history lists now compare parsed instants. Batch
accept/reopen and draft-history schema roundtrips pass, while reversed
chronology remains rejected. Three-size
proposal/playing-draft captures were inspected. For Q02, audition the complete
draft before acceptance, then Use/export with identical Logic instruments and
verify melody preservation, planned rests, section boundaries and role alignment.
Musical/visual approval remains human evidence.

U06a recovery evidence is retained at `~/.codex/melotrail-terra/u06a-repair-evidence`.
Focused regressions cover exact Draft/Accepted identity including planned rests,
atomic mixed-draft Use/Undo/reuse, section/loop continuity, locked-scope routing
and melody-inclusive alternative playback. The two-section blocker fixture now
sets its confirmed arrangement end consistently. Candidate lanes require the
player's exact immutable candidate identity; changing selection cannot display
another candidate's notes or attribute its unbound error to the playing one.
Real-service Review captures at 1536×1024, 1280×900 and 720×900 were inspected.
For Q02, compare alternatives against the same protected melody and section loop,
then Use/Undo/reuse and verify accepted-only export in Logic. Musical, Logic and
visual approval remain pending human evidence; final Export handoff belongs to U06.

U06 automated coverage is complete and rerun in the final Q03b packet. Focused desktop selectors:
`MidiCoreExportPageTest`, `MidiCoreFocusedWorkflowTest`, `MidiCoreReviewPageTest`,
`MidiCoreWorkspaceTest`, `MidiCoreArrangePageTest`, `MidiCoreWorkspaceShellTest`.
Retain the root `MidiCoreMidiPackageExporterTest`, `MidiCoreAcceptedSongAssemblyTest`
and `MidiCoreArrangementDraftTest` checks, then run the required full test/build
and diff checks. Human visual and Logic approval remain pending.
The three-size real-service workflow now applies the Lower piano register repair,
creates the whole draft under that confirmed plan, uses/undoes/reuses it, exports,
reopens and publishes a second snapshot while comparing the first package's bytes.
The complementary two-section standard-style fixture covers accepted whole-song
Bass/Drums rests, inventory, atomic Use/Undo/reuse and omitted role files.
Transport uses a test port; these checks cannot establish audible playback quality.
After successful coordinator execution, captures and ready-to-import packages are
under `desktopApp/build/test-results/midi-core-focused-workflow/` and
`desktopApp/build/test-results/midi-core-export-rest/`, each with `reference-wide`,
`wide`, `compact` and a `logic-package/` per size. Inspect the actual PNGs beside
references 04/09; these output paths are preparation targets, not passed evidence.
For Q02, import each package at bar 1, compare complete-song and individual-file
alignment, verify the protected melody and exact ending, and verify Bass/Drums
are absent in the rest package. Check playback and Logic save/close/reopen with
fixed instrument choices. Record the real user's result before release.

## Logic Pro procedure

**Historical/optional reference, not an active validation or release gate.**
Current AC4-01 prepares and semantically validates packages without opening Logic;
AC4-02's interactive procedure is removed. Preserve prior compatibility evidence
without claiming it applies to the current build or requesting a new live session.

Rerun relevant cases when import/timing/expression, assembly, rests, export or
workflow handoff changes. GUI-only layout changes need UI validation, not a
fabricated DAW pass. GarageBand remains unverified/outside support.

Fixture set: SMF 0 melody, SMF 1 + meta track, short 1/2/3-bar songs, pickup and
initial silence, sub-bar/unequal chords, CC64/pitch expression, repeated sections,
3/4 and 6/8, optional padded end, inactive section/whole-song role, final notes
and fills at the exact end. Include the six historical fixtures below where
still valid. Update fixtures for changed contracts rather than reusing old
hashes as evidence of a new exporter.

### Q02a current matrix preparation

The coordinator runs the current command from the repository root, with a **new**
destination for each preparation:

```bash
./gradlew prepareLogicMatrix -PlogicMatrixDirectory=build/q02-logic-matrix/current-01
```

This test-classpath command calls the current import → explicit authority/padding
→ generation → acceptance → export services, reopens each owned project and
exports a second snapshot. It adds no production runtime or Logic automation.
Existing destinations and symlink ancestors are rejected; failures retain an
unpublished `.<destination>-incomplete-*` staging directory for inspection. No existing project,
source, accepted candidate, export or historical packet is replaced or cleaned.

The packet contains `matrix.json`, `review.md`, `SHA256SUMS` and 21 case folders:
20 current packages plus the expected rejection of the historical extra-note-track
input. Each positive case retains `source.mid`, its current `project/`, a portable
`package/` and `case.json`. The six historical source fixtures are byte-preserved;
short inputs receive explicit trailing padding. The historical
`smf1-reference-tracks.mid` contains a second note-bearing track and is now an
expected app rejection, with a separate supported meta-only-track probe.

Coverage includes velocity-zero note-off, 1/2/3 bars, pickup and initial silence, 3+1+2+2-beat and sub-bar
harmony, source end distinct from last note, explicit padding, 481 PPQ, whole-song
and introductory Bass rests, CC64/CC11/bend/channel-pressure/channel remapping,
release velocity, repeated occurrences, 3/4, 6/8, and an explicit final drum fill.
The brief sub-bar probe uses explicit sustained role patterns, preserving its
original C 0–240 / G7 240–1920 windows and 480-tick source. The pulsed style
produces an empty Chords selection for this short phrase; that failure is not
accepted as silence or repaired by moving the source/harmony. Its separate
regression requires nonempty sustained candidates at both exact chord starts.
The odd-PPQ package has explicitly accepted rests for all generated roles: import
and export retain 481 PPQ, but the generation grid cannot represent sixteenths.
It does not claim odd-PPQ pattern support. Bank-select CC0/32 and program hints
are omitted from arranged exports; supported expression and original bytes remain
protected. That policy is checked by the expressive channel-remapping case.

`matrix.json` binds the Git commit, binary diff and working input-tree hashes
(including untracked source), compiled classes, runtime jars/JDK, source and
selected-melody identity, confirmed authority/plan, catalogs, candidate versions,
settings/seeds, manifest hashes, MIDI byte hashes and semantic hashes. Every file
is covered by `SHA256SUMS`. MIDI/package manifests and the matrix are deterministic
for the same inputs/build; project lifecycle timestamps are observational and may
differ. The fixed export timestamp is a fixture setting, not a review date.
Re-import checks compare every note/expression field, track/channel order, conductor
tempo/meter/markers, individual-track EOTs, role presence and common origin/end.
Project reopen must preserve state and produce identical MIDI without changing
the first snapshot or source/candidate bytes.

Focused coordinator selectors: `MidiCoreLogicMatrixTest`,
`MidiCoreMidiPackageExporterTest`, `MidiCoreAcceptedSongAssemblyTest`,
`MidiCoreArrangementDraftTest`, `MidiCoreSourceImportTest`, and `JdkMidiWriterTest`,
followed by the required `make test`, `make build` and `git diff --check`.
The matrix test exercises the command and retains a fresh packet under
`build/test-results/q02-logic-matrix/run-*/packet/`; it checks repeatability and
detects corrupted notes/channels/controllers/end ticks, manifest/inventory changes,
collisions and failed publication. Recovery checks and current packages are retained at
`~/.codex/melotrail-terra/q01a-q02a-repair-evidence/`: `q02-focused.log`,
`q02-test.log`, `q02-build.log` and `q02-logic-matrix/`. Independent review
and automated semantic checks validate preparation; actual Logic remains pending.

Use `review.md` for concise complete-file and separate-role imports at bar 1,
instrument assignment, full/loop playback and Logic save/close/reopen. Verify
`SHA256SUMS` before and after review. Keep the packet immutable and record results
in a separate copy of the form, identifying `matrix.json` by SHA-256; attach actual
coordinator check evidence separately. Exact macOS/Logic versions, reviewer/date,
each import/play/reopen result, conditional actions and failure bars remain blank
until a human supplies them. Q02 owns that decision; Q02a does not inherit the
2026-08-28 approval or award a musical/release pass.

M03 requires the current Logic fixture preparation to include an unequal
3+1+2+2-beat harmony case, an odd-PPQ case, a source whose end-of-track trails
its last note, and an explicitly padded arrangement end. These fixtures are
prepared by automated contract coverage only; no Logic import/playback/reopen
result is claimed until a reviewer records it below.

M05 prepares frozen v1/current v5+comping-v1 MIDI pairs in
`build/m01-comparison` via `MidiCoreComparisonHarnessTest`. Engine
v5+comping-v1 retains M04 ranking of the bounded M04a voicing pool
with M02 overlap, register, per-finding metrical prominence, bounded phrase
lookahead, and an explicit authority-bound piano boundary input. It includes
held suspensions crossing onto a new chord’s downbeat. M05a adds meter-anchored,
harmony-clipped comping, authored 3/4 and 6/8 patterns and explicit
unsupported-meter rejection. M05 adds versioned phrase-aware support/answer/
inter-phrase whole-bar rests and final-beat suppression from protected melody
activity. Automated checks publish and reopen a three-bar candidate with a
silent middle bar, verify deterministic replay and unchanged source bytes,
and cover rest-only harmonic windows plus 3/4 and 6/8 bar phase. The generated
`review.md` is the short baseline/new listening pack; use identical MIDI
instruments and levels for each listed piano+melody and full-arrangement loop.
For Q02, import the complete and aligned role files for repeated-chorus/bridge,
3/4 and 6/8; verify protected melody, chord
boundaries and final notes, then compare the listed piano/melody loops with
identical Logic instruments and levels. The unscored `review.md` records hashes
and loop ranges. Listening scores and Logic results remain pending human evidence.
Automated state coverage cancels and reopens a multi-occurrence draft, rejects a
retained Chords candidate whose persisted boundary digest no longer matches the
preceding immutable MIDI, preserves its bytes, and publishes a distinct retry.
Completed-draft assembly and Use must also reject missing or mismatched boundary
evidence without changing acceptances.

The M04 boundary-retry regression uses `steady-road` for its three one-bar
occurrences. The same fixture with `open-sky`/seed 41 failed Drums role validation
in the first occurrence; short-section groove validation remains M08b/M08 work.
This does not count as a musical or Logic pass for that style.

1. Record app build, source/export manifest hashes, exact macOS and Logic version.
2. Import/open complete MIDI at song start; record adopt/retain tempo and meter.
3. Import each role file at the same origin. Check names, role/channel separation,
   expected omitted roles, initial silence, bars/beats and first/final notes.
4. Spot-check melody timing/pitch/velocity/expression against the protected source.
   Check no unexpected program/controller data; assign instruments and a drum kit.
5. Play full song plus loops/solo scopes: no stuck/truncated notes or wrong kit hits.
6. Check section markers (cosmetic display finding permitted), then save/close/
   reopen. Record PASS, CONDITIONAL PASS with exact action, or FAIL.

Timing/note/channel/role corruption is always a failure. A screenshot alone does
not establish full playback or reopen; a real reviewer report is needed.

M07b automated fixtures cover mixed candidate/rest draft audition, atomic Use/Undo,
revision-guarded unlock in both candidate/rest directions after plan edits,
stale-state preservation on Undo, locked-rest authority replacement, downstream
rest-dependency invalidation and acceptance guards, desktop section repair and
preview at three fixture sizes, cancellation/retry and piano-boundary reset
across an inactive occurrence. M07 adds an accepted all-song-rest export fixture:
the inactive role is absent from the complete/song-role files and snapshot,
listed as inactive in the manifest, while Melody and active role files retain
their source origin and exact arrangement end. Schema v4 rejects prior schema
versions before writes. Current Logic playback evidence remains pending; Q02
owns its matrix, including a real import check of this changed track count and
role-file omission. The complementary two-bar fixture accepts an introductory
Bass rest and enters at tick 1920 (bar 2), ending at tick 3840. Re-import compares
accepted notes in both `bass.mid` and `complete-song.mid`, checks every emitted
file's PPQ/end, and preserves source bytes. Ready-to-import packages are retained
at `~/.codex/melotrail-terra/m07-repair-evidence/logic-packages-verified/`:
`all-song-bass-rest` and `intro-bass-rest`. Import at song origin in Logic, verify
Bass is absent for the first case and silent for bar 1 in the second, and check
the exact final boundary. These checks are prepared, not human-passed. Companion
native validation also accepts current manifest v2 and preserves rejected v1
bytes; no project migration or soundtrack change is performed.

U04b automated checks cover section identity operations, explicit purpose/phrase
review and confirmation, invalid phrase entry, concurrent section/plan saves,
and source/candidate/accepted-rest preservation after section removal and reopen.
Section/purpose controls were inspected at 1536×1024, 1280×900 and 720×900.
Human visual/Logic gates remain pending.

## Retained historical Logic evidence

**2026-08-28: PASS**, user report for Logic Pro **12.3.1** on macOS **26.6.2
(25G83)**; export fixtures prepared from `adfe25a` (old MC-047) plus the matrix
preparation test. All six complete and per-role packages imported without error;
full playback and save/close/reopen passed. 120 BPM/4/4 and named role separation
are visible in the retained captures. Imported marker display is unassessed;
software-instrument output-channel UI was not independently captured.

This is historical compatibility evidence, not acceptance of future timing/rest
changes. The original package directories were ignored build output. Manifest
hashes below preserve the old record even if those packages are no longer local.

| Package directory | Source fixture (SMF) | Expected complete/role end tick | Manifest SHA-256 |
| --- | --- | ---: | --- |
| `smf0-melody` | `smf0-melody.mid` (0) | 480 | `140306ea5ab542406e9445cbc3db57cfb5142ad22ba1d4d3bb4993d0fb1a44ca` |
| `smf1-reference-tracks` | `smf1-reference-tracks.mid` (1) | 480 | `a9bc0bc7648062e2557f5c6206f8d8cbef592a9c31e3b88f5458474813eb278d` |
| `pickup-timing` | `pickup-timing.mid` (1) | 960 | `1bf0ab41456600a1f205ca1ba362aeb162647d3c673cc3b07e5769e8fb359740` |
| `sub-bar-harmony` | `sub-bar-harmony.mid` (1) | 1920 | `6d31ac90f677f85ad56c66b28c61c9697d53ebdaf19ebc11918267195caa001c` |
| `expressive-controller-pitch` | `expressive-controller-pitch.mid` (1) | 480 | `bec84753b254a6ab8480b6ad50116ce8515fcddba642087c6b096c97289f0d8f` |
| `complete-arrangement-boundary` | `final-boundary-note.mid` (0) | 1920 | `fc35d0c091ca5ecf060592e5e167cd7a02a6d978d41876ea81e6ece6a1c7e725` |


| Fixture | Screenshot | SHA-256 |
| --- | --- | --- |
| `smf0-melody` | [smf0-melody.png](checks/smf0-melody.png) | `8d3b92b762098825a5a4acd5c9bc21e13b4e202322ab8cabb5e66bf4cc7011bd` |
| `smf1-reference-tracks` | [smf1-reference-tracks.png](checks/smf1-reference-tracks.png) | `00bde2e596db606e37c0317155552e147c883c7ebbb33d9032d9484062d8b266` |
| `pickup-timing` | [pickup-timing.png](checks/pickup-timing.png) | `81b2232f0eccaca9a9475f7658ebb26f37237811a687affe55cf59f789805ef2` |
| `sub-bar-harmony` | [sub-bar-harmony.png](checks/sub-bar-harmony.png) | `8913576ef89b14a46396f49df3275a64afebadfb42db0eb21d34ab27f3a3db41` |
| `expressive-controller-pitch` | [expressive-controller-pitch.png](checks/expressive-controller-pitch.png) | `b8d9b703c9670f63f180a8616b7e9173c4961efbace1905310f8e64624faa384` |
| `complete-arrangement-boundary` | [complete-arrangement-boundary.png](checks/complete-arrangement-boundary.png) | `0bb012b3957ddfd6031169aac773f08b57bc140297b352b443d68355f23168b8` |


The historical package settings were PPQ 480, 120 BPM, 4/4, C major, marker
`1:Verse` at tick 0, complete track order Conductor/Melody/Chords/Bass/Drums and
channels 1/2/3/10. Very short historical fixtures are compatibility probes, not
current full-song musical acceptance cases.

## Retained planning evidence and release record

2026-09-06: baseline `make test` passed with fresh root/desktop execution before
document consolidation. Final consolidation checks: `make test`, `make build`
and `git diff --check` PASS. Reports contain 782 root tests with no failures and
261 desktop tests with no failures and 10 existing skips; desktop execution was
cached in the final pass after its fresh baseline run. All 33 task dependencies
resolve without cycles; original UI/TABI/train and Logic images are byte-preserved.
Documentation changes now invalidate Gradle test caching. No generator/UI runtime
change, new Logic run, controlled listening result or video generation is claimed.

Release record remains **pending**: final commit/build; clean test/build/install;
measured repository reduction; final music set and ratings; six-page visual
approval; current Logic matrix; exact remaining limitations; reviewer/date.
Keep one concise record here rather than restoring the old task diaries.

### F06 cleanup measurement (2026-09-07)

F06 inspected only repository-owned candidates. `<repository-root>/sounds`
resolved as a regular, non-symlink directory containing two tracked obsolete
sound-library catalog files (253,314 bytes); its `sounds/` ignore rules were
stale. The tracked, non-ignored root `Piano Song n.17.mp4` resolved as a regular,
non-symlink, unreferenced legacy media file (5,001,765 bytes). Both targets were
removed: 3 files / 5,255,079 tracked bytes (5.01 MiB).

`<repository-root>/.kotlin` resolved as a regular, non-symlink directory with
nine tracked, non-ignored compiler error-cache logs (49,613 bytes). The configured
F06 coordinator path policy does not authorize `.kotlin/`, so those files were
inspected but retained and are not counted as cleanup. `data/audio` and
`.venv-worker` were absent at inspection, so no untracked sound-library,
audio-project, or virtual-environment data was claimed as deleted. The obsolete
audio, data, generic-cache, render, and sound-library ignore entries were
removed.

The F06 test, evidence, and ignore changes add 4,815 bytes, reducing tracked
worktree payload from 32,610,480 to 27,360,216 bytes (16.1%).

Against the recorded 2026-09-06 tracked-source baseline, the F06 tree had 56
production Kotlin files / 20,415 lines versus 236 / 73,677 (180 files and
53,262 lines removed: 76.3% / 72.3%). It has 57 test Kotlin files / 12,855
lines versus 187 / 34,912, and zero Python files / lines versus 33 / 4,782.
The production-line reduction exceeds the 40% investigation target. The removed
legacy data has no production consumer; guards preserve supplied UI/TABI/train
references, Logic captures, and owned MIDI fixtures. F06 does not establish a
clean-install/native-startup result, a human listening decision, or a new Logic
run.

## Recovery notes

For toolchain failure, inspect the JDK requested by Gradle and the actual launcher;
do not restore worker setup. For rejected MIDI, follow the exact track/map/pairing
finding and preserve the original. For silent audition, choose the built-in
synth or an explicitly configured MIDI receiver in the persistent player's
options. Device failure must not mutate musical data.

For stale candidates, inspect the changed authority/dependency and regenerate
only affected scopes. For failed export, check accepted readiness, digests,
writable/new destination and semantic validation. Never rename an old candidate
to make it current or overwrite a snapshot. For bad musical fit, report bars,
melody+piano/full-song comparison, candidate IDs and audible reason rather than
only “in tempo”. Unsupported old audio projects are intentionally not migrated.

U04 recovery verifies the shared section/chord selection, unsaved impact and
bar-total recovery. Native Compose captures at 1536×1024, 1280×900 and 720×900
show first-viewport name editing and Save, with wrapped section actions reachable
by scroll and keyboard. Selecting an unsaved new occurrence shows save guidance
instead of another occurrence’s chord editor. The focused workflow and 411-test
suite pass; reference 03 and actual captures were inspected. Evidence:
`~/.codex/melotrail-terra/u04-repair-evidence/visual`. This is technical UI evidence,
not final visual approval or a new Logic playback decision.

### U07a visual regressions

U07a pins 66 full-page PNGs: all six production destinations at 1536×1024,
1280×900 and 720×900 in empty, partially accepted/stale and fully accepted states;
Arrange progress/retry plus scrolled ready Review decisions and Export results.
Three small control/panel goldens cover focus, primary fill, borders and corners.
These are technical regression baselines; U07 owns human visual approval.

The synthetic presentation fixtures hydrate imported MIDI, melody and authority
consistently. The separate ready fixture has five current accepted candidates,
one confirmed Bass rest, guarded batch Undo and a snapshot matching the accepted
selection. Tests require enabled Play/Undo/Publish/Reveal, accepted identity,
all snapshot files and visible scrolled decision/action bounds. Original stale
fixtures remain separate. Real musical-service behavior remains covered by the
focused workflow suite; synthetic fixtures do not establish Logic approval.

The renderer uses macOS 27.0/aarch64, Compose UI 1.11.0/Skiko 0.144.6, software
raster, density/font scale 1, fixed frame times/dates/seeds and stopped playback.
System Arial regular/bold files are read in place, never redistributed.
`visual/renderer.properties` pins OS, font, renderer-class and native digests.
An environment change fails explicitly rather than skipping or updating goldens.
The user authorized V18a1 requalification on 2026-09-15. All 69 replacement PNGs
come from fresh current-host captures; inspected differences lie around text,
with dimensions and alpha unchanged. Font, renderer and native digests remain
unchanged. Original PNGs/hashes, original/actual/diff image reviews and qualification
receipts are retained in
`~/.codex/melotrail-video-sequential/evidence/V18a1/macos27-20260915`.
This technical refresh does not supply U07's human visual decision.

`VisualImageComparator` compares every ARGB pixel with zero tolerance and no
masking, resizing or registration. Tests write actual/expected/diff PNGs and a
pixel-count report under `desktopApp/build/test-results/visual-shell`,
`visual-primary`, `visual-panel` and `visual-comparator`. Missing baselines fail
and retain actual captures. Tests never create or replace expected resources.
Baseline changes require explicit image inspection and independent checks.

Negative controls reject shifted panels, wrong primary fill, pill corners,
erased drum lanes, single RGB/alpha changes and dimension changes. Independent
checks cover shell/inspector geometry, four aligned 52 dp lanes, role colors,
one player, 48 dp navigation/transport targets, contrast and keyboard focus/
activation. The ready fixture's scrolled captures use un-clipped target positions
so an offscreen Export result is brought into view before its bounds are checked.

Recovery evidence: `~/.codex/melotrail-terra/u07a-repair-evidence`.
The initial new-baseline run deliberately failed for all 24 absent ready images;
state and geometry assertions passed before those images were explicitly
inspected and added. Existing baselines were retained. Focused/full test/build
results and technical image review are recorded with the final repair there.
U07b owns native-density, large-song and responsiveness measurements; U07 retains
the actual human visual decision.
