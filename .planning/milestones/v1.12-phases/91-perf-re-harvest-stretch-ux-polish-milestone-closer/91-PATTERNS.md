# Phase 91: PERF Re-Harvest, Stretch UX Polish & Milestone Closer — Pattern Map

**Mapped:** 2026-05-20
**Files analyzed:** 22 (10 new + 12 modified across all 3 plans)
**Analogs found:** 19 / 22 — 3 files have no exact analog (the sealed-class idiom is fresh in this codebase; see "No Analog Found" below). All 19 with analogs have CONCRETE excerpts with file path + line numbers.

---

## File Classification

### Plan 91-01 — PERF-06 CI Re-Harvest (docs + gh CLI only; no `src/main/java` touch per D-13)

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `docs/test-performance.md` (append `## PERF-06 Re-Harvest`) | docs (append-only) | docs/report | `docs/test-performance.md § CI Results (PERF-05)` (existing section in same file) | exact — same file, append-only convention |
| `.planning/STATE.md` (single-line swap at `:142`) | docs (in-place edit) | docs/baseline-pointer | `.planning/STATE.md:142` (current line carrying the 23:00 v1.11 baseline) | exact — same line |
| `.planning/PROJECT.md` (append row in `## Key Decisions`) | docs (append-only) | docs/trend-log | `.planning/PROJECT.md § Key Decisions` table (existing trend-row format) | exact — same table |
| `gh pr create --draft` (PR #?? opened) | tooling / PR mechanics | gh CLI | `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-06-PLAN.md` Tasks 1+2 | exact — Phase 86 PERF-05 5-run harvest is the verbatim template |

### Plan 91-02 — UX-01 typed-exception hierarchy + flash UX + runbook

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/dataimport/exception/GoogleApiException.java` | exception (sealed base) | typed throw-carrier | `src/main/java/org/ctc/backup/exception/BackupArchiveException.java` (enum-Reason, RuntimeException) | role-match — typed-exception precedent but DIFFERENT idiom (enum-Reason vs sealed-permits); see "No Analog Found" |
| `src/main/java/org/ctc/dataimport/exception/TransientGoogleApiException.java` | exception (final permits) | typed throw-carrier | n/a — derived from sealed base; structural template only | new pattern |
| `src/main/java/org/ctc/dataimport/exception/AuthGoogleApiException.java` | exception (final permits) | typed throw-carrier | n/a — derived from sealed base | new pattern |
| `src/main/java/org/ctc/dataimport/exception/NotFoundGoogleApiException.java` | exception (final permits) | typed throw-carrier | n/a — derived from sealed base | new pattern |
| `src/main/java/org/ctc/dataimport/exception/PermissionGoogleApiException.java` | exception (final permits) | typed throw-carrier | n/a — derived from sealed base | new pattern |
| `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java` | utility (static helper) | transform (raw → typed) | `BackupArchiveException` constructor-throw idiom (file shape) — but no static-mapper precedent | partial-match — no static `*Mapper` class exists; sealed-class switch is a fresh shape |
| `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` (refactor synchronized client-builder + 3 IO call sites) | service (Google client wrapper) | request-response → typed throw | existing `getSheetsClient()` lines 157-181 (current `IOException`-wrap shape) | exact — same file, BEFORE pattern is the refactor target |
| `src/main/java/org/ctc/dataimport/GoogleCalendarService.java` (refactor synchronized client-builder + 2 IO call sites) | service (Google client wrapper) | request-response → typed throw | existing `getCalendarClient()` lines 95-119 (mirror of Sheets) | exact — same file |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (signature change at `execute()` line 99) | service (orchestrator) | propagate typed throw | existing `execute()` line 99-108 wrap point | exact — same method, refactor target |
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` (split single `IOException` catch into 4 typed catches at preview + execute) | controller | flash-attribute redirect | existing `preview()` lines 56-66 + `execute()` lines 102-112 (current `IOException`/`BusinessRuleException` catch shapes) | exact — same file, refactor target |
| `src/main/java/org/ctc/admin/controller/RaceController.java` (split combined `IOException \| IllegalStateException` catch at `createCalendarEvent` line 192-194) | controller | flash-attribute redirect | existing `createCalendarEvent` line 187-196 | exact — same method, refactor target |
| `src/main/resources/templates/admin/layout.html` (insert `<span class="error-badge ...">` inside existing flash block at line 86) | template | server-rendered | existing `layout.html:85-86` (current flash render) | exact — same line, in-place insertion |
| `src/main/resources/templates/admin/driver-import.html` (insert badge inside existing flash block at line 8-10) | template | server-rendered | existing `driver-import.html:8-10` (current `<div th:if="${errorMessage}">`) | exact — same line |
| `src/main/resources/static/admin/css/admin.css` (append after line 358, after `.badge*` rules) | css | static asset | existing `.alert*` + `.badge*` rules at lines 153-163 + 346-357 | exact — same file, append after the BEM-ish `.badge*` block |
| `docs/operations/google-integration.md` (NEW) | docs (operations runbook) | docs/runbook | `docs/operations/release-runbook.md` + `docs/operations/import-runbook.md` (both fully read) | exact — D-09 explicitly picks this shape |

### Plan 91-03 — Milestone Closer (docs + PR mechanics)

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `.planning/MILESTONES.md` (insert v1.12 entry at top, above current line 3) | docs (append-at-top) | milestone log | `.planning/MILESTONES.md` lines 3-44 (v1.11 entry — current top entry) | exact — same file, idiomatic top-insertion |
| `README.md § Test Performance` (pointer update) | docs (in-place edit) | docs/pointer | existing README Test Performance section (current v1.11 23:00 reference) | exact — same section, pointer swap |
| `README.md` Backup section (pointer update) | docs (in-place edit) | docs/pointer | existing README Backup section (current v1.10 PR pointer) | exact — same section |
| `gh pr edit --body-file` (final composite v1.12 PR body) | tooling / PR body | gh CLI | v1.11 PR #122 body (fetched via `gh pr view 122 --json body`) | exact — D-07b explicitly picks this template |
| `gh pr ready` (Draft → Ready flip) | tooling / PR state | gh CLI | n/a — single API call | n/a |

---

## Pattern Assignments

### 1. Sealed `GoogleApiException` base class

**Target file:** `src/main/java/org/ctc/dataimport/exception/GoogleApiException.java` (NEW)

**Analog:** `src/main/java/org/ctc/backup/exception/BackupArchiveException.java` — closest typed-exception precedent in the codebase. RESEARCH.md § "State of the Art" confirms: **no existing `sealed`/`permits` use in `src/main/java/org/ctc/**`** (grep returned 0 hits). The backup exception is the **structural** analog only; D-06 explicitly picks the `sealed` idiom on Java 25 over the enum-Reason precedent.

**Imports pattern** (analog `BackupArchiveException.java:1`):
```java
package org.ctc.backup.exception;
```
For `GoogleApiException` use `package org.ctc.dataimport.exception;` (D-06 default per CONTEXT § Claude's Discretion). Only one import needed: `import java.io.IOException;` (sealed base extends IOException per D-06 — preserves backward compatibility on `throws IOException` signatures).

**Class shape pattern** (analog `BackupArchiveException.java:14-21,88-103`):
```java
/**
 * Runtime exception thrown by ZIP hardening primitives and their callers when a backup archive
 * violates a security or structural constraint.
 *
 * <p>Every reject path in the backup import pipeline routes through this exception; the
 * {@link Reason} enum allows the controller to branch on the {@link #reason()}
 * value and select the appropriate Flash string without inspecting the message text.
 *
 * <p>Structural template mirrors {@code org.ctc.domain.exception.BusinessRuleException}
 * (single-field, no Lombok, no Spring annotations).
 */
public class BackupArchiveException extends RuntimeException {

    public enum Reason { PATH_TRAVERSAL, ENTRY_TOO_LARGE, ... NOT_A_ZIP }

    public BackupArchiveException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BackupArchiveException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
```

**Translation to sealed shape for `GoogleApiException`** (per D-06 + RESEARCH.md Pattern 1):
```java
package org.ctc.dataimport.exception;

import java.io.IOException;

/**
 * Sealed base for typed Google API failures surfaced by {@link org.ctc.dataimport.GoogleSheetsService}
 * and {@link org.ctc.dataimport.GoogleCalendarService}. Extends {@code IOException} so existing
 * {@code throws IOException} method signatures stay backward-compatible.
 *
 * <p>Controllers catch the {@link #category()} value to populate the {@code errorCategory}
 * flash attribute for category-specific UX badge rendering (Phase 91 / UX-01 D-07).
 */
public abstract sealed class GoogleApiException extends IOException
        permits TransientGoogleApiException, AuthGoogleApiException,
                NotFoundGoogleApiException, PermissionGoogleApiException {

    public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION }

    protected GoogleApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract Category category();
}
```

**Key differences vs. BackupArchiveException analog:**
- `abstract sealed` + `permits` clause (Java 25 idiom; D-06 picks this over the enum-Reason single-class shape)
- Extends `IOException` (NOT `RuntimeException`) — D-06 backward-compatibility constraint
- Single protected constructor (no public — concrete subtypes pass through)
- `Category` enum is nested (not standalone) and overridden via `abstract category()` in subtypes (RESEARCH.md Pattern 1)
- No Lombok, no Spring annotations (matches BackupArchiveException convention)

---

### 2. `TransientGoogleApiException` / `AuthGoogleApiException` / `NotFoundGoogleApiException` / `PermissionGoogleApiException`

**Target files:** 4 NEW files in `src/main/java/org/ctc/dataimport/exception/`

**Analog:** Same as #1 — `BackupArchiveException.java` constructor + javadoc shape. The sealed-permits subtypes are derived structurally (one-shot pattern for all 4).

**Constructor + javadoc translation template** (from BackupArchiveException analog constructor at `:88-103`, mapped to permits subtype):
```java
package org.ctc.dataimport.exception;

/**
 * Auth failure surfaced by the Google client when the OAuth token is expired, the
 * service-account JSON is unreadable, or the API returns HTTP 401 / 403-auth.
 *
 * <p>Operator action: re-download the service-account JSON key and replace the file at
 * {@code google.sheets.credentials-path}. See {@code docs/operations/google-integration.md}
 * § Error Categories row {@code AUTH}.
 */
public final class AuthGoogleApiException extends GoogleApiException {

    public AuthGoogleApiException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Category category() {
        return Category.AUTH;
    }
}
```

**Identical structural template for the other 3 subtypes** — only the class name, javadoc text, and `Category.X` value change:
- `TransientGoogleApiException` → `Category.TRANSIENT` (network / 5xx / rate-limit)
- `NotFoundGoogleApiException` → `Category.NOT_FOUND` (404 sheet/calendar ID)
- `PermissionGoogleApiException` → `Category.PERMISSION` (403 access-denied; token valid, resource not shared)

**Pitfall guard** (RESEARCH.md Pitfall 8): every permits subtype MUST be declared `public final class` — Java sealed-class rule requires `final` / `sealed` / `non-sealed` on each permitted subclass. `./mvnw clean test-compile` is the gate.

---

### 3. `GoogleApiExceptionMapper` static-helper class

**Target file:** `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java` (NEW)

**Analog:** **No existing `*Mapper` / `*Util` / `*Support` static-helper class for exception translation in this codebase.** `find src/main/java -name "*Mapper.java" -o -name "*Support.java" -o -name "*Helper.java"` returned 0 hits. This is a fresh-introduction static-helper pattern. The shape is derived from RESEARCH.md § Pattern 2 (which cites the Google API client lib docs directly).

**Imports pattern** (derived; standard JDK + Google client lib already on classpath per pom.xml):
```java
package org.ctc.dataimport.exception;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.security.GeneralSecurityException;
```

**Class signature template** (per RESEARCH.md Pattern 2 + D-06):
```java
package org.ctc.dataimport.exception;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Translates raw {@link IOException} / {@link GeneralSecurityException} thrown by the
 * Google API client into the typed {@link GoogleApiException} hierarchy used by the
 * UX-01 flash UX (Phase 91 D-06). Static-helper class; no state.
 */
public final class GoogleApiExceptionMapper {

    private GoogleApiExceptionMapper() {}

    public static GoogleApiException from(IOException e) {
        if (e instanceof GoogleJsonResponseException gjre) {
            int status = gjre.getStatusCode();
            return switch (status) {
                case 401 -> new AuthGoogleApiException(authMessage(gjre), gjre);
                case 403 -> isAuthReason(gjre)
                        ? new AuthGoogleApiException(authMessage(gjre), gjre)
                        : new PermissionGoogleApiException(permissionMessage(gjre), gjre);
                case 404 -> new NotFoundGoogleApiException(notFoundMessage(gjre), gjre);
                case 408, 429, 500, 502, 503, 504 ->
                        new TransientGoogleApiException(transientMessage(gjre), gjre);
                default -> new TransientGoogleApiException(transientMessage(gjre), gjre);
            };
        }
        return new TransientGoogleApiException(
                "Network problem talking to Google: " + e.getMessage(), e);
    }

    public static AuthGoogleApiException from(GeneralSecurityException e) {
        return new AuthGoogleApiException(
                "Could not authenticate with Google (credentials unreadable): " + e.getMessage(), e);
    }

    private static boolean isAuthReason(GoogleJsonResponseException e) { /* inspect getDetails() */ }
    // ... private message-builder helpers
}
```

**Key conventions** (matching codebase style observed in `GoogleSheetsService.java` + `BackupArchiveException.java`):
- `public final class` + `private` constructor (utility / static-helper idiom)
- No Lombok, no `@Component` (not a Spring bean — pure helper)
- File-level javadoc explains the boundary (controller layer NOT involved)
- Defensive `e instanceof GoogleJsonResponseException gjre` pattern (Java 21+ pattern matching)
- `switch` expression over status codes (Java 21+ exhaustive arrow syntax)
- Default case maps to `TransientGoogleApiException` (conservative — RESEARCH.md A1 risk note: low because retry is the safer remediation hint)

---

### 4. Refactored `GoogleSheetsService` synchronized client-builder

**Target file:** `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` (modified)

**BEFORE pattern** (existing code at `GoogleSheetsService.java:157-181`):
```java
private synchronized Sheets getSheetsClient() throws IOException {
    if (sheetsClient == null) {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "Google Sheets credentials not configured or file not found");
        }
        try (var credentialsStream = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(credentialsStream)
                    .createScoped(SheetsScopes.SPREADSHEETS_READONLY);

            sheetsClient = new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            log.info("Google Sheets API client initialized");
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to initialize Google Sheets API client", e);
        }
    }
    return sheetsClient;
}
```

**REFACTOR target** (per D-06 + RESEARCH.md A6):
- Line 176-178 `catch (GeneralSecurityException e) { throw new IOException(...); }` → `throw GoogleApiExceptionMapper.from(e);` (which returns `AuthGoogleApiException` per Pattern 2 contract).
- Method signature `throws IOException` is preserved (no caller breaks; `GoogleApiException extends IOException`).
- Line 160 `throw new IllegalStateException(...)` is LEFT UNCHANGED — RESEARCH.md Pitfall 5: availability-check failure is NOT a Google API error.
- Line 163-173 (the `Sheets.Builder` chain) is unchanged.

**3 call-site refactors at the public API surface** (`readRange:66`, `readRangeFromSheet:83`, `getSheetNames:94`): these methods already `throws IOException`. After Plan 91-02, the typed `GoogleApiException` subtypes propagate up unchanged (because `GoogleApiException extends IOException`). The HTTP-layer mapping happens at the **first catch-frame inside the Google client** — but Google client itself throws `GoogleJsonResponseException extends IOException`, so the mapper invocation must wrap each `execute()` call. Recommendation (RESEARCH.md Pattern 2): wrap inside `readRange` like:
```java
public List<List<Object>> readRange(String spreadsheetId, String range) throws IOException {
    var client = getSheetsClient();
    try {
        ValueRange response = client.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        List<List<Object>> values = response.getValues();
        return values != null ? values : List.of();
    } catch (IOException e) {
        throw GoogleApiExceptionMapper.from(e);
    }
}
```
Same wrap shape for `getSheetNames` (line 94-100). `readRangeFromSheet` (line 83-86) delegates to `readRange` — already covered.

---

### 5. Refactored `GoogleCalendarService` synchronized client-builder

**Target file:** `src/main/java/org/ctc/dataimport/GoogleCalendarService.java` (modified)

**BEFORE pattern** (existing code at `GoogleCalendarService.java:95-119`):
```java
private synchronized Calendar getCalendarClient() throws IOException {
    if (calendarClient == null) {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "Google Calendar credentials not configured or calendar ID missing");
        }
        try (var credentialsStream = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(credentialsStream)
                    .createScoped(CalendarScopes.CALENDAR_EVENTS);

            calendarClient = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            log.info("Google Calendar API client initialized");
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to initialize Google Calendar API client", e);
        }
    }
    return calendarClient;
}
```

**REFACTOR target** — identical pattern to #4 above:
- Line 114-116 `catch (GeneralSecurityException e) { throw new IOException(...); }` → `throw GoogleApiExceptionMapper.from(e);`
- Line 98-100 `throw new IllegalStateException(...)` unchanged (availability check).
- `createEvent` (line 61-68) + `updateEvent` (line 70-76) public methods: wrap the `client.events().insert(...).execute()` / `.update(...).execute()` call in `try { ... } catch (IOException e) { throw GoogleApiExceptionMapper.from(e); }`.

---

### 6. `DriverSheetImportController#preview` flash-attribute set site

**Target file:** `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` (modified)

**BEFORE pattern** (existing code at `DriverSheetImportController.java:46-66`):
```java
try {
    var preview = driverSheetImportService.preview(sheetUrl);
    model.addAttribute("preview", preview);
    model.addAttribute("sheetUrl", sheetUrl);
    model.addAttribute("hasAmbiguousTabs", preview.tabPreviews().stream()
            .anyMatch(t -> t.suggestedSeasonId() == null));
    model.addAttribute("showGroupColumn", preview.tabPreviews().stream()
            .anyMatch(TabPreview::usesGroups));
    addCommonAttributes(model);
    return "admin/driver-import-preview";
} catch (IOException e) {
    log.error("Error reading Google Sheet for driver import", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Could not read the Google Sheet. Check the URL and service account credentials.");
    return "admin/driver-import";
} catch (IllegalArgumentException | IllegalStateException e) {
    log.error("Driver import preview failed", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Preview failed: " + e.getMessage());
    return "admin/driver-import";
}
```

**REFACTOR target** (per RESEARCH.md Pattern 3 + D-07):
- Replace the single `catch (IOException e)` block (lines 56-60) with 4 typed catches (most-specific first).
- LEAVE the `catch (IllegalArgumentException | IllegalStateException e)` block (lines 61-65) UNCHANGED — RESEARCH.md Pitfall 4 + Pitfall 5: those are client-input + availability errors, not Google API errors.
- The `preview` method uses `model.addAttribute(...)` (not `redirectAttributes.addFlashAttribute(...)`) because the form-render path returns to the same view without redirect. This is correct — D-07 says "controllers add flash attributes + redirect"; preview does NOT redirect, so model is the right surface.

**Existing execute flash-attribute pattern** (`DriverSheetImportController.java:102-112` — Plan 91-02 also touches this method, but the flash pattern here is the verbatim reference):
```java
} catch (BusinessRuleException | ValidationException | IllegalArgumentException e) {
    log.error("Error executing driver sheet import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
} catch (DataIntegrityViolationException e) {
    log.error("Driver sheet import hit DB constraint — transaction rolled back, no rows inserted", e);
    redirectAttributes.addFlashAttribute("errorMessage",
            "Import failed due to a database constraint. Nothing was imported. See server logs for details.");
} catch (IllegalStateException | DataAccessException e) {
    log.error("Error executing driver sheet import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import failed due to an internal error. See server logs for details.");
}
return "redirect:/admin/drivers/import";
```

**Plan 91-02 extends this with `errorCategory` flash attribute** (per D-07):
```java
} catch (AuthGoogleApiException e) {
    log.error("Google Sheets authentication failed", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
    redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
    return "redirect:/admin/drivers/import";
}
```

**Pitfall 6 (RESEARCH.md):** `DriverSheetImportService.execute()` line ~108 currently wraps `IOException` in `IllegalStateException`. Plan 91-02 must either (a) refactor `execute()` to declare `throws IOException` and propagate, OR (b) unwrap `.getCause()` and instanceof-check in the controller catch. RESEARCH recommendation (a) — clean signature propagation; `@Transactional` allows checked exceptions.

---

### 7. `RaceController#createCalendarEvent` catch site at line 192-194

**Target file:** `src/main/java/org/ctc/admin/controller/RaceController.java` (modified)

**BEFORE pattern** (existing code at `RaceController.java:187-196`):
```java
@PostMapping("/{id}/create-calendar-event")
public String createCalendarEvent(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        raceCalendarService.createOrUpdateCalendarEvent(id);
        redirectAttributes.addFlashAttribute("successMessage", "Calendar event saved");
    } catch (IOException | IllegalStateException e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Calendar: " + e.getMessage());
    }
    return "redirect:/admin/races/" + id;
}
```

**REFACTOR target** (per RESEARCH.md Pattern 3 + D-07 + D-08):
- Replace the combined `catch (IOException | IllegalStateException e)` (line 192-194) with 5 catches: 4 typed `GoogleApiException` subtypes + 1 `IllegalStateException` (kept for availability/duration-config errors at `RaceCalendarService:32,35,38,43` per Pitfall 5).
- Each typed catch sets BOTH `errorMessage` (hardcoded user-visible string per RESEARCH.md threat-pattern: do NOT echo `e.getMessage()` from Google API responses) AND `errorCategory` String literal flash attribute.
- The existing `"Calendar: " + e.getMessage()` prefix pattern is REPLACED by category-specific hardcoded strings per D-07 + RESEARCH.md security threat-pattern (service-account JSON key disclosure via error message).

**After refactor sketch** (5 catches; pattern mirrors #6):
```java
} catch (AuthGoogleApiException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
    redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
} catch (NotFoundGoogleApiException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Calendar not found — check ID");
    redirectAttributes.addFlashAttribute("errorCategory", "NOT_FOUND");
} catch (PermissionGoogleApiException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Access denied — share the calendar with the service account");
    redirectAttributes.addFlashAttribute("errorCategory", "PERMISSION");
} catch (TransientGoogleApiException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Connection problem — retry");
    redirectAttributes.addFlashAttribute("errorCategory", "TRANSIENT");
} catch (IllegalStateException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Calendar: " + e.getMessage());
    // No errorCategory — this is an availability/duration-config error, not a Google API error.
}
```

**D-08 audit gate** (RESEARCH.md "Audit of RaceCalendarService consumers" + Open Question #1): Plan 91-02 Wave-2 Task 1 must read `RaceService.java` to identify whether the `raceCalendarService` field at `RaceService.java:32` has additional non-user-trigger call sites. If yes, those callers wrap in try/catch with WARN log + swallow (NO flash UX surface). If the field is unused (dead code), remove it.

---

### 8. `.error-badge` CSS pattern

**Target file:** `src/main/resources/static/admin/css/admin.css` (append after line 358, after the existing `.badge*` rules)

**Closest analog 1 — `.alert` BEM-ish modifier block** (existing code at `admin.css:153-163`):
```css
.alert {
    padding: 12px 16px;
    border-radius: var(--radius-sm);
    margin-bottom: 16px;
    font-size: 14px;
}
.alert-success { background: var(--success-bg); color: #66bb6a; border: 1px solid #2e7d32; }
.alert-error { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
.alert-warning { background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00; }
.alert-warning ul { margin: 6px 0 0 18px; padding: 0; }
.alert-warning li { margin: 2px 0; }
```

**Closest analog 2 — `.badge` BEM-ish modifier block** (existing code at `admin.css:346-357`):
```css
.badge {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    border-radius: var(--radius-lg);
    font-size: 12px;
    font-weight: 600;
}
.badge-active { background: var(--success-bg); color: #66bb6a; }
.badge-inactive { background: #222; color: #999; }
.badge-warning { background: #3b2e0e; color: #ffb74d; }
```

**Pattern conventions to follow** (extracted from analogs):
- Base class block first (`.alert {…}` / `.badge {…}`), then single-line modifier rules.
- Modifier naming: `.alert-success` / `.badge-active` (existing convention is single-dash, NOT double-dash BEM `--`).
- Token usage: `var(--success-bg)`, `var(--danger-bg)`, `var(--radius-sm)`, `var(--radius-lg)` — reuse CSS custom properties (do NOT hardcode colour values where a token exists).
- Each modifier sets `background` + `color` + `border` (border on `.alert-*` only; `.badge-*` uses just background + color).

**Translation to `.error-badge` pattern** (per RESEARCH.md "admin/css/admin.css insertion"):
```css
/* Google API error categories (Phase 91 / UX-01) */
.error-badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: var(--radius-sm);
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-right: 8px;
    vertical-align: middle;
}
.error-badge--transient  { background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00; }
.error-badge--auth       { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
.error-badge--not-found  { background: var(--success-bg); color: #66bb6a; border: 1px solid #2e7d32; }
.error-badge--permission { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
```

**Discretion note** (D-07 § Claude's Discretion): The new BEM modifier shape (`.error-badge--transient`) uses double-dash, **diverging from the codebase's single-dash convention** (`.alert-success`, `.badge-active`). Planner may either (a) stick with double-dash for explicit BEM signalling on a new component (RESEARCH.md default; matches the `error-badge--{lowercase}` Thymeleaf classappend pattern below) OR (b) align with single-dash for codebase consistency (`.error-badge-transient`). The default is (a) — explicit BEM. CLAUDE.md does not lock the convention either way.

**Collision check** (RESEARCH.md): `grep "error-badge" src/main/resources/static/admin/css/admin.css` returns 0 hits — no naming conflict.

**Path correction** (Pitfall 7): The file lives at `src/main/resources/static/admin/css/admin.css` (NOT `src/main/resources/static/admin.css`). CONTEXT.md `<canonical_refs>` line 454 has the wrong path; RESEARCH.md has the corrected path; planner MUST use the corrected path.

---

### 9. Thymeleaf badge fragment insertion point

**Target files:**
- `src/main/resources/templates/admin/layout.html` (line 86 — primary)
- `src/main/resources/templates/admin/driver-import.html` (line 8-10 — secondary)

**Existing flash render at `layout.html:82-86`:**
```html
<div th:if="${importInProgress}" class="alert alert-warning" role="status">
    Backup import in progress — write access is temporarily locked.
</div>
<div th:if="${successMessage}" class="alert alert-success" th:text="${successMessage}"></div>
<div th:if="${errorMessage}" class="alert alert-error" th:text="${errorMessage}"></div>
```

**Existing flash render at `driver-import.html:8-10`:**
```html
<div th:if="${errorMessage}" class="alert alert-error mb-md">
    <p th:text="${errorMessage}"></p>
</div>
```

**REFACTOR target** (per RESEARCH.md "Template insertion shape"):
- `layout.html:86` (single-element flash):
```html
<div th:if="${errorMessage}" class="alert alert-error">
    <span th:if="${errorCategory}"
          class="error-badge"
          th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
          th:text="${errorCategory}"></span>
    <span th:text="${errorMessage}"></span>
</div>
```
- `driver-import.html:8-10` (paragraph-wrapped flash): same insertion shape, wrapped in `<p>` to match existing structure:
```html
<div th:if="${errorMessage}" class="alert alert-error mb-md">
    <p>
        <span th:if="${errorCategory}"
              class="error-badge"
              th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
              th:text="${errorCategory}"></span>
        <span th:text="${errorMessage}"></span>
    </p>
</div>
```

**Conventions** (matches CLAUDE.md § Keep Thymeleaf Templates Lean):
- `th:text` (NOT `th:utext`) — Thymeleaf HTML-escapes by default (RESEARCH.md threat-pattern: XSS-safe).
- `th:classappend` with `|...|` literal substitution operator + `#strings.toLowerCase(...)` — converts `"AUTH"` to `"auth"` for CSS class concatenation.
- Flash key `errorCategory` is always one of 4 hardcoded String literals (`"TRANSIENT"`, `"AUTH"`, `"NOT_FOUND"`, `"PERMISSION"`) set by the controller — no attacker-controlled values.

**`race-detail.html`** does NOT need direct edits — its flash messages flow through `layout.html` via the existing `th:replace="${content}"` mechanism (RESEARCH.md "Template insertion shape").

---

### 10. `docs/operations/google-integration.md` shape analog

**Target file:** `docs/operations/google-integration.md` (NEW)

**Analog 1 — `docs/operations/release-runbook.md`** section sequence (extracted via `grep -n "^##"`):
```
1: # CTC Manager — Release Catch-up Runbook
32: ## Section 1 — Prerequisites
63: ## Section 2 — Retroactive v1.10.0 (master @ `45aabfd0`)
116: ## Section 3 — Retroactive v1.11.0 (master @ `598d1431`)
169: ## Section 4 — Legacy short-form tag cleanup
175: ### 4a. Live-inventory re-verification
195: ### 4b. Per-tag confirmation loop
220: ## Section 5 — Post-runbook verification
243: ## Section 6 — Future-proof releases
```

**Analog 2 — `docs/operations/import-runbook.md`** section sequence:
```
1: # CTC Manager — Backup Import Operational Runbook
12: ## 1. Recovery from auto-backup
43: ## 2. 24h retention semantics
75: ## 3. Audit-id query SQL
117: ## 4. Concurrent-import behavior
140: ## 5. Read-only state during imports
```

**Pattern conventions** (extracted from both analogs):
- Title format: `# CTC Manager — {Topic} Runbook`
- Audience preamble paragraph (1-3 sentences naming `@jegr78` as the operator and stating scope).
- Cross-references block listing relevant code paths and config files (release-runbook.md uses bulleted `[[memory-ref]]` + `CLAUDE.md §` + file paths).
- Numbered `## Section N — Title` (release-runbook uses em-dash; import-runbook uses simple `## N. Title`). RESEARCH.md D-09 + `google-integration.md § Setup / § Error Categories / § Troubleshooting` shape picks the `## Section N — Title` form to align with release-runbook (the more recent runbook).
- Code fences for shell snippets + YAML config + SQL queries.
- Last line: `**Last updated:** YYYY-MM-DD ({Phase} / {ReqID}).`

**Translation to `google-integration.md` (per D-09 + RESEARCH.md `docs/operations/google-integration.md` shape):**
```
# CTC Manager — Google Integration Runbook

[Audience preamble + cross-references]

## Section 1 — Setup
   ### Service account credentials
   ### Verification

## Section 2 — Error Categories
   [4-row table: Category | User-visible message | Root cause | Operator action]

## Section 3 — Troubleshooting
   ### "Authentication problem" badge appears immediately on every request
   ### "Sheet not found" appears for a sheet I just confirmed exists
   ### "Access denied" but the sheet is "Anyone with the link can view"
   ### "Connection problem" badge persists across multiple retries
   ### Calendar event creation works, but the event has the wrong time zone

**Last updated:** 2026-05-20 (Phase 91 / UX-01).
```

The Error Categories table is the **single source of truth** for the flash `errorMessage` strings: any future change to the controller flash text MUST update this table in the same commit (RESEARCH.md "Update-on-Triage discipline").

---

### 11. `MILESTONES.md` v1.12 entry shape

**Target file:** `.planning/MILESTONES.md` (insert at top, above current line 3)

**Analog — v1.11 entry at `MILESTONES.md:3-44`** (full read in load above):
- Heading: `## v1.11 Tooling Infrastructure & Tech-Debt Sweep (Shipped: 2026-05-18)`
- Metadata block (5 lines):
  - `**Phases completed:** N phases (XX-YY), M plans, K/K requirements satisfied`
  - `**Diff:** +X / −Y across Z files (N commits in milestone range)`
  - `**Tests:** N tests passing ...; JaCoCo line coverage X.XX % (gate 82 %, vN.M baseline B.BB %, +delta pp)`
  - `**Timeline:** N days (YYYY-MM-DD → YYYY-MM-DD)`
  - `**Branch:** `gsd/vN.M-…` (PR #NNN)`
  - `**Final-gate CI:** Run [<id>](<URL>) @ SHA `<sha>` SUCCESS — E2E step mm:ss ≤ <ceiling> ...`
  - `**Audit verdict:** passed (`vN.M-MILESTONE-AUDIT.md`); Nyquist scoreboard compliant K/0/0`
- `**Key accomplishments:**` bulleted list — one bullet per phase, prose-style narrative with REQ-ID groupings (BACK-01..05, QUAL-01..05, etc.).
- `**Deferred to next milestone (acknowledged at close):**` bulleted list.
- `**Post-merge self-resolving (not tech debt):**` bulleted list.
- Trailing line: `Known deferred items at close: see `STATE.md` Deferred Items + `vN.M-MILESTONE-AUDIT.md``
- Section separator: `---`

**Translation to v1.12** (per D-07b + RESEARCH.md "MILESTONES.md v1.12 entry shape" + Plan 91-03):
```markdown
## v1.12 Driver-Import Gap-Closure & Test Performance Round 2 (Shipped: YYYY-MM-DD)

**Phases completed:** 4 phases (88-91), ~16 plans, 15/15 requirements satisfied (14 must-have + 1 stretch)
**Diff:** +<X> / −<Y> across <Z> files (<N> commits in milestone range)
**Tests:** <count> tests passing; JaCoCo line coverage <X.XX> % (gate 82 %, v1.11 baseline 88.88 %, +<delta>)
**Timeline:** <N> days (2026-05-18 → YYYY-MM-DD)
**Branch:** `gsd/v1.12-driver-import-and-test-perf` (PR #<num>)
**Final-gate CI:** Run [<id>](<URL>) @ SHA `<sha>` SUCCESS — E2E step <mm:ss> ≤ <ceiling>, SpotBugs 0 BugInstance, no Surefire fork-channel corruption
**Audit verdict:** passed (`v1.12-MILESTONE-AUDIT.md`); Nyquist scoreboard compliant 4/0/0

**Key accomplishments:**
- Phase 88 (...)
- Phase 89 (...)
- Phase 90 (...)
- Phase 91 (PERF-06 + UX-01 + Closer) ...
- JaCoCo / SpotBugs / CodeQL / EXPORT_ORDER summary line

**Deferred to next milestone (acknowledged at close):**
- Test-module-split (...)
- ...

**Post-merge self-resolving (not tech debt):**
- v1.12 milestone PR squash-merge to master (CI release workflow handles v1.12.0 tag)

Known deferred items at close: see `STATE.md` Deferred Items + `v1.12-MILESTONE-AUDIT.md`

---
```

**Per-phase REQ-ID summary convention** (extracted from v1.11 entry bullets): each phase bullet bundles its REQ-IDs as parenthetical suffix. Example from v1.11:
```
Phase 87 v1.10 Nyquist VALIDATION closure: ... (VAL-01..04)
```
For v1.12 Phase 91, the bullet ends with `(PERF-06, UX-01, Closer)`.

---

### 12. v1.12 milestone PR body D-07b composite shape

**Target operation:** `gh pr edit <pr-num> --body-file <tempfile>` (final composite body finalized in Plan 91-03)

**Analog — v1.11 PR #122 body** (fetched via `gh pr view 122 --json body --jq '.body'`; section sequence extracted):

```
1. Status line (top): "## Status: Ready to merge — N of M phases complete, milestone audit `passed`, all operator actions done"

2. Opening paragraph (1 paragraph): names the milestone, references the final-gate CI run + SHA, mentions inline closure activity, names master branch protection state.

3. Phase summary table:
   | Phase | Status | What it ships |
   | -----: | ------ | ------------- |
   (one row per phase, "✅ approved · nyquist" status pattern)

4. Per-phase deep narrative sections (## headings):
   ## Phase 87 — v1.10 Nyquist VALIDATION Closure
   (narrative + per-plan outcome sub-table)
   ## Milestone Audit + Inline Nyquist Closure (date, post-Phase-N)
   ## CI Stabilization (post-Phase-N, pre-closure)

5. Verification Numbers table:
   | Check | Value | Notes |
   (rows: ./mvnw verify exit, JaCoCo, total tests, SpotBugs, CodeQL gate, CI wallclock, milestone Nyquist scoreboard, requirements count, milestone audit status)

6. CLAUDE.md Updates (cumulative across milestone):
   - bullet list of new sections added to CLAUDE.md across the milestone

7. Test plan checklist:
   - [x] checklist of verification activities done

8. Notes for Reviewer (1-2 paragraphs):
   - explains branch-protection state, large-diff explanation, key technical risks
```

**Translation to v1.12 PR body** (per D-07b + RESEARCH.md "v1.12 milestone PR body composite shape"):

For Phase 91 specifically the body MUST also include (per D-07b + RESEARCH.md):
- **15-row REQ-ID master table** (CLEAN-01..03, DOCS-01, DRIV-01..02, REL-01..02, PERF-01..06, UX-01) with columns: REQ-ID | Phase | Status | Plan(s) | Acceptance evidence (commit / file ref). Sourced from `.planning/REQUIREMENTS.md` Traceability section (lines 88-104).
- **CI run links section** — 5 `workflow_dispatch` run URLs from PERF-06 + the auto-triggered `pull_request` event run that landed when the Draft PR opened.
- **CI baseline-comparison block** — v1.11 23:00 → v1.12 mm:ss (ΔX %) — replaces the standalone "CI wallclock (E2E step)" row.

**Planner sequencing for Plan 91-03 final body composition** (RESEARCH.md "Plan 91-03 Closer Mechanics" steps 1-5):
1. `gh pr view <num> --json body` — confirm current rolling body state.
2. Pull JaCoCo / SpotBugs / CodeQL / E2E-step final numbers from the latest CI run on the PR HEAD.
3. Pull the 15-row REQ-ID mapping table from `.planning/REQUIREMENTS.md` Traceability section.
4. Compose the body in a tempfile, then `gh pr edit <num> --body-file <tempfile>`.
5. Flip Draft → Ready-for-review via `gh pr ready <num>`.

---

## Shared Patterns

### Sealed-class exception hierarchy

**Source:** RESEARCH.md Pattern 1 + Java 25 native syntax (no codebase precedent — see "No Analog Found" below).

**Apply to:** All 5 new classes in `src/main/java/org/ctc/dataimport/exception/` (1 base + 4 permits).

**Concrete excerpt** (from #1 above):
```java
public abstract sealed class GoogleApiException extends IOException
        permits TransientGoogleApiException, AuthGoogleApiException,
                NotFoundGoogleApiException, PermissionGoogleApiException {
    public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION }
    protected GoogleApiException(String message, Throwable cause) { super(message, cause); }
    public abstract Category category();
}
```

**Pitfall guard:** Every permits subtype MUST be declared `public final class` (RESEARCH.md Pitfall 8). `./mvnw clean test-compile` is the gate (CLAUDE.md `[[clean-maven-build-authority]]`).

---

### Flash-attribute redirect pattern with `errorCategory`

**Source:** Existing `DriverSheetImportController.java:102-112` (verbatim reference) + D-07 extension with new `errorCategory` key.

**Apply to:** All controller catch blocks for `GoogleApiException` subtypes in:
- `DriverSheetImportController#preview` (line 56-60 — `model.addAttribute(...)`)
- `DriverSheetImportController#execute` (line 102-112 — `redirectAttributes.addFlashAttribute(...)`)
- `RaceController#createCalendarEvent` (line 192-194)

**Concrete excerpt** (extension over the existing pattern):
```java
} catch (AuthGoogleApiException e) {
    log.error("Google Sheets authentication failed", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
    redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
}
```

**Convention:**
- `errorMessage` is a **hardcoded user-visible String literal** per category (NOT `e.getMessage()` — RESEARCH.md security threat-pattern: service-account JSON key disclosure).
- `errorCategory` is a String literal (`"AUTH"`, `"TRANSIENT"`, `"NOT_FOUND"`, `"PERMISSION"`). RESEARCH.md Open Question #2 + Discretion: planner may use `e.category().name()` for compile-time safety; both render identically in Thymeleaf.
- `log.error(..., e)` for AUTH / NOT_FOUND / PERMISSION (root-cause-of-interest); `log.warn(..., e)` for TRANSIENT (retry-expected).
- `IllegalArgumentException` / `IllegalStateException` catches stay UNCHANGED (Pitfall 4 + 5).

---

### Thymeleaf badge insertion

**Source:** RESEARCH.md "Template insertion shape" — extends existing `layout.html:86` + `driver-import.html:8-10` flash render block.

**Apply to:** `layout.html` and `driver-import.html` (NOT `race-detail.html` — flows through layout).

**Concrete excerpt:**
```html
<div th:if="${errorMessage}" class="alert alert-error">
    <span th:if="${errorCategory}"
          class="error-badge"
          th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
          th:text="${errorCategory}"></span>
    <span th:text="${errorMessage}"></span>
</div>
```

**Convention:**
- `th:text` (HTML-escape safe) — never `th:utext`.
- `th:classappend` with `|...|` literal-substitution operator.
- `#strings.toLowerCase(errorCategory)` produces `"auth"` / `"transient"` / `"not_found"` / `"permission"`. NOTE: `"not_found"` (with underscore) — the BEM modifier class is `.error-badge--not-found` (with hyphen). Either (a) replace the underscore with a hyphen in the controller flash value (`addFlashAttribute("errorCategory", "NOT-FOUND")` or via enum mapping), or (b) use a Thymeleaf utility like `#strings.replace(...,'_','-')` in the `th:classappend` expression. Default per RESEARCH: use the kebab-case in the flash key directly (controller sets `"NOT-FOUND"` upper-case-kebab) OR define the CSS class as `.error-badge--not_found` (underscore-aligned). Planner picks; the safer option is to set the flash value as `"NOT_FOUND"` for the badge text + use `#strings.replace(#strings.toLowerCase(errorCategory),'_','-')` in `th:classappend` for the kebab-case CSS class.

---

### Operations runbook shape

**Source:** `docs/operations/release-runbook.md` + `docs/operations/import-runbook.md` (full reads in load above).

**Apply to:** `docs/operations/google-integration.md` (per D-09).

**Concrete excerpt** — section header sequence from release-runbook:
```
# CTC Manager — Release Catch-up Runbook
## Section 1 — Prerequisites
## Section 2 — ...
## Section N — Post-runbook verification
```

**Convention:**
- Title format `# CTC Manager — {Topic} Runbook` (em-dash, not hyphen).
- Audience paragraph immediately after title naming `@jegr78`.
- Cross-references bullet list (memory references + CLAUDE.md sections + file paths).
- `## Section N — Title` sequential numbering with em-dash.
- Trailing `**Last updated:** YYYY-MM-DD (Phase XX / REQ-ID).` line.

---

### `gh` CLI workflow_dispatch + run-watch + pr-edit pattern

**Source:** `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-06-PLAN.md` Tasks 1-2 (verbatim 5-run harvest template).

**Apply to:** Plan 91-01 (5-run PERF-06 harvest) + Plan 91-01 + 91-02 + 91-03 (rolling PR-body edits + final ready-flip).

**Concrete excerpt** (RESEARCH.md "Phase 86 PERF-05 harvest commands"):
```bash
BRANCH=gsd/v1.12-driver-import-and-test-perf

for i in 1 2 3 4 5; do
    gh workflow run ci.yml --ref "$BRANCH"
    sleep 5
    RUN_ID=$(gh run list --workflow=ci.yml --branch="$BRANCH" --event=workflow_dispatch \
        --limit=1 --json databaseId --jq '.[0].databaseId')
    gh run watch "$RUN_ID" --exit-status
done
```

**Convention** (RESEARCH.md Pitfall 1):
- Serial `gh run watch <id> --exit-status` between triggers — MANDATORY because `ci.yml on.concurrency.cancel-in-progress: true` would otherwise kill earlier runs.
- `--event=workflow_dispatch` filter on `gh run list` excludes the auto-triggered `pull_request` event run (the PR-open trigger).
- D-17 trigger-equivalence: PR-branch `workflow_dispatch` ≡ post-merge master CI because `ci.yml` runs identical steps regardless of trigger.

---

## No Analog Found

Files with no close existing analog in the codebase (planner uses RESEARCH.md patterns instead of an existing-file template):

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `GoogleApiException.java` (sealed base) | exception (sealed) | typed throw-carrier | No existing `sealed`/`permits` use in `src/main/java/org/ctc/**` (RESEARCH.md verified via grep: 0 hits). `BackupArchiveException` (enum-Reason RuntimeException) is the structural alternative — D-06 EXPLICITLY picks sealed over enum-Reason for compile-time exhaustive catch typing. Pattern source: RESEARCH.md § Pattern 1 + Java 25 JEP 409 native syntax. |
| `{Transient,Auth,NotFound,Permission}GoogleApiException.java` (final permits) | exception (final permits) | typed throw-carrier | Derived from sealed base; no codebase precedent for permits subtypes. Pattern source: RESEARCH.md § Pattern 1. |
| `GoogleApiExceptionMapper.java` (static helper) | utility (static helper) | transform (raw → typed) | No existing `*Mapper` / `*Util` / `*Support` / `*Helper` static-helper class for exception translation in this codebase (`find` returned 0 hits). The sealed-class `switch` expression over `GoogleJsonResponseException.getStatusCode()` is a fresh pattern. Pattern source: RESEARCH.md § Pattern 2 + Google API client lib official docs. |

**Note on CONTEXT.md inaccuracy** (RESEARCH.md Pitfall 9): CONTEXT.md `<code_context>` § Established Patterns claims "Sealed exception hierarchies on Java 25 — v1.10 Backup phases already used this idiom." This is **incorrect** — the backup hierarchy uses `RuntimeException + enum Reason` (verified via Read above). The planner should treat D-06 as authoritative and ignore the inaccurate precedent framing.

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/dataimport/**` — Google service refactor targets
- `src/main/java/org/ctc/backup/exception/**` — typed-exception precedent (BackupArchiveException, BackupImportException)
- `src/main/java/org/ctc/admin/controller/**` — controller catch-translate patterns
- `src/main/java/org/ctc/domain/service/RaceCalendarService.java` — calendar-sync consumer
- `src/main/resources/templates/admin/{layout.html, driver-import.html}` — flash render insertion points
- `src/main/resources/static/admin/css/admin.css` — `.alert*` + `.badge*` BEM-ish modifier blocks
- `docs/operations/{release-runbook.md, import-runbook.md}` — runbook shape templates
- `.planning/MILESTONES.md` lines 1-50 — v1.11 entry shape
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-06-PLAN.md` (via RESEARCH.md citation) — PERF-05 harvest template
- v1.11 PR #122 body via `gh pr view 122 --json body --jq '.body'` — D-07b composite template

**Files scanned:** ~15 source files + 3 docs files + 2 MILESTONES.md/PR-body references = 20 reference artifacts

**Pattern extraction date:** 2026-05-20
