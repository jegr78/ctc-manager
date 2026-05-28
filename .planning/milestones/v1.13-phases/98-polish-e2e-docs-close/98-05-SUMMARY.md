---
phase: 98-polish-e2e-docs-close
plan: 05
subsystem: discord
tags: [discord, matchday-pairings, announcement-channel, hybrid-multipart, post-09, flyway-v15, mockito, wiremock-it, playwright-e2e]

requires:
  - phase: 95-match-channel-posts
    provides: postOrEdit pattern + DiscordPost tracking entity + WebhookPayload + NamedAttachment
  - phase: 97-matchday-level-posts
    provides: MatchdayResults + PowerRankings post-template; canPostMatchdayResults pre-flight idiom
provides:
  - V15 schema: matchdays.pick_deadline + matchdays.scheduled_weekend + discord_global_config.matchday_pairings_template (all nullable)
  - DiscordPostService.canPostMatchdayPairings(Matchday, DiscordGlobalConfig) — 4 reject branches per D-98-PRE-1
  - DiscordPostService.postMatchdayPairings(Matchday) — hybrid Markdown+PNG multipart to announcement webhook
  - DiscordPostService.buildMatchdayPairingsMarkdown — operator-template-or-default with {{matchdayNumber}}, {{deadline}}, {{weekend}} placeholders
  - MatchdayPairingsGraphicService — Playwright PNG renderer (rating + team-bar + VS + team-bar + rating per match)
  - MatchdayPairingsForm + 3 controller endpoints (GET edit-pairings / POST save-pairings / POST post-matchday-pairings)
  - matchday-detail.html Discord Announcements card under matchdayAnnouncementActive (gated independently from matchdayDiscordActive)
  - discord-config.html operator-template textarea + DiscordConfigForm round-trip
  - Mockito-Unit pre-flight matrix (7 cases) + WireMock IT (6 cases) + Playwright E2E button-state matrix (5 cases)
affects: [98-06 (sibling Schedule button block in the same matchdayAnnouncementActive section), 98-07 (bundle verify + DOCS-02/03 wiring)]

tech-stack:
  added: []
  patterns:
    - "Hybrid Markdown+PNG multipart post on announcement webhook (D-98-PAIR-1 — distinct from pure-PNG match-channel posts)"
    - "Operator-template with simple String.replace placeholder substitution (no SpEL/Thymeleaf per threat T-98-05-TR-1)"
    - "Channel-Differentiation Discipline — matchdayAnnouncementActive vs matchdayDiscordActive booleans in populateMatchdayDiscordModel"

key-files:
  created:
    - src/main/resources/db/migration/V15__add_matchday_pairings_fields.sql
    - src/main/java/org/ctc/admin/service/MatchdayPairingsGraphicService.java
    - src/main/resources/templates/admin/matchday-pairings-render.html
    - src/main/java/org/ctc/admin/dto/MatchdayPairingsForm.java
    - src/main/resources/templates/admin/matchday-pairings-form.html
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsPreFlightTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsIT.java
    - src/test/java/org/ctc/e2e/discord/announcement/MatchdayDetailDiscordAnnouncementE2ETest.java
  modified:
    - src/main/java/org/ctc/domain/model/Matchday.java
    - src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java
    - src/main/java/org/ctc/discord/service/DiscordPostService.java
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/main/java/org/ctc/domain/service/MatchdayService.java
    - src/main/resources/templates/admin/matchday-detail.html
    - src/main/java/org/ctc/discord/dto/DiscordConfigForm.java
    - src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java
    - src/main/java/org/ctc/discord/web/DiscordConfigController.java
    - src/main/resources/templates/admin/discord-config.html
    - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
    - pom.xml

key-decisions:
  - "Separate MatchdayPairingsForm DTO + dedicated /admin/matchdays/{id}/edit-pairings page (planner Discretion Decision 1)"
  - "MatchdayService.savePairings(...) — thin transactional helper (whitelist expansion from 17 to 18 files; documented under Deviations)"
  - "DEFAULT_MATCHDAY_PAIRINGS_TEMPLATE uses third {{matchdayNumber}} placeholder mapped to matchday.getLabel() (e.g. \"Match Day 1\") so the operator-screenshot H1 \"Match Day {N} Pairings\" stays template-resolvable"
  - "Pairings PNG layout iteration capped at (i)-only skeleton — rating + team-bar + VS + team-bar + rating per match; iterations (ii)+(iii) merged into the single template since structurally identical to MatchdayScheduleGraphicService output minus the scheduled-time column"
  - "DiscordPostType.MATCHDAY_PAIRINGS already existed in the enum (pre-Plan-98-05) — no enum edit needed in this plan"

patterns-established:
  - "Announcement-channel button block (matchdayAnnouncementActive section) is structurally independent from the existing matchdayDiscordActive section — sibling Pairings + Schedule (98-06) buttons both live under matchdayAnnouncementActive"
  - "@MockitoBean MatchdayPairingsGraphicService in IT keeps Playwright out of the IT loop; stubbed PNG payload is 2048 bytes for the body-size >1024 assertion"
  - "Re-Post-PATCH path verified for hybrid Markdown+PNG (same messageId, attachments_replaced_at advances)"

requirements-completed:
  - POST-09

duration: ~30min
completed: 2026-05-25
---

# Plan 98-05 SUMMARY — MATCHDAY_PAIRINGS Announcement-Channel Hybrid Post

**Operator-driven Matchday Pairings post (POST-09): Markdown body + Pairings-PNG multipart attachment on the Announcement-Channel via `DiscordGlobalConfig.announcementWebhookUrl`. Pre-Flight predicates, template-override, stale-detection, and the 4-state sibling button cluster on Matchday-Detail.**

## Performance

- **Duration:** ~30 min (inline interactive execution, sequential commits)
- **Tasks:** 14 (13 auto + 1 visual checkpoint deferred to operator)
- **Files modified:** 21 (8 created + 13 modified)
- **Commits:** 14 atomic (plus 1 V15 table-name fix)

## Accomplishments

- New Flyway V15 migration with 3 nullable columns (matchdays.pick_deadline, matchdays.scheduled_weekend, discord_global_config.matchday_pairings_template)
- MatchdayPairingsGraphicService Playwright renderer + Thymeleaf template (`matchday-pairings-render.html`) mirroring the existing schedule layout with seed-rating columns
- DiscordPostService extended with canPostMatchdayPairings + postMatchdayPairings + buildMatchdayPairingsMarkdown (constructor + field + @SuppressFBWarnings justification all updated)
- MatchdayPairingsForm + dedicated `/admin/matchdays/{id}/edit-pairings` page wired via MatchdayService.savePairings
- New Discord Announcements card on matchday-detail.html under matchdayAnnouncementActive (independent of the existing matchdayDiscordActive section per Channel-Differentiation Discipline)
- discord-config.html textarea for operator-overridable Markdown template; null/blank falls back to the hardcoded default
- Targeted test green: 7 Mockito-Unit pre-flight cases + 6 Spring-Boot WireMock IT cases + 5 Playwright E2E button-state cases (all passing 2026-05-25; targeted -Dit.test runs ≤ 31 s each)

## Task Commits

1. **Task 1: V15 Flyway migration** — `0de0fa66` + `77f67725` (table-name correction)
2. **Task 2: Matchday pickDeadline + scheduledWeekend fields** — `100e24ba`
3. **Task 3: DiscordGlobalConfig.matchdayPairingsTemplate field** — `8ea7e347`
4. **Task 4: Mockito-Unit pre-flight matrix (RED)** — `2c6d877d`
5. **Task 5: MatchdayPairingsGraphicService + render template** — `74e73a45`
6. **Task 6: DiscordPostService canPost + post + buildMarkdown (GREEN)** — `e7408b98`
7. **Task 7: WireMock IT for hybrid-multipart flow** — `1c04eed3`
8. **Task 8: MatchdayPairingsForm + Controller endpoints + edit page** — `e58edfe5`
9. **Task 9: matchday-detail.html Discord Announcements card** — `72c2e0cc`
10. **Task 10: discord-config wiring (template textarea)** — `cd938be3`
11. **Task 11: Playwright E2E button-state matrix** — `f0df6fa0`
12. **Task 12: pom.xml JaCoCo exclude** — `639ef038`
13. **Task 13: Visual checkpoint** — deferred to operator (auto-UAT 98)
14. **Task 14: SUMMARY** — this commit

## Decisions Made

1. **V15 table-name correction was committed as a fix-commit** (not amend) per CLAUDE.md "Prefer to create a new commit rather than amending". Plan-text referenced `ALTER TABLE matchday` (singular), actual schema is `matchdays` (plural).
2. **Separate `/edit-pairings` page over inline-edit on matchday-detail.html.** Avoids race-conditions with the GET-refresh and gives the Playwright button-state matrix a deterministic test surface.
3. **`matchdayAnnouncementActive` is a NEW model attribute distinct from `matchdayDiscordActive`** per Channel-Differentiation Discipline ([[feedback-discord-channel-types]]) — the announcement webhook and the race-results forum webhook are independent surfaces.
4. **`MatchdayService.savePairings(id, deadline, weekend)` thin helper** instead of inline-save in the controller — keeps the controller skinny per CLAUDE.md "Keep Controllers Thin". One file beyond the original whitelist (see Deviations).
5. **PNG layout iteration collapsed to a single template** because the Matchday-Pairings layout is structurally the schedule layout minus the scheduled-time column plus left/right rating columns; no separate skeleton/full/watermark iterations were necessary.

## Deviations from Plan

### Whitelist Expansion

**MatchdayService.java added to files-modified**
- **Found during:** Task 8 (controller wiring)
- **Reason:** D-98-FILES-1 whitelist had 17 files; `MatchdayService.savePairings(...)` keeps `MatchdayController` thin (CLAUDE.md "Keep Controllers Thin") instead of inline-saving via `matchdayRepository.findById` in the controller body.
- **Impact:** +5 LOC in MatchdayService.java. No scope creep.
- **Committed in:** `e58edfe5` (Task 8 commit)

### Auto-fixed Issues

**1. [Rule 1 - Schema] V15 table name corrected**
- **Found during:** Task 2 (Matchday entity append — verified table name)
- **Issue:** Plan instructed `ALTER TABLE matchday`; actual table is `matchdays` (V1/V3 + JPA `@Table(name = "matchdays")`).
- **Fix:** Updated V15 file to use the plural form.
- **Verification:** WireMock IT (Task 7) boots Spring context against H2 with V15 applied — 6/6 green.
- **Committed in:** `77f67725` (separate fix-commit on top of `0de0fa66`)

**2. [Rule 2 - Cross-file impact] Two existing pre-flight tests updated for new constructor arg**
- **Found during:** Task 6 (after appending the new MatchdayPairingsGraphicService constructor parameter)
- **Issue:** `DiscordPostServicePreFlightTest` and `DiscordPostServiceRefBranchesTest` instantiate `DiscordPostService` directly via the explicit constructor; both broke with the new parameter.
- **Fix:** Added `mock(MatchdayPairingsGraphicService.class)` to both constructor call-sites.
- **Verification:** `./mvnw -q test-compile` green; `verify` matrix green.
- **Committed in:** `e7408b98` (Task 6 commit — bundled because the constructor change and the test-fix are causally inseparable)

---

**Total deviations:** 1 whitelist expansion + 2 auto-fixes
**Impact on plan:** No scope creep. The table-name fix and the cross-file test impact were structural necessities; the whitelist expansion preserves CLAUDE.md thin-controller principle.

## Issues Encountered

None beyond the V15 table-name correction described above.

## Test Results

- `DiscordPostServiceMatchdayPairingsPreFlightTest` — **7 tests, 0 failures** (1.116 s, Surefire)
- `DiscordPostServiceMatchdayPairingsIT` — **6 tests, 0 failures** (19.48 s, Failsafe)
- `MatchdayDetailDiscordAnnouncementE2ETest` — **5 tests, 0 failures** (17.82 s, Failsafe `-Pe2e`)
- SpotBugs `BugInstance size is 0` (gate preserved)
- JaCoCo `All coverage checks have been met` (gate preserved, MatchdayPairingsGraphicService excluded per CLAUDE.md)

**Full `./mvnw clean verify -Pe2e` deferred to Plan 98-07 per D-98-VERIFY-1.**

## User Setup Required

**Visual Checkpoint (Task 13) — operator action:** the planned visual checkpoint is a live-Discord post on the operator's test guild and is best automated via `/gsd-auto-uat 98` ([[feedback-auto-uat-reminder]]) for the static button-state matrix on `/admin/matchdays/{id}`. The live `Post Matchday Pairings` click + Discord-side screenshot belongs to UAT-09 (to be planned alongside Plan 98-07 bundle verify).

**Operator-template-edit:** populate `/admin/discord-config` → "Matchday Pairings Template" with the operator's preferred Markdown (or leave empty to use the built-in default).

**Pick-deadline + scheduled-weekend:** the operator sets these on `/admin/matchdays/{id}/edit-pairings` before clicking `Post Matchday Pairings`.

## Next Plan Readiness

- Plan 98-06 (MATCHDAY_SCHEDULE POST-10) — wave 2 dependency satisfied: the new `org.ctc.e2e.discord.announcement` package + `matchdayAnnouncementActive` model attribute + Discord Announcements card section all in place; 98-06's Schedule button block APPENDS as a sibling inside the same section.
- Wave-pause invariant per [[feedback-wave-pause]]: STOP here. Present results to operator for the wave-1 checkpoint before starting Plan 98-06.

---
*Phase: 98-polish-e2e-docs-close*
*Plan: 05*
*Completed: 2026-05-25*
