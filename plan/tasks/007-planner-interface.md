# Task 007 — Arrangement Planner Interface

## Goal
Separate planning from rendering.

## Agent prompt
Add:

```kotlin
interface ArrangementPlanner {
    fun plan(input: ArrangementInput): Arrangement
}
```

Implement DeterministicArrangementPlanner.

Keep model-specific/HTTP details out of domain classes. Add tests and a CLI option for deterministic planning.

Do not implement Qwen yet.
