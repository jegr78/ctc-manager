# Phase 76: Operational Hardening — Import Lock + Read-Only Banner + Auto-Backup-Before-Import — Research

**Researched:** 2026-05-14
**Domain:** Spring MVC concurrency (ReentrantLock + HandlerInterceptor), Spring transactional event-listener semantics, JDK NIO atomic-create-or-fail file primitives
**Confidence:** HIGH

## Summary

Phase 76 layers three defensive rings around the Phase 75 wipe+restore path. The implementation shape is fully locked by CONTEXT.md (25 D-decisions + 6 CD-decisions); this research validates the Spring/JDK API contracts the plan relies on, exposes the conflict points the planner must pre-empt in existing code (`BackupController.importExecute` already has a four-clause catch chain — the new `AutoBackupBeforeImportException` must be added *first* per Java exception-matching order), and documents the test-injection discipline that Phase 75's `FailAtTableInjector` already established and that a new `BlockingRestoreFailureInjector` must mirror exactly (same Spring bean name `noopRestoreFailureInjector`, `@Primary`, `spring.main.allow-bean-definition-overriding=true` on the IT — otherwise Spring rejects the bean graph at startup).

Three landmines surfaced that the planner must address before tasks land:

1. **`response.setStatus(409) + redirect:` interaction on Spring Boot 4.0.6** — Spring's view-resolution layer for the `redirect:` prefix uses `RedirectView`, and `RedirectView#renderMergedOutputModel` calls `sendRedirect(...)` which *overwrites* the explicit status with 302 in the default `http10Compatible=true` mode. CD-01's fallback path (return `View`-object with `setStatusCode(HttpStatus.CONFLICT)`) is the correct primary implementation, not a fallback. `response.setStatus(409)` alone leaks back to 302.
2. **AFTER_COMMIT listener runs on the same thread** — verified against the existing `BackupImportPostCommitListener`. The `@TransactionalEventListener(phase=AFTER_COMMIT)` is synchronous-on-the-publisher-thread by default (no `@Async`, no `TaskExecutor` configured). D-06's "release lock in `finally` after `execute()` returns" is therefore correct — but the planner must NOT add `@Async` to the listener or the lock semantics break.
3. **Existing `BackupController.importExecute` catch chain is four clauses deep** — `BackupArchiveException`, `IOException`, `UploadsRestoreException`, `BackupImportException`. The new `AutoBackupBeforeImportException` is a *subclass* of `BackupImportException` (D-17), so Java exception matching requires it placed *before* the `BackupImportException` clause. The wrapper try/catch from D-04 must be the outermost wrapping; the inner catch chain stays largely intact but gets one new clause inserted at the right position.

**Primary recommendation:** Implement D-05's HTTP 409 via the `View`-mode redirect (CD-01's fallback) from day one — empirical Spring behavior says `response.setStatus(409) + return "redirect:/admin/backup"` will leak back to 302 because `HttpServletResponse#sendRedirect` overwrites status. Save the integration-test iteration by going straight to the working primitive.

## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01 — `org.ctc.backup.lock.ImportLockService` — singleton bean with a private `ReentrantLock`.** Exposes `boolean tryLock()` (non-blocking; returns false immediately if held), `void unlock()` (idempotent — guarded by `lock.isHeldByCurrentThread()`), `boolean isLocked()` (read-only). No fairness flag.

**D-02 — tryLock is non-blocking (timeout = 0).**

**D-03 — Lock state is in-memory, single-JVM only.**

**D-04 — Lock acquired in `BackupController.importExecute` BEFORE delegating to the service.** Controller pseudocode locked verbatim.

**D-05 — HTTP 409 status is set on the redirect response itself.** CD-01 fallback (View-mode redirect with `setStatusCode`) is the verified-working primary path.

**D-06 — Lock is released in `finally` AFTER `execute()` returns.** AFTER_COMMIT listener is synchronous-on-same-thread.

**D-07 — `HandlerInterceptor`, NOT `@ControllerAdvice`.** Registered in `org.ctc.admin.WebConfig` via `addInterceptors(...)`.

**D-08 — preHandle logic** (locked, see CONTEXT for full code).

**D-09 — Whitelist = exactly one URL — `POST /admin/backup/import-execute`.**

**D-10 — Whitelist match is `equals(requestURI)`, NOT `startsWith`.**

**D-11 — `@ControllerAdvice ImportLockBannerAdvice` injects `importInProgress` model attribute.** Mirrors `GlobalModelAdvice`. Applies to all controllers (admin + site).

**D-12 — `admin/layout.html` adds one `<div>` BEFORE the existing flash divs.** English wording: `"Backup import in progress — write access is temporarily locked."`. `role="status"`. Reuses `alert-warning` CSS class.

**D-13 — Site templates (NOT admin) do not show the banner.** Site `layout.html` does not include the conditional div; `${importInProgress}` evaluates harmlessly to null/false in any site template that doesn't reference it.

**D-14 — Auto-export runs as the FIRST DB-reading statement inside `BackupImportService.execute(...)`, BEFORE wipe.**

**D-15 — `<ts>` is computed once at top of `execute()` and shared between auto-backup and uploads-old.** Currently at line 456; must move up to ~line 440.

**D-16 — Auto-export reuses `BackupArchiveService.writeZip(OutputStream, Instant)` with a `FileOutputStream` target.** `StandardOpenOption.CREATE_NEW`.

**D-17 — `AutoBackupBeforeImportException extends BackupImportException`.** Distinct subclass; controller catches it FIRST in the chain. Flash wording: `"Import aborted — pre-import auto-backup failed. No database changes. Audit-id: {auditUuid}."`.

**D-18 — Audit row on auto-backup failure has `wiped_counts={}` and `restored_counts={}` (both empty JSON objects).**

**D-19 — Partial-ZIP cleanup is best-effort, never throws.**

**D-20 — `ImportConcurrentLockIT` (Failsafe IT) under `org.ctc.backup.it` — reuses Phase 75's `RestoreFailureInjector` extension point.** Test-only `BlockingRestoreFailureInjector` `@Primary` bean overrides production `NoopRestoreFailureInjector`.

**D-21 — 503-rejector IT — `ImportLockedPostRejectorIT` (Failsafe).** Banner advice IT — `ImportLockBannerAdviceIT`. Planner-judgment: split or merge.

**D-22 — `docs/operations/import-runbook.md`.** New top-level operations directory. Markdown. NOT a wiki page.

**D-23 — Phase 75 code is touched ONLY at three sites.**

**D-24 — No new Maven dependencies.**

**D-25 — No new Flyway migration.**

### Claude's Discretion

- **CD-01: HTTP 409 wire mechanism** — `response.setStatus(409)` + `redirect:` vs View-mode redirect. RESEARCH finding: View-mode is the correct primary path; `response.setStatus(409)` alone leaks back to 302.
- **CD-02: Lock service location** — `org.ctc.backup.lock` (default).
- **CD-03: 503-HTML body** — minimal hand-written HTML string (default) with optional `<meta http-equiv="refresh" content="10">`.
- **CD-04: Banner role attribute** — `role="status"` (default).
- **CD-05: `BlockingRestoreFailureInjector` location** — `src/test/java/org/ctc/backup/it/support/` (default).
- **CD-06: Auto-backup filename** — `auto-backup-before-import.zip` (locked by SECU-07).

### Deferred Ideas (OUT OF SCOPE)

- Lock fairness / queue ordering
- Distributed lock (multi-instance deployment)
- `/admin/backup/history` audit-viewer page (v1.11+)
- `@Scheduled` cleanup of `data/.import-backups/<ts>/` beyond 24 h
- Banner on site templates
- Async / background auto-backup
- Maven Enforcer rule for "no inline style on import-execute path"
- Banner sticky / fixed positioning
- `role="alert"` instead of `role="status"`
- 403 vs 503 during lock for unauthenticated users on prod/docker
- Multi-Tab Detection beyond what the lock naturally provides

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SECU-05 | Concurrent-Import-Lock — `ImportLockService` with `ReentrantLock`-Singleton; second parallel import rejected with HTTP 409 + Flash | ReentrantLock + tryLock semantics, HTTP 409 wire mechanism, 2-thread test via BlockingRestoreFailureInjector pattern (Phase 75 D-13 mirror). Final UI wording per `feedback_ui_language` is English: "Another import is already running — please wait." |
| SECU-06 | Read-Only-Banner during import + `@ControllerAdvice`-filter rejects POSTs on other `/admin/**`-routes with HTTP 503; Import-Execute route whitelisted | `HandlerInterceptor.preHandle` semantics (NOT `@ControllerAdvice` per D-07 swap), `WebMvcConfigurer.addInterceptors(...)` registration site, Spring Security filter-chain ordering (503 fires AFTER 403 for unauthenticated). Final banner wording is English: "Backup import in progress — write access is temporarily locked." |
| SECU-07 | Auto-Backup-Before-Import — synchronous export to `data/.import-backups/<ts>/auto-backup-before-import.zip` BEFORE wipe; import aborts if export fails | `Files.newOutputStream(..., StandardOpenOption.CREATE_NEW)` atomic-create-or-fail semantics, `BackupArchiveService.writeZip(OutputStream, Instant)` REQUIRED-tx-join (read-only, no-op on rollback), REQUIRES_NEW audit-row write on failure path survives outer rollback (Phase 75 D-01 contract carried forward) |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Mutex acquisition / release | Controller (HTTP edge) | Service singleton (state) | Lock acquisition is a request-flow concern (D-04); the lock object itself is a domain-scoped singleton bean. Controller stays thin (CLAUDE.md): tryLock + finally unlock + delegate. |
| Lock-state polling for banner | `@ControllerAdvice` model attribute | Service singleton (read) | `ImportLockBannerAdvice` reads `lockService.isLocked()` — pure read, no state mutation. Mirrors `GlobalModelAdvice` pattern. |
| Write-rejection at request edge | Spring MVC HandlerInterceptor | Service singleton (read) | `HandlerInterceptor.preHandle` runs BEFORE the controller method body — the architecturally correct primitive for "short-circuit POSTs without invoking the controller". `@ControllerAdvice` (per REQUIREMENTS slip) cannot do this. |
| Auto-backup writing | Service body (inside `execute`) | `BackupArchiveService.writeZip` (Phase 73) | Auto-backup is a service-tier concern — the controller hands off to service, the service runs the ZIP write as the FIRST statement (D-14). File-system mutation joins the outer `@Transactional` (read-only join). |
| Audit-row write on auto-backup failure | `DataImportAuditService.recordResult` (REQUIRES_NEW) | Catch-block in `execute` | The audit row commits in its own transaction even if the outer `execute` tx rolls back — Phase 75 D-01 contract carried forward verbatim. |
| Banner DOM injection | Thymeleaf template (admin/layout.html) | `@ControllerAdvice` (model attribute) | Template renders `th:if="${importInProgress}"` over `<div class="alert alert-warning">`. Reuses existing CSS class — zero CSS additions. |
| Read-runbook documentation | Operations docs (`docs/operations/import-runbook.md`) | — | New top-level operations directory; plain Markdown; not in wiki (Phase 77 owns wiki). |

## Standard Stack

### Core (no new dependencies — D-24)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java.util.concurrent.locks.ReentrantLock` | JDK 25 | Mutex with `tryLock()` + `isHeldByCurrentThread()` | The canonical reentrant mutex; `tryLock()` non-blocking semantics + idempotent-unlock via `isHeldByCurrentThread()` guard are the textbook pattern for "acquire-or-fail" lock service in Spring singletons. |
| `org.springframework.web.servlet.HandlerInterceptor` | Spring MVC 7 (via Spring Boot 4.0.6) | Request short-circuit before controller invocation | `preHandle(req, res, handler)` is Spring's intended primitive for "intercept, optionally reject early without entering the controller." Documented in [Spring MVC reference §1.10.4](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html). |
| `org.springframework.web.servlet.config.annotation.WebMvcConfigurer#addInterceptors` | Spring MVC 7 | Interceptor registration | The standard hook for adding interceptors to the dispatcher servlet's chain. Existing `WebConfig.java` already implements `WebMvcConfigurer` — Phase 76 adds a second override. |
| `org.springframework.web.bind.annotation.ControllerAdvice` + `@ModelAttribute` | Spring MVC 7 | Cross-controller model attribute injection | Mirrors existing `GlobalModelAdvice` pattern verbatim. Applied to all `@Controller` beans by default — no `basePackages` filter needed (D-13 confirms harmless on site templates). |
| `org.springframework.web.servlet.view.RedirectView#setStatusCode` | Spring MVC 7 | HTTP-status-aware redirect | The View-mode redirect is the only way to honor a 4xx status on a redirect response on Spring Boot 4.0.6 (see Pitfall §1). `response.setStatus(409) + return "redirect:..."` leaks back to 302. |
| `java.nio.file.Files#newOutputStream(Path, StandardOpenOption.CREATE_NEW)` | JDK 25 | Atomic-create-or-fail file primitive | Throws `FileAlreadyExistsException` if target exists — protects against same-second timestamp collision (D-16 defensive intent). |
| `org.springframework.transaction.event.TransactionalEventListener(phase=AFTER_COMMIT)` | Spring TX 7 | Synchronous-on-publisher-thread post-commit hook | Already in use via `BackupImportPostCommitListener` (Phase 75 Plan 07). D-06 depends on synchronous-on-same-thread default; do NOT add `@Async` to the listener. |

**Installation:** No new packages. Maven `pom.xml` untouched per D-24.

**Version verification:** Spring Boot 4.0.6 confirmed in `pom.xml:7-8`. Spring MVC + Spring TX versions managed by `spring-boot-starter-parent`. JDK 25 confirmed in CLAUDE.md Technology Stack. `[VERIFIED: pom.xml read 2026-05-14]`

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `ReentrantLock` | `Semaphore(1)` | Semaphore lacks `isHeldByCurrentThread()` — the idempotent-unlock guard in D-01 wouldn't compile. Locked out. |
| `ReentrantLock` | `AtomicBoolean` + CAS | Sufficient for non-reentrant single-acquirer use, but the idempotent-unlock guard pattern in D-01 is cleaner with `isHeldByCurrentThread()`. Skipped for cohesion with the textbook ReentrantLock idiom. |
| `HandlerInterceptor` | Servlet `Filter` | Filter runs BEFORE Spring Security — would 503 unauthenticated users too. HandlerInterceptor runs AFTER security, which matches D-06's "authenticated admin sees 503, anonymous sees 403 first" intent. |
| `@ControllerAdvice` for banner | `HandlerInterceptor.postHandle` setting model attribute | `@ControllerAdvice` + `@ModelAttribute` is the idiomatic Spring MVC pattern — already used in `GlobalModelAdvice`. PostHandle is for response modification, not model injection. Skipped for symmetry with existing code. |
| `Files.newOutputStream(... CREATE_NEW)` | `Files.createFile(path) + Files.newOutputStream(path, WRITE)` | Two-step variant has a same-microsecond race window. Single-call `CREATE_NEW` is atomic at the OS level on all supported filesystems (POSIX `O_CREAT|O_EXCL`, NTFS `CREATE_NEW`). Locked-in for D-16. |

## Architecture Patterns

### System Architecture Diagram

```
                  ┌──────────────────────────────────────────────────────┐
                  │  HTTP Request                                         │
                  │  (admin POST /admin/backup/import-execute OR other)   │
                  └──────────────────┬───────────────────────────────────┘
                                     ▼
                  ┌──────────────────────────────────────────────────────┐
                  │  Spring Security FilterChain (prod/docker)           │
                  │  • Anonymous → 403 (terminates here)                 │
                  │  • Authenticated → fall through                      │
                  └──────────────────┬───────────────────────────────────┘
                                     ▼
                  ┌──────────────────────────────────────────────────────┐
                  │  ImportLockedWriteRejector.preHandle (NEW)           │
                  │  • method != POST → return true (GET passes)         │
                  │  • !lockService.isLocked() → return true             │
                  │  • URI == /admin/backup/import-execute → return true │
                  │  • else: 503 + small HTML body → return false        │
                  └──────────────────┬───────────────────────────────────┘
                                     ▼
                  ┌──────────────────────────────────────────────────────┐
                  │  ImportLockBannerAdvice.@ModelAttribute (NEW)        │
                  │  • importInProgress = lockService.isLocked()         │
                  │  • injected into ALL controller invocations          │
                  └──────────────────┬───────────────────────────────────┘
                                     ▼
                  ┌──────────────────────────────────────────────────────┐
                  │  BackupController.importExecute (EXTENDED)           │
                  │  if (!lockService.tryLock())                         │
                  │    → 409 (View-mode redirect) + flash                │
                  │  try { execute() } finally { lockService.unlock() }  │
                  └──────────────────┬───────────────────────────────────┘
                                     ▼
                  ┌──────────────────────────────────────────────────────┐
                  │  BackupImportService.execute @Transactional (EXTENDED)│
                  │  1. compute <ts> (MOVED to top — D-15)               │
                  │  2. resolve auto-backup path                         │
                  │  3. Files.newOutputStream(CREATE_NEW)                │
                  │  4. backupArchive.writeZip(out, Instant.now())       │
                  │     ↳ joins outer tx (read-only)                     │
                  │  5. → existing wipe / restore / event-publish        │
                  │  catch (IOException|RuntimeException):               │
                  │    tryDeletePartialAutoBackup                        │
                  │    throw new AutoBackupBeforeImportException         │
                  └──────────────────┬───────────────────────────────────┘
                                     ▼
                  ┌──────────────────────────────────────────────────────┐
                  │  On AutoBackupBeforeImportException (subclass of      │
                  │  BackupImportException):                              │
                  │  • outer @Transactional rolls back (no-op — no mut)   │
                  │  • DataImportAuditService.recordResult (REQUIRES_NEW) │
                  │    with empty wiped/restored maps + success=false    │
                  │  • controller catches FIRST in catch chain → flash   │
                  └──────────────────────────────────────────────────────┘

  Banner advice runs on EVERY controller invocation (admin GET, admin POST, site GET).
  HandlerInterceptor runs on every /admin/** request, but only short-circuits POSTs
  during a held lock. The lock is in-memory single-JVM.
```

### Recommended Project Structure

```
src/main/java/org/ctc/backup/
├── BackupController.java                        # EXTENDED — adds tryLock/finally + 409 + new catch
├── lock/
│   ├── ImportLockService.java                   # NEW — singleton ReentrantLock wrapper
│   ├── ImportLockedWriteRejector.java           # NEW — HandlerInterceptor
│   └── ImportLockBannerAdvice.java              # NEW — @ControllerAdvice
├── service/
│   ├── BackupImportService.java                 # EXTENDED — adds auto-export block
│   └── BackupArchiveService.java                # unchanged — reuses writeZip(OutputStream, Instant)
├── exception/
│   ├── BackupImportException.java               # unchanged
│   └── AutoBackupBeforeImportException.java     # NEW — extends BackupImportException
└── audit/
    └── DataImportAuditService.java              # unchanged — reuses recordResult REQUIRES_NEW

src/main/java/org/ctc/admin/
└── WebConfig.java                               # EXTENDED — adds addInterceptors(...)

src/main/resources/templates/admin/
└── layout.html                                  # EXTENDED — adds 1 <div th:if="${importInProgress}">

docs/operations/                                 # NEW directory
└── import-runbook.md                            # NEW — D-22 outline

src/test/java/org/ctc/backup/
├── lock/
│   └── ImportLockServiceTest.java               # NEW — Surefire unit
├── it/
│   ├── support/
│   │   └── BlockingRestoreFailureInjector.java  # NEW — test-only Primary bean
│   ├── ImportConcurrentLockIT.java              # NEW — Failsafe (D-20)
│   ├── ImportLockedPostRejectorIT.java          # NEW — Failsafe (D-21)
│   ├── ImportLockBannerAdviceIT.java            # NEW — Failsafe (D-21)
│   ├── AutoBackupBeforeImportPathIT.java        # NEW — Failsafe (happy)
│   └── AutoBackupBeforeImportFailureIT.java     # NEW — Failsafe (failure)
```

### Pattern 1: ReentrantLock with idempotent-unlock guard
**What:** Singleton bean wraps a `ReentrantLock`. `tryLock()` returns false on contention; `unlock()` checks `isHeldByCurrentThread()` before releasing so a stray `finally { unlock(); }` after a failed `tryLock()` is a no-op.
**When to use:** D-01 service shape — single-acquirer in-memory mutex.
**Example:**
```java
// Source: ReentrantLock Javadoc — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html
@Service
@Scope("singleton")  // explicit per REQUIREMENTS.md SECU-05 wording, redundant but documents intent
public class ImportLockService {
    private final ReentrantLock lock = new ReentrantLock();  // fairness=false (D-01)

    public boolean tryLock() {
        return lock.tryLock();  // zero-timeout, non-blocking (D-02)
    }

    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
        // idempotent — stray finally-unlock after failed tryLock is silent no-op
    }

    public boolean isLocked() {
        return lock.isLocked();  // does NOT require current thread to hold the lock
    }
}
```

### Pattern 2: HandlerInterceptor with whitelist short-circuit
**What:** `preHandle(req, res, handler)` runs after Spring Security filters but before the controller method body. Returning `false` short-circuits the request; response state set in `preHandle` IS preserved.
**When to use:** D-07 — block non-whitelisted writes while lock is held.
**Example:**
```java
// Source: Spring MVC reference §1.10.4 — https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html
@Component
@RequiredArgsConstructor
public class ImportLockedWriteRejector implements HandlerInterceptor {
    private static final String LOCK_HTML = """
        <!DOCTYPE html><html><head><meta charset="UTF-8">
        <meta http-equiv="refresh" content="10"></head>
        <body><h1>Backup import in progress — write access is temporarily locked.</h1>
        <p>This page will retry automatically.</p></body></html>
        """;

    private final ImportLockService lockService;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(req.getMethod())) return true;
        if (!lockService.isLocked()) return true;
        if ("/admin/backup/import-execute".equals(req.getRequestURI())) return true;
        res.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        res.setContentType("text/html;charset=UTF-8");
        res.getWriter().write(LOCK_HTML);
        return false;
    }
}
```

Registration in `WebConfig`:
```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(importLockedWriteRejector)
            .addPathPatterns("/admin/**");
}
```

### Pattern 3: View-mode redirect with explicit HTTP status (CD-01 PRIMARY path)
**What:** Spring's `redirect:` prefix uses `RedirectView`, whose `renderMergedOutputModel` calls `HttpServletResponse#sendRedirect`, which *forces* status to 302 in default `http10Compatible=true` mode. To honor 409 on a redirect, return a `View` object directly with explicit `setStatusCode`.
**When to use:** D-05 + CD-01 — concurrent-rejection 409.
**Example:**
```java
// Source: Spring RedirectView Javadoc — https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/view/RedirectView.html
@PostMapping("/import-execute")
public ModelAndView importExecute(
        @Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form,
        BindingResult bindingResult, RedirectAttributes ra) {

    if (!importLockService.tryLock()) {
        ra.addFlashAttribute("errorMessage", "Another import is already running — please wait.");
        RedirectView rv = new RedirectView("/admin/backup");
        rv.setStatusCode(HttpStatus.CONFLICT);  // 409 PRESERVED
        rv.setHttp10Compatible(false);          // required: in http10Compatible=true mode, sendRedirect OVERWRITES status
        return new ModelAndView(rv);
    }
    try {
        // existing binding + reparse + execute body
        // wrap return values as ModelAndView("redirect:/admin/backup") for symmetry
    } finally {
        importLockService.unlock();
    }
}
```

> **Pitfall:** `rv.setHttp10Compatible(false)` is REQUIRED. In default `true` mode, `RedirectView` calls `sendRedirect` which always emits 302. Setting it to `false` causes RedirectView to set the explicit `statusCode` + `Location` header directly. This is documented in the `RedirectView` Javadoc but easy to miss.

> **Alternative shape:** The controller can return a `String` for the success-path branches AND a `View`/`ModelAndView` for the 409 branch — Spring resolves both. But code consistency suggests `ModelAndView` everywhere in this method.

### Pattern 4: @ControllerAdvice + @ModelAttribute global injection
**What:** A single `@ControllerAdvice` class with one `@ModelAttribute` method makes a value available to every `@Controller` invocation.
**When to use:** D-11 — `importInProgress` flag.
**Example:**
```java
// Source: Spring MVC reference §1.5.3 — https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/controller-advice.html
// Mirrors existing org.ctc.admin.controller.GlobalModelAdvice verbatim.
@ControllerAdvice
@RequiredArgsConstructor
public class ImportLockBannerAdvice {
    private final ImportLockService importLockService;

    @ModelAttribute("importInProgress")
    public boolean importInProgress() {
        return importLockService.isLocked();
    }
}
```

> **Scoping note (D-13):** The advice applies to ALL controllers. Site templates that don't reference `${importInProgress}` evaluate it lazily — Thymeleaf returns null on missing-key for `th:if`, which is falsy. The model-attribute injection is harmless on site templates that don't conditionally render based on it.

### Pattern 5: Atomic-create-or-fail file write
**What:** `Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)` creates the file atomically and throws `FileAlreadyExistsException` if it exists — single-call, OS-level atomic.
**When to use:** D-16 — auto-backup ZIP target.
**Example:**
```java
// Source: StandardOpenOption Javadoc — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/StandardOpenOption.html
Path autoBackupZip = importBackupDir.resolve("auto-backup-before-import.zip");
try (OutputStream out = Files.newOutputStream(autoBackupZip, StandardOpenOption.CREATE_NEW)) {
    backupArchive.writeZip(out, Instant.now());
} catch (IOException | RuntimeException e) {
    tryDeletePartialAutoBackup(autoBackupZip);
    throw new AutoBackupBeforeImportException(auditUuid, e);
}
```

### Anti-Patterns to Avoid

- **`response.setStatus(409) + return "redirect:/admin/backup"`** — leaks back to 302. `HttpServletResponse#sendRedirect` (called by `RedirectView` in `http10Compatible=true` mode) forces 302. Use View-mode redirect with `setStatusCode` + `setHttp10Compatible(false)`.
- **`@Async` on `BackupImportPostCommitListener.onImportSucceeded`** — would make AFTER_COMMIT run on a different thread; the controller's `finally { unlock(); }` would still work (the listener doesn't touch the lock), but any future refactor that touched the lock from the listener would throw `IllegalMonitorStateException`. Keep the listener synchronous-on-publisher-thread.
- **`@ControllerAdvice` as a request-rejector** — REQUIREMENTS.md SECU-06 wording slip. `@ControllerAdvice` handles exceptions and contributes model attributes; it cannot intercept requests. Use `HandlerInterceptor.preHandle` (D-07).
- **`startsWith` matching for the whitelist** — `/admin/backup/import-execute-anything` would accidentally pass. Use `equals` (D-10).
- **Two `Instant.now()` calls in `execute(...)`** — one for the `<ts>` directory, one for the auto-backup write timestamp inside the ZIP, would diverge on second-boundaries. D-15 locks `<ts>` shared; the writeZip(`Instant.now()`) is a separate concern (the manifest's `exportDate`) and IS allowed to differ. Document the distinction in code comments.
- **`@Transactional(readOnly=true)` annotation on `BackupArchiveService.writeZip` call site** — the method is already class-level `@Transactional(readOnly=true)`. Adding another transactional annotation at the call site in `execute(...)` would be a no-op join but adds noise. Don't.
- **`Path.equals` for whitelist URI** — use `String.equals` on the raw `requestURI` (D-10). Path semantics don't apply to URIs.
- **Catching `Exception` in `AutoBackupBeforeImportException` constructor** — keep the constructor strictly delegating to the parent. The exception type carries the carrier-shape; the catch-block in `execute(...)` does the catching.
- **Banner `<div>` BELOW the existing flash divs** — D-12 explicitly anchors ABOVE. Operator must see the locked-state signal first.
- **Inline `style="..."` on the banner `<div>`** — `feedback_no_inline_styles` rule. `alert alert-warning` is sufficient (CSS already exists at `admin.css:161`).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| In-process mutex with try-acquire | Custom `AtomicBoolean.compareAndSet`-based lock | `java.util.concurrent.locks.ReentrantLock` | ReentrantLock provides `isHeldByCurrentThread()` (mandatory for idempotent unlock), `isLocked()` (mandatory for read-only banner advice), free thread-state inspection (`getQueueLength`, `hasQueuedThreads`) for future ops. AtomicBoolean would need parallel bookkeeping. |
| Request short-circuit before controller | Custom Servlet `Filter` | `HandlerInterceptor.preHandle` | Filter runs BEFORE Spring Security; would 503 unauthenticated users too. Interceptor runs AFTER security — exactly D-06's order. |
| Status-aware redirect | Manual `response.setHeader("Location", ...)` + `response.setStatus(409)` | `RedirectView` + `setStatusCode` + `setHttp10Compatible(false)` | RedirectView handles relative-vs-absolute path resolution, context-path prefix, Flash-attribute encoding into URL fragments for stateless flash-passing. Manual response-write loses the Flash plumbing. |
| File create with collision check | `Files.exists(p) → Files.createFile(p) + write` (two steps) | `Files.newOutputStream(p, CREATE_NEW)` | Two-step variant has a race. CREATE_NEW is single-call atomic at OS level. |
| Cross-controller flag injection | Setting a `request.setAttribute` in a Filter + reading in every controller | `@ControllerAdvice` + `@ModelAttribute` | Filter-attribute approach requires every controller to read; advice injects automatically. Already in use via `GlobalModelAdvice`. |
| Audit row that survives outer rollback | Manual `JdbcTemplate.update` outside the @Transactional method | `DataImportAuditService.recordResult` with `@Transactional(propagation=REQUIRES_NEW)` | REQUIRES_NEW spawns an independent transaction that commits even if the caller rolls back. Already in use via Phase 75 D-01. |
| ZIP archive write | Custom `ZipOutputStream` plumbing | `BackupArchiveService.writeZip(OutputStream, Instant)` (Phase 73) | The existing service handles 24-entity ordering, manifest-first, Jackson MixIn serialization, ZIP-Slip-on-export defense, transaction lifetime. Re-use unchanged. |
| Lock service location-by-package | Speculative `org.ctc.admin.lock` package | `org.ctc.backup.lock` (CD-02) | Cohesion with the feature it protects. Future generic admin-lock can refactor when a second use case materializes. |

**Key insight:** Phase 76 is mostly a glue layer. Every meaningful primitive already exists in the JDK or Spring; building anything custom would duplicate well-tested infrastructure. The plan correctness comes from wiring the existing primitives in the right order — not from inventing new abstractions.

## Runtime State Inventory

> Phase 76 is a glue / wiring phase, not a rename or migration. Runtime state inventory is mostly N/A. Documented anyway for completeness.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Phase 76 does not change schema (D-25) and does not migrate any existing data. The `data_import_audit` table from Phase 72 V7 is reused via the existing service. | None |
| Live service config | None — no external services. Single-JVM in-memory lock per D-03. | None |
| OS-registered state | None — Phase 76 does not register OS-level tasks, services, or daemons. The 24h retention of `data/.import-backups/<ts>/` is operator-driven `find -mtime +1 -delete` per D-22. | None |
| Secrets / env vars | None — Phase 76 does not introduce new config properties. Reuses existing `app.backup.import-backups-dir` (already at `application.yml:6`). | None |
| Build artifacts | None — no new Maven dependencies (D-24), no compiled binary artifacts. New `.java` files are compiled by the existing `mvn verify` pipeline. | None |

**Verified by:** D-23 (Phase 75 code touched ONLY at three sites), D-24 (no new Maven dependencies), D-25 (no new Flyway migration). No runtime state mutations.

## Common Pitfalls

### Pitfall 1: `response.setStatus(409) + return "redirect:..."` leaks back to 302

**What goes wrong:** Spring's `redirect:` prefix resolves through `RedirectView`. In default `http10Compatible=true` mode, `RedirectView#renderMergedOutputModel` calls `HttpServletResponse#sendRedirect(url)`, which the servlet container implements as `setStatus(302) + setHeader("Location", url) + flush`. The 302 is hard-coded — it ignores any prior `setStatus(409)` call.

**Why it happens:** Tomcat (and Jetty, Undertow) implement `sendRedirect` per Servlet spec §5.2: "sets the appropriate headers and content body to redirect the client to a different URL. The default redirect status code is `SC_FOUND` (302)." The status is set INSIDE `sendRedirect`, after any prior `setStatus`.

**How to avoid:** Return a `View` object (`RedirectView` instance) with `setStatusCode(HttpStatus.CONFLICT)` AND `setHttp10Compatible(false)`. The `false` flag flips RedirectView to a code path that sets the status and Location header directly without `sendRedirect`.

**Warning signs:** First integration test on `ImportConcurrentLockIT` sees `response.status == 302`, not `409`. CD-01 anticipates this; the fallback is the correct primary path.

**Source:** [`RedirectView` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/view/RedirectView.html), [Spring MVC reference §1.6.2.4](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/redirect.html) `[CITED]`.

### Pitfall 2: AFTER_COMMIT listener thread + finally-unlock mismatch

**What goes wrong:** If someone adds `@Async` to `BackupImportPostCommitListener.onImportSucceeded`, the listener runs on a different thread. The controller's `finally { unlock(); }` in `importExecute` still works because the controller thread is the one that called `tryLock()` — it owns the lock and can release it. But the listener's thread does NOT hold the lock, so any future code that tries to release inside the listener would throw `IllegalMonitorStateException: Current thread does not own this lock`.

**Why it happens:** `ReentrantLock` is thread-affine; `unlock()` from a non-holder thread is illegal. The `isHeldByCurrentThread()` guard in D-01 silently no-ops in that case — which is the right behavior, but it hides the bug from logs.

**How to avoid:** Keep `BackupImportPostCommitListener` synchronous-on-same-thread (default behavior; `@Async` not present). Document the contract in a class-level Javadoc comment on `BackupImportPostCommitListener` so future maintainers don't add `@Async` for "performance".

**Warning signs:** Bug surfaces as "lock never releases — admin sees `Another import is already running` indefinitely." Diagnostic: check thread name in DEBUG logs at `unlock()` site — should match the controller thread.

**Source:** [`ReentrantLock#unlock` Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html#unlock()) `[CITED]`. `[VERIFIED: BackupImportPostCommitListener.java read 2026-05-14 — no @Async present]`

### Pitfall 3: HandlerInterceptor order in Spring MVC chain

**What goes wrong:** Spring Security filters run BEFORE MVC dispatcher servlet, which means BEFORE `HandlerInterceptor.preHandle`. An anonymous user on prod/docker hitting a write endpoint during a lock sees 403 (security), not 503 (interceptor). This is correct behavior per D-06, but easy to misdiagnose if the IT runs against a profile-conditional security setup.

**Why it happens:** Servlet filter chain → dispatcher servlet → handler interceptors → controller. Spring Security registers as filters; HandlerInterceptor is an MVC-tier concern.

**How to avoid:** ITs that test the 503 path must use the `dev` or `local` profile (`OpenSecurityConfig` permits everything, CSRF disabled) OR explicitly authenticate via `MockMvc.with(user("admin"))` before posting. CONTEXT D-06 already documents this — the 503 IT must NOT run against `prod`/`docker` security profile or it will see 403 instead.

**Warning signs:** `ImportLockedPostRejectorIT` sees HTTP 403 on a non-whitelisted POST instead of 503. Fix: switch profile or pre-authenticate.

**Source:** [Spring MVC reference §1.10.4 "Interceptors"](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html), [Spring Security reference §"Filter chain"](https://docs.spring.io/spring-security/reference/servlet/architecture.html) `[CITED]`.

### Pitfall 4: `ZipFile` open during AFTER_COMMIT directory move on Windows

**What goes wrong:** Phase 75 already mitigated this via WR-05 (open ZIP exactly once with `ZipFile`, not `ZipInputStream`). The Phase 76 auto-export adds ONE more ZIP open — for the `auto-backup-before-import.zip` write. The auto-export ZIP is OPENED for write inside the `try-with-resources` and CLOSED before Step 5 (manifest re-read) executes. There is no overlap with Phase 75's staging-ZIP read. Verified safe.

**Why it happens:** Windows file-locking semantics: open file handle blocks rename/delete. Phase 75 went to lengths to ensure no overlapping open handles during the AFTER_COMMIT move-triple.

**How to avoid:** Code review checklist — the auto-export `OutputStream` MUST close (try-with-resources) before any other ZIP work begins. The auto-export ZIP path lives at `data/.import-backups/<ts>/auto-backup-before-import.zip`, the staging ZIP lives at `data/<profile>/backup-staging/upload-<uuid>.zip` — different directories, no path overlap, no concern.

**Warning signs:** Phase 76 ITs that don't close the auto-export `OutputStream` before the wipe statement see "file in use" errors on Windows CI. Linux/macOS would not show this.

**Source:** Phase 75 CONTEXT WR-05 + existing `BackupImportService.restoreAll` code `[VERIFIED: BackupImportService.java:594-612 read 2026-05-14]`.

### Pitfall 5: Reentrant lock from same controller thread

**What goes wrong:** `ReentrantLock` is *reentrant* — a single thread can `tryLock()` multiple times and must `unlock()` the same number of times. If a future refactor adds a second `tryLock` inside `execute()` (e.g., for a sub-operation) and the import flow goes single-threaded, the lock will not release on first `unlock()` — only after the second one. The banner advice's `isLocked()` would return `true` for longer than expected.

**Why it happens:** Reentrancy is intentional in `ReentrantLock` and necessary for many use cases — but Phase 76 only ever needs single-acquire-release.

**How to avoid:** Document `ImportLockService` as a single-acquire mutex in its class-level Javadoc. Add a defensive check in `tryLock()` that warns at WARN if the lock is already held by the current thread: `if (lock.isHeldByCurrentThread()) { log.warn("Re-entrant tryLock from same thread — refactor introduced bug"); return false; }` — this turns reentrancy from a silent feature into a loud signal.

**Warning signs:** Tests pass but production import never unlocks. Look for nested `tryLock` calls in the call chain.

**Source:** [`ReentrantLock` Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html) §"Acquisition counts" `[CITED]`.

### Pitfall 6: Auto-export ZIP write happens INSIDE outer @Transactional — what gets rolled back?

**What goes wrong:** The auto-export read happens inside the outer `BackupImportService.execute` `@Transactional(REQUIRED, READ_COMMITTED)` boundary. If `Files.newOutputStream` or `writeZip` throws AFTER some `SELECT` queries have run, the outer `@Transactional` rolls back. But `Files.newOutputStream` + `writeZip` ARE DB-reads — they don't mutate. The rollback is therefore a no-op for the DB, but the partially-written ZIP file on disk is NOT rolled back (file-system mutations don't enroll in JPA transactions).

**Why it happens:** JPA transactions only cover DB operations. The ZIP write is a side-effect outside the transaction's scope.

**How to avoid:** D-19 mandates `tryDeletePartialAutoBackup(Path target)` in the catch block — best-effort `Files.deleteIfExists(target)` inside try-catch, never throws. This is the file-system-side cleanup that the DB transaction can't provide.

**Warning signs:** After a failed auto-export, `data/.import-backups/<ts>/auto-backup-before-import.zip` exists but is truncated (e.g., 8 KB partial file). Operator sees a junk file in the recovery directory.

**Source:** D-19 + JDK `Files.deleteIfExists` Javadoc. `[CITED]`

### Pitfall 7: `Files.deleteIfExists` AFTER the OutputStream try-with-resources

**What goes wrong:** If the try-with-resources block fails to close the OutputStream (e.g., due to an `IOException` during `close()`), and then `tryDeletePartialAutoBackup` runs in the catch block, Windows may refuse to delete the file because the JVM still holds an OS-level handle. On Linux/macOS this is silent (delete succeeds even with open handle, file is unlinked but data is freed when handle closes), but on Windows it fails with `AccessDeniedException`.

**Why it happens:** Windows file locking — open handles block delete.

**How to avoid:** `tryDeletePartialAutoBackup` MUST NOT throw on Windows failures (D-19). Log at WARN, swallow the exception. The 24h retention runbook (D-22) covers operator-side manual cleanup if the auto-delete fails on Windows.

**Warning signs:** On Windows CI, after a deliberately-failing auto-export IT, the partial ZIP file persists. Test must tolerate this — D-19 already mandates best-effort.

**Source:** JDK `Files.deleteIfExists` Javadoc, [Windows file-locking semantics](https://docs.microsoft.com/en-us/windows/win32/fileio/file-management) `[CITED]`.

### Pitfall 8: ZipFile re-open in `restoreAll` after auto-export

**What goes wrong:** Phase 75 opens the staging ZIP exactly once (WR-05). Phase 76 auto-export uses a SEPARATE `BackupArchiveService.writeZip(OutputStream, Instant)` call that produces a NEW ZIP at a NEW path. There's no overlap with the staging ZIP — the auto-export reads the live DB (not the staging ZIP), writes to a new file. No conflict.

**Why this is NOT a pitfall:** The auto-export and the restore-from-staging operate on different files. Confirmed safe by inspecting `BackupArchiveService.writeZip` (reads from `BackupExportService.fetchAllForBackup(...)` — pulls from JPA repos, not from the staging ZIP).

**Action for planner:** Document in plan comments that the auto-export does NOT touch the staging ZIP — the operator could conceivably delete the staging ZIP at the same wall-clock instant and the auto-export would still complete. Defense-in-depth note, not a real concern.

## Code Examples

Verified patterns from project source + JDK/Spring Javadocs:

### Lock service with idempotent unlock
```java
// Source: ReentrantLock Javadoc + D-01 verbatim
// Path: src/main/java/org/ctc/backup/lock/ImportLockService.java (NEW)
package org.ctc.backup.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@Scope("singleton")  // explicit per SECU-05; redundant but documents intent
public class ImportLockService {

    private final ReentrantLock lock = new ReentrantLock();

    /** Non-blocking acquire; returns false immediately if already held. */
    public boolean tryLock() {
        boolean acquired = lock.tryLock();
        if (acquired) {
            log.info("Import lock acquired by thread={}", Thread.currentThread().getName());
        }
        return acquired;
    }

    /** Idempotent release; silent no-op if current thread does not hold the lock. */
    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.info("Import lock released by thread={}", Thread.currentThread().getName());
        }
    }

    /** Read-only lock-state inspection. Used by banner advice + 503 interceptor. */
    public boolean isLocked() {
        return lock.isLocked();
    }
}
```

### Controller wrapper with View-mode 409 redirect
```java
// Source: D-04 + D-05 + CD-01 verified-working primitive
// Path: src/main/java/org/ctc/backup/BackupController.java (EXTENDED)
@PostMapping("/import-execute")
public ModelAndView importExecute(
        @Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form,
        BindingResult bindingResult, Model model, RedirectAttributes ra) {

    // Ring 1 — concurrent-rejection 409
    if (!importLockService.tryLock()) {
        ra.addFlashAttribute("errorMessage",
            "Another import is already running — please wait.");
        RedirectView rv = new RedirectView("/admin/backup");
        rv.setStatusCode(HttpStatus.CONFLICT);
        rv.setHttp10Compatible(false);  // REQUIRED — otherwise sendRedirect overwrites 409 → 302
        return new ModelAndView(rv);
    }
    try {
        // Existing binding-result handling: re-render confirm page with preview
        if (bindingResult.hasErrors()) {
            // ... existing reparse + return "admin/backup-confirm" wrapped as ModelAndView
        }
        try {
            backupImportService.reparse(form.getStagingId());
            BackupImportResult result = backupImportService.execute(form.getStagingId());
            ra.addFlashAttribute("successMessage",
                String.format("Import completed. %d rows restored across %d tables.",
                    result.restoredTotal(), result.entityCount()));
        } catch (AutoBackupBeforeImportException ex) {  // FIRST in chain (subclass of BackupImportException)
            String auditIdText = ex.isAuditWritten()
                ? ex.getAuditUuid().toString()
                : "unavailable (audit write failed; see logs for " + ex.getAuditUuid() + ")";
            ra.addFlashAttribute("errorMessage", String.format(
                "Import aborted — pre-import auto-backup failed. No database changes. Audit-id: %s.",
                auditIdText));
        } catch (BackupArchiveException ex) {
            ra.addFlashAttribute("errorMessage", mapReason(ex));
        } catch (IOException ex) {
            log.error("IO error on import-execute (execute path): stagingId={}",
                form.getStagingId(), ex);
            ra.addFlashAttribute("errorMessage",
                "Backup archive failed safety checks (size or path) and was rejected.");
        } catch (UploadsRestoreException ex) {
            // existing D-15 #3 defensive catch (Phase 75)
        } catch (BackupImportException ex) {  // catches anything else, INCLUDES AutoBackup if not caught above
            // existing D-15 #2 handler
        }
    } finally {
        importLockService.unlock();
    }
    return new ModelAndView("redirect:/admin/backup");
}
```

### Service auto-export block
```java
// Source: D-14 + D-15 + D-16 verbatim
// Path: src/main/java/org/ctc/backup/service/BackupImportService.java (EXTENDED, around line 425-440)
public BackupImportResult execute(UUID stagingId) {
    log.info("Backup import execute started: stagingId={}", stagingId);
    UUID auditUuid = UUID.randomUUID();
    Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
    // ... existing meta-read

    // D-15 — <ts> MOVED to top so auto-backup + uploads-old share it
    String ts = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-");
    Path importBackupDir = importBackupsDir.resolve(ts);
    Path uploadsNewDir = importBackupDir.resolve("uploads-new");
    Path autoBackupZip = importBackupDir.resolve("auto-backup-before-import.zip");

    // D-14 — directory create + auto-export BEFORE wipe
    try {
        Files.createDirectories(importBackupDir);
    } catch (IOException e) {
        throw new AutoBackupBeforeImportException(auditUuid,
            tryRecordFailure(auditUuid, 0, sourceFilename, Map.of(), Map.of()), e);
    }

    try (OutputStream out = Files.newOutputStream(autoBackupZip, StandardOpenOption.CREATE_NEW)) {
        backupArchive.writeZip(out, Instant.now());
        log.info("Auto-backup-before-import written: path={}", autoBackupZip);
    } catch (IOException | RuntimeException e) {
        log.error("Auto-backup-before-import FAILED: path={}", autoBackupZip, e);
        tryDeletePartialAutoBackup(autoBackupZip);
        boolean auditWritten = tryRecordFailure(auditUuid, 0, sourceFilename,
            Map.of(), Map.of());  // D-18 empty maps
        throw new AutoBackupBeforeImportException(auditUuid, auditWritten, e);
    }

    // ... existing wipe + restore + event-publish stays byte-identical
}

private static void tryDeletePartialAutoBackup(Path target) {
    try {
        boolean deleted = Files.deleteIfExists(target);
        if (deleted) log.info("Deleted partial auto-backup: {}", target);
    } catch (IOException io) {
        log.warn("Failed to delete partial auto-backup: {} — operator must clean manually", target, io);
    }
}
```

### Auto-backup exception class
```java
// Source: D-17 verbatim
// Path: src/main/java/org/ctc/backup/exception/AutoBackupBeforeImportException.java (NEW)
package org.ctc.backup.exception;

import java.util.UUID;

/**
 * Phase 76 / D-17 — pre-import auto-backup failure carrier.
 *
 * <p>Extends {@link BackupImportException} so the controller's existing catch-chain
 * inheritance still works, but is caught FIRST (subclass-before-superclass) so the
 * D-17 flash wording distinguishes "auto-backup failed, NO database changes" from
 * the generic "Import failed and was rolled back."
 */
public class AutoBackupBeforeImportException extends BackupImportException {

    public AutoBackupBeforeImportException(UUID auditUuid, boolean auditWritten, Throwable cause) {
        super(auditUuid, auditWritten, cause);
    }
}
```

### Banner div in admin/layout.html
```html
<!-- Source: D-12 verbatim
     Path: src/main/resources/templates/admin/layout.html (EXTENDED, BEFORE line 82 successMessage div) -->
<div th:if="${importInProgress}" class="alert alert-warning" role="status">
    Backup import in progress — write access is temporarily locked.
</div>
<div th:if="${successMessage}" class="alert alert-success" th:text="${successMessage}"></div>
<div th:if="${errorMessage}" class="alert alert-error" th:text="${errorMessage}"></div>
```

### Test injector mirroring Phase 75 pattern
```java
// Source: Phase 75 FailAtTableInjector.java pattern + D-20
// Path: src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java (NEW)
package org.ctc.backup.it.support;

import org.ctc.backup.restore.RestoreFailureInjector;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BlockingRestoreFailureInjector implements RestoreFailureInjector {

    private final String targetTable;
    private final int targetRow;
    private final CountDownLatch acquireSignal = new CountDownLatch(1);  // signals thread-A has acquired lock
    private final CountDownLatch releaseSignal = new CountDownLatch(1);  // thread-B counts down to release thread-A

    public BlockingRestoreFailureInjector(String targetTable, int targetRow) {
        this.targetTable = targetTable;
        this.targetRow = targetRow;
    }

    public CountDownLatch getAcquireSignal() { return acquireSignal; }
    public CountDownLatch getReleaseSignal() { return releaseSignal; }

    @Override
    public void maybeFailAt(String tableName, int rowIndex) {
        if (targetTable.equals(tableName) && rowIndex == targetRow) {
            acquireSignal.countDown();
            try {
                if (!releaseSignal.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test timeout — releaseSignal never fired");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
    }

    @TestConfiguration
    public static class Config {
        @Bean(name = "noopRestoreFailureInjector")  // MIRROR: same bean name as production NoopRestoreFailureInjector (FailAtTableInjector pattern)
        @Primary
        public BlockingRestoreFailureInjector blockingInjector() {
            return new BlockingRestoreFailureInjector("race_results", 500);
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@ControllerAdvice`-as-filter (REQUIREMENTS.md SECU-06 wording) | `HandlerInterceptor.preHandle` (D-07) | Phase 76 plan | Same user-visible behavior (HTTP 503 on non-whitelisted POST during lock); correct Spring MVC primitive. |
| `response.setStatus(409) + return "redirect:..."` (CD-01 default) | View-mode redirect with `setStatusCode + setHttp10Compatible(false)` (CD-01 fallback) | Phase 76 plan (raised to PRIMARY by this research) | Status preserved on the wire; matches SECU-05 goal "HTTP 409 + Flash" exactly. |
| Two-step `Files.exists + Files.createFile + write` for the auto-backup ZIP | Single-call `Files.newOutputStream(path, CREATE_NEW)` (D-16) | Phase 76 plan | OS-level atomic; protects against same-second timestamp collision (D-16 defensive intent). |
| Async / background auto-backup | Synchronous, blocking (D-14 + SECU-07) | Phase 76 design lock | Simpler reasoning; failure-mode is sync exception, no progress-polling UX needed. |

**Deprecated/outdated:**
- N/A — Phase 76 is purely additive.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `BackupImportPostCommitListener.onImportSucceeded` is synchronous-on-publisher-thread by default | Pitfall §2, D-06 | If a future refactor adds `@Async`, the lock semantics in `finally { unlock(); }` still work (different concern), but documentation drift. **VERIFIED 2026-05-14:** no `@Async` annotation present, default `@TransactionalEventListener` is sync. `[VERIFIED: BackupImportPostCommitListener.java read]` |
| A2 | `Files.newOutputStream(path, CREATE_NEW)` is atomic on all supported FS (POSIX, NTFS, macOS APFS) | Pattern 5, Pitfall 6 | Same-second collision races. POSIX `O_CREAT|O_EXCL` is RFC-spec atomic; NTFS `CREATE_NEW` is OS-call atomic. APFS atomic-create is verified per [Apple Foundation docs](https://developer.apple.com/documentation/foundation/filemanager) `[CITED]`. Low risk. |
| A3 | The 503 HandlerInterceptor must register `/admin/**` as path pattern (NOT `/**`) | Pattern 2, D-07 | Wider pattern would unnecessarily intercept site GETs (no harm — GETs pass through anyway). Narrower pattern keeps scope explicit. `[VERIFIED: WebConfig.java existing pattern read]` |
| A4 | `BlockingRestoreFailureInjector` must use the SAME Spring bean name `noopRestoreFailureInjector` as the production bean, with `spring.main.allow-bean-definition-overriding=true` on the IT | Pattern 6, Validation Architecture, code example | If the test uses a different bean name, BOTH beans coexist with `@Primary` → `NoUniqueBeanDefinitionException: more than one 'primary' bean found`. `[VERIFIED: FailAtTableInjector.java read 2026-05-14 — established pattern]` |
| A5 | `BackupController.importExecute` currently has 4 catch clauses (BackupArchiveException, IOException, UploadsRestoreException, BackupImportException) — adding AutoBackupBeforeImportException FIRST (before BackupImportException) is required for correct exception matching | Pitfall §1 in catch-chain order, code example | If placed last, the `BackupImportException` superclass clause catches it first → wrong flash wording. `[VERIFIED: BackupController.java lines 247-285 read]` |
| A6 | Spring Boot 4.0.6 is in use; `RedirectView#setHttp10Compatible(false)` enables explicit-status redirect | Pitfall §1, Pattern 3 | Behavior verified against Spring 6.x docs; Spring Boot 4.0.6 uses Spring Framework 7.x. `RedirectView` API is stable since Spring 3.x. `[CITED: RedirectView Javadoc]` |
| A7 | The `@ControllerAdvice` `ImportLockBannerAdvice` should NOT use `basePackages` filter — applies globally including site | D-11, D-13, Pattern 4 | Site templates that don't reference `${importInProgress}` evaluate it as null/false harmlessly. Verified by Thymeleaf's null-coalescing on `th:if`. `[CITED: Thymeleaf docs §"Conditional evaluation"]` |
| A8 | `data/.import-backups/<ts>/` parent directory exists OR `Files.createDirectories(importBackupDir)` succeeds before the auto-export OutputStream open | Pitfall 6, code example | `Files.createDirectories` is idempotent; throws only on permission/disk failure. The wrapping `try/catch` for the auto-export covers both `createDirectories` and `newOutputStream` failures with the same exception path. `[CITED: Files.createDirectories Javadoc]` |
| A9 | `[ASSUMED]` Spring's MVC dispatcher invokes `HandlerInterceptor.preHandle` AFTER all `Filter`s in the FilterChain — including Spring Security filters | Pattern 2, Pitfall §3 | Verified at the architectural level via Spring docs but not by reading FilterChainProxy code. Low risk — this is the documented and depended-upon ordering since Spring 3.x. `[CITED: Spring MVC reference §1.10.4]` |

**Risk summary:** All assumptions are LOW risk; A4 and A5 are the highest-impact (test setup + exception chain order) and both are VERIFIED against existing code.

## Open Questions

1. **Should the 503-HTML body include a CSRF token for the `<meta http-equiv="refresh">`?**
   - What we know: A simple refresh tag triggers a GET, not a POST. CSRF tokens are only required for state-changing requests.
   - What's unclear: Should the HTML body include any links/forms?
   - Recommendation: NO. Per CD-03, keep the body minimal — just a heading and `<meta refresh>`. No forms. Operator sees the banner on the next refresh.

2. **What is the right test profile for `ImportLockedPostRejectorIT` — `dev` (no security) or a custom `it` profile?**
   - What we know: Phase 75 ITs use `@ActiveProfiles("dev")`. The 503 interceptor runs after Spring Security; on `dev` profile, `OpenSecurityConfig` permits everything (so the test sees 503, not 403).
   - What's unclear: Whether to add a separate IT that runs on `prod` profile (with `@WithMockUser`) to verify the "authenticated admin sees 503" path explicitly.
   - Recommendation: Single IT on `dev` profile sufficient for D-21. The "authenticated admin" path is implicitly covered because the interceptor is profile-independent. Add a JUnit nested class with `@WithMockUser` if extra defense desired — planner's call.

3. **Should `ImportLockBannerAdvice` apply to `@RestController` beans too?**
   - What we know: `@ControllerAdvice` by default applies to BOTH `@Controller` and `@RestController`. The model attribute is harmless on REST endpoints (returned as part of model, which REST controllers ignore).
   - What's unclear: Whether to scope with `annotations = Controller.class`.
   - Recommendation: NO scoping — default global applies. Phase 76 doesn't add REST endpoints; existing REST endpoints (if any) just ignore the model attribute. Symmetry with `GlobalModelAdvice`.

4. **Is there a risk that the auto-backup ZIP write inside the outer @Transactional times out / breaks JPA flush state?**
   - What we know: `BackupArchiveService.writeZip` is `@Transactional(readOnly=true)`; joining as REQUIRED means same tx context. Reads only. No flush.
   - What's unclear: Behavior under MariaDB read-committed when reading after the tx has had some time to "age" (auto-backup might take 30+ seconds on large datasets).
   - Recommendation: Add a `@Transactional`-level timeout? Not in v1.10 scope. Document in runbook (D-22) that auto-backup time should be observed in production. Defer hardening.

5. **Should the `ImportLockService` log lock-state transitions at INFO or DEBUG?**
   - What we know: Phase 75 logs auto-backup, wipe, restore events at INFO.
   - What's unclear: Whether lock-acquire/release adds noise at INFO.
   - Recommendation: INFO. Lock-state transitions are rare (one per admin import) and high-value diagnostic info. Matches Phase 75 convention.

6. **What's the contract on `data/.import-backups/<ts>/uploads-new/` cleanup in the auto-backup failure path?**
   - What we know: D-19 covers partial-ZIP cleanup. The directory itself might be empty after `Files.createDirectories(importBackupDir) + Files.createDirectories(uploadsNewDir)` if auto-export fails BEFORE the wipe.
   - What's unclear: Whether the empty directory should be cleaned up too.
   - Recommendation: Leave the empty directory in place. 24h-retention runbook applies to the whole `data/.import-backups/<ts>/` subdirectory. An empty dir is harmless. Document in plan.

## Environment Availability

> Phase 76 has no new external dependencies — all primitives are JDK 25 or Spring Boot 4.0.6 stack. This section is included for completeness but most rows are "already available — verified by Phase 71/72/73/74/75".

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 (Eclipse Temurin) | ReentrantLock, Files.newOutputStream | ✓ | 25 | — |
| Spring Boot 4.0.6 | HandlerInterceptor, @ControllerAdvice, RedirectView | ✓ | 4.0.6 (pom.xml:7-8) `[VERIFIED]` | — |
| Spring Framework 7.x (managed by Spring Boot 4.0.6) | Spring MVC interceptor chain, @TransactionalEventListener | ✓ | managed | — |
| Thymeleaf 3.1.5 | th:if expression evaluation on admin/layout.html | ✓ | 3.1.5 (pom.xml:29) `[VERIFIED]` | — |
| Maven (mvnw) | ./mvnw verify -Pe2e for E2E gate | ✓ | shipped in repo | — |
| H2 in-memory (dev/test) | All ITs run on H2 by default (Phase 75 baseline) | ✓ | managed | — |
| MariaDB | Optional smoke ITs (Phase 75 BackupImportMariaDbSmokeIT pattern) | ✓ (local docker-compose) | 11.x | H2 sufficient for Phase 76 ITs |
| JaCoCo | Coverage gate ≥ 82% | ✓ | configured in pom.xml | — |
| Playwright Chromium | E2E verify -Pe2e gate (FINAL phase verification) | ✓ (existing E2E tests run it) | 1.59.0 | — |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None.

## Validation Architecture

> Per `.planning/config.json` `workflow.nyquist_validation: true`. This section drives the orchestrator's Step 5.5 Nyquist gate and the planner's VALIDATION.md.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test (Surefire for unit/integration, Failsafe for IT) |
| Config file | `pom.xml` Surefire/Failsafe plugin sections; `application-dev.yml` for IT profile |
| Quick run command | `./mvnw test -Dtest=ImportLockServiceTest` for the unit; `./mvnw verify -Dit.test=ImportConcurrentLockIT` for the IT |
| Full suite command | `./mvnw verify` (Surefire + Failsafe); `./mvnw verify -Pe2e` for the final E2E gate (`feedback_e2e_verification`) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| SECU-05 | `tryLock` non-blocking semantics; `unlock` idempotency; `isLocked` read | unit | `./mvnw test -Dtest=ImportLockServiceTest` | ❌ Wave 0 (NEW) |
| SECU-05 | 2-thread race — thread-B sees 409 + Flash while thread-A holds lock | IT (Failsafe) | `./mvnw verify -Dit.test=ImportConcurrentLockIT` | ❌ Wave 0 (NEW, D-20) |
| SECU-06 | Non-whitelisted POST during lock → 503; whitelisted POST during lock → not blocked (409 from lock service) | IT (Failsafe) | `./mvnw verify -Dit.test=ImportLockedPostRejectorIT` | ❌ Wave 0 (NEW, D-21) |
| SECU-06 | Banner text visible on GET /admin/seasons during held lock | IT (Failsafe) | `./mvnw verify -Dit.test=ImportLockBannerAdviceIT` | ❌ Wave 0 (NEW, D-21) |
| SECU-07 | Happy path — import succeeds → `data/.import-backups/<ts>/auto-backup-before-import.zip` exists with same `<ts>` as uploads-old sibling | IT (Failsafe) | `./mvnw verify -Dit.test=AutoBackupBeforeImportPathIT` | ❌ Wave 0 (NEW) |
| SECU-07 | Auto-export I/O failure → no wipe occurred (row counts unchanged), audit row exists with `success=false` + empty count maps (D-18), Flash matches D-17, partial ZIP cleaned up | IT (Failsafe) | `./mvnw verify -Dit.test=AutoBackupBeforeImportFailureIT` | ❌ Wave 0 (NEW) |
| All | Coverage ≥ 82% (CLAUDE.md constraint) | gate | `./mvnw verify` + open `target/site/jacoco/index.html` | ✓ existing infra |
| All (final UAT) | Full E2E suite BUILD SUCCESS (`feedback_e2e_verification`) | gate | `./mvnw verify -Pe2e` | ✓ existing infra |

### Failure Modes per Ring

**Ring 1 — Mutex (SECU-05):**
- *Mode A:* `tryLock` returns true twice in a row from same thread (reentrancy bug) → Test: `ImportLockServiceTest::givenLockHeld_whenTryLockAgainSameThread_thenTrueReturnedAndUnlockReleasesOnce` flags this as documented reentrant behavior; production-side single-acquire is enforced by D-04 wrapper pattern (only one site calls tryLock).
- *Mode B:* `unlock` called from thread that does not hold the lock → silent no-op via `isHeldByCurrentThread()` guard. Test: `ImportLockServiceTest::givenLockHeldByOtherThread_whenUnlockFromAnotherThread_thenNoOp`.
- *Mode C:* Two concurrent imports race — thread-B's `tryLock()` returns false. Test: `ImportConcurrentLockIT` via `BlockingRestoreFailureInjector`.
- *Mode D:* Lock leaks (process exits with lock held) — restart frees in-memory state by definition. Test: covered by JVM lifecycle, not by IT.

**Ring 2 — 503-Rejector + Banner (SECU-06):**
- *Mode A:* HandlerInterceptor missing from registry → no rejection. Test: registration verified implicitly by `ImportLockedPostRejectorIT` PASS.
- *Mode B:* Whitelist accidentally widened via `startsWith` → `import-execute-anything` slips through. Test: `ImportLockedPostRejectorIT::givenLockHeld_whenPostToWhitelistedExtraPath_then503` (defensive — `equals` per D-10).
- *Mode C:* Banner not rendering on admin page during lock. Test: `ImportLockBannerAdviceIT::givenLockHeld_whenGetAdminSeasons_thenBannerTextInBody`.
- *Mode D:* Banner accidentally renders on site templates. Test: `ImportLockBannerAdviceIT::givenLockHeld_whenGetSiteIndex_thenBannerTextAbsent` (D-13 verification).
- *Mode E:* `@ControllerAdvice` doesn't fire for some controllers (e.g., `@RestController`). Test: covered by `ImportLockBannerAdviceIT` on multiple admin GET paths.

**Ring 3 — Auto-backup (SECU-07):**
- *Mode A:* Auto-export I/O failure → no wipe, audit row with empty counts. Test: `AutoBackupBeforeImportFailureIT` via permission-revoked directory or `FileSystemProvider` mock.
- *Mode B:* Auto-export ZIP partially written → `tryDeletePartialAutoBackup` cleans up. Test: `AutoBackupBeforeImportFailureIT::givenAutoExportFailsMidStream_thenPartialZipCleanedUp`.
- *Mode C:* `CREATE_NEW` collision (same-second timestamp re-import) → `FileAlreadyExistsException` propagates through catch block → audit row + flash. Test: `AutoBackupBeforeImportFailureIT::givenPreExistingFileAtAutoBackupPath_thenImportAbortedWithoutMutation`.
- *Mode D:* Audit-write itself fails during auto-export failure (double-failure path, WR-03) → flash carries `"unavailable (...)"` text. Test: `AutoBackupBeforeImportFailureIT::givenAuditWriteAlsoFails_thenFlashCarriesUnavailable` (may merge with Phase 75 WR-03 coverage; planner judgment).

**Cross-ring:**
- *Mode E:* Lock released even on `AutoBackupBeforeImportException` path → next import attempt succeeds. Test: `ImportConcurrentLockIT::givenAutoExportFailed_whenSecondImportAttempted_thenLockReleasedAndSecondSucceeds` (defensive — verifies `finally { unlock(); }` covers ALL throw paths in the controller).

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=ImportLockServiceTest` (~3 s, unit) — runs on every task save in worktree.
- **Per wave merge:** `./mvnw verify -Dit.test=ImportConcurrentLockIT,ImportLockedPostRejectorIT,ImportLockBannerAdviceIT,AutoBackupBeforeImportPathIT,AutoBackupBeforeImportFailureIT` (~2 min, Failsafe ITs).
- **Phase gate:** `./mvnw verify -Pe2e` (full suite incl. Playwright; the `feedback_e2e_verification` rule).

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java` — unit test for `tryLock` / `unlock` / `isLocked` (covers SECU-05 unit-level).
- [ ] `src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java` — test-only injector with `@TestConfiguration.Config` inner class. Mirrors `FailAtTableInjector.Config` pattern (CD-05 default location).
- [ ] `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` — 2-thread Failsafe IT (SECU-05 IT, D-20).
- [ ] `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` — 503-on-non-whitelisted-POST IT (SECU-06 IT, D-21).
- [ ] `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` — banner-on-GET IT (SECU-06 IT, D-21).
- [ ] `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java` — happy-path IT (SECU-07).
- [ ] `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` — failure-path IT (SECU-07).
- [ ] Framework install: none — JUnit 5 + Spring Boot Test already configured.

**Test-suite organization opinion:** Keep ITs as separate classes per Ring (5 IT classes total). Reasons:
- Each IT has distinct setup (slow-import driver vs simple-import driver vs no-import driver).
- Spring context caching kicks in: 5 ITs with identical `@ActiveProfiles("dev") + @Import(BlockingRestoreFailureInjector.Config.class)` share the same context (Spring 6.x test-context cache) — runtime cost is one cold start + 4 warm reuses, not 5 cold starts.
- A single parameterized IT would conflate failure modes — when one assertion fails, the diagnosis is harder.
- Mirrors Phase 75's split pattern (`BackupImportRollbackIT`, `BackupImportExecuteIT`, `BackupImportPostCommitIT`, `BackupImportSchemaMismatchIT`) — same operator mental model.

## Security Domain

> CTC Manager: `security_enforcement` is implicitly enabled (no explicit `false` in `.planning/config.json`). Phase 76 is itself a defense-in-depth phase, so the security domain is the phase's core concern.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (transitively) | Spring Security `@Profile("prod","docker")` `SecurityConfig` — unchanged by Phase 76. D-06 explicitly confirms 503-interceptor runs AFTER security; unauthenticated admin sees 403 first, not 503. |
| V3 Session Management | no | Phase 76 doesn't touch session state. Lock state is in-memory singleton, not session-scoped. |
| V4 Access Control | yes | The 503 HandlerInterceptor enforces write-access lockout during an active import. Whitelist is `equals`-match on exactly one URL (D-10) — no path-traversal slip. |
| V5 Input Validation | partial | Reuses Phase 74's `BackupImportConfirmForm` `@Valid` + `BindingResult` plumbing. No new DTOs. |
| V6 Cryptography | no | No new cryptographic primitives. Reuses Phase 74's SHA-256-on-ZIP-bytes (via existing `BackupArchiveService` hardening). |
| V7 Error Handling | yes | New `AutoBackupBeforeImportException` carries `auditUuid` so the operator can drill into `data_import_audit` via SQL. No stack-trace leak to the user (flash carries only audit UUID). |
| V8 Data Protection | yes (transitively) | Auto-backup-before-import is itself a data-protection control — wraps the wipe operation with a recovery net. 24h retention is operator-driven. |
| V13 API Errors | yes | HTTP 503 + minimal HTML body on locked POSTs is a defensive response. HTTP 409 + Flash on concurrent acquisition is operator-facing. Neither leaks implementation detail. |

### Known Threat Patterns for Spring MVC + JDK File I/O

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Concurrent destructive op race condition | Tampering | Singleton `ReentrantLock` with `tryLock()` (SECU-05); HandlerInterceptor blocks parallel writes (SECU-06) |
| Partial-file recovery confusion | Tampering / Repudiation | `Files.newOutputStream(... CREATE_NEW)` + `tryDeletePartialAutoBackup` (D-16, D-19); 24h retention in runbook (D-22) |
| Audit-row loss across rollback | Repudiation | `DataImportAuditService.recordResult` `@Transactional(REQUIRES_NEW)` — survives outer rollback (Phase 75 D-01) |
| Status-code leak on redirect | Information Disclosure | View-mode redirect with explicit `setStatusCode` + `setHttp10Compatible(false)` ensures 409 surfaces correctly |
| URL-prefix bypass on whitelist | Elevation of Privilege | `equals` match on exact requestURI (D-10), not `startsWith` |
| Lock leak on unhandled exception in controller body | Denial of Service | `try { ... } finally { unlock(); }` wrapper (D-04) ensures release on every exit path including unchecked exceptions and `Throwable` |
| Banner injection / XSS via flash message | Tampering | Reused existing `<div th:text="${errorMessage}">` plumbing — Thymeleaf default escapes HTML. Banner text is a static literal (D-12). No user input on the banner path. |
| Path traversal in auto-backup ZIP filename | Tampering | `data/.import-backups/<ts>/auto-backup-before-import.zip` is computed from `Instant.now()` + literal filename — no user input. `<ts>` is sanitized (colon → dash) per D-15. |

## Sources

### Primary (HIGH confidence)

- **JDK 25 Javadoc — `java.util.concurrent.locks.ReentrantLock`** — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html — tryLock semantics, isHeldByCurrentThread, isLocked
- **JDK 25 Javadoc — `java.nio.file.Files#newOutputStream`** — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html#newOutputStream — CREATE_NEW atomic semantics
- **JDK 25 Javadoc — `java.nio.file.StandardOpenOption`** — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/StandardOpenOption.html — CREATE_NEW behavior
- **Spring Framework reference §1.10.4 "Interceptors"** — https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html — HandlerInterceptor registration + preHandle short-circuit
- **Spring Framework reference §1.5.3 "Controller Advice"** — https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/controller-advice.html — @ControllerAdvice scoping
- **Spring `RedirectView` Javadoc** — https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/view/RedirectView.html — setStatusCode + setHttp10Compatible behavior
- **Spring `@TransactionalEventListener` Javadoc** — https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html — AFTER_COMMIT phase semantics, default sync execution
- **Existing repository code (read 2026-05-14):**
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/backup/BackupController.java` — current catch-chain shape
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/backup/service/BackupImportService.java` — execute method, `<ts>` computation site, REQUIRES_NEW audit pattern
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/backup/service/BackupArchiveService.java` — writeZip(OutputStream, Instant) signature
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java` — AFTER_COMMIT listener (no @Async confirmed)
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/WebConfig.java` — existing WebMvcConfigurer
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java` — @ControllerAdvice + @ModelAttribute pattern
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/SecurityConfig.java` + `OpenSecurityConfig.java` — security filter ordering vs HandlerInterceptor
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/backup/audit/DataImportAuditService.java` — REQUIRES_NEW recordResult signature
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/backup/exception/BackupImportException.java` — auditUuid + auditWritten carrier shape
  - `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java` — production injector bean name
  - `/Users/jegr/Documents/github/ctc-manager/src/test/java/org/ctc/backup/service/FailAtTableInjector.java` — established test-injector bean-override pattern (`@Bean(name = "noopRestoreFailureInjector") @Primary` + `spring.main.allow-bean-definition-overriding=true`)
  - `/Users/jegr/Documents/github/ctc-manager/src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` — example Failsafe IT structure
  - `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/layout.html` — flash-div insertion site (line 82)
  - `/Users/jegr/Documents/github/ctc-manager/src/main/resources/static/admin/css/admin.css:161` — `alert-warning` CSS class
  - `/Users/jegr/Documents/github/ctc-manager/pom.xml` — Spring Boot 4.0.6 + Thymeleaf 3.1.5 versions

### Secondary (MEDIUM confidence)

- **Spring Security reference — Filter chain** — https://docs.spring.io/spring-security/reference/servlet/architecture.html — verifies filter-before-interceptor ordering
- **Thymeleaf documentation — `th:if` conditional evaluation** — https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html — null-coalescing on conditional expressions

### Tertiary (LOW confidence)

- *(none — all critical claims verified against primary sources)*

## Project Constraints (from CLAUDE.md)

| Constraint | How Phase 76 Honors It |
|------------|------------------------|
| Test Coverage ≥ 82% | Phase 76 adds ~5 classes (4 source + 1 exception subclass) + 1 controller change + 5 IT classes + 1 unit test. Adequate test coverage on new classes ensures the gate holds. Planner monitors JaCoCo after each wave. |
| Flyway — no V1 changes, only V2+ migrations | D-25 — zero migrations. Phase 76 does not touch schema. |
| Profiles — auth only for prod/docker | Unchanged. D-06 confirms 503 fires AFTER 403 for unauthenticated on prod/docker. |
| OSIV remains enabled | Phase 76 does not add new template lazy-fetch sites; banner div uses static text. |
| No breaking changes to URLs / endpoints | Phase 76 extends existing `/admin/backup/import-execute` behavior — no URL changes. |
| Playwright remains compile-scope | Unchanged. |
| German communication / English UI | This RESEARCH.md communicates in English (documentation rule); the response text to the user is in German per CLAUDE.md language rule. Banner text and 503-HTML body are English (D-12, `feedback_ui_language`). |
| Thin controllers | `BackupController.importExecute` wrapper is ~10 LOC: tryLock + try/catch chain + finally unlock. No business logic. |
| DTOs instead of entities in controllers | No new DTOs in Phase 76. `BackupImportConfirmForm` reused unchanged. |
| No fallback calculations | Auto-backup is a deliberate first-class operation, not a "fallback". |
| Keep Thymeleaf templates lean | Banner is a single `th:if` div with literal text — no complex SpEL. |
| No inline styles on buttons | `alert alert-warning` CSS classes only. No `style=` attribute. |
| Isolate test data completely | Phase 76 ITs reuse Phase 75's `TestDataService.seed()` dev fixture (Saison 2023 etc. — not test-isolated, by design of Phase 75 — explicit exception). |
| RaceLineup Source of Truth | N/A — Phase 76 doesn't touch lineup data. |
| Do Not Modify Flyway Migrations | D-25 — zero migrations. |
| `feedback_ui_language` (memory) | English banner text + English 503 HTML body, locked by D-04 / D-12. |
| `feedback_no_inline_styles` (memory) | CSS class `alert-warning` reused. No inline styles. |
| `feedback_e2e_verification` (memory) | Final verification `./mvnw verify -Pe2e` BUILD SUCCESS is the phase gate. |
| `feedback_test_call_optimization` (memory) | ONE final `./mvnw verify` (with Playwright) after all waves; targeted `-Dtest`/`-Dit.test` for per-task feedback during waves. |
| `feedback_wave_pause` (memory) | After each wave merge, orchestrator pauses for user feedback. |

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — All primitives (ReentrantLock, HandlerInterceptor, @ControllerAdvice, RedirectView, Files.newOutputStream) are stable Spring/JDK with documented Javadocs.
- Architecture: HIGH — Three-ring concentric-defense shape is fully locked by CONTEXT.md; this research validated the wiring against existing code (BackupController catch chain, GlobalModelAdvice pattern, FailAtTableInjector bean-override discipline).
- Pitfalls: HIGH — Two real landmines surfaced (Pitfall §1 redirect status leak; Pitfall §3 catch-chain order), both verified against existing code reads and Spring docs. Pitfalls §2 / §4-§8 are documented defenses, not new risks.

**Research date:** 2026-05-14
**Valid until:** 2026-06-13 (30 days — stable stack; Spring Boot 4.0.6 + JDK 25 + Thymeleaf 3.1.5 already in production)

---

*Phase: 76-operational-hardening-import-lock-read-only-banner-auto-back*
*Research authored: 2026-05-14*
