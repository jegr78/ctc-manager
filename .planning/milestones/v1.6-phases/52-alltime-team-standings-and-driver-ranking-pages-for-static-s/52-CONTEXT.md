# Phase 52: Alltime Team Standings & Driver Ranking Pages — Context

**Gathered:** 2026-04-17
**Status:** Ready for planning
**Source:** PRD Express Path (/Users/jegr/.claude/plans/wild-rolling-moonbeam.md)

<domain>
## Phase Boundary

Add two new alltime pages to the static site: "Alltime Team Standings" and "Alltime Driver Ranking". The backend services already exist (`StandingsService.calculateAlltimeStandings()` and `DriverRankingService.calculateAlltimeRanking()`). This phase adds static site generation methods, new templates, and navigation updates. No landing page tile changes.

</domain>

<decisions>
## Implementation Decisions

### Templates
- Two new standalone templates (not conditionals in existing ones): `alltime-standings.html` and `alltime-driver-ranking.html`
- `alltime-standings.html`: layout fragment `layout('Alltime Standings', ...)`, title "Alltime Team Standings", subtitle "All seasons combined", table columns: #, Team, MP, W, D, L, PR, Pts. Team name as text (no link). `currentPage = "alltime-standings"`
- `alltime-driver-ranking.html`: layout fragment `layout('Alltime Driver Ranking', ...)`, title "Alltime Driver Ranking", table columns: #, Driver, Team, Races, Best, Avg, Points. Driver name as text (no link). `currentPage = "alltime-driver-ranking"`

### SiteGeneratorService
- Two new private methods: `generateAlltimeStandings(...)` and `generateAlltimeDriverRanking(...)` following the existing `generateStandings()` pattern
- `generateAlltimeStandings`: calls `standingsService.calculateAlltimeStandings()`, sets context `standings`, `currentPage="alltime-standings"`, `breadcrumbCurrent="Alltime Standings"`, no `seasonSlug`. Output: `{outPath}/alltime-standings.html`
- `generateAlltimeDriverRanking`: calls `driverRankingService.calculateAlltimeRanking()`, sets context `driverRanking`, `currentPage="alltime-driver-ranking"`, `breadcrumbCurrent="Alltime Driver Ranking"`. Output: `{outPath}/alltime-driver-ranking.html`
- Both called from `generate()` after season-specific pages (alongside `generateArchive`, `generateTeamsOverview` etc.)

### Navigation
- In `layout.html` main nav (`.nav-links`): replace the season-bound "Standings" and "Driver Ranking" links with alltime page links
- Alltime links are always visible (no `th:if` on `activeSeasonSlug`) — they use `rootPath + '/alltime-standings.html'` and `rootPath + '/alltime-driver-ranking.html'`
- Active state via `currentPage` matching (`alltime-standings` / `alltime-driver-ranking`)
- Season-specific Standings/Rankings remain reachable via subnav and tiles

### Landing Page
- No changes to `index.html` — existing "Standings" tile still points to current season

### Tests
- Two new integration tests in `SiteGeneratorServiceTest.java`:
  - `whenGenerate_thenAlltimeStandingsPageExists()` — checks file exists, table headers, team names
  - `whenGenerate_thenAlltimeDriverRankingPageExists()` — checks file exists, driver data in table

### Claude's Discretion
- Exact Context variable names and method signatures (following existing SiteGeneratorService patterns)
- CSS styling for alltime pages (reuse existing standings/ranking styles)
- Breadcrumb structure for alltime pages

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Static Site Generation
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Main generation orchestrator, contains `generate()`, `writeTemplate()`, existing `generateStandings()` pattern
- `src/main/resources/templates/site/standings.html` — Season standings template (analog for alltime)
- `src/main/resources/templates/site/driver-ranking.html` — Season driver ranking template (analog for alltime)
- `src/main/resources/templates/site/layout.html` — Navigation structure to modify

### Backend Services (reuse, do NOT modify)
- `src/main/java/org/ctc/domain/service/StandingsService.java` — `calculateAlltimeStandings()` at line 76
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — `calculateAlltimeRanking()` at line 64

### Tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Existing site generation tests (analog for new tests)

</canonical_refs>

<specifics>
## Specific Ideas

- Method signatures from PRD:
  ```java
  private void generateAlltimeStandings(Path outPath, String activeSeasonSlug,
                                         String activeSeasonName, GenerationResult result)
  private void generateAlltimeDriverRanking(Path outPath, String activeSeasonSlug,
                                             String activeSeasonName, GenerationResult result)
  ```
- Navigation HTML pattern from PRD (replace existing conditional links):
  ```html
  <a th:href="${rootPath + '/alltime-standings.html'}"
     th:class="${currentPage == 'alltime-standings'} ? 'nav-link-active' : ''">Standings</a>
  <a th:href="${rootPath + '/alltime-driver-ranking.html'}"
     th:class="${currentPage == 'alltime-driver-ranking'} ? 'nav-link-active' : ''">Driver Ranking</a>
  ```
- Reusable methods: `writeTemplate()` (line 577), `slugify()`
- Verification: `./mvnw verify`, then generate via `curl -X POST http://localhost:9090/admin/generate`, check output files exist, visual check with `playwright-cli`

</specifics>

<deferred>
## Deferred Ideas

None — PRD covers phase scope

</deferred>

---

*Phase: 52-alltime-team-standings-and-driver-ranking-pages-for-static-s*
*Context gathered: 2026-04-17 via PRD Express Path*
