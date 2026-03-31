# RaceLineup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `MatchdayLineup` to `RaceLineup` (bound to Race statt Matchday) und automatisch Lineup-Einträge beim CSV-Import erstellen.

**Architecture:** Entity `MatchdayLineup` wird durch `RaceLineup` ersetzt (FK race statt matchday). Alle Konsumenten (Controller, Templates, Tests) werden migriert. CsvImportService erstellt automatisch RaceLineup-Einträge für Sub-Teams.

**Tech Stack:** Java 25, Spring Boot 4.x, JPA/Hibernate, Thymeleaf, Flyway, JUnit 5/Mockito

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/org/ctc/domain/model/RaceLineup.java` | Entity: race + driver + team |
| Create | `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` | Query methods for race-scoped lineups |
| Create | `src/main/java/org/ctc/admin/controller/RaceLineupController.java` | Admin CRUD for lineup per race |
| Create | `src/main/resources/templates/admin/race-lineup.html` | Lineup edit form per race |
| Delete | `src/main/java/org/ctc/domain/model/MatchdayLineup.java` | Replaced by RaceLineup |
| Delete | `src/main/java/org/ctc/domain/repository/MatchdayLineupRepository.java` | Replaced by RaceLineupRepository |
| Delete | `src/main/java/org/ctc/admin/controller/MatchdayLineupController.java` | Replaced by RaceLineupController |
| Delete | `src/main/resources/templates/admin/matchday-lineup.html` | Replaced by race-lineup.html |
| Modify | `src/main/resources/db/migration/V1__initial_schema.sql` | Tabelle race_lineups statt matchday_lineups |
| Modify | `src/main/java/org/ctc/admin/controller/RaceController.java` | populateDrivers: RaceLineupRepository |
| Modify | `src/main/java/org/ctc/admin/controller/MatchdayController.java` | Lineups aggregiert aus Races |
| Modify | `src/main/resources/templates/admin/matchday-detail.html` | Aggregiertes Lineup readonly, Links anpassen |
| Modify | `src/main/resources/templates/admin/matchdays.html` | Lineup-Link entfernen (jetzt pro Race) |
| Modify | `src/main/resources/templates/admin/race-detail.html` | Lineup-Button + Lineup-Anzeige |
| Modify | `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` | Unbenutzten Mock entfernen |
| Modify | `src/main/java/org/ctc/dataimport/CsvImportService.java` | RaceLineup beim Import erstellen |
| Modify | `src/main/java/org/ctc/dataimport/CsvImportController.java` | Flash-Message mit Lineup-Zähler |

---

## Task 1: RaceLineup Entity & Repository

**Files:**
- Create: `src/main/java/org/ctc/domain/model/RaceLineup.java`
- Create: `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java`
- Modify: `src/main/resources/db/migration/V1__initial_schema.sql:200-209`

- [ ] **Step 1: Create RaceLineup entity**

```java
package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "race_lineups")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"race", "driver", "team"})
public class RaceLineup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    public RaceLineup(Race race, Driver driver, Team team) {
        this.race = race;
        this.driver = driver;
        this.team = team;
    }
}
```

- [ ] **Step 2: Create RaceLineupRepository**

```java
package org.ctc.domain.repository;

import org.ctc.domain.model.RaceLineup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceLineupRepository extends JpaRepository<RaceLineup, UUID> {

    List<RaceLineup> findByRaceId(UUID raceId);

    List<RaceLineup> findByRaceIdAndTeamId(UUID raceId, UUID teamId);

    Optional<RaceLineup> findByRaceIdAndDriverId(UUID raceId, UUID driverId);
}
```

- [ ] **Step 3: Update V1 schema**

In `V1__initial_schema.sql`, replace lines 200-209:

```sql
CREATE TABLE race_lineups (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    team_id UUID NOT NULL,
    CONSTRAINT fk_rl_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_rl_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT fk_rl_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_race_lineup_driver UNIQUE (race_id, driver_id)
);
```

- [ ] **Step 4: Delete old entity and repository**

Delete `src/main/java/org/ctc/domain/model/MatchdayLineup.java`
Delete `src/main/java/org/ctc/domain/repository/MatchdayLineupRepository.java`

- [ ] **Step 5: Compile check**

Run: `./mvnw compile -pl . -q 2>&1 | head -30`
Expected: Compilation errors in controllers that still reference MatchdayLineup (expected, fixed in next tasks)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/ctc/domain/model/RaceLineup.java \
       src/main/java/org/ctc/domain/repository/RaceLineupRepository.java \
       src/main/resources/db/migration/V1__initial_schema.sql
git add -u src/main/java/org/ctc/domain/model/MatchdayLineup.java \
           src/main/java/org/ctc/domain/repository/MatchdayLineupRepository.java
git commit -m "RaceLineup Entity und Repository erstellt, MatchdayLineup entfernt"
```

---

## Task 2: RaceController Migration

**Files:**
- Modify: `src/main/java/org/ctc/admin/controller/RaceController.java:40,150-152,373-404`

- [ ] **Step 1: Update dependency injection**

In `RaceController.java`, replace:
```java
private final MatchdayLineupRepository matchdayLineupRepository;
```
with:
```java
private final RaceLineupRepository raceLineupRepository;
```

Update import: replace `org.ctc.domain.repository.MatchdayLineupRepository` → remove (already uses wildcard `org.ctc.domain.repository.*`).

- [ ] **Step 2: Update populateDrivers method**

Replace the `populateDrivers` method (lines 373-404):

```java
private void populateDrivers(RaceForm form, UUID raceId, UUID seasonId, org.ctc.domain.model.Team team) {
    var lineupDrivers = raceLineupRepository.findByRaceIdAndTeamId(raceId, team.getId());

    if (!lineupDrivers.isEmpty()) {
        // Sub-team: use race lineup
        int pos = form.getResults().size() + 1;
        for (var lineup : lineupDrivers) {
            var rf = new RaceResultForm();
            rf.setDriverId(lineup.getDriver().getId());
            rf.setDriverPsnId(lineup.getDriver().getPsnId());
            rf.setTeamShortName(team.getShortName());
            rf.setPosition(pos);
            rf.setQualiPosition(pos);
            form.getResults().add(rf);
            pos++;
        }
    } else {
        // Standalone team: use season driver assignment
        var seasonDrivers = seasonDriverRepository.findBySeasonIdAndTeamId(seasonId, team.getId());
        int pos = form.getResults().size() + 1;
        for (var sd : seasonDrivers) {
            var rf = new RaceResultForm();
            rf.setDriverId(sd.getDriver().getId());
            rf.setDriverPsnId(sd.getDriver().getPsnId());
            rf.setTeamShortName(team.getShortName());
            rf.setPosition(pos);
            rf.setQualiPosition(pos);
            form.getResults().add(rf);
            pos++;
        }
    }
}
```

- [ ] **Step 3: Update results() call site**

In the `results()` method (lines 140-159), change from:
```java
var matchdayId = race.getMatchday().getId();
var seasonId = race.getMatchday().getSeason().getId();

// Try MatchdayLineup first (for sub-teams), fall back to SeasonDriver
populateDrivers(form, matchdayId, seasonId, race.getHomeTeam());
populateDrivers(form, matchdayId, seasonId, race.getAwayTeam());
```
to:
```java
var raceId = race.getId();
var seasonId = race.getMatchday().getSeason().getId();

// Try RaceLineup first (for sub-teams), fall back to SeasonDriver
populateDrivers(form, raceId, seasonId, race.getHomeTeam());
populateDrivers(form, raceId, seasonId, race.getAwayTeam());
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ctc/admin/controller/RaceController.java
git commit -m "RaceController: MatchdayLineupRepository durch RaceLineupRepository ersetzt"
```

---

## Task 3: RaceLineupController (Lineup pro Race verwalten)

**Files:**
- Create: `src/main/java/org/ctc/admin/controller/RaceLineupController.java`
- Create: `src/main/resources/templates/admin/race-lineup.html`
- Delete: `src/main/java/org/ctc/admin/controller/MatchdayLineupController.java`
- Delete: `src/main/resources/templates/admin/matchday-lineup.html`

- [ ] **Step 1: Create RaceLineupController**

```java
package org.ctc.admin.controller;

import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/admin/races")
@RequiredArgsConstructor
public class RaceLineupController {

    private final RaceRepository raceRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;

    @GetMapping("/{raceId}/lineup")
    public String lineup(@PathVariable UUID raceId, Model model) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var season = race.getMatchday().getSeason();
        var existingLineups = raceLineupRepository.findByRaceId(raceId);

        // Find parent teams that have sub-teams in this season
        var seasonTeams = season.getTeams();
        var parentTeamsWithSubs = seasonTeams.stream()
                .filter(Team::isSubTeam)
                .map(Team::getParentOrSelf)
                .distinct()
                .sorted(Comparator.comparing(Team::getShortName))
                .toList();

        var parentDriverMap = new LinkedHashMap<Team, List<SeasonDriver>>();
        var parentSubTeamMap = new LinkedHashMap<Team, List<Team>>();
        var driverLineupMap = new HashMap<UUID, RaceLineup>();

        for (var parent : parentTeamsWithSubs) {
            var drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), parent.getId());
            parentDriverMap.put(parent, drivers);

            var subTeams = seasonTeams.stream()
                    .filter(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(parent.getId()))
                    .sorted(Comparator.comparing(Team::getShortName))
                    .toList();
            parentSubTeamMap.put(parent, subTeams);
        }

        for (var lineup : existingLineups) {
            driverLineupMap.put(lineup.getDriver().getId(), lineup);
        }

        model.addAttribute("race", race);
        model.addAttribute("parentTeamsWithSubs", parentTeamsWithSubs);
        model.addAttribute("parentDriverMap", parentDriverMap);
        model.addAttribute("parentSubTeamMap", parentSubTeamMap);
        model.addAttribute("driverLineupMap", driverLineupMap);
        return "admin/race-lineup";
    }

    @Transactional
    @PostMapping("/{raceId}/lineup")
    public String saveLineup(@PathVariable UUID raceId,
                             @RequestParam Map<String, String> params,
                             RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(raceId).orElseThrow();

        // Delete existing lineups for this race
        var existing = raceLineupRepository.findByRaceId(raceId);
        raceLineupRepository.deleteAll(existing);

        // Process form params: driver_{driverId} = teamId
        int count = 0;
        for (var entry : params.entrySet()) {
            if (!entry.getKey().startsWith("driver_") || entry.getValue().isBlank()) continue;

            UUID driverId = UUID.fromString(entry.getKey().substring("driver_".length()));
            UUID teamId = UUID.fromString(entry.getValue());

            var driver = driverRepository.findById(driverId).orElseThrow();
            var team = teamRepository.findById(teamId).orElseThrow();

            raceLineupRepository.save(new RaceLineup(race, driver, team));
            count++;
        }

        log.info("Saved {} lineup entries for race {}", count, raceId);
        redirectAttributes.addFlashAttribute("successMessage", "Lineup saved: " + count + " drivers assigned");
        return "redirect:/admin/races/" + raceId + "/lineup";
    }
}
```

- [ ] **Step 2: Create race-lineup.html template**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Race Lineup', ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <h1 th:text="'Lineup — ' + ${race.homeTeam.shortName} + ' vs ' + ${race.awayTeam?.shortName ?: 'Bye'}"></h1>
        <a th:href="@{/admin/races/{id}(id=${race.id})}" class="btn btn-secondary">Back to Race</a>
    </div>

    <div th:if="${parentTeamsWithSubs.isEmpty()}" class="card">
        <div class="empty-state">
            <p>No teams with sub-teams in this season. Lineup assignment is only needed for teams with sub-teams.</p>
        </div>
    </div>

    <form th:if="${!parentTeamsWithSubs.isEmpty()}"
          th:action="@{/admin/races/{id}/lineup(id=${race.id})}" method="post">

        <div th:each="parent : ${parentTeamsWithSubs}" class="card" style="margin-bottom:16px;">
            <h2 th:text="${parent.shortName + ' — ' + parent.name}"></h2>
            <table>
                <thead>
                    <tr><th>PSN ID</th><th>Nickname</th><th>Sub-Team</th></tr>
                </thead>
                <tbody>
                    <tr th:each="sd : ${parentDriverMap.get(parent)}">
                        <td><strong th:text="${sd.driver.psnId}"></strong></td>
                        <td th:text="${sd.driver.nickname}"></td>
                        <td>
                            <select th:name="'driver_' + ${sd.driver.id}">
                                <option value="">-- Not assigned --</option>
                                <option th:each="sub : ${parentSubTeamMap.get(parent)}"
                                        th:value="${sub.id}"
                                        th:text="${sub.shortName}"
                                        th:selected="${driverLineupMap.containsKey(sd.driver.id) && driverLineupMap.get(sd.driver.id).team.id == sub.id}">
                                </option>
                            </select>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>

        <div class="actions">
            <button type="submit" class="btn btn-primary">Save Lineup</button>
        </div>
    </form>
</section>
</body>
</html>
```

- [ ] **Step 3: Delete old controller and template**

Delete `src/main/java/org/ctc/admin/controller/MatchdayLineupController.java`
Delete `src/main/resources/templates/admin/matchday-lineup.html`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ctc/admin/controller/RaceLineupController.java \
       src/main/resources/templates/admin/race-lineup.html
git add -u src/main/java/org/ctc/admin/controller/MatchdayLineupController.java \
           src/main/resources/templates/admin/matchday-lineup.html
git commit -m "RaceLineupController und Template erstellt, MatchdayLineup-Controller entfernt"
```

---

## Task 4: MatchdayController & Templates anpassen

**Files:**
- Modify: `src/main/java/org/ctc/admin/controller/MatchdayController.java:6,32,52-58`
- Modify: `src/main/resources/templates/admin/matchday-detail.html:13,98-106`
- Modify: `src/main/resources/templates/admin/matchdays.html:32`
- Modify: `src/main/resources/templates/admin/race-detail.html`

- [ ] **Step 1: Update MatchdayController**

Remove the `MatchdayLineupRepository` dependency and import. Replace the `detail()` method to aggregate lineups from races:

Replace imports and field:
```java
import org.ctc.domain.repository.MatchdayLineupRepository;
```
→ remove this import (replace with `RaceLineupRepository` if needed, but we'll derive from races instead).

Replace field:
```java
private final MatchdayLineupRepository matchdayLineupRepository;
```
→ replace with:
```java
private final RaceLineupRepository raceLineupRepository;
```

Replace `detail()` method:
```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    var matchday = matchdayRepository.findById(id).orElseThrow();
    // Aggregate lineups from all races of this matchday
    var lineups = matchday.getMatches().stream()
            .flatMap(m -> m.getRaces().stream())
            .flatMap(r -> raceLineupRepository.findByRaceId(r.getId()).stream())
            .toList();
    model.addAttribute("matchday", matchday);
    model.addAttribute("lineups", lineups);
    return "admin/matchday-detail";
}
```

- [ ] **Step 2: Update matchday-detail.html**

Replace the Lineup button link (line 13) — remove the matchday-level lineup link since lineup is now per race:
```html
<a th:href="@{/admin/matchdays/{id}/lineup(id=${matchday.id})}" class="btn btn-secondary">Lineup</a>
```
→ remove this line entirely (lineup is managed per race now, link is in race-detail).

Update the lineup display section (lines 98-106) to show which race each lineup entry belongs to:
```html
<div th:if="${!lineups.isEmpty()}" class="detail-section" style="margin-top:32px;">
    <h2 th:text="'Lineup (' + ${lineups.size()} + ' assignments)'"></h2>
    <div class="chip-list">
        <span th:each="lu : ${lineups}" class="chip">
            <span th:text="${lu.driver.psnId}"></span>
            <span th:if="${lu.team != null}" class="text-dim" th:text="' (' + ${lu.team.shortName} + ')'"></span>
        </span>
    </div>
</div>
```
(Keep as-is — the readonly aggregated view works the same way.)

- [ ] **Step 3: Update matchdays.html**

Remove the Lineup button from the matchdays list (line 32):
```html
<a th:href="@{/admin/matchdays/{id}/lineup(id=${md.id})}" class="btn btn-secondary btn-sm">Lineup</a>
```
→ remove this line entirely.

- [ ] **Step 4: Update race-detail.html — add Lineup button**

In `race-detail.html`, add a Lineup button in the actions area (after line 15):
```html
<a th:href="@{/admin/races/{id}/lineup(id=${race.id})}" class="btn btn-secondary">Lineup</a>
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ctc/admin/controller/MatchdayController.java \
       src/main/resources/templates/admin/matchday-detail.html \
       src/main/resources/templates/admin/matchdays.html \
       src/main/resources/templates/admin/race-detail.html
git commit -m "Matchday-Templates: Lineup-Links zu Race verschoben, aggregierte Anzeige"
```

---

## Task 5: StandingsServiceTest aufräumen

**Files:**
- Modify: `src/test/java/org/ctc/domain/service/StandingsServiceTest.java:5,41`

- [ ] **Step 1: Remove unused mock**

Remove the import:
```java
import org.ctc.domain.repository.MatchdayLineupRepository;
```

Remove the field:
```java
@Mock
private MatchdayLineupRepository matchdayLineupRepository;
```

- [ ] **Step 2: Run tests**

Run: `./mvnw test -pl . -Dtest=StandingsServiceTest -q`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/ctc/domain/service/StandingsServiceTest.java
git commit -m "StandingsServiceTest: Unbenutzten MatchdayLineupRepository-Mock entfernt"
```

---

## Task 6: Build-Verifikation Entity-Migration

- [ ] **Step 1: Full compile**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS (keine Referenzen auf MatchdayLineup mehr)

- [ ] **Step 2: Run all tests**

Run: `./mvnw verify`
Expected: BUILD SUCCESS, alle Tests grün

---

## Task 7: CSV-Import — Tests schreiben (TDD Red)

**Files:**
- Create: `src/test/java/org/ctc/dataimport/CsvImportServiceTest.java`

- [ ] **Step 1: Write test class with lineup tests**

```java
package org.ctc.dataimport;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock private DriverMatchingService driverMatchingService;
    @Mock private DriverRepository driverRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private MatchdayRepository matchdayRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private PlayoffMatchupRepository playoffMatchupRepository;
    @Mock private ScoringService scoringService;
    @Mock private RaceLineupRepository raceLineupRepository;

    @InjectMocks
    private CsvImportService csvImportService;

    private Season season;
    private Matchday matchday;
    private Team parentTeam;
    private Team subTeam1;
    private Team subTeam2;
    private Team standaloneTeam;
    private Driver driver1;
    private Driver driver2;
    private RaceScoring raceScoring;

    @BeforeEach
    void setUp() {
        season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Season 1");
        raceScoring = new RaceScoring();
        season.setRaceScoring(raceScoring);

        matchday = new Matchday(season, "Matchday 1", 1);
        matchday.setId(UUID.randomUUID());

        parentTeam = new Team("Alpha Racing", "AHR");
        parentTeam.setId(UUID.randomUUID());

        subTeam1 = new Team("Alpha Racing 1", "AHR_1", parentTeam);
        subTeam1.setId(UUID.randomUUID());

        subTeam2 = new Team("Alpha Racing 2", "AHR_2", parentTeam);
        subTeam2.setId(UUID.randomUUID());

        standaloneTeam = new Team("Bravo Racing", "BRV");
        standaloneTeam.setId(UUID.randomUUID());

        driver1 = new Driver("driver1_psn", "Driver One");
        driver1.setId(UUID.randomUUID());

        driver2 = new Driver("driver2_psn", "Driver Two");
        driver2.setId(UUID.randomUUID());
    }

    @Test
    void executeImport_withSubTeam_createsRaceLineup() {
        // Given: CSV with two sub-teams
        var metadata = new CsvImportService.ImportMetadata("Season 1", null, null, null, null, matchday.getId());
        var matchResult1 = new DriverMatchingService.MatchResult(
                DriverMatchingService.MatchType.EXACT, driver1, 1.0);
        var matchResult2 = new DriverMatchingService.MatchResult(
                DriverMatchingService.MatchType.EXACT, driver2, 1.0);

        var row1 = new CsvImportService.ImportRow("AHR_1", "driver1_psn", 1, 1, false, matchResult1);
        var row2 = new CsvImportService.ImportRow("AHR_2", "driver2_psn", 2, 2, false, matchResult2);

        var preview = new CsvImportService.ImportPreview(metadata);
        preview.addRow(row1);
        preview.addRow(row2);

        when(seasonRepository.findByName("Season 1")).thenReturn(Optional.of(season));
        when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findByShortName("AHR_1")).thenReturn(Optional.of(subTeam1));
        when(teamRepository.findByShortName("AHR_2")).thenReturn(Optional.of(subTeam2));
        when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            var m = inv.getArgument(0, Match.class);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            var r = inv.getArgument(0, Race.class);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(seasonDriverRepository.findBySeasonIdAndDriverId(any(), any())).thenReturn(Optional.empty());
        when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

        // When
        var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

        // Then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getLineupCount()).isEqualTo(2);

        var captor = ArgumentCaptor.forClass(RaceLineup.class);
        verify(raceLineupRepository, times(2)).save(captor.capture());

        var savedLineups = captor.getAllValues();
        assertThat(savedLineups).extracting(rl -> rl.getDriver().getId())
                .containsExactlyInAnyOrder(driver1.getId(), driver2.getId());
    }

    @Test
    void executeImport_withStandaloneTeam_doesNotCreateRaceLineup() {
        // Given: CSV with standalone teams (no parent)
        var metadata = new CsvImportService.ImportMetadata("Season 1", null, null, null, null, matchday.getId());
        var matchResult1 = new DriverMatchingService.MatchResult(
                DriverMatchingService.MatchType.EXACT, driver1, 1.0);
        var matchResult2 = new DriverMatchingService.MatchResult(
                DriverMatchingService.MatchType.EXACT, driver2, 1.0);

        var row1 = new CsvImportService.ImportRow("AHR", "driver1_psn", 1, 1, false, matchResult1);
        var row2 = new CsvImportService.ImportRow("BRV", "driver2_psn", 2, 2, false, matchResult2);

        var preview = new CsvImportService.ImportPreview(metadata);
        preview.addRow(row1);
        preview.addRow(row2);

        when(seasonRepository.findByName("Season 1")).thenReturn(Optional.of(season));
        when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(parentTeam));
        when(teamRepository.findByShortName("BRV")).thenReturn(Optional.of(standaloneTeam));
        when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            var m = inv.getArgument(0, Match.class);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            var r = inv.getArgument(0, Race.class);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(seasonDriverRepository.findBySeasonIdAndDriverId(any(), any())).thenReturn(Optional.empty());

        // When
        var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

        // Then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getLineupCount()).isEqualTo(0);
        verify(raceLineupRepository, never()).save(any());
    }

    @Test
    void executeImport_withExistingLineup_doesNotDuplicate() {
        // Given: CSV with sub-team, but lineup already exists
        var metadata = new CsvImportService.ImportMetadata("Season 1", null, null, null, null, matchday.getId());
        var matchResult1 = new DriverMatchingService.MatchResult(
                DriverMatchingService.MatchType.EXACT, driver1, 1.0);
        var matchResult2 = new DriverMatchingService.MatchResult(
                DriverMatchingService.MatchType.EXACT, driver2, 1.0);

        var row1 = new CsvImportService.ImportRow("AHR_1", "driver1_psn", 1, 1, false, matchResult1);
        var row2 = new CsvImportService.ImportRow("AHR_2", "driver2_psn", 2, 2, false, matchResult2);

        var preview = new CsvImportService.ImportPreview(metadata);
        preview.addRow(row1);
        preview.addRow(row2);

        when(seasonRepository.findByName("Season 1")).thenReturn(Optional.of(season));
        when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findByShortName("AHR_1")).thenReturn(Optional.of(subTeam1));
        when(teamRepository.findByShortName("AHR_2")).thenReturn(Optional.of(subTeam2));
        when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            var m = inv.getArgument(0, Match.class);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            var r = inv.getArgument(0, Race.class);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(seasonDriverRepository.findBySeasonIdAndDriverId(any(), any())).thenReturn(Optional.empty());

        // driver1 already has a lineup entry, driver2 does not
        when(raceLineupRepository.findByRaceIdAndDriverId(any(), eq(driver1.getId())))
                .thenReturn(Optional.of(new RaceLineup()));
        when(raceLineupRepository.findByRaceIdAndDriverId(any(), eq(driver2.getId())))
                .thenReturn(Optional.empty());

        // When
        var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

        // Then
        assertThat(result.getLineupCount()).isEqualTo(1); // only driver2
        verify(raceLineupRepository, times(1)).save(any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=CsvImportServiceTest -q`
Expected: FAIL — `CsvImportService` has no `RaceLineupRepository` field yet, `ImportResult` has no `getLineupCount()` method.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/ctc/dataimport/CsvImportServiceTest.java
git commit -m "CsvImportServiceTest: Tests fuer RaceLineup-Erstellung beim Import (Red)"
```

---

## Task 8: CSV-Import — Implementierung (TDD Green)

**Files:**
- Modify: `src/main/java/org/ctc/dataimport/CsvImportService.java:23-34,131-144,354-364`
- Modify: `src/main/java/org/ctc/dataimport/CsvImportController.java:137-146`

- [ ] **Step 1: Add RaceLineupRepository dependency to CsvImportService**

Add field after line 34 (`private final ScoringService scoringService;`):
```java
private final RaceLineupRepository raceLineupRepository;
```

Add import (already covered by wildcard `org.ctc.domain.repository.*`). Add model import:
```java
import org.ctc.domain.model.RaceLineup;
```
(Nope — already covered by `org.ctc.domain.model.*` wildcard.)

- [ ] **Step 2: Add lineupCount to ImportResult**

In the `ImportResult` inner class, add:
```java
private int lineupCount;
```

And add method:
```java
public void incrementLineupCount() { lineupCount++; }
```

Full updated `ImportResult`:
```java
@Getter
public static class ImportResult {
    private final List<String> importedRaces = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private int newDriversCreated;
    private int lineupCount;

    public void addImportedRace(String race) { importedRaces.add(race); }
    public void addError(String error) { errors.add(error); }
    public void incrementNewDrivers() { newDriversCreated++; }
    public void incrementLineupCount() { lineupCount++; }
    public boolean hasErrors() { return !errors.isEmpty(); }
}
```

- [ ] **Step 3: Add RaceLineup creation logic in executeImport**

After `race.getResults().add(raceResult);` (line 140), and before the closing `}` of the for-loop over rows, add:

```java
// Create RaceLineup for sub-teams
var resolvedTeam = findTeamFlexible(row.teamShortName());
if (resolvedTeam != null && resolvedTeam.isSubTeam()) {
    var existingLineup = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId());
    if (existingLineup.isEmpty()) {
        raceLineupRepository.save(new RaceLineup(race, driver, resolvedTeam));
        result.incrementLineupCount();
    }
}
```

Note: `resolvedTeam` is looked up via `findTeamFlexible(row.teamShortName())`. The team was already resolved earlier in the outer loop (`homeTeam`/`awayTeam`), but within the inner row loop we need the specific team for that row's driver. Since `findTeamFlexible` is called with the row's teamShortName, this correctly resolves the specific sub-team.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=CsvImportServiceTest -q`
Expected: All 3 tests PASS

- [ ] **Step 5: Update CsvImportController flash message**

In `CsvImportController.java`, update the success message (lines 143-145):
```java
redirectAttributes.addFlashAttribute("successMessage",
        "Import successful: " + result.getImportedRaces().size() + " races, " +
        result.getNewDriversCreated() + " new drivers");
```
→ replace with:
```java
var msg = "Import successful: " + result.getImportedRaces().size() + " races, " +
        result.getNewDriversCreated() + " new drivers";
if (result.getLineupCount() > 0) {
    msg += ", " + result.getLineupCount() + " lineup entries";
}
redirectAttributes.addFlashAttribute("successMessage", msg);
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/ctc/dataimport/CsvImportService.java \
       src/main/java/org/ctc/dataimport/CsvImportController.java
git commit -m "CSV-Import: Automatische RaceLineup-Erstellung fuer Sub-Teams"
```

---

## Task 9: Full Verification

- [ ] **Step 1: Run all tests**

Run: `./mvnw verify`
Expected: BUILD SUCCESS

- [ ] **Step 2: Smoke test with dev profile**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

Verify in browser:
1. Navigate to a matchday → check aggregated lineup display
2. Navigate to a race → check Lineup button exists
3. Click Lineup → verify form works for sub-teams
4. Check CSV import → verify lineup count in flash message

- [ ] **Step 3: Final commit if any cleanup needed**
