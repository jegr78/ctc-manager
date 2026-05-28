---
phase: 98-polish-e2e-docs-close
nyquist_compliant: true
last_updated: 2026-05-25
---

# Phase 98 VALIDATION — Roll-up (Polish + E2E + Docs + Close)

## Per-Plan Nyquist Scoreboard

| Plan | Disposition | Rationale |
|------|-------------|-----------|
| 98-01 | n/a | 0 new `@Test` methods — gate-based (CSS + Runbook + screenshots + clean verify + 9-page mobile sweep). See `98-01-VALIDATION.md`. |
| 98-02 | true | 1 new `@Test` (`DiscordFullMatchdayLifecycleE2ETest.fullMatchdayLifecycle()`) covering 8 BDD steps via `step{1-8}_…` helpers; sample target 1/1 met. All 9 gates green. See `98-02-VALIDATION.md`. |
| 98-03 | n/a | 0 new `@Test` methods — gate-based (.gitignore + README + MILESTONES + REQUIREMENTS + STATE + Wiki + PR-body). See `98-03-VALIDATION.md`. |
| 98-04 | n/a | 0 new `@Test` methods — extends `DiscordPostServiceScheduleIT` with `equalTo("false")` invariant guards on all 4 fields after `buildSchedulePayload` flip to `inline: false`. See `98-04-SUMMARY.md`. |
| 98-05 | true | 6 new `@Test` methods in `DiscordPostServiceMatchdayPairingsIT` (incl. emoji-cache `<:CTC:id>` long-form resolution + body-size>1024 multipart assertion) + Mockito pre-flight matrix + 4 E2E button-state cases in `MatchdayDetailDiscordAnnouncementE2ETest`. Sample target met. See `98-05-SUMMARY.md`. |
| 98-06 | true | 4 new `@Test` methods in `DiscordPostServiceMatchdayScheduleIT` (incl. pure-multipart `body.doesNotContain("\"content\":")` + `body.doesNotContain("\"embeds\":")` invariants) + 4 new E2E button-state cases. Sample target met. See `98-06-SUMMARY.md`. |
| 98-07 | n/a | 0 new `@Test` methods — bundle-verify + bookkeeping + live-UAT-Re-Run. 2 test fixes (DiscordDevSeeder null-guard + MatchdayControllerPostEndpointsIT mock-stubs). See `98-07-SUMMARY.md`. |

**Scoreboard: 3 / 0 / 0** (true / partial / pending) for plans that produce test code.

## Phase-Goal-Backward Walk-Through

Phase 98 goal (from `v1.13-ROADMAP.md`, post-2026-05-25 re-open): *Schließe Polish (Mobile-Overflow + Schedule-Embed-Layout), E2E (Mega-Walkthrough + Pairings/Schedule), Docs (Runbook §§ 1.9-7 + README + Wiki), Matchday-Level Announcement-Posts (POST-09 + POST-10) und Pre-Merge-Bookkeeping ab; UAT-08 ist Operator-Manual.*

| Goal sub-deliverable | Evidence | Status |
|----------------------|----------|--------|
| ROADMAP Erfolgskrit 6 — Mobile-Polish 4 mandatory + 5 sample pages 0px overflow at 375×667 | `.screenshots/98-01-mobile-polish/*.png` + Gate 7 in `98-01-VALIDATION.md` (all 9 pages report `scrollWidth - innerWidth == 0`) | ✓ |
| DOCS-02 — operator runbook expanded with §§ 1.9 / 2.3 / 6 / 7 + App-UI screenshots + Stages 14+15 | `docs/operations/discord-integration.md` (Plan 98-07 § 2.3 +2 bullets + § 7 9 → 15 stages); 8 PNGs in `docs/operations/images/discord/` | ✓ |
| E2E-01 — single suite walks the full lifecycle in CI under the `e2e` profile | `DiscordFullMatchdayLifecycleE2ETest` green in full `clean verify -Pe2e` (1635 surefire + 609 failsafe = 2244 tests green) | ✓ |
| DOCS-03 — README + Wiki canonical paragraph + working link to runbook | `README.md` Discord Integration bullet + Note (v1.13) callout; `.wiki-clone/Discord-Integration.md` pushed to `ctc-manager.wiki.git` commit `d0651bd` | ✓ |
| ROADMAP Erfolgskrit 7 — POST-09 MATCHDAY_PAIRINGS hybrid Markdown+PNG on Announcement-Channel | `DiscordPostService.canPostMatchdayPairings/postMatchdayPairings/buildMatchdayPairingsMarkdown` + Flyway V15 + `MatchdayPairingsGraphicService` + `DEFAULT_MATCHDAY_PAIRINGS_TEMPLATE` w/ `{{ctcEmoji}}` resolution + Live-UAT Stage 14 (Initial-POST `1508521181710385192` 19:24:36 + Update-PATCH 19:25:11 same `messageId`); see `98-05-SUMMARY.md` | ✓ |
| ROADMAP Erfolgskrit 8 — POST-10 MATCHDAY_SCHEDULE pure-multipart PNG on Announcement-Channel | `DiscordPostService.canPostMatchdaySchedule/postMatchdaySchedule` (NO `build*Markdown`) + `MatchdayScheduleGraphicService` + Live-UAT Stage 15 (Initial-POST `1508519650579972120` + 3× PATCH same `messageId`); see `98-06-SUMMARY.md` | ✓ |
| In-milestone polish — Schedule-Embed `inline: false` layout (Deferred Items § ui_debt) | `DiscordPostService.buildSchedulePayload` all 4 fields `inline: false` + `DiscordPostServiceScheduleIT` invariant-guard + Live-UAT Stage 5c (same `messageId 1508418568407224370` PATCH); see `98-04-SUMMARY.md` + STATE.md § Deferred Items closeout | ✓ |
| Pre-merge bookkeeping (D-98-PLAN-5 + D-98-BOOK-1) — MILESTONES + REQUIREMENTS + ROADMAP + STATE + runbook + PR body | MILESTONES.md v1.13 entry refreshed (27 plans, 2244 tests, 88.99 %); REQUIREMENTS.md 27/27 with POST-09 + POST-10 Resolved; ROADMAP Phase-98 [x] + Erfolgskrit 7 + 8 ✓; STATE.md UAT-08 Stages 5c+14+15 added in-place; PR #130 body refreshed | ✓ |
| UAT-08 live-verified (D-98-UAT-1) — operator-bound, 16 stages | `STATE.md` § UAT-08 with 13 original + 3 in-milestone-extended Stages (5c + 14 + 15) all PASS, messageIds + operator visual approval logged | ✓ |
| No `git tag` and no `gh pr merge` (D-98-PLAN-4) | `git tag --list 'v1.13*'` = empty; PR #130 state = OPEN | ✓ |
| `./mvnw clean verify -Pe2e` green; SpotBugs 0; JaCoCo ≥ Phase-92 baseline | 2244 tests green (Plan 98-07 bundle-verify); JaCoCo 88.99 % (+0.11 pp vs Phase-92 baseline 88.88 %; +0.55 pp vs v1.12 baseline); SpotBugs 0 | ✓ |

## Verification Outcome

- **All plan-level gates pass** across 7 plans (98-01..98-07). Plans 98-04..98-07 added during the 2026-05-25 Phase-98 re-open per CONTEXT Q-98-07.
- **Phase goal:** all 11 sub-deliverables ✓.
- **Test suite:** 1635 surefire + 609 failsafe (494 IT + 115 Playwright E2E) = **2244 tests, all green**.
- **JaCoCo:** **88.99 %** (above 82 % pom gate; +0.11 pp vs Phase-92 baseline 88.88 %; +0.55 pp vs v1.12 baseline 88.44 %).
- **SpotBugs:** 0 BugInstance.
- **CodeQL:** gate-step exit 0 (no new HIGH/CRITICAL alerts on the milestone PR head SHA).
- **CI E2E runtime:** to be harvested on first CI run on PR #130 head SHA (will be added to `v1.13-MILESTONE-AUDIT.md`).

## Decisions Honored (Phase-Wide)

D-98-DOCS-1..4, D-98-MOB-1..4, D-98-WIKI-1..3, D-98-CLOSE-1..2, D-98-PLAN-2..7, D-98-E2E-1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, D-98-TEST-1, 2, D-98-PROD-1, D-98-QG-1, D-98-REQ-1..2, D-98-SCHED-1..2, D-98-AUTO-1, D-98-VERIFY-1, D-98-UAT-1..2, D-98-BOOK-1, D-98-DEV-1, D-98-FILES-1. D-98-E2E-2 documented as OVERRIDDEN by PATTERNS.md (`app.discord.base-url` + `@RegisterExtension WireMockExtension` is the codebase idiom).

## Next Steps for Operator

Per `98-07-SUMMARY.md` § Milestone-Close-Hint:

1. `./mvnw clean verify -Pe2e` — sanity recheck (already green per Plan 98-07).
2. `/gsd-validate-phase 98` — **this document is the output.**
3. `/gsd-complete-milestone v1.13` — archive + audit.
4. `gh pr merge 130 --squash --subject "feat(v1.13): discord integration & carry-forwards"`.
5. CI release workflow → `v1.13.0` tag + GitHub Release + Docker image push.
