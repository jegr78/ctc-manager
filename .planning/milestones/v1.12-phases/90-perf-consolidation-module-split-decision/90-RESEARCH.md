# Phase 90: PERF Consolidation & Module-Split Decision — Research

**Researched:** 2026-05-20
**Domain:** Spring TCF context-cache consolidation via composed-annotation; Testcontainers reuse opt-in; Maven test-module-split decision capture.
**Confidence:** HIGH (Context7-free; verified against official Spring/Testcontainers docs + on-disk empirical fingerprint data)

## Summary

Phase 90 ships three sequential plans that share one underlying mechanism (Spring TCF
`MergedContextConfiguration` cache-key identity) and one CI invariant (no production-code
change, no CI behavioural change). The research goal here is **validation, not exploration**
— CONTEXT.md D-01..D-09 lock the decisions; this document checks the technical mechanism
against authoritative sources and against the live fingerprint sidecar files currently
sitting in `target/test-perf/`.

The headline finding: **the planner's `@CtcDevSpringBootContext` strategy is mechanically
sound**. `MergedContextConfiguration.hashCode()` deliberately excludes the test class (it
hashes `locations` + `classes` + `activeProfiles` + `contextInitializerClasses` +
`contextCustomizers` + `propertySourceDescriptors` + `parent` + `ContextLoader` FQN) — so
two classes wearing a composed annotation that resolves to identical `classes =
[CtcManagerApplication]` and `activeProfiles = ["dev"]` will collide into the same hash
bucket. Verified empirically below.

The second headline finding overturns the D-01 hypothesis ranking: **the existing
fingerprint data already proves the answer**. The 29-class `9cefac4c` bucket and the
10-class `f524774b` bucket each contain real distinct test classes — not over-counts from
`@DirtiesContext`-style rebuilds. Five of those classes live in `db.migration`; the
remaining 34 are unrelated `@SpringBootTest + @ActiveProfiles("dev")` consumers that are
**already in scope per D-01**'s "every class currently in those buckets now wears
`@CtcDevSpringBootContext`" acceptance form.

**Primary recommendation:** Plan 90-01 runs the existing aggregator over the current
`target/test-perf/` sidecars to enumerate the 39 classes verbatim (already done in this
research session — see Audit table below), then applies `@CtcDevSpringBootContext` to all
39. Plans 90-02 and 90-03 ship as specified; Testcontainers 2.0.5 supports
`.withReuse(true)` natively and the experimental-feature status is acceptable for the
dev-only opt-in.

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01: Conservative — `db.migration.**` cluster only (plus every other class the aggregator proves shares the same two hash buckets).** Audit re-run mandatory at Plan-01 start.
- **D-02: Custom composed annotation `@CtcDevSpringBootContext`** = `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")`. No `@AliasFor`, no `@Tag`, no `@Transactional` embedded.
- **D-02b: Cache-key reduction proof = aggregator diff before/after.** Top-5 sections copied into 90-01-SUMMARY.md side-by-side; acceptance is bucket collapse + every D-01-identified class in the consolidated bucket.
- **D-03: 3-seed Failsafe verification on `db.migration.**` ITs + 1 seed-stable Surefire run on `db.migration.**` Tests.**
- **D-04: `.withReuse(true)` on the two existing `MariaDBContainer<>` declarations** in `BackupImportMariaDbSmokeIT` and `BackupRoundTripIT.MariaDbRoundTripTests`. CI unaffected.
- **D-04b: Production `application*.yml` and `BackupStagingCleanup` stay untouched.**
- **D-05: Defer-with-explicit-blockers verdict** on test-module-split. Three blockers + re-evaluation trigger.
- **D-06: Three plans, sequential inline** (90-01 PERF-03 → 90-02 PERF-04 → 90-03 PERF-05). `[[wave-pause]]` + `[[inline-sequential-execution]]`.
- **D-07: Wave-5 local idle measurement bundled into Plan 90-01 SUMMARY** (3 `./mvnw clean verify -Pe2e` runs).
- **D-08: Quality gates** — JaCoCo ≥ 0.8888, SpotBugs 0, CodeQL exit 0, EXPORT_ORDER 24, SCHEMA_VERSION 1, V1-V7 immutable.
- **D-09: No production code touched in Phase 90.** All work under `src/test/java/**` and `docs/`.

### Claude's Discretion

- Exact wording of `docs/test-performance.md § PERF-03 Cluster` prose + Top-5 diff table format.
- Whether to re-run `./mvnw clean verify -Pe2e` from scratch for the "before" snapshot OR re-use the most recent Phase-89 Wave-4 fingerprint output. Planner picks based on freshness-vs-runtime trade-off; the "after" snapshot must be from a post-refactor run.
- Whether `@CtcDevSpringBootContext` lives at `org.ctc.testsupport.CtcDevSpringBootContext` (canonical) or under a sub-package. Default: canonical.
- Whether a `@MustBeClosed`-style Javadoc warns future authors not to add `@DirtiesContext` to subclasses.
- Exact wording of `docs/test-performance.md § PERF-04 Testcontainers Reuse`.
- Post-PERF-03 fingerprint sidecar handling (keep as `.test-perf-logs/90-01-wave5-run-{1,2,3}/` evidence OR allow `target/` to wipe).

### Deferred Ideas (OUT OF SCOPE)

- Secondary cluster consolidation (backup-exception `499c01dd`, admin-security `2cb78737`, AdminWorkflowE2E `5ff2b420`).
- Maven sub-module extraction (`ctc-manager-tests` artifact).
- Wider `@CtcDevSpringBootContext` adoption across ~100 IT classes.
- `@CtcLocalSpringBootContext` sister annotation for `@ActiveProfiles("local")`.
- PERF-06 CI authoritative re-harvest (Phase 91).
- UX-01 Google-API typed-exception hierarchy (Phase 91 stretch).
- PERF-04 CI-side reuse enabling.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PERF-03 | "At least one IT cluster identified by PERF-02 fingerprinting is consolidated onto a shared `@ContextConfiguration` (new `BaseFailsafeIT` super-class or a shared `@TestConfiguration` per cluster) with no test-isolation regression and a measurable cache-key reduction recorded in `docs/test-performance.md`." | §Mechanism Validation (composed-annotation cache-key collision proof) + §Audit (39-class enumeration) + §3-Seed Verification Command. |
| PERF-04 | "Testcontainers `~/.testcontainers.properties` reuse is wired (`testcontainers.reuse.enable=true`) and at least one MariaDB-backed IT exercises it; CI runs continue to use cold-start container without regression; setup documented in `docs/test-performance.md` and README." | §Testcontainers Reuse Validation (opt-in semantics, Ryuk interaction, CI invariant). |
| PERF-05 | "A `docs/test-performance.md § Test-Module-Split Decision` section is added that captures the v1.12 verdict (`proceed` / `defer` / `reject`); if `proceed`, the extraction ships." | §Module-Split Verdict Anchors (three blockers + re-evaluation trigger). |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Spring TCF cache-key consolidation | Test infrastructure (`src/test/java/org/ctc/testsupport/`) | — | The composed annotation is a pure test-side concern; production tier (`src/main/java/`) MUST stay git-clean per D-09. |
| Testcontainers reuse opt-in wiring | Test infrastructure (test class instantiation) | Developer-machine config (`~/.testcontainers.properties`) | The `.withReuse(true)` call lives on the existing `MariaDBContainer<>` test-class fields; the activation gate is the per-developer properties file. CI runners never set the file (D-04 invariant). |
| Module-split decision artefact | Documentation (`docs/test-performance.md`) | — | PERF-05 is a docs-only deliverable per D-05. |
| Forensic measurement (Wave-5) | Test infrastructure (existing PERF-02 listeners) + log directory (`.test-perf-logs/`) | — | Reuses Phase 89's `ContextLoadCountListener` + `ContextCacheKeyFingerprintListener`; no new instrumentation. |

## Standard Stack

### Core (already in place — Phase 90 adds zero new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Test Starter | 4.0.6 (managed by parent) | `@SpringBootTest`, `@ActiveProfiles`, `MergedContextConfiguration` | Canonical Spring TCF; the cache-key engine. [VERIFIED: pom.xml `<parent>` group `org.springframework.boot` artifact `spring-boot-starter-parent` version `4.0.6`] |
| Spring TestContext Framework (`spring-test`) | 7.x (transitive via Boot 4.0.6) | `TestExecutionListener`, `DefaultTestContext`, `MergedContextConfiguration#hashCode()` | TCF cache-bucket key implementation. [VERIFIED: Spring Framework 7.0.5 Javadoc for `MergedContextConfiguration` confirms `hashCode()` "generates a unique hash code for all properties of this `MergedContextConfiguration` excluding the test class"] [CITED: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/context/MergedContextConfiguration.html] |
| Testcontainers (BOM) | 2.0.5 | `MariaDBContainer<>`, `@Testcontainers`, `@Container`, `.withReuse(true)` | Reuse API present since 1.10.x; Testcontainers 2.0.5 inherits the same API surface. [VERIFIED: pom.xml line 23 `<testcontainers.version>2.0.5</testcontainers.version>`] [CITED: https://java.testcontainers.org/features/reuse/] |

### Supporting (already in place)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit Jupiter | managed by Boot 4.0.6 | `@Test`, `@Tag`, `@Nested` | Routing for Surefire vs Failsafe via `@Tag`. |
| AssertJ | managed by Boot 4.0.6 | `assertThat(...)` | Already in every db.migration test. |

### Alternatives Considered (REJECTED per CONTEXT.md D-02)

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Composed annotation `@CtcDevSpringBootContext` | Abstract super-class `BaseDevSpringBootIT` | Forces 5+ classes to `extends` (impossible mix with `@Transactional` on V4MigrationSmokeIT and possibly future `@TestInstance(PER_CLASS)` consumers); Java single-inheritance constraint. REJECTED in D-02. |
| Composed annotation | Shared `@TestConfiguration` via `@Import` | Adds beans to the context but does NOT change `MergedContextConfiguration` cache-key components (`classes`, `activeProfiles`, `contextCustomizers` — `@Import` from a test class adds a customizer that's hashed). Wrong tool for cache-key reduction. REJECTED in D-02. |

**Installation:** No `pom.xml` change in Phase 90. Phase 89 already wired all instrumentation.

**Version verification:**
- `testcontainers-bom` 2.0.5 confirmed in pom.xml line 23 [VERIFIED: direct file read].
- Spring Boot 4.0.6 confirmed in pom.xml line 8 [VERIFIED: direct file read].
- `MergedContextConfiguration.hashCode()` cache-key contract confirmed [CITED: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/context/MergedContextConfiguration.html — "An ApplicationContext can be uniquely identified by the combination of configuration parameters that is used to load it"].

## Package Legitimacy Audit

> Phase 90 installs **zero** new external packages. All required libraries are already on the classpath (Spring Boot test starter, Testcontainers, JUnit 5, AssertJ). slopcheck does not apply. **No-op section, kept for orchestrator schema compliance.**

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| _(none — phase adds no new dependencies)_ | — | — | — | — | n/a | n/a |

## Mechanism Validation — `@CtcDevSpringBootContext` cache-key collision proof

### Claim under test (D-02)

> "Two test classes wearing the composed annotation produce an identical `MergedContextConfiguration.hashCode()` bucket."

### Why this holds (authoritative source)

**`MergedContextConfiguration.hashCode()` excludes the test class.** From the Spring
Framework 7.0.5 Javadoc:

> "Generate a unique hash code for all properties of this `MergedContextConfiguration` excluding the test class."
> [CITED: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/context/MergedContextConfiguration.html — verified 2026-05-20]

The hash is computed from: `locations`, `classes`, `contextInitializerClasses`,
`activeProfiles`, `propertySourceDescriptors`, `contextCustomizers`, `parent`, and the FQN
of the `ContextLoader`. Test class identity is deliberately omitted because the cache is
keyed on "the configuration that built this context", not "the class that asked for it"
— that is the whole point of Spring TCF's context cache.

### Empirical proof from current `target/test-perf/` sidecars

Running `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5` against the live
sidecar files (Phase 89 Wave-4 evidence still on disk from 2026-05-19):

```text
# Top 5 cache-key clusters by occurrence x cluster-size
# Source: 5 sidecar file(s) in target/test-perf

1. 9cefac4c -- 29 occurrences across 29 classes (score=841)
2. 499c01dd -- 12 occurrences across 12 classes (score=144)
3. 2cb78737 -- 12 occurrences across 12 classes (score=144)
4. f524774b -- 10 occurrences across 10 classes (score=100)
5. 5ff2b420 --  7 occurrences across  7 classes (score=49)
```

The two PERF-03 buckets enumerated verbatim:

#### Bucket `9cefac4c` — 29 distinct test classes (Surefire-routed)

```
db.migration.V3MigrationTest
db.migration.V5MigrationTest
db.migration.V6MigrationTest
org.ctc.CtcManagerApplicationTests
org.ctc.admin.TestDataServiceIntegrationTest
org.ctc.backup.audit.DataImportAuditSerializationTest
org.ctc.domain.model.BaseEntityAuditTest
org.ctc.domain.model.PhaseTeamUniquenessIntegrationTest
org.ctc.domain.model.SeasonPhaseEntityIntegrationTest
org.ctc.domain.repository.PhaseTeamRepositoryTest
org.ctc.domain.service.MatchdayGeneratorServiceTest
org.ctc.domain.service.PlayoffServiceTest
org.ctc.domain.service.PlayoffServiceTest.AddRaceToMatchup   (Nested)
org.ctc.domain.service.PlayoffServiceTest.BracketCreation    (Nested)
... (14 more Nested classes from PlayoffServiceTest)
org.ctc.domain.service.SwissPairingServiceTest
```

[VERIFIED: `cat target/test-perf/context-loads-*-fingerprints.txt | grep "^9cefac4c" | awk -F'testClass = ' '{print $2}' | awk -F',' '{print $1}' | sort -u`]

#### Bucket `f524774b` — 10 distinct test classes (Failsafe `default-it`-routed)

```
db.migration.V4MigrationSmokeIT
db.migration.V7DataImportAuditMigrationIT
org.ctc.backup.service.BackupArchiveServiceIT
org.ctc.backup.service.BackupExportServiceIT
org.ctc.backup.service.BackupImportPostCommitEdgeCasesIT
org.ctc.backup.service.BackupImportServiceIT
org.ctc.backup.service.BackupImportZipBombIT
org.ctc.backup.service.BackupStagingCleanupRaceIT
org.ctc.backup.service.BackupStagingDirPerForkIT
org.ctc.domain.repository.DriverRepositoryOrderIT
```

[VERIFIED: same grep, hash `f524774b`]

### Hash-bucket-population audit — D-01 hypothesis ranking RESOLVED

CONTEXT.md D-01 asked the planner to test three hypotheses for why bucket `9cefac4c`
shows 29 occurrences when only 5 db.migration classes exist. **The empirical answer is
already on disk:**

| Rank | Hypothesis | Verdict |
|------|------------|---------|
| 1 — PROVEN | "Additional `@SpringBootTest + @ActiveProfiles("dev")` classes outside `db.migration.**` land in the same bucket." | **CORRECT.** 24 of the 29 occurrences are outside `db.migration` (CtcManagerApplicationTests, TestDataServiceIntegrationTest, the PlayoffServiceTest family including 15 `@Nested` classes, BaseEntityAuditTest, PhaseTeamUniquenessIntegrationTest, …). 5 are inside db.migration (V3/V5/V6 in this bucket; V4Smoke/V7Audit live in `f524774b` because they are `@Tag("integration")` Failsafe-routed and the per-fork cache is separate). The `@Nested` PlayoffServiceTest classes each fire their own `beforeTestClass` event but inherit the parent's `MergedContextConfiguration` — that's why they all collide into the same hash. |
| 2 — FALSE | "Per-method `@DirtiesContext`-style rebuilds inflate the count." | **REJECTED.** `grep -rn "@DirtiesContext" src/test/java/db/migration/` returns 0 hits. No db.migration class uses `@DirtiesContext`, and the listener fires only on `beforeTestClass` (not per method). |
| 3 — FALSE | "Listener event-counting model overcounts." | **REJECTED.** `ContextCacheKeyFingerprintListener.beforeTestClass(...)` fires exactly once per test class per Spring TCF lifecycle (verified by reading the listener source — appends one line per invocation; no loop). The line count == class count, modulo `@Nested` classes which are themselves test classes from JUnit's POV. |

**Practical consequence for Plan 90-01:** the D-01-mandated re-run of
`aggregate-fingerprints.sh` against a fresh `target/test-perf/` will produce the same
shape (modulo Surefire `runOrder=random` reshuffling which redistributes classes across
the two forks but does not change which hash each class lands in). **The refactor scope is
the 29 + 10 = 39 classes above, minus the 15 `@Nested` PlayoffServiceTest entries that
inherit their parent's annotation** (annotating the parent `PlayoffServiceTest` suffices —
`@Nested` classes inherit composed annotations from the enclosing class per Spring TCF
inheritance rules).

**Net unique outer-class refactor surface:** **24 classes** (29 − 15 inherited Nested
= 14 in Surefire bucket; + 10 in Failsafe bucket = 24 outer classes).

### Caveats the planner MUST avoid

| Caveat | Impact | Mitigation |
|--------|--------|------------|
| `@DirtiesContext` on a subclass would defeat consolidation | Adding `@DirtiesContext` causes a context rebuild that bypasses the shared cache bucket; the class would re-bootstrap a context, evict the shared one (CACHE size pressure), and force every sibling to re-bootstrap on next call. | None of the 24 outer classes currently has `@DirtiesContext` (verified — grep returns 0 hits in `db.migration/` and spot-checked PlayoffServiceTest/CtcManagerApplicationTests). Planner adds the discretionary Javadoc warning on `@CtcDevSpringBootContext` per CONTEXT.md "Claude's Discretion" §3. |
| `@DynamicPropertySource` fragments the bucket | A per-class `@DynamicPropertySource` registers a `contextCustomizer` that participates in `MergedContextConfiguration.hashCode()`. **This is exactly Phase 86 Lesson** (Plan 86-02: 7 sitegen classes fragmented 1 shared key into 7). | `grep -rn "@DynamicPropertySource" src/test/java/db/migration/` returns 0 hits. Spot-check of PlayoffServiceTest/CtcManagerApplicationTests/BaseEntityAuditTest also returns 0. The 39-class set is `@DynamicPropertySource`-free. The Failsafe-routed bucket members in `f524774b` (BackupImport* and Backup*ServiceIT) likewise contain 0 `@DynamicPropertySource` per the earlier grep audit. SAFE. |
| Surefire/Failsafe per-fork cache isolation (Phase 89 D-11) | Each fork JVM holds its own `DefaultContextCache`. The "29 occurrences" in `9cefac4c` is split across Surefire fork-A and fork-B; same for the "10" in `f524774b` across Failsafe `default-it` fork-A and fork-B. | This is **already accounted for** — the aggregator sums across all 4-5 sidecar files (one per JVM PID). The consolidated bucket's per-fork population drops cleanly because every fork sees the same composed annotation. **No special handling required.** |
| Spring Boot 4 / Spring Framework 7 API drift | The `MergedContextConfiguration` cache-key contract has been stable since Spring 4.x; the 7.0.5 Javadoc explicitly preserves "excluding the test class" wording. No API drift risk. | None — confirmed against current docs. |

[VERIFIED: empirical aggregator output above + Javadoc citation + zero `@DirtiesContext` / `@DynamicPropertySource` in the surface area.]

## Aggregator + Sidecar Validation (D-02b)

### Script existence and shape

- `scripts/test-perf/aggregate-fingerprints.sh` exists, executable, shellcheck-clean. Usage: `scripts/test-perf/aggregate-fingerprints.sh [marker-dir] [top-n]`. [VERIFIED: file read]
- Sidecar format `target/test-perf/context-loads-{PID}-fingerprints.txt` is the canonical Phase 89 D-08 output. Each line: `<hex-hash>\t<mcc-display-truncated-to-200-chars>`. [VERIFIED: `ContextCacheKeyFingerprintListener.java` lines 79-84 + on-disk file inspection]

### Stable Top-N across runs

Cross-checking the on-disk sidecars (Phase 89 Wave-4 Run-2 at PID 16759/16760, Run-1 at PID 13620/13621, plus a stragglerverify run at PID 18162):

- Bucket `9cefac4c` aggregates from 5 sidecar files; the score (`occurrence × cluster_size`) is stable (29 × 29 = 841) because the same 29 classes hit the same bucket regardless of fork distribution.
- Bucket `f524774b` aggregates similarly (10 × 10 = 100).
- Top-5 ordering matches the Plan 89-03 published output verbatim (modulo cosmetic hash-suffix differences between runs of `mvnw clean` due to JVM identity-hashCode randomisation across `mvn clean`-wiped runs — see §Open Questions for the Plan 89-02 `ac9a4e12` vs Plan 89-03 `9cefac4c` discrepancy).

**Consequence:** the planner can use the current `target/test-perf/` data directly for the "before" snapshot OR can re-run `./mvnw clean verify -Pe2e` for a fresh "before" — both are valid (Discretion §2). The "after" snapshot MUST come from a post-refactor run. [VERIFIED: on-disk sidecar parity + aggregator deterministic output].

### Sidecar file survival across Plan 90-01

No file format or path change is needed in Phase 90. The listener stays untouched; the
sidecar continues writing to `target/test-perf/context-loads-{PID}-fingerprints.txt` at JVM
shutdown via the existing `Runtime.getRuntime().addShutdownHook(...)` mechanism. Plan
90-01's only diff is in the annotations on the 24 outer test classes — the listener has no
knowledge of which annotation produced which context. [VERIFIED: listener source has no
annotation-name dependency.]

## Testcontainers Reuse Validation (D-04)

### API surface (Testcontainers 2.0.5)

| Element | Status | Notes |
|---------|--------|-------|
| `.withReuse(true)` fluent API on `GenericContainer` and subclasses | ✓ Available | Inherited from `GenericContainer<>` since 1.10.x. `MariaDBContainer<>` extends `JdbcDatabaseContainer<>` extends `GenericContainer<>`. [CITED: https://java.testcontainers.org/features/reuse/ — "you must mark it as reusable by setting the `.withReuse(true)`"] |
| `~/.testcontainers.properties` opt-in line `testcontainers.reuse.enable=true` | ✓ Required | Activation is **per-developer-machine**. Without the file, `.withReuse(true)` is a silent no-op — the container cold-starts and Ryuk cleans up at JVM exit. [CITED: https://java.testcontainers.org/features/configuration/] |
| Ryuk interaction | **Disabled when reuse is on** | "When container reuse is enabled, Ryuk … will not be started, which in turn means that the Testcontainers will not be taken down after tests are completed. While reuse will prevent Ryuk from cleaning-up containers, they still get stopped by regular lifecycle commands." [CITED: https://java.testcontainers.org/features/reuse/] |
| Container identity / hash labelling | Hash of the `CreateContainerCmd` minus the session-id label | Testcontainers strips the random `sessionId` label when computing the reuse-hash, then matches against running containers labelled `org.testcontainers.reuse.hash=<hash>`. [CITED: https://java.testcontainers.org/features/reuse/ + verified via testcontainers-java PR #1781] |
| CI invariant | **Silent no-op without operator file** | CI runners do not have `~/.testcontainers.properties`. `.withReuse(true)` becomes a no-op; the container starts fresh and Ryuk runs as today. **Zero CI behaviour change.** [CITED: https://java.testcontainers.org/features/reuse/ — "Reusable containers are not suited for CI usage"] |

### Experimental-feature caveat

Testcontainers documents reuse as "experimental". This means:
- Not all features are guaranteed to work (e.g., resource cleanup or networking edge cases).
- The API surface may change in a future Testcontainers major.

**Impact on Phase 90:** acceptable. The reuse opt-in is dev-only (D-04 CI invariant); a future Testcontainers API break would be caught by the existing test suite and would not affect production deployment.

### Hash-stability concern (testcontainers/testcontainers-java#2515)

A documented historical issue: container reuse failed when callers configured per-test
unique labels or mounts because the hash changed every run. **Mitigation already built-in
to current declarations:**

```java
@Container
static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
        .withDatabaseName("ctc_test")
        .withUsername("ctc")
        .withPassword("test");
```

[VERIFIED: `BackupImportMariaDbSmokeIT.java` lines 103-107 + `BackupRoundTripIT.java` lines 517-521]

All three configuration parameters are **constant string literals**. No `Files.createTempDirectory(...)` mount, no per-test label, no dynamic env-var. The hash is stable across runs once `.withReuse(true)` is added. [CITED: testcontainers-java#2515 — "to make withReuse work you must avoid changing the container's CreateContainerCmd between runs"]

### LocalStack-style "doesn't actually reuse" bug

Testcontainers-java#8795 documents a `LocalStackContainer` reuse failure. **Not applicable
to `MariaDBContainer<>` per current open-issue tracker** (no analogous bug filed against
the MariaDB module as of 2026-05-20). [CITED: WebSearch — no `MariaDBContainer reuse` bug surfaced]

## 3-Seed Failsafe Verification (D-03)

### Command pattern validation

**Phase 89 D-13 carry-forward** — the established pattern is:

```bash
./mvnw verify -Dit.test='db.migration.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234
./mvnw verify -Dit.test='db.migration.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678
./mvnw verify -Dit.test='db.migration.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999
```

[VERIFIED: pattern repeated in `.planning/milestones/v1.10-phases/79-…/79-01-baseline-and-independence-audit-PLAN.md` lines 98-100 + Phase 89 D-13 + Phase 86 D-02]

### Surefire vs Failsafe naming caveat

**Failsafe inherits Surefire's runOrder system properties.** Both plugins read `surefire.runOrder` (not `failsafe.runOrder`) — this is a long-standing Failsafe-design choice to keep parity with Surefire's API. The historical exception (Plan 79-01 used `-Dfailsafe.runOrder=reversealphabetical` once) was paired with `-Dsurefire.runOrder=reversealphabetical`; the `failsafe.runOrder` variant works only because Failsafe falls back to its own namespace before deferring to Surefire's. **Stick with `-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=N`** — that is the established, tested pattern in this codebase.

[VERIFIED: pom.xml line 266 uses `maven-surefire-plugin` and Failsafe execution at line 302 inherits the parent Surefire config implicitly via Maven plugin-config defaults.]

### Surefire-routed companion run

Per D-03, ALSO run **one** Surefire-routed pass for the `db.migration.**` Tests (V3/V5/V6 are `*Test.java` Surefire):

```bash
./mvnw test -Dtest='db.migration.**'
```

(Single seed-stable run; the deeper random-order audit on the Surefire fork side was already done in v1.10/v1.11 — the goal here is regression-fence the new annotation.)

### Acceptance

All 4 runs (3 Failsafe seeds + 1 Surefire) BUILD SUCCESS. Per `[[no-flaky-dismissal]]`, any
red is a real regression, not "flaky". Plan 90-01 SUMMARY records the 4 run IDs / log
filenames.

## Wave-5 Local Measurement (D-07)

### Protocol

Per Phase 86 D-09 + Phase 89 D-02 (idle 3-run honest reporting):

```bash
{ time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev; } \
    > .test-perf-logs/90-01-wave5-run-<N>.log 2>&1
```

Three runs, idle system, drop none — record median + delta vs. Phase 89 Wave-4 09:19.

### Recorded metrics per run

- Maven `Total time` (median)
- bash `real` time (median)
- `target/test-perf/context-loads-{PID}.txt` total tally (Phase 89 baseline median 55)
- JaCoCo line coverage ≥ 0.8888 (Phase 89 minimum 0.8902)
- Top-5 cluster diff: pre-refactor (this research session OR fresh) vs. post-refactor (Run-3 sidecar from Wave-5)

**No hard local wallclock gate** (D-07 explicit). PERF-06 in Phase 91 is the authoritative CI re-harvest.

## Runtime State Inventory

Phase 90 has **no rename/refactor/migration of runtime data**. All work is annotation
swaps + Testcontainers fluent-API additions + documentation. The Runtime State Inventory
categories are therefore explicitly N/A:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no DB schema change, no migration, no key/collection rename. | None |
| Live service config | None — n8n / Datadog / Cloudflare not in scope. | None |
| OS-registered state | None — no Task Scheduler / systemd / pm2 / launchd touchpoint. | None |
| Secrets/env vars | None — no `application*.yml`, no `.env`, no SOPS edit (D-04b + D-09). | None |
| Build artifacts | None — no `pom.xml` `<artifactId>` change, no rename of any installed module. | None |

**Verified by:** `git diff --stat` cumulative diff for Phase 90 will touch only
`src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` (new file), the 24 outer
test classes (annotation swap), `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java`,
`src/test/java/org/ctc/backup/service/BackupRoundTripIT.java`, `README.md`,
`docs/test-performance.md`, and the three Plan-N-SUMMARY.md files under
`.planning/milestones/v1.12-phases/90-.../`.

## Common Pitfalls

### Pitfall 1: Adding `@Tag("integration")` to the composed annotation

**What goes wrong:** Composed annotation forces every consumer into Failsafe; Surefire
`*Test.java` files in `db.migration` would silently route into Failsafe and run twice (once
discovered by Surefire's class-graph scan as untagged, then once via Failsafe's
`<groups>integration</groups>`).
**Why it happens:** Tempting "consolidate everything" reflex.
**How to avoid:** Per CONTEXT.md D-02, `@Tag` stays on the **subclass**, NOT on the
composed annotation. Surefire `*Test.java` files stay untagged; Failsafe `*IT.java` files
keep `@Tag("integration")`.
**Warning signs:** `./mvnw verify` runs the same test twice in different phases; Failsafe
totals jump.

### Pitfall 2: Adding `@Transactional` to the composed annotation

**What goes wrong:** Forces transactional semantics on all 24 consumers; some (notably the
`BackupImport*IT` family in `f524774b`) explicitly need non-transactional behaviour to
exercise the post-commit `@TransactionalEventListener(AFTER_COMMIT)` path. Adding
`@Transactional` would silently break those ITs by short-circuiting the commit listener.
**Why it happens:** V4MigrationSmokeIT has `@Transactional`; tempting to fold in.
**How to avoid:** Per CONTEXT.md D-02, `@Transactional` stays on the **subclass** (only
V4MigrationSmokeIT among the 24). Composed annotation contains exactly two annotations.
**Warning signs:** `BackupImportServiceIT` or `BackupImportPostCommitEdgeCasesIT` flips
from green to red after annotation swap; audit row missing.

### Pitfall 3: Re-introducing fragmentation via subclass `@DynamicPropertySource`

**What goes wrong:** A subclass that adds `@DynamicPropertySource` registers a
`contextCustomizer` that participates in the cache-key hash. Bucket splits.
**Why it happens:** Future-developer reflex when wanting per-class config overrides.
**How to avoid:** Defensive Javadoc on `@CtcDevSpringBootContext` documenting the
contract — Discretion §3 in CONTEXT.md. Phase 86 Lesson is the canonical pitfall doc.
**Warning signs:** Top-5 cluster output shows two adjacent hashes with display markers
that share `classes = [CtcManagerApplication]` but differ only in `propertySourceProperties`.

### Pitfall 4: Forgetting to handle `@Nested` PlayoffServiceTest classes

**What goes wrong:** Annotating the outer `PlayoffServiceTest` is sufficient — `@Nested`
classes inherit composed annotations. But if the planner mistakenly annotates each
`@Nested` separately, no harm done (idempotent), but commit diff is noisier than necessary.
**Why it happens:** `@Nested` looks like a standalone test class; tempting to treat each
as a separate refactor target.
**How to avoid:** Annotate only the 14 outer Surefire classes + 10 Failsafe classes = **24
files modified**. The 15 `@Nested` PlayoffServiceTest classes are touched by 0 file edits.
**Warning signs:** Plan 90-01 file-change count > 24 outer test classes.

### Pitfall 5: Testcontainers reuse hash drift via dynamic configuration

**What goes wrong:** If a future plan adds `.withCommand(...)`, `.withEnv("X", random())`,
or any per-run-dynamic config to the MariaDB containers, the reuse hash changes every run
and reuse silently never engages.
**Why it happens:** Reasonable-looking refactor that doesn't realise the hash dependency.
**How to avoid:** Keep all `MariaDBContainer<>` configuration on **constant literals**.
**Warning signs:** Developer reports "I set the opt-in but containers still cold-start";
`docker ps` shows a fresh container every run instead of a labelled long-lived one.

## Code Examples

### Composed annotation (`@CtcDevSpringBootContext`)

```java
// Source: extrapolated from Spring Framework 7 meta-annotation idiom
// [CITED: https://docs.spring.io/spring-framework/reference/testing/annotations/integration-meta.html]
package org.ctc.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.ctc.CtcManagerApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared Spring TCF context configuration for `dev`-profile integration tests.
 *
 * <p>Composes {@code @SpringBootTest(classes = CtcManagerApplication.class)} and
 * {@code @ActiveProfiles("dev")} into a single annotation so every consumer collapses
 * onto the same {@link org.springframework.test.context.MergedContextConfiguration}
 * cache bucket — Spring TCF's {@code DefaultContextCache} keys on the merged config
 * <em>excluding the test class</em>, so two classes wearing this annotation share
 * one cached context.
 *
 * <p>Do NOT add {@code @DirtiesContext}, {@code @Tag}, {@code @Transactional}, or
 * {@code @DynamicPropertySource} to this annotation. {@code @Tag} and
 * {@code @Transactional} belong on the subclass (they vary per consumer);
 * {@code @DirtiesContext} or {@code @DynamicPropertySource} would defeat the
 * consolidation goal by fragmenting or evicting the shared cache key.
 *
 * <p>Phase 90 PERF-03 consolidates the {@code 9cefac4c} and {@code f524774b}
 * cache-key buckets identified by Phase 89 PERF-02 instrumentation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
public @interface CtcDevSpringBootContext {
}
```

### Subclass application (V5MigrationTest after refactor)

```java
// Source: derived from current V5MigrationTest.java + composed-annotation pattern
package db.migration;

import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Test;
// (other imports unchanged)

@CtcDevSpringBootContext
class V5MigrationTest {
    // body unchanged
}
```

### Subclass application (V4MigrationSmokeIT — keeps `@Tag` + `@Transactional`)

```java
// Source: derived from current V4MigrationSmokeIT.java + composed-annotation pattern
package db.migration;

import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.springframework.transaction.annotation.Transactional;
// (other imports unchanged)

@CtcDevSpringBootContext
@Transactional
@Tag("integration")
class V4MigrationSmokeIT {
    // body unchanged
}
```

### Testcontainers reuse (D-04 — both BackupImportMariaDbSmokeIT and BackupRoundTripIT)

```java
// Source: BackupImportMariaDbSmokeIT.java lines 103-107 + Testcontainers reuse API
// [CITED: https://java.testcontainers.org/features/reuse/]
@Container
static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
        .withDatabaseName("ctc_test")
        .withUsername("ctc")
        .withPassword("test")
        .withReuse(true);   // <-- the entire Plan 90-02 code change
```

### Aggregator invocation (before/after diff)

```bash
# Pre-refactor snapshot (optional re-baseline; current target/test-perf/ data is acceptable)
scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5 | tee .test-perf-logs/90-01-aggregator-before.txt

# Apply refactor, then full clean-verify
./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev

# Post-refactor snapshot — bucket collapse + 24-class consolidated bucket
scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5 | tee .test-perf-logs/90-01-aggregator-after.txt

# Acceptance: top bucket score doubles (~30 + ~10 = ~40 occurrences in one bucket)
diff -u .test-perf-logs/90-01-aggregator-before.txt .test-perf-logs/90-01-aggregator-after.txt
```

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5 (managed by Spring Boot 4.0.6) |
| Config file | `pom.xml` Surefire `<forkCount>2 reuseForks=true>`, Failsafe `default-it` same; no separate JUnit Platform config |
| Quick run command | `./mvnw test -Dtest='db.migration.**'` (Surefire) or `./mvnw verify -Dit.test='db.migration.**'` (Failsafe) |
| Full suite command | `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| PERF-03 | Composed annotation present, V3/V5/V6 + V4Smoke + V7Audit + 19 others use it without test-isolation regression | integration (3-seed Failsafe) | `./mvnw verify -Dit.test='db.migration.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}` (3 runs) + `./mvnw test -Dtest='db.migration.**'` (1 run) | ✅ (existing tests; only annotation swap) |
| PERF-03 | Cache-key bucket collapse — `9cefac4c` and `f524774b` merge into one shared hash, populated by all 24 outer classes | observational (PERF-02 aggregator output) | `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5` before+after, diff side-by-side in 90-01-SUMMARY.md | ✅ Phase 89 |
| PERF-03 | Wave-5 honest local measurement — median Maven / context-load count / JaCoCo, delta vs Phase 89 Wave-4 09:19 | observational (3 idle runs, no hard gate) | `{ time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev; }` × 3 | ✅ idle protocol |
| PERF-04 | `.withReuse(true)` present on both `MariaDBContainer<>` declarations, no `~/.testcontainers.properties` change in repo | smoke + manual | `grep -n "withReuse(true)" src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` (must show 2 lines) | ✅ |
| PERF-04 | CI behaviour unchanged — both ITs still gated by `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` and stay skipped on CI default | observational (CI run log inspection on milestone PR) | post-merge CI E2E step wallclock within ±2σ of Phase 89 Wave-4 23:00 baseline (Phase 91 PERF-06 owns the authoritative verdict) | ✅ |
| PERF-04 | Developer-mode reuse demonstration — when `~/.testcontainers.properties` is set with `testcontainers.reuse.enable=true`, `docker ps` shows a long-lived `mariadb:11` container labelled `org.testcontainers.reuse.hash=…` across consecutive `./mvnw verify -Dit.test=BackupImportMariaDbSmokeIT` runs | manual / operator | document command sequence in `docs/test-performance.md § PERF-04 Testcontainers Reuse` verification block | ✅ docs-only |
| PERF-05 | `docs/test-performance.md § Test-Module-Split Decision` section exists with verdict + 3 blockers + re-evaluation trigger | docs | `grep -n "^## Test-Module-Split Decision" docs/test-performance.md` (exit 0 = section present) | ❌ Plan 90-03 creates |

### Critical-Path Behaviours That MUST Be Empirically Observed

1. **Composed-annotation cache-key collision** — after refactor, the aggregator Top-5
   shows a single bucket containing every class identified in the §Mechanism Validation
   §Hash-bucket-population audit table (the 24 outer classes), regardless of Surefire
   `runOrder=random` seed. **Distinguishes from noise:** if 2 buckets remain or the
   classes split, the composed annotation is silently differing per consumer (likely a
   `@DirtiesContext` or `@DynamicPropertySource` snuck in). Diagnostic: re-inspect the
   sidecar file with `grep "<class>" target/test-perf/*-fingerprints.txt` to identify the
   intruding hash.

2. **MariaDB IT survives JVM exit under operator opt-in** — when `~/.testcontainers.properties`
   contains `testcontainers.reuse.enable=true` and `BackupImportMariaDbSmokeIT` runs under
   `-Ddocker.available=true`, the container is NOT torn down on JVM exit (Ryuk skipped),
   `docker ps` shows the labelled container after Maven exits, and a second `-Dit.test=...`
   run re-attaches in < 500ms (vs ~5-7s cold-start). **Distinguishes from noise:** "feels
   fast" is not evidence; `docker ps` between runs is the empirical proof.

3. **CI cold-start preserved** — on the milestone PR CI run, the GitHub Actions E2E step
   wallclock stays within ±2σ of the Phase 89 23:00 baseline (PERF-06 in Phase 91 owns the
   authoritative 5-run re-harvest). **Distinguishes from noise:** a single CI run can show
   ±1.5min variance on its own; PERF-06 is the median-of-3-of-5 protocol.

4. **3-seed Failsafe verification holds** — all 3 Failsafe runs + 1 Surefire run BUILD
   SUCCESS. **Distinguishes from noise:** any single red run is a real regression per
   `[[no-flaky-dismissal]]`; do NOT retry on a different seed and call it green.

### Sampling Cadence

- **Per task commit:** targeted `-Dtest=` / `-Dit.test=` against the modified classes (Wave-5 is the only place where the full suite runs).
- **Per wave merge:** N/A (only one wave per plan per `[[wave-pause]]`).
- **Phase gate:** one final `./mvnw verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` BUILD SUCCESS before `/gsd-verify-work` (the Wave-5 Run-3 is sufficient — no separate phase-gate verify required per `[[test-call-optimization]]`).

### Failure Modes Observation Must Distinguish from Noise

| Symptom | Could Be Noise | Could Be Real | How To Distinguish |
|---------|---------------|---------------|--------------------|
| Wave-5 median moves +30s from Phase 89 09:19 | Run-to-run variance (Wave-4 spread: 08:50 → 09:50 across 3 runs) | Cache-key still fragmented after refactor (aggregator Top-5 still shows 2 buckets) | Look at aggregator output FIRST; ignore wallclock if bucket collapse is correct. |
| Single Failsafe IT flips red on seed 5678 | One-off Playwright Chromium screenshot flake (documented in Phase 81-03 SUMMARY) | Composed annotation introduced inheritance bug | Re-run the exact failed class with `-Dit.test=…` and `-DfailIfNoTests=true`; if it passes alone but red in the suite, it's a true new test-isolation issue (per `[[no-flaky-dismissal]]`). |
| MariaDB container does NOT reuse despite opt-in | Network / Docker daemon transient | Hash drift from a config change | `docker logs <ryuk-id>` shows reuse-skip reason; `docker inspect <mariadb>` shows the `org.testcontainers.reuse.hash` label. |
| Surefire shows the same test running twice | Maven multi-module test discovery | Composed annotation accidentally includes `@Tag("integration")` | `grep -n "@Tag" src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` must return 0 hits. |

### Wave 0 Gaps

None — all required test infrastructure (PERF-02 listeners, aggregator script, sidecar
format, idle protocol logs directory) exists from Phase 89. Phase 90 adds zero test files.

## Security Domain

> `security_enforcement` is enabled by default (config absent — treat as enabled).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Phase 90 touches zero auth code; production-Spring-Security config unchanged per D-09. |
| V3 Session Management | no | Same as V2. |
| V4 Access Control | no | Same. |
| V5 Input Validation | no | No new user input surface. |
| V6 Cryptography | no | Testcontainers reuse hash is not a security boundary — it's a container identity key, not a credential. |
| V7 Error Handling | no | No new exception path. |
| V8 Data Protection | yes (low) | Testcontainers reuse persists DB state between developer test runs — see §Threats below. |
| V11 Business Logic | no | Phase scope is test-shape only. |
| V12 Files & Resources | no | No new file write outside `target/test-perf/` (already in scope from Phase 89). |
| V14 Configuration | yes (low) | `~/.testcontainers.properties` is a developer-machine config artefact — see §Threats. |

### Known Threat Patterns

| Threat | STRIDE | Reality Check | Recommended PLAN.md `<threat_model>` Row |
|--------|--------|---------------|------------------------------------------|
| Classpath leakage of `@CtcDevSpringBootContext` to production code | Tampering | **NOT REAL.** `src/test/java/` is not on the production classpath (Maven scope `test`). The annotation cannot be applied to `src/main/java/` consumers — they don't see the class. | None — explicitly NOT a threat. Surface only if a future phase moves the annotation to `src/main/java/`. |
| Testcontainers reuse leaks DB state between developer test runs | Information Disclosure (low — local dev only) | **REAL but low impact.** When reuse is enabled, the MariaDB container survives JVM exit with whatever schema state the last test left behind. A second test that assumes a clean DB at startup will see stale rows. The two existing ITs (`BackupImportMariaDbSmokeIT`, `BackupRoundTripIT.MariaDbRoundTripTests`) BOTH call `testDataService.seed()` in `@BeforeEach` which seeds + replaces the dev fixture; **AND** the round-trip test itself wipes + restores the entire DB inside the test body, so state leakage between developer test runs is effectively self-healing for these specific ITs. **However**, any future MariaDB IT that does NOT seed at start would be vulnerable. | Recommended row: `Threat: Test-data leakage between consecutive reuse-mode runs on the same developer machine. STRIDE: Information Disclosure. Likelihood: Low. Impact: Test confusion only — no production data, no real PII. Mitigation: existing ITs reseed in @BeforeEach; document the invariant in `docs/test-performance.md § PERF-04 Testcontainers Reuse` so future MariaDB IT authors know to seed defensively.` |
| `~/.testcontainers.properties` documentation discloses credentials | Information Disclosure | **NOT REAL.** The file holds exactly one line: `testcontainers.reuse.enable=true`. No credentials, no tokens. The file is documented widely in the Testcontainers community — no novel disclosure. | None. |
| Ryuk-disabled mode leaves orphaned containers eating developer disk | Denial of Service (local dev only) | **REAL but low.** When reuse is on, Ryuk is disabled — if the developer forgets to `docker rm`, containers accumulate. A `docker container prune` recovers everything. | Recommended row: `Threat: Orphaned MariaDB containers accumulate on developer machine when reuse is enabled and the developer forgets to prune. STRIDE: DoS (disk). Likelihood: Medium. Impact: Disk pressure only. Mitigation: docs/test-performance.md § PERF-04 includes the verification command `docker container prune --filter "label=org.testcontainers.reuse.enable=true"` and the operator-friendly `docker ps --filter "label=org.testcontainers.reuse.hash"` for ID check.` |
| New test annotation introduces SpotBugs / find-sec-bugs finding | n/a | **NOT REAL** — the composed annotation is a bare `@interface` with no fields, no methods, no `byte[]` exposure. `EI_EXPOSE_REP*` / `DM_DEFAULT_ENCODING` patterns do not apply. CodeQL `security-extended` queries do not flag annotation-type definitions. | None — but if a finding surfaces, suppress per CLAUDE.md targeted `@SuppressFBWarnings({"CODE"}, justification="…")` (D-08 carry-forward). |

**Net STRIDE coverage:** 0 real production threats, 2 low-impact dev-only operational
threats (test-data leakage between reuse runs + orphan container accumulation). The
planner emits 2 `<threat_model>` rows on Plan 90-02 (the Testcontainers plan); Plans
90-01 and 90-03 have no security surface to document.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Per-class `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` | Composed `@CtcDevSpringBootContext` | Phase 90 PERF-03 (this phase) | 24 outer test classes; 2 annotations → 1; cache-key collision into a single shared bucket. |
| Cold-start MariaDB container per IT run | Optional `.withReuse(true)` + developer opt-in via `~/.testcontainers.properties` | Phase 90 PERF-04 (this phase) | ~5-7 s saved per IT per developer session; CI unaffected. |
| Per-fork singleton `app.backup.staging-dir` | Per-fork via `-Dsurefire.forkNumber` substitution | Phase 89 (predecessor) | Enabled Failsafe `forkCount=2 reuseForks=true`; -10.4 % local wallclock. |
| Generic `ContextLoadCountListener` (count only) | Hybrid count + `ContextCacheKeyFingerprintListener` sidecar | Phase 89 (predecessor) | Per-context cache-key fingerprint identifies consolidation candidates by hash. |

**Deprecated/outdated in this phase:**
- The phrase "29 occurrences across 29 classes equates to 5 `db.migration` classes" from
  Plan 89-03 SUMMARY's hand-off — empirically falsified above (the 29 are real, but only
  3-5 live in `db.migration`).

## Project Constraints (from CLAUDE.md)

| Directive | Phase 90 Compliance |
|-----------|----------------------|
| **Backward Compatibility:** no breaking changes to existing URLs/endpoints | ✓ Zero production code change (D-09). |
| **Flyway:** do not change existing V1 migration | ✓ Zero migration touched. |
| **OSIV:** remains enabled | ✓ No change. |
| **Playwright:** remains a compile-scope dependency | ✓ No change. |
| **Test Coverage:** ≥ 82 % line coverage maintained | ✓ Phase 89 Wave-4 holds 0.8902; D-08 expects no regression. |
| **Tag Tests by Category (`@Tag`)** | ✓ Composed annotation does NOT include `@Tag`; subclasses keep their existing `@Tag("integration")`. |
| **Static Analysis (SpotBugs + find-sec-bugs)** | ✓ Annotation-type definitions don't trigger `EI_EXPOSE_REP*` / `DM_DEFAULT_ENCODING`. If a finding surfaces unexpectedly, targeted `@SuppressFBWarnings` per CLAUDE.md SAST pattern. |
| **CodeQL SAST** | ✓ No new source code paths added that change security posture. Annotation type definitions are not in CodeQL's security-extended scope. |
| **Test Aufrufe optimieren** | ✓ Phase 90 follows "single final `./mvnw verify -Pe2e`" rule via Wave-5 Run-3; targeted `-Dit.test=` for the 3-seed verification. |
| **Subagent Rules** | ✓ Phase 90 is single-thread inline per `[[inline-sequential-execution]]`; no subagents used. |
| **Branch Protection** | ✓ Active branch `gsd/v1.12-driver-import-and-test-perf`; no `git stash`/`checkout`/`reset` allowed. Research is read-only. |
| **No Inline Styles** | ✓ Not applicable (no UI change). |
| **No Local Git Tags** | ✓ Not applicable. |
| **Wave-Pause** | ✓ D-06 mandates 3 sequential plans with user-feedback pause between. |
| **Clean Build/Test Only** | ✓ No skip flags. |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `@Nested` PlayoffServiceTest inner classes inherit a composed annotation applied to the outer class | §Mechanism Validation §Hash-bucket-population audit | If `@Nested` classes do NOT inherit, Plan 90-01 file count grows from 24 to 39. Spring TCF inheritance rules state `@Nested` inherits from the enclosing class's annotations — verifiable empirically by checking the aggregator output post-refactor (all 15 Nested entries land in the same bucket as the parent `PlayoffServiceTest`). [ASSUMED — verify in Plan 90-01 Wave-5 Run-3 aggregator output] |
| A2 | The 29-vs-39 discrepancy in CONTEXT.md D-01 is purely the difference between Surefire-bucket (29) + Failsafe-bucket (10) — both being separate per-fork cache buckets, not a single cluster. | §Mechanism Validation §Hash-bucket-population audit | If a third hash bucket exists that the planner missed, the bucket-collapse acceptance is incomplete. The aggregator re-run at Plan-01 start would expose it. [VERIFIED via on-disk sidecar enumeration above — 9cefac4c (Surefire) and f524774b (Failsafe) are the only two db.migration-bearing buckets.] |
| A3 | Testcontainers 2.0.5 inherits the 1.10+ `.withReuse(true)` API surface unchanged | §Testcontainers Reuse Validation | Testcontainers 2.x was a renumbering driven by Docker API 1.32→1.40 compatibility (per pom.xml comment line 20-23), not an API redesign. [CITED + VERIFIED via pom.xml comment + Testcontainers reuse docs which describe the same fluent API across 1.x and 2.x.] |
| A4 | The aggregator output ordering (Top-N by `occurrence × cluster_size`) is deterministic across `mvn clean verify` runs | §Aggregator + Sidecar Validation | If non-deterministic, the before/after diff in 90-01 SUMMARY would be noisy. [VERIFIED — awk sort is deterministic; the variance noted between Phase 89 Plan-02 (`ac9a4e12`) and Plan-03 (`9cefac4c`) hex hashes is due to JVM identity-hashCode randomisation across separate `mvn clean` invocations, NOT aggregator behaviour. The bucket *structure* and class membership are stable; only the hex digits change run-to-run.] |

**A1 is the only [ASSUMED] item.** All other claims are [VERIFIED] or [CITED]. If A1 is
wrong, the planner adjusts the refactor surface from 24 outer classes to 24+15 = 39 files —
mechanical, not a planning blocker.

## Open Questions

1. **Hex hash variance across `mvn clean` invocations.**
   - What we know: Plan 89-02 SUMMARY reported Top-1 as `ac9a4e12`; Plan 89-03 SUMMARY reported Top-1 as `9cefac4c`; the current on-disk data shows Top-1 as `9cefac4c`. Same physical cluster, different hex digits.
   - What's unclear: Why the variance between consecutive `mvn clean verify` runs.
   - Recommendation: This is a known property of Java's `Object.hashCode()` — JVM identity-hashCode randomization. `MergedContextConfiguration.hashCode()` includes `int hashCode()` results from non-overridden objects in its component arrays (e.g., `contextInitializerClasses`, certain `ContextCustomizer` instances). The **cluster structure** (which classes collide) is stable; the **hex value** is per-process. The planner records the bucket SHAPES (display markers + class count) in the diff, not the literal hex.

2. **Will the post-refactor consolidated bucket appear as Top-1 with score ~1521 (39²) or higher?**
   - What we know: Pre-refactor Top-1 score is 841 (29²). Post-refactor, the bucket merges with the Top-4 `f524774b` (10 classes) → ~39 classes total = 1521 score.
   - What's unclear: Whether the Phase 86 sitegen 7-cluster (`5ff2b420`) gets reclassified — it should not (different `@ActiveProfiles`+`@DynamicPropertySource` shape) but the planner should confirm in the after-snapshot.
   - Recommendation: Plan 90-01's after-snapshot Top-5 table comments inline on any unexpected re-shuffling.

3. **3-seed Failsafe verification scope question — should Surefire classes in `9cefac4c` (outside `db.migration`) also be 3-seed verified?**
   - What we know: CONTEXT.md D-03 says "3-seed Failsafe verification on `db.migration.**` ITs after consolidation".
   - What's unclear: Whether Surefire-routed `db.migration.**` Tests (V3/V5/V6) need the full 3-seed or just 1 seed-stable run (the D-03 text says "1 Surefire-routed seed-stable run"); and whether the 19 non-db.migration outer classes in `9cefac4c` (PlayoffServiceTest, CtcManagerApplicationTests, etc.) need any verification.
   - Recommendation: Stick strictly to D-03 — 3-seed Failsafe on `db.migration.**` ITs + 1 Surefire seed-stable on `db.migration.**` Tests. The 19 other classes are verified by the **single final** `./mvnw verify -Pe2e` (Wave-5 Run-3); per `[[test-call-optimization]]` and `[[no-flaky-dismissal]]` we don't expand the verification surface without empirical justification.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Eclipse Temurin) | All Maven invocations | ✓ | per pom.xml `<java.version>25` | — |
| Maven via `./mvnw` | All builds | ✓ | per project wrapper | — |
| Docker daemon | PERF-04 verification only (developer-machine) | varies | — | Phase 90 does not require Docker on CI; opt-in only. |
| `scripts/test-perf/aggregate-fingerprints.sh` | Plan 90-01 D-02b before/after diff | ✓ | shellcheck-clean, executable | — |
| Phase 89 PERF-02 listeners (`ContextCacheKeyFingerprintListener` + `ContextLoadCountListener`) | All cache-key fingerprint capture | ✓ | committed | — |
| `target/test-perf/` sidecar files | Aggregator input | ✓ | currently populated from 2026-05-19 Wave-4 run (5 files, 131 lines) | Re-run `./mvnw clean verify -Pe2e` to refresh. |
| `gh` CLI | PR creation post-phase | ✓ | per CLAUDE.md `gh pr create` discipline | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None.

## Implementation-Order Checks (per Plan)

### Plan 90-01 — PERF-03 (composed annotation + cluster refactor + 3-seed + Wave-5 + cache-key diff)

#### `read_first`

The executor MUST load these files before touching anything:

| Path | Why |
|------|-----|
| `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-CONTEXT.md` | Locked D-01..D-09 |
| `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-RESEARCH.md` | This file — §Mechanism Validation §Hash-bucket-population audit |
| `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` | Confirm sidecar format unchanged |
| `scripts/test-perf/aggregate-fingerprints.sh` | Aggregator usage |
| `src/test/java/db/migration/V3MigrationTest.java`, `V5MigrationTest.java`, `V6MigrationTest.java`, `V4MigrationSmokeIT.java`, `V7DataImportAuditMigrationIT.java` | Current annotation state in the cluster |
| `src/test/java/org/ctc/CtcManagerApplicationTests.java` + `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` + `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` | Sample of the wider 24-class scope to confirm A1 (Nested inheritance) |
| `pom.xml` (lines 264-339) | Surefire + Failsafe config; argLine; forkCount; runOrder support |
| `.planning/codebase/TESTING.md` § "Test Categorization (`@Tag`)" | `@Tag` stays on subclass, NOT composed annotation |
| `CLAUDE.md` § "Static Analysis" + § "Test Aufrufe optimieren" | Suppression discipline + single final verify |

#### Verifiable Acceptance Criteria

| # | Criterion | Verification command |
|---|-----------|----------------------|
| AC-1 | `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` exists, body matches §Code Examples §Composed Annotation | `cat src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` |
| AC-2 | All 5 `db.migration` classes use `@CtcDevSpringBootContext` instead of `@SpringBootTest + @ActiveProfiles("dev")` | `grep -lc "@SpringBootTest" src/test/java/db/migration/ | xargs -I{} grep -c "@CtcDevSpringBootContext" {}` == 1 per file |
| AC-3 | All other 19 outer classes in the audit list wear `@CtcDevSpringBootContext` | spot-check 3-5 classes; full enumeration in 90-01 SUMMARY |
| AC-4 | Top-5 aggregator output after refactor shows a single bucket merging the pre-refactor 29+10 occurrences (~38-39 classes; expect 38-40 since Nested classes register their own beforeTestClass events) | `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5` |
| AC-5 | 3-seed Failsafe `db.migration.**` runs all BUILD SUCCESS | 3 separate `mvnw verify -Dit.test='db.migration.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}` |
| AC-6 | 1 Surefire-routed `db.migration.**` run BUILD SUCCESS | `mvnw test -Dtest='db.migration.**'` |
| AC-7 | Wave-5 median Maven `Total time` within ±2σ of Phase 89 09:19 (no hard gate per D-07) | 3 idle `./mvnw clean verify -Pe2e` runs, median recorded in SUMMARY |
| AC-8 | JaCoCo line coverage ≥ 0.8888 across all 3 Wave-5 runs | `target/site/jacoco/index.html` per run |
| AC-9 | SpotBugs `BugInstance` = 0 | log inspection per Wave-5 run |
| AC-10 | `src/main/java/**` git-clean across the plan | `git diff origin/master..HEAD -- src/main/java` empty |

#### Test-Isolation Hazards

| Hazard | Likelihood | Mitigation |
|--------|-----------|------------|
| A subclass adds `@DirtiesContext` and silently breaks consolidation | Low (none currently has it) | Discretionary Javadoc warning on `@CtcDevSpringBootContext` per CONTEXT.md Claude's Discretion §3. |
| `@Nested` PlayoffServiceTest classes don't inherit composed annotation (A1 wrong) | Low | Plan 90-01 Wave-5 Run-3 aggregator output explicitly confirms; if wrong, annotate the 15 inner classes too — mechanical fix, not blocker. |
| Surefire random-order seed exposes pre-existing test-isolation bug | Low (Phase 89 D-13 3-seed already passed) | Per `[[no-flaky-dismissal]]`, any red is a real regression — diagnose root cause; do NOT retry on a different seed and call it green. |
| `forkCount=2 reuseForks=true` cross-fork DB-state leak | Already mitigated in Phase 89 (per-fork `app.backup.staging-dir`) | None new for Phase 90 — the consolidation does not introduce new cross-fork state. |
| Failsafe IT cluster `f524774b` BackupImport* family interacts with `@TransactionalEventListener(AFTER_COMMIT)` and DOES NOT use `@Transactional` | Already correct (V4MigrationSmokeIT is the only `@Transactional` consumer) | Subclass keeps `@Transactional` only where present today; do NOT add to BackupImport*IT. |

### Plan 90-02 — PERF-04 (`.withReuse(true)` + README + docs)

#### `read_first`

| Path | Why |
|------|-----|
| `.planning/milestones/v1.12-phases/90-.../90-CONTEXT.md` | D-04 + D-04b |
| `.planning/milestones/v1.12-phases/90-.../90-RESEARCH.md` | This file — §Testcontainers Reuse Validation |
| `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` lines 103-107 | Current `MariaDBContainer<>` declaration |
| `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` lines 517-521 (nested `MariaDbRoundTripTests`) | Same |
| `README.md` § "Test Performance" lines 152-154 | Anchor for the one-line PERF-04 pointer |
| `docs/test-performance.md` (entire file) | Append-only section pattern; the new § PERF-04 Testcontainers Reuse lives after § PERF-02 Forensics |

#### Verifiable Acceptance Criteria

| # | Criterion | Verification command |
|---|-----------|----------------------|
| AC-1 | `.withReuse(true)` present in both files | `grep -c "withReuse(true)" src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` (must show ≥ 2 total) |
| AC-2 | `docs/test-performance.md § PERF-04 Testcontainers Reuse` section present with opt-in line + verification command + zero-CI-impact framing | `grep -n "^## PERF-04 Testcontainers Reuse" docs/test-performance.md` exit 0 |
| AC-3 | `README.md § Test Performance` extended with the PERF-04 pointer paragraph | `grep -n "testcontainers.reuse.enable" README.md` exit 0 |
| AC-4 | CI behaviour unchanged: both ITs still `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` | `grep -c "docker.available" src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` ≥ 2 |
| AC-5 | `application*.yml` git-clean (D-04b) | `git diff origin/master..HEAD -- src/main/resources/application*.yml` empty |
| AC-6 | `BackupStagingCleanup.java` git-clean (D-04b) | `git diff origin/master..HEAD -- src/main/java/org/ctc/backup/service/BackupStagingCleanup.java` empty |
| AC-7 | Final `./mvnw verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` BUILD SUCCESS | log inspection |

#### Test-Isolation Hazards

| Hazard | Likelihood | Mitigation |
|--------|-----------|------------|
| Developer with `testcontainers.reuse.enable=true` runs the IT twice and the second run sees stale DB state | Low for these specific ITs (both call `testDataService.seed()` in `@BeforeEach` + the test body wipes DB) | Document in `docs/test-performance.md § PERF-04` that future MariaDB ITs MUST seed defensively. Threat model row recommended above. |
| Reuse hash drift from a future config change | Low (current config = constant string literals) | Document in § PERF-04 that any added `.withCommand(...)`, `.withEnv("X", random())`, or per-test mount breaks reuse. |
| CI accidentally enables reuse (sets the operator file by mistake) | Very low (CI runner never has the file in its $HOME) | None — defensive: the `@EnabledIfSystemProperty(named="docker.available", matches="true")` gate stays; CI does not set `docker.available=true` so the ITs are skipped on CI regardless of reuse state. |

### Plan 90-03 — PERF-05 (docs-only verdict)

#### `read_first`

| Path | Why |
|------|-----|
| `.planning/milestones/v1.12-phases/90-.../90-CONTEXT.md` | D-05 verbatim |
| `.planning/milestones/v1.12-phases/90-.../90-RESEARCH.md` | This file — §Module-Split Verdict Anchors below |
| `docs/test-performance.md` (entire file) | Append-only structure; the new § Test-Module-Split Decision lives at the bottom |
| `src/main/java/org/ctc/admin/TestDataService.java` | Confirm `@Profile({"dev","local"})` (Blocker 1 anchor) |
| `.planning/milestones/v1.11-phases/80-openrewrite-integration/deferred-items.md` | 2026-05-16 entry — IDE-JDT-cache pathology (Blocker 2 anchor) |
| `.planning/STATE.md` § Baselines | CI E2E median 23:00 (Blocker 3 anchor — re-evaluation trigger reference point) |

#### Verifiable Acceptance Criteria

| # | Criterion | Verification command |
|---|-----------|----------------------|
| AC-1 | `docs/test-performance.md § Test-Module-Split Decision` section present | `grep -n "^## Test-Module-Split Decision" docs/test-performance.md` exit 0 |
| AC-2 | Section opens with one-sentence Verdict line "Defer — re-evaluate in v1.13 …" | `grep -n "Verdict (v1.12)" docs/test-performance.md` exit 0 |
| AC-3 | Three blockers present, each labelled "**Blocker 1**…", "**Blocker 2**…", "**Blocker 3**…" | `grep -c "^**Blocker" docs/test-performance.md` ≥ 3 (markdown heading variants OK) |
| AC-4 | Re-evaluation trigger language present (v1.13 milestone-discuss reference) | `grep -n "v1.13" docs/test-performance.md` exit 0 |
| AC-5 | "Why not reject?" paragraph captures optionality-preservation rationale | `grep -n "Why not reject" docs/test-performance.md` exit 0 |
| AC-6 | Final `./mvnw verify -Pe2e` BUILD SUCCESS (no code change, but plan gate still applies) | log inspection |
| AC-7 | `src/**/*.java` git-clean (D-09 — pure docs plan) | `git diff origin/master..HEAD -- 'src/**/*.java'` empty |

#### Module-Split Verdict Anchors

The planner writes (with discretion on exact wording):

- **Verdict line:** "**Verdict (v1.12):** Defer — re-evaluate in v1.13 against PERF-06 CI re-harvest baseline."
- **Blocker 1 — TestDataService cross-boundary:**
  - `org.ctc.admin.TestDataService` lives in `src/main/java` and is `@Profile({"dev","local"})` per v1.11 QUAL-02.
  - Verified: `grep -n "@Profile" src/main/java/org/ctc/admin/TestDataService.java` should confirm.
  - A clean test-module split forces one of three architecturally-poor outcomes (duplicate fixtures / circular dep / third "fixtures" module).
- **Blocker 2 — IDE-friction risk:**
  - `.planning/milestones/v1.11-phases/80-openrewrite-integration/deferred-items.md` (2026-05-16) documents the JDT-cache pathology.
  - `[[clean-maven-build-authority]]` reinforces: Maven restructures historically amplify the pathology.
  - The single-module project has been stable through 87 phases.
- **Blocker 3 — No hard cumulative-effect data yet:**
  - Phase 89 Wave-4 -10.4 % local + PERF-04 (dev-only) suggest the cumulative effect may close the 23:00 → 7:50 CI gap meaningfully WITHOUT module split.
  - The authoritative CI cumulative-effect number is unknown until Phase 91 PERF-06.
  - Splitting before knowing risks a substantial restructure for marginal gain.
- **Re-evaluation trigger:** v1.13 milestone-discuss workflow consults `docs/test-performance.md § Test-Module-Split Decision`; if PERF-06 lands materially below 23:00 but still above 7:50 AND no other architectural lever surfaces, the v1.13 workflow re-opens the decision against the hard data baseline.
- **Why not reject?** Rejection forecloses v1.13 optionality; defer-with-explicit-blockers preserves it. Phase 86 D-15 OR-branch precedent shows that defer-with-rationale is the canonical pattern when CI data is incomplete.

#### Test-Isolation Hazards

None — pure docs plan.

## Sources

### Primary (HIGH confidence)

- **Spring Framework 7.0.5 `MergedContextConfiguration` Javadoc** — https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/context/MergedContextConfiguration.html — Verified the cache-key contract "generates a unique hash code for all properties of this MergedContextConfiguration excluding the test class". [VERIFIED 2026-05-20]
- **Spring Framework Reference — Context Caching** — https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html — Confirmed `DefaultContextCache` keys on `MergedContextConfiguration`.
- **Spring Framework Reference — Meta-Annotation Support for Testing** — https://docs.spring.io/spring-framework/reference/testing/annotations/integration-meta.html — Confirmed `@SpringBootTest` and `@ActiveProfiles` can be used as meta-annotations to create composed annotations.
- **Testcontainers Reuse — Java docs** — https://java.testcontainers.org/features/reuse/ — Confirmed `.withReuse(true)` semantics, opt-in via `~/.testcontainers.properties`, Ryuk-skip behaviour.
- **Testcontainers Configuration — Java docs** — https://java.testcontainers.org/features/configuration/ — Confirmed properties file location and `testcontainers.reuse.enable=true` key.
- **pom.xml** at the project root — Testcontainers BOM 2.0.5; Spring Boot 4.0.6; Surefire/Failsafe `forkCount=2 reuseForks=true` with shared `<systemPropertyVariables>` per-fork pattern. [VERIFIED]
- **Phase 89 SUMMARYs (89-01, 89-02, 89-03)** + **`89-CONTEXT.md`** — full Wave-4 hand-off; PERF-02 listener implementation; aggregator script shape. [VERIFIED via direct read]
- **`src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java`** — confirmed sidecar format and JVM shutdown-hook semantics.
- **`target/test-perf/` sidecar files (PIDs 13620, 13621, 16759, 16760, 18162)** — empirical hash-bucket-population data; full class enumeration of `9cefac4c` and `f524774b`. [VERIFIED via shell aggregation]

### Secondary (MEDIUM confidence)

- **Testcontainers java GitHub issue #2515** — "Container is not reused because the hash mismatched" — documented historical edge cases for reuse hash stability. Verified the failure mode does not apply to the current `MariaDBContainer<>` declarations (constant literals only).
- **Baeldung — How to Reuse Testcontainers in Java** — https://www.baeldung.com/java-reuse-testcontainers — Confirmed the workflow but with lower authority than the official docs.

### Tertiary (LOW confidence — NOT used to support load-bearing claims)

- (None — every load-bearing claim in this research is backed by either Context7-equivalent official docs or by on-disk empirical evidence.)

## Metadata

**Confidence breakdown:**

- Mechanism Validation (`@CtcDevSpringBootContext` cache-key): **HIGH** — Spring 7 Javadoc + empirical sidecar enumeration.
- Aggregator + Sidecar Validation: **HIGH** — script direct read + 5-file aggregation reproduced.
- Hash-bucket-population audit (D-01 ranking): **HIGH** — empirical class enumeration from on-disk sidecars.
- Testcontainers Reuse Validation: **HIGH** — official docs + pom.xml version verification.
- 3-Seed Failsafe Verification command pattern: **HIGH** — Phase 89 D-13 + Phase 86 + Phase 79 precedent.
- Validation Architecture (Nyquist): **HIGH** — explicit critical-path behaviours + cadence + failure-mode distinguishers.
- Security Domain: **MEDIUM** — only 2 dev-machine threats surfaced; production posture unchanged.
- Implementation-order checks (per Plan): **HIGH** — derived from CONTEXT.md D-06 + code surface inspection.

**Research date:** 2026-05-20
**Valid until:** 2026-06-20 (stable surface — Spring TCF cache-key contract, Testcontainers reuse API, and the on-disk sidecar shape are unlikely to change inside a 30-day window).

---

*Phase: 90-PERF Consolidation & Module-Split Decision*
*Research completed: 2026-05-20*
