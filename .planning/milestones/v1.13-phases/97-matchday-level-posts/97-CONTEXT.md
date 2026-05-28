# Phase 97: Matchday-Level Posts - Context

**Gathered:** 2026-05-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 97 closes the matchday-level + season-level Discord post matrix on
top of the Phase 95 (match-channel posts) + Phase 96 (provisional + forum-
thread) foundation. Three post-types from `REQUIREMENTS.md`:

- **POST-06 (Match Preview Announcement)** — per-match structured Markdown
  post to the announcement-webhook, including Settings.png + Lineups.png
  attachments and an auto-emoji-resolved "Game On!" line. Operator-triggered
  per match (NOT a batch).
- **POST-07 (Matchday Summary in race-results Forum-Thread)** — split into
  two independent buttons on Matchday-Detail:
  - **POST-07a Match Day Results** — `MatchdayResultsGraphicService` PNG.
  - **POST-07b Power Rankings** — `PowerRankingsGraphicService` PNG, sourced
    from operator-maintained `SeasonTeam.rating` order.
- **POST-08 (Standings to Forum-Thread)** — new `StandingsGraphicService`
  PNG posted to `season.discordStandingsThreadId`. Operator-triggered with
  stale-detection signal when newer race-results exist.

Plus the matching auto-edit hook for MATCH_PREVIEW (`MatchService.save`
Pre/Post-Diff on `streamLink`/`discordTeaser` → `@TransactionalEventListener
AFTER_COMMIT` → Webhook-PATCH on existing MATCH_PREVIEW row).

After Phase 97 closes, ALL 8 production post-types from the design spec
exist with consistent operator UX, leaving Phase 98 for Polish + E2E +
DOCS-02/03 + milestone close.

**Out of scope (revised from REQUIREMENTS.md):**

- **`MATCHDAY_PAIRINGS` Discord-Post-Type is dropped.** REQUIREMENTS.md
  POST-06 originally described two post-types (a separate "Matchday
  Pairings" overview graphic + per-match "Match Previews"); per user
  direction 2026-05-23 the production workflow uses only the per-match
  Preview Announcement (Settings + Lineups embedded in the same message).
  The `MATCHDAY_PAIRINGS` enum value declared in `DiscordPostType` (Phase
  95) stays in code but receives no implementation — kept for potential
  v1.14 use.
- **Batch posting / confirmation modal for Match Previews.** The approved
  97-UI-SPEC.md described POST-06b as a single batch button with a
  confirmation modal that posts N preview messages in one click. Per user
  direction 2026-05-23 this is replaced by a per-match operator-triggered
  button on Match-Detail. UI-SPEC must be revised in Plan 97-01.

</domain>

<spec_lock>
## Requirements (locked via UI-SPEC.md — partial)

**97-UI-SPEC.md (approved 2026-05-23)** locks button colors, typography
spacing, modal classes, error-badge categories, server-model contract
attribute names, and copywriting strings. **Requires revision in Plan
97-01** for the two scope changes captured under "Phase Boundary — Out of
scope":

1. Drop POST-06a "Post Matchday Pairings" button + cluster entry on
   matchday-detail.
2. Replace POST-06b batch-with-modal on matchday-detail with a per-match
   "Post Match Preview" button on `/admin/matches/{id}` Match-Detail page
   inside the existing `.discord-actions--posts` cluster (analog
   TEAM_CARDS / SETTINGS / LINEUPS / SCHEDULE / MATCH_RESULTS).

POST-07 (revised to 2 buttons) and POST-08 placement remain as the
UI-SPEC locked them (Matchday-Detail Discord Actions card + season-form
`#discordIntegration` card).

REQUIREMENTS.md POST-06 + POST-07 entries also need revision in Plan 97-01
/ Plan 97-02 to reflect the simplified scope.

**Downstream agents MUST read both `97-UI-SPEC.md` AND this CONTEXT.md** —
where they conflict, CONTEXT.md takes precedence (later sign-off).

</spec_lock>

<decisions>
## Implementation Decisions

### Q-97-01 — MATCH_PREVIEW Auto-Edit Hook (Area 1)

- **D-97-PREV-1: Mirror Phase 95 D-95-04 pattern verbatim.** NEW
  `MatchPreviewFieldsChangedEvent(UUID matchId)` record in
  `org.ctc.discord.event`. `MatchService.save(MatchForm)` performs
  Pre/Post-Diff on `streamLink` + `discordTeaser` (Objects.equals
  null-safe); on diff publishes the event via `ApplicationEventPublisher`.
  `DiscordAutoPostListener.onMatchPreviewFieldsChanged` handles it under
  `@TransactionalEventListener(phase = AFTER_COMMIT)` +
  `@Transactional(propagation = REQUIRES_NEW)` (exact pattern from existing
  `onScheduleFieldsChanged` in `DiscordAutoPostListener.java:49`). Calls
  `DiscordPostService.autoEditMatchPreviewIfNeeded(Match)`, which performs
  the row-existence lookup + Webhook-PATCH or no-op. **Initial post stays
  operator-button-triggered.** REJECTED: generalising
  `MatchScheduleFieldsChangedEvent` into a multi-field event (refactor
  blast-radius on Phase 95 tests for no semantic gain); inline call from
  `MatchService` to `DiscordPostService` (couples domain service to
  Discord, Phase 95 D-95-04 already REJECTED this).

- **D-97-PREV-1a: Only `streamLink` + `discordTeaser` trigger Auto-Edit.**
  Race.dateTime changes do NOT trigger MATCH_PREVIEW auto-edit even though
  the rendered "Date" bullet in the Markdown depends on it — analog to
  Phase 95 D-95-04a where Race.dateTime changes do not trigger SCHEDULE
  auto-edit either. Race rescheduling after announcement is rare in the
  CTC workflow; operator can manually Re-Post via the Match-Detail button.
  Keeps `RaceService` Discord-free in v1.13 — `MatchService.save` remains
  the sole Discord hook surface in the domain layer. DEFERRED: Race.dateTime
  trigger as a v1.14 / DISC-FUTURE candidate.

- **D-97-PREV-1b: Per-match 1 PATCH.** Each `MatchService.save` that
  publishes the event triggers exactly one Webhook-PATCH on the affected
  match's existing MATCH_PREVIEW `discord_post` row. No batching across
  matches (each match-form save is a single-match transaction anyway).

### Q-97-02 — POST-06 Match Preview Announcement Surface + Pre-Flight (Area 2)

- **D-97-PREV-2: POST-06 simplified to a single per-match button on
  Match-Detail.** Per user direction 2026-05-23: "Die Preview
  Announcements werden einzeln und manuell vom Operator gepostet. Kein
  Batch." `/admin/matches/{id}` template gets an additional button in the
  existing `.discord-actions--posts` cluster (next to the 5 Phase-95 post
  buttons): **Post Match Preview** (initial) / **Re-Post Match Preview**
  (after row exists). Target: `discordGlobalConfig.announcementWebhookUrl`
  (NOT the per-match channel webhook). One `MATCH_PREVIEW` `discord_post`
  row per match with `match_id` FK. REJECTED: Matchday-Detail per-match-row
  table (more UI code, operator already navigates to Match-Detail to edit
  Teaser/StreamLink); duplicate buttons on both pages (Phase 95 REJECTED
  duplicate-button-pattern); batch posting (user explicitly REJECTED).

- **D-97-PREV-2a: One Discord-message per match = Markdown + 2
  attachments.** Multipart Webhook-POST with 2 PNG attachments:
  `settings-md{N}.png` + `lineups-md{N}.png`. The Markdown body follows
  the design-spec § 4.5 layout verified against the live screenshot from
  the operator's current Discord (Match Day 4 DTR vs. TNR B reference):

  ```
  # {season.name}
  ## Match Day {matchday.number}
  ### {teamA.shortName} vs. {teamB.shortName}{teamB.subTeamSuffix}

  {match.discordTeaser}                   ← user-provided Markdown freeform

  - Date: <t:N:F>                         ← from first race time via DiscordTimestamps
  - Stream: {match.streamLink ?: "TBA"}   ← TBA fallback per D-97-PREV-2b

  Game On! {emoji(teamA.shortName)} {emoji(config.vsEmojiName)} {emoji(teamB.shortName)}
  ```

  Attachments come from existing Phase-95 `SettingsGraphicService.generate
  SettingsBytes(...)` + `LineupGraphicService.generateLineupBytes(...)`
  (same byte[] paths Phase 95 POST-03 already uses for the match-channel).
  PHP-style filename templating uses `match.matchday.number`.

- **D-97-PREV-2b: H3 header uses team `shortName` + optional sub-team
  suffix.** Per user direction 2026-05-23 (Area 2.5): "team.shortName +
  optional sub-team-suffix (e.g. 'B')". Discord screenshot reference shows
  `### DTR vs. TNR B` — `DTR` / `TNR` are shortNames, `B` is the sub-team
  letter. Implementation: `team.getShortName()` + (if `team.hasSubTeams()`
  or `team.getParent() != null` resolve the visible suffix per existing
  CTC convention — Planner inspects `Team` entity to pick the cleanest
  expression). REJECTED: `team.name` (UI-SPEC default — too verbose for
  Discord-mobile narrow width); shortName only without suffix
  (ambiguous when a parent team has multiple sub-teams in the same
  matchday).

- **D-97-PREV-2c: Pre-flight predicates allow null `streamLink`.** Button
  enabled when: `match.discordTeaser != null` AND Settings data available
  (existing Phase-95 `matchHasCompleteSettings`) AND Lineups data
  available (existing Phase-95 `matchHasCompleteLineups`) AND `≥ 1 Race`
  in the match has `dateTime` set AND `discordAnnouncementsConfigured`.
  **`streamLink` is OPTIONAL** per user direction 2026-05-23 (Area 2.8):
  "Streamlink kann null/leer sein (soll dann als TBA ausgegeben werden).
  Link kann häufig erst hinterlegt werden, wenn der Stream selbst live
  ist. Announcement muss aber schon davor raus." Null `streamLink` renders
  literal `Stream: TBA` in the Markdown. The Auto-Edit hook from D-97-PREV-1
  will PATCH the post when the operator later adds the streamLink.

  Disabled-tooltip strings (one per failing predicate, evaluated top-down):
  - `Add a teaser text on Match-Edit first` (no `discordTeaser`)
  - `Configure Race Settings for all races first` (no settings)
  - `Configure Race Lineups for all races first` (no lineups)
  - `Set Race date+time first` (no `Race.dateTime` on any race)
  - `Configure announcement-webhook in Discord settings` (no
    `announcementWebhookUrl`)

  Service-Layer pre-flight method: `MatchService.canPostMatchPreview(Match)
  → MatchPreviewPreFlightResult` (record with boolean + Optional<String>
  disabledReason). Controller passes `matchPreviewPreFlight` ModelAttribute
  to Match-Detail template.

### Q-97-03 — POST-07 Matchday Summary in race-results Forum-Thread (Area 3)

- **D-97-MD-1: POST-07 split into 2 independent buttons.** Per user
  direction 2026-05-23 (Area 3.1): "Match Day Results und Power Rankings
  sind unabhängig voneinander - 2 separate Posts, zeitlich entkoppelt.
  Power Rankings kommen zeitlich nach Results. Daher auch bitte 2 eigene
  Buttons dafür." Both live on `/admin/matchdays/{id}` in a NEW
  `<div class="card">` titled "Discord Actions" inside a single
  `<div class="discord-actions discord-actions--posts">` cluster. Both
  target the season's race-results-forum-thread via webhook + `?thread_id=`
  (the Phase 96 D-96-FOR-3a method-overload pattern is reused verbatim).

  **POST-07a "Post Match Day Results"**:
  - PNG: existing `MatchdayResultsGraphicService.generateMatchdayResults
    Bytes(matchday) → byte[]` (Planner verifies whether the byte[]
    variant exists or needs adding analog to Phase 96 D-96-FOR-3d).
  - `discord_post.type = MATCHDAY_OVERVIEW`, `matchday_id` FK.
  - Pre-Flight: `allMatchesFinal` AND `season.discordRaceResultsThreadId !=
    null` AND `globalConfig.raceResultsForumWebhookUrl != null`. 3 distinct
    disabled-tooltip strings (analog Phase 96 D-96-FOR-3c).
  - Stale (Area 3.3): `≥ 1 RaceResult.updatedAt` in matchday >
    `matchdayOverviewPost.updatedAt`. Service-Query (no schema change).
    Button label flips Re-Post → "Update Match Day Results" (yellow-signal,
    Phase 95 Match Results pattern).

  **POST-07b "Post Power Rankings"**:
  - PNG: existing `PowerRankingsGraphicService.generateRankings(year,
    number, subtitle, teamIds) → byte[]` already returns byte[] (verified
    in `PowerRankingsController.download`).
  - `discord_post.type = POWER_RANKINGS`, `matchday_id` FK.
  - Pre-Flight (Area 3.5): per user direction 2026-05-23 "Die Ratings der
    Teams werden nach dem Matchday vom Operator manuell aktualisiert."
    LOOSER than POST-07a: `season.discordRaceResultsThreadId != null` AND
    `globalConfig.raceResultsForumWebhookUrl != null`. **NO
    `allMatchesFinal` gate** — Power Rankings reflect operator-curated
    `SeasonTeam.rating` order, not auto-computed standings; operator
    decides when the ratings are publish-ready (typically after manually
    updating `SeasonTeam.rating` on `/admin/tools/power-rankings`).
  - Stale: `MAX(SeasonTeam.updatedAt WHERE season_id = ?)` >
    `powerRankingsPost.updatedAt`. Operator-trigger only; no auto-edit
    hook (rating edits in the tool page would otherwise spam Discord).
  - **Subtitle + teamIds-order persistence:** the operator currently
    passes these as form params to `/admin/tools/power-rankings/download`.
    For POST-07b the operator's source of truth is `SeasonTeam.rating`
    desc order with default `subtitle = "Match Day {N}"` (computed from
    the matchday). Planner-Discretion whether to (a) snapshot the order
    into a new `discord_post.payload_json` column for exact reproducible
    Re-Post or (b) always regenerate from current `SeasonTeam.rating` at
    Re-Post time. Recommended (b) — simpler, matches operator mental
    model "Re-Post = reflect current ratings."

- **D-97-MD-2: Single shared Discord Actions card on Matchday-Detail.**
  Per user direction 2026-05-23 (Area 3.4): "2 separate Buttons im selben
  .discord-actions--posts Cluster, 2 distinct types." Both buttons sit
  side-by-side in one Card section. Mobile (`max-width: 640px`) inherits
  the existing `.discord-actions` flex-column responsive rule (admin.css
  221–228) — no new CSS needed.

- **D-97-MD-3: Separate `discord_post` rows, separate stale-detection,
  separate Re-Post.** Each button independently transitions Initial →
  Posted → Stale → Re-Post. The two posts land sequentially in the same
  forum-thread (operator clicks both manually with whatever time gap
  works for them — typically Match Day Results first, Power Rankings
  later when ratings are updated).

### Q-97-04 — POST-08 Standings on Season-Form (Area 4)

- **D-97-STA-1: New `StandingsGraphicService` (Playwright-based, iterative
  layout via playwright-cli).** No existing `StandingsGraphicService` in
  `org.ctc.admin.service`; only `StandingsViewService` (read-side) +
  `StandingsPageGenerator` (sitegen HTML) + `templates/admin/standings.html`
  (admin page) + `templates/site/standings.html` exist. Plan 97-03 creates
  `StandingsGraphicService` with `generateStandingsBytes(Season season,
  SeasonPhase phase) → List<byte[]>` (returns 1 or N PNGs depending on
  phase layout — see D-97-STA-3 below). Playwright runtime → excluded
  from JaCoCo per CLAUDE.md "Excluded from coverage". Render-template:
  NEW `templates/admin/standings-render.html` (Playwright-input variant).
  **Iterative design loop per [[feedback-graphic-pixel-positioning]] +
  [[feedback-graphic-design-iteration]]** (Phase 96 GRAFX-01 pattern):
  small commits, operator visual-approval per wave-pause, no autonomous
  design decisions. Starting point: existing `templates/admin/standings.
  html` data shape (Position | Team | Played | W-D-L | Points). **Team
  logo MUST be integrated** (per user direction 2026-05-23, Area 6.2) —
  consistent with the visual family of `MatchdayResultsGraphic` +
  `PowerRankingsGraphic` (both already render team logos).

- **D-97-STA-2: Button placement on `season-form.html`
  `#discordIntegration` card (UI-SPEC verbatim) + phase-selector
  dropdown.** Per UI-SPEC § "POST-08 button on `templates/admin/season-
  form.html`" and user direction 2026-05-23 (Area 4.2): the button is
  appended to the BOTTOM of the existing `#discordIntegration` card
  created by Phase 96 FORUM-01. Visibility predicate (per UI-SPEC):
  `canPostStandings = seasonForm.id != null && season.discordStandings
  ThreadId != null && globalConfig.standingsForumWebhookUrl != null`.
  **Adjacent UI element (NEW vs UI-SPEC):** a `<select name="phaseId">`
  dropdown listing the season's existing `SeasonPhase` entries
  (`REGULAR` / `PLAYOFF` / `PLACEMENT` — only the ones that exist on the
  season). Operator selects the target phase before clicking. If the
  season has exactly one phase, the select is hidden and the phase is
  auto-resolved server-side. One `STANDINGS` `discord_post` row per
  `(season_id, phase_id)` pair (see D-97-STA-3 for schema implications).

- **D-97-STA-3: Standings PNG granularity per phase layout** (per user
  direction 2026-05-23, Area 6.1):

  | Phase | PhaseLayout | PNG count per click | Discord-message shape |
  |-------|-------------|---------------------|-----------------------|
  | REGULAR | non-GROUPS | 1 PNG | Single attachment |
  | REGULAR | GROUPS | N PNGs (1 per `SeasonPhaseGroup`) | **Multipart with N attachments in ONE message** |
  | PLAYOFF | non-GROUPS (always) | 1 PNG | Single attachment |
  | PLACEMENT | non-GROUPS (always) | 1 PNG | Single attachment |

  All variants land as ONE Discord-Webhook-POST per click (multipart when
  N > 1, analog Phase 96 `ProvisionalScoresGraphicService`). ONE
  `STANDINGS` `discord_post` row per `(season_id, phase_id)`. Re-Post
  replaces all N attachments atomically. PNG filenames: `standings-
  {phaseType-lower}.png` (single) or `standings-{phaseType-lower}-
  {groupName-slug}.png` (multipart, one per group).

- **D-97-STA-4: V14 Flyway migration for `discord_post.phase_id` FK
  column.** Per user direction 2026-05-23 (Area 6.3, Option A — payload_
  json) — implementation pivots to **dedicated FK column** for
  consistency with existing polymorphic FK pattern (match_id / matchday_
  id / race_id / season_id all in V12 from Phase 95). New `V14__add_
  discord_post_phase_id.sql`:

  ```sql
  ALTER TABLE discord_post ADD COLUMN phase_id UUID NULL;
  ALTER TABLE discord_post ADD CONSTRAINT fk_discord_post_phase
      FOREIGN KEY (phase_id) REFERENCES season_phases(id) ON DELETE SET NULL;
  CREATE INDEX idx_discord_post_phase_id ON discord_post (phase_id);
  ```

  H2 + MariaDB compatible. Identity-key for STANDINGS:
  `findByChannelIdAndPostTypeAndSeasonIdAndPhaseId(channelId, STANDINGS,
  seasonId, phaseId)`. Existing repository queries for other post-types
  (which leave `phase_id NULL`) are unaffected. **`DiscordPostRef.
  SeasonRef` (Phase 96 D-96-FOR-3b) is EXTENDED** to carry an optional
  `phaseId`: either widen the record to `SeasonRef(UUID seasonId, UUID
  phaseId)` (breaking — Phase 96 callers must update; recommended) or
  add a parallel `SeasonPhaseRef(UUID seasonId, UUID phaseId)` permit
  to the sealed-hierarchy. Planner-Discretion (recommendation: widen
  `SeasonRef` since Phase 96 had only one callsite — `FORUM-02` race-
  result-post — and that callsite always passes `phaseId = null`).

  REJECTED: payload_json TEXT column (less queryable, requires JSON
  parsing in repository, inconsistent with existing FK pattern); always-
  current-state-without-discriminator (operator can't have N STANDINGS
  posts per season — one per phase — without a discriminator); new
  `SeasonPhaseRef` parallel permit + `SeasonRef` untouched (overcomplete,
  duplicates lookup-dispatch in `DiscordPostService.postOrEdit`
  sealed-switch).

- **D-97-STA-5: Stale-detection per (season, phase) via Service-Query
  MAX(RaceResult.updatedAt in phase) > standingsPost.updatedAt.** Per
  user direction 2026-05-23 (Area 4.1, Option A) — REFINED to be
  phase-scoped after D-97-STA-3. `StandingsService.hasNewerResultsSince
  PhaseScoped(seasonId, phaseId, since) → boolean`. Surfaced as
  `standingsStaleByPhase: Map<UUID, Boolean>` ModelAttribute on
  `SeasonController.editSeason` (one boolean per phase the season has).
  Button label per selected phase flips Re-Post → "Update Standings"
  (yellow signal). REJECTED: cross-cutting `RaceResultService` hook
  bumping `season.updatedAt` (false-positives + cross-phase contamination);
  new column `season_phases.last_standings_relevant_change_at` (overkill).
  **NO auto-edit hook** — standings change too frequently to push to
  Discord automatically; operator-button-triggered remains the rule.

### Q-97-05 — Saison-Phasen-Awareness (Area 5)

**Background:** CTC seasons are split into `SeasonPhase` entities with
`PhaseType` (REGULAR / PLAYOFF / PLACEMENT) and optional `PhaseLayout`
(STANDARD / GROUPS). `Matchday` belongs to one `SeasonPhase` + optional
`SeasonPhaseGroup`. Existing graphic services are mixed: a single
`MatchdayResultsGraphicService` handles any matchday regardless of
phase, while `PlayoffRoundOverview/Results/Schedule GraphicService` are
round-orientated (round ≠ matchday). Phase 97 posts must work across all
phase types.

- **D-97-PHA-1: POST-07a uses `MatchdayResultsGraphicService` for ALL
  PhaseTypes uniformly.** Per user direction 2026-05-23 (Area 5.1): "Es
  müssen einfach nur alle Ergebnisse des Matchdays bzw. Playoff Runde
  dargestellt werden." No bracket-style rendering needed at the post
  point — the graphic just shows all results of the
  Matchday (or Playoff-Round-as-Matchday). The existing `Matchday
  ResultsGraphicService.generateResults(Matchday)` handles all three
  phase-types via its existing template (`templates/admin/matchday-
  results-render.html`). REJECTED: per-PhaseType service-dispatch
  (introducing PlayoffRoundResultsGraphicService dispatch logic for
  PLAYOFF-Matchdays would require resolving Matchday→PlayoffRound — they
  are not 1:1; out-of-scope complexity); REGULAR-only with Playoff
  deferred (CTC operator workflow includes Playoff/Placement matchdays
  too; would block Phase 97 from closing the matrix).

- **D-97-PHA-1a: POST-07a button label is constant ("Post Match Day
  Results" / "Re-Post Match Day Results" / "Update Match Day Results").**
  Same string for all phase-types. The Matchday's own label
  (`matchday.label`) supplies the context in the rendered PNG via
  `MatchdayResultsGraphicService` template variables — no per-PhaseType
  button-label switch needed.

- **D-97-PHA-2: POST-06 MATCH_PREVIEW Markdown H2 = `## {matchday.label}`
  direkt.** Per user direction 2026-05-23 (Area 5.2): operator-managed
  matchday label ("Match Day 4" / "Round of 16" / "Placement Match 1" /
  "Final" — any string). Maximal flexibility, no PhaseType switch. UI-SPEC's
  hardcoded `## Match Day {N}` is REVISED to `## {matchday.label}` in
  Plan 97-01. The live screenshot example `## Match Day 4` matches
  because the operator labelled the regular-season matchday that way.

- **D-97-PHA-3: POST-07b Power Rankings stays season-wide
  (phase-agnostic).** Per user direction 2026-05-23 (Area 5.3): existing
  `PowerRankingsGraphicService.loadTeamsForSeasonGroup(year, number)`
  loads `SeasonTeam.rating` across the entire season (all phases). No
  phase- or group-specific power-rankings variant in v1.13. REJECTED:
  per-phase rankings (existing service doesn't support; substantial new
  work); per-group rankings (existing service doesn't support).

- **D-97-PHA-4: POST-08 Standings IS phase-aware (per D-97-STA-3
  granularity table).** Standings ARE phase-bound — REGULAR-with-Groups
  has multi-group standings, Playoffs/Placement have standalone
  standings. Operator selects target phase via dropdown adjacent to the
  Post Standings button. See D-97-STA-2 + D-97-STA-3 + D-97-STA-4.

### Q-97-meta — Plan Decomposition & Sequencing (carry-forward D-96-05)

- **D-97-05: Three plans, sequenziell inline auf
  `gsd/v1.13-discord-integration`.** Mirrors Phase 92/93/94/95/96 D-05/07
  + design-spec § 5 (3 plans for Phase 97) + roadmap estimate. Wave-Pause
  + Mobile-Sweep after each plan ([[feedback-wave-pause]] +
  [[feedback-playwright-cli]]).
  - **Plan 97-01 — POST-06 (Match Preview Announcement + Auto-Edit
    Hook).** New: `MatchPreviewFieldsChangedEvent`, `MatchController.post
    MatchPreview` endpoint, `MatchService.canPostMatchPreview` pre-flight +
    Pre/Post-Diff publish, `DiscordAutoPostListener.onMatchPreviewFields
    Changed`, `DiscordPostService.postMatchPreview` +
    `autoEditMatchPreviewIfNeeded`, `Match-Detail` template button + pre-
    flight UI, **97-UI-SPEC.md revision** (drop POST-06a; move POST-06b
    cluster from matchday-detail to match-detail), **REQUIREMENTS.md
    POST-06 revision** (simplified scope). Tests:
    `DiscordPostServiceMatchPreviewIT` (post + auto-edit happy + 4-permit
    failure), `MatchServicePreviewDiffPublishTest` (Mockito-only),
    `MatchControllerPostMatchPreviewIT`, `MatchDetailPreviewButtonE2ETest`
    + Mobile-Sweep.
  - **Plan 97-02 — POST-07a + POST-07b (Match Day Results + Power
    Rankings, 2 buttons).** New: `MatchdayController.postMatchdayResults`
    + `postPowerRankings` endpoints, `DiscordPostService.postMatchday
    Results` + `postPowerRankings`, `DiscordPostRef.MatchdayRef` permit
    (extends Phase 96 D-96-FOR-3b sealed-hierarchy from MatchRef/RaceRef/
    SeasonRef → +MatchdayRef), repository query
    `findByThreadIdAndPostTypeAndMatchdayId`, `Matchday-Detail` template
    Discord Actions card with 2 buttons + pre-flight + stale-detection.
    `MatchdayResultsGraphicService` may need `generateMatchdayResults
    Bytes` byte[] variant (Planner verifies). **REQUIREMENTS.md POST-07
    revision** (split into 7a + 7b with distinct pre-flight). Tests:
    `DiscordPostServiceMatchdayResultsIT`,
    `DiscordPostServicePowerRankingsIT`,
    `MatchdayControllerPostEndpointsIT`,
    `MatchdayDetailDiscordActionsE2ETest` + Mobile-Sweep.
  - **Plan 97-03 — POST-08 (Standings + new StandingsGraphicService + V14
    phase_id FK migration + iterative graphic-design loop).** New:
    `StandingsGraphicService` (Playwright runtime, JaCoCo-excluded,
    returns `List<byte[]>` per D-97-STA-3 granularity table — 1 PNG for
    REGULAR-non-GROUPS / PLAYOFF / PLACEMENT; N PNGs for REGULAR-with-
    GROUPS), `templates/admin/standings-render.html` with team-logo
    integration (D-97-STA-1), `V14__add_discord_post_phase_id.sql`
    (D-97-STA-4), `SeasonController.postStandings` endpoint accepting
    `phaseId` form-param, `DiscordPostService.postStandings(season, phase)`
    using widened `SeasonRef(seasonId, phaseId)` (D-97-STA-4), `Discord
    PostRepository.findByChannelIdAndPostTypeAndSeasonIdAndPhaseId`,
    `StandingsService.hasNewerResultsSincePhaseScoped(seasonId, phaseId,
    since)` stale-query (D-97-STA-5), `season-form.html` button + phase-
    selector dropdown + per-phase stale-detection ModelAttribute
    (`standingsStaleByPhase: Map<UUID, Boolean>`). **Iterative graphic-
    design loop** (D-97-STA-1) via playwright-cli — small commits per
    layout step, wave-pause operator-approval per visual checkpoint.
    Tests: `DiscordPostServiceStandingsIT` (all 4 phase-layout
    combinations: REG-no-groups / REG-groups / PLAYOFF / PLACEMENT),
    `SeasonControllerPostStandingsIT` (phase-selector form-binding),
    `StandingsServicePhaseScopedStaleDetectionIT`,
    `DiscordPostRefSeasonRefWidenedTest` (Mockito-only, verifies Phase
    96 callsites still compile after `SeasonRef` widening),
    `SeasonFormStandingsButtonE2ETest` (multi-phase dropdown UX) +
    Mobile-Sweep + Visual-Snapshot.
  **Ende von Plan 97-03 = Phase-97-Close via `/gsd-validate-phase 97`.**
  Keine Worktrees, keine writing Subagents per
  [[feedback-inline-sequential-execution]].

### Q-97-meta-pr — PR Mechanics (carry-forward D-96-06)

- **D-97-06: Rolling v1.13 Milestone-PR weiter pflegen.** Phase 92 Plan
  92-01 opened the Draft PR; Phases 93/94/95/96 updated the body via
  `gh pr edit --body-file`. Phase 97 Plans 97-01..03 each append a new row
  to the rolling per-plan summary table (Plan # / REQ-ID / status /
  commit SHA / CI run URL). Squash-Subject locked:
  `feat(v1.13): discord integration & carry-forwards`
  ([[feedback-squash-merge-message]]). PR stays Draft through Phase 98
  close.

### Q-97-meta-gates — Quality Gates (carry-forward D-96-07)

- **D-97-07: Standard gates unchanged.**
  - JaCoCo line coverage ≥ Phase-96-end value (verify against
    96-VERIFICATION.md). Phase 97 adds ~30-50 new tests across 3 plans;
    `StandingsGraphicService` is excluded (Playwright runtime). Coverage
    MUST hold the Phase-96 baseline.
  - SpotBugs `BugInstance` count = 0 (blocking, verify-bound). New classes:
    `StandingsGraphicService`, `MatchPreviewFieldsChangedEvent`, service
    extensions. Lombok-config Phase-86 invariant unchanged.
  - CodeQL gate-step exit 0 on PR HEAD SHA. No new SSRF suppression
    needed (announcement-webhook + forum-webhook URLs already pass
    Phase-93 `DiscordHostValidator.requireAllowed`).
  - **`EXPORT_ORDER` stays 25** (Phase 97 adds NO new `org.ctc.discord.
    model` entity; new files are services/controllers/templates/events).
    `BackupSchema.SCHEMA_VERSION` stays **2** (no wire-contract change).
    `BackupSchemaGuardTest` unchanged.
  - **Flyway: V14 added by Plan 97-03 (`add_discord_post_phase_id`).**
    Verified: V12 (Phase 95) already provisioned all 4 polymorphic FKs
    (`match_id` / `matchday_id` / `race_id` / `season_id`) — so Plans
    97-01 + 97-02 require NO migration. V13 (Phase 96) added Season
    Forum-Thread columns. NEW V14 by Plan 97-03 adds `discord_post.
    phase_id` UUID FK + index (D-97-STA-4) to enable per-phase
    Standings posts with deterministic identity (`season_id, phase_id,
    post_type=STANDINGS`). Other post-types leave `phase_id NULL` —
    backward compatible. H2 + MariaDB compatibility verified.
  - `./mvnw verify -Pe2e` finishes within v1.12 CI E2E 17:39 ± 20%
    tolerance. Phase 97 adds ~8-12 WireMock-ITs + 3 Playwright E2E + 3
    Mobile-Sweep variants. Expected impact: < 90s total.

### Q-97-meta-tests — Test Discipline (carry-forward D-96-08/09)

- **D-97-08: Per-Plan Nyquist VALIDATION.md.** Plans 97-01..03 each ship
  with their own `VALIDATION.md`. Phase 97 self-validates via
  `/gsd-validate-phase 97` before `/gsd-execute-phase 98` start.

- **D-97-09: `@Tag` convention per CLAUDE.md.**
  - WireMock-backed ITs (`DiscordPostServiceMatchPreviewIT`,
    `DiscordPostServiceMatchdayResultsIT`,
    `DiscordPostServicePowerRankingsIT`, `DiscordPostServiceStandingsIT`,
    `MatchControllerPostMatchPreviewIT`,
    `MatchdayControllerPostEndpointsIT`,
    `SeasonControllerPostStandingsIT`, `StandingsServiceStaleDetectionIT`)
    → `@Tag("integration")`.
  - Mockito-only Unit-Tests (`MatchServicePreviewDiffPublishTest`,
    `StandingsServiceStaleDetectionTest` if pure-mock variant exists)
    → untagged (project convention).
  - Playwright E2E (`MatchDetailPreviewButtonE2ETest`,
    `MatchdayDetailDiscordActionsE2ETest`,
    `SeasonFormStandingsButtonE2ETest`) → `@Tag("e2e")`, package
    `org.ctc.e2e.discord.matchday` per `.planning/codebase/TESTING.md`
    § Test Categorization.

### Q-97-meta-uat — Live-UAT Strategy (analog Phase 96 D-96-10)

- **D-97-10: WireMock-IT-only Phase-97-Close; UAT-07 (Live Matchday-Posts
  Lifecycle) staged in STATE.md as Pending UAT for Operator-Run before
  Phase 98 start.** Phase 97 closes when `./mvnw verify -Pe2e` is green
  and all WireMock-ITs for POST-06 + POST-07a + POST-07b + POST-08 cover
  happy + failure paths. UAT-07 runs against operator test-guild with a
  live webhook + bot-token. Procedure (inline until Phase 98 DOCS-02
  fills `docs/operations/discord-integration.md`):
  1. `/admin/matches/{id}` for a match with full Settings + Lineups +
     Teaser + ≥1 Race-DateTime → "Post Match Preview" → multipart-POST
     with 2 PNGs lands in the announcement-webhook channel.
  2. Edit `match.streamLink` on Match-Edit → Save → MATCH_PREVIEW post
     auto-PATCHed within ~5s (Discord shows `(edited)` indicator).
  3. Edit `match.discordTeaser` → same auto-edit behaviour.
  4. `/admin/matchdays/{id}` with `allMatchesFinal == true` and the
     race-results forum-thread linked on the season → click "Post Match
     Day Results" → PNG lands in the forum-thread.
  5. Update one RaceResult → return to Matchday-Detail → "Post Match Day
     Results" button label flips to "Update Match Day Results"
     (yellow-signal) → click → Re-PATCH succeeds.
  6. `/admin/tools/power-rankings` → update `SeasonTeam.rating` for ≥1
     team → return to Matchday-Detail → click "Post Power Rankings" →
     PNG lands in the same forum-thread (sequentially after step 4).
     Power Rankings reflect the updated rating order.
  7. `/admin/seasons/{id}/edit` with standings-forum-thread linked →
     "Post Standings" → PNG lands in standings-forum-thread.
  8. Submit a new RaceResult anywhere in the season → return to
     season-form → "Post Standings" button label flips to "Update
     Standings" → click → Re-PATCH succeeds.
  9. `/admin/discord/posts` filtered by season → expect MATCH_PREVIEW (N
     matches) + MATCHDAY_OVERVIEW + POWER_RANKINGS + STANDINGS rows all
     visible with non-null `attachments_replaced_at` after Re-Post.

### Production Behavior Boundary

- **D-97-11: Production-code paths in Phase 97 limited to:**
  - `src/main/java/org/ctc/admin/service/StandingsGraphicService.java`
    (NEW, Plan 97-03).
  - `src/main/java/org/ctc/admin/service/MatchService.java` (Plan 97-01
    Pre/Post-Diff + publishEvent).
  - `src/main/java/org/ctc/admin/service/MatchdayResultsGraphicService.java`
    (Plan 97-02 — verify byte[] variant exists or add).
  - `src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java`
    (NEW, Plan 97-01).
  - `src/main/java/org/ctc/discord/service/DiscordPostService.java`
    (Plan 97-01 postMatchPreview + autoEditMatchPreviewIfNeeded; Plan
    97-02 postMatchdayResults + postPowerRankings + MatchdayRef sealed-
    switch; Plan 97-03 postStandings via SeasonRef).
  - `src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java`
    (Plan 97-01 — onMatchPreviewFieldsChanged listener).
  - `src/main/java/org/ctc/discord/event/MatchPreviewFieldsChangedEvent.java`
    (NEW, Plan 97-01).
  - `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` (Plan 97-02
    — add MatchdayRef permit + Helper-Factory).
  - `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java`
    (Plan 97-02 — derived query for MATCHDAY_OVERVIEW / POWER_RANKINGS
    by matchday_id).
  - `src/main/java/org/ctc/admin/controller/MatchController.java` (Plan
    97-01 — POST /admin/matches/{id}/post-match-preview).
  - `src/main/java/org/ctc/admin/controller/MatchdayController.java`
    (Plan 97-02 — POST /admin/matchdays/{id}/post-matchday-results +
    POST /admin/matchdays/{id}/post-power-rankings).
  - `src/main/java/org/ctc/admin/controller/SeasonController.java` (Plan
    97-03 — POST /admin/seasons/{id}/post-standings).
  - `src/main/java/org/ctc/domain/service/StandingsService.java` (Plan
    97-03 — `hasNewerResultsSince(seasonId, since)` derived query).
  - `src/main/resources/templates/admin/match-detail.html` (Plan 97-01
    — Post Match Preview button in .discord-actions--posts cluster +
    pre-flight UI).
  - `src/main/resources/templates/admin/matchday-detail.html` (Plan
    97-02 — NEW Discord Actions card with 2 buttons + pre-flight UI).
  - `src/main/resources/templates/admin/season-form.html` (Plan 97-03
    — append Post Standings button to #discordIntegration card).
  - `src/main/resources/templates/admin/standings-render.html` (NEW,
    Plan 97-03 — Playwright-graphic template).
  - `src/main/resources/static/admin/css/admin.css` (in-milestone polish
    [[feedback-in-milestone-polish]] — minor adjustments if any).
  - `.planning/phases/97-matchday-level-posts/97-UI-SPEC.md` (Plan 97-01
    REVISION — drop POST-06a section, move POST-06b cluster from
    matchday-detail to match-detail).
  - `.planning/REQUIREMENTS.md` (Plan 97-01 revises POST-06; Plan 97-02
    splits POST-07 into 7a+7b).
  - `src/main/resources/db/migration/V14__add_discord_post_phase_id.sql`
    (NEW, Plan 97-03 — `ADD COLUMN phase_id UUID NULL` + FK to
    `season_phases(id)` + index, per D-97-STA-4).
  - `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java`
    (Plan 97-03 — `findByChannelIdAndPostTypeAndSeasonIdAndPhaseId`
    derived query for STANDINGS identity-key).
  - `src/main/java/org/ctc/discord/model/DiscordPost.java` (Plan 97-03
    — `@ManyToOne SeasonPhase phase` field nullable, mapped to new
    column).
  - `src/main/java/org/ctc/admin/dto/SeasonForm.java` or new
    `PostStandingsForm.java` (Plan 97-03 — phase-selector form-DTO with
    `UUID phaseId` field, @NotNull validation, no Mass-Assignment leak
    via entity).
  Keine Edits in `org.ctc.dataimport.*`, `org.ctc.sitegen.*`,
  `org.ctc.gt7sync.*`, `org.ctc.scoring.*`, `org.ctc.backup.*`. Each
  plan-SUMMARY asserts `src/` clean outside the explicit whitelist.

### Claude's Discretion

- **Power Rankings persistence strategy for POST-07b** (D-97-MD-1) —
  snapshot subtitle + teamIds-order into a new `discord_post.payload_json`
  column for exact-reproducible Re-Post vs always-regenerate from current
  `SeasonTeam.rating`. Recommended: regenerate (simpler, matches operator
  mental model). Planner picks based on edge-case analysis.
- **Sub-team-suffix resolution for H3 header** (D-97-PREV-2b) — which
  `Team` field/method cleanly produces the `B`/`C`/... suffix. Planner
  inspects `Team` entity + grep all callsites that produce the
  `DTR vs. TNR B`-style label for consistent reuse.
- **`StandingsGraphicService` template strategy** (D-97-STA-1) — duplicate
  `templates/admin/standings.html` into a new `standings-render.html` with
  graphic-specific styling vs reuse via Thymeleaf fragments. Planner
  picks based on visual differences needed.
- **`MatchdayResultsGraphicService.generateMatchdayResultsBytes` byte[]
  variant** (D-97-MD-1) — verify whether the byte[] variant exists; if
  not, add it analog Phase 96 D-96-FOR-3d (`ResultsGraphicService.
  generateResultsBytes`).
- **Visual-regression snapshots for the 3 new Discord-graphic outputs**
  (Plans 97-02 + 97-03) — pixel-hash comparison against `.screenshots/
  97-NN/*-reference.png`; Planner can treat as a test or deferred to
  Phase 98 polish.
- **`discord_post.matchday_id` column existence check** (Plan 97-02) —
  if Phase 95 V11 did not provision the matchday_id FK alongside
  match_id/race_id/season_id, Plan 97-02 adds a V14 migration as a
  prerequisite task. Otherwise reuses existing column.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 97: Matchday-Level Posts" — Goal,
  Depends-on, REQ-IDs (POST-06, POST-07, POST-08), 3-plan estimate.
- `.planning/REQUIREMENTS.md` § "POST-06..08" — original REQ-text
  (**SCOPE REVISED** per D-97-PREV-2 + D-97-MD-1; Plan 97-01 + 97-02
  apply the revisions).
- `.planning/milestones/v1.13-ROADMAP.md` § "Phase 97" — milestone-scoped
  REQ-IDs + dependency on Phase 96 forum-thread infrastructure.
- `.planning/PROJECT.md` § "Current Milestone: v1.13" — design-spec
  pointer + Discord-Integration metadata.
- `.planning/STATE.md` § "Active Milestone — v1.13" + § "Baselines to
  Preserve" — JaCoCo / test-count / CI-time baselines for the gate
  check.
- `.planning/phases/97-matchday-level-posts/97-UI-SPEC.md` — UI design
  contract (**PARTIAL — requires revision in Plan 97-01** per D-97-PREV-2
  / Out of scope section above). Where UI-SPEC and this CONTEXT.md
  conflict, CONTEXT.md wins (later sign-off).

### Design Spec

- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` —
  § 4.5 MATCH_PREVIEW Markdown structure, § 4.7 Forum-Thread linking,
  § 3.7 DiscordTimestamps, § 3.8 Emoji Resolution. **§ 4.3 button matrix
  for matchday-level posts is REVISED** by D-97-PREV-2 (per-match
  Match-Detail location, no batch) + D-97-MD-1 (POST-07 split into 2
  buttons). § 5 plan-count estimate (3 plans for Phase 97) confirmed.

### Phase 96 Hand-off (Forum-Thread Infrastructure)

- `.planning/phases/96-provisional-graphic-forum-threads/96-CONTEXT.md`
  — **D-96-FOR-3a `?thread_id={id}` query-param method-overload pattern**
  on `DiscordWebhookClient` (Plan 97-02 + 97-03 reuse verbatim);
  **D-96-FOR-3b RaceRef/SeasonRef sealed-permits already added**
  (Plan 97-03 reuses SeasonRef for STANDINGS; Plan 97-02 adds
  MatchdayRef); **D-96-FOR-4 auto-unarchive-before-post** (Plan 97-02 +
  97-03 inherit via `DiscordPostService.postOrEdit` shared code path).
- `.planning/phases/96-provisional-graphic-forum-threads/96-01-SUMMARY.md`
  (if exists) — `ProvisionalScoresGraphicService` implementation as
  reference for `StandingsGraphicService` (Plan 97-03).
- `.planning/phases/96-provisional-graphic-forum-threads/96-02-SUMMARY.md`
  (if exists) — Flyway V13 `seasons.discord_*_thread_id` columns + Season
  Thread-Linker UI; Plan 97-03 reads `season.discordStandingsThreadId`
  as a pre-flight predicate.

### Phase 95 Hand-off (Auto-Edit Pattern + DiscordPost Foundation)

- `.planning/phases/95-match-channel-posts/95-CONTEXT.md` —
  **D-95-04 `MatchService.save` Pre/Post-Diff + `@TransactionalEventListener
  AFTER_COMMIT` pattern** (Plan 97-01 mirrors verbatim for MATCH_PREVIEW);
  **D-95-04a Race.dateTime does NOT trigger** (Plan 97-01 inherits the
  scope-restriction); **D-95-12 DiscordPostRef sealed-hierarchy** (Plan
  97-02 extends with MatchdayRef permit); button-label/cluster-shape
  conventions (`Post X` / `Re-Post X` / `Update X` for stale).
- `.planning/phases/95-match-channel-posts/95-04-SUMMARY.md` — POST-05
  Schedule auto-edit live-Discord behaviour as integration smoke-test
  reference for Plan 97-01.
- `src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java`
  (current implementation, lines 49-67) — exact `onScheduleFieldsChanged`
  template for Plan 97-01 `onMatchPreviewFieldsChanged` analog.
- `src/main/java/org/ctc/discord/event/MatchScheduleFieldsChangedEvent.java`
  — record template for Plan 97-01 `MatchPreviewFieldsChangedEvent`.

### Phase 94 Hand-off (Channel-Lifecycle + Modal-Pattern)

- `.planning/phases/94-team-roles-match-channel-lifecycle/94-CONTEXT.md`
  — `.discord-actions--posts` cluster CSS shape; modal show/hide JS
  pattern (Phase 97 does not introduce new modals after D-97-PREV-2 drops
  the batch-confirmation modal; existing inline-onclick pattern used if
  needed).

### Phase 93 Hand-off (DiscordRestClient + Sealed Exception)

- `.planning/phases/93-discord-foundation/93-CONTEXT.md` — sealed
  `DiscordApiException` 4-permit hierarchy (transient / auth / not-found
  / permission). Plan 97-01..03 IT tests cover all 4 categories per
  Phase-95-precedent.

### Existing-Code-Touchpoints (Phase-97-Scope-Reuse)

- `src/main/java/org/ctc/admin/service/SettingsGraphicService.java` —
  byte[] generator for Settings PNG (reused by Plan 97-01 MATCH_PREVIEW
  attachment).
- `src/main/java/org/ctc/admin/service/LineupGraphicService.java` —
  byte[] generator for Lineups PNG (reused by Plan 97-01 MATCH_PREVIEW
  attachment).
- `src/main/java/org/ctc/admin/service/MatchdayResultsGraphicService.java`
  — byte[] generator for Match Day Results PNG (reused by Plan 97-02
  POST-07a; Planner verifies byte[] variant exists).
- `src/main/java/org/ctc/admin/service/PowerRankingsGraphicService.java`
  — `generateRankings(year, number, subtitle, teamIds) → byte[]` already
  byte[] (reused by Plan 97-02 POST-07b).
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java`
  — operator's current download workflow; Plan 97-02 inspects this for
  the source-of-truth on `SeasonTeam.rating` ordering.
- `src/main/java/org/ctc/domain/service/StandingsService.java` /
  `StandingsViewService.java` — read-side standings data (Plan 97-03
  builds the new graphic service on top + adds `hasNewerResultsSince
  PhaseScoped`). `StandingsService.java:149` confirms aggregation
  already spans REGULAR + PLAYOFF + PLACEMENT.
- `src/main/resources/templates/admin/standings.html` — HTML reference
  for the new graphic template (Plan 97-03 picks reuse-vs-duplicate).
  Existing template already handles per-phase × per-group tab rendering
  (lines 28-46) — reuse this fragment logic for the graphic-render
  variant.
- `src/main/java/org/ctc/domain/model/SeasonPhase.java` +
  `SeasonPhaseGroup.java` + `PhaseType.java` (REGULAR/PLAYOFF/
  PLACEMENT) + `PhaseLayout.java` (STANDARD/GROUPS) + `PhaseTeam.java`
  — domain model the StandingsGraphicService traverses to determine the
  PNG-count per click (D-97-STA-3 granularity table).
- `src/main/java/org/ctc/domain/model/Matchday.java` lines 27-31 —
  `phase` + `group` references that the MatchdayResultsGraphicService
  already uses (Plan 97-02 confirms behaviour across all PhaseTypes).
- `src/main/java/org/ctc/admin/service/PlayoffRoundResultsGraphicService.
  java` / `PlayoffRoundOverviewGraphicService.java` / `PlayoffRound
  ScheduleGraphicService.java` — round-orientated alternatives NOT used
  by Phase 97 (per D-97-PHA-1 the matchday-orientated service handles
  Playoff matchdays uniformly).
- `src/main/java/org/ctc/discord/service/DiscordPostService.java` (Phase
  95 + 96 implementation) — `postOrEdit(...)` shared code path that
  Phase 97 posts dispatch through; auto-unarchive (D-96-FOR-4) already
  in place.
- `src/main/java/org/ctc/discord/DiscordWebhookClient.java` (Phase 96
  D-96-FOR-3a `?thread_id=` overload) — Plan 97-02 + 97-03 reuse the
  overload variant for forum-thread targeting.
- `src/main/java/org/ctc/discord/model/DiscordPostType.java` (Phase 95)
  — `MATCH_PREVIEW`, `MATCHDAY_OVERVIEW`, `POWER_RANKINGS`, `STANDINGS`
  enum values already present. `MATCHDAY_PAIRINGS` value stays unused
  per D-97-PREV-2 out-of-scope.
- `src/main/resources/templates/admin/match-detail.html` (Phase 95
  Match Channel cluster + Phase 96 Provisional cluster) — Plan 97-01
  appends Post Match Preview button.
- `src/main/resources/templates/admin/season-form.html` (Phase 96 FORUM-01
  `#discordIntegration` card) — Plan 97-03 appends Post Standings button.

### Convention References

- `CLAUDE.md` § Architectural Principles → Keep Controllers Thin, Score
  Aggregation, Grep All Usages, Spring-Native, Plan Quality Gates.
- `CLAUDE.md` § Conventions → Naming Patterns, Lombok Usage, CSS
  Guidelines, No Comment Pollution.
- `CLAUDE.md` § Subagent Rules → inline-sequential default for execute-
  phase; chain → execute --interactive; memory-aware subagent dispatch.
- `CLAUDE.md` § Build & Test Discipline → clean Maven build authority,
  no flaky dismissal, WireMock-vs-Real-API discipline.
- `.planning/codebase/TESTING.md` § Test Categorization (`@Tag`) — tag
  convention for new tests.
- `.planning/codebase/ARCHITECTURE.md` — Clean 3-tier (Controller →
  Service → Repository).

### User Reference (Live Discord screenshots, 2026-05-23)

- Operator's announcement-channel screenshot (Match Day 4 DTR vs. TNR B)
  — confirms MATCH_PREVIEW layout: H1 season name + H2 Match Day N + H3
  team shortNames + teaser + Date/Stream bullets + Game On! emoji line
  + Settings.png + Lineups.png attachments rendered side-by-side. The
  `(edited)` indicator visible in the screenshot validates the
  auto-edit-hook pattern from Phase 95 D-95-04 working in production.
- Operator's race-results-forum-thread screenshot (Match Day 3 Results
  + Power Rankings) — confirms POST-07 layout: two separate posts in
  the same thread, MatchdayResultsGraphic (per-match scoring table with
  team logos + cumulative records) first, PowerRankingsGraphic (1-14
  ranked teams in two columns) second.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- `DiscordAutoPostListener.onScheduleFieldsChanged` (Phase 95) — exact
  pattern Plan 97-01 mirrors for `onMatchPreviewFieldsChanged`. Both
  listeners use `@TransactionalEventListener(AFTER_COMMIT)` +
  `@Transactional(REQUIRES_NEW)` + `RequestContextHolder`
  request-attribute recording for flash-error surfacing.
- `MatchScheduleFieldsChangedEvent` (Phase 95) — record template (single
  `UUID matchId` field) for `MatchPreviewFieldsChangedEvent`.
- `DiscordPostRef` sealed-hierarchy (Phase 95 D-95-12 + Phase 96
  D-96-FOR-3b) — already has `MatchRef`, `RaceRef`, `SeasonRef`; Plan
  97-02 adds `MatchdayRef(UUID matchdayId)` as the 4th permit.
- `DiscordWebhookClient` `?thread_id=` overload (Phase 96 D-96-FOR-3a)
  — Plan 97-02 + 97-03 reuse for forum-thread targeting.
- `SettingsGraphicService.generateSettingsBytes(...)` +
  `LineupGraphicService.generateLineupBytes(...)` (Phase 95) — Plan 97-01
  reuses for MATCH_PREVIEW attachments (same byte[] paths as POST-03
  match-channel posts, different webhook target).
- `PowerRankingsGraphicService.generateRankings(...)` — already byte[];
  Plan 97-02 reuses verbatim.
- `MatchdayResultsGraphicService` — Plan 97-02 verifies/adds byte[]
  variant.
- `DiscordEmojiCache` (Phase 93) — Plan 97-01 reuses for "Game On!"
  emoji-resolution per design-spec § 3.8.
- `DiscordTimestamps.longDateTime(...)` (Phase 93) — Plan 97-01 reuses
  for the `<t:N:F>` Date bullet per design-spec § 3.7.

### Established Patterns

- **Per-button pre-flight predicates as ModelAttributes** (Phase 95
  Settings/Lineups → Phase 96 D-96-FOR-3c) — Plan 97-01/02/03 follow.
- **Inline-onclick modal show/hide** (Phase 94 Archive Modal pattern) —
  not needed for Phase 97 after D-97-PREV-2 drops the batch-confirmation
  modal.
- **`.discord-actions--posts` cluster wrapper** (Phase 95 in-milestone
  polish CSS) — reused on match-detail (existing) + matchday-detail
  (NEW in Plan 97-02) + season-form (existing from Phase 96 FORUM-01).
  Mobile responsive rules (admin.css 221-228) already cover all 3 pages.
- **Button label transitions Post → Re-Post → Update** (Phase 95 Match
  Results stale-detection) — Plan 97-02 POST-07a + Plan 97-03 POST-08
  follow verbatim.
- **Service-Layer pre-flight method returning a result-record**
  (Phase 96 `canPostRaceResultToForum`) — Plan 97-01..03 each add an
  analog method (`canPostMatchPreview`, `canPostMatchdayResults`,
  `canPostPowerRankings`, `canPostStandings`).
- **`Match-Form save → ApplicationEventPublisher → AFTER_COMMIT listener
  → DiscordPostService`** (Phase 95 D-95-04 + bug-fix `dd5b0ca2`) — Plan
  97-01 follows verbatim.
- **`DiscordHostValidator.requireAllowed` SSRF positive-whitelist**
  (Phase 93 INFRA-02) — every new Webhook URL still passes the
  `discord.com` host check; no new CodeQL suppression.

### Integration Points

- **Phase 95 V11 `discord_post` table polymorphic FK** — Plan 97-02
  verifies `matchday_id` column already exists; if not, adds V14
  migration.
- **Phase 96 V13 `seasons.discord_*_thread_id` columns** — Plan 97-02
  reads `season.discordRaceResultsThreadId` for POST-07a + 07b pre-flight;
  Plan 97-03 reads `season.discordStandingsThreadId` for POST-08
  pre-flight.
- **Phase 93 V8 `discord_global_config.announcement_webhook_url`** —
  Plan 97-01 reads as the POST-06 target.
- **Phase 93 V8 `discord_global_config.race_results_forum_webhook_url`**
  (added by Phase 96) + `standings_forum_webhook_url` (added by Phase 96)
  — Plan 97-02 + 97-03 read as forum-targets.
- **`DiscordPostService.postOrEdit` shared dispatch** (Phase 95 + 96
  implementation) — Plan 97-01..03 add new sealed-switch branches for
  MATCH_PREVIEW (MatchRef + announcement-webhook, no thread_id),
  MATCHDAY_OVERVIEW + POWER_RANKINGS (MatchdayRef + race-results-forum-
  webhook + thread_id), STANDINGS (SeasonRef + standings-forum-webhook
  + thread_id). Auto-unarchive (D-96-FOR-4) inherited for the 3
  forum-thread posts.
- **`MatchService.save(MatchForm)` is the SOLE Discord-hook surface in
  the domain layer** (per D-95-04 + D-97-PREV-1a). Plan 97-01 extends
  the existing diff-then-publish pattern to a second event-type without
  introducing parallel hooks elsewhere.

</code_context>

<specifics>
## Specific Ideas

- **Live Discord screenshots (2026-05-23) are the canonical visual
  reference** for both MATCH_PREVIEW (Match Day 4 DTR vs. TNR B) and the
  POST-07 layout (Match Day 3 Results + Power Rankings in race-results
  thread). Stored in the discussion record + linked from
  97-DISCUSSION-LOG.md. Plan 97-01 + 97-02 must achieve pixel-near
  parity with these references (Markdown structure + attachment
  side-by-side rendering + PNG layouts).
- **POST-06 H3 header explicitly uses team `shortName` + sub-team-suffix
  (`DTR vs. TNR B`)** — not the full team name as the UI-SPEC originally
  said. This is consistent with the operator's live workflow visible in
  the screenshot.
- **Power Rankings reflect operator-curated `SeasonTeam.rating` order,
  NOT auto-computed Standings.** Per user 2026-05-23: "Die Ratings der
  Teams werden nach dem Matchday vom Operator manuell aktualisiert. Wird
  auch für die neuen Stände der Team Cards benötigt. Die Power Rankings
  spiegeln nicht immer den gleichen Stand wie die Standings dar." This
  means POST-07b is independent of `allMatchesFinal` (operator decides
  when ratings are publish-ready) and shares its source-of-truth with
  TEAM_CARDS regeneration.
- **`streamLink` is optional on POST-06** — operator workflow needs to
  send the announcement BEFORE the stream URL is known. `null` renders
  as literal `Stream: TBA` in the Markdown; the auto-edit hook from
  D-97-PREV-1 will PATCH the post when the operator later fills in the
  StreamLink.
- **Settings + Lineups are posted to TWO different Discord destinations
  at TWO different times:** (a) match-channel via Phase 95 POST-03 (one
  per attachment, posted separately as Settings ~2 weeks pre-match +
  Lineups on match-day), AND (b) announcement-channel via Phase 97
  POST-06 (both attachments in the same Match Preview message, posted
  close to match-day). Same generator services, different message
  shapes + targets.
- **Phase-Awareness gilt für POST-06/07a/08, NICHT für POST-07b** —
  POST-06 nutzt `matchday.label` (operator-managed string, phase-
  agnostisch); POST-07a nutzt `MatchdayResultsGraphicService` für
  REGULAR/PLAYOFF/PLACEMENT uniformiert (user-direction: "Es müssen
  einfach nur alle Ergebnisse des Matchdays bzw. Playoff Runde
  dargestellt werden"); POST-08 hat Phase-Selektor + per-phase Identity-
  Key; POST-07b Power Rankings bleibt season-wide (operator-curated
  rating order spans all phases per `SeasonTeam.rating`).
- **Standings-PNG-Granularität per PhaseLayout** (D-97-STA-3): REGULAR-
  ohne-Groups → 1 PNG; REGULAR-mit-Groups → N PNGs (multipart, sorted
  by `SeasonPhaseGroup.sortIndex`); PLAYOFF / PLACEMENT → 1 PNG (kein
  GROUPS-Layout). User-direction 2026-05-23: "Bei Regular Season Phase
  ohne Gruppen 1 PNG. Bei Regular Season Phase mit mehreren Gruppen 1
  PNG je Gruppe im selben Post. Bei Playoff und Placement Matches 1 PNG
  (hier wird es keine Gruppen geben)".
- **Standings-Layout muss Team-Logos integrieren** — konsistent mit
  visueller Familie von `MatchdayResultsGraphic` + `PowerRankingsGraphic`.
  Iterative design loop ([[feedback-graphic-design-iteration]]) startet
  von der existing `templates/admin/standings.html` Datenform.

</specifics>

<deferred>
## Deferred Ideas

- **MATCHDAY_PAIRINGS overview-graphic post** — REQUIREMENTS.md POST-06
  originally included this; per user direction 2026-05-23 the production
  workflow doesn't use it. The `MATCHDAY_PAIRINGS` enum value stays in
  `DiscordPostType` for potential v1.14 use. Captured here so it isn't
  lost if a future requirement re-surfaces it.
- **Race.dateTime auto-edit trigger for MATCH_PREVIEW** — per D-97-PREV-1a
  Race reschedules require manual Re-Post on Match-Detail. If
  reschedule-after-announcement becomes a frequent pattern, v1.14+ can
  extend the diff-publish hook to `RaceService.save` (would require
  Spring-Application-Event refactor since RaceService is currently
  Discord-free).
- **Visual-regression snapshot tests for the 5 new Discord-message
  layouts** (MATCH_PREVIEW Markdown + Settings.png + Lineups.png
  attachments; MATCHDAY_OVERVIEW PNG; POWER_RANKINGS PNG; STANDINGS
  single PNG; STANDINGS multi-group multipart) — Plan 97-02 + 97-03
  Planner can include a pixel-hash test or defer to Phase 98 polish.
- **`SeasonRef` widening strategy** (D-97-STA-4) — widen the existing
  Phase 96 `SeasonRef(UUID seasonId)` record to `SeasonRef(UUID
  seasonId, UUID phaseId)` (recommended; only one Phase 96 callsite —
  FORUM-02 race-result-post — that always passes `phaseId = null`) vs
  add a parallel `SeasonPhaseRef(UUID seasonId, UUID phaseId)` permit
  to the sealed-hierarchy. Planner picks based on code-grep of
  `SeasonRef.*new` callsites.
- **`StandingsGraphicService` Multipart-PNG iteration order** (D-97-STA-3
  REGULAR-with-GROUPS row) — N PNGs in one multipart-POST: order =
  `SeasonPhaseGroup.sortIndex ASC` (existing convention); filename
  pattern reflects sort order so Discord displays Group A → Group B → …
  left-to-right.
- **PowerRankings persistent ordering snapshot per Discord-post** — if
  operator-friction with "rating changed between Post and Re-Post"
  appears in UAT-07, a `discord_post.payload_json` snapshot column +
  V14 migration can be added in v1.14 to make Re-Post exactly reproduce
  the original message.
- **Pinned-thread auto-bump-to-top on Re-Post** — Discord forum-threads
  bump to top of the thread list on new activity; Re-Post via PATCH
  doesn't bump. If operator wants Re-Post to refresh the thread's
  position, a parallel DELETE-then-POST pattern would be needed. Out
  of v1.13 scope.
- **`/admin/discord/posts` listing filtering by season-scope for
  STANDINGS / matchday-scope for MATCH_PREVIEW** — existing filter form
  may need an extra dropdown per scope. Phase 98 polish if user reports
  difficulty finding rows.

</deferred>

---

*Phase: 97-matchday-level-posts*
*Context gathered: 2026-05-23*
