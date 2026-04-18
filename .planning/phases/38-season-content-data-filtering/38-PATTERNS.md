# Phase 38: Season Content & Data Filtering - Pattern Map

**Mapped:** 2026-04-16
**Files analyzed:** 7 (1 Java service, 5 Thymeleaf templates, 1 CSS file, 1 test class)
**Analogs found:** 7 / 7

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` | service | transform / batch | itself (in-place edit) | exact |
| `src/main/resources/templates/site/index.html` | template | request-response | `matchday.html` (same match-meta pattern) | exact |
| `src/main/resources/templates/site/standings.html` | template | request-response | `driver-ranking.html` (same section-title + season.name pattern) | exact |
| `src/main/resources/templates/site/matchday.html` | template | request-response | `standings.html` (same section-title + season.name pattern) | exact |
| `src/main/resources/templates/site/driver-ranking.html` | template | request-response | `standings.html` (same section-title + season.name pattern) | exact |
| `src/main/resources/templates/site/archive.html` | template | request-response | itself (in-place tighten + enrich) | exact |
| `src/main/resources/static/site/css/style.css` | config/style | — | itself (extend with new class) | exact |
| `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` | test | batch | itself (in-place additions) | exact |

---

## Pattern Assignments

### `SiteGeneratorService.java` (service, transform/batch)

**Analog:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` (in-place edit)

**Service class declaration pattern** (lines 27-30):
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteGeneratorService {
```

**generate() entry point — current season loading** (lines 54-82):
```java
@Transactional(readOnly = true)
public GenerationResult generate() {
    var result = new GenerationResult();
    Path outPath = Path.of(outputDir);

    try {
        Files.createDirectories(outPath);

        // Find active season
        var activeSeason = seasonRepository.findByActiveTrue().orElse(null);
        String activeSeasonSlug = activeSeason != null ? slugify(activeSeason.getDisplayLabel()) : "";
        var allSeasons = seasonRepository.findAll();

        // Generate index
        generateIndex(outPath, activeSeason, allSeasons, activeSeasonSlug, result);

        // Generate pages for each season
        for (var season : allSeasons) {  // <-- CHANGE: replace allSeasons with productionSeasons
            generateStandings(outPath, season, activeSeasonSlug, result);
            generateDriverRanking(outPath, season, activeSeasonSlug, result);
            generateMatchdays(outPath, season, activeSeasonSlug, result);
            generateTeamProfiles(outPath, season, activeSeasonSlug, result);
            generateDriverProfiles(outPath, season, activeSeasonSlug, result);
            generatePlayoffBracket(outPath, season, activeSeasonSlug, result);
        }

        // Generate archive
        generateArchive(outPath, allSeasons, activeSeasonSlug, result);  // <-- CHANGE: productionSeasons

        // Copy static assets
        copyAssets(outPath, result);

        log.info("Site generation complete: {} pages", result.getPagesGenerated());
    } catch (IOException e) {
        log.error("Site generation failed", e);
        result.addError("Generation failed: " + e.getMessage());
    }

    return result;
}
```

**CONT-06 required change — insert filter after line 65 (after `var allSeasons = ...`):**
```java
var allSeasons = seasonRepository.findAll();
var productionSeasons = allSeasons.stream()
        .filter(s -> !s.getName().contains("Test"))
        .toList();
// Then use productionSeasons for the for-loop and generateArchive()
// Keep activeSeasonSlug computed from activeSeason (independent fetch via findByActiveTrue())
```

**generateArchive() signature** (lines 238-247) — receives the list passed in, no internal load:
```java
private void generateArchive(Path outPath, List<Season> allSeasons, String activeSeasonSlug,
                               GenerationResult result) throws IOException {
    var ctx = new Context(Locale.GERMAN);
    var seasonEntries = allSeasons.stream()
            .map(s -> new SeasonEntry(s, slugify(s.getDisplayLabel())))
            .toList();
    ctx.setVariable("seasonEntries", seasonEntries);
    writeTemplate("site/archive", ctx, outPath.resolve("archive.html"), activeSeasonSlug);
    result.incrementPages();
}
```

**writeTemplate() — context variable injection pattern** (lines 249-263):
```java
private void writeTemplate(String templateName, Context context, Path outputFile,
                            String activeSeasonSlug) throws IOException {
    Path outRoot = Path.of(outputDir);
    Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
    Path relativeRoot = outputFile.getParent().relativize(outRoot);
    context.setVariable("assetsPath", relativeAssets.toString().replace('\\', '/'));
    String rootStr = relativeRoot.toString().replace('\\', '/');
    context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
    context.setVariable("activeSeasonSlug", activeSeasonSlug != null ? activeSeasonSlug : "");

    String html = templateEngine.process(templateName, context);
    Files.writeString(outputFile, html);
    log.debug("Generated: {}", outputFile);
}
```
Note: `season` is already in context for every per-season call (`ctx.setVariable("season", season)`). No extra variables needed for CONT-01 — `season.year` and `season.number` are directly accessible via entity getters (OSIV + `@Transactional(readOnly=true)` keeps session open).

---

### `index.html` (template, request-response)

**Analog:** `src/main/resources/templates/site/matchday.html` (identical match-meta block)

**Current hero-label pattern** (line 7) — target for CONT-01:
```html
<div class="hero-label" th:if="${season}" th:text="'Season ' + ${season.name}"></div>
```

**Current match-meta block in index.html** (lines 51-55) — target for CONT-07:
```html
<div class="match-meta">
    <span th:text="${race.track ?: ''}"></span>
    <span th:if="${race.track != null && race.car != null}"> — </span>
    <span th:text="${race.car ?: ''}"></span>
</div>
```

**CONT-07 required change — add `th:if` guard to the outer div:**
```html
<div class="match-meta" th:if="${race.track != null or race.car != null}">
    <span th:text="${race.track ?: ''}"></span>
    <span th:if="${race.track != null && race.car != null}"> — </span>
    <span th:text="${race.car ?: ''}"></span>
</div>
```
The inner separator guard is already correct (both must be non-null for the dash to render, satisfying D-10).

**CONT-01 required change for hero-label — append year:**
```html
<div class="hero-label" th:if="${season}"
     th:text="'Season ' + ${season.name} + ' — ' + ${season.year}"></div>
```

---

### `standings.html` (template, request-response)

**Analog:** `src/main/resources/templates/site/driver-ranking.html` (same section-title + season.name structure)

**Current section-title pattern** (line 7) — target for CONT-01:
```html
<h2 class="section-title" th:text="'Team Standings — ' + ${season.name}"></h2>
```

**CONT-01 required change — add `.season-meta` subtitle after the h2:**
```html
<h2 class="section-title" th:text="'Team Standings — ' + ${season.name}"></h2>
<p class="season-meta" th:text="${season.year + ' | Season #' + season.number}"></p>
```

**How `season` arrives in context** — from `generateStandings()` (lines 117-127 of service):
```java
var ctx = new Context(Locale.GERMAN);
ctx.setVariable("season", season);
ctx.setVariable("standings", standingsService.calculateStandings(season.getId()));
```
`season.year` (int) and `season.number` (int) are getters on the entity — Thymeleaf resolves them via `getYear()` / `getNumber()`.

---

### `matchday.html` (template, request-response)

**Analog:** `src/main/resources/templates/site/standings.html` (same section-title + season.name pattern); match-meta block is the canonical source for index.html

**Current section-title pattern** (line 7):
```html
<h2 class="section-title" th:text="${matchday.label + ' — ' + season.name}"></h2>
```

**Current match-meta block** (lines 16-19) — canonical source:
```html
<div class="match-meta">
    <span th:text="${race.track ?: ''}"></span>
    <span th:if="${race.track != null && race.car != null}"> — </span>
    <span th:text="${race.car ?: ''}"></span>
</div>
```

**CONT-01 required change — add `.season-meta` subtitle:**
```html
<h2 class="section-title" th:text="${matchday.label + ' — ' + season.name}"></h2>
<p class="season-meta" th:text="${season.year + ' | Season #' + season.number}"></p>
```

**CONT-07 required change — same as index.html, add `th:if` to outer div:**
```html
<div class="match-meta" th:if="${race.track != null or race.car != null}">
    <span th:text="${race.track ?: ''}"></span>
    <span th:if="${race.track != null && race.car != null}"> — </span>
    <span th:text="${race.car ?: ''}"></span>
</div>
```

**How `race.track` and `race.car` arrive** — `RaceView` record (lines 5-6 of RaceView.java):
```java
public record RaceView(String homeTeamShortName, String awayTeamShortName,
                       String track, String car, int homeTotal, int awayTotal,
                       boolean hasResults, List<ResultView> results) {
```
`track` and `car` are nullable Strings (set to `null` when the Race has no track/car entity).

---

### `driver-ranking.html` (template, request-response)

**Analog:** `src/main/resources/templates/site/standings.html` (identical section-title + season.name structure)

**Current section-title pattern** (line 7):
```html
<h2 class="section-title" th:text="'Driver Ranking — ' + ${season.name}"></h2>
```

**CONT-01 required change — add `.season-meta` subtitle:**
```html
<h2 class="section-title" th:text="'Driver Ranking — ' + ${season.name}"></h2>
<p class="season-meta" th:text="${season.year + ' | Season #' + season.number}"></p>
```

No match-meta or filter changes needed for this template.

---

### `archive.html` (template, request-response)

**Analog:** itself (in-place enrichment)

**Current season column** (line 15) — target for CONT-01:
```html
<td class="font-bold text-white" th:text="${entry.season.name}"></td>
```

**CONT-01 required change — add `.season-meta` line inside `<td>`:**
```html
<td class="font-bold text-white">
    <span th:text="${entry.season.name}"></span>
    <p class="season-meta" th:text="${entry.season.year + ' | #' + entry.season.number}"></p>
</td>
```

**Current period column** (lines 16-20) — already correct for CONT-07; verify no change needed:
```html
<td class="text-dim">
    <span th:if="${entry.season.startDate}" th:text="${entry.season.startDate}"></span>
    <span th:if="${entry.season.startDate != null and entry.season.endDate != null}"> — </span>
    <span th:if="${entry.season.endDate}" th:text="${entry.season.endDate}"></span>
</td>
```
Thymeleaf evaluates `th:if="${entry.season.startDate}"` as falsy when the value is `null` — so this already produces an empty cell when both dates are null. A test is needed to confirm (CONT-07 period test). No structural change expected.

**Existing inline styles to note** (lines 22, 27) — do NOT add more; QUAL-01 scope:
```html
<span th:if="${entry.season.active}" style="color:#4fc3f7;">Active</span>
<a ... style="color:var(--accent); text-decoration:none;">Standings</a>
```

**`entry.season` access** — `SeasonEntry` is a Java record (`record SeasonEntry(Season season, String slug) {}`). Thymeleaf 3.x resolves record accessor methods as property expressions. `entry.season.year` calls `entry.season()` then `getYear()`. Already confirmed by existing `entry.season.name` usage.

---

### `style.css` (config/style)

**Analog:** `src/main/resources/static/site/css/style.css` (extend in-place)

**Existing `.section-title` definition** (lines 142-149) — placement anchor for new class:
```css
.section-title {
    font-family: 'Conthrax', sans-serif;
    font-size: 13px;
    text-transform: uppercase;
    letter-spacing: 2px;
    color: var(--text-muted);
    margin-bottom: 16px;
}
```

**CONT-01 new class to insert after `.section-title`** (after line 149):
```css
.season-meta {
    font-size: 12px;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-top: 4px;
}
```
Uses `--text-muted: #555` (defined at line 19). No new CSS custom properties needed.

---

### `SiteGeneratorServiceTest.java` (test, integration)

**Analog:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` (in-place additions)

**Class-level setup pattern** (lines 22-146):
```java
@SpringBootTest
@ActiveProfiles("dev")
class SiteGeneratorServiceTest {

    private String uniqueSuffix;
    // @Autowired repositories...

    @TempDir
    Path tempDir;

    private Season season;
    private Race testRace;
    private Driver driver1;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        // ... DB setup ...
        siteGeneratorService.setOutputDir(tempDir.toString());
    }
```

**CRITICAL: setUp() season name must be renamed before CONT-06 filter is implemented.**
Current (line 92): `season = new Season("Gen Test " + uniqueSuffix, 2026, 1);`
Required: `season = new Season("Gen Season " + uniqueSuffix, 2026, 1);`
Reason: The filter `!s.getName().contains("Test")` would exclude the test season and break all 13 existing tests.

**Jsoup HTML assertion pattern** (lines 235-245):
```java
@Test
void givenRaceResults_whenGenerate_thenStandingsPageContainsRows() throws IOException {
    // when
    siteGeneratorService.generate();

    // then
    var html = Files.readString(seasonDir().resolve("standings.html"));
    var doc = Jsoup.parse(html);

    var rows = doc.select("tbody tr");
    assertFalse(rows.isEmpty(), "Standings table should have rows");
}
```

**seasonDir() helper** (lines 148-151):
```java
private Path seasonDir() {
    return tempDir.resolve("season").resolve(slugify(season.getDisplayLabel()));
}
```
Note: After renaming the season, `season.getDisplayLabel()` returns `"2026 | #1 | Gen Season XYZ"` — the slug changes accordingly. All existing `seasonDir()` calls remain valid.

**File-existence assertion pattern** (lines 168-174):
```java
@Test
void givenActiveSeason_whenGenerate_thenCreatesStandingsPage() {
    // when
    siteGeneratorService.generate();

    // then
    assertTrue(Files.exists(seasonDir().resolve("standings.html")), "standings.html should exist");
}
```

**Test for absent element** (CSS selector + isEmpty pattern from line 269):
```java
var absoluteLinks = doc.select("a[href^='/']");
assertTrue(absoluteLinks.isEmpty(), "Root-level pages should have no absolute /... links");
```
Apply same pattern for asserting `.match-meta` is absent:
```java
assertTrue(doc.select(".match-meta").isEmpty(), "match-meta should not render when both track and car are null");
```

**Test data mutation pattern** (testRace field set in setUp(), mutated in test — lines 276-288 for reference):
```java
@Test
void givenRaceLineupWithSubTeam_whenGenerate_thenDriverAttributedToSubTeam() throws IOException {
    // given — mutate state on existing entities from setUp()
    var subTeam = new Team("Sub Team " + uniqueSuffix, "GSUB" + uniqueSuffix);
    subTeam.setParentTeam(testRace.getHomeTeam());
    teamRepository.save(subTeam);
    raceLineupRepository.save(new RaceLineup(testRace, driver1, subTeam));

    // when
    siteGeneratorService.generate();

    // then
    var html = Files.readString(seasonDir().resolve("matchday/spieltag-1.html"));
    assertTrue(html.contains("GSUB" + uniqueSuffix), "...");
}
```
For CONT-07 null-track test, mutate `testRace` by setting `track` and `car` to null and re-saving.

**Test naming convention** — BDD Given-When-Then:
```
givenContext_whenAction_thenExpectedResult()
```
Examples from RESEARCH.md test map:
- `givenTestSeason_whenGenerate_thenNoSeasonPagesCreated`
- `givenTestSeason_whenGenerate_thenNotInArchive`
- `givenRaceWithNoTrackOrCar_whenGenerate_thenMatchMetaAbsent`
- `givenSeason_whenGenerate_thenStandingsHasSeasonMeta`
- `givenSeason_whenGenerate_thenHeroLabelContainsYear`

---

## Shared Patterns

### Thymeleaf `th:if` Conditional Rendering
**Source:** `src/main/resources/templates/site/matchday.html` lines 22-23
**Apply to:** `matchday.html` outer `match-meta` div, `index.html` outer `match-meta` div
```html
<!-- Existing inner guard (already correct per D-10): -->
<span th:if="${race.track != null && race.car != null}"> — </span>

<!-- New outer guard pattern to add (CONT-07): -->
<div class="match-meta" th:if="${race.track != null or race.car != null}">
```
Note: Thymeleaf uses `and`/`or` for SpEL boolean operators inside `th:if`. Both `&&`/`and` and `||`/`or` are valid — the existing code uses `&&` and `!=`; new guards should use `or` for clarity (D-09 specifies `or`).

### Season Entity Direct Field Access
**Source:** `src/main/java/org/ctc/domain/model/Season.java` lines 30-35, constructor lines 93-97
**Apply to:** All templates using `season.year` and `season.number`
```java
@Column(name = "season_year", nullable = false)
private int year;       // getter: getYear()

@Column(name = "season_number", nullable = false)
private int number;     // getter: getNumber()
```
Thymeleaf resolves `${season.year}` → `season.getYear()`. Returns primitive int (never null). Safe for direct use in `th:text` concatenation.

### Stream Filter Pattern (Java)
**Source:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` lines 241-243 (existing stream in generateArchive)
**Apply to:** New filter step in `generate()` for CONT-06
```java
// Existing pattern (in generateArchive):
var seasonEntries = allSeasons.stream()
        .map(s -> new SeasonEntry(s, slugify(s.getDisplayLabel())))
        .toList();

// New filter pattern (in generate(), before for-loop):
var productionSeasons = allSeasons.stream()
        .filter(s -> !s.getName().contains("Test"))
        .toList();
```

### Jsoup HTML Assertion Pattern
**Source:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` lines 235-245, 248-258
**Apply to:** All new test methods in `SiteGeneratorServiceTest`
```java
// Read generated file
var html = Files.readString(seasonDir().resolve("standings.html"));
var doc = Jsoup.parse(html);

// Assert element exists
assertFalse(doc.select(".season-meta").isEmpty(), ".season-meta element should exist");

// Assert text content
assertTrue(doc.select(".season-meta").text().contains("2026"), "season-meta should contain year");

// Assert element absent
assertTrue(doc.select(".match-meta").isEmpty(), "match-meta should not render");
```

### CSS Custom Property Usage
**Source:** `src/main/resources/static/site/css/style.css` lines 18-23 (`:root` block)
**Apply to:** New `.season-meta` class
```css
:root {
    --text-muted: #555;     /* use for season-meta color */
    --text-dim: #888;       /* used by .text-dim class */
    --accent: #4fc3f7;      /* used for active/link styling */
}
```
New classes must reference `var(--text-muted)` or other defined custom properties — no hardcoded hex colors except when replacing existing inline styles.

---

## No Analog Found

No files in this phase lack an analog. All changes are in-place modifications to well-established files.

---

## Critical Pre-Condition

**Wave 0 prerequisite — must happen before any other change:**

Rename `setUp()` season in `SiteGeneratorServiceTest.java` line 92:
```java
// BEFORE (causes all 13 tests to break after CONT-06 filter is implemented):
season = new Season("Gen Test " + uniqueSuffix, 2026, 1);

// AFTER (does not match "Test" filter, existing tests continue to pass):
season = new Season("Gen Season " + uniqueSuffix, 2026, 1);
```
This is the single highest-risk item in the phase. The planner must sequence this as Wave 0, Task 0 — before any TDD red tests are written for CONT-06.

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/sitegen/`, `src/main/resources/templates/site/`, `src/main/resources/static/site/css/`, `src/test/java/org/ctc/sitegen/`, `src/main/java/org/ctc/domain/model/Season.java`
**Files scanned:** 8 source files read in full
**Pattern extraction date:** 2026-04-16
