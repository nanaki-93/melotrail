# Task 069 — AI Cohesion and Bounded Melody Patching

## Goal

Add a reviewable AI Cohesion step immediately after Structure that connects all
structure occurrences and may transpose, time-adjust, repair, or patch their
melodies without modifying source/raw/repaired/lo-fi MIDI.

## Dependencies

- Tasks 067 and 068 accepted.

## Requirements

- Split current arrangement generation so whole-song cohesion is a separate
  stage and artifact; detailed instrument arrangement remains the next stage.
- Build cohesion input from the saved structure, stable occurrence IDs, selected
  repaired/lo-fi MIDI, analyses, keys, tempo/meter, chords, pitch ranges, energy,
  and boundary summaries.
- Create one derived melody per structure occurrence because repeated parts may
  need different entry, exit, key, or timing treatment.
- The model returns strict versioned JSON only. It may propose allow-listed:
  - whole-occurrence or bounded-range transposition within validated pitch and
    instrument ranges;
  - tempo/timing alignment and bounded quantization/time scaling;
  - trimming or extending notes at section boundaries;
  - removal of invalid/colliding notes;
  - bounded gap patches and pickup/transition melody material;
  - transition type, energy, and crossfade/bridge intent.
- Define conservative numerical bounds in code and schema. Model output cannot
  provide paths, commands, plugins, arbitrary instruments, executable code, or
  edits outside known occurrence/tick ranges.
- A deterministic Kotlin transformation engine applies the validated plan. AI
  never writes MIDI and never overwrites an existing canonical artifact.
- Validate every result for MIDI structure, pitch/instrument range, note
  duration, collisions, tempo/meter, bar alignment, and boundary continuity.
- Produce an audit report listing every transposition, timing shift, removed
  note, extended/trimmed note, and generated patch with rationale and source
  occurrence.
- Present before/after and boundary A/B preview plus explicit approve, reject,
  and regenerate actions. Qwen/model output is never auto-approved.
- Use the approved cohesion artifact as the source for detailed arrangement and
  transition generation. Retain a deterministic no-AI cohesion fallback.
- Integrate or adapt the existing arrangement critic to review whole-song energy,
  repetition, transition strength, and unsafe/excessive changes.
- Record model identity/version/hash, prompt-contract version, settings, input
  hashes, output hashes, and approval decision for commercial provenance.
- Block commercially marked output if the selected model is not approved for
  commercial use in the model registry.
- Do not offer artist-imitation controls or claim that transformation removes
  copyright obligations attached to the input melody.

## Tests

- Strict schema and adversarial validation tests for paths, commands, unknown
  IDs, excessive transpose/time scale, out-of-range notes, excessive patches,
  overlapping edits, malformed JSON, and stale input hashes.
- Pure transformation-engine tests for every edit type and combined plans.
- Per-occurrence tests proving repeated source parts can receive different
  derived edits while sharing immutable input.
- Deterministic fallback, critic, approval/rejection, regeneration, and atomic
  publication tests.
- A/B preview and semantic loading/error UI tests.
- End-to-end structure-to-cohesion-to-arrangement fixture tests.

## Acceptance criteria

- AI Cohesion is a visible stage immediately after Structure and before detailed
  arrangement.
- It can validly transpose, time-adjust, repair, and patch a melody within
  documented limits.
- Every AI edit is reviewable, attributable, reversible, and applied by
  deterministic code to a derived occurrence artifact.
- Rejecting cohesion leaves all prior artifacts unchanged.
- Detailed arrangement cannot consume an unapproved or stale AI cohesion plan.

## Out of scope

- Free-form DAW editing or unrestricted note generation.
- Training/fine-tuning models or uploading source music to cloud services.
- Voice cloning or exact living-artist imitation.
- Guaranteeing copyright clearance through transformation.

