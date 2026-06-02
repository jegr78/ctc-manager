---
phase: 113-guest-assignment-foundation
verified: 2026-06-01T16:00:00Z
status: passed
score: 4/4 must-haves verified
human_verification_status: auto-verified 2026-06-01 via playwright-cli — see 113-AUTO-UAT.md (4/4 passed)
overrides_applied: 0
human_verification:
  - test: "Open /admin/races/{raceId}/lineup in a browser for a race with no sub-teams. Type a partial PSN ID in the Add-Guest datalist input. Verify the dropdown filters to matching drivers, select one, and confirm the hidden guest_ field name resolves to guest_<driverUUID>."
    expected: "Datalist filters by text; selecting a driver populates the hidden field name with guest_<uuid>; the team id is set to the section's data-team-id."
    why_human: "The datalist typeahead filtering and JS hidden-field wiring require a real browser rendering — MockMvc only verifies the 200 response and model attributes, not client-side JS behavior."
  - test: "Open /admin/races/{raceId}/lineup for a race where a team has sub-teams. Add a guest and select a sub-team. Save. Reopen. Confirm the guest row is prefilled with the correct driver text and sub-team selected."
    expected: "Guest row prefills the datalist text input with 'psnId (nickname)' and selects the correct sub-team; the hidden guest_ field carries the sub-team's UUID as its value."
    why_human: "Prefill correctness of the sub-team select (th:selected logic) and JS re-binding of prefilled rows require visual/browser verification."
  - test: "Click 'Add another guest' multiple times, fill in drivers for each cloned row, then save. Confirm all guest entries persist."
    expected: "Each cloned row independently tracks its own guest_<driverId>=teamId param; all entries persist with is_guest=true."
    why_human: "Clone behavior and per-row JS listener re-binding cannot be tested by MockMvc; requires browser interaction."
  - test: "On a sub-team race, add a guest without selecting a sub-team (leave the first option selected as default). Save. Confirm an errorMessage flash appears and no ghost entry is silently dropped."
    expected: "Sub-team select defaults to the first real sub-team (not a blank option), so the guest is not silently dropped; or if truly blank, the errorMessage is surfaced."
    why_human: "The WR-05 fix (default sub-team select to first option in blank row) only affects the HTML rendered sub-team select on a new row — requires visual verification that no blank/empty first option exists."
---

# Phase 113: Guest Assignment Foundation — Verification Report

**Phase Goal:** Admins can add, edit, and remove guest-driver assignments in race lineups and results — any driver in the system is selectable, and the assignment is durably flagged as a guest entry in the data model.
**Verified:** 2026-06-01
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Admin can open a race lineup form and select any driver in the system (not restricted to the season's roster) as a guest, specifying the fielding team. | VERIFIED | `RaceLineupController.lineup()` adds `allDrivers` (via `driverService.findAll()`) and `guestLineups` to the model. Template renders `<datalist id="guestDriverList">` over `allDrivers` with no roster filter. Controller IT `givenExistingRace_whenGetLineupPage_thenReturnsLineupView` asserts all 5 model attributes including `allDrivers` and `guestLineups`. |
| 2 | Admin can record a finishing position/result for the guest driver under the fielding team (results auto-derive from the lineup). | VERIFIED | `RaceFormDataService.getResultsFormData` reads `raceLineupRepository.findByRaceId` without isGuest filter — guests auto-surface. Test `givenLineupWithGuestEntry_whenGetResultsFormData_thenGuestAppearsInResultsForm` in `RaceFormDataServiceTest` (line 165) proves a 4-arg-constructed guest RaceLineup appears in the results form output without any results-code change. No separate results handling added. |
| 3 | Admin can edit or remove an existing guest-driver assignment. | VERIFIED | Service uses delete-all-then-recreate: dropped guests (not resubmitted) have their RaceResult cascade-deleted and `scoringService.aggregateMatchScores(race)` is called. `givenSavedGuestWithResult_whenSaveWithoutGuest_thenGuestAndResultRemoved` (controller IT) verifies end-to-end removal including orphan result deletion. `givenGuestStillPresent_whenSaveLineup_thenNoResultDeleteAndNoReaggregation` confirms re-saving a kept guest at same team is non-destructive. `givenKeptGuestMovedToDifferentTeam_whenSaveLineup_thenScoresReaggregated` (service test line 357) confirms team-change triggers re-aggregation (CR-01 fix committed at 8ff7468c). |
| 4 | The database persistently stores whether a lineup entry is a guest assignment, independent of season-roster membership (Flyway migration, H2 + MariaDB compatible, V1-V17 untouched). | VERIFIED | `V18__add_race_lineups_is_guest.sql` contains exactly one statement: `ALTER TABLE race_lineups ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE;` — no UNIQUE constraint, no index. V1-V17 untouched (ls confirms 17 prior migrations). `RaceLineup.java` maps `@Column(name = "is_guest", nullable = false)` on `private boolean guest;` — Lombok generates `isGuest()`/`setGuest()`. Both 3-arg (roster) and 4-arg (guest) constructors present. `V18MigrationIT` has two `@Tag("integration")` tests: column-exists assertion and NOT-NULL assertion via DatabaseMetaData. `RaceLineupRestorer.INSERT_SQL` includes `is_guest` at position 5 via `row.path("guest").asBoolean(false)` — backward-compatible with pre-V18 backups. |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V18__add_race_lineups_is_guest.sql` | Additive is_guest column migration | VERIFIED | Single `ALTER TABLE race_lineups ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE;`. No UNIQUE re-add. File exists with correct content. |
| `src/main/java/org/ctc/domain/model/RaceLineup.java` | is_guest entity field + 4-arg constructor | VERIFIED | `@Column(name = "is_guest", nullable = false) private boolean guest;` present. 4-arg `(Race, Driver, Team, boolean)` constructor added. 3-arg `(Race, Driver, Team)` constructor preserved. |
| `src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java` | INSERT SQL including is_guest, backward-compatible bind | VERIFIED | `INSERT_SQL` contains `is_guest` between `team_id` and `created_at` (7 `?` placeholders). Binds `ps.setBoolean(5, row.path("guest").asBoolean(false))` — uses `path` not `get` for backward compat. |
| `src/test/java/db/migration/V18MigrationIT.java` | Schema-existence + NOT NULL assertion for is_guest | VERIFIED | `@CtcDevSpringBootContext @Tag("integration")` — two Given-When-Then tests: column name contains `is_guest`; IS_GUEST `NULLABLE == columnNoNulls`. |
| `src/main/java/org/ctc/domain/service/RaceLineupService.java` | 3-arg saveLineup overload, dedup validation, guest cascade, getGuestLineups, guest-filtered getDriverAssignments | VERIFIED | 2-arg overload delegates to 3-arg with `Map.of()`. 3-arg has roster∩guest collision dedup (`BusinessRuleException`), guest-team scope validation (`validFieldingTeamIds`), `deleteAll`+`flush()` ordering guard, cascade-delete dropped guest results, `aggregateMatchScores` on dropped or team-changed guests. `getDriverAssignments` filters `!lineup.isGuest()`. `getGuestLineups` filters `RaceLineup::isGuest`. Both injected deps `RaceResultRepository` and `ScoringService` present. |
| `src/main/java/org/ctc/admin/controller/RaceLineupController.java` | guest_* param parsing + allDrivers/guestLineups model attributes | VERIFIED | GET adds `guestLineups` and `allDrivers`. POST parses `guest_` namespace into `guestAssignments`, calls 3-arg `saveLineup`. UUID parsing wrapped in try/catch (WR-04 fix). Skipped-guest counting and error flash (WR-05 fix). |
| `src/main/resources/templates/admin/race-lineup.html` | per-team Add-Guest block + datalist + prefill | VERIFIED | `<datalist id="guestDriverList">` rendered once over `allDrivers`. Per-team `guest-section` div with `data-team-id`. Prefilled `guest-row`s via `th:each="gl : ${guestLineups}"` guarded by `gl.team.parentOrSelf.id == entry.team.id`. Blank new-row. Sub-team select when `entry.hasSubTeams`. Add-another-guest button. No `style=` attributes. Script included at section end. |
| `src/main/resources/static/admin/css/admin.css` | guest block styling classes (append-only) | VERIFIED | `guest-section`, `guest-row`, `guest-driver-input`, `guest-subteam`, `guest-add-row` appended at end. Line count is 2114 (grew from pre-phase 2082 per 113-03-SUMMARY). Existing sections intact. |
| `src/main/resources/static/admin/js/guest-lineup.js` | datalist text -> hidden guest_<driverId> + teamId resolution | VERIFIED | IIFE self-contained. `resolveDriverId` iterates `datalist` options by `value` attribute (display string match). `syncRow` sets `hidden.name = 'guest_' + driverId` and `hidden.value = teamId`. Sub-team `<select>` change re-syncs. Remove button calls `row.remove()`. Clone behavior on add-button click resets inputs and re-binds listeners. |
| `src/test/java/org/ctc/backup/restore/entity/RaceLineupRestorerTest.java` | SQL contains is_guest, setBoolean(5,...), timestamps at 6/7, pre-V18 backward-compat test | VERIFIED | Three tests: `tableName`, main restore (row WITH `guest: true` → `setBoolean(5, true)`, timestamps at 6/7, SQL contains `is_guest`), `givenPreV18LineupWithoutGuestField` (row WITHOUT `guest` field → `setBoolean(5, false)`). |
| `src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java` | 7 new guest-related unit tests | VERIFIED | Guest entry persisted with `isGuest()==true` (captor); roster entry has `isGuest()==false`; dropped guest → result deleted + aggregateMatchScores called; kept guest same team → no interactions with scoringService; guest moved to different team → aggregateMatchScores called (line 357, CR-01 fix); guest team not in race → BusinessRuleException; roster+guest collision → BusinessRuleException; getDriverAssignments excludes guests; getGuestLineups returns only guests. |
| `src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java` | GUEST-02 guest auto-derive test | VERIFIED | `givenLineupWithGuestEntry_whenGetResultsFormData_thenGuestAppearsInResultsForm` (line 165) — 4-arg guest RaceLineup, asserts `getResultsFormData` output contains the guest driver ID. |
| `src/test/java/org/ctc/admin/controller/RaceLineupControllerTest.java` | GET model attributes test + guest-persist test + guest-removal test | VERIFIED | 8 tests total: GET asserts all 5 model attributes; `givenGuestParam_whenSaveLineup_thenPersistsGuestEntryWithGuestFlag` asserts `isGuest()==true`; `givenSavedGuestWithResult_whenSaveWithoutGuest_thenGuestAndResultRemoved` asserts both lineup and RaceResult deleted; `givenExistingGuest_whenReSavedToOtherTeam_thenMovesWithoutUniqueViolation`; `givenMalformedGuestKey`; `givenGuestRowWithoutTeam`. No `@MockitoBean` on ScoringService. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `RaceLineup.java` | `race_lineups.is_guest` | `@Column(name = "is_guest", nullable = false)` | WIRED | Exact annotation on line 38. Hibernate maps `boolean guest` → `is_guest` column. |
| `RaceLineupRestorer.java` | JSON guest field | `row.path("guest").asBoolean(false)` | WIRED | Line 40 in restorer. Uses `path` (not `get`) for missing-field safety. |
| `race-lineup.html` | `RaceLineupController guest_* parsing` | hidden input `th:name="'guest_' + ${gl.driver.id}"` with team id value | WIRED | Line 66 in template. Controller parses `guest_` prefix in POST loop. |
| `RaceLineupController.java` | `raceLineupService.saveLineup(raceId, roster, guests)` | 3-arg overload from plan 113-02 | WIRED | Line 77 in controller calls `saveLineup(raceId, rosterAssignments, guestAssignments)`. |
| `RaceLineupService.java` | `scoringService.aggregateMatchScores(race)` | called after cascade-deleting dropped guests OR on team change | WIRED | Lines 147-149. Condition: `!droppedGuestDriverIds.isEmpty() || keptGuestTeamChanged`. CR-01 fix verified at commit 8ff7468c. |
| `RaceLineupService.java` | `raceResultRepository.findByRaceIdAndDriverId` | delete dropped guest's RaceResult | WIRED | Lines 143-145. `ifPresent(raceResultRepository::delete)` for each dropped guest driver. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `race-lineup.html` | `allDrivers` | `driverService.findAll()` in GET handler | Yes — queries all Driver entities | FLOWING |
| `race-lineup.html` | `guestLineups` | `raceLineupService.getGuestLineups(raceId)` → `raceLineupRepository.findByRaceId` filtered on `isGuest()` | Yes — DB query, not static | FLOWING |
| `race-lineup.html` | `driverAssignments` | `raceLineupService.getDriverAssignments(raceId)` → `findByRaceId` filtered `!isGuest()` | Yes — DB query | FLOWING |
| `RaceLineupController POST` | guest_* → `guestAssignments` → `saveLineup` | params map parsed per-key, saved via 4-arg `RaceLineup` constructor with `is_guest=true` | Yes — persists to DB | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| V18 migration column exists and is NOT NULL | Covered by `V18MigrationIT` (2 tests, both green per SUMMARY) | `@Tag("integration")` test passes; SUMMARY reports 2/2 green | PASS |
| Guest entry saved with `is_guest=true` | Controller IT `givenGuestParam_whenSaveLineup_thenPersistsGuestEntryWithGuestFlag` | SUMMARY reports 6/6 green controller tests including this | PASS |
| Guest removal cascades orphan RaceResult | Controller IT `givenSavedGuestWithResult_whenSaveWithoutGuest_thenGuestAndResultRemoved` | Verified in test at line 79; SUMMARY 6/6 green | PASS |
| Score re-aggregation on team change | Service test `givenKeptGuestMovedToDifferentTeam_whenSaveLineup_thenScoresReaggregated` | `keptGuestTeamChanged` logic at lines 118-120,147; test at line 357; CR-01 fix commit 8ff7468c | PASS |
| Pre-V18 backup defaults `is_guest` to false | `RaceLineupRestorerTest.givenPreV18LineupWithoutGuestField` | `row.path("guest").asBoolean(false)` — SUMMARY 3/3 green | PASS |
| Full build gate | `./mvnw clean verify -Pe2e` | BUILD SUCCESS per 113-03-SUMMARY: all unit+IT+E2E green, JaCoCo ≥82%, SpotBugs 0, Checkstyle clean | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| GUEST-01 | 113-03 | Admin can add a guest driver selectable from any driver | SATISFIED | `driverService.findAll()` → `allDrivers` model attr; `<datalist>` over all drivers; controller parses `guest_*` params; IT verifies `isGuest()==true` for off-roster driver |
| GUEST-02 | 113-02 | Admin can record guest driver's result (auto-derive from lineup) | SATISFIED | `RaceFormDataService.populateDrivers` reads `findByRaceId` without isGuest filter; `RaceFormDataServiceTest` guest-auto-derive test passes (8/8) |
| GUEST-03 | 113-02, 113-03 | Admin can edit or remove a guest-driver assignment | SATISFIED | Delete-all-then-recreate; dropped guests have RaceResult cascade-deleted + aggregateMatchScores called; IT `givenSavedGuestWithResult_whenSaveWithoutGuest` verifies both gone |
| GUEST-04 | 113-01 | Lineup entry persistently identifiable as guest (Flyway migration) | SATISFIED | V18 single additive `ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE`; V1-V17 untouched; H2+MariaDB compatible syntax (BOOLEAN = H2+MariaDB standard); V18MigrationIT 2/2 green |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `race-lineup.html` | 37,47 | Legacy HTML comments (`<!-- Sub-team: ... -->`) pre-existing in roster table section | Info | Pre-existing comments outside the guest block; plan executor note says "leave pre-existing comments as-is (out of scope to churn)". No new comment pollution in guest block. Not a blocker. |

No `TBD`, `FIXME`, or `XXX` markers found in any phase-modified file. No `return null`/`return []`/placeholder patterns found in production code. No inline styles in template.

### Human Verification Required

The automated checks cover all data-model, service, controller, and integration aspects. The following UI behaviors require a browser to verify because they depend on client-side JavaScript execution (MockMvc does not execute JS):

#### 1. Datalist Typeahead Filtering and Hidden-Field Resolution

**Test:** Start the dev server (`./scripts/app.sh start dev`). Navigate to `/admin/races/{raceId}/lineup` for a race with two standalone teams (no sub-teams). Type a partial driver PSN ID in the "Guest Drivers" text input.
**Expected:** The native browser datalist dropdown filters to matching options; selecting one populates the hidden `guest_<driverUUID>` field name and the section's `data-team-id` as its value; JS `resolveDriverId` finds the matching `<option>` by `value` attribute.
**Why human:** `guest-lineup.js` logic runs in the browser only; MockMvc asserts the template renders but not that JS executes correctly.

#### 2. Prefilled Guest Row Accuracy (Sub-Team)

**Test:** For a race with a team that has sub-teams, save a guest assigned to a specific sub-team. Reopen the lineup form.
**Expected:** The prefilled guest row shows the correct `psnId (nickname)` text in the datalist input and the correct sub-team pre-selected in the `<select>` (`th:selected="${gl.team.id == sub.id}"`).
**Why human:** Thymeleaf `th:selected` rendering and datalist prefill require browser rendering to verify the visual state of the form.

#### 3. "Add Another Guest" Clone Behavior

**Test:** Click "Add another guest" two or more times, enter distinct drivers, save. Verify all persist with `is_guest=true`.
**Expected:** Each cloned row independently wires its own `guest_<driverId>=teamId` hidden field; all guests saved.
**Why human:** Clone row re-binding of JS listeners cannot be asserted by MockMvc.

#### 4. WR-05 Fix — Sub-Team Default on Blank Row

**Test:** On a sub-team race, open the Add-Guest block and look at the blank (new) guest row's sub-team select.
**Expected:** The sub-team select defaults to the first real sub-team option (not a blank `-- Select --` option), so forgetting to change it does not cause a silent drop.
**Why human:** The HTML rendered by Thymeleaf for the blank row's `<select>` is static (no `<option value="">` added), but visual confirmation that the first real option is in fact the default requires checking the rendered HTML in a browser.

### Gaps Summary

No gaps found. All 4 must-have truths are VERIFIED. All artifacts exist, are substantive, and are wired. All 4 requirement IDs (GUEST-01..04) are satisfied by the codebase evidence. The code-review critical finding (CR-01: score re-aggregation on team change) and all 5 warnings are resolved (commits 8ff7468c, 2471988f, dddcbdd0 confirm the fixes). The full build gate (`./mvnw clean verify -Pe2e`) is BUILD SUCCESS per 113-03-SUMMARY.

The `human_needed` status is set because 4 client-side JS behaviors (datalist resolution, prefill, clone, WR-05 visual confirmation) cannot be verified programmatically and require a browser session.

---

_Verified: 2026-06-01T16:00:00Z_
_Verifier: Claude (gsd-verifier)_
