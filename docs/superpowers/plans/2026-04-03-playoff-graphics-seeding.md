# Playoff Graphics & Seeding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable race graphics (Lineup, Results, Settings, Overlay) for playoff races by adding a PlayoffSeed entity and making graphic services context-aware for both regular season and playoff races.

**Architecture:** New `PlayoffSeed` entity stores team seed numbers per playoff. The seeding UI gets seed number inputs and an auto-seed button. All four graphic services replace the `match == null` check with a `homeTeam/awayTeam` check, resolve `seasonName` from the Playoff entity for playoff races, and use seed numbers instead of standings positions.

**Tech Stack:** Java 25, Spring Boot 4.x, JPA, Thymeleaf, JUnit 5, Mockito

---

## File Structure

### New Files
- `src/main/java/org/ctc/domain/model/PlayoffSeed.java` — Entity (playoff_id, team_id, seed)
- `src/main/java/org/ctc/domain/repository/PlayoffSeedRepository.java` — Repository
- `src/test/java/org/ctc/domain/model/PlayoffSeedTest.java` — Entity unit tests
- `src/test/java/org/ctc/domain/service/PlayoffSeedServiceTest.java` — Seed logic integration tests (within PlayoffServiceTest)

### Modified Files
- `src/main/resources/db/migration/V1__initial_schema.sql` — Add `playoff_seeds` table
- `src/main/java/org/ctc/domain/model/Playoff.java` — Add `seeds` OneToMany relationship
- `src/main/java/org/ctc/admin/dto/SeedForm.java` — Add seed number field to SeedEntry
- `src/main/java/org/ctc/domain/service/PlayoffService.java` — Save seeds, auto-seed logic, SeedingData update
- `src/main/resources/templates/admin/playoff-seed.html` — Seed number inputs, auto-seed button
- `src/main/java/org/ctc/admin/service/LineupGraphicService.java` — Playoff-aware labels + seed
- `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` — Playoff-aware labels
- `src/main/java/org/ctc/admin/service/SettingsGraphicService.java` — Playoff-aware labels + seed
- `src/main/java/org/ctc/admin/service/OverlayGraphicService.java` — Playoff-aware labels + seed
- `src/test/java/org/ctc/admin/service/OverlayGraphicServiceTest.java` — Update "no match" test
- `src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java` — Add no-teams test (was: no match)
- `src/test/java/org/ctc/admin/service/ResultsGraphicServiceTest.java` — Add no-teams test
- `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` — Seed save/load/auto-seed tests

---

### Task 1: PlayoffSeed Entity & Schema

**Files:**
- Create: `src/main/java/org/ctc/domain/model/PlayoffSeed.java`
- Create: `src/main/java/org/ctc/domain/repository/PlayoffSeedRepository.java`
- Modify: `src/main/resources/db/migration/V1__initial_schema.sql`
- Modify: `src/main/java/org/ctc/domain/model/Playoff.java`

- [ ] **Step 1: Add `playoff_seeds` table to V1 schema**

Insert after the `playoff_seasons` table in `V1__initial_schema.sql`:

```sql
CREATE TABLE playoff_seeds (
    id UUID PRIMARY KEY,
    playoff_id UUID NOT NULL,
    team_id UUID NOT NULL,
    seed INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_playoff_seed_playoff FOREIGN KEY (playoff_id) REFERENCES playoffs(id),
    CONSTRAINT fk_playoff_seed_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_playoff_seed_team UNIQUE (playoff_id, team_id),
    CONSTRAINT uk_playoff_seed_number UNIQUE (playoff_id, seed)
);
```

- [ ] **Step 2: Create `PlayoffSeed` entity**

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
@Table(name = "playoff_seeds")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"playoff", "team"})
public class PlayoffSeed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playoff_id", nullable = false)
    private Playoff playoff;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private int seed;

    public PlayoffSeed(Playoff playoff, Team team, int seed) {
        this.playoff = playoff;
        this.team = team;
        this.seed = seed;
    }
}
```

- [ ] **Step 3: Create `PlayoffSeedRepository`**

```java
package org.ctc.domain.repository;

import org.ctc.domain.model.PlayoffSeed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayoffSeedRepository extends JpaRepository<PlayoffSeed, UUID> {
    List<PlayoffSeed> findByPlayoffId(UUID playoffId);
    Optional<PlayoffSeed> findByPlayoffIdAndTeamId(UUID playoffId, UUID teamId);
    void deleteByPlayoffId(UUID playoffId);
}
```

- [ ] **Step 4: Add `seeds` relationship to `Playoff` entity**

Add to `Playoff.java` after the `rounds` field, and add `"seeds"` to `@ToString(exclude = {...})`:

```java
@OneToMany(mappedBy = "playoff", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("seed ASC")
private List<PlayoffSeed> seeds = new ArrayList<>();
```

- [ ] **Step 5: Run tests to verify schema compiles**

Run: `./mvnw test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/ctc/domain/model/PlayoffSeed.java \
       src/main/java/org/ctc/domain/repository/PlayoffSeedRepository.java \
       src/main/resources/db/migration/V1__initial_schema.sql \
       src/main/java/org/ctc/domain/model/Playoff.java
git commit -m "PlayoffSeed Entity, Repository und Schema"
```

---

### Task 2: Seed-Logik im PlayoffService

**Files:**
- Modify: `src/main/java/org/ctc/domain/service/PlayoffService.java`
- Modify: `src/main/java/org/ctc/admin/dto/SeedForm.java`
- Test: `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java`

- [ ] **Step 1: Write failing tests for seed persistence**

Add to `PlayoffServiceTest.java` in a new `@Nested` class:

```java
@Nested
class SeedManagement {

    @Test
    void givenPlayoffWithTeams_whenSaveSeedNumbers_thenSeedsArePersisted() {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 4);
        entityManager.flush();
        entityManager.clear();

        // when
        playoffService.saveSeedNumbers(playoff.getId(), Map.of(
                teams.get(0).getId(), 1,
                teams.get(1).getId(), 2,
                teams.get(2).getId(), 3,
                teams.get(3).getId(), 4
        ));
        entityManager.flush();
        entityManager.clear();

        // then
        var seeds = playoffSeedRepository.findByPlayoffId(playoff.getId());
        assertEquals(4, seeds.size());

        var seed1 = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), teams.get(0).getId());
        assertTrue(seed1.isPresent());
        assertEquals(1, seed1.get().getSeed());
    }

    @Test
    void givenExistingSeeds_whenSaveSeedNumbers_thenOldSeedsReplaced() {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 4);
        playoffService.saveSeedNumbers(playoff.getId(), Map.of(
                teams.get(0).getId(), 1,
                teams.get(1).getId(), 2,
                teams.get(2).getId(), 3,
                teams.get(3).getId(), 4
        ));
        entityManager.flush();
        entityManager.clear();

        // when — swap seed 1 and 2
        playoffService.saveSeedNumbers(playoff.getId(), Map.of(
                teams.get(1).getId(), 1,
                teams.get(0).getId(), 2,
                teams.get(2).getId(), 3,
                teams.get(3).getId(), 4
        ));
        entityManager.flush();
        entityManager.clear();

        // then
        var seed1 = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), teams.get(1).getId());
        assertTrue(seed1.isPresent());
        assertEquals(1, seed1.get().getSeed());
    }

    @Test
    void givenSeedNumbers_whenAutoSeedBracket_thenMatchupsPopulatedCorrectly() {
        // given — 4 teams, seeds 1-4
        var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 4);
        playoffService.saveSeedNumbers(playoff.getId(), Map.of(
                teams.get(0).getId(), 1,
                teams.get(1).getId(), 2,
                teams.get(2).getId(), 3,
                teams.get(3).getId(), 4
        ));
        entityManager.flush();
        entityManager.clear();

        // when
        playoffService.autoSeedBracket(playoff.getId());
        entityManager.flush();
        entityManager.clear();

        // then — Seed 1 vs Seed 4, Seed 2 vs Seed 3
        var data = playoffService.getSeedingData(playoff.getId());
        var matchups = data.firstRound().getMatchups();
        assertEquals(2, matchups.size());

        // Matchup 1: Seed 1 (teams[0]) vs Seed 4 (teams[3])
        assertEquals(teams.get(0).getId(), matchups.get(0).getTeam1().getId());
        assertEquals(teams.get(3).getId(), matchups.get(0).getTeam2().getId());

        // Matchup 2: Seed 2 (teams[1]) vs Seed 3 (teams[2])
        assertEquals(teams.get(1).getId(), matchups.get(1).getTeam1().getId());
        assertEquals(teams.get(2).getId(), matchups.get(1).getTeam2().getId());
    }
}
```

Add `@Autowired private PlayoffSeedRepository playoffSeedRepository;` to the test class fields.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest="PlayoffServiceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — methods `saveSeedNumbers` and `autoSeedBracket` do not exist

- [ ] **Step 3: Add seed number to SeedForm**

In `SeedForm.java`, add a `seedNumber` field to `SeedEntry`:

```java
@Getter @Setter @NoArgsConstructor
public static class SeedEntry {
    private UUID matchupId;
    private int slot;
    private UUID teamId;
    private Integer seedNumber;
}
```

- [ ] **Step 4: Implement `saveSeedNumbers` and `autoSeedBracket` in PlayoffService**

Add `PlayoffSeedRepository` as a constructor parameter and field. Then add these methods:

```java
@Transactional
public void saveSeedNumbers(UUID playoffId, Map<UUID, Integer> teamSeeds) {
    var playoff = playoffRepository.findById(playoffId)
            .orElseThrow(() -> new IllegalArgumentException("Playoff not found: " + playoffId));
    playoffSeedRepository.deleteByPlayoffId(playoffId);
    entityManager.flush();

    for (var entry : teamSeeds.entrySet()) {
        var team = findTeam(entry.getKey());
        var seed = new PlayoffSeed(playoff, team, entry.getValue());
        playoffSeedRepository.save(seed);
    }
    log.info("Saved {} seed numbers for playoff {}", teamSeeds.size(), playoffId);
}

@Transactional
public void autoSeedBracket(UUID playoffId) {
    var seeds = playoffSeedRepository.findByPlayoffId(playoffId);
    if (seeds.isEmpty()) {
        throw new IllegalStateException("No seed numbers assigned yet");
    }

    var playoff = playoffRepository.findById(playoffId)
            .orElseThrow(() -> new IllegalArgumentException("Playoff not found: " + playoffId));
    var firstRound = playoff.getRounds().stream()
            .filter(r -> r.getRoundIndex() == 0)
            .findFirst().orElseThrow();

    var sortedSeeds = seeds.stream()
            .sorted(Comparator.comparingInt(PlayoffSeed::getSeed))
            .toList();

    int totalTeams = sortedSeeds.size();
    var matchups = firstRound.getMatchups().stream()
            .sorted(Comparator.comparingInt(PlayoffMatchup::getBracketPosition))
            .toList();

    // Standard bracket pairing: 1 vs N, 2 vs N-1, etc.
    // Matchup order distributes seeds to avoid early clashes:
    // 4 teams: [1v4, 2v3]
    // 8 teams: [1v8, 4v5, 3v6, 2v7]
    int[] matchupOrder = buildBracketOrder(totalTeams / 2);

    for (int i = 0; i < matchups.size() && i < matchupOrder.length; i++) {
        int seedIdx = matchupOrder[i];
        var matchup = matchups.get(i);
        matchup.setTeam1(sortedSeeds.get(seedIdx).getTeam());
        matchup.setTeam2(sortedSeeds.get(totalTeams - 1 - seedIdx).getTeam());
        playoffMatchupRepository.save(matchup);
    }
    log.info("Auto-seeded bracket for playoff {}", playoffId);
}

private int[] buildBracketOrder(int matchCount) {
    // For standard single-elimination brackets
    return switch (matchCount) {
        case 1 -> new int[]{0};                 // 2 teams: 1v2
        case 2 -> new int[]{0, 1};              // 4 teams: 1v4, 2v3
        case 4 -> new int[]{0, 3, 2, 1};        // 8 teams: 1v8, 4v5, 3v6, 2v7
        default -> {
            int[] order = new int[matchCount];
            for (int i = 0; i < matchCount; i++) order[i] = i;
            yield order;
        }
    };
}
```

Add `EntityManager` as a constructor parameter (add `@Autowired` or constructor injection).

- [ ] **Step 5: Update `saveSeed` to also persist seed numbers from form**

Modify the existing `saveSeed` method in `PlayoffService.java`:

```java
@Transactional
public void saveSeed(UUID playoffId, SeedForm form) {
    for (var entry : form.getSeeds()) {
        if (entry.getTeamId() != null) {
            seedTeam(entry.getMatchupId(), entry.getTeamId(), entry.getSlot());
        }
    }

    // Persist seed numbers if provided
    Map<UUID, Integer> teamSeeds = new LinkedHashMap<>();
    for (var entry : form.getSeeds()) {
        if (entry.getTeamId() != null && entry.getSeedNumber() != null) {
            teamSeeds.put(entry.getTeamId(), entry.getSeedNumber());
        }
    }
    if (!teamSeeds.isEmpty()) {
        saveSeedNumbers(playoffId, teamSeeds);
    }

    log.info("Seeding saved for playoff {}", playoffId);
}
```

- [ ] **Step 6: Update `SeedingData` to include seed map**

Modify the `getSeedingData` method to also return existing seed numbers:

```java
@Transactional(readOnly = true)
public SeedingData getSeedingData(UUID playoffId) {
    var playoff = playoffRepository.findById(playoffId)
            .orElseThrow(() -> new IllegalArgumentException("Playoff not found: " + playoffId));
    var bracket = getBracketView(playoffId);

    var firstRound = playoff.getRounds().stream()
            .filter(r -> r.getRoundIndex() == 0)
            .findFirst().orElseThrow();

    var teams = getPlayoffTeams(playoffId);

    Set<UUID> seededTeamIds = firstRound.getMatchups().stream()
            .flatMap(m -> {
                var ids = new ArrayList<UUID>();
                if (m.getTeam1() != null) ids.add(m.getTeam1().getId());
                if (m.getTeam2() != null) ids.add(m.getTeam2().getId());
                return ids.stream();
            })
            .collect(Collectors.toSet());

    Map<UUID, Integer> seedNumbers = playoffSeedRepository.findByPlayoffId(playoffId).stream()
            .collect(Collectors.toMap(s -> s.getTeam().getId(), PlayoffSeed::getSeed));

    return new SeedingData(playoff, bracket, firstRound, teams, seededTeamIds, seedNumbers);
}
```

Update the `SeedingData` record:

```java
public record SeedingData(Playoff playoff, PlayoffBracketView bracketView,
                           PlayoffRound firstRound, List<Team> teams,
                           Set<UUID> seededTeamIds, Map<UUID, Integer> seedNumbers) {}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest="PlayoffServiceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/ctc/domain/service/PlayoffService.java \
       src/main/java/org/ctc/admin/dto/SeedForm.java \
       src/test/java/org/ctc/domain/service/PlayoffServiceTest.java
git commit -m "PlayoffService: Seed-Nummern speichern und Auto-Seed-Bracket"
```

---

### Task 3: Seeding-UI Erweiterung

> **Hinweis:** Task 2 aendert die `SeedingData`-Record-Signatur (neuer Parameter `seedNumbers`). Der `PlayoffController` kompiliert erst wieder nachdem dieser Task den Controller aktualisiert hat.

**Files:**
- Modify: `src/main/resources/templates/admin/playoff-seed.html`
- Modify: `src/main/java/org/ctc/admin/controller/PlayoffController.java`

- [ ] **Step 1: Update PlayoffController to pass seed numbers to template**

In `PlayoffController.java`, update the `seed` GET method to pass `seedNumbers`:

```java
@GetMapping("/{id}/seed")
public String seed(@PathVariable UUID id, Model model) {
    var data = playoffService.getSeedingData(id);
    var form = new SeedForm();
    form.setPlayoffId(id);

    model.addAttribute("seedForm", form);
    model.addAttribute("playoff", data.playoff());
    model.addAttribute("bracket", data.bracketView());
    model.addAttribute("firstRound", data.firstRound());
    model.addAttribute("teams", data.teams());
    model.addAttribute("seededTeamIds", data.seededTeamIds());
    model.addAttribute("seedNumbers", data.seedNumbers());
    return "admin/playoff-seed";
}
```

- [ ] **Step 2: Add auto-seed POST endpoint to PlayoffController**

```java
@PostMapping("/{id}/auto-seed")
public String autoSeed(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        playoffService.autoSeedBracket(id);
        redirectAttributes.addFlashAttribute("successMessage", "Bracket auto-seeded");
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/playoffs/" + id + "/seed";
}
```

- [ ] **Step 3: Update seeding template with seed number inputs and auto-seed button**

Replace the content of `playoff-seed.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Seeding', ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <h1 th:text="'Seeding — ' + ${playoff.name}"></h1>
    </div>

    <!-- Seed Numbers -->
    <div class="card">
        <h2>Seed Numbers</h2>
        <p class="text-dim" style="margin-bottom:12px;">Assign a seed number to each team. Used for graphics and auto-seeding the bracket.</p>
        <form th:action="@{/admin/playoffs/{id}/seed(id=${playoff.id})}" method="post">
            <input type="hidden" name="playoffId" th:value="${playoff.id}">
            <table>
                <thead>
                    <tr><th>Matchup</th><th>Seed</th><th>Team 1</th><th>Seed</th><th>Team 2</th></tr>
                </thead>
                <tbody>
                    <tr th:each="matchup, iter : ${firstRound.matchups}">
                        <td th:text="'Matchup ' + (${iter.count})"></td>
                        <td style="width:60px;">
                            <input type="number" min="1"
                                   th:name="'seeds[' + (${iter.index * 2}) + '].seedNumber'"
                                   th:value="${matchup.team1 != null ? seedNumbers.get(matchup.team1.id) : ''}"
                                   style="width:50px;">
                        </td>
                        <td>
                            <input type="hidden" th:name="'seeds[' + (${iter.index * 2}) + '].matchupId'" th:value="${matchup.id}">
                            <input type="hidden" th:name="'seeds[' + (${iter.index * 2}) + '].slot'" value="1">
                            <select th:name="'seeds[' + (${iter.index * 2}) + '].teamId'">
                                <option value="">-- Select team --</option>
                                <option th:each="t : ${teams}" th:value="${t.id}" th:text="${t.shortName + ' — ' + t.name}"
                                        th:selected="${matchup.team1 != null && matchup.team1.id == t.id}"></option>
                            </select>
                        </td>
                        <td style="width:60px;">
                            <input type="number" min="1"
                                   th:name="'seeds[' + (${iter.index * 2 + 1}) + '].seedNumber'"
                                   th:value="${matchup.team2 != null ? seedNumbers.get(matchup.team2.id) : ''}"
                                   style="width:50px;">
                        </td>
                        <td>
                            <input type="hidden" th:name="'seeds[' + (${iter.index * 2 + 1}) + '].matchupId'" th:value="${matchup.id}">
                            <input type="hidden" th:name="'seeds[' + (${iter.index * 2 + 1}) + '].slot'" value="2">
                            <select th:name="'seeds[' + (${iter.index * 2 + 1}) + '].teamId'">
                                <option value="">-- Select team --</option>
                                <option th:each="t : ${teams}" th:value="${t.id}" th:text="${t.shortName + ' — ' + t.name}"
                                        th:selected="${matchup.team2 != null && matchup.team2.id == t.id}"></option>
                            </select>
                        </td>
                    </tr>
                </tbody>
            </table>
            <div class="actions" style="margin-top:16px;">
                <button type="submit" class="btn btn-primary">Save Seeding</button>
                <a th:href="@{/admin/playoffs(seasonId=${playoff.season.id})}" class="btn btn-secondary">Back</a>
            </div>
        </form>
    </div>

    <!-- Auto-Seed -->
    <div class="card">
        <h2>Auto-Seed Bracket</h2>
        <p class="text-dim" style="margin-bottom:12px;">Automatically assign teams to matchups based on their seed numbers (1 vs N, 2 vs N-1, etc.).</p>
        <form th:action="@{/admin/playoffs/{id}/auto-seed(id=${playoff.id})}" method="post">
            <button type="submit" class="btn btn-secondary">Auto-Seed by Number</button>
        </form>
    </div>
</section>
</body>
</html>
```

- [ ] **Step 4: Run full test suite to verify nothing broke**

Run: `./mvnw verify`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/admin/playoff-seed.html \
       src/main/java/org/ctc/admin/controller/PlayoffController.java
git commit -m "Seeding-UI: Seed-Nummern und Auto-Seed-Button"
```

---

### Task 4: Grafik-Services playoff-faehig machen — LineupGraphicService

**Files:**
- Modify: `src/main/java/org/ctc/admin/service/LineupGraphicService.java`
- Modify: `src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java`

- [ ] **Step 1: Write failing test — race without teams throws**

Add to `LineupGraphicServiceTest.java`:

```java
@Test
void givenRaceWithNoTeams_whenGenerateLineup_thenThrowsIllegalState() {
    // given
    var service = createService();
    var race = new Race();

    // when / then
    assertThatThrownBy(() -> service.generateLineup(race))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no teams");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest="LineupGraphicServiceTest#givenRaceWithNoTeams_whenGenerateLineup_thenThrowsIllegalState"`
Expected: FAIL — current code checks for "no match", not "no teams"

- [ ] **Step 3: Update `LineupGraphicService.generateLineup`**

Add `PlayoffSeedRepository` as a constructor parameter. Replace the match-check and label/position logic:

```java
public String generateLineup(Race race) throws IOException {
    if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
        throw new IllegalStateException("Race has no teams assigned");
    }

    var homeTeam = race.getHomeTeam();
    var awayTeam = race.getAwayTeam();
    var season = race.getMatchday().getSeason();

    var lineups = raceLineupRepository.findByRaceId(race.getId());
    if (lineups.isEmpty()) throw new IllegalStateException("No lineup entries for this race");
    var pairings = buildPairings(lineups, homeTeam, awayTeam);

    String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
    String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
    if (homeCardBase64 == null) throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
    if (awayCardBase64 == null) throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());

    int homePosition = 0;
    int awayPosition = 0;

    if (race.getPlayoffMatchup() != null) {
        // Playoff: use seed numbers
        var playoff = race.getPlayoffMatchup().getRound().getPlayoff();
        var homeSeed = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), homeTeam.getId());
        var awaySeed = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), awayTeam.getId());
        homePosition = homeSeed.map(PlayoffSeed::getSeed).orElse(0);
        awayPosition = awaySeed.map(PlayoffSeed::getSeed).orElse(0);
    } else {
        // Regular season: use standings
        var standings = standingsService.calculateStandings(season.getId());
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).getTeam().getId().equals(homeTeam.getId())) homePosition = i + 1;
            if (standings.get(i).getTeam().getId().equals(awayTeam.getId())) awayPosition = i + 1;
        }
    }

    // Resolve season name: playoff name for playoff races, season name otherwise
    String seasonName = race.getPlayoffMatchup() != null
            ? race.getPlayoffMatchup().getRound().getPlayoff().getName()
            : season.getName();

    var ctx = new Context();
    ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
    ctx.setVariable("matchdayName", race.getMatchday().getLabel());
    ctx.setVariable("seasonName", seasonName);
    ctx.setVariable("homeCardBase64", homeCardBase64);
    ctx.setVariable("awayCardBase64", awayCardBase64);
    ctx.setVariable("homePosition", homePosition);
    ctx.setVariable("awayPosition", awayPosition);
    ctx.setVariable("pairings", pairings);
    ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
    ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

    String html = renderTemplate(ctx);

    Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
    Files.createDirectories(raceDir);
    Path outputFile = raceDir.resolve("lineup.png");

    renderScreenshot(html, outputFile);

    log.info("Generated lineup graphic: {}", outputFile);
    return "/uploads/races/" + race.getId() + "/lineup.png";
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest="LineupGraphicServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ctc/admin/service/LineupGraphicService.java \
       src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java
git commit -m "LineupGraphicService: Playoff-Kompatibilitaet (Labels + Seed)"
```

---

### Task 5: Grafik-Services playoff-faehig machen — ResultsGraphicService

**Files:**
- Modify: `src/main/java/org/ctc/admin/service/ResultsGraphicService.java`
- Modify: `src/test/java/org/ctc/admin/service/ResultsGraphicServiceTest.java`

- [ ] **Step 1: Write failing test — race without teams throws**

Add to `ResultsGraphicServiceTest.java`:

```java
@Test
void givenRaceWithNoTeams_whenGenerateResults_thenThrowsIllegalState() {
    // given
    var service = createService();
    var race = new Race();

    // when / then
    assertThatThrownBy(() -> service.generateResults(race))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no teams");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest="ResultsGraphicServiceTest#givenRaceWithNoTeams_whenGenerateResults_thenThrowsIllegalState"`
Expected: FAIL

- [ ] **Step 3: Update `ResultsGraphicService.generateResults`**

Replace the match-check and seasonName logic (no position changes needed — Results has no position field):

```java
public String generateResults(Race race) throws IOException {
    if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
        throw new IllegalStateException("Race has no teams assigned");
    }
    if (race.getResults().isEmpty()) throw new IllegalStateException("No results for this race");

    var homeTeam = race.getHomeTeam();
    var awayTeam = race.getAwayTeam();
    var season = race.getMatchday().getSeason();

    var resultRows = buildResultRows(race);

    String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
    String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
    if (homeCardBase64 == null) throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
    if (awayCardBase64 == null) throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());

    int homeTotal = resultRows.stream().mapToInt(DriverResultRow::homePoints).sum();
    int awayTotal = resultRows.stream().mapToInt(DriverResultRow::awayPoints).sum();

    // Resolve season name: playoff name for playoff races, season name otherwise
    String seasonName = race.getPlayoffMatchup() != null
            ? race.getPlayoffMatchup().getRound().getPlayoff().getName()
            : season.getName();

    var ctx = new Context();
    ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
    ctx.setVariable("matchdayName", race.getMatchday().getLabel());
    ctx.setVariable("seasonName", seasonName);
    ctx.setVariable("homeCardBase64", homeCardBase64);
    ctx.setVariable("awayCardBase64", awayCardBase64);
    ctx.setVariable("homeTotal", homeTotal);
    ctx.setVariable("awayTotal", awayTotal);
    ctx.setVariable("homeIsWinner", homeTotal > awayTotal);
    ctx.setVariable("awayIsWinner", awayTotal > homeTotal);
    ctx.setVariable("resultRows", resultRows);
    ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
    ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

    String html = renderTemplate(ctx);

    Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
    Files.createDirectories(raceDir);
    Path outputFile = raceDir.resolve("results.png");

    renderScreenshot(html, outputFile);

    log.info("Generated results graphic: {}", outputFile);
    return "/uploads/races/" + race.getId() + "/results.png";
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest="ResultsGraphicServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ctc/admin/service/ResultsGraphicService.java \
       src/test/java/org/ctc/admin/service/ResultsGraphicServiceTest.java
git commit -m "ResultsGraphicService: Playoff-Kompatibilitaet (Labels)"
```

---

### Task 6: Grafik-Services playoff-faehig machen — SettingsGraphicService

**Files:**
- Modify: `src/main/java/org/ctc/admin/service/SettingsGraphicService.java`

- [ ] **Step 1: Update `SettingsGraphicService.generateSettings`**

Add `PlayoffSeedRepository` as a constructor parameter. Replace the match-check and label/position logic — same pattern as LineupGraphicService:

```java
public String generateSettings(Race race) throws IOException {
    if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
        throw new IllegalStateException("Race has no teams assigned");
    }
    if (race.getCar() == null) throw new IllegalStateException("Race has no car");
    if (race.getTrack() == null) throw new IllegalStateException("Race has no track");
    if (!race.hasAllSettings()) throw new IllegalStateException("Race settings are incomplete");

    var homeTeam = race.getHomeTeam();
    var awayTeam = race.getAwayTeam();
    var season = race.getMatchday().getSeason();
    var settings = race.getSettings();

    String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
    String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
    if (homeCardBase64 == null) throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
    if (awayCardBase64 == null) throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());

    int homePosition = 0;
    int awayPosition = 0;

    if (race.getPlayoffMatchup() != null) {
        var playoff = race.getPlayoffMatchup().getRound().getPlayoff();
        var homeSeed = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), homeTeam.getId());
        var awaySeed = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), awayTeam.getId());
        homePosition = homeSeed.map(PlayoffSeed::getSeed).orElse(0);
        awayPosition = awaySeed.map(PlayoffSeed::getSeed).orElse(0);
    } else {
        var standings = standingsService.calculateStandings(season.getId());
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).getTeam().getId().equals(homeTeam.getId())) homePosition = i + 1;
            if (standings.get(i).getTeam().getId().equals(awayTeam.getId())) awayPosition = i + 1;
        }
    }

    String seasonName = race.getPlayoffMatchup() != null
            ? race.getPlayoffMatchup().getRound().getPlayoff().getName()
            : season.getName();

    var ctx = new Context();
    ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
    ctx.setVariable("matchdayName", race.getMatchday().getLabel());
    ctx.setVariable("seasonName", seasonName);
    ctx.setVariable("homeCardBase64", homeCardBase64);
    ctx.setVariable("awayCardBase64", awayCardBase64);
    ctx.setVariable("homePosition", homePosition);
    ctx.setVariable("awayPosition", awayPosition);
    ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
    ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

    // Settings data
    ctx.setVariable("carName", race.getCar().getDisplayName());
    ctx.setVariable("trackName", race.getTrack().getName());
    ctx.setVariable("numberOfLaps", settings.getNumberOfLaps());
    ctx.setVariable("tyreWearMultiplier", settings.getTyreWearMultiplier());
    ctx.setVariable("fuelConsumptionMultiplier", settings.getFuelConsumptionMultiplier());
    ctx.setVariable("refuelingSpeed", settings.getRefuelingSpeed());
    ctx.setVariable("initialFuel", settings.getInitialFuel());
    ctx.setVariable("numberOfRequiredPitStops", settings.getNumberOfRequiredPitStops());
    ctx.setVariable("timeProgressionMultiplier", settings.getTimeProgressionMultiplier());
    ctx.setVariable("weather", settings.getWeather());
    ctx.setVariable("timeOfDay", settings.getTimeOfDay());
    ctx.setVariable("availableTyres", settings.getAvailableTyres());
    ctx.setVariable("mandatoryTyres", settings.getMandatoryTyres());

    String html = renderTemplate(ctx);

    Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
    Files.createDirectories(raceDir);
    Path outputFile = raceDir.resolve("settings.png");

    renderScreenshot(html, outputFile);

    log.info("Generated settings graphic: {}", outputFile);
    return "/uploads/races/" + race.getId() + "/settings.png";
}
```

- [ ] **Step 2: Run tests to verify nothing broke**

Run: `./mvnw verify`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/ctc/admin/service/SettingsGraphicService.java
git commit -m "SettingsGraphicService: Playoff-Kompatibilitaet (Labels + Seed)"
```

---

### Task 7: Grafik-Services playoff-faehig machen — OverlayGraphicService

**Files:**
- Modify: `src/main/java/org/ctc/admin/service/OverlayGraphicService.java`
- Modify: `src/test/java/org/ctc/admin/service/OverlayGraphicServiceTest.java`

- [ ] **Step 1: Update existing test — change "no match" to "no teams"**

In `OverlayGraphicServiceTest.java`, update the test:

```java
@Test
void givenRaceWithNoTeams_whenGenerateOverlay_thenThrowsIllegalState() {
    // given
    var service = createService();
    var race = new Race();

    // when / then
    assertThatThrownBy(() -> service.generateOverlay(race))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no teams");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest="OverlayGraphicServiceTest#givenRaceWithNoTeams_whenGenerateOverlay_thenThrowsIllegalState"`
Expected: FAIL — current code checks for "no match"

- [ ] **Step 3: Update `OverlayGraphicService.generateOverlay`**

Add `PlayoffSeedRepository` as a constructor parameter. Replace the match-check and update matchdayName resolution. Note: OverlayGraphicService does NOT use `seasonName`, but uses `matchdayName` and standings for record:

```java
public String generateOverlay(Race race) throws IOException {
    if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
        throw new IllegalStateException("Race has no teams assigned");
    }

    var homeTeam = race.getHomeTeam();
    var awayTeam = race.getAwayTeam();
    var season = race.getMatchday().getSeason();

    Map<UUID, SeasonTeam> seasonTeamMap = new HashMap<>();
    for (var st : seasonTeamRepository.findBySeasonId(season.getId())) {
        seasonTeamMap.put(st.getTeam().getId(), st);
    }

    var standings = standingsService.calculateStandings(season.getId());
    Map<UUID, TeamStanding> standingMap = new HashMap<>();
    for (var standing : standings) {
        standingMap.put(standing.getTeam().getId(), standing);
    }

    var homeSt = seasonTeamMap.get(homeTeam.getId());
    var awaySt = seasonTeamMap.get(awayTeam.getId());

    var ctx = new Context();
    ctx.setVariable("homeTeamName", homeTeam.getName());
    ctx.setVariable("homeTeamNameHtml", formatTeamNameHtml(homeTeam.getName()));
    ctx.setVariable("homeTeamShortName", homeTeam.getShortName());
    ctx.setVariable("homeLogoBase64", encodeLogoBase64(homeTeam, homeSt));
    ctx.setVariable("homePrimaryColor", homeSt != null ? homeSt.getEffectivePrimaryColor() : homeTeam.getPrimaryColor());
    ctx.setVariable("homeSecondaryColor", homeSt != null ? homeSt.getEffectiveSecondaryColor() : homeTeam.getSecondaryColor());
    ctx.setVariable("homeRecord", formatRecord(standingMap.get(homeTeam.getId())));
    ctx.setVariable("awayTeamName", awayTeam.getName());
    ctx.setVariable("awayTeamNameHtml", formatTeamNameHtml(awayTeam.getName()));
    ctx.setVariable("awayTeamShortName", awayTeam.getShortName());
    ctx.setVariable("awayLogoBase64", encodeLogoBase64(awayTeam, awaySt));
    ctx.setVariable("awayPrimaryColor", awaySt != null ? awaySt.getEffectivePrimaryColor() : awayTeam.getPrimaryColor());
    ctx.setVariable("awaySecondaryColor", awaySt != null ? awaySt.getEffectiveSecondaryColor() : awayTeam.getSecondaryColor());
    ctx.setVariable("awayRecord", formatRecord(standingMap.get(awayTeam.getId())));
    ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
    ctx.setVariable("matchdayName", race.getMatchday().getLabel());
    ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
    ctx.setVariable("vsBadgeBase64", encodeClasspathResource(VS_BADGE_CLASSPATH, "image/svg+xml"));
    ctx.setVariable("commentatorBase64", encodeClasspathResource(COMMENTATOR_CLASSPATH, "image/png"));
    ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

    String html = renderTemplate(ctx);

    Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
    Files.createDirectories(raceDir);
    Path outputFile = raceDir.resolve("overlay.png");

    renderScreenshotTransparent(html, outputFile);

    log.info("Generated overlay graphic: {}", outputFile);
    return "/uploads/races/" + race.getId() + "/overlay.png";
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest="OverlayGraphicServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ctc/admin/service/OverlayGraphicService.java \
       src/test/java/org/ctc/admin/service/OverlayGraphicServiceTest.java
git commit -m "OverlayGraphicService: Playoff-Kompatibilitaet (Team-Check)"
```

---

### Task 8: Endverifikation

**Files:** (keine neuen Aenderungen)

- [ ] **Step 1: Run full test suite**

Run: `./mvnw verify`
Expected: BUILD SUCCESS with all tests passing

- [ ] **Step 2: Verify coverage threshold**

Run: `./mvnw verify` and check JaCoCo report.
Expected: Coverage >= 82% minimum threshold

- [ ] **Step 3: Check for compilation warnings**

Run: `./mvnw compile -q`
Expected: No warnings
