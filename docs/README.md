# MeloTrail UI Refactor Plan

The audio/music engine has evolved substantially, but the UI must now be refactored to expose and control the new workflow.

The UI is not a separate production engine. It is a **visual control surface over the existing pipeline**.

## Main UI workflow

```text
PROJECT
  ↓
SOURCE PARTS
  ↓
SOURCE PREPARATION
  ↓
STRUCTURE
  ↓
MELODY CONNECTION
  ↓
SOURCE APPROVAL
  ↓
ARRANGEMENT PLAN
  ↓
CORE ARRANGEMENT
  ↓
CORE APPROVAL
  ↓
OPTIONAL LAYERS / TRANSITIONS
  ↓
WHOLE-SONG REVIEW
  ↓
FINAL MIDI
  ↓
RENDER / MIX
  ↓
MASTER / RELEASE
```

## Main navigation

Recommended top-level sections:

1. Project
2. Source
3. Structure
4. Arrange
5. Mix
6. Release

Do not expose every backend class as a separate screen.

The UI should translate backend complexity into a coherent production workflow.
