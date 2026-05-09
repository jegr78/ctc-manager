# Phase 58: Service Layer - Pattern Map

**Mapped:** 2026-04-27
**Files analyzed:** 21 (3 NEW main + 4 NEW tests + 8 MODIFY services + 6 MODIFY repos)
**Analogs found:** 21 / 21 (every file has a strong existing analog)

## File Classification

### NEW files

| New File | Role / Layer | Data Flow | Closest Analog | Match Quality |
|----------|--------------|-----------|----------------|---------------|
| `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` | Domain Service (CRUD + composition) | request-response (read) + transactional writes | `src/main/java/org/ctc/domain/service/SeasonManagementService.java` | exact (orchestration service with CRUD + roster management) |
| `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` | Test fixture helper (pure-Java entity builder) | builder/factory | `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` (helpers section, lines 200-214) | role-match (no full fixture class precedent — extracts the inline helper pattern) |
| `src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java` | Service unit test (Mockito-only) | test | `src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java` | exact (CRUD + composition Mockito test) |
| `src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java` | Repository integration test | DB-truth fixture verification | `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` | role-match (project precedent is `@SpringBootTest @ActiveProfiles("dev") @Transactional`; D-13 says `@DataJpaTest`) |
| `src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java` | Repository integration test | DB-truth fixture verification | `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` | role-match (same as above) |
| `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` | Repository integration test | DB-truth fixture verification | `src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java` | role-match (same package, same DB model) |

### MODIFIED files (extend existing)

| File | Role / Layer | Data Flow | Closest In-File Pattern | Match Quality |
|------|--------------|-----------|-------------------------|---------------|
| `src/main/java/org/ctc/domain/service/StandingsService.java` | Domain Service (read aggregation) | CRUD-read | existing `calculateStandings(seasonId)` lines 26-53 | self (extend existing) |
| `src/main/java/org/ctc/domain/service/PlayoffService.java` | Domain Service (lifecycle + composition) | request-response + transactional writes | existing `createPlayoff` lines 43-95 | self |
| `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` | Domain Service (cross-service composition) | transactional writes | existing `autoSeedBracket` lines 121-153 | self |
| `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java` | Domain Service (algorithmic generation) | transactional writes | existing `generate(seasonId, ...)` lines 34-68 | self |
| `src/main/java/org/ctc/domain/service/SwissPairingService.java` | Domain Service (algorithmic generation) | transactional writes + read aggregation | existing `generateNextRound` lines 28-76 | self |
| `src/main/java/org/ctc/domain/service/DriverRankingService.java` | Domain Service (read aggregation) | CRUD-read | existing `calculateRanking` lines 25-54 + `calculateAlltimeRanking` lines 81-112 | self |
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java` | Domain Service (CRUD + composition) | transactional writes | existing `save` lines 117-154 + `delete` lines 156-162 | self |
| `src/main/java/org/ctc/domain/service/MatchdayService.java` | Domain Service (read + helpers) | CRUD-read | existing `getMatchdayList(seasonId)` lines 51-69 | self |
| `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` | Site generator (caller-side update) | batch read | existing `generateStandings` lines 175-199 | self |
| `src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java` | JPA Repository (custom finders) | DB query | `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` lines 11-29 | role-match |
| `src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java` | JPA Repository (custom finders) | DB query | `RaceResultRepository.java` | role-match |
| `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` | JPA Repository (custom finders) | DB query | `RaceResultRepository.java` | role-match |
| `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` | JPA Repository (custom finders) | DB query | self (extend existing magic-name finders) | self |
| `src/main/java/org/ctc/domain/repository/MatchdayRepository.java` | JPA Repository (custom finders) | DB query | self | self |

---

## Pattern Assignments

### `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` (NEW — Domain Service)

**Analog:** `src/main/java/org/ctc/domain/service/SeasonManagementService.java`

**Class-level annotation combo + imports** (lines 1-20):
```java
package org.ctc.domain.service;

import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonManagementService {

    private final SeasonRepository seasonRepository;
    // ... other repos as final fields injected via @RequiredArgsConstructor
```

**Read pattern with EntityNotFoundException** (lines 77-81 of SeasonManagementService):
```java
@Transactional(readOnly = true)
public Season findById(UUID id) {
    return seasonRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Season", id));
}
```
Apply to D-02: `findRegularPhase(UUID seasonId)` throws `new EntityNotFoundException("Regular SeasonPhase for season", seasonId)`.

**Transactional write + log.info pattern** (lines 117-154 of SeasonManagementService):
```java
@Transactional
public Season save(UUID id, String name, int year, int number, ...) {
    var raceScoring = raceScoringRepository.findById(raceScoringId)
            .orElseThrow(() -> new EntityNotFoundException("RaceScoring", raceScoringId));
    // ...
    season = seasonRepository.save(season);
    if (id != null) {
        log.info("Updated season: {}", season.getName());
    } else {
        log.info("Created season: {}", season.getName());
    }
    return season;
}
```
Apply to D-14 (`create` with duplicate-type guard) + D-20 (REGULAR-LEAGUE auto-derive `PhaseTeam` from `SeasonTeam`).

**Inner record for structured return** (lines 35-44 of SeasonManagementService):
```java
public record SeasonDetailData(Season season, Playoff playoff, boolean hasTeams,
                               boolean hasMatchdays, boolean canGenerate, boolean isSwiss) {}
```
Apply if `SeasonPhaseService` needs a `SeasonPhaseSummary` return type — keep optional, prefer plain entity returns when possible.

**Sub-team auto-add pattern as inspiration for D-20 PhaseTeam derivation** (lines 224-241 of SeasonManagementService):
```java
@Transactional
public String addTeamToSeason(UUID seasonId, UUID teamId) {
    var season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
    // ...
    if (!season.containsTeam(team)) {
        // ... add logic + log.info
        seasonRepository.save(season);
    }
    return team.getShortName();
}
```

**Key conventions to copy:**
- `@Slf4j @Service @RequiredArgsConstructor` stereotype combo
- `@Transactional(readOnly = true)` on every read method
- `@Transactional` on every write method
- `EntityNotFoundException(entityType, id)` for missing entities
- `BusinessRuleException(message)` for D-14 ("Season already has REGULAR/PLAYOFF/PLACEMENT phase")
- `log.info("...{}...", arg)` parameterized after each successful state change

**What's new vs analog:**
- New entity types (`SeasonPhase`, `SeasonPhaseGroup`, `PhaseTeam`)
- Roster auto-derivation from `SeasonTeam` on REGULAR-LEAGUE create (D-20)
- Cross-service injection target: this service is injected into the six refactored services (D-02)

---

### `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` (NEW — Test fixture helper)

**Analog:** `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` (helpers section, lines 200-214)

**Helper-method shape** (lines 200-214 of `SeasonPhaseEntityIntegrationTest`):
```java
private Season newSeason(String name, int year, int number) {
    var season = new Season(name, year, number);
    season.setRaceScoring(raceScoringRepository.findAll().get(0));
    season.setMatchScoring(matchScoringRepository.findAll().get(0));
    return season;
}

private SeasonPhase newSavedPhase(Season season, PhaseType phaseType, PhaseLayout layout, int sortIndex) {
    var phase = new SeasonPhase(season, phaseType, layout, sortIndex);
    phase.setRaceScoring(season.getRaceScoring());
    phase.setMatchScoring(season.getMatchScoring());
    return seasonPhaseRepository.save(phase);
}
```

**Entity constructors available** (verified from `SeasonPhaseGroup` line 35, `PhaseTeam` line 35):
```java
new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, sortIndex);
new SeasonPhaseGroup(phase, name, sortIndex);
new PhaseTeam(phase, team);  // call setGroup(group) separately
```

**Pure-Java pattern (no DB) — set IDs explicitly for Mockito tests** (D-11 spec):
```java
public final class PhaseTestFixtures {

    private PhaseTestFixtures() {}

    public static SeasonPhase regularPhase(Season season, RaceScoring rs, MatchScoring ms) {
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        phase.setId(UUID.randomUUID());  // for Mockito tests where save() doesn't run
        phase.setRaceScoring(rs);
        phase.setMatchScoring(ms);
        return phase;
    }
    // ... groupsRegularPhase(...), playoffPhase(...), assignTeam(phase, team, group)
}
```

**Key conventions to copy:**
- `final class` + `private` constructor (utility class)
- `public static` factory methods named after the entity-shape they produce
- For Mockito-mode: explicitly call `setId(UUID.randomUUID())` so equality/hashing works without DB
- For repo-IT-mode: leave `id` null — let JPA assign

**What's new vs analog:**
- First reusable cross-test fixture class in `src/test/java/org/ctc/domain/service/` (existing tests inline their `@BeforeEach setUp()` builders)
- Re-used by both Mockito tests (services) AND `@SpringBootTest`/`@DataJpaTest` IT-tests (repositories)

---

### `src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java` (NEW — Mockito service test)

**Analog:** `src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java`

**Class-level annotations + mock declaration** (lines 1-48 of `SeasonManagementServiceTest`):
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeasonManagementServiceTest {

    @Mock
    private SeasonRepository seasonRepository;
    @Mock
    private TeamRepository teamRepository;
    // ... all collaborators @Mocked

    @InjectMocks
    private SeasonManagementService service;
```

**BDD given-when-then test pattern** (lines 50-60 of `SeasonManagementServiceTest`):
```java
@Test
void givenTeamNotInSeason_whenAddTeamToSeason_thenTeamAdded() {
    // given
    var season = createSeason("Test Season");
    var team = createTeam("TST", "Test Team");

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

    // when
    String result = service.addTeamToSeason(season.getId(), team.getId());
    // then
    // ... assertions
}
```

**Exception-test pattern** (`assertThatThrownBy` already imported in this file):
```java
// when / then
assertThatThrownBy(() -> service.create(seasonId, PhaseType.REGULAR, ...))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("Season already has");
```

**Key conventions to copy:**
- `@ExtendWith(MockitoExtension.class)` (NOT `@SpringBootTest`)
- `@Mock` on every repository, `@InjectMocks` on the SUT
- `givenContext_whenAction_thenExpectedResult()` method names
- `// given` / `// when` / `// then` body comments (combined `// when / then` for exception tests)
- AssertJ `assertThat(...).isEqualTo(...)`; `assertThatThrownBy(...)` for exceptions
- Use `PhaseTestFixtures.regularPhase(season, rs, ms)` to build entity stubs returned by mocks

**What's new vs analog:**
- New dependencies: `SeasonPhaseRepository`, `SeasonPhaseGroupRepository`, `PhaseTeamRepository` mocks
- New SUT: `SeasonPhaseService` (D-02 resolver, D-14 duplicate guard, D-20 roster auto-derivation)

---

### `src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java` (NEW — Repository IT test)

**Analog:** `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java`

**Class-level annotations + autowiring** (lines 14-77 of `SeasonPhaseEntityIntegrationTest`):
```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SeasonPhaseEntityIntegrationTest {

    @Autowired
    private SeasonRepository seasonRepository;
    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;
    // ...
    @PersistenceContext
    private EntityManager entityManager;
```

**Test data isolation pattern** (lines 39-41 docstring + per-test prefix):
```java
// Test data uses the "Phase56-Test" prefix to comply with CLAUDE.md
// "Isolate Test Data Completely". @Transactional rolls each test back so no
// rows leak between tests or into the dev seed data.
```
For Phase 58: use prefix `Phase58-Test-` on every entity name.

**Save-flush-reload pattern for finder verification** (lines 102-125):
```java
@Test
void givenSeasonPhaseWithGroups_whenReloaded_thenGroupsCollectionIsOrderedBySortIndex() {
    // given
    var season = seasonRepository.save(newSeason("Phase56-Test-S2", 9999, 2));
    var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
    phase.setRaceScoring(season.getRaceScoring());
    phase.setMatchScoring(season.getMatchScoring());
    var savedPhase = seasonPhaseRepository.save(phase);
    seasonPhaseGroupRepository.save(new SeasonPhaseGroup(savedPhase, "Phase56-Test-A", 0));
    entityManager.flush();
    entityManager.clear();

    // when
    var reloaded = seasonPhaseRepository.findById(savedPhase.getId()).orElseThrow();

    // then
    assertThat(reloaded.getGroups()).hasSize(3);
}
```

**Key conventions to copy:**
- `@SpringBootTest @ActiveProfiles("dev") @Transactional` triplet (project precedent — zero `@DataJpaTest` usages exist; D-13 mentions `@DataJpaTest` but Open Question 1 in RESEARCH.md recommends honouring D-13 with a comment OR following project precedent — planner picks)
- `@Autowired` field injection (test class only — services keep constructor injection)
- `@PersistenceContext EntityManager entityManager` for explicit `flush()` / `clear()` between save and verify
- AssertJ `assertThat(...).hasSize(...)` / `.extracting(...).containsExactly(...)`
- Test data prefix `Phase58-Test-` to avoid colliding with seed data

**What's new vs analog:**
- New custom finder verifications: `findBySeasonIdAndPhaseType`, `findBySeasonIdOrderBySortIndex`
- Verifies D-22 magic-name resolution against H2

---

### `src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java` (NEW)

**Analog:** Same as above (`SeasonPhaseEntityIntegrationTest.java`)

**Verifies D-22 magic-name finders:**
- `findByPhaseIdOrderBySortIndex(UUID phaseId)`

Same conventions as `SeasonPhaseRepositoryIT`. Use `Phase58-Test-Group-` prefix.

---

### `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` (NEW)

**Analog:** `src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java` (same package, exercises same DB constraints)

**Verifies D-22 magic-name finders:**
- `findByPhaseId(UUID phaseId)`
- `findByPhaseIdAndGroupId(UUID phaseId, UUID groupId)` — note: groupId nullable; for LEAGUE phases groupId is null. JPA derives `groupId IS NULL` automatically when `null` is passed; verify in test.
- `existsByPhaseSeasonId(UUID seasonId)` (used by D-18 delete-guard)

Same `@SpringBootTest @ActiveProfiles("dev") @Transactional` setup. Use `Phase58-Test-PT-` prefix.

---

### `src/main/java/org/ctc/domain/service/StandingsService.java` (MODIFY — extend with phaseId/groupId)

**Analog (existing in-file pattern):** `calculateStandings(UUID seasonId)` lines 26-53

**Existing canonical loop pattern** (lines 26-53):
```java
@Transactional(readOnly = true)
public List<TeamStanding> calculateStandings(UUID seasonId) {
    var season = seasonRepository.findById(seasonId).orElse(null);
    if (season == null) return List.of();

    var matchScoring = season.getMatchScoring();
    List<Match> matches = matchRepository.findByMatchdaySeasonId(seasonId);
    Map<UUID, TeamStanding> standingsMap = new HashMap<>();
    Map<UUID, UUID> successionMap = season.buildSuccessionMap();

    for (Team team : season.getActiveTeams()) {
        standingsMap.put(team.getId(), new TeamStanding(team));
    }
    for (Match match : matches) {
        processMatch(match, standingsMap, matchScoring, successionMap);
    }

    List<TeamStanding> standings = new ArrayList<>(standingsMap.values());
    standings.removeIf(s -> s.getPlayed() == 0);
    standings.sort(Comparator
            .comparing(TeamStanding::getPoints, Comparator.reverseOrder())
            .thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
            .thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

    log.debug("Calculated standings for season {}: {} teams", seasonId, standings.size());
    return standings;
}
```

**Tiebreaker chain to reuse for D-04 combined-view** (lines 46-49 — locked: `points → pointDifference → pointsFor`).

**Inner class to extend with nullable group** (lines 204-302 — `TeamStanding`):
```java
public static class TeamStanding {
    private final Team team;
    // ... existing wins, draws, losses, points, pointsFor, pointsAgainst, buchholz
    // D-05 ADD: private SeasonPhaseGroup group;  (nullable)
    // D-05 ADD: public SeasonPhaseGroup getGroup() { return group; }
    // D-05 ADD: public void setGroup(SeasonPhaseGroup group) { this.group = group; }
}
```

**Bridge pattern to add (D-01)**:
```java
@Deprecated  // remove in Phase 60 alongside UI cutover
@Transactional(readOnly = true)
public List<TeamStanding> calculateStandings(UUID seasonId) {
    return calculateStandings(seasonPhaseService.findRegularPhase(seasonId).getId(), null);
}

@Transactional(readOnly = true)
public List<TeamStanding> calculateStandings(UUID phaseId, UUID groupId) {
    // ... swap matchRepository.findByMatchdaySeasonId(seasonId)
    //     for matchRepository.findByMatchdayPhaseId(phaseId) (filter by groupId if non-null)
}
```

**Key conventions to copy:**
- Keep `@Transactional(readOnly = true)` on read methods
- Keep the `Comparator.comparing(...).thenComparing(...).thenComparing(...)` tiebreaker chain verbatim
- `log.debug("...phase {}: {} teams", phaseId, standings.size())`
- Inject `SeasonPhaseService` (added to `@RequiredArgsConstructor` final fields)

**What's new vs analog:**
- New collaborator: `SeasonPhaseService` (D-02)
- New repo finder dependency: `MatchRepository.findByMatchdayPhaseId(UUID)` (Phase 58 addition per RESEARCH §Architecture diagram)
- D-05: `TeamStanding.group` nullable field
- D-06: `calculateStandingsWithBuchholz(phaseId, groupId)` requires non-null groupId for GROUPS layout

---

### `src/main/java/org/ctc/domain/service/PlayoffService.java` (MODIFY — phase-aware + auto-create)

**Analog (existing in-file pattern):** `createPlayoff(UUID seasonId, ...)` lines 43-95

**Existing transactional cross-entity write** (lines 43-95):
```java
@Transactional
public Playoff createPlayoff(UUID seasonId, String name, int numberOfTeams) {
    if (!DEFAULT_ROUND_LABELS.containsKey(numberOfTeams)) {
        throw new IllegalArgumentException("Number of teams must be 2, 4 or 8, got: " + numberOfTeams);
    }
    if (playoffRepository.findBySeasonId(seasonId).isPresent()) {
        throw new IllegalArgumentException("Playoff already exists for this season");
    }
    Season season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
    Playoff playoff = new Playoff(season, name);
    playoff = playoffRepository.save(playoff);
    // ... rounds + matchups creation
    log.info("Created playoff '{}' for season '{}' with {} teams, {} rounds", ...);
    return playoff;
}
```

**Apply for D-19 (auto-create PLAYOFF phase) — service-to-service composition pattern from RESEARCH §Pattern 2:**
```java
@Transactional  // single boundary covers SeasonPhase + Playoff writes — atomicity
public Playoff createPlayoff(UUID seasonId, String name, int numberOfTeams) {
    // ... existing arg checks
    var phase = seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF)
            .orElseGet(() -> seasonPhaseService.create(seasonId,
                    PhaseType.PLAYOFF, PhaseLayout.BRACKET, /*sortIndex*/ 10,
                    name, /*copy raceScoring/matchScoring from season*/));
    Playoff playoff = new Playoff(season, name);
    playoff.setPhase(phase);  // Phase 56's bidirectional field
    playoff = playoffRepository.save(playoff);
    // ... existing rounds + matchups creation
}
```
On D-14 duplicate-guard collision (existing PLAYOFF phase already present without a Playoff), use `findByType(...).orElseGet(...)` so we tolerate "phase exists but no playoff yet" but throw via the existing `findBySeasonId(seasonId).isPresent()` check — replace `IllegalArgumentException` with `BusinessRuleException`.

**Existing M:N pattern to mark `@Deprecated`** (lines 97-115):
```java
@Transactional
public void addSeasonToPlayoff(UUID playoffId, UUID seasonId) { ... }
@Transactional
public void removeSeasonFromPlayoff(UUID playoffId, UUID seasonId) { ... }
```
Add `@Deprecated  // remove in Phase 61 alongside playoff_seasons drop` Javadoc on both. Keep functional.

**Pitfall 4 (RESEARCH.md): `addRaceToMatchup` line 305 must set phase on new matchday.** Existing line:
```java
var matchday = new Matchday(season, label, ...);  // line 305
```
Add: `matchday.setPhase(playoff.getPhase());` before save.

**Key conventions to copy:**
- `@Transactional` on cross-entity writes
- `EntityNotFoundException(entityType, id)` for missing entities
- `BusinessRuleException` for "Season already has a playoff phase" (replace existing `IllegalArgumentException`)
- `log.info("Created playoff '{}' for season '{}' ...", ...)`
- Inject `SeasonPhaseService` (added to `@RequiredArgsConstructor` final fields)

**What's new vs analog:**
- New collaborator: `SeasonPhaseService`
- D-19 auto-create flow + duplicate guard via `BusinessRuleException`
- D-03 `@Deprecated` on M:N methods + on legacy `seasonId`-based methods
- Pitfall 4 fix: link auto-created matchday to PLAYOFF phase

---

### `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` (MODIFY — Top-N from REGULAR standings)

**Analog (existing in-file pattern):** `autoSeedBracket(UUID playoffId)` lines 121-153

**Existing seed-pool resolution** (lines 122-126 — uses `findByPlayoffId`):
```java
@Transactional
public void autoSeedBracket(UUID playoffId) {
    var seeds = playoffSeedRepository.findByPlayoffId(playoffId);
    if (seeds.isEmpty()) {
        throw new IllegalStateException("No seed numbers assigned yet");
    }
    // ...
}
```

**Existing M:N team-pool collection from `getSeedingData`** (lines 59-68):
```java
Map<UUID, Team> teamMap = new LinkedHashMap<>();
for (Season season : playoff.getSeasons()) {
    for (Team team : season.getTeams()) {
        teamMap.putIfAbsent(team.getId(), team);
    }
}
for (Team team : playoff.getSeason().getTeams()) {
    teamMap.putIfAbsent(team.getId(), team);
}
```
Pitfall 5 (RESEARCH.md): leave this code path alone in Phase 58 — D-15's new Top-N source replaces it for `autoSeedBracket` only, but `getSeedingData` keeps consulting M:N for the manual seeding UI compatibility.

**Bracket-order helper to reuse verbatim** (lines 155-166 of `PlayoffSeedingService`) — RESEARCH §"Don't Hand-Roll":
```java
private int[] buildBracketOrder(int matchCount) {
    return switch (matchCount) {
        case 1 -> new int[]{0};
        case 2 -> new int[]{0, 1};
        case 4 -> new int[]{0, 3, 2, 1};
        default -> { ... }
    };
}
```

**Apply for D-15 (Top-N from REGULAR standings):**
```java
@Transactional
public void autoSeedBracket(UUID playoffId) {
    var playoff = playoffRepository.findById(playoffId)
            .orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
    var seasonId = playoff.getSeason().getId();
    var regularPhase = seasonPhaseService.findRegularPhase(seasonId);
    var standings = standingsService.calculateStandings(regularPhase.getId(), null);  // combined-view if GROUPS
    var topN = standings.stream().limit(numberOfTeamsForBracket).toList();
    // ... build seeds from topN, write via existing buildBracketOrder logic
    // ALSO: writes PhaseTeam rows on PLAYOFF phase per D-20 side-effect
}
```

**Key conventions to copy:**
- `@Transactional` on writes
- `EntityNotFoundException` / `IllegalStateException` for missing entities and bad state
- Reuse existing `buildBracketOrder` helper

**What's new vs analog:**
- New collaborators: `SeasonPhaseService` + `StandingsService`
- D-15: pull seeds from `standingsService.calculateStandings(regularPhaseId, null)` instead of M:N team pool
- D-20 side-effect: each seeded team becomes a `PhaseTeam` row of the PLAYOFF phase

---

### `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java` (MODIFY — phaseId + groupId)

**Analog (existing in-file pattern):** `generate(UUID seasonId, int numberOfRounds, boolean homeAndAway)` lines 34-68

**Existing transactional generation pattern** (lines 34-68):
```java
@Transactional
public void generate(UUID seasonId, int numberOfRounds, boolean homeAndAway) {
    var season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
    if (season.getFormat() == SeasonFormat.SWISS) {
        throw new IllegalArgumentException("Generator does not support Swiss format — use Swiss Rounds instead");
    }
    if (!matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId).isEmpty()) {
        throw new IllegalStateException("Season already has matchdays — delete them first");
    }
    var teams = season.getEligibleTeams();
    if (teams.size() < 2) {
        throw new IllegalStateException("Need at least 2 teams to generate matchdays");
    }
    List<List<int[]>> rounds = circleMethod(teams.size(), numberOfRounds);
    int sortIndex = 1;
    for (var round : rounds) {
        var matchday = matchdayRepository.save(new Matchday(season, "MD " + sortIndex, sortIndex));
        createMatchesForRound(matchday, round, teams, false);
        sortIndex++;
    }
    // ... homeAndAway loop
    log.info("Generated {} matchdays for season {}", sortIndex - 1, season.getName());
}
```

**Existing record DTO** (line 161):
```java
public record GeneratorFormData(Season season, int teamCount, int optimalRounds) {}
```
For D-16: planner picks shape — either replace `Season season` with `SeasonPhase phase`, OR carry both. RESEARCH §Open Question 4 + Pitfall A7 flag this as "verify with `playwright-cli` to avoid template breakage".

**Apply for D-16:**
```java
@Transactional
public void generate(UUID phaseId, UUID groupId, int numberOfRounds, boolean homeAndAway) {
    var phase = seasonPhaseService.findById(phaseId);  // throws EntityNotFoundException
    if (phase.getLayout() == PhaseLayout.LEAGUE && groupId != null) {
        throw new IllegalArgumentException("LEAGUE layout requires groupId=null");
    }
    if (phase.getLayout() == PhaseLayout.GROUPS && groupId == null) {
        throw new IllegalArgumentException("GROUPS layout requires groupId");
    }
    // ... use new finder matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId, groupId)
    //     and source teams from phaseTeamRepository.findByPhaseIdAndGroupId(phaseId, groupId)
}

@Deprecated  // remove in Phase 60
@Transactional
public void generate(UUID seasonId, int numberOfRounds, boolean homeAndAway) {
    generate(seasonPhaseService.findRegularPhase(seasonId).getId(), null, numberOfRounds, homeAndAway);
}
```

**Key conventions to copy:**
- `@Transactional` for generation
- `EntityNotFoundException` / `IllegalArgumentException` / `IllegalStateException` for the existing guard ladder
- Reuse `circleMethod` + `balanceHomeAway` + `createMatchesForRound` private helpers

**What's new vs analog:**
- New collaborator: `SeasonPhaseService`
- New repo finder dependency: `MatchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc`, `PhaseTeamRepository.findByPhaseIdAndGroupId`
- D-16: layout-aware groupId enforcement (LEAGUE → null required, GROUPS → non-null required)

---

### `src/main/java/org/ctc/domain/service/SwissPairingService.java` (MODIFY — per-group isolation)

**Analog (existing in-file pattern):** `generateNextRound(UUID seasonId)` lines 28-76

**Existing Swiss-round generation** (lines 28-76):
```java
@Transactional
public Matchday generateNextRound(UUID seasonId) {
    var season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
    if (season.getFormat() != SeasonFormat.SWISS) {
        throw new IllegalArgumentException("Season is not in Swiss format");
    }
    var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId);
    int currentRound = matchdays.size();
    // ... completeness check, pairing generation
    log.info("Generated Swiss round {} for season {}: {} pairings", roundNumber, season.getName(), pairings.size());
    return matchday;
}
```

**Recursive dependency** (line 26 + line 85): `SwissPairingService` injects `StandingsService` and calls `standingsService.calculateStandings(seasonId)`. RESEARCH Pitfall 6: refactor `StandingsService` BEFORE `SwissPairingService` — they cannot be in different parallel waves.

**Existing four method surface** to convert (D-17, all accept `(UUID phaseId, UUID groupId)`):
```java
public Matchday generateNextRound(UUID seasonId)         // line 29 → (phaseId, groupId)
public Set<UUID> getByeTeams(UUID seasonId)              // line 205 → (phaseId, groupId)
public int getCurrentRound(UUID seasonId)                // line 239 → (phaseId, groupId)
public boolean isCurrentRoundComplete(UUID seasonId)     // line 245 → (phaseId, groupId)
```

**Key conventions to copy:**
- `@Transactional` for `generateNextRound`; rest can stay non-transactional reads
- `EntityNotFoundException` for missing season/phase
- `IllegalStateException` for "Current round has incomplete races"
- Reuse `createSwissPairings`, `selectByeTeam`, `createPairingsFromOrder`, `createRaceWithMatch` private helpers verbatim
- `log.info("Generated Swiss round {} for phase {} group {}: {} pairings", ...)`

**What's new vs analog:**
- New collaborator: `SeasonPhaseService`
- New repo finder dependency: `MatchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc`, race/match repos similarly
- D-17/D-21: per-group bye lists, per-group current round, per-group completeness — no implicit cross-group sync
- D-01: `@Deprecated` overloads accepting `seasonId` for each of the four methods

---

### `src/main/java/org/ctc/domain/service/DriverRankingService.java` (MODIFY — phase + aggregation)

**Analog (existing in-file pattern):** `calculateRanking(UUID seasonId)` lines 25-54 + `calculateAlltimeRanking(...)` lines 81-112

**Existing ranking accumulation** (lines 25-54):
```java
@Transactional(readOnly = true)
public List<DriverRanking> calculateRanking(UUID seasonId) {
    List<RaceResult> results = raceResultRepository.findByRaceMatchdaySeasonId(seasonId);
    List<SeasonDriver> seasonDrivers = seasonDriverRepository.findBySeasonId(seasonId);
    Map<UUID, Team> driverTeamMap = seasonDrivers.stream()
            .collect(Collectors.toMap(
                    sd -> sd.getDriver().getId(),
                    SeasonDriver::getTeam,
                    (a, b) -> a));
    Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();
    for (RaceResult result : results) {
        UUID driverId = result.getDriver().getId();
        DriverRanking ranking = rankingMap.computeIfAbsent(driverId,
                id -> new DriverRanking(result.getDriver(), driverTeamMap.get(id)));
        ranking.addResult(result);
    }
    List<DriverRanking> rankings = new ArrayList<>(rankingMap.values());
    rankings.sort(Comparator
            .comparing(DriverRanking::getTotalPoints, Comparator.reverseOrder())
            .thenComparing(DriverRanking::getRacesCount)
            .thenComparing(DriverRanking::getAveragePoints, Comparator.reverseOrder()));
    log.debug("Calculated driver ranking for season {}: {} drivers", seasonId, rankings.size());
    return rankings;
}
```

**Existing alltime aggregation pattern** (lines 81-112) — RESEARCH §"Don't Hand-Roll" identifies this as the merge pattern to reuse for `aggregateAcrossPhases`:
```java
private List<DriverRanking> calculateAlltimeRanking(List<RaceResult> results,
                                                     List<SeasonDriver> allSeasonDrivers) {
    Map<UUID, Team> driverTeamMap = allSeasonDrivers.stream()
            .collect(Collectors.groupingBy(sd -> sd.getDriver().getId()))
            .entrySet().stream()
            .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().stream()
                            .max(Comparator.comparing(sd -> sd.getSeason().getName()))
                            .map(sd -> sd.getTeam().getParentOrSelf())
                            .orElse(null)));
    Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();
    for (RaceResult result : results) {
        // ... computeIfAbsent + addResult
    }
    // ... sort by totalPoints/racesCount/averagePoints
}
```

**Apply for D-07/D-09:**
```java
@Transactional(readOnly = true)
public List<DriverRanking> calculateRankingForPhase(UUID phaseId) {
    var phase = seasonPhaseService.findById(phaseId);
    List<RaceResult> regularResults = raceResultRepository.findByRaceMatchdayPhaseId(phaseId);
    List<RaceResult> playoffResults = raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(phaseId);
    List<RaceResult> all = new ArrayList<>();
    all.addAll(regularResults);
    all.addAll(playoffResults);
    // ... per-phase team attribution from PhaseTeam rows
    // ... existing accumulator + sort
}

@Transactional(readOnly = true)
public List<DriverRanking> aggregateAcrossPhases(List<UUID> phaseIds, UUID seasonId) {
    // D-08: REGULAR-phase team for season-wide attribution
    // D-10: RaceLineup-fallback for stand-ins without REGULAR-phase PhaseTeam
    // ... loop phaseIds, call calculateRankingForPhase, merge rankings
}

@Deprecated  // remove in Phase 60
@Transactional(readOnly = true)
public List<DriverRanking> calculateRanking(UUID seasonId) {
    var phaseIds = seasonPhaseService.findAllPhases(seasonId).stream()
            .map(SeasonPhase::getId).toList();
    return aggregateAcrossPhases(phaseIds, seasonId);
}
```

**Key conventions to copy:**
- `@Transactional(readOnly = true)` on every method
- Tiebreaker chain: `totalPoints (desc) → racesCount (asc) → averagePoints (desc)`
- `LinkedHashMap` for stable iteration
- Inner static class `DriverRanking` stays unchanged (D-09 says structurally unchanged)
- `log.debug("Calculated ... ranking for phase {}: {} drivers", phaseId, rankings.size())`

**What's new vs analog:**
- New collaborators: `SeasonPhaseService`, `PhaseTeamRepository`, `RaceLineupRepository` (for D-10 fallback)
- New finders: `RaceResultRepository.findByRaceMatchdayPhaseId`, `findByRacePlayoffMatchupRoundPlayoffPhaseId` (Pitfall 1: 5-step magic-name may need `@Query` JPQL fallback)
- D-07: PLAYOFF + PLACEMENT results now flow into ranking (BEHAVIOR CHANGE — surface in SUMMARY)
- D-08/D-10: REGULAR-phase team attribution + RaceLineup fallback for stand-ins

---

### `src/main/java/org/ctc/domain/service/SeasonManagementService.java` (MODIFY — D-18 guard + D-25 sync)

**Analog (existing in-file pattern):** `save(...)` lines 117-154 + `delete(...)` lines 156-162

**Existing save pattern** (lines 117-154):
```java
@Transactional
public Season save(UUID id, String name, int year, int number, ..., int legs,
                   Integer eventDurationMinutes, UUID raceScoringId, UUID matchScoringId) {
    var raceScoring = raceScoringRepository.findById(raceScoringId)
            .orElseThrow(() -> new EntityNotFoundException("RaceScoring", raceScoringId));
    var matchScoring = matchScoringRepository.findById(matchScoringId)
            .orElseThrow(() -> new EntityNotFoundException("MatchScoring", matchScoringId));
    Season season;
    if (id != null) {
        season = findById(id);
    } else {
        season = new Season();
    }
    // ... set fields ...
    season = seasonRepository.save(season);
    log.info(id != null ? "Updated season: {}" : "Created season: {}", season.getName());
    return season;
}
```

**Existing delete pattern (no guard today)** (lines 156-162):
```java
@Transactional
public String delete(UUID id) {
    var season = findById(id);
    seasonRepository.delete(season);
    log.info("Deleted season: {}", season.getName());
    return season.getName();
}
```

**Apply for D-18 (delete-guard) — use existsBy* per RESEARCH §"Don't Hand-Roll":**
```java
@Transactional
public String delete(UUID id) {
    var season = findById(id);
    // D-18 strict pre-check
    if (matchdayRepository.existsByPhaseSeasonId(id)
            || playoffRepository.existsByPhaseSeasonId(id)
            || phaseTeamRepository.existsByPhaseSeasonId(id)) {
        throw new BusinessRuleException("Season has active phases — clear matches/teams before deleting");
    }
    seasonRepository.delete(season);
    log.info("Deleted season: {}", season.getName());
    return season.getName();
}
```

**Apply for D-25 (auto-sync REGULAR phase after save):**
```java
@Transactional
public Season save(UUID id, ...) {
    // ... existing legacy field writes
    season = seasonRepository.save(season);
    // D-25: auto-sync onto REGULAR phase (find-or-create per Open Question 4)
    var regular = seasonPhaseService.findByType(season.getId(), PhaseType.REGULAR)
            .orElseGet(() -> seasonPhaseService.create(season.getId(),
                    PhaseType.REGULAR, PhaseLayout.LEAGUE, /*sortIndex*/ 0,
                    season.getName(), raceScoring, matchScoring, ...));
    regular.setFormat(season.getFormat());
    regular.setTotalRounds(season.getTotalRounds());
    regular.setLegs(season.getLegs());
    regular.setEventDurationMinutes(season.getEventDurationMinutes());
    regular.setStartDate(season.getStartDate());
    regular.setEndDate(season.getEndDate());
    regular.setRaceScoring(raceScoring);
    regular.setMatchScoring(matchScoring);
    seasonPhaseRepository.save(regular);
    return season;
}
```

**Key conventions to copy:**
- `@Transactional` boundary covers Season + REGULAR-phase write atomically
- `BusinessRuleException` (NOT `IllegalStateException`) for D-18 — flash error message via existing controller catch in `SeasonController.delete`
- `EntityNotFoundException` for missing scoring entities
- `log.info("...{}", season.getName())` for state changes

**What's new vs analog:**
- New collaborator: `SeasonPhaseService`
- New repo finder dependencies: `MatchdayRepository.existsByPhaseSeasonId`, `PlayoffRepository.existsByPhaseSeasonId`, `PhaseTeamRepository.existsByPhaseSeasonId` (Phase 58 additions, Assumption A2)
- D-18: BEHAVIOR CHANGE — strict delete-guard (today: blind cascade)
- D-25: write-through to REGULAR phase (find-or-create) — Pitfall 7 mitigation

---

### `src/main/java/org/ctc/domain/service/MatchdayService.java` (MODIFY — phase-shaped finders)

**Analog (existing in-file pattern):** `getMatchdayList(UUID seasonId)` lines 51-69

**Existing list pattern** (lines 51-69):
```java
public MatchdayListData getMatchdayList(UUID seasonId) {
    List<Matchday> matchdays;
    UUID selectedSeasonId = null;
    if (seasonId != null) {
        matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId);
        selectedSeasonId = seasonId;
    } else {
        var activeSeason = seasonRepository.findByActiveTrue();
        if (activeSeason.isPresent()) {
            matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(activeSeason.get().getId());
            selectedSeasonId = activeSeason.get().getId();
        } else {
            matchdays = matchdayRepository.findAll();
        }
    }
    return new MatchdayListData(matchdays, selectedSeasonId, seasonRepository.findAll());
}
```

**Apply for D-26 (parallel phaseId-shaped methods):**
```java
public List<Matchday> findByPhaseId(UUID phaseId) {
    return matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phaseId);
}

public List<Matchday> findByPhaseIdAndGroupId(UUID phaseId, UUID groupId) {
    return matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId, groupId);
}

@Deprecated  // remove in Phase 60
public List<Matchday> findBySeasonId(UUID seasonId) {
    var phaseIds = seasonPhaseService.findAllPhases(seasonId).stream()
            .map(SeasonPhase::getId).toList();
    return phaseIds.stream()
            .flatMap(pid -> findByPhaseId(pid).stream())
            .toList();
}
```

**Key conventions to copy:**
- Plain methods (no `@Transactional` annotation in this service today — keep as-is)
- Returns `List<Matchday>` directly (no return DTO needed for these helpers)
- Inject `SeasonPhaseService` to support the deprecated bridge

**What's new vs analog:**
- D-26: parallel phaseId/seasonId-shaped methods; controllers stay on seasonId
- New repo finders: `findByPhaseIdOrderBySortIndexAsc`, `findByPhaseIdAndGroupIdOrderBySortIndexAsc`

---

### `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` (MODIFY — caller-side update)

**Analog (existing in-file pattern):** `generateStandings(...)` lines 175-199 + `generateDriverRanking(...)` lines 201+

**Existing caller pattern** (line 180):
```java
var standings = standingsService.calculateStandings(season.getId());  // 5 call sites total
```
Call sites: lines 180, 257, 437, 555, 565 (alltime). Driver-ranking call site: line 205.

**Apply for D-23 (caller-side update — switch to phase-aware):**
```java
// Original:
var standings = standingsService.calculateStandings(season.getId());
// Phase 58 swap (per RESEARCH §Architecture diagram + D-23):
var regularPhase = seasonPhaseService.findRegularPhase(season.getId());
var standings = standingsService.calculateStandings(regularPhase.getId(), null);
```

For driver-ranking line 205:
```java
// Original:
var driverRanking = driverRankingService.calculateRanking(season.getId());
// Phase 58 swap:
var phaseIds = seasonPhaseService.findAllPhases(season.getId()).stream()
        .map(SeasonPhase::getId).toList();
var driverRanking = driverRankingService.aggregateAcrossPhases(phaseIds, season.getId());
```

For alltime (line 555 / 590) — D-09 says public API doesn't change, so these calls stay as-is internally now using the new aggregation.

**Key conventions to copy:**
- Inject `SeasonPhaseService` (added to `@RequiredArgsConstructor` final fields)
- Templates stay LEAGUE-shaped (no per-group rendering yet — Phase 60)

**What's new vs analog:**
- New collaborator: `SeasonPhaseService`
- D-23: 5-10 call sites switch from `seasonId` to `phaseId`

---

### Repositories — Custom finder additions

**Analog file:** `src/main/java/org/ctc/domain/repository/RaceResultRepository.java`

**Existing magic-name + EntityGraph pattern** (lines 11-29):
```java
public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

    @EntityGraph(attributePaths = {"driver"})
    List<RaceResult> findByRaceId(UUID raceId);

    @EntityGraph(attributePaths = {"driver", "race"})
    List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId);

    @EntityGraph(attributePaths = {"driver", "race"})
    List<RaceResult> findByRacePlayoffMatchupIsNull();

    @EntityGraph(attributePaths = {"driver", "race"})
    List<RaceResult> findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(List<UUID> seasonIds);
}
```

**`SeasonPhaseRepository` (lines 1-9 today — empty interface):**

Add per D-22:
```java
Optional<SeasonPhase> findBySeasonIdAndPhaseType(UUID seasonId, PhaseType phaseType);
List<SeasonPhase> findBySeasonIdOrderBySortIndex(UUID seasonId);
```
Confidence: HIGH — 2-3 hops, well within proven precedent depth.

**`SeasonPhaseGroupRepository`:**

Add:
```java
List<SeasonPhaseGroup> findByPhaseIdOrderBySortIndex(UUID phaseId);
```

**`PhaseTeamRepository`:**

Add:
```java
List<PhaseTeam> findByPhaseId(UUID phaseId);
List<PhaseTeam> findByPhaseIdAndGroupId(UUID phaseId, UUID groupId);  // groupId may be null → JPA derives IS NULL
boolean existsByPhaseSeasonId(UUID seasonId);  // for D-18 delete-guard
```

**`RaceResultRepository` additions:**

Add:
```java
@EntityGraph(attributePaths = {"driver", "race"})
List<RaceResult> findByRaceMatchdayPhaseId(UUID phaseId);  // 4 hops — verified precedent

@EntityGraph(attributePaths = {"driver", "race"})
List<RaceResult> findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId);  // 5 hops — Pitfall 1
```

**Pitfall 1 fallback** for the 5-hop finder (RESEARCH.md §Pitfall 1):
```java
@Query("SELECT rr FROM RaceResult rr JOIN rr.race r JOIN r.playoffMatchup pm " +
       "JOIN pm.round pr JOIN pr.playoff p WHERE p.phase.id = :phaseId")
List<RaceResult> findByPlayoffPhaseId(@Param("phaseId") UUID phaseId);
```
Plan defensively: ship the JPQL fallback ready in case the magic name fails at boot with `PropertyReferenceException`.

**`MatchdayRepository` additions:**

Existing pattern (lines 10-14):
```java
public interface MatchdayRepository extends JpaRepository<Matchday, UUID> {
    @EntityGraph(attributePaths = {"season"})
    List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId);
}
```
Add:
```java
@EntityGraph(attributePaths = {"season", "phase"})
List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId);

@EntityGraph(attributePaths = {"season", "phase", "group"})
List<Matchday> findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId);

boolean existsByPhaseSeasonId(UUID seasonId);  // D-18 delete-guard
```

**`MatchRepository` addition** (per RESEARCH §Architecture diagram):
```java
List<Match> findByMatchdayPhaseId(UUID phaseId);  // used by StandingsService
```

**`PlayoffRepository` addition** (D-18 delete-guard):
```java
boolean existsByPhaseSeasonId(UUID seasonId);
```

**Key conventions to copy:**
- Spring Data magic-name first; `@Query` JPQL fallback only on `PropertyReferenceException` at boot (Pitfall 1)
- `@EntityGraph(attributePaths = {...})` only when caller iterates lazy associations (D-24 pragmatic) — matches existing precedent for `RaceResultRepository.findByRaceMatchdaySeasonId`
- `Optional<T>` for single-row finders, `List<T>` for collections, `boolean` for `existsBy*`

**What's new vs analog:**
- New entity navigation paths through `Matchday.phase`, `Playoff.phase`, `RaceResult.race.matchday.phase`, `RaceResult.race.playoffMatchup.round.playoff.phase`
- D-22 magic-name preferred; Pitfall 1 reminds planner to ship JPQL fallback for the 5-hop finder

---

## Shared Patterns

### Spring Service Stereotype Combo
**Source:** Every existing service in `src/main/java/org/ctc/domain/service/` (verified: `SeasonManagementService` lines 17-20, `StandingsService` lines 17-19, `PlayoffService` lines 22-25, etc.)
**Apply to:** `SeasonPhaseService` (NEW) — every refactored service already has this combo

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class FooService {
    private final FooRepository fooRepository;
    private final BarService barService;
    // ... all collaborators as final fields, NO explicit constructor
}
```

### Transactional Boundaries
**Source:** All 17 existing domain services
**Apply to:** Every public method on every service touched in Phase 58

```java
@Transactional(readOnly = true)
public X read(...) { ... }

@Transactional
public X write(...) { ... }
```

Cross-service writes (D-19, D-25) keep a single `@Transactional` boundary — Spring proxies propagate via REQUIRED. Pitfall 2: do NOT call `@Transactional` methods on `this` — always go through an injected service bean.

### Exception Hierarchy
**Source:** `src/main/java/org/ctc/domain/exception/` — `EntityNotFoundException`, `BusinessRuleException`, `ValidationException`
**Apply to:** All Phase 58 services

| Condition | Exception | When |
|-----------|-----------|------|
| Entity lookup miss | `EntityNotFoundException(type, id)` | D-02 missing REGULAR phase, all `findById` misses |
| Business rule violation | `BusinessRuleException(message)` | D-14 duplicate phase, D-18 delete-guard, D-19 duplicate playoff |
| Bad caller arg | `IllegalArgumentException(message)` | D-16 layout/groupId mismatch, existing pattern preserved |
| Bad runtime state | `IllegalStateException(message)` | "Current round has incomplete races", "Maximum number of legs reached" — existing patterns preserved |

`GlobalExceptionHandler` maps `EntityNotFoundException` → 404, `BusinessRuleException` → 409, but controllers still catch `BusinessRuleException` for save/delete to render flash error messages (CONVENTIONS.md lines 240-247). The `SeasonController.delete` already has this catch — D-18 will surface as a flash error automatically.

### Logging Pattern
**Source:** Every service file (e.g., `SeasonManagementService` line 149, `PlayoffService` line 92)
**Apply to:** All Phase 58 services

```java
log.info("Created {} phase for season {}", type, season.getName());           // state change
log.info("Updated season: {}", season.getName());                              // state change
log.debug("Calculated standings for season {}: {} teams", seasonId, size);    // calculation
log.warn("Swiss pairing: forced rematch {} vs {}", a, b);                      // handled anomaly
```
Always parameterized `{}`, never string concatenation (CONVENTIONS.md lines 250-267).

### `@Deprecated` Bridge Pattern (D-01, D-03)
**Source:** RESEARCH.md §Pattern 1 + existing `PlayoffRepository.findByLinkedSeasonId` precedent
**Apply to:** Every refactored service's `seasonId`-shaped public method + `PlayoffService.addSeasonToPlayoff`/`removeSeasonFromPlayoff`

```java
@Deprecated  // remove in Phase 60 alongside UI cutover
@Transactional(readOnly = true)
public List<TeamStanding> calculateStandings(UUID seasonId) {
    return calculateStandings(seasonPhaseService.findRegularPhase(seasonId).getId(), null);
}
```
Always single-line delegate. Javadoc note `"remove in Phase 60"` (services callable from controllers) or `"remove in Phase 61"` (M:N methods, dropped with `playoff_seasons`).

### BDD Test Naming + Mockito Setup
**Source:** `SeasonManagementServiceTest`, `DriverRankingServiceTest`, `StandingsServiceTest`
**Apply to:** All Phase 58 service tests

```java
@ExtendWith(MockitoExtension.class)
class FooServiceTest {

    @Mock private DependencyA depA;
    @InjectMocks private FooService sut;

    @Test
    void givenContext_whenAction_thenExpectedResult() {
        // given
        // when
        // then
    }
}
```
- AssertJ `assertThat(...)` and `assertThatThrownBy(...)` (already imported in `SeasonManagementServiceTest`)
- Static factory helpers from `PhaseTestFixtures` for entity stubs
- Honour Open Question 2: new test methods follow each host file's existing style — Mockito stays Mockito, `@SpringBootTest` stays `@SpringBootTest`.

### Repository IT Test Pattern
**Source:** `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java`
**Apply to:** All three new `*RepositoryIT.java` files

```java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class FooRepositoryIT {
    @Autowired private FooRepository fooRepository;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void givenX_whenY_thenZ() {
        // given (use Phase58-Test- prefix on all entity names)
        // when (entityManager.flush() + clear() before reload)
        // then (assertThat(...))
    }
}
```

---

## No Analog Found

All 21 files have a strong existing analog. The only weak match is for `PhaseTestFixtures.java` because the codebase has no precedent for a reusable cross-test fixture utility class — existing tests use inline `@BeforeEach setUp()` builders or private helpers. The analog is the helper-method shape inside `SeasonPhaseEntityIntegrationTest.java` lines 200-214, generalized into a `final` utility class per D-11.

| File | Reason for weak match |
|------|----------------------|
| `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` | First reusable test-fixture utility; pattern is generalised from inline `@BeforeEach` helpers in 23 existing service tests. |

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/domain/service/` (17 files scanned, 8 deeply read)
- `src/main/java/org/ctc/domain/repository/` (6 files deeply read)
- `src/main/java/org/ctc/domain/model/` (3 files spot-checked: `SeasonPhase`, `SeasonPhaseGroup`, `PhaseTeam`)
- `src/main/java/org/ctc/domain/exception/` (2 files: `EntityNotFoundException`, `BusinessRuleException`)
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` (1 file, 5 call sites grep'd + verified)
- `src/test/java/org/ctc/domain/service/` (24 test files listed, 4 deeply read)
- `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` (deeply read)

**Files deeply read:** 14
**Files surfaced via Grep/Glob:** 8
**Total files scanned:** ~50
**Pattern extraction date:** 2026-04-27

**Critical pitfalls flagged from RESEARCH.md (planner must address):**
- Pitfall 1: 5-hop magic-name `findByRacePlayoffMatchupRoundPlayoffPhaseId` — ship `@Query` JPQL fallback ready
- Pitfall 4: `PlayoffService.addRaceToMatchup` line 305 — set `matchday.setPhase(playoff.getPhase())` to avoid double-counting
- Pitfall 6: `SwissPairingService` injects `StandingsService` — refactor `StandingsService` BEFORE `SwissPairingService`
- Pitfall 7: `SeasonManagementService.save` create-path must bootstrap REGULAR phase atomically to avoid 404 on view-standings for new seasons
- D-07 BEHAVIOR CHANGE: PLAYOFF results now flow into driver-ranking — flag in SUMMARY
- D-18 BEHAVIOR CHANGE: strict delete-guard replaces blind cascade — flag in SUMMARY
