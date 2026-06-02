# Phase 113: Guest Assignment Foundation - Pattern Map

**Mapped:** 2026-06-01
**Files analyzed:** 8 (3 new, 5 modified) + 5 test files
**Analogs found:** 13 / 13

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/resources/db/migration/V18__add_race_lineups_is_guest.sql` | migration | batch | `V17__add_matches_walkover_team_id.sql` | role-match |
| `src/main/java/org/ctc/domain/model/RaceLineup.java` | model | CRUD | `RaceResult.java` (boolean field) | exact |
| `src/main/java/org/ctc/domain/service/RaceLineupService.java` | service | CRUD | itself + `RaceService.saveResults` (aggregate pattern) | exact |
| `src/main/java/org/ctc/admin/controller/RaceLineupController.java` | controller | request-response | itself (extend `driver_*` param loop) | exact |
| `src/main/resources/templates/admin/race-lineup.html` | template | request-response | itself (extend) + `race-form.html` searchable-dropdown | exact |
| `src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java` | utility | batch | `RaceResultRestorer.java` (boolean binding) | exact |
| `src/main/java/org/ctc/backup/serialization/RaceLineupMixIn.java` | utility | transform | itself (no change needed — auto-serialized) | exact |
| `src/test/java/db/migration/V18MigrationIT.java` | test | request-response | `V17MigrationIT.java` | exact |
| `src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java` | test | CRUD | itself (extend existing tests) | exact |
| `src/test/java/org/ctc/admin/controller/RaceLineupControllerTest.java` | test | request-response | itself (extend existing IT) | exact |
| `src/test/java/org/ctc/backup/restore/entity/RaceLineupRestorerTest.java` | test | batch | itself (extend existing unit test) | exact |
| `src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java` | test | CRUD | `RaceLineupServiceTest.java` (MockitoExtension pattern) | role-match |

---

## Pattern Assignments

### `src/main/resources/db/migration/V18__add_race_lineups_is_guest.sql` (migration, batch)

**Analog:** `src/main/resources/db/migration/V17__add_matches_walkover_team_id.sql` (ALTER TABLE shape) + `src/main/resources/db/migration/V1__initial_schema.sql` (BOOLEAN syntax)

**V17 ALTER TABLE shape** (lines 1-3):
```sql
ALTER TABLE matches ADD COLUMN walkover_team_id UUID NULL;
ALTER TABLE matches ADD CONSTRAINT fk_match_walkover_team FOREIGN KEY (walkover_team_id) REFERENCES teams(id);
CREATE INDEX IF NOT EXISTS idx_matches_walkover_team_id ON matches(walkover_team_id);
```

**V1 BOOLEAN NOT NULL DEFAULT FALSE syntax** (lines 32, 155, 255):
```sql
active BOOLEAN NOT NULL DEFAULT FALSE,
bye BOOLEAN DEFAULT FALSE NOT NULL,
fastest_lap BOOLEAN NOT NULL DEFAULT FALSE,
```

**Adaptation:** V18 contains exactly ONE statement — no constraint (already exists in V1 as `uk_race_lineup_driver`), no index:
```sql
ALTER TABLE race_lineups ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE;
```

**Critical constraint:** The `UNIQUE(race_id, driver_id)` constraint `uk_race_lineup_driver` already exists in V1 line 277. Adding it again in V18 causes a migration failure. V18 must contain only the single `ALTER TABLE ADD COLUMN` statement above.

---

### `src/main/java/org/ctc/domain/model/RaceLineup.java` (model, CRUD)

**Analog:** `src/main/java/org/ctc/domain/model/RaceResult.java`

**Boolean field pattern from RaceResult** (lines 46-47):
```java
@Column(nullable = false)
private boolean fastestLap;
```

**Adaptation — add `is_guest` with explicit `@Column(name=...)` (Pitfall 2: Hibernate maps `boolean guest` to `guest`, not `is_guest`):**
```java
@Column(name = "is_guest", nullable = false)
private boolean guest;
```

**Existing 3-arg constructor to keep intact** (lines 38-42):
```java
public RaceLineup(Race race, Driver driver, Team team) {
    this.race = race;
    this.driver = driver;
    this.team = team;
}
```

**New 4-arg constructor to add (guest entries):**
```java
public RaceLineup(Race race, Driver driver, Team team, boolean guest) {
    this.race = race;
    this.driver = driver;
    this.team = team;
    this.guest = guest;
}
```

**Lombok already on class** (lines 13-16): `@Getter @Setter @NoArgsConstructor @ToString(exclude = {"race", "driver", "team"})` — no annotation changes needed. Lombok generates `isGuest()` / `setGuest()` from the `boolean guest` field.

---

### `src/main/java/org/ctc/domain/service/RaceLineupService.java` (service, CRUD)

**Analog for delete-all-then-recreate:** itself (lines 75-95)
**Analog for aggregateMatchScores:** `src/main/java/org/ctc/domain/service/RaceService.java` lines 254-286

**Current constructor injection** (lines 21-25):
```java
private final RaceRepository raceRepository;
private final RaceLineupRepository raceLineupRepository;
private final SeasonDriverRepository seasonDriverRepository;
private final TeamRepository teamRepository;
private final DriverRepository driverRepository;
```

**New `final` fields to add for D-11 cascade:**
```java
private final RaceResultRepository raceResultRepository;
private final ScoringService scoringService;
```

**Current `saveLineup` signature** (line 76):
```java
public int saveLineup(UUID raceId, Map<UUID, UUID> driverTeamAssignments)
```

**Adaptation — extend to 3-arg and add 2-arg overload for backward-compat (Pitfall 6: `TestDataService` calls the old 2-arg):**
```java
@Transactional
public int saveLineup(UUID raceId, Map<UUID, UUID> rosterAssignments) {
    return saveLineup(raceId, rosterAssignments, Map.of());
}

@Transactional
public int saveLineup(UUID raceId, Map<UUID, UUID> rosterAssignments, Map<UUID, UUID> guestAssignments) { ... }
```

**Current delete-all-then-recreate core** (lines 80-93):
```java
var existing = raceLineupRepository.findByRaceId(raceId);
raceLineupRepository.deleteAll(existing);

int count = 0;
for (var entry : driverTeamAssignments.entrySet()) {
    var driver = driverRepository.findById(entry.getKey())
            .orElseThrow(() -> new EntityNotFoundException("Driver", entry.getKey()));
    var team = teamRepository.findById(entry.getValue())
            .orElseThrow(() -> new EntityNotFoundException("Team", entry.getValue()));
    raceLineupRepository.save(new RaceLineup(race, driver, team));
    count++;
}
log.info("Saved {} lineup entries for race {}", count, raceId);
```

**aggregateMatchScores call pattern from RaceService.saveResults** (lines 273-276):
```java
raceRepository.save(race);
if (race.getResults().isEmpty()) {
    scoringService.recomputeMatchScoresFromAllLegs(race);
} else {
    scoringService.aggregateMatchScores(race);
}
```

**Adaptation for D-11 cascade (detect dropped guests before deleteAll, then cascade-delete):**
```java
var existing = raceLineupRepository.findByRaceId(raceId);
var droppedGuestDriverIds = existing.stream()
    .filter(RaceLineup::isGuest)
    .map(rl -> rl.getDriver().getId())
    .filter(id -> !guestAssignments.containsKey(id))
    .toList();

raceLineupRepository.deleteAll(existing);

// ... recreate roster entries with 3-arg constructor (is_guest = false) ...
// ... recreate guest entries with 4-arg constructor (is_guest = true) ...

for (var driverId : droppedGuestDriverIds) {
    raceResultRepository.findByRaceIdAndDriverId(raceId, driverId)
        .ifPresent(raceResultRepository::delete);
}
if (!droppedGuestDriverIds.isEmpty()) {
    scoringService.aggregateMatchScores(race);
}
```

**Current `getDriverAssignments`** (lines 66-73) — must filter out guests (Pitfall 4):
```java
public Map<UUID, UUID> getDriverAssignments(UUID raceId) {
    var existingLineups = raceLineupRepository.findByRaceId(raceId);
    var assignments = new HashMap<UUID, UUID>();
    for (var lineup : existingLineups) {
        assignments.put(lineup.getDriver().getId(), lineup.getTeam().getId());
    }
    return assignments;
}
```
**Adaptation:** add `.filter(lu -> !lu.isGuest())` before the for-loop (or stream-based equivalent).

**New `getGuestLineups` method (D-10 prefill):**
```java
public List<RaceLineup> getGuestLineups(UUID raceId) {
    return raceLineupRepository.findByRaceId(raceId).stream()
        .filter(RaceLineup::isGuest)
        .toList();
}
```

---

### `src/main/java/org/ctc/admin/controller/RaceLineupController.java` (controller, request-response)

**Analog:** itself

**Current GET handler** (lines 25-39):
```java
@GetMapping("/{raceId}/lineup")
public String lineup(@PathVariable UUID raceId, Model model) {
    var data = raceLineupService.getLineupData(raceId);
    var teamEntries = new ArrayList<RaceLineupService.LineupTeamEntry>();
    if (data.homeEntry() != null) teamEntries.add(data.homeEntry());
    if (data.awayEntry() != null) teamEntries.add(data.awayEntry());

    model.addAttribute("race", data.race());
    model.addAttribute("teamEntries", teamEntries);
    model.addAttribute("driverAssignments", raceLineupService.getDriverAssignments(raceId));
    return "admin/race-lineup";
}
```

**Adaptation — add `guestLineups` and `allDrivers` to model:**
```java
model.addAttribute("guestLineups", raceLineupService.getGuestLineups(raceId));
model.addAttribute("allDrivers", driverService.findAll());
```
Requires injecting `DriverService driverService` as a new `final` field.

**Current POST handler param loop** (lines 43-58):
```java
@PostMapping("/{raceId}/lineup")
public String saveLineup(@PathVariable UUID raceId,
                         @RequestParam Map<String, String> params,
                         RedirectAttributes redirectAttributes) {
    var driverTeamAssignments = new HashMap<UUID, UUID>();
    for (var entry : params.entrySet()) {
        if (!entry.getKey().startsWith("driver_") || !hasText(entry.getValue())) {
            continue;
        }
        UUID driverId = UUID.fromString(entry.getKey().substring("driver_".length()));
        UUID teamId = UUID.fromString(entry.getValue());
        driverTeamAssignments.put(driverId, teamId);
    }
    int count = raceLineupService.saveLineup(raceId, driverTeamAssignments);
    redirectAttributes.addFlashAttribute("successMessage", "Lineup saved: " + count + " drivers assigned");
    return "redirect:/admin/races/" + raceId + "/lineup";
}
```

**Adaptation — add `guest_*` namespace alongside `driver_*`:**
```java
var rosterAssignments = new HashMap<UUID, UUID>();
var guestAssignments  = new HashMap<UUID, UUID>();
for (var entry : params.entrySet()) {
    if (entry.getKey().startsWith("driver_") && hasText(entry.getValue())) {
        rosterAssignments.put(
            UUID.fromString(entry.getKey().substring("driver_".length())),
            UUID.fromString(entry.getValue()));
    } else if (entry.getKey().startsWith("guest_") && hasText(entry.getValue())) {
        guestAssignments.put(
            UUID.fromString(entry.getKey().substring("guest_".length())),
            UUID.fromString(entry.getValue()));
    }
}
int count = raceLineupService.saveLineup(raceId, rosterAssignments, guestAssignments);
```

---

### `src/main/resources/templates/admin/race-lineup.html` (template, request-response)

**Analog:** itself (existing roster table structure, lines 13-52) + `race-form.html` (searchable-dropdown hidden-field pattern) + `searchable-dropdown.js` (hidden-field wiring JS)

**Existing per-team roster table structure** (lines 13-51) — unchanged; Add-Guest block appended inside same `th:each="entry : ${teamEntries}"` div:
```html
<div th:each="entry : ${teamEntries}" class="card mb-md">
    <h2 th:text="${entry.team.shortName + ' — ' + entry.team.name}"></h2>
    <!-- ... existing roster table ... -->
</div>
```

**Existing sub-team select pattern** (lines 32-40) — same `entry.subTeams` used for guests:
```html
<select th:name="'driver_' + ${sd.driver.id}">
    <option value="">-- Not assigned --</option>
    <option th:each="sub : ${entry.subTeams}"
            th:value="${sub.id}"
            th:text="${sub.shortName}"
            th:selected="${driverAssignments.get(sd.driver.id) == sub.id}">
    </option>
</select>
```

**Existing checkbox pattern** (lines 43-48):
```html
<input type="checkbox"
       th:name="'driver_' + ${sd.driver.id}"
       th:value="${entry.team.id}"
       th:checked="${driverAssignments.containsKey(sd.driver.id)}">
```

**searchable-dropdown.js hidden-field wiring pattern** (lines 1-45 of `searchable-dropdown.js`):
```javascript
var input = container.querySelector('.dropdown-input');
var hidden = container.querySelector('input[type="hidden"]');
// On item click:
input.value = this.dataset.label;
hidden.value = this.dataset.id;
```

**race-form.html `<datalist>` analog — hidden + text input pair** (lines 42-43):
```html
<input type="text" id="trackSearch" placeholder="Search tracks..."
       autocomplete="off" class="dropdown-input">
<input type="hidden" name="trackId" th:value="${raceForm.trackId}" id="trackId">
```

**Adaptation — Add-Guest block structure per team:**

Datalist rendered once outside the team loop:
```html
<datalist id="guestDriverList">
    <option th:each="d : ${allDrivers}"
            th:value="${d.psnId + ' (' + d.nickname + ')'}"
            th:attr="data-id=${d.id}">
    </option>
</datalist>
```

Per-team Add-Guest block (appended inside `th:each="entry : ${teamEntries}"` after existing roster table):
```html
<div class="card mb-md guest-section">
    <h3>Guest Drivers — <span th:text="${entry.team.shortName}"></span></h3>

    <!-- Prefilled existing guest rows (D-10): filter guestLineups by team -->
    <div th:each="gl : ${guestLineups}"
         th:if="${gl.team.id == entry.team.id
                  or (gl.team.parentTeam != null and gl.team.parentTeam.id == entry.team.id)}">
        <input type="text" list="guestDriverList"
               th:value="${gl.driver.psnId + ' (' + gl.driver.nickname + ')'}"
               placeholder="Search driver..."
               class="guest-driver-input">
        <input type="hidden"
               th:name="'guest_' + ${gl.driver.id}"
               th:value="${gl.team.id}"
               class="guest-driver-id">
        <select th:if="${entry.hasSubTeams}">
            <option value="">-- Sub-team --</option>
            <option th:each="sub : ${entry.subTeams}"
                    th:value="${sub.id}"
                    th:text="${sub.shortName}"
                    th:selected="${gl.team.id == sub.id}">
            </option>
        </select>
    </div>

    <!-- Blank new-guest row -->
    <div class="guest-row">
        <input type="text" list="guestDriverList"
               placeholder="Search driver..."
               class="guest-driver-input">
        <input type="hidden" name="" value="" class="guest-driver-id">
        <select th:if="${entry.hasSubTeams}">
            <option value="">-- Sub-team --</option>
            <option th:each="sub : ${entry.subTeams}"
                    th:value="${sub.id}" th:text="${sub.shortName}">
            </option>
        </select>
    </div>
</div>
```

**D-06 datalist→driverId JS resolution (bespoke, no framework — analogous to `searchable-dropdown.js` hidden-field pattern):**
```javascript
document.querySelectorAll('.guest-driver-input').forEach(function(input) {
    input.addEventListener('change', function() {
        var val = this.value;
        var opt = document.querySelector('#guestDriverList option[value="' + CSS.escape(val) + '"]');
        var hidden = this.nextElementSibling;
        if (opt) {
            hidden.value = /* teamId set by sub-team select or entry.team.id */;
            hidden.name  = 'guest_' + opt.dataset.id;
        } else {
            hidden.value = '';
            hidden.name  = '';
        }
    });
});
```

---

### `src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java` (utility, batch)

**Analog:** `src/main/java/org/ctc/backup/restore/entity/RaceResultRestorer.java`

**RaceResultRestorer boolean binding pattern** (lines 34-60):
```java
private static final String INSERT_SQL =
        "INSERT INTO race_results (id, race_id, driver_id, position, quali_position, "
      + "fastest_lap, points_race, points_quali, points_fl, points_total, "
      + "created_at, updated_at) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

// In batchUpdate setter:
ps.setBoolean(6, row.get("fastestLap").asBoolean());
```

**Current RaceLineupRestorer INSERT SQL** (lines 32-35):
```java
private static final String INSERT_SQL =
        "INSERT INTO race_lineups (id, race_id, driver_id, team_id, "
      + "created_at, updated_at) "
      + "VALUES (?, ?, ?, ?, ?, ?)";
```

**Current parameter binding** (lines 44-51):
```java
ps.setObject(1, UUID.fromString(row.get("id").asText()));
ps.setObject(2, UUID.fromString(row.get("race").asText()));
ps.setObject(3, UUID.fromString(row.get("driver").asText()));
ps.setObject(4, UUID.fromString(row.get("team").asText()));
ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
```

**Adaptation — add `is_guest` at position 5 (shifting timestamps to 6, 7):**
```java
private static final String INSERT_SQL =
        "INSERT INTO race_lineups (id, race_id, driver_id, team_id, is_guest, "
      + "created_at, updated_at) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?)";

// In setter:
ps.setObject(1, UUID.fromString(row.get("id").asText()));
ps.setObject(2, UUID.fromString(row.get("race").asText()));
ps.setObject(3, UUID.fromString(row.get("driver").asText()));
ps.setObject(4, UUID.fromString(row.get("team").asText()));
ps.setBoolean(5, row.path("guest").asBoolean(false));  // row.path() not row.get() — backward-compat for old backups without field
ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
```

**Key difference from RaceResultRestorer:** Use `row.path("guest").asBoolean(false)` (not `row.get("guest").asBoolean()`) to gracefully handle old backup files that predate the `is_guest` column.

---

### `src/main/java/org/ctc/backup/serialization/RaceLineupMixIn.java` (utility, transform)

**Current state** (lines 1-27): `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` — does NOT list `guest`. Jackson will auto-serialize the new `boolean guest` field (Lombok-generated `isGuest()`) as `"guest": false/true` in backup JSON without any MixIn change.

**No modification needed.** Verify assumption A3 by running `BackupImportMariaDbSmokeIT` after implementation.

---

## Test Pattern Assignments

### `src/test/java/db/migration/V18MigrationIT.java` (test, integration — NEW)

**Analog:** `src/test/java/db/migration/V17MigrationIT.java` — exact pattern to copy

**V17MigrationIT structure** (lines 1-82):
```java
package db.migration;

@CtcDevSpringBootContext
@Tag("integration")
class V17MigrationIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void givenH2WithV17Applied_whenInspectingMatchesWalkoverTeamIdColumn_thenExists() throws Exception {
        Set<String> columns = collectColumnNames("MATCHES");
        assertThat(columns).contains("walkover_team_id");
    }

    @Test
    void givenH2WithV17Applied_whenInspectingWalkoverTeamIdNullability_thenNullable() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            assertColumnNullable(md, "MATCHES", "WALKOVER_TEAM_ID");
        }
    }

    // collectColumnNames helper reads DatabaseMetaData.getColumns()
    // assertColumnNullable helper checks DatabaseMetaData.columnNullable
}
```

**Adaptation:** V18 checks `RACE_LINEUPS.is_guest` is `NOT NULL` (not nullable — opposite of V17's nullable test):
```java
@CtcDevSpringBootContext
@Tag("integration")
class V18MigrationIT {

    @Test
    void givenH2WithV18Applied_whenInspectingRaceLineupsIsGuestColumn_thenExists() throws Exception {
        assertThat(collectColumnNames("RACE_LINEUPS")).contains("is_guest");
    }

    @Test
    void givenH2WithV18Applied_whenInspectingIsGuestNullability_thenNotNullable() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "RACE_LINEUPS", "IS_GUEST")) {
                assertThat(rs.next()).as("is_guest column must exist").isTrue();
                assertThat(rs.getInt("NULLABLE"))
                    .as("is_guest must be NOT NULL")
                    .isEqualTo(DatabaseMetaData.columnNoNulls);
            }
        }
    }

    // same collectColumnNames helper as V17MigrationIT
}
```

---

### `src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java` (test, CRUD — EXTEND)

**Existing structure** (lines 1-228): `@ExtendWith(MockitoExtension.class)`, `@InjectMocks RaceLineupService`, mocks for all current dependencies.

**New mocks to add** (D-11 cascade):
```java
@Mock
private RaceResultRepository raceResultRepository;
@Mock
private ScoringService scoringService;
```

**Existing test method pattern to follow** (lines 38-64):
```java
@Test
void givenDriverTeamMapping_whenSaveLineup_thenCreatesEntries() {
    // given
    var raceId = UUID.randomUUID();
    // ... setup ...
    when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
    when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of());
    when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
    when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
    when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

    // when
    int count = service.saveLineup(raceId, Map.of(driverId, teamId));

    // then
    assertThat(count).isEqualTo(1);
    verify(raceLineupRepository).save(any(RaceLineup.class));
}
```

**New test methods to add:**
- `givenGuestAssignment_whenSaveLineup_thenGuestEntryHasIsGuestTrue()` — verify `isGuest()` is `true` on saved `RaceLineup`
- `givenGuestRemovedFromResubmit_whenSaveLineup_thenCascadeDeletesRaceResult()` — verify `raceResultRepository.delete()` called
- `givenGuestRemovedFromResubmit_whenSaveLineup_thenAggregatesMatchScores()` — verify `scoringService.aggregateMatchScores()` called
- `givenLineupWithGuest_whenGetDriverAssignments_thenGuestExcluded()` — verify guest not in roster map (Pitfall 4)
- `givenLineupWithGuest_whenGetGuestLineups_thenOnlyGuestsReturned()` — verify new accessor

---

### `src/test/java/org/ctc/admin/controller/RaceLineupControllerTest.java` (test, request-response — EXTEND)

**Existing structure** (lines 1-105): `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional`, `TestHelper.createFullSeasonFixture("Test_Lineup")` in `@BeforeEach`.

**Existing POST test pattern** (lines 53-71):
```java
@Test
void givenTwoDriversAssigned_whenSaveLineup_thenRedirectsAndPersistsTwoEntries() throws Exception {
    // given
    var driver1 = testHelper.createDriver("Test_lineup_d1", "Test Lineup Driver 1");
    // ...
    // when
    mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup")
            .param("driver_" + driver1.getId(), fixture.homeTeam().getId().toString())
            .param("driver_" + driver2.getId(), fixture.awayTeam().getId().toString()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/races/" + fixture.race().getId() + "/lineup"))
        .andExpect(flash().attributeExists("successMessage"));
    // then
    var lineups = raceLineupRepository.findByRaceId(fixture.race().getId());
    assertEquals(2, lineups.size());
}
```

**Existing GET assertion** (lines 43-50):
```java
mockMvc.perform(get("/admin/races/" + fixture.race().getId() + "/lineup"))
    .andExpect(status().isOk())
    .andExpect(view().name("admin/race-lineup"))
    .andExpect(model().attributeExists("race", "teamEntries", "driverAssignments"));
```

**New test methods to add:**
- `givenGuestParam_whenSaveLineup_thenPersistsGuestEntry()` — POST with `guest_<uuid>`, verify `isGuest() == true` in DB
- `givenGuestRemoved_whenSaveLineup_thenGuestRaceResultDeleted()` — requires pre-creating a RaceResult for the guest
- `givenExistingGuest_whenGetLineupPage_thenModelContainsGuestLineups()` — GET asserts `model().attributeExists("guestLineups", "allDrivers")`

---

### `src/test/java/org/ctc/backup/restore/entity/RaceLineupRestorerTest.java` (test, batch — EXTEND)

**Existing SQL assertion pattern** (lines 60-69):
```java
assertThat(sqlCaptor.getValue())
    .matches("^INSERT INTO race_lineups \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
    .contains("race_id")
    .contains("driver_id")
    .contains("team_id");
```

**Existing JDBC binding assertion pattern** (lines 71-78):
```java
PreparedStatement ps = mock(PreparedStatement.class);
setterCaptor.getValue().setValues(ps, row);
verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
// ...
verify(ps).setTimestamp(5, Timestamp.valueOf("2025-01-15 10:00:00"));
verify(ps).setTimestamp(6, Timestamp.valueOf("2025-01-16 11:30:00"));
```

**Adaptation:** Update SQL assertion to include `is_guest`; update JDBC verifications for new position 5 (`setBoolean`) and shifted positions 6, 7 for timestamps; add a second test for backward-compat with old backup (no `guest` field):
```java
// Existing row JSON must gain "guest": true
// Updated assertions:
assertThat(sqlCaptor.getValue()).contains("is_guest");
verify(ps).setBoolean(5, true);  // or false
verify(ps).setTimestamp(6, Timestamp.valueOf("2025-01-15 10:00:00"));
verify(ps).setTimestamp(7, Timestamp.valueOf("2025-01-16 11:30:00"));

// New backward-compat test: row without "guest" field → setBoolean(5, false)
```

---

### `src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java` (test, CRUD — NEW)

**Analog:** `src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java` (Mockito unit test structure)

**Pattern to copy** (lines 1-35):
```java
package org.ctc.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceFormDataServiceTest {

    @Mock
    private RaceLineupRepository raceLineupRepository;
    // ... other mocks as needed by RaceFormDataService constructor

    @InjectMocks
    private RaceFormDataService service;
```

**Test to add for GUEST-02 (auto-derive guest in results form):**
```java
@Test
void givenLineupWithGuestEntry_whenPopulateDrivers_thenGuestAppearsInResultsForm()
```
Verify that a `RaceLineup` with `isGuest() == true` is included in the `populateDrivers` result list (the method uses `raceLineupRepository.findByRaceId(raceId)` without filtering on `isGuest` — guests auto-surface in results form).

---

## Shared Patterns

### `@Transactional` on service write methods
**Source:** `src/main/java/org/ctc/domain/service/RaceLineupService.java` line 75, `RaceService.saveResults` line 254
**Apply to:** `RaceLineupService.saveLineup` (already present, keep on both overloads)
```java
@Transactional
public int saveLineup(UUID raceId, Map<UUID, UUID> rosterAssignments, Map<UUID, UUID> guestAssignments) { ... }
```

### `scoringService.aggregateMatchScores(race)` after result mutation
**Source:** `src/main/java/org/ctc/domain/service/RaceService.java` lines 276, 295
**Apply to:** `RaceLineupService.saveLineup` D-11 cascade path
```java
if (!droppedGuestDriverIds.isEmpty()) {
    scoringService.aggregateMatchScores(race);
}
```

### EntityNotFoundException pattern for repository lookups
**Source:** `src/main/java/org/ctc/domain/service/RaceLineupService.java` lines 79, 85-88
**Apply to:** All new `driverRepository.findById()` / `teamRepository.findById()` calls in extended `saveLineup`
```java
.orElseThrow(() -> new EntityNotFoundException("Driver", entry.getKey()))
```

### Flash attribute pattern for POST redirect
**Source:** `src/main/java/org/ctc/admin/controller/RaceLineupController.java` line 57
**Apply to:** POST handler remains unchanged in success path:
```java
redirectAttributes.addFlashAttribute("successMessage", "Lineup saved: " + count + " drivers assigned");
return "redirect:/admin/races/" + raceId + "/lineup";
```

### Lombok annotation order on Spring components
**Source:** `src/main/java/org/ctc/domain/service/RaceLineupService.java` lines 17-19; `RaceLineupController.java` lines 9-10
**Apply to:** Any new `@RequiredArgsConstructor` + `@Slf4j` components
```java
@Slf4j
@Service
@RequiredArgsConstructor
// or:
@Slf4j
@Controller
@RequestMapping(...)
@RequiredArgsConstructor
```

### `@Tag("integration")` on `*IT.java` files
**Source:** `src/test/java/db/migration/V17MigrationIT.java` line 18
**Apply to:** `V18MigrationIT.java` — mandatory per CLAUDE.md "Tag Tests by Category"

### `@CtcDevSpringBootContext` on migration ITs
**Source:** `src/test/java/db/migration/V17MigrationIT.java` line 17
**Apply to:** `V18MigrationIT.java`

### Given-When-Then test method naming
**Source:** `src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java` line 39 (`givenDriverTeamMapping_whenSaveLineup_thenCreatesEntries`)
**Apply to:** All new test methods

---

## No Analog Found

None — all files have close analogs in the codebase.

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/`, `src/main/resources/`, `src/test/java/`
**Files scanned:** 13 source files read directly
**Pattern extraction date:** 2026-06-01
