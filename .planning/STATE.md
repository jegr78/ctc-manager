---
gsd_state_version: 1.0
milestone: v1.14
milestone_name: Team Card Redesign & Data Safety
status: planning
last_updated: "2026-05-29T00:00:00.000Z"
last_activity: 2026-05-29
progress:
  total_phases: 2
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-29 for v1.14 milestone start)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** v1.14 Team Card Redesign & Data Safety — roadmap drafted (2 phases, 4 requirements, 100 % coverage). Phase 104 (Data Safety Lockdown) is immediately startable; Phase 105 (Team Card Visual Redesign) awaits external Claude-Design handoff.

## Current Position

Phase: Not started (roadmap drafted, awaiting `/gsd-discuss-phase 104` or `/gsd-plan-phase 104`)
Plan: —
Status: Roadmap created — 2 phases (104-105), 4/4 requirements mapped
Last activity: 2026-05-29 — `/gsd-roadmapper` wrote v1.14 ROADMAP.md + REQUIREMENTS.md traceability

### Prior position (Phase 103 close)

Phase: 103 (string-utils-blank-check-sweep) — COMPLETE ✓ (v1.13 final phase)
Plan: 1 of 1 (103-01 — `StringUtils.hasText` sweep across 42 production files)
Verification: passed (v1.13 milestone close 2026-05-28, JaCoCo 89.43 %, 2393 tests)
Last activity: 2026-05-28 — v1.13 shipped (PR #130 squash-merged to master)

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
- v1.13 Discord Integration & Carry-Forwards (12 phases 92-103, 43 plans, 28/28 requirements satisfied, JaCoCo **89.43 %**, **2393 tests**, CI E2E median 17:39 held, Nyquist 12/12 compliant, 399 commits, +93.8k LOC, 9 new Flyway migrations V8-V16) — shipped 2026-05-28

## Active Milestone

**v1.14 Team Card Redesign & Data Safety** — started 2026-05-29 on branch `gsd/v1.14-team-card-redesign` (branched from `origin/master` `b0b90696`; pom already at `1.14.0-SNAPSHOT` from post-v1.13 release CI).

Two pillars (2 phases, 4 requirements, granularity `standard`, phase numbers continue from v1.13):

1. **Phase 104 — Data Safety Lockdown (SAFE-01 + SAFE-02)** — sofort startbar; `DevDataSeeder` + `TestDataService` zurück auf `@Profile("dev")` (v1.11 `@Profile({"dev","local"})` Drift) plus Regressions-IT, der den Re-Drift fängt. Fix bereits vorbereitet in `stash@{0}` (`v1.14-phase1-seed: scope DevDataSeeder + TestDataService to @Profile('dev')`). End-of-phase `./mvnw clean verify -Pe2e` war für den Bean-Scope-Flip allein bereits grün; nur Regressions-IT + Re-verify bleibt als neue technische Arbeit.

2. **Phase 105 — Team Card Visual Redesign (CARD-01 + CARD-02)** — **awaiting external Claude-Design handoff before discuss/plan/execute.** Discuss/Plan/Execute startet erst sobald der User das HTML/CSS-Spec aus der parallel laufenden Claude-Design-Session hier einbringt. CARD-02 sichert Backward-Compat für alle bestehenden Card-Consumer (Discord auto-post POST-02, manueller Re-Post + Refresh auf `/admin/discord/posts` + Team-Detail, Admin-Preview).

Roadmap-Coverage: 4/4 v1.14 requirements mapped to exactly one phase. Traceability section in `.planning/REQUIREMENTS.md` reflects SAFE-01/SAFE-02 → Phase 104, CARD-01/CARD-02 → Phase 105.

v1.13 PR #130 squash-merged to master 2026-05-28; release CI tagged `v1.13.0` und bumped pom auf 1.14.0-SNAPSHOT (`b0b90696`).

## Deferred Items

Items carried forward into v1.14 from v1.13 close (per `PROJECT.md § Out of scope for v1.14` + `REQUIREMENTS.md § Future Requirements`):

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| future_req | DISC-FUTURE-01 — Inbound Discord interaction (slash commands, polls, reaction reads) | Deferred to a later milestone; requires deployment model change (always-online endpoint instead of outbound webhooks) |
| future_req | DISC-FUTURE-02 — Auto-trigger pipeline (race-save → post) | Deferred; revisit once edit-confidence is established |
| future_req | DISC-FUTURE-03 — Discord settings-form migration into the admin app | Deferred to a later milestone |
| future_req | DISC-FUTURE-04 — Multi-guild support | Deferred to a later milestone |
| future_req | DISC-FUTURE-05 — Discord-notification webhook for the public site | Deferred to a later milestone |
| tech_debt | String `.isEmpty()` audit (~10 callsites with different semantics from `.isBlank()`) | Deferred; case-by-case decision per Phase 103 CONTEXT D-06 |
| uat (carry) | UAT-02 legacy season visual smoke (real pre-V4 production data) | post-deploy operator action (procedure `docs/uat/UAT-02-legacy-season-smoke.md`); cross-milestone per CLAUDE.md "Pre-existing debt may cross milestone boundaries" |
| uat (carry) | QUAL-02 `local`-profile MariaDB manual smoke | post-deploy operator action; **note:** Phase 104 SAFE-01 reverts the v1.11 widening — this UAT semantics changes from "verify seeder runs against MariaDB" to "verify seeder is ABSENT on local" (already covered by SAFE-02 regression IT) |
| uat (carry) | UX-01 visual UAT — 4 error-category badges × Desktop + Mobile (8 Playwright screenshots) | post-deploy operator action (procedure `.planning/phases/91/91-02-SUMMARY.md § Manual UAT`) |
| tech_debt (v1.13) | Existing match-channels created under the Phase-94 scheme (`md{N}-{home}-vs-{away}`) retain their old names. Phase 100 D-08 verdict 2026-05-26: leave-as-is — no PATCH-rename action, no diagnostic UI list, no lazy auto-rename. | Potential v1.14+ phase — admin bulk-rename action on `/admin/discord-config` that iterates matches WHERE `discord_channel_id IS NOT NULL` and PATCHes each via Discord API. **Not in v1.14 scope per scoping decision 2026-05-29.** |
| tech_debt (v1.13) | Two-scheme coexistence in the `matches` table is acceptable post-Phase-100. Discord is the source of truth for live channel names; `matches.discord_channel_id` stores only the channel ID, not the name. | Linked to row above. Diagnostic read-only "channels with old naming scheme" panel on `/admin/discord-config` is the optional v1.14+ entry point. **Not in v1.14 scope per scoping decision 2026-05-29.** |
| operator_action | Real-upload restore into `data/local/uploads/` — user already mirrored the real logo / car / track images to `~/Library/CloudStorage/.../CTC-Admin/Backup/uploads` | Operator action, not a v1.14 code requirement. Execute after Phase 104 ships if local-MariaDB smoke is desired. |
| operator_action | Orphan-file cleanup in `data/local/uploads/teams/` — 17 verwaiste Test-Logo folders (VRX / SGM / ADR / TBR / ICL / SVT / NFR / EGP / HMS / PWR) from v1.11 seeder drift | Operator action, not a v1.14 code requirement. After Phase 104 ships, run `rm -rf` against the 17 T-prefix / fictive-shortname folders. |

## Pending UATs

(Carry-forward from v1.13 close — no new UAT slots opened by v1.14 yet; UAT slots for Phase 104 + 105 will be added during `/gsd-discuss-phase` or `/gsd-plan-phase`.)

### UAT-02: Legacy Season Visual Smoke (carry-forward from v1.11 QUAL-05)

- **Procedure:** docs/uat/UAT-02-legacy-season-smoke.md
- **Status:** v1.13 milestone complete; cross-milestone carry-forward into v1.14
- **Result:** _(operator fills after execution)_
- **Date:** _(operator fills)_
- **Screenshots:** _(operator links)_

### QUAL-02: Local-Profile MariaDB Smoke (semantics change in v1.14 Phase 104)

- **Pre-Phase-104:** verify `DevDataSeeder` widened to `@Profile({"dev","local"})` seeds correctly against real MariaDB.
- **Post-Phase-104 (target semantics):** verify `DevDataSeeder` + `TestDataService` are ABSENT on `local` profile (Spring context boots, neither bean instantiated; SAFE-02 IT covers automated verification, this operator UAT covers the real-MariaDB visual smoke).
- **Status:** post-deploy operator action — semantics flips with Phase 104
- **Result:** _(operator fills after execution)_

### UX-01: Driver-Import Error-Category Badges (carry-forward from v1.12)

- **Procedure:** Trigger each of the 4 typed `GoogleApiException` permits (transient/auth/not-found/permission) in `/admin/driver-import` and capture Desktop + Mobile screenshots per `feedback_playwright_cli`
- **Status:** post-deploy operator action (cross-milestone)
- **Result:** _(operator fills)_

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md "Key Decisions" table and per-milestone in `milestones/v*.x-ROADMAP.md`. v1.14 decisions will be captured per phase during `/gsd-discuss-phase`.

Roadmap-level decisions for v1.14 (captured during ROADMAP.md drafting 2026-05-29):

- **2-phase structure (D-Two-Pillars)** — v1.14 has exactly two unrelated workstreams (Data Safety + Team Card Redesign); they don't share entities, services, templates, or migrations. Splitting into two phases (104 + 105) is more honest than bundling — each phase has its own goal, success criteria, and end-of-phase verify gate.
- **Phase 104 first (D-Safety-Before-Redesign)** — Data Safety is immediately executable (fix staged in `stash@{0}`, regression IT is small + isolated). Team Card Redesign blocks on external handoff. Sequencing Phase 104 first lets the milestone make progress while waiting on the design spec, and establishes a clean green-baseline on the milestone branch before any UI/CSS churn.
- **SAFE-01 + SAFE-02 in one phase (D-Fix-And-Fence-Together)** — The bean-scope flip and the regression IT belong together: shipping the flip without the fence re-opens the v1.11 drift risk on the next refactor; shipping the IT without the flip would test a state the code isn't in. Single small phase keeps them atomic.
- **Phase 105 blocked label in roadmap (D-External-Handoff-Visible)** — `/gsd-progress` and STATE.md MUST surface "awaiting external Claude-Design handoff" so the orchestrator and the user both see that Phase 105 is not blocked on internal capacity. The label removes the "why isn't this moving?" ambiguity.
- **CARD-02 backward-compat as explicit success criterion (D-No-Caller-Churn)** — The redesign rewrites `TeamCardService` output; every caller (Discord auto-post, Re-Post, Refresh, admin preview) must keep working without code changes on their side. Promoting CARD-02 to a first-class requirement (not "implicit") forces the planner to explicitly verify each consumer path during discuss/plan.

### Phase Numbering

Last phase shipped: **103** (v1.13 closer). v1.14 spans phases **104-105** (integer phases, no insertions, no reset — milestone started without `--reset-phase-numbers`). Estimated 2-5 plans total (Phase 104 ≈ 1-2 plans, Phase 105 ≈ 2-3 plans pending handoff scope).

### Roadmap Evolution

- 2026-05-28: v1.13 milestone closed via `/gsd-complete-milestone v1.13`; PR #130 squash-merged to master; release CI tagged `v1.13.0`.
- 2026-05-29: v1.14 milestone started. Branch `gsd/v1.14-team-card-redesign` already in place (off `origin/master` `b0b90696`, pom at `1.14.0-SNAPSHOT`). User scoping decision narrowed v1.14 to Layout + Visual Redesign of the single existing team card plus Data Safety lockdown (5 explicit exclusions in `REQUIREMENTS.md § Out of Scope`).
- 2026-05-29: v1.14 ROADMAP.md created — 2 phases (104-105), 4/4 REQ-IDs mapped (100 % coverage), no orphans. Per-phase REQ counts: 2 (SAFE) + 2 (CARD) = 4 ✓. Phase 104 ready for `/gsd-discuss-phase 104` or `/gsd-plan-phase 104`; Phase 105 blocked on external Claude-Design handoff.

### Blockers/Concerns

At roadmap creation (2026-05-29):

- **Phase 105 blocked on external handoff** — Discuss/plan/execute cannot start until the user delivers the Claude-Design HTML/CSS spec into this session. The branch can make progress on Phase 104 in the meantime.
- **JaCoCo baseline to preserve** — v1.13 closed at **89.43 %**; both v1.14 phases must hold ≥ 89.43 % (Phase 104 adds a small regression IT → coverage neutral/positive; Phase 105 redesigns existing graphic service which is JaCoCo-excluded already per `pom.xml`, so the redesign itself is coverage-neutral but any non-graphic helper additions must come with tests).
- **`stash@{0}` content must be re-verified before Plan 104-01 executes** — The `v1.14-phase1-seed` stash exists on the user's local machine; the planner/executor for Phase 104 should restore + re-verify before turning it into a commit (`git stash show -p stash@{0}` then `git stash pop` on the milestone branch).
- **Phase 105 UI surface is `playwright-cli` mandatory** — Per CLAUDE.md "Development Approach", visual verification with `playwright-cli` (Desktop + Mobile) is required for the team-card redesign; screenshots go to `.screenshots/105-*/`.

### Baselines to Preserve

- JaCoCo line coverage: **≥ 89.43 %** (v1.13 baseline; v1.14 phases must maintain or improve; gate remains 82 % in pom.xml)
- Test count: **≥ 2393** (v1.13 baseline; Phase 104 adds 1 regression IT → 2394+, Phase 105 may add visual-snapshot/IT depending on handoff)
- `./mvnw verify -Pe2e` CI median (E2E step): **17:39 ± 20 %** (v1.12/v1.13 baseline; no expected regression — Phase 104 is a Spring-context IT only, Phase 105 is graphic-service redesign with `TeamCardService` JaCoCo-excluded)
- `BackupSchema.SCHEMA_VERSION`: **2** (v1.13 Phase 101; no v1.14 changes anticipated)
- `EXPORT_ORDER` size: **26 entities** (v1.13 Phase 101; no v1.14 schema changes anticipated)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0 on new HIGH/CRITICAL** (3-layer FP suppression invariant maintained)
- Flyway migrations: V1-V16 immutable; no new V17+ anticipated for v1.14
- Dev-profile seeder behaviour: `DevDataSeeder` + `TestDataService` continue to seed exactly as before for `@Profile("dev")` and `@Profile("dev,demo")` — Phase 104 only narrows `local` out, does NOT touch `dev` semantics

## Session Continuity

**Last session:** 2026-05-29T00:00:00.000Z

**Stopped at:** v1.14 ROADMAP.md created (2 phases, 4/4 REQ-IDs mapped)

**Next action:** `/gsd-discuss-phase 104` or `/gsd-plan-phase 104` for Data Safety Lockdown. Phase 105 remains blocked on external Claude-Design handoff — do NOT start discuss/plan/execute for Phase 105 until the user delivers the design spec into this session.

**Branch:** `gsd/v1.14-team-card-redesign` (off `origin/master` `b0b90696`, pom at `1.14.0-SNAPSHOT`)

## Operator Next Steps

- Start Phase 104 with `/gsd-discuss-phase 104` (or `/gsd-plan-phase 104` if no discussion needed — fix is well-scoped via the stashed diff).
- Deliver Claude-Design HTML/CSS handoff for Phase 105 when ready (paste into the session or save into the repo + reference the path).
