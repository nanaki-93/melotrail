# Architecture

Owner: runtime boundaries and persistence. Product behavior and upcoming changes
live in [PLAN](../PLAN.md); implementation status lives in [TASKS](../TASKS.md).

## Components and dependency direction

```text
Compose Desktop
  -> application use cases and immutable presentation state
       -> project / music / structure / arrangement domain
       <- project-store adapter
       <- Standard MIDI reader/writer adapter
       <- local MIDI audition adapter
  -> immutable MIDI package -> Logic Pro

Optional, separately installed TABI companion
  <- finished Logic soundtrack + optional immutable MIDI export manifest
  -> its own assets/jobs/provider/encoder -> local video file
```

| Owner | Current path under `src/main/kotlin/app/melotrail` | Responsibility |
| --- | --- | --- |
| MIDI semantics | `midi/domain`, `midi/adapter` | SMF parsing/writing, canonical events, pairing and preservation |
| Project | `project`, `project/adapter` | Schema, paths, identities, hashes, atomic store |
| Musical authority | `music/core`, `structure` | Key/chords, exact occurrences, harmonic windows |
| Generation | `arrangement/core` | Pure Chords/Bass/Drums engines, patterns, context, validation |
| Orchestration | `application/MidiCore*` | Import, authority changes, generation, drafts, acceptance, export |
| Playback | `audition`, `audition/adapter` | One MIDI session, managed synth/output, device/resource cleanup |
| Presentation | `desktopApp/.../desktop/MidiCore*`, shared shell/theme/primitives | Six pages, intents, visual projections, one persistent dock |

Names identify observed owners, not an instruction to keep/delete by prefix.
`DesktopMain.main` already calls `MidiCoreDesktopEntrypoint`; obsolete factory
code in the same file is removal scope. The existing visual-evidence provider
is reusable work requiring verification, not an absent component to duplicate.

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

## Optional video isolation

[TABI_VIDEO](TABI_VIDEO.md) specifies a separate companion, preferably a separate
repository. Its soundtrack, library, jobs, provider credentials and encoder never
enter the MIDI schema/build/runtime. It reads immutable export manifests only,
and remains optional for installation, offline use, audition and export.
A future launch integration passes a snapshot identity; it does not create a
second project authority or restore removed release/mastering code.
