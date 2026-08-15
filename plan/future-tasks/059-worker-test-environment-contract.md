# Future Task 059 — Worker Test Environment Contract

Status: deferred; not active without explicit promotion.

## Finding

Task 056 finding AUD-056-01: invoking the system `python` cannot import the
worker test dependencies, while `.venv/bin/python` runs the suite successfully.

## Goal

Enforce or otherwise make the selected worker-test interpreter unambiguous
without changing worker algorithms or installing dependencies automatically.

## Scope

Add a focused test/target for the supported virtualenv contract. Keep the
Python worker, optional transcription environment, and dependency pins
unchanged unless separately justified.
