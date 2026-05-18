---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
plan: 07
subsystem: backup-import
tags:
  - after-commit
  - transactional-event-listener
  - atomic-move
  - filesystem-mutation
  - audit-row
  - integration-test
  - h2
requirements:
  - IMPORT-06
  - IMPORT-07
dependency_graph:
  requires:
    - 75-01 (BackupImportSucceededEvent, UploadsRestoreException)
    - 75-02 (DataImportAuditService.recordResult REQUIRES_NEW writer)
    - 75-03 (TeamRestorer 2-pass)
    - 75-06 (BackupImportService.execute orchestrator + event publish)
  provides:
    - "BackupImportPostCommitListener — AFTER_COMMIT D-09 move-triple + success audit + staging cleanup"
    - "BackupImportPostCommitIT — H2 IT proving the 3 D-09 scenarios"
    - "TeamRestorerIT — H2 IT proving 2-pass FK reconstruction end-to-end"
  affects:
    - 75-08 (controller upgrade — flash messages read auditUuid from BackupImportException / BackupImportResult)
    - 75-09 (rollback IT — same audit-row contract on the failure path)
    - 75-10 (MariaDB smoke — same listener exercised on real MariaDB)
tech_stack:
  added: []
  patterns:
    - "@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) for post-commit file-system mutations"
    - "Best-effort REQUIRES_NEW audit-row write on Step-1 / Step-2 failure path"
    - "Step-1 revert on Step-2 failure with loud ERROR log + audit success=false"
    - "Direct JdbcTemplate.update(INSERT_SQL) for audit-row writes (bypasses Hibernate identity-strategy pitfalls)"
    - "OutputCaptureExtension for ERROR-log assertions in failure-path ITs"
    - "Per-test cleanup of REQUIRES_NEW-committed rows (outer @Transactional cannot roll inner-tx back)"
key_files:
  created:
    - src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java
    - src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java
    - src/test/java/org/ctc/backup/restore/entity/TeamRestorerIT.java
  modified:
    - src/main/java/org/ctc/backup/audit/DataImportAuditService.java
    - src/test/java/org/ctc/backup/audit/DataImportAuditServiceTest.java
decisions:
  - "Listener has NO @Transactional annotation on the handler method — REQUIRES_NEW is owned by DataImportAuditService.recordResult itself (Plan 02), per RESEARCH §9 Spring 6.1+ rule"
  - "Step 1 + Step 2 failures throw UploadsRestoreException AFTER writing a success=false audit row best-effort — files in known state, operator signal in DB + logs"
  - "Step 3 + Step 4 failures only log ERROR / WARN, do NOT rethrow — Steps 1+2 already mutated the filesystem, exception propagation would mask the partial-success state"
  - "Best-effort Step-1 revert on Step-2 failure attempts Files.move(uploads-old -> uploadsTarget) — log warn on success, log error on double-failure (manual recovery required)"
  - "Rule-1 fix in DataImportAuditService: switch from repository.save(...) to JdbcTemplate.update(INSERT_SQL) — both repository.save() and em.persist() fail on pre-allocated UUID with @GeneratedValue(strategy=UUID), and a direct JDBC INSERT is consistent with the Plan 75 AuditingEntityListener-bypass design"
  - "BackupImportPostCommitIT publishes events inside TransactionTemplate.executeWithoutResult so the AFTER_COMMIT listener fires after the inner tx commits"
  - "TeamRestorerIT wipes ALL tables in FK-reverse via BackupSchema.getExportOrder().reversed() inside @Transactional + @Rollback — simpler than a hand-curated subset and exactly mirrors production wipe order"
metrics:
  duration_sec: 2431
  duration_human: "~41 minutes"
  tasks_completed: 2
  files_created: 3
  files_modified: 2
  completed_date: "2026-05-14"
commits:
  - hash: cead397
    type: feat
    message: "feat(75-07): add BackupImportPostCommitListener for AFTER_COMMIT move-triple"
  - hash: 952946c
    type: test
    message: "test(75-07): add BackupImportPostCommitIT + TeamRestorerIT (H2 real-DB)"
---

# Phase 75 Plan 07: AFTER_COMMIT Post-Commit Listener Summary

AFTER_COMMIT listener that owns the post-commit `uploads/`-tree atomic-move-triple (D-09) +
the success-path REQUIRES_NEW audit-row write + the staged-ZIP cleanup. The listener consumes
`BackupImportSucceededEvent` (Plan 01 record) published by `BackupImportService.execute(...)`
(Plan 06) as the last statement of the outer `@Transactional` method — Spring buffers delivery
until commit, so the file-system mutations land outside the JPA transaction by construction.
Two H2 integration tests pin the contract: `BackupImportPostCommitIT` proves all three D-09
scenarios (happy path, Step-2-fails-with-Step-1-revert, Step-3-audit-fails-with-swallow), and
`TeamRestorerIT` proves the Plan 03 2-pass discipline reconstructs the `parent_team_id`
self-FK chain end-to-end on a real database.

## Performance

- **Duration:** ~41 minutes
- **Started:** 2026-05-14T09:20:14Z
- **Completed:** 2026-05-14T10:00:45Z
- **Tasks:** 2 (1 listener + 1 IT bundle)
- **Files created:** 3 (1 main + 2 test)
- **Files modified:** 2 (Rule-1 audit fix + Plan-02 test follow-up)

## Accomplishments

- **`BackupImportPostCommitListener`** annotated with
  `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. NO `@Transactional`
  on the handler method itself — REQUIRES_NEW is owned by `DataImportAuditService.recordResult`
  (Plan 02), satisfying the Spring 6.1+ rule that listener methods may not run in the outer
  (already-committed) transaction (RESEARCH §9).
- **D-09 atomic-move-triple implemented verbatim:**
  - Step 1: `Files.move(uploadsTarget → importBackupDir/uploads-old, ATOMIC_MOVE)`.
  - Step 2: `Files.move(uploadsNewDir → uploadsTarget, ATOMIC_MOVE)`. On failure: best-effort
    revert of Step 1 via `Files.move(uploads-old → uploadsTarget, ATOMIC_MOVE)`, plus
    `success=false` audit row + `throw UploadsRestoreException`.
  - Step 3: REQUIRES_NEW success audit-row write (success=true). On failure: ERROR log only,
    NO rethrow (files already in target state).
  - Step 4: `backupImportService.deleteStagingFile(stagingId)` — best-effort cleanup. WARN log
    on failure.
- **Loud-fail contract:** every failure path emits an `ERROR`-level SLF4J line with the exact
  Step number (Plan 08 controller flash relies on this).
- **`BackupImportPostCommitIT`** — 3 scenarios:
  - `givenAllStepsSucceed_whenEventPublishedInsideTx_thenAfterCommitMoveTripleCompletesAndAuditRowSuccessIsTrue`
  - `givenStep2FailsBecauseUploadsNewMissing_whenEventPublished_thenStep1RevertedAndAuditSuccessFalse`
  - `givenStep3AuditWriteFails_whenEventPublished_thenFilesStillInTargetStateAndExceptionNotRethrown`
- **`TeamRestorerIT`** — 2 scenarios:
  - `givenTeamsJsonWithParentChildRelations_whenRestoreInvoked_thenAllTeamsAndParentTeamFKsRestoredOnH2`
  - `givenTeamsWithoutAnySubTeams_whenRestoreInvoked_thenPass2Skipped`
- **Rule-1 fix in `DataImportAuditService`:** switched from `repository.save(...)` to
  `JdbcTemplate.update(INSERT_SQL)`. Both `repository.save(...)` and `em.persist(...)` fail on
  the pre-allocated UUID (Spring Data merge raises optimistic-lock; Hibernate persist raises
  "Detached entity"); the direct JDBC INSERT is consistent with the Plan 75
  AuditingEntityListener-bypass design.

## Tasks Executed

### Task 1 — `BackupImportPostCommitListener` — `cead397`

178-line `@Slf4j @Component` listener with explicit constructor (`@RequiredArgsConstructor` was
NOT used because Lombok-generated constructors do not reliably forward annotations on
parameters; Phase 74 / 75 prefer explicit ctors for consistency). The handler method
`onImportSucceeded(BackupImportSucceededEvent)` implements Steps 1-4 verbatim from the plan's
`<interfaces>` block. The private `recordResultBestEffort(event, success)` helper wraps the
Step-1 + Step-2 failure-path audit-row write so a double-failure (audit write itself fails)
logs ERROR but does NOT mask the original `UploadsRestoreException`.

Javadoc cross-references D-09, RESEARCH §2 / Pattern 2, RESEARCH §9 (Spring 6.1+
REQUIRES_NEW rule), and the PATTERNS Pitfall §2 note that AFTER_COMMIT swallows exceptions
silently — the recovery contract is best-effort revert + loud log + audit row, not exception
propagation.

Acceptance greps:
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`: 1 ✔
- `StandardCopyOption.ATOMIC_MOVE`: 3 (Step 1 + Step 2 + Step-1 revert) ✔
- `UploadsRestoreException` references: 7 (catch sites + Javadoc + throws) ✔
- `dataImportAuditService.recordResult`: 2 (success-path + helper) ✔
- `deleteStagingFile`: 3 (call site + Javadoc references) ✔
- Bare `@Transactional` on the listener method: 0 ✔

### Task 2 — ITs + Rule-1 audit-service fix + Plan-02 test follow-up — `952946c`

Two new Failsafe ITs plus a Rule-1 fix in `DataImportAuditService` (and its existing Plan-02
unit test).

`BackupImportPostCommitIT` (`@SpringBootTest @ActiveProfiles("dev") @ExtendWith(OutputCaptureExtension.class)`):
- Uses `TransactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(...))`
  so the AFTER_COMMIT listener fires after the inner tx commits.
- Tests the full state contract: file-system layout (`uploadsTarget`, `uploads-old/`,
  `uploads-new/`), persisted audit-row, captured ERROR log lines.
- Test 2 does NOT call `assertThatThrownBy`: Spring's
  `TransactionalApplicationListenerMethodAdapter` logs listener exceptions rather than
  re-throwing through `publishEvent` by default. Behaviour is therefore proven via state +
  log assertions.
- Test 3 uses `@MockitoSpyBean DataImportAuditService` + `doThrow(...).when(...)` to simulate
  the Step-3 audit-write failure.

`TeamRestorerIT` (`@SpringBootTest @ActiveProfiles("dev") @Transactional @Rollback`):
- Wipes all 24 tables in FK-reverse order via `BackupSchema.getExportOrder().reversed()`
  inside the test transaction (rolled back at end so the dev fixture is undisturbed).
- Builds 3 JSON rows mirroring the `TeamMixIn` JSON shape (`parentTeam` as bare UUID string,
  NOT a `{id:...}` object) — the plan text described `{id:T1id}` but the real `TeamMixIn`
  `@JsonIdentityReference(alwaysAsId=true)` produces bare strings (verified against
  `TeamRestorer.restore` which calls `UUID.fromString(row.get("parentTeam").asText())`).

## Verification

| Check | Result |
| ----- | ------ |
| `./mvnw -q compile` | BUILD SUCCESS |
| `./mvnw -q test-compile` | BUILD SUCCESS |
| `BackupImportPostCommitIT` — 3 tests | 3/0/0 (~29 s with Spring boot) |
| `TeamRestorerIT` — 2 tests | 2/0/0 (~2 s) |
| `BackupImportExecuteIT` (Plan 06 — regression) | 2/0/0 |
| `BackupArchiveExtractUploadsIT` (Plan 06 — regression) | 3/0/0 |
| Wave-2 IT bundle (4 ITs combined) | 10/0/0 |
| `DataImportAuditServiceTest` (Plan 02 — updated) | 3/0/0 |
| `DataImportAuditSerializationTest` (Plan 02 — regression) | 1/0/0 |
| `PlayoffRestorerTest` (Plan 05 — regression) | 4/0/0 |
| `TeamRestorerTest` (Plan 03 — regression) | 4/0/0 |
| `BackupImportServiceIT` (Phase 74 — regression) | 4/0/0 |
| `BackupStagingCleanupIT` (Phase 74 — regression) | 4/0/0 |

Acceptance greps for Task 2:

| Check | Result |
| ----- | ------ |
| PostCommitIT scenarios (3) | 3 ✔ |
| TeamRestorerIT scenarios (2) | 2 ✔ |
| Both ITs use `@SpringBootTest` | 2/2 ✔ |
| PostCommitIT references `uploadsTarget` / `uploadsOld` / `auditRepository` | 40 matches ✔ |
| TeamRestorerIT references `getParentTeam` | 5 ✔ |

## Decisions Made

1. **Listener method carries NO `@Transactional` annotation.** REQUIRES_NEW is owned by
   `DataImportAuditService.recordResult` itself (Plan 02). RESEARCH §9 documents the Spring
   6.1+ rule that a `@TransactionalEventListener` method cannot run in the outer (already
   committed) JPA transaction; the audit-service-side propagation is the exact contract.

2. **Step 1 / Step 2 failure-path audit write is "best-effort".** `recordResultBestEffort(...)`
   wraps `recordResult(...)` in try/catch — a failure here must NOT mask the original
   `UploadsRestoreException`. The double-failure case (audit write fails AND uploads move
   fails) gets an ERROR log per call, then the original cause propagates.

3. **Step 3 / Step 4 failures do NOT throw.** Steps 1+2 already mutated the filesystem; an
   exception thrown out of the listener method (after Step 2 succeeded) would put the system
   in an inconsistent state from the operator's perspective. The Plan 08 controller flash
   strategy is to surface the audit-id; the operator reads the SLF4J log if Step 3 failed.

4. **Switched `DataImportAuditService` from `repository.save(...)` to direct JDBC INSERT
   (Rule-1 fix).** Both `repository.save(...)` (Spring Data → `em.merge(...)`) and
   `em.persist(...)` fail on a brand-new row with a pre-allocated UUID +
   `@GeneratedValue(strategy=UUID)`:
   - `repository.save(...)` triggers `ObjectOptimisticLockingFailureException` ("Row was
     already updated or deleted by another transaction") because merge selects-first-then-
     updates and the missing row is interpreted as a race.
   - `em.persist(...)` raises `EntityExistsException` ("Detached entity passed to persist")
     because Hibernate's UUID strategy flags any non-null pre-allocated UUID as "already
     persisted elsewhere".
   - Direct `JdbcTemplate.update(INSERT_SQL)` sidesteps both traps. The pattern is consistent
     with the Plan 75 design driver (24 `EntityRestorer`s all use `JdbcTemplate.batchUpdate`
     to bypass `AuditingEntityListener` — same reasoning applies to the audit-row writer that
     deliberately sets `executed_at` explicitly).

5. **`BackupImportPostCommitIT` Test 2 does NOT use `assertThatThrownBy`.** Spring's
   `TransactionalApplicationListenerMethodAdapter` logs listener exceptions rather than
   re-throwing through `eventPublisher.publishEvent` by default. The Step-2 failure contract
   is therefore proven via:
   - file-system state (Step-1 revert restored `uploadsTarget`, `uploads-old/` is gone),
   - persisted audit row (success=false),
   - captured ERROR log line ("AFTER_COMMIT Step 2 failed", "UploadsRestoreException").

6. **`TeamRestorerIT` wipes ALL 24 tables in FK-reverse order via `BackupSchema`.** A
   hand-curated subset would only cover the direct FK-referrers (races, matches, season_drivers,
   season_teams, phase_teams, race_lineups, playoff_seeds, playoff_matchups, ...) — but those
   tables themselves reference races/matches/etc., which forces an arbitrary cascade order
   that the JPA schema knows but the test does not. Reusing `BackupSchema.getExportOrder()
   .reversed()` exactly mirrors what `BackupImportService.wipeAllTables` does in production
   (Plan 06), keeping the test in sync with the schema's truth source.

7. **`TeamRestorerIT.givenTeamsWithoutAnySubTeams_whenRestoreInvoked_thenPass2Skipped` was
   simplified to drop the `OutputCaptureExtension` assertion.** The original draft asserted on
   the restorer's DEBUG log line `"pass2Rows=0"`, but DEBUG-level logging for
   `org.ctc.backup.restore.entity` is not enabled in the dev profile. The behaviour is fully
   covered by:
   - `SELECT COUNT(*) FROM teams WHERE parent_team_id IS NOT NULL` returning 0,
   - `SELECT COUNT(*) FROM teams` returning exactly the input size (2).

   The Plan 03 unit test `TeamRestorerTest` provides the additional assertion that the second
   `batchUpdate` is never invoked — that needs a mocked `JdbcTemplate` which a real-DB IT
   cannot provide.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `DataImportAuditService.recordResult` cannot persist pre-allocated UUID rows**

- **Found during:** Task 2 IT execution (`BackupImportPostCommitIT` Test 1 + Test 3).
- **Issue:** The Plan 02 `recordResult` used `DataImportAuditRepository.save(audit)` which
  Spring Data dispatches to `em.merge(...)` when the ID is non-null (Phase 75 deliberately
  pre-allocates the UUID so the controller can echo it in the flash message BEFORE the outer
  tx commits — D-15). On a brand-new row, this raises
  `org.springframework.orm.ObjectOptimisticLockingFailureException` ("Row was already updated
  or deleted by another transaction"). Switching the implementation to `em.persist(audit)`
  did not help — Hibernate's `@GeneratedValue(strategy=UUID)` logic instead raised
  `jakarta.persistence.EntityExistsException` ("Detached entity passed to persist").
- **Fix:** Replaced the persist call with a direct `JdbcTemplate.update(INSERT_SQL, ...)`
  call. The pattern is consistent with the rest of Phase 75 (24 `EntityRestorer`s already
  use `JdbcTemplate.batchUpdate` to bypass `AuditingEntityListener` — same design rationale).
- **Files modified:**
  - `src/main/java/org/ctc/backup/audit/DataImportAuditService.java`
  - `src/test/java/org/ctc/backup/audit/DataImportAuditServiceTest.java` (updated to read
    back rows via the real repository; mocking removed; per-test `@AfterEach` cleanup added
    because REQUIRES_NEW inner-tx commits cannot be rolled back by a `@Transactional`
    outer test method).
- **Commit:** `952946c` (bundled with the Task 2 ITs).

**2. [Rule 3 - Blocking Issue] `TeamRestorerIT` initial wipe-list was too narrow**

- **Found during:** Task 2 IT execution.
- **Issue:** The first cut wiped only `race_lineups`, `season_drivers`, `phase_teams`,
  `season_teams` before `teams` — but the dev fixture also seeds `matches`, `races`,
  `playoff_matchups`, `playoff_seeds`, all of which carry FKs to `teams`. H2 raised
  `Referential integrity constraint violation: FK_MATCH_HOME_TEAM`.
- **Fix:** Replaced the hand-curated subset with
  `BackupSchema.getExportOrder().reversed()` — mirrors production wipe order in
  `BackupImportService.wipeAllTables()` (Plan 06). The dev fixture is restored on
  `@Rollback`.
- **Files modified:** `src/test/java/org/ctc/backup/restore/entity/TeamRestorerIT.java`
- **Commit:** `952946c`.

**3. [Rule 1 - Bug] `TeamRestorerIT` Pass-2 log-line assertion was over-specified**

- **Found during:** Multi-test run after introducing Rule-1 fix #1.
- **Issue:** `givenTeamsWithoutAnySubTeams_whenRestoreInvoked_thenPass2Skipped` originally
  asserted on captured stdout containing `"pass2Rows=0"`. That log line is at DEBUG level,
  and dev-profile logging defaults to INFO — the assertion was structurally guaranteed to
  fail.
- **Fix:** Dropped the OutputCapture assertion. The Pass-2 skip is fully observable via the
  zero count of `parent_team_id IS NOT NULL` + the total row-count matching exactly the
  input size. The unit test `TeamRestorerTest` already asserts (via mocked JdbcTemplate)
  that the second `batchUpdate` is never invoked.
- **Files modified:** `src/test/java/org/ctc/backup/restore/entity/TeamRestorerIT.java`
- **Commit:** `952946c`.

### Authentication Gates

None.

## Known Stubs

None. The listener is production code wired end-to-end against the Plan 06 publisher; the
ITs exercise the real listener against the real `DataImportAuditService` and the real
`BackupImportService.deleteStagingFile`.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: filesystem-write | `BackupImportPostCommitListener` | Three `Files.move(..., ATOMIC_MOVE)` operations on operator-owned paths. Mitigation: paths come from `BackupImportSucceededEvent` (Plan 01 record) — published by `BackupImportService.execute(...)` which validates the staging-file UUID against the staging-dir layout. Plan 06's `extractUploadsTo` already validates entry names via `PathTraversalGuard` before extraction to `uploadsNewDir`. The listener does not accept any external input beyond the trusted event payload. |
| threat_flag: native-sql | `DataImportAuditService.recordResult` | New native-SQL INSERT surface for audit rows. Mitigation: SQL is a `static final String` literal with `?` placeholders; all 8 parameters are passed via `JdbcTemplate.update(sql, args...)` (parameterized — no concatenation). UUIDs / Timestamps / booleans are typed parameters. Table name is hard-coded `data_import_audit`. |

## TDD Gate Compliance

Plan is `type: execute` — plan-level RED/GREEN gate sequencing is not enforced.

Task 1 ships the listener implementation in a `feat:` commit; Task 2 ships the locking ITs +
the Rule-1 audit-service fix + the Plan-02 test follow-up in a `test:` commit. The
implementation is unreachable from production callers in Plan 07's scope (Plan 08 wires the
flash strings; Plan 09 wires the rollback IT) — the IT contract is locked by the green test
run at the Task 2 boundary.

## Self-Check: PASSED

**Files checked (all FOUND):**

- `src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java`
- `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java`
- `src/test/java/org/ctc/backup/restore/entity/TeamRestorerIT.java`
- `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` (modified — Rule-1 fix)
- `src/test/java/org/ctc/backup/audit/DataImportAuditServiceTest.java` (modified — follow-up)

**Commits checked (all FOUND in `git log`):**

- `cead397` — feat(75-07): add BackupImportPostCommitListener for AFTER_COMMIT move-triple
- `952946c` — test(75-07): add BackupImportPostCommitIT + TeamRestorerIT (H2 real-DB)

---
*Phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat*
*Completed: 2026-05-14*
