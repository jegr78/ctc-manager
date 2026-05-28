---
phase: 97-matchday-level-posts
plan: 02
subsystem: discord-integration
tags: [discord, webhook, forum-thread, matchday-results, power-rankings, stale-detection]

requires:
  - phase: 96-discord-forum-thread-integration
    provides: race-results forum-thread + auto-unarchive-before-post shared path + 7-arg postOrEdit with thread_id query param
  - phase: 95-discord-post-buttons
    provides: .discord-actions--posts cluster pattern + applyErrorFlash 5-permit category mapping
  - phase: 97-matchday-level-posts/01
    provides: MatchPreviewPreFlightResult DTO record + DiscordPostService.resolveAnnouncementChannelId helper (reused)
provides:
  - Two independent "Post Match Day Results" / "Post Power Rankings" buttons on /admin/matchdays/{id} (Discord Actions card)
  - MATCHDAY_OVERVIEW + POWER_RANKINGS discord_post rows scoped by (announcementChannelId, matchdayId)
  - populateMatchdayDiscordModel helper + 9 model attributes on MatchdayController.detail
  - Per-button stale-detection (matchdayResultsStale via race-results timestamps; powerRankingsStale via SeasonTeam.updatedAt)
affects: [Plan 97-03 (POST-08 Standings reuses the announcement-webhook-derived channelId lookup pattern + same MatchPreviewPreFlightResult shape)]

tech-stack:
  added: []
  patterns:
    - "Sibling-button pre-flight matrix on a single card: two PreFlightResults + two existing-post lookups + two stale-detection flags → 9 model attributes, one shared th:if outer gate (matchdayDiscordActive)"
    - "Tightly-scoped IOException-to-Transient mapping: catch IOException only around the actual generate*() call so DiscordApiException subtypes from the webhook POST propagate with their original category"

key-files:
  created:
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayResultsIT.java
    - src/test/java/org/ctc/discord/service/DiscordPostServicePowerRankingsIT.java
    - src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java
    - src/test/java/org/ctc/e2e/discord/matchday/MatchdayDetailDiscordActionsE2ETest.java
  modified:
    - src/main/java/org/ctc/discord/service/DiscordPostService.java
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/main/resources/templates/admin/matchday-detail.html
    - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Reused Plan 97-01's MatchPreviewPreFlightResult record as the shared pre-flight DTO across both new canPost methods. Name kept (no rename to a generic DiscordPostPreFlightResult) to avoid touching Plan 97-01 callsites and producing zero blast-radius — the record fields are already generic (boolean, @Nullable String)."
  - "Discovered + fixed a latent bug while writing the 4xx-permit ITs: DiscordApiException extends IOException, so a try-catch wrapping `generateResults(...) + postOrEdit(...)` with `catch (IOException e) → DiscordTransientException` silently re-categorises every 4xx response from the webhook as TRANSIENT. Solution: scope the IOException catch tightly to just the graphic call (which can throw IOException) and re-throw DiscordApiException unchanged. The existing `postRaceResultToForumThread` (Phase 96) has the same latent shape; not fixed in this plan to keep blast-radius minimal — flagged for a future hardening sweep."
  - "Stale-detection for power rankings uses MAX(SeasonTeam.updatedAt) for the season (operator-curated rating), NOT match results. Loaded via SeasonTeamRepository.findBySeasonId — no new repository method needed."
  - "Discord Actions card lives on matchday-detail.html as a detail-section (not a separate .card) — matches the existing graphic-download cluster pattern and reuses .discord-actions/.discord-actions--posts verbatim. Zero new CSS."

patterns-established:
  - "Sibling buttons sharing a common pre-flight matrix: outer th:if gates the cluster on a single combined predicate (matchdayDiscordActive = threadLinked AND webhookConfigured), inner buttons branch on per-action canPost + existing-post-row + stale flags. The disabled-tooltip strings are computed server-side as the first failing predicate."
  - "DiscordApiException-aware service try-catch: when a service method wraps an IOException-throwing graphic generator AND a webhook call, the catch MUST scope to the IO call only OR explicitly rethrow DiscordApiException before catching the generic IOException. Failure mode: 4xx HTTP errors get re-categorised as TRANSIENT and operators receive misleading flash messages."

requirements-completed: [POST-07a, POST-07b]

duration: 142min
completed: 2026-05-24
---

# Phase 97 Plan 02: Matchday Results + Power Rankings Buttons Summary

**Two independent matchday-level forum posts ship POST-07a + POST-07b: Match Day Results gates on all matches final + thread + webhook; Power Rankings stays looser (thread + webhook only) because the rating is operator-curated.**

## Performance

- **Duration:** ~142 min (interactive mode, 3 sequential tasks, 1 cycle of TRANSIENT-bug discovery + fix)
- **Tasks:** 3
- **Files modified:** 10 (4 production, 5 test, 1 planning artifact)
- **Tests added:** 25 (10 service ITs + 9 controller ITs + 6 E2E)
- **Coverage:** verified via full clean verify -Pe2e at plan-end

## Accomplishments

- `DiscordPostService` extended with `canPostMatchdayResults` + `postMatchdayResults` (gated on `allMatchesFinal`) and `canPostPowerRankings` + `postPowerRankings` (no finality gate per D-97-MD-1). Both write to the same race-results forum thread with `?thread_id=` and rely on the shared auto-unarchive path from Phase 96.
- `MatchdayController.detail()` enriches the model with 9 attributes via the new `populateMatchdayDiscordModel` helper (matchdayDiscordActive, two pre-flight booleans + reasons, two existing-post lookups, two stale flags). Two new POST endpoints mirror MatchController.postSchedule's shape with the same 5-permit error-flash mapping.
- `matchday-detail.html` gets a new "Discord Actions" detail-section with two button triplets — gated by the outer `matchdayDiscordActive` predicate; inner buttons branch on per-action pre-flight + existing-post + stale flags. Zero new CSS (inherits the Phase 95 `.discord-actions--posts` cluster).
- `REQUIREMENTS.md` POST-07 entry split into POST-07a + POST-07b with the distinct pre-flight rules; traceability table row updated to match.
- Latent TRANSIENT-rewrap bug fixed in the two new service methods (DiscordApiException extends IOException — a broad `catch (IOException)` rewraps every 4xx from the webhook as TRANSIENT). Tightly-scoped catch now lets NOT_FOUND / AUTH / MISSING_PERMISSIONS / TRANSIENT all propagate with their true category.

## Task Commits

1. **Task 1: DiscordPostService methods + 2 service ITs** — `39183ff4` (feat)
2. **Task 2: MatchdayController endpoints + Discord Actions card + controller IT** — `1ee6c773` (feat)
3. **Task 3: REQUIREMENTS POST-07 split + E2E test** — pending (this commit)

## Files Created/Modified

### Modified production files
- `src/main/java/org/ctc/discord/service/DiscordPostService.java` — adds `MatchdayResultsGraphicService` + `PowerRankingsGraphicService` fields (constructor updated, SuppressFBWarnings justification extended); `canPostMatchdayResults` + `canPostPowerRankings` + `postMatchdayResults` + `postPowerRankings` + `allNonByeMatchesFinal` predicate + `slug` filename helper. The IO-vs-DiscordApiException catch ordering preserves original 4xx categories.
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — 4 new field injections, `populateMatchdayDiscordModel(Model, Matchday)` helper, `isMatchdayResultsStale` + `isPowerRankingsStale` private helpers, two new `@PostMapping` endpoints (`/post-matchday-results`, `/post-power-rankings`), `applyErrorFlash(DiscordApiException)` + `applyErrorFlash(BusinessRuleException)` helpers mirroring MatchController.
- `src/main/resources/templates/admin/matchday-detail.html` — new `detail-section` ("Discord Actions" card) with two button triplets per pre-flight matrix. No inline styles.

### New test files
- `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayResultsIT.java` — 7 ITs: happy path + re-post (PATCH) + pre-flight fail + 4 sealed exception permits. WireMock stubs the channel GET (auto-unarchive prerequisite) + the webhook POST/PATCH. `@MockitoBean MatchdayResultsGraphicService` mocks the Playwright-backed graphic.
- `src/test/java/org/ctc/discord/service/DiscordPostServicePowerRankingsIT.java` — 3 ITs: happy path + no-finality-gate (post succeeds even when matches not final) + teamIds-rating-order assertion via Mockito ArgumentCaptor.
- `src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java` — 9 MockMvc ITs: 2 happy path + 4 exception permits + 1 BusinessRule + 2 GET enrichment scenarios. `@MockitoBean DiscordPostService` isolates the controller surface.
- `src/test/java/org/ctc/e2e/discord/matchday/MatchdayDetailDiscordActionsE2ETest.java` — 6 Playwright E2E: both-enabled / results-disabled-rankings-enabled / both re-post variants / no-thread-card-hidden / mobile-viewport.

### Modified test files
- `src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java` — added MatchdayResultsGraphicService + PowerRankingsGraphicService mocks to direct constructor invocation.
- `src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java` — same constructor signature update.

### Planning artifacts
- `.planning/REQUIREMENTS.md` — POST-07 entry split into POST-07a (Match Day Results, gates on allMatchesFinal + thread + webhook) + POST-07b (Power Rankings, gates ONLY on thread + webhook); traceability table row replaced with two rows.

## Verification

- `./mvnw -Dit.test=DiscordPostServiceMatchdayResultsIT,DiscordPostServicePowerRankingsIT verify` — 10/10 green.
- `./mvnw -Dit.test=MatchdayControllerPostEndpointsIT verify` — 9/9 green.
- `./mvnw -Dit.test=MatchdayDetailDiscordActionsE2ETest verify -Pe2e` — 6/6 green.
- `./mvnw clean verify -Pe2e` — BUILD SUCCESS at plan-end with SpotBugs `BugInstance size: 0`, JaCoCo line coverage above the 82% threshold, AdminWorkflowE2ETest unaffected.

## Pending UAT (Operator)

Per CONTEXT D-97-10, live-Discord smoke remains operator-driven:
1. Operator on `/admin/matchdays/{id}` (with all matches final + season race-results thread linked + webhook configured) clicks **Post Match Day Results** → verifies the matchday-results PNG appears in the race-results forum thread with auto-unarchive if previously archived.
2. Operator clicks **Post Power Rankings** → verifies the power-rankings PNG appears as a SEPARATE post in the same thread.
3. Operator re-posts both buttons → verifies the existing messages PATCH in place (Discord shows `(edited)` marker, no duplicate post).

Operator can run `/gsd-auto-uat 97-02` to automate the visual smoke per CLAUDE.md "gsd-auto-uat for UI-Heavy Verification" (button-state matrix only — live Discord posts still require manual action).

## Notes for Subsequent Plans

- **Plan 97-03 (POST-08)** can reuse the same sibling-button pre-flight matrix pattern for the season-form Post Standings flow. The `resolveAnnouncementChannelId(webhookUrl)` helper (Plan 97-01) generalises naturally — Plan 97-03 may add a sibling `resolveStandingsForumChannelId(webhookUrl)` or keep the single helper.
- **Latent bug in `postRaceResultToForumThread`**: the same `catch (IOException e) → DiscordTransientException` shape exists there (Phase 96, line 421 of DiscordPostService). Not fixed here to stay in scope. Adding a 4xx-category IT for that method in a future polish pass would catch it.
- **MatchPreviewPreFlightResult naming**: the record is now used for 3 distinct contexts (MatchPreview + MatchdayResults + PowerRankings). A future rename to `DiscordPostPreFlightResult` would be cleaner but is out of scope here — would touch Plan 97-01 callsites.
