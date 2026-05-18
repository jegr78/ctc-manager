# Phase 82: Backup Cleanup - Context

**Gathered:** 2026-05-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Resolve all 12 Phase-75 REVIEW.md Info/Warning items (WR-01..WR-08 + IN-01..IN-04) without breaking the backup wire contract or the existing round-trip test suite, AND extend the test suite per BACK-01/BACK-03/BACK-05 so future cleanup work cannot silently drop the guarantees this phase locks in.

**🔑 Scope reality (discovered during discuss-phase):** 7 of the 12 items (WR-02..WR-08) are already fixed and merged on master via the v1.10 PR #121 (`fix(75): WR-XX ...` commits — see git log). Phase 82 produces NEW commits ONLY for the 5 unresolved items (WR-01 + IN-01..IN-04) and the 3 new test obligations (BACK-01, BACK-03, BACK-05). The 7 pre-resolved items are catalogued in a single `82-BACKLOG-AUDIT.md` artifact that maps each REVIEW.md ID to its existing-on-master commit SHA — that satisfies BACK-02 "each item resolved with one atomic commit referencing the REVIEW.md ID" without busywork no-op cherry-picks.

**In scope (NEW work):**
- **WR-01** fix — extract `BackupExecutedByResolver` `@Component` bean, inject into both `BackupImportService` and `DataImportAuditService`, remove the duplicated `resolveExecutedBy()` private methods. One unit test covering all 4 resolution branches.
- **IN-01** fix — remove `@RequiredArgsConstructor` from the 18 restorers that have no `final` instance fields (DriverRestorer, MatchRestorer, MatchScoringRestorer, MatchdayRestorer, PhaseTeamRestorer, PsnAliasRestorer, RaceAttachmentRestorer, RaceLineupRestorer, RaceRestorer, RaceResultRestorer, RaceScoringRestorer, RaceSettingsRestorer, SeasonDriverRestorer, SeasonPhaseGroupRestorer, SeasonPhaseRestorer, SeasonRestorer, SeasonTeamRestorer, TeamRestorer). Single atomic commit `chore(82): IN-01 remove no-op @RequiredArgsConstructor (18 restorers)`.
- **IN-02** fix — align all restorer annotation order to `@Slf4j @Component [@RequiredArgsConstructor]`. The 6 currently-`@Component @Slf4j` restorers are CarRestorer, PlayoffMatchupRestorer, PlayoffRestorer, PlayoffRoundRestorer, PlayoffSeedRestorer, TrackRestorer. Single atomic commit `style(82): IN-02 align restorer annotation order`. Document the convention as a single-sentence addition under CLAUDE.md `## Conventions` (or skip if the planner judges this isn't worth permanent enforcement).
- **IN-03** fix — `BackupImportService.restoreOneTable` escalates the silent skip on a missing ZIP data entry from `log.debug` to `log.warn` with an explicit corruption-signal message. Still returns 0 (soft tolerance, audit row shows the gap). Single test asserting WARN log emission on synthetic missing-entry input.
- **IN-04** fix — `application.yml` line 6: change `import-backups-dir: data/.import-backups` to `import-backups-dir: data/${spring.profiles.active:dev}/import-backups` to mirror the existing `staging-dir: data/${spring.profiles.active:dev}/backup-staging` pattern. Update README "Backup & Restore" section path references. No DB migration needed (filesystem-only).
- **BACK-01** new test — `BackupSchemaGuardTest.java` (unit) asserting `BackupSchema.SCHEMA_VERSION == 1` AND `backupSchema.getExportOrder().size() == 24`. Two `@Test` methods, no Spring context, plain unit test (untagged).
- **BACK-03** new test — Integration test measuring `ZipEntry`-open count per restore. Implementation: add a package-private `@VisibleForTesting AtomicInteger zipOpenCounter` field in `BackupImportService`, increment on the single `new ZipFile(...)` call inside `restoreAll` (line ~635), reset in `execute()`. IT exercises one full import and asserts `zipOpenCounter.get() == 1`. Verifies WR-05 stays fixed forever.
- **BACK-05** new test — Extend `BackupRoundTripIT` (both `H2RoundTripTests` and `MariaDbRoundTripTests` `@Nested` classes) with one `@Test givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch` that iterates `backupSchema.getExportOrder()`, captures `JdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + ref.tableName(), Long.class)` pre-wipe, runs export → wipe → import, then asserts each table's post-restore count == pre-wipe count. Single test, 24 iterations.
- **82-BACKLOG-AUDIT.md** doc — maps WR-02..WR-08 + CR-01 + CR-02 each to its existing-on-master commit SHA + one-line evidence. Satisfies BACK-02 "each item resolved with atomic commit referencing the REVIEW.md ID" (the commits exist; this doc points to them).
- **Phase-end verification** — single `./mvnw verify -Pe2e` run confirming JaCoCo ≥ 82 % (target: hold v1.10 baseline 87.80 % ± 0.5 pp), SpotBugs gate green, all Failsafe IT pass (including `BackupRoundTripIT` H2 + MariaDB nested classes, `BackupImportRollbackIT`, and the new BACK-03 IT), Playwright E2E green.

**Out of scope (deliberate):**
- Re-committing the 7 already-fixed WR items (CR-01, CR-02, WR-02..WR-08) as fresh Phase-82 commits — see scope-reality note above. Replayed commits would be no-op diffs.
- Modifying `BackupSchema.SCHEMA_VERSION` to 2 — explicitly forbidden by SC#1 (BACK-01 is a GUARD, not a bump trigger).
- Changing the 24-entity `EXPORT_ORDER` set — forbidden by SC#1.
- Refactoring `BackupImportService.execute()` beyond what WR-01 + IN-03 require — out of scope.
- Refactoring `BackupArchiveService` — already covered by WR-07 (merged).
- Migrating existing `data/.import-backups/<ts>/` artifacts from old path — the dir is 24h-recovery-only; document the path change in README, do not write a migration script.
- Adding a Flyway migration — Phase 82 touches only Java code + `application.yml` + tests + docs. No schema changes.
- Touching `BackupController`, `BackupImportPostCommitListener`, `BackupArchiveService`, `BackupManifest`, or any of the 24 `MixIn` classes — none of the 5 unresolved items live there.
- Auto-amending the ROADMAP SC#2 wording — the audit-doc satisfies BACK-02 spirit without ROADMAP churn.

</domain>

<decisions>
## Implementation Decisions

### Scope & Commit Strategy

- **D-01:** **5 fix commits + 3 test commits + 1 audit doc** on the Phase-82 feature branch. The 7 pre-resolved WR items (CR-01, CR-02, WR-02..WR-08) are NOT re-committed — they live on master via PR #121's squash and are catalogued in `82-BACKLOG-AUDIT.md`. SC#2's "one atomic commit per item, each commit referencing the REVIEW.md ID" is interpreted item-centric (each item is traceable to one atomic commit somewhere in the master history), NOT phase-centric (each item requires a NEW Phase-82 commit). This avoids 7 no-op cherry-pick commits.
- **D-02:** **Commit order (code-fix first, tests second, docs last):**
  1. `fix(82): WR-01 extract BackupExecutedByResolver bean`
  2. `fix(82): IN-04 profile-isolate import-backups-dir`
  3. `fix(82): IN-03 warn on missing ZIP data entry`
  4. `chore(82): IN-01 remove no-op @RequiredArgsConstructor (18 restorers)`
  5. `style(82): IN-02 align restorer annotation order (@Slf4j @Component first)`
  6. `test(82): BACK-01 schema-version + export-order guard test`
  7. `test(82): BACK-03 ZipEntry-open count IT for restore path`
  8. `test(82): BACK-05 extend BackupRoundTripIT to all 24 entities`
  9. `docs(82): 82-BACKLOG-AUDIT.md (7 pre-resolved items + SHA pointers)`
  10. `docs(82): STATE + manifest + verification`
- **D-03:** **Branch** — feature branch off `origin/master` per CLAUDE.md `## Git Workflow`. Planner picks the name (suggestion: `feature/backup-cleanup`). Phase-PR target is the milestone branch `gsd/v1.11-tooling-and-cleanup` (per PROJECT.md tail and Phase 81 precedent).

### WR-01: BackupExecutedByResolver

- **D-04:** **New `@Component`-class `BackupExecutedByResolver`** in package `org.ctc.backup.audit`. Single public method `String resolve(String callerOverride)` encapsulating the 4-branch resolution chain currently duplicated:
  1. `if (environment.matchesProfiles("dev | local")) return "dev";`
  2. `if (callerOverride != null && !callerOverride.isBlank()) return callerOverride;`
  3. `Authentication auth = SecurityContextHolder.getContext().getAuthentication(); if (auth != null && auth.getName() != null && !auth.getName().isBlank()) return auth.getName();`
  4. `return "unknown";`
  Annotated `@Component @RequiredArgsConstructor` per CLAUDE.md conventions. `Environment` injected as `final` field.
- **D-05:** **Both callers updated:** `BackupImportService` and `DataImportAuditService` lose their private `resolveExecutedBy(...)` methods and gain a `final BackupExecutedByResolver executedByResolver` field via `@RequiredArgsConstructor`. Call sites switch from `resolveExecutedBy()` / `resolveExecutedBy(executedByCaller)` to `executedByResolver.resolve(callerOverride)`. Caller-override semantics preserved (BackupImportService.tryRecordFailure passes `null`, success-path resolver-call passes `null`, DataImportAuditService passes the `executedByCaller` param).
- **D-06:** **Unit test `BackupExecutedByResolverTest`** in `src/test/java/org/ctc/backup/audit/`. Plain Mockito unit test (no Spring context). Four test methods, one per resolution branch:
  - `givenDevProfile_whenResolve_thenReturnsDev`
  - `givenNonDevProfileAndCallerOverride_whenResolve_thenReturnsOverride`
  - `givenNonDevProfileAndNoOverrideAndAuth_whenResolve_thenReturnsAuthName`
  - `givenNonDevProfileAndNoOverrideAndNoAuth_whenResolve_thenReturnsUnknown`
  Uses `Mockito.mock(Environment.class)` and `SecurityContextHolder` static-mock per branch. Tagged: untagged (plain unit test).

### IN-01 + IN-02: Restorer Annotation Cleanup

- **D-07:** **One commit per REVIEW.md ID** — IN-01 (18 files, remove `@RequiredArgsConstructor`) and IN-02 (6 files, swap `@Component @Slf4j` → `@Slf4j @Component`) are separate commits because they trace to separate REVIEW.md IDs. The two commits CAN touch overlapping files (e.g., DriverRestorer is in both lists if IN-02 normalises everything to `@Slf4j @Component @RequiredArgsConstructor` then IN-01 strips the `@RequiredArgsConstructor`) — order them IN-01 first, IN-02 second so the post-IN-01 file set is `@Slf4j @Component` (no third annotation to align).
- **D-08:** **Convention enforcement** — CLAUDE.md `## Conventions` section gets a one-line addition under `### Lombok Usage`: "Annotation order on Spring components is `@Slf4j @Component @RequiredArgsConstructor` (alphabetical)." If the planner judges this isn't worth permanent CLAUDE.md weight (six files isn't a clear pattern), the convention can live as a code-review note without CLAUDE.md edit — Claude discretion.
- **D-09:** **Verification** — after IN-01 + IN-02, `git grep -l "@RequiredArgsConstructor" src/main/java/org/ctc/backup/restore/entity/` should return only restorers that have `private final` fields (none today, per scout — they're all field-less). After IN-02, `git grep -E "^@Component" src/main/java/org/ctc/backup/restore/entity/*.java | grep -v "@Slf4j @Component"` should return empty.

### IN-03: Missing ZIP Entry Policy

- **D-10:** **Soft tolerance with WARN escalation.** `BackupImportService.restoreOneTable` (around line 644-646 per REVIEW.md) keeps the return-0 semantics (preserves the Javadoc contract "absent data files are not a hard error") but escalates the silent `log.debug` to `log.warn` with a corruption-signal message: `log.warn("Backup ZIP has no data entry for table={} (entryPath={}) — possible corruption or schema regression", ref.tableName(), entryPath);`. Operators see the gap in logs; the audit row's `restoredCounts` shows 0 for the missing table.
- **D-11:** **Test** — extend the existing BACK-03 IT (or add a sibling `BackupImportMissingEntryWarnIT`) with one `@Test givenZipWithMissingEntry_whenRestore_thenWarnLogEmittedAndZeroRows`. Uses a synthetic test ZIP missing one data entry (e.g., `data/cars.json`). Asserts log capture via `LogCaptor` or `OutputCaptureExtension`. **Claude discretion:** planner picks the captor — `OutputCaptureExtension` is built into Spring Boot test and is the lighter dependency.

### IN-04: Profile-Isolate import-backups-dir

- **D-12:** **`application.yml` line 6:** change `import-backups-dir: data/.import-backups` to `import-backups-dir: data/${spring.profiles.active:dev}/import-backups`. Mirrors the existing `staging-dir: data/${spring.profiles.active:dev}/backup-staging` pattern (cross-checked during scout).
- **D-13:** **No backward-compat shim.** The dir is 24h-retention recovery storage, not a stable contract. Existing operator's `data/.import-backups/<ts>/` artifacts under the old path stay where they are; new imports write to the profile-isolated path. Document the path change in README "## Backup & Restore" section (the operator-recovery procedure paragraph) — single-sentence note. **Discretion:** planner picks exact README sentence wording.
- **D-14:** **Application property type stays `Path` / `String`** (no controller-facing schema change). No DB migration. No Flyway file. Only `application.yml` + README edits.
- **D-15:** **Test** — add a slice IT asserting the resolved path under `dev` profile starts with `data/dev/import-backups` and under a non-dev profile (e.g., `local`) starts with `data/local/import-backups`. Smaller `@SpringBootTest(classes = ...)` or a `@TestPropertySource` slice — planner picks the lightest shape. If a similar test exists for staging-dir, mirror it.

### BACK-01: Schema-Version + Export-Order Guard

- **D-16:** **New unit test `BackupSchemaGuardTest`** at `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java`. Two `@Test` methods:
  - `givenBackupSchema_whenInspected_thenSchemaVersionIsOne` → asserts `BackupSchema.SCHEMA_VERSION == 1`.
  - `givenBackupSchema_whenInspected_thenExportOrderHasTwentyFourEntities` → asserts `backupSchema.getExportOrder().size() == 24`.
  Plain unit test, NO Spring context — instantiate `BackupSchema` directly (or use the bean via constructor if the JPA Metamodel is required for the dynamic Kahn topo-sort). **Discretion:** if `BackupSchema` requires injected dependencies (e.g., `EntityManager` to build the metamodel), the test becomes a small `@DataJpaTest` slice or `@SpringBootTest(classes = {BackupSchema.class, ...})` — keep it as lightweight as possible.
  Untagged (Surefire unit test category).
- **D-17:** **Constant assertion message** — assertion failure must say "BackupSchema.SCHEMA_VERSION changed from 1 — this is a wire contract bump; see Phase 75 SCHEMA_VERSION gate or write a new migration phase". Helps the inevitable future developer understand WHY the guard exists.

### BACK-03: ZipEntry-Open Count IT

- **D-18:** **In-source counter.** Add a package-private `@VisibleForTesting AtomicInteger zipOpenCounter = new AtomicInteger(0);` field on `BackupImportService`. Reset to 0 at the top of `execute(UUID stagingId)`. Increment via `zipOpenCounter.incrementAndGet()` immediately before the `try (ZipFile zf = new ZipFile(staged.toFile()))` line (~line 635) inside `restoreAll`. One-int, one-increment, no production-flow change. NOT exposed via getter on the public API — package-private field access from the test (same package: `org.ctc.backup.service`).
- **D-19:** **New IT `BackupRestoreZipOpenCountIT`** at `src/test/java/org/ctc/backup/service/`. `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")`. One `@Test givenStagedBackup_whenExecuteImport_thenZipOpenedExactlyOnce`. Uses `TestDataService` to stage a backup, calls `backupImportService.execute(stagingId)`, asserts `backupImportService.zipOpenCounter.get() == 1`. Tagged `@Tag("integration")` per CLAUDE.md TESTING.md categorisation.
- **D-20:** **Why not Mockito.mockStatic(ZipFile.class)?** Mockito-inline mockStatic on JDK classes is brittle across JDK versions, requires bytecode hooks, and slows test startup. The in-source counter is 3 lines of production code that's permanently testable and zero-cost at runtime.
- **D-21:** **SpotBugs interaction** — adding `AtomicInteger zipOpenCounter` to BackupImportService may trigger `EI_EXPOSE_REP` if accessed externally. The package-private field is read-only from same-package tests (no getter). If SpotBugs flags it, suppress per-field with rationale comment per Phase 81 D-09 — but expectation is no flag because the field is package-private, not `public`.

### BACK-05: BackupRoundTripIT 24-Entity Row-Count Parity

- **D-22:** **Single `@Test` per `@Nested` class.** `BackupRoundTripIT.H2RoundTripTests` and `BackupRoundTripIT.MariaDbRoundTripTests` each gain one new `@Test givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch`. Implementation:
  ```java
  @Test
  void givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch() {
      Map<String, Long> preCounts = new LinkedHashMap<>();
      for (EntityRef ref : backupSchema.getExportOrder()) {
          preCounts.put(ref.tableName(),
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM " + ref.tableName(), Long.class));
      }
      // export → wipe → import (reuse existing helpers in this @Nested class)
      runFullRoundTrip();
      for (EntityRef ref : backupSchema.getExportOrder()) {
          long post = jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM " + ref.tableName(), Long.class);
          assertThat(post)
              .as("row-count parity for table=" + ref.tableName())
              .isEqualTo(preCounts.get(ref.tableName()));
      }
  }
  ```
- **D-23:** **Helper reuse.** Both `@Nested` classes already have their own `BackupImportService` + `JdbcTemplate` autowiring (per scout) and existing 3-entity spot-check helpers. The 24-entity test reuses the same `runFullRoundTrip()` / export-stage-execute helper that the 3-entity tests use — only the assertion shape changes.
- **D-24:** **Table-name source = `getExportOrder()`** (not hand-listed). Single source of truth — when EXPORT_ORDER grows to 25 entities in a future milestone, the test will automatically pick up the new entity and assert parity for it too (assuming the migration adds the table). The BACK-01 guard test will fail loud on the size change, prompting the developer to update both the BackupRoundTripIT expectation AND the SCHEMA_VERSION bump path.
- **D-25:** **MariaDB nested class precondition.** Per existing IT shape, `MariaDbRoundTripTests` uses `@EnabledIfSystemProperty(named = "mariadb.smoke", matches = "true")`. The new 24-entity test inherits the same gate — runs only on the MariaDB smoke CI workflow, not on every dev `mvnw verify`.

### Test Cadence

- **D-26:** **Per-commit:** targeted test only.
  - WR-01 commit → `./mvnw test -Dtest=BackupExecutedByResolverTest` + spot-check `./mvnw test -Dtest='BackupImportService*' -DfailIfNoTests=false`
  - IN-04 commit → `./mvnw test -Dtest=BackupImportServiceIT -DfailIfNoTests=false` (or whichever IT references the import-backups-dir path)
  - IN-03 commit → `./mvnw test -Dtest='BackupImport*'`
  - IN-01 + IN-02 commits → `./mvnw test -Dtest='*Restorer*'` (Surefire-only, cheap)
  - BACK-01 commit → `./mvnw test -Dtest=BackupSchemaGuardTest`
  - BACK-03 commit → `./mvnw verify -Dit.test=BackupRestoreZipOpenCountIT -DfailIfNoTests=false -DskipUnitTests=false`
  - BACK-05 commit → `./mvnw verify -Dit.test=BackupRoundTripIT -DfailIfNoTests=false` (H2 nested only on dev machine; MariaDB nested in CI smoke workflow)
- **D-27:** **Per-commit (main/java-touching only):** `./mvnw spotbugs:check -DskipTests` after WR-01, IN-03, IN-04 commits. IN-01/IN-02 (annotation-only) and IN-04 (yaml-only) are very unlikely to flip SpotBugs; the planner can choose to skip the spotbugs:check for those commits and rely on the final `mvnw verify -Pe2e` to catch regressions. **Discretion granted.**
- **D-28:** **Phase-end:** ONE final `./mvnw verify -Pe2e` confirming all 1011+ tests + 36 Playwright E2E green + JaCoCo line coverage holds at v1.10 baseline 87.80 % ± 0.5 pp (gate is 82 %, so 5+ pp comfort buffer remains).
- **D-29:** **MariaDB smoke verification.** Before raising the phase PR, trigger the existing `mariadb-migration-smoke.yml` GitHub workflow (or run locally with `-Dmariadb.smoke=true`) to confirm `BackupRoundTripIT.MariaDbRoundTripTests` 24-entity parity test passes on MariaDB. Phase 75 D-10 already gates this.

### Claude's Discretion

- Final wording of the `82-BACKLOG-AUDIT.md` doc (a simple markdown table mapping ID → SHA → one-line evidence is sufficient — planner produces).
- Whether the README path-change note for IN-04 is a single sentence or a small "Path changed in v1.11" paragraph.
- Whether to add the IN-02 annotation-order convention to CLAUDE.md (D-08) — promote if the convention is worth permanent enforcement, drop if it's a one-time normalisation.
- Whether the BACK-01 guard test needs Spring context (`@SpringBootTest` slice) or stays pure (no context) — depends on whether `BackupSchema.getExportOrder()` requires injected dependencies that the bean resolves lazily.
- Whether the BACK-03 IT can reuse an existing fixture (e.g., the Saison-2023 GROUPS fixture used by `BackupImportMariaDbSmokeIT`) or needs a fresh minimal fixture.
- Exact log-captor choice for IN-03 test (D-11): `OutputCaptureExtension` (Spring Boot built-in, recommended) vs LogCaptor library.
- Whether `BackupExecutedByResolverTest` needs `MockitoExtension` plus per-test `Mockito.mockStatic(SecurityContextHolder.class)` or a custom `SecurityContextHolderProvider` indirection — Mockito static-mock is the simplest, planner picks.
- Whether the test data for BACK-05 needs a richer fixture than the existing dev-profile Saison-1 fixture (which may have 0 rows in some of the 24 entities — e.g., `playoff_*` tables). If a 0-row entity short-circuits the parity test to "0 == 0" (trivially passing), the planner adds a richer fixture OR adds an explicit assertion that at least N entities have non-zero rows.
- Final SpotBugs run-per-commit cadence (D-27) — execution discretion.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 82: Backup Cleanup" — goal, depends-on (Phase 81), 5 requirement IDs (BACK-01..BACK-05), 5 success criteria
- `.planning/REQUIREMENTS.md` §"Backup Cleanup (BACK)" — BACK-01..BACK-05 line items
- `.planning/PROJECT.md` §"Current Milestone: v1.11" — milestone scope, includes the explicit Backup cleanup section
- `.planning/STATE.md` §"Deferred Items" — original 12-item carryover entry (PRE-discovery; obsoleted by 82-BACKLOG-AUDIT.md scope-reality)

### Phase 75 Original Review (source of truth for the 12 items)
- `.planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-REVIEW.md` (available via `git show 4f31489f:.planning/...`) — original 14-finding review (2 Critical + 8 Warning + 4 Info); Phase 82 resolves the 8 Warning + 4 Info; the 2 Critical were resolved in Phase 75 itself
- Pre-resolved item commits on master (catalogued in 82-BACKLOG-AUDIT.md):
  - CR-01 → `b39d003f fix(75): CR-01 preserve LinkedHashMap insertion order in audit event`
  - CR-02 → `4212a3d9 fix(75): CR-02 sweep orphan uploadsTarget before Step-1 revert`
  - WR-02 → `34cbecbb fix(75): WR-02 entityCount counts only entities that contributed rows`
  - WR-03 → `2f326b61 fix(75): WR-03 report audit-write failure in BackupImportException + flash`
  - WR-04 → `76a9b520 fix(75): WR-04 make staging .meta corruption explicit in audit source_filename`
  - WR-05 → `f2e9125d fix(75): WR-05 open backup ZIP once via ZipFile (eliminate 24x rescans)`
  - WR-06 → `a310e4eb fix(75): WR-06 surface BackupArchiveException reason in import failure flash`
  - WR-07 → `930b0788 fix(75): WR-07 pre-check MAX_ENTRIES before writing extracted upload file`
  - WR-08 → `ef38ca5d fix(75): WR-08 catch Throwable in execute() so OOM still writes audit row`
  - All 9 commits squash-merged to master via PR #121 (commit `45aabfd0`)

### Prior Phase Context (carry-forward)
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-CONTEXT.md` — SpotBugs gate active on every `verify`; suppressions in `config/spotbugs-exclude.xml`; lombok.config invariant
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-REVIEW.md` + `81-REVIEW-FIX.md` — Phase 81 just shipped, gate stable
- `.planning/phases/80-openrewrite-integration/80-CONTEXT.md` — branch-off-origin/master + milestone-branch precedent (`gsd/v1.11-tooling-and-cleanup`)

### Live Source (Phase 82 touch list)
- `src/main/java/org/ctc/backup/service/BackupImportService.java`:
  - Lines 469-476 (LinkedHashMap counts — already fixed via CR-01, do not touch)
  - Line 518 + 731-738 (`resolveExecutedBy()` private method — WR-01 removes this; replaced by `executedByResolver.resolve(...)`)
  - Lines 629-655 (`restoreAll` + `restoreOneTable` — BACK-03 adds counter; IN-03 escalates log level around line 644-646)
  - Line 635 (`new ZipFile(...)` — BACK-03 counter increment goes immediately before this)
  - Line 545 (`catch (Throwable t)` — already fixed via WR-08, do not touch)
- `src/main/java/org/ctc/backup/audit/DataImportAuditService.java`:
  - Lines 102 + 138-150 (`resolveExecutedBy(callerOverride)` private method — WR-01 removes; callers route through `executedByResolver.resolve(executedByCaller)`)
- `src/main/java/org/ctc/backup/restore/entity/*.java`:
  - 18 files with no-op `@RequiredArgsConstructor` (IN-01 removes the annotation): DriverRestorer, MatchRestorer, MatchScoringRestorer, MatchdayRestorer, PhaseTeamRestorer, PsnAliasRestorer, RaceAttachmentRestorer, RaceLineupRestorer, RaceRestorer, RaceResultRestorer, RaceScoringRestorer, RaceSettingsRestorer, SeasonDriverRestorer, SeasonPhaseGroupRestorer, SeasonPhaseRestorer, SeasonRestorer, SeasonTeamRestorer, TeamRestorer
  - 6 files with `@Component @Slf4j` order (IN-02 swaps to `@Slf4j @Component`): CarRestorer, PlayoffMatchupRestorer, PlayoffRestorer, PlayoffRoundRestorer, PlayoffSeedRestorer, TrackRestorer
- `src/main/java/org/ctc/backup/schema/BackupSchema.java`:
  - Line 17 `getExportOrder()` Javadoc + line 51 method signature — referenced by BACK-01 guard test (do not modify)
  - `SCHEMA_VERSION` constant — referenced by BACK-01 guard test (do not modify; assertion target)
- `src/main/resources/application.yml`:
  - Line 6 `import-backups-dir: data/.import-backups` — IN-04 changes to `data/${spring.profiles.active:dev}/import-backups`
- `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java`:
  - Lines 1-80 reviewed; `@Nested` H2RoundTripTests + MariaDbRoundTripTests already defined — BACK-05 adds one `@Test` method per nested class

### Codebase Maps
- `.planning/codebase/TESTING.md` §"Test Categorization (@Tag)" — `*IT.java` requires `@Tag("integration")`; `@Nested` inherits parent's tag; BACK-03 IT must be tagged
- `.planning/codebase/CONVENTIONS.md` §"Lombok Usage" — `@RequiredArgsConstructor` on services with `final` fields; IN-01 enforces this against restorers that have NO `final` fields
- `.planning/codebase/STRUCTURE.md` — package layout `org.ctc.backup.{controller,service,io,dto,audit,lock,event,restore,schema,serialization}` — confirms BackupExecutedByResolver belongs in `org.ctc.backup.audit`
- `.planning/codebase/STACK.md` — Spring Boot 4.0.6 + JUnit 5 + Mockito; confirms Mockito-extension and `@SpringBootTest` slice patterns

### External (Spring + JUnit + Mockito)
- https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging.log-capture — `OutputCaptureExtension` for IN-03 WARN-log test
- https://github.com/mockito/mockito/wiki/Using-Mockito-with-Java-25 — Mockito static-mock guidance for SecurityContextHolder mocking in WR-01 test

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`BackupRoundTripIT` `@Nested` H2RoundTripTests + MariaDbRoundTripTests** (Phase 73-03 + Phase 77 extension) — structural template for BACK-05. The 3-entity (Race + SeasonDriver + Team) row-count + SHA-256 byte-equality spot-check exists; BACK-05 adds one `@Test` per nested class iterating `getExportOrder()` for all 24 entities. Existing helpers (`runFullRoundTrip()`-equivalent) reused without modification.
- **`BackupImportRollbackIT`** — companion IT to BackupRoundTripIT; runs the rollback-injection path. BACK-05 changes do not touch this file but its existence confirms `@Tag("integration")` + `@SpringBootTest` IT pattern in this package.
- **`BackupSchema.SCHEMA_VERSION` constant + `getExportOrder()` method** (line 17 + 51) — the BACK-01 guard targets; both already exist, the test just pins their current values.
- **`@VisibleForTesting` package-private field pattern** — used elsewhere in the codebase (planner verifies); BACK-03 `zipOpenCounter` adopts the same shape (no Lombok annotation needed — plain `AtomicInteger`).
- **`@Component @RequiredArgsConstructor` injection pattern with `final Environment`** — multiple existing beans inject `Environment` (e.g., `DataImportAuditService:80-83`); WR-01's `BackupExecutedByResolver` follows the same pattern.
- **`application.yml` `${spring.profiles.active:dev}` interpolation** — already used by `staging-dir: data/${spring.profiles.active:dev}/backup-staging`; IN-04 reuses the same expression.

### Established Patterns
- **One commit per REVIEW.md ID** (Phase 75 precedent — see git log `fix(75): WR-XX`) — Phase 82 follows the same shape for WR-01 + IN-01..IN-04. Commit message includes the ID.
- **Atomic-commit choreography on a phase feature branch** (Phase 80 D-07, Phase 81 D-12) — N small commits, then a phase-PR with squash-merge.
- **Test categorisation by `@Tag`** (CLAUDE.md `## Constraints`) — unit tests untagged; `*IT.java` tagged `@Tag("integration")`. BACK-03 IT MUST carry the tag.
- **English-only file content** (CLAUDE.md `## Language`) — all new docs, code comments, log messages, test descriptions in English. Discussion was German; written artifacts are English.
- **No inline styles / no controller logic** — irrelevant to Phase 82 (no UI changes, no controller touches).
- **SpotBugs suppression discipline** (CLAUDE.md `### Static Analysis (SpotBugs + find-sec-bugs)`) — every `<Match>` carries XML rationale + code cross-ref; no `@SuppressWarnings("all")`. WR-01's new bean is unlikely to trigger any new pattern; BACK-03 counter is package-private (no `EI_EXPOSE_REP`).
- **Two-phase log assertion** — `OutputCaptureExtension` returns capture object; assert `capture.toString()` contains expected substring. Used elsewhere.

### Integration Points
- **`pom.xml`** — NO changes. No new dependency. Mockito + Spring Boot test + AssertJ + OutputCaptureExtension all already on classpath.
- **`config/spotbugs-exclude.xml`** — likely no new entries needed. Re-check after WR-01 commit; if SpotBugs flags the new `BackupExecutedByResolver` bean for `EI_EXPOSE_REP` (it shouldn't — bean has no exposable fields beyond `Environment` which is itself a Spring proxy), add a per-class entry with rationale per Phase 81 D-09.
- **`CLAUDE.md`** — single-line addition under `### Lombok Usage` for IN-02 annotation-order convention (D-08; discretion-granted). No other CLAUDE.md edits.
- **`README.md` "## Backup & Restore" section** — single-sentence note for IN-04 path change. Wiki page `Backup-and-Restore.md` may also need a path-change note (planner checks).
- **`application-{dev,local,docker,prod}.yml`** — none touched. Profile-isolation lives in the root `application.yml` line 6 expression. Profile-specific overrides exist for `staging-dir` (checked during scout); IN-04 mirrors the staging-dir shape exactly so no per-profile overrides are needed.
- **Flyway `V*.sql`** — NONE. No schema change. CLAUDE.md invariant respected.

### What This Phase Does NOT Touch
- `BackupController` — already covered by WR-06 (merged); WR-01 doesn't touch it (controller doesn't resolve executedBy).
- `BackupImportPostCommitListener` — already covered by CR-02 (merged); no Phase 82 touch.
- `BackupArchiveService` — already covered by WR-07 (merged); no Phase 82 touch.
- `BackupManifest` record — wire contract; SCHEMA_VERSION stays at 1.
- 24 `MixIn` classes (Jackson serialisation) — wire contract; not touched.
- `ImportLockService` / `ImportLockBannerAdvice` / `ImportLockedWriteRejector` — Phase 76 scope, not Phase 82.
- `DataImportAudit` entity / Flyway V7 — DB schema unchanged.
- Test-data fixtures (`TestDataService`, `DevDataSeeder`, `DemoDataSeeder`) — out of scope; Phase 83 handles `DevDataSeeder` profile-widening.

</code_context>

<specifics>
## Specific Ideas

- Branch name suggestion: `feature/backup-cleanup` (planner's discretion — `fix/backup-cleanup-review-items` also acceptable).
- Phase-PR target: `gsd/v1.11-tooling-and-cleanup` (per PROJECT.md tail + Phase 81 precedent).
- Audit doc filename: `82-BACKLOG-AUDIT.md` (mirrors phase-doc naming convention `{padded_phase}-*.md`).
- `82-BACKLOG-AUDIT.md` shape — simple markdown table:
  ```
  | REVIEW ID | Status | Commit SHA | Subject |
  | --------- | ------ | ---------- | ------- |
  | CR-01 | resolved in Phase 75 (merged via PR #121) | b39d003f | fix(75): CR-01 preserve LinkedHashMap insertion order in audit event |
  | CR-02 | resolved in Phase 75 (merged via PR #121) | 4212a3d9 | fix(75): CR-02 sweep orphan uploadsTarget before Step-1 revert |
  | WR-01 | resolved in Phase 82 (commit on feature branch) | <new SHA> | fix(82): WR-01 extract BackupExecutedByResolver bean |
  | ... |
  ```
- Coverage discipline: capture pre-Phase-82 JaCoCo line coverage from Phase 81 verification (87.80 %), assert post-Phase-82 is within ±0.5 pp (or ≥ 82 % gate, whichever is the stricter live floor). Same pattern as Phase 81 D-04 smoke-check.
- Per CLAUDE.md `feedback_test_call_optimization`: NO `./mvnw verify` between fix commits — targeted tests only. Final `./mvnw verify -Pe2e` ONE time before raising the PR.
- Per CLAUDE.md `feedback_e2e_verification`: Phase-end verification uses `-Pe2e` to confirm Playwright E2E classpath stays clean (Phase 82 doesn't touch Playwright code but BACK-03 IT loads full Spring context which exercises Playwright bean wiring).
- Per CLAUDE.md `feedback_clean_maven_build_authority`: if any IDE shows a stale compilation error after IN-01/IN-02 (Lombok annotation removal can confuse IDE caches), run `./mvnw clean test-compile` BEFORE trusting the IDE.
- Per CLAUDE.md `feedback_subagent_stability`: if planner dispatches subagents for the 5 fix commits, EACH subagent prompt must name the active branch + explicitly forbid `git stash`/`git checkout`/`git reset`/branch switching, AND name the single REVIEW.md ID it owns (no scope creep into sibling commits).
- Per CLAUDE.md `feedback_milestone_branch`: Phase-82 feature branch lives off `origin/master` per CLAUDE.md `## Git Workflow`; PR target is `gsd/v1.11-tooling-and-cleanup` (the v1.11 milestone branch, which itself targets `master` on milestone close).

</specifics>

<deferred>
## Deferred Ideas

- **CLAUDE.md annotation-order convention as a CI gate** — D-08 documents the convention but does not enforce it via SpotBugs or Checkstyle. If future PRs reintroduce the wrong order, escalate to a SpotBugs custom detector or accept it as code-review discipline. Promote to a phase only if drift recurs.
- **`BackupSchema` auto-discovery of entities via Hibernate Metamodel** — currently the EXPORT_ORDER size is implicit (Kahn topo-sort over JPA Metamodel). BACK-01 guard pins 24; when v1.12 adds a 25th entity, the guard fails loud and the developer updates the guard + writes a SCHEMA_VERSION bump migration. The fail-loud behaviour is the deferred safety net.
- **`82-BACKLOG-AUDIT.md` as a project-wide pattern** — if v1.12+ phases also inherit pre-resolved items from prior milestones, formalise the BACKLOG-AUDIT doc as a phase artifact template. Not done in v1.11.
- **Existing `data/.import-backups/<old-path>/` artifact migration script** — D-13 explicitly defers this. If operator complaints surface, a one-shot `mv data/.import-backups data/${profile}/import-backups` script is a Phase 999 backlog item.
- **Bumping `BackupSchema.SCHEMA_VERSION` to 2** — explicitly forbidden by SC#1. When the 24-entity wire contract needs to change (new column, new entity, removed column), that's its own dedicated phase with a migration + schema-mismatch IT update.
- **Test for `BackupRestoreZipOpenCountIT` on MariaDB profile** — BACK-03 IT targets H2 by default. If MariaDB-specific `ZipFile` lifecycle (e.g., async file lock behaviour on Linux ext4) differs from H2, a `@Nested` MariaDB variant of the counter test could land later. Defer until a regression surfaces.
- **Promoting the `BackupExecutedByResolver` to a more general `ExecutorContextResolver`** — only one bean currently needs this. If a future audit-bearing service emerges (e.g., manual data correction, bulk-edit), generalise. Single-use today.

</deferred>

---

*Phase: 82-backup-cleanup*
*Context gathered: 2026-05-16*
