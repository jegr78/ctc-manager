---
phase: 113-guest-assignment-foundation
reviewed: 2026-06-01T00:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - src/main/java/org/ctc/admin/controller/RaceLineupController.java
  - src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java
  - src/main/java/org/ctc/domain/model/RaceLineup.java
  - src/main/java/org/ctc/domain/service/RaceLineupService.java
  - src/main/resources/db/migration/V18__add_race_lineups_is_guest.sql
  - src/main/resources/static/admin/css/admin.css
  - src/main/resources/static/admin/js/guest-lineup.js
  - src/main/resources/templates/admin/race-lineup.html
  - src/test/java/db/migration/V18MigrationIT.java
  - src/test/java/org/ctc/admin/controller/RaceLineupControllerTest.java
  - src/test/java/org/ctc/backup/restore/entity/RaceLineupRestorerTest.java
  - src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java
  - src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java
findings:
  critical: 1
  warning: 5
  info: 4
  total: 10
status: issues_found
---

# Phase 113: Code Review Report

**Reviewed:** 2026-06-01
**Depth:** standard
**Files Reviewed:** 13
**Status:** issues_found

## Summary

Phase 113 adds an `is_guest` flag to `race_lineups` (Flyway V18), guest save/cascade logic in `RaceLineupService`, a controller that parses `guest_*` params, and an Add-Guest admin UI (datalist typeahead + vanilla JS). The migration is correctly additive (single `ADD COLUMN`, no UNIQUE re-add), the backup restorer uses the backward-compatible `row.path("guest").asBoolean(false)` default, and the MixIn/exporter round-trip the `guest` field cleanly. No hardcoded secrets, no `eval`, no inline styles in the template, and `admin.css` was appended (existing sections intact).

The dominant concern is a **score re-aggregation gap**: the cascade only re-aggregates when a guest is *dropped*, not when a kept guest's team is *changed*, which silently desyncs standings from the (mutated) source-of-truth team attribution. Several robustness and quality issues follow.

## Critical Issues

### CR-01: Guest team-change keeps results but skips score re-aggregation

**File:** `src/main/java/org/ctc/domain/service/RaceLineupService.java:120-135`
**Issue:** `saveLineup` re-aggregates scores only for guests that were *dropped* (`droppedGuestDriverIds`). When a guest is *kept* but reassigned to a different team (e.g. moved between sub-teams, or — for a standalone parent — re-pointed at a different `team_id`), the old `RaceLineup` is deleted and a new one with the new `team_id` is inserted, but `scoringService.aggregateMatchScores(race)` is **not** called. `ScoringService.isDriverInTeam` (line 217-223) derives the driver→team attribution exclusively from the current `RaceLineup` row, so any existing `RaceResult` for that guest is now attributed to a different team in the standings while the aggregated `Match`/`PlayoffMatchup` scores still reflect the old team. This violates the project rule "after any RaceResult-affecting mutation, call `aggregateMatchScores`" — here the result is untouched but its *effective team membership* changed, which is functionally a results mutation. Standings drift until the next unrelated result save re-aggregates.

The test `givenGuestStillPresent_whenSaveLineup_thenNoResultDeleteAndNoReaggregation` (RaceLineupServiceTest.java:328) actually *encodes* this gap as intended behavior — it asserts `verifyNoInteractions(scoringService)` when a guest stays, including the team-change sub-case.

**Fix:** Re-aggregate whenever the lineup write could have changed any team attribution for a driver that has a result, not just on drop. Simplest correct form — compare the prior team-per-guest map against the new one and re-aggregate if any kept guest's team changed (or, conservatively, re-aggregate whenever any guest existed before or after the save and a matching result exists):
```java
// before deleteAll: capture prior guest team mapping
var priorGuestTeams = existing.stream()
        .filter(RaceLineup::isGuest)
        .collect(Collectors.toMap(l -> l.getDriver().getId(), l -> l.getTeam().getId()));
// ... after re-inserting guests ...
boolean teamChanged = guestAssignments.entrySet().stream()
        .anyMatch(e -> priorGuestTeams.containsKey(e.getKey())
                && !e.getValue().equals(priorGuestTeams.get(e.getKey()))
                && raceResultRepository.findByRaceIdAndDriverId(raceId, e.getKey()).isPresent());
if (!droppedGuestDriverIds.isEmpty() || teamChanged) {
    scoringService.aggregateMatchScores(race);
}
```
Add a service test: existing guest with a result, re-saved to a different `team_id`, asserts `aggregateMatchScores(race)` is called.

## Warnings

### WR-01: delete-all + re-insert with same `(race_id, driver_id)` risks UNIQUE violation on flush

**File:** `src/main/java/org/ctc/domain/service/RaceLineupService.java:109-127`
**Issue:** `deleteAll(existing)` followed by `save(new RaceLineup(...))` for the same `(race_id, driver_id)` pair runs inside one `@Transactional`. Hibernate's default `ActionQueue` executes INSERTs before DELETEs at flush, so re-saving a driver who already had a row can violate `uk_race_lineup_driver` (race_id, driver_id) — V1 line 277. This is a pre-existing pattern (roster saves already did delete+re-insert), but Phase 113 broadens the re-insert surface (guest rows, and roster↔guest transitions for the same driver) and adds no flush ordering guard. The controller `@Transactional` tests pass only because each re-saves *different* drivers or relies on test rollback; the same-driver re-save path is untested.
**Fix:** Force the delete to flush before the inserts, e.g. inject and call `raceLineupRepository.flush()` immediately after `deleteAll(existing)`, OR delete only the rows whose `(race_id, driver_id)` is not being re-inserted and update the rest in place. Add an IT against H2 that re-saves the same driver (roster→guest and guest→guest team change) within one request to prove no constraint violation.

### WR-02: No validation that guest driver is off-roster; off-roster contract is unenforced

**File:** `src/main/java/org/ctc/domain/service/RaceLineupService.java:91-100`
**Issue:** The only guard is the roster-vs-guest collision check *within the same request* (lines 96-100). Nothing prevents marking a current season-roster driver as a guest, or marking the same driver `is_guest=true` for one team while they are a real roster member of another team in the season. A guest is supposed to be an off-roster stand-in (per the phase intent and `getResultsFormData` auto-derivation); persisting a roster driver as a guest produces a misleading `is_guest=true` flag that downstream consumers (standings narratives, exports) may treat differently.
**Fix:** In the guest loop, verify the driver is not a `SeasonDriver` of the race's season (`seasonDriverRepository.findBySeasonIdAndDriverId(...)` or equivalent) before persisting `is_guest=true`; throw `BusinessRuleException` otherwise, or downgrade them to a roster entry. Add a test for "roster driver submitted as guest → rejected / coerced".

### WR-03: Unvalidated user-supplied team ids accepted for guest/roster assignment

**File:** `src/main/java/org/ctc/admin/controller/RaceLineupController.java:50-66`
**Issue:** `params` map values are parsed straight into `teamId` and persisted via `teamRepository.findById(...)` with no check that the team belongs to the race's season or to the home/away parent of the section the guest was rendered under. A crafted POST (`guest_<driverId>=<any-team-uuid>`) assigns a guest to an arbitrary team in the database. Because controllers must not trust client-supplied ids beyond existence, this is an authorization/data-integrity gap, not just UX. The JS constrains values client-side only.
**Fix:** In `saveLineup`, validate each assigned `teamId` resolves to a team that is `home/away` (or a sub-team of their parent) for `raceId`; reject otherwise with `BusinessRuleException`. Keep the existence lookups but add the season/match-scope check server-side.

### WR-04: Malformed `driver_`/`guest_` key or value surfaces as a raw 500

**File:** `src/main/java/org/ctc/admin/controller/RaceLineupController.java:58-62`
**Issue:** `UUID.fromString(key.substring(...))` and `UUID.fromString(entry.getValue())` throw `IllegalArgumentException` on any malformed segment (e.g. `guest_notauuid=...`). This is caught by the catch-all `@ExceptionHandler(Exception.class)` in `GlobalExceptionHandler`, so it does not crash the app, but it renders a generic error page instead of a field-level validation message and gives an attacker a cheap way to trigger 500s. No test covers malformed input.
**Fix:** Wrap the parse in a guarded helper that skips (or collects as a binding error) unparseable keys/values rather than throwing, and surface an `errorMessage` flash attribute on bad input. Add a test posting a malformed `guest_<garbage>` param.

### WR-05: Guest with empty/unselected sub-team is silently dropped

**File:** `src/main/resources/static/admin/js/guest-lineup.js:31-38` and `src/main/resources/templates/admin/race-lineup.html:74-83`
**Issue:** For a sub-team section, a fresh guest row's sub-team `<select>` defaults to the empty `-- Select sub-team --` option. If the operator types a valid guest driver but forgets to choose a sub-team, `teamValueForRow` returns `''`, `syncRow` writes an empty `hidden.value`, and the controller's `hasText` guard (RaceLineupController.java:54) silently skips the entry. The save reports success ("N drivers assigned") while dropping the guest, with no feedback. The success message count is also misleading.
**Fix:** Either default the sub-team select to the first real sub-team, or block submit / show an inline error when a guest driver is entered without a team. At minimum, have the controller count and report skipped guest rows so the success message is accurate.

## Info

### IN-01: Comment-pollution — file-header / Phase-style restatements in touched source & SQL

**File:** `src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java:13-27`; `src/test/java/org/ctc/backup/restore/entity/RaceLineupRestorerTest.java:19-26`
**Issue:** The Javadoc blocks restate schema/conventions already centralized in `CLAUDE.md` (e.g. "Schema (V1/V18): ...", "RaceLineup is the source-of-truth ...", "Auditing bypass ..."). Project convention bans file-header comment blocks that restate what the file does or repeat conventions, and discourages multi-line Javadoc beyond non-obvious WHY. The `"Schema (V1/V18)"` phrasing is also a thinly-veiled phase/version marker that will rot when V19+ touches the table.
**Fix:** Trim to a single-line WHY where genuinely non-obvious (the auditing-bypass note is the only candidate); drop the schema restatement and source-of-truth paragraph. Apply the same trim to the test-class Javadoc.

### IN-02: `guest-add-row` / `guest-remove` rely solely on generic btn classes — verify visual parity

**File:** `src/main/resources/templates/admin/race-lineup.html:72,82,84`; `src/main/resources/static/admin/css/admin.css:2084-2110`
**Issue:** The new `.guest-add-row` and `.guest-remove` buttons have no dedicated CSS rule; they style only via `btn btn-sm`. That is convention-compliant (no inline styles), but there is no spacing rule separating the "Add another guest" button from the rows, and the JS class hook (`guest-add-row`) has no visual affordance distinguishing it from a Remove button. Confirm via playwright-cli that the block renders cleanly on desktop + mobile.
**Fix:** Optional — add a small `margin-top` rule for `.guest-add-row` in the appended `admin.css` block, or confirm the existing `.guest-row` spacing suffices visually.

### IN-03: `resolveDriverId` matches the first option by display string — ambiguous PSN/nickname collisions

**File:** `src/main/resources/static/admin/js/guest-lineup.js:7-15`
**Issue:** Driver resolution is by exact `psnId (nickname)` string. Two drivers sharing the same display string resolve to whichever option appears first, silently assigning the wrong driver. The datalist `value` is human text, not the id.
**Fix:** Low priority given PSN ids are typically unique; if collisions are possible, render the `data-id` as part of the option value or de-duplicate display strings server-side.

### IN-04: Standalone-team checkbox `checked` state ignores team match

**File:** `src/main/resources/templates/admin/race-lineup.html:49-53`
**Issue:** For non-sub-team entries the checkbox uses `th:checked="${driverAssignments.containsKey(sd.driver.id)}"` — it reflects whether the driver is assigned *anywhere*, not specifically to this team. In the current single-team-per-section render this is effectively correct, but it is a latent mismatch if a driver were ever assignable across the two sections.
**Fix:** Tighten to compare the assigned team id: `th:checked="${driverAssignments.get(sd.driver.id) == entry.team.id}"` for symmetry with the sub-team `th:selected` expression on line 44.

---

_Reviewed: 2026-06-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
