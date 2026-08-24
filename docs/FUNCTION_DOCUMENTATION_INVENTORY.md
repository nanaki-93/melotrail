# Function documentation inventory

Inventory reviewed: 2026-08-24

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
