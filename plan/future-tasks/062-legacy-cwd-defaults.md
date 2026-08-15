# Future Task 062 — Legacy CWD Defaults

Status: deferred; not active without explicit promotion.

## Finding

Task 056 finding AUD-056-05: legacy CLI/Spring adapters retain CWD-relative
storage and a hardcoded worker default outside the packaged desktop boundary.

## Goal

Make each retained legacy default explicit and testable without changing the
desktop sound-library locator or moving project data.

## Scope

Add narrow configuration tests and revise only the affected legacy defaults or
their documentation. Do not refactor audio processing, desktop services, or
the retired frontend.
