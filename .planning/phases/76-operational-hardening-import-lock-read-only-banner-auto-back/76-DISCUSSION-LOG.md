# Phase 76: Operational Hardening — Import Lock + Read-Only Banner + Auto-Backup-Before-Import - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-14
**Phase:** 76-operational-hardening-import-lock-read-only-banner-auto-back
**Areas discussed:** Lock service shape, Lock acquisition site, 503-rejector mechanism, Banner wiring, Auto-backup wiring, Test injection, Operational runbook location
**Mode:** Auto-resolved per system directive ("work without stopping for clarifying questions"). All gray areas decided by Claude using CLAUDE.md conventions, Phase 75 carry-forward, and locked feedback rules. User can redirect any decision before plan-phase.

---

## Lock Service Shape (resolves SECU-05 wiring)

| Option | Description | Selected |
|--------|-------------|----------|
| `ReentrantLock` singleton wrapper with `tryLock()` / `unlock()` / `isLocked()` | Standard JDK primitive, minimal surface, three idempotent methods | ✓ |
| `Semaphore(1)` | Equivalent semantics but less natural API for "is this lock held?" check | |
| `synchronized` static method | Crude; ties lock to a static class which obstructs the `isLocked()` check the banner needs | |
| DB-backed advisory lock (`SELECT GET_LOCK(...)`) | Distributed-lock semantics; over-engineered for a single-JVM deployment | |

**Decision:** D-01 — `ReentrantLock` singleton wrapped in `org.ctc.backup.lock.ImportLockService`. Non-blocking `tryLock()` per D-02 (HTTP 409 immediate). Fairness flag false per CLAUDE.md "no premature abstraction".
**Notes:** Memory-only state per D-03 — CTC is single-JVM. Interface stays clean for a future Hazelcast-backed swap if multi-instance deployment ever materializes.

---

## Lock Acquisition Site

| Option | Description | Selected |
|--------|-------------|----------|
| Controller-level `tryLock()` before delegating to service | Short-circuits before any expensive work; 409 visible at the HTTP boundary | ✓ |
| Service-level wrapper inside `BackupImportService.execute(...)` | Cohesion with resource; but auto-backup (D-14) is the first DB-reading step — we'd still pay 10 MB write cost on a queued reject | |
| AOP `@Aspect` with custom `@ImportLocked` marker | Hides behavior in advice; harder to debug in the controller's catch chain | |
| Servlet `Filter` | Heavier than needed; HandlerInterceptor is the idiomatic Spring MVC choice | |

**Decision:** D-04 — controller-level `tryLock()` + `finally unlock()` wrapper. Release after `execute()` returns is correct per D-06 (AFTER_COMMIT listener is synchronous-on-same-thread by Spring default).
**Notes:** 409 status set on the redirect response via `response.setStatus(409)` per D-05; CD-01 fallback to `RedirectView.setStatusCode(...)` if Spring upgrades break the redirect-status preservation.

---

## 503-Rejector Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| `HandlerInterceptor` registered in `WebConfig` | Idiomatic Spring MVC primitive for "intercept request, optionally short-circuit before controller" | ✓ |
| Servlet `Filter` / `OncePerRequestFilter` | More general but heavier; runs before MVC dispatch — not needed | |
| `@ControllerAdvice` with `@ModelAttribute` short-circuit | NOT POSSIBLE — `@ControllerAdvice` cannot reject requests, only handle exceptions or contribute model attributes (REQUIREMENTS.md SECU-06 wording slip — see D-07) | |
| Per-controller `@Around` AOP | High plumbing cost; would require marker annotations on every admin controller method | |

**Decision:** D-07 — `org.ctc.backup.lock.ImportLockedWriteRejector` `HandlerInterceptor` registered in `WebConfig.addInterceptors(...)` with `addPathPatterns("/admin/**")`.
**Notes:** REQUIREMENTS.md SECU-06's literal wording "@ControllerAdvice filter" is interpreted as the abstract "request filter" concept, not the Spring `@ControllerAdvice` annotation. Behavior matches the goal verbatim; mechanism is corrected to the semantically-valid Spring primitive. Whitelist = exactly `POST /admin/backup/import-execute` per D-09 (`equals`, not `startsWith` per D-10).

---

## Banner Wiring

| Option | Description | Selected |
|--------|-------------|----------|
| `@ControllerAdvice` injecting `importInProgress` model attribute | Mirrors existing `GlobalModelAdvice`; layout.html uses `th:if="${importInProgress}"` | ✓ |
| `HandlerInterceptor` sets request attribute | Layout HTML reads via `${#request.getAttribute(...)}` — awkward Thymeleaf | |
| Spring-injected bean exposed via SpEL | `${@importLockService.isLocked()}` — non-idiomatic; couples templates to bean names | |
| Custom Thymeleaf dialect | Massive overkill | |

**Decision:** D-11 — `org.ctc.backup.lock.ImportLockBannerAdvice @ControllerAdvice @ModelAttribute`. One-`<div>` edit to `admin/layout.html` per D-12; English wording "Backup import in progress — write access is temporarily locked."; reuses existing `alert alert-warning` CSS class (`admin.css:161`); zero new CSS.
**Notes:** Site templates (`templates/site/...`) excluded per D-13 — public read-only pages don't render the banner even though the model attribute is set globally (harmless null when `th:if` not used). `role="status"` per CD-04 for polite screen-reader semantics.

---

## Auto-Backup Wiring

| Option | Description | Selected |
|--------|-------------|----------|
| First DB-reading statement INSIDE `BackupImportService.execute()`, before wipe | Auto-export joins the outer @Transactional (REQUIRED) and reads pre-wipe snapshot; shares `<ts>` with uploads-old | ✓ |
| Controller-level before `service.execute()` call | Two `<ts>` computations to keep in sync; awkward to thread the auto-backup Path into the service | |
| New service method `executeWithAutoBackup(...)` wrapping existing `execute()` | Adds another service method; doesn't materially simplify | |
| `@TransactionalEventListener(phase=BEFORE_COMMIT)` triggered from a no-op marker event | Way too cute; nobody can read it | |

**Decision:** D-14 — auto-export runs inside `execute()`, BEFORE wipe, reusing `BackupArchiveService.writeZip(OutputStream, Instant)` with a `Files.newOutputStream(..., CREATE_NEW)` target per D-16. `<ts>` is computed ONCE at top of `execute()` and reused for both auto-backup ZIP path and the `BackupImportSucceededEvent.importBackupDir` payload per D-15.
**Notes:** `AutoBackupBeforeImportException extends BackupImportException` per D-17 — controller catches it first in the catch chain (subclass-first matching) and flashes the locked D-17 wording. Audit row written via REQUIRES_NEW with empty count maps per D-18. Partial-ZIP cleanup is best-effort and never throws (D-19).

---

## Test Injection for 2-Thread Concurrent IT

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse Phase 75 `RestoreFailureInjector` with a blocking-instead-of-failing impl | Single extension point; test installs `BlockingRestoreFailureInjector` `@Primary` bean that `latch.await(5, SECONDS)` instead of throwing | ✓ |
| New parallel `RestoreSlownessInjector` interface | Two injectors for one concern; muddies the production code | |
| `Thread.sleep(...)` in a `@TestConfiguration` `@Bean` post-construct hook | Flaky on slow CI; no clean coordination signal | |
| Mockito `@SpyBean` on `JdbcTemplate` | Brittle; couples test to the restore implementation internals | |

**Decision:** D-20 — extend the Phase 75 `RestoreFailureInjector` extension point semantically ("influences restore timing/behavior"); add a test-only `BlockingRestoreFailureInjector` under `src/test/java/.../support/` per CD-05.
**Notes:** `ImportConcurrentLockIT` (`org.ctc.backup.it`) drives the 2-thread scenario with two latches (release-A after B's 409 observed). D-21 adds `ImportLockedPostRejectorIT` + `ImportLockBannerAdviceIT` (or merged class per CD-05) for the 503 and banner-rendering coverage. Surefire `ImportLockServiceTest` covers the unit-level tryLock / unlock / isLocked semantics.

---

## Operational Runbook Location

| Option | Description | Selected |
|--------|-------------|----------|
| `docs/operations/import-runbook.md` (new top-level operations directory) | Plain Markdown; clear ownership; future operational docs co-locate | ✓ |
| `docs/superpowers/specs/import-runbook.md` | Spec directory is for design specs, not runbooks | |
| `docs/site/operations/...` | Site directory is regenerated on each build; runbook would disappear | |
| WIKI page | Phase 77 QUAL-05 owns the wiki — collision; Phase 76's runbook is text-file scope per goal-bullet-4 | |

**Decision:** D-22 — `docs/operations/import-runbook.md` (new directory). Content outline locked: recovery from auto-backup, 24h retention semantics, audit-id query SQL, concurrent-import behavior, read-only state during imports.
**Notes:** Phase 77 QUAL-05 will likely link to or summarize this runbook from the wiki page — the runbook stays the source of truth for operational details.

---

## Claude's Discretion

Areas where multiple implementations are valid and the planner can choose:

- **CD-01:** HTTP 409 wire mechanism — `response.setStatus(409)` + `redirect:` string is the default; fallback to `RedirectView.setStatusCode(...)` if Spring redirect-status preservation breaks.
- **CD-02:** Lock service package location — default `org.ctc.backup.lock`; could move to `org.ctc.admin.lock` if a future admin operation also wants a lock.
- **CD-03:** 503-HTML body shape — minimal hand-written HTML string with `<meta http-equiv="refresh" content="10">` is the recommended default; richer Thymeleaf-rendered body is overkill for an interceptor path.
- **CD-04:** Banner ARIA role — default `role="status"` (polite); switchable to `role="alert"` if operator demands.
- **CD-05:** `BlockingRestoreFailureInjector` location — default `src/test/java/.../support/` shared package; can inline as `@TestConfiguration` nested class if only one IT needs it after refinement.
- **CD-06:** Auto-backup filename — `auto-backup-before-import.zip` locked per SECU-07; no ambiguity remains.

## Deferred Ideas

Ideas mentioned during analysis that belong in future phases (full list in CONTEXT.md `<deferred>` section):

- Lock fairness (premature optimization)
- Distributed lock for multi-instance deployment (v2.x territory)
- `/admin/backup/history` audit-viewer page (v1.11+)
- `@Scheduled` cleanup of `data/.import-backups/<ts>/` beyond 24 h (operator-driven for now)
- Banner on site templates (no site write paths today)
- Async / background auto-backup (locked synchronous per SECU-07)
- Banner sticky positioning (default inline flow is sufficient)
- `role="alert"` instead of `role="status"` (polite default sufficient)
- 403 vs 503 ordering for unauthenticated requests (filter-chain order is preserved)
- Multi-tab detection (lock semantics handle this correctly)
