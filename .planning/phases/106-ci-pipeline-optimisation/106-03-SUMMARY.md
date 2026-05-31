---
phase: 106-ci-pipeline-optimisation
plan: 03
status: complete
requirements: [CI-06]
requirements-completed: [CI-06]
files_modified: [pom.xml, docs/ci/FLAKY-TEST-POLICY.md]
commit: 34e4e35e
---

# 106-03 Summary — no-rerun build guard + flaky-test policy

## What was built

- **Task 1 (pom.xml):** Added a third `exec-maven-plugin` execution
  `<id>no-rerun-guard</id>` at `<phase>validate</phase>`, mirroring the PLAT-07 /
  CLEAN-01 idiom. The CDATA bash body greps `pom.xml` for the **opening-tag form**
  `<(rerunFailingTestsCount|retryCount)` and, on any match, prints
  `[CI-06 build-guard] FAIL` (referencing CLAUDE.md "No Flaky Dismissal" +
  `docs/ci/FLAKY-TEST-POLICY.md`) and `exit 1`; otherwise prints
  `[CI-06 build-guard] OK` and `exit 0`. The opening-tag form (identifiers preceded by
  `(` / `|`, never by `<`) prevents the guard's own pattern literal from self-tripping
  — confirmed by `grep -nE '<(rerunFailingTestsCount|retryCount)' pom.xml` returning
  zero matches on the committed pom.

- **Task 2:** Proved the guard RED→GREEN and documented the quarantine policy.

## Verification

- **GREEN (clean pom):** `./mvnw -q validate -Dspring.profiles.active=dev` →
  `[CI-06 build-guard] OK`, exit 0.
- **RED (proof):** transiently inserting `<rerunFailingTestsCount>2</rerunFailingTestsCount>`
  into the Surefire `<configuration>` → `[CI-06 build-guard] FAIL`, mvnw exit **1**.
  Transient line then removed; committed pom contains no real rerun/retry element.
- **Fork config preserved:** `git diff pom.xml` shows no change to Surefire/Failsafe
  `forkCount=2` / `reuseForks=true` (only the new execution was added).
- `docs/ci/FLAKY-TEST-POLICY.md` exists, 49 lines (≥15), states rerun/retry are
  permanently forbidden + enforced by the pom guard, references CLAUDE.md "No Flaky
  Dismissal" and the `@Tag("flaky")` in-milestone-fix quarantine rule.

## Deviations

None.
