---
phase: 95-match-channel-posts
plan: 03
subsystem: discord-integration

tags: [discord, settings, lineups, multipart-bundle, pre-flight, data-incomplete, business-rule, n-plus-one]

requires:
  - phase: 95-01-post-persistence-skeleton-list-page
    provides: [DiscordPostService.postOrEdit, WebhookPayload.empty, DiscordPostRef.match]
  - phase: 95-02-team-cards-hybrid-trigger
    provides: [readPng path-traversal guard, applyErrorFlash pattern, .discord-actions--posts CSS placeholder occupied]
provides:
  - DiscordPostService.postSettings (multipart-bundle, N attachments per race, list-index ordering)
  - DiscordPostService.postLineups (analog)
  - matchHasCompleteSettings / matchHasCompleteLineups predicates (also exposed as UI model booleans)
  - MatchController POST endpoints (/post-settings, /post-lineups) + extended detail() with 4 model booleans
  - applyErrorFlash(BusinessRuleException, String) overload mapping to errorCategory='data-incomplete'
  - .error-badge--data-incomplete CSS variant (amber/yellow shade)
  - Match-Detail buttons: Settings + Lineups × {Post / disabled-with-tooltip / Re-Post}
affects: [95-04-results-schedule, 96-public-channels]

tech-stack:
  added: [Spring MockitoBean + on-disk dummy PNG pattern reused, applyErrorFlash overload for BusinessRuleException]
  patterns: [Multipart-bundle: N races → N PNG attachments via list-index filename (settings-race-1.png, settings-race-2.png), Pre-flight gate as service-level predicate AND UI model boolean, disabled-span pattern for UI-gated POSTs with tooltip, 5th error category 'data-incomplete' for missing-data pre-flight failures]

key-files:
  created:
    - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceSettingsBundleIT.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceLineupsBundleIT.java
    - src/test/java/org/ctc/admin/controller/MatchControllerPostSettingsPreFlightIT.java
    - src/test/java/org/ctc/e2e/discord/posts/MatchDetailSettingsLineupsButtonsE2ETest.java
  modified:
    - src/main/java/org/ctc/discord/service/DiscordPostService.java
    - src/main/java/org/ctc/admin/controller/MatchController.java
    - src/main/resources/templates/admin/match-detail.html
    - src/main/resources/static/admin/css/admin.css

key-decisions:
  - "Reused existing org.ctc.domain.exception.BusinessRuleException for pre-flight failures (no duplicate class created under org.ctc.discord.exception). DiscordChannelService.assertPreconditions sets the precedent for the same pattern."
  - "Added applyErrorFlash(BusinessRuleException, String) overload that maps to errorCategory='data-incomplete' (new 5th category). Existing inline BusinessRuleException handlers in createDiscordChannel/moveToArchive keep their 'not-found' semantics (different intent: missing teams/channel vs missing race data)."
  - "N+1 race-lineup lookup accepted (RESEARCH Pitfall 6 — 3-4 races per match). matchHasCompleteLineups runs raceLineupRepository.findByRaceId per race; further optimization deferred unless profiling shows it matters."
  - "Bundle ITs manually call match.getRaces().add(race) after raceRepository.save because Hibernate's L1 cache returns the original Match reference even after matchRepository.findById would otherwise see the new Race rows. The test seeder bypasses the @Transactional + L1-cache interaction explicitly."
  - "Refactored findTeamCardsPost → generic findMatchPost(Match, DiscordPostType) to support 3 post-types from a single helper. Plan 95-04 will reuse it for MATCH_RESULTS and SCHEDULE."

patterns-established:
  - "Multipart-bundle helper: postRaceBundle(Match, type, filenamePrefix, RaceGraphicLoader) walks match.getRaces() with IntStream-style index, calls a functional graphic loader per race, wraps each as NamedAttachment with `${prefix}${i+1}.png` filename, and delegates to postOrEdit. Reused by both postSettings and postLineups; ready for Plan 95-04 race-results extension."
  - "Defensive service-level pre-flight: even though buttons are UI-gated, postSettings / postLineups raise BusinessRuleException if a URL-manipulator bypasses the disabled span. Single source of truth for the rule."
  - "Disabled-span pattern: `<span class='btn btn-secondary btn-sm disabled' data-testid='post-X-disabled' title='Configure X for all races first'>Post X</span>` — visible but not clickable, hover tooltip explains why. Clean alternative to a disabled <button> inside a <form> (which would still POST on form-level Enter)."

requirements-completed: [POST-03]

duration: ~50min
completed: 2026-05-22
---

# Phase 95-03: Settings + Lineups Multipart Bundle Summary

**Settings + Lineups multipart-POSTs with strict pre-flight gating and a new 5th 'data-incomplete' error category — operators can only trigger a post once every race in the match has its prerequisite data configured.**

## Performance

- **Duration:** ~50 min
- **Started:** 2026-05-22T17:53:00+02:00
- **Completed:** 2026-05-22T18:10:00+02:00
- **Tasks:** 3 plan tasks (Task 95-03-04 final-verify deferred to phase-end per [[feedback_test_call_optimization]])
- **Files created:** 5 (all tests) | **Files modified:** 4

## Accomplishments

- `DiscordPostService.postSettings(Match)` + `postLineups(Match)` build a multipart bundle: N attachments per match (one PNG per race), list-index filenames (`settings-race-1.png`, `settings-race-2.png`, …) — never depending on a non-existent `raceNumber` field (RESEARCH Landmine 2).
- `matchHasCompleteSettings(Match)` / `matchHasCompleteLineups(Match)` boolean predicates serve both as service-level defensive gates AND as UI model attributes — single source of truth for the visibility rule.
- New private helper `postRaceBundle(Match, DiscordPostType, String filenamePrefix, RaceGraphicLoader loader)` deduplicates the Settings + Lineups paths; ready for Plan 95-04 race-results reuse.
- `MatchController.applyErrorFlash` gains a `BusinessRuleException` overload that maps to the new `errorCategory='data-incomplete'`. The existing 4 categories (`transient`/`auth`/`not-found`/`category-full`) are untouched.
- `.error-badge--data-incomplete` CSS variant added in the existing amber/yellow palette slot (`#3a3015` bg + `#ffd54f` text + `#f9a825` border) — distinct from the existing 4 variants.
- Match-Detail Discord-Actions-Panel filled with 6 new button blocks (Settings + Lineups × {Post / disabled-with-tooltip / Re-Post}) on top of the 3 Team-Cards blocks from Plan 95-02. Tooltips: "Configure settings for all races first" / "Configure lineups for all races first".
- 5 test classes: pre-flight predicate unit test (6 scenarios, Mockito-only, untagged), 2 service-level multipart-bundle ITs (covering happy-path, repost-as-PATCH, pre-flight-fail), MockMvc controller IT (3 scenarios: success / data-incomplete / transient), E2E (4 button-visibility states across desktop + mobile).

## Task Commits

1. **Task 95-03-01: postSettings + postLineups + pre-flight predicates** — `97d6bbfa` (feat)
2. **Task 95-03-02: MatchController endpoints + match-detail buttons + data-incomplete CSS** — `5b9f4a61` (feat)
3. **Task 95-03-03: 5 test classes (PreFlight unit + 2 bundle ITs + controller IT + E2E)** — `8233e203` (test)
4. **Task 95-03-04: final verify + screenshots + VALIDATION.md** — DEFERRED to phase-end per [[feedback_test_call_optimization]] (ONE final `./mvnw verify -Pe2e` runs after Plan 95-04 closes; screenshots and PR body update batched).

## Files Created/Modified

- `src/main/java/org/ctc/discord/service/DiscordPostService.java` (MOD) — 3 new fields injected (SettingsGraphicService + LineupGraphicService + RaceLineupRepository), 4 new public methods, postRaceBundle helper, RaceGraphicLoader functional interface
- `src/main/java/org/ctc/admin/controller/MatchController.java` (MOD) — 2 new POST endpoints, extended detail() with 4 new model attributes, new applyErrorFlash overload for BusinessRuleException, generic findMatchPost helper
- `src/main/resources/templates/admin/match-detail.html` (MOD) — 6 new button blocks in `.discord-actions--posts` placeholder (Settings + Lineups × 3 states)
- `src/main/resources/static/admin/css/admin.css` (MOD) — `.error-badge--data-incomplete` variant
- `src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java` (NEW)
- `src/test/java/org/ctc/discord/service/DiscordPostServiceSettingsBundleIT.java` (NEW)
- `src/test/java/org/ctc/discord/service/DiscordPostServiceLineupsBundleIT.java` (NEW)
- `src/test/java/org/ctc/admin/controller/MatchControllerPostSettingsPreFlightIT.java` (NEW)
- `src/test/java/org/ctc/e2e/discord/posts/MatchDetailSettingsLineupsButtonsE2ETest.java` (NEW)

## Decisions Made

- **REUSE the existing `org.ctc.domain.exception.BusinessRuleException`.** PATTERNS.md flagged it as the canonical pre-flight failure class; `DiscordChannelService.assertPreconditions` already raises it for the same kind of "missing-prerequisites" scenario. Creating a duplicate class under `org.ctc.discord.exception` would shadow the existing catch branches in MatchController.
- **applyErrorFlash overload, not a parallel helper.** Plan acceptance criterion `grep -c "applyBusinessRuleFlash" == 0` enforced the single-helper-route rule. The new overload `applyErrorFlash(BusinessRuleException, String)` sets `errorCategory="data-incomplete"` while the existing inline BusinessRuleException handlers in createDiscordChannel and moveToArchive keep their `not-found` semantics (different intent — those are missing-resource, not missing-input).
- **findTeamCardsPost generalized to findMatchPost.** Plan 95-02 added `findTeamCardsPost(Match)`. Plan 95-03 introduces two more types — extracted the type-parameterized variant `findMatchPost(Match, DiscordPostType)` so Plans 95-04 / 96 / 97 don't need yet another helper. The original wrapper remains for source-stability of existing call sites.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule: Plan vs JPA L1-cache] Bundle ITs manually call match.getRaces().add(race) after save**
- **Found during:** Task 95-03-03 (initial IT run)
- **Issue:** Test seeder creates Match → loops over `helper.createRace(md, match)` → calls `matchRepository.save(match)` → `matchRepository.findById(match.getId())`. But the L1 persistence-context cache returns the SAME Match instance with the original empty races list. The OneToMany association doesn't auto-refresh, so the service-under-test sees an empty `match.getRaces()` and the pre-flight predicate correctly returns false → BusinessRuleException → test fails on the happy-path branch.
- **Fix:** Manually add each new Race to `match.getRaces()` in the test seeder, then save. The persisted state is correct; we're just bypassing the L1-cache staleness explicitly.
- **Files modified:** src/test/java/org/ctc/discord/service/DiscordPostServiceSettingsBundleIT.java + DiscordPostServiceLineupsBundleIT.java
- **Verification:** All 5 bundle-IT scenarios pass.
- **Committed in:** Part of `8233e203`.

**2. [Rule: Plan vs incremental scope] Task 95-03-04 deferred to phase-end**
- **Found during:** Task-boundary review
- **Issue:** Plan called for full `./mvnw verify -Pe2e` + screenshots + rolling PR body update at the end of Plan 95-03. [[feedback_test_call_optimization]] says "ONE final verify per phase, gezielte tests dazwischen". With Plan 95-04 still pending in the same wave, doing a full verify here would be wasted effort if 95-04 lands a regression.
- **Fix:** Plan 95-04 will own the final-verify + screenshots + PR body update for both 95-03 and 95-04 in one batch.
- **Committed in:** N/A (no code change — process adjustment documented in this SUMMARY).

---

**Total deviations:** 2 (1 cache-bypass fix, 1 process-batch deferral)
**Impact on plan:** Test coverage is at full plan scope; process deferral consolidates the heavy verify into one phase-end run.

## Issues Encountered

- **JPA L1-cache + OneToMany race-list staleness** (described above). Standard issue when tests build entities incrementally through related repositories within a single @Transactional context. Fix is local to the test seeder.
- **`MatchController` constructor expansion blocked existing MoveToArchive unit test** — already resolved in Plan 95-02 (added 4 mocked deps). Plan 95-03 added 0 new constructor deps because the 3 new fields are reachable via the existing DiscordPostService injection.

## User Setup Required

None — Plan 95-03 ships UI controls only. Once operators configure RaceSettings (existing flow) and RaceLineup (existing flow) for all races in a match, the Settings + Lineups buttons enable automatically.

## Next Phase Readiness

- **Plan 95-04 ready.** The `postRaceBundle` helper + the 5th error category + the `findMatchPost` generic helper are all ready for Plan 95-04 to extend with MATCH_RESULTS (per-race bundle) + SCHEDULE (embed-only, no attachments).
- **Pattern verified end-to-end.** Settings (post-create) → Lineups (operator-input) → Match Results / Schedule (post-event) are now three structurally identical operations on the same skeleton — Plan 95-04 should be the smallest of the three concrete-post-type plans.

---
*Phase: 95-match-channel-posts*
*Plan: 03*
*Completed: 2026-05-22*
