---
phase: 113-guest-assignment-foundation
plan: 03
subsystem: admin-ui
tags: [guest-drivers, controller, thymeleaf, datalist, vanilla-js]
requires:
  - "RaceLineupService 3-arg saveLineup + getGuestLineups (113-02)"
  - "DriverService.findAll()"
provides:
  - "RaceLineupController guest_* param parsing + allDrivers/guestLineups GET model"
  - "race-lineup.html per-team Add-Guest block (datalist typeahead, sub-team select, prefill)"
  - "guest-lineup.js datalist->hidden guest_<driverId> resolution + clone/remove rows"
  - "append-only guest-* admin.css classes"
affects:
  - "Phase 115 (visual guest marking renders on these same surfaces)"
tech-stack:
  added: []
  patterns:
    - "Native <datalist> typeahead (no JS framework, no search endpoint) — D-05"
    - "guest_<driverId>=teamId param namespace alongside driver_<id>=teamId — D-09"
    - "Real @Transactional ScoringService proxy in controller IT (no @MockitoBean) — exercises guest-removal cascade end-to-end"
key-files:
  created:
    - src/main/resources/static/admin/js/guest-lineup.js
  modified:
    - src/main/java/org/ctc/admin/controller/RaceLineupController.java
    - src/main/resources/templates/admin/race-lineup.html
    - src/main/resources/static/admin/css/admin.css
    - src/test/java/org/ctc/admin/controller/RaceLineupControllerTest.java
key-decisions:
  - "Driver resolution iterates datalist <option> by value comparison (no CSS.escape needed) — simpler and injection-safe vs the CSS.escape approach the plan offered"
  - "Each guest row carries a Remove button (JS row.remove()) so D-04 'individually removable' is met; removal = row not resubmitted = service cascade drops it"
  - "guest-lineup.js included inline at the end of the race-lineup <section> fragment (page-specific), not in the global layout"
requirements-completed: [GUEST-01, GUEST-03]
duration: 18 min
completed: 2026-06-01
---

# Phase 113 Plan 03: Admin Guest Lineup UI Summary

Wired the admin lineup form end-to-end for guests: the controller parses `guest_*` params and feeds `allDrivers`/`guestLineups` to the GET model, and `race-lineup.html` gained a per-team Add-Guest block (native `<datalist>` typeahead, sub-team select, prefill of existing guests) driven by a self-contained `guest-lineup.js`.

## Tasks

- **Task 1** — `RaceLineupController` injects `DriverService`, adds `guestLineups` + `allDrivers` to the GET model, and parses both `driver_`/`guest_` namespaces into separate maps passed to the 3-arg `saveLineup`. Controller IT extended: GET asserts all 5 attributes; a guest-persist test (`isGuest()==true` for an off-roster driver); a guest-removal test that pre-creates a guest lineup + `RaceResult` and asserts both are gone after a save without the guest param — running the real `@Transactional` `ScoringService` cascade (no `@MockitoBean`). Commit `cc8bd72e`.
- **Task 2** — `race-lineup.html` Add-Guest block (datalist, prefilled rows guarded by `gl.team.parentOrSelf.id == entry.team.id`, blank row, sub-team select when `hasSubTeams`, Add-another-guest button), new `guest-lineup.js` (resolve datalist option → hidden `guest_<driverId>` + (sub-)team id, clone/remove rows), append-only `admin.css` guest classes. No inline styles. Commit `4ffb8d1d`.

2 tasks, 5 files (1 created, 4 modified), 2 atomic commits.

## Verification

- `./mvnw -Dtest=RaceLineupControllerTest clean test` — 6/6 green (3 existing + 3 new); GET test renders the new template without Thymeleaf errors.
- **Phase-end gate** `./mvnw clean verify -Pe2e` — BUILD SUCCESS: all unit + IT + Playwright E2E tests green, JaCoCo "All coverage checks have been met" (≥82%), SpotBugs `BugInstance size is 0`, Checkstyle (validate) clean.
- `admin.css` grew 2082 → 2110 lines (append-only, no section loss); zero `style=` attributes in the template.

## Deviations from Plan

None - plan executed exactly as written.

Note: the plan's per-task verify command `-Dit.test=RaceLineupControllerTest` matches nothing because the class is a `@SpringBootTest`-backed `*Test` (untagged, runs under Surefire), not a Failsafe `*IT`. Used `-Dtest=RaceLineupControllerTest` for the targeted run; the phase-end `clean verify -Pe2e` is the authoritative gate. This is a verify-command mismatch, not a behavioral deviation.

**Total deviations:** 0.

## Issues Encountered

None.

## Next Phase Readiness

Phase 113 complete: GUEST-01..04 delivered. Guests can be added/edited/removed from the lineup form, persist with `is_guest=true`, auto-derive into results, and round-trip through backup. Phase 114 (scoring/crediting) and Phase 115 (visual marking) build on the `is_guest` flag via the existing lineup join.
