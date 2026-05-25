---
plan: 98-07-bundle-verify-bookkeeping
phase: 98-polish-e2e-docs-close
milestone: v1.13-discord-integration
branch: gsd/v1.13-discord-integration
mode: inline-sequential
status: complete
completed: 2026-05-25
req_ids: [POST-09, POST-10]
---

# Plan 98-07 — SUMMARY

Bundle-Verify + Live-UAT-Re-Run + Pre-Merge-Bookkeeping. Closes Phase 98 (and
v1.13 milestone bar `/gsd-complete-milestone` operator step).

## Outcome

- **Bundle-Verify:** `./mvnw clean verify -Pe2e` → **2244 tests green** (1635 surefire + 494 IT + 115 Playwright E2E), 0 failures, 0 errors.
- **JaCoCo line-coverage:** **88.9890 %** (>82 % pom gate; +0.11 pp vs Phase-92 baseline 88.88 %; +0.55 pp vs v1.12 baseline 88.44 %).
- **Live-UAT-Re-Run:** Stages 5c + 14 + 15 PASS per operator visual approval 2026-05-25 (live Discord-Test-Guild artifacts logged in STATE.md).
- **REQ-Coverage:** 25/25 → **27/27** (POST-09 + POST-10 Resolved).

## Atomic Commits

| # | Commit | What |
|--:|--------|------|
| 1 | `40711ce` | docs(98-07): add POST-09 + POST-10 REQ-IDs as Pending + scaffold plan |
| 2 | `2c7ff63` | docs(98-07): ROADMAP Phase-98 Erfolgskriterien 7 + 8 add (Pairings + Schedule) |
| 3 | `3ae0d1b` | docs(98-07): runbook §§ 2.3 + 7 — Pairings + Schedule buttons + UAT Stages 14+15 |
| 4 | `379de2f` | docs(98-07): STATE.md UAT-08 + Stages 5c+14+15 + Deferred 98-04 resolved |
| 5 | `de27441` | fix(98-07): null-guard DiscordDevSeeder when configService.getOrInitialize returns null |
| 6 | `a5e2caf` | test(98-07): stub canPostMatchdayPairings + canPostMatchdaySchedule in MatchdayControllerPostEndpointsIT |
| 7 | `3dffefe` | docs(98-07): flip POST-09 + POST-10 → Resolved (UAT-08 Stages 14+15 PASS) |
| 8 | `8989bd7` | docs(98-07): MILESTONES.md v1.13 entry — +3 plans, 2244 tests, 88.99 % coverage |
| 9 | (this commit) | docs(98-07): SUMMARY + VALIDATION refresh — Phase 98 ready for milestone close |

External (no git commit):
- PR #130 body refreshed via `gh pr edit 130 --body-file -` with phase-tracker + verification snapshot + Live-UAT evidence + deferred items + post-merge note. Squash-subject locked: `feat(v1.13): discord integration & carry-forwards`.

## Bug-fixes surfaced by Bundle-Verify

Two test-only regressions caught by `clean verify -Pe2e` after Plans 98-05/06 landed:

1. **DiscordDevSeeder NPE in `RaceControllerCalendarTest`** (commit `de27441`).
   - Root cause: Plan 98-05's new `backfillDefaultTemplates(cfg)` runs on `ApplicationReadyEvent` and dereferences `cfg`. `RaceControllerCalendarTest` `@MockitoBean`s `DiscordGlobalConfigService` — the mock returns `null` for `getOrInitialize()` → NPE → all 9 tests fail with "Failed to load ApplicationContext".
   - Fix: defensive null-guard at top of `seed()`; production path unaffected (real service always returns a row via `getOrInitialize → repo.save(new DiscordGlobalConfig())`).
   - Lesson: per CLAUDE.md "Phase-Overwrite-Prevention" — when Plan-N adds a new `ApplicationReadyEvent` listener that dereferences a service, audit which test classes `@MockitoBean` that service.

2. **`MatchdayControllerPostEndpointsIT` 500 on detail-GET** (commit `a5e2caf`).
   - Root cause: Plans 98-05/06 added `canPostMatchdayPairings` + `canPostMatchdaySchedule` to `DiscordPostService`. The IT mocks the service via `@MockitoBean` and stubs only the 2 pre-existing `canPostMatchday{Results,PowerRankings}` methods → the 2 new methods return null → `populateMatchdayDiscordModel` NPEs on `pairingsPreFlight.canPost()`.
   - Fix: extend `@BeforeEach` with stubs for the 2 new methods returning `MatchPreviewPreFlightResult(true, null)`.
   - Lesson: per CLAUDE.md "Grep All Usages Before Refactor" — when adding new methods to a `@MockitoBean`'d service, grep for `@MockitoBean.*DiscordPostService` and audit each `@BeforeEach`.

## Live-UAT-Re-Run Evidence (Stages 5c + 14 + 15)

Operator-driven on live Discord-Test-Guild 2026-05-25 — full text in `.planning/STATE.md` § UAT-08:

- **Stage 5c** — Schedule-Embed `inline: false` polish: live Re-Post on Match `880eb32e` field-edit + Save → `Edited SCHEDULE messageId=1508418568407224370` (same `messageId` from Stage 5 → PATCH path verified post-layout-flip). Discord embed shows all 4 fields one-per-row (operator visual approval).
- **Stage 14** — MATCHDAY_PAIRINGS: Matchday `cb5e3e10` → Edit Pairings (`pickDeadline=2026-05-30T19:00` + `scheduledWeekend="30 May - 1 June"`) → Post Matchday Pairings → `Posted MATCHDAY_PAIRINGS messageId=1508521181710385192` (19:24:36). Re-Edit weekend → `"29-31 May (EDITED)"` → button flipped to **Update Matchday Pairings** → click → `Edited MATCHDAY_PAIRINGS messageId=1508521181710385192` (19:25:11, **same `messageId`** = PATCH verified). `{{ctcEmoji}}` rendered as `<:CTC:id>` long-form (after operator-noted bug + emoji-cache resolution fix + DiscordDevSeeder auto-refresh).
- **Stage 15** — MATCHDAY_SCHEDULE: same matchday → Post Matchday Schedule → `Posted MATCHDAY_SCHEDULE messageId=1508519650579972120`. Pure-multipart confirmed (no Markdown, no embed). 3× iterative Update PATCHes all preserved `messageId`.

## Decisions Honored

- D-98-PLAN-6: 3 new plans sequential inline (98-05 → 98-06 → 98-07), Wave-Pause between each.
- D-98-PLAN-7: Wave-Pause + inline-sequential + no worktree + no subagent (per CLAUDE.md "Subagent Rules — Inline Sequential is the Default").
- D-98-VERIFY-1: EIN voller `clean verify -Pe2e` in Plan 98-07 covering 98-04 + 98-05 + 98-06 (NOT per-plan during TDD loops).
- D-98-UAT-1: incremental Live-UAT-Re-Run for 3 new stages + smoke regression.
- D-98-UAT-2: in-place edit of UAT-08 (NOT a new UAT-09) — 3 stages added.
- D-98-BOOK-1: ALL 8 bookkeeping items pre-merge in PR #130 (REQUIREMENTS / ROADMAP / MILESTONES / STATE / runbook / PR body / VALIDATION / SUMMARY).
- D-98-DEV-1: dev-server stop before `./mvnw clean` (per memory `feedback_clean_kills_app_pid`).
- D-98-FILES-1: append-only on shared files (REQUIREMENTS / ROADMAP / MILESTONES / STATE / runbook) — no rewrites.
- D-98-AUTO-1: NO AFTER_COMMIT hook for POST-09 or POST-10 (operator-driven Re-Post).

## Files Modified

Bookkeeping-only (no source-code changes beyond the 2 bundle-verify bugfixes):

- `.planning/REQUIREMENTS.md` — POST-09 + POST-10 added (Pending → Resolved); total 25 → 27.
- `.planning/milestones/v1.13-ROADMAP.md` — Phase-98 [x] + Erfolgskrit 7 + 8 ✓; Coverage-Summary 25 → 27; Progress 0/3 → 7/7.
- `.planning/MILESTONES.md` — v1.13 entry refreshed (27 plans, 2244 tests, 88.99 %, timeline locked).
- `.planning/STATE.md` — UAT-08 in-place edit (Stages 5c + 14 + 15 added); Deferred Items § ui_debt resolution-line links Stage 5c.
- `docs/operations/discord-integration.md` — § 2.3 +2 bullets (POST-09/10); § 7 9 → 15 stages.
- `.planning/phases/98-polish-e2e-docs-close/98-07-PLAN.md` — scaffolded inline.
- `.planning/phases/98-polish-e2e-docs-close/98-07-SUMMARY.md` — this file.
- `.planning/phases/98-polish-e2e-docs-close/98-VALIDATION.md` — Goal-Backward roll-up refreshed for 7 plans incl. 98-04..98-07.
- `src/main/java/org/ctc/discord/DiscordDevSeeder.java` — null-guard at top of `seed()`.
- `src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java` — 2 new `@BeforeEach` mock stubs.

## Milestone-Close-Hint (Operator)

Phase 98 is complete. Plan 98-07 is the closer. Next operator step:

1. (Optional) re-run `./mvnw clean verify -Pe2e` — already green per this plan.
2. `/gsd-complete-milestone v1.13` — archives Phase 98 under `.planning/milestones/v1.13-phases/` + writes `v1.13-MILESTONE-AUDIT.md`.
3. `gh pr merge 130 --squash --subject "feat(v1.13): discord integration & carry-forwards"` — single squash commit per CLAUDE.md "Git Workflow".
4. CI release workflow tags `v1.13.0` + builds GitHub Release + Docker images (no local `git tag` per CLAUDE.md "No Local Git Tags").
