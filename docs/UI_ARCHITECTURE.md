# UI Architecture

## Principle

The backend remains authoritative.

The UI:
- reads project/stage status;
- displays artifacts and validation reports;
- starts explicit pipeline stages;
- allows bounded user overrides;
- never duplicates music-generation logic.

## Recommended application shell

```text
┌───────────────────────────────────────────────────────────────┐
│ MeloTrail          Song Name                   Worker ● Ready │
├──────────────┬────────────────────────────────────────────────┤
│ Project      │                                                │
│ Source       │              MAIN WORKSPACE                    │
│ Structure    │                                                │
│ Arrange      │                                                │
│ Mix          │                                                │
│ Release      │                                                │
│              │                                                │
├──────────────┴────────────────────────────────────────────────┤
│ Pipeline status / current stage / errors / background jobs   │
└───────────────────────────────────────────────────────────────┘
```

## UI state model

The UI should consume a normalized project workflow state:

```json
{
  "projectId": "...",
  "currentStage": "CORE_ARRANGEMENT",
  "stages": {
    "SOURCE_IMPORT": "DONE",
    "CLEANUP": "DONE",
    "AI_FIX": "DONE",
    "AI_ENHANCE": "DONE",
    "STRUCTURE": "DONE",
    "MELODY_CONNECTION": "DONE",
    "SOURCE_APPROVAL": "APPROVED",
    "ARRANGEMENT_PLAN": "DONE",
    "CORE_ARRANGEMENT": "READY_FOR_REVIEW",
    "CORE_APPROVAL": "WAITING",
    "ENSEMBLE_COHESION": "LOCKED",
    "MIX": "LOCKED",
    "RELEASE": "LOCKED"
  }
}
```

The UI must not infer stage completion from the existence of random files.

Use explicit backend state/contracts.
