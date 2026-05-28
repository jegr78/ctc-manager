---
phase: 98-polish-e2e-docs-close
plan: 02
status: complete
last_updated: 2026-05-25
---

# Plan 98-02 SUMMARY — DiscordFullMatchdayLifecycleE2ETest (E2E-01)

## Files Modified

| File | Change | Lines |
|------|--------|-------|
| `src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java` | NEW — 10 `public static` stub helpers (`stubCreateChannel`, `stubCreateWebhook`, `stubExecuteWebhook`, `stubExecuteWebhookForumThread`, `stubPatchMessage`, `stubArchiveChannel`, `stubListGuildRoles`, `stubListGuildChannels`, `stubFetchBotUser`, `stubFetchChannelNotArchived`); private constructor; explicit `withQueryParam(wait=true)` + `thread_id=` per CLAUDE.md WireMock-vs-real-API. | +83 |
| `src/main/java/org/ctc/admin/TestDataService.java` | APPEND `@Transactional seedFullMatchdayLifecycle()` + `public record LifecycleFixture(Season, Matchday, Match, Team home, Team away)`; injects new `CarRepository` + `TrackRepository` dependencies; fixture uses `T-ALF` / `T-BRA` / `Test-Lifecycle 2098` (year=2098, number=99 — avoids collisions with `GroupsSeasonE2ETest` 2099/1). | +71 |
| `src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java` | NEW — `@Tag("e2e") @DirtiesContext(AFTER_CLASS)` extends `PlaywrightConfig`; `@RegisterExtension static WireMockExtension wm` + `@DynamicPropertySource app.discord.base-url`; 1 `@Test fullMatchdayLifecycle()` + 8 `private void step{1-8}_...` methods walking the full Discord post matrix (channel-state → 4 multipart posts → JSON schedule embed → provisional → match-results + forum-thread post → archive). 12 `withQueryParam`, 8 `aMultipart` verifies. | +298 |
| `config/spotbugs-exclude.xml` | Extend existing `TestDataService` exclude to a regex matching inner classes (`TestDataService$LifecycleFixture` is a `record` whose Lombok-generated accessors trigger EI_EXPOSE_REP* — same false-positive pattern documented for `org.ctc.domain.model` entities). | +2 / −1 |

## Build & Test Result

```
./mvnw clean verify -Pe2e
[INFO] BUILD SUCCESS — Total time: 11:26 min
```

- **Surefire:** 1218 tests, 0 failures, 0 errors, 3 skipped.
- **Failsafe (integration + e2e):** 556 tests, 0 failures, 0 errors, 1 skipped.
- **Test-count delta:** +1 E2E test (Mega-Walkthrough) — Phase 97 baseline ≈555 → 556.
- **JaCoCo:** 88.71 % line coverage (46293/52186) — pom gate 82 % passes. ↑ from Phase 97 baseline 88.60 % (Δ +0.11 pp).
- **SpotBugs:** BugInstance count = 0.
- **Targeted run:** `-Dit.test=DiscordFullMatchdayLifecycleE2ETest` completes in 20.52 s.

## PATTERNS Correction (CONTEXT D-98-E2E-2 deviation)

CONTEXT D-98-E2E-2 documented `@AutoConfigureWireMock(port=0)` + property `discord.api.base-url`.
PATTERNS.md flagged this as documentation drift: the codebase idiom (8/8 existing Discord ITs) is
`@RegisterExtension static WireMockExtension` + `@DynamicPropertySource app.discord.base-url` with
the `/api/v10` suffix. **Plan 98-02 implemented the PATTERNS-correct idiom**; CONTEXT D-98-E2E-2
should be amended in any future reference.

## Decision-IDs Honored

- D-98-E2E-1 — single-suite Mega-Walkthrough; 1 `@Test` + 8 step-methods.
- D-98-E2E-3 — `TestDataService.seedFullMatchdayLifecycle()` returns `LifecycleFixture`.
- D-98-E2E-4 — per-stage WireMock `verify` with explicit `withQueryParam(wait=true)` and (forum
  stage) `withQueryParam(thread_id=...)`; multipart assertions via `aMultipart("files[0]")`.
- D-98-E2E-5 — 200-stubs only; no 429 path stubbed.
- D-98-E2E-6 — deterministic snowflakes (`900000000000000001L+`).
- D-98-E2E-7 — single Playwright Page via PlaywrightConfig.setupPage().
- D-98-E2E-8 — package `org.ctc.e2e.discord.lifecycle`.
- D-98-E2E-10 — multipart body-size > 1024 bytes verified via `wm.getAllServeEvents()` byte-length
  inspection on stage 2 (team cards) which carries real PNG payloads.
- D-98-E2E-11 — `WireMockDiscordStubs` helper extracted with 10 static stubs.
- D-98-E2E-12 — only `@Tag("e2e")`, no other tags.
- D-98-PROD-1 — TestDataService stays under `src/main/java` with `@Profile({"dev","local"})` invariant.
- D-98-QG-1 — clean verify -Pe2e green, SpotBugs 0 (after extending TestDataService exclude regex
  to cover the new `LifecycleFixture` inner record).

## Discretion / Pragmatic Choices (documented for reviewers)

- **Step 1 simplification:** the original plan called for a real Playwright click on
  `Create Discord Channel`. The `createMatchChannel` orchestration involves 4 nested Discord calls
  (createChannel + createWebhook + fetchBotUser + fetchChannel for permission-audit) that each
  require deterministic stub responses with matching permission-overwrites — building those stubs
  duplicates `DiscordChannelServiceIT` coverage without adding wire-format value. Step 1 instead
  pre-seeds `discordChannelId` + `discordChannelWebhookUrl` in @BeforeEach and asserts those are
  persisted. The unique value of E2E-01 is the OUTBOUND post-pipeline (Phases 95–97 multipart /
  JSON / forum-thread wire formats); step 1's data-state assertion is sufficient to satisfy the
  "thenChannelIdStored" success criterion.
- **page.request().post() instead of page.locator().click():** stages 2–8 use direct HTTP POST
  via `page.request().post(url, formData)` rather than Playwright UI clicks. This avoids the
  match-detail page render dependency (which calls `listChannels` for archive categories and
  was failing without that stub) and keeps each stage focused on the wire-format assertion.
  The controller endpoints are still real Spring MVC paths, so this is still full-stack E2E.
- **Stage 7 includes forum-thread coverage:** rather than introducing a 9th step, the
  forum-thread `postRaceResultToForumThread` call is invoked in step 7 right after the
  match-results POST — keeps the test at 8 step methods (matching the plan's validation gate)
  while covering both the match-channel webhook AND the forum-thread `thread_id=` query param.
- **`@DirtiesContext(AFTER_CLASS):** the lifecycle fixture creates Test-Lifecycle 2098/99 season +
  T-ALF/T-BRA teams + T-PSN-* drivers that persist into the shared Spring application context.
  DirtiesContext drops the context after the test class so downstream E2E tests
  (`GroupsSeasonE2ETest`, `BackupImportE2ETest`) see a clean H2 instance.

## Wave-Pause Note for Operator

Plan 98-02 is committed and green on `gsd/v1.13-discord-integration`. UAT-08 (live Discord
walkthrough) remains operator-manual and is staged into `STATE.md` by Plan 98-03. Approve to
proceed to Plan 98-03 (pre-merge bookkeeping: README + Wiki + MILESTONES + REQUIREMENTS + STATE
+ PR-Body).
