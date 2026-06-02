# Phase 111: Log-Injection Remediation (CodeQL CWE-117) - Pattern Map

**Mapped:** 2026-05-31
**Files analyzed:** 21 (2 new + 17 modified call-site files + 2 conditional CI files)
**Analogs found:** 21 / 21

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/util/LogSanitizer.java` | utility | transform | `src/main/java/org/ctc/domain/util/HexColor.java` | exact |
| `src/test/java/org/ctc/util/LogSanitizerTest.java` | test | transform | `src/test/java/org/ctc/domain/util/HexColorTest.java` | exact |
| `src/main/java/org/ctc/admin/controller/DriverController.java` | controller | request-response | self (line 80) | call-site mod |
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` | controller | request-response | self (line 115) | call-site mod |
| `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` | controller | request-response | self (lines 106, 146) | call-site mod |
| `src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` | middleware | request-response | self (line 39) | call-site mod |
| `src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java` | middleware | request-response | self (line 71) | call-site mod |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` | service | file-I/O | self (line 318) | call-site mod |
| `src/main/java/org/ctc/dataimport/CsvImportService.java` | service | batch | self (lines 194, 398, 415, 419) | call-site mod |
| `src/main/java/org/ctc/dataimport/DriverMatchingService.java` | service | transform | self (lines 33, 40, 47, 56, 62) | call-site mod |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` | service | request-response | self (lines 72, 104) | call-site mod |
| `src/main/java/org/ctc/domain/service/FileStorageService.java` | service | file-I/O | self (line 176) | call-site mod |
| `src/main/java/org/ctc/domain/service/MatchdayService.java` | service | CRUD | self (lines 106, 118) | call-site mod |
| `src/main/java/org/ctc/domain/service/PlayoffService.java` | service | CRUD | self (line 121) | call-site mod |
| `src/main/java/org/ctc/domain/service/ScoringService.java` | service | transform | self (line 40) | call-site mod |
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java` | service | CRUD | self (lines 188, 198) | call-site mod |
| `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` | service | CRUD | self (lines 275, 290) | call-site mod |
| `src/main/java/org/ctc/domain/service/StandingsViewService.java` | service | request-response | self (line 71) | call-site mod |
| `src/main/java/org/ctc/domain/service/TeamManagementService.java` | service | CRUD | self (line 161) | call-site mod |
| `.github/codeql/ctc-model-pack/qlpack.yml` | config | — | `.github/codeql/codeql-config.yml` | role-match |
| `.github/codeql/ctc-model-pack/models/LogSanitizer.yml` | config | — | `.github/codeql/codeql-config.yml` | role-match |
| `.github/workflows/codeql.yml` | config | — | self | call-site mod |

---

## Pattern Assignments

### `src/main/java/org/ctc/util/LogSanitizer.java` (utility, transform)

**Analog:** `src/main/java/org/ctc/domain/util/HexColor.java`

**Class shape** (HexColor.java lines 1–20 — complete file):
```java
package org.ctc.domain.util;

import static org.springframework.util.StringUtils.hasText;

import java.util.regex.Pattern;

public final class HexColor {

    private static final Pattern PATTERN = Pattern.compile("^#[0-9a-fA-F]{3,8}$");

    private HexColor() {}

    public static String sanitize(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }
}
```

**Pattern to copy:**
- `public final class` with `private` no-arg constructor
- Single public static method returning `String`
- No Lombok (no `@Slf4j`, no `@RequiredArgsConstructor`)
- No Spring dependencies — pure JDK
- Package: `org.ctc.util` (new neutral package, not `org.ctc.domain.util`)
- No file-header comment; no Javadoc on the method (rule: comment only non-obvious WHY)

**One allowed WHY-comment** (regex choice is non-obvious per CLAUDE.md): inline on the `replaceAll("\\R", "_")` line explaining CodeQL barrier recognition. No other comments.

---

### `src/test/java/org/ctc/util/LogSanitizerTest.java` (test, transform)

**Analog:** `src/test/java/org/ctc/domain/util/HexColorTest.java`

**Test structure** (HexColorTest.java lines 1–46 — complete file):
```java
package org.ctc.domain.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HexColorTest {

    @Test
    void givenNull_whenSanitize_thenReturnsNull() {
        assertThat(HexColor.sanitize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#fff", "#FFF", "#ffffff", "#FFFFFF", "#ff00aa80", "#1A2B3C4D"})
    void givenValidHex_whenSanitize_thenReturnsTrimmedValue(String input) {
        assertThat(HexColor.sanitize(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fff", "#xyz", "#ff", "#fffffffff", "#fff;color:red", ...})
    void givenInvalidOrInjectionPayload_whenSanitize_thenReturnsNull(String input) {
        assertThat(HexColor.sanitize(input)).isNull();
    }
}
```

**Pattern to copy:**
- No `@Tag` annotation — plain unit test, runs in Surefire (not Failsafe)
- No `@SpringBootTest` or Spring context
- Package-private class (`class LogSanitizerTest`, not `public class`)
- Imports: `static org.assertj.core.api.Assertions.assertThat`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.ValueSource`
- Method names: Given-When-Then (`givenNull_whenSanitize_thenReturnsLiteralNull`)
- AssertJ `assertThat(...)` for all assertions

---

## Shared Pattern: Static Import at Call Sites

All 17 modified files gain exactly one static import line and wrap tainted arguments inline. The import goes after any existing static imports (or as the first static import block if none exist), before regular imports — following standard Java import ordering seen across the codebase.

**Import line to add to every modified file:**
```java
import static org.ctc.util.LogSanitizer.sanitize;
```

**Wrap pattern:** only the user-controlled `{}` argument is wrapped; format strings and safe args (counts, UUIDs derived before taint, enum values) are left unwrapped.

---

## Call-Site Excerpts (Before → After)

### Alert 1 — `admin/controller/DriverController.java:80`
**Tainted arg:** `driverForm.getPsnId()`

Before (line 80):
```java
log.info("Saved driver: {}", driverForm.getPsnId());
```
After:
```java
log.info("Saved driver: {}", sanitize(driverForm.getPsnId()));
```

---

### Alert 2 — `admin/controller/DriverSheetImportController.java:115`
**Tainted arg:** `sheetUrl` (sibling counts `seasonKeys`/`acceptKeys`/`skipKeys` are `long` — not user-controlled strings, leave unwrapped)

Before (lines 115–116):
```java
log.info("Driver sheet execute: sheetUrl={}, {} seasonId keys, {} accept keys, {} skip keys",
        sheetUrl, seasonKeys, acceptKeys, skipKeys);
```
After:
```java
log.info("Driver sheet execute: sheetUrl={}, {} seasonId keys, {} accept keys, {} skip keys",
        sanitize(sheetUrl), seasonKeys, acceptKeys, skipKeys);
```

---

### Alert 3 — `admin/controller/TemplateEditorController.java:106`
**Tainted arg:** `templateType` (`@PathVariable`)

Before (line 106):
```java
log.warn("Blocked unsafe template save for type {}: {}", templateType, e.getMessage());
```
After:
```java
log.warn("Blocked unsafe template save for type {}: {}", sanitize(templateType), e.getMessage());
```

---

### Alert 4 — `admin/controller/TemplateEditorController.java:146`
**Tainted arg:** `e.getMessage()` (exception from user-supplied template content)

Before (line 146):
```java
log.warn("Blocked unsafe template preview: {}", e.getMessage());
```
After:
```java
log.warn("Blocked unsafe template preview: {}", sanitize(e.getMessage()));
```

---

### Alert 5 — `backup/exception/BackupUploadExceptionHandler.java:39`
**Tainted arg:** `request.getRequestURI()` (sibling `ex.getMaxUploadSize()` is `long` — safe, leave unwrapped)

Before (lines 38–39):
```java
log.warn("Multipart upload rejected: max-size exceeded — uri={}, limit={}",
        request.getRequestURI(), ex.getMaxUploadSize());
```
After:
```java
log.warn("Multipart upload rejected: max-size exceeded — uri={}, limit={}",
        sanitize(request.getRequestURI()), ex.getMaxUploadSize());
```

---

### Alert 6 — `backup/lock/ImportLockedWriteRejector.java:71`
**Tainted arg:** `req.getRequestURI()` (sibling `req.getMethod()` is an HTTP method string — technically safe, but it is also user-influenced; wrap both as per D-06 sibling rule)

Before (line 71):
```java
log.info("Rejected admin POST during import lock: {} {}", req.getMethod(), req.getRequestURI());
```
After:
```java
log.info("Rejected admin POST during import lock: {} {}", sanitize(req.getMethod()), sanitize(req.getRequestURI()));
```

---

### Alert 7 — `backup/service/BackupImportService.java:318`
**Tainted arg:** `file.getOriginalFilename()` (sibling `file.getSize()` is `long` — safe)

Before (lines 317–318):
```java
log.info("Backup import staging started: originalFilename={}, sizeBytes={}",
        file.getOriginalFilename(), file.getSize());
```
After:
```java
log.info("Backup import staging started: originalFilename={}, sizeBytes={}",
        sanitize(file.getOriginalFilename()), file.getSize());
```

---

### Alert 8 — `dataimport/CsvImportService.java:194`
**Tainted args (3):** `homeTeam.getShortName()`, `effectiveAwayTeam.getShortName()`, `matchday.getLabel()` — wrap all three (D-06)

Before (lines 194–195):
```java
log.info("Overwriting existing match: {} vs {} on {}",
        homeTeam.getShortName(), effectiveAwayTeam.getShortName(), matchday.getLabel());
```
After:
```java
log.info("Overwriting existing match: {} vs {} on {}",
        sanitize(homeTeam.getShortName()), sanitize(effectiveAwayTeam.getShortName()), sanitize(matchday.getLabel()));
```

---

### Alert 9 — `dataimport/CsvImportService.java:398`
**Tainted arg:** `row.psnId()`

Before (line 398):
```java
log.info("Created new driver: {}", row.psnId());
```
After:
```java
log.info("Created new driver: {}", sanitize(row.psnId()));
```

---

### Alert 10 — `dataimport/CsvImportService.java:415`
**Tainted args (3):** `driver.getPsnId()`, `teamShortName`, `season.getName()` — wrap all three (D-06)

Before (line 415):
```java
log.debug("Created SeasonDriver: {} -> {} ({})", driver.getPsnId(), teamShortName, season.getName());
```
After:
```java
log.debug("Created SeasonDriver: {} -> {} ({})", sanitize(driver.getPsnId()), sanitize(teamShortName), sanitize(season.getName()));
```

---

### Alert 11 — `dataimport/CsvImportService.java:419`
**Tainted args (3):** same three args on the `Updated SeasonDriver` path — wrap all three (D-06)

Before (line 419):
```java
log.debug("Updated SeasonDriver: {} -> {} ({})", driver.getPsnId(), teamShortName, season.getName());
```
After:
```java
log.debug("Updated SeasonDriver: {} -> {} ({})", sanitize(driver.getPsnId()), sanitize(teamShortName), sanitize(season.getName()));
```

---

### Alert 12 — `dataimport/DriverMatchingService.java:33`
**Tainted args (2):** `searchTerm` (primary), `exact.get().getPsnId()` (sibling — D-06)

Before (line 33):
```java
log.debug("Exact match for '{}': {}", searchTerm, exact.get().getPsnId());
```
After:
```java
log.debug("Exact match for '{}': {}", sanitize(searchTerm), sanitize(exact.get().getPsnId()));
```

---

### Alert 13 — `dataimport/DriverMatchingService.java:40`
**Tainted args (2):** `searchTerm`, `caseInsensitive.get().getPsnId()` (sibling — D-06)

Before (line 40):
```java
log.debug("Case-insensitive match for '{}': {}", searchTerm, caseInsensitive.get().getPsnId());
```
After:
```java
log.debug("Case-insensitive match for '{}': {}", sanitize(searchTerm), sanitize(caseInsensitive.get().getPsnId()));
```

---

### Alert 14 — `dataimport/DriverMatchingService.java:47`
**Tainted args (2):** `searchTerm`, `aliasMatch.get().getPsnId()` (sibling — D-06)

Before (line 47):
```java
log.debug("Alias match for '{}': {}", searchTerm, aliasMatch.get().getPsnId());
```
After:
```java
log.debug("Alias match for '{}': {}", sanitize(searchTerm), sanitize(aliasMatch.get().getPsnId()));
```

---

### Alert 15 — `dataimport/DriverMatchingService.java:56`
**Tainted args (2):** `searchTerm`, `match.driver().getPsnId()` (sibling — D-06; `match.similarity()` is a numeric score — safe)

Before (lines 56–57):
```java
log.debug("Fuzzy match for '{}': {} (similarity: {})",
        searchTerm, match.driver().getPsnId(), match.similarity());
```
After:
```java
log.debug("Fuzzy match for '{}': {} (similarity: {})",
        sanitize(searchTerm), sanitize(match.driver().getPsnId()), match.similarity());
```

---

### Alert 16 — `dataimport/DriverMatchingService.java:62`
**Tainted arg:** `searchTerm`

Before (line 62):
```java
log.debug("No match found for '{}'", searchTerm);
```
After:
```java
log.debug("No match found for '{}'", sanitize(searchTerm));
```

---

### Alert 17 — `dataimport/DriverSheetImportService.java:72`
**Tainted arg:** `spreadsheetId` (derived from user-supplied `sheetUrl` via `extractSpreadsheetId`)

Before (line 72):
```java
log.info("Building driver sheet import preview for spreadsheet {}", spreadsheetId);
```
After:
```java
log.info("Building driver sheet import preview for spreadsheet {}", sanitize(spreadsheetId));
```

---

### Alert 18 — `dataimport/DriverSheetImportService.java:104`
**Tainted arg:** `sheetUrl`

Before (line 104):
```java
log.info("Executing driver sheet import: sheetUrl={}", sheetUrl);
```
After:
```java
log.info("Executing driver sheet import: sheetUrl={}", sanitize(sheetUrl));
```

---

### Alert 19 — `domain/service/FileStorageService.java:176`
**Tainted arg:** `filename` (method param from HTTP request)

Before (line 176):
```java
log.warn("Attempted path traversal in filename: {}", filename);
```
After:
```java
log.warn("Attempted path traversal in filename: {}", sanitize(filename));
```

---

### Alert 20 — `domain/service/MatchdayService.java:106`
**Tainted arg:** `label` (form field from controller)

Before (line 106):
```java
log.info("Saved matchday: {} (season {})", label, seasonId);
```
After:
```java
log.info("Saved matchday: {} (season {})", sanitize(label), seasonId);
```

---

### Alert 21 — `domain/service/MatchdayService.java:118` (SPECIAL CASE)
**Context:** Existing `safeWeekend` variable uses `replaceAll("[\\r\\n]", "_")` — not CodeQL-recognised (confirmed open in live alert API). Must eliminate `safeWeekend` entirely and use `sanitize()` for both `matchday.getLabel()` and `scheduledWeekend`.

**Tainted args (2):** `matchday.getLabel()`, `scheduledWeekend` (stored in `safeWeekend`)

Before (lines 117–119):
```java
String safeWeekend = scheduledWeekend == null ? null : scheduledWeekend.replaceAll("[\\r\\n]", "_");
log.info("Saved matchday pairings: {} (deadline={}, weekend={})",
        matchday.getLabel(), pickDeadline, safeWeekend);
```
After (remove `safeWeekend` variable entirely):
```java
log.info("Saved matchday pairings: {} (deadline={}, weekend={})",
        sanitize(matchday.getLabel()), pickDeadline, sanitize(scheduledWeekend));
```

Note: `matchday.setScheduledWeekend(scheduledWeekend)` on line 115 is left unchanged (D-05 — do not sanitize at entry point).

---

### Alert 22 — `domain/service/PlayoffService.java:121`
**Tainted args (2):** `name` (playoff name from form), `season.getName()` (sibling — D-06)

Before (lines 121–122):
```java
log.info("Created playoff '{}' for season '{}' with {} teams, {} rounds, linked to PLAYOFF phase {}",
        name, season.getName(), numberOfTeams, numRounds, phase.getId());
```
After:
```java
log.info("Created playoff '{}' for season '{}' with {} teams, {} rounds, linked to PLAYOFF phase {}",
        sanitize(name), sanitize(season.getName()), numberOfTeams, numRounds, phase.getId());
```

---

### Alert 23 — `domain/service/ScoringService.java:40`
**Tainted arg:** ternary expression `result.getDriver() != null ? result.getDriver().getPsnId() : "unknown"`. Wrap entire ternary — `sanitize(Object)` handles it transparently.

Before (lines 40–42):
```java
log.debug("Calculated points for driver {}: race={}, quali={}, fl={}, total={}",
        result.getDriver() != null ? result.getDriver().getPsnId() : "unknown",
        rp, qp, fp, result.getPointsTotal());
```
After:
```java
log.debug("Calculated points for driver {}: race={}, quali={}, fl={}, total={}",
        sanitize(result.getDriver() != null ? result.getDriver().getPsnId() : "unknown"),
        rp, qp, fp, result.getPointsTotal());
```

---

### Alert 24 — `domain/service/SeasonManagementService.java:188`
**Tainted arg:** `threadId` (Discord thread ID from form field)

Before (line 188):
```java
log.info("Linked race-results thread {} to season {}", threadId, seasonId);
```
After:
```java
log.info("Linked race-results thread {} to season {}", sanitize(threadId), seasonId);
```

---

### Alert 25 — `domain/service/SeasonManagementService.java:198`
**Tainted arg:** `threadId` (second `linkStandingsThread` method)

Before (line 198):
```java
log.info("Linked standings thread {} to season {}", threadId, seasonId);
```
After:
```java
log.info("Linked standings thread {} to season {}", sanitize(threadId), seasonId);
```

---

### Alert 26 — `domain/service/SeasonPhaseService.java:275`
**Tainted arg:** `name` (group name from form)

Before (line 275):
```java
log.info("Created group '{}' (sortIndex={}) for phase {}", name, sortIndex, phaseId);
```
After:
```java
log.info("Created group '{}' (sortIndex={}) for phase {}", sanitize(name), sortIndex, phaseId);
```

---

### Alert 27 — `domain/service/SeasonPhaseService.java:290`
**Tainted arg:** `name` (updateGroup)

Before (line 290):
```java
log.info("Updated group {} ({})", groupId, name);
```
After:
```java
log.info("Updated group {} ({})", groupId, sanitize(name));
```

---

### Alert 28 — `domain/service/StandingsViewService.java:71`
**Tainted arg:** `seasonId` (UUID string from `@RequestParam` before `UUID.fromString` conversion — CodeQL tracks this as tainted)

Before (line 71):
```java
log.debug("Invalid season ID format: {}", seasonId);
```
After:
```java
log.debug("Invalid season ID format: {}", sanitize(seasonId));
```

---

### Alert 29 — `domain/service/TeamManagementService.java:161`
**Tainted args (2):** `parent.getShortName()` (primary), `sub.getShortName()` (sibling — D-06)

Before (line 161):
```java
log.info("Propagated colors from {} to {}", parent.getShortName(), sub.getShortName());
```
After:
```java
log.info("Propagated colors from {} to {}", sanitize(parent.getShortName()), sanitize(sub.getShortName()));
```

---

## Conditional: CodeQL Model Pack Files

### `.github/codeql/ctc-model-pack/qlpack.yml` (config)

**Analog:** `.github/codeql/codeql-config.yml` (existing CodeQL config structure)

Existing `codeql-config.yml` (lines 1–14 — complete file):
```yaml
name: ctc-manager-codeql-config

queries:
  - uses: security-extended

# Suppression rationale + Alert-ID mapping: docs/security/sast-acceptance.md
query-filters:
  - exclude:
      id: java/ssrf
  - exclude:
      id: java/zipslip
  - exclude:
      id: java/path-injection
```

**Pattern to copy for `qlpack.yml`:** YAML file in `.github/codeql/` directory, minimal fields only.

---

### `.github/workflows/codeql.yml` (config, modified)

Existing `Initialize CodeQL` step (lines 50–55):
```yaml
      - name: Initialize CodeQL
        uses: github/codeql-action/init@v4
        with:
          languages: java-kotlin
          queries: security-extended
          config-file: ./.github/codeql/codeql-config.yml
```

The `packs` parameter (or a `codeql pack install` pre-step) is added to this step to reference `jegr78/ctc-model-pack`. The existing `config-file` reference is preserved unchanged.

---

## Shared Patterns

### Static Import Convention

**Source:** `src/main/java/org/ctc/dataimport/DriverMatchingService.java` lines 3–3 (existing static import precedent):
```java
import static org.springframework.util.StringUtils.hasText;
```

Static imports appear as a block before regular `java.*` imports. The new `import static org.ctc.util.LogSanitizer.sanitize;` follows this ordering. If a file has no existing static imports, it becomes the first import block.

### Annotation Order on New Utility Class

**Source:** CLAUDE.md "Lombok Usage" — `@Slf4j @Component @RequiredArgsConstructor` (alphabetical, Slf4j first). `LogSanitizer` uses NO Lombok annotations (no Spring component, static-only utility), so no annotation ordering applies. The class declaration is bare `public final class LogSanitizer`.

### No-Comment-Pollution Rule

**Source:** CLAUDE.md "No Comment Pollution" — banned: file-header blocks, cross-reference comments, Javadoc on obvious methods. Allowed: single-line WHY comment for non-obvious constraints.

`LogSanitizer.sanitize()` gets exactly one inline comment on the `replaceAll("\\R", "_")` line explaining the CodeQL barrier recognition. No other comments in any modified file.

---

## No Analog Found

None. All files have analogs:
- New utility files: `HexColor.java` / `HexColorTest.java` are exact role+data-flow matches.
- Modified call-site files: self-analogs (single-line wraps, no structural change).
- CodeQL config files: `codeql-config.yml` structure covers the YAML shape.

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/`, `src/test/java/org/ctc/`, `.github/codeql/`, `.github/workflows/`
**Files scanned:** 22 (HexColor, HexColorTest, all 17 flagged files, codeql-config.yml, codeql.yml)
**Pattern extraction date:** 2026-05-31
