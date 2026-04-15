# Phase 21: English Code - Context

**Gathered:** 2026-04-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace all remaining German text in the codebase with English equivalents. This covers test data strings, HTML template comments, and a final verification scan. Production Java source (methods, variables, constants, enums, logs, exceptions) is already fully English from prior work.

Requirements: I18N-03, I18N-04, I18N-05

</domain>

<decisions>
## Implementation Decisions

### Scope
- **D-01:** Focus on confirmed remaining German items + verification scan (not a full re-scan of the entire project)
- **D-02:** Production Java source is already English — no changes needed there
- **D-03:** Carries forward Phase 20 decisions: only true proper nouns (GT7 data) allowed to remain German, umlaut-handling code stays as-is

### Test Data Strings
- **D-04:** Replace all 27 occurrences of `"Spieltag N"` with `"Matchday N"` across 3 test files (StandingsServiceTest, StandingsControllerTest, SiteGeneratorServiceTest)
- **D-05:** Update slug assertion in SiteGeneratorServiceTest from `spieltag-1.html` to `matchday-1.html` to match the changed test data

### HTML Comments
- **D-06:** Translate 3 German HTML comments to English:
  - `team-detail.html:82` — `<!-- Seasons ohne Fahrer -->` → `<!-- Seasons without drivers -->`
  - `matchday-detail.html:69` — `<!-- Legs (nur anzeigen bei Multi-Leg oder wenn Legs vorhanden) -->` → `<!-- Show legs only for multi-leg or when legs exist -->`
  - `matchday-detail.html:87` — `<!-- Single-Leg: direkt Link zum Race -->` → `<!-- Single-leg: direct link to race -->`

### Verification
- **D-07:** Final grep-based verification scan using a defined word list of common German words (Spieltag, Saison, Fahrer, Mannschaft, Rennen, Punkte, Ergebnis, Wertung, Tabelle, Gruppe, Runde, etc.) plus umlaut scan across all .java and .html files
- **D-08:** Allowlist for GT7 proper nouns (Nurburgring, etc.) and umlaut-handling code patterns (replaceAll with umlauts)
- **D-09:** One-time verification only — no permanent guard test (carried from Phase 20 D-09)

### Claude's Discretion
- Exact German word list for the verification grep scan
- Order of file changes
- Whether to combine all changes into one commit or split by category

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — I18N-03, I18N-04, I18N-05 requirements for English cleanup

### Prior Phase Context
- `.planning/phases/20-english-messages/20-CONTEXT.md` — Phase 20 decisions (D-06 allowlist, D-07 umlaut-handling, D-09 no guard test)

### Conventions
- `CLAUDE.md` — Language policy: "Communication: German, Documentation/Code/Comments/UI Texts: English"

</canonical_refs>

<code_context>
## Existing Code Insights

### Files to Change
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — 24× "Spieltag N"
- `src/test/java/org/ctc/admin/controller/StandingsControllerTest.java` — 1× "Spieltag 1"
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — 1× "Spieltag 1" + 1× slug assertion "spieltag-1.html"
- `src/main/resources/templates/admin/team-detail.html` — 1 German comment
- `src/main/resources/templates/admin/matchday-detail.html` — 2 German comments

### Established Patterns
- Matchday names are user-provided strings passed to constructor: `new Matchday(season, "name", orderNum)`
- SiteGeneratorService slugifies matchday labels for file names via `slugify()` method
- Phase 20 already established the pattern of scanning with grep and using an allowlist

### Integration Points
- No integration changes — text-only refactoring in tests and comments
- Slug change in test only affects test assertions, not production behavior

</code_context>

<specifics>
## Specific Ideas

No specific requirements — straightforward find-and-replace plus verification scan.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 21-english-code*
*Context gathered: 2026-04-08*
