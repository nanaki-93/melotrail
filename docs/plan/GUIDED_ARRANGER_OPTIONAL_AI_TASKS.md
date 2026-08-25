# Optional guided-arranger AI tasks

**Status:** FROZEN<br>
**Not part of the mandatory GA-001–GA-014 execution prompt**

After explicit authorization, use
[`EXECUTE_GUIDED_ARRANGER_OPTIONAL_AI_PROMPT.md`](EXECUTE_GUIDED_ARRANGER_OPTIONAL_AI_PROMPT.md).

Do not start this file merely because the deterministic implementation is
finished. Phase 7 requires all of:

- GA-014I complete and committed;
- current hash-bound H5-01 listening evidence passes;
- a clean working tree and green standard validation;
- explicit new user authorization to start optional AI;
- agreement that AI remains suggestion-only and cannot change melody/harmony.

If any condition is missing, stop. The main execution prompt must not infer
authorization from this document.

## GA-AI-001 — Define bounded arrangement suggestions

Add a strict versioned schema containing only existing StylePack IDs and
permitted arrangement-choice parameters. Establish an offline deterministic
baseline to beat. Reject prose, note events, chords, unknown IDs, absolute
paths, and undeclared fields.

**Acceptance:** generated output cannot represent a melody/harmony edit or a new
pattern; compatibility is validated before a suggestion candidate exists.

**Commit:** `guided-arranger-ai: GA-AI-001 define bounded suggestions`

## GA-AI-002 — Add the local suggestion adapter

Add the local Qwen/model boundary with strict JSON, bounded context, timeout,
capability, model/license, and failure evidence. Do not automatically apply a
fallback/default suggestion when the model fails.

**Acceptance:** unknown/extra fields, prose, malformed JSON, incompatible IDs,
timeout, missing model, and license-not-ready all reject safely without changing
the active arrangement.

**Commit:** `guided-arranger-ai: GA-AI-002 add local suggestion adapter`

## GA-AI-003 — Publish immutable suggestion candidates

Validate model output against the exact project/style-pack revision and publish
a content-addressed suggestion plus visible arrangement-choice diff. It never
becomes active because a file exists.

**Acceptance:** stale inputs reject; candidate lineage is complete; melody,
harmony, sources, and current arrangement hashes remain unchanged.

**Commit:** `guided-arranger-ai: GA-AI-003 validate suggestion candidates`

## GA-AI-004 — Add suggestion review UI

Add preview/A-B audition, explicit apply, rejection, and undo. Applying creates
a normal immutable `ArrangementPlan` revision through the same application
command used by manual edits.

**Acceptance:** no special AI mutation path; user can inspect every changed
choice; rejection has no musical side effect; stale async callbacks cannot apply.

**Commit:** `guided-arranger-ai: GA-AI-004 add suggestion review UI`

## GA-AI-005 — Evaluate or remove the experiment

Run a holdout randomized A/B evaluation against the deterministic default using
the dimensional listening system. Record usefulness, correction time, failure
rate, and preference without changing the MVP gate.

**Acceptance:** keep the feature only if current human evidence shows a material
improvement without authority/integrity regressions. Otherwise remove its
production entry points in this task and retain only the evaluation evidence.

**Commit:** `guided-arranger-ai: GA-AI-005 evaluate arrangement suggestions`

After GA-AI-005, rerun the full standard validation and report that optional AI
is advisory. Never describe it as autonomous production or publishable-song
generation.
