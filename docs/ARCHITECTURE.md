# Architecture

Owner: runtime boundaries and persistence. Product behavior and upcoming changes
live in [PLAN](../PLAN.md); implementation status lives in [TASKS](../TASKS.md).

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

  -> Video workspace (planned in V10–V33; loaded lazily)
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
| Video (planned) | `video` and `desktopApp/.../desktop/video` | Independent projects, references, prompts, generation jobs, silent preview/assembly/export |

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

Planned video projects use a separately selected root outside MIDI projects and
exports. They own immutable reference copies, prompt/shot versions, scene looks,
takes, job attempts, assemblies and export snapshots. Video code cannot write a
MIDI project or import MIDI application/storage owners. Only the application
composition root coordinates the two workspaces.

## Planned derived data and arrangement authority

M02 adds a pure, versioned melody-context projection. It contains musical
observations and uncertainty; it cannot edit source, chords or project authority.

M06 adds one confirmed arrangement-plan record referencing authoritative
occurrences. Per-occurrence purpose, repeat family, phrase group, energy, role
activity, density, register, groove and boundary intent have one owner. An
unconfirmed style proposal is session state; a confirmed plan is an explicit
project mutation. Candidates record the plan/scoped settings they consumed.

M07 introduces a typed planned-rest selection alongside generated candidates.
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

## Planned video isolation

[TABI_VIDEO](TABI_VIDEO.md) specifies an independent Video tab in the same
Kotlin/Compose application. It takes an externally finished scene image, optional
ready character/background layers and a motion prompt, then exports a complete
180–300 second silent video. In-app image generation, outfit/style synthesis and
mandatory automatic asset extraction are deferred. V18b2 validates ready assets
against V18a; V18 selects imported looks and compiles supported motion inputs. It needs no MIDI
project, manifest, song or soundtrack. The app-level MIDI/Video switch stays
above the existing six MIDI destinations; entering Video pauses the one MIDI
session while preserving its position, and silent preview creates no MIDI player.

Video services are constructed lazily. Missing models, credentials or media
tools cannot prevent MIDI startup, audition or export. Models and large media
stay outside Git and MIDI storage; hosted uploads require an explicit mode and
authorization. The current V17a adapter validates a separately installed,
hash-pinned ComfyUI/LTX/Gemma profile and starts one dedicated loopback server
through the existing owned-process supervisor. Its private input/output/temp/user
directories, one-inference admission and bounded health/resource/stop lifecycle
belong to the video runtime. A per-launch response marker attests the listener;
an occupied port or competing listener is never treated as that runtime. Cleanup
timeouts and unconfirmed native supervision retain process ownership and block
restart. The pinned ComfyUI/GGUF import-source sets are rechecked at launch,
separately from the large model hashes checked during explicit setup.
It exposes no workflow submission or layer-preparation claim. The Video tab,
application composition, ready-artwork motion setup and full assembly
remain planned in their later V10–V33 rows.

The Swift companion and MIDI Export soundtrack handoff have been removed from
active production and build wiring. Historical external evidence remains; no
Swift compatibility route exists. The independent Video tab and its installed
runtime proof are still pending V20–V29.
