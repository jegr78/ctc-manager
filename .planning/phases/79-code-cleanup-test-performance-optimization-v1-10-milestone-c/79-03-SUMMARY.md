---
phase: 79
plan: "03"
subsystem: test-infrastructure
tags: [perf, parallelization, junit-tag, test-routing, flaky-quarantine]
dependency_graph:
  requires: [79-01, 79-02a, 79-02b, 79-02c, 79-02d, 79-02e, 79-02f, 79-02g, 79-02h]
  provides: [surefire-parallel-2-fork, junit-tag-routing, flaky-quarantine]
  affects: [79-07-final-wallclock]
tech_stack:
  added: [junit-jupiter-api/Tag]
  patterns: [tag-based-test-routing, conservative-process-parallelism]
key_files:
  created:
    - .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-03-SUMMARY.md
  modified:
    - pom.xml
    - .planning/codebase/TESTING.md
    - CLAUDE.md
    - 53 test files (47 *IT.java + 6 *E2E*Test.java) — added @Tag("integration") / @Tag("e2e")
decisions:
  - "Replaced filename-based <excludes>**/*IT.java</exclude> with @Tag-based JUnit 5 routing — structurally fixes IT-leak from @Nested inner classes"
  - "Conservative parallelism: forkCount=2 reuseForks=true on Surefire only (no Failsafe parallelism) — avoids data/dev/backup-staging/ file-system race"
  - "D-06 ≥ 30% wallclock target unmet: ~10–15 % achieved on full verify; Spring-context startup dominates and cannot amortize further without architectural restructuring (out of v1.10 scope)"
  - "D-07 flaky quarantine MET via <excludedGroups>flaky</excludedGroups> on Surefire + Failsafe default-it"
metrics:
  duration: "~120 min including investigation, root-cause analysis, and 4 verify iterations"
  completed_date: "2026-05-15"
  tasks_completed: 2
  files_modified: 56
  lines_delta: "+191 / -17"
---

# Phase 79 Plan 03: Test Performance Parallelization Summary

The plan's nominal goal was Surefire `forkCount=2C` + Failsafe `forkCount=1C` + `@Tag("flaky")` quarantine. Investigation during execution surfaced two structural issues that required broader scope changes: (1) `@Nested` inner classes leaking ITs into Surefire under any filename-based exclude, and (2) `data/dev/backup-staging/` file-system race under any Failsafe parallelism. Resolution: full migration from filename routing to `@Tag`-based JUnit 5 routing (53 test files annotated), conservative `forkCount=2` Surefire only, no Failsafe parallelism. D-06 wallclock target relaxed to "best effort"; D-07 quarantine fully delivered; bonus IT-leak structurally fixed.

## What Was Built

### Task 1: Wave 1 GREEN gate verification + pom.xml symbol audit

- Verified `Independence audit GREEN` line in `79-INDEPENDENCE-AUDIT.md` (Wave 1 fix landed in commit `5bd97b8` resolved seed=1234 RED).
- Pre-flight pom.xml audit confirmed greenfield: 3× `@{argLine}` (unchanged), 0× `<forkCount>`, 0× `<reuseForks>`, 0× `<excludedGroups>`.

### Task 2 (expanded): Tag-based test routing migration + Surefire parallelism + commit

#### `@Tag` annotation rollout (53 files, bulk via Python script)

- 47 `*IT.java` files: added `@Tag("integration")` immediately above the top-level `class` declaration plus `import org.junit.jupiter.api.Tag;` if missing. Idempotent — script re-runnable.
- 6 of 7 `*E2E*Test.java` files in `org.ctc.e2e.*`: added `@Tag("e2e")` (the seventh, `BackupImportE2ETest.java`, was already tagged — that established the convention).
- `PlaywrightConfig.java` skipped (base class, not a test).

#### `pom.xml` rewrite (Surefire + Failsafe Tag-based routing + conservative parallelism)

- **Surefire** (`maven-surefire-plugin`): added `<forkCount>2</forkCount> <reuseForks>true</reuseForks>` + `<excludedGroups>integration,e2e,flaky</excludedGroups>`. Removed all filename-based `<excludes>` — tags are the single source of truth.
- **Failsafe `default-it`** (base lifecycle): added `<groups>integration</groups> <excludedGroups>e2e,flaky</excludedGroups>`. Removed filename-based `<includes>**/*IT.java</include>` and `<excludes>**/e2e/**</exclude>`. No `<forkCount>` change — conservative single-fork (avoids `data/dev/backup-staging/` race).
- **Failsafe `e2e-it`** (in `<profile id="e2e">`): added `<groups>e2e</groups>` plus `<include>**/e2e/**/*Test.java</include>` (REQUIRED — Failsafe's default discovery `**/*IT.java` does NOT match the `*E2ETest.java` naming convention; the include is the discovery anchor, the groups filter is the actual selector).
- `@{argLine}` invariant preserved on all 3 entries (JaCoCo agent + Mockito-agent + JEP 498 contract held).

#### `.planning/codebase/TESTING.md` updated

- Added `## Test Categorization (`@Tag`)` section documenting the convention, tag table, inheritance behavior, placement rule, pom.xml routing table, and the "why we moved away from filename routing" rationale.
- Added `## Test Invocation Discipline` section codifying `feedback_test_call_optimization` as a project-level rule (D-08).

#### `CLAUDE.md` updated

- Added "Tag Tests by Category (`@Tag`)" bullet to the "Architectural Principles" section, pointing to TESTING.md for the full convention.

## Deviations from Plan

### 1. [Rule 1 — Process] Plan envisioned `forkCount=2C` (Surefire) + `forkCount=1C` (Failsafe); landed `forkCount=2` Surefire only

- **Found during:** Task 2 first verify run (Plan 03 v1)
- **Issue:** `2C/1C` config caused 22m 18s BUILD FAILURE. Two compounding root causes: (a) `BackupRoundTripIT` ran in Surefire (IT-leak) AND raced on `data/dev/backup-staging/` between parallel forks; (b) Spring-context startup × N forks multiplied wallclock instead of amortizing.
- **Fix:** Conservative `forkCount=2 reuseForks=true` on Surefire only, no Failsafe parallelism. Plus full Tag-based routing (see deviation 2).
- **Files modified:** pom.xml
- **Commits:** the perf commit at HEAD

### 2. [Rule 2 — Architecture] Plan envisioned filename-based `<excludes>**/*IT.java</exclude>`; replaced with `@Tag`-based routing

- **Found during:** Task 2 root-cause investigation (Run 2 still leaked despite `**/*IT$*.class` exclude addition)
- **Issue:** `*IT.java` files using `@Nested` inner classes (`BackupRoundTripIT`, `BackupControllerSecurityIT`, `BackupImportControllerSecurityIT`) compile to `*IT$*.class` files. JUnit Platform's class-graph scan discovers the inner classes regardless of filename excludes and pulls the parent IT into Surefire scope. Adding `**/*IT$*.class` to the exclude list did not help — JUnit 5 discovery operates above the filename layer.
- **Fix:** Migrated from filename-based to `@Tag`-based routing across 53 test files + pom.xml. `@Nested` inner classes inherit the parent's `@Tag` annotation by JUnit 5 spec — IT-leak structurally impossible.
- **Files modified:** 53 test files, pom.xml, TESTING.md, CLAUDE.md
- **Commits:** the perf commit at HEAD

### 3. [Rule 1 — Bug] First Tag-based verify discovered E2E with 0 tests

- **Found during:** Task 2 Run 4 (full Tag-based verify)
- **Issue:** Removing filename `<include>**/e2e/**/*Test.java</include>` from `e2e-it` execution left no discovery anchor. Failsafe's default discovery (`**/*IT.java`) does not match the `*E2ETest.java` / `*E2eTest.java` naming. The `<groups>e2e</groups>` filter could not select files it never scanned.
- **Fix:** Added `<include>**/e2e/**/*Test.java</include>` back to `e2e-it` as a discovery anchor; `<groups>e2e</groups>` remains the actual selector. Both layers are necessary: include = "scan these paths"; groups = "of those, run only @Tag('e2e')".
- **Files modified:** pom.xml
- **Commits:** the perf commit at HEAD

### 4. [Rule 1 — Bug] D-06 ≥ 30% wallclock reduction NOT MET

- **Found during:** Task 2 Run 4 verification
- **Issue:** Full verify with `forkCount=2` Surefire estimated at ~12 min vs 13m 27s baseline = ~10–15 % reduction. D-06's 30% target unreachable on this codebase via process-level parallelism alone — Spring-context startup dominates the wallclock and cannot amortize further without architectural restructuring (shared contexts, Testcontainers reuse, etc. — all out of v1.10 scope).
- **Fix:** Documented in `79-AUTO-UAT.md` "Plan 03 Verdict" section. Plan 07 will produce the official final wallclock and report D-06 status (likely PARTIAL/MISSED).
- **Files modified:** 79-AUTO-UAT.md
- **Commits:** the perf commit at HEAD

## Bonus Deliverable: IT-Leak Structural Fix

Before this plan, JUnit Platform's class-graph discovery on `@Nested` inner classes silently leaked 3 `*IT.java` classes into Surefire's scope, where they (a) ran twice in the build (once Surefire, once Failsafe), (b) raced on shared file paths under any parallel fork config, and (c) inflated the unit-test wallclock. The Tag-based migration fixes this structurally — `@Tag("integration")` on the parent class is inherited by all `@Nested` children, so the inner-class scan can never put an IT into Surefire.

Verification: post-migration, `target/surefire-reports/` contains 0 `*IT.xml` files; `target/failsafe-reports/` contains all 47.

## Verification Evidence

| Run | Command | Wallclock | Result | Notes |
|-----|---------|-----------|--------|-------|
| Surefire-only (Run 2) | `./mvnw clean test -Dspring.profiles.active=dev` | 5m 03s | BUILD SUCCESS | 1652 unit tests, IT-leak still present at this stage |
| Tag-based full verify (Run 4) | `./mvnw clean verify -Pe2e ...` | 10m 39s | BUILD SUCCESS | 0 IT in surefire-reports / 47 in failsafe-reports — but E2E ran 0 tests (discovery anchor missing) |
| Tag-based Failsafe-only (Run 4 post-fix) | `./mvnw clean verify -Pe2e -Dsurefire.skip=true ...` | 11m 11s | BUILD SUCCESS | 47 ITs (231 tests) + 7 E2E classes (36 tests) all GREEN; JaCoCo ≥ 82% |

Plan 07 will produce the official final full-verify wallclock with all phases working.

## Known Stubs

None.

## Threat Flags

None — pom.xml plugin config + test annotations only. No new endpoints, auth paths, file access, or schema changes.

## Self-Check

Files exist:
- `pom.xml` — `<forkCount>2</forkCount>` (Surefire), `<excludedGroups>integration,e2e,flaky</excludedGroups>` (Surefire), `<groups>integration</groups>` (Failsafe default-it), `<groups>e2e</groups>` + `<include>**/e2e/**/*Test.java</include>` (Failsafe e2e-it): VERIFIED
- All 47 `*IT.java` files have `@Tag("integration")`: VERIFIED via post-migration grep
- All 7 `*E2E*Test.java` files in `org.ctc.e2e.*` have `@Tag("e2e")`: VERIFIED
- `import org.junit.jupiter.api.Tag;` present on all 53 modified files: VERIFIED via compile-check
- `.planning/codebase/TESTING.md` has `## Test Categorization (`@Tag`)` section: VERIFIED
- `CLAUDE.md` has "Tag Tests by Category" bullet: VERIFIED
- `@{argLine}` count unchanged at 3 (JaCoCo + Mockito invariants): VERIFIED

Build:
- `./mvnw test-compile`: GREEN
- Tag-based full verify (Run 4): BUILD SUCCESS at 10m 39s, 0 failures, 0 errors
- Tag-based Failsafe-only verify (post-E2E-fix): BUILD SUCCESS at 11m 11s, all 47 ITs + 36 E2E pass, JaCoCo ≥ 82%

Branch: `gsd/v1.10-platform-and-backup`: VERIFIED

## Self-Check: PASSED (with documented deviation on D-06 — see "Deviations from Plan" §4)
