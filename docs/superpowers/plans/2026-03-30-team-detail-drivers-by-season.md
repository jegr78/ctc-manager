# Team Detail: Fahrer nach Saison & Sub-Team gruppieren

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Team-Detailseite zeigt Fahrer gruppiert nach Saison und innerhalb jeder Saison nach Team/Sub-Team, statt als flache Chip-Liste.

**Architecture:** Der Controller bereitet SeasonDrivers als gruppierte Datenstruktur auf (Season -> Team -> Drivers). Das Template nutzt native `<details>`/`<summary>` HTML-Elemente fuer collapsible Season-Sections. TestDataService wird um SeasonDriver-Seeding erweitert, damit E2E-Tests die neue Ansicht verifizieren koennen.

**Tech Stack:** Java 25, Spring Boot 4.x, Thymeleaf, Playwright, JUnit 5/MockMvc

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `src/main/java/org/ctc/admin/dto/SeasonDriverGroupDto.java` | DTO: gruppierte Fahrer je Season |
| Modify | `src/main/java/org/ctc/admin/controller/TeamController.java` | Detail-Endpoint: Gruppierungslogik |
| Modify | `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` | Neue Query: findByTeamIdIn |
| Modify | `src/main/resources/templates/admin/team-detail.html` | Verschachtelte Season/Team/Driver-Sections |
| Modify | `src/main/resources/static/admin/css/admin.css` | Season-Accordion + Team-Group Styles |
| Modify | `src/main/java/org/ctc/admin/TestDataService.java` | SeasonDriver-Seeding fuer Testdaten |
| Modify | `src/test/java/org/ctc/admin/controller/TeamControllerTest.java` | Unit-Tests fuer Gruppierungslogik |
| Modify | `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` | E2E-Test: Team-Detail mit gruppierten Fahrern |

---

### Task 1: SeasonDriver-Seeding in TestDataService

Ohne SeasonDrivers in den Testdaten gibt es nichts zu gruppieren. Diese Task erweitert TestDataService, damit Fahrer ihren Teams in Seasons zugewiesen werden.

**Files:**
- Modify: `src/main/java/org/ctc/admin/TestDataService.java`
- Modify: `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java`

- [ ] **Step 1: SeasonDriverRepository um findByTeamIdIn erweitern**

```java
// In SeasonDriverRepository.java — neue Methode hinzufuegen:
List<SeasonDriver> findByTeamIdIn(List<UUID> teamIds);
```

- [ ] **Step 2: TestDataService um SeasonDriver-Seeding erweitern**

In `TestDataService.java` die Injections und `seed()` erweitern:

```java
// Neue Dependency injizieren:
private final SeasonDriverRepository seasonDriverRepository;

// In seed() nach seedDrivers() aufrufen:
seedSeasonDrivers();
```

Neue Methode `seedSeasonDrivers()` hinzufuegen:

```java
private void seedSeasonDrivers() {
    var allTeams = teamRepository.findAll();
    var allDrivers = driverRepository.findAll();

    // Helper fuer Team-Lookup
    java.util.function.Function<String, Team> findTeamByShort = (shortName) ->
            allTeams.stream()
                    .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                    .findFirst().orElseThrow();
    java.util.function.Function<String, Team> findSubByShort = (shortName) ->
            allTeams.stream()
                    .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                    .findFirst().orElseThrow();
    java.util.function.Function<String, Driver> findDriver = (psnId) ->
            allDrivers.stream()
                    .filter(d -> d.getPsnId().equals(psnId))
                    .findFirst().orElseThrow();

    var s4 = seasonRepository.findByName("Season 4 - 2026").orElseThrow();
    var s3a = seasonRepository.findByName("Season 3 - 2025 - Group A").orElseThrow();

    // P1R Fahrer in Season 4 — direkt dem Parent-Team zugewiesen
    for (String psnId : List.of("France-k88", "P1R_Jake", "P1R_SLAMMER", "P1R_OldBanger",
            "YT_Sorte13", "Unfazed__be", "P1R_Valkyrie", "motorstormhero")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findTeamByShort.apply("P1R")));
    }

    // CLR Fahrer in Season 4 — aufgeteilt auf Sub-Teams
    for (String psnId : List.of("BetelgeuzeFIN", "chiccoblasi", "CLR_Prodigy_97", "CLR_RichyI78", "CSX_Thomas", "DylanCliff_28")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findSubByShort.apply("CLR 1")));
    }
    for (String psnId : List.of("IEquinoXe-", "kurt_666_", "lemonysqueez", "RA_F1nalized__", "RA_Shred", "RA_Yannis73")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findSubByShort.apply("CLR 2")));
    }

    // TNR Fahrer in Season 4 — aufgeteilt auf Sub-Teams
    for (String psnId : List.of("Chaz__CA", "D-man371D-man", "Deekuhn", "Dirty_Donavan", "Fjneet90", "Ghostriderz16173", "GMZ_Alfred")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findSubByShort.apply("TNR A")));
    }
    for (String psnId : List.of("LEVITIUS", "Lightning_Lorry", "LotariRacing", "Mo_Flavor", "Nutcap_1", "panicpotato17", "Phantom_Steve111")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findSubByShort.apply("TNR B")));
    }
    for (String psnId : List.of("RayCarter", "Savvy-Unchained", "sir_maggs", "TNR_Capt_Slow", "TNR_SHAWN46", "TNR_Wipperman537")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findSubByShort.apply("TNR C")));
    }

    // AHR Fahrer in Season 4 — aufgeteilt auf Sub-Teams
    for (String psnId : List.of("AHR_Hills_93", "AHR_j_mac", "AHR-PezzzaGT", "AHR-Tankbro", "danfn22016", "grey_roc", "Jacko_GT7", "JackPlayz_01")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findSubByShort.apply("AHR 1")));
    }
    for (String psnId : List.of("Lemonz7836", "miggldeehiggins", "OFFICIAL_001", "PnR-Proton", "remir201", "Saittam-46", "stevedp81", "stigimoss", "Tracer-tel")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findSubByShort.apply("AHR 2")));
    }

    // Standalone-Teams in Season 4 (kein Sub-Team)
    for (String psnId : List.of("TCR_Rapid_GT", "TCR_Sheltie", "TCR_Sonic", "TCR_Tidgney", "Etlits", "Hogston_GT", "TCR_Bracing1", "TCR_White-tiger", "bmataz", "YtrRytonlad28")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findTeamByShort.apply("TCR")));
    }
    for (String psnId : List.of("DTR_Butzen-Katz", "DTR_H1PPYH33D", "DTR_Kierin", "DTR_M3guy", "DTR_MoominPappa", "DTR_Rosdwerg", "is250dec", "Jaristoteles", "mugelina", "Sionetica")) {
        seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findTeamByShort.apply("DTR")));
    }

    // P1R Fahrer auch in Season 3 Group A (via P1Rx Sub-Team) — fuer Multi-Season-Test
    for (String psnId : List.of("France-k88", "P1R_Jake", "P1R_SLAMMER", "P1R_OldBanger")) {
        seasonDriverRepository.save(new SeasonDriver(s3a, findDriver.apply(psnId), findSubByShort.apply("P1Rx")));
    }

    log.info("Created season driver assignments for Season 4 and Season 3 Group A");
}
```

- [ ] **Step 3: Verify — Anwendung starten und pruefen**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

Erwartung: Team-Detailseite (z.B. P1R) zeigt Fahrer mit Season-Name in Klammern (alte Darstellung funktioniert noch).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ctc/admin/TestDataService.java src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java
git commit -m "TestDataService: SeasonDriver-Seeding fuer Season 4 und Season 3"
```

---

### Task 2: DTO und Controller-Gruppierung

Die Fahrer-Daten werden im Controller nach Season und Team gruppiert. Ein neues DTO traegt die Struktur ins Template.

**Files:**
- Create: `src/main/java/org/ctc/admin/dto/SeasonDriverGroupDto.java`
- Modify: `src/main/java/org/ctc/admin/controller/TeamController.java`

- [ ] **Step 1: Failing Test schreiben — Controller liefert seasonDriverGroups**

In `src/test/java/org/ctc/admin/controller/TeamControllerTest.java`:

```java
@Autowired
private SeasonDriverRepository seasonDriverRepository;

@Autowired
private SeasonRepository seasonRepository;

@Autowired
private DriverRepository driverRepository;

@Test
void shouldShowTeamDetailWithGroupedDrivers() throws Exception {
    // Testdaten: Team mit Sub-Team, Season, Fahrer
    var parent = teamRepository.save(new Team("Grouped Racing", "GRP"));
    var sub = teamRepository.save(new Team("Grouped Racing A", "GRP A", parent));

    var season = seasonRepository.findByActiveTrue().orElseThrow();

    var driver1 = driverRepository.save(new Driver("grp_driver1", "GRP Driver 1"));
    var driver2 = driverRepository.save(new Driver("grp_driver2", "GRP Driver 2"));
    seasonDriverRepository.save(new SeasonDriver(season, driver1, parent));
    seasonDriverRepository.save(new SeasonDriver(season, driver2, sub));

    mockMvc.perform(get("/admin/teams/" + parent.getId()))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("seasonDriverGroups"))
            .andExpect(model().attribute("seasonDriverGroups",
                    org.hamcrest.Matchers.hasSize(1)));
}
```

- [ ] **Step 2: Test ausfuehren — FAIL erwartet**

Run: `./mvnw test -pl . -Dtest=TeamControllerTest#shouldShowTeamDetailWithGroupedDrivers`

Erwartung: FAIL — `seasonDriverGroups` nicht im Model.

- [ ] **Step 3: DTO erstellen**

Create `src/main/java/org/ctc/admin/dto/SeasonDriverGroupDto.java`:

```java
package org.ctc.admin.dto;

import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SeasonDriverGroupDto(
        Season season,
        Map<Team, List<SeasonDriver>> driversByTeam
) {
    public int totalDriverCount() {
        return driversByTeam.values().stream().mapToInt(List::size).sum();
    }
}
```

- [ ] **Step 4: Controller-Detail-Methode erweitern**

In `src/main/java/org/ctc/admin/controller/TeamController.java`:

Neue Dependency injizieren:

```java
private final SeasonDriverRepository seasonDriverRepository;
```

`detail()` Methode ersetzen:

```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    var team = teamRepository.findById(id).orElseThrow();
    var seasons = seasonRepository.findByTeamsId(id);

    // Alle relevanten Team-IDs (dieses Team + Sub-Teams)
    var teamIds = new java.util.ArrayList<UUID>();
    teamIds.add(id);
    team.getSubTeams().forEach(sub -> teamIds.add(sub.getId()));

    // Alle SeasonDrivers fuer diese Teams laden und gruppieren
    var allSeasonDrivers = seasonDriverRepository.findByTeamIdIn(teamIds);

    var seasonDriverGroups = allSeasonDrivers.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                    SeasonDriver::getSeason,
                    java.util.LinkedHashMap::new,
                    java.util.stream.Collectors.groupingBy(
                            SeasonDriver::getTeam,
                            java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.toList()
                    )
            ))
            .entrySet().stream()
            .sorted((a, b) -> {
                // Active season first, then by name descending
                if (a.getKey().isActive() != b.getKey().isActive()) {
                    return a.getKey().isActive() ? -1 : 1;
                }
                return b.getKey().getName().compareTo(a.getKey().getName());
            })
            .map(entry -> {
                // Sort drivers within each team by psnId
                var sortedByTeam = new java.util.LinkedHashMap<Team, java.util.List<SeasonDriver>>();
                entry.getValue().entrySet().stream()
                        .sorted(java.util.Comparator.comparing(e -> e.getKey().getShortName()))
                        .forEach(e -> {
                            var sortedDrivers = e.getValue().stream()
                                    .sorted(java.util.Comparator.comparing(sd -> sd.getDriver().getPsnId()))
                                    .toList();
                            sortedByTeam.put(e.getKey(), sortedDrivers);
                        });
                return new SeasonDriverGroupDto(entry.getKey(), sortedByTeam);
            })
            .toList();

    model.addAttribute("team", team);
    model.addAttribute("seasons", seasons);
    model.addAttribute("seasonDriverGroups", seasonDriverGroups);
    return "admin/team-detail";
}
```

Import hinzufuegen:

```java
import org.ctc.admin.dto.SeasonDriverGroupDto;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.repository.SeasonDriverRepository;
import java.util.ArrayList;
```

- [ ] **Step 5: Test ausfuehren — PASS erwartet**

Run: `./mvnw test -pl . -Dtest=TeamControllerTest#shouldShowTeamDetailWithGroupedDrivers`

Erwartung: PASS

- [ ] **Step 6: Weiteren Test — Team ohne Fahrer**

In `TeamControllerTest.java`:

```java
@Test
void shouldShowTeamDetailWithEmptyDriverGroups() throws Exception {
    var team = teamRepository.save(new Team("Empty Racing", "EMP"));

    mockMvc.perform(get("/admin/teams/" + team.getId()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("seasonDriverGroups",
                    org.hamcrest.Matchers.hasSize(0)));
}
```

- [ ] **Step 7: Test ausfuehren — PASS erwartet**

Run: `./mvnw test -pl . -Dtest=TeamControllerTest`

Erwartung: Alle Tests PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/ctc/admin/dto/SeasonDriverGroupDto.java src/main/java/org/ctc/admin/controller/TeamController.java src/test/java/org/ctc/admin/controller/TeamControllerTest.java
git commit -m "TeamController: Fahrer nach Season und Team gruppiert aufbereiten"
```

---

### Task 3: Template — Verschachtelte Season/Team/Driver-Sections

Das Template ersetzt die flache Seasons- und Drivers-Section durch eine gruppierte Darstellung.

**Files:**
- Modify: `src/main/resources/templates/admin/team-detail.html`

- [ ] **Step 1: Seasons- und Drivers-Sections ersetzen**

In `src/main/resources/templates/admin/team-detail.html` die Zeilen 43-63 (die zwei `detail-section` Divs fuer "Seasons" und "Drivers") ersetzen durch:

```html
        <div class="detail-section" style="margin-top:32px;">
            <h2>Seasons &amp; Drivers</h2>
            <div th:if="${seasonDriverGroups.isEmpty() && seasons.isEmpty()}" class="empty-state" style="padding:16px;">
                <p>Not assigned to any season.</p>
            </div>
            <div th:if="${!seasons.isEmpty() && seasonDriverGroups.isEmpty()}" class="empty-state" style="padding:16px;">
                <p>Assigned to
                    <span th:text="${seasons.size()}"></span> season(s) but no drivers assigned yet.
                </p>
                <div class="chip-list" style="margin-top:8px;">
                    <a th:each="s : ${seasons}" th:href="@{/admin/seasons/{id}(id=${s.id})}" class="chip">
                        <span th:text="${s.name}"></span>
                        <span th:if="${s.active}" class="badge badge-active" style="margin-left:6px;font-size:11px;">Active</span>
                    </a>
                </div>
            </div>
            <div th:each="group : ${seasonDriverGroups}">
                <details class="season-accordion" th:open="${group.season.active}">
                    <summary class="season-header">
                        <span class="season-header-title" th:text="${group.season.name}"></span>
                        <span th:if="${group.season.active}" class="badge badge-active" style="margin-left:6px;font-size:11px;">Active</span>
                        <span class="text-dim" th:text="'(' + ${group.totalDriverCount()} + ' Drivers)'"></span>
                    </summary>
                    <div class="season-drivers">
                        <div th:each="entry : ${group.driversByTeam}" class="team-driver-group">
                            <div class="team-group-label">
                                <a th:href="@{/admin/teams/{id}(id=${entry.key.id})}" th:text="${entry.key.shortName}"></a>
                                <span th:if="${entry.key.isSubTeam()}" class="badge badge-sub">Sub</span>
                            </div>
                            <div class="chip-list">
                                <a th:each="sd : ${entry.value}"
                                   th:href="@{/admin/drivers/{id}(id=${sd.driver.id})}"
                                   class="chip"
                                   th:text="${sd.driver.psnId}"></a>
                            </div>
                        </div>
                    </div>
                </details>
            </div>
            <!-- Seasons ohne Fahrer (nicht in seasonDriverGroups enthalten) -->
            <div th:if="${!seasonDriverGroups.isEmpty()}" th:with="groupedSeasonIds=${seasonDriverGroups.![season.id]}">
                <div th:each="s : ${seasons}" th:if="${!groupedSeasonIds.contains(s.id)}">
                    <details class="season-accordion">
                        <summary class="season-header">
                            <a th:href="@{/admin/seasons/{id}(id=${s.id})}" th:text="${s.name}" class="season-header-title"></a>
                            <span class="text-dim">(0 Drivers)</span>
                        </summary>
                    </details>
                </div>
            </div>
        </div>
```

- [ ] **Step 2: Manuell pruefen — Dev-Modus starten**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

Erwartung: Team-Detailseite (z.B. P1R, CLR, TNR) zeigt Fahrer gruppiert nach Season mit Team-Labels. Aktive Season ist expanded, aeltere collapsed.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/admin/team-detail.html
git commit -m "Team-Detail Template: Fahrer nach Season und Team gruppiert"
```

---

### Task 4: CSS — Season-Accordion und Team-Group Styles

Neue CSS-Klassen fuer die collapsible Season-Headers und Team-Gruppen.

**Files:**
- Modify: `src/main/resources/static/admin/css/admin.css`

- [ ] **Step 1: CSS-Styles nach dem Chip-Block einfuegen**

In `admin.css` nach Zeile 813 (nach `.chip .badge { ... }`) folgende Styles einfuegen:

```css
/* === Season Accordion (Team Detail) === */
.season-accordion {
    margin-bottom: 8px;
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    overflow: hidden;
}

.season-accordion summary {
    list-style: none;
}
.season-accordion summary::-webkit-details-marker {
    display: none;
}

.season-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    background: var(--bg-input);
    cursor: pointer;
    font-size: 14px;
    color: var(--text);
    transition: background 0.15s;
}
.season-header:hover {
    background: var(--bg-hover);
}
.season-header::before {
    content: '';
    display: inline-block;
    width: 0;
    height: 0;
    border-left: 5px solid var(--text-dim);
    border-top: 4px solid transparent;
    border-bottom: 4px solid transparent;
    transition: transform 0.15s;
    flex-shrink: 0;
}
details[open] > .season-header::before {
    transform: rotate(90deg);
}

.season-header-title {
    font-weight: 600;
    color: var(--white);
    text-decoration: none;
}
a.season-header-title:hover {
    color: var(--accent);
}

.season-drivers {
    padding: 12px 16px 16px;
}

.team-driver-group {
    margin-bottom: 12px;
}
.team-driver-group:last-child {
    margin-bottom: 0;
}

.team-group-label {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    text-transform: uppercase;
    color: var(--text-dim);
    margin-bottom: 6px;
    padding-left: 4px;
}
.team-group-label a {
    color: var(--text-dim);
    text-decoration: none;
    transition: color 0.15s;
}
.team-group-label a:hover {
    color: var(--accent);
}

.badge-sub {
    display: inline-flex;
    align-items: center;
    padding: 1px 6px;
    font-size: 10px;
    border-radius: var(--radius-lg);
    background: #1a2a3a;
    color: var(--accent);
}
```

- [ ] **Step 2: Manuell pruefen**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

Pruefen:
- Chevron-Icon dreht sich beim Oeffnen/Schliessen
- Season-Headers haben korrekten Hover-Effekt
- Team-Labels in Uppercase mit dimmed Farbe
- Sub-Team-Badge blau/cyan
- Responsive: Mobile < 768px korrekt

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/admin/css/admin.css
git commit -m "CSS: Season-Accordion und Team-Group Styles fuer Team-Detail"
```

---

### Task 5: Unit-Tests erweitern

Weitere Tests fuer Edge Cases der Gruppierungslogik.

**Files:**
- Modify: `src/test/java/org/ctc/admin/controller/TeamControllerTest.java`

- [ ] **Step 1: Test — Sub-Team-Fahrer erscheinen beim Parent-Team**

```java
@Test
void shouldIncludeSubTeamDriversInParentDetail() throws Exception {
    var parent = teamRepository.save(new Team("Parent Inc", "PIN"));
    var sub = teamRepository.save(new Team("Parent Inc A", "PIN A", parent));

    var season = seasonRepository.findByActiveTrue().orElseThrow();
    var d1 = driverRepository.save(new Driver("pin_parent1", "Parent Driver"));
    var d2 = driverRepository.save(new Driver("pin_sub1", "Sub Driver"));
    seasonDriverRepository.save(new SeasonDriver(season, d1, parent));
    seasonDriverRepository.save(new SeasonDriver(season, d2, sub));

    mockMvc.perform(get("/admin/teams/" + parent.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("pin_parent1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("pin_sub1")));
}
```

- [ ] **Step 2: Test — Sub-Team-Detail zeigt nur eigene Fahrer**

```java
@Test
void shouldShowOnlyOwnDriversForSubTeam() throws Exception {
    var parent = teamRepository.save(new Team("Own Inc", "OWN"));
    var sub = teamRepository.save(new Team("Own Inc A", "OWN A", parent));

    var season = seasonRepository.findByActiveTrue().orElseThrow();
    var d1 = driverRepository.save(new Driver("own_parent1", "Parent Only"));
    var d2 = driverRepository.save(new Driver("own_sub1", "Sub Only"));
    seasonDriverRepository.save(new SeasonDriver(season, d1, parent));
    seasonDriverRepository.save(new SeasonDriver(season, d2, sub));

    mockMvc.perform(get("/admin/teams/" + sub.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("own_sub1")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("own_parent1"))));
}
```

- [ ] **Step 3: Tests ausfuehren**

Run: `./mvnw test -pl . -Dtest=TeamControllerTest`

Erwartung: Alle Tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/ctc/admin/controller/TeamControllerTest.java
git commit -m "TeamControllerTest: Edge-Case Tests fuer gruppierte Fahrer-Anzeige"
```

---

### Task 6: Playwright E2E-Test

E2E-Test der Team-Detailseite mit der neuen gruppierten Fahrer-Ansicht.

**Files:**
- Modify: `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java`

- [ ] **Step 1: E2E-Test schreiben — Team-Detail zeigt Season-Gruppen**

In `AdminWorkflowE2ETest.java` neuen Test hinzufuegen:

```java
@Test
void shouldShowTeamDetailWithDriversGroupedBySeason() {
    // Navigate to Teams list
    page.navigate(url("/admin/teams"));

    // Click on P1R parent team (seeded by TestDataService, has drivers in S4 and S3)
    page.locator("a:has-text('P1R')").first().click();

    // Verify page title and heading
    assertThat(page.locator("h1")).containsText("Project One Racing");

    // Verify "Seasons & Drivers" section exists
    assertThat(page.locator("h2:has-text('Seasons & Drivers')")).isVisible();

    // Verify active season is expanded (details[open])
    var activeSeason = page.locator("details.season-accordion[open]");
    assertThat(activeSeason).isVisible();
    assertThat(activeSeason.locator(".season-header")).containsText("Season 4 - 2026");
    assertThat(activeSeason.locator(".badge-active")).isVisible();

    // Verify drivers are visible in expanded season
    assertThat(activeSeason.locator(".chip")).first().isVisible();
    assertThat(activeSeason.locator(".season-drivers")).containsText("France-k88");

    // Verify driver count is shown
    assertThat(activeSeason.locator(".season-header")).containsText("Drivers");
}

@Test
void shouldShowTeamDetailWithSubTeamDriverGroups() {
    // Navigate to CLR (has sub-teams CLR 1 and CLR 2 with drivers)
    page.navigate(url("/admin/teams"));
    page.locator("a:has-text('CLR')").first().click();

    assertThat(page.locator("h1")).containsText("Community League Racing");

    // Verify sub-team labels are shown
    var activeSeason = page.locator("details.season-accordion[open]");
    assertThat(activeSeason.locator(".team-group-label")).first().isVisible();

    // Verify Sub badge is present
    assertThat(activeSeason.locator(".badge-sub")).first().isVisible();
}

@Test
void shouldCollapseAndExpandSeasonAccordion() {
    page.navigate(url("/admin/teams"));
    page.locator("a:has-text('P1R')").first().click();

    // Season 3 should be collapsed by default (not active)
    var collapsedSeason = page.locator("details.season-accordion:not([open]):has-text('Season 3')");
    assertThat(collapsedSeason).isVisible();

    // Click to expand
    collapsedSeason.locator(".season-header").click();

    // Now drivers should be visible
    assertThat(collapsedSeason.locator(".season-drivers")).isVisible();
    assertThat(collapsedSeason.locator(".chip")).first().isVisible();
}
```

- [ ] **Step 2: E2E-Tests ausfuehren**

Run: `./mvnw verify -Pe2e`

Erwartung: Alle E2E-Tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java
git commit -m "E2E: Team-Detail mit gruppierten Fahrern nach Season und Sub-Team"
```

---

### Task 7: Alle Tests ausfuehren und finalisieren

**Files:** Keine neuen Aenderungen.

- [ ] **Step 1: Vollstaendige Test-Suite ausfuehren**

Run: `./mvnw verify`

Erwartung: Alle Unit- und Integrationstests PASS.

- [ ] **Step 2: E2E-Tests ausfuehren**

Run: `./mvnw verify -Pe2e`

Erwartung: Alle E2E-Tests PASS.

- [ ] **Step 3: Manueller Smoke-Test**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

Pruefliste:
- P1R: 8 Fahrer direkt beim Parent in Season 4, 4 Fahrer bei P1Rx in Season 3
- CLR: Fahrer aufgeteilt auf CLR 1 und CLR 2 in Season 4
- TNR: Fahrer aufgeteilt auf TNR A, TNR B, TNR C in Season 4
- Team ohne Fahrer (z.B. VEZ): Zeigt "Assigned to X season(s) but no drivers assigned yet."
- Accordion: Aktive Season expanded, aeltere collapsed
- Chevron dreht sich korrekt
- Mobile responsive (< 768px)
- Keyboard: Tab/Enter oeffnet/schliesst Accordion
