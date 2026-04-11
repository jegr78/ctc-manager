# Phase 21: English Code - Research

**Researched:** 2026-04-08
**Domain:** Text-only refactoring — test data strings, HTML comments, grep verification scan
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Focus on confirmed remaining German items + verification scan (not a full re-scan of the entire project)
- **D-02:** Production Java source is already English — no changes needed there
- **D-03:** Carries forward Phase 20 decisions: only true proper nouns (GT7 data) allowed to remain German, umlaut-handling code stays as-is
- **D-04:** Replace all 27 occurrences of `"Spieltag N"` with `"Matchday N"` across 3 test files (StandingsServiceTest, StandingsControllerTest, SiteGeneratorServiceTest)
- **D-05:** Update slug assertion in SiteGeneratorServiceTest from `spieltag-1.html` to `matchday-1.html` to match the changed test data
- **D-06:** Translate 3 German HTML comments to English:
  - `team-detail.html:82` — `<!-- Seasons ohne Fahrer -->` → `<!-- Seasons without drivers -->`
  - `matchday-detail.html:69` — `<!-- Legs (nur anzeigen bei Multi-Leg oder wenn Legs vorhanden) -->` → `<!-- Show legs only for multi-leg or when legs exist -->`
  - `matchday-detail.html:87` — `<!-- Single-Leg: direkt Link zum Race -->` → `<!-- Single-leg: direct link to race -->`
- **D-07:** Final grep-based verification scan using a defined word list of common German words (Spieltag, Saison, Fahrer, Mannschaft, Rennen, Punkte, Ergebnis, Wertung, Tabelle, Gruppe, Runde, etc.) plus umlaut scan across all .java and .html files
- **D-08:** Allowlist for GT7 proper nouns (Nurburgring, etc.) and umlaut-handling code patterns (replaceAll with umlauts)
- **D-09:** One-time verification only — no permanent guard test (carried from Phase 20 D-09)

### Claude's Discretion

- Exact German word list for the verification grep scan
- Order of file changes
- Whether to combine all changes into one commit or split by category

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| I18N-03 | All German constants, enum labels, and string literals converted to English | D-04 covers the remaining string literals ("Spieltag N" in 27 test occurrences) |
| I18N-04 | All German code comments and Javadoc converted to English | D-06 covers the 3 remaining German HTML comments |
| I18N-05 | All German variable and method names renamed to English equivalents | Verified: production Java source is fully English; no remaining work beyond the test string literals |
</phase_requirements>

## Summary

Phase 21 is a narrow, well-scoped text-only refactoring. The research confirms the CONTEXT.md scope assessment is accurate: production Java source is fully English after Phase 20, and the only remaining German text is 27 test string literals ("Spieltag N") across 3 test files, plus 3 German HTML comments in 2 template files. No production behavior changes, no schema changes, no API changes.

The slug assertion in SiteGeneratorServiceTest is a cascading dependency of the Spieltag-to-Matchday rename: `slugify("Spieltag 1")` produces `spieltag-1`, so changing the test data to `"Matchday 1"` makes the slug `matchday-1`. The assertion at line 186 must also be updated from `spieltag-1.html` to `matchday-1.html` to stay aligned with the test data.

The final verification scan is a one-time grep pass using a German word list and a umlaut scan. The only exemptions are `replaceAll("[äÄ]", "ae")` (character transformation logic in `SiteGeneratorService.slugify()`) and GT7 proper nouns (no instances were found in source files beyond test data during research).

**Primary recommendation:** Execute in two tasks — (1) replace all "Spieltag" strings in test files, (2) replace 3 HTML comments — then run `./mvnw verify` followed by the verification grep scan to confirm zero remaining German text.

## Standard Stack

This phase requires no additional libraries. All work is find-and-replace on existing files using the project's established toolchain.

### Toolchain (existing)
| Tool | Purpose | Notes |
|------|---------|-------|
| `./mvnw verify` | Run full test suite + JaCoCo coverage | Confirms no test regressions from string changes |
| `grep -rn` | Verification scan for remaining German text | Standard POSIX tool, no install needed |

**No new dependencies.** [VERIFIED: codebase inspection]

## Architecture Patterns

### Change Categories

**Category A: Test data string replacement**
- 3 files, 27 string occurrences of `"Spieltag N"` → `"Matchday N"`
- 1 additional slug assertion: `"spieltag-1.html"` → `"matchday-1.html"`

**Category B: HTML comment translation**
- 2 template files, 3 comment replacements

**Category C: Verification scan**
- Grep pass across all `.java` and `.html` source files
- Confirm zero remaining German text (with allowlist)

### File Inventory (verified by grep)

| File | Type | Change |
|------|------|--------|
| `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` | Java test | 24× `"Spieltag N"` → `"Matchday N"` |
| `src/test/java/org/ctc/admin/controller/StandingsControllerTest.java` | Java test | 1× `"Spieltag 1"` → `"Matchday 1"` |
| `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` | Java test | 1× `"Spieltag 1"` → `"Matchday 1"` + 1× slug assertion |
| `src/main/resources/templates/admin/team-detail.html` | Thymeleaf template | 1 German comment |
| `src/main/resources/templates/admin/matchday-detail.html` | Thymeleaf template | 2 German comments |

[VERIFIED: grep scan 2026-04-08]

### Slug Dependency Chain

The `slugify()` method in `SiteGeneratorService` transforms a matchday label into a URL-safe filename:

```
"Spieltag 1" → slugify() → "spieltag-1" → file: "spieltag-1.html"
"Matchday 1" → slugify() → "matchday-1" → file: "matchday-1.html"
```

The test at `SiteGeneratorServiceTest.java:186` asserts `matchday/spieltag-1.html` exists. After the test data changes to `"Matchday 1"`, the generated file will be `matchday-1.html`. The assertion MUST be updated to `matchday/matchday-1.html` in the same task as the string replacement. [VERIFIED: code inspection]

### Verification Scan Command

```bash
# German word list scan (production scope: src/main/java + src/main/resources/templates)
grep -rn \
  --include="*.java" --include="*.html" \
  -i "spieltag\|saison\|fahrer\|mannschaft\|rennen\|punkte\|ergebnis\|wertung\|tabelle\|gruppe\|runde\|platz\|sieger\|startaufstellung\|liga" \
  src/main/java/ src/main/resources/templates/ \
  | grep -v "replaceAll.*\[.\+\]"

# Umlaut scan (same scope)
grep -rPn "[äöüÄÖÜß]" src/main/java/ src/main/resources/templates/ \
  | grep -v "replaceAll.*\[.\+\]"
```

Both commands should return zero lines. If any results appear, check against the allowlist:
- `replaceAll("[äÄ]", "ae")` patterns in `SiteGeneratorService.java` — exempt (umlaut-handling code)
- GT7 track proper nouns — exempt (D-03/D-06)

[VERIFIED: codebase scan confirms zero remaining German outside test files and known HTML comments]

### Verification Scan Scope Decision

The locked decisions specify scanning `.java` and `.html` files. The planner should apply the scan to production scope only (`src/main/java/` and `src/main/resources/templates/`), since test files will have their German text replaced in the earlier tasks. Scanning test files after the rename would also pass and provide additional confidence. The planner may choose either approach — Claude's discretion applies.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Batch string replacement | Custom script | IDE refactoring or direct file edits — files are small and changes are targeted |
| Slug behavior verification | Manual slug calculation | Trust existing `slugify()` tests + `./mvnw verify` |

## Common Pitfalls

### Pitfall 1: Forgetting the slug assertion
**What goes wrong:** Developer replaces `"Spieltag 1"` in the test setup but forgets the `Files.exists(seasonDir().resolve("matchday/spieltag-1.html"))` assertion at line 186 — test fails with file-not-found.
**Why it happens:** The assertion is 76 lines below the test data setup, easy to miss.
**How to avoid:** Treat `SiteGeneratorServiceTest.java` as a 2-change file: (1) string replacement at line 110, (2) slug assertion at line 186.
**Warning signs:** `./mvnw verify` failure mentioning `spieltag-1.html` does not exist.

### Pitfall 2: Scanning test files in the final verification
**What goes wrong:** Running the verification grep against `src/test/` expects zero German text but may find the test prefix `"T-ALF"` or similar — creates false alarm.
**Why it happens:** Test data uses deliberate prefixes that look like German abbreviations.
**How to avoid:** Scope the verification grep to `src/main/java/` and `src/main/resources/templates/` only, matching the locked decision scope.

### Pitfall 3: Coverage regression
**What goes wrong:** String changes in test files could theoretically break branch coverage if a renamed string is used in a conditional.
**Why it happens:** In this case it will not happen — `"Spieltag N"` strings are only constructor arguments for `Matchday` name, not branching conditions. But worth confirming with `./mvnw verify`.
**How to avoid:** Always run `./mvnw verify` after all changes and confirm JaCoCo stays at or above 82%.

## Code Examples

### Correct replacement pattern in StandingsServiceTest (representative)

```java
// Before (line 75):
var matchday = new Matchday(season, "Spieltag 1", 1);

// After:
var matchday = new Matchday(season, "Matchday 1", 1);
```

### Correct HTML comment translation

```html
<!-- Before (team-detail.html:82): -->
<!-- Seasons ohne Fahrer -->

<!-- After: -->
<!-- Seasons without drivers -->
```

```html
<!-- Before (matchday-detail.html:69): -->
<!-- Legs (nur anzeigen bei Multi-Leg oder wenn Legs vorhanden) -->

<!-- After: -->
<!-- Show legs only for multi-leg or when legs exist -->
```

```html
<!-- Before (matchday-detail.html:87): -->
<!-- Single-Leg: direkt Link zum Race -->

<!-- After: -->
<!-- Single-leg: direct link to race -->
```

### Correct slug assertion update

```java
// Before (SiteGeneratorServiceTest.java:186):
assertTrue(Files.exists(seasonDir().resolve("matchday/spieltag-1.html")), "matchday page should exist");

// After:
assertTrue(Files.exists(seasonDir().resolve("matchday/matchday-1.html")), "matchday page should exist");
```

[VERIFIED: code inspection of actual file contents]

## Project Constraints (from CLAUDE.md)

| Directive | Impact on Phase |
|-----------|----------------|
| Test Coverage: minimum 82% line coverage | Run `./mvnw verify` after changes; string-only changes have negligible coverage impact |
| Flyway: do not change V1 migrations | Not applicable — no schema changes in this phase |
| No Fallback Calculations | Not applicable |
| Isolate Test Data Completely | E2E test data uses "Test_" prefix — not affected by "Spieltag" rename (E2E uses `TestDataService`, not `StandingsServiceTest`) |
| Do Not Modify Flyway Migrations | Not applicable |
| Communication German, Code/Comments/UI English | This phase enforces the code/comments/UI English rule |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | E2E test data in `TestDataService` does not use "Spieltag" — confirmed by grep returning only the 3 known test files | Standard Stack | Zero risk — verified by grep |
| A2 | No other German text exists in production Java or HTML beyond the 3 HTML comments | Architecture Patterns | Verification scan in final task will detect this |

Both assumptions are confirmed by the codebase grep — tagged A1/A2 for traceability only.

## Environment Availability

Step 2.6: No external dependencies. Phase is pure text replacement + `./mvnw verify`. Maven wrapper is present in the repository.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `./mvnw` | Test validation | Yes | Wrapper in repo | — |
| `grep` | Verification scan | Yes | macOS built-in | — |

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test |
| Config file | `pom.xml` (Surefire + Failsafe configuration) |
| Quick run command | `./mvnw test` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| I18N-03 | "Spieltag N" → "Matchday N" in test data | unit (existing) | `./mvnw test -pl . -Dtest=StandingsServiceTest,StandingsControllerTest,SiteGeneratorServiceTest` | Yes |
| I18N-04 | HTML comments are English | manual review / grep scan | `grep -rn "ohne\|nur anzeigen\|direkt" src/main/resources/templates/` | Yes (grep) |
| I18N-05 | All production identifiers are English | verification scan | `grep -rn -i "spieltag\|saison\|fahrer" src/main/java/ src/main/resources/templates/` | Yes (grep) |

### Sampling Rate

- **Per task commit:** `./mvnw test -pl . -Dtest=StandingsServiceTest,StandingsControllerTest,SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green (`./mvnw verify`) + verification grep returns zero hits before `/gsd-verify-work`

### Wave 0 Gaps

None — existing test infrastructure covers all phase requirements. No new test files needed.

## Security Domain

Not applicable. This phase modifies test string literals and HTML comments. No authentication, session management, input validation, or cryptographic concerns are introduced or changed.

## Sources

### Primary (HIGH confidence)
- Codebase grep scan (2026-04-08) — confirmed exact line numbers and content for all 5 files
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:301-307` — slugify() implementation verified
- `.planning/phases/21-english-code/21-CONTEXT.md` — locked decisions D-01 through D-09
- `.planning/phases/20-english-messages/20-CONTEXT.md` — Phase 20 allowlist decisions

### Secondary (MEDIUM confidence)
- N/A — all claims verified directly against codebase

## Metadata

**Confidence breakdown:**
- Scope accuracy: HIGH — grep-verified every file and occurrence count
- Architecture: HIGH — no structural changes, pure text replacement
- Pitfalls: HIGH — slug dependency chain verified by code reading

**Research date:** 2026-04-08
**Valid until:** Not time-sensitive — codebase state will not change before planning
