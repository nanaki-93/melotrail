# Prompt: execute optional guided-arranger AI tasks

Use this prompt only after GA-014I, a passing current H5-01 gate, and a new
explicit user decision to experiment with suggestion-only AI.

```text
Implement the frozen optional AI sequence in
docs/plan/GUIDED_ARRANGER_OPTIONAL_AI_TASKS.md from GA-AI-001 through GA-AI-005.

Before acting, read AGENTS.md, root PLAN.md, the entire mandatory guided-arranger
task/log history, the optional task file, and affected code/docs. Verify:
- GA-014I is complete and its exact commit exists;
- H5-01 evidence is current and passing;
- the user explicitly authorized optional AI in this session;
- git status --short is empty;
- make test, make worker-test, and make build pass.

If any condition fails, stop. Never infer authorization from the presence of
this file.

Execute one GA-AI task at a time. For each:
1. Inspect current code/tests/docs and run focused baseline tests.
2. Implement only the selected task. AI may suggest existing StylePack IDs and
   permitted arrangement choices only; it may not change sources, approved
   melody, tempo, meter, key, bars, structure, harmony, style-pack data, or
   renderer/mix authority.
3. Add offline regression tests for malformed/prose/extra output, unknown IDs,
   stale context, timeout, missing model/license, rejection, and no-side-effect
   behavior as applicable.
4. Run focused tests, make test, make worker-test, make build, documentation
   coverage when applicable, and git diff --check.
5. Review all changes for unrelated files, silent application/fallback, fake
   approval, unsafe paths, user assets, binaries, and unsupported claims.
6. Stage explicit paths only, inspect the cached diff, and create exactly one
   local commit using the task's exact commit subject.
7. Verify a clean tree and report the SHA before continuing.

Do not amend/rebase/squash/push/tag/publish/upload, use git add -A, or run a
destructive clean/reset/checkout. Only one writer agent may edit/commit; read-only
audit subagents are allowed.

GA-AI-005 requires real user A/B ratings. Commit the evaluation scaffolding and
stop with PENDING_HUMAN_REVIEW; never generate listener scores. Resume only after
the user supplies hash-current ratings. Keep the feature only if that evidence
passes the task contract; otherwise remove its production entry points rather
than weakening the deterministic baseline.

At closure, report all five task commits, validation, human evidence, and whether
the experiment was retained or removed. Do not claim autonomous production,
publishability, platform approval, or monetization.
```
