# Task 002 — Project and Part Model

## Goal
Introduce a minimal local project representation.

## Agent prompt
You are implementing Task 002.

Create small models for:
- Project
- Part
- PartAnalysis reference
- ordered structure of part IDs

Use existing JSON serialization conventions.

Project:
```text
project/
  project.json
  parts/
```

Example:
```json
{
  "version": 1,
  "name": "demo",
  "parts": [
    {"id": "A", "file": "parts/A.wav", "role": "verse"}
  ],
  "structure": ["A", "B", "A"]
}
```

Requirements:
- relative paths from project root;
- validate referenced files;
- reject duplicate IDs;
- preserve source files;
- JSON round-trip tests.

Do not add a database or generic repository layer.
