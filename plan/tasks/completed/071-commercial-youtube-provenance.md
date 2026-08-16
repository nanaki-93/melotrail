# Task 071 — Commercial and YouTube-Oriented Provenance

## Goal

Give creators a truthful commercial-readiness report and export evidence bundle
for music they intend to monetize on YouTube.

## Dependencies

- Task 070 accepted.

## Policy baseline

Capture and re-check at implementation/release time:

- [YouTube AI-use disclosure](https://support.google.com/youtube/answer/14328491)
- [YouTube channel monetization policies](https://support.google.com/youtube/answer/1311392)

As of 2026-08-16, YouTube's official guidance identifies AI-generated music as
content to disclose through its AI-use workflow, says disclosure itself does not
limit monetization eligibility, and evaluates monetized channels for original,
authentic, non-repetitive/non-mass-produced content. Policies can change.

## Requirements

- Add a user attestation for every imported source stating whether the creator
  owns it, has commercial permission, believes it is public domain, or has not
  established rights. Record the choice and date; never infer ownership.
- Explain that transposition, timing changes, repair, arrangement, or AI patching
  do not automatically clear the rights attached to an input melody.
- Extend the existing model registry so every model used in transcription,
  planning, cohesion, repair assistance, or generation has identity/version,
  weight/code license, commercial permission, output-rights note, hash, and
  reviewed status.
- Validate every used sound library/sample against a versioned registry with
  source, license, commercial use, attribution, redistribution, and content hash.
- A missing, unknown, conditional-unreviewed, or blocked commercial term must
  prevent the label `Commercial-ready`; it may still allow local work with a
  visible warning when legally and technically permissible.
- Generate a machine-readable provenance manifest and human-readable commercial
  report next to the final master. Include:
  - user source attestation and source hashes;
  - raw, repaired, lo-fi, cohesion, arrangement, stem, mix, and master hashes;
  - deterministic operation/profile versions;
  - AI model and prompt-contract identity plus approval decisions;
  - sound/sample licenses and required attribution;
  - export format and final release hash.
- Generate a YouTube upload checklist that recommends enabling the AI-use
  disclosure for AI-generated music and reminds the creator to add required
  attribution and create original, non-mass-produced video/channel value.
- State prominently that the report is evidence and workflow assistance—not
  legal advice, copyright clearance, Content ID clearance, or a monetization
  guarantee.
- Never upload, contact YouTube, or submit a monetization application.
- Never market content as “copyright free” solely because Melotrail processed it.
- Add a release-time documentation check so linked official platform policies
  are reviewed for changes before shipping.

## Tests

- Commercial-readiness decision table for owned/permitted/public-domain/unknown
  sources and approved/conditional/unknown/blocked models and samples.
- Manifest determinism, completeness, hash binding, attribution, tamper/stale
  detection, and path-safety tests.
- UI tests for attestation, warning, blocking status, disclosure checklist, and
  non-guarantee wording.
- Fixtures proving an otherwise valid master is not labeled commercial-ready
  when any used dependency has unresolved commercial terms.
- Documentation link and policy-review-date checks.

## Acceptance criteria

- Every commercial-ready export is traceable to attested sources and reviewed
  commercial-use assets/models.
- AI transformations and generated music are clearly identified for disclosure.
- Unknown rights never appear as approved.
- The UI and reports never promise YouTube monetization or legal clearance.
- Provenance remains valid only while all referenced hashes match.

## Out of scope

- Legal advice, copyright registration, rights acquisition, Content ID dispute,
  YouTube upload, or Partner Program application.
- Guaranteeing originality at the legal or platform-review level.
- Monitoring YouTube policy automatically over the network.

