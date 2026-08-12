# Task 003 — CLI Project Creation and Part Import

## Goal
Create a project and add audio parts.

## Agent prompt
Inspect the existing CLI and implement commands conceptually equivalent to:

music-cli project create ./projects/demo
music-cli part add ./projects/demo --id A --file ./piano.wav --role verse

Requirements:
- create project directory and project.json;
- copy input into project/parts;
- preserve source;
- validate supported audio;
- reject duplicate IDs;
- update project.json;
- tests for creation and duplicates.

Do not analyze or generate audio yet.
