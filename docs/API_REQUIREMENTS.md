# Backend API Requirements for the New UI

The UI refactor may expose missing backend contracts.

Do not work around missing APIs by guessing state in JavaScript.

Recommended API categories:

## Project

- GET project
- PUT project musical authority
- GET workflow status

### Current local contract

The supported Compose Desktop product uses the typed local application boundary,
not a Spring/HTTP controller. `ProjectApplicationService.getWorkflowStatus`
accepts `GetWorkflowStatus` and returns the normalized `WorkflowReadModel`.
Every ordered step exposes an explicit lifecycle (`LOCKED`, `READY`, `RUNNING`,
`FAILED`, `REVIEW_REQUIRED`, `APPROVED`, `STALE`, or `COMPLETE`), separate
approval state, safe artifact-version IDs and hashes, and the current durable
stage-run job ID/progress/retry/failure state. Artifact filesystem paths and
exception messages are not part of this UI contract.

## Parts

- list parts
- import part
- transcribe
- cleanup
- AI Fix
- AI Enhance
- get artifact versions
- preview artifact

## Structure

- get/update structure
- assemble source song
- get boundaries
- generate melody connections
- update one boundary
- source-song critic
- source approval

## Arrangement

- generate global plan
- update/lock plan
- get ArrangementState
- generate role
- validate role
- accept/reject role
- targeted role correction
- core draft render
- core approval
- optional-layer generation
- transition generation
- ensemble cohesion
- whole-song critic
- targeted polish
- final MIDI approval

## Mix

- render stems
- get stems
- update mix plan
- build dry mix
- build LoFi mix
- run audio critic
- approve mix

## Release

- build master
- commercial-ready validation
- release-similarity critic
- get provenance
- get YouTube metadata
- export

Long-running operations should expose:
- job ID
- state
- progress
- error
- result artifact IDs

Avoid frontend polling random files directly.
