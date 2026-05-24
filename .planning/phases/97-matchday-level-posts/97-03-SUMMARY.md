---
phase: 97-matchday-level-posts
plan: 03
subsystem: discord-integration
tags: [discord, webhook, forum-thread, standings, phase-scoped, v14-migration, playwright-graphic]

requires:
  - phase: 96-discord-forum-thread-integration
    provides: standings forum-thread (season.discordStandingsThreadId) + auto-unarchive-before-post shared path + 7-arg postOrEdit with thread_id query param
  - phase: 95-discord-post-buttons
    provides: applyErrorFlash 5-permit category mapping + .discord-actions--posts cluster pattern + DiscordPostService.postOrEdit lookup/insert protocol
  - phase: 97-matchday-level-posts/01
    provides: MatchPreviewPreFlightResult DTO record (reused as generic pre-flight shape)
  - phase: 97-matchday-level-posts/02
    provides: AbstractGraphicService runtime pattern + JaCoCo exclusion convention for Playwright-dependent services

provides:
  - "Post Standings" per-phase button on /admin/seasons/{id}/edit (#discordIntegration card) covering REGULAR / REGULAR-GROUPS / PLAYOFF / PLACEMENT
  - StandingsGraphicService.generateStandingsBytes(Season, SeasonPhase) → List<byte[]> (1 PNG for non-GROUPS; N PNGs sorted by SeasonPhaseGroup.sortIndex ASC for GROUPS)
  - Dynamic 1920×1080 standings graphic with CTC logo + team logos + SeasonTeam primary-color strip + auto-scaling row/font/logo sizes (no overflow for 14+ teams)
  - Flyway V14 ADD COLUMN discord_post.phase_id UUID NULL + FK to season_phases ON DELETE SET NULL (H2 + MariaDB compatible)
  - DiscordPostRef.SeasonRef widened to (UUID seasonId, @Nullable UUID phaseId) with backward-compatible factory seasonRef(s) for pre-existing Phase 96 callsites + new seasonPhaseRef(s, p) for phase-scoped identity
  - StandingsService.hasNewerResultsSincePhaseScoped(seasonId, phaseId, since) for per-phase stale-detection signalling "Update Standings"
  - PostStandingsForm DTO (@NotNull UUID phaseId) for Mass-Assignment-safe form binding
  - Phase-selector <select> on the standings cluster, auto-hidden to <input type="hidden"> when season has exactly 1 phase

affects: [Phase 98 (operator runbook + milestone close — POST-08 row appears as the final discord-post-button in v1.13)]

tech-stack:
  added: []
  patterns:
    - "Phase-scoped identity-key via sealed-DTO widening: SeasonRef(UUID seasonId, @Nullable UUID phaseId) extends DiscordPostRef. Factory pair (seasonRef vs. seasonPhaseRef) keeps Phase 96 callsites untouched while enabling new per-phase identity in the postOrEdit lookup/insert switch."
    - "Dynamic Playwright graphic sizing for variable team-count: server-side computes rowHeightPx/fontSizePx/logoSizePx from `Math.max(min, Math.min(max, tableAvailablePx / rowCount))` and injects as Thymeleaf variables. Avoids CSS-side overflow heuristics; template stays declarative."
    - "Server-side phase-selector auto-collapse: when allPhases.size() == 1, controller injects the single phase id as `<input type='hidden'>` instead of a `<select>`; template branches on `data-testid` per-mode to allow E2E to assert dropdown visibility deterministically."
    - "List<byte[]> graphic return for fan-out posts: StandingsGraphicService.generateStandingsBytes returns List<byte[]> (1 element for non-GROUPS, N for GROUPS), and DiscordPostService.postStandings multiparts all attachments in a single Webhook message — Re-Post replaces all N attachments atomically via PATCH."

key-files:
  created:
    - src/main/resources/db/migration/V14__add_discord_post_phase_id.sql
    - src/main/java/org/ctc/admin/dto/PostStandingsForm.java
    - src/main/java/org/ctc/admin/service/StandingsGraphicService.java
    - src/main/resources/templates/admin/standings-render.html
    - src/test/java/org/ctc/admin/controller/SeasonControllerPostStandingsIT.java
    - src/test/java/org/ctc/admin/service/StandingsGraphicServiceContractTest.java
    - src/test/java/org/ctc/admin/service/StandingsGraphicPreviewTest.java
    - src/test/java/org/ctc/discord/dto/DiscordPostRefSeasonRefWidenedTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceStandingsIT.java
    - src/test/java/org/ctc/discord/service/DiscordPostV14MigrationIT.java
    - src/test/java/org/ctc/domain/service/StandingsServicePhaseScopedStaleDetectionIT.java
    - src/test/java/org/ctc/e2e/discord/matchday/SeasonFormStandingsButtonE2ETest.java
  modified:
    - src/main/java/org/ctc/admin/controller/SeasonController.java
    - src/main/java/org/ctc/discord/dto/DiscordPostRef.java
    - src/main/java/org/ctc/discord/model/DiscordPost.java
    - src/main/java/org/ctc/discord/repository/DiscordPostRepository.java
    - src/main/java/org/ctc/discord/service/DiscordPostService.java
    - src/main/java/org/ctc/domain/service/StandingsService.java
    - src/main/resources/templates/admin/season-form.html
    - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
    - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
    - pom.xml
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Widen SeasonRef sealed-permit (D-97-STA-4) instead of introducing a new SeasonPhaseRef permit. Reason: the four-permit sealed-switch in postOrEdit would have grown to five, every Phase 96 callsite of DiscordPostRef.season(s) would have needed to choose one of two refs, and the Phase 95 IT scaffolding would have re-broken. A nullable phaseId on the existing record + a factory pair leaves the contract additive."
  - "List<byte[]> over single byte[] for the graphic API (D-97-STA-3). REGULAR-GROUPS layouts in IRL 2023 produce 2 group standings PNGs; passing a list keeps the multipart construction symmetric and lets Re-Post atomically replace all N attachments via the existing postOrEdit's multipart PATCH path — no per-group post rows."
  - "Per-phase identity-key (V14 NULL-able phase_id) rather than per-(season, phaseType) or per-(season, phaseLabel). NULL-able to preserve historical Phase 96 non-phase-scoped discord_post rows (re-import path stays intact); FK to season_phases.id with ON DELETE SET NULL so phase delete does not cascade into Discord history loss."
  - "Dynamic standings graphic sizing computed server-side, not via CSS auto-flow. Three iterations were needed to make 14 teams fit cleanly: (1) initial 860px table area + 12 visible rows, (2) shrink to 780px + visible row stretch, (3) FINAL — removed redundant `.short` subtitle row, dropped line-height to 1, formulas: rowHeightPx = clamp(36, tableAvailablePx/rowCount, 80), fontSizePx = clamp(18, rowHeightPx - 10, 36), logoSizePx = clamp(28, rowHeightPx - 8, 60). Color-strip uses SeasonTeam.getEffectivePrimaryColor() (season override → team default fallback)."
  - "Phase-selector dropdown auto-collapses to hidden input on single-phase seasons. Reason: a 1-option <select> is operator friction; the hidden-input mode keeps the form-submit semantics identical (still binds to phaseId) and a separate data-testid (`post-standings-phase-hidden` vs. `post-standings-phase-select`) lets E2E deterministically assert which mode rendered."
  - "Auto-edit hook deferred / not in scope for POST-08. Re-Post is operator-triggered via the existing button, supplemented by the stale-detection 'Update Standings' label flip — same UX as POST-04 (Match Results) and POST-07a (Match Day Results). A future @TransactionalEventListener AFTER_COMMIT auto-edit on scoringService.aggregateMatchScores would be a separate plan once we have telemetry on operator re-post latency."

patterns-established:
  - "Sealed-DTO widening with @Nullable secondary fields + factory-pair for backward compatibility — see DiscordPostRef.SeasonRef + seasonRef/seasonPhaseRef. Pattern reusable when an existing discord_post-style polymorphic identity needs an additional sub-scope without introducing a new permit."
  - "Server-side computed dimensions for Playwright graphics: when a single template must render N rows of variable team-count, compute pixel sizes in the service (clamp formula on tableAvailablePx) and inject as Thymeleaf variables. Avoids CSS overflow + scales deterministically up to 14+ teams in a fixed 1920×1080 frame."
  - "Iterative graphic-design loop with operator visual approval (per CLAUDE.md feedback_graphic_design_iteration + feedback_graphic_pixel_positioning): build initial version, capture .screenshots/, present via AskUserQuestion, iterate. The StandingsGraphicPreviewTest manual @SpringBootTest @Disabled-by-default + dev profile is the reusable harness for this loop."

requirements-completed: [POST-08]

duration: 215min
completed: 2026-05-24
---

# Phase 97 Plan 03: Standings + V14 Migration + Grafik-Loop Summary

**POST-08 ships the per-phase "Post Standings" button on Season-Edit, backed by a phase-scoped V14 identity-key migration, a sealed-DTO widening that keeps Phase 96 callsites untouched, a dynamic-sizing Playwright standings graphic that handles 14+ teams without overflow, and an iterative visual-approval design loop.**

## Scope Implemented

### Phase-Scoped Identity (V14 + SeasonRef Widening)

- `V14__add_discord_post_phase_id.sql` adds `phase_id UUID NULL` with FK to `season_phases(id) ON DELETE SET NULL`. Migration is H2 + MariaDB compatible per the V12 pattern.
- `DiscordPostRef.SeasonRef` widened from `SeasonRef(UUID id)` to `SeasonRef(UUID seasonId, @Nullable UUID phaseId)` per D-97-STA-4.
- Factory pair: `DiscordPostRef.seasonRef(season)` (Phase 96 legacy callsites, phaseId=null) and `DiscordPostRef.seasonPhaseRef(season, phase)` (new phase-scoped identity).
- `DiscordPostService.postOrEdit` sealed-switch updated to dispatch lookup on `s.phaseId() != null` and write `row.setPhaseId(s.phaseId())` in the insert branch.
- `DiscordPostRepository.findByChannelIdAndPostTypeAndSeasonIdAndPhaseId` derived query for phase-scoped lookup.

### StandingsGraphicService (Dynamic 1920×1080)

- Extends `AbstractGraphicService` per PATTERNS.md Pattern Map line 24. Playwright runtime → JaCoCo-excluded (added to the pom.xml exclude list).
- `generateStandingsBytes(season, phase) → List<byte[]>`: 1 PNG for REGULAR-non-GROUPS / PLAYOFF / PLACEMENT, N PNGs sorted by `SeasonPhaseGroup.sortIndex` ASC for REGULAR-with-GROUPS.
- Template `templates/admin/standings-render.html`: 1920×1080 dark theme, `@font-face` from `fontBase64`, `ctcLogoBase64` header right, team-logo + `SeasonTeam.getEffectivePrimaryColor()` color-strip + team name per row.
- Dynamic sizing (server-side compute, injected as Thymeleaf vars):
  - `tableAvailablePx = 1080 - 240`
  - `rowHeightPx = clamp(36, tableAvailablePx / rowCount, 80)`
  - `fontSizePx = clamp(18, rowHeightPx - 10, 36)`
  - `logoSizePx = clamp(28, rowHeightPx - 8, 60)`
  - `posFontSizePx = min(44, fontSizePx + 4)`
- Verified visually with IRL 2026 (14 teams, league layout) + IRL 2023 (6 teams × 2 groups, groups layout) — no overflow.

### Controller + Form + Template

- `PostStandingsForm` DTO with `@NotNull UUID phaseId` — Mass-Assignment-safe binding (no SeasonPhase entity in controller).
- `SeasonController.postStandings(@Valid @ModelAttribute PostStandingsForm form)` POST endpoint + applyErrorFlash 5-permit category mapping reused.
- `SeasonController.edit()` enriched with model attributes: `allPhases`, per-phase `discordStandingsPostExists`, per-phase `discordStandingsStale`, `discordIntegrationActive` predicate.
- `templates/admin/season-form.html`: phase-selector `<select data-testid="post-standings-phase-select">` (multi-phase) auto-collapses to `<input type="hidden" data-testid="post-standings-phase-hidden">` (single-phase). Button label flips between "Post Standings" / "Re-Post Standings" / "Update Standings" based on post-existence + stale flag.

### Per-Phase Stale Detection

- `StandingsService.hasNewerResultsSincePhaseScoped(seasonId, phaseId, since)` `@Transactional(readOnly=true)`.
- Union of `RaceResultRepository.findByRaceMatchdayPhaseId` + `findByRacePlayoffMatchupRoundPlayoffPhaseId` — covers REGULAR / PLAYOFF / PLACEMENT race-result attribution.

## Tasks Delivered

| Task | Description | Atomic Commits |
|------|-------------|----------------|
| 1 | V14 migration + DiscordPost.phaseId field | `3e4a3e10` |
| 2 | SeasonRef widening + DiscordPostService dispatch + repo query | `3e4a3e10` |
| 3 | StandingsGraphicService + standings-render template | `b3e1462f` |
| 4 | PostStandingsForm + SeasonController endpoint + season-form template + StandingsService stale-detection | `b3e1462f` |
| 5 | Iterative graphic-design loop with user visual approval (3 iterations) + dynamic sizing | `5e33368c` |
| 6 | REQUIREMENTS.md POST-08 refinement + this SUMMARY | (this commit) |

## Test Coverage

- `DiscordPostV14MigrationIT` — V14 migration applies cleanly + FK + index assertions on H2 and validates JPA mapping.
- `DiscordPostRefSeasonRefWidenedTest` — sealed-permit contract: factory-pair semantics, nullable phaseId, applyTo behavior.
- `StandingsGraphicServiceContractTest` — generateStandingsBytes returns List<byte[]> with expected cardinality per phase layout; Playwright runtime smoke (passes when chromium installed, otherwise skipped via JaCoCo-excluded class).
- `DiscordPostServiceStandingsIT` — WireMock-backed multipart-POST + Re-Post PATCH + thread_id query-param + phase-scoped identity lookup.
- `StandingsServicePhaseScopedStaleDetectionIT` — REGULAR + PLAYOFF + PLACEMENT race-result attribution; pre-existing-post + newer-result + no-newer-result branches.
- `SeasonControllerPostStandingsIT` — POST endpoint flash-badge mapping for all 5 permit categories + form-DTO validation + edit() model enrichment.
- `SeasonFormStandingsButtonE2ETest` — 6 Playwright E2E scenarios: phase-select visible (multi-phase) / hidden-input mode (single-phase) / cluster hidden when no thread / cluster hidden when no webhook / phase-status list shows never-posted / mobile viewport renders button.
- `StandingsGraphicPreviewTest` (manual, `@Disabled` by default) — reusable harness for re-generating preview PNGs via `./mvnw -Dtest=StandingsGraphicPreviewTest test` after removing `@Disabled`.

## Visual Iterations (User Approved)

| Iteration | Issue | Fix |
|-----------|-------|-----|
| 1 | Initial 14-team layout — only 12 visible (overflow) | tableAvailablePx 860 → 780, rowHeight max 70 |
| 2 | Still 12/14 visible — `.short` subtitle row stretched rows beyond rowHeightPx | Removed `.short` element, set `line-height: 1` globally |
| 3 | Color strip purpose unclear to operator | Confirmed as SeasonTeam.primaryColor (with team.primaryColor fallback via getEffectivePrimaryColor) — user approved |

Final layout: CTC logo top-right, `CTC {year} — STANDINGS` h1 + phase label h2 top-left, dynamic-sized rows with color-strip + team-logo + name + W/D/L/PTS columns. Approved 2026-05-24.

## Deviations from Plan

- None in scope. Tasks 1+2 were merged into one atomic commit (`3e4a3e10`) because they share the same V14-driven contract change and split would have left an intermediate compile-broken state. Tasks 3+4 merged into `b3e1462f` for the same reason (template + service + controller are co-dependent for the endpoint to flash-badge meaningfully).
- Visual-design loop took 3 iterations (planned: 1–2). The extra iteration was the `.short`-row-stretch root-cause analysis; no plan re-scope needed.
- `.screenshots/` committed-artefacts cleanup (`d7b32bd2`) was an out-of-scope but per-user-directive hygiene commit during the design loop — 10 stale PNGs from Phase 74-09 and Phase 77 were removed from tracking (folder remains `.gitignore`d).

## Known Follow-ups (NOT in scope, captured for Phase 98 or v1.14)

- Auto-edit hook on `scoringService.aggregateMatchScores`: future plan once we have telemetry on operator re-post latency. Same shape as POST-06's `MatchPreviewFieldsChangedEvent` AFTER_COMMIT listener.
- `postRaceResultToForumThread` (Phase 96) still has the latent IOException catch-all that re-categorises 4xx as TRANSIENT (flagged in 97-02 SUMMARY) — not fixed in 97-03 to keep blast-radius minimal.
- **`SeasonPhaseRestorer.java:79` latent null-safety bug**: `UUID.fromString(row.get("raceScoring").asText())` does not handle null FKs (raceScoring + matchScoring are `@JoinColumn` without `nullable=false`). The Plan 97-03 E2E test `SeasonFormStandingsButtonE2ETest` originally committed a PLAYOFF SeasonPhase without raceScoring (since E2E tests are not @Transactional), which polluted the H2 dev fixture for the later-running `BackupImportE2ETest` and tripped the restorer. **Fixed in this plan** by setting `raceScoring + matchScoring` from the REGULAR phase on the test's PLAYOFF phase (`SeasonFormStandingsButtonE2ETest.java:67-69`). The underlying restorer null-handling bug is left as-is for a future hardening sweep — defensive `nullableUuid()` helper analogous to existing `nullableString/Date/Int` helpers in the same file.
