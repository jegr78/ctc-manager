---
gsd_state_version: 1.0
milestone: v1.13
milestone_name: Discord Integration & Carry-Forwards
status: executing
stopped_at: Phase 93 context gathered
last_updated: "2026-05-21T11:43:49.000Z"
last_activity: 2026-05-21 -- Phase 93 execution started
progress:
  total_phases: 7
  completed_phases: 1
  total_plans: 7
  completed_plans: 4
  percent: 14
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-20)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** Phase 93 — discord-foundation

## Current Position

Phase: 93 (discord-foundation) — EXECUTING
Plan: 1 of 3
Status: Executing Phase 93
Last activity: 2026-05-21 -- Phase 93 execution started

## Completed Milestones

- v1.0 Technical Debt Cleanup (5 phases, 12 plans) — shipped 2026-04-04
- v1.1 Codebase Concerns Cleanup (10 phases, 20 plans) — shipped 2026-04-07
- v1.2 Driver Merge (4 phases, 5 plans) — shipped 2026-04-07
- v1.3 English Test Data (8 phases) — shipped 2026-04-10
- v1.5 Code Review Fixes (9 phases, 14 plans) — shipped 2026-04-15
- v1.6 Static Site Quality (17 phases, 56 requirements) — shipped 2026-04-18
- v1.8 Bulk Driver Import from Google Sheets (2 phases, 4 plans, +52 tests) — shipped 2026-04-25
- v1.9 Season Phases & Groups (15 phases, ~70 plans, 38/38 requirements, +88.4k LOC) — shipped 2026-05-09
- v1.10 Spring Boot 4.0.6 Upgrade & Data Export/Import (9 phases, 50 plans, 39/39 requirements, +77.4k LOC, 87.80% JaCoCo) — shipped 2026-05-16
- v1.11 Tooling Infrastructure & Tech-Debt Sweep (8 phases 80-87, 46 plans, 46/46 requirements, JaCoCo 88.88%, 1675 tests, CI E2E median 23:00) — shipped 2026-05-18
- v1.12 Driver-Import Gap-Closure & Test Performance Round 2 (4 phases 88-91, 15 plans, 15/15 requirements substantively satisfied, JaCoCo 88.44%, 1696 tests, CI E2E median **17:39** Δ−23.3 %, Nyquist 4/0/0 compliant) — shipped 2026-05-20

## Active Milestone

**v1.13 Discord Integration & Carry-Forwards** — Phases 92-98 (in flight).

- Branch: `gsd/v1.13-discord-integration` (off `origin/master`)
- Design spec: `docs/superpowers/specs/2026-05-20-discord-integration-design.md` (18 decisions)
- Detailed roadmap: `.planning/milestones/v1.13-ROADMAP.md`
- Coverage: 25/25 REQ-IDs mapped to 7 phases (5 carry-forward + 20 Discord)
- Estimated duration: 15-20 working days
- New Flyway migrations: V8 (`discord_global_config`), V9 (`teams.discord_role_id`), V10 (`matches.discord_*`), V11 (`discord_post`), V12 (`seasons.discord_*_thread_id`)
- Zero new production dependencies (Spring `RestClient` is Spring 6.1+ core)

### Phase Order (Sequenced)

| Phase | Name | REQ-IDs | Depends on |
| ----- | ---- | ------- | ---------- |
| 92 | Carry-Forwards & Cleanup | UX-01, COV-01, CLEAN-01, DOCS-01, BOOK-01 | — |
| 93 | Discord Foundation | INFRA-01, INFRA-02, INFRA-03 | 92 |
| 94 | Team Roles + Match Channel Lifecycle | CHAN-01, CHAN-02, CHAN-03 | 93 |
| 95 | Match Channel Posts | POST-01..05 | 94 |
| 96 | Provisional Graphic + Forum Threads | GRAFX-01, FORUM-01, FORUM-02 | 95 |
| 97 | Matchday-Level Posts | POST-06, POST-07, POST-08 | 96 |
| 98 | Polish + E2E + Docs + Close | E2E-01, DOCS-02, DOCS-03 | 97 |

## Deferred Items

Items carried forward into v1.13 (from v1.12 audit + post-merge follow-ups) — all absorbed into Phase 92:

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| tech_debt | UX-01 scope-gap — `CsvImportController` (race-results sheet-import) not migrated to typed-catch + `errorCategory` flash + badge UX; T-91-02-IL info-leak (`e.getMessage()` echo) re-introduced for this 3rd consumer of typed `GoogleSheetsService` | **Phase 92** — apply typed-catch + `errorCategory` flash + badge UX to `CsvImportController` for parity (REQ UX-01) |
| tech_debt | JaCoCo Δ−0.44 pp (88.44 % vs 88.88 %, above 82 % gate); root cause javac-mandated defensive `catch (GoogleApiException)` blocks (Java 25 lacks sealed-exhaustiveness on catch) + uncovered service-layer IOException paths | **Phase 92** — add `RaceControllerCalendarTest` + IT coverage for Google service error paths (REQ COV-01) |
| tech_debt | CLEAN-02 grep-predicate drift — Phase 89 PERF-01 introduced AssertJ `Assumptions.assumeThat` in `BackupStagingDirPerForkIT.java:12,37`; different package + intent than the JUnit `Assumptions.assumeFalse` that CLEAN-02 originally targeted, but grep can't distinguish | **Phase 92** — tighten predicate to `org\.junit\.jupiter\.api\.Assumptions` (REQ CLEAN-01) |
| docs_debt | Optional audit-trail retrofill — Phases 89/90/91 close on VALIDATION.md + per-plan SUMMARY.md without phase-level VERIFICATION.md (v1.11 had VERIFICATION.md per phase, some retroactively via commit `2e84fd57`) | **Phase 92** — optional retroactive `89-VERIFICATION.md` / `90-VERIFICATION.md` / `91-VERIFICATION.md` (REQ DOCS-01) |
| bookkeeping_debt | REQUIREMENTS.md checkbox + traceability lag — 7 of 15 v1.12 REQ-IDs require flip from `Pending`/`[ ]` to `Resolved`/`[x]` (PERF-01..06 + UX-01); Plan 91-03 deliberately deferred per stale-state avoidance pattern | **Phase 92** — flip 7 stale checkboxes + 4 stale `Pending` traceability rows in `milestones/v1.12-REQUIREMENTS.md` (REQ BOOK-01) |
| uat | UAT-02 legacy season visual smoke (real pre-V4 production data) | post-deploy operator action (procedure docs/uat/UAT-02-legacy-season-smoke.md) |
| uat | QUAL-02 local-profile MariaDB manual smoke (DevDataSeeder widening) | post-deploy operator action |
| uat | UX-01 visual UAT — 4 error-category badges × Desktop + Mobile (8 Playwright screenshots) | post-deploy operator action (procedure 91-02-SUMMARY.md § Manual UAT) |

Post-merge self-resolving items (not tracked further):

| Category | Item | Status |
| -------- | ---- | ------ |
| post_merge | v1.13 release workflow tagging (produces `v1.13.0` annotated tag + GitHub Release + `ghcr.io/jegr78/ctc-manager:1.13.0` + `:latest`) | will resolve on squash-merge with subject `feat(v1.13): discord integration & carry-forwards` (per `docs/operations/release-runbook.md § 6 — Squash-merge subject discipline`) |

## Pending UATs

### UAT-02: Legacy Season Visual Smoke (carry-forward from v1.11 QUAL-05)

- **Procedure:** docs/uat/UAT-02-legacy-season-smoke.md
- **Status:** Executing Phase 93
- **Result:** _(operator fills after execution)_
- **Date:** _(operator fills)_
- **Screenshots:** _(operator links)_

### QUAL-02: Local-Profile MariaDB Smoke (carry-forward from v1.11 QUAL-02)

- **Procedure:** start app with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` and verify `DevDataSeeder` widened to `@Profile({"dev","local"})` seeds correctly against real MariaDB
- **Status:** post-deploy operator action
- **Result:** _(operator fills after execution)_

### UX-01: Driver-Import Error-Category Badges (carry-forward from v1.12)

- **Procedure:** Trigger each of the 4 typed `GoogleApiException` permits (transient/auth/not-found/permission) in `/admin/driver-import` and capture Desktop + Mobile screenshots per `feedback_playwright_cli`
- **Status:** post-deploy operator action
- **Result:** _(operator fills)_

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md "Key Decisions" table and per-milestone in `milestones/v*.x-ROADMAP.md`. v1.13 decisions will be captured per phase during `/gsd-discuss-phase`.

Roadmap-level decisions for v1.13 (captured during ROADMAP.md drafting 2026-05-20):

- **Phase 92 sequenced first (D-Phase-92-First)** — JaCoCo recovery + CLEAN-01 grep-predicate fix creates a clean baseline before Flyway V8-V12 migrations land. Running coverage measurements against a broken baseline would mask Discord-phase regressions. UX-01 + COV-01 + CLEAN-01 + DOCS-01 + BOOK-01 all bundled into a single sequencing-first phase to clear the v1.12 audit slate before any new business logic.
- **Phase 93 hard-precedes Phase 94 (D-Foundation-Before-Lifecycle)** — `DiscordRestClient` + `DiscordWebhookClient` + sealed `DiscordApiException` hierarchy + `DiscordEmojiCache` must exist before channel-creation buttons can be wired. CHAN-02's "Create Discord Channel" button requires `DiscordRestClient.createChannel` + `createWebhook` and a working CSRF + DTO mass-assignment surface from INFRA-02.
- **Phase 95 hard-precedes Phase 96 (D-Tracking-Before-Forum)** — `DiscordPost` tracking entity from POST-01 (Flyway V11) is structurally reused by FORUM-02 (race-result forum-thread posts). Same `DiscordPostService.postOrEdit` pattern + same `discord_post` row tracking; FORUM-02 only adds the `?thread_id=` query param + auto-unarchive logic.
- **Phase 96 hard-precedes Phase 97 (D-Threads-Before-MatchdayPosts)** — Forum-threads must be linkable on `seasons.discord_*_thread_id` before POST-07 (Matchday Overview + Power Rankings → race-results thread) and POST-08 (Standings → standings thread) can target them.
- **Phase 98 sequenced last (D-E2E-After-All-Buttons)** — E2E suite exercises the full create-channel → post-all-stages → archive lifecycle covering Phases 94-97; cannot run before all 11 post types from POST-01..08 have buttons.
- **Zero new production dependencies (D-No-New-Deps)** — Spring `RestClient` is Spring 6.1+ core, multipart via `MultipartBodyBuilder`, WireMock already in test scope. Avoids JDA / Discord4J transitive-dependency footprint + license review.
- **Outbound-only architecture (D-Outbound-Only)** — No inbound slash commands or reaction reads. Local app, no always-online endpoint feasible. Inbound is out-of-scope per design spec § 2.2 and tracked as DISC-FUTURE-01.
- **Button-triggered, no auto-post (D-Operator-Control)** — All Discord posting is operator-button-triggered. No DB-event auto-trigger pipeline (DISC-FUTURE-02). Preserves full operator control over what lands in Discord.

### Phase Numbering

Last phase shipped: **91** (v1.12 closer). v1.13 spans phases **92-98** (integer phases, no insertions). Per design spec § 5: 7 phases, ~23 plans estimated.

### Roadmap Evolution

- 2026-05-20: v1.12 milestone closed via `/gsd-complete-milestone v1.12`; PR #129 awaits squash-merge.
- 2026-05-20: v1.13 milestone started. Branch `gsd/v1.13-discord-integration` created off `origin/master`. Brainstorming session (multi-round) resolved 18 design decisions; design spec committed at `docs/superpowers/specs/2026-05-20-discord-integration-design.md`. 25 REQ-IDs defined in `.planning/REQUIREMENTS.md` (5 carry-forward UX-01/COV-01/CLEAN-01/DOCS-01/BOOK-01 + 20 Discord INFRA-01..03/CHAN-01..03/POST-01..08/GRAFX-01/FORUM-01..02/E2E-01/DOCS-02..03).
- 2026-05-20: v1.13 ROADMAP.md created — 7 phases (92-98), 25/25 REQ-IDs mapped (100 % coverage), no orphans. Per-phase REQ counts: 5+3+3+5+3+3+3 = 25 ✓. Awaiting user approval before `/gsd-discuss-phase 92`.

### Blockers/Concerns

At roadmap creation (2026-05-20):

- **JaCoCo baseline regression must be closed in Phase 92** — current 88.44 % vs v1.11 88.88 % baseline; Discord-phase test coverage assumed to maintain ≥ 88.88 % only if Phase 92's `RaceControllerCalendarTest` + Google service IT lands first. Phase 93+ measurements run against the post-Phase-92 baseline.
- **Live Discord UAT required for Phase 93's INFRA-03** — `Test Connection` button (Bot `GET /users/@me`) only meaningfully verifies against a live Discord token + a real (test) guild. WireMock-only ITs cover the happy + 4-permit exception paths but cannot prove the actual Discord-API contract is honored. UAT step explicit in Phase 93 success criteria.
- **Permission-overwrite audit (Phase 94 CHAN-02) is security-critical** — wrong role-mapping causes opposing team to see match-channel pre-match (T-93-03 from design spec § 3.4). Post-create permission-audit assertion is non-negotiable.
- **Rate-limit-burst risk on matchday-batch posting (Phase 97 POST-06)** — "Post Match Previews (batch)" iterates over `matchday.matches` and could exceed Discord's per-bucket token bucket. Mitigated by `DiscordRateLimitInterceptor` (per-bucket token-bucket + max-5-parallel + sequential batch).
- **Forum-thread auto-unarchive (Phase 96 FORUM-02)** — Discord requires PATCH `archived=false` before posting to an archived thread; default config leaves the thread unarchived after the post (per design spec § 4.7). Configurable but not exposed in v1.13 UI.
- **WireMock-Discord-simulator divergence risk (Phase 98 E2E-01)** — Mandatory UAT in Phase 98 against live Discord with test-season + edge cases (empty forum, archived thread, full category) to catch any drift between WireMock fixtures and real Discord behaviour.

### Baselines to Preserve

- JaCoCo line coverage: **≥ 88.88%** (v1.11 baseline; Phase 92 restores; subsequent phases must maintain or improve)
- Test count: **≥ 1696** (v1.12 baseline; Phase 92 adds ~10, Discord phases add ~50-80)
- `./mvnw verify -Pe2e` CI median (E2E step): **17:39 ± 20 %** (v1.12 baseline; WireMock-only Discord tests, no live Discord in CI)
- `BackupSchema.SCHEMA_VERSION`: **1** (must remain 1 unless backup wire contract changes)
- `EXPORT_ORDER` size: **24 entities** (guard test active; Discord entities under `org.ctc.discord.*` are structurally excluded by the `org.ctc.domain.model.*` package filter per Phase 72 D-15)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0 on new HIGH/CRITICAL** (3-layer FP suppression invariant maintained)
- Flyway migrations: V1-V7 immutable; v1.13 adds **V8, V9, V10, V11, V12**

## Session Continuity

**Last session:** 2026-05-21T10:07:12.458Z

**Stopped at:** Phase 93 context gathered

**Next action:** `/gsd-discuss-phase 92` to scope Phase 92 (Carry-Forwards & Cleanup): UX-01 `CsvImportController` parity + COV-01 JaCoCo recovery + CLEAN-01 grep-predicate tightening + DOCS-01 retroactive 89/90/91-VERIFICATION.md + BOOK-01 bookkeeping flip in `milestones/v1.12-REQUIREMENTS.md`.

**Branch:** `gsd/v1.13-discord-integration` (off `origin/master`)

## Operator Next Steps

1. Verify v1.13 ROADMAP.md + STATE.md + REQUIREMENTS.md reflect the brainstorming-approved 7-phase / 25-REQ structure (this commit).
2. Squash-merge v1.12 PR #129 if not yet merged (subject `feat(v1.12): driver-import gap-closure & test performance round 2` for the MINOR bump to `v1.12.0`).
3. Run `/gsd-discuss-phase 92` to decompose Phase 92 into plans (estimated 4 plans per design spec § 5).
4. After Phase 92 completes, the JaCoCo baseline is restored to ≥ 88.88 % and Discord-phase development can proceed against a clean coverage gate.
