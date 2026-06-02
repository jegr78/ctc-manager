---
phase: 115-guest-marking-visibility
plan: 03
subsystem: ui
tags: [admin, thymeleaf, racelineup, guest-marker, osiv]

requires:
  - phase: 115-01
    provides: ".guest-marker / .guest-label CSS classes in admin.css (the marker glyph + accent)"
  - phase: 113-guest-assignment-foundation
    provides: "RaceLineup.guest — read directly on matchday-detail via OSIV; resolved per result for race-detail"
provides:
  - "RaceDetailData.guestDriverMap (Map<UUID,Boolean>) prepared in RaceService.getRaceDetailData"
  - "guestDriverMap exposed in RaceController detail model"
  - "Guest star marker on admin race-detail result rows and matchday-detail lineup chips, plus actual sub-team name on chips"
affects: [115-06]

tech-stack:
  added: []
  patterns:
    - "Service-prepared per-driver guest flag map (no template SpEL); reuses the lineup Optional already fetched for driverTeamMap (no extra query)"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/RaceService.java
    - src/main/java/org/ctc/admin/controller/RaceController.java
    - src/main/resources/templates/admin/race-detail.html
    - src/main/resources/templates/admin/matchday-detail.html
    - src/test/java/org/ctc/domain/service/RaceServiceTest.java
    - src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java

key-decisions:
  - "guestDriverMap placed immediately after driverTeamMap in RaceDetailData; both construction sites updated (RaceService.java + RaceControllerCalendarTest.java)"
  - "matchday-detail reads lu.guest directly via OSIV (field access, not SpEL logic) and shows lu.team.shortName (actual sub-team, D-08/D-11)"

patterns-established:
  - "Inline .guest-marker span gated by th:if used in admin templates (same as Plan-02 graphics) instead of the guestMarker fragment — see Deviation"

requirements-completed: [MARK-04]

duration: 18min
completed: 2026-06-01
---

# Phase 115 Plan 03: Admin Detail Guest Markers Summary

**Admin race-detail result rows and matchday-detail lineup chips now show the amber guest star; matchday chips additionally show the actual fielding sub-team name. The race-detail flag is service-prepared in a guestDriverMap (no extra query, no template logic).**

## Performance

- **Tasks:** 2 completed (Task 1 TDD)
- **Files modified:** 6 (1 service, 1 controller, 2 templates, 2 test classes)

## Accomplishments

- **Task 1 (data):** Added `Map<UUID,Boolean> guestDriverMap` to `RaceDetailData` (after `driverTeamMap`). The existing per-result loop now fetches the lineup `Optional` once into a local and derives BOTH the team name and `guestDriverMap.put(driverId, lineup.map(RaceLineup::isGuest).orElse(false))` — no second repository call. Updated both construction sites (`RaceService.java:146`, `RaceControllerCalendarTest.java:111`). RaceServiceTest 17→18 green, RaceControllerCalendarTest 9 green.
- **Task 2 (render):** Thin controller addition `model.addAttribute("guestDriverMap", data.guestDriverMap())`. race-detail.html Driver cell shows the star gated by a null-safe `guestDriverMap[result.driver.id]` lookup; matchday-detail.html chip shows the star gated by `lu.guest` plus a `.guest-label` with `lu.team.shortName`.

## Deviation (architecture, low blast-radius)

The plan's race-detail key_link assumed `th:replace="~{admin/fragments/guest-marker :: guestMarker(${...})}"`. The repository's **PLAT-07 build guard** (`pom.xml`) forbids any `th:replace/insert/include` fragment call containing a `${...}` expression argument (only `layout(${pageTitle}` is whitelisted). A fragment call with the map-lookup expression would fail the build at the `validate` phase. Resolution: used the **inline `.guest-marker` span gated by `th:if`** — the exact pattern Plan 02 established for the graphics — which renders the identical ★ glyph and `.guest-marker` class (so D-02 visual consistency is preserved via the central CSS token, the real single-source mechanism). All plan grep acceptance criteria (`guest-marker`, `guest-label`, `guestDriverMap`) still pass.

Note: the Plan-01 `guestMarker` Thymeleaf fragment is consequently not consumed by these admin surfaces. Plan 05 (site driver-profile) faces the same PLAT-07 constraint and will use inline spans too. Whether to keep or remove the now-unused fragment is a candidate cleanup for Plan 06 / phase review — left in place for now (out of this plan's scope).

## Verification

- Task 1 targeted: `RaceServiceTest` (18) + `RaceControllerCalendarTest` (9) green.
- Task 2: `./mvnw clean test-compile` succeeds; PLAT-07 guard OK; grep gates pass (guestDriverMap, guest-marker, guest-label).
- Construction-site audit: exactly the two known `new RaceDetailData` sites, both updated.
- Full wave-end `clean verify` deferred to after Plan 115-05 (last Wave-2 plan) — single run per CLAUDE.md test discipline.

## Self-Check: PASSED

Edits confined to the plan's files_modified whitelist. Deviation documented above.
