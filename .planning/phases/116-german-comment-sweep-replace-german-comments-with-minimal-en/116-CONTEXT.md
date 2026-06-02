# Phase 116: German Comment Sweep - Context

**Gathered:** 2026-06-02
**Status:** Ready for planning
**Source:** Orchestrator inventory scan + user scope decisions (no discuss-phase needed — mechanical sweep)

<domain>
## Phase Boundary

German comments crept into the codebase via two Claude-Design handoffs during the v1.14/v1.15 milestones, violating two CLAUDE.md rules: "Language" (Documentation, Code, Comments, and UI Texts: English) and "No Comment Pollution". This phase finds every remaining German comment and either translates it to concise English or removes it where redundant.

**Comments only. No behavioral change.** `./mvnw clean verify -Pe2e` must stay green and coverage must hold. String literals, UI texts, log messages, and test data are NOT in scope (they were separately verified clean — see Deferred).

`src/main/java` is already clean (0 German comments). The hotspots are Thymeleaf templates, `application.yml`, `admin.css`, and several test classes.
</domain>

<decisions>
## Implementation Decisions (LOCKED)

### Scope — "Everything incl. tests" (user-selected)
Templates + `application.yml` + `admin.css` + all German test comments are in scope. Legitimate German string literals stay untouched.

### Reduction policy — "Remove redundant, shorten the rest" (user-selected)
- Obvious/redundant comments (e.g. `<!-- Saison-Stamm-Display -->`, the `admin.css` Phase-60 marker) are **removed entirely**.
- Genuine "why" comments (OSIV rationale in `application.yml`, the single-leg branch note) are **translated to concise English and kept**.
- Per CLAUDE.md "No Comment Pollution": no Phase/Plan/Task/UAT markers, no file-header restatements, no greppable cross-references.

### "Saison" → "Season" in test comments
The dev fixture is literally named `Season 2023` (`TestDataService.java:178` — `createSeason("Season 2023", ...)`). Test comments that write "Saison 2023" are therefore using the German word incorrectly; they must become "Season 2023" to match the actual fixture name. This is a comment-text fix, not a code/data change.

### No new comments invented
Removing a redundant comment is preferred over inventing an English replacement for something self-evident.
</decisions>

<inventory>
## Verified Inventory (exact targets — grep-confirmed 2026-06-02)

### A. Thymeleaf templates (CLEAN-01) — 3 files, 4 comment blocks
- `src/main/resources/templates/admin/matchday-detail.html:105` — `<!-- Single-Leg: direkt Link zum Race -->` → keep as concise English ("why" for the single-leg branch).
- `src/main/resources/templates/admin/provisional-scores-render.html:209` — `<!-- nur anzeigen, wenn das Match mehr als ein Rennen hat (Service: raceLabel sonst null setzen) -->` → concise English (non-obvious render condition).
- `src/main/resources/templates/admin/season-detail.html:6` — `<!-- Saison-Header: saison-wide actions only -->` → concise English or remove.
- `src/main/resources/templates/admin/season-detail.html:21` — `<!-- Saison-Stamm-Display -->` → **remove** (redundant).

### B. Config / static resources (CLEAN-02)
- `src/main/resources/application.yml:28-30` — OSIV rationale block (German) → concise English ("why" — keep, it documents a deliberate decision).
- `src/main/resources/application.yml:41` — `# OSIV-Warnung unterdruecken (bewusst aktiviert, ...)` → concise English.
- `src/main/resources/application.yml:83` — `# Actuator Health-Endpoint (fuer Docker Healthchecks)` → concise English or remove.
- `src/main/resources/static/admin/css/admin.css:2034` — `/* === Phase 60: Two-row tabs (Saison-Detail + Standings) === */` → **double violation** (German + banned Phase marker). Remove the Phase marker; keep at most a minimal English section label or remove entirely.

### C. Test sources (CLEAN-03)
"Saison" → "Season" (matches fixture name) and full German sentences → English:
- `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java:177` — `// T-ALF (Test Alpha Racing) hat Fahrer in Test-Season 2026 und 2025` → English.
- `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java:191` — `// Fahrer im DOM vorhanden (Accordion muss nicht offen sein)` → English ("why").
- `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java:27,30,41` — German Javadoc + workflow description + the German mandate quote `"UI-Klick-Eintragung für Race-Results"` → English.
- `src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java:56,98` — `Saison-Detail ...` → `Season detail ...`.
- `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` (lines ~85,86,88,142,144,207) — "Saison 2023" → "Season 2023".
- `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java:24` — "Saison 2023" → "Season 2023".
- `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java:47` — "Saison 2023" → "Season 2023".
- `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` (lines ~54,160,162) — "Saison 2023" → "Season 2023".
- `src/test/java/org/ctc/backup/service/FailAtTableInjector.java` (lines ~23,73) — "Saison 2023" → "Season 2023".
- `src/test/java/org/ctc/backup/restore/entity/RaceResultRestorerTest.java:22` — "Saison-2023" → "Season-2023".

> The planner MUST re-run the detection scan during planning to catch any occurrence this snapshot missed (line numbers may drift). The grep recipe is in `<specifics>`.

### Out (verified legitimate — DO NOT touch)
- `src/main/java/org/ctc/sitegen/SiteSlugger.java:11` — umlaut transliteration regex (`[äÄ]`→`ae`, etc.). Functional code.
- `src/main/java/org/ctc/admin/service/TemplatePreviewService.java:99` — `"Nürburgring 24h"` real track name (string literal).
- `src/test/java/org/ctc/backup/restore/entity/PlayoffRestorerTest.java:59,99` — `"Saison 2023 Playoffs"` is a test-data **string-literal pair** (JSON input + assertion). Not a comment; changing it would alter test data. Out of scope under the locked "comments only" decision.
- `src/main/resources/static/admin/js/*.js` — scanned, 0 German comments.
</inventory>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project conventions (binding)
- `CLAUDE.md` → "Language" (English-only for comments), "Conventions / No Comment Pollution" (remove redundant comments; no Phase/Plan/Task markers; remove pollution from touched files), "Build & Test Discipline" (clean build is source of truth; one full `clean verify -Pe2e` at phase end), "Verify-Kadenz" memory (targeted `-Dtest=`/`-Dit.test=` during the phase, single full run at the end).

### Source of truth for the "Saison"→"Season" decision
- `src/main/java/org/ctc/admin/TestDataService.java:178` — `createSeason("Season 2023", ...)` proves the fixture is English-named.
</canonical_refs>

<specifics>
## Detection recipe (for the planner's re-scan + the CLEAN-04 verification)

German-comment detection across all comment-bearing types. A finding is German if a comment line/block contains an umlaut (`äöüßÄÖÜ`) OR a German function word (`der|die|das|und|oder|nicht|für|mit|von|auf|ist|sind|wird|werden|eine|keine|muss|soll|wenn|dann|hier|diese|wegen|damit|siehe|Hinweis|über|nach|durch|nur|kein|sonst|jeweils|anzeigen|ausblenden|direkt|Saison|Rennen|Spieler|Fahrer|Punkte`).

- HTML comments: extract `<!-- ... -->` blocks (multi-line aware), flag German.
- Java comments: `//`, `/* */`, `* ` (Javadoc) lines — flag German. **Exclude string literals** (a German word inside `"..."` that is data/UI/track-name is not a comment).
- YAML/properties: `#` comment lines.
- CSS: `/* ... */` blocks.
- `pom.xml`/SQL: `<!-- -->` / `--` / `/* */` (currently 0 found — confirm still 0).

**CLEAN-04 acceptance:** the same scan, run after edits, returns zero German *comment* findings (string-literal exclusions documented above), AND `./mvnw clean verify -Pe2e` is green with coverage ≥ the pom threshold.
</specifics>

<deferred>
## Deferred / Out of Scope
- German string literals, UI texts, log messages: verified clean except the legitimate proper nouns listed in `<inventory>` "Out". No work item.
- `PlayoffRestorerTest` "Saison 2023 Playoffs" test-data pair: out of scope (string literal, not a comment).
- Any broader i18n / German-to-English UI-text effort: not part of this phase.
</deferred>

---

*Phase: 116-german-comment-sweep*
*Context gathered: 2026-06-02 via orchestrator inventory scan + user scope decisions*
