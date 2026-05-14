---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
plan: 08
subsystem: backup-import
tags:
  - controller
  - flash-messages
  - d-15
  - d-17
  - playwright-e2e
  - real-execute
requirements:
  - IMPORT-05
  - IMPORT-07
dependency_graph:
  requires:
    - 75-06 (BackupImportService.execute orchestrator + BackupImportException carrier)
    - 75-07 (BackupImportPostCommitListener — owns the UploadsRestoreException throw site)
    - 74-* (BackupImportConfirmForm @AssertTrue, admin/backup-confirm.html template, /admin/backup/import-execute endpoint, BackupImportService.reparse, BackupArchiveService.writeZip)
  provides:
    - "BackupController.importExecute upgraded — real BackupImportService.execute(...) delegation"
    - "D-15 #1 success flash: 'Import completed. {N} rows restored across {M} tables.'"
    - "D-15 #2 failure flash on BackupImportException with audit UUID placeholder"
    - "D-15 #3 defensive soft-fail flash on UploadsRestoreException (rare-to-never path)"
    - "BackupImportE2ETest happy-path walkthrough now exercises the real wipe+restore round-trip"
  affects:
    - 75-09 (parallel wave 4 — BackupImportRollbackIT exercises the failure branch of the same controller)
    - 75-10 (Live MariaDB UAT — human verifies the rendered flash on a real DB)
tech_stack:
  added: []
  patterns:
    - "Controller-side String.format binding for locked D-15 flash strings (no i18n bundle yet — deferred per Phase 74 D-02 carry-forward)"
    - "Catch chain ordered by request-lifecycle origin (ZIP-parse -> AFTER_COMMIT-listener -> @Transactional-body) with sibling-RuntimeException Javadoc note"
    - "PATTERNS Q5 resolution: no GlobalExceptionHandler.UploadsRestoreException mapping — internal catch suffices"
    - "Playwright Pattern.compile regex for flash string assertion (allows runtime-variable row/entity counts)"
    - "MockMvc @MockitoBean BackupImportService for unit testing the controller without spinning up the full restore orchestrator"
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/backup/BackupController.java
    - src/test/java/org/ctc/backup/BackupControllerTest.java
    - src/test/java/org/ctc/e2e/BackupImportE2ETest.java
decisions:
  - "Phase 74 stub-flash string ('Validation succeeded. Import execution will be enabled in Phase 75.') is REMOVED from both production code (BackupController) and tests (BackupImportE2ETest). The stub no longer exists in the codebase."
  - "D-15 #1 success flash is unconditional when BackupImportService.execute returns a BackupImportResult. The DB transaction has committed by that point; uploads-restore soft-fail manifests via SLF4J ERROR + data_import_audit.success=false, not via a controller-side flash (planner-locked per CONTEXT D-15 #3 commentary)."
  - "Catch chain order: BackupArchiveException -> IOException -> UploadsRestoreException -> BackupImportException. Order is INDEPENDENT because all three D-15 exception types are siblings extending RuntimeException (no inheritance between them). The chosen order matches the request lifecycle (ZIP-parse -> AFTER_COMMIT-listener -> @Transactional-body)."
  - "UploadsRestoreException catch is DEFENSIVE. The AFTER_COMMIT listener (Plan 07) fires AFTER the controller's redirect response is built, so its UploadsRestoreException typically does NOT propagate back to this thread. The defensive catch covers a future refactor that invokes the move-triple from a non-AFTER_COMMIT path; the auditUuid is reported as 'unknown' because the exception carrier does not transport the UUID."
  - "PATTERNS Open-Question §5 (UploadsRestoreException GlobalExceptionHandler mapping) is RESOLVED HERE: no global handler is added. The controller's local catch is sufficient per RESEARCH §7 defensive-default deferred."
  - "BackupControllerTest uses @MockitoBean for BackupImportService — pure controller unit test. The full execute orchestrator is exercised end-to-end by BackupImportExecuteIT (Plan 06), BackupImportPostCommitIT (Plan 07), and BackupImportRollbackIT (parallel Plan 09)."
  - "BackupControllerTest.givenInvalidConfirmForm — the controller re-renders admin/backup-confirm.html on binding failure; that template reads preview.totalImportedRows. Stubbing reparse(...) to return a minimal BackupImportPreview prevents SpEL evaluation failure during the re-render path."
  - "BackupImportE2ETest happy-path: success-flash assertion uses Pattern.compile('Import completed\\. \\d+ rows restored across \\d+ tables\\.') so the test stays insensitive to dev-fixture row-count drift across phases."
  - "Task 3 checkpoint:human-verify SKIPPED for this autonomous execution per orchestrator objective override ('The Playwright E2E test extension does NOT require browser-visible UAT here — Phase 75 plan 10 handles the human UAT'). The headless E2E test is the lockdown contract for Plan 08."
metrics:
  duration_sec: 1800
  duration_human: "~30 minutes"
  tasks_completed: 2
  files_created: 0
  files_modified: 3
  completed_date: "2026-05-14"
commits:
  - hash: 4a9d5b0
    type: feat
    message: "feat(75-08): upgrade BackupController.importExecute to real execute + D-15 flash strings"
  - hash: fb2098b
    type: test
    message: "test(75-08): extend BackupImportE2ETest with real-execute success-flash walkthrough"
---

# Phase 75 Plan 08: Controller Upgrade + D-15 Flash Strings + E2E Real-Execute Walkthrough Summary

Upgrades `BackupController.importExecute` from Phase 74's stub-flash to the real
`BackupImportService.execute(stagingId)` delegation. Binds the three locked D-15 flash
strings (success / failure / uploads-restore-soft-fail) to the controller's response paths.
Extends `BackupImportE2ETest` so the happy-path walkthrough (upload → preview → confirm →
execute) lands the D-15 #1 success flash on `/admin/backup` after a real wipe-and-restore
round-trip on H2. The Phase 74 D-08 stub-flash string is removed from both production code
and tests. Endpoint URL (`POST /admin/backup/import-execute`) is unchanged per D-17;
`admin/backup-confirm.html` template survives unchanged.

## Performance

- **Duration:** ~30 minutes
- **Tasks:** 2 (1 controller + unit tests, 1 E2E extension)
- **Files modified:** 3
- **Files created:** 0
- **Commits:** 2 (this SUMMARY commit follows)

## Accomplishments

- **`BackupController.importExecute` upgraded to real execute delegation:** the Phase 74 stub
  flash (`"Validation succeeded. Import execution will be enabled in Phase 75."`) is removed.
  The method now calls `BackupImportService.execute(form.getStagingId())` after the D-09
  defense-in-depth `reparse(...)` re-validation and binds the success flash from the returned
  `BackupImportResult.restoredTotal()` / `entityCount()` via `String.format(...)`.
- **D-15 #1 success flash bound** (locked English literal):
  `"Import completed. {N} rows restored across {M} tables."` — rendered unconditionally when
  `execute()` returns without throwing (DB transaction has committed by that point).
- **D-15 #2 failure flash bound** on `BackupImportException`:
  `"Import failed and was rolled back — see logs. Audit-id: {auditUuid}."` — the
  `ex.getAuditUuid()` placeholder is substituted via `String.format(...)`.
- **D-15 #3 defensive soft-fail flash bound** on `UploadsRestoreException` (CONTEXT D-15
  planner-note: this clause is rare-to-never-hit in practice because the AFTER_COMMIT listener
  fires after the controller's redirect response is built). The audit UUID is reported as
  `"unknown"` because the exception carrier does not transport it; the operator detects the
  soft-fail in practice via the ERROR log line, `data_import_audit.success=false`, and the
  absence of `uploads-old/ → uploads/` move.
- **PATTERNS Open-Question §5 RESOLVED:** no `GlobalExceptionHandler.UploadsRestoreException`
  mapping added — the controller's local catch suffices per RESEARCH §7 defensive-default
  deferred. Documented in the Phase-75 class-level Javadoc on `BackupController`.
- **`BackupControllerTest`** gains 3 new MockMvc scenarios pinning the D-15 contract:
  - `givenValidConfirmForm_whenExecutePost_thenServiceExecuteCalledAndSuccessFlashRendered`
    — stubs `execute(...)` to return `BackupImportResult(uuid, 17042, 24)`; asserts
    `302 -> /admin/backup` with the D-15 #1 success flash equal to
    `"Import completed. 17042 rows restored across 24 tables."` (literal-string match — no
    regex; this is the production-side flash producer test).
  - `givenServiceThrowsBackupImportException_whenExecutePost_thenFailureFlashWithAuditUuid`
    — stubs `execute(...)` to throw `BackupImportException(specificUuid, cause)`; asserts
    `302 -> /admin/backup` with the D-15 #2 errorMessage equal to
    `"Import failed and was rolled back — see logs. Audit-id: 11111111-...-555555555555."`.
  - `givenInvalidConfirmForm_whenExecutePost_thenServiceNotCalledAndBindingErrorFlashed`
    — submits with `acknowledged=false`; asserts service.execute is NEVER invoked, the
    controller re-renders `admin/backup-confirm` (HTTP 200), and reparse is called exactly
    once (re-render path). Stubs reparse to return a minimal BackupImportPreview so the
    template's `${preview.totalImportedRows}` SpEL expression resolves cleanly.
- **`BackupImportE2ETest`** happy-path now exercises the real execute round-trip:
  - The Phase 74 stub-flash test method
    `givenPhase73ExportZip_whenAdminUploadsAndConfirms_thenLandsOnBackupWithStubFlash`
    is renamed to `givenValidBackupZip_whenAdminClicksConfirm_thenSuccessFlashRenderedOnAdminBackup`.
  - Body still walks upload → preview → confirm → execute; the FINAL flash assertion is
    now the D-15 #1 regex `Pattern.compile("Import completed\\. \\d+ rows restored across \\d+ tables\\.")`.
  - Negative assertion explicitly catches a regression: the Phase 74 stub-flash string must
    NOT appear after Plan 08 ships.
  - All other Phase 74 scenarios (`givenPreviewPage_whenAdminClicksCancel_...`,
    `givenConfirmPage_whenAdminSubmitsWithoutTickingCheckbox_...`,
    `givenStagingUuid_whenCookieJarIsClearedBetweenPreviewAndExecute_...`) are preserved
    verbatim; the cookie-jar test's redirect-only assertion still holds because endpoint
    URL is unchanged (D-17).

## Tasks Executed

### Task 1 — controller + unit tests — `4a9d5b0`

`BackupController.java`:
- Added imports for `BackupImportResult`, `BackupImportException`, `UploadsRestoreException`.
- Extended class-level Javadoc with a Phase 75 — D-17 paragraph that names the template-unchanged
  invariant and locks the PATTERNS Q5 resolution (no global UploadsRestoreException handler).
- Replaced the Phase 74 STUB Javadoc on `importExecute` with the Phase 75 — D-15 version
  that documents the REVISION-iteration-1 (W4) catch-chain order (sibling RuntimeException,
  order is INDEPENDENT, request-lifecycle rationale) and the D-15 #3 defensive-catch planner-note.
- Replaced the stub-flash body inside the `try` block with the real
  `backupImportService.execute(form.getStagingId())` call wrapped in a
  `String.format("Import completed. %d rows restored across %d tables.", ...)` flash binding.
- Added 3 new catch clauses in lifecycle order:
  `catch (BackupArchiveException) -> mapReason flash (unchanged D-02#3 path)`,
  `catch (IOException) -> log error + reject-path flash`,
  `catch (UploadsRestoreException) -> D-15 #3 with auditUuid="unknown" + ERROR log`,
  `catch (BackupImportException) -> D-15 #2 with String.format ex.getAuditUuid()`.
- Removed the `// STAGING FILE NOT DELETED — Phase 75 inherits per D-08` comment; replaced
  with the Plan 75-07 listener-driven staging-file-delete-on-success commentary.

`BackupControllerTest.java`:
- Added `@MockitoBean BackupImportService backupImportService`.
- Added the 3 MockMvc scenarios listed above (Given-When-Then naming, dev profile, no CSRF).
- Added imports for `BackupImportPreview` / `BackupImportResult` / `BackupImportException`,
  Mockito `never`, `times`, `when`, MockMvcResultMatchers `flash`, `redirectedUrl`.
- The Phase 74 stub-flash assertion was never in this unit test (it was only in the E2E test);
  no removal needed here. Class-level Javadoc extended to document the 6-scenario contract.

Verification:
- `./mvnw -q -o -Dtest=BackupControllerTest test` -> Tests run: 6, Failures: 0, Errors: 0
  (3 Phase-73 + 3 Phase-75 scenarios).

### Task 2 — E2E extension — `fb2098b`

`BackupImportE2ETest.java`:
- Renamed test method `givenPhase73ExportZip_whenAdminUploadsAndConfirms_thenLandsOnBackupWithStubFlash`
  to `givenValidBackupZip_whenAdminClicksConfirm_thenSuccessFlashRenderedOnAdminBackup`.
- Final flash-assertion switched from literal-equals
  (`"Validation succeeded. Import execution will be enabled in Phase 75."`) to regex
  `Pattern.compile("Import completed\\. \\d+ rows restored across \\d+ tables\\.")` — D-15 #1.
- Added negative assertion `doesNotContain("Validation succeeded. Import execution will be enabled in Phase 75.")`
  so a future regression that re-introduces the stub string fails loud.
- Class-level Javadoc updated to mark the Phase 75-08 carry-forward + name the locked
  D-15 #1 flash regex.
- SC#5 cookie-jar test's `executeResp` assertions updated to reflect D-17 (endpoint URL
  unchanged Phase 74 -> 75) instead of the now-deleted "Execute stub" wording.

Verification:
- `./mvnw -q -o -Dit.test=BackupImportE2ETest verify -Pe2e` -> Tests run: 4, Failures: 0,
  Errors: 0 (1 happy-path real-execute + 3 preserved Phase 74 scenarios).

### Task 3 — checkpoint:human-verify (SKIPPED per orchestrator override)

The plan's third task is a `checkpoint:human-verify` for playwright-cli manual verification
of the success flash on `/admin/backup` against a running `dev,demo` server. The orchestrator
objective explicitly overrode this gate:

> "The Playwright E2E test (BackupImportE2ETest) extension does NOT require browser-visible
> UAT here — Phase 75 plan 10 handles the human UAT. The E2E test runs headless via Failsafe
> + -Pe2e."

The headless E2E test (Task 2) is therefore the locking contract for Plan 08; the manual
visual verification is bundled into Plan 75-10's HUMAN-UAT checklist (CONTEXT D-16) so
the operator does the screenshot-pair-on-Saison-2023 walkthrough once on real MariaDB
rather than twice (once on dev/H2 here, once on local/MariaDB in Plan 10).

## Verification

| Check | Result |
| ----- | ------ |
| `./mvnw -q -o compile` | BUILD SUCCESS |
| `./mvnw -q -o test-compile` | BUILD SUCCESS |
| `./mvnw -q -o -Dtest=BackupControllerTest test` | Tests run: 6, Failures: 0, Errors: 0 |
| `./mvnw -q -o -Dit.test=BackupImportE2ETest verify -Pe2e` | Tests run: 4, Failures: 0, Errors: 0 (BUILD SUCCESS, exit 0) |
| `grep -c 'backupImportService.execute' BackupController.java` | 1 (>= 1) |
| `grep -c 'Validation succeeded. Import execution will be enabled in Phase 75' BackupController.java` | 0 |
| `grep -c 'Import completed\.' BackupController.java` | 1 (>= 1) |
| `grep -c 'Import failed and was rolled back' BackupController.java` | 1 (>= 1) |
| `grep -c 'Import database succeeded but uploads restore failed' BackupController.java` | 1 (>= 1) |
| `grep -c 'getAuditUuid' BackupController.java` | 1 (>= 1) |
| 3 catch clauses (importExecute body) `grep -E 'catch \((BackupArchiveException\|UploadsRestoreException\|BackupImportException)' \| wc -l` | 4 total in file (2 inside binding-error block, 4 inside execute-path block including IOException); the importExecute execute-path block has the required `BackupArchiveException` -> `IOException` -> `UploadsRestoreException` -> `BackupImportException` chain in lifecycle order |
| Catch chain order (lines 253 -> 259 -> 269 in importExecute execute-path) | ascending: BackupArchiveException < UploadsRestoreException < BackupImportException ✓ |
| Sibling-RuntimeException Javadoc note | present: `"siblings extending"`, `"REVISION-iteration-1 (W4)"`, `"catch chain order"` |
| 3 new BackupControllerTest scenarios | present: `givenValidConfirmForm_whenExecutePost`, `givenServiceThrowsBackupImportException_whenExecutePost`, `givenInvalidConfirmForm_whenExecutePost` |
| New E2E `@Test` method | present: `givenValidBackupZip_whenAdminClicksConfirm_thenSuccessFlashRenderedOnAdminBackup` |
| Phase 74 stub-flash assertion in E2E removed | 0 matches for `containsText.*Validation succeeded.*Phase 75` |
| Test asserts D-15 #1 regex | present: `Import completed\\. \\d+ rows restored across \\d+ tables\\.` |

## Decisions Made

1. **Stub-flash string removed from both production code AND tests.** The Phase 74 D-08 string
   (`"Validation succeeded. Import execution will be enabled in Phase 75."`) no longer
   appears in the codebase. The E2E test's negative assertion catches a regression.
2. **D-15 #1 success flash is unconditional on execute-success.** When
   `BackupImportService.execute(...)` returns a `BackupImportResult`, the DB transaction has
   committed. Uploads-restore soft-fail manifests asynchronously via the AFTER_COMMIT
   listener's ERROR log + `data_import_audit.success=false` row, not via a controller-side
   flash. Documented in the controller's class-level Javadoc.
3. **Catch chain order matches request lifecycle.** `BackupArchiveException` -> `IOException`
   -> `UploadsRestoreException` -> `BackupImportException`. All three D-15 types are siblings
   extending `RuntimeException`, so order is INDEPENDENT — the lifecycle-order choice is for
   reader comprehension, not correctness. Documented inline in the method Javadoc.
4. **D-15 #3 catch is DEFENSIVE.** The AFTER_COMMIT listener (Plan 07) is the real
   `UploadsRestoreException` throw site and runs AFTER the controller's redirect response is
   built — the controller's catch is therefore rarely-to-never hit. The audit UUID is
   reported as `"unknown"` because the carrier does not transport it; the operator detects
   the soft-fail via the listener's ERROR log + the audit row.
5. **PATTERNS Q5 RESOLVED here:** no `GlobalExceptionHandler.UploadsRestoreException`
   mapping is added. The controller's local catch suffices per RESEARCH §7 defensive-default
   deferred. Future refactor (e.g. an admin-history page that re-throws from a non-controller
   path) can add the global handler in a one-line change.
6. **BackupControllerTest uses literal-equals assertions for flash strings.** The unit test
   is the production-side flash producer — exact-match catches accidental message drift
   (typos, plural form changes, audit-id format changes). The E2E test uses regex because
   the row/entity counts vary with dev-fixture seeding across phases.
7. **BackupControllerTest.givenInvalidConfirmForm stubs `reparse` with a minimal preview.**
   On binding-error the controller re-renders `admin/backup-confirm.html`, which dereferences
   `${preview.totalImportedRows}`. A `null` preview would explode SpEL evaluation; a
   minimal but well-formed `BackupImportPreview` keeps the re-render path clean while still
   asserting the core contract (`service.execute` is NEVER invoked).
8. **Task 3 human-verify checkpoint SKIPPED per orchestrator objective.** The headless
   Playwright E2E is the lockdown contract; the manual screenshot-pair-on-Saison-2023
   walkthrough is bundled into Plan 75-10's HUMAN-UAT.

## Deviations from Plan

### Worktree path-safety incident (Rule 3, self-corrected)

**1. [Rule 3 - Blocking Issue] Initial Edit calls landed in main-repo instead of worktree**

- **Found during:** Task 1 start.
- **Issue:** The first three `Edit` invocations used relative paths and absolute paths
  derived from the orchestrator's spawn-time `pwd` snapshot. These absolute paths resolved
  to the main repository at `/Users/jegr/Documents/github/ctc-manager/src/main/java/...`
  instead of the worktree at
  `/Users/jegr/Documents/github/ctc-manager/.claude/worktrees/agent-a0e71b110dcc5a7b7/src/main/java/...`.
  The edits silently wrote to the wrong location; a subsequent `git status` in the worktree
  showed zero changes while `git status` in the main repo showed the controller modified
  (#3099 absolute-path safety issue described in the executor guard).
- **Fix:** Reverted the main-repo modifications via `git checkout -- <file>` (the main repo
  was unstaged, so no force operations were necessary). Re-applied all edits using the full
  worktree-absolute prefix
  `/Users/jegr/Documents/github/ctc-manager/.claude/worktrees/agent-a0e71b110dcc5a7b7/...`
  derived from `git rev-parse --show-toplevel` run inside the worktree.
- **Files affected during the failed write:** none (the changes were reverted before any
  commit).
- **Commits:** N/A (no commit landed on the wrong tree).
- **Status:** clean — main repo is untouched, worktree has both Task 1 + Task 2 commits as
  expected.

### Authentication Gates

None.

## Known Stubs

None. The controller delegation is production-grade; the unit tests use mocks (legitimate
test-double pattern); the E2E test exercises the full real-execute path on H2.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| (none new) | — | The new code surface is pure HTTP plumbing — no new filesystem writes, no new SQL, no new auth paths. Existing surfaces remain: `BackupImportService.execute` (covered by Plan 06 threat flags), `BackupImportPostCommitListener` (covered by Plan 07 threat flags). The defensive `UploadsRestoreException` catch reduces — does not increase — surface, because it bounds the rare cross-thread case rather than letting it escape to the default 500-page. |

## TDD Gate Compliance

Plan is `type: execute` — plan-level RED/GREEN gate sequencing is not enforced. Task 1 ships
the controller body + unit tests as a single `feat:` commit (the unit tests are the locking
contract for the D-15 flash strings); Task 2 ships the E2E extension as a `test:` commit
(the E2E is the locking contract for the headless real-execute round-trip). Both commits
were verified green before being committed.

## Next Plan Readiness

- **Plan 09 (parallel wave 4 — BackupImportRollbackIT):** Installs `FailAtTableInjector` via
  `@TestConfiguration`, triggers `RestoreFailureSimulatedException` at 50% of the largest
  table. The IT exercises the SAME controller path that Plan 08 ships; the failure flash
  string (`"Import failed and was rolled back — see logs. Audit-id: ..."`) is the contract
  the IT will lock down via the controller's MockMvc response or the audit-row read-back.
- **Plan 10 (Live MariaDB HUMAN-UAT):** Uses the same controller endpoint on a local
  MariaDB instance. The operator visually confirms the D-15 #1 success flash renders +
  the audit row is committed by the AFTER_COMMIT listener. Screenshot pairs on Saison 2023
  land in `.screenshots/75/before/` and `.screenshots/75/after/`.

## Self-Check: PASSED

**Files checked (all FOUND):**

- `src/main/java/org/ctc/backup/BackupController.java` (modified)
- `src/test/java/org/ctc/backup/BackupControllerTest.java` (modified)
- `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` (modified)

**Commits checked (all FOUND in `git log`):**

- `4a9d5b0` — feat(75-08): upgrade BackupController.importExecute to real execute + D-15 flash strings
- `fb2098b` — test(75-08): extend BackupImportE2ETest with real-execute success-flash walkthrough

---
*Phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat*
*Completed: 2026-05-14*
