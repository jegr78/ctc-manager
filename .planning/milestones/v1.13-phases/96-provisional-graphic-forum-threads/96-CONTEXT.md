# Phase 96: Provisional Graphic + Forum Threads - Context

**Gathered:** 2026-05-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Liefere drei eng zusammenhängende, aber distinkte Capabilities:

1. **GRAFX-01** — Neuer `ProvisionalScoresGraphicService` (per-Race PNG mit
   `match-card`-Header + 2 per-Team-Blöcken im "Per-Race-Detail"-Layout
   analog der heutigen Google-Sheets-Vorlage); plus "Post Provisional
   Scores" Multipart-Button auf Match-Detail (N Attachments = N
   abgeschlossene Races; 1 Discord-Message, 1 `discord_post`-Row mit
   `match_id`).
2. **FORUM-01** — Flyway V13 fügt 2 Spalten zu `seasons` hinzu
   (`discord_race_results_thread_id` + `discord_standings_thread_id`)
   plus 2 Spalten zu `discord_global_config`
   (`race_results_forum_webhook_url` + `standings_forum_webhook_url`).
   Discord-Config-Form bekommt 2 neue Felder. Season-Edit-Page bekommt
   "Discord Integration" Section mit 2 Thread-Linker-Widgets
   (Link existing / Unlink — KEIN Create-new-Thread, siehe D-96-FOR-1c).
3. **FORUM-02** — `DiscordPostService.postOrEdit` Extension für
   `SeasonRef`/`RaceRef` + `?thread_id={id}` Query-Param im
   `DiscordWebhookClient` (Phase 95 ließ explizites
   `UnsupportedOperationException` für non-MatchRef-Branches);
   Race-Detail "Post Race Result to Forum-Thread" Button mit
   Auto-Unarchive-vor-Post-Logic + No-Re-Archive-After-Post (D-96-FOR-4a).

Phase 96 baut auf der Phase-95-Post-Plattform (`DiscordPostService.postOrEdit`,
`DiscordWebhookClient.executeMultipart`, `editMessageWithAttachments`,
sealed-`DiscordApiException`-Hierarchie, `discord_post`-Entity, Multipart-
Bundle-Pattern aus Plan 95-03 Settings/Lineups). Drei sequenzielle inline-
Plans auf `gsd/v1.13-discord-integration` per
[[feedback-inline-sequential-execution]] + [[feedback-wave-pause]], mapping
1:1 zu Plan 96-01 (GRAFX-01 — Service + Template + Match-Detail-Multipart-
Button) / Plan 96-02 (FORUM-01 — V13 Schema + Config-Form + Season-Edit
Section + DiscordForumService + Link-Modal) / Plan 96-03 (FORUM-02 —
DiscordPostService SeasonRef/RaceRef + ?thread_id-Query-Plumbing +
Race-Detail-Button + Auto-Unarchive) — entspricht Design-Spec § 5 + ROADMAP
Schätzung 3 Plans.

In scope:

- **Plan 96-01 (GRAFX-01)** — Per-Race Provisional Graphic + Multipart-Post:
  - **`ProvisionalScoresGraphicService`** in `org.ctc.admin.service`
    (sibling zu `MatchResultsGraphicService`), `extends AbstractGraphicService
    implements TemplateManageable` (CTC-Konvention für custom-template-
    overrides). API: `byte[] generateProvisional(Race race)` (per-Race-PNG,
    NICHT per-Match — D-96-GRX-1a).
  - **Thymeleaf-Template** `src/main/resources/templates/admin/
    provisional-scores-render.html` analog `match-results-render.html`:
    - **`match-card`-Header** (home + away team-cards via base64 encoded
      über `encodeCardBase64()` aus `AbstractGraphicService`, identisch zu
      MatchResults). Match-Total-Badge im Header optional ("Match Score
      after this race: H X | A Y").
    - **2 Per-Team-Blöcke** (home oben, away unten) mit per-Driver-Rows:
      - Spalten: `Driver | Position | Quali | FL | Pts-Race | Pts-Quali |
        Pts-FL | Total` (Erweiterung gegenüber MatchResults — Position/
        Quali/FL kommen aus `RaceResult` direkt).
      - Sortierung: by `race.results` natural-order (Position ASC), pro
        Team gefiltert.
      - **"Overall" Footer-Row pro Block**: Σ Pts-Race, Σ Pts-Quali, Σ
        Pts-FL, Σ Total (über die 4 numeric Spalten).
    - **PROVISIONAL-Banner / Watermark** ist NICHT explizit nötig — User-
      Direktive: "kombinieren können mit Race Results" + Re-Post ersetzt
      immer das vorherige PNG, also unmissverständlich der aktuelle
      Race-Stand.
    - Pixel-Iteration via `playwright-cli` per
      [[feedback-graphic-pixel-positioning]] +
      [[feedback-graphic-design-iteration]]: User-Reference-Screenshot
      (Google-Sheets-Vorlage + heutige Results-Tabelle aus dem Chat
      2026-05-23) wird unter `.screenshots/96-01/provisional-reference.png`
      abgelegt; kleine Commits, User prüft pro Iteration.
  - **Match-Detail Multipart-Post-Button** (D-96-GRX-1b — gleiche Topology
    wie Phase 95 D-95-03 Settings/Lineups):
    - **Pre-Flight**: Button visible wenn `match.final == false` UND
      ≥ 1 Race im Match hat `race.results.isEmpty() == false`. Disabled
      mit Tooltip wenn `match.final == true` ("Match is final — post
      Match Results instead") oder wenn `0 Races have results`
      ("Provisional needs at least one completed race"). Pre-Flight-
      Predicate-Method `matchHasProvisionalData(match)` im Service-Layer
      (analog zu `matchHasCompleteSettings` aus Phase 95).
    - **Multipart-Bundle-Logic**: `match.getRaces().stream()
      .filter(r -> !r.getResults().isEmpty())
      .sorted(Comparator.comparingInt(Race::getRaceNumber))
      .map(race -> NamedAttachment.of("provisional-race-" +
      race.getRaceNumber() + ".png",
      provisionalScoresGraphicService.generateProvisional(race)))
      .toList();` — analog zu Plan 95-03 Settings-Bundle.
    - **`DiscordPostService.postProvisionalScores(Match)`**: ruft
      `postOrEdit(channelId, webhookUrl, PROVISIONAL_SCORES,
      WebhookPayload.empty(), bundle, DiscordPostRef.match(match))`.
      MatchRef-Branch ist bereits in Phase 95 implementiert — kein
      DiscordPostService-Extension nötig in Plan 96-01.
    - **Re-Post-Pfad**: identisch zu Phase-95 Multipart-PATCH (replaces
      all attachments atomically) — wenn nach Race-2 alle Provisionals
      neu gepostet werden müssen, ersetzt der Re-Post-Klick das
      vorherige PNG-Set.
    - **Discord-Multipart-Limit**: 10 Attachments + 25 MB. CTC-Match-Footprint
      max 4 Races à ~200 KB = ~800 KB → unkritisch (analog Phase 95
      Settings/Lineups-Analyse).
  - **Match-Detail Discord-Actions-Panel Erweiterung** in `templates/admin/
    match-detail.html` `.discord-actions--posts` Cluster:
    ```
    Provisional   [Post Provisional Scores]   ← visible wenn !PROVISIONAL_SCORES-row exists AND matchHasProvisionalData
                  [Re-Post Provisional]       ← visible wenn PROVISIONAL_SCORES-row exists
    ```
    Keine inline-Styles ([[feedback-no-inline-styles]]); falls neue CSS
    nötig (z.B. `.btn-provisional`-Variante), in Plan 96-01 inline
    ([[feedback-in-milestone-polish]]).
  - **Tests** (Plan 96-01):
    - `ProvisionalScoresGraphicServiceTest` (Mockito-only Unit — Template-
      Rendering + Bytes-Output + Empty-Results-IllegalStateException).
    - `DiscordPostServiceProvisionalScoresIT` (WireMock — Multipart-POST
      mit N PNGs + PATCH re-edit).
    - `MatchControllerProvisionalPostIT` (WireMock — Controller-Endpoint
      `POST /admin/matches/{id}/post-provisional` + Pre-Flight branches).
    - `MatchDetailProvisionalButtonsE2ETest` (Playwright + Mobile-Sweep
      per [[feedback-playwright-cli]]).
    - Optional: Visual-Regression-Snapshot über pixel-Hash gegen
      `.screenshots/96-01/provisional-reference.png` — als deferred
      Idee, nicht blocking für Plan-Close.

- **Plan 96-02 (FORUM-01)** — Schema + Thread-Linker UI:
  - **Flyway V13** `V13__add_seasons_discord_threads_and_forum_webhooks.sql`:
    ```sql
    ALTER TABLE discord_global_config
        ADD COLUMN race_results_forum_webhook_url VARCHAR(500);
    ALTER TABLE discord_global_config
        ADD COLUMN standings_forum_webhook_url VARCHAR(500);
    ALTER TABLE seasons
        ADD COLUMN discord_race_results_thread_id VARCHAR(32);
    ALTER TABLE seasons
        ADD COLUMN discord_standings_thread_id VARCHAR(32);
    ```
    H2 + MariaDB kompatibel (4 nullable ADD COLUMN, kein Index nötig
    da Lookup über `seasons.id` läuft, nicht über thread-ID). Existing
    channel-IDs aus V8 bleiben (D-96-FOR-2a — werden für die Thread-
    Enumeration des Modal-Pickers gebraucht).
  - **`DiscordGlobalConfig` Entity** erweitert um 2 neue String-Felder
    + `@ToString.Exclude` auf beiden (Webhook-URLs sind Secrets per
    Phase 93 D-93-02-Sec [[feedback-no-comment-pollution]] erstreckt
    sich aufs Konventions-Pattern). Lombok generiert Getter/Setter.
  - **`DiscordConfigForm` DTO** erweitert um `raceResultsForumWebhookUrl`
    + `standingsForumWebhookUrl` Felder (`@URL`-validation analog zu
    `announcementWebhookUrl`). `@NotBlank` NICHT setzen — beide sind
    optional bis Operator FORUM-02-Posts aktiv nutzt.
  - **`templates/admin/discord-config.html` Erweiterung**: 2 neue
    Form-Felder im "Forum Channels" Cluster (oder neuer Cluster
    "Forum Webhooks"). Layout im selben `.card` Container.
  - **`DiscordForumService`** (NEU) in `org.ctc.discord.service`
    (sibling zu `DiscordPostService` aus Phase 95). API:
    ```java
    public List<ThreadInfo> listThreads(String forumChannelId);
        // Combined: listActiveThreads(guildId).filter(parent_id == forumChannelId)
        //        + listArchivedThreads(forumChannelId)
        // Sort: pinned > active (last_message_timestamp desc) > archived (last_message_timestamp desc)
    ```
    Re-Use des existierenden `DiscordRestClient.listActiveThreads(guildId)`
    + `listArchivedThreads(channelId)`. Filter active-list by `parent_id ==
    forumChannelId` (Discord's GET active gibt ALLE Active-Threads im
    Guild zurück; Phase 96 muss client-side filtern).
  - **`Thread` DTO** (existing aus Phase 93) erweitern um `pinned`-Flag
    (oder `flags` int + bit-mask) wenn Discord die `pinned`-Info in
    der Response liefert (laut Discord-Docs: `thread.flags` bit 1 ist
    `PINNED`). Falls Bot-Permission unzureichend für `flags`-Field
    → Fallback: pinned-detection skip, alle als active behandeln.
    Planner-Discretion (D-96-FOR-2b).
  - **Season-Edit "Discord Integration" Section** in `templates/admin/
    season-form.html` (oder `season-detail.html` — D-96-FOR-2c, see
    Claude's Discretion):
    ```
    ── Discord Integration ──────────────────────────────────
    race-results Forum-Thread:
      ● linked → "Season 4 - 2026" (ID: 1234567890123)
        [Change Link...] [Unlink]
      ○ not linked
        [Link existing Thread...]

    standings Forum-Thread:
      (same pattern)
    ```
  - **"Link existing Thread..." Modal**: Bot ruft
    `discordForumService.listThreads(forumChannelId)`. Liste zeigt
    `{thread.name} (ID: {thread.id}) {(pinned)}/(archived)/`. Pinned
    Thread vorausgewählt (D-96-FOR-2d — User-direktive analog zu
    `Move-to-Archive`-Modal Pattern aus Phase 94). Operator kann
    übersteuern. Confirm → POST `/admin/seasons/{id}/link-thread`
    mit thread-id + which-thread (`race-results` | `standings`).
    `seasonService.linkRaceResultsThread(seasonId, threadId)` /
    `linkStandingsThread(seasonId, threadId)` Mutator-Methoden.
  - **"Unlink" Button**: clears DB field nur. POST
    `/admin/seasons/{id}/unlink-thread?type=race-results|standings`.
    Discord-Thread bleibt unangetastet (per FORUM-01-Requirement-Text).
  - **NO "Create new Thread..." Modal** (D-96-FOR-1c — User-Direktive
    2026-05-23): Operator erstellt Threads direkt im Discord-Client
    pro Saison; App muss keine Create-Thread-Workflow unterstützen.
    `DiscordForumService.createThread()` ist NICHT Phase-96-Scope
    (deferred falls je benötigt).
  - **Tests** (Plan 96-02):
    - `DiscordForumServiceTest` (Mockito — listThreads Sort + Filter
      + Pinned-Detection-Fallback).
    - `DiscordForumServiceIT` (WireMock — listActive + listArchived
      + Empty-Forum-Edge-Case).
    - `SeasonControllerLinkThreadIT` (link + unlink endpoints).
    - `SeasonEditDiscordSectionE2ETest` (Playwright — Modal-Open +
      Pinned-Auto-Select + Submit + Unlink + Mobile-Sweep).

- **Plan 96-03 (FORUM-02)** — DiscordPostService SeasonRef/RaceRef +
  ?thread_id= Plumbing + Race-Detail Button + Auto-Unarchive:
  - **`DiscordPostService.postOrEdit` Extension** (gegenüber Phase 95
    MatchRef-only):
    ```java
    @Transactional
    public DiscordPost postOrEdit(
        String channelId,           // forum-channel ID oder match-channel ID
        String webhookUrl,
        DiscordPostType type,
        WebhookPayload payload,
        List<NamedAttachment> attachments,
        DiscordPostRef ref,
        @Nullable String threadId   // NEU: wenn != null, ?thread_id={id} angehängt
    ) throws DiscordApiException {
      // existing host-validator + parseWebhookUrl
      // Replace MatchRef-only-guard with sealed-switch:
      Optional<DiscordPost> existing = switch (ref) {
        case DiscordPostRef.MatchRef m -> repo.findByChannelIdAndPostTypeAndMatchId(channelId, type, m.matchId());
        case DiscordPostRef.RaceRef r -> repo.findByChannelIdAndPostTypeAndRaceId(channelId, type, r.raceId());
        case DiscordPostRef.SeasonRef s -> repo.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.seasonId());
        case DiscordPostRef.MatchdayRef d -> repo.findByChannelIdAndPostTypeAndMatchdayId(channelId, type, d.matchdayId());
      };
      // existing post-or-edit branch, but threadId propagates into
      // webhookClient.executeMultipart(url, payload, attachments, threadId);
      // webhookClient.editMessageWithAttachments(url, msgId, payload, attachments, threadId);
    }
    ```
  - **`DiscordPostRepository` Erweiterung**: 3 neue Lookup-Methoden
    (`findByChannelIdAndPostTypeAndRaceId`,
    `findByChannelIdAndPostTypeAndSeasonId`,
    `findByChannelIdAndPostTypeAndMatchdayId`). Spring-Data-JPA
    derived queries — keine Custom-JPQL nötig.
  - **`DiscordWebhookClient` ?thread_id= Plumbing** (D-96-FOR-3a):
    Method-Overloads ODER neuer `WebhookTarget`-Record-Wrapper
    (`record WebhookTarget(String webhookUrl, @Nullable String threadId)`)
    — Planner-Discretion. Variante A: Overload aller 4 existing methods
    (`execute`, `executeMultipart`, `editMessage`,
    `editMessageWithAttachments`) mit optionalem `threadId`-Param.
    Variante B: WebhookTarget-Wrapper als 1. Param. Variante A ist
    inkremental und bricht keine Phase-95-Callsites, Variante B ist
    cleaner aber refactor-intensiv.
    `?thread_id={id}`-Append im URL-Builder (Spring `UriComponentsBuilder`
    oder simpler String-Append wenn URL-Validation upfront).
  - **`SeasonRef` + `RaceRef`-Sealed-Record-Permits** (D-96-FOR-3b):
    `DiscordPostRef`-Hierarchie um die fehlenden Permits ergänzen.
    Aus Phase 95 ist die `MatchRef + MatchdayRef + RaceRef + SeasonRef`-
    Topologie bereits vorgesehen (D-95-12), aber nur MatchRef
    instanziiert. Phase 96 implementiert RaceRef + SeasonRef.
    MatchdayRef bleibt Phase-97-Scope (POST-07).
  - **`hostValidator.requireAllowed(webhookUrl)`** unverändert — die
    2 neuen Forum-Webhook-URLs gehen über existierende
    `DiscordHostValidator` Phase-93-Allowlist (`discord.com`).
  - **Auto-Unarchive Logic** (D-96-FOR-4 — angewendet bei jedem
    forum-thread Post-or-Edit):
    ```java
    private void unarchiveIfArchived(String threadId) {
      // GET /channels/{threadId} → check thread.thread_metadata.archived
      Channel thread = discordRestClient.fetchChannel(threadId);
      if (thread.threadMetadata() != null && thread.threadMetadata().archived()) {
        log.info("Unarchiving forum thread {} before post", threadId);
        discordRestClient.modifyChannel(threadId, ChannelModifyRequest.unarchive());
      }
    }
    ```
    Aufgerufen vor jedem `executeMultipart`-Call wenn threadId != null.
    Per D-96-FOR-4a: KEIN Re-Archive nach Post — Discord's natürliche
    Inactivity-Auto-Archive übernimmt das.
    `ChannelModifyRequest.unarchive()` ist eine NEUE Helper-Factory
    auf dem existing ChannelModifyRequest-DTO aus Phase 94 (analog
    zu `move-to-category`). Plan 96-03 fügt das hinzu.
  - **Race-Detail "Post Race Result to Forum-Thread" Button**:
    - **Pre-Flight** (D-96-FOR-3c): Visible wenn (a) `race.results.isEmpty()
      == false` UND (b) `season.discordRaceResultsThreadId != null` UND
      (c) `discordGlobalConfig.raceResultsForumWebhookUrl != null`.
      Sonst disabled mit Tooltip: jeweils erklärende Message
      ("No race results yet" / "Link a race-results thread first" /
      "Configure race-results forum-webhook in Discord settings").
    - **Service-Method**:
      ```java
      @Transactional
      public DiscordPost postRaceResultToForumThread(Race race) {
        Season season = race.getMatchday().getSeason();
        String threadId = season.getDiscordRaceResultsThreadId();
        String webhookUrl = discordGlobalConfigService.getOrThrow().getRaceResultsForumWebhookUrl();
        byte[] png = resultsGraphicService.generateResultsBytes(race); // see below
        NamedAttachment att = NamedAttachment.of(
            "race-result-" + race.getMatchday().getLabel() + "-" + race.getId() + ".png",
            png);
        unarchiveIfArchived(threadId);
        return postOrEdit(
            extractChannelIdFromWebhook(webhookUrl),
            webhookUrl,
            DiscordPostType.RACE_RESULTS,
            WebhookPayload.empty(),
            List.of(att),
            DiscordPostRef.race(race),
            threadId);
      }
      ```
    - **PNG-Source**: `ResultsGraphicService.generateResults(Race)`
      gibt heute `String` (uploads-URL) zurück — Plan 96-03 fügt
      `generateResultsBytes(Race race) throws IOException` als
      `byte[]`-Variante hinzu (D-96-FOR-3d). Reuse: gleicher Render-
      Logic, schreibt nicht auf Disk, returnt PNG-Bytes direkt
      (analog `MatchResultsGraphicService.generateMatchResults(Match)
      → byte[]`).
  - **Race-Detail Discord-Actions-Panel** in `templates/admin/race-detail.html`
    (NEU — Phase 95 hatte nur Match-Detail-Discord-Actions). Cluster
    `.discord-actions--posts` analog Match-Detail-Pattern:
    ```
    Race Results to Forum:
      [Post Race Result]    ← visible wenn RACE_RESULTS-row !exists AND pre-flight ok
      [Re-Post Race Result] ← visible wenn RACE_RESULTS-row exists
    ```
  - **Tests** (Plan 96-03):
    - `DiscordPostServiceForumThreadIT` (WireMock — post + re-post
      mit ?thread_id= im URL + Auto-Unarchive-vor-Post).
    - `DiscordPostServiceRefBranchesTest` (Mockito — alle 4 RefBranches
      via Sealed-Switch + Repository-Lookup-Dispatch).
    - `DiscordWebhookClientThreadIdIT` (WireMock — ?thread_id= URL-
      Append in alle 4 methods).
    - `RaceControllerPostRaceResultToForumIT` (Controller-Endpoint +
      Pre-Flight-Branches).
    - `RaceDetailForumPostButtonE2ETest` (Playwright + Mobile-Sweep).

</domain>

<decisions>
## Implementation Decisions

### Provisional-Scores Layout (Q-96-01)

- **D-96-GRX-1: Anlehnung an MatchResults + Erweiterung um
  per-Race-Detail-Spalten.** ProvisionalScoresGraphicService leitet das
  Layout aus `match-results-render.html` + `ResultsGraphicService`
  ab (KEINE externe Vorlage außer den heutigen User-Reference-Screenshots
  aus Chat 2026-05-23). Iterative playwright-cli-Schleife per
  [[feedback-graphic-pixel-positioning]] +
  [[feedback-graphic-design-iteration]]. REJECTED: Anlehnung an
  Google-Sheets-Pixel-Layout (User hat KEIN Sheet-URL geteilt,
  match-results-Konvention ist konsistenter); ASCII-spec-in-CONTEXT
  (zu vage); externe-Mockup-Datei (kein Mockup vorhanden).

- **D-96-GRX-1a: Per-Race-PNG, nicht per-Match.** Jede Race im Match
  produziert eine eigene Provisional-Grafik. KEIN aggregiertes
  Match-PNG. User-Direktive 2026-05-23: "Je Rennen eine eigene
  Provisional Grafik. Eine Komplettgrafik wird nicht benötigt." Layout:
  match-card-Header + 2 Team-Blöcke (home/away) mit per-Driver-Detail
  (`Driver | Position | Quali | FL | Pts-Race | Pts-Quali | Pts-FL | Total`)
  + Overall-Row pro Block. REJECTED: Alle-Races-stacked (visuelle
  Überlast); Aggregat-pro-Driver (verliert Race-Granularität);
  Latest-Race+Match-Total-Badge (vermischt Race-Detail und
  Match-Aggregat unklar).

- **D-96-GRX-1b: Multipart-Bundle auf Match-Detail (1 Discord-Message,
  N PNGs).** Analog Phase 95 D-95-03 Settings/Lineups: ein Klick
  postet alle abgeschlossenen Races als N-Attachment-Multipart-Bundle
  zum match-channel-Webhook; Re-Post-PATCH ersetzt alle Attachments
  atomar. 1 `discord_post`-Row mit `match_id` + `PROVISIONAL_SCORES`-Type.
  User-Direktive 2026-05-23: "Option 4 [Multipart: 1 Post mit allen
  abgeschlossenen Races]". REJECTED: Race-Detail-per-Race-Buttons
  (vervielfältigt Discord-Messages, Operator hätte N statt 1 Post
  pro Match); Match-Detail-mit-per-Race-Zeile (UI-Aufblähung,
  N-Buttons im Discord-Actions-Cluster); Beide-Orte-gespiegelt
  (Synchronisations-Risiko).

- **D-96-GRX-1c: Provisional-Target = match-channel only, NIE
  forum-thread.** User-Direktive 2026-05-23: "Provisional Results
  werden nur im Match Day Kanal gepostet. Diese landen nie im Results
  Forum Thread." Damit ist die ROADMAP-Wortlaut-Mehrdeutigkeit
  ("applies to both Race-Result and Provisional-Scores Forum-Thread
  posts" in FORUM-02-Requirement) aufgelöst: Auto-Unarchive-Logic
  applies nur zu RACE_RESULTS, nicht zu PROVISIONAL_SCORES. Phase 96-03
  Tests prüfen explizit dass PROVISIONAL_SCORES-Posts KEIN ?thread_id=
  enthalten.

### Forum-Channel-Webhook-Setup (Q-96-02)

- **D-96-FOR-1: Operator pastet 2 Webhook-URLs in discord-config
  (Mittel-Variante mit Picker + Auto-Pre-Select).** Discord-Config-Form
  bekommt 2 neue Felder (`raceResultsForumWebhookUrl` +
  `standingsForumWebhookUrl`). Operator erstellt die Webhooks manuell
  im Discord-Client (Server Settings → Integrations → Webhooks → New
  Webhook auf race-results-forum-channel + standings-forum-channel).
  Parallel zum announcement-webhook-Setup aus Phase 93 — explicit +
  sichtbar. User-Direktive 2026-05-23: "Mittel: Webhook-URLs + Picker
  mit Auto-Pre-Select". REJECTED: Bot-Direkt-POST (bricht "all-via-
  webhook"-Invariante aus § 3.1, heterogener Edit-Pfad); Bot-auto-
  create-Webhook (User wollte explizite/sichtbare Setup-UX); Mittel-
  minus mit channel-ID-Resolution (extra Bot-Call beim Save, kein
  spürbarer Nutzen).

- **D-96-FOR-1b: Existing channel-IDs aus V8 bleiben.** Werden weiterhin
  für die Thread-Enumeration des "Link existing Thread..."-Modal
  gebraucht (`DiscordForumService.listThreads(forumChannelId)` braucht
  die channel-ID, nicht die webhook-URL). User-Diskussion 2026-05-23
  hat klargestellt dass channel-ID + webhook-URL komplementäre Rollen
  haben: channel-ID = Thread-Enumeration + Create-Thread (Letzteres
  dropped, siehe D-96-FOR-1c), webhook-URL = Posting.

- **D-96-FOR-1c: KEIN "Create new Thread..." Modal — Operator erstellt
  Threads manuell pro Saison im Discord-Client.** User-Direktive
  2026-05-23: "Der Bot muss keine neuen Threads hier erstellen. Das
  macht der Operator direkt im Discord für jede Saison." Scope-
  Reduktion gegenüber FORUM-01-Requirement-Text. Begründung: weniger
  UI-Code, weniger Bot-Calls, weniger Test-Aufwand; Operator-Friction
  unverändert (manueller Setup einmalig pro Saison, ~1 Min). Deferred:
  falls je benötigt → DISC-FUTURE Ticket für Phase 99+ /
  v1.14 Backlog.

### FORUM-01 Thread-Linker UI (Q-96-02 cont.)

- **D-96-FOR-2: Modal-Picker mit Auto-Pre-Select des pinned Threads.**
  Season-Edit "Discord Integration"-Section zeigt 2 Thread-Linker-
  Widgets. "Link existing Thread..."-Klick öffnet Modal mit
  Thread-Liste (active + archived) via `DiscordForumService.listThreads
  (forumChannelId)`. Sortierung: pinned > active (last_message_timestamp
  desc) > archived (last_message_timestamp desc). Pinned Thread
  vorausgewählt — Operator confirmt oder übersteuert. User-Direktive
  2026-05-23: "ähnlich zur Auswahl bei der Move To Archive Aktion
  bei den Match Day Kanälen". Channel-Picker-Pattern aus Phase 94
  D-94-06 wird wiederverwendet. Pagination: erste 100 archived threads
  reichen für CTC-Test-Guild (deferred falls je >100 archived).

- **D-96-FOR-2a: Existing `Thread` DTO bekommt `pinned`-Flag.**
  Discord liefert `thread.flags` int (Bitfield) oder
  `thread.thread_metadata.archived` boolean in der Response. Plan 96-02
  prüft API-Response-Shape gegen Discord-Docs + erweitert die existing
  `Thread`-record-DTO. Falls Bot-Permission oder API-Variant pinned-
  detection blockt → Fallback: alle als active behandeln, kein
  pre-select. Planner-Discretion (Detail-Implementation).

- **D-96-FOR-2b: Season-Edit-Page-Location (NICHT Season-Detail).**
  Discord-Integration-Section liegt im Edit-Formular `season-form.html`
  (oder ein eigenes `/admin/seasons/{id}/discord-integration` sub-page,
  wenn Form zu groß wird). REJECTED: Season-Detail-Read-Only (Operator
  muss die Felder editieren können — Edit-Form ist der natürliche
  Ort). Planner-Discretion: Form-inline vs dedicated-sub-page.

### FORUM-02 Race-Result-Post + DiscordPostService Extension (Q-96-03 implicit)

- **D-96-FOR-3: `DiscordPostService.postOrEdit` Extension zu allen 4
  DiscordPostRef-Permits + `@Nullable String threadId`-Param.** Phase 95
  Plan 95-01 ließ explizites `UnsupportedOperationException` für
  non-MatchRef-Branches; Plan 96-03 implementiert RaceRef + SeasonRef
  vollständig. MatchdayRef bleibt Phase-97-Scope (POST-07). Sealed-Switch
  dispatcht zu existing Repository-Lookup-Methods (`findByChannelIdAnd
  PostTypeAndRaceId` / `findByChannelIdAndPostTypeAndSeasonId` neu
  hinzugefügt). Phase 95 D-95-12 hatte die DiscordPostRef-Sealed-
  Hierarchie bereits vorgesehen, jetzt erst voll ausgenutzt.

- **D-96-FOR-3a: `?thread_id={id}` Query-Param via Method-Overload
  (Variante A).** `DiscordWebhookClient.execute/executeMultipart/
  editMessage/editMessageWithAttachments` bekommen Overload-Variante
  mit `@Nullable String threadId`-Param. Append via `UriComponentsBuilder
  .fromUriString(webhookUrl).queryParam("thread_id", threadId).build().toUri()`.
  Existing Phase-95-Callsites unverändert (kein Refactor-Risiko).
  REJECTED: `WebhookTarget`-Wrapper-Record (Variante B) — cleaner
  langfristig, aber Refactor-Aufwand auf alle Phase-95-Callsites
  (~6 Methoden × 2 Tests/Methode) erhöht Plan-Größe unnötig.

- **D-96-FOR-3b: `DiscordPostRef.RaceRef` + `SeasonRef` Sealed-Permits
  ergänzt.** Phase 95 D-95-12 hatte die Sealed-Hierarchy umrissen,
  aber nur `MatchRef` instanziiert (`record MatchRef(UUID matchId)
  implements DiscordPostRef`). Plan 96-03 fügt
  `record RaceRef(UUID raceId)` + `record SeasonRef(UUID seasonId)`
  als Permits hinzu. Helper-Factory-Methoden:
  `DiscordPostRef.race(Race race)` + `DiscordPostRef.season(Season s)`.
  `applyTo(DiscordPost row)` setzt das jeweilige FK-Feld. Repository-
  Lookup-Dispatch via sealed-switch wie unter D-96-FOR-3 spezifiziert.

- **D-96-FOR-3c: Pre-Flight-Predicates auf Race-Detail Button.**
  Visible wenn alle 3 Bedingungen: (a) `race.results.isEmpty() == false`,
  (b) `season.discordRaceResultsThreadId != null`, (c)
  `discordGlobalConfig.raceResultsForumWebhookUrl != null`. Service-
  Layer-Pre-Flight (Methode `canPostRaceResultToForum(Race race)`),
  Template liest Boolean-Predicate als ModelAttribute (analog Phase 95
  Settings/Lineups-Pattern). Tooltip-Messages je nach failing Predicate
  (3 distinct tooltips).

- **D-96-FOR-3d: `ResultsGraphicService` bekommt `byte[]`-Variante.**
  Existing `generateResults(Race race) → String` (uploads-URL) bleibt
  unverändert. Neu: `generateResultsBytes(Race race) throws IOException
  → byte[]` — identischer Render-Logic, kein Disk-Write, Output direkt
  als Bytes. Analog zu `MatchResultsGraphicService.generateMatchResults
  (Match) → byte[]` (Phase 95). FORUM-02-Post-Pfad nutzt die
  byte[]-Variante; existing Race-Detail "Generate Results Graphic"-
  Button bleibt auf der String-Variante (Persist-to-Disk-Path).

### Auto-Unarchive + Re-Archive (Q-96-04)

- **D-96-FOR-4: Auto-Unarchive vor jedem forum-thread-Post; KEIN
  Re-Archive nach dem Post.** Bot prüft `thread.thread_metadata.archived`
  vor dem Post (1 GET-Call); wenn archived → PATCH archived=false
  (1 PATCH-Call) → POST. Nach dem Post wird der Thread NICHT wieder
  archiviert — Discord's natürliche Inactivity-Auto-Archive
  (24h/3d/7d/30d je nach Thread-Setting) übernimmt das. User-Direktive
  2026-05-23: "Nie re-archive (Spec-Default)". Minimaler Code,
  minimale Bot-Calls (1 GET + 1 PATCH + 1 POST + 0 PATCH re-archive
  = 3 statt 4 Calls). REJECTED: app.yml-Flag (YAGNI); UI-Checkbox
  (überzogen für seltene Funktion); Pre-Status-Restaurieren (Discord
  archived es eh automatisch — Bot-Re-Archive wäre redundant).

- **D-96-FOR-4a: Auto-Unarchive applies zu ALLEN forum-thread Posts.**
  Race-Result (FORUM-02), Provisional-Scores (D-96-GRX-1c: NIE
  forum-thread → kein Auto-Unarchive nötig), Matchday-Overview
  (Phase 97 POST-07), Power-Rankings (Phase 97 POST-07), Standings
  (Phase 97 POST-08). Implementation lebt in `DiscordPostService.postOrEdit`
  zentral — wird einmal in Plan 96-03 implementiert + von Phase 97
  wiederverwendet.

### Plan Decomposition & Sequencing (Q-96-meta carry-forward from Phase 92/93/94/95 D-05/07)

- **D-96-05: Drei Plans, sequenziell inline auf
  `gsd/v1.13-discord-integration`.** Mirrors Phase 92 D-05 + Phase 93
  D-05 + Phase 94 D-07 + Phase 95 D-95-05 + Design-Spec § 5 (3 Plans
  für Phase 96) + ROADMAP-Schätzung. Wave-Pause + Mobile-Sweep nach
  jedem Plan ([[feedback-wave-pause]] + [[feedback-playwright-cli]]).
  Reihenfolge:
  - **Plan 96-01 — GRAFX-01 (ProvisionalScoresGraphicService + Multipart-
    Post auf Match-Detail).** Unabhängig — nutzt Phase-95-MatchRef-
    Plumbing ohne DiscordPostService-Extension.
  - **Plan 96-02 — FORUM-01 (V13 Schema + discord-config-Erweiterung +
    DiscordForumService + Season-Edit Discord-Section + Link-Modal).**
    Schema + UI-Foundation für Plan 96-03.
  - **Plan 96-03 — FORUM-02 (DiscordPostService RefBranches +
    ?thread_id=-Plumbing + Race-Detail-Button + Auto-Unarchive).**
    Baut auf 96-02-Schema auf. **Ende von Plan 96-03 = Phase-96-Close
    via `/gsd-validate-phase 96`.**
  Keine Worktrees, keine Subagents per [[feedback-inline-sequential-execution]].
  Wave-Pause [[feedback-wave-pause]] nach JEDEM der 3 Plan-Closes.
  REJECTED: 01 FORUM-01-Foundation + 02 FORUM-02 + 03 GRAFX (UX-
  Iteration-Last wäre risiko-arm, aber Plan 96-01 ist autark — kein
  Grund den GRAFX ans Ende zu schieben); 4-Plans-Aufteilung (zu
  granular für 2-3-Tage-Schätzung).

### PR Mechanics (carried forward from Phase 92/93/94/95 D-06/08)

- **D-96-06: Rolling v1.13 Milestone-PR weiter pflegen.** Phase 92
  Plan 92-01 hat die Draft-PR geöffnet, Phasen 93/94/95 haben den Body
  via `gh pr edit --body-file` aktualisiert. Phase 96 Plans 96-01..03
  ergänzen jeweils eine neue Zeile in der rolling per-plan summary
  table (Plan # / REQ-ID / status / commit SHA / CI run URL). Squash-
  Subject locked: `feat(v1.13): discord integration & carry-forwards`
  ([[feedback-squash-merge-message]]). PR bleibt Draft bis Ende
  Phase 98.

### Quality Gates (carried forward from Phase 92/93/94/95 D-07/09)

- **D-96-07: Standard-Gates unverändert.**
  - JaCoCo line coverage ≥ 88.88% (oder Phase-95-Endwert wenn anders —
    siehe 95-VERIFICATION). Phase 96 fügt ~30-50 neue Tests hinzu
    (3 Plans × ~10-17 Tests: Mockito-Unit + WireMock-IT + 1
    Playwright-E2E pro Plan + Mobile-Sweep-Variante). Coverage
    MUSS Phase-95-Baseline halten.
  - SpotBugs `BugInstance` count = 0 (blocking, verify-bound).
    Neue Klassen in `org.ctc.admin.service.ProvisionalScoresGraphicService`,
    `org.ctc.discord.service.DiscordForumService`, sowie Erweiterungen
    von `DiscordPostService` + `DiscordWebhookClient`. `@ToString.Exclude`
    auf neuen webhook-URL-Feldern in `DiscordGlobalConfig`. Lombok-config
    Phase-86-Invariant gilt unverändert.
  - CodeQL gate-step exit 0 auf PR HEAD SHA. Keine neuen
    SSRF-Suppression nötig (forum-webhook-URLs gehen über existierende
    `DiscordHostValidator.requireAllowed()`-Guards aus Phase 93).
    Falls neue Finding auftaucht: Three-layer-FP-Invariant aus
    Phase 85 D-19 anwenden.
  - **`EXPORT_ORDER` bleibt 25** (Phase 95 D-95-07 hatte auf 25 gebumpt
    via DiscordPost-Entity; Phase 96 fügt KEINE neue Entity hinzu —
    nur Spalten-Adds auf existing `seasons` + `discord_global_config`).
    `BackupSchema.SCHEMA_VERSION` bleibt **2** (Spalten-Add ist
    backward-compatible via Jackson `ignore-unknown` — keine
    Wire-Contract-Breaking-Change per Phase-72 D-15 Convention).
    `BackupSchemaGuardTest` unverändert.
  - **`SeasonMixIn` Erweiterung**: existing MixIn in
    `org.ctc.backup.serialization.SeasonMixIn` bekommt die 2 neuen
    Discord-Thread-ID-Felder explizit ignoriert ODER exportiert
    (Operator-Wahl — empfohlen: exportieren, da sie zur Saison-
    Identity gehören). `DiscordGlobalConfigMixIn` analog für die 2
    neuen Webhook-URLs (empfohlen: NICHT exportieren — sind Secrets,
    Operator restored-from-Backup soll Secrets neu pasten;
    Sicherheits-Default). Planner-Discretion.
  - Flyway V1-V12 immutable (V12 = Phase 95 `discord_post`). Phase 96
    fügt **V13** hinzu (`add_seasons_discord_threads_and_forum_webhooks`).
  - `./mvnw verify -Pe2e` finishes within v1.12 CI E2E 17:39 ± 20%
    tolerance. Phase 96 fügt ~8-12 WireMock-ITs + 3 Playwright-E2E
    + Mobile-Sweep-Variante hinzu. Expected impact: < 90 s gesamt.

### Test Discipline (carried forward from Phase 93/94/95 D-10/11)

- **D-96-08: Per-Plan Nyquist VALIDATION.md.** Plans 96-01..03 shippen
  jeweils mit eigener `VALIDATION.md`. Phase 96 self-validates via
  `/gsd-validate-phase 96` vor `/gsd-execute-phase 97` start.

- **D-96-09: `@Tag`-Convention per CLAUDE.md.**
  - WireMock-backed ITs (`DiscordPostServiceProvisionalScoresIT`,
    `DiscordPostServiceForumThreadIT`, `DiscordPostServiceRefBranchesIT`,
    `DiscordWebhookClientThreadIdIT`, `DiscordForumServiceIT`,
    `MatchControllerProvisionalPostIT`,
    `RaceControllerPostRaceResultToForumIT`,
    `SeasonControllerLinkThreadIT`) → `@Tag("integration")`.
  - Mockito-only Unit-Tests (`ProvisionalScoresGraphicServiceTest`,
    `DiscordForumServiceTest`, `DiscordPostServiceRefBranchesTest`)
    → untagged (Projekt-Convention).
  - Playwright E2E (`MatchDetailProvisionalButtonsE2ETest`,
    `SeasonEditDiscordSectionE2ETest`,
    `RaceDetailForumPostButtonE2ETest`) → `@Tag("e2e")`,
    package `org.ctc.e2e.discord.forum` per
    `.planning/codebase/TESTING.md` § Test Categorization.

### Live-UAT Strategy (analog Phase 94/95 D-12)

- **D-96-10: WireMock-IT-only Phase-96-Close; UAT-06 (Live Provisional +
  Forum-Thread Lifecycle) gestaged als STATE.md Pending UAT für
  Operator-Run vor Phase 97 POST-06 start.** Phase 96 schließt wenn
  `./mvnw verify -Pe2e` grün ist und alle WireMock-ITs für GRAFX-01 +
  FORUM-01 + FORUM-02 happy + failure paths covered haben. UAT-06
  läuft gegen Operator-Test-Guild mit echtem Webhook + Bot-Token:
  1. Operator startet `dev` profile mit `DISCORD_BOT_TOKEN` + live
     test guild.
  2. `/admin/discord-config` → 2 neue Felder befüllen (race-results-
     forum-webhook-url + standings-forum-webhook-url) → Save → grüne
     Badge "Configuration saved."
  3. `/admin/seasons/{id}/edit` (oder Discord-Integration-Sub-Page)
     → "Link existing race-results Thread..." → Modal öffnet sich →
     Thread-Liste sichtbar mit Pinned-Auto-Select → Confirm → grüne
     Badge "Thread linked." → Wiederholung für standings-Thread.
  4. Match-Detail eines Matches mit ≥1 Race mit Results → "Post
     Provisional Scores" → Multipart-POST mit N PNGs landet im
     Match-Channel → operator verifiziert visuell: jede PNG zeigt
     match-card-header + 2 team-blocks mit per-driver-detail
     (Position/Quali/FL/Pts-Race/Pts-Quali/Pts-FL/Total + Overall-Row).
  5. Re-Post Provisional Scores (nach Race-2 Completion) → Discord-
     Edit-Indicator sichtbar, PNG-Set ersetzt.
  6. Race-Detail mit Results → "Post Race Result to Forum-Thread"
     → Webhook-POST mit ?thread_id= landet im race-results-forum-
     thread.
  7. **Auto-Unarchive-Test**: Operator archiviert manuell den race-
     results-thread im Discord-Client → klickt erneut "Re-Post Race
     Result" → Bot unarchiviert + posted → Thread bleibt unarchived
     (keine Re-Archive nach dem Post).
  8. `/admin/discord/posts`-Listing zeigt alle 5 neuen Posts (1
     PROVISIONAL_SCORES + N RACE_RESULTS), Filter funktioniert.
  Operator-Resultat dokumentiert in STATE.md als UAT-06 Done.

### Production Behavior Boundary

- **D-96-11: Production-Code-Pfade in Phase 96 begrenzt auf:**
  - `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java`
    (NEU), `ResultsGraphicService.java` (Scope-Add `generateResultsBytes`).
  - `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java`
    (2 neue Felder + @ToString.Exclude).
  - `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java`
    (2 neue Felder + `@URL`-validation).
  - `src/main/java/org/ctc/discord/dto/Thread.java` (Scope-Add
    `pinned`/`flags`-Feld, Plan 96-02).
  - `src/main/java/org/ctc/discord/dto/DiscordPostRef.java`
    (Scope-Add RaceRef + SeasonRef Permits, Plan 96-03).
  - `src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java`
    (Scope-Add `unarchive()`-Factory-Method, Plan 96-03).
  - `src/main/java/org/ctc/discord/service/DiscordForumService.java`
    (NEU, Plan 96-02).
  - `src/main/java/org/ctc/discord/service/DiscordPostService.java`
    (Scope-Add SeasonRef/RaceRef-Branches + threadId-Param +
    Auto-Unarchive, Plan 96-03; sowie postProvisionalScores in
    Plan 96-01).
  - `src/main/java/org/ctc/discord/DiscordWebhookClient.java`
    (Scope-Add ?thread_id=-Overloads, Plan 96-03).
  - `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java`
    (3 neue derived-query-Methoden, Plan 96-03).
  - `src/main/java/org/ctc/domain/model/Season.java` (2 neue
    Discord-Thread-ID-Felder, Plan 96-02).
  - `src/main/java/org/ctc/admin/dto/SeasonForm.java` (2 neue Felder,
    Plan 96-02).
  - `src/main/java/org/ctc/admin/controller/SeasonController.java`
    (Link/Unlink-Endpoints, Plan 96-02).
  - `src/main/java/org/ctc/admin/controller/MatchController.java`
    (Post-Provisional-Endpoint, Plan 96-01).
  - `src/main/java/org/ctc/admin/controller/RaceController.java`
    (Post-Race-Result-To-Forum-Endpoint, Plan 96-03).
  - `src/main/resources/db/migration/V13__add_seasons_discord_threads_and_forum_webhooks.sql`
    (NEU, Plan 96-02).
  - `src/main/resources/templates/admin/provisional-scores-render.html`
    (NEU, Plan 96-01).
  - `src/main/resources/templates/admin/discord-config.html`
    (2 neue Felder, Plan 96-02).
  - `src/main/resources/templates/admin/season-form.html`
    (Discord-Integration-Section, Plan 96-02).
  - `src/main/resources/templates/admin/match-detail.html`
    (Provisional-Buttons, Plan 96-01).
  - `src/main/resources/templates/admin/race-detail.html`
    (Forum-Post-Buttons, Plan 96-03).
  - `src/main/resources/static/admin/css/admin.css`
    (`.discord-integration`-Section-Styling, Modal-Styling,
    Provisional-Button-Cluster — in-milestone-polish per
    [[feedback-in-milestone-polish]]).
  - `src/main/java/org/ctc/backup/serialization/SeasonMixIn.java`
    (potenzielle Erweiterung, siehe D-96-07 Planner-Discretion).
  Keine Edits in `org.ctc.dataimport.*`, `org.ctc.sitegen.*`,
  `org.ctc.gt7sync.*`, `org.ctc.scoring.*`. Jeder Plan-SUMMARY
  asserts `src/` clean außerhalb der explicit gelisteten Pfade.

### Claude's Discretion

- **`Thread` DTO `pinned`-Detection-Mechanik** (D-96-FOR-2a) —
  `thread.flags` (Bitfield) vs `thread.thread_metadata.archived`
  vs Discord-API-actual-Response-Shape; Planner prüft Discord-Docs +
  WireMock-Fixtures + implementiert.
- **Season-Edit-Section-Location** (D-96-FOR-2b) — Form-inline
  unter den existing Season-Form-Feldern vs dedicated sub-page
  `/admin/seasons/{id}/discord-integration`; Planner picks based on
  Form-Größe + Mobile-UX.
- **`SeasonMixIn` + `DiscordGlobalConfigMixIn` Backup-Wire-Contract**
  (D-96-07) — exportieren oder ignorieren der neuen Discord-Felder;
  empfohlen: Thread-IDs exportieren (Saison-Identity), Webhook-URLs
  NICHT exportieren (Secret-Discipline). Planner-Discretion.
- **`DiscordWebhookClient` Overload-Strategy** (D-96-FOR-3a) —
  Variante A Method-Overloads (recommended) vs Variante B
  WebhookTarget-Wrapper-Record (cleaner-langfristig). Planner picks
  based on Phase-95-Callsite-Count + Refactor-Risiko.
- **`ResultsGraphicService.generateResultsBytes` Implementation-
  Variante** (D-96-FOR-3d) — neue Methode vs Refactor der existing
  `generateResults`-Methode mit Shared-Private-Helper; Planner picks.
- **Visual-Regression-Snapshot für ProvisionalScoresGraphicService**
  (Plan 96-01 optional) — Pixel-Hash-Vergleich gegen
  `.screenshots/96-01/provisional-reference.png`; Planner kann das
  als Test oder als Deferred-Idea für Phase 98 behandeln.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 96: Provisional Graphic + Forum Threads"
  — Goal, Depends-on (Phase 95), Requirements (GRAFX-01, FORUM-01,
  FORUM-02), UI hint = yes.
- `.planning/REQUIREMENTS.md` § "GRAFX-01..02 + FORUM-01..02" —
  vollständige Requirement-Texte inkl. Acceptance Criteria.
- `.planning/milestones/v1.13-ROADMAP.md` § "Phase 96: Provisional
  Graphic + Forum Threads" (Lines 94-106) — Success Criteria 1-5 +
  Phase Dependency Graph (depends auf Phase 95; Phase 97
  POST-07/POST-08 depends auf Phase 96 forum-thread-Linking + V13).
- `.planning/PROJECT.md` § "Current Milestone: v1.13" —
  Zero-new-production-dependencies, outbound-only, button-triggered
  Invarianten.
- `.planning/STATE.md` § "Active Milestone — v1.13" + § "Baselines
  to Preserve" (JaCoCo ≥ 88.88%, CI E2E 17:39 ± 20%, SpotBugs 0,
  CodeQL exit 0, EXPORT_ORDER 25 unchanged, SCHEMA_VERSION 2
  unchanged, Flyway V1-V12 immutable, V13 lands here) + § "Pending
  UATs" (UAT-05 PASSED 2026-05-23; UAT-06 wird in Phase-96-Close
  hinzugefügt per D-96-10).
- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` —
  DIE authoritative Design-Reference. § 3.1 Integration Model
  (all-message-posting-via-Webhook + uniform Webhook-PATCH edit-path
  — invariante für Forum-Thread-Posts), § 3.2 Package Layout
  (`DiscordForumService` Plan 96-02), § 3.3 Data Model (V12 ist
  Phase 95, V13 ist Phase 96 — Korrektur des ursprünglichen V11/V12-
  Mappings via Phase-95 CONTEXT), § 4.1 Routes
  (`/admin/seasons/{id}/edit` + Discord-Config-Page), § 4.2 Extended
  Pages, § 4.3 Button Matrix (Match-Detail "Post Provisional Scores"
  + Race-Detail "Post Race Result to Forum-Thread"), § 4.7 Forum-
  Thread Linking (Modal-Picker-Pattern), § 5 Phase Breakdown
  (Phase 96 = 3 Plans), § 9 Resolved Brainstorming Decisions
  (D-Spec-4 Saison-scoped Forum-Threads, D-Spec-8 App-generated
  Provisional-Scores).

### Phase 95 Hand-off (PRIMARY INPUT — Post-Plattform)

- `.planning/phases/95-match-channel-posts/95-CONTEXT.md` —
  **D-95-03 Multipart-Bundle-Pattern** = Plan 96-01 Provisional-
  Multipart-Bundle übernimmt 1:1 die Bundle-Logic (sortiert by
  raceNumber, NamedAttachment-List, 1 `discord_post`-Row).
  **D-95-03a Multipart-PATCH-Edit-Path** = Plan 96-01 nutzt das
  unverändert. **D-95-04 Auto-Edit-Hook in MatchService.save** =
  irrelevant für Phase 96 (keine analoge Auto-Edit-Logik nötig —
  Provisional + Race-Result-Forum sind beide button-triggered, nicht
  auto-edit). **D-95-07 EXPORT_ORDER + SCHEMA_VERSION** = Phase 96
  bleibt auf 25 / 2 (keine neue Entity). **D-95-11 Production-Path-
  Boundary** = analoger Scope-Cut für Phase 96 (D-96-11). **D-95-12
  DiscordPostRef Sealed-Hierarchy** = Phase 96 implementiert RaceRef +
  SeasonRef Permits (Phase 95 hatte sie definiert aber nicht
  instanziiert).
- `.planning/phases/95-match-channel-posts/95-04-VALIDATION.md` +
  `95-VALIDATION.md` (post-validate) — bestätigen dass Phase 95
  Post-Plattform stabil ist für Phase 96 zu bauen.
- `.planning/phases/95-match-channel-posts/95-04-SUMMARY.md` —
  beschreibt den finalen Stand von DiscordPostService inkl. der
  `UnsupportedOperationException`-Carry-Forwards für RaceRef +
  SeasonRef.

### Phase 94 Hand-off (Channel-Lifecycle + Modal-Pattern)

- `.planning/phases/94-team-roles-match-channel-lifecycle/94-CONTEXT.md`
  — **D-94-06 (`Move-to-Archive`-Modal-Pattern)** = Vorbild für
  FORUM-01 "Link existing Thread..."-Modal mit Auto-Pre-Select des
  highest-num-with-room-Eintrags (Phase 96 → Pinned-Thread-Pre-Select).
  **D-94-13 (Production-Path-Boundary)** = analoger Scope-Cut.

### Phase 93 Hand-off (DiscordRestClient + Sealed-Exception)

- `.planning/phases/93-discord-foundation/93-CONTEXT.md` — **D-93-02
  (`@ToString.Exclude` für Webhook-URL-Secrets)** = wird in Phase 96
  auf die 2 neuen Forum-Webhook-URL-Felder von DiscordGlobalConfig
  angewendet. **D-93-04 (DiscordEmojiCache-Shape)** = nicht relevant
  für Phase 96. **D-93-05 (Per-Plan Nyquist VALIDATION.md)** = Phase
  96 erbt unverändert.

### Existing-Code-Touchpoints (Phase-96-Scope-Reuse)

- `src/main/java/org/ctc/discord/DiscordRestClient.java` —
  `listActiveThreads(guildId)` + `listArchivedThreads(channelId)` +
  `createThread(channelId, ThreadCreateRequest)` (latter NICHT
  benötigt per D-96-FOR-1c) + `modifyChannel(channelId, ChannelModifyRequest)`
  (wird für Auto-Unarchive in Plan 96-03 wiederverwendet) +
  `fetchChannel(channelId)` (wird für Archived-Status-Check vor
  Auto-Unarchive verwendet).
- `src/main/java/org/ctc/discord/dto/Thread.java` (Phase 93) —
  Plan 96-02 erweitert um `pinned`-Flag oder `flags`-int (planner-
  discretion).
- `src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java`
  (Phase 94) — Plan 96-03 erweitert um `unarchive()`-Factory-Method
  (`new ChannelModifyRequest(null, null, null, null, null, false)`
  oder analoge Shape).
- `src/main/java/org/ctc/discord/service/DiscordPostService.java`
  (Phase 95) — Plan 96-01 fügt `postProvisionalScores(Match)` hinzu;
  Plan 96-03 extendet `postOrEdit` um die 3 fehlenden
  DiscordPostRef-Permits + threadId-Param + Auto-Unarchive-Pfad.
- `src/main/java/org/ctc/discord/DiscordWebhookClient.java`
  (Phase 93/95) — Plan 96-03 erweitert alle 4 message-Methoden um
  ?thread_id=-Query-Param-Overloads.
- `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java`
  (Phase 95) — Strukturelles Vorbild für ProvisionalScoresGraphicService
  in Plan 96-01.
- `src/main/java/org/ctc/admin/service/ResultsGraphicService.java`
  (existing) — Plan 96-03 fügt `generateResultsBytes(Race)→byte[]`
  hinzu.
- `src/main/resources/templates/admin/match-results-render.html`
  (Phase 95) — Strukturelles Vorbild für `provisional-scores-render.html`
  in Plan 96-01.
- `src/main/resources/templates/admin/match-detail.html` —
  `.discord-actions--posts` Cluster aus Phase 95 bekommt das
  Provisional-Buttons-Pair in Plan 96-01 hinzugefügt.
- `src/main/resources/templates/admin/race-detail.html` —
  BISHER ohne Discord-Actions-Section; Plan 96-03 fügt eine neue
  `.discord-actions--posts` Cluster für die Forum-Thread-Buttons hinzu.

### Convention-References

- `CLAUDE.md` § Architectural Principles → Keep Controllers Thin,
  DTOs instead of Entities, No Comment Pollution, RaceLineup as
  Source of Truth.
- `CLAUDE.md` § Conventions → Naming Patterns, Lombok Usage,
  Annotation Order (`@Slf4j @Component @RequiredArgsConstructor`),
  Logging mit parameterized `{}`, CSS-Klassen statt inline-styles.
- `.planning/codebase/TESTING.md` § Test Categorization (`@Tag`) —
  `@Tag("integration")` für `*IT.java`, `@Tag("e2e")` für Playwright,
  untagged für Mockito-Units.
- `.planning/codebase/ARCHITECTURE.md` — Clean 3-tier (Controller →
  Service → Repository) für alle neuen Discord-Pfade.

### User-Reference (Provisional Layout)

- **Chat 2026-05-23 (zwei Screenshots vom User)** — (1) Heutiger
  Google-Sheets-Workflow-Screenshot (AHR 1 + TNR A Blöcke mit
  per-Driver-Detail-Spalten Driver | Position | Quali | FL | Pts-Race
  | Pts-Quali | Pts-FL | Total + Overall-Row pro Block) und (2)
  bestehende Race-Detail Results-Tabelle des CTC-Managers
  (12 Driver Flat-Tabelle mit POS | TEAM | DRIVER | QUALI | RACE |
  QUALI | FL | TOTAL). Plan 96-01 legt zur Iteration eine Kopie
  unter `.screenshots/96-01/provisional-reference.png` ab.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`AbstractGraphicService` + `TemplateManageable`** (existing):
  alle Graphic-Services erben — Plan 96-01 nutzt dieselbe Render-
  Pipeline (Thymeleaf-Template + Playwright-Screenshot,
  encodeCardBase64, encodeClasspathResource für CTC-Logo + Font).
- **`MatchResultsGraphicService.generateMatchResults(Match) → byte[]`**
  (Phase 95): Strukturelles Vorbild für
  `ProvisionalScoresGraphicService.generateProvisional(Race) → byte[]`.
  Plan 96-01 kopiert die match-card-Header-Logic + erweitert um
  per-Driver-Spalten.
- **`ResultsGraphicService.generateResults(Race) → String`** (existing):
  liefert heute uploads-URL-Path; Plan 96-03 fügt
  `generateResultsBytes(Race) → byte[]` Variante hinzu für FORUM-02-
  Post-Pfad (analog zu MatchResultsGraphicService-Pattern).
- **`DiscordPostService.postOrEdit`** (Phase 95): MatchRef-Branch
  ist stable production-code; Plan 96-01 nutzt es unverändert für
  PROVISIONAL_SCORES (MatchRef). Plan 96-03 erweitert um RaceRef +
  SeasonRef-Branches + threadId-Param.
- **`DiscordWebhookClient.executeMultipart` + `editMessageWithAttachments`**
  (Phase 95): Plan 96-03 fügt ?thread_id=-Overloads hinzu;
  Code-Path identisch (MultipartBodyBuilder + UriComponentsBuilder).
- **`DiscordRestClient.listActiveThreads` + `listArchivedThreads`**
  (Phase 93): Plan 96-02 nutzt beide für die
  `DiscordForumService.listThreads`-Combined-Liste; Sort + Filter
  client-side.
- **`DiscordRestClient.modifyChannel(channelId, ChannelModifyRequest)`**
  (Phase 94): Plan 96-03 nutzt es für Auto-Unarchive
  (`ChannelModifyRequest.unarchive()` factory).
- **`DiscordRestClient.fetchChannel(channelId)`** (Phase 94): Plan
  96-03 nutzt es für den Archived-Status-Check vor Auto-Unarchive.
- **`@ToString.Exclude` für Webhook-URL-Secrets** (Phase 93 D-93-02):
  wird auf die 2 neuen Forum-Webhook-URL-Felder in
  DiscordGlobalConfig angewendet.
- **Match-Detail `.discord-actions--posts` Cluster + admin.css**
  (Phase 95): bestehende responsive-wrap-CSS deckt Mobile-Overflow
  ab (Phase 98 polishet noch `.card`-Container); Plan 96-01 fügt
  Provisional-Buttons in den Cluster hinzu.
- **SettingsGraphic/LineupsGraphic-Pre-Flight-Pattern** (Phase 95
  D-95-03b): Service-Layer-Pre-Flight-Predicate-Methods
  (`matchHasCompleteSettings` / `matchHasCompleteLineups`) — Plan
  96-01 `matchHasProvisionalData(match)` + Plan 96-03
  `canPostRaceResultToForum(race)` folgen demselben Pattern.

### Established Patterns

- **Form-DTO + @Valid + BindingResult + Flash-Attribute** (CLAUDE.md):
  alle neuen MatchController- + RaceController- + SeasonController-
  POST-Endpoints folgen dem Pattern.
- **Sealed-Exception-Hierarchy** (Phase 93): 4 Discord-API-Permits
  + `data-incomplete` BusinessRuleException-Subtyp (Phase 95
  D-95-03b). Phase 96 nutzt dieselbe Failure-UX.
- **Service-Layer-Pre-Flight-Predicate-Methods** (Phase 94/95):
  Plan 96-01 + Plan 96-03 erben das Pattern.
- **Channel-Picker-Modal-Pattern** (Phase 94 D-94-06): Plan 96-02
  Thread-Picker-Modal folgt der Move-to-Archive-Modal-Topology
  (Liste + Auto-Pre-Select + Operator-Übersteuerung + Confirm-POST).
- **Per-Plan Nyquist VALIDATION.md** (Phase 89+): 3 Plans, 3
  VALIDATION.md.
- **Rolling Draft-PR via `gh pr edit --body-file`** (Phase 92-95):
  Phase 96 erweitert die existierende v1.13-PR um die 96er Plans.
- **`@Tag`-Discipline für Test-Routing** (CLAUDE.md): Phase 96 hält
  das ein (D-96-09).
- **Inline-sequential-Execution + Wave-Pause** (Phase 92-95
  D-05/07/95-05): Phase 96 unverändert.

### Integration Points

- **`Match-Detail` Discord-Actions-Panel ← `[Post Provisional Scores]`**
  (Plan 96-01): neues Button-Pair innerhalb des existing Phase-95-
  `.discord-actions--posts` Clusters.
- **`Season-Edit` Discord-Integration-Section ← Link/Unlink-Modal-Pair**
  (Plan 96-02): neuer Form-Section unter den existing Season-Form-
  Feldern.
- **`Race-Detail` Discord-Actions-Section ← `[Post Race Result to Forum-Thread]`**
  (Plan 96-03): NEUE Section auf race-detail.html (bislang ohne
  Discord-Actions).
- **`SeasonController.linkRaceResultsThread / linkStandingsThread / unlinkThread`**
  (Plan 96-02): 3 neue POST-Endpoints.
- **`MatchController.postProvisional / refreshProvisional`** (Plan
  96-01): neuer POST-Endpoint(s).
- **`RaceController.postRaceResultToForum`** (Plan 96-03): neuer
  POST-Endpoint.
- **`DiscordPostService.postOrEdit ← @Nullable String threadId`**
  (Plan 96-03): existing Phase-95-Callsites unverändert; neue
  forum-thread-Callsites passen threadId explizit.
- **`DiscordPostService` ← Auto-Unarchive-Step vor jedem
  threadId-Post** (Plan 96-03): zentral implementiert; Phase 97
  POST-07/POST-08 erbt automatisch.

</code_context>

<specifics>
## Specific Ideas

- **Provisional-Layout = "Anlehnung an MatchResultsGraphic" + erweiterte
  Spalten** (D-96-GRX-1, User-Direktive 2026-05-23 mit 2
  Reference-Screenshots im Chat): match-card-Header + 2 Team-Blöcke
  mit per-Driver-Detail Position/Quali/FL/3-Points-Cols/Total +
  Overall-Row pro Block. Iterative playwright-cli-Schleife per
  Memory-Rule [[feedback-graphic-pixel-positioning]] +
  [[feedback-graphic-design-iteration]].
- **Per-Race-PNG, Multipart-Bundle, Match-Channel only** (D-96-GRX-1a
  + D-96-GRX-1b + D-96-GRX-1c): klare User-Direktive 2026-05-23.
  Spec-FORUM-02-Wording "applies to both Race-Result and Provisional-
  Scores Forum-Thread posts" wird als Auto-Unarchive-Logik (nicht
  als Target) interpretiert.
- **Operator-pasted Webhook-URLs** (D-96-FOR-1): explicit Setup-UX
  analog Phase-93-announcement-webhook — User-Direktive 2026-05-23
  "Mittel: Webhook-URLs + Picker mit Auto-Pre-Select".
- **No-Create-Thread + Pinned-Auto-Select** (D-96-FOR-1c + D-96-FOR-2):
  klare User-Direktive 2026-05-23 — Operator erstellt Threads
  manuell pro Saison im Discord-Client; Modal-Picker mit pinned-
  Vor-Selektion analog Move-to-Archive aus Phase 94.
- **No-Re-Archive nach Auto-Unarchive** (D-96-FOR-4): User-Direktive
  2026-05-23 — Discord's natürliche Inactivity-Auto-Archive übernimmt
  das. Minimaler Code, 3 Calls statt 4.

</specifics>

<deferred>
## Deferred Ideas

- **Bot-side "Create new Thread..." Workflow** (out of Phase 96
  scope per D-96-FOR-1c) — falls je benötigt, eigenes
  DISC-FUTURE-Ticket. Aktuell: Operator-Workflow manuell im Discord-
  Client, ~1 Min pro Saison.
- **Re-Archive-After-Post Configurability** (deferred per D-96-FOR-4)
  — falls Operator-Workflow es je verlangt, kann app.yml-Flag oder
  per-Klick-Modal-Option in v1.14 hinzugefügt werden.
- **Visual-Regression-Snapshot-Test für ProvisionalScoresGraphicService**
  — Pixel-Hash-Vergleich gegen `.screenshots/96-01/provisional-reference.png`
  als Test; Planner-Discretion ob Plan 96-01 oder Phase 98 das
  hinzufügt.
- **`DiscordForumService.createThread()`-Implementation** — DTOs
  (`ThreadCreateRequest`) + REST-Client-Method existieren bereits
  aus Phase 93/94, aber ohne Service-Wrapper + ohne UI. Deferred.
- **`DiscordPostRef.MatchdayRef`-Permit-Implementation** — Phase 95
  D-95-12 sah es vor; Phase 96-03 lässt es weg da nur RaceRef +
  SeasonRef für FORUM-02 nötig sind. Phase 97 POST-07 implementiert
  MatchdayRef (carried-forward für Phase 97 als TODO).
- **Webhook-URL-Export-vs-Skip im Backup-Wire-Contract**
  (D-96-07 Planner-Discretion) — Default-Empfehlung: NICHT
  exportieren (Secret-Discipline); operator-rebuilds-from-backup
  pastet die Webhook-URLs neu.
- **Mobile-Viewport `.card`-Overflow auf Season-Edit nach Discord-
  Integration-Section-Add** — bleibt Phase 98 Polish-Phase
  (`Mobile-viewport .card overflow` Success Criterion 6 in
  milestone-roadmap). Plan 96-02 sweepe Desktop + Mobile, aber
  ggf. surfacing-only ohne Fix; Fix landet im Phase-98-CSS-Sweep.

</deferred>

---

*Phase: 96-provisional-graphic-forum-threads*
*Context gathered: 2026-05-23*
