# Composition workflow UI plan

## UI strategy

Keep the existing Compose Desktop workspace, responsive breakpoints, project
picker, navigation shell, cards, dialogs, progress semantics, transport, and
view-model/application-service boundary. Change the information architecture
around a musician's workflow instead of adding more low-level action buttons to
the current Overview/Import pages.

## Primary destinations

| Destination | Purpose | Completion signal |
| --- | --- | --- |
| Setup | name, key/scale, tempo, meter, mood, profile | valid composition settings |
| Harmony | verse/chorus/bridge progressions | required section harmony valid |
| Melody Parts | import and process section performances | at least required parts ready |
| Structure | arrange stable part occurrences | valid non-empty form |
| Arrange | roles, instruments, density, occurrence variations | approved arrangement |
| Build | cohesion, humanization, rendering | approved/rendered build inputs |
| Mix & Master | stem balance, optional style finish, master | approved master |
| Export | formats, evidence, release checklist | validated export manifest |

Library, video preview, and settings remain secondary destinations. Advanced
diagnostics/evidence are contextual details, not primary workflow screens.

## Setup

- Name field and structured tonic/mode picker with derived label.
- BPM field/stepper using profile recommendation; core values remain editable
  within global musical bounds.
- Time-signature controls. The initial Lo-fi simple mode may show only 4/4, with
  a clear profile constraint rather than a hidden domain assumption.
- Profile selector initially contains Lo-fi and explains that it controls
  defaults/suggestions, not ownership of the song.
- Mood cards/dropdown from profile-supported definitions.
- Save validates without triggering downstream work. A changed key/tempo/meter/
  profile/mood preview lists affected downstream stages before confirmation.

## Harmony editor

- Tabs/cards for Verse, Chorus, and Bridge; architecture allows more section
  types from a section catalog.
- Chord chips/rows display formatted chord symbols but edit root and quality in
  structured controls.
- Add, remove, edit, and reorder work with keyboard and pointer controls.
- Invalid/empty required progressions show actionable local errors.
- Progression preview and optional MIDI audition may be Later; MVP contract
  should not require audio rendering to save harmony.
- Future duration/inversion/slash/extension controls are not shown as inert MVP
  widgets. The domain reserves them without confusing the first UI.

## Melody Parts

Each part card shows:

- persistent name and section type;
- source filename/media type/rights attestation without exposing absolute path;
- stage rail: importing -> extracting -> cleaning -> normalizing -> transposing
  -> correcting -> enhancing -> ready;
- current/past stage status, progress, warning, failure, and retry action;
- source-key confidence/confirmation when transposition cannot proceed safely;
- enhancement intensity (`Off`, `Subtle`, `Balanced`, `Creative`);
- preview/compare selector for Original, Cleaned, Corrected, and Enhanced when
  artifacts exist;
- explicit current-selection label and downstream-stale warning.

The user chooses file, part name, section type, and attestation, then clicks
Import once. The application stage orchestrator starts eligible stages. Closing
and reopening the project reconstructs progress from persisted run records.

Retry starts the failed stage with the same inputs/config unless the user edits
settings. Successful earlier stages remain complete. Cancellation is Later
unless the underlying worker/model can be safely stopped; do not label a status
“cancelled” while work continues silently.

## Structure

Reuse the current available-part and ordered-structure experience, but bind UI
rows to persisted occurrence IDs. Repeating Verse A creates new occurrence IDs
referencing the same part. Reorder retains IDs, labels, and variation choices.

The view may offer profile suggestions (Intro/Verse/Chorus/Outro) but never
replace a user's structure without explicit acceptance.

## Arrange

- Show arrangement roles separately from concrete instruments.
- Select/disable roles supported by the profile, then assign an available
  instrument or accept a profile suggestion.
- Keep energy, density, section variation, AI draft/review, licensing, and
  generation readiness where useful.
- Label profile-derived defaults as suggestions. User overrides persist.
- Arrangement completion no longer depends on cohesion approval.

## Build

Build has three reviewable substages:

1. Cohesion: boundary cards, transition intent, preview, approve/reject/retry.
2. Humanization: profile/mood-derived amount, seed, regenerate with new seed,
   bypass, and comparison.
3. Render: stem progress and failures.

Simple mode can offer one primary “Build” action that runs approved/default
steps; expanded mode exposes evidence. Cohesion/humanization do not claim they
changed the melody unless their edit reports say so.

## Mix, master, and export

Preserve the current per-stem controls and master/export flow. Rename or regroup
fixed “Bedroom Lo-fi” processing as optional Profile Texture/Style Processing,
defaulting to the profile policy and remaining bypassable. Surface exact input
build, mix revision, master config, and commercial evidence status.

## Readiness and navigation

`WorkflowReadModel` remains the authority for blocked/current/review/stale/
complete state. It must derive state from settings, harmony, selected artifacts,
stage runs, approvals, and hashes—not UI page visits or mere file existence.

Navigation rules:

- users may inspect any destination;
- primary actions explain missing prerequisites and link to the owning screen;
- changed upstream creative decisions mark exact downstream results stale;
- no automatic rerun begins merely because the user navigated;
- automatic import processing starts only from an accepted import or explicit
  retry/config change.

## State and service boundaries

Add immutable UI models for setup, progression editors, part run summaries,
artifact comparisons, role assignments, and build substages. `WorkspaceViewModel`
maps application snapshots and emits typed commands. It must not compute chord
spelling, artifact precedence, cache keys, worker paths, or AI prompts.

Because `WorkspaceViewModel` and `WorkspacePageRouter` are already large, new
screens/components should be split by destination while preserving the one
workspace state owner. Do not mix an unrelated whole-UI rewrite into the domain
migration.

## Accessibility and responsive behavior

- Every status uses text/icon semantics, not color alone.
- Chord/structure reorder has keyboard actions and stable focus.
- Stage progress announces part, stage, state, and actionable failure.
- Wide layout may show navigation plus detail; medium/narrow use scrollable
  step navigation and cards without hiding primary actions.
- Transport never overlaps modal/editor actions and identifies which artifact is
  playing.
- Test wide, medium, and narrow layouts plus real keyboard/screen-reader review.

## Milestone-one boundary

MVP delivers the full setup/harmony/parts/structure contracts and progress UI,
with deterministic or mock correction/enhancement implementations. Advanced AI,
audition polish, arbitrary meters/modes, profile editors, and profile downloads
are Later/Future. UI labels must not promise quality/capability that is only a
placeholder.

