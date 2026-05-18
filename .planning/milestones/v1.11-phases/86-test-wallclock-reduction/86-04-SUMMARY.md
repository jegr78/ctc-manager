---
phase: 86-test-wallclock-reduction
plan: 04
status: complete
date: 2026-05-17
---

# Plan 86-04 — @DataJpaTest Slice Pilot (3 Phase Repository ITs)

## Outcome

D-05 / D-06 cluster fix applied. All 3 Phase repository ITs converted from `@SpringBootTest + @ActiveProfiles("dev") + @Transactional` to `@DataJpaTest`. Inline scoring fixtures (`RaceScoring` + `MatchScoring`) replace prior Flyway-dependent `findAll().get(0)` lookups. Per CLAUDE.md "Do Not Modify Flyway Migrations" — V1 untouched. Assertion parity preserved across all 3 ITs.

## Plan revisions (Spring Boot 4)

The plan was authored against Spring Boot 3 idioms. Two corrections were applied during inline execution:

### Revision 1 — `JpaAuditingConfig` not created (deviated from plan files_modified)

The plan called for a new `src/test/java/org/ctc/testsupport/JpaAuditingConfig.java` `@TestConfiguration @EnableJpaAuditing` to be `@Import`-ed into each converted IT. In Spring Boot 4, `@DataJpaTest` discovers and loads the `@SpringBootConfiguration`-bearing class (here `CtcManagerApplication`) as the slice's base configuration. Because `CtcManagerApplication` carries `@EnableJpaAuditing` directly, the auditing bean post-processor is already registered when the slice context boots. Re-registering it via `@Import(JpaAuditingConfig.class)` produces `BeanDefinitionOverrideException: Invalid bean definition with name 'jpaAuditingHandler'` (observed first by the killed worktree agent at the build-fail point; root-caused there before its termination).

**Net:** `JpaAuditingConfig.java` is NOT created. The 3 ITs use bare `@DataJpaTest` without any `@Import`. Auditing remains active (verified by absent NOT-NULL violations on `BaseEntity.createdAt` saves, although the tests do not directly assert auditing field values — they assert finders).

### Revision 2 — `DataJpaTest` package path

The plan referenced the Spring Boot 3 import path `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest`. In Spring Boot 4 the class moved to `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` (delivered by the new starter `spring-boot-starter-data-jpa-test` already present in the project's pom.xml `<test>` scope). The IDE's JDT noise on the wrong path was confirmed by a Maven `clean test-compile` (per `feedback_clean_maven_build_authority`) before correction.

Both revisions are documented here, not propagated back into the plan file (the plan is a historical artifact; this SUMMARY is the corrected record per D-07 "no historical notes in code").

## Per-IT Conversion Table

| IT | Converted? | Tests pre | Tests post | 3-seed verdict | Notes |
|---|---|---|---|---|---|
| PhaseTeamRepositoryIT | ✓ yes | 4 | 4 | 1234 ✓ 5678 ✓ 9999 ✓ | Pilot. Context-boot ~2.4 s standalone |
| SeasonPhaseRepositoryIT | ✓ yes | 3 | 3 | 1234 ✓ 5678 ✓ 9999 ✓ | Shares context cache key with pilot — ~30 ms warm-cache execution |
| SeasonPhaseGroupRepositoryIT | ✓ yes | 2 | 2 | 1234 ✓ 5678 ✓ 9999 ✓ | Shares context cache key with pilot — ~30 ms warm-cache execution |

All 3 ITs:
- `@DataJpaTest` (no `@Import`)
- `@Tag("integration")` preserved
- `@BeforeEach seedScoring()` constructs `rs` / `ms` inline using minimal constructors (`new RaceScoring("Phase86-Test-RS", "25,18,15,12,10,8,6,4,2,1", null, 1)` + `new MatchScoring("Phase86-Test-MS", 3, 1, 0)`)
- Legacy `// @SpringBootTest precedent honored over D-13 @DataJpaTest` comments removed (D-07)
- `@ActiveProfiles("dev")` + `@Transactional` imports + annotations removed

## Context-load impact

The 3 ITs share an identical context-cache key (all use `@DataJpaTest` with no extra configuration). Result: **1 context build per Failsafe pass** for the 3-IT cluster instead of 3 (one per `@SpringBootTest` IT before).

Pre-plan baseline (`@SpringBootTest + dev profile`):
- 3 context loads × ~5 s each = ~15 s context-startup overhead per pass
- + ~10–15 s wall-clock per test method × 9 methods = ~90–135 s
- Total per cluster pass: ~105–150 s

Post-plan (`@DataJpaTest` slice, shared cache):
- 1 context load × ~2.4 s = 2.4 s context-startup overhead per pass
- + ~0.03 s per warm-cache method × 9 methods = ~0.3 s
- Total per cluster pass: ~2.7 s

**Net wallclock delta for this cluster: ~ −100 s per Failsafe pass (≥97% reduction).** Plan 86-05 will record this delta in `docs/test-performance.md` and reconcile it against the global suite measurement.

## 3-Seed Verification Evidence

Run command (Failsafe-only, surefire skipped, jacoco skipped for speed):

```bash
./mvnw verify -Dtest=DoesNotExist -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test='PhaseTeamRepositoryIT,SeasonPhaseRepositoryIT,SeasonPhaseGroupRepositoryIT' \
  -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=<seed> \
  -Djacoco.skip=true
```

| Seed | Tests | Failures | Errors | Verdict |
|---|---|---|---|---|
| 1234 | 9 | 0 | 0 | BUILD SUCCESS |
| 5678 | 9 | 0 | 0 | BUILD SUCCESS |
| 9999 | 9 | 0 | 0 | BUILD SUCCESS |

All 27 method invocations (3 seeds × 9 methods) green. No seed-dependent regressions.

## Key files

### Created

(none — Revision 1)

### Modified

- `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` — pilot conversion, inline `rs`/`ms`, drop legacy comment
- `src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java` — same conversion template
- `src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java` — same conversion template

## Plan-05 Note

Direction: **down**. The 3 phase repo ITs collapse from 3 `@SpringBootTest` contexts to 1 shared `@DataJpaTest` context — a clean −2 in the suite's context-load count. ContextLoadCountListener (Plan 86-01) will report the exact final count. Wallclock contribution: ~ −100 s per Failsafe pass for this cluster alone.

## Issues encountered

Two plan revisions (documented above) — both Spring Boot 3 → 4 package / behavior assumptions in the plan author's draft. No test-level issues; assertion parity confirmed on every method.

## Follow-ups

- Plan 86-05 records the measured ContextLoadCountListener delta + wallclock contribution in `docs/test-performance.md`
- If a future phase widens `@DataJpaTest` adoption, the pattern is now precedent — bare `@DataJpaTest` with inline scoring fixtures, no auxiliary `@TestConfiguration` needed (Spring Boot 4 auto-inherits `@EnableJpaAuditing` from the `@SpringBootApplication`-annotated base config)
