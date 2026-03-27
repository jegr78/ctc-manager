# Detail/Readonly Views Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add readonly detail pages for all admin entities with cross-navigation links between related entities.

**Architecture:** Each entity gets a new `GET /{id}` controller method returning a dedicated `*-detail.html` template. Templates use a shared layout pattern (toolbar + key-value fields + related data tables). List pages get links on entity names pointing to detail pages. CSS classes for detail-link styling and field grids.

**Tech Stack:** Spring Boot 4.x, Thymeleaf, CSS, JUnit 5 + MockMvc

**Spec:** `docs/superpowers/specs/2026-03-27-detail-views-design.md`

---

### Task 1: CSS-Klassen fuer Detail-Seiten

**Files:**
- Modify: `src/main/resources/static/admin/css/admin.css`

- [ ] **Step 1: Detail-CSS-Klassen hinzufuegen**

Am Ende von `admin.css` (nach Zeile 384) folgende Klassen ergaenzen:

```css
/* Detail views */
.detail-link {
    color: var(--text);
    text-decoration: none;
    border-bottom: 1px dashed rgba(255, 255, 255, 0.25);
    transition: color 0.15s, border-color 0.15s;
}
.detail-link:hover {
    color: var(--accent);
    border-bottom-color: var(--accent);
}

.detail-fields {
    display: grid;
    grid-template-columns: 160px 1fr;
    gap: 12px 24px;
    margin-bottom: 32px;
}
.detail-fields .label {
    color: var(--text-dim);
    font-size: 13px;
    text-transform: uppercase;
}

.detail-section h2 {
    font-size: 16px;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--border);
}

.back-link {
    color: var(--text-dim);
    text-decoration: none;
    font-size: 13px;
    transition: color 0.15s;
}
.back-link:hover {
    color: var(--accent);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/admin/css/admin.css
git commit -m "Detail-Views: CSS-Klassen fuer Detail-Seiten hinzugefuegt"
```

---

### Task 2: Season Detail-Seite

**Files:**
- Modify: `src/main/java/de/ctc/admin/controller/SeasonController.java`
- Create: `src/main/resources/templates/admin/season-detail.html`
- Modify: `src/main/resources/templates/admin/seasons.html`
- Modify: `src/test/java/de/ctc/admin/controller/SeasonControllerTest.java`

**Hinweis:** SeasonController benoetigt zusaetzlich `PlayoffRepository` als Dependency.

- [ ] **Step 1: Test schreiben**

In `SeasonControllerTest.java` neuen Test hinzufuegen:

```java
@Test
void shouldShowSeasonDetail() throws Exception {
    var season = seasonRepository.save(new Season("Detail Test"));

    mockMvc.perform(get("/admin/seasons/" + season.getId()))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/season-detail"))
            .andExpect(model().attributeExists("season"));
}
```

- [ ] **Step 2: Test ausfuehren — muss fehlschlagen**

```bash
./mvnw test -pl . -Dtest=SeasonControllerTest#shouldShowSeasonDetail -Dspring-boot.run.profiles=dev
```

Erwartet: FAIL — kein Mapping fuer `GET /admin/seasons/{id}`

- [ ] **Step 3: Controller-Methode hinzufuegen**

In `SeasonController.java` neue Methode hinzufuegen (vor der `create` Methode). Zusaetzlich `PlayoffRepository` als Dependency injizieren:

Neues Feld (neben dem bestehenden `seasonRepository`):

```java
private final PlayoffRepository playoffRepository;
```

Import:

```java
import de.ctc.domain.repository.PlayoffRepository;
```

Neue Methode:

```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    var season = seasonRepository.findById(id).orElseThrow();
    var playoff = playoffRepository.findBySeasonId(id).orElse(null);
    model.addAttribute("season", season);
    model.addAttribute("playoff", playoff);
    return "admin/season-detail";
}
```

- [ ] **Step 4: Detail-Template erstellen**

Datei `src/main/resources/templates/admin/season-detail.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Season: ' + ${season.name}, ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <div>
            <a th:href="@{/admin/seasons}" class="back-link">&larr; Back to Seasons</a>
            <h1 th:text="${season.name}"></h1>
        </div>
        <div class="actions">
            <a th:href="@{/admin/seasons/{id}/edit(id=${season.id})}" class="btn btn-primary">Edit</a>
            <form th:action="@{/admin/seasons/{id}/delete(id=${season.id})}" method="post" style="display:inline"
                  onsubmit="return confirm('Really delete this season?')">
                <button type="submit" class="btn btn-danger">Delete</button>
            </form>
        </div>
    </div>

    <div class="card">
        <div class="detail-fields">
            <span class="label">Name</span>
            <span th:text="${season.name}"></span>
            <span class="label">Start Date</span>
            <span th:text="${season.startDate ?: '-'}"></span>
            <span class="label">End Date</span>
            <span th:text="${season.endDate ?: '-'}"></span>
            <span class="label">Status</span>
            <span>
                <span th:if="${season.active}" class="badge badge-active">Active</span>
                <span th:unless="${season.active}" class="badge badge-inactive">Inactive</span>
            </span>
        </div>

        <div class="detail-section">
            <h2 th:text="'Teams (' + ${season.teams.size()} + ')'"></h2>
            <table th:if="${!season.teams.isEmpty()}">
                <thead>
                    <tr><th>Short Name</th><th>Name</th><th>Type</th></tr>
                </thead>
                <tbody>
                    <tr th:each="team : ${season.teams}">
                        <td><a th:href="@{/admin/teams/{id}(id=${team.id})}" class="detail-link" th:text="${team.shortName}"></a></td>
                        <td th:text="${team.name}"></td>
                        <td>
                            <span th:if="${team.isSubTeam()}" class="badge badge-active">Sub</span>
                            <span th:unless="${team.isSubTeam()}" class="badge badge-inactive">Parent</span>
                        </td>
                    </tr>
                </tbody>
            </table>
            <div th:if="${season.teams.isEmpty()}" class="empty-state" style="padding:16px;"><p>No teams assigned.</p></div>
        </div>

        <div class="detail-section" style="margin-top:32px;">
            <h2 th:text="'Matchdays (' + ${season.matchdays.size()} + ')'"></h2>
            <table th:if="${!season.matchdays.isEmpty()}">
                <thead>
                    <tr><th>#</th><th>Label</th><th>Date</th><th>Races</th></tr>
                </thead>
                <tbody>
                    <tr th:each="md : ${season.matchdays}">
                        <td th:text="${md.sortIndex}"></td>
                        <td><a th:href="@{/admin/matchdays/{id}(id=${md.id})}" class="detail-link" th:text="${md.label}"></a></td>
                        <td th:text="${md.date ?: '-'}"></td>
                        <td th:text="${md.races.size()}"></td>
                    </tr>
                </tbody>
            </table>
            <div th:if="${season.matchdays.isEmpty()}" class="empty-state" style="padding:16px;"><p>No matchdays yet.</p></div>
        </div>

        <div th:if="${playoff != null}" class="detail-section" style="margin-top:32px;">
            <h2>Playoff</h2>
            <div class="detail-fields">
                <span class="label">Name</span>
                <a th:href="@{/admin/playoffs(seasonId=${season.id})}" class="detail-link" th:text="${playoff.name}"></a>
                <span class="label">Rounds</span>
                <span th:text="${playoff.rounds.size()}"></span>
            </div>
        </div>
    </div>
</section>
</body>
</html>
```

- [ ] **Step 5: Test ausfuehren — muss gruen sein**

```bash
./mvnw test -pl . -Dtest=SeasonControllerTest#shouldShowSeasonDetail -Dspring-boot.run.profiles=dev
```

Erwartet: PASS

- [ ] **Step 6: Listen-Template anpassen**

In `seasons.html` den Season-Namen als Link zur Detail-Seite machen. Zeile aendern von:

```html
<td th:text="${season.name}"></td>
```

zu:

```html
<td><a th:href="@{/admin/seasons/{id}(id=${season.id})}" class="detail-link" th:text="${season.name}"></a></td>
```

- [ ] **Step 7: Alle Tests ausfuehren**

```bash
./mvnw verify
```

Erwartet: Alle Tests gruen.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/de/ctc/admin/controller/SeasonController.java src/main/resources/templates/admin/season-detail.html src/main/resources/templates/admin/seasons.html src/test/java/de/ctc/admin/controller/SeasonControllerTest.java
git commit -m "Season Detail-Seite mit Teams und Matchdays"
```

---

### Task 3: Team Detail-Seite

**Files:**
- Modify: `src/main/java/de/ctc/admin/controller/TeamController.java`
- Modify: `src/main/java/de/ctc/domain/repository/SeasonRepository.java`
- Create: `src/main/resources/templates/admin/team-detail.html`
- Modify: `src/main/resources/templates/admin/teams.html`
- Modify: `src/test/java/de/ctc/admin/controller/TeamControllerTest.java`

- [ ] **Step 1: Test schreiben**

In `TeamControllerTest.java` neuen Test hinzufuegen:

```java
@Test
void shouldShowTeamDetail() throws Exception {
    var team = teamRepository.save(new Team("Detail Racing", "DTR"));

    mockMvc.perform(get("/admin/teams/" + team.getId()))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/team-detail"))
            .andExpect(model().attributeExists("team"));
}
```

- [ ] **Step 2: Test ausfuehren — muss fehlschlagen**

```bash
./mvnw test -pl . -Dtest=TeamControllerTest#shouldShowTeamDetail -Dspring-boot.run.profiles=dev
```

Erwartet: FAIL

- [ ] **Step 3: Controller-Methode hinzufuegen**

In `TeamController.java` neue Methode (vor `create`). Zusaetzlich `SeasonRepository` als Dependency injizieren:

Neues Feld:

```java
private final SeasonRepository seasonRepository;
```

Import:

```java
import de.ctc.domain.repository.SeasonRepository;
```

Neue Repository-Methode in `SeasonRepository.java`:

```java
List<Season> findByTeamsId(UUID teamId);
```

Neue Controller-Methode:

```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    var team = teamRepository.findById(id).orElseThrow();
    var seasons = seasonRepository.findByTeamsId(id);
    model.addAttribute("team", team);
    model.addAttribute("seasons", seasons);
    return "admin/team-detail";
}
```

- [ ] **Step 4: Detail-Template erstellen**

Datei `src/main/resources/templates/admin/team-detail.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Team: ' + ${team.shortName}, ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <div>
            <a th:href="@{/admin/teams}" class="back-link">&larr; Back to Teams</a>
            <h1 th:text="${team.name}"></h1>
        </div>
        <div class="actions">
            <a th:href="@{/admin/teams/{id}/edit(id=${team.id})}" class="btn btn-primary">Edit</a>
            <form th:action="@{/admin/teams/{id}/delete(id=${team.id})}" method="post" style="display:inline"
                  onsubmit="return confirm('Really delete this team?')">
                <button type="submit" class="btn btn-danger">Delete</button>
            </form>
        </div>
    </div>

    <div class="card">
        <div class="detail-fields">
            <span class="label">Name</span>
            <span th:text="${team.name}"></span>
            <span class="label">Short Name</span>
            <span th:text="${team.shortName}"></span>
            <span class="label">Logo</span>
            <span th:text="${team.logoUrl ?: '-'}"></span>
            <span class="label">Parent Team</span>
            <span th:if="${team.parentTeam != null}">
                <a th:href="@{/admin/teams/{id}(id=${team.parentTeam.id})}" class="detail-link" th:text="${team.parentTeam.name}"></a>
            </span>
            <span th:if="${team.parentTeam == null}">-</span>
        </div>

        <div th:if="${team.hasSubTeams()}" class="detail-section">
            <h2 th:text="'Sub-Teams (' + ${team.subTeams.size()} + ')'"></h2>
            <table>
                <thead>
                    <tr><th>Short Name</th><th>Name</th></tr>
                </thead>
                <tbody>
                    <tr th:each="sub : ${team.subTeams}">
                        <td><a th:href="@{/admin/teams/{id}(id=${sub.id})}" class="detail-link" th:text="${sub.shortName}"></a></td>
                        <td th:text="${sub.name}"></td>
                    </tr>
                </tbody>
            </table>
        </div>

        <div class="detail-section" style="margin-top:32px;">
            <h2 th:text="'Seasons (' + ${seasons.size()} + ')'"></h2>
            <table th:if="${!seasons.isEmpty()}">
                <thead>
                    <tr><th>Name</th><th>Status</th></tr>
                </thead>
                <tbody>
                    <tr th:each="s : ${seasons}">
                        <td><a th:href="@{/admin/seasons/{id}(id=${s.id})}" class="detail-link" th:text="${s.name}"></a></td>
                        <td>
                            <span th:if="${s.active}" class="badge badge-active">Active</span>
                            <span th:unless="${s.active}" class="badge badge-inactive">Inactive</span>
                        </td>
                    </tr>
                </tbody>
            </table>
            <div th:if="${seasons.isEmpty()}" class="empty-state" style="padding:16px;"><p>Not assigned to any season.</p></div>
        </div>

        <div class="detail-section" style="margin-top:32px;">
            <h2 th:text="'Drivers (' + ${team.seasonDrivers.size()} + ')'"></h2>
            <table th:if="${!team.seasonDrivers.isEmpty()}">
                <thead>
                    <tr><th>PSN-ID</th><th>Nickname</th><th>Season</th></tr>
                </thead>
                <tbody>
                    <tr th:each="sd : ${team.seasonDrivers}">
                        <td><a th:href="@{/admin/drivers/{id}(id=${sd.driver.id})}" class="detail-link" th:text="${sd.driver.psnId}"></a></td>
                        <td th:text="${sd.driver.nickname}"></td>
                        <td><a th:href="@{/admin/seasons/{id}(id=${sd.season.id})}" class="detail-link" th:text="${sd.season.name}"></a></td>
                    </tr>
                </tbody>
            </table>
            <div th:if="${team.seasonDrivers.isEmpty()}" class="empty-state" style="padding:16px;"><p>No drivers assigned.</p></div>
        </div>
    </div>
</section>
</body>
</html>
```

- [ ] **Step 5: Test ausfuehren — muss gruen sein**

```bash
./mvnw test -pl . -Dtest=TeamControllerTest#shouldShowTeamDetail -Dspring-boot.run.profiles=dev
```

Erwartet: PASS

- [ ] **Step 6: Listen-Template anpassen**

In `teams.html` den Team-Namen (parent + sub) als Links machen:

Parent-Team-Zeile (Zeile 18) aendern von:

```html
<td><strong th:text="${team.shortName}"></strong></td>
```

zu:

```html
<td><a th:href="@{/admin/teams/{id}(id=${team.id})}" class="detail-link"><strong th:text="${team.shortName}"></strong></a></td>
```

Sub-Team-Zeile (Zeile 30) aendern von:

```html
<td style="padding-left:32px;" th:text="'↳ ' + ${sub.shortName}"></td>
```

zu:

```html
<td style="padding-left:32px;"><a th:href="@{/admin/teams/{id}(id=${sub.id})}" class="detail-link" th:text="'↳ ' + ${sub.shortName}"></a></td>
```

- [ ] **Step 7: Alle Tests ausfuehren**

```bash
./mvnw verify
```

Erwartet: Alle Tests gruen.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/de/ctc/admin/controller/TeamController.java src/main/java/de/ctc/domain/repository/SeasonRepository.java src/main/resources/templates/admin/team-detail.html src/main/resources/templates/admin/teams.html src/test/java/de/ctc/admin/controller/TeamControllerTest.java
git commit -m "Team Detail-Seite mit Sub-Teams, Seasons und Drivers"
```

---

### Task 4: Driver Detail-Seite

**Files:**
- Modify: `src/main/java/de/ctc/admin/controller/DriverController.java`
- Create: `src/main/resources/templates/admin/driver-detail.html`
- Modify: `src/main/resources/templates/admin/drivers.html`
- Create: `src/test/java/de/ctc/admin/controller/DriverControllerTest.java`

- [ ] **Step 1: Test schreiben**

Neue Testklasse `DriverControllerTest.java`:

```java
package de.ctc.admin.controller;

import de.ctc.domain.model.Driver;
import de.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DriverRepository driverRepository;

    @Test
    void shouldShowDriverDetail() throws Exception {
        var driver = driverRepository.save(new Driver("detail_test_psn", "Detail Tester"));

        mockMvc.perform(get("/admin/drivers/" + driver.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-detail"))
                .andExpect(model().attributeExists("driver"));
    }
}
```

- [ ] **Step 2: Test ausfuehren — muss fehlschlagen**

```bash
./mvnw test -pl . -Dtest=DriverControllerTest#shouldShowDriverDetail -Dspring-boot.run.profiles=dev
```

Erwartet: FAIL

- [ ] **Step 3: Controller-Methode hinzufuegen**

In `DriverController.java` neue Methode (vor `create`):

```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    var driver = driverRepository.findById(id).orElseThrow();
    model.addAttribute("driver", driver);
    return "admin/driver-detail";
}
```

- [ ] **Step 4: Detail-Template erstellen**

Datei `src/main/resources/templates/admin/driver-detail.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Driver: ' + ${driver.psnId}, ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <div>
            <a th:href="@{/admin/drivers}" class="back-link">&larr; Back to Drivers</a>
            <h1 th:text="${driver.nickname}"></h1>
        </div>
        <div class="actions">
            <a th:href="@{/admin/drivers/{id}/edit(id=${driver.id})}" class="btn btn-primary">Edit</a>
            <form th:action="@{/admin/drivers/{id}/delete(id=${driver.id})}" method="post" style="display:inline"
                  onsubmit="return confirm('Really delete this driver?')">
                <button type="submit" class="btn btn-danger">Delete</button>
            </form>
        </div>
    </div>

    <div class="card">
        <div class="detail-fields">
            <span class="label">PSN-ID</span>
            <span th:text="${driver.psnId}"></span>
            <span class="label">Nickname</span>
            <span th:text="${driver.nickname}"></span>
            <span class="label">Status</span>
            <span>
                <span th:if="${driver.active}" class="badge badge-active">Active</span>
                <span th:unless="${driver.active}" class="badge badge-inactive">Inactive</span>
            </span>
        </div>

        <div class="detail-section">
            <h2 th:text="'Season Assignments (' + ${driver.seasonDrivers.size()} + ')'"></h2>
            <table th:if="${!driver.seasonDrivers.isEmpty()}">
                <thead>
                    <tr><th>Season</th><th>Team</th></tr>
                </thead>
                <tbody>
                    <tr th:each="sd : ${driver.seasonDrivers}">
                        <td><a th:href="@{/admin/seasons/{id}(id=${sd.season.id})}" class="detail-link" th:text="${sd.season.name}"></a></td>
                        <td><a th:href="@{/admin/teams/{id}(id=${sd.team.id})}" class="detail-link" th:text="${sd.team.shortName}"></a></td>
                    </tr>
                </tbody>
            </table>
            <div th:if="${driver.seasonDrivers.isEmpty()}" class="empty-state" style="padding:16px;"><p>No season assignments.</p></div>
        </div>
    </div>
</section>
</body>
</html>
```

- [ ] **Step 5: Test ausfuehren — muss gruen sein**

```bash
./mvnw test -pl . -Dtest=DriverControllerTest#shouldShowDriverDetail -Dspring-boot.run.profiles=dev
```

Erwartet: PASS

- [ ] **Step 6: Listen-Template anpassen**

In `drivers.html` die PSN-ID als Link machen. Zeile aendern von:

```html
<td><strong th:text="${driver.psnId}"></strong></td>
```

zu:

```html
<td><a th:href="@{/admin/drivers/{id}(id=${driver.id})}" class="detail-link"><strong th:text="${driver.psnId}"></strong></a></td>
```

- [ ] **Step 7: Alle Tests ausfuehren**

```bash
./mvnw verify
```

Erwartet: Alle Tests gruen.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/de/ctc/admin/controller/DriverController.java src/main/resources/templates/admin/driver-detail.html src/main/resources/templates/admin/drivers.html src/test/java/de/ctc/admin/controller/DriverControllerTest.java
git commit -m "Driver Detail-Seite mit Season-Zuordnungen"
```

---

### Task 5: Matchday Detail-Seite

**Files:**
- Modify: `src/main/java/de/ctc/admin/controller/MatchdayController.java`
- Create: `src/main/resources/templates/admin/matchday-detail.html`
- Modify: `src/main/resources/templates/admin/matchdays.html`
- Create: `src/test/java/de/ctc/admin/controller/MatchdayControllerTest.java`

- [ ] **Step 1: Test schreiben**

Neue Testklasse `MatchdayControllerTest.java`:

```java
package de.ctc.admin.controller;

import de.ctc.domain.model.Matchday;
import de.ctc.domain.model.Season;
import de.ctc.domain.repository.MatchdayRepository;
import de.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MatchdayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Test
    void shouldShowMatchdayDetail() throws Exception {
        var season = seasonRepository.save(new Season("MD Detail Season"));
        var matchday = matchdayRepository.save(new Matchday(season, "Test Matchday", 1));

        mockMvc.perform(get("/admin/matchdays/" + matchday.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-detail"))
                .andExpect(model().attributeExists("matchday"));
    }
}
```

- [ ] **Step 2: Test ausfuehren — muss fehlschlagen**

```bash
./mvnw test -pl . -Dtest=MatchdayControllerTest#shouldShowMatchdayDetail -Dspring-boot.run.profiles=dev
```

Erwartet: FAIL

- [ ] **Step 3: Controller-Methode hinzufuegen**

In `MatchdayController.java` neue Methode (vor `create`). Zusaetzlich `MatchdayLineupRepository` als Dependency injizieren:

Neues Feld im Controller (neben den bestehenden Repository-Feldern):

```java
private final MatchdayLineupRepository matchdayLineupRepository;
```

Import hinzufuegen:

```java
import de.ctc.domain.repository.MatchdayLineupRepository;
```

Neue Methode:

```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    var matchday = matchdayRepository.findById(id).orElseThrow();
    var lineups = matchdayLineupRepository.findByMatchdayId(id);
    model.addAttribute("matchday", matchday);
    model.addAttribute("lineups", lineups);
    return "admin/matchday-detail";
}
```

- [ ] **Step 4: Detail-Template erstellen**

Datei `src/main/resources/templates/admin/matchday-detail.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Matchday: ' + ${matchday.label}, ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <div>
            <a th:href="@{/admin/matchdays(seasonId=${matchday.season.id})}" class="back-link">&larr; Back to Matchdays</a>
            <h1 th:text="${matchday.label}"></h1>
        </div>
        <div class="actions">
            <a th:href="@{/admin/matchdays/{id}/lineup(id=${matchday.id})}" class="btn btn-secondary">Lineup</a>
            <a th:href="@{/admin/matchdays/{id}/edit(id=${matchday.id})}" class="btn btn-primary">Edit</a>
            <form th:action="@{/admin/matchdays/{id}/delete(id=${matchday.id})}" method="post" style="display:inline"
                  onsubmit="return confirm('Really delete this matchday?')">
                <button type="submit" class="btn btn-danger">Delete</button>
            </form>
        </div>
    </div>

    <div class="card">
        <div class="detail-fields">
            <span class="label">Label</span>
            <span th:text="${matchday.label}"></span>
            <span class="label">Sort Index</span>
            <span th:text="${matchday.sortIndex}"></span>
            <span class="label">Date</span>
            <span th:text="${matchday.date ?: '-'}"></span>
            <span class="label">Season</span>
            <a th:href="@{/admin/seasons/{id}(id=${matchday.season.id})}" class="detail-link" th:text="${matchday.season.name}"></a>
        </div>

        <div class="detail-section">
            <h2 th:text="'Races (' + ${matchday.races.size()} + ')'"></h2>
            <table th:if="${!matchday.races.isEmpty()}">
                <thead>
                    <tr><th>Home</th><th></th><th>Away</th><th>Track</th><th>Results</th></tr>
                </thead>
                <tbody>
                    <tr th:each="race : ${matchday.races}">
                        <td><a th:href="@{/admin/teams/{id}(id=${race.homeTeam.id})}" class="detail-link" th:text="${race.homeTeam.shortName}"></a></td>
                        <td style="text-align:center;">vs</td>
                        <td><a th:href="@{/admin/teams/{id}(id=${race.awayTeam.id})}" class="detail-link" th:text="${race.awayTeam.shortName}"></a></td>
                        <td th:text="${race.track ?: '-'}"></td>
                        <td>
                            <a th:href="@{/admin/races/{id}(id=${race.id})}" class="detail-link">
                                <span th:if="${race.results.isEmpty()}" class="badge badge-inactive">Open</span>
                                <span th:unless="${race.results.isEmpty()}" class="badge badge-active" th:text="${race.results.size() + ' Results'}"></span>
                            </a>
                        </td>
                    </tr>
                </tbody>
            </table>
            <div th:if="${matchday.races.isEmpty()}" class="empty-state" style="padding:16px;"><p>No races yet.</p></div>
        </div>

        <div th:if="${!lineups.isEmpty()}" class="detail-section" style="margin-top:32px;">
            <h2 th:text="'Lineup (' + ${lineups.size()} + ' assignments)'"></h2>
            <table>
                <thead>
                    <tr><th>Driver</th><th>Sub-Team</th></tr>
                </thead>
                <tbody>
                    <tr th:each="lineup : ${lineups}">
                        <td><a th:href="@{/admin/drivers/{id}(id=${lineup.driver.id})}" class="detail-link" th:text="${lineup.driver.psnId}"></a></td>
                        <td><a th:href="@{/admin/teams/{id}(id=${lineup.team.id})}" class="detail-link" th:text="${lineup.team.shortName}"></a></td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</section>
</body>
</html>
```

- [ ] **Step 5: Test ausfuehren — muss gruen sein**

```bash
./mvnw test -pl . -Dtest=MatchdayControllerTest#shouldShowMatchdayDetail -Dspring-boot.run.profiles=dev
```

Erwartet: PASS

- [ ] **Step 6: Listen-Template anpassen**

In `matchdays.html` das Label als Link machen. Zeile aendern von:

```html
<td><strong th:text="${md.label}"></strong></td>
```

zu:

```html
<td><a th:href="@{/admin/matchdays/{id}(id=${md.id})}" class="detail-link"><strong th:text="${md.label}"></strong></a></td>
```

- [ ] **Step 7: Alle Tests ausfuehren**

```bash
./mvnw verify
```

Erwartet: Alle Tests gruen.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/de/ctc/admin/controller/MatchdayController.java src/main/resources/templates/admin/matchday-detail.html src/main/resources/templates/admin/matchdays.html src/test/java/de/ctc/admin/controller/MatchdayControllerTest.java
git commit -m "Matchday Detail-Seite mit Races und Lineup"
```

---

### Task 6: Race Detail-Seite

**Files:**
- Modify: `src/main/java/de/ctc/admin/controller/RaceController.java`
- Create: `src/main/resources/templates/admin/race-detail.html`
- Modify: `src/main/resources/templates/admin/races.html`
- Create: `src/test/java/de/ctc/admin/controller/RaceControllerTest.java`

- [ ] **Step 1: Test schreiben**

Neue Testklasse `RaceControllerTest.java`:

```java
package de.ctc.admin.controller;

import de.ctc.domain.model.Matchday;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.Season;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.MatchdayRepository;
import de.ctc.domain.repository.RaceRepository;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Test
    void shouldShowRaceDetail() throws Exception {
        var season = seasonRepository.save(new Season("Race Detail Season"));
        var matchday = matchdayRepository.save(new Matchday(season, "RD Matchday", 1));
        var home = teamRepository.save(new Team("Home Racing", "RDH"));
        var away = teamRepository.save(new Team("Away Racing", "RDA"));
        var race = raceRepository.save(new Race(matchday, home, away));

        mockMvc.perform(get("/admin/races/" + race.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-detail"))
                .andExpect(model().attributeExists("race"));
    }
}
```

- [ ] **Step 2: Test ausfuehren — muss fehlschlagen**

```bash
./mvnw test -pl . -Dtest=RaceControllerTest#shouldShowRaceDetail -Dspring-boot.run.profiles=dev
```

Erwartet: FAIL

- [ ] **Step 3: Controller-Methode hinzufuegen**

In `RaceController.java` neue Methode (vor `create`):

```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    var race = raceRepository.findById(id).orElseThrow();
    model.addAttribute("race", race);
    return "admin/race-detail";
}
```

- [ ] **Step 4: Detail-Template erstellen**

Datei `src/main/resources/templates/admin/race-detail.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Race: ' + ${race.homeTeam.shortName} + ' vs ' + ${race.awayTeam.shortName}, ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <div>
            <a th:href="@{/admin/races(matchdayId=${race.matchday.id})}" class="back-link">&larr; Back to Races</a>
            <h1>
                <span th:text="${race.homeTeam.shortName}"></span> vs
                <span th:text="${race.awayTeam.shortName}"></span>
            </h1>
        </div>
        <div class="actions">
            <a th:href="@{/admin/races/{id}/results(id=${race.id})}" class="btn btn-success">Results</a>
            <a th:href="@{/admin/races/{id}/edit(id=${race.id})}" class="btn btn-primary">Edit</a>
            <form th:action="@{/admin/races/{id}/delete(id=${race.id})}" method="post" style="display:inline"
                  onsubmit="return confirm('Really delete this race?')">
                <button type="submit" class="btn btn-danger">Delete</button>
            </form>
        </div>
    </div>

    <div class="card">
        <div class="detail-fields">
            <span class="label">Matchday</span>
            <a th:href="@{/admin/matchdays/{id}(id=${race.matchday.id})}" class="detail-link" th:text="${race.matchday.label}"></a>
            <span class="label">Home Team</span>
            <a th:href="@{/admin/teams/{id}(id=${race.homeTeam.id})}" class="detail-link" th:text="${race.homeTeam.name}"></a>
            <span class="label">Away Team</span>
            <a th:href="@{/admin/teams/{id}(id=${race.awayTeam.id})}" class="detail-link" th:text="${race.awayTeam.name}"></a>
            <span class="label">Track</span>
            <span th:text="${race.track ?: '-'}"></span>
            <span class="label">Car</span>
            <span th:text="${race.car ?: '-'}"></span>
            <span class="label">Playoff</span>
            <span th:if="${race.playoffMatchup != null}">
                <a th:href="@{/admin/playoffs/matchup/{id}(id=${race.playoffMatchup.id})}" class="detail-link" th:text="'Matchup #' + ${race.playoffMatchup.bracketPosition}"></a>
            </span>
            <span th:if="${race.playoffMatchup == null}">-</span>
        </div>

        <div class="detail-section">
            <h2 th:text="'Results (' + ${race.results.size()} + ')'"></h2>
            <div th:if="${!race.results.isEmpty()}">
                <!-- Score summary -->
                <div style="margin-bottom:24px;">
                    <th:block th:with="homeTotal=${race.results.stream().filter(r -> r.driver.seasonDrivers.stream().anyMatch(sd -> sd.team.id == race.homeTeam.id || sd.team.id == race.homeTeam.parentOrSelf.id)).mapToInt(r -> r.pointsTotal).sum()},
                                       awayTotal=${race.results.stream().filter(r -> r.driver.seasonDrivers.stream().anyMatch(sd -> sd.team.id == race.awayTeam.id || sd.team.id == race.awayTeam.parentOrSelf.id)).mapToInt(r -> r.pointsTotal).sum()}">
                    </th:block>
                </div>
                <table>
                    <thead>
                        <tr><th>Pos</th><th>Driver</th><th>Quali</th><th>FL</th><th>Points</th></tr>
                    </thead>
                    <tbody>
                        <tr th:each="result : ${race.results}">
                            <td th:text="${result.position}"></td>
                            <td><a th:href="@{/admin/drivers/{id}(id=${result.driver.id})}" class="detail-link" th:text="${result.driver.psnId}"></a></td>
                            <td th:text="${result.qualiPosition}"></td>
                            <td th:text="${result.fastestLap ? '✓' : ''}"></td>
                            <td th:text="${result.pointsTotal}"></td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <div th:if="${race.results.isEmpty()}" class="empty-state" style="padding:16px;"><p>No results yet.</p></div>
        </div>
    </div>
</section>
</body>
</html>
```

- [ ] **Step 5: Test ausfuehren — muss gruen sein**

```bash
./mvnw test -pl . -Dtest=RaceControllerTest#shouldShowRaceDetail -Dspring-boot.run.profiles=dev
```

Erwartet: PASS

- [ ] **Step 6: Listen-Template anpassen**

In `races.html` die Home/Away Team-Namen als Link zur Race-Detail-Seite machen. Die Zeilen aendern von:

```html
<td><strong th:text="${race.homeTeam.shortName}"></strong></td>
<td style="text-align:center">vs</td>
<td><strong th:text="${race.awayTeam.shortName}"></strong></td>
```

zu:

```html
<td><a th:href="@{/admin/races/{id}(id=${race.id})}" class="detail-link"><strong th:text="${race.homeTeam.shortName}"></strong></a></td>
<td style="text-align:center">vs</td>
<td><a th:href="@{/admin/races/{id}(id=${race.id})}" class="detail-link"><strong th:text="${race.awayTeam.shortName}"></strong></a></td>
```

- [ ] **Step 7: Alle Tests ausfuehren**

```bash
./mvnw verify
```

Erwartet: Alle Tests gruen.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/de/ctc/admin/controller/RaceController.java src/main/resources/templates/admin/race-detail.html src/main/resources/templates/admin/races.html src/test/java/de/ctc/admin/controller/RaceControllerTest.java
git commit -m "Race Detail-Seite mit Ergebnissen und Cross-Links"
```

---

### Task 7: Playoff-Seiten mit Cross-Links erweitern

**Files:**
- Modify: `src/main/resources/templates/admin/playoff-bracket.html`
- Modify: `src/main/resources/templates/admin/playoff-matchup.html`

- [ ] **Step 1: Bracket-Template anpassen**

In `playoff-bracket.html` die Team-Namen in Matchups als Links machen. Die `bracket-team-name` Spans aendern von:

```html
<span class="bracket-team-name" th:text="${matchup.team1ShortName != null ? matchup.team1ShortName : 'TBD'}"></span>
```

zu:

```html
<span class="bracket-team-name">
    <th:block th:if="${matchup.team1ShortName != null}" th:text="${matchup.team1ShortName}"></th:block>
    <th:block th:if="${matchup.team1ShortName == null}">TBD</th:block>
</span>
```

**Hinweis:** Da die Bracket-View DTOs nutzt (nicht Entities), stehen die Team-IDs dort nicht direkt zur Verfuegung. Diesen Schritt nur umsetzen wenn `MatchupView` die Team-IDs enthaelt. Falls nicht, muss `MatchupView` in `PlayoffService` um `team1Id` und `team2Id` ergaenzt werden.

Zuerst pruefen ob `MatchupView` Team-IDs hat. Falls nicht:

In `PlayoffService.java` die innere Klasse `MatchupView` um die Felder `team1Id` und `team2Id` (UUID) erweitern und in der `getBracketView()` Methode befuellen.

- [ ] **Step 2: Matchup-Template anpassen**

In `playoff-matchup.html` die Team-Namen im Titel als Links machen. Zeilen aendern von:

```html
<span th:text="${matchup.team1 != null ? matchup.team1.shortName : 'TBD'}"></span> vs
<span th:text="${matchup.team2 != null ? matchup.team2.shortName : 'TBD'}"></span>
```

zu:

```html
<a th:if="${matchup.team1 != null}" th:href="@{/admin/teams/{id}(id=${matchup.team1.id})}" class="detail-link" th:text="${matchup.team1.shortName}"></a>
<span th:if="${matchup.team1 == null}">TBD</span>
vs
<a th:if="${matchup.team2 != null}" th:href="@{/admin/teams/{id}(id=${matchup.team2.id})}" class="detail-link" th:text="${matchup.team2.shortName}"></a>
<span th:if="${matchup.team2 == null}">TBD</span>
```

Legs-Tabelle: "Results" Link als Detail-Link zu Race-Detail. Zeile aendern von:

```html
<a th:href="@{/admin/races/{id}/results(id=${leg.id})}" class="btn btn-secondary btn-sm">Results</a>
```

zu:

```html
<a th:href="@{/admin/races/{id}(id=${leg.id})}" class="detail-link">View</a>
<a th:href="@{/admin/races/{id}/results(id=${leg.id})}" class="btn btn-secondary btn-sm">Results</a>
```

- [ ] **Step 3: Alle Tests ausfuehren**

```bash
./mvnw verify
```

Erwartet: Alle Tests gruen.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/admin/playoff-bracket.html src/main/resources/templates/admin/playoff-matchup.html
git commit -m "Playoff-Seiten: Cross-Links zu Team- und Race-Details"
```

Falls `PlayoffService` geaendert wurde:

```bash
git add src/main/resources/templates/admin/playoff-bracket.html src/main/resources/templates/admin/playoff-matchup.html src/main/java/de/ctc/domain/service/PlayoffService.java
git commit -m "Playoff-Seiten: Cross-Links zu Team- und Race-Details"
```

---

### Task 8: Abschluss — Gesamttest und visueller Check

- [ ] **Step 1: Alle Tests ausfuehren**

```bash
./mvnw verify
```

Erwartet: Alle Tests gruen.

- [ ] **Step 2: App starten und visuell pruefen**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Folgende Punkte manuell pruefen:
1. Seasons-Liste: Klick auf Name oeffnet Detail-Seite
2. Season-Detail: Teams und Matchdays als Tabellen, Links funktionieren
3. Teams-Liste: Klick auf Short Name oeffnet Detail-Seite (Parent + Sub)
4. Team-Detail: Sub-Teams, Drivers, Parent-Link
5. Drivers-Liste: Klick auf PSN-ID oeffnet Detail-Seite
6. Driver-Detail: Season-Zuordnungen mit Links
7. Matchdays-Liste: Klick auf Label oeffnet Detail-Seite
8. Matchday-Detail: Races und Lineup
9. Races-Liste: Klick auf Home/Away oeffnet Race-Detail
10. Race-Detail: Ergebnisse, Cross-Links
11. Playoff-Bracket: Team-Namen verlinkt
12. Playoff-Matchup: Team-Namen und Race-Links

- [ ] **Step 3: App stoppen**
