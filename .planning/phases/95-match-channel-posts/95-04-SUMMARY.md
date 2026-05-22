---
phase: 95-match-channel-posts
plan: 04
subsystem: discord-integration

tags: [discord, match-results, schedule, embed, auto-edit-hook, stale-detection, pitfall-4-pinned]

requires:
  - phase: 95-01-post-persistence-skeleton-list-page
    provides: [postOrEdit dispatcher]
  - phase: 95-02-team-cards-hybrid-trigger
    provides: [readPng, applyErrorFlash pattern, .discord-actions--posts placeholder]
  - phase: 95-03-settings-lineups-multipart-bundle
    provides: [applyErrorFlash(BusinessRuleException) overload, findMatchPost generic helper, disabled-span pattern, data-incomplete CSS variant]
provides:
  - DiscordPostService.postMatchResults (single-PNG multipart-POST/PATCH)
  - DiscordPostService.postSchedule (JSON-only 4-field Embed payload)
  - DiscordPostService.autoEditScheduleIfNeeded (no-op when no SCHEDULE row exists)
  - DiscordPostService.matchCanRenderResults predicate
  - MatchService.updateDiscordFields auto-edit hook (3-field diff, exception-swallowing)
  - MatchController POST endpoints (/post-match-results, /post-schedule) + 5 new detail() model attributes
  - Match-Detail buttons: Match Results × {Post / disabled / Re-Post / Update} + Schedule × {Post / disabled / Re-Post}
  - max(RaceResult.updatedAt) stale-detection signal (post-Pitfall-4 correction)
affects: [96-public-channels, 97-cross-channel-rollups, 98-polish]

tech-stack:
  added: [Pre/post-diff hook in domain service]
  patterns: [Stale-detection via max(child.updatedAt) (NOT parent.updatedAt — empirically pinned), Pre/post-diff hook with exception-swallow, Auto-edit no-op when no canonical row exists, Embed payload with _TBD_ placeholders for null fields]

key-files:
  created:
    - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchResultsIT.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceScheduleIT.java
    - src/test/java/org/ctc/domain/service/MatchServiceScheduleEditHookIT.java
    - src/test/java/org/ctc/admin/controller/MatchDetailMatchResultsStaleIT.java
    - src/test/java/org/ctc/domain/service/MatchUpdatedAtNoopSaveIT.java
    - src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java
    - src/test/java/org/ctc/e2e/discord/posts/MatchDetailScheduleButtonE2ETest.java
  modified:
    - src/main/java/org/ctc/discord/service/DiscordPostService.java
    - src/main/java/org/ctc/domain/service/MatchService.java
    - src/main/java/org/ctc/admin/controller/MatchController.java
    - src/main/resources/templates/admin/match-detail.html

key-decisions:
  - "RESEARCH Pitfall 4 / Assumption A1 EMPIRICALLY FALSIFIED. matchRepository.save(match) DOES advance match.updatedAt even on a true no-op save — Spring Data JPA's @LastModifiedDate fires on every entity merge, not only on dirty fields. Switched the stale-detection signal from `match.updatedAt` to `max(race.results[*].updatedAt)` per the plan's contingency-first ordering. MatchUpdatedAtNoopSaveIT is GREEN with the FLIPPED assertion (isAfter, not isEqualTo) pinning the empirical truth."
  - "Embed.color field deferred to Phase 98 polish per RESEARCH Landmine 4. The current Embed record has no color field; Discord renders the embed with its native default color."
  - "Auto-edit hook landed in MatchService.updateDiscordFields (NOT a non-existent save(MatchForm)) per RESEARCH Landmine 3. Hook fires only when lobbyHost / raceDirector / streamer changes — Race.dateTime changes are out of scope for v1.13 (D-95-04a, deferred to v1.14)."
  - "Auto-edit hook swallows ALL exceptions. The match save already succeeded by the time the hook runs; rolling it back would be hostile UX. WARN-log records the failure category for operator diagnosis."

patterns-established:
  - "Stale-detection on parent-of-children entity: when @LastModifiedDate fires too eagerly on the parent (Pitfall 4), compare against max(child.updatedAt) where the child is the source-of-truth for the rendered state. Generalizable to any post-type whose content is derived from related entities."
  - "Pre/post-diff hook in domain service: capture pre-state strings BEFORE setter calls fire, save, then Objects.equals-diff to decide whether to invoke the dependent side-effect. Avoids redundant calls + handles the form-DTO null/blank semantics correctly."
  - "Empirical contingency-first: when a plan rests on an unverified assumption (A1 = no-op-save-doesn't-advance-updatedAt), write the assumption-pin test FIRST. If the assumption is falsified, the planned implementation switches to the documented fallback path within the same plan — no replan cycle needed."

requirements-completed: [POST-04, POST-05]

duration: ~55min
completed: 2026-05-22
---

# Phase 95-04: Match Results + Schedule Auto-Edit Summary

**POST-04 Match Results with race-result-based stale-detection + POST-05 Schedule JSON embed + automatic embed-patch hook on Match-form save — closes Phase 95 with all 5 POST types live behind a single hybrid-trigger UI.**

## Performance

- **Duration:** ~55 min
- **Started:** 2026-05-22T18:10:00+02:00
- **Completed:** 2026-05-22T18:39:00+02:00
- **Tasks:** 5 plan tasks (final-verify in this plan covers all 4 phase plans)
- **Files created:** 7 (all tests) | **Files modified:** 4

## Accomplishments

- `DiscordPostService.postMatchResults(Match)` packages a single `match-results.png` byte-array (directly from `MatchResultsGraphicService.generateMatchResults` — no `/uploads/` URL trip) into a multipart-POST/PATCH via the Plan-95-01 postOrEdit dispatcher. Pre-flight requires all races to have RaceResults (RESEARCH Landmine 5).
- `DiscordPostService.postSchedule(Match)` builds a JSON-only Embed payload with 4 fields: Date (formatted as `<t:UNIX:F> (<t:UNIX:R>)` via `DiscordTimestamps`), Lobby Host, Race Director, Streamer. Null/blank host fields render as `_TBD_`. No color field per RESEARCH Landmine 4.
- `DiscordPostService.autoEditScheduleIfNeeded(Match)` is a no-op when no SCHEDULE row exists or no race has a `dateTime`; otherwise it rebuilds the payload and routes through `postOrEdit` (which finds the existing row and dispatches to JSON-PATCH).
- `MatchService.updateDiscordFields` captures pre-state of `lobbyHost / raceDirector / streamer` BEFORE setters fire, applies the form, saves, then conditionally invokes `autoEditScheduleIfNeeded(saved)` only when at least one of the 3 schedule fields changed. The hook is wrapped in `catch (DiscordApiException) + catch (RuntimeException)` — both swallow with a WARN log so the match save commits regardless of Discord-side failure.
- `MatchController.detail()` GET pre-loads 5 new model attributes: `matchResultsPost`, `matchResultsStale` (via the NEW max-child-updatedAt signal), `schedulePost`, `matchCanRenderResults`, `scheduleVisible`.
- Match-Detail `.discord-actions--posts` placeholder gains 7 more button blocks (Match Results × {Post / disabled / Re-Post / Update} + Schedule × {Post / disabled / Re-Post}) on top of the 9 blocks already shipped by Plans 95-02 + 95-03.
- 7 test classes cover every new behavior:
  - `MatchUpdatedAtNoopSaveIT` (contingency-first pin, ran BEFORE writing other tests per plan-ordering rule)
  - `DiscordPostServiceMatchResultsIT` (4 scenarios incl. PATCH-on-existing + pre-flight fail)
  - `DiscordPostServiceScheduleIT` (5 scenarios incl. `_TBD_` rendering + `<t:UNIX:F>` Date format)
  - `MatchServiceScheduleEditHookIT` (4 Pattern-4 branches: changed-with-post / unchanged / changed-without-post / 5xx-swallow)
  - `MatchDetailMatchResultsStaleIT` (3 label states: Post / Re-Post / Update)
  - 2 E2E (Match Results + Schedule button visibility across desktop + mobile viewports)
- Full `./mvnw verify -Pe2e` ends with BUILD SUCCESS — 1566 Surefire tests + 448 Failsafe tests (incl. all 7 new ones + the 95-01/02/03 suites without regressions). JaCoCo line coverage 88.61% (above configured threshold). SpotBugs 0 BugInstance.

## Task Commits

1. **Task 95-04-01: postMatchResults + postSchedule + autoEditScheduleIfNeeded + matchCanRenderResults** — `87192f8f` (feat)
2. **Task 95-04-02: MatchService.updateDiscordFields auto-edit hook** — `957efe64` (feat)
3. **Task 95-04-03: MatchController endpoints + Match Results stale-label + Schedule visibility** — `9e90292d` (feat)
4. **Task 95-04-04a: MatchUpdatedAtNoopSaveIT (contingency-first) + Pitfall-4 stale-signal pivot** — `b1064fec` (fix)
5. **Task 95-04-04b: 6 remaining test classes** — `fcae5704` (test)
6. **Task 95-04-05: SUMMARY.md + VALIDATION.md + UAT-05 staging + ROADMAP update** — this commit

## Files Created/Modified

- `src/main/java/org/ctc/discord/service/DiscordPostService.java` (MOD) — 2 new fields injected, 4 new public methods, postRaceBundle helper (from Plan 95-03) reused
- `src/main/java/org/ctc/domain/service/MatchService.java` (MOD) — DiscordPostService injected, updateDiscordFields extended with pre/post-diff hook
- `src/main/java/org/ctc/admin/controller/MatchController.java` (MOD) — 2 new POST endpoints, detail() extended with 5 new model attributes, isStale + latestRaceResultUpdate helpers
- `src/main/resources/templates/admin/match-detail.html` (MOD) — 7 new button blocks
- 7 new test classes (5 IT + 2 E2E + 1 unit-style pin)

## Decisions Made

- **Pitfall-4 pivot to max(child.updatedAt).** The plan was contingency-first: write `MatchUpdatedAtNoopSaveIT` BEFORE the rest of the suite. The test FAILED in its original form (no-op save advances updatedAt by ~83ms), so the implementation switched to `max(race.results[*].updatedAt)` as the stale signal. The test now PINS the empirical truth with the assertion FLIPPED (isAfter instead of isEqualTo). The plan acceptance criterion `grep -q "Pitfall 4"` is satisfied; the implementation acceptance criterion `matchResultsStale` predicate is rewired.
- **Auto-edit hook lives in domain service, not controller.** Per CLAUDE.md "Keep Controllers Thin", the cross-cutting Discord side-effect on Match-form save belongs in `MatchService.updateDiscordFields`. The controller stays free of Discord knowledge for the save path.
- **Single uniform success message per endpoint.** "Match results posted." / "Schedule posted." — no separate "Match results updated." for the re-post branch (RESEARCH discretion). The match-detail page UI already differentiates the action via the button label (Post vs Re-Post vs Update), so the flash message just confirms success of whichever action ran.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule: RESEARCH assumption falsified at execution time] Stale-signal switched from match.updatedAt to max(RaceResult.updatedAt)**
- **Found during:** Task 95-04-04 (contingency-first MatchUpdatedAtNoopSaveIT run)
- **Issue:** Plan rested on RESEARCH Pitfall 4 / Assumption A1 — that `matchRepository.save(match)` does NOT advance `match.updatedAt` on a no-op save. Empirically, the timestamp jumped from `18:18:49.288` → `18:18:49.371` (~83ms increase) on a true no-op save.
- **Fix:** Pivoted the stale-detection signal to `max(race.results[*].updatedAt)` per the plan's documented fallback. `MatchController.isStale` and `latestRaceResultUpdate` helpers now compute the signal from race results. The pin test flipped to `assertThat(saved.getUpdatedAt()).isAfter(original)` documenting the empirical truth.
- **Files modified:** src/main/java/org/ctc/admin/controller/MatchController.java + src/test/java/org/ctc/domain/service/MatchUpdatedAtNoopSaveIT.java
- **Verification:** All 7 test classes pass; stale predicate works correctly when race-result is updated AFTER the post.
- **Committed in:** `b1064fec`

**2. [Rule: Plan vs JPA-cache] Test seeders manually add Race to match.getRaces() + nudge race-result updatedAt**
- **Found during:** Task 95-04-04 (initial bundle IT + stale IT runs)
- **Issue:** Same JPA L1-cache pattern from Plan 95-03 — match.getRaces() not visible to the service-under-test unless manually populated. Plus the stale tests needed the race-result updatedAt to be AFTER the post.updatedAt — required a Thread.sleep + re-save of the result.
- **Fix:** Tests do `match.getRaces().add(race)` after creating each Race, and bump `result.setPosition(2)` + save to push `result.updatedAt` past the post.updatedAt for the stale-positive scenarios.
- **Committed in:** Part of `fcae5704`.

---

**Total deviations:** 2 (1 architectural pivot per contingency-first plan, 1 test-seeder JPA-cache bypass)
**Impact on plan:** Contingency-first ordering caught the falsified assumption WITHIN the same plan — no replan cycle. Test coverage is at full plan scope (7 classes).

## Issues Encountered

- **RESEARCH Pitfall 4 empirically falsified** (described above). Pivot was clean because the plan had documented the fallback signal in advance.
- **JPA L1-cache staleness on test seeders** — same pattern as Plan 95-03; resolved with manual `match.getRaces().add(race)` calls.

## User Setup Required

None. All 5 POST types are operator-facing UI controls only. After this phase merges:
- `UAT-05` (live Discord smoke) is STAGED in `.planning/STATE.md` for operator action on the milestone branch (D-95-10) — 11-step procedure covers all 5 post types, the auto-post hook from Plan 95-02, the auto-edit-Schedule hook from Plan 95-04, and the stale-detection label flip on Match Results.

## Next Phase Readiness

- **Phase 95 closes.** All 4 plans shipped, no regressions, JaCoCo + SpotBugs gates green.
- **Phase 96 (Public Channels) ready.** The `DiscordPostRef` sealed-interface's MatchdayRef / RaceRef / SeasonRef permits are stubbed in Plan 95-01; Phase 96 wires them by extending postOrEdit's MatchRef-only check.
- **Phase 98 (Polish) backlog item:** `Embed.color` field deferred per RESEARCH Landmine 4. The current Embed record has no color field; the Phase-98 polish will add it + apply a brand color across all 5 post types.

---
*Phase: 95-match-channel-posts*
*Plan: 04*
*Completed: 2026-05-22*
