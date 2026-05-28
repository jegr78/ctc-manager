# Phase 95: Match Channel Posts - Context

**Gathered:** 2026-05-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Liefere die fünf Per-Match-Posttypen (`TEAM_CARDS`, `SETTINGS`, `LINEUPS`,
`SCHEDULE`, `MATCH_RESULTS`) als Webhook-Posts mit gespeichertem `message_id`
und einheitlichem In-Place-Edit-Path via Webhook-PATCH (POST-01..05). Vier
sequenzielle inline-Plans auf `gsd/v1.13-discord-integration` per
[[feedback-inline-sequential-execution]] + [[feedback-wave-pause]], mapping
1:1 zu POST-01 (Persistence + List-Page + Service-Skeleton) / POST-02
(Team-Cards Hybrid-Trigger + Refresh-Link) / POST-03 (Settings + Lineups
Multipart-Bundle) / POST-04+05 (Match-Results Stale-Detection + Schedule
Auto-Edit-Hook) — entspricht Design-Spec § 5.

Phase 95 baut die ERSTE Post-Surface auf der Phase-94-Channel-Lifecycle-Plattform.
Phase 94 lieferte `DiscordChannelService.createMatchChannel(Match)` + Webhook-
Creation + V10/V11-Schema. Phase 95 wired den per-Channel-Webhook (gespeichert
in `match.discordChannelWebhookUrl`) an konkrete Post-Aktionen mit
`DiscordPostService.postOrEdit(channel_id, post_type, payload, attachments)`
als zentralem Idempotenz-Pivot.

In scope:

- **POST-01 (Plan 95-01)** — Persistence + Service-Skeleton + List-Page +
  Webhook-Multipart-Edit:
  - **Flyway `V12__discord_post.sql`** (nicht V11 wie ursprünglich
    Design-Spec § 3.3 plante — V11 wurde in Phase 94 Plan-04 für
    `matches.discord_channel_archived_at` belegt; Phase 96 verschiebt
    sich entsprechend auf V13 für `seasons.discord_*_thread_id`). Schema
    1:1 wie Design-Spec § 3.3 (channel_id, message_id, webhook_id,
    webhook_token, post_type, match_id, matchday_id, race_id, season_id,
    posted_at, updated_at + 5 FK-Indizes). H2 + MariaDB kompatibel,
    `ON DELETE SET NULL` auf allen 4 FK-Spalten (Match/Matchday/Race/Season
    können archiviert werden während Discord-History persist bleibt).
  - **`DiscordPost` Entity** in `org.ctc.discord.model` (sibling zu
    `DiscordGlobalConfig` aus Phase 93). `extends BaseEntity`,
    `@ToString(exclude = {"webhookToken"})` — Token ist Secret (analog
    zu `DiscordGlobalConfig.announcementWebhookUrl` Phase 93 D-93-02-Sec).
    Felder gemäß V12-Schema. `post_type` als `@Enumerated(EnumType.STRING)`
    von neuem `DiscordPostType` enum (12 Werte gemäß Design-Spec § 3.3:
    `TEAM_CARDS, SETTINGS, LINEUPS, SCHEDULE, PROVISIONAL_SCORES,
    MATCH_RESULTS, RACE_RESULTS, MATCHDAY_PAIRINGS, MATCH_PREVIEW,
    MATCHDAY_OVERVIEW, POWER_RANKINGS, STANDINGS`) — Phase 95 nutzt 5
    davon, Phase 96/97 die restlichen 7. Enum-Location:
    `org.ctc.discord.model.DiscordPostType`.
  - **`DiscordPostRepository`** in `org.ctc.discord.repository` mit
    Lookup-Method `findByChannelIdAndPostTypeAndMatchId(String, DiscordPostType,
    UUID)` für POST-02/03/04/05 Re-Post-Dispatch + `findAll(Specification)`
    für die Filter-Listing-Page.
  - **`DiscordPostService`** in `org.ctc.discord.service` (sibling zu
    `DiscordChannelService` Phase 94). API:
    ```java
    public DiscordPost postOrEdit(
        String webhookUrl,        // aus match.discordChannelWebhookUrl
        DiscordPostType type,
        WebhookPayload payload,
        List<NamedAttachment> attachments,
        DiscordPostRef ref);      // match/matchday/race/season-Foreign-Key
    ```
    Logik: `findByChannelIdAndPostTypeAndMatchId(...)` → wenn existiert,
    `webhookClient.editMessageWithAttachments(url, msgId, payload, attachments)`
    (oder `editMessage` ohne Attachments für SCHEDULE-Embed) + update
    `updated_at` + `attachments_replaced_at` → sonst
    `webhookClient.execute(url, payload, attachments)` + insert. Returns
    `DiscordPost` row. Atomar transactional gegen `discord_post`-Row.
    Discord-Side-Effekt vor DB-Commit (analog zu Phase 94
    `DiscordChannelService.createMatchChannel` D-94-04 ordering — Discord
    ist source of truth, DB cached state).
  - **`DiscordWebhookClient`-Erweiterung**:
    `editMessageWithAttachments(String webhookUrl, String messageId,
    WebhookPayload payload, List<NamedAttachment> attachments)` —
    Multipart-PATCH analog zu vorhandener
    `execute(url, payload, attachments)`-Methode (Phase 93 Plan-01),
    aber `.patch().uri("/messages/{messageId}", messageId)` statt `.post()`.
    ~30 LOC, gleicher `MultipartBodyBuilder`-Code-Path. Existing
    `editMessage()` (JSON-only PATCH) bleibt für SCHEDULE-Embed-Edits
    (POST-05 keine Attachments).
  - **`/admin/discord/posts` List-Page** — neuer `DiscordPostController`
    in `org.ctc.discord.web` (sibling zu Phase 93
    `DiscordConfigController`). GET-Listing mit Filter-Form (season /
    match / post_type Dropdowns), Re-Edit + Re-Post Buttons pro Row.
    Re-Edit / Re-Post implementiert via `DiscordPostService.postOrEdit`
    mit existierender `DiscordPost`-Row (immer PATCH-Pfad). Pagination
    via Spring `Pageable` (50 Rows/Page). Filter-Form-DTO
    `DiscordPostFilterForm` in `org.ctc.discord.dto`. Template
    `templates/admin/discord-posts.html` analog zu Phase 93
    `discord-config.html` Layout.

- **POST-02 (Plan 95-02)** — Team Cards Hybrid-Trigger + Refresh-Link:
  - **Auto-Post bei Channel-Create** — `DiscordChannelService.createMatchChannel`
    bekommt am Ende (nach Webhook-Persist + Audit-Pass) einen Call zu
    `discordPostService.postTeamCards(match)` (per D-95-01). Failure
    kategorisiert via existierendes `DiscordApiExceptionMapper`-Pattern:
    Channel + Webhook bleiben persisted, WARN-Log
    (`"Auto-post TEAM_CARDS failed for match {}: {}"`), Flash-Badge
    `errorMessage="Channel created. Team Cards post failed: {category}
    — click Re-Post Team Cards to retry."` + `errorCategory="{category}"`
    (transient/auth/not-found). Kein Channel-Rollback (Channel ist teure
    Ressource — D-95-01a).
  - **`DiscordPostService.postTeamCards(Match)` Auto-Gen-Fallback** —
    Pre-Flight: `teamCardService.cardExists(homeST) && cardExists(awayST)`.
    Wenn beide vorhanden → 2 PNGs laden, `WebhookPayload.empty()` +
    `attachments[0]=home-card.png`, `attachments[1]=away-card.png`, ein
    Multipart-POST/PATCH. Wenn fehlt: `teamCardService.generateCard(homeST)`
    + `generateCard(awayST)` synchron (Playwright headless ~10-30s pro
    Card), dann Posten. Wenn `generateCard()` selbst kippt:
    `errorCategory="transient"` + Re-Post-Button bleibt sichtbar
    (D-95-02).
  - **Match-Detail Discord-Actions-Panel** — neuer Section innerhalb
    der existierenden `.discord-actions--posts` Phase-94-Placeholder
    (siehe `templates/admin/match-detail.html` line 55-58):
    ```
    Team Cards    [Post Team Cards]      ← visible wenn !TEAM_CARDS-row exists
                  [Re-Post Team Cards]   ← visible wenn TEAM_CARDS-row exists
                  [↻ Refresh Cards]      ← visible wenn TEAM_CARDS-row exists, Generate+Re-Post in 1 Klick
    ```
    Sichtbarkeit-Logic via Thymeleaf `th:if`. Keine inline-Styles
    ([[feedback-no-inline-styles]]) — CSS-Klassen aus `admin.css`
    `.discord-actions--posts` `.btn-tab` Variante; falls neue CSS
    nötig, in same Plan ([[feedback-in-milestone-polish]]).
  - **"Refresh Team Cards"-Link** (D-95-02) — kombiniert
    `teamCardService.generateCard(homeST) + generateCard(awayST) +
    discordPostService.postOrEdit(...TEAM_CARDS)` in einer Aktion. POST
    `/admin/matches/{id}/refresh-team-cards`. Flash-Badge bei Erfolg
    `"Team cards regenerated and re-posted"`. Failure-Mode wie Auto-Post.
  - Tests: `DiscordPostServiceTeamCardsIT` (WireMock — happy-path
    multipart-POST mit 2 attachments + multipart-PATCH re-edit),
    `DiscordChannelServiceAutoPostHookIT` (verify
    `createMatchChannel` ruft `postTeamCards` am Ende), `MatchDetailTeamCardsButtonsE2ETest`
    (Playwright — Buttons sichtbar/unsichtbar je nach DB-State, click
    triggert POST, success-flash erscheint) + Desktop+Mobile-Sweep
    ([[feedback-playwright-cli]]).

- **POST-03 (Plan 95-03)** — Settings + Lineups Multipart-Bundle:
  - **Service-Layer**: `DiscordPostService.postSettings(Match)` +
    `postLineups(Match)`. Pre-Flight-Pflicht (D-95-03b): alle
    `match.getRaces()` haben Settings-Entity != null (für SETTINGS) bzw.
    `RaceLineup` mit ≥1 Driver pro Team (für LINEUPS). Sonst
    `BusinessRuleException("Configure settings/lineups for all races
    first")` → Flash-Badge nicht-discord-API-Kategorie
    (`errorCategory="data-incomplete"`, neue Kategorie für
    `admin.css` palette — analog zu `category-full` Phase 94).
  - **Multipart-Bundle Logic** (D-95-03):
    ```java
    List<NamedAttachment> attachments = match.getRaces().stream()
        .sorted(Comparator.comparingInt(Race::getRaceNumber))
        .map(race -> NamedAttachment.of(
            "settings-race-" + race.getRaceNumber() + ".png",
            settingsGraphicService.generateSettings(race)))
        .toList();
    return postService.postOrEdit(webhookUrl, SETTINGS,
        WebhookPayload.empty(), attachments,
        DiscordPostRef.match(match));
    ```
    Lineups analog mit `lineupGraphicService.generateLineup(race)`.
    Discord-Multipart-Limit: 10 attachments + 25 MB total. Bei 4 Races
    à ~200KB pro PNG = ~800KB total → unkritisch. Edge-case bei >10
    Races (theoretisch möglich in einem Match): `BusinessRuleException`
    (sollte nie passieren — CTC-Format ist 3-4 Races/Match).
  - **Match-Detail Buttons**:
    ```
    Settings      [Post Settings]        ← visible wenn alle races.settings != null AND !SETTINGS-row exists
                  [Re-Post Settings]     ← visible wenn SETTINGS-row exists
    Lineups       [Post Lineups]         ← visible wenn alle races haben RaceLineup AND !LINEUPS-row exists
                  [Re-Post Lineups]      ← visible wenn LINEUPS-row exists
    ```
    Tooltip bei disabled-state (computed via Service-Layer Pre-Flight,
    nicht im Template) `"Configure settings/lineups for all races
    first"`.
  - Tests: `DiscordPostServiceSettingsBundleIT` (WireMock — 4-attachment
    multipart-POST + PATCH re-edit), `DiscordPostServiceLineupsBundleIT`,
    `DiscordPostServicePreFlightTest` (Mockito-only unit für
    Pre-Flight-Branches: alle races komplett / 1 fehlt / leere races
    list), `MatchDetailSettingsLineupsButtonsE2ETest` + Mobile-Sweep.

- **POST-04+05 (Plan 95-04)** — Match-Results Stale-Detection + Schedule
  Embed + Auto-Edit-Hook (gebundelt da beide Match-Detail-Buttons + die
  letzten 2 von 5 Post-Types):
  - **POST-04 Match-Results**: `DiscordPostService.postMatchResults(Match)`
    nutzt existierenden `MatchResultsGraphicService.generateMatchResults(Match)`
    der `byte[]` returnt. Single-PNG-Multipart-POST/PATCH.
    Stale-Detection via existierendem `BaseEntity.updatedAt`
    (`@LastModifiedDate`): in `DiscordPost.findByChannelIdAndPostTypeAndMatchId`
    laden → wenn `existing.updatedAt < match.updatedAt`, button-label
    wechselt zu `"Update Match Results"` (Frontend Thymeleaf-Compare).
    POST-Action ist identisch zu Re-Post — postOrEdit handelt
    Idempotenz. ROADMAP-Erfolgskrit 3: "stale-detection only triggers
    when underlying data has actually changed (no PATCH on a no-op
    save)" — wird im Controller-Test verifiziert.
    Button visible wenn `match.final == true` (Stewarding abgeschlossen).
  - **POST-05 Schedule Embed**: `DiscordPostService.postSchedule(Match)`
    baut `WebhookPayload` mit Embed (kein Attachment) gemäß Design-Spec
    § 4.6:
    ```java
    Embed embed = Embed.builder()
        .title("Match Schedule")
        .field("Date", "<t:" + epochOfFirstRace + ":F> (<t:...+:R>)")
        .field("Lobby Host", match.lobbyHost ?: "_TBD_")
        .field("Race Director", match.raceDirector ?: "_TBD_")
        .field("Streamer", match.streamer ?: "_TBD_")
        .build();
    ```
    First-race-Time = `match.races.stream().map(Race::getDateTime).min(...)`.
    `DiscordTimestamps.longDateTime()` + `.relative()` aus Phase 93.
    Button visible wenn `firstRaceTime != null` (ROADMAP-Krit 4).
  - **POST-05 Auto-Edit-Hook** (D-95-04): erweitert
    `MatchService.save(MatchForm form)`:
    ```java
    Match before = matchRepository.findById(form.id()).orElseThrow();
    boolean scheduleFieldsChanged =
        !Objects.equals(before.getLobbyHost(),    form.lobbyHost())
     || !Objects.equals(before.getRaceDirector(), form.raceDirector())
     || !Objects.equals(before.getStreamer(),     form.streamer());

    applyForm(before, form);
    Match saved = matchRepository.save(before);

    if (scheduleFieldsChanged) {
        discordPostService.autoEditScheduleIfNeeded(saved);
    }
    return saved;
    ```
    `autoEditScheduleIfNeeded(Match)` macht `findByChannelId...SCHEDULE`
    — wenn existiert → PATCH, sonst no-op (kein automatischer
    Initial-Post, der bleibt button-getrigger). Race.dateTime-Änderung
    triggert NICHT automatisch (D-95-04a) — bei Race-Reschedule muss
    Operator manuell Re-Post-Button klicken. Begründung: minimaler
    Coupling-Surface (`RaceService` bleibt Discord-frei in v1.13).
  - Tests: `DiscordPostServiceMatchResultsIT` (WireMock happy-path +
    stale-detection branches), `DiscordPostServiceScheduleIT` (Embed-
    Payload-Shape + DiscordTimestamps integration), `MatchServiceScheduleEditHookIT`
    (3 Branches: schedule-fields-changed → PATCH; schedule-fields-unchanged
    → no PATCH; no SCHEDULE post exists → no PATCH), `MatchDetailMatchResultsButtonE2ETest`
    + `MatchDetailScheduleButtonE2ETest` + Mobile-Sweep.

</domain>

<decisions>
## Implementation Decisions

### Team-Cards-Post-Trigger (Q-95-01)

- **D-95-01: Hybrid — Auto-Post bei Channel-Create + Re-Post-Button.**
  `DiscordChannelService.createMatchChannel(Match)` ruft am Ende (nach
  Webhook-Persistence + Permission-Audit-Pass per Phase-94 D-94-04 +
  D-94-15) `discordPostService.postTeamCards(match)`. Match-Detail
  Discord-Actions-Panel zeigt zusätzlich permanent einen
  "Re-Post Team Cards"-Button (visible wenn TEAM_CARDS-Row in
  `discord_post` existiert). Bricht die "5-Buttons-uniform"-Symmetrie
  zu POST-03/04/05 bewusst — Team Cards sind strukturell mit der
  Channel-Identität verknüpft (`md{N}-{home}-vs-{away}` enthält
  identische Team-Identitäten). REJECTED: Manual-Only (B) als
  reine Symmetrie-Wahrung ohne UX-Vorteil; Auto-only-without-Re-Post (A)
  wegen fehlender Recovery für Logo-/Lineup-Updates; Defer to Phase 98
  als unnötige Vertagung des bereits dokumentierten User-Wunschs.

- **D-95-01a: Auto-Post-Failure → Channel bleibt + gelbe Badge + WARN-
  Log; kein Channel-Rollback.** Channel + Webhook bleiben persisted
  (sind die teure Ressource: Discord-50-Channels-per-Category-Limit
  zählt nur den Channel). `errorMessage="Channel created. Team Cards
  post failed: {category} — click Re-Post Team Cards to retry."`,
  `errorCategory={transient|auth|not-found}` (existierende 4-Kategorie-Palette
  aus Phase 93). Operator-Workflow: Re-Post-Button auf Match-Detail
  klickbar zum Retry. Strikteres Channel-Rollback wäre Overkill —
  Channel bleibt operativ nutzbar, Team-Cards-Recovery ist preisgünstig.

### TEAM_CARDS PNG-Quelle (Auto-Gen-Strategy)

- **D-95-02: Auto-Gen-on-Demand + dedizierter "Refresh Team Cards"-
  Link auf Match-Detail.** `DiscordPostService.postTeamCards(Match)`
  Pre-Flight prüft `teamCardService.cardExists(homeST) &&
  cardExists(awayST)`. Wenn fehlt → ruft `teamCardService.generateCard()`
  synchron auf (Playwright headless ~10-30s pro Card). Match-Detail
  zeigt zusätzlich permanent einen kleinen "↻ Refresh Team Cards"-
  Link/Button im Discord-Actions-Panel (visible wenn TEAM_CARDS-Row
  existiert), der `generateCard(homeST) + generateCard(awayST) +
  postOrEdit(...)` in 1 Klick kombiniert — für den Edge-Case
  "Logo wurde aktualisiert" oder "Lineup wurde nach MD-Erstellung
  geändert". Auto-Gen-Failure (Playwright kippt) bubblet als
  `transient`-Category zur Flash-Badge mit Re-Post-Button-Recovery.
  REJECTED: Pre-Gen-only (A) wegen unnötiger Operator-Friction
  (`/admin/tools/team-cards`-Detour beim Happy-Path); Always-Regenerate
  (C) wegen doppelter Playwright-Kosten bei jedem Post-Klick
  (~30-60s) — Wave-Pause-Mobile-Sweeps würden unverhältnismäßig
  langsam.

### Settings/Lineups Race→Match-Bridge

- **D-95-03: Multipart-Bundle — 1 Post pro Type mit N Attachments.**
  Pro Match: 1 SETTINGS-Post mit `files[0..N-1] = settings-race-{i}.png`,
  1 LINEUPS-Post mit `files[0..N-1] = lineups-race-{i}.png`.
  Race-Index `i` = `race.raceNumber` (sortiert). Re-Post via
  Webhook-PATCH ersetzt alle Attachments atomisch in der existierenden
  Discord-Message. ROADMAP-Erfolgskrit 1 ("1 row pro Type, Re-Post
  edits in place") clean erfüllt. Discord-Multipart-Limit
  10 attachments + 25 MB > CTC-Match-Footprint (3-4 Races à ~200KB).
  REJECTED: N Buttons / N Posts (A) wegen `discord_post`-PK-Shape
  müsste `(channel_id, post_type, race_id)` statt `match_id` werden
  + bricht 1-row-pro-Type-Erfolgskriterium; Composite Match-PNG (C)
  wegen neuem Template-Aufwand + Mobile-Layout-Risk + Pixel-
  Positionierung-Iteration ([[feedback-graphic-pixel-positioning]]);
  Race-Detail-Buttons (B) wegen ROADMAP-Wortlaut "on Match-Detail"
  + Navigation-Friction.

- **D-95-03a: `DiscordWebhookClient` Scope-Add in Plan 95-01 —
  `editMessageWithAttachments(String webhookUrl, String messageId,
  WebhookPayload payload, List<NamedAttachment> attachments)`.**
  Multipart-PATCH-Methode analog zur existierenden
  `execute(url, payload, attachments)`-POST-Methode (Phase 93
  INFRA-01), aber `.patch().uri("/messages/{messageId}", messageId)`
  statt `.post()`. ~30 LOC im selben Client. Existierende
  `editMessage()` (JSON-only PATCH) bleibt für SCHEDULE-Embed-Edits
  (POST-05 hat keine Attachments). Test:
  `DiscordWebhookClientMultipartEditIT` (WireMock — verifiziert
  multipart-PATCH-Body-Pattern mit N Attachments).

- **D-95-03b: Pre-Flight-Pflicht: Buttons visible nur wenn alle
  Races komplett.** SETTINGS-Button visible wenn
  `match.races.stream().allMatch(r -> r.getSettings() != null)`.
  LINEUPS-Button visible wenn alle Races eine `RaceLineup` mit ≥1
  Driver pro Team haben (per `raceLineupRepository.findByRace(...)`).
  Sonst Tooltip `"Configure settings/lineups for all races first"`
  + Link-zu-Race-Edit. Saubere Semantik: "Post Settings" garantiert
  vollständige Daten. REJECTED: Pragmatisches K-von-N-Posten
  (Iterativer Workflow) wegen Re-Post-Confusion (Operator weiß
  nicht ohne Channel-Check ob alle Races drin sind) + brüchige
  Discord-PATCH-Diff-Visualisierung; Hybrid mit "{K}/{N}"-Badge
  als unnötiger UX-Komplexität.

### POST-05 Schedule Auto-Edit-Hook

- **D-95-04: `MatchService.save(MatchForm)` Pre/Post-Diff +
  `DiscordPostService.autoEditScheduleIfNeeded(Match)`.** Hook lebt
  im Domain-Service (CLAUDE.md "Keep Controllers Thin"):
  `MatchService.save()` lädt die Entity vor `applyForm`, vergleicht
  `lobbyHost / raceDirector / streamer` (Object.equals null-safe),
  ruft nach `save()` den DiscordPostService-Pfad wenn `changes
  && SCHEDULE-row exists`. `autoEditScheduleIfNeeded` macht
  Lookup + PATCH oder no-op (kein automatischer Initial-Post —
  Initial-Post bleibt Button-getriggert per ROADMAP-Krit 4).
  REJECTED: MatchController inline-Diff (A) bricht CLAUDE.md
  Controller-thin-Pattern + [[feedback-orchestrator-discipline]];
  Spring ApplicationEvent (C) als YAGNI in v1.13 — kein 2.
  Konsument bis Phase 97 (MATCH_PREVIEW Auto-Edit könnte später
  refactoren zu Event, wenn 3. Konsument auftaucht); JPA
  `@PostUpdate`-Listener (D) wegen hidden control-flow + Test-
  Visibility-Problemen.

- **D-95-04a: Nur die 3 Match-Schedule-Felder triggern Schedule-
  Auto-Edit. Race.dateTime-Änderung NICHT automatisch.** Trigger-
  Felder: `lobbyHost, raceDirector, streamer`. Race-Date-Verschiebung
  (theoretisch beeinflusst `Date <t:N:F>` im SCHEDULE-Embed) muss
  manuell via Re-Post-Button ausgelöst werden. Begründung:
  minimaler Coupling-Surface — `RaceService` bleibt Discord-frei
  in v1.13, `MatchService.save()` ist die einzige Discord-Hook-
  Stelle. Race-Reschedule-After-MD-Start ist seltener Edge-Case
  (typisches CTC-Workflow: Races werden bei MD-Erstellung
  scheduled und nicht mehr geändert). DEFERRED: Race.dateTime-
  Trigger als DISC-FUTURE Ticket falls Edge-Case in Practice
  häufig wird.

### Plan Decomposition & Sequencing (carried forward from Phase 92/93/94 D-05/07)

- **D-95-05: Vier Plans, sequenziell inline auf
  `gsd/v1.13-discord-integration`.** Mirrors Phase 92 D-05 +
  Phase 93 D-05 + Phase 94 D-07 + Design-Spec § 5 (4 Plans für
  Phase 95). Wave-Pause + Mobile-Sweep nach jedem Plan
  ([[feedback-wave-pause]] + [[feedback-playwright-cli]]).
  Reihenfolge:
  - **Plan 95-01 — POST-01 Persistence + Service-Skeleton + List-
    Page + Webhook-Multipart-Edit.** Flyway V12 + DiscordPost
    Entity + Repository + DiscordPostService.postOrEdit() +
    DiscordPostType enum + DiscordWebhookClient.editMessageWithAttachments()
    Scope-Add + DiscordPostController GET-Listing + Filter-Form-DTO
    + Thymeleaf-Template + admin.css `.discord-posts-filter`-Layout.
    Tests: DiscordPostServiceWireMockIT (happy postOrEdit branches),
    DiscordWebhookClientMultipartEditIT, DiscordPostFilterControllerIT,
    DiscordPostsListE2ETest + Mobile-Sweep.
  - **Plan 95-02 — POST-02 Team Cards Hybrid-Trigger + Refresh-Link.**
    DiscordPostService.postTeamCards() + Auto-Gen-Fallback +
    DiscordChannelService Auto-Post-Hook am Ende von createMatchChannel
    + Match-Detail Discord-Actions-Panel-Erweiterung mit 3 Buttons
    (Post / Re-Post / Refresh) + Refresh-Link-Controller-Endpoint
    POST /admin/matches/{id}/refresh-team-cards + admin.css
    `.discord-actions--posts` Button-Cluster-CSS (in-milestone-polish
    [[feedback-in-milestone-polish]]). Tests:
    DiscordPostServiceTeamCardsIT, DiscordChannelServiceAutoPostHookIT,
    MatchControllerTeamCardsRefreshIT, MatchDetailTeamCardsButtonsE2ETest
    + Mobile-Sweep.
  - **Plan 95-03 — POST-03 Settings + Lineups Multipart-Bundle +
    Pre-Flight.** DiscordPostService.postSettings() + postLineups()
    + Pre-Flight-Predicate-Methods (matchHasCompleteSettings,
    matchHasCompleteLineups) + Match-Detail-Button-Visibility-Logic
    (Pre-Flight-Result als ModelAttribute) + tooltips +
    `data-incomplete` errorCategory + `admin.css`
    `.error-badge--data-incomplete` Variante. Tests:
    DiscordPostServiceSettingsBundleIT (4 Races multipart-POST+PATCH),
    DiscordPostServiceLineupsBundleIT, DiscordPostServicePreFlightTest,
    MatchDetailSettingsLineupsButtonsE2ETest + Mobile-Sweep.
  - **Plan 95-04 — POST-04+05 Match-Results Stale-Detection +
    Schedule Embed + Auto-Edit-Hook.** DiscordPostService.postMatchResults()
    + postSchedule() + autoEditScheduleIfNeeded() + MatchService.save()
    Pre/Post-Diff-Erweiterung + Match-Detail Buttons mit
    Stale-Label-Logic + admin.css Button-States. Tests:
    DiscordPostServiceMatchResultsIT, DiscordPostServiceScheduleIT,
    MatchServiceScheduleEditHookIT (3 Diff-Branches), Match-Detail
    Buttons E2E + Mobile-Sweep. **Ende von Plan 95-04 = Phase-95-Close
    via `/gsd-validate-phase 95`.**
  Keine Worktrees, keine Subagents per [[feedback-inline-sequential-execution]].
  Wave-Pause [[feedback-wave-pause]] nach JEDEM der 4 Plan-Closes.

### PR Mechanics (carried forward from Phase 92/93/94 D-06/08)

- **D-95-06: Rolling v1.13 Milestone-PR weiter pflegen.** Phase 92
  Plan 92-01 hat die Draft-PR geöffnet, Phasen 93+94 haben den Body
  via `gh pr edit --body-file` aktualisiert. Phase 95 Plans 95-01..04
  ergänzen jeweils eine neue Zeile in der rolling per-plan summary
  table (Plan # / REQ-ID / status / commit SHA / CI run URL).
  Squash-Subject locked: `feat(v1.13): discord integration &
  carry-forwards` ([[feedback-squash-merge-message]]). PR bleibt
  Draft bis Ende Phase 98.

### Quality Gates (carried forward from Phase 92/93/94 D-07/09)

- **D-95-07: Standard-Gates unverändert.**
  - JaCoCo line coverage ≥ 88.88% (oder Phase-94-Endwert wenn nicht
    erreicht — siehe 94-VERIFICATION nach `/gsd-validate-phase 94`).
    Phase 95 fügt ~50-70 neue Tests hinzu (4 Plans × ~12-18 Tests:
    Mockito-Unit + WireMock-IT + 1 Playwright E2E pro Plan +
    Mobile-Sweep-Variante). Coverage MUSS Phase-94-Baseline halten.
  - SpotBugs `BugInstance` count = 0 (blocking, verify-bound).
    `org.ctc.discord.repository.*` + `org.ctc.discord.model.DiscordPost`
    (neuer Entity) bringen potentielle `EI_EXPOSE_REP*`-Findings —
    `@ToString.Exclude` auf `webhookToken` notwendig (analog zu
    Phase-93 `DiscordGlobalConfig.announcementWebhookUrl`). Lombok-
    config Phase-86-Invariant gilt unverändert.
  - CodeQL gate-step exit 0 auf PR HEAD SHA. Keine neuen
    SSRF-Suppression nötig (Webhook-URLs gehen über existierende
    `DiscordHostValidator.requireAllowed()`-Guards aus Phase 93).
    Falls neue Finding auftaucht: Three-layer-FP-Invariant aus
    Phase 85 D-19 (codeql-config.yml + Source-Marker +
    sast-acceptance.md-Triade) anwenden.
  - `EXPORT_ORDER` = 24 entities (Phase 95 fügt **+1 Entity** hinzu:
    `DiscordPost`). **`BackupSchema.SCHEMA_VERSION` bumpt auf 2**
    (Backup-Wire-Contract-Schema-Change per Phase-72 D-15 Convention).
    `BackupSchemaGuardTest` Update auf 25 Entities + Version 2.
    Plan 95-01 schreibt die zusätzliche MixIn-Klasse +
    `DiscordPostRestorer` in `org.ctc.backup.serialization` +
    `org.ctc.backup.restore` (analog zu allen 24 existierenden
    Entity-Restorern aus Phase 72-79). Topo-Sort via JPA-Metamodel
    Kahn-Algorithm aus Phase 72 D-12 hängt `DiscordPost` automatisch
    nach `Match/Matchday/Race/Season` ein (ON-DELETE-SET-NULL-FKs).
  - Flyway V1-V11 immutable (V11 = Phase 94 Plan-04
    `matches.discord_channel_archived_at`). Phase 95 fügt V12 hinzu.
    Phase 96 verschiebt sich von ursprünglich-geplant V12 auf **V13**
    (siehe Design-Spec § 3.3 Korrektur — wird in Phase 96 CONTEXT
    festgehalten).
  - `./mvnw verify -Pe2e` finishes within v1.12 CI E2E 17:39 ± 20 %
    tolerance. Phase 95 fügt ~10-15 WireMock-ITs + 4 Playwright-E2E
    + Mobile-Sweep-Variante hinzu. Expected impact: < 120 s gesamt.

### Test Discipline (carried forward from Phase 93/94 D-10/11)

- **D-95-08: Per-Plan Nyquist VALIDATION.md.** Plans 95-01..04 shippen
  jeweils mit eigener `VALIDATION.md`. Phase 95 self-validates via
  `/gsd-validate-phase 95` vor `/gsd-execute-phase 96` start.

- **D-95-09: `@Tag`-Convention per CLAUDE.md.**
  - WireMock-backed ITs (`DiscordPostService*IT`, `DiscordWebhookClientMultipartEditIT`,
    `DiscordChannelServiceAutoPostHookIT`, `MatchControllerTeamCardsRefreshIT`,
    `MatchServiceScheduleEditHookIT`, `DiscordPostFilterControllerIT`)
    → `@Tag("integration")`.
  - Mockito-only Unit-Tests (`DiscordPostServicePreFlightTest`,
    `DiscordPostTypeTest`, `DiscordPostRefTest`) → untagged
    (Projekt-Convention).
  - Playwright E2E (`DiscordPostsListE2ETest`,
    `MatchDetailTeamCardsButtonsE2ETest`,
    `MatchDetailSettingsLineupsButtonsE2ETest`, `MatchDetailMatchResultsButtonE2ETest`,
    `MatchDetailScheduleButtonE2ETest`) → `@Tag("e2e")`, package
    `org.ctc.e2e.discord.posts` per `.planning/codebase/TESTING.md`
    § Test Categorization.

### Live-UAT Strategy (analog Phase 94 D-12)

- **D-95-10: WireMock-IT-only Phase-95-Close; UAT-05 (Live-Discord
  Post Lifecycle) gestaged als STATE.md Pending UAT für Operator-
  Run vor Phase 96 GRAFX-01.** Phase 95 schließt wenn `./mvnw verify
  -Pe2e` grün ist und alle WireMock-ITs für POST-01..05 happy + failure
  paths covered haben. UAT-05 läuft gegen Operator-Test-Guild mit
  echtem Webhook + Bot-Token nach Phase-95-PR-Merge auf Milestone-
  Branch:
  1. Operator wählt Match auf Match-Detail in `dev` profile mit
     `DISCORD_BOT_TOKEN` env-var + live test guild.
  2. Create Channel button drücken → Channel + Webhook persisted +
     **Auto-Team-Cards-Post** sichtbar in Discord-Channel als
     Side-by-Side-Bild.
  3. Re-Post Team Cards → Discord-Edit-Indicator sichtbar, identische
     Cards in derselben Message.
  4. Refresh Team Cards → Cards werden regeneriert (Logo-Test:
     Operator ändert Logo zwischen 2 + 4, Cards reflektieren neues
     Logo).
  5. Post Settings → Multipart-Bundle mit N PNGs (4 für 4-Race-Match)
     erscheint als 1 Message mit allen Bildern Side-by-Side/Carousel.
  6. Post Lineups analog.
  7. Post Schedule → Embed mit 4 Feldern (`Date` mit nativem Discord-
     Timestamp-Render in Operator-Timezone, plus `_TBD_` für leere
     Felder).
  8. Match-Form bearbeiten: `lobbyHost` ändern + speichern → Schedule-
     Embed wird auto-updated (Discord-Edit-Indicator + neuer Wert
     sichtbar).
  9. Race-Result eintragen + Match als final markieren → Post Match
     Results Button verfügbar → Click → PNG erscheint.
  10. Match-Result korrigieren → Match-Detail zeigt "Update Match
     Results" statt "Re-Post" → Click → Discord-Message edited.
  11. `/admin/discord/posts` Listing-Page öffnen → alle 5 Posts
     sichtbar, Filter funktioniert, Re-Edit + Re-Post pro Row.
  Operator-Resultat dokumentiert in STATE.md als UAT-05 Done.

### Production Behavior Boundary

- **D-95-11: Production-Code-Pfade in Phase 95 begrenzt auf:**
  - `src/main/java/org/ctc/discord/model/DiscordPost.java`,
    `DiscordPostType.java` (NEU).
  - `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java`
    (NEU).
  - `src/main/java/org/ctc/discord/service/DiscordPostService.java`
    (NEU) + bestehende
    `src/main/java/org/ctc/discord/service/DiscordChannelService.java`
    (hook in createMatchChannel für Auto-Team-Cards-Post).
  - `src/main/java/org/ctc/discord/DiscordWebhookClient.java`
    (Scope-Add `editMessageWithAttachments`).
  - `src/main/java/org/ctc/discord/dto/DiscordPostFilterForm.java`,
    `DiscordPostRef.java` (NEU).
  - `src/main/java/org/ctc/discord/web/DiscordPostController.java`
    (NEU).
  - `src/main/java/org/ctc/admin/controller/MatchController.java`
    (5 neue POST-Endpoints für die Post-Buttons + Refresh-Endpoint).
  - `src/main/java/org/ctc/domain/service/MatchService.java`
    (save()-Methode bekommt Pre/Post-Diff-Hook für Schedule-Auto-Edit).
  - `src/main/resources/db/migration/V12__discord_post.sql` (NEU).
  - `src/main/resources/templates/admin/match-detail.html`
    (Discord-Actions-Panel-Erweiterung — innerhalb existierender
    `.discord-actions--posts` Placeholder aus Phase 94).
  - `src/main/resources/templates/admin/discord-posts.html` (NEU).
  - `src/main/resources/static/admin/css/admin.css`
    (`.discord-actions--posts` Button-Cluster + `.error-badge--data-incomplete`
    Variante).
  - `src/main/java/org/ctc/backup/serialization/{DiscordPostMixIn}.java`
    (NEU per D-95-07 SCHEMA_VERSION bump).
  - `src/main/java/org/ctc/backup/restore/DiscordPostRestorer.java`
    (NEU per D-95-07 SCHEMA_VERSION bump).
  - `src/main/java/org/ctc/backup/schema/BackupSchema.java`
    (SCHEMA_VERSION 1 → 2).
  Keine Edits in `org.ctc.dataimport.*`, `org.ctc.sitegen.*`,
  `org.ctc.gt7sync.*`, `org.ctc.scoring.*`. Jeder Plan-SUMMARY
  asserts `src/` clean außerhalb der explicit gelisteten Pfade.

### DiscordPostRef Helper (Foreign-Key-Polymorphism)

- **D-95-12: `DiscordPostRef` sealed record-hierarchy für die 4
  FK-Spalten.** Statt `(UUID matchId, UUID matchdayId, UUID raceId,
  UUID seasonId)` an `postOrEdit()` zu reichen (4 Args, 3 davon
  null), eine sealed-record-Variante:
  ```java
  public sealed interface DiscordPostRef
      permits MatchRef, MatchdayRef, RaceRef, SeasonRef {
      static DiscordPostRef match(Match m) { return new MatchRef(m.getId()); }
      // ...
  }
  ```
  Service-Layer-Mapping zu Repository-Lookup-Methods sauberer.
  Phase 96-97 erweitert um Race-/Matchday-/Season-Posts ohne neue
  Service-Signaturen. Planner-Discretion: alternativ als 4-Field-DTO
  ohne sealed-Hierarchie, falls Phase 95 Single-Konsument-Pattern
  bleibt.

### Claude's Discretion

- **`DiscordPostType` enum-location**: `org.ctc.discord.model` (sibling
  zu `DiscordPost`) vs. `org.ctc.discord.dto` (sibling zu
  `ChannelCreateRequest`) — Planner picks based on cohesion (Entity-
  Field-Type → model package empfohlen).
- **`DiscordPostRef` sealed-hierarchy vs. 4-Field-Plain-Record** —
  Planner picks; sealed empfohlen für Phase-96-97 forward-ext, plain
  akzeptabel falls Plan-Größe explodiert.
- **`/admin/discord/posts`-Navigation-Eintrag**: Sidebar vs.
  Admin-Header-Tab-Bar vs. nur Direct-URL — Planner picks based on
  existing `admin/layout.html` Navigation-Structure (vermutlich
  Sidebar-Eintrag unter "Discord" group).
- **`DiscordPostFilterForm`-Field-Types**: UUID-Dropdowns vs.
  Snowflake-String-Inputs für `seasonId`/`matchId` — Planner picks
  basierend auf existing Form-DTO-Patterns + UX-Erwartung
  (Dropdown empfohlen für ~50-Eintrag-Listen, Free-Text für
  großvolumige).
- **Race-Sort-Order in Multipart-Bundle**: `race.raceNumber` (heutige
  Sort) vs. `race.dateTime` ASC — Planner picks; `raceNumber` ist
  semantisch korrekter (Race-Number ist die offizielle Reihenfolge),
  `dateTime` nur falls Operator Races out-of-order schedules
  (unwahrscheinlich).
- **`SCHEDULE`-Embed-Color**: Discord-Default vs. CTC-Branding-Hex —
  Planner picks; CTC-Brand-Color (#0066CC oder ähnlich) empfohlen für
  visual-cohesion mit dem Rest der Admin-UI; falls keine Brand-Color
  in `admin.css` definiert ist, default-grey reicht.
- **Re-Edit vs. Re-Post Semantik auf `/admin/discord/posts`-Page** —
  Phase-95-Wortlaut unterscheidet die beiden, aber per
  postOrEdit-Pattern macht Re-Edit/Re-Post denselben PATCH-Call. Planner
  kann beide zu einem Button konsolidieren ("Re-Post") oder beide als
  separate UX-Affordances belassen — beides akzeptabel.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 95: Match Channel Posts" — Goal,
  Depends-on (Phase 94), Requirements (POST-01..05).
- `.planning/REQUIREMENTS.md` § "POST-01..05" (Lines 50-58) —
  vollständige Requirement-Texte inkl. Acceptance Criteria.
- `.planning/milestones/v1.13-ROADMAP.md` § "Phase 95: Match Channel
  Posts" (Lines 79-92) — Success Criteria 1-6 + Phase Dependency
  Graph (depends auf Phase 94; Phase 96 GRAFX-01 + FORUM-02
  Forward-Chain depends auf POST-01 `DiscordPost` Entity +
  `postOrEdit` Pattern).
- `.planning/PROJECT.md` § "Current Milestone: v1.13" — "zero new
  production dependencies", "outbound-only", "button-triggered"
  Invarianten.
- `.planning/STATE.md` § "Active Milestone — v1.13" + § "Baselines to
  Preserve" (JaCoCo ≥ 88.88%, CI E2E 17:39 ± 20%, SpotBugs 0,
  CodeQL exit 0, EXPORT_ORDER 24 → 25, SCHEMA_VERSION 1 → 2,
  Flyway V1-V11 immutable, V12 lands here) + § "Pending UATs"
  (UAT-04 zu sein abgeschlossen; UAT-05 wird in Phase-95-Close
  hinzugefügt per D-95-10).
- `.planning/phases/95-match-channel-posts/95-DISCUSSION-INPUT.md` —
  Q-95-01 (Auto-post Team Cards bei Channel-Creation) als
  pre-discussion seed; in D-95-01 + D-95-01a aufgelöst (Hybrid + no-rollback).
- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` —
  DIE authoritative Design-Reference. § 3.3 Data Model (`discord_post`
  Tabelle vollständig spezifiziert — ursprünglich V11 geplant,
  korrigiert auf V12 wegen Phase-94-Belegung), § 3.5 Error Handling
  (sealed exception pattern mit `data-incomplete` als 5. Kategorie
  per D-95-03b), § 3.6 Rate-Limit (existierender DiscordRateLimitInterceptor
  aus Phase 93 deckt alle Phase-95-Calls ab), § 3.7 Discord
  Timestamps (`DiscordTimestamps.longDateTime + relative` für
  POST-05 SCHEDULE-Embed), § 4.1 New Pages (`/admin/discord/posts`
  Listing-Page = POST-01-Scope), § 4.3 Button Matrix (Match-Detail-
  Buttons für TEAM_CARDS / SETTINGS / LINEUPS / SCHEDULE /
  MATCH_RESULTS / Re-Post Variants), § 4.6 Schedule Post Structure
  (Embed mit 4 Fields + `_TBD_` Fallback), § 5 Phase Breakdown
  (Phase 95 = 4 Plans), § 6 Risks (rate-limit-burst durch
  sequenzielle Multi-Multipart-Post-Operationen — Token-Bucket
  aus Phase 93 deckt das ab), § 9 Resolved Brainstorming
  Decisions (D-Spec-7 Webhook-PATCH-Edit-Strategy, D-Spec-10
  one-post-with-2-attachments-für-team-cards).

### Phase 94 Hand-off (PRIMARY INPUT — Channel-Lifecycle-Plattform)

- `.planning/phases/94-team-roles-match-channel-lifecycle/94-CONTEXT.md`
  — **D-94-01 (Match-Detail-Page-Architektur)** = Phase 95 baut alle
  Discord-Action-Buttons in den `.discord-actions--posts` Placeholder
  (`templates/admin/match-detail.html` line 55-58) ein; kein neuer
  Page-Layout. **D-94-04 (Permission-Audit + Channel-Delete-on-Fail)** =
  Phase 95 Auto-Post-Hook (D-95-01) läuft AM ENDE von
  `createMatchChannel`, NACH dem Audit + DB-Persist — Audit-Failure
  führt zu Channel-Delete (vorhandenes Pattern), Post-Failure NICHT
  (D-95-01a). **D-94-08 (Rolling PR via gh pr edit --body-file)** =
  Phase 95 Plans 95-01..04 ergänzen die rolling-summary-table.
  **D-94-09 (Quality Gates Standard)** = Phase 95 erbt die Gates;
  EXPORT_ORDER bumpt auf 25 + SCHEMA_VERSION 1 → 2 (D-95-07 Delta).
  **D-94-12 (WireMock-IT-only Phase-Close + Live-UAT-deferred)** =
  Phase 95 macht's identisch: WireMock-IT-only Phase-95-Close,
  UAT-05 als STATE.md Pending UAT (D-95-10). **D-94-13 (Production
  Path Boundary)** = analoger Scope-Cut für Phase 95 (D-95-11).
  **D-94-15 (Bot-Self-Override Plan 94-04 Gap-Closure)** = bedeutet
  dass der Bot in jedem Channel ein 4. Permission-Overwrite hat
  und PATCH/POST/DELETE-Rechte auf seine eigenen Channels behält —
  kritische Vorbedingung für POST-04 Re-Post-Pfade.

### Phase 93 Hand-off (PRIMARY INPUT — INFRA-Foundation)

- `.planning/phases/93-discord-foundation/93-CONTEXT.md` — **D-93-01
  (UAT-03 Pattern für Live-Discord-UAT)** = Template für UAT-05
  (D-95-10). **D-93-02 (Single-Row findFirstByOrderByIdAsc-Pattern)**
  = nicht direkt anwendbar für `discord_post` (multi-row Tabelle),
  aber das `@ToString.Exclude` Secret-Discipline-Pattern gilt für
  `DiscordPost.webhookToken`. **D-93-04 (DiscordEmojiCache-Shape)** =
  Strukturelles Vorbild falls Phase-95 einen weiteren Cache bräuchte
  (aktuell nicht — discord_post hat selbst keine TTL-Daten).
  **D-93-05 (Per-Plan Nyquist VALIDATION.md)** = Phase 95 erbt
  unverändert.

### Existing-Code-Touchpoints (Phase-95-Scope-Reuse)

- `src/main/java/org/ctc/discord/DiscordWebhookClient.java` —
  Phase-93 INFRA-01, bekommt `editMessageWithAttachments()` Scope-Add
  in Plan 95-01 (D-95-03a).
- `src/main/java/org/ctc/discord/service/DiscordChannelService.java`
  — Phase-94 CHAN-02, bekommt Auto-Team-Cards-Hook am Ende von
  `createMatchChannel` in Plan 95-02 (D-95-01).
- `src/main/java/org/ctc/admin/service/TeamCardService.java` —
  bestehender Per-SeasonTeam-Card-Service, Phase 95 nutzt
  `cardExists()` + `generateCard()` + `getCardPath()` für
  Pre-Flight + Auto-Gen + Refresh-Link (D-95-02).
- `src/main/java/org/ctc/admin/service/SettingsGraphicService.java`
  — Per-Race-Settings-PNG-Render-Service, Phase 95 nutzt
  `generateSettings(Race)` für Multipart-Bundle (D-95-03).
- `src/main/java/org/ctc/admin/service/LineupGraphicService.java`
  — Per-Race-Lineup-PNG-Render-Service, Phase 95 nutzt
  `generateLineup(Race)` für Multipart-Bundle (D-95-03).
- `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java`
  — Per-Match-Match-Results-Byte-Array-Render-Service, Phase 95
  nutzt `generateMatchResults(Match)` direkt für POST-04.
- `src/main/java/org/ctc/domain/service/MatchService.java` — bestehender
  Match-Domain-Service, Phase 95 erweitert `save(MatchForm)` mit
  Pre/Post-Diff-Hook für Schedule-Auto-Edit (D-95-04).
- `src/main/java/org/ctc/domain/model/BaseEntity.java` — `@LastModifiedDate
  LocalDateTime updatedAt` ist die Quelle für POST-04 Stale-Detection
  (`existingPost.updatedAt < match.updatedAt`).
- `src/main/resources/templates/admin/match-detail.html` line 55-58 —
  vorbereiteter `.discord-actions--posts` Placeholder aus Phase 94
  wird in allen 4 Plans gefüllt.

### Backup-Wire-Contract-Reference (D-95-07 SCHEMA_VERSION Bump)

- `src/main/java/org/ctc/backup/schema/BackupSchema.java` — `SCHEMA_VERSION
  = 1` → wird in Plan 95-01 auf `2` gebumpt.
- `src/main/java/org/ctc/backup/serialization/` — 24 existierende
  MixIn-Klassen; Plan 95-01 fügt `DiscordPostMixIn` hinzu.
- `src/main/java/org/ctc/backup/restore/` — 24 existierende
  EntityRestorer-Klassen; Plan 95-01 fügt `DiscordPostRestorer` hinzu.
- `.planning/phases/72-*/72-CONTEXT.md` § D-72-12 (JPA-Metamodel-Kahn-
  Topo-Sort) + § D-72-15 (SCHEMA_VERSION-Bump-Convention) — die
  authoritative References für korrekte Reihenfolge.

### Convention-References

- `CLAUDE.md` § Architectural Principles → Keep Controllers Thin,
  DTOs instead of Entities, No Comment Pollution, RaceLineup as
  Source of Truth.
- `CLAUDE.md` § Conventions → Naming Patterns, Lombok Usage, Annotation
  Order (`@Slf4j @Component @RequiredArgsConstructor`), Logging mit
  parameterized `{}`, CSS-Klassen statt inline-styles.
- `.planning/codebase/TESTING.md` § Test Categorization (`@Tag`) —
  `@Tag("integration")` für `*IT.java`, `@Tag("e2e")` für Playwright,
  untagged für Mockito-Units.
- `.planning/codebase/ARCHITECTURE.md` — Clean 3-tier (Controller →
  Service → Repository) für alle neuen Discord-Pfade.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`DiscordWebhookClient`** (Phase 93): bestehende `execute(url,
  payload, attachments)` Multipart-POST-Methode = Vorlage für
  `editMessageWithAttachments()` Multipart-PATCH-Erweiterung in Plan
  95-01. `MultipartBodyBuilder`-Code-Path identisch, nur
  `.patch().uri("/messages/{messageId}", messageId)` statt `.post()`.
- **`TeamCardService.generateCard(SeasonTeam)` + `cardExists()` +
  `getCardPath()`**: bestehende Per-SeasonTeam-Card-Generation +
  Caching. Phase 95 nutzt Auto-Gen-Fallback in
  `DiscordPostService.postTeamCards(Match)`.
- **`SettingsGraphicService.generateSettings(Race)` +
  `LineupGraphicService.generateLineup(Race)`**: Per-Race-PNG-Services
  liefern PNG-Pfade als `String`. Phase 95 baut Multipart-Bundle
  über `match.getRaces()` mit jeweils einer PNG pro Race als
  Attachment.
- **`MatchResultsGraphicService.generateMatchResults(Match)`**:
  liefert `byte[]` direkt (anders als Settings/Lineup die Pfade
  geben). Plan 95-04 nutzt das byte-Array direkt für
  Single-Attachment-Post.
- **`DiscordTimestamps.longDateTime() + relative()`** (Phase 93):
  Wrap `<t:UNIX:F>` und `<t:UNIX:R>` für SCHEDULE-Embed-Date-Field.
- **`DiscordApiExceptionMapper` + sealed exception hierarchy**
  (Phase 93): 4-Permit-Pattern + `errorCategory` Flash-Badge
  liefert die Failure-UX out-of-the-box; Phase 95 fügt nur die
  5. Kategorie `data-incomplete` für Pre-Flight-Fails hinzu
  (D-95-03b).
- **`BaseEntity.updatedAt`**: `@LastModifiedDate LocalDateTime
  updatedAt` ist die Quelle für POST-04 Stale-Detection — kein
  neues Feld auf `Match` nötig.
- **`Match.getRaces()`**: existing `@OneToMany(...).List<Race>`
  liefert den Race-Stream für Settings/Lineups-Multipart-Bundle.

### Established Patterns

- **Form-DTO + `@Valid` + `BindingResult` + Flash-Attribute** (CLAUDE.md):
  alle 5 neuen MatchController-POST-Endpoints folgen dem Pattern
  (existiert bereits seit Phase 94 für create-channel/move-to-archive).
- **Sealed-Exception-Hierarchy** (Phase 93): 4 Discord-API-Permits
  + `data-incomplete` als BusinessRuleException-Subtyp; Phase 95
  Buttons routen via `try/catch (DiscordApiException) | catch
  (BusinessRuleException)` zur jeweiligen Flash-Category.
- **Service-Layer-Pre-Flight-Predicates** (Phase 94 CHAN-02
  `assert match.homeTeam.discordRoleId != null`): Phase 95
  Settings/Lineups-Pre-Flight (`matchHasCompleteSettings`,
  `matchHasCompleteLineups`) folgt demselben Layout — Service-
  Methoden, Controller liest Predicate als ModelAttribute für
  Button-Visibility.
- **Single-Row-`findFirstByOrderByIdAsc` für Singleton-Configs**
  (Phase 93 D-93-02): NICHT anwendbar auf `discord_post` (multi-
  row). Aber das `@ToString(exclude = {"webhookToken"})` Pattern
  schon (D-95-07).
- **Per-Plan Nyquist VALIDATION.md** (Phase 89+): 4 Plans, 4
  VALIDATION.md.
- **Rolling Draft-PR via `gh pr edit --body-file`** (Phase 92-94):
  Phase 95 erweitert die existierende v1.13-PR um die 95er Plans.
- **`.discord-actions` CSS-Cluster** (Phase 94 D-94-06): bestehende
  responsive-wrap-CSS deckt Mobile-Overflow ab; Phase 95 fügt
  `.discord-actions--posts` Sub-Cluster für die Post-Buttons + die
  `.error-badge--data-incomplete` Variante hinzu (in-milestone-polish
  [[feedback-in-milestone-polish]]).
- **Bot-Self-Override-Discipline** (Phase 94 D-94-15): bestehende
  4-Permission-Overwrites pro Match-Channel garantieren dass der
  Bot `PATCH /channels/{id}/messages/{msgId}` machen darf —
  Vorbedingung für alle POST-01..05 Re-Post-Pfade. Wenn UAT-04
  noch nicht durch ist, blockiert das die Phase-95-Live-UAT-05
  (aber NICHT die WireMock-IT-only Phase-95-Close).

### Integration Points

- **`DiscordChannelService.createMatchChannel(Match)` ←
  `discordPostService.postTeamCards(match)`** (Plan 95-02): Auto-
  Post-Hook am Ende von Channel-Create.
- **`MatchService.save(MatchForm)` ←
  `discordPostService.autoEditScheduleIfNeeded(match)`** (Plan 95-04):
  Pre/Post-Diff-Hook nach Schedule-Field-Change.
- **`MatchController.{post,refresh}*` → `DiscordPostService.{post*,...}`**
  (Plans 95-02..04): 5 neue POST-Endpoints (`post-team-cards`,
  `refresh-team-cards`, `post-settings`, `post-lineups`, `post-match-results`,
  `post-schedule`) — alle via Controller-thin-Pattern delegieren an
  `DiscordPostService`.
- **`DiscordPostController` → `DiscordPostRepository.findAll(Specification)`**
  (Plan 95-01): Filter-Form-DTO + Pageable für die Listing-Page.
- **`backup/serialization/DiscordPostMixIn` + `backup/restore/DiscordPostRestorer`**
  (Plan 95-01): SCHEMA_VERSION-Bump-Integration.

</code_context>

<specifics>
## Specific Ideas

- **Auto-Post Team Cards bei Channel-Create** ist eine explizite
  User-Forderung aus dem Phase-94-UAT-04-Retro (`95-DISCUSSION-INPUT.md`
  Q-95-01) — *nicht* aus dem ursprünglichen Design-Spec abgeleitet.
  Hybrid-Resolution (D-95-01) ist die Antwort: Auto-Post für 1-Klick-
  Happy-Path + Re-Post-Button für Recovery.
- **Refresh Team Cards-Link** (D-95-02) ist eine User-getriebene
  Workflow-Optimierung: bei Logo-/Lineup-Updates zwischen Channel-
  Creation und MD-Start (selten, aber realistisch) erspart der Link
  den `/admin/tools/team-cards`-Detour.
- **Multipart-Bundle für Settings/Lineups statt Composite-PNG**
  (D-95-03) folgt dem Discord-native Pattern (mehrere Attachments
  pro Message) statt einer aufwendigen neuen Grid-Template — vermeidet
  die Pixel-Positionierung-Iteration aus
  [[feedback-graphic-pixel-positioning]].
- **No-Race.dateTime-Trigger** (D-95-04a) ist eine minimale-Coupling-
  Entscheidung: RaceService bleibt Discord-frei in v1.13.

</specifics>

<deferred>
## Deferred Ideas

- **Race.dateTime-Änderung triggert Schedule-Embed-Auto-Edit** —
  DISC-FUTURE Ticket für v1.14, falls Edge-Case in Practice häufig
  wird. Aktuell: manueller Re-Post-Button-Klick reicht.
- **Spring ApplicationEvent für Discord-Auto-Edit-Hooks** — YAGNI
  in v1.13. Refactor-Kandidat für v1.14 wenn Phase 97 MATCH_PREVIEW-
  Auto-Edit + Phase 96 RACE_RESULTS-Auto-Edit den 3. Konsumenten
  bringen würden.
- **K-von-N Settings/Lineups-Posting (iterativer Workflow)** —
  Pre-Flight strikt (D-95-03b) ist die aktuelle Wahl; falls
  Operator-Feedback nach Phase 95 zeigt dass partial-Posten ein
  echter Use-Case ist, kann Phase 97/98 einen K-von-N-Modus
  hinzufügen.
- **`/admin/discord/posts`-Page mit Bulk-Re-Post-Button** — aktuell
  nur per-Row-Buttons. Bulk-Re-Post (z.B. "Re-Post all SETTINGS für
  Matchday X") könnte v1.14-Wunsch sein.

</deferred>

---

*Phase: 95-match-channel-posts*
*Context gathered: 2026-05-22*
