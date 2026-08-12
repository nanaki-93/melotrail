# Task 006 — Arrangement Schema and Deterministic Planner

## Goal
Create arrangement.json and a deterministic planner.

## Agent prompt
Define version 1 of arrangement.json and implement a planner requiring no AI.

Input:
- project
- analyses
- structure
- requested instruments
- optional style

For the first deterministic implementation:
- source part remains intact;
- generated instruments may be empty or follow simple fixed rules.

Example:
```json
{
  "version": 1,
  "sections": [
    {
      "index": 0,
      "partId": "A",
      "instruments": [
        {"name": "piano", "mode": "source"},
        {"name": "bass", "mode": "generated", "role": "root_fifth", "density": 0.3}
      ],
      "transitionOut": {"type": "none", "bars": 0}
    }
  ]
}
```

Validate IDs, instrument modes, density 0..1, and transition values.

Do not connect Qwen or generate audio yet.
