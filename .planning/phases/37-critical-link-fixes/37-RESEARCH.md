# Phase 37: Critical Link Fixes - Research

**Researched:** 2026-04-16
**Domain:** Static site generation — Java path computation, Thymeleaf context variables, file copying
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Pass pre-computed season slug from `SiteGeneratorService` to templates as a context variable (e.g., `seasonSlug`), instead of having `archive.html` re-slugify `season.name` with Thymeleaf `#strings`. Single source of truth: the `slugify()` method in the service.
- **D-02:** The archive template link pattern becomes `'season/' + ${seasonSlug} + '/standings.html'` — no Thymeleaf string manipulation in the template.
- **D-03:** Pass the active season's slug to the layout template as a context variable (e.g., `activeSeasonSlug`). The nav "Driver Ranking" link becomes `rootPath + '/season/' + activeSeasonSlug + '/driver-ranking.html'` instead of the current root-level `rootPath + '/driver-ranking.html'`.
- **D-04:** All pages must receive `activeSeasonSlug` in their template context. This requires `writeTemplate()` or each `generate*()` method to set this variable. The active season is already loaded in `generate()`.
- **D-05:** In `writeTemplate()`, when `relativeRoot.toString()` is empty (root-level files), default `rootPath` to `"."` — standard relative path convention. This makes root-level links `"./index.html"` instead of `"/index.html"`.
- **D-06:** During site generation, copy team logo files from the upload directory to the static site assets directory (e.g., `assets/img/logos/`). Rewrite `team.logoUrl` in the template context to a relative path within the static site (e.g., `assetsPath + '/img/logos/' + filename`).
- **D-07:** If a team has no logo (null `logoUrl`), skip the copy — the existing `th:if="${team.logoUrl}"` guard in the template already handles this.
- **D-08:** Logo files that don't exist on disk (e.g., stale URLs) should be silently skipped with a warning log, not cause generation failure.

### Claude's Discretion
- Exact error handling for missing logo files (warn vs. debug log level)
- Whether to also fix the Standings nav link to point to active season (currently points to root index.html which shows standings — may already work correctly)
- Internal refactoring of `writeTemplate()` to reduce repetition in setting context variables

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LINK-01 | Archive page links use slugified displayLabel matching actual directory names | Archive template uses `s.name` via Thymeleaf `#strings`, but service slugifies `getDisplayLabel()`. Fix: pass `seasonSlug` context variable per D-01/D-02. |
| LINK-02 | Nav "Driver Ranking" link resolves to active season's driver-ranking page | `layout.html` hard-codes `rootPath + '/driver-ranking.html'` which doesn't exist. Fix: inject `activeSeasonSlug` per D-03/D-04. |
| LINK-03 | All navigation links use relative paths (not absolute /index.html) | Root-level files get empty `rootPath` string, producing `/index.html` (absolute). Fix: default to `"."` per D-05. |
| LINK-04 | Team logo images resolve correctly on static site pages | `team.logoUrl` contains `/uploads/teams/{uuid}/{filename}` — a server path, not a static site asset. Fix: copy logos and rewrite path per D-06/D-07/D-08. |
</phase_requirements>

---

## Summary

Phase 37 fixes four distinct bugs in `SiteGeneratorService` and the Thymeleaf site templates. All bugs have known root causes verified by reading the source code. No new features, no schema changes — the work is constrained to `SiteGeneratorService.java`, `layout.html`, and `archive.html`.

**LINK-01** (archive slug mismatch): `archive.html` builds the season directory link using Thymeleaf's `#strings.toLowerCase(#strings.replace(s.name, ' ', '-'))`, but the service creates directories using `slugify(season.getDisplayLabel())` where `getDisplayLabel()` returns `"year | #number | name"`. These produce different strings. The fix is to pass a pre-computed `seasonSlug` into the archive template context for each season in the `seasons` list — requiring a wrapper or by converting seasons to a list of season-slug pairs.

**LINK-02** (driver ranking 404): `layout.html` links Driver Ranking to `rootPath + '/driver-ranking.html'`, but no such file exists at the root — driver ranking pages live at `season/{slug}/driver-ranking.html`. The active season slug must be passed to all templates as `activeSeasonSlug`, then the nav link updated to use it.

**LINK-03** (absolute paths on root pages): `writeTemplate()` calculates `rootPath` as `outputFile.getParent().relativize(outRoot)`. For root-level files (index.html, archive.html), this yields an empty string. When the template evaluates `rootPath + '/index.html'` it produces `/index.html` (absolute). Defaulting empty `rootPath` to `"."` yields `./index.html` — a valid relative path that works when the site is opened from any location.

**LINK-04** (team logos): `team.logoUrl` stores server-side paths like `/uploads/teams/{uuid}/{filename}`. These are served by Spring's `WebConfig` resource handler at runtime, but have no meaning in a static site. The fix copies logo files to `assets/img/logos/` and passes a relative path (`assetsPath + '/img/logos/{filename}'`) as a template context variable to replace the entity's `logoUrl`.

**Primary recommendation:** All four fixes are changes to `SiteGeneratorService.java` (Java logic) plus small updates to `layout.html` and `archive.html` (template expressions). Each fix is independent and can be implemented and tested as a separate task.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Season slug computation | Backend Service | — | `slugify()` lives in service; single source of truth |
| Template path variables (assetsPath, rootPath, activeSeasonSlug) | Backend Service | — | All path computation done in Java, passed to templates |
| Nav link rendering | Frontend (Thymeleaf) | — | `layout.html` consumes context variables set by service |
| Archive link rendering | Frontend (Thymeleaf) | — | `archive.html` consumes `seasonSlug` context variable |
| Logo file copy | Backend Service | Filesystem | `SiteGeneratorService` reads from uploadDir, writes to outputDir |
| Logo path rewriting | Backend Service | — | Service sets `teamLogoRelPath` context variable; template consumes it |

---

## Standard Stack

All technology is already present in the project — no new dependencies.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Thymeleaf | (Spring Boot 4.x managed) | Server-side HTML templating | Already used for all site templates |
| Spring Framework Path | (JDK 25) | `java.nio.file.Path` for relative path computation | Used in `writeTemplate()` today |
| Jsoup | (already in pom.xml) | HTML assertion in integration tests | Used in `SiteGeneratorServiceTest` already |

### No New Dependencies Required
All fixes are Java logic and Thymeleaf expression changes within the existing codebase.

**Version verification:** All libraries already managed by Spring Boot 4.x BOM. [VERIFIED: pom.xml and existing code]

---

## Architecture Patterns

### System Architecture Diagram

```
[generate() entry point]
        |
        +-- activeSeason loaded once
        |   activeSeasonSlug = slugify(activeSeason.getDisplayLabel())  [NEW]
        |
        +-- generateIndex(outPath, activeSeason, allSeasons, activeSeasonSlug, result)
        +-- for each season:
        |     generateStandings(outPath, season, activeSeasonSlug, result)
        |     generateDriverRanking(outPath, season, activeSeasonSlug, result)
        |     generateMatchdays(outPath, season, activeSeasonSlug, result)
        |     generateTeamProfiles(outPath, season, activeSeasonSlug, result)  [logo copy here]
        |     generateDriverProfiles(outPath, season, activeSeasonSlug, result)
        |     generatePlayoffBracket(outPath, season, activeSeasonSlug, result)
        +-- generateArchive(outPath, allSeasons, activeSeasonSlug, result)  [seasonSlug per season]
        +-- copyAssets(outPath, result)
        |
        v
[writeTemplate(templateName, context, outputFile)]
        |
        +-- compute assetsPath (unchanged — already correct)
        +-- compute rootPath: if empty -> "."  [LINK-03 FIX]
        +-- context.setVariable("activeSeasonSlug", activeSeasonSlug)  [LINK-02 FIX]
        +-- templateEngine.process(...)
        +-- Files.writeString(outputFile, html)
```

Logo copy flow (LINK-04):
```
generateTeamProfiles()
        |
        for each team with teamStanding:
        +-- if team.logoUrl starts with "/uploads/":
        |     extract filename from logoUrl
        |     source = uploadDir / logoUrl.substring("/uploads/".length())
        |     target = outPath / "assets/img/logos/" / filename
        |     Files.copy(source, target, REPLACE_EXISTING)
        |     teamLogoRelPath = assetsPath + "/img/logos/" + filename
        +-- ctx.setVariable("teamLogoRelPath", teamLogoRelPath)  [null if no logo]
```

### Recommended Project Structure
No new directories. Additions to existing structure:
```
docs/site/
├── assets/
│   ├── css/
│   ├── img/
│   │   ├── ctc-logo-white.png
│   │   └── logos/          # NEW: copied team logos
│   │       └── {uuid}/{filename}.png
```

### Pattern 1: Pre-computed context variables
**What:** Service computes all path/slug values in Java before passing to Thymeleaf. Templates contain zero logic — only variable interpolation.
**When to use:** Any time a template would otherwise need `#strings`, `#dates`, or conditional expressions beyond null-guards.
**Example:**
```java
// Source: SiteGeneratorService.writeTemplate() — existing pattern
context.setVariable("assetsPath", relativeAssets.toString().replace('\\', '/'));
context.setVariable("rootPath", relativeRoot.toString().isEmpty() ? "." : relativeRoot.toString().replace('\\', '/'));
context.setVariable("activeSeasonSlug", activeSeasonSlug);
```

### Pattern 2: Season slug passed per-season for archive
**What:** When iterating over all seasons for the archive page, each season needs its own slug. The template cannot call `slugify()`. The service must pass a list of pairs or a map.
**When to use:** When a template iterates a collection and needs a derived value per item.
**Example:**
```java
// Pass list of records (or maps) pairing season with its pre-computed slug
record SeasonEntry(Season season, String slug) {}
var seasonEntries = allSeasons.stream()
    .map(s -> new SeasonEntry(s, slugify(s.getDisplayLabel())))
    .toList();
ctx.setVariable("seasonEntries", seasonEntries);
```
Then in archive.html:
```html
<tr th:each="entry : ${seasonEntries}">
    <td th:text="${entry.season.name}"></td>
    ...
    <td><a th:href="'season/' + ${entry.slug} + '/standings.html'">Standings</a></td>
</tr>
```
Note: The template currently uses `${seasons}` variable. The archive template must be updated to use `${seasonEntries}` or an equivalent. [VERIFIED: archive.html line 14 currently iterates `${seasons}`]

### Anti-Patterns to Avoid
- **Thymeleaf string manipulation for slug:** `${#strings.toLowerCase(#strings.replace(s.name, ' ', '-'))}` — produces wrong result since it uses only the name, not displayLabel. Remove entirely.
- **Mutating the Team entity** to rewrite `logoUrl`: Never set `team.setLogoUrl(relativeUrl)` — this would persist to the database via OSIV. Use a separate template context variable.
- **Absolute path for rootPath fallback:** If `relativeRoot` is empty do not use `"/"` — this produces server-rooted absolute paths like `/index.html`. Use `"."` instead.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Slug computation | Second slugify in template | `slugify()` in service, passed as context var | Single source of truth; avoids mismatch |
| Path traversal check on logo copy | Custom validation | `logoFile.startsWith(uploadDir)` pattern from `TeamCardService.encodeLogoBase64()` | Same guard already used in codebase |
| Logo MIME detection | String extension parsing | `Files.probeContentType()` | Standard JDK, already used in TeamCardService |

**Key insight:** The codebase already has a proven pattern for resolving `logoUrl` to a disk path in `TeamCardService.encodeLogoBase64()` (line 173–190). The logo copy logic in `SiteGeneratorService` should follow the same structure.

---

## Common Pitfalls

### Pitfall 1: Mutating Team entity for logo path rewrite
**What goes wrong:** If `team.setLogoUrl(relativeUrl)` is called inside a `@Transactional(readOnly = true)` method, Hibernate in dev mode may log warnings; in a read-write transaction it would dirty-check and persist the change to the database — corrupting all future runtime logo display.
**Why it happens:** OSIV keeps the Hibernate session open. Dirty entities get persisted on session flush.
**How to avoid:** Use a separate context variable (`teamLogoRelPath`). Never mutate entity fields during site generation.
**Warning signs:** `UPDATE teams SET logo_url=...` appearing in SQL logs after site generation.

### Pitfall 2: Archive template variable name change breaks iteration
**What goes wrong:** If the archive template is updated to iterate `${seasonEntries}` but the service still passes `${seasons}`, no rows render (Thymeleaf renders nothing for null iteration variable — no error).
**Why it happens:** Thymeleaf silently skips `th:each` on null collections.
**How to avoid:** Rename consistently in service AND template at the same time. New test asserts archive contains at least one row.
**Warning signs:** Generated archive.html has an empty `<tbody>` but no exception was thrown.

### Pitfall 3: Logo filename collision across teams
**What goes wrong:** Two teams upload logos with the same filename (e.g., both named `logo.png`). Copying to `assets/img/logos/` flat directory causes the second to overwrite the first.
**Why it happens:** `fileStorageService.storeImage()` adds a UUID prefix to avoid collision in the upload directory, but a naive copy to a flat destination drops that uniqueness.
**How to avoid:** Use the full relative path from `logoUrl` (preserving the `teams/{uuid}/{filename}` subdirectory structure) when copying to `assets/img/logos/`. Example: copy to `assets/img/logos/teams/{uuid}/{filename}`, not `assets/img/logos/{filename}`.
**Warning signs:** One team's logo appears on multiple team profile pages.

### Pitfall 4: rootPath = "." affects assetsPath calculation
**What goes wrong:** Changing `rootPath` to `"."` has no effect on `assetsPath`, since they are computed from different relativize calls. But a reviewer might assume they are linked.
**Why it happens:** `assetsPath` uses `outputFile.getParent().relativize(outRoot.resolve("assets"))` — correct at all levels. The LINK-03 fix only touches `rootPath`.
**How to avoid:** Only the `rootPath` line needs the empty-string guard. `assetsPath` is already correct.
**Warning signs:** Root-level pages showing `assetsPath = "."` instead of `"assets"` — this would be a different bug introduced by incorrect change.

### Pitfall 5: activeSeasonSlug is null when no active season
**What goes wrong:** If no season is active, `activeSeason` is null. Calling `slugify(activeSeason.getDisplayLabel())` throws NPE.
**Why it happens:** `generate()` uses `findByActiveTrue().orElse(null)`.
**How to avoid:** Guard with `activeSeason != null ? slugify(activeSeason.getDisplayLabel()) : ""`. Templates should also guard: `th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"` on the Driver Ranking nav link.
**Warning signs:** NPE in `generate()` on sites with no active season.

---

## Code Examples

### LINK-03: rootPath fix in writeTemplate()
```java
// Source: SiteGeneratorService.writeTemplate() — after fix
String rootStr = relativeRoot.toString().replace('\\', '/');
context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
```

### LINK-02: activeSeasonSlug in writeTemplate()
```java
// activeSeasonSlug is passed as parameter (computed once in generate())
context.setVariable("activeSeasonSlug", activeSeasonSlug != null ? activeSeasonSlug : "");
```

### LINK-02: layout.html nav update
```html
<!-- Before -->
<a th:href="${rootPath + '/driver-ranking.html'}">Driver Ranking</a>

<!-- After -->
<a th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"
   th:href="${rootPath + '/season/' + activeSeasonSlug + '/driver-ranking.html'}">Driver Ranking</a>
```

### LINK-01: archive.html template update
```html
<!-- Before -->
<tr th:each="s : ${seasons}">
    <td th:text="${s.name}"></td>
    ...
    <a th:href="'season/' + ${#strings.toLowerCase(#strings.replace(s.name, ' ', '-'))} + '/standings.html'">Standings</a>

<!-- After -->
<tr th:each="entry : ${seasonEntries}">
    <td th:text="${entry.season.name}"></td>
    ...
    <a th:href="'season/' + ${entry.slug} + '/standings.html'">Standings</a>
```

### LINK-04: logo copy and path rewrite in generateTeamProfiles()
```java
// Following pattern from TeamCardService.encodeLogoBase64()
private String copyLogoToAssets(String logoUrl, Path outPath, String assetsPath) {
    if (logoUrl == null || !logoUrl.startsWith("/uploads/")) return null;
    try {
        Path logoFile = uploadDir.resolve(logoUrl.substring("/uploads/".length())).normalize();
        if (!logoFile.startsWith(uploadDir)) {
            log.warn("Path traversal attempt in logo URL: {}", logoUrl);
            return null;
        }
        if (!Files.exists(logoFile)) {
            log.warn("Logo file not found, skipping: {}", logoUrl);
            return null;
        }
        // Preserve UUID-prefixed subdirectory to avoid filename collision
        String relativePart = logoUrl.substring("/uploads/".length()); // e.g., "teams/{uuid}/abc123_SGM.png"
        Path target = outPath.resolve("assets").resolve("img").resolve("logos").resolve(relativePart);
        Files.createDirectories(target.getParent());
        Files.copy(logoFile, target, StandardCopyOption.REPLACE_EXISTING);
        return assetsPath + "/img/logos/" + relativePart;
    } catch (IOException e) {
        log.warn("Failed to copy logo: {}", logoUrl, e);
        return null;
    }
}
```

### Test pattern: Jsoup link assertion
```java
// Follows existing SiteGeneratorServiceTest pattern (lines 235-258)
@Test
void givenSeason_whenGenerate_thenArchiveContainsCorrectSeasonSlug() throws IOException {
    // when
    siteGeneratorService.generate();
    // then
    var html = Files.readString(tempDir.resolve("archive.html"));
    var doc = Jsoup.parse(html);
    String expectedSlug = slugify(season.getDisplayLabel());
    var links = doc.select("a[href*='season/" + expectedSlug + "']");
    assertFalse(links.isEmpty(), "Archive should contain link with correct season slug");
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `#strings.toLowerCase(#strings.replace(...))` in template | Pre-computed slug in service context | Phase 37 | Slug consistent with directory names |
| Empty `rootPath` for root files | `"."` default | Phase 37 | Relative links work when opened from filesystem |
| Hard-coded `rootPath + '/driver-ranking.html'` | `rootPath + '/season/' + activeSeasonSlug + '/driver-ranking.html'` | Phase 37 | Nav link resolves to an actual file |
| `team.logoUrl` as server path in template | `teamLogoRelPath` as relative static asset path | Phase 37 | Logos display without a running server |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Logo files in uploadDir have filename uniqueness guaranteed by UUID prefix from `FileStorageService.storeImage()` | Code Examples (LINK-04) | If filenames collide, the preserve-subdirectory copy strategy prevents overwrites — low risk regardless |
| A2 | No active season is an edge case worth guarding, not a common production state | Pitfall 5 | If site is generated with no active season and guard is missing, NPE crashes generation |
| A3 | The Standings nav link (`rootPath + '/index.html'`) already works correctly because index.html shows standings | Claude's Discretion (CONTEXT.md) | If index.html doesn't show standings for current season, a separate fix would be needed — out of phase scope |

---

## Open Questions

1. **Should `seasonSlug` be passed via a SeasonEntry record or a Map?**
   - What we know: Archive template iterates `${seasons}`, a `List<Season>`. Adding `slug` per-season needs either a wrapper record, a `LinkedHashMap<Season, String>`, or a parallel `List<String>`.
   - What's unclear: Thymeleaf iteration syntax works for any `Iterable`. A record is more type-safe. A `Map` is harder to iterate in th:each while maintaining season data.
   - Recommendation: Introduce a `record SeasonEntry(Season season, String slug)` inner type in `SiteGeneratorService` (analogous to `RaceView`). Pass `List<SeasonEntry>` to archive template.

2. **Does the Driver Ranking nav link need to be hidden when activeSeasonSlug is empty?**
   - What we know: `generate()` sets `activeSeason = findByActiveTrue().orElse(null)`. If null, `activeSeasonSlug = ""`.
   - What's unclear: Should nav hide the link or link to archive as fallback?
   - Recommendation: Use `th:if` to hide the Driver Ranking nav link when `activeSeasonSlug` is empty. A site with no active season has no meaningful ranking to link to.

---

## Environment Availability

Step 2.6: SKIPPED — phase is purely Java/Thymeleaf code changes with no external CLI dependencies. `./mvnw verify` is the only execution requirement, already available.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test |
| Config file | pom.xml (Surefire + Failsafe) |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LINK-01 | Archive page link href contains correct season slug (from displayLabel) | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenArchiveContainsCorrectSeasonSlug` | ❌ Wave 0 |
| LINK-02 | Nav Driver Ranking link points to `season/{activeSlug}/driver-ranking.html` | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenActiveSeason_whenGenerate_thenNavDriverRankingLinkIsCorrect` | ❌ Wave 0 |
| LINK-03 | Root-level pages have relative nav links (no `/index.html`) | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenActiveSeason_whenGenerate_thenRootPageNavUsesRelativePaths` | ❌ Wave 0 |
| LINK-04 | Team logo file copied to assets; team-profile img src is relative path | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenTeamWithLogo_whenGenerate_thenLogoIsCopiedAndLinkedRelatively` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green (`./mvnw verify`) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] Four new test methods in `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`:
  - `givenSeason_whenGenerate_thenArchiveContainsCorrectSeasonSlug` — REQ LINK-01
  - `givenActiveSeason_whenGenerate_thenNavDriverRankingLinkIsCorrect` — REQ LINK-02
  - `givenActiveSeason_whenGenerate_thenRootPageNavUsesRelativePaths` — REQ LINK-03
  - `givenTeamWithLogo_whenGenerate_thenLogoIsCopiedAndLinkedRelatively` — REQ LINK-04
- [ ] LINK-04 test requires a team logo fixture on disk; use `@TempDir` for uploadDir and write a small PNG (or 1-byte file) to simulate

---

## Security Domain

No security-sensitive changes. This phase writes static HTML files and copies image files within the server's configured directories. The logo copy follows the same path-traversal guard pattern already in `TeamCardService.encodeLogoBase64()`:
- Resolve relative to `uploadDir`
- Verify `logoFile.startsWith(uploadDir)` before reading
- Reject paths starting outside uploadDir with `log.warn()`

No ASVS categories newly applicable — no authentication, sessions, access control, input from users, or cryptography involved.

---

## Project Constraints (from CLAUDE.md)

| Constraint | Impact on Phase 37 |
|-----------|-------------------|
| No logic in Thymeleaf templates | `archive.html` must remove `#strings.toLowerCase(#strings.replace(...))` and use context variable instead |
| No Fallback Calculations | Logo path must be computed correctly in service; no `th:if` workarounds in templates beyond null guards |
| Controllers thin | `SiteGeneratorService` owns all path computation — no logic in `SiteGeneratorController` |
| OSIV enabled | Never mutate entity fields (`team.setLogoUrl()`) during site generation — use separate context var |
| TDD: write tests first | New test methods for all 4 requirements written before implementation |
| Test naming: Given-When-Then | `givenTeamWithLogo_whenGenerate_thenLogoIsCopiedAndLinkedRelatively()` pattern |
| Minimum 82% line coverage | Current coverage 84.5%; 4 new integration tests increase coverage, no risk of regression |
| No Breaking Changes to URLs | No endpoint changes in this phase |
| Playwright dependency: compile-scope only | No E2E changes needed for this phase |

---

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — verified all generate*() methods, writeTemplate(), slugify() [VERIFIED: file read]
- `src/main/resources/templates/site/layout.html` — verified broken nav links [VERIFIED: file read]
- `src/main/resources/templates/site/archive.html` — verified broken slug expression [VERIFIED: file read]
- `src/main/resources/templates/site/team-profile.html` — verified logo usage [VERIFIED: file read]
- `src/main/java/org/ctc/domain/model/Season.java` — verified `getDisplayLabel()` format [VERIFIED: file read]
- `src/main/java/org/ctc/domain/model/Team.java` — verified `logoUrl` field type [VERIFIED: file read]
- `src/main/java/org/ctc/domain/service/FileStorageService.java` — verified `/uploads/` prefix convention [VERIFIED: file read]
- `src/main/java/org/ctc/admin/service/TeamCardService.java` — verified path-traversal guard pattern [VERIFIED: file read]
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — verified test patterns and existing coverage [VERIFIED: file read]
- `target/site/jacoco/jacoco.csv` — verified SiteGeneratorService at 98% coverage, total at 84.5% [VERIFIED: file read]

### Secondary (MEDIUM confidence)
- Path behavior analysis (empty relativeRoot produces empty string) — verified by tracing `java.nio.file.Path.relativize()` semantics [VERIFIED: code trace]
- Logo filename collision analysis — verified by checking `fileStorageService.storeImage()` UUID prefix behavior [VERIFIED: file read]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all existing technology, no new dependencies
- Architecture: HIGH — root causes verified by reading source code
- Pitfalls: HIGH — derived from actual code behavior (OSIV, Thymeleaf null iteration, Path.relativize())
- Test patterns: HIGH — follows established Jsoup+@TempDir patterns in existing test class

**Research date:** 2026-04-16
**Valid until:** 2026-05-16 (stable codebase, no external dependencies)
