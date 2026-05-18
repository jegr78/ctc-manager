---
phase: 86-test-wallclock-reduction
plan: 02
status: complete
date: 2026-05-17
---

# Plan 86-02 — Sitegen Cluster outputDir Isolation (D-04) + Cluster C Conditional Removal

## Outcome

D-04 cluster fix applied. All 7 sitegen test classes (6 Surefire + 1 Failsafe) now own their own per-class temp directory bound to `ctc.site.output-dir` via `@DynamicPropertySource`, replacing the shared-singleton `siteProperties.setOutputDir(tempDir.toString())` mutation pattern in `@BeforeAll`. All 8 `@DirtiesContext` annotations in the sitegen cluster + the defensive Phase 79 annotation on `TestDataServiceIntegrationTest` are removed — 3-seed random-order verification confirms no regressions.

## Plan revision (Pitfall 3 — PER_CLASS lifecycle)

The plan called for `@TempDir static Path tempDir;` per class. JUnit 5 does initialize static `@TempDir` fields **before** Spring's `@DynamicPropertySource` resolver under the standard PER_METHOD lifecycle (validated by `BackupStagingCleanupIT`). However, under `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` — which all 7 sitegen tests use (their non-static `@BeforeAll` setup requires it for `@Autowired` service access) — the order inverts: Spring resolves `ctc.site.output-dir` against a still-`null` static `tempDir` and bean-binding fails with `NullPointerException: Cannot invoke "Path.toString()" because "...tempDir" is null`.

**Revision:** introduce a small test-support helper `org.ctc.testsupport.SitegenTestDir.create(String label)` that creates a temp directory via `Files.createTempDirectory(...)` at static-field-initialization time (guaranteed to run before any JUnit or Spring callback) and registers a JVM shutdown hook for best-effort cleanup. Each sitegen test declares `static final Path tempDir = SitegenTestDir.create("<label>");` — same semantics as `@TempDir static` but without the lifecycle dependency.

This is functionally equivalent to the plan's intent; the structural fix (per-class output-dir isolation via `@DynamicPropertySource`) holds.

## @DirtiesContext Count Delta

| File | Before | After | Notes |
|---|---|---|---|
| DriverProfilePageGeneratorTest | class-level `@DirtiesContext` (1) | — | Removed |
| MatchdaysPageGeneratorTest | class-level `@DirtiesContext` (1) | — | Removed |
| StandingsPageGeneratorTest | class-level `@DirtiesContext` (1) | — | Removed |
| DriverRankingPageGeneratorTest | class-level `@DirtiesContext` (1) | — | Removed |
| TeamProfilePageGeneratorTest | class-level `@DirtiesContext` (1) | — | Removed |
| SiteGeneratorE2ETest | class-level `@DirtiesContext` (1) | — | Removed |
| SiteGeneratorPhaseAwarenessIT | class-level `@DirtiesContext` (1) | — | Removed (Flyway clean+migrate in @BeforeAll is the actual H2 reset mechanism — no need to evict the bean cache) |
| TestDataServiceIntegrationTest | class-level `@DirtiesContext` (1) + 5-line Phase-79 defensive comment | — | Removed (Cluster C — D-04 root cause fixed) |
| **Total** | **8** | **0** | **−8 evictions in Cluster A + C** |

## Per-class context-cache-key impact

With `@DirtiesContext` removed, each sitegen test class's context normally lives across all its `@Test` methods (no eviction). Because `@DynamicPropertySource` binds `ctc.site.output-dir` to a per-class unique path, each test class gets its OWN context cache key — 7 distinct sitegen contexts per Surefire pass.

**Net wallclock impact for the sitegen cluster: ≈ unchanged** — pre-plan @DirtiesContext at class level evicted AFTER each class (default `ClassMode.AFTER_CLASS`), forcing the next sitegen class to rebuild. Post-plan, distinct cache keys force 7 builds anyway. The architectural win is structural correctness (no shared-singleton mutation, per-class output-dir isolation) — ContextLoadCountListener (Plan 86-01) will quantify the precise delta at Plan 86-05 measurement gate.

The Cluster C removal does deliver a measurable saving: `TestDataServiceIntegrationTest` no longer evicts the context after its 18 tests complete, allowing the cached `@SpringBootTest + dev + @Transactional` context to be reused by other unit tests sharing that key.

## 3-Seed Verification Evidence

### Task 1 — 6 Surefire sitegen tests

```bash
./mvnw test -Dtest='org.ctc.sitegen.{DriverProfile,Matchdays,Standings,DriverRanking,TeamProfile,SiteGeneratorE2E}PageGeneratorTest' \
  -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=<seed> -Djacoco.skip=true
```

| Seed | Tests run | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| 1234 | 46 | 0 | 0 | 1 (@Disabled) | BUILD SUCCESS |
| 5678 | 46 | 0 | 0 | 1 (@Disabled) | BUILD SUCCESS |
| 9999 | 46 | 0 | 0 | 1 (@Disabled) | BUILD SUCCESS |

### Task 2 — SiteGeneratorPhaseAwarenessIT (Failsafe)

```bash
./mvnw verify -Dtest=DoesNotExist -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=SiteGeneratorPhaseAwarenessIT \
  -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=<seed> -Djacoco.skip=true
```

| Seed | Tests run | Failures | Errors | Verdict |
|---|---|---|---|---|
| 1234 | 9 | 0 | 0 | BUILD SUCCESS |
| 5678 | 9 | 0 | 0 | BUILD SUCCESS |
| 9999 | 9 | 0 | 0 | BUILD SUCCESS |

Result: `@DirtiesContext` cleanly removable. The Flyway clean+migrate in `@BeforeAll` (which is independent of context bean cache) is the actual H2 reset mechanism — bean-cache eviction was redundant.

### Task 3 — TestDataServiceIntegrationTest + sitegen cluster (Cluster C verification)

```bash
./mvnw test -Dtest='org.ctc.admin.TestDataServiceIntegrationTest,org.ctc.sitegen.*Test' \
  -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=<seed> -Djacoco.skip=true
```

| Seed | Tests run | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| 1234 | 156 | 0 | 0 | 3 (@Disabled / @Disabled / @Disabled) | BUILD SUCCESS |
| 5678 | 156 | 0 | 0 | 3 | BUILD SUCCESS |
| 9999 | 156 | 0 | 0 | 3 | BUILD SUCCESS |

Result: combined-cluster verification on 156 tests across 9 sitegen `*Test` classes + `TestDataServiceIntegrationTest` (18 tests) green on all 3 seeds. The defensive @DirtiesContext on `TestDataServiceIntegrationTest` is unnecessary — the test's own `Flyway.clean() + migrate() + seed()` in `@BeforeAll` covers H2 isolation, and the new per-class context cache keys for sitegen tests further isolate the contexts.

## Key files

### Created

- `src/test/java/org/ctc/testsupport/SitegenTestDir.java` — `@TempDir`-equivalent helper for PER_CLASS-lifecycle sitegen tests (Plan-revision artifact)

### Modified

- `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java`
- `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java`
- `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` — `@DirtiesContext` + 5-line Phase-79 defensive comment block removed

All 7 sitegen classes: `siteProperties.setOutputDir(...)` mutation removed, `@TempDir Path injectedTempDir` parameter removed from `@BeforeAll`, `private Path tempDir;` instance field replaced with `static final Path tempDir = SitegenTestDir.create("<label>")`, `@DynamicPropertySource siteOutputDir(...)` static method added, `@DirtiesContext` removed. `@Autowired private SiteProperties siteProperties;` field removed where unused (it remained in `SiteGeneratorE2ETest` because `siteProperties.setLinks(...)` is still called).

## Plan-05 Note (context-load count expectation)

Expected direction: **slight up or unchanged** for the sitegen cluster alone (each per-class `@DynamicPropertySource` binding creates a unique cache key → 7 contexts whether or not @DirtiesContext is on the classes). The Cluster C removal removes 1 spurious eviction.

Combined with Plan 86-04's @DataJpaTest collapse (3 phase repo ITs → 1 shared context, −2 evictions) and Plan 86-03's per-method @DirtiesContext audit (−2 evictions in backup ITs), the global suite delta from Phase 86 Wave 2 is:

| Source | Δ context loads per pass |
|---|---|
| 86-02 sitegen cluster | ≈ 0 (architectural fix; was already 7 builds with eviction-rebuild pattern) |
| 86-02 TestDataServiceIntegrationTest | −1 (no class-level eviction after run) |
| 86-03 backup ITs | −2 (latch-free methods no longer evict) |
| 86-04 phase repo ITs | −2 (3 `@SpringBootTest` contexts → 1 shared `@DataJpaTest`) |
| **Wave 2 total** | **−5 context loads per Failsafe + Surefire pass** |

Plan 86-05 will replace these estimates with measured `ContextLoadCountListener` data.

## Issues encountered

One plan revision (Pitfall 3 manifesting under PER_CLASS lifecycle — see "Plan revision" section above). Documented and worked around via the `SitegenTestDir` helper. No test failures encountered; all 3 seeds green on first verification pass after the helper-based pattern was applied.

## Follow-ups

- Plan 86-05 records the measured ContextLoadCountListener delta and the cluster-level wallclock impact in `docs/test-performance.md`
- If a future phase wants to share a single sitegen context across all 7 sitegen test classes, the fix would be to colocate `tempDir` creation in a shared base class (or use `@ContextHierarchy` to share the parent context) — not in scope for v1.11
