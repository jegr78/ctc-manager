# Phase 76: Operational Hardening — Import Lock + Read-Only Banner + Auto-Backup-Before-Import — Pattern Map

**Mapped:** 2026-05-14
**Files analyzed:** 12 new + 4 extended = 16
**Analogs found:** 14 / 16 (2 files: `ImportLockedWriteRejector` + `WebConfig.addInterceptors(...)` — no `HandlerInterceptor` exists in the codebase yet; planner uses RESEARCH Pattern 2 + Spring reference docs)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/backup/lock/ImportLockService.java` | service (singleton state holder) | request-response (read/write of in-memory mutex) | `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` | role-match (singleton `@Service` wrapping a JDK primitive — JdbcTemplate ↔ ReentrantLock) |
| `src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java` | middleware (HandlerInterceptor) | request-response (short-circuit POST) | — (no `HandlerInterceptor` exists in `org.ctc.*`) | NO ANALOG — use RESEARCH Pattern 2 |
| `src/main/java/org/ctc/backup/lock/ImportLockBannerAdvice.java` | controller-advice (model attribute) | event-driven (per-request injection) | `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java` | exact (single `@ModelAttribute` method on a `@ControllerAdvice`) |
| `src/main/java/org/ctc/backup/exception/AutoBackupBeforeImportException.java` | exception (failure carrier subclass) | transform (wraps cause + carries `auditUuid`) | `src/main/java/org/ctc/backup/exception/BackupImportException.java` (parent type) + `UploadsRestoreException.java` (sibling shape) | exact (subclass; delegates to parent 3-arg ctor) |
| `src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java` | test (Surefire unit) | request-response (3 method semantics) | `src/test/java/org/ctc/backup/service/BackupImportLimitsTest.java` | role-match (BDD-named JUnit 5; pure Java assertions) |
| `src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java` | test infrastructure (`@Primary` bean + `RestoreFailureInjector` impl) | event-driven (latch on `maybeFailAt`) | `src/test/java/org/ctc/backup/service/FailAtTableInjector.java` | exact (must mirror the `@Bean(name="noopRestoreFailureInjector") @Primary` discipline verbatim) |
| `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` | test (Failsafe IT, 2-thread) | event-driven (latch coordination) | `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` | role-match (Failsafe IT with `@Import(...Config.class)` + `spring.main.allow-bean-definition-overriding=true`) |
| `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` | test (Failsafe IT) | request-response (drive slow import + assert 503) | `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` | role-match |
| `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` | test (Failsafe IT) | request-response (drive slow import + assert banner HTML) | `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` | role-match |
| `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java` | test (Failsafe IT, happy path) | file-I/O (assert ZIP exists at `<ts>/auto-backup-before-import.zip`) | `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java` | role-match (happy-path full round-trip IT) |
| `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` | test (Failsafe IT, failure path) | file-I/O (inject IOException on write) | `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` | role-match (failure-path with audit-row assertion) |
| `docs/operations/import-runbook.md` | documentation (Markdown) | n/a | — (new top-level dir) | NO ANALOG — plain Markdown per D-22 outline |
| `src/main/java/org/ctc/backup/BackupController.java` (EXTENDED) | controller | request-response | self (lines 228-289 — wrap with tryLock/finally) | self-extend (preserve existing catch chain + insert new AutoBackupBeforeImportException catch FIRST) |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` (EXTENDED) | service | request-response + file-I/O | self (lines 419-505 — insert auto-export block + move `<ts>` upward) | self-extend |
| `src/main/java/org/ctc/admin/WebConfig.java` (EXTENDED) | config (`WebMvcConfigurer`) | request-response wiring | self (existing `addResourceHandlers` override) | self-extend (add `addInterceptors(...)` override beside it) |
| `src/main/resources/templates/admin/layout.html` (EXTENDED) | template | request-response | self (line 82 — flash `<div th:if>` block) | self-extend (one new sibling `<div>` directly above) |

---

## Pattern Assignments

### `src/main/java/org/ctc/backup/lock/ImportLockService.java` (service, singleton-state)

**Analog:** `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` (role-match — both are `@Service` singletons wrapping a primitive that is mutated on a hot request-flow path; both carry SLF4J logging discipline). The shape itself is also fully locked by RESEARCH Pattern 1.

**Package + imports pattern** (mirror `DataImportAuditService.java:1-18`):
```java
package org.ctc.backup.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;
```

**Class annotations pattern** (RESEARCH Pattern 1 + CONTEXT D-01 SECU-05 wording):
```java
@Slf4j
@Service
@Scope("singleton")  // explicit per CONTEXT D-01 / SECU-05 wording — redundant but documents intent
public class ImportLockService {
    private final ReentrantLock lock = new ReentrantLock();  // fairness=false per D-01
```

**Lombok convention** (CONVENTIONS.md §"Services and Controllers" lines 57-64): `@RequiredArgsConstructor` is NOT used here because the class has zero injected dependencies — the `ReentrantLock` field is final-initialized inline. `@Slf4j` is kept (it lets the planner add diagnostic logging on `tryLock`/`unlock` transitions later without re-annotating).

**Core pattern — three methods** (RESEARCH Pattern 1, lines 256-273):
```java
public boolean tryLock() {
    return lock.tryLock();  // non-blocking, zero-timeout (D-02)
}

public void unlock() {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
    // idempotent — stray finally-unlock after a failed tryLock is a silent no-op (D-01)
}

public boolean isLocked() {
    return lock.isLocked();  // read-only; does NOT require current thread to hold the lock
}
```

**No error handling needed:** `ReentrantLock` methods do not throw checked exceptions; `tryLock()` returns `false` instead of throwing, and `isHeldByCurrentThread()` guards `unlock()` against `IllegalMonitorStateException`.

---

### `src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java` (middleware, request-short-circuit)

**Analog:** NONE in `org.ctc.*` — no class in `src/main/java/` implements `HandlerInterceptor`. The closest structural reference is `WebConfig` (the registration site) + RESEARCH Pattern 2 (lines 281-305). Planner uses Spring MVC reference §1.10.4 verbatim.

**Package + imports pattern** (mirror Spring reference + CONVENTIONS.md §"Import Organization" order — own, Jakarta, Lombok, Spring, Java):
```java
package org.ctc.backup.lock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
```

**Class annotations pattern** (RESEARCH Pattern 2 + CONVENTIONS.md services-and-controllers Lombok):
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportLockedWriteRejector implements HandlerInterceptor {
    private final ImportLockService importLockService;
```

**Lock-HTML constant** (RESEARCH Pattern 2 + CD-03 minimal HTML + auto-refresh):
```java
private static final String LOCK_HTML = """
    <!DOCTYPE html><html><head><meta charset="UTF-8">
    <meta http-equiv="refresh" content="10"></head>
    <body><h1>Backup import in progress — write access is temporarily locked.</h1>
    <p>This page will retry automatically.</p></body></html>
    """;
```

**Core pattern — preHandle short-circuit** (CONTEXT D-08 verbatim + D-09 whitelist + D-10 equals-match):
```java
@Override
public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
        throws IOException {
    if (!"POST".equalsIgnoreCase(req.getMethod())) return true;
    if (!importLockService.isLocked()) return true;
    if ("/admin/backup/import-execute".equals(req.getRequestURI())) return true;  // D-09 / D-10
    res.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
    res.setContentType("text/html;charset=UTF-8");
    res.getWriter().write(LOCK_HTML);
    return false;
}
```

**Error handling:** `IOException` from `res.getWriter().write(...)` is declared (servlet I/O — the container handles the failure). No try/catch needed.

---

### `src/main/java/org/ctc/backup/lock/ImportLockBannerAdvice.java` (controller-advice, model-injection)

**Analog:** `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java` (EXACT match — both are single-class `@ControllerAdvice` with one `@ModelAttribute` method exposing a single value).

**Package + imports pattern** (CONTEXT D-11: live next to `ImportLockService` for cohesion = `org.ctc.backup.lock`):
```java
package org.ctc.backup.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
```

**Class shape — full file** (mirror `GlobalModelAdvice.java:1-18` line-for-line; swap `@Value` injection for `@RequiredArgsConstructor` since the dependency is a Spring bean, not a property string):
```java
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

**Scoping note (D-13):** No `basePackages` filter on `@ControllerAdvice` — applies globally. Site templates that don't reference `${importInProgress}` evaluate it as null/false in `th:if` and harmlessly skip.

**No error handling:** `isLocked()` cannot throw.

---

### `src/main/java/org/ctc/backup/exception/AutoBackupBeforeImportException.java` (exception subclass)

**Analog:** `src/main/java/org/ctc/backup/exception/BackupImportException.java` (EXACT — parent type; CONTEXT D-17 locks `extends BackupImportException`). Sibling shape: `UploadsRestoreException.java` (single-purpose RuntimeException subclass) and `BackupArchiveException.java` (constructor-delegation pattern).

**Package + imports pattern** (mirror `BackupImportException.java:1-3`):
```java
package org.ctc.backup.exception;

import java.util.UUID;
```

**Class shape** (CONTEXT D-17 + Anti-Pattern "keep constructor strictly delegating"):
```java
/**
 * Phase 76 / SECU-07 — failure carrier thrown by {@code BackupImportService.execute(UUID)}
 * when the pre-import auto-backup ZIP write fails (D-14 / D-16). Subclass of
 * {@link BackupImportException} so the existing controller catch-chain captures it via
 * inheritance; the controller's NEW catch clause MUST appear BEFORE the parent catch in
 * the chain (Java exception-matching order — RESEARCH Pitfall #3).
 *
 * <p>Semantic difference vs {@link BackupImportException}: NO database mutation occurred —
 * the audit-row counts ({@code wipedCounts}, {@code restoredCounts}) are empty {@code {}}
 * JSON objects per CONTEXT D-18, and the controller flash communicates "no rollback was
 * needed" rather than the generic "rolled back" wording (D-17).
 */
public class AutoBackupBeforeImportException extends BackupImportException {

    public AutoBackupBeforeImportException(UUID auditUuid, Throwable cause) {
        super(auditUuid, /* auditWritten */ true, cause);
    }

    public AutoBackupBeforeImportException(UUID auditUuid, boolean auditWritten, Throwable cause) {
        super(auditUuid, auditWritten, cause);
    }
}
```

**Constructor-delegation pattern from `BackupImportException.java:44-46` + `:59-63`** — both constructors call `super(...)` directly. No new fields, no overridden `getMessage()` — the parent's `describe(cause)` synthesis already produces a useful log message.

---

### `src/main/java/org/ctc/backup/BackupController.java` (EXTENDED — `importExecute` method, lines 228-289)

**Analog:** self. The existing method body already implements the multi-clause catch-chain idiom. Phase 76 surgical edit per CONTEXT D-04 / D-05 / D-17 / D-23.

**Imports to add** (`BackupController.java:1-35` current imports):
```java
import org.ctc.backup.exception.AutoBackupBeforeImportException;  // D-17
import org.ctc.backup.lock.ImportLockService;                      // D-04
import org.springframework.http.HttpStatus;                        // D-05
import org.springframework.web.servlet.ModelAndView;               // D-05 view-mode redirect
import org.springframework.web.servlet.view.RedirectView;          // D-05 view-mode redirect
```

**Field injection** (mirror existing `@RequiredArgsConstructor` discipline already on the class):
```java
private final ImportLockService importLockService;
```
Constructor stays Lombok-generated — just add the field as `final`.

**Wrapper pattern around existing body** (CONTEXT D-04 verbatim pseudocode + RESEARCH Pattern 3 for the 409):
```java
@PostMapping("/import-execute")
public ModelAndView importExecute(  // RETURN TYPE CHANGE: String → ModelAndView (RESEARCH Pattern 3)
        @Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form,
        BindingResult bindingResult, Model model, RedirectAttributes ra) {

    // 409 GUARD — D-04 / D-05 / RESEARCH Pattern 3 (view-mode redirect, NOT response.setStatus)
    if (!importLockService.tryLock()) {
        ra.addFlashAttribute("errorMessage",
                "Another import is already running — please wait.");
        RedirectView rv = new RedirectView("/admin/backup");
        rv.setStatusCode(HttpStatus.CONFLICT);
        rv.setHttp10Compatible(false);  // REQUIRED: in http10Compatible=true mode sendRedirect overwrites status
        return new ModelAndView(rv);
    }
    try {
        // -------- existing body (lines 232-285) preserved verbatim, wrapped in ModelAndView returns --------
        // Note: every `return "redirect:/admin/backup";` becomes
        //       `return new ModelAndView("redirect:/admin/backup");`
        // and the binding-errors branch becomes
        //       `return new ModelAndView("admin/backup-confirm").addObject("preview", preview);`
    } finally {
        importLockService.unlock();  // D-06 — synchronous AFTER_COMMIT already completed
    }
}
```

**New catch clause — INSERTED FIRST in the inner catch chain** (CONTEXT D-17 + RESEARCH Pitfall #3: subclass must precede parent in Java catch matching). Insert BEFORE the existing `catch (BackupImportException ex)` at line 269:
```java
} catch (AutoBackupBeforeImportException ex) {
    // D-17 — semantically NO rollback is needed because nothing was mutated.
    String auditIdText = ex.isAuditWritten()
            ? ex.getAuditUuid().toString()
            : "unavailable (audit write failed; see logs for " + ex.getAuditUuid() + ")";
    ra.addFlashAttribute("errorMessage",
            String.format("Import aborted — pre-import auto-backup failed. "
                    + "No database changes. Audit-id: %s.", auditIdText));
}
```

**Existing catch chain preservation** (lines 253-285 — `BackupArchiveException`, `IOException`, `UploadsRestoreException`, `BackupImportException`): byte-identical except every `return "redirect:..."` becomes `return new ModelAndView("redirect:...")`. The exhaustive switch helper `mapReason(BackupArchiveException)` (lines 314-321) stays untouched.

**Logging pattern reuse:** existing `log.error(...)` calls (line 241, 256, 264) stay — `@Slf4j` already on the class via CONVENTIONS.md.

---

### `src/main/java/org/ctc/backup/service/BackupImportService.java` (EXTENDED — `execute` method, lines 419-505)

**Analog:** self. The existing method body is the canonical Phase 75 contract. Phase 76 surgical edits per CONTEXT D-14 / D-15 / D-16.

**Imports to add** (`BackupImportService.java:39-58` current imports):
```java
import org.ctc.backup.exception.AutoBackupBeforeImportException;     // D-17
import java.io.OutputStream;                                          // D-16
import java.nio.file.StandardOpenOption;                              // D-16
```

**Edit 1 — MOVE `<ts>` computation upward** (CONTEXT D-15). Current site: line 456 (after meta-read at line 442-453). New site: directly after `if (!Files.exists(staged))` guard at line 439, BEFORE `sourceFilename` read at line 440. Resulting order:
1. `Path staged = ...` (line 429 — unchanged)
2. `if (!Files.exists(staged))` guard (lines 431-439 — unchanged)
3. **`String ts = Instant.now()...` (MOVED from line 456 → new position ~line 440)**
4. **`Path importBackupDir = importBackupsDir.resolve(ts);` (MOVED with it)**
5. **`Path autoBackupZip = importBackupDir.resolve("auto-backup-before-import.zip");` (NEW — D-16)**
6. `sourceFilename` read (lines 440-453 — unchanged)
7. `Path uploadsNewDir = ...` (line 458 — stays in its current position; depends only on `importBackupDir`)
8. **NEW auto-export block** (see Edit 2 below)
9. Existing wipe → extract → restore → publish (lines 464-505 — unchanged)

**Edit 2 — INSERT auto-export block** (CONTEXT D-14 / D-16 + RESEARCH Pattern 5). Insert directly after Step-0 manifest re-read (line 469-470) and BEFORE Step-1 wipe (line 473):
```java
// Step 0.5 — Phase 76 / SECU-07: pre-import auto-backup (D-14 / D-16).
// Runs INSIDE the outer @Transactional(REQUIRED, READ_COMMITTED) — the read-only
// writeZip(...) joins this tx (no-op join). If the write fails, NO DB mutation has
// occurred yet; the outer tx rolls back as a no-op.
try {
    Files.createDirectories(importBackupDir);
    try (OutputStream out = Files.newOutputStream(autoBackupZip,
            StandardOpenOption.CREATE_NEW)) {
        backupArchive.writeZip(out, Instant.now());
    }
} catch (IOException | RuntimeException autoExportEx) {
    tryDeletePartialAutoBackup(autoBackupZip);  // D-19 best-effort, never throws
    log.error("Auto-backup-before-import failed for staging-id {} — aborting import",
            stagingId, autoExportEx);
    boolean auditWritten = tryRecordFailure(auditUuid, schemaVersion,
            sourceFilename, Map.of(), Map.of());  // D-18 — empty count maps
    throw new AutoBackupBeforeImportException(auditUuid, auditWritten, autoExportEx);
}
```

**Edit 3 — NEW private helper method** (CONTEXT D-19 — mirror existing `tryCleanupUploadsNew` shape at lines 738-753):
```java
/**
 * Best-effort partial-ZIP cleanup on auto-backup failure (D-19). Never throws.
 */
private static void tryDeletePartialAutoBackup(Path target) {
    if (target == null) return;
    try {
        Files.deleteIfExists(target);
    } catch (IOException io) {
        log.warn("Failed to delete partial auto-backup ZIP {}", target, io);
    }
}
```

**Logging pattern** (mirror existing `log.error(...)` calls at lines 434, 451, 728): always parameterized `{}`, never string concat (CONVENTIONS.md §"Logging").

**Wipe / extract / restore / publish blocks (lines 472-510) — BYTE-IDENTICAL post-edit** per CONTEXT D-23.

---

### `src/main/java/org/ctc/admin/WebConfig.java` (EXTENDED — add `addInterceptors`)

**Analog:** self (existing `addResourceHandlers` override). The new `addInterceptors` override sits next to it.

**Imports to add** (`WebConfig.java:1-8` current imports):
```java
import org.ctc.backup.lock.ImportLockedWriteRejector;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
```

**Field injection switch** — current `WebConfig` uses `@Value` field injection only (no `@RequiredArgsConstructor`). To pull in `ImportLockedWriteRejector`, switch the class to `@RequiredArgsConstructor` and convert the `@Value` field to constructor-property-injection:
```java
@Configuration
@RequiredArgsConstructor  // NEW — needed to inject ImportLockedWriteRejector
public class WebConfig implements WebMvcConfigurer {

    private final ImportLockedWriteRejector importLockedWriteRejector;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;  // keep as @Value — Lombok handles the rest via final-on-bean fields
    // ... existing addResourceHandlers unchanged ...
```

Alternative (less invasive) — add a setter-style `@Autowired` field. The planner picks; the cleanest is the `@RequiredArgsConstructor` switch.

**New override pattern** (RESEARCH Pattern 2 registration block — lines 309-315):
```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(importLockedWriteRejector)
            .addPathPatterns("/admin/**");
}
```

**Existing `addResourceHandlers(/uploads/**)` is byte-identical** (CONTEXT D-23).

---

### `src/main/resources/templates/admin/layout.html` (EXTENDED — line 82, banner div)

**Analog:** self (existing flash-message div block at lines 82-83).

**Edit pattern** (CONTEXT D-12 verbatim — insert ABOVE the success/error flash divs):
```html
<div class="container">
    <div th:if="${importInProgress}" class="alert alert-warning" role="status">
        Backup import in progress — write access is temporarily locked.
    </div>
    <div th:if="${successMessage}" class="alert alert-success" th:text="${successMessage}"></div>
    <div th:if="${errorMessage}" class="alert alert-error" th:text="${errorMessage}"></div>
    <div th:replace="${content}"></div>
</div>
```

**CSS reuse** — `alert alert-warning` already exists at `src/main/resources/static/admin/css/admin.css:161`:
```css
.alert-warning { background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00; }
```
Zero CSS additions per CONTEXT D-12 + feedback_no_inline_styles. Three-line `<div>`, no `style=` attribute, no JavaScript.

**ARIA pattern (CD-04):** `role="status"` (polite live region — announced on page load, not aggressively interrupting).

---

### `src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java` (test, Surefire unit)

**Analog:** `src/test/java/org/ctc/backup/service/BackupImportLimitsTest.java` (role-match — pure JUnit 5, BDD given-when-then naming, no Spring context, no mocks needed for a primitive-wrapping service).

**Package + imports pattern** (mirror `BackupImportLimitsTest.java:1-5`):
```java
package org.ctc.backup.lock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
```

**Class shape** (no Spring annotations — direct instantiation):
```java
class ImportLockServiceTest {

    @Test
    void givenFreshService_whenIsLocked_thenReturnsFalse() { ... }

    @Test
    void givenFreshService_whenTryLockCalled_thenReturnsTrueAndIsLockedFlips() { ... }

    @Test
    void givenLockHeld_whenSecondTryLockOnSameThread_thenReturnsTrue() {
        // ReentrantLock allows re-entrance on the same thread — document the behavior.
    }

    @Test
    void givenLockHeld_whenUnlockCalled_thenIsLockedFlipsBackToFalse() { ... }

    @Test
    void givenLockNotHeld_whenUnlockCalled_thenIdempotentNoOp() {
        // CRITICAL — D-01 idempotent-unlock guard (isHeldByCurrentThread check)
    }

    @Test
    void givenLockHeldByOtherThread_whenIsLockedRead_thenReturnsTrueWithoutCurrentThreadCheck() {
        // ReentrantLock.isLocked() does NOT require current-thread holding — D-01 contract
    }
}
```

**BDD naming pattern** (CLAUDE.md §"Test Naming"): `givenContext_whenAction_thenExpectedResult()`. Body uses `// given` / `// when` / `// then` comments.

---

### `src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java` (test infrastructure)

**Analog:** `src/test/java/org/ctc/backup/service/FailAtTableInjector.java` (EXACT — must mirror the `@Bean(name = "noopRestoreFailureInjector") @Primary` discipline verbatim per CONTEXT D-20 + RESEARCH summary lines 9-10).

**Package** (CD-05 default): `org.ctc.backup.it.support`.

**Critical bean-override discipline** (mirror `FailAtTableInjector.java:90-108` verbatim — only the impl body differs):
```java
@TestConfiguration
public static class Config {

    @Bean(name = "noopRestoreFailureInjector")  // SAME NAME as the production @Component
    @Primary
    public RestoreFailureInjector blockingInjector(CountDownLatch holdLatch,
                                                   CountDownLatch releaseLatch) {
        return new BlockingRestoreFailureInjector(holdLatch, releaseLatch, "race_results", 50);
    }
}
```

**Test classes opt-in** (mirror `BackupImportRollbackIT.java:90-95`):
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(BlockingRestoreFailureInjector.Config.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@ExtendWith(OutputCaptureExtension.class)  // optional — for log assertions
class ImportConcurrentLockIT { ... }
```

**Core injector body** (latch-based blocking):
```java
public class BlockingRestoreFailureInjector implements RestoreFailureInjector {
    private final CountDownLatch hasAcquired;
    private final CountDownLatch releaseLatch;
    private final String targetTable;
    private final int targetRow;

    @Override
    public void maybeFailAt(String tableName, int rowIndex) {
        if (targetTable.equals(tableName) && rowIndex == targetRow) {
            hasAcquired.countDown();  // thread B starts after this
            try {
                releaseLatch.await(5, TimeUnit.SECONDS);  // thread A pauses here
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

---

### `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` (test, 2-thread Failsafe IT)

**Analog:** `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` (role-match — same Failsafe IT discipline, same `@Import(...Config.class)` + bean-override property, same export-then-stage-then-execute scaffolding).

**Package note:** The CONTEXT files at lines 215, 286-292 specify `src/test/java/org/ctc/backup/it/` as the Phase 76 IT location. The current Phase 75 ITs live in `src/test/java/org/ctc/backup/service/` (the `it/` subdirectory does not yet exist). **Planner creates the new `it/` package directory** — this is the CONTEXT D-20/D-21 anchor.

**Class header + autowire block pattern** (mirror `BackupImportRollbackIT.java:90-130`):
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(BlockingRestoreFailureInjector.Config.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class ImportConcurrentLockIT {

    @Autowired BackupImportService backupImportService;
    @Autowired BackupArchiveService backupArchiveService;
    @Autowired TestDataService testDataService;
    @Autowired DataImportAuditRepository dataImportAuditRepository;
    @Autowired ImportLockService importLockService;
    @Autowired CountDownLatch hasAcquired;
    @Autowired CountDownLatch releaseLatch;
    // ... + @Value bound staging/uploads/import-backups dirs
```

**`@BeforeAll` fixture seeding** (mirror `BackupImportRollbackIT.java:136-150`):
```java
@BeforeAll
void seedFixture() throws IOException {
    testDataService.seed();
    stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
    Files.createDirectories(stagingDir);
}
```

**Core scenario** (CONTEXT D-20 lines 132-139):
1. Two staged ZIPs uploaded via shared dev-data fixture.
2. Thread A: `POST /admin/backup/import-execute` with stagingId-A (blocking injector pauses at row 50 of `race_results`).
3. Thread B (waits on `hasAcquired` latch): `POST /admin/backup/import-execute` with stagingId-B.
4. Assert thread B response: HTTP 409 + Flash `"Another import is already running — please wait."`.
5. `releaseLatch.countDown()`.
6. Thread A completes the import normally; assert HTTP 302 redirect + success Flash.
7. Assert exactly 1 audit row with `success=true` (thread A's) — thread B never reached the service.

**Drive the controller**, not the service directly: use `MockMvc` (already in use in `BackupControllerIT.java` — planner reads that for the MockMvc-with-`@SpringBootTest` shape).

---

### `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` (test, Failsafe IT — 503-rejector)

**Analog:** `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` (role-match — same slow-import driver) + `BackupControllerIT.java` (for MockMvc POST-form scaffolding).

**Scenario** (CONTEXT D-21 line 140):
- Drive slow import in background thread via `BlockingRestoreFailureInjector` pausing at `race_results:50`.
- While lock is held:
  - Fire MockMvc `POST /admin/teams` (non-whitelisted) → assert HTTP 503 + body contains banner wording.
  - Fire MockMvc `POST /admin/backup/import-execute` → NOT rejected by interceptor; the controller-level `tryLock` rejects with 409 instead (planner verifies the 409 path).
- `releaseLatch.countDown()`; thread A completes; verify no 503 on subsequent POSTs.

---

### `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` (test, Failsafe IT — banner)

**Analog:** `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java`.

**Scenario** (CONTEXT D-21 line 140):
- Drive slow import in background thread.
- While lock is held: fire MockMvc `GET /admin/seasons` → assert response HTML contains the literal banner text `"Backup import in progress — write access is temporarily locked."`.
- After release: fire same GET → assert banner text is NOT present.

---

### `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java` (test, happy-path Failsafe IT)

**Analog:** `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java` (role-match — full export → stage → execute round trip, with explicit file-system assertions).

**Scenario**:
- Seed fixture; export + stage as in `BackupImportExecuteIT.java:104-...`.
- Invoke `backupImportService.execute(stagingId)`.
- Assert success.
- Walk `data/.import-backups/` and find ONE `<ts>/auto-backup-before-import.zip` plus a SIBLING `<ts>/uploads-old/` (per D-15 — same `<ts>` for both).
- Assert the ZIP is non-empty and parseable via `backupArchive.readManifest(autoBackupZip)`.
- Cleanup in `@AfterEach` to keep cross-test state hygienic.

---

### `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` (test, failure-path Failsafe IT)

**Analog:** `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` (failure-path assertion battery — rollback + audit-row + log capture).

**Scenario**:
- Seed fixture, export + stage.
- Inject `IOException` on auto-export write — strategies:
  - **(preferred)** make the target directory read-only via `chmod` on the parent of `<ts>/` (POSIX) or `setReadOnly()` (cross-platform JDK).
  - **alternative** pre-create the `auto-backup-before-import.zip` file with the same `<ts>` (the `CREATE_NEW` semantic throws `FileAlreadyExistsException`). Requires controlling `Instant.now()`.
- Assert `BackupImportException` thrown is an `AutoBackupBeforeImportException` (not the parent type).
- Assert table row counts UNCHANGED — wipe never ran.
- Assert `data_import_audit` row exists with `success=false`, `tableCountsWiped="{}"`, `tableCountsRestored="{}"` (D-18).
- Assert partial ZIP cleaned up (D-19) — `Files.exists(autoBackupZip)` is false after the failure.

---

### `docs/operations/import-runbook.md` (documentation)

**Analog:** NONE — new top-level `docs/operations/` directory per CONTEXT D-22.

**5 locked sections** (CONTEXT D-22 lines 145-150):
1. Recovery from auto-backup (ZIP location, re-import via `/admin/backup`, mariadb-import + manual `uploads/` move if app is down).
2. 24h retention semantics + `find data/.import-backups -mtime +1 -delete` example.
3. Audit-id query SQL + interpretation of `wiped=0, restored=0, success=false` = D-18 pre-import auto-backup failure.
4. Concurrent-import behavior (HTTP 409 explanation).
5. Read-only state during imports (yellow banner + 503).

Plain Markdown, no Thymeleaf, no wiki linking syntax. UI text quotes match D-04 / D-12 / D-17 wording verbatim.

---

## Shared Patterns

### Lombok service-style annotations
**Source:** `src/main/java/org/ctc/backup/audit/DataImportAuditService.java:47-48` + CONVENTIONS.md §"Services and Controllers"
**Apply to:** `ImportLockService`, `ImportLockedWriteRejector`, `ImportLockBannerAdvice`, `BlockingRestoreFailureInjector`
```java
@Slf4j
@Service                       // or @Component / @ControllerAdvice
@RequiredArgsConstructor       // unless zero dependencies (see ImportLockService note)
```

### Constructor injection via `final` fields
**Source:** CONVENTIONS.md lines 57-64, observed across all `@Service` classes
**Apply to:** Every new class that depends on `ImportLockService`
```java
private final ImportLockService importLockService;
// Lombok-generated constructor — never hand-written
```

### Failsafe IT class header
**Source:** `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java:90-95`
**Apply to:** All 5 Phase 76 ITs (`ImportConcurrentLockIT`, `ImportLockedPostRejectorIT`, `ImportLockBannerAdviceIT`, `AutoBackupBeforeImportPathIT`, `AutoBackupBeforeImportFailureIT`)
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(BlockingRestoreFailureInjector.Config.class)              // only ITs that drive a slow import
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@ExtendWith(OutputCaptureExtension.class)                         // only ITs that assert on logs
```

### BDD test method naming
**Source:** CLAUDE.md §"Test Naming (Given-When-Then)" + `BackupImportRollbackIT.java:184`
**Apply to:** Every new test method (unit + IT)
```java
void givenContext_whenAction_thenExpectedResult() {
    // given
    // when
    // then
}
```

### Flash-attribute keys
**Source:** CONVENTIONS.md §"Controller Patterns" + `BackupController.java:250, 282`
**Apply to:** The new 409 flash (D-04) and the new auto-backup-failure flash (D-17)
```java
ra.addFlashAttribute("successMessage", "...");  // never used in Phase 76
ra.addFlashAttribute("errorMessage", "...");    // both Phase 76 flashes
```

### Parameterized SLF4J logging
**Source:** CONVENTIONS.md §"Logging" + universal across `org.ctc.backup.*`
**Apply to:** All new log statements
```java
log.info("Backup import execute started: stagingId={}", stagingId);  // never string concat
log.error("Auto-backup-before-import failed for staging-id {} — aborting import", stagingId, ex);
```

### Audit-row REQUIRES_NEW write on failure
**Source:** `BackupImportService.java:715-732` (`tryRecordFailure` helper) + `DataImportAuditService.java:101-142`
**Apply to:** The new D-18 auto-backup-failure path (call `tryRecordFailure(auditUuid, schemaVersion, sourceFilename, Map.of(), Map.of())` — both count maps empty per D-18)

### English UI text only (overrides REQUIREMENTS.md German)
**Source:** CLAUDE.md memory `feedback_ui_language` + CONTEXT specifics line 300
**Apply to:** All UI strings in Phase 76 — banner text, 409 flash, 503 HTML body, auto-backup-failure flash, runbook
- Banner: `"Backup import in progress — write access is temporarily locked."` (D-12)
- 409 flash: `"Another import is already running — please wait."` (D-04)
- Auto-backup-failure flash: `"Import aborted — pre-import auto-backup failed. No database changes. Audit-id: {auditUuid}."` (D-17)

### No inline CSS styles
**Source:** CLAUDE.md memory `feedback_no_inline_styles` + CONTEXT D-12
**Apply to:** `admin/layout.html` banner div — uses existing `alert alert-warning` class only; no `style=` attribute

### Idiomatic Spring view-mode redirect with explicit HTTP status
**Source:** RESEARCH Pattern 3 + RESEARCH Pitfall #1
**Apply to:** `BackupController.importExecute` 409 path
```java
RedirectView rv = new RedirectView("/admin/backup");
rv.setStatusCode(HttpStatus.CONFLICT);
rv.setHttp10Compatible(false);  // REQUIRED — default true mode forces 302 via sendRedirect
return new ModelAndView(rv);
```

---

## No Analog Found

| File | Role | Data Flow | Reason | Fallback |
|------|------|-----------|--------|----------|
| `src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java` | middleware (HandlerInterceptor) | request-short-circuit | No `HandlerInterceptor` impl exists in `org.ctc.*` codebase yet | Use RESEARCH Pattern 2 (lines 281-305) verbatim — Spring MVC reference §1.10.4 |
| `src/main/java/org/ctc/admin/WebConfig.java` extension — `addInterceptors(...)` | config (`WebMvcConfigurer` override) | wiring | No `addInterceptors(InterceptorRegistry)` exists in the codebase | Use RESEARCH Pattern 2 registration block (lines 309-315) verbatim |
| `docs/operations/import-runbook.md` | documentation | n/a | New top-level directory, no precedent | Follow D-22 5-section outline; cross-link to relevant code paths |

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/backup/` (all subpackages — `audit`, `exception`, `restore`, `service`, `event`, `dto`)
- `src/main/java/org/ctc/admin/` (`controller/GlobalModelAdvice.java`, `WebConfig.java`)
- `src/main/resources/templates/admin/layout.html`
- `src/main/resources/static/admin/css/admin.css` (alert-warning rule at line 161)
- `src/test/java/org/ctc/backup/service/` (Phase 75 Failsafe IT baseline)

**Files scanned:** 13 production classes, 4 test classes, 1 template, 1 CSS file, 4 documentation files (CLAUDE.md, CONVENTIONS.md, ARCHITECTURE.md, CONTEXT.md+RESEARCH.md).

**Key pre-existing assets reused verbatim:**
- `RestoreFailureInjector` interface + `NoopRestoreFailureInjector @Primary` — Phase 75 D-13 carry-forward.
- `BackupArchiveService.writeZip(OutputStream, Instant)` — Phase 73, no overload added.
- `DataImportAuditService.recordResult(...)` REQUIRES_NEW — Phase 75 D-01 carry-forward.
- `BackupImportException(UUID, boolean, Throwable)` 3-arg constructor — Phase 75 WR-03 carry-forward.
- `tryRecordFailure(...)` private helper in `BackupImportService` — Phase 75 Plan 06 carry-forward.
- `alert-warning` CSS class — pre-existing in `admin.css:161`.
- `GlobalModelAdvice` ControllerAdvice shape — mirrored verbatim by `ImportLockBannerAdvice`.
- `FailAtTableInjector.Config` test-bean-override discipline — mirrored verbatim by `BlockingRestoreFailureInjector.Config`.

**Pattern extraction date:** 2026-05-14
