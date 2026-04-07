# Phase 20: English Messages - Research

**Researched:** 2026-04-07
**Domain:** Text-only refactoring — German comments, configuration comments, Javadoc, and string literals to English
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Use grep-based scan across ALL files in the project (not just src/main/java) to find German text
- **D-02:** Scan covers all file types — .java, .html, .xml, .yml, .properties, .sql, .md (project docs excluded from changes but scanned for awareness)
- **D-03:** Full-project scope — every German text in every file, including comments, Javadoc, variable/method names, string literals, constants, enum labels, configs
- **D-04:** This phase absorbs Phase 21 (English Code) scope — I18N-01 through I18N-05 are all addressed here
- **D-05:** Test files (src/test) are included in the scan and conversion
- **D-06:** Only true proper nouns are allowed to remain German — GT7 data (track names like "Nurburgring"), place names that are inherently German
- **D-07:** Umlaut-handling code (e.g., `replaceAll("[äÄ]", "ae")` in SiteGeneratorService) stays as-is — this is character transformation logic, not German text
- **D-08:** Template preview sample data (e.g., `"Nürburgring 24h"` in TemplatePreviewService) stays as-is — this is GT7 track data
- **D-09:** One-time verification only — no permanent guard test against German text re-entering
- **D-10:** After this phase, reliance on code review to maintain English-only convention

### Claude's Discretion

- Exact order of file processing (by package, by file type, etc.)
- How to handle edge cases where translation is ambiguous
- Grouping of changes into commits

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| I18N-01 | All German log messages converted to English | Already complete per CONTEXT.md: 174 log statements already in English |
| I18N-02 | All German exception messages converted to English | Already complete per CONTEXT.md: 211 throw/orElseThrow already in English |
| I18N-03 | All German constants, enum labels, and string literals converted to English | None found in scan — verified clean |
| I18N-04 | All German code comments and Javadoc converted to English | 6 locations found — see Complete German Text Inventory |
| I18N-05 | All German variable and method names renamed to English equivalents | None found in scan — verified clean |

</phase_requirements>

---

## Summary

Phase 20 is a pure text-only refactoring phase. The CONTEXT.md pre-analysis was accurate: log messages, exception messages, and flash messages are already in English. The remaining German text is confined to inline code comments and configuration file comments — no German appears in string literals, method names, variable names, constants, enum labels, or Thymeleaf templates.

The full scope is small and surgical: **19 lines across 4 files**. Three of the files are source code (TestDataService.java, AdminWorkflowE2ETest.java), and two are configuration files (application.yml, logback-spring.xml) and one Dockerfile. Changes are text-only — no logic, no test fixtures, no API contracts, no Flyway migrations are touched.

**Primary recommendation:** Process all 4 files in a single wave. The changes are independent and low-risk. Run `./mvnw verify` after changes to confirm no coverage regression.

---

## Complete German Text Inventory

This is the exhaustive list of German text found by direct codebase scan. [VERIFIED: codebase grep]

### File 1: `src/main/java/org/ctc/admin/TestDataService.java`

| Line | Current German Text | English Translation |
|------|---------------------|---------------------|
| 462 | `// Sub-Team-Zuordnungen werden NICHT geseeded — die kommen aus dem Import` | `// Sub-team assignments are NOT seeded — they come from the CSV import` |
| 463 | `// (ensureSeasonDriver aktualisiert das Team bei erneutem Import)` | `// (ensureSeasonDriver updates the team assignment on re-import)` |
| 464 | `// Hier nur Parent-Team-Zuordnungen als Platzhalter fuer Entwicklung ohne Import` | `// Only parent-team assignments as placeholders for development without import` |
| 503 | `// === Komplett isolierte Testdaten (kein Bezug zu echten Teams/Fahrern) ===` | `// === Fully isolated test data (no relation to real teams/drivers) ===` |
| 505 | `// Test-Teams` | `// Test teams` |
| 511 | `// Test-Fahrer` | `// Test drivers` |

### File 2: `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java`

| Line | Current German Text | English Translation |
|------|---------------------|---------------------|
| 175 | `// T-ALF (Test Alpha Racing) hat Fahrer in Test-Season 2026 und 2025` | `// T-ALF (Test Alpha Racing) has drivers in Test-Season 2026 and 2025` |
| 189 | `// Fahrer im DOM vorhanden (Accordion muss nicht offen sein)` | `// Drivers are present in DOM (accordion does not need to be open)` |
| 197 | `// T-BRV (Test Bravo Racing) hat Sub-Teams T-BRV 1 + T-BRV 2` | `// T-BRV (Test Bravo Racing) has sub-teams T-BRV 1 + T-BRV 2` |
| 216 | `// T-ALF hat Lineups in Test-Season 2026 + Test-Season 2025` | `// T-ALF has lineups in Test-Season 2026 + Test-Season 2025` |

### File 3: `src/main/resources/application.yml`

| Line | Current German Text | English Translation |
|------|---------------------|---------------------|
| 13 | `# OSIV: Hibernate-Session bleibt bis zum Ende des HTTP-Requests offen,` | `# OSIV: Hibernate session stays open until the end of the HTTP request,` |
| 14 | `# damit Thymeleaf-Templates lazy-geladene Felder rendern koennen.` | `# so that Thymeleaf templates can render lazy-loaded fields.` |
| 15 | `# Bewusste Entscheidung fuer diese Admin-Anwendung (kein REST-API).` | `# Deliberate decision for this admin application (no REST API).` |
| 26 | `# OSIV-Warnung unterdruecken (bewusst aktiviert, siehe spring.jpa.open-in-view)` | `# Suppress OSIV warning (deliberately enabled, see spring.jpa.open-in-view)` |
| 39 | `# Statische Seitengenerierung` | `# Static site generation` |
| 44 | `# Actuator Health-Endpoint (fuer Docker Healthchecks)` | `# Actuator health endpoint (for Docker health checks)` |

### File 4: `src/main/resources/logback-spring.xml`

| Line | Current German Text | English Translation |
|------|---------------------|---------------------|
| 4 | `<!-- Spring Boot Defaults (CONSOLE_LOG_PATTERN, Farben, etc.) -->` | `<!-- Spring Boot defaults (CONSOLE_LOG_PATTERN, colors, etc.) -->` |
| 7 | `<!-- Log-Verzeichnis: profil-abhaengig -->` | `<!-- Log directory: profile-dependent -->` |
| 21 | `<!-- Console Appender (Spring Boot Default-Pattern) -->` | `<!-- Console appender (Spring Boot default pattern) -->` |
| 29 | `<!-- File Appender mit Rolling Policy -->` | `<!-- File appender with rolling policy -->` |
| 37 | `<!-- Taegliche Rotation mit Groessen-Split -->` | `<!-- Daily rotation with size-based split -->` |
| 39 | `<!-- Max Groesse pro Datei bevor Split innerhalb eines Tages -->` | comment is inline in existing XML, just translate |
| 41 | `<!-- Archive aelter als 7 Tage loeschen -->` | `<!-- Delete archives older than 7 days -->` |
| 43 | `<!-- Gesamtlimit ueber alle archivierten Dateien -->` | `<!-- Total size limit across all archived files -->` |
| 45 | `<!-- Beim Start alte Archive aufraeumen -->` | `<!-- Clean up old archives on startup -->` |

### File 5: `Dockerfile`

| Line | Current German Text | English Translation |
|------|---------------------|---------------------|
| 6 | `# Maven Wrapper und pom.xml kopieren fuer Dependency-Caching` | `# Copy Maven Wrapper and pom.xml for dependency caching` |
| 11 | `# Dependencies herunterladen (gecached solange pom.xml sich nicht aendert)` | `# Download dependencies (cached as long as pom.xml does not change)` |
| 14 | `# Source kopieren und bauen` | `# Copy source and build` |
| 21 | `# curl fuer Healthcheck + Chromium-Dependencies fuer Playwright installieren` | `# Install curl for health check + Chromium dependencies for Playwright` |
| 27 | `# Non-root User erstellen` | `# Create non-root user` |
| 32 | `# Verzeichnisse fuer Uploads und Site-Output` | `# Create directories for uploads and site output` |
| 35 | `# JAR aus Build-Stage kopieren` | `# Copy JAR from build stage` |
| 38 | `# Playwright Chromium-Browser installieren (fuer Team Card Generierung)` | `# Install Playwright Chromium browser (for team card generation)` |

---

## Verified Clean Areas

The following areas were scanned and confirmed to contain NO German text. [VERIFIED: codebase grep]

| Area | Verification Method | Result |
|------|---------------------|--------|
| Log messages (174 statements) | Grep for umlaut + common German words in log.info/debug/warn/error | Clean |
| Exception messages (211 statements) | Grep for umlaut + common German words in throw/orElseThrow | Clean |
| Flash messages (controllers) | Grep for German in "successMessage"/"errorMessage" | Clean |
| Thymeleaf templates (55 admin + 8 site) | Grep for umlauts and German words in .html files | Clean |
| YAML config files (except application.yml) | Grep for German in application-{dev,local,docker,prod}.yml | Clean |
| docker-compose.yml / docker-compose.prod.yml | Grep for German | Clean |
| pom.xml | Grep for German | Clean |
| GitHub workflow files (.github/) | Grep for German | Clean |
| Flyway migration SQL files | Grep for German (NOT to be changed) | Clean — no German found |
| String literals / constants | Grep for German words in string context | Clean |
| Variable / method names | Grep for German identifiers | Clean |
| Enum labels | Inspected SeasonFormat, AttachmentType | Clean |

**Umlaut exceptions (DO NOT CHANGE — per D-07, D-08):**
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:303` — `replaceAll("[äÄ]", "ae")` — character transformation logic
- `src/main/java/org/ctc/admin/service/TemplatePreviewService.java:91` — `"Nürburgring 24h"` — GT7 track data
- `src/test/java/org/ctc/admin/service/TemplatePreviewServiceTest.java:112` — `assertThat(html).contains("Nürburgring 24h")` — test verifying the above

---

## Architecture Patterns

### This Phase is Text-Only

No structural patterns are needed. The changes are:
- Replace comment text (no logic change)
- No method renames
- No variable renames
- No entity/DTO/service changes
- No template changes
- No migration changes (Flyway files were already clean)

### Change Safety

All changes are in:
1. Inline comments (`//` and `/* */` and `<!-- -->`) — zero runtime impact
2. YAML comments (`#`) — zero runtime impact
3. Dockerfile comments (`#`) — zero runtime impact

No compilation risk. No test logic change. The tests in `AdminWorkflowE2ETest.java` being modified contain only comment text — the actual test code (assertions, locators) is already in English and is not touched.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Verifying no German remains | Custom script | `grep -r` with German word patterns | Already the agreed approach (D-01) |
| Permanent guard test | JUnit test scanning source files | Code review (D-10) | User explicitly decided against guard test |

---

## Common Pitfalls

### Pitfall 1: Accidentally modifying Flyway migrations
**What goes wrong:** Developer edits a V1 or V2 migration SQL comment → Flyway checksum mismatch → application fails to start.
**Why it happens:** SQL files in `src/main/resources/db/migration/` contain comments — a broad file sweep may touch them.
**How to avoid:** Flyway migration files have no German text (verified). Do not open them. CLAUDE.md explicitly forbids changes.
**Warning signs:** Any edit to a `V1__*.sql` or `V2__*.sql` file is a bug.

### Pitfall 2: Changing umlaut-handling regex patterns
**What goes wrong:** The `replaceAll("[äÄ]", "ae")` pattern in SiteGeneratorService is modified because it contains German characters.
**Why it happens:** A naive search-and-replace of umlauts may catch character class patterns.
**How to avoid:** The umlaut characters in this file are inside a regex character class `[...]` — the intent is to match those characters, not to display them as German text. Per D-07, leave as-is.
**Warning signs:** Any edit to `SiteGeneratorService.java` line 303.

### Pitfall 3: Breaking E2E test assertions
**What goes wrong:** Developer rewrites test comment + accidentally modifies adjacent assertion or locator string.
**Why it happens:** Comments in E2E tests are adjacent to Playwright locator strings.
**How to avoid:** Edit only the `//` comment lines in AdminWorkflowE2ETest. Do not touch `page.locator(...)` or `assertThat(...)` lines.
**Warning signs:** E2E test failures on `./mvnw verify -Pe2e`.

### Pitfall 4: Coverage regression from TestDataService changes
**What goes wrong:** Coverage drops below 82% because a change accidentally broke a branch.
**Why it happens:** TestDataService changes are comment-only — but if a surrounding code line is accidentally altered, it may not compile.
**How to avoid:** Comment changes only. Run `./mvnw verify` to confirm coverage after changes.
**Warning signs:** JaCoCo output shows below 82% or Maven verify fails.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Maven Surefire/Failsafe + JaCoCo |
| Config file | `pom.xml` |
| Quick run command | `./mvnw verify` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| I18N-01 | No German log messages | N/A — already English, no test needed | — | N/A |
| I18N-02 | No German exception messages | N/A — already English, no test needed | — | N/A |
| I18N-03 | No German string literals/constants | Grep verification at phase end | `grep -r '[äöüÄÖÜß]' src/main/` | ❌ manual |
| I18N-04 | No German comments or Javadoc | Grep verification at phase end | `grep -r '[äöüÄÖÜß]' src/` | ❌ manual |
| I18N-05 | No German variable/method names | N/A — none found in scan | — | N/A |

**Sampling rate:**
- Per task commit: `./mvnw verify` (unit + integration + coverage gate)
- Phase gate: `./mvnw verify -Pe2e` (full suite including Playwright E2E)

### Wave 0 Gaps

None — no new test files required. This phase makes comment-only changes. The existing test suite validates that no test logic was accidentally broken.

---

## Runtime State Inventory

> Phase 20 is a pure source-code text change (comments and config comments only). No runtime state is affected.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — comment changes do not affect database | None |
| Live service config | None — YAML config values unchanged, only YAML comments | None |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | None — comment changes do not change bytecode | None |

---

## Environment Availability

> Step 2.6: SKIPPED — this phase is purely source-code comment changes. No external dependencies are introduced or required.

---

## Security Domain

> No security-relevant changes in this phase. All modifications are to source code comments and configuration file comments. No authentication, input validation, session management, cryptography, or access control is affected.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| — | — | — | — |

**All claims in this research were verified by direct codebase grep scan — no assumed knowledge required.**

---

## Open Questions

None. The full set of German text was identified by direct scan. The scope is completely known.

---

## Sources

### Primary (HIGH confidence)
- Direct codebase grep scan — all 5 affected files identified by exhaustive pattern matching
- `src/main/java/org/ctc/admin/TestDataService.java` — 6 German comment lines confirmed
- `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` — 4 German comment lines confirmed
- `src/main/resources/application.yml` — 6 German comment lines confirmed
- `src/main/resources/logback-spring.xml` — 9 German comment lines confirmed
- `Dockerfile` — 8 German comment lines confirmed
- `.planning/phases/20-english-messages/20-CONTEXT.md` — Phase boundary and decisions
- `CLAUDE.md` — Language policy, Flyway constraint, testing requirements

---

## Metadata

**Confidence breakdown:**
- German text inventory: HIGH — verified by direct grep scan of entire codebase
- Clean areas: HIGH — verified by grep with no matches
- Architecture: HIGH — text-only changes, no patterns needed
- Pitfalls: HIGH — based on Flyway constraint in CLAUDE.md and D-07/D-08 allowlist

**Research date:** 2026-04-07
**Valid until:** Stable — comment changes are not time-sensitive
