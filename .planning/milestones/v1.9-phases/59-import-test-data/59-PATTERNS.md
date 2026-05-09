# Phase 59: Import & Test Data — Pattern Map

**Mapped:** 2026-04-28
**Files analyzed:** 9 (5 modify, 4 create) + tests
**Analogs found:** 9 / 9 (all touched files have a strong existing analog)

---

## Project Skills

`.claude/skills/` and `.agents/skills/` exist; only `claude-md-improver`, `playwright-cli`, and `gsd-auto-uat` are present. None of them contain rules that affect Java service / test patterns. CLAUDE.md (project root) is the binding spec — all patterns below honor it.

---

## File Classification

### Modified files

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` | service | request-response (preview) + transactional CRUD (execute) | self (extend in place) | exact (extension) |
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java` | service | CRUD | self (add `findUnique` method) | exact (extension) |
| `src/main/java/org/ctc/admin/TestDataService.java` | service (seed) | batch / transactional bulk-write | self (rewrite `seedSeasons` + `seedPlayoffs`) | exact (extension) |
| `src/main/java/org/ctc/admin/DevDataSeeder.java` | bootstrap (`CommandLineRunner`) | event-driven (startup) | self (no behavior change) | exact (passthrough) |
| `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` | repository | CRUD | self (add `findByPhaseIdAndTeamId`) | exact (extension) |

### Created files

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `TabWarning` record (in `DriverSheetImportService`) | DTO record | structured-return | sibling records `TabPreview`, `ErrorRow`, `NewDriverRow` (same file) | exact |
| `WarningType` enum (in `DriverSheetImportService`) | enum | value validation | sibling enum `ErrorReason` (same file) | exact |
| `seedPhaseTeams()` method (in `TestDataService`) | service helper (seed) | bulk write | `SeasonPhaseService.create` D-20 block + sibling `seedSeasonDrivers()` | role-match |
| `SeasonManagementServiceTest.findUnique...` | unit test | request-response | `SeasonPhaseServiceTest` `findRegularPhase` / `findByType` blocks | exact (mirror) |
| `DriverSheetImportServiceTest.groupResolution...` / `tabPattern...` | unit test | request-response | `DriverSheetImportServiceTest` (same file) season-auto-match section | exact (mirror) |
| `DriverSheetImportServiceIT` (NEW — does not yet exist) | integration test | preview→execute roundtrip | `TestDataServiceIntegrationTest` (`@SpringBootTest @ActiveProfiles("dev") @Transactional`) + `PhaseTeamRepositoryIT` (mockless persistence) | role-match |
| `TestDataServiceIT` smoke for consolidated 2023 | integration test | bulk-read smoke | `TestDataServiceIntegrationTest` (extend with phase / group / phase_team assertions) | exact (mirror) |

### IMPORTANT discrepancy with CONTEXT.md

CONTEXT.md `<canonical_refs>` and `<code_context>` claim:
- `PhaseTeamRepository.findByPhaseIdAndTeamId(UUID, UUID)` exists from Phase 58 D-22.
- `DriverSheetImportServiceIT` exists from v1.8.

**Reality (verified 2026-04-28):**
- `PhaseTeamRepository` (file lines 9–16) only declares `findByPhaseId`, `findByPhaseIdAndGroupId`, and `existsByPhaseSeasonId`. The `findByPhaseIdAndTeamId(UUID, UUID)` finder **does not exist** and must be added in Phase 59. It is a trivial one-liner; D-05 depends on it.
- `src/test/java/org/ctc/dataimport/` contains only Unit / Controller tests (no `DriverSheetImportServiceIT`). The Phase 59 IT must be created from scratch using the `TestDataServiceIntegrationTest` `@SpringBootTest @ActiveProfiles("dev") @Transactional` analog plus the `DriverSheetImportServiceTest` `setupSheetsStub` Mockito helper, but with `@MockBean GoogleSheetsService` instead of `@Mock`.

The planner must include both as concrete tasks (Plan 59-01 or 59-02 adds the repo finder; Plan 59-04 creates the IT from scratch).

---

## Pattern Assignments

### `src/main/java/org/ctc/domain/service/SeasonManagementService.java` (service, CRUD — extension)

**Analog:** self. Drop `findUnique(int year, int number)` next to `findById` near line 84.

**Imports already present** (lines 1–16):
```java
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
```

**Constructor / DI** (lines 18–37): the `SeasonRepository` field already exists — no new field needed. Class header pattern to follow:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonManagementService {
    private final SeasonRepository seasonRepository;
    // ...
}
```

**Read-only finder pattern** (sibling — `findById` lines 83–87, `findActiveSeason` lines 89–94):
```java
@Transactional(readOnly = true)
public Season findById(UUID id) {
    return seasonRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Season", id));
}

@Transactional(readOnly = true)
public Optional<Season> findByIdOptional(UUID id) {
    return seasonRepository.findById(id);
}
```

**BusinessRuleException pattern** (sibling — `delete` lines 203–216):
```java
if (matchdayRepository.existsByPhaseSeasonId(id)
        || playoffRepository.existsByPhaseSeasonId(id)
        || phaseTeamRepository.existsByPhaseSeasonId(id)) {
    throw new BusinessRuleException(
            "Season has active phases — clear matches/teams before deleting");
}
```

**Recommended `findUnique` shape (D-02 / D-18, derived from above patterns):**
```java
/**
 * Resolves a unique season for the given (year, number) tuple. Wraps
 * {@link SeasonRepository#findByYearAndNumber} (which returns {@link List}
 * because no DB UNIQUE constraint exists — see Phase 59 D-17 / D-19) and
 * enforces the contract:
 * <ul>
 *   <li>0 hits → {@link Optional#empty()}</li>
 *   <li>1 hit  → {@link Optional#of(Season)}</li>
 *   <li>&gt;1 hits → {@link BusinessRuleException} (D-02)</li>
 * </ul>
 */
@Transactional(readOnly = true)
public Optional<Season> findUnique(int year, int number) {
    var hits = seasonRepository.findByYearAndNumber(year, number);
    if (hits.size() > 1) {
        throw new BusinessRuleException(
                "Multiple seasons exist for (" + year + ", " + number
                + ") — consolidate them first or rename sheet tab to disambiguate");
    }
    return hits.stream().findFirst();
}
```

For the legacy-tab branch (D-01 with no `_S<n>`), the planner has two options (locked by D-02 / D-18, decided per `<specifics>`):
1. Caller-side: importer matches `^\d{4}$` → calls `findByYear(year)` directly, applies the same 0/1/many logic inline.
2. Service-side overload: add `findUnique(int year)` that internally calls `seasonRepository.findByYear(year)` and applies the same Optional + BusinessRuleException contract.

Recommendation: option 2 (overload), so the importer has a single helper to call.

---

### `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (service, request-response + CRUD — extension)

**Analog:** self. The file is small (437 lines) and already contains every shape the extension needs.

#### Constructor injection extension (D-28)

**Current** (lines 35–40):
```java
private final GoogleSheetsService googleSheetsService;
private final DriverMatchingService driverMatchingService;
private final SeasonRepository seasonRepository;
private final TeamRepository teamRepository;
private final SeasonDriverRepository seasonDriverRepository;
private final DriverRepository driverRepository;
```

**Add (D-28):**
```java
private final SeasonPhaseService seasonPhaseService;     // for findRegularPhase (Phase 58 D-02)
private final PhaseTeamRepository phaseTeamRepository;   // for findByPhaseIdAndTeamId (NEW finder, D-05)
private final SeasonManagementService seasonManagementService; // for findUnique(year, number) (D-02)
```

`@RequiredArgsConstructor` regenerates the constructor automatically — no manual constructor changes.

#### Tab-pattern union (D-01, replaces line 33)

**Current** (line 33):
```java
private static final Pattern YEAR_TAB_PATTERN = Pattern.compile("^\\d{4}$");
```

**Replace with (single regex, two named groups, per `<specifics>`):**
```java
// D-01: accept legacy ^\d{4}$ AND new ^\d{4}_S\d+$ patterns.
// year       — group(1), always present
// seasonNum  — group(2), null for legacy form
private static final Pattern YEAR_TAB_PATTERN = Pattern.compile("^(\\d{4})(?:_S(\\d+))?$");
```

The current filter call at line 59 (`.filter(name -> YEAR_TAB_PATTERN.matcher(name).matches())`) keeps working unchanged — the union pattern only loosens the predicate, never tightens it. The current sort at line 60 (`Comparator.comparingInt(Integer::parseInt)`) breaks when names contain `_S<n>` — replace with a custom comparator extracting the year via regex group(1).

#### Per-tab season resolution (D-01..D-04, replaces lines 200–216 in `buildTabPreview`)

**Current (legacy, lines 201–216):**
```java
int year = Integer.parseInt(tabName);
List<Season> seasons = seasonRepository.findByYear(year);
UUID suggestedSeasonId;
String ambiguousReason;
if (seasons.size() == 1) {
    suggestedSeasonId = seasons.get(0).getId();
    ambiguousReason = null;
} else if (seasons.isEmpty()) {
    suggestedSeasonId = null;
    ambiguousReason = "No season found for year " + year;
} else {
    suggestedSeasonId = null;
    ambiguousReason = "Multiple seasons for year " + year;
}
```

**Replacement skeleton (D-01..D-04 + D-18, exception caught and surfaced):**
```java
var matcher = YEAR_TAB_PATTERN.matcher(tabName);
matcher.matches(); // already proven by the upstream filter
int year = Integer.parseInt(matcher.group(1));
Integer number = matcher.group(2) == null ? null : Integer.parseInt(matcher.group(2));

UUID suggestedSeasonId;
String ambiguousReason;
try {
    Optional<Season> resolved = (number != null)
            ? seasonManagementService.findUnique(year, number)        // D-02
            : seasonManagementService.findUnique(year);                // legacy fallback (D-01)
    if (resolved.isPresent()) {
        suggestedSeasonId = resolved.get().getId();
        ambiguousReason = null;
    } else {
        suggestedSeasonId = null;
        ambiguousReason = (number != null)
                ? "No season found for (" + year + ", " + number + ")"
                : "No season found for year " + year;
    }
} catch (BusinessRuleException ex) {
    // D-18: multi-hit → surface as ambiguousReason, not as 5xx
    suggestedSeasonId = null;
    ambiguousReason = ex.getMessage();
}
```

#### Group resolution + warning emission (D-05, D-06 — added inside `buildTabPreview`)

**Pattern to follow** (sibling — line 254 already does the team lookup):
```java
Optional<Team> teamOpt = teamRepository.findByShortName(rawTeamCode);
```

**Add (per-tab cache for REGULAR phase id; per-row group lookup):**
```java
// D-28 recommendation: resolve REGULAR phase once per tab, cache locally
SeasonPhase regularPhase = null;
if (suggestedSeasonId != null) {
    try {
        regularPhase = seasonPhaseService.findRegularPhase(suggestedSeasonId); // Phase 58 D-02
    } catch (EntityNotFoundException ex) {
        // No REGULAR phase — leave null, group resolution will skip
        log.debug("No REGULAR phase for season {}; group resolution disabled", suggestedSeasonId);
    }
}

Set<String> warnedTeams = new LinkedHashSet<>(); // dedup key per D-06

// inside the row loop, AFTER `Team team = teamOpt.get();` (line 259):
String resolvedGroupName = null;
if (regularPhase != null) {
    var pt = phaseTeamRepository
            .findByPhaseIdAndTeamId(regularPhase.getId(), team.getId());  // NEW finder
    if (pt.isPresent() && pt.get().getGroup() != null) {
        resolvedGroupName = pt.get().getGroup().getName();
    } else if (warnedTeams.add(rawTeamCode)) {
        // D-06: emit one warning per team (not per row)
        warnings.add(new TabWarning(
                WarningType.TEAM_NOT_IN_REGULAR_PHASE,
                rawTeamCode,
                "Team " + rawTeamCode + " has no PhaseTeam in REGULAR phase of target season"));
    }
}
```

The repository finder must be added (it does not yet exist — see "IMPORTANT discrepancy" above):

```java
// PhaseTeamRepository.java — Spring Data magic naming, mirror existing siblings
Optional<PhaseTeam> findByPhaseIdAndTeamId(UUID phaseId, UUID teamId);
```

Mirror the existing patterns (lines 11–15):
```java
List<PhaseTeam> findByPhaseId(UUID phaseId);
List<PhaseTeam> findByPhaseIdAndGroupId(UUID phaseId, UUID groupId);
boolean existsByPhaseSeasonId(UUID seasonId);
```

#### `TabWarning` record + `WarningType` enum (D-06 — new sibling records)

**Analog (sibling records, lines 341–411):** identical Lombok-free `record` declaration style; identical inner-`enum` style.

**Pattern from `ErrorRow` + `ErrorReason` (lines 390–411):**
```java
public record ErrorRow(
        String psnId,
        String teamCode,
        ErrorReason reason
) {}

public enum ErrorReason {
    BLANK_PSN_ID("PSN ID is blank"),
    BLANK_TEAM_CODE("Team short code is blank"),
    UNKNOWN_TEAM_CODE("Team short code not found"),
    DUPLICATE_IN_TAB("PSN already listed earlier in this tab");

    private final String message;
    ErrorReason(String message) { this.message = message; }
    public String message() { return message; }
}
```

**Apply (D-06):**
```java
public record TabWarning(
        WarningType type,
        String teamShortName,
        String message
) {}

public enum WarningType {
    TEAM_NOT_IN_REGULAR_PHASE("Team has no PhaseTeam in REGULAR phase");
    // future warning kinds appended here without breaking API

    private final String message;
    WarningType(String message) { this.message = message; }
    public String message() { return message; }
}
```

#### Row-record extension (D-08, lines 358–388)

**Current shapes (5 rows touched, `ErrorRow` excluded per D-08):**
```java
public record NewDriverRow(String psnId, String teamShortName) {}
public record NewAssignmentRow(String psnId, UUID existingDriverId, String teamShortName) {}
public record ConflictRow(String psnId, UUID existingDriverId, UUID existingSeasonDriverId,
                          String existingTeamShortName, String sheetTeamShortName) {}
public record FuzzySuggestionRow(String psnId, UUID suggestedDriverId, String suggestedPsnId,
                                 String suggestedNickname, double similarity, String teamShortName) {}
public record UnchangedRow(String psnId, UUID existingDriverId, UUID existingSeasonDriverId,
                           String teamShortName) {}
```

**Apply (D-08): append one trailing `String resolvedGroupName` field to each.**

`TabPreview` (lines 345–356) gains `List<TabWarning> warnings`:
```java
public record TabPreview(
        String tabName,
        int year,
        Integer number,                       // NEW: D-01 (null for legacy ^\d{4}$ tabs)
        UUID suggestedSeasonId,
        String ambiguousReason,
        List<TabWarning> warnings,            // NEW: D-06
        List<NewDriverRow> newDrivers,
        List<NewAssignmentRow> newAssignments,
        List<ConflictRow> conflicts,
        List<FuzzySuggestionRow> fuzzySuggestions,
        List<UnchangedRow> unchanged,
        List<ErrorRow> errors
) {}
```

Adding `Integer number` is not strictly required by D-01–D-08 but is highly recommended by `<specifics>` so the controller / template (Phase 60) can render `2025_S2` labels without re-parsing the tab name. The planner may defer if it minimizes Phase 59 churn — note in the plan.

#### Execute path (D-07, D-16): no changes required

The execute path (lines 83–198) writes only `SeasonDriver` and never mutates `PhaseTeam`. D-16 explicitly confirms this is the desired behavior — the warning is informational, not blocking. The execute branch handles ambiguous (`suggestedSeasonId == null`) tabs by skipping via `addSkippedTab(tab.year())` (lines 99–102) — keep verbatim.

---

### `src/main/java/org/ctc/admin/TestDataService.java` (service, batch — rewrite of seedSeasons + seedPlayoffs)

**Analog:** self.

#### Existing seed pattern for entity-graph creation (sibling `seedRaceLineups` lines 661–738)

The `seedRaceLineups` block already shows the manual `new Entity(...)` + `repo.save(entity)` pattern with subsequent linking (no service calls — direct persistence). This is the mandated path per D-27 (no `seasonPhaseService.create(...)` from the seed):

```java
// pattern excerpt: direct entity construction (Phase 56 D-01 cascade-save via Season.phases)
var testSeason1 = createSeason("Test-Season 2026", 2026, 99, "Test", scorings);
List.of(testAlpha, testBravo, testBravo1, testBravo2).forEach(testSeason1::addTeam);
seasonRepository.save(testSeason1);
```

Do likewise for the consolidated 2023 GROUPS season:

```java
// D-09: ONE season for 2023, GROUPS layout
var s1 = createSeason("Season 2023", 2023, 1, "Round Robin — two groups", scorings);
s1.setFormat(SeasonFormat.ROUND_ROBIN);
List.of(/* all 12 teams */).forEach(s1::addTeam);

// D-09: REGULAR phase (GROUPS layout)
var regularPhase = new SeasonPhase(s1, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
regularPhase.setRaceScoring(scorings.raceScoring());
regularPhase.setMatchScoring(scorings.matchScoring());
regularPhase.setFormat(SeasonFormat.ROUND_ROBIN);
regularPhase.setLegs(2);
s1.getPhases().add(regularPhase); // cascade-save via Season.phases (Phase 56 D-01)

// D-13: two SeasonPhaseGroup rows, sortIndex 0 / 1
var groupA = new SeasonPhaseGroup(regularPhase, "Group A", 0);
var groupB = new SeasonPhaseGroup(regularPhase, "Group B", 1);
regularPhase.getGroups().add(groupA);
regularPhase.getGroups().add(groupB);

s1 = seasonRepository.save(s1); // cascade-saves phase + groups (Season.phases CascadeType.ALL)
```

**Note**: phase / group ids materialize after the cascade save. To get fresh references with ids, re-read via `seasonPhaseRepository.findBySeasonIdAndPhaseType(s1.getId(), PhaseType.REGULAR)` or pull from `s1.getPhases().get(0).getGroups()` (the in-memory collection mirrors persisted state after `save`).

#### `seedPhaseTeams()` — new helper (D-12)

Mirror the existing `assignSeasonDrivers` / `seedSeasonDrivers` style (lines 315–404). Reuse the local `findParent` / `findSub` helper-lambdas from line 320 onwards. Suggested shape:

```java
private void seedPhaseTeams() {
    var allSeasons = seasonRepository.findAll();
    var allTeams = teamRepository.findAll();

    java.util.function.Function<String, Team> findParent = shortName ->
            allTeams.stream()
                    .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                    .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));
    java.util.function.Function<String, Team> findSub = shortName ->
            allTeams.stream()
                    .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                    .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

    // 2023 GROUPS-layout — 12 PhaseTeam rows split across two groups (D-13)
    var s1 = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getNumber() == 1)
            .findFirst().orElseThrow();
    var s1Regular = s1.getPhases().stream()
            .filter(p -> p.getPhaseType() == PhaseType.REGULAR).findFirst().orElseThrow();
    var groupA = s1Regular.getGroups().stream().filter(g -> g.getName().equals("Group A"))
            .findFirst().orElseThrow();
    var groupB = s1Regular.getGroups().stream().filter(g -> g.getName().equals("Group B"))
            .findFirst().orElseThrow();

    // Group A: ADR/ICL/SVT/NFR/HMS/VRX-A
    for (var team : List.of(findParent.apply("ADR"), findParent.apply("ICL"),
                            findParent.apply("SVT"), findParent.apply("NFR"),
                            findParent.apply("HMS"), findSub.apply("VRX A"))) {
        var pt = new PhaseTeam(s1Regular, team);
        pt.setGroup(groupA);
        phaseTeamRepository.save(pt);
    }
    // Group B: EGP/PWR/VRX-B/SGM-B/SGM-S/TBR-R
    for (var team : List.of(findParent.apply("EGP"), findParent.apply("PWR"),
                            findSub.apply("VRX B"), findSub.apply("SGM B"),
                            findSub.apply("SGM S"), findSub.apply("TBR R"))) {
        var pt = new PhaseTeam(s1Regular, team);
        pt.setGroup(groupB);
        phaseTeamRepository.save(pt);
    }

    // 2024 / 2026 LEAGUE — group_id NULL (D-12)
    seedLeaguePhaseTeams(allSeasons, 2024, 2);
    seedLeaguePhaseTeams(allSeasons, 2026, 4);
}

private void seedLeaguePhaseTeams(List<Season> seasons, int year, int number) {
    var s = seasons.stream()
            .filter(x -> x.getYear() == year && x.getNumber() == number)
            .findFirst().orElseThrow();
    var regular = s.getPhases().stream()
            .filter(p -> p.getPhaseType() == PhaseType.REGULAR).findFirst().orElseThrow();
    for (var st : s.getSeasonTeams()) {
        phaseTeamRepository.save(new PhaseTeam(regular, st.getTeam())); // group=null per D-12
    }
}
```

Both 2024 (SWISS) and 2026 (LEAGUE) seasons get their REGULAR phase auto-created by `SeasonManagementService.save` (Phase 58 D-25). However, `TestDataService.seedSeasons` calls `seasonRepository.save(s2)` directly, **not** `SeasonManagementService.save` — so the auto-sync does not fire from the seed path. The planner has two options:
1. Have the seed call `SeasonManagementService.save` for each season (heavy refactor of the rating-set blocks at lines 175–248 — likely incurs re-reading rating values).
2. Have the seed manually attach a REGULAR phase per `<specifics>` (consistent with the GROUPS-2023 path above) and let `seedPhaseTeams()` populate rosters.

Recommendation: option 2 (fully self-contained seed, mirrors the GROUPS-2023 path). D-27 anyway forbids `seasonPhaseService.create(...)` calls from the seed, so this is consistent.

#### `seedPlayoffs` rewire (D-14, lines 740–920)

**Pattern: DELETE the legacy `playoff2023.getSeasons().add(s1b)` line + the `s1aSorted/s1bSorted` manual sort blocks.** With the consolidated 2023 season, top-2 from each group falls out of `PlayoffSeedingService.autoSeedBracket` (Phase 58 D-15 — see `PlayoffSeedingService.java` lines 144–169 above).

**Current path to remove (lines 810–846):**
```java
var s1aSorted = s1aTeamScores.entrySet().stream()
        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
        .map(Map.Entry::getKey).toList();
// ... manual matchup wiring ...
playoff2023.getSeasons().add(s1b);  // <-- legacy M:N hack, drop entirely
```

**Replacement (uses Phase 58 services):**
```java
// D-14: 2023 PLAYOFF — top-2 per group come from PlayoffSeedingService.autoSeedBracket,
// which calls StandingsService on the REGULAR phase (combined-view per Phase 58 D-04).
var playoff2023 = playoffService.createPlayoff(s1.getId(), "2023 Playoffs", 4);
playoffSeedingService.autoSeedBracket(playoff2023.getId());
```

The current `s2` (2024) playoff path stays virtually identical — just no manual sort needed, replace with `autoSeedBracket`. Inject `PlayoffSeedingService` into `TestDataService` (mirror the existing `PlayoffService` injection at line 41).

#### `playoff_seasons` cleanup

`Playoff.getSeasons().add(s1b)` (line 830) is the only write to the M:N bridge in this file. Removing it is the only seed-side change required for D-14 / `<specifics>` "M:N `playoff_seasons` write becomes obsolete". The bridge stays functional for legacy code paths until Phase 61 MIGR-06.

---

### `src/main/java/org/ctc/admin/DevDataSeeder.java` (bootstrap, event-driven — passthrough)

**Analog:** self. No behavior change. Already minimal (33 lines):

```java
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {
    private final TestDataService testDataService;
    private final SiteGeneratorService siteGeneratorService;

    @Override
    public void run(String... args) {
        testDataService.seed();
        generateSite();
    }
    // ...
}
```

The DATA-02 success criterion (GROUPS-layout REGULAR phase + named groups + PLAYOFF phase) is satisfied entirely by the rewritten `TestDataService.seed()` — no changes needed here. Plan should explicitly call this out as a no-op file.

---

## Test Pattern Assignments

### Unit: `SeasonManagementServiceTest` — `findUnique` branches

**Analog:** `SeasonPhaseServiceTest.findRegularPhase` / `findByType` blocks (`src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java`, lines 41–105) — exact mirror.

**BusinessRuleException pattern** (lines 132–145):
```java
@Test
void givenExistingRegularPhase_whenCreateRegular_thenThrowsBusinessRuleException() {
    // given
    var season = buildSeason("Phase58-Test-Season-4");
    var existingPhase = PhaseTestFixtures.regularPhase(season, ...);
    when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR))
            .thenReturn(Optional.of(existingPhase));

    // when / then
    assertThatThrownBy(() -> seasonPhaseService.create(season.getId(), PhaseType.REGULAR, ...))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Season already has REGULAR phase");
}
```

**Apply to `findUnique` (3 cases per D-02):**
```java
@ExtendWith(MockitoExtension.class)
class SeasonManagementServiceTest {
    @Mock private SeasonRepository seasonRepository;
    // ... existing 12 mocks ...
    @InjectMocks private SeasonManagementService service;

    @Test
    void givenNoSeason_whenFindUnique_thenReturnsEmpty() {
        // given
        when(seasonRepository.findByYearAndNumber(2025, 2)).thenReturn(List.of());
        // when
        var result = service.findUnique(2025, 2);
        // then
        assertThat(result).isEmpty();
    }

    @Test
    void givenExactlyOneSeason_whenFindUnique_thenReturnsOptionalOf() {
        // given
        var season = createSeason("Phase59-Test-S2025-2");
        when(seasonRepository.findByYearAndNumber(2025, 2)).thenReturn(List.of(season));
        // when
        var result = service.findUnique(2025, 2);
        // then
        assertThat(result).contains(season);
    }

    @Test
    void givenMultipleSeasons_whenFindUnique_thenThrowsBusinessRule() {
        // given
        when(seasonRepository.findByYearAndNumber(2023, 1))
                .thenReturn(List.of(createSeason("S1"), createSeason("S2")));
        // when / then
        assertThatThrownBy(() -> service.findUnique(2023, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Multiple seasons exist for (2023, 1)");
    }
}
```

The existing `SeasonManagementServiceTest` (lines 26–58) already declares all 13 `@Mock` fields needed — append the new tests to it; do not create a separate file.

---

### Unit: `DriverSheetImportServiceTest` — group-resolution + warnings + tab-pattern

**Analog:** self (`src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java`).

**Helper to extend** (lines 95–110):
```java
private void setupSheetsStub(String url, Map<String, List<List<Object>>> tabsToRows) throws IOException {
    when(googleSheetsService.extractSpreadsheetId(url)).thenReturn(SPREADSHEET_ID);
    when(googleSheetsService.getSheetNames(SPREADSHEET_ID)).thenReturn(new ArrayList<>(tabsToRows.keySet()));
    for (var entry : tabsToRows.entrySet()) {
        lenient().when(googleSheetsService.readRangeFromSheet(SPREADSHEET_ID, entry.getKey(), "A:C"))
                .thenReturn(entry.getValue());
    }
}

private List<List<Object>> oneDataRow(String psn, String name, String teamCode) {
    return List.of(List.of("PSN ID", "Name", "Team"), List.of(psn, name, teamCode));
}
```

**Add new `@Mock` fields** (mirror existing pattern at lines 35–44):
```java
@Mock private SeasonPhaseService seasonPhaseService;
@Mock private PhaseTeamRepository phaseTeamRepository;
@Mock private SeasonManagementService seasonManagementService;
```

**Tab-pattern test** (mirror season-auto-match block, lines 178–214):
```java
@Test
void givenLegacyTab_whenPreview_thenSingleSeasonAutoResolved() throws IOException {
    setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("psn", "X", "AHR")));
    when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
    // ...
}

@Test
void givenNumberedTab_whenPreview_thenResolvedByYearAndNumber() throws IOException {
    setupSheetsStub(SHEET_URL, Map.of("2025_S2", oneDataRow("psn", "X", "AHR")));
    when(seasonManagementService.findUnique(2025, 2)).thenReturn(Optional.of(season2025s2));
    // ...
}

@Test
void givenAmbiguousLegacyTab_whenPreview_thenSurfacesBusinessRuleMessage() throws IOException {
    setupSheetsStub(SHEET_URL, Map.of("2023", oneDataRow("psn", "X", "AHR")));
    when(seasonManagementService.findUnique(2023))
            .thenThrow(new BusinessRuleException("Multiple seasons exist for (2023, 1) — ..."));
    // when
    var preview = driverSheetImportService.preview(SHEET_URL);
    // then
    var tab = preview.tabPreviews().get(0);
    assertThat(tab.suggestedSeasonId()).isNull();
    assertThat(tab.ambiguousReason()).contains("Multiple seasons exist for (2023, 1)");
}
```

**Group-resolution test** (D-05):
```java
@Test
void givenTeamInGroupA_whenPreview_thenResolvedGroupNameSet() throws IOException {
    setupSheetsStub(SHEET_URL, Map.of("2023_S1", oneDataRow("psn", "X", "ADR")));
    when(seasonManagementService.findUnique(2023, 1)).thenReturn(Optional.of(season2023));
    when(seasonPhaseService.findRegularPhase(season2023.getId())).thenReturn(regularPhase);
    when(teamRepository.findByShortName("ADR")).thenReturn(Optional.of(teamAdr));
    when(phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), teamAdr.getId()))
            .thenReturn(Optional.of(phaseTeamGroupA));
    when(driverMatchingService.findDriver("psn")).thenReturn(MatchResult.noMatch("psn"));

    var preview = driverSheetImportService.preview(SHEET_URL);
    var tab = preview.tabPreviews().get(0);
    assertThat(tab.newDrivers()).hasSize(1);
    assertThat(tab.newDrivers().get(0).resolvedGroupName()).isEqualTo("Group A");
}
```

**Warning test** (D-06 — dedup per team):
```java
@Test
void givenTwoRowsSameMissingTeam_whenPreview_thenSingleWarningEmitted() throws IOException {
    Map<String, List<List<Object>>> tabs = Map.of("2023_S1", List.of(
            List.of("PSN ID", "Name", "Team"),
            List.of("psn1", "X", "FOO"),
            List.of("psn2", "Y", "FOO")));
    // ... setup so phaseTeamRepository.findByPhaseIdAndTeamId returns Optional.empty() ...

    var preview = driverSheetImportService.preview(SHEET_URL);
    var tab = preview.tabPreviews().get(0);
    assertThat(tab.warnings()).hasSize(1);
    assertThat(tab.warnings().get(0).type()).isEqualTo(WarningType.TEAM_NOT_IN_REGULAR_PHASE);
    assertThat(tab.warnings().get(0).teamShortName()).isEqualTo("FOO");
}
```

**`PhaseTestFixtures` reuse** for setup helpers (line 40 onwards already provides `regularPhase(...)`, `groupsRegularPhase(...)`, `assignTeam(...)`):
```java
var s1 = buildSeason("Phase59-Test-S2023-1");
var regular = PhaseTestFixtures.groupsRegularPhase(s1, rs, ms, "Group A", "Group B");
var groupA = regular.getGroups().get(0);
var phaseTeamGroupA = PhaseTestFixtures.assignTeam(regular, teamAdr, groupA);
```

---

### Integration: `DriverSheetImportServiceIT` (NEW)

**Analogs:**
- `TestDataServiceIntegrationTest` (lines 18–52) for the `@SpringBootTest @ActiveProfiles("dev") @Transactional` shape.
- `DriverSheetImportServiceTest.setupSheetsStub` (lines 95–110) for the Sheets-mocking helper, but using `@MockBean` instead of `@Mock`.
- `PhaseTeamRepositoryIT` (lines 25–52) for the `@PersistenceContext EntityManager` flush/clear pattern.

**Skeleton (D-21 / D-22):**
```java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DriverSheetImportServiceIT {

    @MockBean
    private GoogleSheetsService googleSheetsService;             // D-22

    @Autowired private DriverSheetImportService driverSheetImportService;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private SeasonPhaseRepository seasonPhaseRepository;
    @Autowired private PhaseTeamRepository phaseTeamRepository;
    @Autowired private TeamRepository teamRepository;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void given2023S1Tab_whenPreview_thenGroupNameResolvedFromConsolidatedSeason() throws Exception {
        // given — TestDataService.seed() runs via DevDataSeeder (Phase 59 produces consolidated 2023)
        when(googleSheetsService.extractSpreadsheetId("url")).thenReturn("sid");
        when(googleSheetsService.getSheetNames("sid")).thenReturn(List.of("2023_S1"));
        when(googleSheetsService.readRangeFromSheet("sid", "2023_S1", "A:C")).thenReturn(List.of(
                List.of("PSN ID", "Name", "Team"),
                List.of("ADR_Driver01", "Sample", "ADR")));
        // when
        var preview = driverSheetImportService.preview("url");
        // then
        var tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNotNull();
        assertThat(tab.warnings()).isEmpty();
        // ADR is in Group A of the consolidated 2023 season
        assertThat(tab.unchanged().stream().findFirst().map(u -> u.resolvedGroupName()))
                .contains("Group A");
    }

    @Test
    void givenAmbiguousLegacyDataset_whenPreview_thenSurfacesBusinessRuleAsAmbiguousReason() {
        // Insert a duplicate (year=2023, number=1) season manually to simulate legacy bestand
        // Assert preview tab carries ambiguousReason starting with "Multiple seasons exist for (2023, 1)"
    }
}
```

---

### Integration: `TestDataServiceIT` smoke (D-23)

**Analog:** `TestDataServiceIntegrationTest` (existing, lines 18–300). Append new tests to that file (do not create a sibling).

**Pattern from existing tests** (lines 92–108):
```java
@Test
void givenDevSeed_whenStarted_thenS1GroupAHasSixTeams() {
    var season = findSeason(2023, "Group A");
    assertThat(season.getSeasonTeams()).hasSize(6);
}
```

**New tests (D-23):**
```java
@Test
void givenDevSeed_whenStarted_thenConsolidated2023HasOneRegularGroupsPhase() {
    var season = findSeason(2023, 1); // existing helper at line 47
    var phases = season.getPhases().stream()
            .filter(p -> p.getPhaseType() == PhaseType.REGULAR).toList();
    assertThat(phases).hasSize(1);
    assertThat(phases.get(0).getLayout()).isEqualTo(PhaseLayout.GROUPS);
}

@Test
void givenDevSeed_whenStarted_thenConsolidated2023HasTwoNamedGroups() {
    var season = findSeason(2023, 1);
    var regular = season.getPhases().stream()
            .filter(p -> p.getPhaseType() == PhaseType.REGULAR).findFirst().orElseThrow();
    var groupNames = regular.getGroups().stream().map(SeasonPhaseGroup::getName).toList();
    assertThat(groupNames).containsExactly("Group A", "Group B");
}

@Test
void givenDevSeed_whenStarted_thenConsolidated2023HasTwelvePhaseTeams() {
    var season = findSeason(2023, 1);
    var regular = season.getPhases().stream()
            .filter(p -> p.getPhaseType() == PhaseType.REGULAR).findFirst().orElseThrow();
    assertThat(phaseTeamRepository.findByPhaseId(regular.getId())).hasSize(12);
}

@Test
void givenDevSeed_whenStarted_thenLeaguePhaseTeamsHaveNullGroup() {
    var s2026 = findSeason(2026, 4);
    var regular = s2026.getPhases().stream()
            .filter(p -> p.getPhaseType() == PhaseType.REGULAR).findFirst().orElseThrow();
    var pts = phaseTeamRepository.findByPhaseId(regular.getId());
    assertThat(pts).isNotEmpty();
    assertThat(pts).allMatch(pt -> pt.getGroup() == null);
}
```

The existing helper `findSeason(int year, int number)` (lines 47–52) already supports the consolidated 2023 case. The legacy `findSeason(2023, "Group A")` calls **break** after the rewrite — they must be removed or rewritten to look up via group name on the REGULAR phase. **Planner action:** the existing tests at lines 57–143 that use `findSeason(2023, "Group A")` / `(2023, "Group B")` need to be either deleted (preferred — they are pre-Phase-56 invariants) or rewritten to reflect the consolidated layout. Document this as a regression-fix item in the relevant plan.

---

## Shared Patterns

### 1. Service stereotype combo (CLAUDE.md Conventions)

**Source:** every service in `src/main/java/org/ctc/domain/service/` and `src/main/java/org/ctc/admin/`.

**Apply to:** `SeasonManagementService` extension, `DriverSheetImportService` extension, `TestDataService` rewrite. No new service classes are created in Phase 59.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class XxxService {
    private final XxxRepository xxxRepository;
    // log.info() for state changes, log.debug() for calculations
    // parameterized {} format only — never string concat
}
```

### 2. BusinessRuleException for business-rule violations (CLAUDE.md Conventions)

**Source:** `org.ctc.domain.exception.BusinessRuleException` (8 lines). Used by `SeasonManagementService.delete` (line 210), `SeasonPhaseService.create` (line 108), `PlayoffService.createPlayoff` (line 55).

**Apply to:** D-02 / D-18 (`findUnique` multi-hit), surfaced by importer as `ambiguousReason`.

```java
throw new BusinessRuleException("...human-readable English message...");
```

### 3. Spring Data magic naming for repositories (Phase 58 D-22 precedent)

**Source:** `PhaseTeamRepository` (lines 11–15) — pure interface declarations, no `@Query`.

**Apply to:** new `findByPhaseIdAndTeamId(UUID, UUID)` finder.

### 4. `record` for service-return data (Phase 54 precedent)

**Source:** `DriverSheetImportService` records (lines 341–411), `SeasonManagementService.SeasonGroupOption` (line 50).

**Apply to:** `TabWarning` record + `WarningType` enum (D-06).

### 5. Test-data isolation via prefix (CLAUDE.md "Isolate Test Data Completely")

**Source:** `PhaseTeamRepositoryIT` uses `Phase58-Test-PT-` prefix (line 22 doc-comment); `TestDataService.seedRaceLineups` uses `T-ALF`, `T-BRV` (lines 668–671); `PhaseTestFixtures` uses `Phase58-Test-` (line 23 doc-comment).

**Apply to:** all new Phase 59 test seasons / teams. Recommendation: `Phase59-Test-`, `T-ALF` / `T-BRV` for E2E carry-overs.

### 6. `@MockBean` for `GoogleSheetsService` in IT tests (D-22, v1.8 pattern)

**Source:** none verified — CONTEXT.md claimed v1.8 had a `DriverSheetImportServiceIT` with this pattern, but no such file exists. Use the generic `@SpringBootTest @MockBean` pattern from Spring Boot docs combined with the `setupSheetsStub` helper transplanted from `DriverSheetImportServiceTest`.

**Apply to:** every Phase 59 IT that touches `DriverSheetImportService`.

### 7. Given/When/Then test naming (CLAUDE.md Development Approach)

**Source:** `SeasonPhaseServiceTest` lines 45–127 (e.g. `givenSeasonWithRegularPhase_whenFindRegularPhase_thenReturnsPhase`).

**Apply to:** every new test method. Body uses `// given` / `// when` / `// then` comments; for exception tests use `// when / then` combined with `assertThatThrownBy`.

### 8. JaCoCo 82% line-coverage gate (CLAUDE.md Constraints)

**Source:** `pom.xml` enforce-mode (Phase 58 closed at 86.78%).

**Apply to:** all Phase 59 new code (importer additions, `findUnique`, `seedPhaseTeams`). Plan-04 (or `D-26` Plan-59-04) verifies via `./mvnw verify` that the gate stays green.

### 9. `Season.phases` cascade-save (Phase 56 D-01)

**Source:** `Season.java` line 71 (`@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true) private List<SeasonPhase> phases`).

**Apply to:** `TestDataService.seedSeasons` for the consolidated 2023 GROUPS season — attach `SeasonPhase` + `SeasonPhaseGroup` instances to the in-memory Season collection, then call `seasonRepository.save(season)` once. JPA cascades the writes.

### 10. Single-transaction execute (Phase 54 / 55 precedent)

**Source:** `DriverSheetImportService.execute` `@Transactional` boundary (line 83).

**Apply to:** D-16 — execute path stays exactly as today (writes only `SeasonDriver`); the new group-resolution code lives in `preview()` / `buildTabPreview()` (already `@Transactional(readOnly = true)` at line 52).

---

## No Analog Found

None. Every touched file in Phase 59 has a strong existing analog (mostly self-extension). The two CONTEXT.md inaccuracies (`findByPhaseIdAndTeamId`, `DriverSheetImportServiceIT`) are flagged in the "IMPORTANT discrepancy" section above and have clear sibling patterns the planner should copy.

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/dataimport/`
- `src/main/java/org/ctc/admin/`
- `src/main/java/org/ctc/domain/service/`
- `src/main/java/org/ctc/domain/repository/`
- `src/main/java/org/ctc/domain/model/`
- `src/main/java/org/ctc/domain/exception/`
- `src/test/java/org/ctc/dataimport/`
- `src/test/java/org/ctc/admin/`
- `src/test/java/org/ctc/domain/service/`
- `src/test/java/org/ctc/domain/repository/`

**Files scanned:** ~25 files (15 production + 10 test).

**Pattern extraction date:** 2026-04-28.

**Phase:** 59-import-test-data.
