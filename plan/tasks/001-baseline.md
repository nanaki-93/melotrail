# Task 001 — Baseline and Guardrails

## Goal
Understand the existing repository and establish a safe baseline.

## Agent prompt
You are implementing Task 001.

Goal:
Create a reliable baseline for the existing music application before adding the arranger.

First:
1. Read README.md and build/run docs.
2. Inspect the repository tree.
3. Identify Kotlin CLI/orchestration.
4. Identify Python worker and repair/LoFi/master.
5. Run existing tests/build.
6. Identify current pipeline invocation and file formats.

Requirements:
- Do not redesign the application.
- Do not modify DSP behavior.
- Do not add frameworks.
- Document the existing pipeline and where arranger code should live.

Acceptance criteria:
- Existing build succeeds or pre-existing failures are documented.
- Existing tests run.
- Current pipeline command is documented.

Do not rewrite worker/mastering or add database/web/cloud components.

At the end list changed files, commands, and pre-existing failures.
