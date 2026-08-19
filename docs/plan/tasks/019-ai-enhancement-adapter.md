# Task 019 — Context-aware AI enhancement adapter

## Goal

Implement a bounded local-model enhancement planner behind Task 018 contracts,
with deterministic validated application and explicit user approval.

## Why

Later musical assistance should improve flow/contour/harmony relationship while
preserving recognizable melody and commercial lineage.

## Dependencies

Task 018. This task is Later and does not block the UI/domain MVP.

## Existing Code

- local Qwen adapters in `MidiAiFix`, song planning, and cohesion
- strict JSON extraction/validation/model dependency provenance
- deterministic MIDI edit applier and preview/approval patterns

## Changes

- Build model input from serialized musical context plus bounded note/phrase
  summaries; do not send paths or let the model choose files.
- Support allowed enhancement goals: phrase endings/flow/contour, severe chord
  clash adjustment, tasteful passing notes, repetition reduction within the
  intensity-specific vocabulary/budget.
- Validate echoed IDs/hashes, operations, range, timing/polyphony/harmony, edit
  budget, anchor retention, and identity-distance metrics.
- Deterministically apply a validated proposal to a draft artifact and report all
  edits/reasons; reject the entire unsafe/malformed plan atomically.
- Require preview and user approval before Enhanced becomes selected; rejection
  keeps evidence and Corrected selection.
- Record provider/model/version/license/template/schema and accepted plan hash.
- Complete the Task 017 cutover: delete the obsolete combined `MidiAiFix` runtime
  implementation, prompt/configuration, dependency wiring, UI actions, selection
  branches, and exclusive tests after legacy artifact metadata is mapped into the
  canonical correction/enhancement evidence model.

## Files

Add enhancement model adapter/prompt template/validator metrics and tests; wire
model config, stage processor, approvals, provenance, and documentation.

## API / Contracts

Use Task 018 plan schema, adding version only through explicit compatible schema
evolution. Approval command includes draft/input/context hashes.

## UI

Show generating/review/rejected/approved state and edit summary; use Task 020 A/B.

## Backend

Local model port is injected/fakeable and never writes artifacts directly.

## Python Worker

Use only if an actual model dependency requires Python; if added, introduce a
versioned capability/command and keep validation/application in Kotlin.

## Tests

Fake valid plans at every intensity, malformed/unsafe/hash mismatch, excessive
identity change, harmony/range/timing violations, rejection/approval, provenance.

## Acceptance

- AI cannot modify files or exceed code-owned bounds.
- Original/Corrected remain selectable and recognizable anchors are protected.
- Unknown model/license prevents commercial-ready status.
- Repository/build searches find no callable combined AI-fix path or obsolete
  configuration/action label.

## Out of Scope

Cloud AI, model training, complete melody generation, harmony/structure changes.
