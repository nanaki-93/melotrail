# Task 008 — Local Qwen Arrangement Planner

## Goal
Add optional local Qwen planning.

## Agent prompt
Implement a LocalQwenArrangementPlanner using the local model interface available on the machine through lm studio in http://127.0.0.1:1234.

The model receives:
- project metadata
- part analyses
- structure
- allowed instruments
- style
- constraints

Require JSON-only output matching arrangement schema v1.

Requirements:
- parse and validate;
- allow-list instruments/transitions;
- density 0..1;
- reject invalid output clearly;
- no arbitrary paths;
- deterministic planner remains explicitly selectable.

Do not execute model output, model code, shell commands, or model-supplied paths.

Add fixture-based tests that never require a live model.
