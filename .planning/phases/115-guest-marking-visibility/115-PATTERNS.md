# Phase 115: Guest Marking & Visibility — Pattern Map

**Mapped:** 2026-06-01
**Files analyzed:** 17 new/modified files
**Analogs found:** 17 / 17

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ResultsGraphicService.java` | service | request-response | `LineupGraphicService.java` | exact |
| `ProvisionalScoresGraphicService.java` | service | request-response | `ResultsGraphicService.java` | exact |
| `LineupGraphicService.java` | service | request-response | self (only service with `RaceLineup` input) | exact |
| `RaceService.java` | service | CRUD | `RaceService.java` `getRaceDetailData` loop | self-analog |
| `DriverRankingService.java` | service | transform | `DriverRankingService.DriverRanking` inner class | self-analog |
| `DriverProfilePageGenerator.java` | service/sitegen | transform | `DriverProfilePageGenerator.writeDriverProfile` | self-analog |
| `templates/admin/fragments/guest-marker.html` | template/fragment | request-response | `site/fragments/match-card.html` | role-match |
| `templates/admin/results-render.html` | template | request-response | `lineup-render.html` driver-info block | exact |
| `templates/admin/provisional-scores-render.html` | template | request-response | `results-render.html` driver-name block | exact |
| `templates/admin/lineup-render.html` | template | request-response | self (`.driver-info.home` / `.driver-info.away`) | self-analog |
| `templates/admin/race-detail.html` | template | request-response | `matchday-detail.html` chip/team-map pattern | role-match |
| `templates/admin/matchday-detail.html` | template | request-response | `matchday-detail.html` chip loop (lu variable) | self-analog |
| `templates/site/driver-ranking.html` | template | request-response | `alltime-driver-ranking.html` row pattern | exact |
| `templates/site/alltime-driver-ranking.html` | template | request-response | `driver-ranking.html` row pattern | exact |
| `templates/site/driver-profile.html` | template | request-response | `driver-profile.html` `${results}` iteration | self-analog |
| `static/admin/css/admin.css` | config/style | n/a | admin.css `:root` + `.badge` rules | self-analog |
| `static/site/css/style.css` | config/style | n/a | style.css `:root` | self-analog |

---

## Pattern Assignments

### `ResultsGraphicService.java` (service, request-response)

**Analog:** `LineupGraphicService.java` — only existing graphic service that already injects `RaceLineupRepository` and passes the full `List<RaceLineup>` to its build method.

**Imports pattern** (`LineupGraphicService.java` lines 1–20):
```java
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.service.ScoringService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
```

**Constructor injection pattern** — add `RaceLineupRepository` alongside `ScoringService` (`ResultsGraphicService.java` lines 29–36):
```java
private final ScoringService scoringService;
// ADD:
private final RaceLineupRepository raceLineupRepository;

public ResultsGraphicService(TemplateEngine templateEngine,
                             ScoringService scoringService,
                             RaceLineupRepository raceLineupRepository,
                             @Value("${app.upload-dir:uploads}") String uploadDir) {
    super(templateEngine, uploadDir);
    this.scoringService = scoringService;
    this.raceLineupRepository = raceLineupRepository;
}
```

**Core pattern — record expansion** (`ResultsGraphicService.java` lines 182–184):
```java
// BEFORE:
public record DriverResultRow(String homeDriver, String homeNickname, int homePoints,
                              int awayPoints, String awayDriver, String awayNickname) {}

// AFTER (append two boolean fields at the end):
public record DriverResultRow(String homeDriver, String homeNickname, int homePoints,
                              int awayPoints, String awayDriver, String awayNickname,
                              boolean homeIsGuest, boolean awayIsGuest) {}
```

**Core pattern — buildResultRows guest flag resolution** (`ResultsGraphicService.java` lines 148–175):

The existing loop already separates `homeResults` and `awayResults` via `scoringService.isDriverInTeam`. Add a per-result `raceLineupRepository.findByRaceIdAndDriverId` call at row-construction time to extract the `isGuest` flag. The `RaceLineup.guest` field is a primitive boolean, not a lazy relation — safe to read without additional joins.

```java
for (int i = 0; i < TEAM_DRIVERS; i++) {
    // ... existing name/points resolution unchanged ...
    boolean homeIsGuest = i < homeResults.size() &&
        raceLineupRepository.findByRaceIdAndDriverId(raceId, homeResults.get(i).getDriver().getId())
            .map(RaceLineup::isGuest).orElse(false);
    boolean awayIsGuest = i < awayResults.size() &&
        raceLineupRepository.findByRaceIdAndDriverId(raceId, awayResults.get(i).getDriver().getId())
            .map(RaceLineup::isGuest).orElse(false);
    rows.add(new DriverResultRow(homeName, homeNick, homePoints, awayPoints, awayName, awayNick,
                                  homeIsGuest, awayIsGuest));
}
```

**Test analog:** `ResultsGraphicServiceTest.java` — mock `RaceLineupRepository` alongside `ScoringService`; same `createService()` factory pattern; assert `row.homeIsGuest()` / `row.awayIsGuest()` in new test method following Given-When-Then naming.

---

### `ProvisionalScoresGraphicService.java` (service, request-response)

**Analog:** `ResultsGraphicService.java` — same graphic service structure (extends `AbstractGraphicService`, uses `ScoringService`, custom template support).

**Imports pattern** — inject `RaceLineupRepository` as with `ResultsGraphicService` above.

**Constructor injection pattern** — add `RaceLineupRepository` alongside `ScoringService` (`ProvisionalScoresGraphicService.java` lines 29–39):
```java
private final ScoringService scoringService;
// ADD:
private final RaceLineupRepository raceLineupRepository;

public ProvisionalScoresGraphicService(TemplateEngine templateEngine,
                                       ScoringService scoringService,
                                       RaceLineupRepository raceLineupRepository,
                                       @Value("${app.upload-dir:uploads}") String uploadDir,
                                       PlaywrightScreenshotter screenshotter) {
    super(templateEngine, uploadDir);
    this.scoringService = scoringService;
    this.raceLineupRepository = raceLineupRepository;
    this.screenshotter = screenshotter;
}
```

**Core pattern — record expansion** (`ProvisionalScoresGraphicService.java` lines 189–191):
```java
// BEFORE:
public record ProvisionalRow(String driverName, int position, int qualiPosition, boolean fastestLap,
                              int ptsRace, int ptsQuali, int ptsFl, int total) {}

// AFTER (isGuest appended as last field):
public record ProvisionalRow(String driverName, int position, int qualiPosition, boolean fastestLap,
                              int ptsRace, int ptsQuali, int ptsFl, int total, boolean isGuest) {}
```

**Core pattern — buildContext guest flag** (`ProvisionalScoresGraphicService.java` lines 64–78):

The `buildContext` loop iterates `race.getResults()`. The `isGuest` flag must be resolved per result in `toRow()` or in the loop where `toRow()` is called. Since `toRow()` only receives `RaceResult` (no `RaceLineup` access), the lookup happens in `buildContext`:

```java
for (RaceResult result : race.getResults()) {
    boolean isGuest = raceLineupRepository
        .findByRaceIdAndDriverId(raceId, result.getDriver().getId())
        .map(RaceLineup::isGuest).orElse(false);
    ProvisionalRow row = toRow(result, isGuest);
    // ... add to homeRows / awayRows ...
}
```

`toRow` gains `boolean isGuest` as parameter; `emptyRow()` returns `isGuest=false` (line 134):
```java
private ProvisionalRow emptyRow() {
    return new ProvisionalRow("n/a", 0, 0, false, 0, 0, 0, 0, false);
}
```

**Test analog:** `ProvisionalScoresGraphicServiceTest.java` — same `createService()` factory, mock `RaceLineupRepository`, assert `row.isGuest()`.

---

### `LineupGraphicService.java` (service, request-response)

**Analog:** Self — the only existing graphic service that already receives `List<RaceLineup>` directly. `RaceLineup.guest` is a primitive field on each lineup object, directly accessible.

**Core pattern — record expansion** (`LineupGraphicService.java` lines 200–201):
```java
// BEFORE:
public record DriverPairing(String homeDriver, String homeNickname,
                            String awayDriver, String awayNickname) {}

// AFTER:
public record DriverPairing(String homeDriver, String homeNickname, String awayDriver,
                            String awayNickname, boolean homeIsGuest, boolean awayIsGuest) {}
```

**Core pattern — buildPairings guest flag** (`LineupGraphicService.java` lines 128–147):

The existing code maps `homeEntries` / `awayEntries` via `.map(RaceLineup::getDriver)` which LOSES the `RaceLineup` reference. Change to retain the lineup:

```java
// BEFORE:
var homeEntries = lineups.stream()
    .filter(lu -> isTeamOrSubTeam(lu.getTeam(), homeTeam))
    .map(RaceLineup::getDriver)
    .toList();

// AFTER — keep full RaceLineup:
var homeEntries = lineups.stream()
    .filter(lu -> isTeamOrSubTeam(lu.getTeam(), homeTeam))
    .toList();
var awayEntries = lineups.stream()
    .filter(lu -> isTeamOrSubTeam(lu.getTeam(), awayTeam))
    .toList();

// Then in the loop:
String homePsn = i < homeEntries.size() ? homeEntries.get(i).getDriver().getPsnId() : "n/a";
boolean homeIsGuest = i < homeEntries.size() && homeEntries.get(i).isGuest();
// ... etc.
pairings.add(new DriverPairing(homePsn, homeNick, awayPsn, awayNick, homeIsGuest, awayIsGuest));
```

**Test analog:** `LineupGraphicServiceTest.java` — same `new RaceLineup(race, driver, team)` constructor; note `RaceLineup` has a 4-arg constructor with `boolean guest` (`RaceLineup.java` line 47): `new RaceLineup(race, driver, team, true)` for guest fixtures.

---

### `RaceService.java` (service, CRUD)

**Analog:** Self — `getRaceDetailData` method (lines 77–153) already builds `driverTeamMap` in the same result-iteration loop where the guest flag can be co-extracted.

**Core pattern — parallel guestDriverMap** (`RaceService.java` lines 92–103):
```java
// EXISTING loop (copy pattern for guestDriverMap):
driverTeamMap = new HashMap<>();
// ADD parallel map:
Map<UUID, Boolean> guestDriverMap = new HashMap<>();
for (var result : race.getResults()) {
    var lineup = raceLineupRepository.findByRaceIdAndDriverId(
            race.getId(), result.getDriver().getId());
    var teamName = lineup
        .map(rl -> rl.getTeam().getShortName())
        .orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
            .filter(sd -> sd.getSeason().getId().equals(sid))
            .map(sd -> sd.getTeam().getShortName())
            .findFirst().orElse("?"));
    driverTeamMap.put(result.getDriver().getId(), teamName);
    // ADD:
    guestDriverMap.put(result.getDriver().getId(),
        lineup.map(RaceLineup::isGuest).orElse(false));
}
```

**Record expansion** (`RaceService.java` lines 349–359) — `RaceDetailData` has 21 fields; add `guestDriverMap` alongside `driverTeamMap`:
```java
public record RaceDetailData(Race race, int homeTotal, int awayTotal,
                             Map<UUID, String> driverTeamMap,
                             Map<UUID, Boolean> guestDriverMap,   // ADD after driverTeamMap
                             boolean canGenerateLineup,
                             // ... all remaining fields unchanged ...
```

**Single constructor call** (`RaceService.java` line 142) — the one `new RaceDetailData(...)` invocation must be updated to include `guestDriverMap` in the correct position.

**Test analog:** `RaceServiceTest.java` — `@Mock RaceLineupRepository raceLineupRepository`, `@InjectMocks RaceService service`. New test asserts `result.guestDriverMap()` contains `true` for a driver whose mocked lineup returns `isGuest=true`.

---

### `DriverRankingService.java` (service, transform)

**Analog:** Self — `DriverRanking` inner class (lines 207–274) uses mutable state fields (`bestPosition`, `racesCount`) mutated after construction; `hasGuestAppearance` follows the same pattern.

**Core pattern — inner class field + methods** (append after line 216, before constructor at 218):
```java
public static class DriverRanking {
    private final Driver driver;
    private final Team team;
    private int totalPoints;
    // ... existing fields ...
    private boolean hasGuestAppearance;   // ADD

    // ... existing constructor + addResult + getters unchanged ...

    public void markGuestAppearance() {   // ADD
        this.hasGuestAppearance = true;
    }

    public boolean isHasGuestAppearance() {   // ADD — Java boolean naming convention
        return hasGuestAppearance;
    }
}
```

**Core pattern — calling markGuestAppearance in calculateRankingForPhase** (lines 48–55):

After `rankingMap.computeIfAbsent`, look up the lineup for this result's race and call `markGuestAppearance()` if `isGuest`:

```java
for (RaceResult result : all) {
    UUID driverId = result.getDriver().getId();
    DriverRanking ranking = rankingMap.computeIfAbsent(driverId, id -> {
        Team team = resolveAttributedTeam(result.getDriver(), seasonId, result.getRace().getId());
        return new DriverRanking(result.getDriver(), team);
    });
    ranking.addResult(result);
    // ADD — per-result guest check (covers both pure guest and dual-role, D-05):
    raceLineupRepository.findByRaceIdAndDriverId(result.getRace().getId(), driverId)
        .filter(RaceLineup::isGuest)
        .ifPresent(rl -> ranking.markGuestAppearance());
}
```

**Same pattern** applies in `calculateAlltimeRanking` (lines 152–159): after `ranking.addResult(result)`, add the same lineup lookup. The existing `guestRaceByDriver` map is already computed (lines 139–150) and the `raceLineupRepository.findByRaceIdAndDriverId` call already exists — reuse/extend it.

**`aggregateAcrossPhases`** (lines 70–90) delegates to `calculateRankingForPhase` per phase and then merges. Since `calculateRankingForPhase` now sets `hasGuestAppearance` correctly, the merge loop must propagate it:
```java
DriverRanking merged = rankingMap.get(driverId);
phaseRanking.getRaceResults().forEach(merged::addResult);
// ADD:
if (phaseRanking.isHasGuestAppearance()) {
    merged.markGuestAppearance();
}
```

**Test analog:** `DriverRankingServiceGuestIT.java` — extend with a new test asserting `guestRow.isHasGuestAppearance() == true` and `dualRoleRow.isHasGuestAppearance() == true`. Fixtures `Test_Guest_1` and `Test_DualRole_1` already exist.

---

### `DriverProfilePageGenerator.java` (service/sitegen, transform)

**Analog:** Self — `writeDriverProfile` method (lines 94–162) currently sets `"results"` as `List<RaceResult>` and `"resultsByPhase"` as `LinkedHashMap<PhaseType, List<RaceResult>>`.

**New inner record** (add before `generate` method, after class-level constants):
```java
public record DriverProfileRow(RaceResult result, boolean guest, String fieldingTeamName) {}
```

**Imports pattern** — already has `RaceLineupRepository` injected (line 51); no new dependency needed.

**Core pattern — N+1 avoidance** (lines 75–81, second pass already fetches `seasonLineups`):

Pre-build a guest-lookup map from the `seasonLineups` already fetched:
```java
var seasonLineups = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId());
// ADD — build lookup keyed by "raceId:driverId" string composite:
Map<String, RaceLineup> guestLookup = new java.util.HashMap<>();
for (var rl : seasonLineups) {
    if (rl.isGuest()) {
        guestLookup.put(rl.getRace().getId() + ":" + rl.getDriver().getId(), rl);
    }
}
```

Pass `guestLookup` into `writeDriverProfile`.

**Core pattern — building DriverProfileRow list** (inside `writeDriverProfile`, replacing `List<RaceResult> results`):
```java
List<DriverProfileRow> profileRows = results.stream()
    .map(r -> {
        var key = r.getRace().getId() + ":" + driver.getId();
        var rl = guestLookup.get(key);
        boolean isGuest = rl != null;
        String fieldingTeamName = isGuest ? rl.getTeam().getShortName() : null;
        return new DriverProfileRow(r, isGuest, fieldingTeamName);
    })
    .toList();
```

Phase-grouped variant:
```java
LinkedHashMap<PhaseType, List<DriverProfileRow>> profileRowsByPhase = new LinkedHashMap<>();
// same pattern — wrap each result in DriverProfileRow using guestLookup
```

**Model variable rename** (lines 138–154): replace `context.setVariable("results", results)` → `"profileRows"`, `"resultsByPhase"` → `"profileRowsByPhase"`. Template access changes from `result.*` to `row.result.*` and adds `row.guest` / `row.fieldingTeamName`.

**Test analog:** `DriverProfilePageGeneratorIT.java` — extend `givenPureGuestDriver_whenGenerate_thenProfilePageExists` to also assert the HTML contains `&#x2605;` (glyph) and "as guest for" for guest race rows.

---

### `templates/admin/fragments/guest-marker.html` (template/fragment, request-response)

**Analog:** `templates/site/fragments/match-card.html` (lines 1–20) — only existing Thymeleaf fragment file; same `<th:block th:fragment="...">` pattern.

**Core pattern** (copy the `match-card.html` fragment structure):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<th:block th:fragment="guestMarker(isGuest)">
    <span class="guest-marker" th:if="${isGuest}" aria-label="Guest driver">&#x2605;</span>
</th:block>
</body>
</html>
```

Usage in admin/site templates: `th:replace="~{admin/fragments/guest-marker :: guestMarker(${...})}"`

**Note:** The `admin/fragments/` directory does not yet exist — create it. The `site/fragments/` directory exists as the structural analog.

---

### `templates/admin/results-render.html` (template, request-response)

**Analog:** Self — lines 97–108 contain the `.result-row` / `.driver-info` block being modified.

**Inline style pattern** (lines 5–74): graphic templates hardcode all colors; no `admin.css` load. Follow the existing `#f5c542` amber pattern for gold accents. Add to the `<style>` block:
```css
.guest-marker { color: #f59e0b; font-size: 0.85em; margin-right: 4px; vertical-align: middle; }
```

**Row rendering pattern** (lines 98–108) — insert inline `<span>` (NOT `th:replace` fragment — see Pitfall 5 in RESEARCH.md):
```html
<div class="driver-info home" th:classappend="${row.homeDriver == 'n/a'} ? 'empty-slot' : ''">
    <span class="guest-marker" th:if="${row.homeIsGuest}" aria-label="Guest driver">&#x2605;</span>
    <span class="driver-name" th:text="${row.homeDriver}"></span>
    ...
</div>
```

Mirror for the `.driver-info.away` block using `row.awayIsGuest`.

---

### `templates/admin/provisional-scores-render.html` (template, request-response)

**Analog:** `results-render.html` inline style + row rendering pattern (above).

Same inline CSS rule in `<style>` block. The template iterates `homeRows` and `awayRows` in a `<td class="col-driver">` cell:
```html
<span class="guest-marker" th:if="${row.isGuest}" aria-label="Guest driver">&#x2605;</span>
<span class="driver-name" th:text="${row.driverName}"></span>
```

---

### `templates/admin/lineup-render.html` (template, request-response)

**Analog:** Self — lines 99–109 are the `.pairing` / `.driver-info` block.

Same inline CSS rule. Insert glyph before `.driver-name` (lines 100–107):
```html
<div class="driver-info home" ...>
    <span class="guest-marker" th:if="${p.homeIsGuest}" aria-label="Guest driver">&#x2605;</span>
    <span class="driver-name" th:text="${p.homeDriver}"></span>
    ...
</div>
<div class="driver-info away" ...>
    <span class="driver-name" th:text="${p.awayDriver}"></span>
    <span class="guest-marker" th:if="${p.awayIsGuest}" aria-label="Guest driver">&#x2605;</span>
```

For `.driver-info.away`, the marker goes AFTER the name (right-aligned text-flow), mirroring how `.away` aligns `flex-end`.

---

### `templates/admin/race-detail.html` (template, request-response)

**Analog:** Self — lines 165–177 contain the `th:each="result : ${race.results}"` table row.

**Map lookup pattern** (line 169 — `driverTeamMap` lookup is the exact analog):
```html
<td>
    <span class="td-label"
          th:text="${driverTeamMap != null ? driverTeamMap[result.driver.id] : '?'}"></span>
    <!-- ADD: -->
    <span class="guest-marker" th:if="${guestDriverMap != null and guestDriverMap[result.driver.id]}"
          aria-label="Guest driver">&#x2605;</span>
</td>
<td>
    <!-- driver name cell, line 171 -->
    <a th:href="..." class="detail-link" th:text="${result.driver.psnId}"></a>
    <!-- Optional D-08 sub-team label already in driverTeamMap.shortName — no extra query needed -->
</td>
```

The controller passes `guestDriverMap` as a model attribute alongside the existing `driverTeamMap` (find where `RaceDetailData.driverTeamMap()` is unpacked into the model and add `guestDriverMap` in the same place).

---

### `templates/admin/matchday-detail.html` (template, request-response)

**Analog:** Self — lines 153–160 contain the `th:each="lu : ${entry.value}"` chip loop.

**Direct OSIV access pattern** (lines 156–158): `lu` is a `RaceLineup` — `lu.guest` is a primitive field, accessible without joins.
```html
<!-- BEFORE (lines 156-158): -->
<span th:each="lu : ${entry.value}" class="chip">
    <span th:text="${lu.driver.psnId}"></span>
</span>

<!-- AFTER: -->
<span th:each="lu : ${entry.value}" class="chip">
    <span class="guest-marker" th:if="${lu.guest}" aria-label="Guest driver">&#x2605;</span>
    <span th:text="${lu.driver.psnId}"></span>
    <span class="guest-label" th:if="${lu.guest}" th:text="${lu.team.shortName}"></span>
</span>
```

No service change needed — OSIV keeps Hibernate session open through Thymeleaf rendering.

---

### `templates/site/driver-ranking.html` (template, request-response)

**Analog:** `alltime-driver-ranking.html` (identical `th:each="r, i : ${driverRanking}"` pattern, same `.entity-link` cell).

**Driver name cell** (line 30):
```html
<td class="font-bold">
    <span class="guest-marker" th:if="${r.hasGuestAppearance}" aria-label="Guest driver">&#x2605;</span>
    <a class="entity-link" th:href="${driverSlugMap.get(r.driver.id)}"
       th:text="${r.driver.psnId}"></a>
</td>
```

---

### `templates/site/alltime-driver-ranking.html` (template, request-response)

**Analog:** `driver-ranking.html` — same pattern. Driver name cell (line 22):
```html
<td class="font-bold">
    <span class="guest-marker" th:if="${r.hasGuestAppearance}" aria-label="Guest driver">&#x2605;</span>
    <a class="entity-link" th:href="${driverSlugMap.get(r.driver.id)}"
       th:text="${r.driver.psnId}"></a>
</td>
```

---

### `templates/site/driver-profile.html` (template, request-response)

**Analog:** Self — lines 33–49 (flat results iteration) and lines 69–88 (phase-grouped iteration).

**Variable rename:** Template currently uses `result` from `${results}` / `${entry.value}`. After the `DriverProfileRow` change, the iteration variable wraps the result:
- Flat: `th:each="row : ${profileRows}"` — access via `row.result.*`
- Phase-grouped: `th:each="row : ${entry.value}"` where the map value type is `List<DriverProfileRow>`

**Guest marker + sub-label** (replace `td` in the Matchday column, line 34 analog):
```html
<tr th:each="row : ${profileRows}">
    <td>
        <span class="guest-marker" th:if="${row.guest}" aria-label="Guest driver">&#x2605;</span>
        <span th:text="${row.result.race.matchday.label}"></span>
        <span class="guest-label" th:if="${row.guest}">
            as guest for <span th:text="${row.fieldingTeamName}"></span>
        </span>
    </td>
    <td>...</td>  <!-- Opponent column — unchanged except use row.result.race.* -->
    <td class="text-dim" th:text="${row.result.race.track?.name ?: '-'}"></td>
    <td class="text-center" th:text="${row.result.position}"></td>
    ...
    <td class="text-right font-bold text-white" th:text="${row.result.pointsTotal}"></td>
</tr>
```

---

### `static/admin/css/admin.css` (config/style)

**Analog:** Self — `:root` block starts at line 12; `.badge` rules are at lines 377–412; existing `.guest-section` / `.guest-row` at lines 2084–2114.

**APPEND to `:root` block** (after line 30, before closing `}`):
```css
--guest: #f59e0b;
```

**APPEND after existing `.badge` rules** (after line 412, before `.hidden`):
```css
.guest-marker {
    color: var(--guest);
    font-size: 12px;
    margin-right: 4px;
    vertical-align: middle;
    line-height: 1;
}

.guest-label {
    font-size: 12px;
    font-weight: 400;
    color: var(--text-dim);
    margin-left: 4px;
}
```

**Rule:** Append only — never rewrite or replace existing sections (CLAUDE.md subagent rule for shared CSS files).

---

### `static/site/css/style.css` (config/style)

**Analog:** Self — `:root` block at line 15.

**APPEND to `:root` block** (after existing tokens):
```css
--guest: #f59e0b;
```

**APPEND at end of file** (after line 1089):
```css
.guest-marker {
    color: var(--guest);
    font-size: 12px;
    margin-right: 4px;
    vertical-align: middle;
    line-height: 1;
}

.guest-label {
    font-size: 12px;
    font-weight: 400;
    color: var(--text-dim);
    margin-left: 4px;
}
```

---

## Shared Patterns

### RaceLineup Guest Flag Resolution
**Source:** `RaceLineup.java` line 39: `private boolean guest;` — primitive, no lazy load.
**Access:** `raceLineupRepository.findByRaceIdAndDriverId(raceId, driverId).map(RaceLineup::isGuest).orElse(false)`
**Apply to:** `ResultsGraphicService.buildResultRows`, `ProvisionalScoresGraphicService.buildContext`, `DriverRankingService.calculateRankingForPhase`, `DriverRankingService.calculateAlltimeRanking`, `RaceService.getRaceDetailData`.

### Inline Guest Marker in Graphic Templates
**Source:** `results-render.html` existing inline `<style>` block pattern — all graphic templates use hardcoded hex values (e.g., `#f5c542`), never `var()`.
**Apply to:** `results-render.html`, `provisional-scores-render.html`, `lineup-render.html`.
```css
.guest-marker { color: #f59e0b; font-size: 0.85em; margin-right: 4px; vertical-align: middle; }
```

### Fragment-Based Guest Marker in Admin/Site Templates
**Source:** `site/fragments/match-card.html` (lines 1–19) — `<th:block th:fragment="name(param)">` structure.
**Apply to:** `race-detail.html`, `matchday-detail.html`, `driver-ranking.html`, `alltime-driver-ranking.html`, `driver-profile.html`.

However, for `matchday-detail.html` and the ranking templates, inline `<span>` is simpler than a `th:replace` call and avoids fragment resolution overhead. Use the fragment only where the glyph needs `aria-label` and consistent rendering across both admin and site paths. Either approach is acceptable per RESEARCH.md note on `admin/fragments/` being classpath-resolvable from site templates.

### Chip Pattern (admin templates)
**Source:** `matchday-detail.html` lines 155–159 — `<span class="chip">` wrapping driver info. The `.chip` CSS class already exists in `admin.css`.
**Apply to:** `matchday-detail.html` (self-analog).

### Record Expansion Pattern
**Source:** All three graphic service records (`DriverResultRow`, `ProvisionalRow`, `DriverPairing`) follow Java record syntax. Adding fields appends to the canonical constructor parameter list. One construction site per record — grep before editing.
```bash
grep -rn "new DriverResultRow\|new ProvisionalRow\|new DriverPairing\|new RaceDetailData" src/
```
**Apply to:** All four records before modifying.

### Mutable Inner Class Pattern
**Source:** `DriverRankingService.DriverRanking` (lines 207–274) — mutable `bestPosition`, `racesCount` set via `addResult()`. The `hasGuestAppearance` field follows the same post-construction mutation pattern.
**Apply to:** `DriverRankingService.DriverRanking`.

---

## No Analog Found

All files have clear codebase analogs. No entries in this section.

---

## Critical Pitfalls (from RESEARCH.md — planner must address)

| Pitfall | File(s) | Mitigation |
|---------|---------|------------|
| Record expansion breaks all construction sites | All 4 records | `grep -rn` before editing; update single construction call per record |
| `buildPairings` maps to `Driver` losing `RaceLineup` reference | `LineupGraphicService.java` | Keep `List<RaceLineup>` (not `List<Driver>`) for `homeEntries`/`awayEntries` |
| N+1 in `DriverProfilePageGenerator` | `DriverProfilePageGenerator.java` | Pre-build `guestLookup` map from `seasonLineups` (already fetched); no per-result query |
| `hasGuestAppearance` only set for pure guests, not dual-role | `DriverRankingService.java` | Per-result lineup lookup in the accumulation loop — NOT relying on whether driver has `SeasonDriver` |
| CSS variable `var(--guest)` not available in graphic templates | all `*-render.html` | Hardcode `#f59e0b` in inline `<style>`; only `admin.css`/`style.css` use `var(--guest)` |
| Thymeleaf fragment `th:replace` in string template mode | graphic `*-render.html` | Use inline `<span>` in graphic templates — never `th:replace` |

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/admin/service/`, `src/main/java/org/ctc/domain/service/`, `src/main/java/org/ctc/sitegen/`, `src/main/resources/templates/admin/`, `src/main/resources/templates/site/`, `src/main/resources/static/`
**Files scanned:** 25
**Pattern extraction date:** 2026-06-01
