# Ordered implementation tasks

These tasks are binding and sequential. Each task must preserve original source
and known-good artifacts, add regression evidence, update affected operational
documentation, pass its checks, and end in exactly one focused commit. Do not
combine task commits or implement a later task early.

Global invariants for every task:

- project key/mode, tempo/meter, structure, and section harmony are authoritative;
- source files and approved candidates are immutable;
- MIDI remains canonical during composition and WAV during production;
- Qwen proposes bounded plans; deterministic code validates/applies them;
- protected anchors are never silently changed;
- project-relative paths, SHA-256 lineage, atomic publication, and stale
  invalidation remain mandatory;
- an experimental bypass cannot satisfy a quality-certified gate;
- tests remain offline unless the task explicitly names an opt-in live smoke;
- obsolete replaced code and exclusive tests/docs are removed in the owning task.

## QP-001 — Establish the composition-quality baseline

Goal: turn the current failure into repeatable evidence before changing musical
behavior.

Implement:

- Add compact deterministic MIDI fixtures reproducing fractional bars,
  mismatched beat phase, mode mismatch, overlapping melody notes, chord clashes,
  flat arrangement density, and unsafe boundary roles.
- Add a quality-measurement harness for occurrence phase, bar residual, maximum
  melody polyphony, scale/chord exposure, boundary jumps, role phase, density
  contrast, critic totals, and selected-artifact lineage.
- Keep the real four-source E2E opt-in and project-local; do not commit the large
  `data/audio` tree.
- Record calibrated PPQ-scaled policy inputs without claiming listening quality.

Tests and acceptance:

- The fixture deterministically demonstrates each known defect.
- Existing sources/artifacts are unchanged.
- `make test` and `make worker-test` pass; record any pre-existing build issue.

Commit: `quality-pipeline: QP-001 establish composition quality baseline`

## QP-002 — Publish beat, onset, tempo, and downbeat evidence

Goal: expose the musical time evidence needed to align independent performances.

Implement:

- Version the worker analyze contract and return beat times/frames, onset times,
  tempo candidates/confidence, leading activity, and bounded downbeat evidence.
- Remove the current behavior that computes onsets and then publishes an empty
  list.
- Define explicit `UNKNOWN/REVIEW_REQUIRED` evidence when a downbeat cannot be
  established safely.
- Add matching Kotlin protocol/domain validation, capability negotiation, path
  confinement, and safe error mapping.
- Keep the worker stateless; Kotlin persists reports and decisions.

Tests and acceptance:

- Synthetic click/pickup fixtures verify beat/downbeat/onset positions and low-
  confidence behavior.
- Malformed/empty/silent inputs fail or report uncertainty without invented beats.
- Worker and Kotlin contract tests pass offline.

Commit: `quality-pipeline: QP-002 add beat and downbeat evidence`

## QP-003 — Conform source timing to the project grid

Goal: map performed musical time onto the declared project tempo/meter without
merely relabeling tempo metadata.

Implement:

- Add an immutable `SourceTimingDecision` and `MidiTimeMappingReport` containing
  source beats/downbeat, target beats, target bars, pickup/body/tail windows,
  residuals, confidence, policy version, and hashes.
- Warp note/controller ticks piecewise onto the project beat grid while
  preserving ordering, positive duration, and expressive within-beat offsets
  inside a bounded policy.
- Ordinary occurrences occupy whole canonical bars. Pickups/tails are explicit
  typed windows that never shift the following occurrence.
- Require user review for low confidence, large duration change, or ambiguous
  target-bar count.
- Publish a derived candidate; never overwrite normalized/transposed input.

Tests and acceptance:

- The QP-001 fractional sources produce zero uncontrolled bar-phase accumulation.
- Repeated use of one source has identical local mapping and distinct global
  occurrence bounds.
- Tempo/meter, source hashes, and original MIDI are preserved as evidence.

Commit: `quality-pipeline: QP-003 align sources to the project grid`

## QP-004 — Make project-key transposition mode-aware

Goal: adapt confirmed source key and mode into the authoritative project key and
mode.

Implement:

- Retain ordinary chromatic tonic transposition when modes match.
- When modes differ, map recognized source scale degrees to the corresponding
  target scale degrees while retaining register/contour and explicit octave-fold
  policy.
- Preserve percussion and every non-pitch MIDI property.
- Report mode-adjusted movements and unresolved chromatic source notes.
- Never auto-confirm source-key evidence below the current confidence gate.
- Version reports/processors so cached tonic-only output becomes stale.

Tests and acceptance:

- C major -> C natural minor maps E/A/B to Eb/Ab/Bb.
- G major -> C natural minor maps all seven degrees correctly.
- Same-mode/enharmonic/drum/range behavior remains deterministic.
- Input note count and timing are unchanged.

Commit: `quality-pipeline: QP-004 implement mode-aware transposition`

## QP-005 — Prepare one monophonic melody per source section

Goal: remove transcription chords, doubled notes, and overlaps before harmony
repair.

Implement:

- Collect note-bearing non-drum events across source tracks into a deterministic
  melody candidate.
- Resolve simultaneous/overlapping candidates using transcription confidence
  when present, then bounded continuity/velocity/duration rules.
- Persist every selection, removal, deduplication, trim, ambiguity, and source
  note identity in a preparation report.
- Block ambiguous material outside the safe policy rather than guessing.
- Emit one note-bearing track and enforce global, cross-pitch/channel polyphony
  of at most one.

Tests and acceptance:

- Chords, octave doubles, same-pitch overlaps, cross-pitch overlaps, channels,
  repeated note-ons, and malformed pairs have fixtures.
- The result is deterministic, non-empty when valid, and globally monophonic.
- Source and earlier selected artifacts remain byte-identical.

Commit: `quality-pipeline: QP-005 enforce monophonic source melody`

## QP-006 — Fit prepared melody to project scale and section harmony

Goal: correct invalid melody pitches using the authoritative chord active in the
section occurrence.

Implement:

- Build/reuse one occurrence/tick harmonic timeline from project authority.
- Allow short weak-position project-scale passing tones; require strong, long,
  landing, and otherwise exposed tones to be active chord tones.
- Treat explicit active chord tones as authorized chromatic harmony when the
  user-authored chord lies outside the base scale.
- Choose nearest valid pitches with contour/register preservation and a strict
  movement/edit budget; block ambiguous or excessive repair.
- Check notes crossing chord boundaries and split/shorten or request review
  under an explicit policy.
- Publish note-level before/after/reason evidence and derive protected anchors
  only after the deterministic candidate is valid.

Tests and acceptance:

- Intro/verse/chorus/bridge/outro fixtures use different progressions.
- Exposed clashes are fixed, legitimate passing tones survive, and authority is
  never rewritten.
- Every output note satisfies the versioned eligibility rule.

Commit: `quality-pipeline: QP-006 fit melody to canonical harmony`

## QP-007 — Assemble the canonical structured full melody

Goal: create one complete melody before arrangement while retaining exact song
structure.

Implement:

- Upgrade source-song assembly to one conductor track plus one note-bearing full
  melody track.
- Persist section markers and a versioned sidecar with stable occurrence IDs,
  roles, part IDs, bar/tick/pickup windows, chord spans, preparation reports,
  note lineage, anchors, and hashes.
- Adapt melody-connection identity to the assembled full melody instead of
  reconstructing per-source note identities.
- Prevent cross-boundary overlap except an explicit validated tie.
- Use a new content-addressed processor-version path so old candidates remain
  inspectable and are never overwritten.

Tests and acceptance:

- Repeated sections remain distinct occurrences in the sidecar.
- Exactly one note-bearing track exists and maximum melody polyphony is one.
- Full duration, markers, harmony spans, hashes, and canonical PPQ agree.

Commit: `quality-pipeline: QP-007 assemble canonical full melody`

## QP-008 — Cut every downstream melody consumer over to the approved full melody

Goal: make the approved full melody the single piano/melody truth.

Implement:

- Extend source-song approval resolution to return the exact connected artifact,
  sidecar, context, and hashes.
- Make arrangement planner context/state, generated-role validators, Cohesion,
  full-song criticism/enhancement, humanization, stem rendering, preview, and
  release lineage consume that artifact.
- Provide occurrence views by clipping the full melody through sidecar windows;
  do not rebuild timing by concatenating part durations.
- Remove the superseded selected-part/occurrence fallback from current piano
  paths once all callers are migrated.
- Any post-connection melody edit must publish a new full candidate and rerun
  monophony/harmony/anchor validation.

Tests and acceptance:

- Every downstream piano reference equals the approved connected-melody hash.
- A stale/missing approval blocks with recovery instead of falling back.
- Rendered timeline section bounds match the canonical sidecar.

Commit: `quality-pipeline: QP-008 adopt canonical melody downstream`

## QP-009 — Correct selected-artifact composition and invalidation

Goal: make optional per-track transformations form one explicit chain.

Implement:

- Define one resolver order for transposed -> corrected -> AI Fix -> Enhance ->
  Feel -> prepared.
- Ensure selected Feel applies to the current approved upstream candidate rather
  than being ignored when Enhance is selected.
- Represent zero-edit results as `NO_OP`, not a misleading enhanced selection.
- Bind every step to input/context/processor hashes and correct downstream stale
  propagation.
- Delete competing selection helpers after caller migration.

Tests and acceptance:

- The full selection matrix, including Enhance + Feel, has regression coverage.
- Changing any selected branch produces the expected prepared/full-melody hash
  and exact invalidation set.

Commit: `quality-pipeline: QP-009 fix selected artifact lineage`

## QP-010 — Strengthen the Source Song Critic and approval gate

Goal: prevent invalid source composition from entering arrangement.

Implement:

- Add hard checks for timing mapping, explicit bar/pickup windows, global
  monophony, key eligibility, exposed chord fit, structure coverage, anchors,
  extreme jumps, and canonical artifact lineage.
- Replace the current scale-tone-or-chord-tone shortcut with the exposure-aware
  QP-006 policy.
- Quality-certified flow cannot override hard invariants or auto-confirm low-
  confidence key evidence.
- Retain private-audition override only where policy permits and label its
  downstream output experimental.
- Surface complete counts even if UI issue details are capped.

Tests and acceptance:

- Every QP-001 defect blocks the correct gate until repaired.
- A valid prepared melody passes without an override.
- Approval becomes stale after any authority or canonical-melody change.

Commit: `quality-pipeline: QP-010 enforce source composition quality`

## QP-011 — Make arrangement planning expressive and section-aware

Goal: replace generic flat “vibes” with bounded profile/mood/groove/section
intent.

Implement:

- Pass resolved profile/mood groove, voicing, density, register, articulation,
  energy, and section-purpose constraints into global and detailed planners.
- Make prompt examples unmistakably non-executable examples; validate against
  copying a constant schema default across every role/section.
- Keep deterministic section variation authoritative or constrain Qwen changes
  within versioned bounds.
- Require meaningful planned section contrast while respecting user choices and
  legitimate repeated-section continuity.
- Keep instrument assignment separate from musical role intent.

Tests and acceptance:

- Intro/verse/chorus/bridge plans differ intentionally in at least their
  approved energy/density/role behavior.
- Qwen fixture output that copies flat defaults is rejected or normalized by an
  explicit rule.

Commit: `quality-pipeline: QP-011 improve arrangement musical intent`

## QP-012 — Validate generated roles against the accepted ensemble state

Goal: admit bass, drums, pad, strings, and transitions only when they support the
canonical melody and harmony.

Implement:

- Unify role validation for note integrity, range/capabilities, canonical chord
  fit, beat/downbeat phase, density, section activity, melody masking/collision,
  and transition bounds.
- Generate and validate incrementally so each role sees accepted prior tracks.
- Fix density-direction diagnostics and distinguish deliberate silence from
  generator failure.
- Persist comparable before/after and role metrics.

Tests and acceptance:

- Off-phase drums/bass, chord-clashing bass, masked melody, excessive density,
  inactive-section notes, and valid silence have fixtures.
- Failed role candidates never enter `ArrangementState`.

Commit: `quality-pipeline: QP-012 strengthen generated role validation`

## QP-013 — Rebuild Ensemble Cohesion as a boundary-local stage

Goal: make transitions connect the actual adjacent arrangement without adding
unrelated instruments or harmony.

Implement:

- Compute active, entering, exiting, and supported roles per boundary.
- Make `CONTINUITY` sustain/tie an active role or no-op; remove unconditional
  drum-fill mapping.
- Execute or remove every plan field (`bars`, rhythmic gesture, harmonic
  handoff, energy contour) so persisted intent matches rendered MIDI.
- Constrain bass walks and pitched bridges to active harmony; make drum fills
  follow the canonical beat phase.
- Use overlay-aware merge/ducking and rerun full role/melody validation.
- Persist actual before/after note evidence and critic metric deltas.

Tests and acceptance:

- No bridge uses a role inactive on both sides without an explicit reviewed
  entry action.
- The saved problematic five-boundary plan is covered by deterministic fixtures.
- Approved Cohesion cannot increase blocker/critical counts.

Commit: `quality-pipeline: QP-013 make cohesion boundary-local`

## QP-014 — Make whole-song criticism and polish improvement-gated

Goal: prevent broad or ineffective final rewrites and silent bypass.

Implement:

- Critic evaluates all issues and stores complete aggregates even when display
  details are bounded.
- Correct metric directionality and add canonical melody, groove, harmony,
  masking, density/contrast, boundary, and role-activity checks.
- Targeted enhancement receives complete target evidence in bounded batches;
  it cannot change authority or unreported windows.
- Selection requires preserved anchors/invariants and measurable improvement in
  targeted metrics without new critical issues.
- A caught model error becomes an explicit failed/retry/bypass decision; it
  cannot silently select bypass in a quality-certified run.

Tests and acceptance:

- No-op, regression, partial improvement, and genuine improvement candidates
  are distinguished.
- Large issue sets are neither silently truncated nor underfed to correction.

Commit: `quality-pipeline: QP-014 gate targeted full-song polish`

## QP-015 — Expose canonical melody preparation and quality review in the UI

Goal: let the musician understand and approve what changed before arrangement.

Implement:

- Show source key/confidence, timing/downbeat mapping, target bars/pickup,
  monophony removals, pitch/harmony repairs, protected anchors, blockers, and
  exact artifact selection.
- Provide loudness-matched source/prepared/full-melody and boundary previews.
- Show the structure timeline from canonical sidecar windows.
- Distinguish private audition, quality-certified build, and commercial-evidence
  readiness.
- Keep one primary next action with accessible detailed evidence; do not add a
  generic “improve everything” control.

Tests and acceptance:

- Compose tests cover ready/review/blocked/stale/error states, keyboard access,
  and wide/medium/narrow layouts.
- UI never infers completion from file existence or hides an experimental bypass.

Commit: `quality-pipeline: QP-015 add canonical melody quality review UI`

## QP-016 — Prove composition and production quality end to end

Goal: validate the complete song, not merely artifact creation.

Implement:

- Add deterministic reference-song integration covering all ordinary pipeline
  stages and the QP-001 metrics.
- Upgrade the opt-in four-source E2E to reject unresolved key, timing, harmony,
  monophony, role, Cohesion, and critic blockers; remove automatic quality
  overrides.
- Assert the selected artifact kind/hash at every handoff and verify sources are
  unchanged.
- Produce debug comparison MIDI/WAV and a structured listening record using
  [`QUALITY_GATES.md`](QUALITY_GATES.md).
- Validate production mix/master format, duration, clipping/peak, loudness,
  masking/low-end, stereo, and melody-audibility evidence without treating one
  target number as a YouTube guarantee.

Tests and acceptance:

- `make test`, `make worker-test`, and `make build` pass.
- The deterministic reference song passes all hard gates.
- The real four-source run has recorded renderer/model/listening evidence before
  a release-quality claim is made; unavailable dependencies remain explicitly
  unverified.

Commit: `quality-pipeline: QP-016 prove end-to-end song quality`

## QP-017 — Close release evidence, policy, cleanup, and documentation

Goal: finish with one honest commercial-evidence workflow and no obsolete
pipeline/documentation branches.

Implement:

- Bind source/instrument/sample/model/AI-use/arrangement/mix/master/export
  evidence to the exact selected canonical melody lineage.
- Generate current AI-use disclosure guidance and release metadata for human
  review; never claim platform approval.
- Retain signature-motif and cross-release arrangement similarity as evidence,
  with human originality review and bounded re-plan scope.
- Update policy review date/links, operational docs, release checklist, function
  documentation inventory, and all affected KDoc/docstrings.
- Remove superseded occurrence-piano reconstruction, old selection helpers,
  obsolete flags/DTOs/tests/docs, and temporary compatibility paths proven
  unreachable after the cutover.
- Run link/reference searches, documentation coverage, clean module builds, and
  applicable package/manual gates.

Tests and acceptance:

- No dangling plan/task/prompt link or removed symbol/path remains.
- Commercial evidence distinguishes complete evidence from monetization
  guarantee and blocks unresolved rights/attribution/model/AI-use review.
- `make test`, `make worker-test`, and `make build` pass from the final tree.
- Release acceptance records every unverified manual dependency honestly.

Commit: `quality-pipeline: QP-017 close release readiness and cleanup`
