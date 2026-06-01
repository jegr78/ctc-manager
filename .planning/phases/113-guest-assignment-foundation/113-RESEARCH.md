# Phase 113: Guest Assignment Foundation - Research

**Researched:** 2026-06-01
**Domain:** JPA/Spring Boot entity flag, Flyway migration, Thymeleaf form, service CRUD, backup restorer
**Confidence:** HIGH (all findings verified by direct codebase inspection)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Guest flag lives **only on `RaceLineup`** — new boolean column `is_guest` (default `false`). `RaceResult` gets **no** guest column.
- **D-02:** Schema changes in new migration **V18** (V18 confirmed free; highest existing is V17). H2 + MariaDB compatible. V1–V17 untouched.
- **D-03:** Add **`UNIQUE(race_id, driver_id)` on `race_lineups`** plus service-layer dedup validation. ⚠ Must verify no existing duplicate rows before adding the constraint.
- **D-04/05:** Per-team **"Add Guest Driver" block** using a **native HTML `<datalist>` typeahead** (`<input list=...>`). No new search endpoint, no JS framework.
- **D-06 (Claude's Discretion):** `<datalist>`→driverId resolution mechanism.
- **D-07:** Guest's fielding team restricted to the race's two teams only.
- **D-08:** For teams with sub-teams, Add-Guest block includes a sub-team `<select>`.
- **D-09:** Guest rows post in `guest_<driverId>=teamId` param namespace. `saveLineup` keeps delete-all-then-recreate; sets `is_guest=true` for guests.
- **D-10:** Re-edit prefill: lineup service returns a separate list of existing guests per team; template prefills them in the Add-Guest block.
- **D-11:** Guest removal cascades to guest's `RaceResult` for that race, then calls `scoringService.aggregateMatchScores(race)`.

### Claude's Discretion

- Exact `<datalist>`→`driverId` resolution mechanism (D-06).
- Exact service/record signatures and whether the guest accessor extends `getLineupData` or is a new method.
- Whether `RaceLineup` gets a 4-arg constructor `(race, driver, team, isGuest)` or a setter; keep existing 3-arg constructor working for roster callers.

### Deferred Ideas (OUT OF SCOPE)

- Guest scoring & personal driver-ranking crediting — Phase 114 (SCORE-01..03).
- Visual guest marking across Scorecard, Provisional Scores, matchday graphics, admin race detail, driver-ranking, driver-profile — Phase 115 (MARK-01..06).

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GUEST-01 | Admin can add a guest driver to a race lineup from any driver in the system, specifying fielding team | D-04/D-05/D-09: new "Add Guest" block + datalist + `guest_*` params in `saveLineup` |
| GUEST-02 | Admin can record a guest's finishing position/result | Auto-derived: `RaceFormDataService.populateDrivers` reads lineup regardless of guest flag — no results-form changes needed |
| GUEST-03 | Admin can edit or remove a guest-driver assignment | D-09 delete-all-then-recreate; D-11 cascade-delete `RaceResult` on guest removal |
| GUEST-04 | Lineup/result entry persistently identifiable as guest in data model | D-01/D-02: `is_guest BOOLEAN NOT NULL DEFAULT FALSE` on `race_lineups` via Flyway V18 |

</phase_requirements>

---

## Summary

Phase 113 adds a guest-driver assignment capability to race lineups. The change surface is: one Flyway migration (V18), one entity field on `RaceLineup`, two service changes (`RaceLineupService.saveLineup` + a new guest-prefill accessor), one controller change (`RaceLineupController` parsing `guest_*` params), one template change (`race-lineup.html` Add-Guest block per team), and two backup artifact updates (`RaceLineupRestorer` INSERT SQL + `RaceLineupRestorerTest`).

**Critical finding — D-03 is partially resolved by V1:** The `UNIQUE(race_id, driver_id)` constraint already exists on `race_lineups` as `uk_race_lineup_driver` in `V1__initial_schema.sql`. V18 does NOT need to add it again — only the `is_guest` column is new. The service-layer dedup validation (rejecting duplicate driverIds across both namespaces) is still needed, but the DB-level uniqueness is already enforced.

**Critical finding — existing searchable-dropdown pattern:** The project already ships `searchable-dropdown.js` (text input + hidden UUID field + filterable dropdown list) used in `race-form.html`. This pattern provides the driverId resolution D-06 asks for out of the box. D-05 locked "native `<datalist>`" — the planner must implement exactly that per the locked decision, but the JS hidden-field resolution mechanism from `searchable-dropdown.js` is available as a reference if a small amount of bespoke JS is needed for the guest block.

**Primary recommendation:** Implement V18 with only `ALTER TABLE race_lineups ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE` (constraint already exists). Wire `is_guest` into `RaceLineup` entity, `RaceLineupRestorer` INSERT SQL, `saveLineup` (new `guestAssignments` param map), a new `getGuestAssignments` method for prefill, and the template Add-Guest block per team using `<datalist>` over all drivers. Add `ScoringService` and `RaceResultRepository` injection to `RaceLineupService` for the D-11 cascade.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Guest flag persistence (DB column) | Database/Storage | — | Flyway V18; `race_lineups.is_guest` boolean |
| Guest flag on entity | API/Backend (JPA) | — | `RaceLineup.isGuest` field + Lombok getter/setter |
| Guest save/validate/cascade | API/Backend (Service) | — | `RaceLineupService.saveLineup` owns the delete-all-recreate flow |
| Guest param parsing | API/Backend (Controller) | — | `RaceLineupController.saveLineup` parses `guest_*` params |
| Guest CRUD UI (datalist, sub-team select, prefill) | Frontend Server (Thymeleaf SSR) | — | `race-lineup.html`; server-renders `<datalist>` with all drivers |
| Results auto-derive from lineup | API/Backend (Service) | — | `RaceFormDataService.populateDrivers` — no change for GUEST-02 |
| Cascade-delete on guest removal | API/Backend (Service) | — | `saveLineup` detects dropped guest UUIDs, deletes `RaceResult` rows, calls `aggregateMatchScores` |
| Backup round-trip | Database/Storage | — | `RaceLineupRestorer` + `RaceLineupRestorerTest` must include `is_guest` column |

---

## Standard Stack

No new external libraries. All capabilities use the existing stack.

### Core (existing, no additions)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot 4.x / Spring Data JPA | project-managed | Entity persistence, repositories | Project stack |
| Flyway | project-managed | Schema migration V18 | CLAUDE.md "Do Not Modify Flyway Migrations" |
| Thymeleaf | project-managed | SSR template, `<datalist>` rendering | CLAUDE.md "no frontend build tool" |
| Lombok | project-managed | `@Getter @Setter` on `RaceLineup` | CLAUDE.md Lombok conventions |
| JUnit 5 + AssertJ + Mockito | project-managed | Unit + integration tests | CLAUDE.md "TDD" |

**Installation:** None — no new dependencies.

---

## Package Legitimacy Audit

Not applicable — no external packages are added in this phase.

---

## Architecture Patterns

### System Architecture Diagram

```
Admin POST /admin/races/{id}/lineup
    │
    ▼
RaceLineupController.saveLineup()
    ├── parse driver_<uuid>=teamId  (roster entries, is_guest=false)
    └── parse guest_<uuid>=teamId   (guest entries,  is_guest=true)
         │
         ▼
    RaceLineupService.saveLineup(raceId, rosterAssignments, guestAssignments)
         ├── raceLineupRepository.deleteAll(existing)
         ├── for each roster entry → new RaceLineup(race, driver, team) [is_guest defaults false]
         ├── for each guest entry  → new RaceLineup(race, driver, team, true)
         ├── detect dropped guests (old guest UUIDs not in new guestAssignments)
         │       └── raceResultRepository.findByRaceIdAndDriverId() → delete → save
         └── scoringService.aggregateMatchScores(race)   [if any guest was removed]

Admin GET /admin/races/{id}/lineup
    │
    ▼
RaceLineupController.lineup()
    ├── raceLineupService.getLineupData(raceId)           → race, teamEntries (roster)
    ├── raceLineupService.getDriverAssignments(raceId)    → driver→team map (roster only, is_guest=false)
    ├── raceLineupService.getGuestLineups(raceId)         → List<RaceLineup> where is_guest=true
    └── driverService.findAll()                           → allDrivers for <datalist>
    model: race, teamEntries, driverAssignments, guestLineups, allDrivers
         │
         ▼
    race-lineup.html
         ├── per-team roster table (unchanged)
         └── per-team "Add Guest" block
               ├── existing guests (prefilled from guestLineups filtered by team)
               │     ├── <input list="driverList"> prefilled with psnId
               │     ├── <input type="hidden" name="guest_<driverId>"> prefilled with teamId
               │     └── sub-team <select> (if entry.hasSubTeams)
               ├── blank new-guest row (empty input + hidden)
               └── [Add another guest] button (JS clone or static extra rows)
         └── <datalist id="driverList"> rendered once with all drivers

Race results form (GUEST-02 — unchanged):
RaceFormDataService.populateDrivers()
    └── findByRaceId() → all lineup entries including is_guest=true → auto-populates results form
```

### Recommended Project Structure

No new directories. All changes in existing packages:

```
src/main/java/org/ctc/
├── domain/
│   ├── model/
│   │   └── RaceLineup.java          # + isGuest boolean field; 4-arg constructor or setter
│   ├── repository/
│   │   └── RaceLineupRepository.java  # + findByRaceIdAndIsGuestTrue(UUID) if needed for prefill
│   └── service/
│       └── RaceLineupService.java   # + getGuestLineups(); saveLineup extended signature
├── admin/
│   └── controller/
│       └── RaceLineupController.java  # + guest_* param parsing; allDrivers in model
├── backup/
│   ├── restore/entity/
│   │   └── RaceLineupRestorer.java  # INSERT SQL adds is_guest column
│   └── serialization/ (no change — MixIn uses @JsonIgnoreProperties; new boolean field auto-serialized)
src/main/resources/
├── db/migration/
│   └── V18__add_race_lineups_is_guest.sql
└── templates/admin/
    └── race-lineup.html             # Add-Guest block + datalist
src/test/java/
├── db/migration/
│   └── V18MigrationIT.java          # new; @Tag("integration")
├── org/ctc/domain/service/
│   └── RaceLineupServiceTest.java   # extend existing
└── org/ctc/admin/controller/
    └── RaceLineupControllerTest.java  # extend existing
    └── RaceLineupRestorerTest.java  # extend for is_guest field
```

### Pattern 1: Flyway Migration — BOOLEAN Column Add (V18)

**What:** H2 + MariaDB compatible ALTER TABLE with BOOLEAN (not tinyint) and default false.
**When to use:** Any additive schema change that must work on both H2 (tests) and MariaDB (prod).

**Verified pattern from V1__initial_schema.sql (line 32/155/255):**
```sql
-- src/main/resources/db/migration/V18__add_race_lineups_is_guest.sql
ALTER TABLE race_lineups ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE;
```

`BOOLEAN NOT NULL DEFAULT FALSE` is the exact syntax used by V1 for `active`, `bye`, and `fastest_lap` — all identical H2/MariaDB compatible. No `tinyint(1)` needed; H2 and MariaDB both accept `BOOLEAN`.

**D-03 finding — UNIQUE constraint already exists:** `uk_race_lineup_driver UNIQUE (race_id, driver_id)` was created in V1 line 277. V18 does NOT add it again. The only new DDL is the `ALTER TABLE ADD COLUMN` above.

**Pre-constraint check (D-03 planner note):** Since the constraint exists from V1, no pre-flight dedup query is needed in V18. The service-layer dedup validation (see Pattern 3) still prevents a roster driver being submitted twice via both namespaces.

### Pattern 2: RaceLineup Entity Field

**What:** Add `isGuest` boolean with Lombok `@Getter @Setter` (already on class). Extend constructor.
**Reference:** `RaceResult.java` uses `@Column(nullable = false) private boolean fastestLap` — identical pattern.

```java
// src/main/java/org/ctc/domain/model/RaceLineup.java
@Column(nullable = false)
private boolean guest;   // maps to is_guest; Lombok generates isGuest()/setGuest()

// Keep 3-arg constructor (roster callers unbroken):
public RaceLineup(Race race, Driver driver, Team team) {
    this.race = race;
    this.driver = driver;
    this.team = team;
    // guest defaults to false via Java boolean default
}

// Add 4-arg convenience constructor for guest entries:
public RaceLineup(Race race, Driver driver, Team team, boolean guest) {
    this.race = race;
    this.driver = driver;
    this.team = team;
    this.guest = guest;
}
```

Note: JPA column name derives from field name `guest` → `is_guest` via Spring's naming strategy (underscore + prefix `is_`). Verify with `@Column(name = "is_guest")` explicit mapping if auto-naming is uncertain.

**Safe approach:** use `@Column(name = "is_guest", nullable = false)` explicitly. This matches the V18 migration column name without relying on naming strategy.

### Pattern 3: RaceLineupService — saveLineup Extension

**Current signature:** `saveLineup(UUID raceId, Map<UUID, UUID> driverTeamAssignments)`
**Extended signature:** `saveLineup(UUID raceId, Map<UUID, UUID> rosterAssignments, Map<UUID, UUID> guestAssignments)`

**Key logic changes:**

1. **Pre-save: detect dropped guest UUIDs for D-11 cascade:**
   ```java
   // Before deleteAll, collect existing guest driverIds
   var existingGuests = raceLineupRepository.findByRaceId(raceId).stream()
       .filter(RaceLineup::isGuest)
       .toList();
   var droppedGuestDriverIds = existingGuests.stream()
       .map(rl -> rl.getDriver().getId())
       .filter(id -> !guestAssignments.containsKey(id))
       .toList();
   ```

2. **Delete-all-then-recreate (unchanged pattern):**
   ```java
   raceLineupRepository.deleteAll(existing);
   // save roster entries (is_guest = false, via 3-arg constructor)
   // save guest entries (is_guest = true, via 4-arg constructor)
   ```

3. **D-11 cascade: delete RaceResult for dropped guests:**
   ```java
   for (var driverId : droppedGuestDriverIds) {
       raceResultRepository.findByRaceIdAndDriverId(raceId, driverId)
           .ifPresent(raceResultRepository::delete);
   }
   if (!droppedGuestDriverIds.isEmpty()) {
       scoringService.aggregateMatchScores(race);
   }
   ```

4. **Dedup validation (D-03 service layer):** Both roster and guest maps use driverId as key, so a driver appearing in both `rosterAssignments` and `guestAssignments` would be a map collision — the last-put wins and the constraint-level uniqueness catches any slip. For an explicit user error message, check intersection before saving:
   ```java
   var duplicates = rosterAssignments.keySet().stream()
       .filter(guestAssignments::containsKey).toList();
   if (!duplicates.isEmpty()) {
       throw new BusinessRuleException("Driver already in lineup as roster driver");
   }
   ```

**New injected dependencies for `RaceLineupService`:**
- `RaceResultRepository raceResultRepository` (for D-11 delete)
- `ScoringService scoringService` (for D-11 aggregation)

### Pattern 4: RaceLineupController — Guest Param Parsing

**Current:** parses `driver_<uuid>=teamId`
**Extended:** also parse `guest_<uuid>=teamId`

```java
@PostMapping("/{raceId}/lineup")
public String saveLineup(@PathVariable UUID raceId,
                         @RequestParam Map<String, String> params,
                         RedirectAttributes redirectAttributes) {
    var rosterAssignments = new HashMap<UUID, UUID>();
    var guestAssignments  = new HashMap<UUID, UUID>();
    for (var entry : params.entrySet()) {
        if (entry.getKey().startsWith("driver_") && hasText(entry.getValue())) {
            UUID driverId = UUID.fromString(entry.getKey().substring("driver_".length()));
            rosterAssignments.put(driverId, UUID.fromString(entry.getValue()));
        } else if (entry.getKey().startsWith("guest_") && hasText(entry.getValue())) {
            UUID driverId = UUID.fromString(entry.getKey().substring("guest_".length()));
            guestAssignments.put(driverId, UUID.fromString(entry.getValue()));
        }
    }
    int count = raceLineupService.saveLineup(raceId, rosterAssignments, guestAssignments);
    // ...
}
```

**GET handler — add `allDrivers` and `guestLineups` to model:**
```java
@GetMapping("/{raceId}/lineup")
public String lineup(@PathVariable UUID raceId, Model model) {
    var data = raceLineupService.getLineupData(raceId);
    // ... existing teamEntries population ...
    model.addAttribute("race", data.race());
    model.addAttribute("teamEntries", teamEntries);
    model.addAttribute("driverAssignments", raceLineupService.getDriverAssignments(raceId));
    model.addAttribute("guestLineups", raceLineupService.getGuestLineups(raceId));
    model.addAttribute("allDrivers", driverService.findAll());   // for <datalist>
    return "admin/race-lineup";
}
```

This requires injecting `DriverService` into `RaceLineupController`.

### Pattern 5: RaceLineupService — getGuestLineups (D-10 prefill)

**Option A (new repository query):** Add `findByRaceIdAndGuest(UUID raceId, boolean guest)` to `RaceLineupRepository` — Spring Data derives it from the method name.

**Option B (filter existing query):** Reuse `findByRaceId` and filter in Java:
```java
public List<RaceLineup> getGuestLineups(UUID raceId) {
    return raceLineupRepository.findByRaceId(raceId).stream()
        .filter(RaceLineup::isGuest)
        .toList();
}
```

Option B avoids a new query and is sufficient given the small list size (max 12 drivers per race). Use `@EntityGraph(attributePaths = {"driver", "team"})` is already on `findByRaceId` so lazy-load is covered.

### Pattern 6: Template — Add-Guest Block + Datalist (D-04/D-05/D-08/D-10)

**`<datalist>` rendered once outside the team loop:**
```html
<!-- All-drivers datalist, rendered once -->
<datalist id="guestDriverList">
    <option th:each="d : ${allDrivers}"
            th:value="${d.psnId + ' (' + d.nickname + ')'}"
            th:attr="data-id=${d.id}">
    </option>
</datalist>
```

**Per-team Add-Guest block (inside the existing `th:each="entry : ${teamEntries}"` loop):**
```html
<div class="card mb-md guest-section">
    <h3>Guest Drivers — <span th:text="${entry.team.shortName}"></span></h3>

    <!-- Prefilled existing guest rows (D-10) -->
    <div th:each="gl : ${guestLineups}"
         th:if="${gl.team.id == entry.team.id
                  or gl.team.parentOrSelf.id == entry.team.id}">
        <input type="text" list="guestDriverList"
               th:value="${gl.driver.psnId + ' (' + gl.driver.nickname + ')'}"
               placeholder="Search driver...">
        <input type="hidden"
               th:name="'guest_' + ${gl.driver.id}"
               th:value="${gl.team.id}">
        <!-- Sub-team select (D-08) -->
        <select th:if="${entry.hasSubTeams}"
                th:name="'guest_subteam_placeholder'"
                onchange="this.previousElementSibling.name='guest_'+this.value.split('|')[0]">
            <!-- Planner resolves the exact JS mechanism for sub-team→driverId hidden field naming -->
        </select>
    </div>

    <!-- Blank new-guest row -->
    <div class="guest-row">
        <input type="text" list="guestDriverList"
               placeholder="Search driver..."
               class="guest-driver-input">
        <input type="hidden" name="" class="guest-driver-id">
        <select th:if="${entry.hasSubTeams}">
            <option value="">-- Sub-team --</option>
            <option th:each="sub : ${entry.subTeams}"
                    th:value="${sub.id}" th:text="${sub.shortName}"></option>
        </select>
    </div>
</div>
```

**D-06 — driverId resolution (Claude's Discretion):**
The `<datalist>` only binds a display string to the text input — it does not natively set a hidden field. The project already has `searchable-dropdown.js` which implements exactly the hidden-field+text-input+filterable-list pattern. However, D-05 locks "native `<datalist>`", so a small amount of bespoke vanilla JS is needed:

```javascript
// Add to race-lineup.html (inline or separate small script)
document.querySelectorAll('.guest-driver-input').forEach(function(input) {
    input.addEventListener('change', function() {
        var val = this.value;
        var opt = document.querySelector('#guestDriverList option[value="' + val + '"]');
        var hidden = this.nextElementSibling; // the hidden input
        hidden.value = opt ? opt.dataset.id : '';
        hidden.name  = opt ? 'guest_' + opt.dataset.id : '';
    });
});
```

**Alternative (recommended for planner consideration):** Use the existing `.searchable-dropdown` CSS class + hidden-field pattern from `searchable-dropdown.js` (already loaded globally via `layout.html` line 103). The only difference is replacing the custom dropdown list with the `<datalist>` for the text input. This would reuse the hidden-field wiring already present. The planner may adopt either approach.

### Anti-Patterns to Avoid

- **Do not add `is_guest` to `race_results`:** D-01 is explicit; team is derived from lineup via `findByRaceIdAndDriverId`.
- **Do not add the `UNIQUE(race_id, driver_id)` constraint in V18:** It already exists in V1 as `uk_race_lineup_driver`. Adding it again will cause `Duplicate key constraint` error on Flyway migration.
- **Do not change `getDriverAssignments`:** It returns the roster driver→team map (non-guests). Guests need a separate accessor so the template can distinguish roster rows from guest rows.
- **Do not skip `scoringService.aggregateMatchScores` after guest removal:** CLAUDE.md "Score Aggregation on Result Save" is explicit.
- **Do not use `@MockitoBean` on `ScoringService` in the controller IT:** CLAUDE.md "WireMock is not Real-API Coverage" rule — the real Spring `@Transactional` proxy must run.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Driver search/typeahead | Custom AJAX endpoint + JS framework | Native HTML `<datalist>` + small vanilla JS for hidden-field | D-05 locked; existing `searchable-dropdown.js` shows the pattern |
| Cascade delete on guest removal | Template-level orphan detection | Service-layer: `raceResultRepository.findByRaceIdAndDriverId` + delete in `saveLineup` | CLAUDE.md "No Fallback Calculations"; must be transactional |
| Lineup uniqueness enforcement | DB-level unique constraint in V18 | Already enforced by `uk_race_lineup_driver` from V1 | Constraint already exists; service-layer dedup for UX |
| All-drivers list | New API endpoint or session-scoped cache | `driverService.findAll()` in controller, passed to model | OSIV active; simple call, no cache needed for admin-only page |

---

## Runtime State Inventory

Not applicable — this is a greenfield feature (new column + new UI block). No rename/refactor/migration of existing string values.

The only runtime concern: **existing `race_lineups` rows will have `is_guest = false` after V18** (guaranteed by `DEFAULT FALSE`). No data migration required. No stored data carries guest semantics before this phase.

---

## Common Pitfalls

### Pitfall 1: Adding a Duplicate UNIQUE Constraint in V18

**What goes wrong:** Migration fails with `Duplicate key name 'uk_race_lineup_driver'` or equivalent on MariaDB, or H2 throws a constraint name collision.
**Why it happens:** `uk_race_lineup_driver UNIQUE (race_id, driver_id)` was created in V1 (line 277 of `V1__initial_schema.sql`). D-03's description says "add UNIQUE" but the constraint already exists.
**How to avoid:** V18 contains ONLY the `ALTER TABLE race_lineups ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE` statement. No constraint DDL.
**Warning signs:** If V18 SQL includes `ADD CONSTRAINT uk_race_lineup_driver`, it will fail.

### Pitfall 2: `is_guest` Column Name vs. Lombok Field Name

**What goes wrong:** Hibernate maps `boolean guest` to column `guest`, not `is_guest`. The migration creates `is_guest`, causing `Column 'is_guest' not found` at startup.
**Why it happens:** Hibernate naming strategy applies `is_` prefix only for Java `boolean` fields named `isXxx`, not plain `xxx`.
**How to avoid:** Use `@Column(name = "is_guest", nullable = false)` explicitly on the field. Field can be named `guest` (Lombok generates `isGuest()`/`setGuest()`).
**Warning signs:** `UnknownColumnException` on startup with H2 profile.

### Pitfall 3: `RaceLineupRestorer` INSERT SQL Missing `is_guest`

**What goes wrong:** Backup restore fails with column count mismatch or inserts `NULL` into `NOT NULL` column.
**Why it happens:** `RaceLineupRestorer.INSERT_SQL` is a hardcoded string listing specific columns. After V18 adds `is_guest NOT NULL`, restoring a V18+ backup without the column in the INSERT will fail.
**How to avoid:** Update `INSERT_SQL` to include `is_guest` and add `ps.setBoolean(N, row.get("guest").asBoolean(false))` in the `setValues` lambda. The JSON field name will be `guest` (from Lombok getter `isGuest()` → Jackson serializes as `guest`). Also update `RaceLineupRestorerTest` to verify the new column is included.
**Warning signs:** `BackupImportMariaDbSmokeIT` fails; restorer test assertions fail on SQL string.

### Pitfall 4: `getDriverAssignments` Returning Guests in Roster Map

**What goes wrong:** Guest drivers appear checked/selected in the existing roster table, creating duplicate hidden inputs (`driver_<uuid>` and `guest_<uuid>`) for the same driver.
**Why it happens:** `getDriverAssignments` currently returns ALL lineups (roster + guest would both be returned after V18).
**How to avoid:** Filter `getDriverAssignments` to `is_guest = false` entries only. Either filter in Java or add a `findByRaceIdAndGuest(raceId, false)` repository method. Update the existing test for `getDriverAssignments`.
**Warning signs:** After saving a lineup with a guest, reopening the form shows the guest checked in the roster table.

### Pitfall 5: Datalist Text-to-driverId Resolution Failure

**What goes wrong:** Guest form submits with empty `guest_` param name (the hidden field name wasn't set) or wrong UUID.
**Why it happens:** Native `<datalist>` binds the selected option's `value` (display text) to the text input — it does not natively update a hidden field. Without JS, the hidden field name stays empty and the controller skips it.
**How to avoid:** Implement the `change` listener on `.guest-driver-input` to set the hidden field's `name` attribute to `guest_<driverId>` and its `value` to the teamId. Test by inspecting the POST body in the controller IT.
**Warning signs:** `saveLineup` receives zero `guest_*` params even when a driver was selected; controller IT shows `guestAssignments.size() == 0`.

### Pitfall 6: `saveLineup` Signature Change Breaks Existing Callers

**What goes wrong:** Compile error — `TestDataService`, `DevDataSeeder`, or tests call the old 2-arg `saveLineup(raceId, driverTeamAssignments)`.
**Why it happens:** Refactoring to a 3-arg signature.
**How to avoid:** Grep all usages: `grep -rn "saveLineup" src/`. Per CLAUDE.md, audit before refactoring. Add an overloaded method or keep backward-compatible signature (e.g., delegate `saveLineup(raceId, roster)` to `saveLineup(raceId, roster, Map.of())`).
**Warning signs:** Compile error in `TestDataService`/`DevDataSeeder` on `./mvnw clean compile`.

---

## Code Examples

### V18 Migration — Exact SQL Shape

```sql
-- src/main/resources/db/migration/V18__add_race_lineups_is_guest.sql
ALTER TABLE race_lineups ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE;
```

Source: verified from V1 BOOLEAN syntax (lines 32, 155, 255); V1 line 277 confirms UNIQUE already exists.

### RaceLineup Entity — is_guest Field

```java
// src/main/java/org/ctc/domain/model/RaceLineup.java
@Column(name = "is_guest", nullable = false)
private boolean guest;

public RaceLineup(Race race, Driver driver, Team team) {
    this.race = race; this.driver = driver; this.team = team;
    // guest = false by default
}

public RaceLineup(Race race, Driver driver, Team team, boolean guest) {
    this.race = race; this.driver = driver; this.team = team;
    this.guest = guest;
}
```

### RaceLineupRestorer — Updated INSERT SQL

```java
// src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java
private static final String INSERT_SQL =
        "INSERT INTO race_lineups (id, race_id, driver_id, team_id, is_guest, "
      + "created_at, updated_at) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?)";

// In setValues lambda:
ps.setObject(1, UUID.fromString(row.get("id").asText()));
ps.setObject(2, UUID.fromString(row.get("race").asText()));
ps.setObject(3, UUID.fromString(row.get("driver").asText()));
ps.setObject(4, UUID.fromString(row.get("team").asText()));
ps.setBoolean(5, row.path("guest").asBoolean(false));  // default false for old backups
ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
```

Note: `row.path("guest").asBoolean(false)` gracefully handles old backup files that lack the `guest` field (backward-compatible restore).

### V18MigrationIT — New Migration Test

```java
// src/test/java/db/migration/V18MigrationIT.java
package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CtcDevSpringBootContext
@Tag("integration")
class V18MigrationIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void givenH2WithV18Applied_whenInspectingRaceLineupsIsGuestColumn_thenExists() throws Exception {
        Set<String> columns = collectColumnNames("RACE_LINEUPS");
        assertThat(columns).contains("is_guest");
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

    private Set<String> collectColumnNames(String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| No guest concept | `is_guest BOOLEAN` on `RaceLineup` | V18 (this phase) | Single source of truth for guest status; Phase 114/115 read the flag |
| All lineup entries treated as roster | Separate `guest_*` param namespace | This phase | Roster save behavior unchanged; guests flagged on recreate |

**Deprecated/outdated:**
- None in this phase scope.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Hibernate's Spring naming strategy maps `boolean guest` field to `guest` column (not `is_guest`), requiring explicit `@Column(name = "is_guest")` | Pattern 2 | If naming strategy adds `is_` prefix automatically, the annotation is redundant but harmless. The explicit annotation is always safe. |
| A2 | `driverService.findAll()` returns all drivers sorted by psnId or without a specific order — template renders `<datalist>` in arbitrary order | Pattern 6 | If users expect alphabetical order in the datalist, `driverService.findAll()` needs `Sort.by("psnId")`. Low UX impact; datalist filters as user types. |
| A3 | `RaceLineupMixIn` `@JsonIgnoreProperties` does not list `guest` — so Jackson will auto-serialize the new boolean field in backup JSON without any MixIn change | Backup section | If MixIn accidentally ignores it, `is_guest` won't round-trip in backups. Verify by running `BackupImportMariaDbSmokeIT` or a new IT. |

---

## Open Questions

1. **Sub-team hidden-field naming for guest rows (D-08)**
   - What we know: For sub-team teams, the guest must be assigned to a concrete sub-team. The `guest_<driverId>=teamId` param must carry the sub-team's ID, not the parent's.
   - What's unclear: If the driver is unknown at page-load time (new guest row), the hidden field `name` must be set dynamically by JS when the user selects a driver AND a sub-team. This requires JS coordination between two fields.
   - Recommendation: Planner designs the sub-team flow carefully. One approach: the hidden field name is always `guest_<driverId>` set on driver selection (`change` on datalist input); the value is set by the sub-team `<select>` `change` event. Both handlers must be wired.

2. **Backward-compatible `saveLineup` signature for callers**
   - What we know: `TestDataService` calls `raceLineupRepository.save(new RaceLineup(...))` directly — NOT via `saveLineup`. `DevDataSeeder` has no such call. So `saveLineup` callers are only `RaceLineupController`.
   - What's unclear: Any future callers or tests that call `saveLineup` with 2 args.
   - Recommendation: Provide a 2-arg overload that delegates to 3-arg with `Map.of()` for guests. This avoids touching any existing tests.

3. **`getDriverAssignments` filtering**
   - What we know: Currently returns ALL lineups (no guest-flag filter). After V18, guest entries would appear in the roster map.
   - Recommendation: Add `.filter(rl -> !rl.isGuest())` in `getDriverAssignments`. Update the existing service unit test to verify this filter.

---

## Environment Availability

Step 2.6: SKIPPED (no external tools or services beyond the existing Spring Boot + H2/MariaDB + Maven stack).

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito (existing) |
| Config file | `pom.xml` Surefire/Failsafe config |
| Quick run command | `./mvnw -Dtest=RaceLineupServiceTest,RaceLineupControllerTest test` |
| Full suite command | `./mvnw clean verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GUEST-04 | `is_guest` column exists, NOT NULL in H2 | integration | `./mvnw -Dit.test=V18MigrationIT verify` | ❌ Wave 0 |
| GUEST-04 | `is_guest` flag persists `true` for guest entries, `false` for roster | integration (service) | `./mvnw -Dit.test=RaceLineupControllerTest verify` | ✅ (extend) |
| GUEST-01 | `saveLineup` with `guest_*` params persists guest entries with `is_guest=true` | integration (controller) | `./mvnw -Dit.test=RaceLineupControllerTest verify` | ✅ (extend) |
| GUEST-01 | `getLineupData` returns `allDrivers` in model; GET form renders `<datalist>` | integration (controller) | `./mvnw -Dit.test=RaceLineupControllerTest verify` | ✅ (extend) |
| GUEST-02 | `RaceFormDataService.populateDrivers` includes guest lineup entries | unit | `./mvnw -Dtest=RaceFormDataServiceTest test` | ❌ Wave 0 (new test) |
| GUEST-03 | Guest removal cascade-deletes `RaceResult` and calls `aggregateMatchScores` | unit | `./mvnw -Dtest=RaceLineupServiceTest test` | ✅ (extend) |
| GUEST-03 | `getDriverAssignments` filters out guests (no guest in roster map) | unit | `./mvnw -Dtest=RaceLineupServiceTest test` | ✅ (extend) |
| GUEST-04 | `RaceLineupRestorer` INSERT SQL includes `is_guest` and binds boolean | unit | `./mvnw -Dtest=RaceLineupRestorerTest test` | ✅ (extend) |

### Sampling Rate

- **Per task commit:** `./mvnw -Dtest=RaceLineupServiceTest,RaceLineupControllerTest test`
- **Per wave merge:** `./mvnw clean verify`
- **Phase gate:** `./mvnw clean verify -Pe2e` (full suite including Playwright E2E) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/db/migration/V18MigrationIT.java` — covers GUEST-04 schema persistence
- [ ] `src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java` — covers GUEST-02 auto-derive path (confirm guest lineup entries surface in results form)

*(Existing `RaceLineupServiceTest.java`, `RaceLineupControllerTest.java`, and `RaceLineupRestorerTest.java` all exist and need extension — not creation.)*

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Dev profile runs without auth (CLAUDE.md Profiles) |
| V3 Session Management | no | Standard Spring Session |
| V4 Access Control | no | `/admin/*` is already secured in prod profile |
| V5 Input Validation | yes | UUID parsing from `guest_*` params: `UUID.fromString(...)` throws `IllegalArgumentException` on malformed input — needs to be caught or rely on existing exception handler |
| V6 Cryptography | no | No crypto in this phase |

**V5 note:** The existing `driver_*` param parsing in `RaceLineupController` uses `UUID.fromString(...)` without try/catch — it relies on the global exception handler. The `guest_*` extension follows the same pattern. No new risk introduced; same behavior as existing roster parsing.

**No log-injection risk:** Driver PSN IDs are not logged in the lineup save path. New `log.info("Saved {} lineup entries...")` should not include user-supplied driver name strings.

---

## Sources

### Primary (HIGH confidence)

All findings verified by direct inspection of production source files in the repository.

- `src/main/resources/db/migration/V1__initial_schema.sql` — `race_lineups` schema (constraint `uk_race_lineup_driver`, BOOLEAN syntax)
- `src/main/resources/db/migration/V17__add_matches_walkover_team_id.sql` — ALTER TABLE pattern (most recent migration)
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — entity structure, 3-arg constructor
- `src/main/java/org/ctc/domain/model/RaceResult.java` — `@Column(nullable = false) private boolean fastestLap` pattern
- `src/main/java/org/ctc/domain/service/RaceLineupService.java` — `saveLineup`, `getLineupData`, `getDriverAssignments`
- `src/main/java/org/ctc/admin/controller/RaceLineupController.java` — `driver_*` param parsing
- `src/main/java/org/ctc/domain/service/RaceFormDataService.java` — `populateDrivers`, `toRaceData`
- `src/main/java/org/ctc/domain/service/RaceService.java` — `aggregateMatchScores` call pattern
- `src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java` — hardcoded INSERT SQL
- `src/main/java/org/ctc/backup/serialization/RaceLineupMixIn.java` — Jackson annotations
- `src/main/resources/templates/admin/race-lineup.html` — existing template structure
- `src/main/resources/templates/admin/race-form.html` — `searchable-dropdown` pattern
- `src/main/resources/static/admin/js/searchable-dropdown.js` — hidden-field+input+list JS pattern
- `src/test/java/db/migration/V17MigrationIT.java` — migration IT template (exact pattern to follow)
- `src/test/java/org/ctc/admin/controller/RaceLineupControllerTest.java` — existing controller IT pattern
- `src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java` — existing service unit test pattern
- `src/test/java/org/ctc/backup/restore/entity/RaceLineupRestorerTest.java` — restorer test pattern

---

## Metadata

**Confidence breakdown:**

- Flyway V18 SQL: HIGH — exact BOOLEAN syntax from V1; constraint already exists (verified)
- Entity field: HIGH — exact pattern from `RaceResult.boolean fastestLap` + explicit `@Column(name=...)`
- Service/controller changes: HIGH — full source read; all methods and call sites verified
- Template changes: HIGH — template and JS patterns verified; D-06 resolution mechanism is Claude's Discretion
- Backup restorer: HIGH — INSERT SQL structure and test pattern fully verified
- Coverage impact: MEDIUM — current baseline 89%; new tests for 6 methods on a lean service should maintain coverage; exact delta requires measurement post-implementation

**Research date:** 2026-06-01
**Valid until:** 2026-07-01 (stable internal codebase; no external dependencies)
