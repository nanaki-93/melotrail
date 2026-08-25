# Function documentation inventory

Inventory reviewed: 2026-08-25

[`FUNCTION_DOCUMENTATION_INVENTORY.json`](FUNCTION_DOCUMENTATION_INVENTORY.json)
is the checked-in inventory for every Kotlin/Python production source under
`src/main/kotlin`, `desktopApp/src/main/kotlin`, and `worker/`. It deliberately
excludes tests, build output, and virtual environments.

The validator records both the number and digest of callable declarations. A
new function or declaration change therefore fails verification until the
source row is reviewed. Run it locally with:

```bash
python3 tools/check_documentation_coverage.py --repository .
./gradlew checkDocumentationCoverage
```

Classifications have the following meanings:

- `documented` — every callable in the source has directly attached KDoc or a
  Python function docstring.
- `inherited-contract` — each callable implements a separately documented
  contract; the row explains the owner.
- `trivial/generated` — the source has no callable declarations or consists
  solely of generated code.
- `deferred-with-reason` — a source-specific legacy exemption. Its reason and
  declaration digest are review evidence, not a substitute for documenting a
  changed behavior or contract.

When editing a callable, add or update its focused KDoc/docstring first, then
refresh that source row's function count, direct-documentation count, and
declaration digest. Do not copy an old exemption to a new function. The
validator's failure identifies the row requiring review; the focused unit test
in `worker/tests/test_documentation_coverage.py` exercises documented, exempt,
missing, and stale-declaration cases offline.

The 2026-08-24 consolidation refreshed every discovered source row. Existing
source-specific classifications were preserved when still valid; newly found
legacy callables were classified locally rather than hidden behind a global
exception.

QP-013 refreshed the affected Cohesion, critic-candidate, and comparison rows
after their boundary-local rendering and approval contracts changed.

QP-014 refreshed the full-song Critic, targeted-enhancement, workflow-evidence,
and local-planner rows after complete actionable-batch evidence and
improvement-gated candidate selection were added.

QP-015 refreshed the desktop review, workspace orchestration, and preview
application-service rows after adding verified canonical-melody evidence,
typed source/prepared/full/boundary monitor requests, and peak-safe RMS
matching for those opt-in piano previews.

QP-016 refreshes the mix, mastering, desktop, worker-protocol, and local
codec-preview rows after adding hash-bound low-end plans and explicit local
delivery-codec evidence. The inventory retains the locally scoped legacy
declaration exemptions only where direct KDoc/docstrings are still incomplete.

QP-017 adds a fully documented quality-review evidence service. It produces
immutable MIDI/WAV debug copies and an explicitly pending listening form;
offline tests cannot populate listener, date, device, or decision fields.

QP-018 refreshes commercial provenance and the model-license conversion boundary
after adding an explicit approved canonical-melody closure, structured AI-use
review input, and human-review-only YouTube release metadata. Historical v2/v3
manifests remain readable evidence; new v4 manifests block commercial readiness
when those current inputs are unresolved.

The 2026-08-25 live five-source review also refreshes the affected MIDI-fix,
planning, Cohesion, enhancement, selection, drum, pad, and pattern-library rows.
It covers occurrence-scoped validation, catalog-driven lo-fi grooves and fills,
authoritative-harmony chord comping, and the bounded no-bass arrangement path.
