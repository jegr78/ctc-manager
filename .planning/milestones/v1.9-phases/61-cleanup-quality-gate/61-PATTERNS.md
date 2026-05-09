# Phase 61: Cleanup & Quality Gate - Pattern Map

**Mapped:** 2026-05-01
**Files analyzed:** 28 (5 create, 14 modify in main, ~9 modify in test)
**Analogs found:** 26 / 28 (2 first-instance: SQL fixtures, V6MigrationTest)

---

## File Classification

| File | Plan | Role | Data Flow | Closest Analog | Match Quality |
|------|------|------|-----------|----------------|---------------|
| `.planning/ROADMAP.md` | 61-01 | docs | edit | self (Phase 56-60 sections) | exact |
| `.planning/PROJECT.md` | 61-01 | docs | edit | self (Key-Decisions table) | exact |
| `src/main/java/org/ctc/domain/model/Season.java` | 61-02 | entity | model | self (existing Season convenience methods) | exact |
| `src/main/java/org/ctc/domain/model/Matchday.java` | 61-02 | entity | model | self + `Season.java` getTeams() | exact |
| `src/main/java/org/ctc/domain/model/Playoff.java` | 61-02 | entity | model | self + `Matchday.java` | exact |
| `src/main/java/org/ctc/domain/service/PlayoffService.java` | 61-02 | service | CRUD | self (lines 47-118 first-class API) | exact |
| `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` | 61-02 | service | CRUD | self | exact |
| `src/main/java/org/ctc/domain/service/MatchService.java` | 61-02 | service | CRUD | self (Phase 58 D-04 phase-aware pattern) | exact |
| `src/main/java/org/ctc/domain/service/MatchdayService.java` | 61-02 | service | CRUD | self (Phase 58 D-26 dual-API) | exact |
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java` | 61-02 | service | CRUD | self | exact |
| `src/main/java/org/ctc/domain/service/StandingsService.java` | 61-02 | service | aggregation | self (Phase 58 D-04) | exact |
| `src/main/java/org/ctc/admin/controller/SeasonController.java` | 61-02 | controller | request-response | self (Phase 60 phaseId-canonical) | exact |
| `src/main/java/org/ctc/admin/controller/PlayoffController.java` | 61-02 | controller | request-response | self (lines 81-104) | exact |
| `src/main/java/org/ctc/dataimport/CsvImportService.java` | 61-02 | service | batch | self | exact |
| `src/main/java/org/ctc/admin/TestDataService.java` | 61-02 | service | batch | self (line 932 already cleaned) | exact |
| `src/main/resources/templates/admin/*.html` | 61-02 | template | render | grep-audit only — already cleaned in Phase 60 | confirmed-clean |
| `src/test/java/org/ctc/TestHelper.java` | 61-02 | test-helper | factory | self (existing createSeason w/ phase bootstrap) | exact |
| `src/test/java/org/ctc/admin/controller/PlayoffControllerTest.java` | 61-02 | test | unit | self | exact |
| `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` | 61-02 | test | unit | self | exact |
| `src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql` | 61-03 | migration | DDL | `V5__nullable_legacy_scoring_columns.sql` | exact |
| `src/test/java/db/migration/V6MigrationTest.java` | 61-03 | test | integration | `V4MigrationSmokeIT.java` | role-match (read-only post-V6 vs. post-V4 with seed) |
| `pom.xml` | 61-03 | config | edit | self (no changes per D-21) | exact |
| `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` | 61-04 | test | E2E | `AdminWorkflowE2ETest.java` + `ImportE2eTest.java` + `ScoringE2ETest.java` | composite |
| `src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java` | 61-05 | test | E2E read-only | `AdminWorkflowE2ETest.java` + new @Sql pattern | role-match |
| `src/test/resources/sql/legacy-season-without-playoff.sql` | 61-05 | fixture | DDL/DML | `V4MigrationSmokeIT.java` `@BeforeEach seedSmokeTestData()` (lines 60-88) | role-match (first instance of @Sql fixture) |
| `src/test/resources/sql/legacy-season-with-playoff.sql` | 61-05 | fixture | DDL/DML | same as above | role-match |

---

## Plan 61-01: ROADMAP-Update + PROJECT.md Scope-Decision-Eintrag

### `.planning/ROADMAP.md` (docs, edit)

**Analog:** self — existing Phase 61 section (lines 242-257)

**Pattern to extend** (current SC1):
```markdown
1. Flyway cleanup migration executes successfully, dropping `seasons.format`, `seasons.total_rounds`, `seasons.legs`, `seasons.event_duration_minutes`, `seasons.start_date`, `seasons.end_date`, `seasons.race_scoring_id`, `seasons.match_scoring_id`, and the `playoff_seasons` join table; `./mvnw verify` remains green afterwards
```

**Executor change:** Insert two columns into the SC1 drop list:
- `matchdays.season_id` (bridge column, Phase 56 D-02 had it as "remains")
- `playoffs.season_id` (bridge column, Phase 57 SC5 had it as "remains")

**Keep verbatim:** the goal sentence at line 244 ("The old bridge columns and join table are removed..."), the dependency line ("**Depends on**: Phase 60"), all SC2/SC3/SC4.

---

### `.planning/PROJECT.md` (docs, edit)

**Analog:** self — `## Current Milestone: v1.9 Season Phases & Groups` Key-Decisions table (read pattern from existing entries; one new row appended)

**Pattern to follow:** existing Key-Decisions rows (Phase 56-60) typically have shape:
- `| Phase 61 | Bridge-Spalten-Drop in V6 erweitert | matchdays.season_id + playoffs.season_id mit gedroppt | denormalisiert + wartungsbelastend, Phase 61 D-01 |`

**Executor change:** Append exactly one row capturing D-01 scope-extension with reason "denormalisiert + wartungsbelastend".

---

## Plan 61-02: Code-Cleanup (Entity-Refactoring + Aufrufstellen + Test-Fixes)

### `src/main/java/org/ctc/domain/model/Season.java` (entity, model)

**Analog:** self — existing convenience-method block (lines 103-198) demonstrates the JavaDoc + delegation pattern that the new Derived `getMatchdays()` must match.

**Convenience-Method Pattern to copy** (lines 107-116):
```java
/**
 * Convenience method: returns the list of Teams participating in this season,
 * ordered by short name. Derived from the seasonTeams association.
 */
public List<Team> getTeams() {
    return seasonTeams.stream()
            .map(SeasonTeam::getTeam)
            .sorted(Comparator.comparing(Team::getShortName))
            .collect(Collectors.toCollection(ArrayList::new));
}
```

**Executor changes:**
- **Remove fields** (lines 38, 41, 47-55, 57-63): `startDate`, `endDate`, `format`, `totalRounds`, `legs`, `eventDurationMinutes`, `raceScoring`, `matchScoring` and their `@Column`/`@ManyToOne` annotations.
- **Update `@ToString(exclude = ...)` line 19:** drop `"raceScoring", "matchScoring"`, keep `"phases"`, `"matchdays"` (matchdays is now a method but the toString exclude key just needs the property name, harmless to leave).
- **Replace** `@OneToMany ... private List<Matchday> matchdays` (lines 65-67) with a derived getter (D-05) following the convenience-method pattern shown above:
```java
/**
 * Convenience method: returns all matchdays across all phases of this season,
 * sorted first by phase sortIndex, then by matchday sortIndex. Derived from the phases association
 * — replaces the legacy {@code @OneToMany(mappedBy = "season")} mapping that was dropped in
 * Phase 61 alongside MIGR-06 (matchday.season_id bridge column removed in V6).
 */
public List<Matchday> getMatchdays() {
    return phases.stream()
            .flatMap(p -> p.getMatchdays().stream())
            .sorted(Comparator.comparingInt((Matchday m) -> m.getPhase().getSortIndex())
                    .thenComparingInt(Matchday::getSortIndex))
            .toList();
}
```
- **Note for executor:** `SeasonPhase.matchdays` does NOT currently exist as a `@OneToMany` field (verified at `SeasonPhase.java` line 65 — only `groups` is mapped). Plan 61-02 must ADD the `@OneToMany(mappedBy = "phase") List<Matchday> matchdays` field on `SeasonPhase` before the derived getter on `Season` works. Pattern (mirror `SeasonPhase.groups` at lines 65-67):
```java
@OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("sortIndex ASC")
private List<Matchday> matchdays = new ArrayList<>();
```

---

### `src/main/java/org/ctc/domain/model/Matchday.java` (entity, model)

**Analog:** self — current state (lines 26-32) shows the dual `season` + `phase` ManyToOne mapping; the cleanup keeps `phase`, drops `season` field, and adds a Convenience-Getter.

**Pattern to copy from existing code in same file** (lines 26-32 dual-mapping shape):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "season_id", nullable = false)
private Season season;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "phase_id")
private SeasonPhase phase;
```

**Executor changes:**
- **Remove** lines 26-28 (the `season` field with `@JoinColumn(name="season_id")`).
- **Make `phase` `@JoinColumn(name="phase_id", nullable=false)`** — was already `phase_id` set NOT NULL in V4 by Phase 57.
- **Update `@ToString(exclude = ...)` line 19:** keep `"phase"`, `"group"`, drop `"season"`.
- **Update constructor** (lines 51-55): Replace `Matchday(Season, String, int)` with `Matchday(SeasonPhase, String, int)` — but note this triggers compile-cascade in `MatchdayService` line 109 (`new Matchday(season, label, sortIndex)`), `TestDataService`, etc. Per D-02 keep a Convenience-Constructor `Matchday(Season, String, int)` is NOT desired (D-02 only mandates `getSeason()` getter, not setter/constructor).
- **Add Convenience-Getter** (D-02):
```java
/**
 * Convenience getter — derives season via {@code getPhase().getSeason()}.
 * The {@code matchdays.season_id} bridge column was dropped in V6 (MIGR-06);
 * the phase association is now the single source of truth.
 * Returns {@code null} only if {@code phase} is unset, which should not occur post-V4.
 */
public Season getSeason() {
    return phase != null ? phase.getSeason() : null;
}
```
- Lombok `@Getter` will collide with this manual getter — verify and rely on Lombok's behavior of skipping the manual one (it does), or remove `@Getter`-generated method only for `season` (not possible per-field with `@Getter`-class-level — manual method overrides Lombok's).

---

### `src/main/java/org/ctc/domain/model/Playoff.java` (entity, model)

**Analog:** self — existing `season` ManyToOne (lines 28-31) and `seasons` ManyToMany (lines 48-53) show what is being removed; same Convenience-Getter pattern as Matchday applies.

**Removal targets** (lines 28-31, 48-53):
```java
@NotNull
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "season_id", nullable = false, unique = true)
private Season season;
// ...
@ManyToMany
@JoinTable(name = "playoff_seasons",
        joinColumns = @JoinColumn(name = "playoff_id"),
        inverseJoinColumns = @JoinColumn(name = "season_id"))
@OrderBy("name ASC")
private List<Season> seasons = new ArrayList<>();
```

**Executor changes:**
- **Remove** the `season` ManyToOne (lines 28-31) and the `seasons` ManyToMany (lines 48-53).
- **Make `phase` NOT NULL on @JoinColumn line 34** (`@JoinColumn(name = "phase_id", nullable = false, unique = true)`). The UNIQUE constraint already exists per V3 line 58.
- **Update `@ToString(exclude = ...)` line 21:** drop `"season"`, `"seasons"`, keep `"phase"`, `"rounds"`, `"seeds"`.
- **Update constructor** (lines 63-66) — replace `Playoff(Season, String)` signature; planner finalizes whether to keep season-based constructor (purely as facade) or migrate callers (PlayoffService line 79: `new Playoff(season, name)`).
- **Add Convenience-Getter** (D-02 — same JavaDoc style as Matchday.getSeason):
```java
/**
 * Convenience getter — derives season via {@code getPhase().getSeason()}.
 * The {@code playoffs.season_id} bridge column and the {@code playoff_seasons} M:N join table
 * were both dropped in V6 (MIGR-06); the phase association is now the single source of truth.
 */
public Season getSeason() {
    return phase != null ? phase.getSeason() : null;
}
```

---

### `src/main/java/org/ctc/domain/service/PlayoffService.java` (service, CRUD)

**Analog:** self — first-class API in `createPlayoff` (lines 47-118) is the model for "no @Deprecated" — single phase-aware path.

**Removal targets** (lines 120-148):
```java
/**
 * @deprecated Phase 58 D-03: M:N {@code playoff_seasons} is removed in Phase 61...
 */
@Deprecated
@Transactional
public void addSeasonToPlayoff(UUID playoffId, UUID seasonId) {
    Playoff playoff = playoffRepository.findById(playoffId)
            .orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
    Season season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
    if (!playoff.getSeasons().contains(season)) {
        playoff.getSeasons().add(season);
        playoffRepository.save(playoff);
    }
}

@Deprecated
@Transactional
public void removeSeasonFromPlayoff(UUID playoffId, UUID seasonId) {
    Playoff playoff = playoffRepository.findById(playoffId)
            .orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
    playoff.getSeasons().removeIf(s -> s.getId().equals(seasonId));
    playoffRepository.save(playoff);
}
```

**Iteration cleanup target** (lines 150-166 `getPlayoffTeams`):
```java
@Transactional(readOnly = true)
public List<Team> getPlayoffTeams(UUID playoffId) {
    Playoff playoff = playoffRepository.findById(playoffId)
            .orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
    Map<UUID, Team> teamMap = new LinkedHashMap<>();
    for (Season season : playoff.getSeasons()) {              // ← REMOVE this loop entirely (D-01)
        for (Team team : season.getTeams()) {
            teamMap.putIfAbsent(team.getId(), team);
        }
    }
    for (Team team : playoff.getSeason().getTeams()) {        // ← KEEP (uses Convenience-Getter)
        teamMap.putIfAbsent(team.getId(), team);
    }
    return new ArrayList<>(teamMap.values());
}
```

**Executor changes:**
- Delete both `@Deprecated` methods (lines 120-148 inclusive).
- In `getPlayoffTeams`: remove the `for (Season season : playoff.getSeasons())` loop; keep the second loop (uses convenience-getter `playoff.getSeason()`).
- Audit `seasonRepository` field (line 38) — still needed for `getPlayoffListData`/`getPlayoffDetailData`, do NOT remove.

---

### `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` (service, CRUD)

**Analog:** self — `tryLoadFromRegularStandings` (lines 211-253) is the first-class D-15 implementation; that path uses ONLY `playoff.getSeason()`.

**Removal target** (lines 64-74 in `getSeedingData`):
```java
// Collect all teams from linked seasons and the main season
Map<UUID, Team> teamMap = new LinkedHashMap<>();
for (Season season : playoff.getSeasons()) {                  // ← REMOVE entire for-loop (D-01)
    for (Team team : season.getTeams()) {
        teamMap.putIfAbsent(team.getId(), team);
    }
}
for (Team team : playoff.getSeason().getTeams()) {            // ← KEEP (uses convenience-getter)
    teamMap.putIfAbsent(team.getId(), team);
}
List<Team> teams = new ArrayList<>(teamMap.values());
```

**Executor changes:** Delete the `playoff.getSeasons()` loop in `getSeedingData` (lines 66-70). The second loop already uses the Convenience-Getter and is the canonical post-cleanup form.

---

### `src/main/java/org/ctc/domain/service/MatchService.java` (service, CRUD)

**Analog:** self — `createMatchWithLegs` (line 78) takes the Matchday parameter, the lookup of legs at line 83 must shift from season to phase.

**Pattern target** (lines 83 + 106):
```java
int legs = matchday.getSeason().getLegs();    // ← Season.getLegs() removed in 61-02; must change
// ...
int maxLegs = matchday.getSeason().getLegs(); // ← same
```

**Executor change** (D-01 compile-cascade):
```java
int legs = matchday.getPhase().getLegs();
// ...
int maxLegs = matchday.getPhase().getLegs();
```
Note: `Matchday.getPhase()` is the JPA-mapped field; `SeasonPhase.getLegs()` is Lombok `@Getter` from `SeasonPhase.java` line 51-52 (`private int legs = 1;`).

Line 38 (`return new CreateFormData(matchday, matchday.getSeason().getTeams())`) stays unchanged — uses Convenience-Getter, which still works.

---

### `src/main/java/org/ctc/domain/service/MatchdayService.java` (service, CRUD)

**Analog:** self — Phase 58 D-26 dual-API pattern (line 107 `matchday.setSeason(season)`).

**Audit target** (line 107):
```java
matchday.setSeason(season);  // ← Matchday.season field removed in 61-02; setSeason() goes away with @Setter
```

**Executor change:** Replace with `matchday.setPhase(seasonPhaseService.findRegularPhase(season.getId()))` OR delegate to the phase-aware overload. Planner finalizes whether `saveMatchday` becomes a thin wrapper around `saveMatchday(label, sortIndex, phaseId, ...)`. Line 123 (`var seasonId = matchday.getSeason().getId()`) keeps working via Convenience-Getter, no change needed.

---

### `src/main/java/org/ctc/domain/service/SeasonManagementService.java` (service, CRUD)

**Audit targets:**
- Line 144: `boolean isSwiss = season.getFormat() == SeasonFormat.SWISS;` — `Season.getFormat()` gone; replace with REGULAR-phase lookup `seasonPhaseService.findRegularPhase(season.getId()).getFormat() == SeasonFormat.SWISS`.
- Line 249: `for (var md : season.getMatchdays())` — works via D-05 Derived-Getter, no change needed (Convenience-Method).

---

### `src/main/java/org/ctc/domain/service/StandingsService.java` (service, aggregation)

**Audit target** (lines 171-182 `calculateStandingsLegacy`):
```java
private List<TeamStanding> calculateStandingsLegacy(UUID seasonId) {
    var season = seasonRepository.findById(seasonId).orElse(null);
    if (season == null) return List.of();
    var matchScoring = season.getMatchScoring();   // ← Season.matchScoring gone in 61-02
    List<Match> matches = matchRepository.findByMatchdaySeasonId(seasonId);
    // ...
    Map<UUID, UUID> successionMap = season.buildSuccessionMap();
    for (Team team : season.getActiveTeams()) {
```

**Executor decision:** Per Phase 58 D-04 the legacy fallback exists "only for seasons without a REGULAR SeasonPhase" — but post-V4 EVERY season has a REGULAR phase (per Phase 57 D-06). Plan 61-02 should DELETE `calculateStandingsLegacy` entirely + the corresponding `if-else` upstream that calls it. The `// MIGR-06 cleanup` TODO comment at line 142-168 is precisely what Plan 61-02 acts on.

---

### `src/main/java/org/ctc/admin/controller/SeasonController.java` (controller, request-response)

**Audit target** (lines 208 + 229):
```java
&& (data.season().getTotalRounds() == null || data.season().getMatchdays().size() < data.season().getTotalRounds()));  // line 208
form.setNumberOfRounds(season.getTotalRounds() != null ? season.getTotalRounds() : formData.optimalRounds());          // line 229
```

**Executor change:** Both must move to the REGULAR phase. Pattern (mirror Phase 58 D-26 — ask `seasonPhaseService.findRegularPhase(seasonId).getTotalRounds()`). `season.getMatchdays()` (line 208) keeps working via D-05 Derived-Getter.

---

### `src/main/java/org/ctc/admin/controller/PlayoffController.java` (controller, request-response)

**Analog:** self — clean phase-canonical handlers (lines 81-104 `save`, 131-145 `seed`).

**Removal target** (lines 115-129):
```java
@PostMapping("/{id}/add-season")
public String addSeason(@PathVariable UUID id, @RequestParam UUID seasonId,
                        RedirectAttributes redirectAttributes) {
    playoffService.addSeasonToPlayoff(id, seasonId);
    redirectAttributes.addFlashAttribute("successMessage", "Season linked");
    return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForPlayoff(id);
}

@PostMapping("/{id}/remove-season")
public String removeSeason(@PathVariable UUID id, @RequestParam UUID seasonId,
                           RedirectAttributes redirectAttributes) {
    playoffService.removeSeasonFromPlayoff(id, seasonId);
    redirectAttributes.addFlashAttribute("successMessage", "Season removed");
    return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForPlayoff(id);
}
```

**Executor change (D-03):** Delete both `@PostMapping` blocks and their methods entirely. UI has been hidden since Phase 60 D-43; dropping returns 404 on old bookmarks. Tracked Behavior Change.

---

### `src/main/java/org/ctc/dataimport/CsvImportService.java` (service, batch)

**Audit target** (lines 229, 335):
```java
scoringService.calculatePoints(raceResult, season.getRaceScoring());
```

**Executor change:** `Season.raceScoring` gone in 61-02. Resolve scoring via the REGULAR phase: `scoringService.calculatePoints(raceResult, regularPhase.getRaceScoring())` where `regularPhase` is fetched once at the top of the method via `seasonPhaseService.findRegularPhase(seasonId)`.

---

### `src/main/java/org/ctc/admin/TestDataService.java` (service, batch)

**Audit target:** Line 932 already references the cleaned-up state ("Eliminates the legacy `playoff.getSeasons().add(s1b)` hack"). Plan 61-02 just needs to grep-confirm no remaining `setSeason(...)` on `Matchday`/`Playoff` literals or `season.setFormat/setLegs/...` calls anywhere in TestDataService. Recent commits show TestDataService is already phase-aware.

---

### `src/main/resources/templates/admin/*.html` (template, render)

**Audit confirmation:** The grep `\$\{season\.(format|legs|totalRounds|eventDurationMinutes|startDate|endDate|raceScoring|matchScoring)` over all templates returned ZERO matches as of the snapshot. Phase 60 already cleaned templates.

**Executor action:** Plan 61-02 includes a defensive grep-pass (CONTEXT.md D-06) over `src/main/resources/templates/admin/*.html` to confirm zero residue. If any line surfaces, the template either must use `${season.regularPhase.legs}` (after a controller-side model addition) or the legacy SpEL access is removed outright.

---

### `src/test/java/org/ctc/TestHelper.java` (test-helper, factory)

**Analog:** self — existing `createSeason(name, year, number)` (lines 34-47) bootstraps a REGULAR phase + scoring. The new helpers must follow the same `seasonPhaseRepository.save(new SeasonPhase(...))` shape.

**Pattern to copy** (lines 30-47):
```java
/**
 * Creates a Season with a bootstrapped REGULAR phase (LEAGUE layout, sortIndex=0).
 * Mirrors the bootstrap performed by SeasonManagementService.save — ensures tests that
 * call seasonPhaseRepository.findBySeasonIdAndPhaseType(id, REGULAR) find a result.
 */
public Season createSeason(String name, int year, int number) {
    var suffix = UUID.randomUUID().toString().substring(0, 4);
    var rs = raceScoringRepository.save(...);
    var ms = matchScoringRepository.save(...);
    var season = new Season(name, year, number);
    season.setRaceScoring(rs);                    // ← season.setRaceScoring gone in 61-02; remove
    season.setMatchScoring(ms);                   // ← season.setMatchScoring gone in 61-02; remove
    var saved = seasonRepository.save(season);
    seasonPhaseRepository.save(new SeasonPhase(saved, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
    return saved;
}
```

**Executor changes (D-06):**
- Remove `season.setRaceScoring(rs)` + `season.setMatchScoring(ms)` (lines 42-43); move both to the SeasonPhase: after `seasonPhaseRepository.save(...)`, set scoring on the phase, save again.
- **Add new factory methods** following the same shape:
  - `createMatchdayInRegularPhase(Season season, String label, int sortIndex)` — uses `seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), REGULAR)`, sets `matchday.setPhase(regularPhase)`, persists.
  - `createPlayoffInPhase(Season season, String name, int teamCount)` — wraps `playoffService.createPlayoff(...)` (D-19 pattern auto-creates PLAYOFF phase).

---

### `src/test/java/org/ctc/admin/controller/PlayoffControllerTest.java` (test, unit)

**Audit target** (lines 312-331):
```java
.doesNotContain("/admin/playoffs/" + playoff.getId() + "/add-season");
.doesNotContain("/admin/playoffs/" + playoff.getId() + "/remove-season");

mockMvc.perform(post("/admin/playoffs/" + playoff.getId() + "/add-season")
mockMvc.perform(post("/admin/playoffs/" + playoff.getId() + "/remove-season")
```

**Executor change:** Replace the existing positive-route tests with negative-route tests asserting `status().isNotFound()` after route deletion (D-03 — old bookmarks return 404). The `doesNotContain` assertions at lines 312-314 stay valid (they assert UI no longer links to those endpoints — Phase 60 already passes this).

---

### `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` (test, unit)

**Audit target** (lines 913-937 — three call sites of removed methods):
```java
playoffService.addSeasonToPlayoff(playoff.getId(), linkedSeasonId);
// ... and:
playoffService.removeSeasonFromPlayoff(playoff.getId(), linkedSeasonId);
```

**Executor change:** Delete the @Test methods that exercise these paths entirely. They guard a removed feature; no replacement is needed (the playoff-phase-binding path is already covered by other tests via Phase 58 D-19).

---

## Plan 61-03: V6 SQL Migration + V6MigrationTest

### `src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql` (migration, DDL — CREATE)

**Analog:** `src/main/resources/db/migration/V5__nullable_legacy_scoring_columns.sql`

**Pattern to copy verbatim (header style, H2/MariaDB compat note):**
```sql
-- Phase 60 UI-01: legacy scoring FK columns become nullable
-- Slim Season form (UI-01) no longer requires raceScoring/matchScoring at season-creation time;
-- ...
-- Compatible with H2 2.x and MariaDB 10.7+.

ALTER TABLE seasons ALTER COLUMN race_scoring_id DROP NOT NULL;
ALTER TABLE seasons ALTER COLUMN match_scoring_id DROP NOT NULL;

ALTER TABLE season_phases ALTER COLUMN race_scoring_id DROP NOT NULL;
ALTER TABLE season_phases ALTER COLUMN match_scoring_id DROP NOT NULL;
```

**Executor template (D-07 ordering: drop M:N first, then bridge FKs, then season fields):**
```sql
-- Phase 61 MIGR-06 + D-01 scope-extension: drop all legacy season-level fields and bridge tables.
-- After Phase 56-60 the canonical model is Season -> SeasonPhase -> Matchday/Playoff;
-- the columns/tables dropped here are denormalized residue from the pre-v1.9 flat model.
-- Order: 1) M:N table, 2) bridge FK columns, 3) seasons legacy columns.
-- Compatible with H2 2.x and MariaDB 10.7+.
-- IRREVERSIBLE — ops must take a backup before applying to prod (Tracked Behavior Change).

DROP TABLE playoff_seasons;

ALTER TABLE matchdays DROP COLUMN season_id;
ALTER TABLE playoffs DROP COLUMN season_id;

ALTER TABLE seasons DROP COLUMN format;
ALTER TABLE seasons DROP COLUMN total_rounds;
ALTER TABLE seasons DROP COLUMN legs;
ALTER TABLE seasons DROP COLUMN event_duration_minutes;
ALTER TABLE seasons DROP COLUMN start_date;
ALTER TABLE seasons DROP COLUMN end_date;
ALTER TABLE seasons DROP COLUMN race_scoring_id;
ALTER TABLE seasons DROP COLUMN match_scoring_id;
```

**Executor must verify:**
- FK index `idx_matchdays_season_id` and `idx_playoffs_season_id` (from V2) are dropped automatically by MariaDB when the column is dropped — H2 confirmed same. If not, add explicit `DROP INDEX` statements for both dialects.
- Verify against `application-{dev,local,docker,prod}.yml` `ddl-auto=validate` — entity must be cleaned in 61-02 BEFORE V6 is added (D-08 Code-First).

---

### `src/test/java/db/migration/V6MigrationTest.java` (test, integration — CREATE)

**Analog:** `src/test/java/db/migration/V4MigrationSmokeIT.java` (lines 38-45 imports + class header) — same package, same Spring-Boot bootstrap pattern, same H2-via-Flyway approach. Note: `V4MigrationSmokeIT` uses `IT` suffix (Failsafe) but D-09 mandates **Surefire** (V6MigrationTest with `Test` suffix runs in standard `./mvnw verify`).

**Pattern to copy** (`V4MigrationSmokeIT.java` lines 38-53):
```java
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
@Transactional
class V4MigrationSmokeIT {
    private static final UUID SMOKE_RACE_SCORING_ID = UUID.fromString("00000000-0000-0057-0000-000000000001");
    // ...

    @Autowired private SeasonRepository seasonRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
```

**Information-Schema query pattern** (`V4MigrateSeasonsToPhasesIT.java` lines 92-114) for asserting column existence:
```java
@Test
void givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase() {
    Integer regularPhaseCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM season_phases WHERE phase_type = 'REGULAR'", Integer.class);
    assertThat(regularPhaseCount).isEqualTo(3);
    // ...
    Map<String, Object> phase1 = jdbcTemplate.queryForMap(
            "SELECT * FROM season_phases WHERE season_id = ? AND phase_type = 'REGULAR'", SEASON_1_ID);
    assertThat(phase1.get("format")).isEqualTo("LEAGUE");
}
```

**Executor template (D-09 — 4 assertions per CONTEXT.md):**
```java
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
class V6MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SeasonRepository seasonRepository;

    @Test
    void givenV6HasRun_whenQueryInformationSchema_thenSeasonsLegacyColumnsAreGone() {
        // H2 INFORMATION_SCHEMA.COLUMNS — also works on MariaDB
        Integer formatCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'SEASONS' AND COLUMN_NAME = 'FORMAT'", Integer.class);
        assertThat(formatCol).isZero();
        // ...repeat for total_rounds, legs, event_duration_minutes, start_date, end_date,
        //         race_scoring_id, match_scoring_id
    }

    @Test
    void givenV6HasRun_whenQueryInformationSchema_thenPlayoffSeasonsTableIsGone() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PLAYOFF_SEASONS'",
                Integer.class);
        assertThat(tableCount).isZero();
    }

    @Test
    void givenV6HasRun_whenQueryMatchdays_thenSeasonIdColumnIsGone() {
        // Bridge column dropped per D-01 scope-extension
        Integer col = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'MATCHDAYS' AND COLUMN_NAME = 'SEASON_ID'", Integer.class);
        assertThat(col).isZero();
    }

    @Test
    void givenV6HasRun_whenLoadAllSeasons_thenJpaMappingStillWorks() {
        // Hibernate ddl-auto=validate post-V6 must agree with the trimmed Season entity.
        // findAll() exercises the schema-vs-entity match.
        assertThat(seasonRepository.findAll()).isNotNull();
    }
}
```

**Notes for executor:**
- File path: `src/test/java/db/migration/V6MigrationTest.java` (mirrors V4 source layout, NOT under `org.ctc`).
- Suffix `Test` (not `IT`) per D-09 — runs in **Surefire** with normal `./mvnw verify`, not Failsafe.
- The `@Transactional` annotation is OPTIONAL (no DML side effects — purely SELECT-side asserts), but harmless if added.
- Use `assertThat(col).isZero()` (AssertJ) — consistent with V4 test style.

---

### `pom.xml` (config, edit)

**Analog:** self — JaCoCo block (lines 198-249) already at 0.82.

**Executor change (D-21):** No changes in Plan 61-03. If Plan 61-05 needs to repair coverage, the `<minimum>0.82</minimum>` (line 241) stays — Plan 61-05 adds tests, never lowers threshold.

---

## Plan 61-04: QUAL-02 GROUPS-E2E

### `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` (test, E2E — CREATE)

**Analogs (composite):**
1. `AdminWorkflowE2ETest.java` — overall test class structure (extends `PlaywrightConfig`, `setupPage`/`teardownPage`).
2. `ImportE2eTest.java` lines 108-120 — `@TestConfiguration TestGoogleSheetsConfig` stub (D-12).
3. `ScoringE2ETest.java` lines 46-69 — UI-click result entry form pattern (D-15).

**Pattern 1 — Class header + lifecycle** (`AdminWorkflowE2ETest.java` lines 1-19):
```java
package org.ctc.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class AdminWorkflowE2ETest extends PlaywrightConfig {

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }
```

**Pattern 2 — `@Import` + `@TestConfiguration` GoogleSheets stub** (`ImportE2eTest.java` lines 14-15, 108-120 — copy literally and customize the `getSheetTab` return):
```java
@Import(ImportE2eTest.TestGoogleSheetsConfig.class)
class ImportE2eTest extends PlaywrightConfig {
    // ...
    @TestConfiguration
    static class TestGoogleSheetsConfig {
        @Bean @Primary
        GoogleSheetsService googleSheetsService() {
            return new GoogleSheetsService("") {
                @Override
                public boolean isAvailable() { return true; }
            };
        }
    }
}
```

**Pattern 3 — UI form submit + flash assertion** (`ScoringE2ETest.java` lines 46-69):
```java
page.navigate(url("/admin/race-scorings/new"));
page.fill("#name", "E2E Test Scoring");
page.click("text=+ Add");
var raceInputs = page.locator("#racePointsRows input[type=number]");
raceInputs.nth(0).fill("15");
page.click("text=Save");
assertThat(page.locator(".alert-success")).containsText("Race-Scoring saved");
assertThat(page.locator("table")).containsText("E2E Test Scoring");
```

**Pattern 4 — slim season form (no scoring/format/dates)** (`AdminWorkflowE2ETest.java` lines 53-67):
```java
page.navigate(url("/admin/seasons/new"));
page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Name").setExact(true)).fill("E2E Season");
page.fill("#year", "2026");
page.fill("#number", "99");
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
```

**Executor template (D-11/D-13/D-14/D-16):**
```java
package org.ctc.e2e;

import org.ctc.dataimport.GoogleSheetsService;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat as assertThatJ;  // disambiguate

@Import(GroupsSeasonE2ETest.TestGoogleSheetsConfig.class)
class GroupsSeasonE2ETest extends PlaywrightConfig {

    @Autowired private SeasonPhaseRepository seasonPhaseRepository;
    @Autowired private PhaseTeamRepository phaseTeamRepository;

    @BeforeEach void setUp() { setupPage(); }
    @AfterEach void tearDown() { teardownPage(); }

    @Test
    void givenGroupsLayout_whenFullSeasonWorkflow_thenStandingsCorrect() {
        // given — Test-GROUPS Season 2099 (D-14 prefix)
        // 1. create season via /admin/seasons/new
        // 2. add 2nd phase with GROUPS layout via /admin/seasons/{id}/phases/new
        // 3. create Group A + Group B
        // 4. add 4 teams (T-GA-1, T-GA-2, T-GB-1, T-GB-2 per D-14)
        // 5. drive driver-import preview → execute (stub returns 6-12 driver rows)
        // 6. generate matchdays per group (D-16: 2 per group, 1 race each = 4 races total)
        // 7. enter race results via UI clicks (D-15)
        // 8. assert per-group standings + combined-view standings

        // Hybrid asserts (D-13):
        // UI: assertThat(page.locator(".group-tab:has-text('Group A')")).isVisible();
        // DB: assertThatJ(phaseTeamRepository.findByPhaseId(p.getId())).hasSize(4);
        //     assertThatJ(seasonPhaseRepository.findBySeasonIdAndPhaseType(s.getId(), REGULAR)
        //                  .get().getLayout()).isEqualTo(PhaseLayout.GROUPS);
    }

    @TestConfiguration
    static class TestGoogleSheetsConfig {
        @Bean @Primary
        GoogleSheetsService googleSheetsService() {
            return new GoogleSheetsService("") {
                @Override public boolean isAvailable() { return true; }
                // override the 2099 tab listing + row reads here per D-12 (planner finalizes
                // exact GoogleSheetsService contract — likely getTabsForSpreadsheet + readSheet)
            };
        }
    }
}
```

**Executor notes:**
- File path is `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` — runs in Failsafe (`-Pe2e` profile), not Surefire.
- Test data uses T-prefix `T-GA-1, T-GA-2, T-GB-1, T-GB-2` and PSNIDs `T_groups_drv01..drv12`, year=2099 (D-14 — avoids DevDataSeeder collision).
- `PlaywrightConfig.setupPage()` provides `page` field; `url(path)` builds localhost URL.

---

## Plan 61-05: QUAL-03 Regression-E2E + Coverage-Repair

### `src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java` (test, E2E read-only — CREATE)

**Analogs:**
1. `AdminWorkflowE2ETest.java` — class structure & assertion style.
2. `V4MigrationSmokeIT.java` lines 60-88 — JdbcTemplate-style row insertion (the `@Sql` script will translate this to a `.sql` file).

**Pattern 1 — `@Sql` annotation usage** (NEW — first instance in this codebase, but Spring's standard pattern):
```java
@Sql(scripts = "/sql/legacy-season-without-playoff.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Test
void givenLegacyMigratedSeasonWithoutPlayoff_thenRegularTabOnly() {
    // ...
}
```

**Executor template (D-17/D-18/D-19):**
```java
package org.ctc.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class LegacyMigratedSeasonE2ETest extends PlaywrightConfig {

    @BeforeEach void setUp() { setupPage(); }
    @AfterEach void tearDown() { teardownPage(); }

    @Test
    @Sql(scripts = "/sql/legacy-season-without-playoff.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void givenLegacyMigratedSeasonWithoutPlayoff_thenRegularTabOnly() {
        // given — Test-Legacy-Season-2098 inserted via @Sql with 1 REGULAR phase + 2 matchdays + races

        page.navigate(url("/admin/seasons/<UUID>"));

        // (a) exactly 1 REGULAR-tab visible
        assertThat(page.locator(".phase-tab:has-text('Regular')")).hasCount(1);
        // (b) NO playoff-tab
        assertThat(page.locator(".phase-tab:has-text('Playoff')")).hasCount(0);
        // (c) Matchday-list rendert
        assertThat(page.locator(".matchday-row")).hasCount(2);
        // (d) Click matchday → race-list rendert
        page.locator(".matchday-row").first().click();
        assertThat(page.locator(".race-row")).hasCount(2);
        // (e) Race-detail rendert mit Results
        page.locator(".race-row").first().click();
        assertThat(page.locator(".race-result-row")).hasCount(2);
        // (f) Legacy-Standings-URL auto-redirect (Phase 60 D-12, D-31)
        page.navigate(url("/admin/standings?season=<UUID>"));
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*phaseId=.*"));
        // (g) Standings-Tabelle erwartete Werte
        assertThat(page.locator(".standings-row").first()).containsText("T-LEG-A");
    }

    @Test
    @Sql(scripts = "/sql/legacy-season-with-playoff.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void givenLegacyMigratedSeasonWithPlayoff_thenRegularAndPlayoffTabs() {
        // given — Test-Legacy-Season-2097 + 1 PLAYOFF phase + 1 playoff bracket
        page.navigate(url("/admin/seasons/<UUID>"));

        assertThat(page.locator(".phase-tab:has-text('Regular')")).hasCount(1);
        assertThat(page.locator(".phase-tab:has-text('Playoff')")).hasCount(1);
        // ...same a-g asserts plus playoff-bracket page asserts
    }
}
```

**Executor notes:**
- The `<UUID>` placeholders must match the deterministic UUIDs hardcoded in the SQL fixtures (V4MigrationSmokeIT pattern: `00000000-0000-0061-0000-000000000010` etc.).
- `@Sql` requires `org.springframework.test.context.jdbc.Sql` import.
- E2E test extends `PlaywrightConfig` → runs in Failsafe (`-Pe2e`).

---

### `src/test/resources/sql/legacy-season-without-playoff.sql` (fixture, DDL/DML — CREATE)

**Analog:** `V4MigrationSmokeIT.java` `@BeforeEach seedSmokeTestData()` lines 60-88 (translate the JdbcTemplate calls to plain INSERT statements).

**Pattern to copy** (`V4MigrationSmokeIT.java` lines 62-88):
```java
jdbcTemplate.update(
    "INSERT INTO race_scorings (id, name, race_points, fastest_lap_points, created_at, updated_at) "
    + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
    SMOKE_RACE_SCORING_ID, "Phase57-Smoke-RaceScoring", "25,18,15,12,10", 0);

jdbcTemplate.update(
    "INSERT INTO seasons (id, name, season_year, season_number, format, legs, active, "
    + "race_scoring_id, match_scoring_id, created_at, updated_at) "
    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
    SMOKE_SEASON_ID, "Phase57-Smoke-Season", 2099, 99, "LEAGUE", 2, false,
    SMOKE_RACE_SCORING_ID, SMOKE_MATCH_SCORING_ID);

jdbcTemplate.update(
    "INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, "
    + "race_scoring_id, match_scoring_id, created_at, updated_at) "
    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
    SMOKE_PHASE_ID, SMOKE_SEASON_ID, 0, "REGULAR", "LEAGUE", "LEAGUE",
    SMOKE_RACE_SCORING_ID, SMOKE_MATCH_SCORING_ID);
```

**Executor template (post-V6 schema — note: `seasons.format/legs/...` already DROPPED; only identity fields remain):**
```sql
-- QUAL-03 fixture: legacy migrated season WITHOUT playoff (post-V6 schema)
-- Simulates a season that originally lived in pre-v1.9 flat shape, was migrated by V4
-- (REGULAR phase backfilled, scoring + format moved to phase, matchdays re-keyed),
-- and now exists in the post-V6 canonical form. Read-only assertions follow.
-- Deterministic UUIDs: 00000000-0000-0061-...

INSERT INTO race_scorings (id, name, race_points, fastest_lap_points, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000001', 'Phase61-RaceScoring', '25,18,15,12,10', 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO match_scorings (id, name, points_win, points_draw, points_loss, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000002', 'Phase61-MatchScoring', 3, 1, 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Season post-V6: only id/name/year/number/active fields (NO format/legs/scoring/dates)
INSERT INTO seasons (id, name, season_year, season_number, active, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000010', 'Test-Legacy-Season-2098', 2098, 1, false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- One REGULAR phase carrying scoring + legs (canonical post-Phase-57 form)
INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, legs,
                           race_scoring_id, match_scoring_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000011',
        '00000000-0000-0061-0000-000000000010',
        0, 'REGULAR', 'LEAGUE', 'LEAGUE', 2,
        '00000000-0000-0061-0000-000000000001',
        '00000000-0000-0061-0000-000000000002',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Teams + season_teams + phase_teams (LEAGUE layout, group_id NULL)
-- ... (planner fills in 2-3 teams + matchdays + races + race_results
--      — values chosen so standings produce a deterministic non-tied order)
```

---

### `src/test/resources/sql/legacy-season-with-playoff.sql` (fixture, DDL/DML — CREATE)

**Same analog/pattern** as above, additionally includes:
- 1 PLAYOFF phase row (`phase_type = 'PLAYOFF'`, `layout = 'BRACKET'`, `sort_index = 10`).
- 1 row in `playoffs` (linked to PLAYOFF phase via `phase_id`, NO `season_id` column anymore post-V6).
- 1 `playoff_rounds` + 1 `playoff_matchups` row.
- (Optional, for richness) 1 `playoff_seeds` row to exercise seed display.

**Note:** The fixture must NOT reference `playoff_seasons` (dropped in V6) or `playoffs.season_id` (dropped in V6).

---

## Cross-cutting Audit Targets (Plan 61-02 verification + grep-pass)

**Run these grep patterns across `src/main/java`, `src/main/resources/templates`, and `src/test/java` BEFORE/AFTER the cleanup:**

| Grep pattern | Where | Must be ZERO after cleanup |
|--------------|-------|----------------------------|
| `season\.getFormat\b` | src/main, src/test | yes |
| `season\.getLegs\b` | src/main, src/test | yes |
| `season\.getTotalRounds\b` | src/main, src/test | yes |
| `season\.getEventDurationMinutes\b` | src/main, src/test | yes |
| `season\.getStartDate\b` | src/main, src/test | yes |
| `season\.getEndDate\b` | src/main, src/test | yes |
| `season\.getRaceScoring\b` | src/main, src/test | yes |
| `season\.getMatchScoring\b` | src/main, src/test | yes |
| `playoff\.getSeasons\b` | src/main, src/test | yes |
| `playoff\.addSeason\b` (= `addSeasonToPlayoff` callers) | src/main, src/test | yes |
| `playoff\.removeSeason\b` (= `removeSeasonFromPlayoff` callers) | src/main, src/test | yes |
| `addSeasonToPlayoff\|removeSeasonFromPlayoff` | src/main, src/test | yes |
| `\.setSeason\(` on `Matchday`/`Playoff` literals | src/main, src/test | yes (Matchday.season + Playoff.season fields gone; only the convenience `getSeason()` remains) |
| `\$\{season\.(format\|legs\|totalRounds\|eventDurationMinutes\|startDate\|endDate\|raceScoring\|matchScoring)` | `src/main/resources/templates/**/*.html` | yes (already zero — re-verify) |
| `/admin/playoffs/.*/(add\|remove)-season` | src/main, src/test | yes (D-03 — old endpoints gone) |
| `playoff_seasons` (table name) | src/main/resources/db, src/test (except V3/V4/V5/V6 historical migrations themselves) | yes |
| `matchdays\.season_id\|playoffs\.season_id` (column name in JPQL or native SQL) | src/main, src/test (except the fixture SQL files themselves where they MUST be absent) | yes |

**The Matchday.getSeason() and Playoff.getSeason() Convenience-Getters MUST remain functional** (D-02) — verify that callers like `MatchService.java:38`, `MatchdayController.java:86,88`, `MatchdayService.java:123`, `RaceService.java:213`, `AbstractMatchdayGraphicService.java:46` keep working after cleanup.

---

## Shared Patterns (apply across multiple files)

### Convenience-Getter (D-02)

**Sources:** `Matchday.java` (new), `Playoff.java` (new), `Season.java` getTeams() (existing template at lines 107-116).

**Apply to:** Both `Matchday.getSeason()` and `Playoff.getSeason()` plus the new `Season.getMatchdays()` Derived-Getter (D-05).

```java
/**
 * Convenience getter — derives <X> via <Y>. The legacy <Z> bridge column was dropped in V6
 * (MIGR-06); the phase association is now the single source of truth.
 */
public Season getSeason() { return phase != null ? phase.getSeason() : null; }
```

### REGULAR-phase scoring lookup (D-08 compile-cascade)

**Source:** `PlayoffService.java` lines 63-77 (find-or-create REGULAR phase + scoring read).

**Apply to:** `CsvImportService.java:229,335`, `StandingsService.calculateStandingsLegacy` removal, `SeasonManagementService.java:144`, `SeasonController.java:208,229`, `TestHelper.createSeason` constructor.

```java
SeasonPhase regular = seasonPhaseService.findRegularPhase(seasonId);
RaceScoring rs = regular.getRaceScoring();
MatchScoring ms = regular.getMatchScoring();
int legs = regular.getLegs();
```

### @Service stereotype (Spring Boot conventions)

**Source:** `PlayoffService.java` lines 23-26 (`@Slf4j @Service @RequiredArgsConstructor`).

**Apply to:** all changes in `PlayoffService`, `PlayoffSeedingService`, `MatchService`, `MatchdayService`, `SeasonManagementService`, `StandingsService`, `CsvImportService`. Existing files already conform; no new annotations needed.

### Test-data isolation T-prefix (D-14, CLAUDE.md "Isolate Test Data Completely")

**Source:** `feedback_test_data_isolation` project memory + `AdminWorkflowE2ETest:175` (`T-ALF`, `Test-Season 2026`).

**Apply to:** GroupsSeasonE2ETest (`T-GA-1`, `T_groups_drv01`, year=2099) and Legacy fixtures (`T-LEG-A`, year=2098/2097).

### Playwright `assertThat(page.locator(...))` style

**Source:** `AdminWorkflowE2ETest.java` line 7 import + lines 28, 37-49 idiomatic locator chains. **Apply to:** both new E2E tests.

```java
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
// ...
assertThat(page.locator("h1")).containsText("Seasons");
assertThat(page.locator(".alert-success")).containsText("Season saved");
```

### TDD given-when-then naming (CLAUDE.md §Development Approach)

**Source:** `AdminWorkflowE2ETest.givenSeasonForm_whenSaveWithValidData_thenSeasonAppearsInList()` (line 53).

**Apply to:** all new test methods in V6MigrationTest, GroupsSeasonE2ETest, LegacyMigratedSeasonE2ETest.

---

## No Analog Found

| File | Reason |
|------|--------|
| `src/test/resources/sql/*.sql` | First `@Sql` fixture in this codebase. Closest analog is the JdbcTemplate-driven seeder pattern in `V4MigrationSmokeIT.seedSmokeTestData()` — must be transliterated to SQL INSERT scripts. |
| `src/test/java/db/migration/V6MigrationTest.java` | First Surefire-side migration test (V4MigrationSmokeIT runs as IT/Failsafe with seed; this one is read-only post-V6 verification under Surefire). Pattern is a hybrid of V4MigrationSmokeIT class header and V4MigrateSeasonsToPhasesIT INFORMATION_SCHEMA queries. |

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/{domain/model,domain/service,admin/controller,admin,dataimport}/`
- `src/main/resources/{db/migration,templates/admin}/`
- `src/test/java/{db/migration,org/ctc/e2e,org/ctc}/`
- `pom.xml`

**Files scanned:** ~40 (entities 4, services 7, controllers 2, migrations 3, templates audited 6, tests 6, fixtures 0, config 1).

**Pattern extraction date:** 2026-05-01.

**Project conventions binding:** CLAUDE.md (Java 25, Spring Boot 4.x, Lombok constructor injection, given-when-then BDD naming, T-prefix test isolation, `gsd/v1.9-season-phases-groups` branch).
