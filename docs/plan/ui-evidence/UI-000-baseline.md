# UI-000 redesign baseline

Date: 2026-09-05

Repository baseline: `main` at `d7aaf0839b2f229779dd9b10d1e714b5c8835dcc`
Status: unapproved pre-redesign evidence; this is not a visual golden set.

## Safe commit boundary

The worktree and index were clean before UI-000. `git diff --check` and
`git diff --cached --check` passed. No uncommitted MC-048I preparation,
generator/validator work, documentation edits, or compiler-session-marker
deletion exists to be absorbed by a UI task. The planning handoff and unrelated
committed edits are owned by `d7aaf08`; UI commits begin after it and stage only
their reviewed paths.

The existing `WorkspaceScreenTest.kt` retains old reference-overlay readers and
legacy route tests. It is an observed baseline owner, not a target implementation
or an approved fidelity test. UI-017 and MC-051 own its replacement/removal
after all target consumers and regression coverage are proven.

## Design-only reference manifest

Every original was viewed at its native 1536 × 1024 pixels. These images are
design inputs only: no application or normal golden-test path may load them.

| Reference | SHA-256 | MIDI Core mapping |
| --- | --- | --- |
| `01-dashboard-overview.png` | `a4b4b13a64f5db9a4bfe0133b98b2d441d00b0ce8d42a75b0185c4b1310dde0d` | Project dashboard: factual metrics, section strip, real role evidence and inspector; no recent-project fiction or video preview. |
| `02-import.png` | `4f9133696c2da50cfc69a418c1127c6f3fd295bc46ddc4f464f8b22afc290564` | One immutable SMF source, import report and inspected source notes; no audio import or processing settings. |
| `03-structure.png` | `997d0152dba4bb47cc6c45abf1cd7fd1ad7a46204ed639d11ebe404ff9b119a5` | Exact sections/harmony table and contextual inspector; no inferred structure, per-section tempo, AI suggestion or video frame. |
| `04-arrange.png` | `ca2ed249b8f17e2814d35b1e219e06ed206e316e87912f84bfb15823c1b7b964` | Four factual MIDI lanes, style gallery, full-draft action and selected-section inspector; no mixer, instruments, Pad/Strings/Lead/FX or AI settings. |
| `06-mix-master.png` | `c47650effef5e699524612c64d6f460c0151877a3ac847185783e06246643d10` | Visual grammar only: thin panel edges, compact icon/label rhythm and controls. No Mix & Master destination or audio controls. |
| `07-library.png` | `ce9c284f5873a15cc45081cd88e339fa1c4243dad3a4ef42e0bf048f0123f6f2` | Arrange style and Review candidate card treatment only. No sound catalogue, download, search or sample preview. |
| `08-video-preview.png` | `7712eec9adc442a267697610ef63cd94c6e900f5b942f65948a69c81b4bdb45b` | Future video proposal only. MIDI Core may show factual protected-melody/section evidence, never a video preview or export. |
| `09-export.png` | `b2f42b2b6d96bf43b32bfa21b79542741a229ca19bd765f5a11fc42d8d0732de` | Immutable MIDI package summary, file facts, destination and Logic Pro guidance; no audio/video formats or metadata editor. |
| `10-settings.png` | `ac402241d53176f570893d2f60d71452512e0bfc3f23fa0d70f9b7b2656cd888` | Styling for contextual device/options/help panels only. No Settings route, accounts, telemetry, model, audio or autosave controls. |

## Existing six-page capture inventory

The following ignored output was present under
`desktopApp/build/test-results/midi-core-focused-workflow/` when UI-000 began.
It is produced by `MidiCoreFocusedWorkflowTest` and is useful only as a
pre-redesign comparison record. It is not a visual baseline, does not compare
pixels, and should not be copied into resources.

| State | Page | Size | SHA-256 |
| --- | --- | --- | --- |
| wide | Project | 1280 × 900 | `77f0b75ef68519aebd9701abb6de4b6972b8727242eea8cf90367069626b0da9` |
| wide | MIDI | 1280 × 900 | `99418ddc323d85649b7641924c798e596fd471051c1837ef8faa6de3e703773d` |
| wide | Structure & Harmony | 1280 × 900 | `2798e7ec23ce5dd8b1b730ca7084479116158068e1dfb37dad30eaa4f0088439` |
| wide | Arrange | 1280 × 900 | `f89ef46dd2340ad0b9e3d60dc61b4ab55748ce9c041e274c1b9b2af9c6f15afd` |
| wide | Review | 1280 × 900 | `e0ce69a3400b84322593f3328a4cd8c5cce0d53765723d79061fe22e9d57cc47` |
| wide | Export | 1280 × 900 | `27d3f3e9fbee98e1aff8647a2f29e16b80d617c5f8681d20a1218a54cebd409e` |
| compact | Project | 720 × 900 | `cc08cee94fc8b7920305e4937e714a1c1d8a6895f902998c9e92d4ad6f56c135` |
| compact | MIDI | 720 × 900 | `b8a330914f3688f87ea547a5853ffbc33900aebb166efae655a7c8de94e9a6ba` |
| compact | Structure & Harmony | 720 × 900 | `c503a0ac8b70d8b8c29786dc87fb0fdd1deb130715967e9b75b60049d3df0e82` |
| compact | Arrange | 720 × 900 | `cc04438b84a5e0baf5e33400b672923c42e08890bf8eb2892c83441749382f90` |
| compact | Review | 720 × 900 | `810441442475f11a087ef54e3db980e66b5a78881a5a51d78482987a307cc4af` |
| compact | Export | 720 × 900 | `1baa2dc821e40c2b2c52ef7bf478ca7c0f6542da89d1d019d3b3db794892be55` |

Existing root `desktopApp/build/reports/task-*` screenshots and overlays are
also ignored historical output. Several exercise old audio/video, Library,
Mix/Master and Settings routes; they are neither copied nor used as target
evidence. UI-017 must replace the current image-write/dimension checks with
deterministic expected/actual/diff comparisons for only the six routes.

## Baseline validation

| Command | Result |
| --- | --- |
| `make test` | PASS — 14 Gradle tasks up-to-date (no forced run). |
| `make build` | PASS — 15 Gradle tasks; documentation coverage executed; test tasks up-to-date. |
| `git diff --check` | PASS. |
| `git diff --cached --check` | PASS. |
| reference SHA-256/dimension inventory | PASS — all nine files present at 1536 × 1024. |

No workflow, MIDI export, DAW behavior, visual metric or accessibility behavior
changed in UI-000. UI-001 owns the first objective geometry/color/type targets;
UI-019 owns real visual approval; MC-048I remains awaiting observed musician
sessions after the redesign.
