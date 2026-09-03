# T-NN · <Title>

| Field | Value |
|---|---|
| **Phase** | <phase number and name> |
| **Depends on** | <task IDs, or "none"> |
| **Blocks** | <task IDs, or "none"> |
| **Estimate** | <hours> |

## Goal
One sentence. Why this task exists and what capability it unlocks.

## Scope — files to create / modify
Exact paths. Exact class names. If a file is only *modified*, say which part.

## Requirements
Numbered, individually testable statements. Where `api-contract.md` or the specification
pins a name, signature, column, or HTTP status, quote it verbatim — do not paraphrase.

## Out of scope
What this task must NOT touch. Prevents two tasks fighting over the same file.

## Acceptance criteria
Observable and checkable by someone who did not write the code: HTTP status codes,
SQL state, log output, on-screen behaviour. No "works correctly".

## Definition of Done
- [ ] `mvn clean package` succeeds with zero warnings introduced by this task.
- [ ] All acceptance criteria demonstrated (command or screenshot recorded in the PR).
- [ ] No `TODO`, `FIXME`, commented-out code, or `printStackTrace()` left behind.
- [ ] Public types and non-obvious logic carry Javadoc.
- [ ] Reviewed against `api-contract.md`; any deviation logged in `docs/decisions/ADR-002-contract-deviations.md`.
