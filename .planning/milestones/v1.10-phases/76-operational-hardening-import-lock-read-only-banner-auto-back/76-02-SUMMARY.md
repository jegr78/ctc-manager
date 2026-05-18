---
phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
plan: 02
status: complete
requirements:
  - SECU-06
files_created:
  - src/main/java/org/ctc/backup/lock/ImportLockBannerAdvice.java
  - src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java
  - src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java
  - src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java
files_modified:
  - src/main/java/org/ctc/admin/WebConfig.java
  - src/main/resources/templates/admin/layout.html
---

# Plan 76-02 — Ring 2: Read-Only Banner + Write Rejector (SECU-06)

Advertises the read-only state on every admin GET response and enforces it on
every non-whitelisted admin POST while `ImportLockService.isLocked()` returns
true. Closes SECU-06.

## Artifacts

| Path | Role |
|------|------|
| `org.ctc.backup.lock.ImportLockBannerAdvice` | `@ControllerAdvice` exposing `${importInProgress}` model attribute on every admin GET; reads `importLockService.isLocked()`. |
| `org.ctc.backup.lock.ImportLockedWriteRejector` | `HandlerInterceptor.preHandle` rejecting POST/PUT/PATCH/DELETE requests under `/admin/**` with HTTP 503 + locked flash when the lock is held. Whitelists `/admin/backup/import-execute` (the import endpoint itself, already gated by `tryLock`) and any non-admin paths. |
| `org.ctc.admin.WebConfig.addInterceptors(...)` | Registers `ImportLockedWriteRejector` on `/admin/**` paths. |
| `templates/admin/layout.html` | Yellow `alert alert-warning` banner with `role="status"` rendered between the global nav and the per-page flash blocks when `${importInProgress}` is true. Wording: `Backup import in progress — write access is temporarily locked.` |

## Verification

```
$ ./mvnw -q -Dit.test='ImportLockBannerAdviceIT,ImportLockedPostRejectorIT' -DfailIfNoTests=false verify
[INFO] BannerAdvice — Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 (36.7 s)
[INFO] PostRejector — Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 (5.6 s)
[INFO] BUILD SUCCESS
```

## Scope deviation (deliberate)

The original Task 3 implementation crashed on the slow-import-handshake tests
because the `BlockingRestoreFailureInjector.Config` (Plan 76-01) exposes its
two `CountDownLatch` beans as singletons in a Spring context whose cache key
is identical to `ImportConcurrentLockIT` — and `CountDownLatch` is
non-resettable. The first IT to consume a latch left it at 0; every
subsequent `hasAcquired.await(...)` returned `true` immediately and the
`isLocked()` assertion fired before thread A had even acquired the lock.

The fix annotates both 76-02 IT classes with
`@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)` so each
`givenLockHeld_*` test method starts with a fresh Spring context (and fresh
latches). The trade-off is ~5–10 seconds of context startup per dirty
method, total IT class wall-clock around 40 seconds — acceptable for these
7 tests. Plan 76-01's `ImportConcurrentLockIT` does not need the fix because
its single slow-import test runs alone.

## Handoff to Plan 76-04

Rings 1 and 2 active. Every admin GET advertises the locked state with a
visible banner; every non-whitelisted admin POST is blocked with HTTP 503
while a backup import runs. Plan 76-04 documents the operator runbook
(banner wording, recovery steps, 24h `.import-backups/` retention policy).
