# Phase 90: PERF Consolidation & Module-Split Decision — Pattern Map

**Mapped:** 2026-05-20
**Files analyzed:** ~30 (1 new test infrastructure, ~24 refactor targets, 2 Testcontainers tweaks, 5 docs/summary surfaces)
**Analogs found:** all surfaces map onto Phase-89 / Phase-86 precedents already in the tree
**Branch:** `gsd/v1.12-driver-import-and-test-perf` (DO NOT switch — read-only mapping work)

> Re-run mandate: per RESEARCH.md §Hash-bucket-population audit + CONTEXT.md D-01,
> the planner MUST re-run `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 10`
> against a fresh `target/test-perf/` at Plan 90-01 start and treat the resulting class
> enumeration of buckets `9cefac4c` and `f524774b` as the authoritative refactor surface.
> The table below lists the audit-time membership at 2026-05-20 (RESEARCH.md lines 168-206)
> as the planning-time best estimate — the planner re-verifies before refactor.

---

## File Classification

| New / Modified File | Role | Data Flow / Consumer | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` | new — composed-annotation meta-type | Spring TCF (`MergedAnnotations` → `MergedContextConfiguration` cache-bucket key) | `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` (testsupport package layout); RESEARCH.md §Code Examples lines 453-493 (literal shape) | exact (testsupport package + Spring-TCF idiom) |
| `src/test/java/db/migration/V3MigrationTest.java` | refactor — annotation swap | Surefire (`*Test.java`, untagged → fork-A/B) | self + `V6MigrationTest.java` (same shape today) | exact |
| `src/test/java/db/migration/V5MigrationTest.java` | refactor — annotation swap | Surefire — Top-1 bucket holder per 89-03 hand-off | self | exact |
| `src/test/java/db/migration/V6MigrationTest.java` | refactor — annotation swap | Surefire | self | exact |
| `src/test/java/db/migration/V4MigrationSmokeIT.java` | refactor — annotation swap; preserve `@Tag("integration")` + `@Transactional` on class | Failsafe `default-it` (fork-A/B per Phase 89 D-11) | self (current shape lines 38-42) | exact |
| `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` | refactor — annotation swap; preserve `@Tag("integration")` (NO `@Transactional`) | Failsafe `default-it` | self (current shape lines 30-33) | exact |
| `src/test/java/org/ctc/CtcManagerApplicationTests.java` | refactor — annotation swap (in `9cefac4c` Surefire bucket per RESEARCH.md line 174) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` | refactor — annotation swap (bucket `9cefac4c`) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/backup/audit/DataImportAuditSerializationTest.java` | refactor — annotation swap (bucket `9cefac4c`) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/domain/model/BaseEntityAuditTest.java` | refactor — annotation swap (bucket `9cefac4c`) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java` | refactor — annotation swap (bucket `9cefac4c`) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` | refactor — annotation swap (bucket `9cefac4c`) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryTest.java` | refactor — annotation swap (bucket `9cefac4c`) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/domain/service/MatchdayGeneratorServiceTest.java` | refactor — annotation swap (bucket `9cefac4c`) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` | refactor — annotation swap on the OUTER class only; `@Nested` inner classes inherit per Spring TCF + JUnit (RESEARCH.md line 225-227) | Surefire | `V5MigrationTest.java`; `@Nested` inheritance rule cross-referenced to `.planning/codebase/TESTING.md` §`@Nested` Inheritance | role-match (annotate parent only) |
| `src/test/java/org/ctc/domain/service/SwissPairingServiceTest.java` | refactor — annotation swap (bucket `9cefac4c`) | Surefire | `V5MigrationTest.java` | role-match |
| `src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java` | refactor — annotation swap; preserve `@Tag("integration")` + any class-level test annotations (bucket `f524774b`) | Failsafe `default-it` | `V4MigrationSmokeIT.java` (sans `@Transactional`) | role-match |
| `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java` | refactor — annotation swap; preserve `@Tag("integration")` (bucket `f524774b`) | Failsafe `default-it` | `V4MigrationSmokeIT.java` | role-match |
| `src/test/java/org/ctc/backup/service/BackupImportPostCommitEdgeCasesIT.java` | refactor — annotation swap; preserve `@Tag("integration")` (bucket `f524774b`) | Failsafe `default-it` | `V4MigrationSmokeIT.java` | role-match |
| `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` | refactor — annotation swap; preserve `@Tag("integration")` + `@TestInstance(PER_CLASS)` (bucket `f524774b`) | Failsafe `default-it` | `V4MigrationSmokeIT.java` | role-match |
| `src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java` | refactor — annotation swap; preserve `@Tag("integration")` (bucket `f524774b`) | Failsafe `default-it` | `V4MigrationSmokeIT.java` | role-match |
| `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` | refactor — annotation swap; preserve `@Tag("integration")` (bucket `f524774b`) | Failsafe `default-it` | `V4MigrationSmokeIT.java` | role-match |
| `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` | refactor — annotation swap; preserve `@Tag("integration")` + `@TestInstance(PER_CLASS)` (bucket `f524774b`) | Failsafe `default-it` | `V4MigrationSmokeIT.java` | role-match |
| `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` | refactor — annotation swap; preserve `@Tag("integration")` (bucket `f524774b`) | Failsafe `default-it` | `V4MigrationSmokeIT.java` | role-match |
| `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` | refactor — add `.withReuse(true)` to single static `MariaDBContainer<>` field at line 104 | Testcontainers (developer-machine opt-in via `~/.testcontainers.properties`) | self + RESEARCH.md §Code Examples lines 530-541 | exact |
| `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` | refactor — add `.withReuse(true)` to nested-class static `MariaDBContainer<>` field at line 518 (inside `MariaDbRoundTripTests`) | Testcontainers | `BackupImportMariaDbSmokeIT.java` (same `.withDatabaseName/.withUsername/.withPassword` chain shape) | exact |
| `docs/test-performance.md` | docs — append three new `## ...` sections | reader-facing forensics doc (existing `## PERF-02 Forensics`, `## Post-Optimization Wallclock (Wave 4)`, `## CI Results (PERF-05)` precedent) | self — append-only structure per Phase 86 / 89 precedent | exact |
| `README.md` § Test Performance | docs — extend the existing pointer paragraph with one PERF-04 opt-in sentence | reader-facing index → `docs/test-performance.md` | self (lines 151-154; Phase 89 Plan 89-03 added the pointer) | exact |
| `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-01-SUMMARY.md` | new — phase summary | GSD orchestrator + planning audit | `89-01-SUMMARY.md` | exact |
| `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-02-SUMMARY.md` | new — phase summary | GSD orchestrator | `89-02-SUMMARY.md` | exact |
| `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-03-SUMMARY.md` | new — phase summary | GSD orchestrator | `89-03-SUMMARY.md` | exact |

> The 24 refactor targets above match RESEARCH.md §Hash-bucket-population audit's
> "Net unique outer-class refactor surface: 24 classes (14 Surefire + 10 Failsafe)"
> figure (RESEARCH.md line 229-230). The 15 `@Nested` `PlayoffServiceTest` entries
> collapse into the single outer-class annotation on `PlayoffServiceTest.java` per
> Spring-TCF + JUnit inheritance rules (RESEARCH.md Pitfall 4 lines 430-440).

---

## Pattern Assignments

### Plan 90-01 — PERF-03 cluster consolidation

---

#### `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` (NEW, composed annotation)

**Role:** test infrastructure — meta-annotation that composes `@SpringBootTest` + `@ActiveProfiles`.
**Data flow:** consumed by Spring TCF's `MergedAnnotations` resolver at `beforeTestClass`; produces an identical `MergedContextConfiguration.hashCode()` for every test class wearing it.

**Analog:**
1. Package layout — `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` (sibling testsupport class; `package org.ctc.testsupport;` declaration).
2. Literal annotation shape — RESEARCH.md §Code Examples lines 453-493 (already authored, ready to copy verbatim).

**Required structural elements** (RESEARCH.md lines 487-492 + CONTEXT.md D-02):
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
public @interface CtcDevSpringBootContext { }
```

**Mandatory exclusions on the annotation body** (RESEARCH.md §Common Pitfalls 1-3, CONTEXT.md D-02):
- NO `@Tag(...)` — `@Tag` varies per consumer (Surefire `*Test.java` untagged; Failsafe `*IT.java` is `@Tag("integration")`). Embedding `@Tag` would mis-route Surefire-targeted tests into Failsafe.
- NO `@Transactional` — only `V4MigrationSmokeIT` carries it; embedding would force every consumer into transactional rollback semantics.
- NO `@DirtiesContext` — would cause cache eviction, defeating consolidation.
- NO `@DynamicPropertySource` — registers a `contextCustomizer` that participates in `MergedContextConfiguration.hashCode()` (Phase 86 Plan 02 cache-fragmentation lesson, reverse direction).

**Discretionary Javadoc** (CONTEXT.md §Claude's Discretion bullet 3): use the RESEARCH.md lines 468-485 wording verbatim — it already documents the `@DirtiesContext` / `@DynamicPropertySource` trap, the Phase 86 lesson cross-reference, and the "Spring TCF keys on merged config excluding the test class" mechanism.

**Imports** (RESEARCH.md lines 460-466):
```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.ctc.CtcManagerApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
```

**Plan assignment:** Plan 90-01 (Task 1 — annotation creation MUST land before the swap-refactor tasks).

---

#### `src/test/java/db/migration/V5MigrationTest.java` (refactor — exemplar Surefire-bucket member)

**Role:** Surefire-routed regression guard for V5 migration; canonical Surefire shape in bucket `9cefac4c`.
**Data flow:** Spring context boot (`@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` → `JdbcTemplate` autowire → INFORMATION_SCHEMA query).

**Analog before refactor** (self, lines 25-27):
```java
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
class V5MigrationTest {
```

**Pattern after refactor** (RESEARCH.md §Code Examples lines 495-509):
```java
import org.ctc.testsupport.CtcDevSpringBootContext;
// remove: import org.springframework.boot.test.context.SpringBootTest;
// remove: import org.springframework.test.context.ActiveProfiles;
// remove: import org.ctc.CtcManagerApplication;

@CtcDevSpringBootContext
class V5MigrationTest {
    // body unchanged
}
```

**What to adapt for every Surefire-bucket member** (V3MigrationTest, V5MigrationTest, V6MigrationTest, CtcManagerApplicationTests, TestDataServiceIntegrationTest, DataImportAuditSerializationTest, BaseEntityAuditTest, PhaseTeamUniquenessIntegrationTest, SeasonPhaseEntityIntegrationTest, PhaseTeamRepositoryTest, MatchdayGeneratorServiceTest, PlayoffServiceTest, SwissPairingServiceTest):
- Replace the two-annotation stack with `@CtcDevSpringBootContext`.
- Remove the three now-unused imports (`SpringBootTest`, `ActiveProfiles`, and `CtcManagerApplication` — but ONLY if no other code in the file references `CtcManagerApplication`; a few classes import it for other reasons).
- Add `import org.ctc.testsupport.CtcDevSpringBootContext;`.
- Body, `@Test` methods, fields, `@Autowired` injections — unchanged.
- For `PlayoffServiceTest.java` specifically: annotate the OUTER class only; the 15 `@Nested` inner classes inherit the composed annotation per JUnit + Spring-TCF inheritance (RESEARCH.md Pitfall 4 + `.planning/codebase/TESTING.md` §`@Nested` Inheritance line 126-132).

**Plan assignment:** Plan 90-01.

---

#### `src/test/java/db/migration/V4MigrationSmokeIT.java` (refactor — exemplar Failsafe-bucket member with `@Transactional`)

**Role:** Failsafe-routed `db.migration` IT; canonical Failsafe shape in bucket `f524774b` that also wears `@Transactional`.
**Data flow:** Spring context boot + Flyway autoload + `@Transactional` rollback + repository assertions.

**Analog before refactor** (self, lines 38-42):
```java
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class V4MigrationSmokeIT {
```

**Pattern after refactor** (RESEARCH.md §Code Examples lines 511-528):
```java
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.springframework.transaction.annotation.Transactional;

@CtcDevSpringBootContext
@Transactional
@Tag("integration")
class V4MigrationSmokeIT {
    // body unchanged
}
```

**Constraint summary:** `@Tag("integration")` STAYS on the subclass (Failsafe routing — `.planning/codebase/TESTING.md` §Test Categorization line 100-101, "@Tag(\"integration\") | *IT.java | Failsafe"). `@Transactional` STAYS on the subclass (varies per consumer; only V4MigrationSmokeIT carries it among bucket members).

**What to adapt for every Failsafe-bucket member without `@Transactional`** (V7DataImportAuditMigrationIT, BackupArchiveServiceIT, BackupExportServiceIT, BackupImportPostCommitEdgeCasesIT, BackupImportServiceIT, BackupImportZipBombIT, BackupStagingCleanupRaceIT, BackupStagingDirPerForkIT, DriverRepositoryOrderIT):
- Replace the two-annotation stack with `@CtcDevSpringBootContext`.
- KEEP `@Tag("integration")` exactly where it is on the subclass.
- KEEP any `@TestInstance(Lifecycle.PER_CLASS)` (some Backup ITs carry it for `@BeforeAll` reasons — verify per-class on read).
- Body unchanged.

**Plan assignment:** Plan 90-01.

---

### Plan 90-02 — PERF-04 Testcontainers reuse

---

#### `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` + `BackupRoundTripIT.java`

**Role:** Testcontainers MariaDB smoke ITs; gated behind `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` (CI never sets this — RESEARCH.md §Testcontainers Reuse Validation; CONTEXT.md D-04 invariant).
**Data flow:** Testcontainers Ryuk companion + `mariadb:11` container → `@DynamicPropertySource` injects JDBC URL → Spring boot.

**Analog (BackupImportMariaDbSmokeIT.java lines 103-107):**
```java
@Container
static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
        .withDatabaseName("ctc_test")
        .withUsername("ctc")
        .withPassword("test");
```

**Pattern after refactor** (RESEARCH.md §Code Examples lines 530-541):
```java
@Container
static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
        .withDatabaseName("ctc_test")
        .withUsername("ctc")
        .withPassword("test")
        .withReuse(true);
```

**What to adapt — identical at both touchpoints:**
- `BackupImportMariaDbSmokeIT.java` line 104 — append `.withReuse(true)` to the existing static `MariaDBContainer<>` field.
- `BackupRoundTripIT.java` line 518 — same change inside the nested `MariaDbRoundTripTests` class (note: the static container lives inside a `@Nested` class wearing its own `@SpringBootTest @ActiveProfiles("local")` annotations — DO NOT also swap this class to `@CtcDevSpringBootContext`; it uses `local` profile, not `dev`, and is OUT of the PERF-03 hash buckets per the audit table).
- NO change to `.withDatabaseName / .withUsername / .withPassword` chain — Testcontainers issue #2515 (RESEARCH.md lines 289-306) warns that container hash-stability depends on identical config chains across runs.

**Plan assignment:** Plan 90-02.

---

#### `README.md` § Test Performance (extend pointer)

**Role:** reader-facing index → `docs/test-performance.md`.
**Data flow:** static markdown; reader navigation.

**Analog** (lines 151-154 — Phase 89 Plan 89-03 added it):
```markdown
## Test Performance

Test wallclock metrics, per-phase optimisation history, and the per-fork backup-staging-dir + cache-key fingerprint instrumentation are documented in [`docs/test-performance.md`](docs/test-performance.md). The current local baseline is the v1.12 Wave-4 median ...
```

**Pattern for PERF-04 extension** (CONTEXT.md §Specific Ideas line 198, planner-discretionary exact wording):
- Append ONE sentence under the existing paragraph pointing at the new `## PERF-04 Testcontainers Reuse` section.
- DO NOT rewrite the existing pointer. DO NOT add a second `##` header. Phase-89 set the precedent of single `## Test Performance` section with append-only sentences.

**Plan assignment:** Plan 90-02 (per CONTEXT.md D-06; the README pointer ships with PERF-04, not with PERF-03 or PERF-05).

---

#### `docs/test-performance.md` § PERF-04 Testcontainers Reuse (NEW section)

**Role:** developer-facing forensic doc; append-only growth.
**Data flow:** static markdown.

**Analog** (Phase-89 precedent — `docs/test-performance.md` § PERF-02 Forensics lines 291-334; Phase-86 precedent — § Post-Optimization Wallclock (Wave 3) lines 115-141, § CI Results (PERF-05) lines 183-233):
- `## PERF-04 Testcontainers Reuse` header (level-2, same as siblings).
- Prose paragraph explaining the opt-in mechanism (the `~/.testcontainers.properties` per-developer file with `testcontainers.reuse.enable=true`).
- Verification command block (`docker ps` showing the long-lived `testcontainers-ryuk-…` + labelled `mariadb:11` instance between consecutive runs — CONTEXT.md D-04 line 50-51).
- "CI behaviour unchanged" paragraph anchoring D-04 invariant.

**Plan assignment:** Plan 90-02.

---

### Plan 90-03 — PERF-05 Test-Module-Split Decision

---

#### `docs/test-performance.md` § Test-Module-Split Decision (NEW section)

**Role:** decision-record artefact per REQUIREMENTS.md PERF-05 `defer` branch.
**Data flow:** static markdown; consumed by v1.13 milestone-discuss workflow per CONTEXT.md D-05 §Re-evaluation trigger.

**Analog** (`docs/test-performance.md § PERF-02 Forensics` lines 291-334; `docs/test-performance.md § Result Verdict (PERF-04 / PERF-05)` lines 15-54 — Phase-86 verdict-record precedent).

**Required structural elements** (CONTEXT.md D-05 + §Specific Ideas line 199):
1. One-sentence verdict line: `**Verdict (v1.12):** Defer — re-evaluate in v1.13 against PERF-06 CI re-harvest baseline.`
2. Three numbered blockers (TestDataService cross-boundary, IDE-friction-risk, no hard cumulative-effect data — exact wording in CONTEXT.md D-05 lines 59-61).
3. Re-evaluation trigger paragraph (CONTEXT.md D-05 lines 62).
4. "Why not reject?" paragraph (CONTEXT.md §Specific Ideas line 199 — optionality preservation rationale).

**What to adapt** — pure docs work; no code touched (CONTEXT.md D-09 — `src/main/java/**` git-clean).

**Plan assignment:** Plan 90-03.

---

### Phase-summary files (all three plans)

---

#### `90-01-SUMMARY.md`, `90-02-SUMMARY.md`, `90-03-SUMMARY.md`

**Role:** GSD orchestrator hand-off + planning audit record.
**Data flow:** read by `/gsd-validate-phase`; cross-referenced by `90-01-PLAN.md` etc.

**Analog** (siblings in v1.12 phase directory):
- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` (Plan 89-01 SUMMARY shape — front-matter, "What shipped", "Wave-4 numbers" table, "Decisions honored" table, "Invariants held" checklist).
- `.../89-02-SUMMARY.md` (instrumentation-plan SUMMARY shape).
- `.../89-03-SUMMARY.md` (measurement-plan SUMMARY shape — the Top-5 cluster output table is the load-bearing analog for 90-01's before/after diff section).

**Required structural elements** (Phase-89 precedent):
- YAML front-matter: `phase`, `plan`, `slug`, `status: complete`, `completed: <date>`, `wave: <N>`, `depends_on: [..]`, `requirements: [PERF-..]`.
- `## Objective recap` paragraph.
- `## What shipped` bullet list.
- For 90-01 only: `## Wave-5 numbers` table (3 runs × Maven Total / bash real / context loads / JaCoCo / Notes) + `## PERF-03 Top-5 cluster diff before/after` side-by-side block (CONTEXT.md D-02b).
- `## Decisions honored` table referencing D-01..D-09 as applicable.
- `## Invariants held` checklist (JaCoCo ≥ 0.8888, SpotBugs 0, CodeQL exit 0, EXPORT_ORDER 24, SCHEMA_VERSION 1, V1-V7 immutable, `src/main/java/**` git-clean per D-09).

**Plan assignment:** each SUMMARY ships with its plan.

---

## Shared Patterns (cross-cutting)

### Spring TCF cache-bucket identity (PERF-03 mechanism)

**Source:** Spring Framework 7.0.5 Javadoc + `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` lines 33-94 + RESEARCH.md §Mechanism Validation.

**Applies to:** every refactor target in buckets `9cefac4c` and `f524774b`.

**Mechanism:** `MergedContextConfiguration.hashCode()` excludes the test class; two classes wearing `@CtcDevSpringBootContext` produce identical `(locations, classes=[CtcManagerApplication], activeProfiles=["dev"], contextCustomizers, propertySourceDescriptors, parent, ContextLoader FQN)` tuples, so they collide into one cache bucket.

### Annotation order on Spring components / test classes (`CLAUDE.md §Lombok Usage`)

**Source:** `CLAUDE.md` § Lombok Usage line "On Spring components use `@Slf4j @Component @RequiredArgsConstructor` (alphabetical — `@Slf4j` first)".

**Applies to:** the new `CtcDevSpringBootContext.java` annotation declaration (`@Retention` → `@Target` → `@SpringBootTest` → `@ActiveProfiles`); also to subclass annotation stacks where multiple annotations stack on the same class.

**Rule for refactor:** since `@CtcDevSpringBootContext` is the only test-context annotation on the subclass, no ordering decision is needed. When `@Tag` + `@Transactional` co-exist on a Failsafe IT, follow the existing `V4MigrationSmokeIT.java` line 38-42 ordering (`@CtcDevSpringBootContext` → `@Transactional` → `@Tag("integration")`) — matches RESEARCH.md §Code Examples lines 522-525.

### `@Tag` placement (testing convention)

**Source:** `.planning/codebase/TESTING.md` §Test Categorization (`@Tag`) lines 89-132 + CLAUDE.md §Subagent-Rules "Tag Tests by Category".

**Applies to:** every Failsafe `*IT.java` member of bucket `f524774b` (must keep `@Tag("integration")` on the subclass) + every Surefire `*Test.java` member of bucket `9cefac4c` (MUST NOT acquire `@Tag` from the composed annotation — that would mis-route to Failsafe).

**Rule:** `@Tag` lives on the subclass, never on the composed annotation. `@Nested` inner classes inherit the outer class's `@Tag` (TESTING.md lines 126-132) — applies to `PlayoffServiceTest`'s 15 nested classes.

### Static-analysis discipline (SpotBugs / CodeQL)

**Source:** `CLAUDE.md` § Static Analysis + § CodeQL SAST; CONTEXT.md D-08.

**Applies to:** the new `CtcDevSpringBootContext.java` annotation file.

**Rule:**
- A plain `@interface` declaration with no fields and no methods should not surface any SpotBugs `BugInstance`. If it does (e.g., `DM_DEFAULT_ENCODING` from any inadvertent stream usage — unlikely on an annotation type), use targeted `@SuppressFBWarnings({"<CODE>"}, justification="…")` per CLAUDE.md SAST pattern. NEVER `@SuppressWarnings("all")`.
- No `config/spotbugs-exclude.xml` entry should be needed; if one is, it MUST carry the XML rationale comment + code-cross-reference per CLAUDE.md § Static Analysis line "Every `<Match>` entry MUST have an XML rationale comment".
- CodeQL gate-step must exit 0 on PR HEAD SHA; the annotation file's surface is too small to surface new findings, but the 3-layer FP suppression invariant (source marker + `codeql-config.yml` query-filter + `docs/security/sast-acceptance.md` row) applies if anything does surface.

### Production-code git-clean invariant (CONTEXT.md D-09)

**Source:** CONTEXT.md D-09; carries forward Phase 89 D-14.

**Applies to:** every Plan-N SUMMARY.

**Rule:** `git diff origin/master..HEAD -- 'src/main/java/**'` MUST be empty on the head of each plan branch / each plan commit. The PERF-03 composed annotation lives under `src/test/java/org/ctc/testsupport/`. PERF-04's `.withReuse(true)` lives in test files. PERF-05 is docs-only.

### `docs/test-performance.md` append-only structure

**Source:** Phase-86 + Phase-89 precedent visible in `docs/test-performance.md` section headers (Wave 3 → Wave 4; PERF-02 Forensics; CI Results PERF-05).

**Applies to:** `## PERF-03 Cluster` (Plan 90-01), `## PERF-04 Testcontainers Reuse` (Plan 90-02), `## Test-Module-Split Decision` (Plan 90-03).

**Rule:** append new `## ...` sections at the end of the file, NEVER edit earlier sections (except the existing `## v1.12 Forward Path` which Phase 89 Plan 89-03 set the precedent of in-place editing for "Lever-1 DONE" — Phase 90 may add similar Lever-2 / Lever-3 DONE annotations to that section). Each new section follows level-2 header + prose + code-block + table convention of its sibling sections.

### Phase summary front-matter convention

**Source:** Phase-89 SUMMARY files (`89-01-SUMMARY.md` → `89-03-SUMMARY.md`).

**Applies to:** `90-01-SUMMARY.md`, `90-02-SUMMARY.md`, `90-03-SUMMARY.md`.

**Rule:** YAML front-matter with `phase / plan / slug / status / completed / wave / depends_on / requirements`. `## Objective recap` → `## What shipped` → optional measurement table → `## Decisions honored` table → `## Invariants held` checklist. The measurement table appears only in 90-01 (Wave-5) per CONTEXT.md D-07.

---

## No Analog Found

| File | Reason |
|---|---|
| `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` | First composed-annotation type in the codebase. RESEARCH.md §Code Examples lines 453-493 supplies a literal authored template that compensates for the absence of a same-shape analog. The package-layout analog (sibling `testsupport` classes) covers location + package declaration but NOT the meta-annotation idiom itself. |

---

## Constraints Summary (cross-cutting rules the planner MUST respect)

1. **Annotation-order** (`CLAUDE.md §Lombok Usage`): on subclass — `@CtcDevSpringBootContext` → `@Transactional` → `@Tag("integration")` (matches existing `V4MigrationSmokeIT.java` order, lines 38-42); on the composed annotation itself — `@Retention` → `@Target` → `@SpringBootTest` → `@ActiveProfiles` (RESEARCH.md lines 487-491).

2. **`@Tag` placement** (`.planning/codebase/TESTING.md` §Test Categorization): NEVER on the composed annotation; ALWAYS on the subclass for Failsafe `*IT.java` members. `@Nested` inner classes inherit the outer class's `@Tag`.

3. **No production-code edits** (CONTEXT.md D-09 + Phase 89 D-14 carry-forward): `src/main/java/**` git-clean on every plan commit; PERF-03 annotation lives in `src/test/java/org/ctc/testsupport/`.

4. **No `@DirtiesContext` / `@DynamicPropertySource` on bucket members or on the composed annotation** (Phase 86 Plan 02 lesson; RESEARCH.md §Common Pitfalls 3 lines 420-428): both fragment / evict the shared cache key. Audit confirmed all 24 outer-class members are currently free of both (RESEARCH.md lines 236-238).

5. **Surefire vs Failsafe routing** (`.planning/codebase/TESTING.md` §Test Categorization): `*Test.java` untagged → Surefire (bucket `9cefac4c`); `*IT.java` with `@Tag("integration")` → Failsafe (bucket `f524774b`). The composed annotation does NOT change file naming; only annotation count.

6. **Static-analysis discipline** (`CLAUDE.md §Static Analysis + §CodeQL SAST` + CONTEXT.md D-08): targeted `@SuppressFBWarnings` only; XML rationale comment + code-cross-reference required for any new `<Match>` entry; 3-layer FP suppression (source marker + `codeql-config.yml` + `docs/security/sast-acceptance.md` row) for any new CodeQL finding. Expected new-finding count: 0.

7. **Quality gates** (CONTEXT.md D-08): JaCoCo ≥ 0.8888, SpotBugs `BugInstance` 0, CodeQL exit 0 on PR HEAD SHA, EXPORT_ORDER 24, SCHEMA_VERSION 1, Flyway V1-V7 immutable. Each Plan-N SUMMARY asserts these.

8. **Audit-before-refactor mandate** (CONTEXT.md D-01 + RESEARCH.md §Hash-bucket-population audit): the planner MUST re-run `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 10` against a fresh `target/test-perf/` and treat its output as the authoritative refactor surface. The 24 classes enumerated above are the audit-time best estimate (2026-05-20) but the actual refactor scope is whatever the re-run reports.

9. **`@Nested` outer-class annotation suffices** (RESEARCH.md Pitfall 4 lines 430-440 + `.planning/codebase/TESTING.md` §`@Nested` Inheritance lines 126-132): annotate `PlayoffServiceTest.java` once; the 15 nested classes inherit. Do not duplicate the annotation across nested classes.

10. **Testcontainers `.withReuse(true)` chain-position** (RESEARCH.md §Testcontainers Reuse Validation + §Hash-stability concern): append `.withReuse(true)` AFTER the `.withDatabaseName / .withUsername / .withPassword` chain; do NOT reorder the existing chain (Testcontainers issue #2515 — container hash-stability depends on identical config).

11. **Append-only `docs/test-performance.md`** (Phase 86 + 89 precedent): new `## ...` sections appended; existing sections left alone except for the Phase-89-set precedent of `## v1.12 Forward Path` Lever-N DONE annotations.

12. **Per-plan SUMMARY ownership** (CONTEXT.md D-06 + Phase 89 D-01 carry-forward): each Plan-N produces its own SUMMARY at commit time; SUMMARYs do not bundle across plans (atomic-revert hostile per Phase 89 D-01 rejected-alternative).

---

## Metadata

**Analog search scope:**
- `src/test/java/org/ctc/testsupport/` (testsupport package layout + composed-annotation precedent)
- `src/test/java/db/migration/` (5 db.migration classes — Surefire + Failsafe shape exemplars)
- `src/test/java/org/ctc/backup/service/` (Testcontainers MariaDB declarations)
- `docs/test-performance.md` (append-only structure precedent)
- `README.md` § Test Performance (existing pointer precedent)
- `.planning/milestones/v1.12-phases/89-*` (SUMMARY shape precedent + Phase 89 PATTERNS.md cross-reference)
- `.planning/codebase/TESTING.md` (test-categorization rules)
- `CLAUDE.md` (annotation-order, static-analysis, production-code-invariant rules)

**Files read during mapping:**
1. CONTEXT.md (full)
2. RESEARCH.md §§ Summary, User Constraints, Mechanism Validation, Hash-bucket-population audit, Code Examples
3. Phase 89 CONTEXT.md (full)
4. `.planning/codebase/TESTING.md` (full — `@Nested` inheritance rule)
5. `src/test/java/org/ctc/testsupport/{CtcDev placeholder — does not yet exist}` analogs: `ContextCacheKeyFingerprintListener.java`, `ContextLoadCountListener.java`, `SitegenTestDir.java`
6. `src/test/java/db/migration/{V3,V4Smoke,V5,V6,V7Audit}` (annotation shape verification)
7. `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` lines 95-117 + `BackupRoundTripIT.java` lines 510-535 (Testcontainers chain)
8. `src/test/resources/META-INF/spring.factories` (already wired — no edit needed)
9. `docs/test-performance.md` lines 285-340 (PERF-02 Forensics analog) + grep for section structure
10. `README.md` lines 151-154 (Test Performance pointer)
11. `.planning/milestones/v1.12-phases/89-*/89-03-SUMMARY.md` lines 1-80 (Top-5 cluster output + SUMMARY shape)
12. `.planning/milestones/v1.12-phases/89-*/89-PATTERNS.md` lines 1-100 (PATTERNS shape precedent)

**Pattern extraction date:** 2026-05-20.
**Read-only assertion:** no source file modified during pattern mapping; only `90-PATTERNS.md` written.
