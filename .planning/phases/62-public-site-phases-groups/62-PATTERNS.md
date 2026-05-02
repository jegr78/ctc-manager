# Phase 62: Public Site Phase + Group Awareness — Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 28 (5 new helper classes, 1 utility, 1 writer collaborator, 3 new view records, 1 refactored orchestrator, 5 rewritten templates, 1 CSS file, 2 service signature changes, 1 new repo method, 1 new IT class, 5 new helper unit tests, 1 updated Mockito IT)
**Analogs found:** 28 / 28

---

## File Classification

### New helper classes (D-20)

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/sitegen/StandingsPageGenerator.java` | service (page generator) | transform (read entities → write HTML) | `SiteGeneratorService.generateStandings` Z. 186-211 + admin `StandingsService` (decomposition pattern) | exact |
| `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java` | service (page generator) | transform | `SiteGeneratorService.generateDriverRanking` Z. 213-239 + admin `DriverRankingService` (decomposition pattern) | exact |
| `src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java` | service (page generator) | transform | `SiteGeneratorService.generateMatchdays` Z. 241-267 + `generateMatchdayIndex` Z. 653-678 | exact |
| `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java` | service (page generator) | transform | `SiteGeneratorService.generateTeamProfiles` Z. 269-346 | exact |
| `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` | service (page generator) | transform | `SiteGeneratorService.generateDriverProfiles` Z. 348-385 | exact |

### Sitegen utilities + collaborators (D-20 support)

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/sitegen/SiteSlugger.java` | utility | pure function | `SiteGeneratorService.slugify` Z. 823-828 | exact |
| `src/main/java/org/ctc/sitegen/TemplateWriter.java` | service (collaborator) | file I/O | `SiteGeneratorService.writeTemplate` Z. 680-699 | exact |
| `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` (refactored) | service (orchestrator) | event-driven (per-season loop) | itself, post-extraction; current `generate()` Z. 65-130 stays as the loop body | refactor of self |

### New view records (D-04, D-06, D-13/D-15)

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/sitegen/model/PhaseTabView.java` | model (view record) | data carrier | `SiteGeneratorService.SeasonEntry` Z. 834 + `RaceView` (existing model package member) | role-match |
| `src/main/java/org/ctc/sitegen/model/GroupSubTabView.java` | model (view record) | data carrier | same — sibling of `PhaseTabView` | role-match |
| `src/main/java/org/ctc/sitegen/model/PhaseBreakdownEntry.java` | model (view record) | data carrier | `SiteGeneratorService.DriverEntry` Z. 848 (label + numeric content + URL) | role-match |
| `src/main/java/org/ctc/sitegen/model/GenerationContext.java` *(new shared input)* | model (immutable input) | data carrier | inline `Path outPath, String activeSeasonSlug, String activeSeasonName, boolean hasPlayoff, ...` parameters in every `generateX` today | role-match |

### Templates rewritten (D-04, D-10, D-11, D-13, D-15)

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `src/main/resources/templates/site/standings.html` | view (Thymeleaf) | request-response (template render) | `templates/admin/standings.html` Z. 65-102 (`showGroupColumn` / `showBuchholz` flag pattern) + admin `season-detail.html` Z. 266-290 (tab row shape) | exact (column flags) |
| `src/main/resources/templates/site/matchdays.html` | view | request-response | itself + admin tab-row pattern | role-match |
| `src/main/resources/templates/site/driver-ranking.html` | view | request-response | itself + admin tab-row pattern; "All Phases" tab is new copy (UI-SPEC) | role-match |
| `src/main/resources/templates/site/team-profile.html` | view | request-response | itself + UI-SPEC `showPhaseBreakdown` flag | role-match |
| `src/main/resources/templates/site/driver-profile.html` | view | request-response | itself + UI-SPEC `showPhaseBreakdown` flag | role-match |

### CSS (D-05)

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `src/main/resources/static/site/css/style.css` (additions) | static asset | n/a | existing `.subnav` / `.subnav-link` / `.subnav-link.active` block Z. 622-684 | exact (reuse pattern) |

### Service signature changes (D-19)

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/domain/service/StandingsService.java` (alltime internal change) | service | CRUD/transform | `StandingsService.aggregateAcrossPhases` shape on the driver-ranking side (Z. 76-106) — same iterate-over-phases-and-merge pattern | role-match (mirror sister-service approach) |
| `src/main/java/org/ctc/domain/service/DriverRankingService.java` (alltime internal change) | service | CRUD/transform | sister `StandingsService` post-D-19 + existing `aggregateAcrossPhases` Z. 76-106 | role-match |
| `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` (new `findByRaceMatchdaySeasonIdIn`) | repository | data access | existing `findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn` Z. 30-31 (drop the IsNull predicate) | exact |

### Tests

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java` *(new)* | test (Surefire IT) | request-response (assert on generated files) | `SiteGeneratorE2ETest.java` Z. 29-105 + `SiteGeneratorServiceTest.java` Z. 25-100 (both `@SpringBootTest @ActiveProfiles("dev")` + Jsoup) | exact |
| `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java` *(new)* | test (unit, Spring) | request-response | `SiteGeneratorServiceTest.java` (per-page assertion shape) | role-match |
| `src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java` *(new)* | test | request-response | same | role-match |
| `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java` *(new)* | test | request-response | same | role-match |
| `src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java` *(new)* | test | request-response | same | role-match |
| `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` *(new)* | test | request-response | same | role-match |
| `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java` *(updated)* | test (Mockito contract) | n/a | itself Z. 75-97 (constructor enumeration) — must be updated for new helpers, not replaced | refactor of self |
| `src/test/resources/sitegen/baseline/single-league-standings.html` *(new)* | test fixture (golden file) | n/a | none — first golden snapshot file in the project | no analog |
| `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` (new method) | test (unit) | CRUD | existing `StandingsServiceTest` methods (any) | exact |
| `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` (new method) | test (unit) | CRUD | existing `DriverRankingServiceTest` methods (any) | exact |

---

## Pattern Assignments

### `org.ctc.sitegen.StandingsPageGenerator` (service, transform)

**Analog:** `SiteGeneratorService.generateStandings` Z. 186-211 (embedded today) + `SiteGeneratorService` Lombok stereotype block Z. 33-37.

**Stereotype + injection pattern** (mirror `SiteGeneratorService.java:33-55`):

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsPageGenerator {

    private final TemplateEngine templateEngine;
    private final TemplateWriter templateWriter;
    private final SiteSlugger slugger;
    private final StandingsService standingsService;
    private final SeasonPhaseService seasonPhaseService;
    private final SeasonPhaseGroupRepository seasonPhaseGroupRepository;  // new — D-17 group enumeration
    private final PhaseTeamRepository phaseTeamRepository;                // new — D-22 empty-state roster
    // ...
}
```

**Core per-page-generation pattern** (lines 186-211 of today's `SiteGeneratorService`):

```java
private void generateStandings(Path outPath, Season season, String activeSeasonSlug,
                                String activeSeasonName, boolean hasPlayoff, String playoffSeasonSlug, GenerationResult result) throws IOException {
    var ctx = new Context(Locale.ENGLISH);
    ctx.setVariable("season", season);
    var regularPhase = seasonPhaseService.findRegularPhase(season.getId());
    var standings = standingsService.calculateStandings(regularPhase.getId(), null);
    var teamSlugMap = new java.util.HashMap<java.util.UUID, String>();
    for (var s : standings) {
        teamSlugMap.put(s.getTeam().getId(), "team/" + slugify(s.getTeam().getShortName()) + ".html");
    }
    ctx.setVariable("standings", standings);
    ctx.setVariable("teamSlugMap", teamSlugMap);

    ctx.setVariable("currentPage", "standings");
    ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
    ctx.setVariable("seasonName", season.getName());
    ctx.setVariable("hasPlayoff", hasPlayoff);
    ctx.setVariable("playoffSeasonSlug", playoffSeasonSlug);
    ctx.setVariable("breadcrumbCurrent", "Standings");

    var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
    Files.createDirectories(dir);
    writeTemplate("site/standings", ctx, dir.resolve("standings.html"), activeSeasonSlug, activeSeasonName);
    result.incrementPages();
}
```

**Phase 62 mutations to copy on top of this pattern:**
1. Wrap the body in a per-phase loop driven by `seasonPhaseService.findAllPhases(season.getId())` + an inner loop `seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phase.getId())` (D-04, D-17). Skip PLAYOFF in the outer loop because PLAYOFF tab links to `playoff.html` (D-08).
2. Add server-side flag set: `showPhaseTabs` (true if `findAllPhases.size() >= 2`), `showGroupTabs` (true if phase layout is GROUPS), `showGroupColumn` (true if GROUPS combined view), `showBuchholz` (true if Swiss + per-group), `emptyState`, `phaseTabs`, `groupTabs` — all booleans/lists computed in the helper, not in the template (CLAUDE.md "Keep Templates Lean").
3. For Swiss + per-group: use `standingsService.calculateStandingsWithBuchholz(phaseId, groupId)` (StandingsService.java:97-131) instead of `calculateStandings`.
4. Empty-state fallback (D-22): if `standings.isEmpty()`, build 0-point rows from `phaseTeamRepository.findByPhaseId(phaseId)` (or `findByPhaseIdAndGroupId` for per-group) and set `emptyState=true`. Pattern parent is admin `templates/admin/standings.html` Z. 60-63 plus the empty-state copy in UI-SPEC.

**File-naming pattern** (D-01/D-02/D-03):

```java
String phaseSlug = phase.getPhaseType().name().toLowerCase(Locale.ENGLISH);  // "regular" / "playoff" / "placement"
String groupSlug = slugger.slugify(group.getName());
Path file = dir.resolve("standings-" + phaseSlug + "-group-" + groupSlug + ".html");
```

The legacy `standings.html` filename stays untouched (D-04). The new variants are additive.

---

### `org.ctc.sitegen.DriverRankingPageGenerator` (service, transform)

**Analog:** `SiteGeneratorService.generateDriverRanking` Z. 213-239.

**Core pattern** (lines 213-239 of today's `SiteGeneratorService`):

```java
private void generateDriverRanking(Path outPath, Season season, String activeSeasonSlug,
                                    String activeSeasonName, boolean hasPlayoff, String playoffSeasonSlug, GenerationResult result) throws IOException {
    var ctx = new Context(Locale.ENGLISH);
    ctx.setVariable("season", season);
    var phaseIds = seasonPhaseService.findAllPhases(season.getId()).stream()
            .map(SeasonPhase::getId).toList();
    var driverRanking = driverRankingService.aggregateAcrossPhases(phaseIds, season.getId());
    var driverSlugMap = new java.util.HashMap<java.util.UUID, String>();
    for (var r : driverRanking) {
        driverSlugMap.put(r.getDriver().getId(), "driver/" + slugify(r.getDriver().getPsnId()) + ".html");
    }
    ctx.setVariable("driverRanking", driverRanking);
    ctx.setVariable("driverSlugMap", driverSlugMap);
    // ... currentPage, seasonSlug, ... breadcrumbCurrent ...
    writeTemplate("site/driver-ranking", ctx, dir.resolve("driver-ranking.html"), activeSeasonSlug, activeSeasonName);
    result.incrementPages();
}
```

**Phase 62 mutations:**
1. Legacy `driver-ranking.html` continues to call `driverRankingService.aggregateAcrossPhases(phaseIds, season.getId())` — D-11 SC4 byte-identity for the canonical URL.
2. Per-phase variants `driver-ranking-{phaseSlug}.html`: switch to `driverRankingService.calculateRankingForPhase(phase.getId())` (DriverRankingService.java:39-68 — already supports REGULAR + PLAYOFF via union-merge).
3. Same `showPhaseTabs` / `phaseTabs` flag set as standings; first tab is "All Phases" (UI-SPEC copy).

---

### `org.ctc.sitegen.MatchdaysPageGenerator` (service, transform)

**Analog:** `SiteGeneratorService.generateMatchdays` Z. 241-267 (per-matchday detail) + `generateMatchdayIndex` Z. 653-678 (list page).

**Decision (per Research Open Question 4 + Risk 9):** ONE class with TWO entry methods (`generateIndex(ctx, result)` writes the list page; `generateDetails(ctx, result)` writes per-matchday detail pages). Cohesion is high, file split unjustified.

**Core pattern — list page** (lines 653-678):

```java
private void generateMatchdayIndex(...) throws IOException {
    var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
    var ctx = new Context(Locale.ENGLISH);
    ctx.setVariable("season", season);
    ctx.setVariable("matchdays", matchdays);
    var matchdayLinkMap = new java.util.LinkedHashMap<java.util.UUID, String>();
    for (var md : matchdays) {
        matchdayLinkMap.put(md.getId(), "matchday/" + slugify(md.getLabel()) + ".html");
    }
    ctx.setVariable("matchdayLinkMap", matchdayLinkMap);
    // ... flags ...
    writeTemplate("site/matchdays", ctx, dir.resolve("matchdays.html"), activeSeasonSlug, activeSeasonName);
    result.incrementPages();
}
```

**Phase 62 mutations (D-10):**
1. Legacy `matchdays.html` collapses to REGULAR-only matchdays via `matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regularPhase.getId())` for consistency with `standings.html` legacy default (Open Question 2 recommendation).
2. Per-phase variants use `matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phaseId)` (MatchdayRepository.java:32).
3. Per-group variants use `matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId, groupId)` (MatchdayRepository.java:37).
4. Tab row markup driven by same flag set; per-matchday detail pages (`generateMatchdays`) themselves are NOT phase-suffixed — their slugs are unique per season already.

**Pre-fetch pattern to preserve** (Z. 245):

```java
// Pre-fetch all lineups for the season to avoid per-result repository queries in toRaceView
var allLineups = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId());
```

`toRaceView` (Z. 770-821) moves with this helper because only `generateMatchdays` calls it.

---

### `org.ctc.sitegen.TeamProfilePageGenerator` (service, transform)

**Analog:** `SiteGeneratorService.generateTeamProfiles` Z. 269-346 (78 LOC — largest helper).

**Core pattern — RaceLineup is Source of Truth** (lines 296-313 — preserve verbatim, this is the load-bearing logic per `feedback_racelineup_source_of_truth`):

```java
// Load team drivers for Drivers section via RaceLineup (source of truth per CLAUDE.md).
var lineupDrivers = allLineups.stream()
        .filter(rl -> {
            var rlTeam = rl.getTeam();
            return rlTeam.getId().equals(team.getId())
                    || (rlTeam.getParentTeam() != null
                        && rlTeam.getParentTeam().getId().equals(team.getId()));
        })
        .map(rl -> rl.getDriver())
        .distinct()
        .toList();

var driversToShow = lineupDrivers.isEmpty()
        ? allSeasonDrivers.stream()
                .filter(sd -> sd.getTeam().getId().equals(team.getId()))
                .map(sd -> sd.getDriver())
                .distinct()
                .toList()
        : lineupDrivers;
```

**Phase 62 mutations (D-13/D-14):**
1. Standings panel keeps using `calculateStandings(regularPhase.getId(), null)` (combined view, D-14) — no change to today's behavior.
2. Add `showPhaseBreakdown` flag: `boolean showPhaseBreakdown = seasonPhaseService.findAllPhases(season.getId()).size() >= 2;`
3. Build `List<PhaseBreakdownEntry>` per phase: rank from `calculateStandings(phase.getId(), null)`, points from same. PLAYOFF rendered as "SF exit" / "F exit" / "Champion" instead of points (planner finalises copy).
4. SC4 byte-identity invariant: when `showPhaseBreakdown=false`, the template MUST emit zero new bytes. Use the snapshot golden-file approach in tests (Risk 2).

---

### `org.ctc.sitegen.DriverProfilePageGenerator` (service, transform)

**Analog:** `SiteGeneratorService.generateDriverProfiles` Z. 348-385.

**Core pattern** (lines 348-385):

```java
private void generateDriverProfiles(...) throws IOException {
    var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());
    var generatedDriverIds = new java.util.HashSet<java.util.UUID>();

    for (var sd : seasonDrivers) {
        var driver = sd.getDriver();
        if (!generatedDriverIds.add(driver.getId())) continue;
        var team = sd.getTeam();
        var results = raceResultRepository.findByDriverId(driver.getId()).stream()
                .filter(r -> r.getRace().getMatchday().getSeason().getId().equals(season.getId()))
                .toList();
        // ... ctx vars, writeTemplate ...
    }
}
```

**Phase 62 mutations (D-15):**
1. When `showPhaseBreakdown=true`: split `results` into a `Map<PhaseType, List<RaceResult>>` via in-Java filter on `result.getRace().getMatchday().getPhase().getPhaseType()` — no new repo method needed (verified — Research §"Verified API Surface" line 175-176).
2. Set `resultsByPhase` (LinkedHashMap, REGULAR → PLAYOFF → PLACEMENT order) instead of flat `results` when `showPhaseBreakdown=true`. Template renders one `<table>` block per phase with `<h3>` heading per UI-SPEC copy.

---

### `org.ctc.sitegen.SiteSlugger` (utility, pure)

**Analog:** `SiteGeneratorService.slugify` Z. 823-828.

**Exact code to lift** (preserve byte-identity — D-02 / D-03 stability + Risk 7):

```java
String slugify(String input) {
    return input.toLowerCase()
            .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
}
```

**Promotion:** keep the function logic identical; expose as `public String slugify(String)` on a `@Component` (or `@Service` — neither has dependencies, both work). Inject into every helper that needs it. Do not make it `static` — Spring-injected makes test-time mocking trivial.

---

### `org.ctc.sitegen.TemplateWriter` (collaborator, file I/O)

**Analog:** `SiteGeneratorService.writeTemplate` Z. 680-699 (two overloads).

**Exact code to lift**:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateWriter {

    private final TemplateEngine templateEngine;
    private final SiteProperties siteProperties;

    public void write(String templateName, Context context, Path outputFile,
                      String activeSeasonSlug, String activeSeasonName) throws IOException {
        write(templateName, context, outputFile, Path.of(siteProperties.getOutputDir()), activeSeasonSlug, activeSeasonName);
    }

    public void write(String templateName, Context context, Path outputFile,
                      Path outRoot, String activeSeasonSlug, String activeSeasonName) throws IOException {
        Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
        Path relativeRoot = outputFile.getParent().relativize(outRoot);
        context.setVariable("assetsPath", relativeAssets.toString().replace('\\', '/'));
        String rootStr = relativeRoot.toString().replace('\\', '/');
        context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
        context.setVariable("activeSeasonSlug", activeSeasonSlug);
        context.setVariable("activeSeasonName", activeSeasonName);

        String html = templateEngine.process(templateName, context);
        Files.writeString(outputFile, html);
        log.debug("Generated: {}", outputFile);
    }
}
```

Behavior is byte-identical to today's private method. Plan 0 must verify `git diff docs/site/` is empty after extraction.

---

### `org.ctc.sitegen.model.PhaseTabView` (model, data carrier)

**Analog:** existing `org.ctc.sitegen.model.RaceView` (entire file, 19 LOC) + inline `record SeasonEntry(...)` Z. 834.

**Pattern (mirror `RaceView.java:5-8`):**

```java
package org.ctc.sitegen.model;

public record PhaseTabView(String label, String href, boolean active, String ariaControlsId) {}
```

`GroupSubTabView` and `PhaseBreakdownEntry` follow the same flat record shape.

---

### `org.ctc.sitegen.SiteGeneratorService` (refactored orchestrator)

**Analog:** itself — `generate()` Z. 65-130.

**Loop body to preserve** (Z. 87-104), with per-page calls swapped to helpers:

```java
for (var season : productionSeasons) {
    if (seasonPhaseService.findByType(season.getId(), org.ctc.domain.model.PhaseType.REGULAR).isEmpty()) {
        log.debug("Skipping season {} — no REGULAR phase", season.getName());
        continue;
    }
    var ctx = new GenerationContext(outPath, season, activeSeasonSlug, activeSeasonName,
            resolvePlayoffSeasonSlug(season));
    standingsPageGenerator.generate(ctx, result);
    driverRankingPageGenerator.generate(ctx, result);
    matchdaysPageGenerator.generateIndex(ctx, result);
    matchdaysPageGenerator.generateDetails(ctx, result);
    teamProfilePageGenerator.generate(ctx, result);
    driverProfilePageGenerator.generate(ctx, result);
    generatePlayoffBracket(outPath, season, activeSeasonSlug, activeSeasonName, result);  // stays inline (D-21 planner discretion)
}
```

Records `SeasonEntry`/`DriverEntry`/`TeamOverviewEntry`/`DriverOverviewEntry`/`SeasonDriverInfo` move to `org.ctc.sitegen.model.*` (Z. 834-856). `GenerationResult` static nested class stays inline (Z. 858-867) — pure accumulator, no extraction value.

**Constructor field churn (Risk 1):** the orchestrator drops fields that move to helpers and gains 5 new helper fields. The Mockito `SiteGeneratorServiceIT` constructor enumeration MUST be updated in Plan 0 (or replaced with per-helper unit tests).

---

### `templates/site/standings.html` (view, request-response)

**Analog (column flags):** `templates/admin/standings.html` Z. 75-97.

**Column-flag pattern to copy** (admin Z. 75-97):

```html
<!-- per-column TH -->
<th th:if="${showGroupColumn}" class="sortable" data-col="2" data-type="text" aria-label="Sort by Group">Group</th>
<th th:if="${showBuchholz}" class="sortable" data-col="8" data-type="num" aria-label="Sort by Buchholz">Buchholz</th>
<!-- per-row TD -->
<td th:if="${showGroupColumn}" th:text="${standing.group != null ? standing.group.name : '-'}"></td>
<td th:if="${showBuchholz}" th:text="${standing.buchholz}"></td>
```

**Analog (tab-row shape):** `templates/admin/season-detail.html` Z. 266-290.

**Tab-row pattern to mirror (style native to public site per D-05 — `.subnav` not `.tab-nav`):**

```html
<nav th:if="${showPhaseTabs}" class="phase-tab-row" role="tablist" aria-label="Phase navigation">
    <div class="phase-tab-row-inner">
        <a th:each="t : ${phaseTabs}"
           th:href="${t.href}"
           th:classappend="${t.active ? ' active' : ''}"
           role="tab"
           th:attr="aria-selected=${t.active}, aria-controls=${t.ariaControlsId}"
           class="phase-tab"
           th:text="${t.label}">Tab</a>
    </div>
</nav>
<nav th:if="${showGroupTabs}" class="group-tab-row" role="tablist" aria-label="Group navigation">
    <div class="group-tab-row-inner">
        <a th:each="g : ${groupTabs}"
           th:href="${g.href}"
           th:classappend="${g.active ? ' active' : ''}"
           role="tab"
           th:attr="aria-selected=${g.active}, aria-controls=${g.ariaControlsId}"
           class="group-tab"
           th:text="${g.label}">Group</a>
    </div>
</nav>
```

Differences vs admin: no `+ Add Phase` / `+ Add Group` CTA (public users have no edit rights); use UI-SPEC class names (`.phase-tab-row`, `.phase-tab`, `.group-tab-row`, `.group-tab`); compute `aria-selected` server-side at generation time.

**Existing template body to preserve verbatim for SC4** (today's `templates/site/standings.html` Z. 5-35) — when `showPhaseTabs=false` and `showGroupTabs=false`, the rendered HTML must be byte-identical to today.

---

### `templates/site/matchdays.html` / `driver-ranking.html` (views, request-response)

**Analog:** themselves (today's `matchdays.html` Z. 1-30, `driver-ranking.html` Z. 1-35) for the table body; the same tab-row pattern from `standings.html` for the header.

The driver-ranking tab row's first tab is "All Phases" (UI-SPEC copy line 263 of UI-SPEC) and links to legacy `driver-ranking.html`; subsequent tabs are per-phase variants.

---

### `templates/site/team-profile.html` (view, request-response)

**Analog:** itself (today's Z. 1-55). The Drivers section pattern Z. 35-52 stays untouched.

**New Phase-Breakdown section to add (UI-SPEC contract):**

```html
<div class="section section-gap" th:if="${showPhaseBreakdown}">
    <h2 class="section-title">Phase Breakdown</h2>
    <div class="table-wrap">
        <table>
            <thead>
                <tr><th>Phase</th><th class="text-right">Result</th></tr>
            </thead>
            <tbody>
                <tr th:each="entry : ${phaseBreakdown}">
                    <td th:text="${entry.label}"></td>
                    <td class="text-right" th:text="${entry.summary}"></td>
                </tr>
            </tbody>
        </table>
    </div>
</div>
```

`th:if="${showPhaseBreakdown}"` is the visibility gate (D-13). When false, the section emits zero bytes — preserves SC4 byte-identity.

---

### `templates/site/driver-profile.html` (view, request-response)

**Analog:** itself (today's Z. 1-75). Statistics section Z. 55-72 stays untouched.

**Phase-aware results sectioning (D-15):** when `showPhaseBreakdown=true`, split the results section by phase. Iterate `resultsByPhase` (LinkedHashMap server-side, REGULAR → PLAYOFF → PLACEMENT order) and emit one `.section` block per phase with the phase-specific `<h2 class="section-title">` heading from UI-SPEC copy table ("Regular Season Results" / "Playoff Results" / "Placement Phase Results").

When `showPhaseBreakdown=false`, retain the single `<div class="section" th:if="${results != null && !results.isEmpty()}">` block with heading `"Race History — " + ${season.displayLabel}` (today's Z. 17-53) for byte-identity.

---

### `static/site/css/style.css` (additions)

**Analog:** existing `.subnav` block Z. 622-684.

**Pattern to copy (verbatim where possible)** (Z. 622-654):

```css
.subnav {
    background: var(--bg-card);
    border-bottom: 1px solid var(--border);
    padding: 0 32px;
}
.subnav-inner {
    max-width: 1100px;
    margin: 0 auto;
    display: flex;
    gap: 4px;
    height: 44px;
    align-items: center;
}
.subnav-link {
    color: var(--text-dim);
    text-decoration: none;
    padding: 6px 12px;
    border-radius: 4px;
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 1px;
    transition: all 0.2s;
}
.subnav-link:hover { color: var(--white); background: rgba(255,255,255,0.05); }
.subnav-link.active {
    color: var(--accent);
    background: rgba(79, 195, 247, 0.1);
}
```

**Mobile rule to mirror** (Z. 680-684):

```css
@media (max-width: 768px) {
    .subnav { padding: 0 16px; overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .subnav-inner { height: auto; padding: 8px 0; flex-wrap: nowrap; }
    .breadcrumb { padding: 8px 16px; }
}
```

**Phase 62 additions** (UI-SPEC §"New CSS Classes — Phase Tab Rows"):
- `.phase-tab-row` / `.phase-tab-row-inner` / `.phase-tab` / `.phase-tab.active` — duplicate `.subnav` shape exactly (height 44px).
- `.group-tab-row` / `.group-tab-row-inner` / `.group-tab` / `.group-tab.active` — same shape but height 36px (secondary row).
- `.empty-phase-banner` — full-width banner inside `.section`; `border: 1px solid var(--border); border-radius: 8px; background: var(--bg-card); padding: 24px; text-align: center`.
- Mobile rules duplicate the `.subnav` mobile rule for the new classes.

`feedback_no_inline_styles`: NO `style="..."` attributes on the new tab anchors; all visuals via these classes.

---

### `StandingsService.calculateAlltimeStandings(seasons)` (D-19 internal change)

**Analog:** sister-service `DriverRankingService.aggregateAcrossPhases(phaseIds, seasonId)` Z. 76-106 — the iterate-over-phases-and-merge shape.

**Today's REGULAR-only loop** (StandingsService.java:159-182):

```java
@Transactional(readOnly = true)
public List<TeamStanding> calculateAlltimeStandings(List<Season> seasons) {
    Map<UUID, TeamStanding> alltimeMap = new HashMap<>();

    for (Season season : seasons) {
        List<TeamStanding> seasonStandings = calculateStandings(season.getId());  // ← REGULAR-only delegate
        if (seasonStandings.isEmpty()) continue;

        for (TeamStanding standing : seasonStandings) {
            Team parentTeam = standing.getTeam().getParentOrSelf();
            TeamStanding alltime = alltimeMap.computeIfAbsent(
                    parentTeam.getId(), id -> new TeamStanding(parentTeam));
            alltime.merge(standing);
        }
    }
    // ... sort + return ...
}
```

**Phase 62 D-19 mutation:** replace the `calculateStandings(season.getId())` call with an inner phase loop:

```java
for (Season season : seasons) {
    for (SeasonPhase phase : seasonPhaseService.findAllPhases(season.getId())) {
        List<TeamStanding> phaseStandings = calculateStandings(phase.getId(), null);  // includes PLAYOFF + PLACEMENT
        if (phaseStandings.isEmpty()) continue;
        for (TeamStanding standing : phaseStandings) {
            Team parentTeam = standing.getTeam().getParentOrSelf();
            TeamStanding alltime = alltimeMap.computeIfAbsent(
                    parentTeam.getId(), id -> new TeamStanding(parentTeam));
            alltime.merge(standing);
        }
    }
}
```

Public signature unchanged. Tracked Behavior Change called out in PR + release notes (D-29). Add `@SeeAlso` Javadoc note.

---

### `DriverRankingService.calculateAlltimeRanking(seasonIds)` (D-19 internal change)

**Analog:** itself, Z. 127-132.

**Today's REGULAR-only call** (DriverRankingService.java:127-132):

```java
@Transactional(readOnly = true)
public List<DriverRanking> calculateAlltimeRanking(List<UUID> seasonIds) {
    return calculateAlltimeRanking(
            raceResultRepository.findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(seasonIds),
            seasonDriverRepository.findBySeasonIdIn(seasonIds));
}
```

**Phase 62 D-19 mutation:** drop the `IsNull` filter — switch to a NEW repository method `findByRaceMatchdaySeasonIdIn(List<UUID> seasonIds)` (plural overload of Z. 24's `findByRaceMatchdaySeasonId(UUID seasonId)`):

```java
@Transactional(readOnly = true)
public List<DriverRanking> calculateAlltimeRanking(List<UUID> seasonIds) {
    return calculateAlltimeRanking(
            raceResultRepository.findByRaceMatchdaySeasonIdIn(seasonIds),
            seasonDriverRepository.findBySeasonIdIn(seasonIds));
}
```

**New `RaceResultRepository` method to add (mirror Z. 22-24's singular):**

```java
@EntityGraph(attributePaths = {"driver", "race"})
@Query("SELECT rr FROM RaceResult rr WHERE rr.race.matchday.phase.season.id IN :seasonIds")
List<RaceResult> findByRaceMatchdaySeasonIdIn(List<UUID> seasonIds);
```

**Risk 10:** verify in Plan 5 task 1 that PLAYOFF races in `TestDataService.seed()` 2023 fixture have a `Matchday` row (they do — `TestDataService.java:933` creates `playoffMatchday2023 = matchdayRepository.save(new Matchday(playoff2023.getPhase(), "2023 Playoffs", 4))`). Therefore the new finder picks them up.

---

### `SiteGeneratorPhaseAwarenessIT` (test, request-response)

**Analog:** `SiteGeneratorE2ETest.java` Z. 29-105 (Spring Boot + Jsoup + `@MockitoBean YouTubeScraperService` + `@TempDir`).

**Test class skeleton pattern** (lift verbatim from `SiteGeneratorE2ETest.java:29-95`):

```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
class SiteGeneratorPhaseAwarenessIT {

    private Path tempDir;

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private SiteProperties siteProperties;
    @Autowired private SeasonRepository seasonRepository;
    // ... other repositories ...
    @Autowired private TestDataService testDataService;        // SC5 fixture seeder

    @MockitoBean private YouTubeScraperService youTubeScraperService;

    @BeforeAll
    void setUp(@TempDir Path injectedTempDir) {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");
        this.tempDir = injectedTempDir;
        siteProperties.setOutputDir(tempDir.toString());

        // SC5 fixture: D-25 reuses TestDataService.seed() — produces 2023 GROUPS + PLAYOFF
        testDataService.seed();

        siteGeneratorService.generate();
    }

    @Test
    void givenGroupsLayoutSeason_whenGenerate_thenPerGroupAndCombinedFilesExist() throws IOException {
        Path seasonDir = tempDir.resolve("season").resolve("2023");
        assertTrue(Files.exists(seasonDir.resolve("standings.html")));
        assertTrue(Files.exists(seasonDir.resolve("standings-regular-group-a.html")));
        assertTrue(Files.exists(seasonDir.resolve("standings-regular-group-b.html")));

        var doc = Jsoup.parse(Files.readString(seasonDir.resolve("standings.html")));
        assertNotNull(doc.selectFirst("nav.group-tab-row[role=tablist]"));
        assertNotNull(doc.selectFirst("th:contains(Group)"));  // Combined-View column
    }

    // ... givenMultiPhaseSeason..., givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline (golden file), givenPlayoffPhaseWithoutResults...
}
```

**Test-isolation pitfall (Risk 11):** SC5 IT must NOT pre-rename existing seasons with `Test_` prefix the way `SiteGeneratorE2ETest.java:99-104` does — TestDataService 2023 fixture is the test target and its name "Season 2023" survives the `productionSeasons` filter (`SiteGeneratorService.java:79-81`).

---

### `StandingsPageGeneratorTest` etc. (per-helper unit tests)

**Analog:** `SiteGeneratorServiceTest.java` Z. 25-100 — `@SpringBootTest @ActiveProfiles("dev")` + `@TempDir` + Spring-managed bean injection. Each helper unit test follows the same shape, scoped to a single helper.

---

### `SiteGeneratorServiceIT` (Mockito contract test, updated)

**Analog:** itself Z. 79-97.

**What changes:** the constructor argument list at Z. 79-96 must be updated to include the 5 new helper bean mocks (or any new constructor params introduced by Plan 0). Alternatively, the entire IT can be replaced by per-helper unit tests since the D-23 contract it asserts (phase-aware API used) becomes a property of each helper's own tests.

Plan 0 must execute one of these two options atomically with the helper-extraction commit; do not leave a broken test suite between commits.

---

## Shared Patterns

### Service stereotype + constructor injection

**Source:** `org.ctc.sitegen.SiteGeneratorService` Z. 33-37 (and every `org.ctc.domain.service.*Service`).

**Apply to:** every new helper class (`StandingsPageGenerator`, `DriverRankingPageGenerator`, `MatchdaysPageGenerator`, `TeamProfilePageGenerator`, `DriverProfilePageGenerator`, `TemplateWriter`, `SiteSlugger`).

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsPageGenerator {
    private final TemplateEngine templateEngine;
    private final TemplateWriter templateWriter;
    private final SiteSlugger slugger;
    // ... domain-service collaborators as final fields ...
}
```

CLAUDE.md mandate: `@RequiredArgsConstructor` + `final` fields for constructor injection. `@Slf4j` for logging.

### `@Transactional(readOnly = true)` on every read path

**Source:** `SiteGeneratorService.generate()` Z. 65 + every read method on `StandingsService` / `DriverRankingService` / `SeasonPhaseService`.

**Apply to:** the orchestrator's `generate()` method (already applied — keep it). Helper-class entry methods are called within the orchestrator's transaction; OSIV keeps the session open for Thymeleaf. Do NOT add `@Transactional` separately on each helper method — would create nested transactions.

### Server-side feature flags into Thymeleaf (Lean Templates)

**Source:** `templates/admin/standings.html` Z. 75-97 (`showGroupColumn` / `showBuchholz`) + UI-SPEC §"Standings Table — Group Column Contract".

**Apply to:** every rewritten template in Phase 62.

```java
// in helper class
ctx.setVariable("showPhaseTabs", phases.size() >= 2);
ctx.setVariable("showGroupTabs", phase.getLayout() == PhaseLayout.GROUPS);
ctx.setVariable("showGroupColumn", combinedViewOfGroupsLayout);
ctx.setVariable("showBuchholz", phase.getFormat() == PhaseFormat.SWISS && groupId != null);
ctx.setVariable("showPhaseBreakdown", phases.size() >= 2);  // team-profile / driver-profile
ctx.setVariable("emptyState", standings.isEmpty());
```

```html
<!-- in template -->
<th th:if="${showGroupColumn}">Group</th>
<div th:if="${emptyState}" class="empty-phase-banner">...</div>
```

CLAUDE.md mandate "Keep Thymeleaf Templates Lean" — no SpEL projections, no nested conditions.

### RaceLineup as Source of Truth

**Source:** `SiteGeneratorService.generateTeamProfiles` Z. 296-313 + `feedback_racelineup_source_of_truth`.

**Apply to:** `TeamProfilePageGenerator` (preserve verbatim) and any new code path that resolves driver↔team for a given phase. Always prefer `RaceLineup` over `SeasonDriver`; fall back to `SeasonDriver` only when no `RaceLineup` exists for the season.

### Test-data isolation (`!s.getName().contains("Test")` filter)

**Source:** `SiteGeneratorService.generate()` Z. 79-81.

**Apply to:** any test that wants its fixture seasons to appear in generated output. SC5 IT uses `TestDataService.seed()` whose season names ("Season 2023" etc.) deliberately do NOT contain "Test" → they survive the production filter. Any test that does NOT want its fixture in the output (typical existing `SiteGeneratorE2ETest`) must rename existing seasons with the `Test_` prefix in `@BeforeAll` (Z. 99-104 of E2E test).

### Slug generation (D-02 / D-03 stability)

**Source:** `SiteGeneratorService.slugify` Z. 823-828 → moves to `SiteSlugger.slugify`.

**Apply to:** every file-naming and cross-link computation in Phase 62. Phase slug = `phase.getPhaseType().name().toLowerCase(Locale.ENGLISH)` (NOT `slugger.slugify(phaseType.name())` — D-02 mandates lowercased PhaseType name, no further mangling). Group slug = `slugger.slugify(group.getName())`.

### `@SpringBootTest @ActiveProfiles("dev") + @TempDir + Jsoup` for sitegen IT

**Source:** `SiteGeneratorE2ETest.java` Z. 29-95 + `SiteGeneratorServiceTest.java` Z. 25-95.

**Apply to:** SC5 IT (`SiteGeneratorPhaseAwarenessIT`) and per-helper unit tests. Use `@MockitoBean YouTubeScraperService` to skip network calls. Inject `siteGeneratorService` + `siteProperties.setOutputDir(tempDir.toString())` to redirect output.

### Tracked Behavior Change PR/Release-Notes annotation

**Source:** `feedback_orchestrator_discipline` + Phase 58 / Phase 61 D-23 pattern + UI-SPEC §"Tracked Behavior Changes".

**Apply to:** Plan 5 (D-19 alltime change) — PR description must explicitly call out "TRACKED BEHAVIOR CHANGE: alltime-standings.html and alltime-driver-ranking.html numbers will recompute and may shift visibly for any historical season with a PLAYOFF or PLACEMENT phase." Mirror the wording used in Phase 61 PR.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/test/resources/sitegen/baseline/single-league-standings.html` | test fixture (golden snapshot) | n/a | First golden-file snapshot in the project; create by capturing pre-Plan-1 output of a single-REGULAR-LEAGUE production season's `standings.html` and committing it as the byte-identity baseline for SC4 (Risk 2). Planner instructs the executor to capture it via `./mvnw spring-boot:run` against a known fixture, then `cp docs/site/season/{slug}/standings.html src/test/resources/sitegen/baseline/`. |

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/sitegen/`, `src/main/java/org/ctc/domain/service/`, `src/main/java/org/ctc/domain/repository/`, `src/main/resources/templates/site/`, `src/main/resources/templates/admin/{season-detail,standings,playoff-bracket}.html`, `src/main/resources/static/site/css/style.css`, `src/test/java/org/ctc/sitegen/`.

**Files scanned:** 18 source files + 8 templates + 1 CSS file + 3 test files = 30 files (line ranges loaded as targeted reads only — no whole-file loads of files > 200 LOC).

**Pattern extraction date:** 2026-05-02
