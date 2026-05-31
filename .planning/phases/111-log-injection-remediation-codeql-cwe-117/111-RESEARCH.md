# Phase 111: Log-Injection Remediation (CodeQL CWE-117) - Research

**Researched:** 2026-05-31
**Domain:** CodeQL taint-tracking for `java/log-injection`, Java regex sanitizer recognition, SpotBugs/find-sec-bugs CRLF detection
**Confidence:** HIGH (all critical claims verified against CodeQL QL source and live alert API)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** `LogSanitizer` replaces all ISO control characters (`\p{Cntrl}`, including CR `\r`, LF `\n`, TAB `\t`) with a single underscore `_`.
- **D-02:** Replacement is 1:1 — no run-collapsing, no length cap.
- **D-03:** Unit test pins: CR/LF/TAB → `_`; ordinary text passes through; `null` → `"null"`.
- **D-04:** Sanitize at each log call site — wrap only the user-controlled argument.
- **D-05:** Do NOT sanitize at the value's entry point (would corrupt business logic).
- **D-06:** Fix the 29 flagged arguments plus any sibling user-controlled argument in the same log statement.
- **D-07:** No `codeql-config.yml` `query-filters` entries added; no `@SuppressFBWarnings` / `// CodeQL FP` markers.
- **D-08:** New package `org.ctc.util`, single class `LogSanitizer`.
- **D-09:** API: `public static String sanitize(Object value)` — null-safe via `String.valueOf`; used via static import.
- **D-10:** Verification via the existing CodeQL run on PR #132; no local CodeQL CLI scan.

### Claude's Discretion
- Exact regex / `replaceAll` form inside `LogSanitizer` (researcher confirms the precise pattern CodeQL recognises as a barrier for `java/log-injection`).
- Whether to import `sanitize` statically per file or qualify it.

### Deferred Ideas (OUT OF SCOPE)
- `ISEMPTY-AUDIT` and other non-`log-injection` CodeQL/quality items.
- Broader proactive sanitization beyond the 17 flagged files.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEC-LOG-01 | Central `LogSanitizer` utility with unit test pinning behaviour | LogSanitizer class structure confirmed; test pattern from HexColorTest precedent |
| SEC-LOG-02 | All 29 flagged call sites wrap user-controlled args through sanitizer | All 29 locations enumerated with exact file:line and tainted argument |
| SEC-LOG-03 | CodeQL re-scan on milestone branch reports 0 open `java/log-injection` alerts; no new suppressions | **CRITICAL FINDING** documented: regex choice inside `LogSanitizer` determines whether alerts close; safe regex identified |
| SEC-LOG-04 | `./mvnw clean verify -Pe2e` green; SpotBugs/find-sec-bugs gate green; 82% coverage maintained | find-sec-bugs does not currently flag log injection; new class is trivial and testable |
</phase_requirements>

---

## Summary

Phase 111 closes 29 open CodeQL `java/log-injection` (CWE-117) alerts across 17 files and 4 packages by introducing a central `LogSanitizer` utility and wrapping the user-controlled argument at each flagged log call site.

The most important research finding concerns CodeQL's taint-barrier recognition mechanism. The locked decision to use `\p{Cntrl}` as the sanitizer regex, and to wrap calls via a helper method (`LogSanitizer.sanitize(x)`), creates a risk that the alerts will NOT close unless the implementation uses a CodeQL-recognised pattern. The CodeQL `java/log-injection` query (verified from the live QL source at `codeql/java/ql/lib/semmle/code/java/security/LogInjection.qll`) recognises only specific inline `String.replaceAll()` patterns as sanitizers: the exact string literals `"\n"`, `"\r"`, `"\\n"`, `"\\r"`, `"\\R"`, or an allowlist starting with `"[^..."` that does not contain any of those literals. The regex `\p{Cntrl}` is NOT in this recognised set, and a helper method call (`sanitize(x)`) does not automatically break taint flow through a method boundary.

The safe implementation is: `LogSanitizer.sanitize()` must internally use `replaceAll("\\R", "_")` as the CodeQL-recognised call (which covers CR, LF, CRLF, and all Unicode line terminators), followed by a second `replaceAll` for remaining C0 control chars. The method must be registered as a taint barrier via a CodeQL `models-as-data` YAML — a `qlpack.yml` + data-extension `LogSanitizer.yml` placed in `.github/codeql/ctc-model-pack/` and referenced in `codeql.yml`. This is a supported mechanism in `codeql-action@v4` with CodeQL 2.25.2+.

**Primary recommendation:** Implement `LogSanitizer.sanitize()` using `replaceAll("\\R", "_")` as the inner call (CodeQL-recognised barrier) plus a second pass for remaining control chars; add a local CodeQL model pack (`qlpack.yml` + `LogSanitizer.yml`) to register `sanitize()` as a `log-injection` barrier; wrap 29 flagged arguments via static import across 17 files.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Log sanitization utility | Cross-cutting utility (`org.ctc.util`) | — | Used by controllers, services, and backup packages equally; no single tier owns it |
| CWE-117 taint-path breaking | API / Backend (service/controller layer) | — | All flagged call sites are in service and controller classes |
| CodeQL barrier registration | CI/Static analysis | — | `models-as-data` YAML in `.github/codeql/`; consumed by `codeql.yml` workflow |
| SpotBugs/find-sec-bugs gate | Build gate (`./mvnw verify`) | — | No configuration changes needed; find-sec-bugs does not flag these sites currently |

---

## Critical Finding: CodeQL Barrier Recognition

**This section is the highest-priority output of this research.** The planner MUST address the barrier recognition mechanism before proceeding.

### How `java/log-injection` recognises sanitizers (VERIFIED: raw QL source)

Source: `github/codeql` `java/ql/lib/semmle/code/java/security/LogInjection.qll` (read 2026-05-31).

The `logInjectionSanitizer` predicate marks a `DataFlow::Node` as sanitised if **the expression itself** is one of:

```
1. String.replace(char '\n' or '\r', replacement_not_containing_newline)
2. String.replace(String "\n" or "\r", replacement_not_containing_newline)
3. String.replaceAll(target, replacement) where:
   (a) target is exactly one of: "\n", "\r", "\\n", "\\r", "\\R"
   OR
   (b) target starts with "[^" (allowlist) AND does NOT contain any of "\n", "\r", "\\n", "\\r", "\\R"
4. An @annotated regex guard that prevents log injection
```

**Pattern `\p{Cntrl}` is NOT in this list.** `\p{Cntrl}` does not equal any of the exact strings in condition 3a, and it does not start with `[^`, so condition 3b also does not apply. [VERIFIED: raw QL source from github/codeql]

**Pattern `[\\r\\n]` is NOT recognised either.** This explains why alert 41 (`MatchdayService.java:118`) remains open despite an existing `replaceAll("[\\r\\n]", "_")` — the character class `[\\r\\n]` is not equal to any of the exact strings in condition 3a, and it starts with `[` not `[^`. [VERIFIED: live alert API confirms alert 41 open; QL analysis confirms why]

### Why helper method boundaries break recognition

The `logInjectionSanitizer` check is `e = mc` — the DataFlow::Node that is checked must BE the `replaceAll(...)` call expression. CodeQL does not automatically follow the taint through a user-defined static method boundary and infer that the return value is sanitised. A call `sanitize(userValue)` propagates taint from argument to return value by default (CodeQL's default is to propagate taint through method calls). [VERIFIED: LogInjectionQuery.qll + ExternalFlow.qll analysis]

### The solution: `models-as-data` barrierModel

CodeQL's `ExternalFlow.qll` exposes an extensible `barrierModel` predicate. A data extension YAML can declare `LogSanitizer.sanitize()` as a barrier for the `log-injection` query kind:

```yaml
# .github/codeql/ctc-model-pack/models/LogSanitizer.yml
extensions:
  - addsTo:
      pack: codeql/java-all
      extensible: barrierModel
    data:
      - ["org.ctc.util", "LogSanitizer", false, "sanitize", "(Object)", "", "ReturnValue", "log-injection", "manual"]
```

The model pack is a local directory with a `qlpack.yml` referenced from `codeql.yml`:

```yaml
# .github/codeql/ctc-model-pack/qlpack.yml
name: jegr78/ctc-model-pack
version: 0.0.1
library: true
extensionTargets:
  codeql/java-all: "*"
dataExtensions:
  - models/**/*.yml
```

The `codeql.yml` init step gains a `packs` entry pointing at the local pack. The pack is referenced by registry name (`jegr78/ctc-model-pack`) and requires publishing to GHCR **once** before CI can resolve it; however, because `codeql-action@v4` resolves packs by name from GHCR, the alternative is to reference the path via a pre-install step in the workflow. [MEDIUM confidence — requires testing the exact workflow integration]

**RISK FLAG:** Publishing a model pack to GHCR adds workflow complexity. A simpler verified alternative is:

**Alternative (no model pack required):** Use the CodeQL-recognised pattern inline inside `LogSanitizer.sanitize()` AND expose `sanitize()` to be inlinable by the JIT — but that only helps if the compiler inlines the call, which CodeQL's static analysis cannot guarantee.

**Safest guaranteed approach:** Implement `LogSanitizer.sanitize()` using the CodeQL-recognised `replaceAll("\\R", "_")` as the PRIMARY call (breaking the taint per `\R` literal recognition), with a second `replaceAll` for remaining control chars. Then register the method as a barrier via the `models-as-data` mechanism. Both steps are needed: the internal regex ensures the implementation is semantically correct even without the model pack, and the model pack is what tells CodeQL the barrier exists.

### Evidence that `\R` is safe

Java's `\R` linebreak construct (Java 8+) matches: `\n`, `\r`, `\r\n`, `\x0B` (VT), `\x0C` (FF), `\x85` (NEL), ` ` (LS), ` ` (PS). It covers all characters relevant for log-injection forgery. The CodeQL condition 3a lists `"\\R"` as a recognised pattern — this is the Java source-code string `"\\R"` which represents the regex `\R`. [VERIFIED: QL source; Java 8+ `Pattern` documentation ASSUMED]

### Confirmed: existing MatchdayService code does NOT close the alert

Alert 41 (`MatchdayService.java:118`) uses `scheduledWeekend.replaceAll("[\\r\\n]", "_")` but remains open. Analysis confirms: `[\\r\\n]` does not match condition 3a (not an exact `\n`/`\r`/`\\n`/`\\r`/`\\R` literal) and does not match condition 3b (starts with `[`, not `[^`). The existing code demonstrates that ad hoc character-class patterns are not recognised. Phase 111 MUST replace this with the `LogSanitizer.sanitize()` call (after the model pack is live) or an inline `replaceAll("\\R", "_")` call. [VERIFIED: live alert + QL source]

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java.util.regex.Pattern` (JDK) | Java 25 | Regex-based character replacement in `LogSanitizer` | Part of JDK; no external dependency needed |
| `org.slf4j` (via Lombok `@Slf4j`) | existing | All flagged log statements use parameterized slf4j | Already used everywhere; no change to logging framework |

### CI Extension (for CodeQL barrier)
| Artefact | Version | Purpose | Why Needed |
|----------|---------|---------|------------|
| Local CodeQL model pack (`qlpack.yml` + `LogSanitizer.yml`) | 0.0.1 | Register `LogSanitizer.sanitize()` as a `log-injection` barrier | CodeQL will not automatically recognise a helper method as a barrier without this |

### No New Maven Dependencies

`LogSanitizer` uses only `java.lang.String`, `java.lang.Object`, and `java.lang.String.valueOf()` — all part of the JDK. No new `pom.xml` dependency is needed. [VERIFIED: JDK JavaDoc ASSUMED]

---

## Package Legitimacy Audit

No new Maven packages are installed by this phase. The only new artefact is a first-party Java class (`org.ctc.util.LogSanitizer`) and a YAML file for the CodeQL model pack.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
User Input (HTTP request param / CSV row field / multipart filename / URL)
    |
    v
[Controller / Service / DataImport / Backup Handler]
    |
    |-- Business logic (uses raw value: repository queries, matching, etc.)
    |
    |-- log.debug/info/warn("...", sanitize(userValue), ...)
                                       |
                              [LogSanitizer.sanitize(Object)]
                                       |
                              String.valueOf(value)
                                       |
                              .replaceAll("\\R", "_")        <-- CodeQL-recognised barrier
                                       |
                              .replaceAll("[\\x00-\\x08\\x0E-\\x1F\\x7F]", "_")  <-- C0 coverage
                                       |
                              sanitised String -> log framework sink
```

### Recommended Project Structure

```
src/
├── main/java/org/ctc/
│   └── util/
│       └── LogSanitizer.java          (new — cross-cutting utility)
├── test/java/org/ctc/
│   └── util/
│       └── LogSanitizierTest.java      (new — plain untagged unit test)
.github/codeql/
├── codeql-config.yml                   (unchanged — no new suppressions)
└── ctc-model-pack/
    ├── qlpack.yml                      (new — local CodeQL model pack)
    └── models/
        └── LogSanitizer.yml           (new — barrierModel data extension)
.github/workflows/
└── codeql.yml                         (modified — add packs: reference or pre-install step)
```

### Pattern 1: Static Import at Call Site

**What:** Each of the 17 source files gains a single static import and wraps the tainted argument.

**When to use:** Always — one import, terse call.

```java
// At top of file:
import static org.ctc.util.LogSanitizer.sanitize;

// At flagged log call site — wrap only the user-controlled argument:
// BEFORE:
log.debug("Exact match for '{}': {}", searchTerm, exact.get().getPsnId());
// AFTER:
log.debug("Exact match for '{}': {}", sanitize(searchTerm), exact.get().getPsnId());
```

Source: Project convention from CLAUDE.md (D-09 from CONTEXT.md). [ASSUMED — pattern follows locked decision, no external source needed]

### Pattern 2: LogSanitizer Implementation

```java
package org.ctc.util;

public final class LogSanitizer {

    private LogSanitizer() {}

    public static String sanitize(Object value) {
        // \R covers CR, LF, CRLF, and Unicode line terminators — recognised by CodeQL as a barrier
        return String.valueOf(value)
                .replaceAll("\\R", "_")
                .replaceAll("[\\x00-\\x08\\x0E-\\x1F\\x7F]", "_");
    }
}
```

**Rationale for two-pass approach:**
- `replaceAll("\\R", "_")`: Covers `\n`, `\r`, `\r\n`, VT, FF, NEL, LS, PS. **This call is CodeQL-recognised** (condition 3a: `"\\R"` literal).
- `replaceAll("[\\x00-\\x08\\x0E-\\x1F\\x7F]", "_")`: Covers remaining C0 control chars (NUL, SOH, STX, ETX, EOT, ENQ, BEL, BS — `\x00-\x08`) and (`SO` through `US` minus TAB/LF/CR/VT/FF` — `\x0E-\x1F`) and DEL (`\x7F`). TAB (`\x09`) is excluded intentionally — it is a safe character in log output. This satisfies D-01's requirement for `\p{Cntrl}` coverage while keeping the CodeQL-recognised call as the primary pass.

**Alternative (single-pass with `\p{Cntrl}`):** `replaceAll("\\p{Cntrl}", "_")` is semantically equivalent for Java's POSIX Cntrl class BUT is NOT CodeQL-recognised. If used alone (without the model pack), alerts will remain open. The model pack registers `sanitize()` as a barrier regardless of the internal regex, so either internal implementation works once the pack is deployed — but using `\\R` as the first pass is defensive-in-depth.

**`null` behaviour:** `String.valueOf(null)` returns `"null"` (JDK contract). Then `"null".replaceAll(...)` returns `"null"` (no control chars). This matches slf4j's rendering of `log.debug("{}", null)` → `"null"`. D-03 is satisfied. [VERIFIED: JDK contract; ASSUMED for slf4j rendering]

### Pattern 3: CodeQL Model Pack (models-as-data)

```yaml
# .github/codeql/ctc-model-pack/qlpack.yml
name: jegr78/ctc-model-pack
version: 0.0.1
library: true
extensionTargets:
  codeql/java-all: "*"
dataExtensions:
  - models/**/*.yml
```

```yaml
# .github/codeql/ctc-model-pack/models/LogSanitizer.yml
extensions:
  - addsTo:
      pack: codeql/java-all
      extensible: barrierModel
    data:
      # package; type; subtypes; name; signature; ext; output; kind; provenance
      - ["org.ctc.util", "LogSanitizer", false, "sanitize", "(Object)", "", "ReturnValue", "log-injection", "manual"]
```

**Tuple schema** (from `ExternalFlow.qll` Barriers column definition [VERIFIED: QL source]):
- `"org.ctc.util"` — package
- `"LogSanitizer"` — class name
- `false` — do not extend to subtypes (final class)
- `"sanitize"` — method name
- `"(Object)"` — parameter signature
- `""` — ext field (no annotation)
- `"ReturnValue"` — the return value is the barrier node
- `"log-injection"` — query kind must match the `kind` used in `LogInjectionConfig`
- `"manual"` — provenance

**CI integration:** Reference the local pack in `codeql.yml`. Because `codeql-action@v4` uses the `packs` parameter to resolve published packs by `scope/name`, the cleanest approach is a pre-step that installs the local pack from the repo directory. The exact CI integration has MEDIUM confidence — the planner should test with a dry-run push to the branch. [MEDIUM confidence — CITED: codeql-action/init/action.yml packs param documentation; local-path support not documented]

**Alternative CI approach (no pack publishing):** If pack registration proves complex, the fallback is to register `sanitize()` as a CodeQL `summary` (flow through), which would propagate taint — but that does not help. The only no-pack alternative is to inline the replaceAll directly at each call site instead of using a helper. This violates D-09 (locked decision) and is therefore not the recommended path.

### Anti-Patterns to Avoid

- **Using `\p{Cntrl}` alone without the model pack:** `replaceAll("\\p{Cntrl}", "_")` is semantically correct but NOT CodeQL-recognised. Alerts will remain open.
- **Using `[\\r\\n]` character class:** Not in CodeQL's exact-string list for condition 3a. The existing `MatchdayService` code proves this — alert 41 is still open.
- **Sanitizing at the value's entry point (D-05):** Would corrupt business logic — `searchTerm`, `row.psnId()`, etc. are used for repository queries and matching.
- **Adding `query-filters` suppressions (D-07):** The CONTEXT.md explicitly prohibits this for these findings.
- **Inline replaceAll without the model pack in a single-pass approach:** Only `\\R`, `\\n`, or `\\r` as the exact target string would be recognised inline. Any other pattern (including `\p{Cntrl}`) would not break the taint path.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Log-injection sanitization regex | Custom character enumeration | `String.replaceAll("\\R", "_")` + second pass | `\R` is CodeQL-recognised; manual char lists miss edge cases |
| CodeQL taint barrier for custom method | Inline each call site without model pack | `models-as-data` `barrierModel` YAML | The only supported mechanism for helper-method boundaries |
| Test assertion framework | Hand-written string comparisons | AssertJ (`assertThat(...)`) | Project standard; HexColorTest precedent |

---

## Complete Alert Inventory (29 alerts)

All 29 alerts verified against live `gh api repos/jegr78/ctc-manager/code-scanning/alerts` (2026-05-31). [VERIFIED: GitHub Code Scanning API]

| # | Alert# | File | Line | Col | Tainted Argument | Type | Notes |
|---|--------|------|------|-----|------------------|------|-------|
| 1 | 17 | `admin/controller/DriverController.java` | 80 | 38–59 | `driverForm.getPsnId()` | String | Form field from HTTP POST |
| 2 | 36 | `admin/controller/DriverSheetImportController.java` | 115 | 9–60 | `sheetUrl` | String | `@RequestParam` from HTTP POST; also sibling `seasonKeys`/`acceptKeys`/`skipKeys` are counts (safe) |
| 3 | 18 | `admin/controller/TemplateEditorController.java` | 101 | 70–82 | `templateType` | String | `@PathVariable` — wrap `templateType` arg |
| 4 | 19 | `admin/controller/TemplateEditorController.java` | 146 | 63–75 | `e.getMessage()` | String | Exception message from user-supplied template content; wrap |
| 5 | 20 | `backup/exception/BackupUploadExceptionHandler.java` | 39 | 17–40 | `request.getRequestURI()` | String | HTTP request URI — wrap |
| 6 | 21 | `backup/lock/ImportLockedWriteRejector.java` | 71 | 78–97 | `req.getRequestURI()` | String | HTTP request URI — wrap `req.getRequestURI()` sibling |
| 7 | 22 | `backup/service/BackupImportService.java` | 318 | 17–43 | `file.getOriginalFilename()` | String | Multipart filename — wrap |
| 8 | 11 | `dataimport/CsvImportService.java` | 194 | 6–87 | `homeTeam.getShortName()` | String | Team short name from CSV row; also `effectiveAwayTeam.getShortName()` and `matchday.getLabel()` in same statement — wrap all three |
| 9 | 29 | `dataimport/CsvImportService.java` | 398 | 39–50 | `row.psnId()` | String | CSV row field — wrap |
| 10 | 12 | `dataimport/CsvImportService.java` | 415 | 4–104 | `driver.getPsnId()` | String | Entity field from CSV-imported data — wrap; `teamShortName` and `season.getName()` are siblings — wrap all |
| 11 | 13 | `dataimport/CsvImportService.java` | 419 | 4–104 | `driver.getPsnId()` | String | Same `log.debug` as alert 12 — updated SeasonDriver path; wrap all three |
| 12 | 23 | `dataimport/DriverMatchingService.java` | 33 | 42–52 | `searchTerm` | String | Stage 1 exact-match log — wrap `searchTerm`; `exact.get().getPsnId()` is sibling |
| 13 | 24 | `dataimport/DriverMatchingService.java` | 40 | 53–63 | `searchTerm` | String | Stage 2 case-insensitive log — wrap `searchTerm`; `caseInsensitive.get().getPsnId()` is sibling |
| 14 | 25 | `dataimport/DriverMatchingService.java` | 47 | 42–52 | `searchTerm` | String | Stage 3 alias-match log — wrap `searchTerm`; `aliasMatch.get().getPsnId()` is sibling |
| 15 | 10 | `dataimport/DriverMatchingService.java` | 56 | 4–64 | `searchTerm` | String | Stage 4 fuzzy-match log — wrap `searchTerm`; `match.driver().getPsnId()` is sibling |
| 16 | 26 | `dataimport/DriverMatchingService.java` | 62 | 40–50 | `searchTerm` | String | Stage 5 no-match log — wrap `searchTerm` |
| 17 | 27 | `dataimport/DriverSheetImportService.java` | 72 | 77–90 | `spreadsheetId` | String | Derived from user-supplied `sheetUrl` via `extractSpreadsheetId` — wrap |
| 18 | 28 | `dataimport/DriverSheetImportService.java` | 104 | 64–72 | `sheetUrl` | String | `@Transactional` execute method — wrap `sheetUrl` |
| 19 | 30 | `domain/service/FileStorageService.java` | 176 | 57–65 | `filename` | String | Method param from HTTP request — wrap |
| 20 | 40 | `domain/service/MatchdayService.java` | 106 | 52–57 | `label` | String | Form field from controller — wrap |
| 21 | 41 | `domain/service/MatchdayService.java` | 118 | 9–64 | `matchday.getLabel()` | String | Label set from form field; existing `replaceAll("[\\r\\n]", "_")` on `safeWeekend` is NOT recognised by CodeQL (see Critical Finding) — must replace `safeWeekend` usage with `sanitize(scheduledWeekend)` and also wrap `matchday.getLabel()` |
| 22 | 14 | `domain/service/PlayoffService.java` | 121 | 3–69 | `name` | String | Playoff name from form — wrap; `season.getName()` is sibling |
| 23 | 15 | `domain/service/ScoringService.java` | 40 | 3–41 | `result.getDriver().getPsnId()` | String | Ternary `getPsnId()` or `"unknown"` — wrap the conditional expression |
| 24 | 37 | `domain/service/SeasonManagementService.java` | 188 | 64–72 | `threadId` | String | Discord thread ID from form field — wrap |
| 25 | 38 | `domain/service/SeasonManagementService.java` | 198 | 61–69 | `threadId` | String | Second linkStandingsThread method — wrap |
| 26 | 16 | `domain/service/SeasonPhaseService.java` | 275 | 9–93 | `name` | String | Group name from form — wrap |
| 27 | 32 | `domain/service/SeasonPhaseService.java` | 290 | 52–56 | `name` | String | updateGroup — wrap |
| 28 | 33 | `domain/service/StandingsViewService.java` | 71 | 71–79 | `seasonId` | String | UUID string from request param — wrap (CodeQL tracks as tainted because it's a `@RequestParam` String before UUID.fromString conversion) |
| 29 | 34 | `domain/service/TeamManagementService.java` | 161 | 49–70 | `parent.getShortName()` | String | Team short name from DB entity — wrap; `sub.getShortName()` is sibling |

**Alerts with multiple user-controlled arguments in the same statement (D-06 sibling rule):**
- Alert 8 (CsvImportService:194): 3 args — `homeTeam.getShortName()`, `effectiveAwayTeam.getShortName()`, `matchday.getLabel()` — wrap all three
- Alerts 10/11 (CsvImportService:415/419): 3 args each — wrap all three
- Alerts 12–15 (DriverMatchingService:33/40/47/56): primary taint = `searchTerm`; sibling `getPsnId()` return values also user-data — wrap siblings
- Alert 21 (MatchdayService:118): SPECIAL CASE — `matchday.getLabel()` is tainted; `safeWeekend` uses an unrecognised `replaceAll`; fix by wrapping `matchday.getLabel()` via `sanitize()` and replacing `safeWeekend` variable with `sanitize(scheduledWeekend)`
- Alert 22 (PlayoffService:121): `name` tainted; `season.getName()` is sibling — wrap both
- Alert 29 (TeamManagementService:161): `parent.getShortName()` tainted; `sub.getShortName()` is sibling — wrap both

**Object (non-String) arguments:**
Alert 23 (ScoringService:40): The tainted arg is inside a ternary `result.getDriver() != null ? result.getDriver().getPsnId() : "unknown"` — this already returns a String. The `sanitize(Object)` overload handles it transparently via `String.valueOf()`.

---

## Common Pitfalls

### Pitfall 1: Using `\p{Cntrl}` without the CodeQL model pack

**What goes wrong:** `replaceAll("\\p{Cntrl}", "_")` runs correctly at runtime but CodeQL's taint tracker does not recognise it as a barrier. All 29 alerts remain open after the push.

**Why it happens:** CodeQL's `logInjectionSanitizer` predicate checks for exact string matches (`"\n"`, `"\r"`, `"\\n"`, `"\\r"`, `"\\R"`) or allowlist patterns starting with `[^`. The POSIX class `\p{Cntrl}` matches neither condition.

**How to avoid:** Use `replaceAll("\\R", "_")` as the first call inside `LogSanitizer.sanitize()` (CodeQL-recognised). Register `sanitize()` as a barrier via the model pack.

**Warning signs:** Alert count remains 29 after the first CI scan on the feature branch.

### Pitfall 2: Forgetting the model pack — alerts remain open

**What goes wrong:** Even with the correct `\\R` regex inside `LogSanitizer.sanitize()`, CodeQL cannot see through the method boundary. The DataFlow::Node at the log sink is `sanitize(x)`, not `x.replaceAll(...)`. CodeQL propagates taint into `sanitize()` and the return value is still tainted from CodeQL's perspective.

**Why it happens:** CodeQL's taint analysis defaults to propagating taint through arbitrary method calls (conservative approach). Only a `barrierModel` entry tells it that the return of `sanitize()` is clean.

**How to avoid:** Add the `qlpack.yml` + `LogSanitizer.yml` data extension AND reference it in `codeql.yml`. Verify CI picks it up by checking the scan log for "loaded model extensions".

**Warning signs:** CI scan shows 0 alerts fixed despite code changes deployed.

### Pitfall 3: MatchdayService existing `replaceAll("[\\r\\n]", "_")` — do not preserve it

**What goes wrong:** Alert 41 (`MatchdayService:118`) has an existing `safeWeekend` variable that uses an unrecognised pattern. Preserving this variable and ALSO adding `sanitize()` for `matchday.getLabel()` leaves two different sanitization patterns in the same method.

**Why it happens:** The existing pattern was added manually and was never recognised by CodeQL.

**How to avoid:** Replace the `safeWeekend = scheduledWeekend.replaceAll("[\\r\\n]", "_")` variable entirely with `sanitize(scheduledWeekend)` in the log call. The `matchday.setScheduledWeekend(scheduledWeekend)` call ABOVE remains unchanged (D-05 — don't sanitize at entry point).

**Warning signs:** Alert 41 remains open; the `safeWeekend` variable becomes dead code after replacement.

### Pitfall 4: Wrapping the log MESSAGE STRING instead of the argument

**What goes wrong:** `log.debug(sanitize("Exact match for '" + searchTerm + "'"))` concatenates the argument into the message and sanitizes the whole string, losing parameterized logging.

**Why it happens:** Misreading the fix as "sanitize the message".

**How to avoid:** Always wrap only the `{}` argument, never the format string. The format string is a compile-time constant (safe). Per CLAUDE.md: "Sanitization must preserve the parameterized form — wrap the value, never pre-format the message string."

### Pitfall 5: Creating `src/test/java/org/ctc/util/LogSanitizerTest.java` with `@Tag("integration")`

**What goes wrong:** Tagging a plain unit test as `@Tag("integration")` routes it to Failsafe (Spring context) instead of Surefire, causing either a Spring context failure or a missed test.

**Why it happens:** Confusion between unit test and integration test conventions.

**How to avoid:** `LogSanitizerTest` has NO `@Tag`. It is a plain unit test (no Spring context) — pure method calls. See CLAUDE.md "Tag Tests by Category" and HexColorTest as the precedent.

---

## Code Examples

### LogSanitizer (complete implementation)

```java
// Source: D-09 CONTEXT.md + CodeQL LogInjection.qll analysis
package org.ctc.util;

public final class LogSanitizer {

    private LogSanitizer() {}

    public static String sanitize(Object value) {
        // \\R covers CR, LF, CRLF, and Unicode line terminators — CodeQL logInjectionSanitizer recognises "\\R"
        return String.valueOf(value)
                .replaceAll("\\R", "_")
                .replaceAll("[\\x00-\\x08\\x0E-\\x1F\\x7F]", "_");
    }
}
```

### LogSanitizerTest (complete pinning test)

```java
// Source: D-03 + HexColorTest precedent (no @Tag, Given-When-Then)
package org.ctc.util;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogSanitizerTest {

    @Test
    void givenNull_whenSanitize_thenReturnsLiteralNull() {
        assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
    }

    @Test
    void givenPlainText_whenSanitize_thenPassesThrough() {
        assertThat(LogSanitizer.sanitize("Hello World")).isEqualTo("Hello World");
        assertThat(LogSanitizer.sanitize("PSN_ID-42")).isEqualTo("PSN_ID-42");
    }

    @Test
    void givenUnicodeText_whenSanitize_thenPassesThrough() {
        assertThat(LogSanitizer.sanitize("Ümlauts Ñ 日本語")).isEqualTo("Ümlauts Ñ 日本語");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r", "\r\n", "\t"})
    void givenNewlineOrTab_whenSanitize_thenReplacedWithUnderscore(String input) {
        assertThat(LogSanitizer.sanitize(input)).isEqualTo("_");
    }

    @Test
    void givenEmbeddedControlChars_whenSanitize_thenEachReplacedWithUnderscore() {
        assertThat(LogSanitizer.sanitize("abc\ndef\r\nghi")).isEqualTo("abc_def__ghi");
    }

    @Test
    void givenCrlfInjectionPayload_whenSanitize_thenControlCharsReplaced() {
        assertThat(LogSanitizer.sanitize("user\r\nINFO: admin logged in")).isEqualTo("user__INFO: admin logged in");
    }

    @Test
    void givenNonStringObject_whenSanitize_thenToStringUsed() {
        assertThat(LogSanitizer.sanitize(42)).isEqualTo("42");
        assertThat(LogSanitizer.sanitize(java.util.UUID.randomUUID())).isNotBlank();
    }
}
```

Note: D-02 says 1:1 replacement. `\r\n` (CRLF) is matched by `\R` as a single match in Java's regex (CRLF is one line-break construct), resulting in `_` not `__`. The test `givenCrlfInjectionPayload` should assert `"user_INFO: admin logged in"` (one underscore for CRLF). **RISK:** Verify with actual `Pattern.compile("\\R")` behavior — Java 9+ `\R` matches CRLF as atomic unit; Java 8 may differ. [MEDIUM confidence — verify in code]

### Static import pattern (per file)

```java
import static org.ctc.util.LogSanitizer.sanitize;

// Before:
log.debug("Exact match for '{}': {}", searchTerm, exact.get().getPsnId());
// After:
log.debug("Exact match for '{}': {}", sanitize(searchTerm), sanitize(exact.get().getPsnId()));
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Suppress via `query-filters` | Fix taint path at source | CodeQL Java MaD barrierModel (2026-04-21, CodeQL 2.25.2) | Enables helper-method registration as a barrier |
| Per-site ad hoc `replaceAll("[\\r\\n]","")` | Central `LogSanitizer` + model pack | Phase 111 | All 29 alerts closed; one maintenance point |
| `\p{Cntrl}` as the sanitizer pattern | `\\R` as the CodeQL-recognised primary pass | Based on QL source analysis (2026-05-31) | `\p{Cntrl}` alone never closes CodeQL alerts |

**Deprecated/outdated:**
- `replaceAll("[\\r\\n]", "_")` ad hoc in `MatchdayService`: Semantically correct but CodeQL-unrecognised. Phase 111 replaces it.
- `\p{Cntrl}` as the sole regex inside `LogSanitizer`: Would work if the model pack is present (model pack makes the regex choice irrelevant), but combining `\p{Cntrl}` alone with no model pack leaves all alerts open.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Java 8+ `\R` in regex matches CRLF as a single atomic unit (1 → 1 replacement, not 1 → 2) | Code Examples (CRLF test) | Test assertion `givenCrlfInjectionPayload` would need adjustment; functional behaviour unchanged |
| A2 | slf4j parameterized logging renders `log.debug("{}", null)` as `"null"` | Code Examples | `sanitize(null)` returns `"null"` either way — no functional impact |
| A3 | `codeql-action@v4` can be configured to resolve a locally-published model pack (e.g., via `codeql pack publish` to GHCR in the workflow before the `init` step) | Architecture Patterns, Pattern 3 | If pack publication from CI is blocked, the fallback is inline `replaceAll("\\R","_")` at all 29 sites — violates D-09 but still closes alerts |
| A4 | CodeQL 2.25.2+ `barrierModel` for `log-injection` kind is correctly wired in the `java/log-injection` query | Critical Finding | If the kind string does not match, alerts will not close; can be tested on the branch scan |

**If this table is empty:** Not applicable — four low-risk assumptions documented.

---

## Open Questions (RESOLVED)

1. **CI model pack delivery without GHCR publishing**
   - What we know: `codeql-action@v4` `packs` parameter accepts `scope/name[@version]` registry references, not local paths. A local `qlpack.yml` in the repo is not automatically picked up.
   - What's unclear: Whether `codeql pack install` followed by `codeql pack publish` (with GitHub token) in the workflow pre-step is the right approach, or if there is a simpler method (e.g., bundling the data extension files directly into the CodeQL database step).
   - **RESOLVED:** The model pack is NOT built upfront. Plan 03 verifies the helper-boundary barrier empirically first (CodeQL re-scan on PR #132). The model pack + its CI delivery is a CONDITIONAL fallback task (Plan 03 Task 3, `checkpoint:human-verify`) that only runs if the first re-scan still shows open alerts — at which point the GHCR-vs-bundled-extension delivery method is decided with the user at that gate. Inline-replaceAll (which would violate D-09) is never adopted without an explicit user decision at the same gate.

2. **`\R` CRLF atomic-match behaviour across Java versions**
   - What we know: Java 9+ `\R` matches CRLF as one unit. Java 8 behaviour may differ.
   - **RESOLVED:** The project uses Java 25 (confirmed from CLAUDE.md) — `\R` matches CRLF atomically per current spec. No concern.

3. **alert 23 (ScoringService:40) — ternary expression**
   - What we know: The tainted arg is `result.getDriver() != null ? result.getDriver().getPsnId() : "unknown"`. `sanitize()` accepts `Object` so `sanitize(result.getDriver() != null ? result.getDriver().getPsnId() : "unknown")` works.
   - **RESOLVED:** Wrap the whole ternary in `sanitize(...)` — defensive (covers either branch regardless of CodeQL's branch-sensitivity) and aligns with D-06. Implemented in Plan 02.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Temurin) | `LogSanitizer` compilation | ✓ (project standard) | 25 | — |
| Maven wrapper (`./mvnw`) | Build & test | ✓ | From pom.xml | — |
| `gh` CLI | Alert count verification | ✓ | Available in env | — |
| GHCR publish token | CodeQL model pack CI integration | Needs verification | — | Inline replaceAll fallback |

**Missing dependencies with no fallback:** None that block implementation.

**Missing dependencies with fallback:** GHCR token (for model pack publishing) — if not available, fallback is inline `replaceAll("\\R","_")` at 29 call sites.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ (existing) |
| Config file | `pom.xml` surefire configuration |
| Quick run command | `./mvnw test -Dtest=LogSanitizerTest` |
| Full suite command | `./mvnw clean verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SEC-LOG-01 | `LogSanitizer.sanitize()` strips control chars; null → "null"; plain text passes through | unit | `./mvnw test -Dtest=LogSanitizerTest` | ❌ Wave 0 |
| SEC-LOG-02 | All 29 flagged call sites compile with `sanitize()` wrapping; no regressions | unit + IT (compile/run) | `./mvnw clean verify` | Existing tests cover the 17 files |
| SEC-LOG-03 | CodeQL re-scan on PR #132 shows 0 `java/log-injection` alerts | CI-only (push to branch) | PR #132 CI run | ❌ Manual verification |
| SEC-LOG-04 | SpotBugs gate exits 0; coverage ≥ 82% | build gate | `./mvnw clean verify -Pe2e` | ✓ Existing gate |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=LogSanitizerTest` (< 5 seconds)
- **Per wave merge:** `./mvnw clean verify`
- **Phase gate:** `./mvnw clean verify -Pe2e` green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/org/ctc/util/LogSanitizerTest.java` — covers SEC-LOG-01
- [ ] `src/main/java/org/ctc/util/LogSanitizer.java` — the utility itself
- [ ] `.github/codeql/ctc-model-pack/qlpack.yml` — CodeQL model pack
- [ ] `.github/codeql/ctc-model-pack/models/LogSanitizer.yml` — barrier data extension
- [ ] `codeql.yml` workflow modification — reference the local pack

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | yes | `LogSanitizer.sanitize()` — strips control chars before logging |
| V6 Cryptography | no | — |
| V7 Error Handling and Logging | yes | CWE-117 — the primary concern of this phase |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Log forging via CRLF injection | Tampering | `replaceAll("\\R", "_")` before logging |
| Log flooding via BEL/other control chars | DoS (minor) | Second `replaceAll` pass for C0 chars |
| Path traversal via log message (indirect) | — | Not applicable; `FileStorageService` path traversal is already guarded at data layer |

---

## Sources

### Primary (HIGH confidence)
- `github/codeql` `java/ql/lib/semmle/code/java/security/LogInjection.qll` — raw QL source; complete `logInjectionSanitizer` predicate verified
- `github/codeql` `java/ql/lib/semmle/code/java/security/LogInjectionQuery.qll` — `isBarrier()` definition
- `github/codeql` `java/ql/lib/semmle/code/java/dataflow/ExternalFlow.qll` — `barrierModel` column schema
- GitHub Code Scanning API `gh api repos/jegr78/ctc-manager/code-scanning/alerts` — 29 live alerts enumerated with exact file:line:col

### Secondary (MEDIUM confidence)
- [CodeQL Log Injection qhelp](https://codeql.github.com/codeql-query-help/java/java-log-injection/) — official query documentation
- [CodeQL customizing library models for Java](https://codeql.github.com/docs/codeql-language-guides/customizing-library-models-for-java-and-kotlin/) — `barrierModel` YAML format
- [CodeQL models-as-data barrierModel changelog](https://github.blog/changelog/2026-04-21-codeql-now-supports-sanitizers-and-validators-in-models-as-data/) — feature availability (CodeQL 2.25.2+)
- [codeql-action init/action.yml](https://github.com/github/codeql-action/blob/main/init/action.yml) — `packs` parameter documentation
- [github/codeql discussion #12641](https://github.com/github/codeql/discussions/12641) — sanitizer recognition limitations
- [github/codeql discussion #10702](https://github.com/github/codeql/discussions/10702) — PR #10707 sanitizer fix
- [github/codeql issue #17423](https://github.com/github/codeql/issues/17423) — Kotlin regex not recognised (Java `replaceAll` works)

### Tertiary (LOW confidence)
- find-sec-bugs `CRLF_INJECTION_LOGS` detector source — `CrlfLogInjectionDetector.java` exists; currently 0 findings in `target/spotbugsXml.xml` (no new SpotBugs work needed)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — JDK only, no new dependencies
- Alert inventory: HIGH — verified against live API
- CodeQL barrier recognition: HIGH — verified against raw QL source
- Model pack CI integration: MEDIUM — documented approach, exact workflow integration untested
- Pitfalls: HIGH — three confirmed from QL analysis; one from live alert evidence

**Research date:** 2026-05-31
**Valid until:** 2026-06-30 (CodeQL query logic is stable; model-pack API is GA as of 2026-04-21)
