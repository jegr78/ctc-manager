# Phase 20: English Messages - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Convert ALL German text in the entire project to English. This covers log messages, exception messages, flash messages, string constants, enum labels, comments, Javadoc, variable names, method names, and configuration text. Effectively combines I18N-01 through I18N-05 (originally split across Phase 20 and Phase 21) into a single phase. Only true proper nouns (GT7 track names like "Nurburgring", umlaut-handling code patterns) remain as-is.

</domain>

<decisions>
## Implementation Decisions

### Verification Approach
- **D-01:** Use grep-based scan across ALL files in the project (not just src/main/java) to find German text
- **D-02:** Scan covers all file types — .java, .html, .xml, .yml, .properties, .sql, .md (project docs excluded from changes but scanned for awareness)

### Scope
- **D-03:** Full-project scope — every German text in every file, including comments, Javadoc, variable/method names, string literals, constants, enum labels, configs
- **D-04:** This phase absorbs Phase 21 (English Code) scope — I18N-01 through I18N-05 are all addressed here
- **D-05:** Test files (src/test) are included in the scan and conversion

### Allowlist (Permitted German Text)
- **D-06:** Only true proper nouns are allowed to remain German — GT7 data (track names like "Nurburgring"), place names that are inherently German
- **D-07:** Umlaut-handling code (e.g., `replaceAll("[äÄ]", "ae")` in SiteGeneratorService) stays as-is — this is character transformation logic, not German text
- **D-08:** Template preview sample data (e.g., `"Nürburgring 24h"` in TemplatePreviewService) stays as-is — this is GT7 track data

### Guard Tests
- **D-09:** One-time verification only — no permanent guard test against German text re-entering
- **D-10:** After this phase, reliance on code review to maintain English-only convention

### Claude's Discretion
- Exact order of file processing (by package, by file type, etc.)
- How to handle edge cases where translation is ambiguous
- Grouping of changes into commits

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — I18N-01 through I18N-05 requirements for English cleanup

### Conventions
- `.planning/codebase/CONVENTIONS.md` — Naming patterns, Lombok usage, entity patterns
- `.planning/codebase/STRUCTURE.md` — Directory layout and file organization
- `CLAUDE.md` — Language policy: "Communication: German, Documentation/Code/Comments/UI Texts: English"

</canonical_refs>

<code_context>
## Existing Code Insights

### Current State
- 174 log statements across all services — already in English
- 211 throw/orElseThrow statements — already in English
- Flash messages in controllers — already in English
- Umlauts in string literals — only in GT7 data and umlaut-handling code

### Key Files to Scan
- `src/main/java/org/ctc/` — All production Java source (17 domain services, 18 controllers, 15 admin services, 16 DTOs)
- `src/test/java/org/ctc/` — All test source
- `src/main/resources/templates/` — 55 admin + 8 site Thymeleaf templates
- `src/main/resources/` — application*.yml, logback-spring.xml, Flyway migrations
- Project root — Dockerfile, docker-compose files, pom.xml, CLAUDE.md

### Established Patterns
- `log.info()` with parameterized `{}` format — consistent across codebase
- `EntityNotFoundException("Entity", id)` pattern — standardized
- Flash attributes via `"successMessage"` / `"errorMessage"` keys

### Integration Points
- No integration changes needed — this is a text-only refactoring
- Flyway migrations must NOT be changed (checksum-protected)
- UI-visible texts in Thymeleaf templates may need English conversion

</code_context>

<specifics>
## Specific Ideas

- Scan the entire project, not just src/main — includes test files, configs, and all file types
- Messages are already English — primary work is finding remaining German in comments, Javadoc, variable/method names, and non-Java files

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 20-english-messages*
*Context gathered: 2026-04-07*
