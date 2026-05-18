---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
plan: 09
subsystem: backup-import
tags:
  - integration-test
  - rollback
  - audit-survival
  - failure-injection
  - h2
  - output-capture
requirements:
  - IMPORT-05
  - IMPORT-07
dependency_graph:
  requires:
    - 75-01 (RestoreFailureInjector SPI + RestoreFailureSimulatedException)
    - 75-02 (DataImportAuditService REQUIRES_NEW writer)
    - 75-06 (BackupImportService.execute orchestrator + catch-block + tryCleanupUploadsNew)
    - 75-07 (BackupImportPostCommitListener — used in negative — must NOT fire on rollback)
  provides:
    - "BackupImportRollbackIT — primary regression net for Phase 75 success-criterion-3"
    - "FailAtTableInjector + @TestConfiguration Config (test-only @Primary RestoreFailureInjector override at race_results:500)"
    - "Rule-1 fix: uploads-new cleanup assertion is per-run, not tree-wide"
  affects:
    - 75-10 (MariaDB smoke uses the same execute(...) entry point; the rollback contract this IT pins must hold on real MariaDB as well)
tech_stack:
  added: []
  patterns:
    - "@Import(@TestConfiguration) + bean-name override (name='noopRestoreFailureInjector') + spring.main.allow-bean-definition-overriding=true — only viable shape to swap a @Primary @Component in Spring 6.x without a duplicate-@Primary exception"
    - "@ExtendWith(OutputCaptureExtension.class) + CapturedOutput method-param for SLF4J ERROR-log assertions (mirrors Plan 07's PostCommitIT pattern)"
    - "JdbcTemplate.queryForObject(SELECT COUNT(*) FROM <safe_table>) for 24-table parity assertion — avoids 24 autowires + table-name guard mirrors BackupImportService.SAFE_TABLE_NAME pattern"
    - "AssertJ catchThrowableOfType(...) to retain the typed exception for getAuditUuid() drill-down"
    - "Diff pre- vs post-import filesystem sets so per-run cleanup contracts survive Failsafe forked JVMs with sibling ITs"
key_files:
  created:
    - src/test/java/org/ctc/backup/service/FailAtTableInjector.java
    - src/test/java/org/ctc/backup/service/FailAtTableInjectorTest.java
    - src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java
  modified: []
decisions:
  - "FailAtTableInjector + sibling FailAtTableInjectorTest (not inline @Nested) — sibling Tests are the established CTC repo style (RaceResultRestorerTest sibling pattern) and keep the production-shape file (FailAtTableInjector) free of @Test methods so Surefire scans cleanly."
  - "Bean-name override discipline: the @TestConfiguration's @Bean is named 'noopRestoreFailureInjector' (matching the production @Component's default name) and the test class enables spring.main.allow-bean-definition-overriding=true via @TestPropertySource. Plain @Primary on a different bean name would coexist with NoopRestoreFailureInjector.@Primary and throw NoUniqueBeanDefinitionException — Spring 6.x does not silently prefer the test bean."
  - "Per-table row-count parity via JdbcTemplate.queryForObject (24 native COUNT(*) queries) instead of 24 individual repository autowires. Mirrors the production wipe loop's table-name regex guard (^[a-z_]+$) for defensive symmetry."
  - "uploads-new cleanup assertion is diff-based (pre-vs-post set difference) rather than tree-wide existence check. A tree-wide check broke when BackupImportExecuteIT (Plan 06) had left an unrelated uploads-new from an earlier test in the same forked JVM — the Plan 06 D-12 contract is per-run, scoped to <ts>, not global."
  - "Test 1 uses AssertJ catchThrowableOfType(...) instead of assertThatThrownBy(lambda) so the typed BackupImportException reference survives for the getAuditUuid() drill-down in assertion (b). Lambda capture of a reassigned thrown variable is a compile error."
  - "Sanity-precondition assertion: preImportCounts.get('race_results') > 500 fires before the export so a slim fixture would point at a missing TestDataService.seed() loud-fail rather than appear as a silent 'injector never matched' false-pass."
metrics:
  duration_sec: 2741
  duration_human: "~46 minutes"
  tasks_completed: 2
  files_created: 3
  files_modified: 0
  completed_date: "2026-05-14"
commits:
  - hash: b4b3883
    type: test
    message: "test(75-09): add FailAtTableInjector + 3 unit tests"
  - hash: ef48e54
    type: test
    message: "test(75-09): add BackupImportRollbackIT for mid-restore-failure regression"
  - hash: '7522108'
    type: fix
    message: "fix(75-09): scope uploads-new cleanup assertion to this run only"
---

# Phase 75 Plan 09: Mid-Restore-Failure Rollback IT Summary

**Primary regression net for Phase 75 success-criterion-3** — a `RestoreFailureSimulatedException` injected at row 500 of `race_results` (~50% mid-point of the largest fixture table, RESEARCH Assumption A1) drives `BackupImportService.execute(UUID)` into its rollback path. The IT proves all four sub-requirements of SC#3 plus the W3 SLF4J ERROR-log loud-fail contract: every one of the 24 tables returns to its pre-import row count, the REQUIRES_NEW `data_import_audit` row with `success=false` survives the outer rollback (Plan 75-02 contract), the live `uploads/` tree is byte-identical to its pre-import snapshot (AFTER_COMMIT listener never fired), and this run's `uploads-new/` staging directory is cleaned up by the catch-block (Plan 75-06 D-12).

## Performance

- **Duration:** ~46 minutes
- **Started:** 2026-05-14T10:19:00Z (worktree spawn)
- **Completed:** 2026-05-14T11:05:41Z
- **Tasks:** 2 (test injector + IT)
- **Files created:** 3
- **Files modified:** 0
- **Commits:** 3 (test + test + fix)

## Accomplishments

- **`FailAtTableInjector`** implements the production `RestoreFailureInjector` SPI (Plan 75-01 D-13) with a `(targetTable, targetRow)` constructor. `maybeFailAt(...)` throws `RestoreFailureSimulatedException` only on exact case-sensitive snake_case table-name + row-index match — every other call is a no-op (3 unit tests pin the contract).
- **`FailAtTableInjector.Config` `@TestConfiguration`** exposes the injector as the `@Primary` bean for any test that does `@Import(FailAtTableInjector.Config.class)`. The bean is given the production component's name (`noopRestoreFailureInjector`) and the test class enables `spring.main.allow-bean-definition-overriding=true` so the override replaces the production bean rather than coexist with it (which would otherwise throw `NoUniqueBeanDefinitionException: more than one 'primary' bean found`).
- **`BackupImportRollbackIT`** — Failsafe IT under `@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS) @Import(FailAtTableInjector.Config.class) @TestPropertySource(...) @ExtendWith(OutputCaptureExtension.class)`:
  - **Test 1** drives the full `seed → export → stage → execute(throws)` flow and asserts:
    - (a) all 24 table row counts equal `preImportCounts` (rollback complete; native `JdbcTemplate.queryForObject("SELECT COUNT(*) FROM <table>")` per table; defensive `^[a-z_]+$` table-name guard mirrors the production wipe pattern).
    - (b) the `data_import_audit` row addressed by the thrown `BackupImportException.getAuditUuid()` has `success=false`, a non-blank `sourceFilename`, and a populated `executedAt` (REQUIRES_NEW survival from Plan 75-02).
    - (c) the live `uploads/` tree (snapshot of relative paths) is byte-equal to the pre-import snapshot — AFTER_COMMIT listener did NOT fire because the outer commit aborted.
    - (d) **per-run** uploads-new cleanup: diff of pre-vs-post `uploads-new/` directory sets is empty (this run's `<ts>/uploads-new/` was removed by `tryCleanupUploadsNew`).
    - (e) **W3:** captured SLF4J output contains both `"Import failed for staging-id <uuid>"` and `"RestoreFailureSimulatedException"` — the loud-fail contract Plan 75-08's flash messaging depends on.
  - **Test 2** proves the operator-retry contract: the staged ZIP at `data/<profile>/backup-staging/upload-<uuid>.zip` survives the failure path (no Step-4 staging-cleanup fired). An admin can re-invoke `execute(stagingId)` without re-uploading.
- **Sanity precondition** in Test 1: `preImportCounts.get("race_results") > 500`. A slim fixture would cause the injector to never match — instead of a silent false-pass, the IT fails loud with a clear "TestDataService.seed must produce >500 race_results" message.

## Tasks Executed

### Task 1 — `FailAtTableInjector` + `FailAtTableInjectorTest` — `b4b3883`

Public `FailAtTableInjector` class with constructor `(String targetTable, int targetRow)`. The `maybeFailAt(String tableName, int rowIndex)` method performs a case-sensitive `equals` + `==` match and throws `new RestoreFailureSimulatedException("Simulated mid-restore failure at " + tableName + ":" + rowIndex)` on hit. Nested `@TestConfiguration public static class Config` defines a `@Bean(name = "noopRestoreFailureInjector") @Primary public RestoreFailureInjector failAtTable()` (the bean-name choice is deliberate — see Decisions §1).

Sibling `FailAtTableInjectorTest` (Surefire unit test, no `@SpringBootTest`) covers the 3 cells of the truth table: matching-table-and-row → throws, non-matching table → no-op, matching table but different row → no-op. Given-When-Then naming throughout. 3 tests green in 88 ms.

### Task 2 — `BackupImportRollbackIT` (Failsafe IT) — `ef48e54` + `7522108`

`@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS) @Import(FailAtTableInjector.Config.class) @TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true") @ExtendWith(OutputCaptureExtension.class)` — a single dev-profile Spring context per class.

`@BeforeAll` invokes `testDataService.seed()` (REVISION-iteration-1 B1: `seedSaison2023()` does NOT exist; the single entry point `seed()` loads Season 2023 + 2024 + 2024-Empty + 2026 per Javadoc). `@BeforeEach` captures pre-state for every test: per-table row counts via native `COUNT(*)` queries against `BackupSchema.getExportOrder()`, the `uploads/` tree snapshot (set of relative paths), and the set of `uploads-new/` directories under `importBackupsDir`.

Test 1 builds a real export ZIP via `BackupArchiveService.writeZip(baos, Instant.now())` (REVISION-iteration-1 B3: `BackupExportService.export(...)` does NOT exist), wraps it in `MockMultipartFile`, stages it, then calls `execute(stagingId)`. `catchThrowableOfType(...)` captures the typed `BackupImportException` so `getAuditUuid()` is accessible for assertion (b). The 5-part assertion battery runs (per-table counts → audit row → uploads-tree equality → per-run uploads-new diff → captured-output ERROR-log assertions).

Test 2 mirrors the setup but asserts only the staged-ZIP-survival contract: `Files.exists(stagingDir.resolve("upload-" + stagingId + ".zip"))` returns `true` both before AND after the failure path.

The `7522108` fix-up scoped assertion (d) from tree-wide to per-run: the initial draft walked `importBackupsDir` and asserted no `uploads-new/` directory exists anywhere — but `BackupImportExecuteIT` (Plan 06) runs earlier in the same Failsafe forked JVM and may leave an unrelated `uploads-new/` behind from its own happy-path test. Diffing pre- vs post-import sets pins the assertion to directories created BY THIS RUN.

## Verification

| Check | Result |
| ----- | ------ |
| `./mvnw -q compile` | BUILD SUCCESS |
| `./mvnw -q -Dtest=FailAtTableInjectorTest test` | 3/0/0 (88 ms) |
| `./mvnw -Dit.test=BackupImportRollbackIT verify` | 2/0/0 (33 s) — both scenarios green |
| `./mvnw -Dit.test='BackupImportExecuteIT,BackupImportRollbackIT,BackupImportPostCommitIT,BackupArchiveExtractUploadsIT,TeamRestorerIT' verify` | 12/0/0 — Plan 9 Verification §1 bundle green |
| JaCoCo `jacoco:check` after IT bundle | `All coverage checks have been met` — Plan 9 Verification §3 |
| `git status` after IT runs | clean (no leaked `data/dev/uploads/` modifications) — Plan 9 Verification §5 |

### Acceptance Greps (Plan §acceptance_criteria)

| Grep | Required | Actual |
| ---- | -------- | ------ |
| `class FailAtTableInjector implements RestoreFailureInjector` | ≥1 | 1 ✔ |
| `@TestConfiguration` in `FailAtTableInjector.java` | ≥1 | 1 ✔ |
| `@Bean` + `@Primary` in same file | ≥1 | 2 ✔ (both annotations present) |
| 3 scenario names in `FailAtTableInjectorTest.java` | 3 | 3 ✔ |
| `@Import(FailAtTableInjector.Config.class)` in `BackupImportRollbackIT.java` | ≥1 | 1 ✔ |
| Test 1 + Test 2 scenario names | 2 | 2 ✔ |
| 5-assertion-battery markers (`preImportCounts`, `findById(...auditUuid)`, `containsExactlyInAnyOrder`, `uploads-new`, `CapturedOutput\|output.getOut`) | ≥5 | 16 ✔ |
| `OutputCaptureExtension` | ≥1 | 2 ✔ |
| `RestoreFailureSimulatedException\|Import failed for staging-id` | ≥1 | 10 ✔ |
| `BackupImportException` | ≥1 | 9 ✔ |

## Decisions Made

1. **Bean-name override discipline for `RestoreFailureInjector`.** The CONTEXT D-13 / Plan 09 `<interfaces>` text said `@Primary` on the test bean would "override the production `NoopRestoreFailureInjector.@Primary` because test config takes precedence per Spring Test convention". That is **not** how Spring 6.x behaves — two `@Primary` beans on the same type throw `NoUniqueBeanDefinitionException: more than one 'primary' bean found`. The only viable resolution is to make the test bean's name match the production bean's name (`noopRestoreFailureInjector`, the default `AnnotationBeanNameGenerator` lowercase-first of `NoopRestoreFailureInjector`) AND enable `spring.main.allow-bean-definition-overriding=true` so Spring's bean-registration phase replaces the definition rather than rejects the conflict. The IT enables this via `@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")` on the class.

2. **Sibling test file over inline `@Nested` for the 3 unit tests.** The Plan §acceptance_criteria text gave the planner explicit discretion ("inline JUnit tests OR a sibling test file"). The CTC repo convention (`RaceResultRestorerTest`, `PlayoffRestorerTest`, ...) is sibling test files; the chosen shape matches.

3. **`JdbcTemplate.queryForObject("SELECT COUNT(*) FROM <table>")` for 24-table parity.** Avoids 24 autowires and 24 hand-written `repository.count()` calls. The defensive `^[a-z_]+$` table-name regex mirrors `BackupImportService.SAFE_TABLE_NAME` so any future malformed `@Table(name=...)` annotation fails the IT fast rather than silently issuing arbitrary native SQL.

4. **Per-run diff for the uploads-new cleanup assertion (Rule-1 fix).** The Plan 06 D-12 cleanup contract is scoped to THIS run's `<ts>/uploads-new/` directory. Failsafe runs multiple ITs in the same forked JVM, and `BackupImportExecuteIT` (Plan 06 happy-path) is allowed to leave an unrelated `uploads-new/` in `data/.import-backups/`. The original tree-wide existence check broke when that happened; the diff-based check is the correct semantic.

5. **`catchThrowableOfType(...)` for typed-exception drill-down.** `assertThatThrownBy(lambda)` captures the throwable internally and exposes assertions on `.cause()` etc, but does not expose the typed reference for the subsequent `BackupImportException.getAuditUuid()` call. `catchThrowableOfType(..., BackupImportException.class)` returns the typed instance, which we then assert on. The two assertion idioms compose cleanly.

6. **Sanity-precondition `> 500` race_results.** `FailAtTableInjector("race_results", 500)` only fires when the restore loop reaches that row index. A future fixture shrink (or a test that loads only Saison-2024-Empty) would silently skip the injection — the test would then pass without exercising the rollback path at all. The pre-export assertion `assertThat(preImportCounts.get("race_results")).isGreaterThan(500L)` makes that failure mode loud.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Duplicate `@Primary` beans on `RestoreFailureInjector`**

- **Found during:** Task 2 first IT run.
- **Issue:** The Plan 09 `<interfaces>` block specified `@Bean @Primary failAtTable()` inside the `@TestConfiguration`, with the comment "the `@Primary` annotation overrides the production `NoopRestoreFailureInjector.@Primary` because test config takes precedence per Spring Test convention". This is **incorrect** in Spring 6.x: Spring's `DefaultListableBeanFactory.determinePrimaryCandidate` throws `NoUniqueBeanDefinitionException: more than one 'primary' bean found among candidates: [noopRestoreFailureInjector, failAtTable]` when two beans of the same type both carry `@Primary`. Test config does not silently win over production config.
- **Fix:** Renamed the test bean to `noopRestoreFailureInjector` (matching the production `@Component`'s default bean name) so Spring's bean-definition-override path replaces the production bean rather than coexist with it. Added `@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")` on `BackupImportRollbackIT` (Spring Boot disables override by default since 2.1). The Plan-text annotation `@Primary` is preserved on the test bean for clarity even though it is now redundant (only one bean exists post-override).
- **Files modified:** `src/test/java/org/ctc/backup/service/FailAtTableInjector.java`, `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java`
- **Commit:** `ef48e54` (bundled with Task 2 IT)

**2. [Rule 1 - Bug] Tree-wide `uploads-new` cleanup assertion broke under Failsafe forked-JVM IT-sequencing**

- **Found during:** Plan 9 Verification §1 bundle run after Task 2 commit.
- **Issue:** The original assertion walked the entire `importBackupsDir` and asserted no `uploads-new/` directory exists anywhere. When `BackupImportExecuteIT` (Plan 06, runs earlier in the same forked JVM) had successfully executed an import and left an unrelated `<ts>/uploads-new/` from its happy-path test (Plan 07's AFTER_COMMIT listener may not have moved it depending on timing), the assertion fired falsely on a directory that was not created by my run.
- **Fix:** Diffed pre- vs post-import sets of `uploads-new/` directories. The Plan 06 D-12 cleanup contract guarantees the per-run `<ts>/uploads-new/` directory is removed on the failure path — it does NOT guarantee that every `uploads-new/` directory anywhere on disk is removed. The per-run diff captures the contract correctly.
- **Files modified:** `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java`
- **Commit:** `7522108`

### Authentication Gates

None.

## Known Stubs

None. The IT exercises real production code (`BackupImportService.execute`, `DataImportAuditService.recordResult`, `BackupArchiveService.writeZip`, `BackupImportPostCommitListener.onImportSucceeded`) via the real Spring context.

## Threat Flags

None — Plan 09 adds test-only files. The injector throws only when the test target matches (production no-op stays `@Primary` in production contexts that do not `@Import(FailAtTableInjector.Config.class)`).

## TDD Gate Compliance

Plan is `type: execute` — plan-level RED/GREEN gate sequencing is not enforced.

Task 1 (`tdd="true"`): RED phase confirmed by running `./mvnw -Dtest=FailAtTableInjectorTest test` BEFORE the implementation existed (compile error on `FailAtTableInjector` class reference); GREEN phase confirmed after the production class was written (3 tests green). Implementation + tests committed as a single `test:` commit because in the CTC repo the test-file-companion idiom (`Foo.java` + `FooTest.java` in the same commit) is the established pattern (verified in PlayoffRestorerTest pairing).

Task 2 (`tdd="true"`): the behavior is the IT itself; production code (`BackupImportService.execute`, AFTER_COMMIT listener, REQUIRES_NEW audit writer) already shipped in Plans 06 + 07. The IT was written, run against existing production code, and (after the two Rule-1 fixes) confirmed green — locking the contract.

## Next Plan Readiness

- **Plan 10 (MariaDB smoke):** Uses the same `BackupImportService.execute(...)` entry point on a Testcontainers MariaDB. The rollback contract this IT pins (24-table parity + REQUIRES_NEW audit survival + uploads tree unchanged + uploads-new cleanup + loud ERROR log) must hold on real MariaDB. No code changes expected on the rollback path — the `FailAtTableInjector` mechanism is dialect-agnostic.

## Self-Check: PASSED

**Files checked (all FOUND):**

- `src/test/java/org/ctc/backup/service/FailAtTableInjector.java`
- `src/test/java/org/ctc/backup/service/FailAtTableInjectorTest.java`
- `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java`

**Commits checked (all FOUND in `git log`):**

- `b4b3883` — test(75-09): add FailAtTableInjector + 3 unit tests
- `ef48e54` — test(75-09): add BackupImportRollbackIT for mid-restore-failure regression
- `7522108` — fix(75-09): scope uploads-new cleanup assertion to this run only

---
*Phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat*
*Completed: 2026-05-14*
