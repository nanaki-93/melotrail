# Task 105 — Function Documentation Coverage

## Goal

Provide useful maintainers’ documentation for all non-trivial production
functions in the Kotlin application and Python worker, with a repeatable way to
keep that coverage current.

## Dependencies

- Task 102 accepted.

## Requirements

- Create a checked-in documentation inventory covering `../../../../src/main/kotlin`,
  `../../../../desktopApp/src/main/kotlin`, and `../../../../worker`. For each source file, record
  documented, inherited-contract, trivial/generated, or deferred-with-reason.
- Add KDoc/docstrings to all non-trivial functions and methods. Explain intent,
  important invariants, side effects/artifact ownership, inputs and outputs,
  error/retry behaviour, and concurrency/dispatcher ownership when relevant.
- Document public classes, sealed commands, wire schemas, application-service
  entry points, Compose screen-level functions, worker command handlers, and
  non-obvious private algorithms. Do not add boilerplate that merely repeats a
  function name or type signature.
- Keep comments truthful during Tasks 106–109. Remove stale comments discovered
  while documenting instead of preserving contradictory history.
- Add a lightweight offline documentation coverage check (or deterministic
  inventory validator) that fails for newly added non-trivial production
  functions without a classification. Exclude tests and generated/build output.

## Tests

- Unit test the coverage validator with documented, exempt, and missing cases.
- Run the validator together with the relevant Kotlin/Python test suites.

## Acceptance criteria

- Every non-trivial production function has focused documentation or a specific
  reviewable exemption, and future additions are checked automatically.
- Documentation describes actual contracts rather than implementation trivia.

## Out of scope

- Generating a public SDK site or documenting third-party dependencies.
