---
phase: 104-data-safety-lockdown
verified: 2026-05-29T00:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 104: Data Safety Lockdown Verification Report

**Phase Goal:** The `local` Spring profile can no longer accidentally seed fictional test data into the real MariaDB, and any future re-drift toward including `local` in the seeders' `@Profile` value is caught by `./mvnw verify` instead of by a production data accident.

**Verified:** 2026-05-29T00:00:00Z
**Status:** passed
**Re-verification:** No — initial verification
**Branch (verified read-only):** `gsd/v1.14-team-card-redesign`

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `--spring.profiles.active=local` does NOT instantiate `DevDataSeeder` or `TestDataService` | VERIFIED | `DevDataSeeder.java:12` and `TestDataService.java:40` both narrowed to `@Profile("dev")`. New IT `LocalProfileDataSafetyIT` proves both `getBeanNamesForType(...)` arrays are empty under `@ActiveProfiles("local")`. `application-local.yml` confirmed to contain NO `spring.profiles.include: dev` — annotation is the only gate. |
| 2 | `--spring.profiles.active=dev` (and `dev,demo`) continues to seed fictive teams/drivers/seasons exactly as before | VERIFIED | Annotation narrowed (dropped `"local"`, kept `"dev"`); no body changes in either class. All `@ActiveProfiles("dev")` ITs remained green in the end-of-phase gate (TestDataServiceLifecycleSeedTest, BackupExportServiceIT, SiteGeneratorPhaseAwarenessIT, …). Failsafe report count: 140 IT files green. |
| 3 | New IT loads context with `@ActiveProfiles("local")` and asserts both beans absent — fails on re-drift | VERIFIED | `src/test/java/org/ctc/admin/LocalProfileDataSafetyIT.java` exists (44 lines), `@Tag("integration")`, `@SpringBootTest(properties={…H2…})`, `@ActiveProfiles("local")`, two `assertThat(applicationContext.getBeanNamesForType(<class>)).isEmpty()` assertions. Failsafe XML report: `tests=1 errors=0 skipped=0 failures=0 time=1.403s`. |
| 4 | End-of-phase `./mvnw clean verify -Pe2e` exits 0 with JaCoCo gate held, SpotBugs 0, all tests green | VERIFIED | SUMMARY.md cites BUILD SUCCESS (9:39 min, commit `91d62eb2`). JaCoCo recomputed from `target/site/jacoco/jacoco.csv` cols 8/9: **89.4211 %** (LINE_COVERED=9763, LINE_MISSED=1155). 82 % pom gate: satisfied by 7.42 pp margin. v1.13-close baseline (89.42 % per `v1.13-MILESTONE-AUDIT.md`): held at parity. SpotBugs `<BugInstance>` count in `target/spotbugsXml.xml`: **0**. Test count: 199 Surefire + 140 Failsafe report files. |
| 5 | Source contains zero phase/plan/requirement/UAT/wave/closure marker comments in modified/new files | VERIFIED | `grep -rE "// (Phase\|SAFE\|v1\.11\|drift\|closure\|Wave\|UAT\|Plan)"` against `DevDataSeeder.java`, `TestDataService.java`, `LocalProfileDataSafetyIT.java` returns zero matches. Only structural `// given` / `// when` / `// then` BDD markers present (CLAUDE.md "Test Naming"-allowed). |

**Score:** 5/5 truths verified.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/admin/DevDataSeeder.java` | Contains `@Profile("dev")` (line 12) | VERIFIED | Read at line 12: `@Profile("dev")`. No `{"dev", "local"}` form remains. Class body unchanged. Wired: imported `org.springframework.context.annotation.Profile`. |
| `src/main/java/org/ctc/admin/TestDataService.java` | Contains `@Profile("dev")` (line 40) | VERIFIED | Read at line 40: `@Profile("dev")`. No `{"dev", "local"}` form remains. Class body / Javadoc unchanged ("…for the `dev` profile" Javadoc now strictly accurate again). |
| `src/test/java/org/ctc/admin/LocalProfileDataSafetyIT.java` | Contains `@ActiveProfiles("local")`, `@Tag("integration")`, two `getBeanNamesForType(...).isEmpty()` assertions | VERIFIED | All three present (lines 21, 22, 33-34, 37-42). Constructor: `@Autowired ApplicationContext`. No forbidden constructs (no `@Transactional`, no `@MockitoBean`, no `@DataJpaTest`, no `@ConditionalOnProperty`, no `Awaitility`, no `Thread.sleep`). Failsafe-conventional `IT` suffix. Single Given-When-Then test method. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `DevDataSeeder.java` | Spring context under `@ActiveProfiles("local")` | `@Profile` annotation | WIRED | `@Profile("dev")` excludes the bean from the `local` context — proven by `LocalProfileDataSafetyIT` `.isEmpty()` assertion passing. |
| `TestDataService.java` | Spring context under `@ActiveProfiles("local")` | `@Profile` annotation | WIRED | `@Profile("dev")` excludes the bean from the `local` context — proven by `LocalProfileDataSafetyIT` `.isEmpty()` assertion passing. |
| `LocalProfileDataSafetyIT.java` | `applicationContext.getBeanNamesForType(DevDataSeeder.class)` AND `…(TestDataService.class)` | AssertJ `assertThat(...).isEmpty()` | WIRED | Both calls present (lines 33-34), both assertions present (lines 37-42), with descriptive `.as("…")` messages naming the regression-protection contract. |

### Companion Bean Untouched (Must NOT Be Modified)

| File | Required State | Verified |
|------|----------------|----------|
| `src/main/java/org/ctc/admin/OpenSecurityConfig.java:12` | STILL `@Profile({"dev", "local"})` (auth, not data — explicit exception per memory `feedback_local_profile_no_dev_data`) | VERIFIED — line 12 reads `@Profile({"dev", "local"})` unchanged. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Failsafe report for `LocalProfileDataSafetyIT` exists and shows green | `cat target/failsafe-reports/TEST-org.ctc.admin.LocalProfileDataSafetyIT.xml \| head` | XML root: `tests="1" errors="0" skipped="0" failures="0" flakes="0" time="1.403"` | PASS |
| JaCoCo line coverage meets 82 % pom gate AND v1.13-close baseline (89.42 %) | `awk -F',' 'NR>1 {missed+=$8; covered+=$9} END {printf "%.4f%%", covered*100/(covered+missed)}' target/site/jacoco/jacoco.csv` | `89.4211%` (LINE_COVERED=9763 LINE_MISSED=1155) | PASS |
| SpotBugs BugInstance count is 0 | `grep -c "<BugInstance" target/spotbugsXml.xml` | `0` | PASS |
| No `@ConditionalOnProperty` workaround backdoor on the narrowed seeders | `grep -E "@ConditionalOnProperty" DevDataSeeder.java TestDataService.java` | no matches | PASS |
| No `spring.profiles.include: dev` in `application-local.yml` that would re-activate the bean via include-chain | `grep "spring.profiles.include" application-local.yml application.yml` | no matches | PASS |
| Branch lock held — verifier still on `gsd/v1.14-team-card-redesign` | `git branch --show-current` | `gsd/v1.14-team-card-redesign` | PASS |
| Other main-source files referencing the narrowed beans only do so in comments/imports, not via parallel `@Component` re-introduction | `grep -rE "DevDataSeeder\|TestDataService" src/main/java/` (excluding the two source files) | Only 3 cross-references: `DemoDataSeeder.java` `@Order(2) // run after DevDataSeeder` (ordering hint), `TeamCardService.java` (one-line comment about Surefire/Failsafe skip), `DriverProfilePageGenerator.java` (one-line comment about RaceLineup usage). No parallel `@Component` or `@Service` re-introducing either bean under a different name. | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description (verbatim from REQUIREMENTS.md) | Status | Evidence |
|-------------|-------------|---------------------------------------------|--------|----------|
| SAFE-01 | `104-01-PLAN.md` | "`DevDataSeeder` and `TestDataService` Spring beans are only loaded when the active profile contains `dev`. Active profiles `local`, `docker`, and `prod` MUST NOT instantiate either bean, so the test-data seeder cannot run against the real MariaDB or write demo logos into `data/local/uploads/`. (Reverts the v1.11 `@Profile({"dev","local"})` drift introduced by commit `598d1431`.)" | SATISFIED | `DevDataSeeder.java:12` and `TestDataService.java:40` both `@Profile("dev")`. No other profile token present. By Spring `@Profile` semantics, `local` / `docker` / `prod` contexts will not instantiate either bean — proven empirically for `local` by the new IT, proven structurally for `docker` / `prod` by the absence of any "dev" matcher when those profiles are active. |
| SAFE-02 | `104-01-PLAN.md` | "An integration test loads the Spring context with `@ActiveProfiles("local")` and asserts that both `DevDataSeeder` and `TestDataService` beans are absent from the context. The test must fail if either bean is registered, so any future re-drift toward including `local` in the seeder's `@Profile` value is caught by `./mvnw verify` instead of by a production data accident." | SATISFIED | `src/test/java/org/ctc/admin/LocalProfileDataSafetyIT.java` exists, tagged `@Tag("integration")` so it runs in Failsafe on every `./mvnw verify`. Asserts BOTH bean classes via `getBeanNamesForType(...).isEmpty()`. Failsafe XML confirms `tests=1 failures=0 errors=0 skipped=0`. Any future `@Profile` widening, `@ConditionalOnProperty` flip, or sibling `@Component` re-introduction turns this red. |

**Coverage:** 2/2 phase requirements (SAFE-01, SAFE-02) substantively closed. No orphaned requirements (REQUIREMENTS.md Traceability table maps both IDs exclusively to Phase 104).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | None | — | Clean scan across all 3 modified/new files (no marker comments, no `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`, no console-log-only stubs, no empty `return null` stubs, no `Thread.sleep` / `Awaitility` flake-prone constructs). |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| — | — | — | SKIPPED (no `scripts/*/tests/probe-*.sh` probes documented in PLAN or SUMMARY; this phase's gate is the Maven `./mvnw clean verify -Pe2e` lifecycle, executed by the executor as commit `91d62eb2`; per orchestrator note + CLAUDE.md "Clean Maven Build is the Source of Truth", do not re-run a full verify here) |

### Cross-Phase Regression Notes

- **v1.13 baseline preserved.** v1.13-close JaCoCo (Phase 103, per `.planning/milestones/v1.13-MILESTONE-AUDIT.md`) = **89.42 %**. Phase 104 measured: **89.4211 %**. At parity — no coverage regression. The "89.43 %" value in PLAN must_haves and STATE.md is a documented Phase-102 propagation typo (SUMMARY.md "Deviations from Plan" reconciles this); the orchestrator dispatch note authorizes scoring it as parity, not regression. Follow-up: STATE.md baseline text correction (89.43 → 89.42) at v1.14 milestone close — informational, not a gap.
- **`OpenSecurityConfig` correctly untouched.** Auth-not-data exception held — `@Profile({"dev", "local"})` preserved on line 12 per memory `feedback_local_profile_no_dev_data`.
- **No "Phase 95 auto-post hook" regression.** No source file under the auto-post code path was touched; the WireMock-backed POST-02 IT is not in scope for this phase, and Failsafe count (140 IT XMLs) signals no IT regression in that area.
- **SAFE-01 v1.11 drift commit cited:** `598d1431` — this verification confirms the substantive revert lands on `gsd/v1.14-team-card-redesign` via commit `4b92695a` (`fix(104-01): scope DevDataSeeder + TestDataService to @Profile("dev")`).

### Human Verification Required

None. All four ROADMAP Success Criteria are programmatically verifiable through:

- file-level `@Profile` inspection (SC-1, SC-2 structural side),
- Failsafe XML test outcomes (SC-1 empirical side, SC-3),
- Maven gate artifacts — JaCoCo CSV and SpotBugs XML (SC-4).

No UI surface, no real-time behavior, no external service integration in this phase. The cross-milestone operator UAT carried in REQUIREMENTS.md (`QUAL-02` — `local`-profile MariaDB manual smoke) remains a cross-milestone-debt item (NOT a Phase 104 deliverable per the REQUIREMENTS.md "Cross-milestone operator-action UATs" section) and is therefore not raised here.

### Gaps Summary

None. Phase 104 substantively closes SAFE-01 and SAFE-02:

1. The two data-seeding components are narrowed back to `@Profile("dev")` exactly as REQUIREMENTS.md and ROADMAP.md mandate.
2. A regression fence (`LocalProfileDataSafetyIT`) lives in Failsafe under `@Tag("integration")` and will fail `./mvnw verify` on any future widening, conditional-flip, or component re-introduction that re-registers either bean under `@ActiveProfiles("local")`.
3. The auth-not-data companion (`OpenSecurityConfig`) was correctly left untouched.
4. The Maven gate (`./mvnw clean verify -Pe2e`) was green at commit `91d62eb2` with JaCoCo 89.42 % (82 % pom gate + v1.13-close baseline both held), SpotBugs 0, the new IT passing in 1.403 s, no marker-comment pollution in any modified/new source file.

The single documented coverage delta (89.43 → 89.42) is a measurement-truth reconciliation against the actual v1.13 closer (Phase 103), not a phase-induced regression — `TestDataService` is JaCoCo-excluded via `pom.xml`, so the `@Profile` narrowing cannot mechanically affect the denominator via that class.

Phase goal achieved. Ready to proceed.

---

_Verified: 2026-05-29T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
