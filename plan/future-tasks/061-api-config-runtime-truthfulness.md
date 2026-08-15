# Future Task 061 — API Configuration Runtime Truthfulness

Status: deferred; not active without explicit promotion.

## Finding

Task 056 finding AUD-056-03: `GET /api/config` returns separate literal
defaults when the running Spring service uses environment-supplied settings.

## Goal

Make the retained local JSON API’s configuration response explicitly match the
active server configuration or identify an intentionally separate persisted
configuration.

## Scope

Add a narrow Spring API regression test and change only the configuration
adapter/response contract required by that test. Preserve project data, API
controllers, CLI, and Compose behavior.
