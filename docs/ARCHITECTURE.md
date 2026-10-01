# Architecture

Owner: runtime boundaries and persistence. Product behavior and upcoming changes
live in [PLAN-AUDIO](../PLAN-AUDIO.md) and [PLAN-VIDEO](../PLAN-VIDEO.md);
implementation status lives in [TASKS-AUDIO](../TASKS-AUDIO.md) and
[TASKS-VIDEO](../TASKS-VIDEO.md), respectively.

## Components and dependency direction

```text
Compose Desktop
  -> MIDI workspace (implemented)
       -> application use cases and immutable presentation state
       -> project / music / structure / arrangement domain
       <- project-store adapter
       <- Standard MIDI reader/writer adapter
       <- local MIDI audition adapter
       -> immutable MIDI package -> Logic Pro

  -> Video workspace (VG1–VG6; backend foundations present, UI/delivery planned; loaded lazily)
       -> video application and domain
       <- independent video project/asset/job stores
       <- selected local inference and video-only media adapters
       <- explicitly selected hosted adapter (optional)
       -> complete silent MP4 -> user's external Apple editor
```

| Owner | Current or planned path | Responsibility |
| --- | --- | --- |
| MIDI semantics | `midi/domain`, `midi/adapter` | SMF parsing/writing, canonical events, pairing and preservation |
| Project | `project`, `project/adapter` | Schema, paths, identities, hashes, atomic store |
| Musical authority | `music/core`, `structure` | Key/chords, exact occurrences, harmonic windows |
| Generation | `arrangement/core` | Pure Chords/Bass/Drums engines, patterns, context, validation |
| Orchestration | `application/MidiCore*` | Import, authority changes, generation, drafts, acceptance, export |
| Playback | `audition`, `audition/adapter` | One MIDI session, managed synth/output, device/resource cleanup |
| Presentation | `desktopApp/.../desktop/MidiCore*`, shared shell/theme/primitives | Six pages, intents, visual projections, one persistent dock |
| Video | `video` (backend foundations) and `desktopApp/.../desktop/video` (planned UI) | Independent projects, artwork, prompts and generation jobs; integrated preview/continuous assembly/export remain planned |

Names identify observed owners, not an instruction to keep/delete by prefix.
`DesktopMain.main` calls `MidiCoreDesktopEntrypoint`; obsolete desktop factory
and audio/worker paths have been removed. The optional installed-launcher check
uses the same six-page shell with isolated preferences and a bounded exit.

Domain code has no Compose, filesystem, HTTP or MIDI-device dependency. Use cases
coordinate ports; adapters translate I/O. UI dispatches intents and renders
state; it never parses MIDI, mutates files or runs a generator directly.
Architecture tests must cover real desktop paths, not an unused `target/` subtree.

Keep the current root-engine plus desktop module structure unless a concrete
boundary requires change. Do not create a framework, service mesh, plugin system
or one module per small type to achieve a smaller repository.

## Storage and safety

Current logical project layout:

```text
project.json
source/original.mid
candidates/<role>/<occurrence>/<candidate>.mid
reports/import.json
reports/candidates/<candidate>.json
exports/<snapshot>/complete-song.mid
exports/<snapshot>/melody.mid, chords.mid, bass.mid, drums.mid, manifest.json
```

Project-relative paths are confined beneath the project root; resolve and
validate symlinks as well as textual traversal. External selected inputs remain
read-only. Source, candidates and export packages bind immutable content digests.
A missing/mismatched reference is an error, never a request to choose another file.

Only one project-state write transaction runs at a time. Stage and validate
writes, atomically replace project JSON, and preserve the last known-good state
on cancellation/failure. New export snapshots use new destinations. Acceptance
and undo change references, never candidate bytes. Batch acceptance revalidates
all scopes and locks and commits all changes or none.

Schema changes have one current writer. Do not maintain legacy audio readers,
migrations or dual pipelines. If a current MIDI version becomes unsupported,
reject it before writes and preserve its files; any conversion must be a
separately authorized, explicit operation. New additive records must not
reinterpret already accepted MIDI or silently upgrade artifacts.

Video project storage uses a separately selected root outside MIDI projects and
exports. It owns immutable reference copies, scene looks, prepared scenes,
continuous-plan proposals, takes, job attempts and export records. Current Video
project schema 5 records append-only assembly versions; `VideoAssemblyStore`
persists bounded schema-1/planner-2 descriptors through `VideoProjectStore`'s shared
locked, revision-guarded descriptor publisher. Reopening verifies the descriptor,
source identities and prepared image facts. Unsupported project/plan versions
reject without migration or artifact rewriting. Proposal persistence is not
executable readiness: application integration, continuous execution/checkpoints
and complete export orchestration remain VG4–VG5 work. `VideoAssemblyActionKind`
still has scalar scheduled actions and no pose-sequence or rig semantics. After
the selected standalone rig/reuse proof, VG4-07/09 must scope only the required
proven-action representation/adapter through existing assembly/planner/store/
preparation/job/media owners, with one current contract, rig/part/action/timing
pins and absolute chunk-boundary tests. Runtime held-pose admission is neither
rig support nor episode-schedule integration.
Video code cannot write a
MIDI project or import MIDI application/storage owners. Only the application
composition root coordinates the two workspaces.

## Derived data and arrangement authority

The current engine provides a pure, versioned melody-context projection. It contains musical
observations and uncertainty; it cannot edit source, chords or project authority.

The project stores one confirmed arrangement-plan record referencing authoritative
occurrences. Per-occurrence purpose, repeat family, phrase group, energy, role
activity, density, register, groove and boundary intent have one owner. An
unconfirmed style proposal is session state; a confirmed plan is an explicit
project mutation. Candidates record the plan/scoped settings they consumed.

A typed planned-rest selection exists alongside generated candidates.
A complete draft covers every required scope with one of these states; a failed
or missing candidate cannot be recast as silence. Assembly, use/undo, currentness,
audition and export consume the same scope-selection contract.

Keep these records small. Do not persist duplicate UI lane events or copy the
whole project into each candidate. Candidate fingerprints bind source, confirmed
scope, generator/pattern/profile versions, plan inputs, seed and consumed
upstream/boundary/repeat dependencies. Timestamps are provenance, not musical
inputs. Stale async completion is rejected before publication.

## Generation and playback

Resolve the whole-song plan before Chords → Bass → Drums generation. Shared
groove intent precedes both bass and drums; final drums may additionally consume
validated bass evidence. No cyclic accepted-role dependency is allowed.

Carry bounded previous/next-section summaries rather than an invisible global
rewrite. Hash used neighbors and invalidate only the affected dependency set.
Cancellation may preserve completed immutable scopes for exact retry, but never
publish an incomplete draft as complete. A new generation run preserves accepted
work until the user explicitly uses its result.

A style preview is ephemeral and uses the same plan resolution and role engines
as a full draft. Cache by authority, style/plan version, occurrence and seed;
rapid requests are latest-wins. Preview cannot write candidates, revisions or
acceptances. Draft audition is permitted before acceptance; export is accepted-only.

One shell-owned MIDI session serves all pages. Use a managed built-in synthesizer
by default or an explicitly selected receiver. Real position observation is
bounded and lifecycle-managed; no page-local clock or second sequencer. Seek,
pause, stop, loop, mute/solo and device loss release notes/resources predictably.
Audition timbre is not authoritative and does not render audio files.

## Video isolation and remaining integration

Production order (updated 2026-10-01): standalone coherent parts/rig → reviewed
five-second wave → reviewed second action → description-driven 20–30-second
reuse proof/review. Only then scope minimum production rig/action binding and
moving scenery join → combined 60-second proof/review → full 180-second Tokyo
film/editor review → selected second-city reuse proof/review → app integration.
The default/current full target is 180 seconds, not a mandatory 240; the product
supports 180–300. See [PLAN-VIDEO](../PLAN-VIDEO.md#6-step-by-step-delivery-order) and the
[TABI recipe/evidence](TABI_VIDEO.md#tabi-train-series-recipe-2026-09-30).
The user-selected prepared 2D/2.5D method first uses Blender as a standalone proof
candidate, outside production projects/jobs. New ignored rig/scripts/input data
are experiment artifacts, not a second durable ledger or a production rig schema.
Use a fresh directly owned supervisor; do not reuse the failed shared-socket guard
or mutate consumed packets. Preserve rejected seven-pose footage and all failures.
Image-generation-assisted missing-parts preparation is external, finite and
separately admitted; it does not add in-app synthesis/extraction.

After that proof is accepted and the production runtime explicitly chosen, a thin
harness must use existing Kotlin project/job/media ownership. Scope a lazy rig
media-stage adapter and typed action binding behind tests; no second scheduler,
parallel persistence schema or MIDI dependency. The current held-pose API must
not masquerade as an articulated rig. Limit pre-pilot product engineering to those
required bindings, verified completed-chunk continuation and bounded encoding.
Preserve existing planner/store/decoder work; defer app caller cleanup and UI.

For the user's recurring train series, reuse compatible cabin/camera/TABI/props,
replace city-specific exterior art and activity choice/order/timing/quiet intervals,
and derive new immutable scene/plan/job identities through the same owners. These
are content inputs, not a separate episode schema, city-specific backend or second
scheduler. All visible exterior apertures and the full selected duration need
coverage; original static small panes and a short reuse clip cannot prove a full
new-city film. Generic scenarios remain valid. Full-pilot success unlocks second-
city method testing; its reviewed short proof unlocks app work, not full second-
city coverage, app/release acceptance or automatic live budgets.

The initial full-film assembly route is verified absolute chunk PNGs followed by
one immutable numbered image sequence and a pinned FFmpeg image2/H.264 encode.
It is a proposed production route, not completed full-output delivery. The current
FFmpeg distribution lacks concat support; a tool/assembly change requires its own
scope and tests. Frame chunking does not bound all decoded assets or accumulated
staging. Enforce separately admitted stage and cumulative budgets through existing
native supervision; never reset a whole-attempt deadline per chunk. Pilot success
establishes a recipe, not installed/app/rights/release acceptance.

[TABI_VIDEO](TABI_VIDEO.md) specifies an independent Video tab in the same
Kotlin/Compose application. It takes an externally finished scene image, optional
ready character/background layers and a motion prompt, then exports a complete
180–300 second silent video. In-app image generation, outfit/style synthesis and
mandatory automatic asset extraction are deferred. Existing ready-asset validation
uses the prepared-scene descriptor; look selection and preparation compile supported
motion inputs. VG1 verifies these foundations; VG2 completes preview orchestration.
Video needs no MIDI project, manifest, song or soundtrack. The app-level MIDI/Video switch stays
above the existing six MIDI destinations; entering Video pauses the one MIDI
session while preserving its position, and silent preview creates no MIDI player.

Video services are constructed lazily. Missing models, credentials or media
tools cannot prevent MIDI startup, audition or export. Models and large media
stay outside Git and MIDI storage; hosted uploads require an explicit mode and
authorization. The current local setup adapter validates a separately installed,
hash-pinned ComfyUI/LTX/Gemma profile and starts one dedicated loopback server
through the existing owned-process supervisor. Its private input/output/temp/user
directories, one-inference admission and bounded health/resource/stop lifecycle
belong to the video runtime. A per-launch response marker attests the listener;
an occupied port or competing listener is never treated as that runtime. Cleanup
timeouts and unconfirmed native supervision retain process ownership and block
restart. The pinned ComfyUI/GGUF import-source sets are rechecked at launch,
separately from the large model hashes checked during explicit setup.
The runtime owns server lifecycle; `ComfyVideoClient` and `LocalVideoBackend`
separately own short I2V API jobs. This does not supply semantic layer extraction
or full-video orchestration. `VideoMotionRenderer` supervises the external
Node/Canvas controlled compositor in bounded absolute-frame ranges; it is a media
stage, not a second generative provider. Manifest 4/tool 1.2.0 adds `POSE_REPLACE`:
3–16 strictly ordered absolute-frame steps holding supplied cutouts, neutral
entry/return and ≤9000-frame span; each invocation remains ≤300 frames. It replaces the whole cutout and
applies alpha once, not a dissolve or native articulated interpolation, and rejects
simultaneous blink/breathing/head/subject-steam controls. Synthetic production-
imported tests and later matching-kit source checks pass, but seven-pose moving
quality is rejected; the selected rig route is not implemented by this control.
Window/frond/foreground mattes must fit the selected cabin and every allowed rig
state as well as any supplied poses; the accepted
old fixed-head mask is not automatically compatible. Original art/masks/videos
remain immutable. The Video tab/application composition, visible motion setup and
full assembly/export remain planned in VG2–VG6. For this series, VG6's full app
export uses the selected second city/different script only after complete corridor/
contact/sequence readiness and separately admitted full execution.

The Swift companion and MIDI Export soundtrack handoff have been removed from
active production and build wiring. Historical external evidence remains; no
Swift compatibility route exists. The independent Video tab and its installed
runtime proof are still pending in VG3 and VG6.
