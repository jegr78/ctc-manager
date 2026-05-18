---
phase: 74
plan: "10"
subsystem: backup-import
tags: [backup, e2e, playwright, stateless-proof, IMPORT-01, IMPORT-02, IMPORT-03, IMPORT-04]
dependency_graph:
  requires:
    - "74-08: BackupController import endpoints (controller under test)"
    - "74-09: backup-preview.html + backup-confirm.html (DOM selectors)"
    - "74-05: BackupImportService.stage/reparse/deleteStagingFile"
    - "73-03: BackupArchiveService.writeZip (runtime fixture generator)"
  provides:
    - "SC#1 proof: 24-card grid + schema-match pill rendered from real ZIP (IMPORT-01/02)"
    - "SC#5 proof: confirm-page renders after cookie-jar wipe (IMPORT-03 stateless re-parse)"
    - "IMPORT-04 proof: checkbox gate + D-02#5 stub Flash verified end-to-end"
    - "D-06+D-16 proof: cancel POST deletes staging file synchronously"
  affects: []
tech_stack:
  added: []
  patterns:
    - "Playwright APIRequestContext with setMaxRedirects(0) to capture raw redirect response"
    - "page.waitForRequest() lambda to pin POST method for cancel-form assertion"
    - "Browser.newContext() fresh context for SC#5 stateless proof (zero-cookie isolation)"
key_files:
  created:
    - src/test/java/org/ctc/e2e/BackupImportE2ETest.java
  modified: []
decisions:
  - "URL after checkbox-validation failure is /import-execute (not /import-confirm): Spring renders
     backup-confirm VIEW directly from POST target without redirect — test asserts /import-execute"
  - "Playwright APIRequestContext auto-follows redirects: setMaxRedirects(0) needed to capture
     raw 302 Location header on execute-stub response"
  - "Rendered Flash in cookie-jar test intentionally omitted: Spring Flash is HttpSession-coupled;
     asserting only Location header is the correct narrow assertion for SC#5"
metrics:
  duration: "~25 minutes"
  completed: "2026-05-13"
  tasks_completed: 3
  files_created: 1
  files_modified: 0
---

# Phase 74 Plan 10: BackupImportE2ETest — Playwright Walkthrough Summary

BackupImportE2ETest — four Playwright E2E tests proving SC#1 (preview rendered), SC#5 (stateless re-parse via UUID after cookie wipe), IMPORT-04 (checkbox gate + D-02#5 Flash), and D-06+D-16 (cancel POST cleanup), all passing under `./mvnw verify -Pe2e`.

## Tasks Executed

### Task 1: Happy-path walkthrough (IMPORT-01/02/04)

**Commit:** `1b6df70`

Created `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` extending `PlaywrightConfig` with
`@Tag("e2e")`, `@BeforeEach`/`@AfterEach` lifecycle, and global `page.onDialog(d -> d.accept())`
handler registered after `setupPage()`.

Test `givenPhase73ExportZip_whenAdminUploadsAndConfirms_thenLandsOnBackupWithStubFlash`:
- Generates runtime fixture via `backupArchiveService.writeZip()` into `@TempDir` (D-25 — no committed binary)
- Navigates to `/admin/backup`, uploads the fixture, clicks "Import Backup"
- Asserts preview page: `<h1>` "Backup Import — Preview", 24-card grid (`.card-grid > .card`),
  schema-match pill "Schema version 1 matches.", staging UUID present and parseable
- Clicks "Proceed to Confirm", asserts warning callout visible, `#acknowledged` unchecked
- Ticks `#acknowledged`, clicks "Execute Import" (JS `confirm()` auto-accepted)
- Asserts redirect to `/admin/backup`, `.alert.alert-success` contains verbatim D-02#5 string:
  `"Validation succeeded. Import execution will be enabled in Phase 75."`

### Task 2: Cancel cleanup + missing-checkbox validation (D-06/D-16/D-10)

**Commit:** `1b6df70` (same commit — all 4 methods written atomically)

Test `givenPreviewPage_whenAdminClicksCancel_thenStagingFileDeletedAndRedirects`:
- Pre-asserts cancel form has `method="post"` and carries matching `stagingId`
- Pins HTTP method via `page.waitForRequest(req -> req.method().equals("POST") && req.url().endsWith("/import-cancel"), ...)`
- Asserts staging file `upload-{uuid}.zip` deleted from `stagingDir` after cancel
- Asserts "Import canceled." Flash visible

Test `givenConfirmPage_whenAdminSubmitsWithoutTickingCheckbox_thenSeesFieldError`:
- Reaches confirm page, clicks Execute Import without ticking checkbox
- Asserts URL stays on `/admin/backup/import-execute` (Spring re-renders confirm view via POST endpoint, no redirect)
- Asserts `.field-error` (class-only, tag-agnostic per Plan 09 Notes) visible with locked text
  `"You must acknowledge the deletion warning to continue."`

### Task 3: Stateless cookie-jar proof (SC#5 / IMPORT-03)

**Commit:** `6d255b3` (fix commit for `setMaxRedirects(0)`)

Test `givenStagingUuid_whenCookieJarIsClearedBetweenPreviewAndExecute_thenPagesStillFunction`:
- Uploads fixture + reaches preview in original context (baseline: staging file exists)
- Wipes cookies on original context via `context.clearCookies()`
- Spawns `browser.newContext()` (zero-cookie state — strongest possible isolation)
- POSTs to `/admin/backup/import-confirm` with only the staging UUID via `APIRequestContext`
- Asserts HTTP 200 + response body contains UUID, "Backup Import", "Confirm", checkbox label
- POSTs to `/admin/backup/import-execute` with `setMaxRedirects(0)` to capture raw 302
- Asserts redirect status 3xx + `Location` header ends with `/admin/backup`
- Flash banner intentionally NOT asserted (Spring Flash is session-coupled — correct by design)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] URL assertion for checkbox validation test**
- **Found during:** Task 2 run
- **Issue:** Spring MVC renders `admin/backup-confirm` VIEW directly from the `/import-execute`
  POST handler on validation error (no Redirect-After-POST pattern for error case). The test plan
  described the URL as `/admin/backup/import-confirm` but the actual URL after form submission
  is `/admin/backup/import-execute` (the POST target).
- **Fix:** Changed `hasURL(Pattern.compile(".*/admin/backup/import-confirm$"))` to
  `hasURL(Pattern.compile(".*/admin/backup/import-execute$"))` in the post-submit assertion.
- **Files modified:** `src/test/java/org/ctc/e2e/BackupImportE2ETest.java`
- **Commit:** `6d255b3`

**2. [Rule 1 - Bug] Playwright APIRequestContext auto-follows redirects**
- **Found during:** Task 3 run
- **Issue:** Playwright's `APIRequestContext.post()` follows redirects by default, returning HTTP 200
  (the final `/admin/backup` GET response) instead of the raw 302 from `/import-execute`. The test
  asserted `isBetween(300, 399)` which failed with actual status 200.
- **Fix:** Added `.setMaxRedirects(0)` to the `RequestOptions` on the execute POST to disable
  auto-redirect and capture the raw redirect response with its `Location` header.
- **Files modified:** `src/test/java/org/ctc/e2e/BackupImportE2ETest.java`
- **Commit:** `6d255b3`

**3. [Rule 3 - Blocking] Worktree fork point behind Phase 74 commits**
- **Found during:** Pre-execution compile check
- **Issue:** The worktree branch was forked from commit `3c5a540` (v1.9 Phase Groups) instead of
  `a2e9e2b` (Phase 74 hotfix). All backup production classes (`org.ctc.backup.*`) were missing
  from the worktree, causing compilation failure.
- **Fix:** Applied per `worktree_branch_check` protocol: `git reset --hard a2e9e2b` to bring the
  worktree to the correct fork point. The `BackupImportE2ETest.java` file was preserved as
  untracked across the reset.
- **No commit needed:** Reset only corrects the worktree base; production code unchanged.

## Test Run Results

```
./mvnw failsafe:integration-test failsafe:verify -Pe2e -Dit.test=BackupImportE2ETest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 32.06 s
BUILD SUCCESS
```

```
./mvnw failsafe:integration-test failsafe:verify -Pe2e
Tests run: 203, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Note: `./mvnw verify` (without `-Pe2e`) excludes the e2e package via Surefire's `**/e2e/**`
exclusion pattern — `BackupImportE2ETest` is invisible to the standard build.

## Known Stubs

None — this plan adds only a test class. The `BackupController.importExecute` stub Flash
`"Validation succeeded. Import execution will be enabled in Phase 75."` is intentional (D-02#5)
and asserted verbatim by test 1.

## Threat Flags

None — no production code modified. Test class only.

## Self-Check: PASSED

- `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` — FOUND
- commit `1b6df70` — FOUND  
- commit `6d255b3` — FOUND
- 4 `@Test` methods — CONFIRMED
- `@Tag("e2e")` — CONFIRMED
- `extends PlaywrightConfig` — CONFIRMED
- No `@SpringBootTest` / `@ActiveProfiles` on class — CONFIRMED (inherited)
- No committed binary fixtures — CONFIRMED (`writeZip` at runtime)
- No `Thread.sleep`, no hard-coded ports — CONFIRMED
- No production files modified — CONFIRMED
