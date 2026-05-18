# Phase 76: Operational Hardening — Import Lock + Read-Only Banner + Auto-Backup-Before-Import - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Defense-in-depth wrapping around the existing Phase 75 wipe+restore path. Three concentric safety rings, all NEW code with surgical edits to two existing files (`BackupController.importExecute`, `admin/layout.html`):

1. **Ring 1 — Mutex (SECU-05):** `ImportLockService` (`@Service @Scope("singleton")`) wraps a single `ReentrantLock`. `BackupController.importExecute` calls `lockService.tryLock()` BEFORE delegating to the service; a second concurrent attempt is rejected with HTTP 409 + Flash ("Another import is already running — please wait.") without entering the service layer at all.
2. **Ring 2 — Read-only state advertised to every other admin path (SECU-06):**
   - A new `@ControllerAdvice` (`ImportLockBannerAdvice`) injects an `importInProgress` boolean model attribute on every admin request, mirroring the existing `GlobalModelAdvice` pattern.
   - `admin/layout.html` renders a persistent yellow banner (`<div class="alert alert-warning">`) when `importInProgress == true`.
   - A new `HandlerInterceptor` (`ImportLockedWriteRejector`) registered in `WebConfig` rejects every NON-WHITELISTED POST under `/admin/**` with HTTP 503 while the lock is held. The whitelist contains exactly one URL: `/admin/backup/import-execute`. All other admin POSTs (cancel, preview, every CRUD form) are blocked.
3. **Ring 3 — Recoverable history (SECU-07):** `BackupImportService.execute(UUID)` is extended to run a synchronous `BackupArchiveService.writeZip(...)` to `data/.import-backups/<ts>/auto-backup-before-import.zip` as its FIRST DB-reading statement, BEFORE the wipe. On auto-export failure, a new `AutoBackupBeforeImportException` is thrown, the @Transactional rollback is a no-op (nothing has mutated), and a REQUIRES_NEW audit row with `success=false` is written via the existing `DataImportAuditService` (Phase 75 D-01).

**Out of scope** (Phase 77 and beyond):
- `BackupRoundTripIT` SHA-256 hash assertion on H2 + MariaDB CI workflow (Phase 77, QUAL-02).
- README + WIKI "Backup & Restore" documentation (Phase 77, QUAL-05) — Phase 76 ships only the operational runbook text file per goal-bullet-4.
- JaCoCo final coverage gate hold (Phase 77, QUAL-01).
- `/admin/backup/history` audit-viewer UI page (deferred to v1.11+; Phase 74/75 already deferred this).
- Async / background auto-backup (locked synchronous per SECU-07).
- `@Scheduled` cleanup of `data/.import-backups/<ts>/` beyond 24 h (operator-driven `rm -rf`; Phase 75 D-deferred).

</domain>

<decisions>
## Implementation Decisions

### Lock Service Shape (resolves SECU-05)

- **D-01: `org.ctc.backup.lock.ImportLockService` — singleton bean with a private `ReentrantLock`.** Exposes three methods: `boolean tryLock()` (non-blocking; returns false immediately if held — equivalent to `lock.tryLock()` with zero timeout), `void unlock()` (idempotent — guarded by `lock.isHeldByCurrentThread()` so a stray `finally { unlock(); }` after a failed tryLock is a no-op), and `boolean isLocked()` (read-only; reads `lock.isLocked()` — used by the banner advice and the 503 interceptor). No fairness flag (`new ReentrantLock()`, not `new ReentrantLock(true)`) — fairness adds throughput cost for a feature invoked by a single admin once per UAT/release window.
- **D-02: tryLock is non-blocking (timeout = 0).** A queued second import is NOT the desired UX — the admin should see "another import is running" immediately and decide whether to wait + retry manually. Blocking with a timeout would create UX confusion ("is the page hung?") and ties up the request thread. Goal-text-locked behavior: "second concurrent import attempt is rejected with HTTP 409 + Flash" — non-blocking matches the contract.
- **D-03: Lock state is in-memory, single-JVM only.** No DB-backed lock, no distributed lock (Hazelcast / ZooKeeper / Redis). CTC runs as a single Spring Boot JVM on a single host; multi-instance deployment is explicitly NOT in v1.10 scope (out-of-scope list in REQUIREMENTS.md). If a future deployment model requires it, the `ImportLockService` interface stays intact and the impl swaps for a Hazelcast-backed variant — but that's v2.x territory.

### Lock Acquisition Site (resolves where in the request flow lock is acquired)

- **D-04: Lock acquired in `BackupController.importExecute` BEFORE delegating to the service.** Controller pseudocode:
  ```java
  if (!importLockService.tryLock()) {
      ra.addFlashAttribute("errorMessage", "Another import is already running — please wait.");
      return "redirect:/admin/backup";   // 302 → GET /admin/backup, HTTP 409 status set on the redirect response per D-05
  }
  try {
      backupImportService.reparse(form.getStagingId());  // existing D-09 defense-in-depth
      BackupImportResult result = backupImportService.execute(form.getStagingId());
      // existing D-15 #1 success flash
  } catch (...) {
      // existing D-15 #2 / #3 / new auto-backup flash branches
  } finally {
      importLockService.unlock();
  }
  ```
- **D-05: HTTP 409 status is set on the redirect response itself, not on a custom error page.** Because the controller returns a `redirect:/admin/backup`, the response status is 302 by default. To honor the SECU-05 goal ("HTTP 409 + Flash"), the controller sets `response.setStatus(HttpStatus.CONFLICT.value())` BEFORE returning the redirect string. Spring's redirect handling preserves the 409 status (it's NOT a 3xx that the servlet engine overwrites — see Spring `RedirectView.renderMergedOutputModel` behavior with explicit status). Alternative: return `ResponseEntity` with status + redirect URL header, but that breaks the rest of the controller's redirect-string idiom. Planner verifies on first integration test pass; if status leaks back to 302, fall back to a `View`-mode redirect with an explicit `HttpStatus` (see Spring `RedirectView(setStatusCode)`).
- **D-06: Lock is released in `finally` AFTER `execute()` returns.** Spring's default `@TransactionalEventListener(phase=AFTER_COMMIT)` is SYNCHRONOUS on the same thread — so by the time `execute(...)` returns, the Plan 75-07 uploads-move listener has already completed (success path) OR thrown (uploads-move-soft-fail path; lock release is still the right action). Releasing in `finally` is therefore correct for both paths without any extra plumbing.

### 503-Rejector Mechanism (resolves SECU-06 "non-import POST → 503")

- **D-07: `HandlerInterceptor`, NOT `@ControllerAdvice`.** REQUIREMENTS.md SECU-06 wording "`@ControllerAdvice`-filter" is a SLIP — `@ControllerAdvice` cannot intercept requests, it handles exceptions and contributes model attributes. The semantically-correct Spring MVC primitive for "intercept request, optionally short-circuit before the controller runs" is `HandlerInterceptor.preHandle(...)`. The class `org.ctc.backup.lock.ImportLockedWriteRejector` implements `HandlerInterceptor` and is registered in `org.ctc.admin.WebConfig` via `addInterceptors(...).addPathPatterns("/admin/**")`.
- **D-08: preHandle logic (locked):**
  ```java
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
      if (!"POST".equalsIgnoreCase(req.getMethod())) return true;          // GETs always allowed (banner renders, no DB mutation)
      if (!importLockService.isLocked()) return true;                       // no lock → no rejection
      if ("/admin/backup/import-execute".equals(req.getRequestURI())) return true;  // whitelist (D-09)
      res.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
      // For form-driven admin clients, redirect-via-Flash isn't reachable from the interceptor
      // because no controller runs. Two acceptable UX choices: (a) render a minimal HTML body
      // explaining the lock, (b) just set status + send empty body (browser shows generic 503).
      // (a) preferred — locked English wording mirrors the banner.
      res.setContentType("text/html;charset=UTF-8");
      res.getWriter().write(LOCK_HTML);  // small static constant; see D-12
      return false;
  }
  ```
- **D-09: Whitelist = exactly one URL — `POST /admin/backup/import-execute`.** Rationale:
  - `import-execute` is the route that ACQUIRED the lock — blocking it would deadlock the very import we are protecting.
  - `import-preview` and `import-confirm` are blocked: starting a second import flow during an active import is exactly the SECU-05 / SECU-06 abuse the phase exists to prevent.
  - `import-cancel` is blocked: cancelling another browser's queued import while one is running creates a race against the staging-file lifecycle. The operator can cancel after the active import completes.
  - All other `/admin/**` POSTs (CRUD forms, generate, gt7-sync, csv-import, drivers, teams, …) are blocked — the entire point of the read-only banner is "no writes anywhere during an import."
- **D-10: Whitelist match is `equals(requestURI)`, NOT `startsWith`.** Path-prefix matching would accidentally cover `/admin/backup/import-execute-anything`; equals is strict.

### Banner Wiring (resolves SECU-06 banner rendering)

- **D-11: `@ControllerAdvice ImportLockBannerAdvice` injects `importInProgress` model attribute.** Mirrors the existing `org.ctc.admin.controller.GlobalModelAdvice` pattern (single class, single `@ModelAttribute` method). Reads `importLockService.isLocked()`. Lives at `org.ctc.backup.lock.ImportLockBannerAdvice` (next to `ImportLockService` and `ImportLockedWriteRejector` for cohesion). Annotated `@ControllerAdvice` (NOT `@ControllerAdvice(basePackages = "...")` — applies to all controllers so the banner is consistent across admin + site GETs).
- **D-12: `admin/layout.html` adds one `<div>` BEFORE the existing flash divs.** Locked English wording: `"Backup import in progress — write access is temporarily locked."`. The interceptor's 503-HTML body uses the same English string (see D-08). Markup:
  ```html
  <div th:if="${importInProgress}" class="alert alert-warning" role="status">
      Backup import in progress — write access is temporarily locked.
  </div>
  ```
  - CSS class `alert-warning` ALREADY EXISTS (`src/main/resources/static/admin/css/admin.css:161` — `background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00;`). No new CSS, no inline styles (`feedback_no_inline_styles`).
  - `role="status"` (ARIA) advertises the live-region semantics; screen readers will announce the banner on page load.
  - Banner sits ABOVE the `successMessage` / `errorMessage` divs so the import-locked state is the first thing the operator sees.
- **D-13: Site templates (NOT admin) do not show the banner.** The site templates (`src/main/resources/templates/site/...`) are public read-only league pages — they have no write paths to block. The `ImportLockBannerAdvice` model attribute is set globally but the site `layout.html` does not include the conditional div. Planner verifies the site layout file does not accidentally render the banner; the model attribute is harmless either way (Thymeleaf evaluates `${importInProgress}` to null → th:if=false).

### Auto-Backup Wiring (resolves SECU-07)

- **D-14: Auto-export runs as the FIRST DB-reading statement inside `BackupImportService.execute(...)`, BEFORE wipe.** Sequence (with Phase 75 baseline + Phase 76 inserts marked `[76]`):
  1. Stage file lookup + meta read (Phase 75 — unchanged)
  2. `[76]` Compute `<ts>` (MOVED from current Phase 75 position; see D-15)
  3. `[76]` Create `data/.import-backups/<ts>/` directory
  4. `[76]` Auto-export to `data/.import-backups/<ts>/auto-backup-before-import.zip` (NEW — see D-16)
  5. Manifest re-read (Phase 75 — unchanged; defense-in-depth even though controller `reparse()` already validated)
  6. Wipe — through to restore — through to event publish (Phase 75 — unchanged)
- **D-15: `<ts>` is computed once at top of `execute()` and shared between auto-backup and uploads-old.** Phase 75 D-11 anchored this carry-forward: `Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-")`. The current Phase 75 code computes `<ts>` AFTER the `Files.exists(staged)` check and BEFORE wipe; Phase 76 MOVES this computation upward by ~10 lines so the auto-backup ZIP path can be derived from it. Single source of truth — the `BackupImportSucceededEvent.importBackupDir` payload (Phase 75) carries the resolved Path; the auto-backup ZIP is `importBackupDir.resolve("auto-backup-before-import.zip")`.
- **D-16: Auto-export reuses `BackupArchiveService.writeZip(OutputStream, Instant)` with a `FileOutputStream` target.** No new overload on `BackupArchiveService` — the existing signature accepts any `OutputStream`. Auto-export pseudocode:
  ```java
  Path autoBackupZip = importBackupDir.resolve("auto-backup-before-import.zip");
  try (OutputStream out = Files.newOutputStream(autoBackupZip, StandardOpenOption.CREATE_NEW)) {
      backupArchive.writeZip(out, Instant.now());  // joins outer @Transactional (REQUIRED) — read-only joins are no-ops in JPA
  } catch (IOException | RuntimeException e) {
      tryDeletePartialAutoBackup(autoBackupZip);   // best-effort cleanup of half-written file
      throw new AutoBackupBeforeImportException(auditUuid, e);  // see D-17
  }
  ```
  - `StandardOpenOption.CREATE_NEW` fails if the target exists — defensive: a same-second timestamp collision (two imports started within the same wall-clock second) crashes loud rather than silently overwriting.
  - The auto-export read of the DB happens BEFORE the wipe, inside the same outer `@Transactional(REQUIRED, READ_COMMITTED)` — sees a consistent snapshot of pre-wipe state.
- **D-17: `AutoBackupBeforeImportException extends BackupImportException`.** Inherits the `auditUuid` + `auditWritten` carrier shape (Phase 75 `BackupImportException` already encodes both). Add as a distinct subclass so the controller can catch and flash a distinct message; the existing `BackupImportException` catch covers wipe / restore / uploads-move failures via inheritance.
  - Controller flash (NEW, D-15 #4 in flash naming convention):
    ```
    "Import aborted — pre-import auto-backup failed. No database changes. Audit-id: {auditUuid}."
    ```
  - Distinct from D-15 #2 ("Import failed and was rolled back") because semantically NO rollback is needed — nothing was mutated.
- **D-18: Audit row on auto-backup failure has `wiped_counts={}` and `restored_counts={}` (both empty JSON objects).** The existing Phase 75 `tryRecordFailure(auditUuid, schemaVersion, sourceFilename, wipedCounts, restoredCounts)` helper accepts these maps; passing empty maps is the natural representation of "no DB mutation occurred". The audit row's `success=false` + the empty count maps unambiguously signal "failure at pre-import auto-backup step" to anyone querying the audit table later.
- **D-19: Partial-ZIP cleanup is best-effort, never throws.** Helper method `tryDeletePartialAutoBackup(Path target)` does `Files.deleteIfExists(target)` inside a try-catch that LOGS but never propagates. The operator's recovery instructions in the runbook (D-22) name `data/.import-backups/<ts>/` explicitly; a partial ZIP left behind is recoverable via `rm`.

### Test Strategy (resolves SECU-05 2-thread test)

- **D-20: `ImportConcurrentLockIT` (Failsafe IT) under `org.ctc.backup.it` — reuses Phase 75's `RestoreFailureInjector` extension point for the "deliberately slow" thread-A injection.** Mirrors Phase 75 D-13's pattern: a test-only `@TestConfiguration` provides a `BlockingRestoreFailureInjector` `@Primary` bean that overrides the production no-op `NoopRestoreFailureInjector`. The injector's `maybeFailAt(table, rowIndex)` method calls `latch.await(5, SECONDS)` instead of throwing — semantically "injector influences restore timing/behavior" stays clean.
  - Scenario:
    1. Two staged ZIPs uploaded via shared dev-data fixture.
    2. Thread A: `POST /admin/backup/import-execute` with stagingId-A; the blocking injector pauses at row 50 of largest table.
    3. Thread B (waits via second latch until thread A has acquired the lock): `POST /admin/backup/import-execute` with stagingId-B.
    4. Assert thread B response: HTTP 409 + Flash `"Another import is already running — please wait."`.
    5. Count down thread A's release latch.
    6. Thread A completes the import normally; assert HTTP 302 redirect + success Flash.
    7. Assert exactly 1 audit row with `success=true` (thread A's) — thread B never reached the service.
- **D-21: 503-rejector IT — `ImportLockedPostRejectorIT` (Failsafe).** Drives a slow import via the same `BlockingRestoreFailureInjector` pattern; while the lock is held, fire one whitelisted POST (`/admin/backup/import-execute` — already running, should NOT be blocked by the interceptor — but the lock-service-level check rejects it via 409, NOT 503) and one non-whitelisted POST (e.g., `/admin/teams` form submit) and assert HTTP 503. Banner advice IT — `ImportLockBannerAdviceIT` — drives a slow import and fires `GET /admin/seasons` while the lock is held; assert the response HTML contains the banner text.
  - **Planner judgment**: D-21 may be split into 2-3 distinct IT classes OR a single parameterized IT — the criterion is that every Phase 76 success criterion (1, 2, 3) has a regression-protective IT, not that they share a class.

### Operational Runbook Location (resolves SECU-07 goal-bullet-4)

- **D-22: `docs/operations/import-runbook.md` — new top-level operations directory.** Plain-text Markdown, NOT a wiki page (Phase 77 QUAL-05 owns the wiki page). Contents (locked outline):
  1. **Recovery from auto-backup.** Where the ZIP lives (`data/.import-backups/<ts>/auto-backup-before-import.zip`), how to re-import via `/admin/backup` if the app is up, how to restore the underlying MariaDB via `mariadb-import` + manual `uploads/` move if the app is down.
  2. **24h retention semantics.** `data/.import-backups/<ts>/uploads-old/` is preserved 24 h; operator cleans up with `find data/.import-backups -mtime +1 -delete` or analog. Same retention applies to `auto-backup-before-import.zip` siblings.
  3. **Audit-id query SQL.** Example: `SELECT * FROM data_import_audit WHERE id = '<uuid>'`. Documents the `success` column, `table_counts_wiped` / `table_counts_restored` JSON shape, and how to interpret "wiped=0, restored=0, success=false" (= pre-import auto-backup failure path per D-18).
  4. **Concurrent-import behavior.** "If you see HTTP 409 with 'Another import is already running', another admin tab or window is mid-import. Wait for completion (refresh once after ~30 s) or coordinate."
  5. **Read-only state during imports.** "While the yellow banner is visible, no admin form submission will succeed (HTTP 503). The banner disappears automatically when the import finishes."

### Wiring vs. Refactor Discipline

- **D-23: Phase 75 code is touched ONLY at three sites; everything else is additive.**
  - `BackupController.importExecute` — adds `tryLock` / `finally unlock` wrapper + new `AutoBackupBeforeImportException` catch clause + 409 status set.
  - `BackupImportService.execute` — adds the 3-statement auto-export block immediately after stage-file lookup; existing wipe / restore / event-publish stays byte-identical.
  - `admin/layout.html` — adds one `<div th:if="${importInProgress}">` block.
- **D-24: No new Maven dependencies.** `ReentrantLock` is `java.util.concurrent.locks`, `HandlerInterceptor` + `@ControllerAdvice` are Spring MVC core, `Files.newOutputStream` is JDK. The "no new dependencies in v1.10" project constraint (STATE.md "Key Technical Context") holds.
- **D-25: No new Flyway migration.** Phase 76 does not touch the schema — the existing `data_import_audit` table from Phase 72 V7 captures the auto-backup-failure path via D-18. CLAUDE.md "Do Not Modify Flyway Migrations" constraint holds with zero migrations.

### Claude's Discretion

- **CD-01: HTTP 409 wire mechanism in D-05** — `response.setStatus(409)` + `redirect:/admin/backup` is the cleanest. If a future Spring upgrade overrides 4xx-with-redirect (RedirectView.statusCode contract change), the fallback is to return a `View` object directly. Planner verifies on first integration test and falls back if needed.
- **CD-02: Lock service location** — `org.ctc.backup.lock` vs `org.ctc.backup.service` vs `org.ctc.admin.lock`. Default recommendation: `org.ctc.backup.lock` (cohesion with the feature it protects). Planner can move to `org.ctc.admin.lock` if a future admin operation also wants a lock — that's speculative.
- **CD-03: 503-HTML body in D-08** — minimal hand-written HTML string vs Thymeleaf-rendered error template. Default: minimal HTML string (no controller runs in the interceptor path, Thymeleaf rendering would require setting up a ViewResolver call). Planner re-evaluates if the body needs to be richer (e.g., include a "Refresh" button); current minimal "Backup import in progress. Please wait." text + a `<meta http-equiv="refresh" content="10">` for auto-refresh is the recommended polish.
- **CD-04: Banner role attribute** — `role="status"` (polite, announced when the user lands on the page) vs `role="alert"` (aggressive, may interrupt) vs no role. Default recommendation: `role="status"` per D-12. Planner can change if the operator complains they need a louder signal.
- **CD-05: `BlockingRestoreFailureInjector` location** — `src/test/java/org/ctc/backup/it/support/` vs inline `@TestConfiguration` nested class in `ImportConcurrentLockIT`. Default: support package so the rejector IT and banner IT can share it. Planner can inline if only one IT needs it after refinement.
- **CD-06: Auto-backup filename within the ts directory** — `auto-backup-before-import.zip` per SECU-07 wording is locked. The `<ts>` portion (parent directory name) is the colon-dashed `Instant` per D-15. No ambiguity remains.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v1.10 milestone foundation

- `.planning/ROADMAP.md` §"Phase 76" — Goal text (locks `ImportLockService` singleton + `ReentrantLock`, `@ControllerAdvice`-WORDED-AS-FILTER-but-implemented-as-HandlerInterceptor per D-07, persistent yellow banner, synchronous auto-export, 24 h retention runbook documentation). 4 success criteria; this CONTEXT does not override any of them — D-07 reframes the implementation mechanism but the user-visible behavior matches the goal text verbatim.
- `.planning/REQUIREMENTS.md` §SECU-05 / SECU-06 / SECU-07 — Acceptance criteria. **NOTE:** German UI strings ("Import läuft — Schreibzugriff temporär gesperrt", "Ein anderer Import läuft bereits — bitte warten") in REQUIREMENTS.md SECU-05/06 are OVERRIDDEN by `feedback_ui_language` (English-only UI rule, CLAUDE.md). D-04 / D-12 supply the final English wording.
- `.planning/PROJECT.md` §"Audit log persistence" — `DataImportAudit` is Lombok-`@Entity`-NOT-record, deliberately does NOT extend `BaseEntity` so the auto-backup-failure path (D-18) can fully control `executedAt` via the existing `DataImportAuditService.recordResult(...)` (Phase 75 D-01, REQUIRES_NEW propagation). V7 columns use `LONGTEXT` for `table_counts_wiped` and `table_counts_restored` — both `{}` (empty JSON object) on D-18's pre-import auto-backup failure path.
- `.planning/STATE.md` §"Key Technical Context" — "No new Maven dependencies" project-constraint; Phase 76 D-24 confirms.

### Prior-phase context (mandatory carry-forward)

- `.planning/phases/72-backup-wire-contract-schema-manifest-objectmapper-audit-log-/72-CONTEXT.md` — `DataImportAudit` Lombok-entity (not record), V7 migration shape. Phase 76 reads only; no schema change.
- `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-CONTEXT.md` — `BackupArchiveService.writeZip(OutputStream, Instant)` signature + transactional shape. Phase 76 D-16 reuses verbatim with a `FileOutputStream` target — no new overload.
- `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` — `BackupImportConfirmForm`, staging-file UUID contract, controller `import-execute` shape. Phase 76 D-04 / D-23 wraps the existing controller method; no DTO changes, no template changes beyond `admin/layout.html`.
- `.planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-CONTEXT.md` — `BackupImportException` carrier shape (D-15 #2), `DataImportAuditService.recordResult` REQUIRES_NEW propagation (D-01), `<ts>` timestamp format (D-11), `BackupImportSucceededEvent` AFTER_COMMIT contract (D-14), `RestoreFailureInjector` extension point (D-13). All FIVE flow directly into Phase 76 decisions: D-15 reuses the timestamp format, D-17 extends the exception hierarchy, D-18 reuses the audit-write helper, D-20 reuses the injector pattern, D-06 depends on AFTER_COMMIT being synchronous-on-same-thread.

### Existing code Phase 76 references (mix of reuse + extend)

- `src/main/java/org/ctc/backup/BackupController.java:228` (the `importExecute` method) — Phase 76 wraps with `tryLock()` / `finally unlock()` per D-04, sets HTTP 409 status on the concurrent-rejection path per D-05, adds a `catch (AutoBackupBeforeImportException)` branch per D-17. Existing catch chain (`BackupArchiveException` → `UploadsRestoreException` → `BackupImportException`) is preserved; the new `AutoBackupBeforeImportException` is a subclass of `BackupImportException` so it must be caught FIRST in the chain per Java exception-matching order.
- `src/main/java/org/ctc/backup/service/BackupImportService.java:419` (the `execute` method) — Phase 76 adds the 3-statement auto-export block per D-14 between the meta-read (line ~453) and the wipe (line ~473), MOVES the `<ts>` computation upward per D-15 (currently at line 456). The wipe / extract / restore / event-publish blocks are byte-identical post-edit.
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java:131` (`writeZip(OutputStream, Instant)`) — Phase 76 D-16 calls it with `Files.newOutputStream(autoBackupZip, CREATE_NEW)` as the stream argument.
- `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` — Phase 76 D-18 calls `recordResult(...)` (REQUIRES_NEW) with empty `tableCountsWiped` / `tableCountsRestored` maps on the auto-backup-failure path.
- `src/main/java/org/ctc/backup/exception/BackupImportException.java` — Phase 76 D-17 adds `AutoBackupBeforeImportException extends BackupImportException` in the same package; constructor delegates to the existing 3-arg ctor.
- `src/main/java/org/ctc/backup/restore/RestoreFailureInjector.java` — Phase 76 D-20 reuses the interface verbatim (no API change). A new test-only `BlockingRestoreFailureInjector` `@Primary` bean is added under `src/test/java/.../support/`.
- `src/main/java/org/ctc/admin/WebConfig.java` — Phase 76 EXTENDS this class to register `ImportLockedWriteRejector` via `addInterceptors(...)` per D-07. The existing `addResourceHandlers(/uploads/**)` mapping is untouched.
- `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java` — Phase 76 ADDS a sibling `ImportLockBannerAdvice` (separate file, same package OR `org.ctc.backup.lock` per CD-02; default `org.ctc.backup.lock`) per D-11. The existing `appVersion` model attribute is untouched.
- `src/main/resources/templates/admin/layout.html:82` (the `successMessage`/`errorMessage` div block) — Phase 76 D-12 adds one `<div th:if="${importInProgress}">` ABOVE this block. Identical CSS class (`alert alert-warning`) — already defined at `src/main/resources/static/admin/css/admin.css:161`. No new CSS rules.
- `src/main/resources/application.yml:6` (`app.backup.import-backups-dir: data/.import-backups`) — Phase 76 reuses the existing property; the auto-backup ZIP lives at `${app.backup.import-backups-dir}/<ts>/auto-backup-before-import.zip`. No new property.

### Existing code Phase 76 references but does NOT modify

- `src/main/java/org/ctc/admin/SecurityConfig.java` + `OpenSecurityConfig.java` — Phase 76 does NOT change the auth matrix. `/admin/backup/import-execute` stays profile-conditional (auth on prod/docker; open on dev/local). The 503-rejector runs AFTER security filters per Spring's filter chain — locked admin still sees 503, anonymous prod/docker users see 403 first.
- `src/main/resources/templates/site/...` — Phase 76 D-13 explicitly excludes site templates from the banner; no site template edits.
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — Phase 76 D-09 BLOCKS this controller's POSTs while the import lock is held (it is NOT whitelisted). No code change needed; the interceptor rejects.
- All other admin controllers (seasons, teams, drivers, matchdays, races, playoffs, scorings, …) — same: blocked by the interceptor while the lock is held; no code change.

### Test infrastructure (mandatory reading)

- `.planning/codebase/TESTING.md` §"Failsafe IT split" + §"@SpringBootTest profile policy" — Phase 76 IT location, Testcontainers vs H2 default, `@ActiveProfiles` discipline.
- `.planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-CONTEXT.md` §D-13 — `RestoreFailureInjector` test-injection pattern that Phase 76 D-20 mirrors.
- `src/test/java/org/ctc/backup/it/...` (Phase 75 IT location) — Phase 76 `ImportConcurrentLockIT` + `ImportLockedPostRejectorIT` + `ImportLockBannerAdviceIT` (or merged per CD-05) all land in the same package.

### Project conventions (mandatory reading)

- `CLAUDE.md` §"Architectural Principles" — Controllers thin (Phase 76 D-04 keeps the controller wrapper to ~10 LOC: tryLock, try/catch chain, finally unlock — no business logic), DTOs in controllers (no new DTOs in Phase 76), no inline styles (D-12 reuses existing `alert-warning` class), do-not-modify-Flyway-migrations (D-25 zero migrations).
- `CLAUDE.md` §"Constraints" — Coverage ≥ 82 % (Phase 76 adds ~5 classes + 3 ITs; planner monitors JaCoCo), OSIV active (no impact — Phase 76 has no new template lazy-fetch sites).
- `CLAUDE.md` §"feedback_ui_language" (memory) — ENGLISH UI ONLY; REQUIREMENTS.md German strings are overridden per D-04 / D-12 above.
- `CLAUDE.md` §"feedback_no_inline_styles" (memory) — D-12 reuses `alert-warning` class; no `style=` attributes.
- `CLAUDE.md` §"feedback_e2e_verification" (memory) — Final `./mvnw verify -Pe2e` BUILD SUCCESS is the Phase 76 verification gate.
- `.planning/codebase/CONVENTIONS.md` §"flash attributes" — `successMessage` / `errorMessage` keys are reused for the 409 / auto-backup-failure flashes (D-04 / D-17).
- `.planning/codebase/ARCHITECTURE.md` §"Exception mapping" — `BusinessRuleException` → 409 already exists at the global handler level. Phase 76 D-05 sets 409 inline at the controller (not via a thrown exception) because the 409 is a UX signal, not a business-rule violation. This is a deliberate divergence; the global handler stays untouched.

### External APIs (consulted, not on-disk)

- `java.util.concurrent.locks.ReentrantLock` — `tryLock()` non-blocking, `isLocked()` read-only, `isHeldByCurrentThread()` for the idempotent-unlock guard (D-01).
- Spring `HandlerInterceptor.preHandle(...)` — D-07's request-short-circuit primitive.
- Spring `WebMvcConfigurer.addInterceptors(InterceptorRegistry)` — registration site (D-07).
- Spring `@ControllerAdvice` + `@ModelAttribute` — D-11's banner-flag injection (mirrors `GlobalModelAdvice`).
- Spring `RedirectView.setStatusCode(HttpStatus)` — fallback path in CD-01 if `response.setStatus(409)` + `redirect:` string leaks back to 302.
- Spring `@TransactionalEventListener(phase=AFTER_COMMIT)` — already in use by Phase 75 Plan 07; default synchronous-on-same-thread semantics enable D-06's "release after `execute()` returns" pattern.
- `java.nio.file.Files.newOutputStream(Path, StandardOpenOption.CREATE_NEW)` — D-16's atomic-create-or-fail auto-backup write target.
- Spring `HttpStatus.CONFLICT` (409) / `HttpStatus.SERVICE_UNAVAILABLE` (503) — D-05 / D-08 status codes.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`BackupArchiveService.writeZip(OutputStream, Instant)` (Phase 73)** — Streaming export with `@Transactional(readOnly=true)` joins; Phase 76 D-16 reuses verbatim with a `FileOutputStream` target. No new overload, no new API surface on the service.
- **`DataImportAuditService.recordResult(...)` (Phase 75 D-01)** — REQUIRES_NEW propagation; Phase 76 D-18 calls it with empty `tableCountsWiped` / `tableCountsRestored` maps on the auto-backup-failure path. Audit row survives even though no `@Transactional` rollback occurs (because nothing was mutated).
- **`BackupImportException(auditUuid, auditWritten, cause)` (Phase 75)** — Failure carrier with WR-03 distinction between "audit row exists" vs "audit-write itself failed". Phase 76 D-17 subclasses it for `AutoBackupBeforeImportException` — controller flash uses the same `auditWritten ? auditUuid : "unavailable (...)"` rendering already in `BackupController.importExecute`.
- **`BackupImportSucceededEvent` + AFTER_COMMIT listener (Phase 75 Plan 07)** — The synchronous-on-same-thread default enables D-06's "release lock in finally after `execute()` returns" simplification. No need to thread a CountDownLatch through the event flow.
- **`RestoreFailureInjector` interface + `NoopRestoreFailureInjector` `@Primary` (Phase 75 D-13)** — Extension point Phase 76 D-20 reuses for the 2-thread IT's "deliberately slow" injection. Test-only `@Primary` override pattern is identical to Phase 75's `FailAtTableInjector`.
- **`alert-warning` CSS class (`admin.css:161`)** — Dark-yellow on dark-bg (`#3b2e0e` / `#ffb74d` / border `#b26a00`). Phase 76 D-12 reuses verbatim; zero CSS additions.
- **`GlobalModelAdvice` (`org.ctc.admin.controller`)** — Single-class `@ControllerAdvice` with a `@ModelAttribute` method (currently exposes `appVersion`). Phase 76 D-11's `ImportLockBannerAdvice` mirrors the shape one-for-one.
- **`WebConfig` (`org.ctc.admin`)** — Existing `WebMvcConfigurer`. Phase 76 D-07 ADDS `addInterceptors(...)` override; existing `addResourceHandlers(...)` is untouched.
- **`app.backup.import-backups-dir` (`application.yml:6`)** — Existing config property already resolves to `data/.import-backups`. Phase 76 reuses for the auto-backup ZIP parent directory.
- **`BackupController.importExecute` catch-chain (Phase 75 D-15 + WR-06)** — Already structured for multiple flash strings; Phase 76 adds one more `catch (AutoBackupBeforeImportException)` clause at the top of the chain (subclass-first matching).

### Established Patterns

- **`@RequiredArgsConstructor` + `@Slf4j`** — `ImportLockService`, `ImportLockBannerAdvice`, `ImportLockedWriteRejector`, `BlockingRestoreFailureInjector` all follow.
- **`@ControllerAdvice` + single `@ModelAttribute` method** — `ImportLockBannerAdvice` follows `GlobalModelAdvice` shape exactly.
- **`@Service @Scope("singleton")` (default)** — `ImportLockService` is singleton-by-default; the explicit `@Scope("singleton")` annotation per REQUIREMENTS.md SECU-05 wording is redundant but harmless — KEEP it for documentation value.
- **`RedirectAttributes`-backed Flash** — D-04 / D-17 flash strings follow Phase 75 D-15 convention.
- **`@Transactional` boundaries** — D-14's auto-export joins the outer `execute()` tx (REQUIRED); the auto-export's own `@Transactional(readOnly=true)` declaration is a no-op join.
- **Given-When-Then test naming** — `ImportConcurrentLockIT`, `ImportLockedPostRejectorIT`, `ImportLockBannerAdviceIT` all follow.
- **Failsafe IT location convention** — `src/test/java/org/ctc/backup/it/` (Phase 75 baseline) — Phase 76 ITs land in the same package.

### Integration Points

- **New classes (Phase 76 adds, no existing class deleted/renamed):**
  - `org.ctc.backup.lock.ImportLockService` — singleton `ReentrantLock` wrapper (D-01).
  - `org.ctc.backup.lock.ImportLockedWriteRejector` — `HandlerInterceptor` (D-07).
  - `org.ctc.backup.lock.ImportLockBannerAdvice` — `@ControllerAdvice` `@ModelAttribute` (D-11).
  - `org.ctc.backup.exception.AutoBackupBeforeImportException extends BackupImportException` (D-17).
  - `docs/operations/import-runbook.md` — new file, new directory (D-22).
- **Extended classes:**
  - `org.ctc.backup.BackupController.importExecute` — adds tryLock / finally unlock + 409 status + AutoBackupBeforeImportException catch (D-04, D-05, D-17).
  - `org.ctc.backup.service.BackupImportService.execute` — adds 3-statement auto-export block + moves `<ts>` computation up (D-14, D-15, D-16).
  - `org.ctc.admin.WebConfig` — adds `addInterceptors(...)` override (D-07).
- **Templates:**
  - `src/main/resources/templates/admin/layout.html` — adds one `<div th:if>` for the banner (D-12).
- **CSS / JS:**
  - Zero additions — `alert-warning` reused (D-12).
- **Config:**
  - Zero additions — `app.backup.import-backups-dir` reused.
- **Schema / migrations:**
  - Zero additions — `data_import_audit` from Phase 72 V7 sufficient (D-25).
- **Tests (Failsafe ITs):**
  - `ImportConcurrentLockIT` (D-20) — 2-thread test under `org.ctc.backup.it`.
  - `ImportLockedPostRejectorIT` (D-21) — drives slow import, asserts 503 on non-whitelisted POST + non-rejection on whitelisted POST.
  - `ImportLockBannerAdviceIT` (D-21) — drives slow import, asserts banner text in `GET /admin/seasons` response body.
  - `ImportLockServiceTest` (Surefire) — unit test for tryLock / unlock / isLocked semantics including the `isHeldByCurrentThread()` idempotent-unlock guard.
  - `AutoBackupBeforeImportPathIT` (Failsafe) — happy-path: import succeeds → auto-backup ZIP exists at `data/.import-backups/<ts>/auto-backup-before-import.zip` with same `<ts>` as `uploads-old/` sibling.
  - `AutoBackupBeforeImportFailureIT` (Failsafe) — inject `IOException` on auto-export write (e.g., via a read-only directory) → assert: no wipe occurred (row counts unchanged), audit row exists with success=false / empty count maps (D-18), Flash matches D-17 wording, partial ZIP cleaned up.

</code_context>

<specifics>
## Specific Ideas

- **The "@ControllerAdvice filter" wording in REQUIREMENTS.md SECU-06 is a slip — `@ControllerAdvice` cannot intercept requests.** Phase 76 D-07 swaps to `HandlerInterceptor`. The user-visible behavior (HTTP 503 on non-whitelisted POST during lock) is identical. This is documented inline in D-07 so a future reviewer doesn't "fix" the implementation back to the literal wording. The roadmap goal text uses the more accurate "@ControllerAdvice filter rejects" — interpret "filter" as the abstract concept, not Servlet `Filter`.
- **The German UI strings in REQUIREMENTS.md SECU-05/06 are OVERRIDDEN by `feedback_ui_language`.** All admin UI text in CTC Manager is English (per CLAUDE.md memory). Phase 76 D-04 + D-12 lock the English wording: "Another import is already running — please wait." and "Backup import in progress — write access is temporarily locked."
- **The `<ts>` shared between auto-backup and uploads-old is non-negotiable.** Phase 75 D-11 already anchored the format and the co-location intent. Phase 76 D-15 enforces the single-source-of-truth: compute `<ts>` ONCE at the top of `BackupImportService.execute()` and reuse it for both the auto-backup ZIP path and the `BackupImportSucceededEvent.importBackupDir` payload. Two calls to `Instant.now()` would create different second-truncated values on cycle boundaries — `<ts>` divergence between the auto-backup ZIP and the uploads-old directory breaks the operator's mental model.
- **CSS class `alert-warning` is already defined.** No new CSS file, no new class, no `style=` attribute. The class is already used elsewhere in the admin UI (presumably) and the dark-yellow-on-dark-bg styling matches the existing dark-theme palette without coordination.
- **`AFTER_COMMIT` listener is synchronous-on-same-thread by default.** Phase 75 Plan 07 listener runs in the same thread as `execute()`. Therefore the controller's `try { execute(); } finally { unlock(); }` correctly releases the lock AFTER the listener has run — including the uploads-move step. No cross-thread coordination needed for lock release.
- **The 503 interceptor is BEHIND security.** Spring's filter chain runs Spring Security's filters BEFORE MVC HandlerInterceptors. So an unauthenticated POST on prod/docker sees 403 first (Spring Security), not 503. Authenticated admin sees 503 during the lock. Anonymous on dev/local (OpenSecurityConfig) sees 503 directly. This matches the goal ("HTTP 503" — not "HTTP 503 ONLY for authenticated users").
- **The 409 response is on a redirect.** The controller sets HTTP 409 then returns `redirect:/admin/backup`. The browser sees HTTP 409 with a `Location` header (technically `409 + Location` is unusual but RFC-permitted), and follows the redirect — the GET shows the locked-state Flash. This is the cleanest UX: the admin sees the regular backup page with a clear error message, not a generic 409 error page.

</specifics>

<deferred>
## Deferred Ideas

- **Lock fairness / queue ordering** — Default `ReentrantLock(false)` per D-01. If a future use case ever wants "first-come-first-served" semantics, the constructor swap is trivial. Not worth the throughput cost for an admin-only feature.
- **Distributed lock (multi-instance deployment)** — `ImportLockService` interface design (D-03) accommodates a future Hazelcast / Redis-backed impl. Out of scope for v1.10 because CTC is a single-JVM deployment.
- **`/admin/backup/history` audit-viewer page** — v1.11+ (Phase 74/75 already deferred this). Phase 76 surfaces audit-id in the new D-17 flash for SQL drill-down.
- **`@Scheduled` cleanup of `data/.import-backups/<ts>/` beyond 24 h** — Operator-driven cleanup per D-22 runbook entry #2. If operational pain warrants automated cleanup, v1.11 adds a one-line `@Scheduled` job.
- **Banner on site templates** — Phase 76 D-13 excludes site templates (public read-only pages). If a future feature adds writeable site forms, the banner advice already injects the model attribute globally; only the site `layout.html` needs to opt in.
- **Async / background auto-backup** — SECU-07 locks synchronous behavior. Async with progress polling is `OPS-FUT-01` / Future Requirements territory.
- **Maven Enforcer rule for "no inline style on import-execute path"** — `feedback_no_inline_styles` is a general-purpose rule; Phase 71-05 PLAT-07 already covers fragment-call ternaries. No new build guard needed for Phase 76.
- **Banner sticky / fixed positioning** — Default flow (inline at top of main content) is sufficient. If operator UX feedback wants always-visible-while-scrolling, future enhancement adds `position: sticky; top: 0` to a new `.import-banner` CSS class.
- **`role="alert"` instead of `role="status"`** — Default `role="status"` per D-12 / CD-04 is polite (announces on page load only). Aggressive `role="alert"` would interrupt screen-reader users mid-page. Switchable if operator feedback demands.
- **403 vs 503 during lock for unauthenticated users on prod/docker** — Today they see 403 first (Spring Security runs before the interceptor). Phase 76 does NOT change the filter order; if a future requirement wants "always 503 regardless of auth status during a lock", that's a filter-chain reorder, not a Phase 76 concern.
- **Multi-Tab Detection** — A single admin opening two browser tabs and POSTing import-execute from both would trip SECU-05 correctly via the lock. No need for cookie / session-attribute tab-id tracking.

</deferred>

---

*Phase: 76-operational-hardening-import-lock-read-only-banner-auto-back*
*Context gathered: 2026-05-14*
