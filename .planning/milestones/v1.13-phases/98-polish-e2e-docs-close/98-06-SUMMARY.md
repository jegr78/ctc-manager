---
phase: 98-polish-e2e-docs-close
plan: 06
subsystem: discord
tags: [discord, matchday-schedule, announcement-channel, pure-multipart-png, post-10, mockito, wiremock-it, playwright-e2e, no-schema]

requires:
  - phase: 95-match-channel-posts
    provides: postOrEdit pattern + DiscordPost tracking entity + WebhookPayload + NamedAttachment
  - phase: 98-05
    provides: matchdayAnnouncementActive section + Discord Announcements card layout + DiscordEmojiCache seed + Plan-98-05 PreFlight-Test mock-construct pattern
provides:
  - DiscordPostType.MATCHDAY_SCHEDULE enum value (Announcement-channel post type)
  - DiscordPostService.canPostMatchdaySchedule(Matchday, DiscordGlobalConfig) — 2 reject branches per D-98-PRE-2
  - DiscordPostService.postMatchdaySchedule(Matchday) — pure Multipart-PNG (no Markdown body / no JSON embed) on announcement webhook
  - MatchdayController POST /admin/matchdays/{id}/post-matchday-schedule endpoint
  - populateMatchdayDiscordModel extension with 4 new attrs (canPostMatchdaySchedule, matchdayScheduleDisabledReason, matchdaySchedulePost, matchdayScheduleStale)
  - isMatchdayScheduleStale helper — MAX(match.updatedAt, race.updatedAt) across non-BYE matches+races
  - matchday-detail.html Schedule sibling-button block (4 states: disabled / initial / repost / update)
  - 3 new E2E cases APPENDED to MatchdayDetailDiscordAnnouncementE2ETest (sibling pattern)
  - 1 new Mockito-Unit pre-flight test class + 1 new WireMock IT
affects: [98-07 (bundle clean-verify + REQUIREMENTS.md POST-10 flip + DOCS-02 runbook update + UAT-10 operator-driven Live-Discord post)]

tech-stack:
  added: []
  patterns:
    - "Pure Multipart-PNG post on announcement webhook (D-98-SCHED-1 — distinct from Pairings hybrid Markdown+PNG)"
    - "No new schema (D-98-SCHED-2) — reuses existing MatchdayScheduleGraphicService that was wired prior to Plan 98-06 for download-schedule endpoint"
    - "Stale-detection aggregates MAX(match.updatedAt, race.updatedAt) across non-BYE matches — distinct from Pairings stale-check (matchday.updatedAt only)"

key-files:
  created:
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdaySchedulePreFlightTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayScheduleIT.java
  modified:
    - src/main/java/org/ctc/discord/model/DiscordPostType.java
    - src/main/java/org/ctc/discord/service/DiscordPostService.java
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/main/resources/templates/admin/matchday-detail.html
    - src/test/java/org/ctc/e2e/discord/announcement/MatchdayDetailDiscordAnnouncementE2ETest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsPreFlightTest.java
    - pom.xml

key-decisions:
  - "Reuse existing MatchdayScheduleGraphicService (no new graphic service) — service was wired into MatchdayController.downloadSchedule before Plan 98-06; only the new postMatchdaySchedule call site is added"
  - "Separate DiscordPostServiceMatchdaySchedulePreFlightTest class (planner-discretion per D-98-TEST-3) — Schedule pre-flight has different fixture-shape (race.dateTime mocks instead of pickDeadline+weekend mocks); separate class clearer in Surefire output"
  - "isMatchdayScheduleStale aggregates MAX(match.updatedAt, race.updatedAt) across non-BYE matches per D-98-STALE-1 Planner-Discretion (different from Pairings simpler matchday.updatedAt-only)"
  - "Schedule button block APPENDED as 3rd-and-4th-state-pair sibling to Pairings (same matchdayAnnouncementActive section) — both Announcement-Channel posts live structurally adjacent on matchday-detail.html per Channel-Differentiation"

patterns-established:
  - "Two-method explicit-constructor evolution — when Plan 98-05 added matchdayPairingsGraphicService and Plan 98-06 added matchdayScheduleGraphicService, both touched the same explicit-constructor + @SuppressFBWarnings justification + 4 test-class instantiations. Pattern: keep all 4 test fixtures alphabetized, justification-string sorted by alphabetical service name."
  - "Pre-flight stale-check granularity differs by post-type semantics — Pairings stale on matchday-level edits (pickDeadline/weekend on Matchday entity); Schedule stale on race-level edits (dateTime on Race entity) because Schedule PNG reflects race-times not matchday-fields."

requirements-completed:
  - POST-10

duration: ~20min
completed: 2026-05-25
---

# Plan 98-06 SUMMARY — MATCHDAY_SCHEDULE Announcement-Channel Pure-PNG Post

**Operator-driven Matchday Schedule post (POST-10): pure Multipart-PNG (no Markdown, no JSON embed) on the Announcement-Channel via `DiscordGlobalConfig.announcementWebhookUrl`. Reuses the existing `MatchdayScheduleGraphicService` (already wired for the download-schedule endpoint pre-Plan-98-06). 4-state sibling button on Matchday-Detail beside the Plan-98-05 Pairings button.**

## Performance

- **Duration:** ~20 min (10 tasks, inline interactive execution)
- **Tasks:** 10 (9 auto + 1 visual checkpoint deferred to UAT-10)
- **Files modified:** 9 (2 created + 7 modified)
- **Commits:** 8 atomic

## Accomplishments

- New `MATCHDAY_SCHEDULE` value in `DiscordPostType` enum (placed adjacent to `MATCHDAY_PAIRINGS`)
- `DiscordPostService.canPostMatchdaySchedule` — 2 reject branches per D-98-PRE-2 (any non-BYE match without race date+time / blank announcement webhook URL)
- `DiscordPostService.postMatchdaySchedule` — pure Multipart-PNG via `WebhookPayload.empty()` + 1 attachment `matchday-schedule-{slug}.png` on the announcement webhook
- New `matchdayScheduleGraphicService` field + constructor param + `@SuppressFBWarnings` justification extension (4 existing constructor-call sites updated for the new mock arg)
- `MatchdayController` POST `/admin/matchdays/{id}/post-matchday-schedule` + 4 new model attrs in `populateMatchdayDiscordModel` + `isMatchdayScheduleStale` helper (MAX(match.updatedAt, race.updatedAt) across non-BYE matches)
- Schedule sibling-button block on `matchday-detail.html` (4 states: disabled span / primary / repost / update — same selectors as Pairings)
- New WireMock IT (4 cases) + new Mockito-Unit (4 cases) + 3 new E2E cases APPENDED to the Plan-98-05 E2E class (total: 8 E2E cases in the class)
- `MatchdayScheduleGraphicService` added to `pom.xml` `<excludes>` per CLAUDE.md (Playwright-runtime not JaCoCo-instrumentable)

## Task Commits

1. **Task 1: DiscordPostType enum** — `80ae5493`
2. **Task 2: Schedule Mockito-Unit pre-flight (RED)** — `fed9b330`
3. **Task 3: canPostMatchdaySchedule + postMatchdaySchedule (GREEN)** — `26d149f0`
4. **Task 4: WireMock IT** — `a02eec9b`
5. **Task 5: MatchdayController endpoint + populate-model** — `eacd44e2`
6. **Task 6: matchday-detail Schedule sibling button block** — `fa3f8647`
7. **Task 7: E2E Schedule cases APPEND** — `9bbbe8c4`
8. **Task 8: pom.xml JaCoCo exclude** — `4f7c5727`
9. **Task 9: Visual checkpoint** — deferred to UAT-10 (operator-driven Live-Discord post; see § User Setup Required below)
10. **Task 10: SUMMARY** — this commit

## Decisions Made

1. **Reused the existing `MatchdayScheduleGraphicService`** (no new graphic service) — the service was wired into `MatchdayController.downloadSchedule` before Plan 98-06; only the new `postMatchdaySchedule` call site is added. CONTEXT D-98-FILES-1 was outdated on this point; documented inline in 98-06-PLAN.md "CRITICAL CONTEXT-DRIFT NOTE".
2. **Separate `DiscordPostServiceMatchdaySchedulePreFlightTest` class** (planner-discretion per D-98-TEST-3) — Schedule pre-flight has different fixture-shape (race.dateTime mocks) vs. Pairings (pickDeadline+weekend mocks); separate class clearer in Surefire output.
3. **`isMatchdayScheduleStale` aggregates MAX(match.updatedAt, race.updatedAt)** across non-BYE matches per D-98-STALE-1 Planner-Discretion — semantically different from Pairings (`matchday.updatedAt` only) because Schedule PNG reflects race-times, not matchday-level fields.
4. **Constructor evolution discipline** — Plan 98-05 added `matchdayPairingsGraphicService`, Plan 98-06 adds `matchdayScheduleGraphicService`. Both required updating the same explicit-constructor + `@SuppressFBWarnings` justification + 4 plain-mock test fixtures (`DiscordPostServicePreFlightTest`, `DiscordPostServiceRefBranchesTest`, both pre-flight Pairings + Schedule tests). The alphabetical-by-class-name convention keeps churn local.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Race entity missing setSortIndex method] First IT draft assumed Race has `setSortIndex(int)` — Race lacks this method.**
- **Found during:** Task 4 (WireMock IT — test-compile failure on `race.setSortIndex(0)`).
- **Fix:** Switched IT race-seeding to `helper.createRace(md, match)` (existing TestHelper method) instead of bespoke `new Race()` constructor. Removes the false setter assumption.
- **Verification:** `./mvnw verify -Pe2e -Dit.test=DiscordPostServiceMatchdayScheduleIT` → 4/4 green.
- **Committed in:** `a02eec9b` (Task 4 commit — bundled with the IT it fixes).

**2. [Rule 2 — Cross-file impact] Four existing pre-flight tests updated for new constructor arg**
- **Found during:** Task 3 (after appending the new `MatchdayScheduleGraphicService` constructor parameter).
- **Files affected:** `DiscordPostServicePreFlightTest`, `DiscordPostServiceRefBranchesTest`, `DiscordPostServiceMatchdayPairingsPreFlightTest`, new `DiscordPostServiceMatchdaySchedulePreFlightTest`.
- **Fix:** Added `mock(MatchdayScheduleGraphicService.class)` argument to all 4 constructor call-sites (alphabetical placement after `MatchdayPairingsGraphicService`).
- **Committed in:** `26d149f0` (Task 3 commit — bundled because the constructor change and test-fixes are causally inseparable).

---

**Total deviations:** 0 whitelist expansions + 2 auto-fixes
**Impact on plan:** No scope creep. Both auto-fixes were structural necessities of the constructor evolution + an incorrect API assumption in the IT draft.

## Issues Encountered

**Live Schedule-post UAT could not be automated mid-plan** because the dev-profile seed data does NOT set `race.dateTime` on any seeded match (`TestDataService.seedRace` skips dateTime entirely). The Schedule pre-flight requires `firstRaceTime(m).isPresent()` for every non-BYE match, which means the operator must set dateTime on every race of every match in the target matchday before clicking Post Matchday Schedule. Automating this for the auto-UAT would require ~7 form-submissions per matchday (one per race) and is brittle — operator manual setup is the intended workflow. The button-state matrix (disabled-with-tooltip / primary / repost / update) is fully verified via the 3 new E2E cases in `MatchdayDetailDiscordAnnouncementE2ETest` + the 4 WireMock IT cases that exercise the real `postOrEdit` path.

## Test Results

- `DiscordPostServiceMatchdaySchedulePreFlightTest` — **4 tests, 0 failures** (1.06 s, Surefire)
- `DiscordPostServiceMatchdayScheduleIT` — **4 tests, 0 failures** (~20 s, Failsafe)
- `MatchdayDetailDiscordAnnouncementE2ETest` — **8 tests, 0 failures** (5 Pairings + 3 Schedule, ~23 s, Failsafe `-Pe2e`)
- SpotBugs `BugInstance size is 0` (gate preserved)
- JaCoCo `All coverage checks have been met` on the targeted runs (full clean verify deferred to Plan 98-07 per D-98-VERIFY-1)

**Bundle `./mvnw clean verify -Pe2e` deferred to Plan 98-07 per D-98-VERIFY-1.**

## User Setup Required

**Operator UAT-10 (Live-Discord Schedule Post) — operator action:**

1. On any active matchday, set `race.dateTime` on every race of every non-BYE match (Race-Edit page → Date & Time field). Without dateTimes, the Schedule button stays disabled with the tooltip "Set Race date+time for all matches first".
2. Navigate to `/admin/matchdays/{id}` → "Post Matchday Schedule" button transitions from disabled span to enabled primary button → click → expect "Matchday Schedule posted." flash + button-label flip to "Re-Post Matchday Schedule".
3. Verify in the Discord announcement channel: a pure-PNG post (no Markdown body) lands in the announcement channel via the "CTC Announcements APP" webhook identity. PNG layout reuses the existing `matchday-schedule-render.html` (1920×1080, header "Match Day {N} Schedule" + N rows of timestamp + Team-A + VS + Team-B + records, BST/GMT auto-format).
4. Re-Post verification: edit any race's `dateTime` → return to matchday-detail → button label flips from "Re-Post Matchday Schedule" to "Update Matchday Schedule" (light-yellow stale-indicator). Click → expect the Discord message is PATCHed in place (same `messageId`, `(edited)` marker visible in Discord; verify `attachments_replaced_at` advances in `/admin/discord/posts`).

## Next Plan Readiness

- **Plan 98-07 prerequisites satisfied** — Pairings (98-05) + Schedule (98-06) both implemented, tested, and partially UAT-verified (Pairings live-post PASS 2026-05-25; Schedule live-post deferred to UAT-10).
- Wave-pause invariant per [[feedback-wave-pause]]: STOP here. Present results to operator for the wave-2 checkpoint before starting Plan 98-07.

---
*Phase: 98-polish-e2e-docs-close*
*Plan: 06*
*Completed: 2026-05-25*
