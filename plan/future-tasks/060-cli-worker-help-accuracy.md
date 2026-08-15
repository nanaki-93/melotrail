# Future Task 060 — CLI Worker Help Accuracy

Status: deferred; not active without explicit promotion.

## Finding

Task 056 finding AUD-056-02: CLI help calls `--worker-url` process-based and
unused even though active adapters use the HTTP worker and `WORKER_BASE_URL`.

## Goal

Make CLI help and accepted worker configuration truthful while retaining
documented compatibility behavior.

## Scope

Add focused parser/help coverage and update only the affected CLI/help text.
Do not change worker orchestration, endpoint contracts, or unrelated CLI output.
