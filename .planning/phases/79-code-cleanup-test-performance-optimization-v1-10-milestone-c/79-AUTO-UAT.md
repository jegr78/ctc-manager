# Phase 79 — Auto UAT

## Wallclock Baseline

| Measurement | Git SHA | Invocation | Duration | Date |
|-------------|---------|-----------|----------|------|
| Baseline (before D-05) | `28d0469` | `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true` | `13m 27s` | 2026-05-15 |
| Final (after D-05) | `1636266` | _same command_ | `11m 11s` (Maven) / `11m 13s` (wallclock) | 2026-05-15 |

**Target: Final ≤ Baseline × 0.7 (D-06)**
(i.e., Final must be ≤ 9m 23s to achieve the ≥ 30% wallclock reduction required by D-06)

### Baseline Run Details

- **Command:** `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`
- **Build result:** BUILD SUCCESS
- **Total time (Maven):** 13:26 min
- **Wall time (time builtin):** `13m 27.38s` real, `989.00s` user, `148.92s` system, `140%` CPU
- **Tests:** 1227 Surefire unit + 112 Failsafe IT + 36 E2E Playwright
- **JaCoCo:** 289 classes analyzed, all coverage checks met (≥ 82% line)
- **Finished at:** 2026-05-15T17:45:52+02:00

### Final Run Details (Plan 07)

- **Command:** `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`
- **Git SHA at run start:** `1636266` (post-Wave-4 tracking commit; subsequent backlog commit `67115db` added .planning/backlog files only — no source impact)
- **Build result:** BUILD SUCCESS
- **Maven Total time:** 11:11 min
- **Bash wallclock (start→end):** 11m 13s (= Maven `Total time` + harness startup)
- **Tests:** 1652 Surefire unit + 231 Failsafe IT + 36 E2E Playwright (post-Wave-3 Tag-based routing)
- **JaCoCo:** 289 classes analyzed, line coverage 0.8780 (87.80%), all coverage checks met
- **Finished at:** 2026-05-15T23:28:58+02:00

## Reduction Verdict

| Metric | Value |
|---|---|
| Baseline duration | `13m 27s` (807s) at git SHA `28d0469` |
| Final duration    | `11m 11s` (671s, Maven) / `11m 13s` (wallclock) at git SHA `1636266` |
| Absolute reduction | `2m 16s` (136s) |
| Percentage reduction | **16.85 %** |
| D-06 threshold | ≥ 30 % |
| **Verdict** | **DOES NOT MEET ≥ 30 % D-06 threshold — partial reduction documented; gap accepted for v1.10** |

### Why D-06 was not met

Per Plan 03 SUMMARY (commit `75ffef5`), the conservative `forkCount=2 reuseForks=true` Surefire-only parallelism was the maximum-safe configuration that this codebase supports. The two harder-to-amortize cost centres are:

1. **Spring-context startup dominates the wallclock.** Each fork pays ~3–5 s × N classes that boot a Spring context. Forking parallelizes class boundaries but multiplies the per-fork startup tax.
2. **`data/dev/backup-staging/` is a singleton path.** Failsafe `default-it` parallelism would race on this path under `forkCount=1C+`. Plan 03 RESEARCH §3 recommended `1C` for Failsafe but the staging-dir race made even `1C` unsafe — so Failsafe stays single-fork.

Reaching ≥ 30 % requires architectural restructuring (shared Spring contexts via `@SpringBootTest` test-class-grouping; per-fork staging-dir isolation; Testcontainers reuse). That is significantly more work than Plan 03 envisioned and is out of v1.10 scope.

### Wave 8 advancement

Per Plan 07's `## Reduction Verdict` template ("If DOES NOT MEET: orchestrator decides whether to tune `forkCount=2.5C` Surefire and re-measure, OR accept the partial reduction and continue to Wave 8 with documented gap"), Phase 79 advances to Wave 8 (`/gsd-audit-milestone v1.10`) with the documented D-06 gap.

The orchestrator opted to accept the partial reduction because:
- Spring-context cost is structural; `2.5C` would multiply that cost further (likely making wallclock WORSE, as `2C` already showed in Plan 03 Run 1).
- The architectural restructuring needed to reach ≥ 30 % is not within v1.10's scope or risk budget.
- D-07 (`@Tag("flaky")` quarantine), D-18 (JaCoCo ≥ 82 %), and D-19 (BUILD SUCCESS) are all MET — Phase 79's other deliverables ship cleanly.
- The IT-leak structural fix delivered as a bonus during Plan 03 is a genuine improvement to the test suite's correctness.

A v1.11 backlog item should track the architectural test-restructuring work needed to reach D-06's intent.

## Intermediate Measurements (Plan 03 implementation)

### Run 1 — Plan 03 v1 (`forkCount=2C` Surefire + `forkCount=1C` Failsafe, filename-based excludes)

- **Command:** `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`
- **Result:** **BUILD FAILURE** at 22m 18s (worse than baseline)
- **Symptom:** `BackupRoundTripIT` (an `*IT.java`) ran in Surefire instead of Failsafe and raced on `data/dev/backup-staging/` between parallel forks. Wallclock multiplied by Spring-context startup × 2C forks instead of amortizing.
- **Diagnosis:** `<exclude>**/*IT.java</exclude>` filtered the top-level `BackupRoundTripIT.class` but NOT the `@Nested`-compiled inner classes (`BackupRoundTripIT$H2DevRoundTripTests.class`, `…$MariaDbRoundTripTests.class`). JUnit Platform's class-graph scan discovered the inner classes and dragged the parent IT into Surefire — same root cause for `BackupControllerSecurityIT` and `BackupImportControllerSecurityIT` (the only other 2 of 47 `*IT.java` files with `@Nested`). Reverted before commit.

### Run 2 — Plan 03 v2 (`forkCount=2 reuseForks=true` Surefire only, filename excludes + extra `**/*IT$*.class`)

- **Command:** `./mvnw clean test --no-transfer-progress -Dspring.profiles.active=dev`
- **Result:** **BUILD SUCCESS** at 5m 03s (Surefire only, no Failsafe / e2e)
- **Symptom:** IT-leak still present — 3 IT files (`BackupRoundTripIT`, `BackupControllerSecurityIT`, `BackupImportControllerSecurityIT`) still ran under Surefire. `**/*IT$*.class` exclude alone did not stop JUnit Platform's class-graph discovery from reaching the inner classes.

### Run 3 — Plan 03 v3 (full `@Tag`-based migration, see Run 4)

Per user direction, replaced the brittle filename-based filtering with `@Tag`-based JUnit 5 routing across all 47 `*IT.java` (`@Tag("integration")`) and 7 `*E2ETest.java` (`@Tag("e2e")`). pom.xml: `excludedGroups=integration,e2e,flaky` for Surefire; `groups=integration` + `excludedGroups=e2e,flaky` for Failsafe `default-it`; `groups=e2e` + broad `<include>**/e2e/**/*Test.java</include>` (compatibility with non-IT naming) for Failsafe `e2e-it`.

### Run 4 — Plan 03 final (Tag-based, intermediate measurement)

- **Surefire-only Run** (`./mvnw clean test`): not separately measured (5m 03s estimate from Run 2 still applies — the Tag filter is equivalent in practice for the unit-test set)
- **Full verify Run** (`./mvnw clean verify -Pe2e`): **BUILD SUCCESS** at 10m 39s (during which E2E execution returned 0 — see Run 5 fix)
- **Failsafe-only Run** (`./mvnw verify -Pe2e -Dsurefire.skip=true`): **BUILD SUCCESS** at 11m 11s
  - 47 ITs in failsafe `default-it` (231 tests)
  - 7 E2E classes in failsafe `e2e-it` (36 tests)
  - JaCoCo ≥ 82%
- **Full verify (estimated)** with all phases passing E2E correctly: ~12 minutes (vs 13m 27s baseline = ~10–15 % reduction)

### Run 5 — E2E discovery fix (post Run 4 forensics)

- **Issue:** Run 4's full verify showed `e2e-it` "Tests run: 0" — Failsafe's default discovery pattern is `**/*IT.java` which does NOT match the `*E2ETest.java` / `*E2eTest.java` naming. Removing the filename `<include>` left no discovery anchor.
- **Fix:** Added `<include>**/e2e/**/*Test.java</include>` back to the `e2e-it` execution as a discovery anchor; `<groups>e2e</groups>` is the actual selector. Both work together: include = "scan these files", groups = "of those, run only @Tag('e2e')".
- **Verification:** Run 4's Failsafe-only re-run (post-fix) shows all 36 E2E tests discovered + executed (above).

### Plan 03 Verdict

**D-06 ≥ 30% wallclock reduction:** likely **NOT MET on full verify**. Process-level parallelism on this codebase delivers ~10–15 % reduction; Spring-context startup cost dominates and cannot amortize further without architectural restructuring (out of v1.10 scope).

**D-07 `@Tag("flaky")` quarantine mechanism:** **MET.** `<excludedGroups>` on Surefire + Failsafe `default-it` excludes any test tagged `flaky`. Currently 0 quarantined tests; max-5 cap enforced by CD-05 monthly review.

**Bonus: IT-leak fix.** All 47 `*IT.java` are now `@Tag("integration")` and route only to Failsafe; all 7 E2E tests are `@Tag("e2e")` and route only to `e2e-it`. The `@Nested` inner-class discovery problem is structurally impossible.

Plan 07 will produce the official final wallclock measurement against the new configuration.
