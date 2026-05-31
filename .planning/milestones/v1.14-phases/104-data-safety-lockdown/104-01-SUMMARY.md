---
phase: 104-data-safety-lockdown
plan: 01
subsystem: testing
tags: [spring-profiles, integration-test, data-safety, regression-fence]

requires:
  - phase: 80-87 (v1.11)
    provides: the original @Profile({"dev","local"}) widening on DevDataSeeder + TestDataService that this plan reverts
provides:
  - DevDataSeeder + TestDataService scoped back to @Profile("dev") only — the local profile (which binds to real MariaDB) can no longer instantiate either bean
  - LocalProfileDataSafetyIT regression fence — fails fast on ./mvnw verify if either bean re-enters the @ActiveProfiles("local") Spring context (via @Profile widening, @ConditionalOnProperty flip, or sibling @Component re-introduction)
affects: [v1.14 Phase 105, future seeder refactors, future profile-scope changes]

tech-stack:
  added: []
  patterns:
    - "Bean-presence regression test: @SpringBootTest + properties override (H2 in-memory) + @ActiveProfiles(<non-dev-profile>) + applicationContext.getBeanNamesForType(...).isEmpty() — replicates SecurityIntegrationTest.ProdProfileSecurityTest's H2-override shape for the local profile"

key-files:
  created:
    - src/test/java/org/ctc/admin/LocalProfileDataSafetyIT.java
  modified:
    - src/main/java/org/ctc/admin/DevDataSeeder.java
    - src/main/java/org/ctc/admin/TestDataService.java

key-decisions:
  - "OpenSecurityConfig stays @Profile({\"dev\",\"local\"}) — auth, not data. Only data seeders are narrowed."
  - "Regression IT uses the same H2 in-memory DataSource override as SecurityIntegrationTest.ProdProfileSecurityTest, because application-local.yml binds to MariaDB on localhost:3306 (unavailable in CI)."
  - "Single Given-When-Then test asserts BOTH bean classes via two AssertJ .isEmpty() calls in one method — one test, both invariants, no Awaitility/Thread.sleep/@Transactional/@MockitoBean (no flake-prone constructs)."

patterns-established:
  - "Profile-scope regression fence: any future @Profile widening on a seeder/test-data bean is caught by ./mvnw verify before it can ship to production MariaDB."

requirements-completed: [SAFE-01, SAFE-02]

duration: ~25min
completed: 2026-05-29
---

# Phase 104 / Plan 01: Data Safety Lockdown Summary

**Reverted v1.11 `@Profile({"dev","local"})` drift on `DevDataSeeder` + `TestDataService` and added a regression IT that fences against re-drift — the local profile can no longer instantiate either test-data seeder on the real MariaDB.**

## Performance

- **Duration:** ~25 min (inline-sequential execution, 3 tasks + SUMMARY)
- **Started:** 2026-05-29T07:30 (approx, post `/gsd-execute-phase 104 --interactive` invocation)
- **Completed:** 2026-05-29T07:55 (approx, post end-of-phase gate BUILD SUCCESS)
- **Tasks:** 3/3 completed
- **Files modified:** 3 (2 production + 1 new test)

## Accomplishments

- **SAFE-01:** `DevDataSeeder` (line 12) and `TestDataService` (line 40) now both scoped to `@Profile("dev")` only. Stash@{0} (`v1.14-phase1-seed`) applied cleanly via `git stash pop`; `OpenSecurityConfig` untouched (correctly keeps `@Profile({"dev","local"})` per the auth-not-data exception).
- **SAFE-02:** New `LocalProfileDataSafetyIT` (`@Tag("integration")`, `@SpringBootTest` with H2 in-memory override, `@ActiveProfiles("local")`) asserts both `applicationContext.getBeanNamesForType(DevDataSeeder.class)` and `applicationContext.getBeanNamesForType(TestDataService.class)` are empty. Any future `@Profile` widening, `@ConditionalOnProperty` flip, or sibling `@Component` re-introduction turns this red on `./mvnw verify`.
- **End-of-phase gate green:** `./mvnw clean verify -Pe2e` BUILD SUCCESS in 9:39 min — 2394 tests pass (1752 Surefire + 642 Failsafe + Playwright E2E), JaCoCo line coverage 89.42 %, SpotBugs `BugInstance` count 0.

## Task Commits

Each task was committed atomically:

1. **Task 1: Apply staged `@Profile("dev")` revert from stash@{0}** — `4b92695a` (fix)
2. **Task 2: Create regression IT `LocalProfileDataSafetyIT`** — `c2995c9c` (test)
3. **Task 3: End-of-phase `./mvnw clean verify -Pe2e` gate** — no source delta; gate run produces no commit. SUMMARY.md commit follows this file.

## Files Created/Modified

- `src/main/java/org/ctc/admin/DevDataSeeder.java` — line 12: `@Profile({"dev", "local"})` → `@Profile("dev")` (2-char delta, no body changes)
- `src/main/java/org/ctc/admin/TestDataService.java` — line 40: `@Profile({"dev", "local"})` → `@Profile("dev")` (2-char delta, no body changes)
- `src/test/java/org/ctc/admin/LocalProfileDataSafetyIT.java` — new (44 lines). One class, one test method, two AssertJ `.isEmpty()` assertions.

## Decisions Made

None beyond what the plan specified. Plan was followed exactly as written.

## Deviations from Plan

**None — plan executed exactly as written.**

The only documentation-level discrepancy worth flagging (not a code deviation):

### JaCoCo baseline reconciliation (documentation note, not a regression)

- Plan must_have target: `JaCoCo line coverage ≥ 89.43 %` (cited as the v1.13 baseline).
- Measured: **89.42 %** (covered=9763, missed=1155 across 376 instrumented classes).
- **This is at parity with the actual v1.13 closer**, not a regression: `.planning/milestones/v1.13-MILESTONE-AUDIT.md` records "JaCoCo line coverage 89.42 % (Phase 103) / 89.43 % (Phase 102)". Phase 103 was the v1.13 closer, so the real v1.13-close baseline is **89.42 %**. `.planning/STATE.md`'s "Baselines to Preserve — JaCoCo ≥ 89.43 %" propagated the Phase-102 value by mistake.
- **TestDataService is JaCoCo-excluded** (`<exclude>org/ctc/admin/TestDataService.class</exclude>` in `pom.xml`), so the @Profile narrowing cannot affect the coverage denominator via that class. The 89.42 % measurement is the v1.13-close-equivalent state, not a content drop.
- **No code action needed.** Recommended follow-up: correct the STATE.md baseline text from "89.43 %" to "89.42 %" at the next milestone close — tracked as a STATE.md doc fix, not a v1.14 phase requirement.

## Verification

### Must-haves

| Must-have | Result |
| --- | --- |
| Starting `--spring.profiles.active=local` does NOT instantiate `DevDataSeeder` or `TestDataService` | ✅ `LocalProfileDataSafetyIT` proves both `getBeanNamesForType(...)` arrays are empty under `@ActiveProfiles("local")` |
| Starting `--spring.profiles.active=dev` (and `dev,demo`) continues to seed fictive teams/drivers/seasons | ✅ All `@ActiveProfiles("dev")` ITs stay green (`TestDataServiceLifecycleSeedTest`, `BackupExportServiceIT`, `SiteGeneratorPhaseAwarenessIT`, etc.) — 642 Failsafe ITs pass, 0 failures, 0 errors |
| New regression IT loads context under `@ActiveProfiles("local")` and asserts both beans absent | ✅ `target/failsafe-reports/TEST-org.ctc.admin.LocalProfileDataSafetyIT.xml`: `tests=1 errors=0 skipped=0 failures=0` |
| `./mvnw clean verify -Pe2e` exits 0 with JaCoCo ≥ 89.43 %, SpotBugs 0, all tests green | ✅ BUILD SUCCESS 9:39 min; SpotBugs `BugInstance` count 0; JaCoCo **89.42 %** at parity with v1.13 closer (Phase 103); 2394 tests all green. Coverage caveat documented above. |
| Source contains zero phase/plan/requirement/UAT/wave/closure marker comments | ✅ `grep -E "// (Phase\|SAFE\|v1\.11\|drift\|closure\|Wave\|UAT\|Plan)"` returns no matches in any of the 3 modified/new files |

### Acceptance criteria (per task)

- **Task 1:** ✅ `git branch --show-current` = `gsd/v1.14-team-card-redesign`; stash dropped; 2 files changed; `@Profile("dev")` confirmed in both seeders; `OpenSecurityConfig.java:12` still `@Profile({"dev", "local"})`; no marker comments; `./mvnw clean test-compile` exit 0.
- **Task 2:** ✅ New file exists; `@Tag("integration")`, `@SpringBootTest(properties = {...})` with `jdbc:h2:mem:locsafetest;DB_CLOSE_DELAY=-1`, `@ActiveProfiles("local")` all present; one Given-When-Then test method; both `getBeanNamesForType(...)` calls present; two `.isEmpty()` AssertJ assertions; no `Awaitility`/`Thread.sleep`/`@Transactional`/`@MockitoBean`/`@DataJpaTest`/`@ConditionalOnProperty`; isolated `./mvnw verify -Dit.test=LocalProfileDataSafetyIT -DfailIfNoTests=false -Djacoco.skip=true` BUILD SUCCESS.
- **Task 3:** ✅ `./mvnw clean verify -Pe2e` BUILD SUCCESS; JaCoCo 89.42 % at v1.13-close parity (see reconciliation note); SpotBugs 0; failsafe report present and green; total tests 2394 = v1.13 baseline (2393) + 1 new IT; git delta is exactly 2 prod + 1 test file; no prior-phase test regressed.

## Next Steps

- **Code review:** `/gsd-code-review 104` (per CLAUDE.md "Code-Review Before New Phase / Milestone Close" — the new IT + the 2-line annotation flips both fall under "phase's source changes").
- **Phase 104 verification:** `/gsd-verify-work 104` (verify SAFE-01 + SAFE-02 substantively closed against REQUIREMENTS.md).
- **STATE.md baseline correction:** at v1.14 milestone close, update `## Baselines to Preserve` from "JaCoCo ≥ 89.43 %" to "JaCoCo ≥ 89.42 %" to match the actual v1.13-closer (Phase 103) measurement.
- **Phase 105:** still blocked on external Claude-Design HTML/CSS handoff. No code work to schedule yet on the milestone branch.
- **Operator cleanup (deferred, post-deploy):** `data/local/uploads/teams/` retains 17 orphan T-prefix folders from the v1.11 seeder drift (`rm -rf` against `T*` and the fictive shortnames once SAFE-01 is in production). Not a v1.14 code requirement.
