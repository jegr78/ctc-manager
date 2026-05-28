---
phase: 96-provisional-graphic-forum-threads
plan: 01
subsystem: discord-integration

tags: [discord, provisional-scores, graphic-service, multipart-bundle, iterator-counter, playwright-resource-mitigation]

requires:
  - phase: 95-04-match-results-schedule
    provides: [DiscordPostService.postOrEdit MatchRef branch, DiscordPostType enum, applyErrorFlash(BusinessRuleException) pattern, .discord-actions--posts cluster, findMatchPost helper]
provides:
  - ProvisionalScoresGraphicService (extends AbstractGraphicService implements TemplateManageable)
  - PlaywrightScreenshotter @Component (Function<String, byte[]> production binding for the screenshotter seam)
  - provisional-scores-render.html template (header + 2 stacked team-blocks + 8-column per-driver tables + Overall footer-row per block)
  - DiscordPostService.matchHasProvisionalData(Match) predicate
  - DiscordPostService.postProvisionalScores(Match) — filtered-race multipart bundle with iterator-counter filename stability
  - MatchController POST /admin/matches/{id}/post-provisional + provisionalPost/matchHasProvisionalData model attrs
  - match-detail.html Provisional Post / Disabled / Re-Post triplet inside .discord-actions--posts cluster
  - app.seed.generate-team-cards property gate on TestDataService.generateTeamCards (resource-mitigation infra)
  - TeamCardService.generateCard PlaywrightException retry-wrapper for Page.captureScreenshot protocol errors
affects: [96-02-forum-config-thread-linker, 96-03-sealed-switch-forum-posts]

tech-stack:
  added: [Spring `Function<String, byte[]>` bean autowire for graphic-service test seam, conditional team-card generation in tests via `@Value` on TestDataService]
  patterns: [
    Locked 2-arg `generateProvisional(Race, int)` signature with iterator-counter as single source of truth for raceLabel + filename stability,
    Test seam via injectable Function<String, byte[]> screenshotter (mock in unit tests; PlaywrightScreenshotter @Component in production),
    Property-gated test-time resource shedding (`app.seed.generate-team-cards=false` in surefire/failsafe systemPropertyVariables) — skip the expensive Chromium-bound step without changing test logic,
    Targeted PlaywrightException retry on the specific "Protocol error (Page.captureScreenshot)" message — safety-net for residual race-condition tail
  ]

key-files:
  created:
    - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
    - src/main/java/org/ctc/admin/service/PlaywrightScreenshotter.java
    - src/main/resources/templates/admin/provisional-scores-render.html
    - src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceProvisionalScoresIT.java
    - src/test/java/org/ctc/admin/controller/MatchControllerProvisionalPostIT.java
    - src/test/java/org/ctc/e2e/discord/posts/MatchDetailProvisionalButtonsE2ETest.java
  modified:
    - src/main/java/org/ctc/discord/service/DiscordPostService.java
    - src/main/java/org/ctc/admin/controller/MatchController.java
    - src/main/resources/templates/admin/match-detail.html
    - src/main/java/org/ctc/admin/TestDataService.java
    - src/main/java/org/ctc/admin/service/TeamCardService.java
    - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
    - pom.xml

key-decisions:
  - "Signature lock: `generateProvisional(Race race, int raceIndex)` is the ONLY public render entry — the 1-arg `generateProvisional(Race)` form is forbidden. This prevents silent fallback to `race.getRaceNumber()` or other race-derived label sources. The iterator-counter from the calling loop is the single source of truth for both `raceLabel` (Context variable) and filename (`provisional-race-N.png`). Test 6 in ProvisionalScoresGraphicServiceTest and Test 12 (filename stability) in DiscordPostServiceProvisionalScoresIT pin this contract."
  - "D-96-GRX-1c assertion-pin: PROVISIONAL_SCORES posts target the match-channel webhook only — never a forum-thread. DiscordPostServiceProvisionalScoresIT.noThreadIdEverAppended verifies no `?thread_id=` query param is ever appended for this post-type. Plan 96-03's threadId plumbing intentionally does NOT route through this code path."
  - "Resource-mitigation strategy chosen over architectural pool refactor: rather than redesign TeamCardService to share a Playwright/Browser instance (Option C in the analysis, scoped to a future plan), the fix shed the expensive step at the seed-time origin via a property gate AND added a targeted retry for the residual tail. This was a single-milestone-polish-window fix per the in-milestone-polish discipline — not a deferral. Total verify-time impact: no change (~7:27 min); flake rate at this layer: 0% across the verification runs."
  - "Visual layout iteration deferred to operator wave-pause review: the reference screenshot in `.screenshots/96-01/provisional-reference.png` was not dropped pre-execute. Per the `<pre_flight>` contract, Task 96-01-02 produced a baseline layout matching the 96-PATTERNS.md specification (8-column per-driver table + Overall footer-row per block, 1920×1080 viewport, Conthrax font, silver-gradient header). Operator iterates via playwright-cli during the post-plan wave-pause; pixel-fidelity tightening is in-scope for the same plan, not a Phase-98 polish deferral."

patterns-established:
  - "Test-time resource shedding via property gates: when a Spring-boot data-seed step incurs expensive side-effects (here: Chromium spawns for PNG rendering) that aren't needed for the vast majority of tests, gate the expensive sub-step with `@Value(\"${app.foo:true}\")` and inject `false` via surefire/failsafe systemPropertyVariables. Preserves implicit data dependencies that other tests rely on while removing the resource cost. Generalizable to any seed-step that boots a heavy external dependency."
  - "Injectable Function<String, byte[]> seam for graphic-service unit tests: AbstractGraphicService's renderScreenshot writes to a temp file then reads bytes back — an integrated pipeline that's hard to mock. The seam pattern (constructor-injected Function) lets unit tests assert Context-variable wiring without booting a real Chromium, while a single @Component (PlaywrightScreenshotter) provides the production binding. Generalizable to future GraphicService subclasses where the existing renderScreenshot pipeline is too coupled for fine-grained behavior verification."
  - "Iterator-counter contract for re-post filename stability: when a post-type bundles N attachments derived from a filtered collection (here: races with non-empty results), the filename MUST be derived from the loop's iterator counter, NEVER from the entity's natural number (race.getRaceNumber()) or random-derived value. This guarantees that re-posting the same data without changes produces identical filenames — required for atomic attachment-replacement via Discord's multipart PATCH. The contract is pinned at three levels: the service signature (2-arg only), a unit-test assertion via ArgumentCaptor, and an IT-level multipart-body verification."

requirements-completed: [GRAFX-01]

duration: ~90min (incl. ~25min Chromium-flake root-cause + Fix A/B + clean-verify cycle)
completed: 2026-05-23

## Validation Results

```
./mvnw clean verify -Pe2e
  Surefire:  1580 tests, 0 failures, 0 errors, 1 skipped
  Failsafe:   391 tests, 0 failures, 0 errors, 2 skipped
  E2E:         78 tests, 0 failures, 0 errors, 0 skipped
  Total:     2049 tests green
  JaCoCo:    All coverage checks have been met
  SpotBugs:  0 BugInstances, 0 errors
  Time:      07:27 min
```

Per Plan 96-01 test class:
- `ProvisionalScoresGraphicServiceTest`: 7/7 green (signature lock + 6 Context-variable behaviors)
- `DiscordPostServiceProvisionalScoresIT`: 8/8 green (matchHasProvisionalData truthtable + multipart POST/PATCH + BusinessRuleException + no-thread-id assertion + filename-stability regression)
- `MatchControllerProvisionalPostIT`: 2/2 green (happy + pre-flight branches)
- `MatchDetailProvisionalButtonsE2ETest`: 4/4 green (Desktop enabled + Disabled + Re-Post + Mobile)

## Authentication Gates

None — PROVISIONAL_SCORES post-type uses the existing match-channel webhook URL configured per-match; no new credentials path.

## Deviations from Plan

**[Rule 4 — Architectural Decision] Chromium-resource-exhaustion in long verify runs** — Found during the post-Task-96-01-03 wave-close verify (~5:13 min run, 2 sitegen test errors with `Protocol error (Page.captureScreenshot)`). Root cause was DevDataSeeder.run() invoking `TestDataService.generateTeamCards()` at every @SpringBootTest context bootstrap (~46 fresh Chromium spawns × ~131 test classes ≈ ~800 spawns per verify run). Operator approved Fix A+B in-milestone-polish: (A) gated `generateTeamCards()` on `app.seed.generate-team-cards` property, set to `false` in surefire/failsafe systemPropertyVariables; (B) added targeted `Protocol error (Page.captureScreenshot)` retry in `TeamCardService.generateCard`. After fix: 0% flake rate across two verification runs, total wall-time stable at ~7:27 min, JaCoCo + SpotBugs clean.

**[Rule 1 — Bug] DiscordPostServicePreFlightTest constructor signature drift** — Found during the test-compile step after injecting `ProvisionalScoresGraphicService` into `DiscordPostService`'s constructor. The pre-existing PreFlightTest directly instantiated DiscordPostService with the old arg-list. Fixed by adding `mock(ProvisionalScoresGraphicService.class)` to the inline constructor call.

**[Rule 1 — Bug] MatchDetailProvisionalButtonsE2ETest synthetic message_id column overflow** — Found during the post-Fix-A+B verify (~6:49 min). `post.setMessageId("msg-eps-existing-" + match.getId())` produced 53 chars; `discord_post.message_id` is VARCHAR(32). Fixed by using the first 8 chars of the match UUID suffix.

**[Rule 1 — Bug] Stale JDT-bytecode false-positive on `BackupSchemaExclusionIT`** — Detected during the post-fix verify (~7:34 min). Pre-existing AssertJ usage in BackupSchemaExclusionIT was flagged by Eclipse JDT as "Unresolved compilation problem" at runtime despite javac compiling clean. Per the project's `[Clean Maven Build = Wahrheit]` discipline, ran `./mvnw clean test-compile` then re-ran `./mvnw clean verify -Pe2e`. Memory `feedback_clean_build_only` updated with a tightened trigger rule (every `./mvnw verify` MUST be preceded by `clean`).

**Total deviations:** 4 — 1 architectural (Rule 4, operator-approved), 3 auto-fixed (Rule 1).
**Impact:** None on the GRAFX-01 acceptance surface — all deviations were resolved within Plan 96-01 scope.

## Next Phase Readiness

Plan 96-02 starts on the same `gsd/v1.13-discord-integration` milestone branch. The new `ProvisionalScoresGraphicService` and `PlaywrightScreenshotter` beans are unaffected by Plan 96-02's Flyway V13 + Forum-Webhook plumbing. Wave-pause: operator should review `.screenshots/96-01/` PNGs after manually triggering the Post Provisional button on a dev-mode match — pixel-fidelity iteration vs. the Google-Sheets reference can still happen in-plan if the baseline layout needs tuning.
---
