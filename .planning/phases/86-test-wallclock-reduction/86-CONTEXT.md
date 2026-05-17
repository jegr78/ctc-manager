# Phase 86: Test Wallclock Reduction - Context

**Gathered:** 2026-05-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Reduce `./mvnw verify -Pe2e` wallclock from the v1.10 baseline of 11m 11s to **≤7m 50s (≥30% reduction)** OR document the specific architectural blocker plus a concrete v1.12 forward path. Phase scope: full `@DirtiesContext` audit, ≥1 repository-only IT converted from `@SpringBootTest` to `@DataJpaTest`, ApplicationContext initialization count documented in `docs/test-performance.md`, and improved wallclock validated on CI over multiple consecutive runs.

</domain>

<decisions>
## Implementation Decisions

### @DirtiesContext Audit Strategy (16 usages, 12 files)

- **D-01:** Default audit mode is **remove-then-verify**. Each `@DirtiesContext` is treated as tech debt until proven otherwise: remove the annotation, run 3× Surefire with `-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234, 5678, 9999}`, all-green = stays removed, any-red = annotation restored with an explanatory comment naming the specific shared state.
- **D-02:** Verify loop is **Surefire-only per removal** (`./mvnw test -Dtest=…` with random order) for fast feedback — the full `./mvnw verify -Pe2e` gate runs once at phase close.
- **D-03:** The 3 backup ITs with `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` (`ImportConcurrentLockIT`, `ImportLockBannerAdviceIT`, `ImportLockedPostRejectorIT`) replace the annotation with a dedicated **`ImportLockService` reset helper bean** invoked via `@AfterEach`. The shared state is concretely the `ReentrantLock` inside `ImportLockService` — resetting it directly is cheaper than a full context reload per test method. This is the single biggest Wallclock win in the phase.
- **D-04:** The 7 sitegen tests with class-level `@DirtiesContext` (`DriverProfilePageGeneratorTest`, `MatchdaysPageGeneratorTest`, `StandingsPageGeneratorTest`, `DriverRankingPageGeneratorTest`, `TeamProfilePageGeneratorTest`, `SiteGeneratorE2ETest`, `SiteGeneratorPhaseAwarenessIT`) are tackled as a **cluster root-cause fix**: hypothesis is the shared `ctc.site.output-dir` filesystem path. Use per-test `@TempDir Path siteOut` + `@DynamicPropertySource` (or property override) to bind `ctc.site.output-dir` to a per-class temp directory. If the hypothesis holds, all 7 annotations drop with one structural refactor.

### @DataJpaTest Pilot Scope (PERF-03)

- **D-05:** **Primary pilot is `PhaseTeamRepositoryIT`** — smallest schema surface (PhaseTeam finders + `existsByPhaseSeasonId` delete-guard), cleanest like-for-like comparison.
- **D-06:** **Scope is all three domain Phase repository ITs** (`PhaseTeamRepositoryIT` + `SeasonPhaseRepositoryIT` + `SeasonPhaseGroupRepositoryIT`) converted to `@DataJpaTest` in this phase. Each adds an incremental Wallclock contribution and establishes the slice pattern as a reusable convention.
- **D-07:** The legacy `// @SpringBootTest precedent honored over D-13 @DataJpaTest — see RESEARCH Open Question 1` comments are **removed along with the `@SpringBootTest` annotation**. No historical note needed — the commit message and `docs/test-performance.md` capture the reversal.
- **D-08:** **Auditing handling:** add a dedicated `@TestConfiguration JpaAuditingConfig` class with `@EnableJpaAuditing`, imported into each `@DataJpaTest` via `@Import(JpaAuditingConfig.class)`. Keeps slice purity (no full production config auto-loaded) while preserving auto-populated `created_at`/`updated_at` semantics where tests rely on them.

### Wallclock Measurement & Baseline (PERF-02, PERF-04, PERF-05)

- **D-09:** **Local re-baseline FIRST.** Run `time ./mvnw clean verify -Pe2e` 3× on current v1.11-master and record the median in `docs/test-performance.md` as the Phase-86 baseline. The Phase-79 number (11m 11s) becomes historical reference only — v1.11 phases 80-85 (OpenRewrite, SpotBugs, Renovate, CodeQL) may have shifted the Wallclock picture, so a fresh like-for-like baseline matters.
- **D-10:** **CI median methodology is 5 consecutive runs, drop min+max, median of the 3 middle runs.** This refines PERF-05's "3 consecutive runs" to be more robust against cold-cache and runner-variance outliers. If the 5 runs show >20% variance, repeat the block.
- **D-11:** **CI is the source of truth** for the ≤7m 50s gate. Local measurements during the audit serve only as fast-feedback direction sense. PERF-05 already mandates CI validation — the final verdict is the GitHub Actions median.
- **D-12:** **ApplicationContext init count instrumentation:** custom Spring `ApplicationContextInitializer` registered via `spring.factories` (or `META-INF/spring.factories` for test scope). The initializer holds a static `AtomicInteger` incremented on each new context load; the final count is written to a marker file (e.g., `target/test-perf/context-loads.txt`) at JVM shutdown. Pre-audit and post-audit counts both appear in `docs/test-performance.md` PERF-02 section.

### Blocker-Fallback Path (PERF-04 OR-branch)

- **D-13:** If ≥30% is not achieved, the v1.12 forward path in `docs/test-performance.md` documents the **top-3 structural levers**, each with: short description, expected Wallclock delta (e.g., "~90s"), effort estimate (S/M/L), risks/dependencies. Format makes the doc directly actionable for v1.12 planning, not a vague pointer.
- **D-14:** The `data/dev/backup-staging/` singleton-path race (Phase 79 D-06 finding) is **audited and documented as Top-1 v1.12 lever**, not fixed in Phase 86. Reason: per-fork staging-dir refactor is structural (touches `BackupImportService`, `BackupStagingCleanup`, Failsafe surefire-fork-numbering), would expand Phase 86 well beyond the audit-and-slice scope, and risks introducing new races. Hand-off to v1.12 is the cleaner boundary.
- **D-15:** **Realistic-optimistic expectation:** 16 `@DirtiesContext` removals (with the 3 backup-IT reset-bean swap + 7 sitegen `@TempDir` cluster fix) plus 3 `@DataJpaTest` conversions plus context-cache benefits should yield roughly 20-25% reduction (8m30s-9m). ≥7m 50s likely requires the Failsafe-parallelism unlock that's deferred to v1.12. The phase plans for honest documentation of partial win + blocker path as the default outcome, with potential to over-deliver if the sitegen `@TempDir` hypothesis holds stronger than estimated.

### Plan Scope Limit

- **D-16:** **Soft cap: ~6 plans, 2 waves.** If after plan 3-4 the trajectory makes ≥7m 50s clearly unreachable, plans 5-6 finalize the blocker documentation and v1.12-path lever set — phase closes cleanly. Avoids Phase-79-déjà-vu (forkCount tuning iterations that produced 22m 18s on Run 1).

### Claude's Discretion

- Exact wording of `docs/test-performance.md` sections; specific seed values within the (1234/5678/9999) family or substitutions thereof; whether the `JpaAuditingConfig` lives in `src/test/java/org/ctc/testsupport/` or `src/test/java/org/ctc/domain/repository/` (researcher/planner picks the location matching existing test-support conventions).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` — Phase 86 section (Goal, Depends-on, Requirements, Success Criteria); Phase 87 (downstream dependency)
- `.planning/REQUIREMENTS.md` — PERF-01..PERF-05 full text
- `.planning/PROJECT.md` — v1.11 milestone scope; "Phase 79 D-06 wallclock-reduction debt" deferred item
- `.planning/STATE.md` — current position (Phase 85 complete, Phase 86 next)

### Prior Context (Phase 79 D-06)

- git ref `701739fb` — "docs(79): record final wallclock + reduction verdict (D-06)"
- git ref `e1bf4432` — "docs(79-07): final wallclock + jacoco + verification SUMMARY (D-06 partial, D-18/D-19 PASS)"
- Archived `.planning/phases/79-…/79-VERIFICATION.md` (accessible via `git show 60f5f915~1:.planning/phases/79-…/79-VERIFICATION.md`) — documents the 16.85% partial result, forkCount=2C blowup, and Failsafe-parallelism blocker

### Testing Conventions (must respect)

- `.planning/codebase/TESTING.md` §"Test Categorization (`@Tag`)" — every new test class needs `@Tag(...)`; `@DataJpaTest` ITs keep `@Tag("integration")`
- `.planning/codebase/TESTING.md` §"Test Invocation Discipline" — one final `./mvnw verify -Pe2e` per phase; targeted `-Dtest=` / `-Dit.test=` between waves
- `CLAUDE.md` "Tag Tests by Category" rule

### Deliverable (to be created in this phase)

- `docs/test-performance.md` — Phase-86 baseline, post-optimization wallclock, ApplicationContext init counts (pre/post), Phase-86 result verdict, v1.12 top-3 forward-path levers if ≥30% not reached

### Architecture (orienting reads)

- `pom.xml` lines covering Surefire `<forkCount>2</forkCount>`, `<reuseForks>true</reuseForks>`, `<excludedGroups>integration,e2e,flaky</excludedGroups>`; Failsafe `default-it` and the `e2e` profile execution
- `src/main/java/org/ctc/backup/service/BackupImportService.java` — `data/${profile}/backup-staging/` path construction (audited only in Phase 86, fixed in v1.12)
- `src/main/java/org/ctc/backup/service/ImportLockService.java` — singleton `ReentrantLock` whose reset replaces 3 `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` annotations
- `src/main/resources/application*.yml` — `ctc.site.output-dir` property bound to filesystem path (sitegen `@TempDir` refactor target)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`@Tag("integration")` routing convention** — already established in Phase 79; new `@DataJpaTest` ITs keep this tag so Failsafe `default-it` picks them up automatically.
- **`@TempDir` Junit Jupiter support** — already used in `BackupStagingCleanupIT` and other tests; sitegen cluster fix builds on the same pattern.
- **`@DynamicPropertySource`** — pattern is available for binding `ctc.site.output-dir` to per-test temp paths without YAML overrides.
- **`AuditingEntityListener`** wired via `BaseEntity` — needs `JpaAuditingConfig` re-enable in slice tests; pattern is well-trodden.

### Established Patterns

- **3-tier separation (Controller → Service → Repository)** means repository tests can be sliced cleanly — no controller/service mocking gymnastics.
- **`@SpringBootTest + @Transactional`** is the current default for repository ITs (3 files have the explicit "Phase 58 precedent" comment). Phase 86 overrides this precedent.
- **Surefire random-order** is already supported (`-Dsurefire.runOrder=random`) — Phase 79 used it during the @Tag refactor.
- **Tag-based fork separation** — Surefire `forkCount=2 reuseForks=true` for unit tests; Failsafe `default-it forkCount=1C`; tests inside each fork share JVM but Spring caches contexts.

### Integration Points

- **`spring.factories` (test scope)** — register the new `ContextLoadCountListener` here; minimal blast radius.
- **`pom.xml` Surefire + Failsafe configuration** — no fork-count changes needed for Phase 86 (Phase 79 already proved higher fork counts blow up). The improvements come from reducing per-test context-pollution events, not parallelism.
- **`docs/` directory** — `docs/security/sast-acceptance.md`, `docs/uat/UAT-02-…` show the convention for living Phase-deliverable docs. `docs/test-performance.md` follows the same shape: ToC + per-section evidence tables.

</code_context>

<specifics>
## Specific Ideas

- **Seed family (1234/5678/9999)** as default Surefire random seeds for the per-removal verification triple.
- **Marker file** `target/test-perf/context-loads.txt` for the `ContextLoadCountListener` output — under `target/` so it's gitignored and clean-build resets it.
- **v1.12 lever doc shape:** each lever ≈ 100-150 words, columns "Lever | Estimated Wallclock Delta | Effort (S/M/L) | Risks/Dependencies | Required Touchpoints".
- **`time` wrapper** for re-baseline measurements — Maven's "Total time" reports wall time; bash `time` adds harness startup. Phase 79 verification doc kept both — Phase 86 follows the same dual-record pattern.

</specifics>

<deferred>
## Deferred Ideas

- **Per-fork `data/dev/backup-staging/` refactor** — Top-1 v1.12 lever; structural enough to deserve its own phase. Touches `BackupImportService`, `BackupStagingCleanup`, Failsafe surefire-fork-numbering system property propagation.
- **Shared `@SpringBootTest` context strategy** — explicit `@ContextConfiguration` classes shared across IT clusters to maximize Spring's TCF cache reuse. Candidate v1.12 lever #2.
- **Testcontainers MariaDB reuse** — `withReuse(true)` + `~/.testcontainers.properties` for warm-container startups. Candidate v1.12 lever #3, only relevant once MariaDB ITs exist (none in v1.11).
- **Wider `@DataJpaTest` migration** — beyond the 3 Phase repository ITs, other repository ITs (e.g., `RaceRepository`, `DriverRepository`, `SeasonRepository`) could benefit from the slice pattern. Deferred to v1.12 if Phase 86's pilot proves the win.
- **Spring TCF Cache stats `DEBUG` logging** — alternative to the custom `ContextLoadCountListener`. Considered but not selected; can be added in v1.12 if PERF-02 follow-up needs more granularity.

</deferred>

---

*Phase: 86-Test Wallclock Reduction*
*Context gathered: 2026-05-17*
