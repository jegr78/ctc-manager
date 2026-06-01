# Phase 113: Guest Assignment Foundation - Context

**Gathered:** 2026-06-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the data-model foundation and admin CRUD for **guest-driver assignments** in race lineups and results. An admin can add, edit, and remove a guest — any existing driver in the system (not restricted to the season roster) — fielded for one of the race's two teams, and the assignment is durably flagged as a guest entry.

**In scope:** Flyway migration (guest flag + lineup uniqueness), `RaceLineup` guest flag, lineup-form guest CRUD UI (datalist typeahead), service-layer save/validation/orphan-cleanup, re-edit prefill of existing guests. Results entry for guests works automatically because results rows derive from the lineup.

**Out of scope (other phases):**
- Scoring & personal driver-ranking crediting → **Phase 114** (SCORE-01..03)
- Visual marking / guest indicators across graphics, admin views, public site → **Phase 115** (MARK-01..06)
- Ad-hoc/free-text guest creation, bulk guest import, guest-specific scoring rules, reassigning points away from the fielding team → **out of scope for v1.17** (see REQUIREMENTS.md "Out of Scope")

</domain>

<decisions>
## Implementation Decisions

### Data Model — Guest Flag Placement
- **D-01:** Guest flag lives **only on `RaceLineup`** — new boolean column `is_guest` (default `false`). `RaceResult` gets **no** guest column; it already derives its team from `RaceLineup` via `RaceLineupRepository.findByRaceIdAndDriverId`, and all downstream views/graphics (Phase 115) derive guest status from the same lineup join. Single source of truth, no denormalization drift — consistent with CLAUDE.md "RaceLineup is Source of Truth" and "No Fallback Calculations".
- **D-02:** Schema changes go in a **new migration `V18`** (V18 confirmed free; highest existing is V17). H2 + MariaDB compatible, existing V1–V17 untouched (CLAUDE.md "Do Not Modify Flyway Migrations").

### Data Model — Lineup Uniqueness / Dedup
- **D-03:** Add **`UNIQUE(race_id, driver_id)` on `race_lineups`** (analogous to the existing constraint on `race_results`) in the same/new V18 migration, **plus** service-layer validation in `saveLineup` that rejects duplicate driver assignments with a clear error message. Prevents a driver appearing twice in one race lineup (e.g. a guest who is also a roster driver of a participating team, or selected twice).
- **⚠ Constraint risk for planner:** Before adding the unique constraint, verify no existing `race_lineups` rows already violate `(race_id, driver_id)`. If duplicates exist in any environment's data, the migration must dedupe first or the constraint creation fails. Include this in the NOT-NULL/Constraint audit.

### Admin UI — Guest-Add UX
- **D-04:** Per-team **"Add Guest Driver" block** under each team card in `race-lineup.html`, separate from the existing season-roster table. Supports multiple guest rows, each individually removable.
- **D-05:** Driver selection uses a **native HTML `<datalist>` typeahead** (`<input list=...>` + `<datalist>` of all drivers rendered server-side as `psnId (nickname)`), **not** a full `<select>` dropdown. A hidden field carries the resolved `driverId`. No new search endpoint, no JS framework, no frontend build tool — fits the Thymeleaf SSR / Spring-native stack. (The full all-drivers `<select>` pattern in `driver-merge.html` is the conceptual analog, but the user explicitly wants typeahead filtering.)
- **D-06 (planner note):** The datalist input shows display text; the service/form must resolve the chosen driver back to a `driverId` (e.g. hidden field populated on selection, or resolve `psnId`→driver on submit). Planner decides the exact resolution mechanism.

### Admin UI — Fielding-Team Scope
- **D-07:** The guest's fielding team is **restricted to the race's two teams** (`race.homeTeam` / `race.awayTeam`) — not any team in the system. Consistent with the home-vs-away race structure and the per-team Add-Guest block.
- **D-08:** For a participating team that **has sub-teams** (`LineupTeamEntry.hasSubTeams = true`), the Add-Guest block gets an additional **sub-team `<select>`** (same `entry.subTeams` options used for roster drivers); the guest is assigned to the concrete sub-team (the actual fielding unit), not the parent. Teams without sub-teams: team is implied by the block. This keeps guest assignment consistent with existing sub-team lineup semantics (and avoids skewing Phase 114 scoring).

### Form Transport & Edit/Remove
- **D-09:** Guest rows post in a **dedicated param namespace `guest_<driverId>=teamId`** (where `teamId` is the concrete (sub-)team), alongside the existing roster `driver_<id>=teamId` params. `saveLineup` keeps its **delete-all-then-recreate** model, handles both namespaces, and sets `is_guest = true` on guest entries (roster entries stay `false`). Remove = guest row not resubmitted; edit team = changed value. A `RaceLineup` constructor/setter carries the flag.

### Re-Edit — Prefill Existing Guests
- **D-10:** On reopening the lineup form, existing guests must be restored as guest rows. The lineup service (`getLineupData` / a new accessor) returns a **separate list of existing guest lineup entries** (`is_guest = true`) per team; the template prefills them in the Add-Guest block (datalist input prefilled with `psnId`, hidden `driverId`, team/sub-team from the block). Roster assignments continue via the existing `getDriverAssignments` driver→team map (which today carries no flag).

### Orphaned Results on Guest Removal
- **D-11:** When a guest is removed from the lineup, **cascade-delete the guest's `RaceResult` for that race** transactionally inside the save flow, then call `scoringService.aggregateMatchScores(race)` so standings stay correct. Prevents orphaned results with no lineup-derived team (which would otherwise fall back to `"?"` in `toRaceData`). Consistent with CLAUDE.md "Score Aggregation on Result Save". (Planner: scope the cascade to guest entries; do not change existing roster-removal behavior unless the planner finds the same orphan risk already applies.)

### Claude's Discretion
- Exact `<datalist>`→`driverId` resolution mechanism (D-06).
- Exact service/record signatures and whether the guest accessor extends `getLineupData` or is a new method.
- Whether `RaceLineup` gets a 4-arg constructor `(race, driver, team, isGuest)` or a setter; keep existing 3-arg constructor working for roster callers.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & Roadmap
- `.planning/REQUIREMENTS.md` §"GUEST — Guest Assignment" — GUEST-01..04, the four requirements this phase satisfies; also the "Out of Scope" table.
- `.planning/ROADMAP.md` §"Phase 113: Guest Assignment Foundation" — goal + 4 success criteria.

### Code touchpoints (source of truth for the model + flow)
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — add `is_guest` flag here (D-01); 3-arg constructor + `@ToString(exclude=...)`.
- `src/main/java/org/ctc/domain/model/RaceResult.java` — **no** guest column; existing `UNIQUE(race_id, driver_id)` is the analog for D-03; no `team_id` (team derived from lineup).
- `src/main/java/org/ctc/domain/service/RaceLineupService.java` — `getLineupData`, `getDriverAssignments`, `saveLineup` (delete-all-recreate). Primary change surface for D-09/D-10/D-11.
- `src/main/java/org/ctc/admin/controller/RaceLineupController.java` — parses `driver_*` params today; extend for `guest_*` (D-09).
- `src/main/resources/templates/admin/race-lineup.html` — per-team roster table; add the Add-Guest block + datalist (D-04/D-05/D-08/D-10).
- `src/main/java/org/ctc/domain/service/RaceFormDataService.java` §`populateDrivers`, `toRaceData` — results rows derive from lineup (why guests flow into results automatically; why orphaned results fall back to `"?"`).
- `src/main/java/org/ctc/domain/service/RaceService.java` §`saveResults` — calls `scoringService.aggregateMatchScores(race)` (D-11 must follow the same aggregation discipline).
- `src/main/resources/templates/admin/driver-merge.html` — existing all-drivers picker pattern (conceptual analog for the driver list; D-05 upgrades it to typeahead).
- `src/main/resources/db/migration/V17__add_matches_walkover_team_id.sql` — most recent migration; V18 is the next free version.

### Project conventions
- `CLAUDE.md` §"RaceLineup is Source of Truth", §"Score Aggregation on Result Save", §"No Fallback Calculations", §"Do Not Modify Flyway Migrations", §"DTOs instead of Entities in Controllers", §"No Inline Styles on Buttons" / CSS Guidelines.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **All-drivers picker** (`driver-merge.html`): `<option th:each="d : ${allDrivers}" th:text="${d.psnId + ' (' + d.nickname + ')'}">` — the same `allDrivers` model + display format feeds the new `<datalist>`.
- **`RaceLineupRepository.findByRaceIdAndDriverId`** — already the lineup→team join used by both `RaceService` and `RaceFormDataService`; guest status will be derived through this same join in Phase 115.
- **`scoringService.aggregateMatchScores(race)`** — the mandated re-aggregation entry point after any result mutation (used in D-11).

### Established Patterns
- **Lineup save = delete-all-then-recreate** from `driver_<id>=teamId` params (`RaceLineupController.saveLineup` → `RaceLineupService.saveLineup`). The guest namespace (D-09) extends this without changing the model.
- **Results rows are lineup-derived** (`RaceFormDataService.populateDrivers`): adding a guest to the lineup automatically surfaces it in the results form — GUEST-02 ("record guest result") needs no separate results-form work beyond the lineup being correct.
- **Sub-team lineup handling**: `LineupTeamEntry(team, drivers, subTeams, hasSubTeams)` — parent entry with per-driver sub-team dropdown. D-08 mirrors this for guests.
- **`@NotNull` on `RaceLineup.driver/team/race`** — guest rows still require a valid driver and team; no nullable relaxations.

### Integration Points
- New migration `V18` (guest flag + lineup unique constraint) — must be H2+MariaDB compatible; verify no existing `(race_id, driver_id)` duplicates before adding the constraint.
- `RaceLineup` entity flag → consumed by Phase 114 (scoring/crediting) and Phase 115 (visual marking) via the existing lineup join.
- Seeders (`TestDataService`, `DevDataSeeder`) and backup export/import (`findAllForBackup` style) may need the new column wired; planner should audit per CLAUDE.md "Entity Refactoring Order".

</code_context>

<specifics>
## Specific Ideas

- User explicitly rejected a full all-drivers `<select>` for guest selection in favor of a **typeahead** — implemented as a native `<datalist>` (D-05). This is a firm UX preference, not Claude's discretion.
- A guest is **always an existing driver** selected from the pool — never on-the-fly driver creation in the lineup form (REQUIREMENTS.md "Out of Scope").

</specifics>

<deferred>
## Deferred Ideas

- **Guest scoring & personal driver-ranking crediting** — Phase 114 (SCORE-01..03). The `is_guest` flag laid down here is the hook.
- **Visual guest marking** across Scorecard / Provisional Scores / matchday graphics, admin race & matchday detail, driver-ranking (admin + public), public driver-profile — Phase 115 (MARK-01..06); concrete visual treatment decided against a rendered reference via `playwright-cli`.

None — discussion stayed within phase scope (the two items above are explicit later phases of the same milestone, not scope creep).

</deferred>

---

*Phase: 113-Guest Assignment Foundation*
*Context gathered: 2026-06-01*
