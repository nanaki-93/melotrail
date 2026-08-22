# Melotrail Agent Instructions

## Project
Melotrail is a local AI-assisted MIDI music arranger and producer.

## Architecture
- Kotlin owns orchestration, project/domain models, and CLI/UI.
- Python worker owns audio/MIDI processing.
- Qwen is used for constrained musical planning/editing.
- MIDI is the canonical representation during composition.
- WAV is the canonical representation during audio production.

## Musical invariants
- Project key is authoritative.
- Verse/chorus/bridge chord progressions are authoritative.
- AI must not independently replace project harmony.
- Preserve protected melody anchors.
- AI mutations must be validated.
- Never silently overwrite a known-good MIDI candidate.
- Cohesion runs AFTER arrangement.
- Final polish must be targeted, not an unrestricted rewrite.

## Development
- Inspect existing implementation before changing architecture.
- Always remove old code, don't keep legacy branches.
- Make small changes.
- Add regression tests for bugs.
- Do not rewrite working DSP unless required.
- Preserve intermediate MIDI/audio in debug mode.

## Validation
Run:
make test
make worker-test
make build

## Documentation
Read:
- all the docs files
- PLAN.md
- README.md