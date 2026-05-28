# Phase 98: Polish + E2E + Docs + Close - Context

**Gathered:** 2026-05-24 (initial) / 2026-05-25 (Re-Open Q-98-06..09 for 98-05+)
**Status:** Re-Opened — 98-01/02/03 shipped + UAT-08 PASS + Plan 98-04 source-fixed; Plans 98-05/06/07 pending per Q-98-06..09 below (matchday-level announcement-channel posts)

<domain>
## Phase Boundary

Phase 98 schließt v1.13 Discord-Integration als production-ready Milestone ab.
Drei Plans, sequenziell inline auf `gsd/v1.13-discord-integration` per
[[feedback-inline-sequential-execution]] + [[feedback-wave-pause]] (carry-forward
von Phase 92-97 D-9*-05/07/D-9*-08):

- **Plan 98-01 — Mobile-Polish CSS-Sweep + DOCS-02 Runbook-Erweiterung
  (REQs: DOCS-02 + ROADMAP § Phase 98 Erfolgskrit 6 "Mobile-viewport `.card`
  overflow"):**
  - Globaler CSS-Sweep auf `src/main/resources/static/admin/css/admin.css`
    `.card` + `.form-group input/select/textarea` + `.searchable-dropdown
    .dropdown-list`: `min-width: 0`, `box-sizing: border-box`,
    `max-width: 100%` (verhindert flex-overflow auf 375 px Viewport). Plus:
    Mobile-Padding-Reduktion auf 16 px im existierenden 640 px-Media-Query
    (Line 221 in `admin.css` — der gleiche MQ den `.discord-actions` Phase 94
    nutzt).
  - Playwright-cli Verifikation: 4 ROADMAP-Krit-6-Pflicht-Seiten
    (`/admin/discord-config`, `/admin/teams/{id}/edit`, `/admin/matches/{id}`,
    `/admin/matches/{id}/edit`) + 4-5 Stichproben-Seiten (`/admin` Dashboard,
    `/admin/seasons/{id}`, `/admin/matchdays/{id}`, `/admin/drivers`,
    `/admin/tools`). Desktop + Mobile (375×667) Screenshots in
    `.screenshots/98-mobile-polish/` (gitignored).
  - In-milestone polish discovery policy: leichte non-Discord-Fixes
    (< 30 min Aufwand) im selben Plan addressieren per
    [[feedback-in-milestone-polish]]; größere Fixes in DEFERRED → v1.14.
  - DOCS-02 Runbook-Update auf `docs/operations/discord-integration.md`
    (existierende 417 Zeilen werden inkrementell ERWEITERT, NICHT
    restrukturiert):
    - **§ 1.9 Forum-Channel + Thread Setup** (race-results + standings):
      Forum-Channel via Discord-Client erstellen, Channel-ID kopieren,
      `discord_global_config.race_results_forum_webhook_url` setzen, ersten
      Thread anlegen, `seasons.discord_race_results_thread_id` setzen, selbe
      Sequenz für Standings.
    - **§ 2.3 Daily Operations (Phase 94-97 Workflows)**: per-Match
      Channel-Create + 5 Match-Channel-Posts (TEAM_CARDS / SETTINGS / LINEUPS
      / SCHEDULE / MATCH_RESULTS) + PROVISIONAL_SCORES (Phase 96) +
      Match-Preview Announcement (POST-06) + Matchday-Level Posts
      (MATCHDAY_OVERVIEW / POWER_RANKINGS) + STANDINGS (POST-08) +
      Archive-Modal. Pre-Flight-Gates, Stale-Detection-Yellow-Signals,
      Re-Post-Button-Semantik.
    - **§ 6 Token-Rotation Procedure**: Regenerate-Flow + `.env`-Update +
      `./scripts/app.sh restart` + `Test Connection`-Verifikation + Emergency-
      Rotation wenn Token geleaked.
    - **§ 7 UAT-08 Procedure + Extended Troubleshooting**: 8-Stage
      Full-Matchday-Lifecycle Operator-Procedure analog UAT-03/04/05/06/07.
      Extended Troubleshooting für `category-full` (Phase 94),
      `not-found bei archived channels`, missing webhook/thread-id, missing
      `discordTeaser`.
  - Screenshot-Strategie (D-98-DOCS-1): App-UI Screenshots werden
    playwright-cli automatisiert generiert und nach
    `docs/operations/images/discord/` committed (NICHT `.screenshots/`, da
    gitignored). Discord-Developer-Portal + Discord-Server-Interaktionen
    werden TEXTUELL als nummerierte Schritte dokumentiert — keine
    Portal-Screenshots erforderlich (User-Direktive 2026-05-24: "alles kann
    automatisiert werden, ohne dass tatsächliche Screenshots bereitgestellt
    werden müssen. Das reicht für den Beginn völlig aus").
  - Single-Audience-Stil (Operator) durchgängig — bestehende §1-5 Imperativ-
    Stil bleibt unverändert.

- **Plan 98-02 — E2E-01 Full-Matchday-Lifecycle Mega-Walkthrough
  (REQ: E2E-01):**
  - Neue Test-Klasse `DiscordFullMatchdayLifecycleE2ETest` in
    `src/test/java/org/ctc/e2e/discord/lifecycle/`. **EIN** `@Test`
    `fullMatchdayLifecycle()` der alle 8 Stages sequenziell durchspielt:
    Stage 1 create-channel → Stage 2 post-team-cards → Stage 3 post-settings
    → Stage 4 post-lineups → Stage 5 post-schedule → Stage 6 post-provisional
    → Stage 7 post-match-results → Stage 8 move-to-archive. **Hybrid-Struktur
    (Variante C aus Q1)** als Planner-Discretion möglich (eine `@Test` +
    8 `private step1_..._step8_` Methoden für Failure-Lokalisierung), aber
    Top-Level bleibt "ein Mega-Walkthrough".
  - **WireMock-Integration via Spring-Boot `@AutoConfigureWireMock(port = 0)`**
    auf der Test-Klasse + `@SpringBootTest(properties =
    "discord.api.base-url=http://localhost:${wiremock.server.port}")`.
    Identisches Pattern zu existierenden Phase-95-97 ITs
    (`DiscordPostService*IT`, `DiscordWebhookClientMultipartEditIT`,
    `MatchServiceScheduleEditHookIT`).
  - **DB-Seed via `TestDataService.seedFullMatchdayLifecycle()` NEUE Methode**
    (Test-Prefix `T-`/`Test-` per CLAUDE.md "Isolate Test Data Completely"):
    1 Test-Season + 1 Matchday + 1 Match (2 Test-Teams + Sub-Teams + Drivers
    + RaceLineup + Settings + Race-Times). Aufrufbar als Endpoint oder direkt
    aus dem Test via `@Autowired TestDataService`.
  - **Recorded-payloads Assertions via WireMock `verify()` mit
    `RequestPatternBuilder`** per Stage: URL-Pattern (`urlPathEqualTo(...)`
    + `withQueryParam(...)` per [[feedback-wiremock-vs-real-api]] explicit-
    query-param-rule), Multipart-Body-Parts (filename, content-type,
    body-size > 1024 bytes für Attachment-Posts), Headers
    (`X-RateLimit-Remaining`, `X-RateLimit-Reset-After`).
  - **Standard-200-Stubs ohne 429-Rate-Limit-Path** — der Mega-Walkthrough
    asserted dass `DiscordRateLimitInterceptor` (Phase 93) die Headers liest,
    triggert aber keine Wartezyklen. 429-Edge-Case ist bereits in
    `DiscordRateLimitInterceptorIT` Phase 93 abgedeckt.
  - **Hardcoded deterministische Snowflake-IDs pro Stage** (z.B.
    `900000000000000001L` + stage_offset), erlaubt DB-Assertions auf
    `match.discordChannelId == 900000000000000001L`.
  - **Eine Playwright `Page`-Session durch alle 8 Stages** — simuliert echten
    Operator-Browser-Workflow inkl. Flash-Messages + Modal-State.
  - **Helper-Klasse `WireMockDiscordStubs`** in
    `src/test/java/org/ctc/discord/wiremock/` (sibling zu `org.ctc.discord.*`
    Production-Code, Test-only-Visibility): static `stubCreateChannel(...)`,
    `stubCreateWebhook(...)`, `stubExecuteWebhook(...)`,
    `stubPatchMessage(...)`, `stubArchiveChannel(...)`. Reduziert Mega-Test-
    Datei auf ~120 Zeilen; wiederverwendbar in zukünftigen Phase-95-97 ITs
    falls Refactor.
  - **Tag-Strategie**: nur `@Tag("e2e")` (CLAUDE.md "Test Categorization").
    Kein Sub-Tag — Selektives Triggern via
    `-Dit.test=DiscordFullMatchdayLifecycleE2ETest`.
  - **Test-Count**: 1 neuer Test (der Mega-Walkthrough). Coverage-Erwartung
    minimal (zwischen −0.1 und +0.3 pp), Baseline 88.88 % bleibt; Plan 98-02
    asserted Post-Run JaCoCo ≥ Phase-97-Baseline; falls deutlich unterschritten
    (>1 pp): Wave-Pause + Root-Cause.

- **Plan 98-03 — DOCS-03 README + Wiki + MILESTONES.md + REQUIREMENTS.md
  (REQ: DOCS-03 + Milestone-Bookkeeping):**
  - **README.md** bekommt einen kurzen "Discord Integration"-Bullet unter
    "Admin Features" (~5-8 Sätze: was die Integration tut + für wen +
    Highlight-Features Auto-Edit / Pre-Flight-Gates / Stale-Detection + Link
    zu `docs/operations/discord-integration.md` + 1 App-UI-Screenshot
    embedded). Canonical paragraph identisch zwischen README + Wiki.
  - **GitHub Wiki Update via lokalen Clone** (D-98-WIKI-1): Plan 98-03 cloned
    `https://github.com/jegr78/ctc-manager.wiki.git` in `.wiki-clone/`
    (gitignored), schreibt:
    - **NEUE Page `Discord-Integration.md`** (canonical paragraph + Setup-
      Link zu `docs/operations/discord-integration.md` + 1-2 App-UI
      Screenshots als visual hook, Pfade zu hochgeladenen Wiki-Assets).
    - **`Home.md` Sidebar-Update** mit Discord-Integration-Link analog zu
      existierenden Wiki-Page-Verlinkungen (z.B. Backup-and-Restore).
    - **Wiki-eigenes `README.md`** falls vorhanden — sonst skip.
    Committet + pusht zum Wiki-Repo. Wiki-Asset-Uploads für Screenshot-
    Re-use erfolgen via `git add` im `.wiki-clone/` (Wiki-Repos akzeptieren
    Bilder als normale Files).
  - **CHANGELOG-Referenz**: Plan 98-03 referenziert die v1.13-Release-Notes
    die Semantic Release auf GitHub Releases generiert (per
    `feedback_no_local_git_tags` — CI-Workflow generiert das beim Merge).
    Kein lokales CHANGELOG.md-File-Update — README + Wiki linken auf die
    GitHub-Release-Page.
  - **MILESTONES.md v1.13-Entry** in Plan 98-03 (D-98-CLOSE-1): Phases 92-98
    + Plans + Tests + Coverage + CI E2E-Time + 25/25 REQ-Coverage + ggf.
    Carry-Over-Items für v1.14. Format analog zu v1.10/11/12-Einträgen.
  - **REQUIREMENTS.md Resolved-Flip** für alle 25 v1.13-REQ-IDs in Plan 98-03
    (D-98-CLOSE-2): per User-Direktive 2026-05-24 "alles muss im PR mit drin
    sein, kein post-merge bookkeeping" — Branch-protection-rule verlangt
    Pre-Merge-Updates.
  - **PR-Body-Final-Update** via `gh pr edit <num> --body "..."` per
    [[feedback-pr-description-update]] mit finalem Phase-Tracker, Coverage-
    Delta, Test-Count, CI E2E-Time, alle 25 REQ-IDs auf ✓, Milestone-Close-
    Hinweis. Squash-subject locked: `feat(v1.13): discord integration &
    carry-forwards` per [[feedback-squash-merge-message]].
  - **Test-Count**: 0 (rein dokumentarisch).

</domain>

<decisions>
## Implementation Decisions

### Q-98-01 — E2E-01 Test Architecture (Area 1)

- **D-98-E2E-1: Mega-Walkthrough — eine `@Test`-Methode, 8 Stages
  sequenziell.** `DiscordFullMatchdayLifecycleE2ETest.fullMatchdayLifecycle()`
  walked create-channel → 5 Match-Channel-Posts → Provisional → Match-Results
  → Archive in einer Test-Session. Entspricht 1:1 ROADMAP-Erfolgskrit 1
  "one suite ... full lifecycle". Hybrid-Pattern (eine `@Test` +
  8 `private step1_..._step8_`-Methoden für Failure-Lokalisierung im
  Stack-Trace) ist Planner-Discretion innerhalb dieser Decision.
  REJECTED: Suite-Pattern (8 separate Tests) wegen Roadmap-Wortlaut-Konflikt
  + 8× Spring-Context-Spin-up CI-Impact.

- **D-98-E2E-2: Spring-Boot `@AutoConfigureWireMock(port = 0)` + property-
  override.** `@SpringBootTest(properties = "discord.api.base-url=http://
  localhost:${wiremock.server.port}")` injiziert dynamischen WireMock-Port
  in Phase-93-`DiscordRestClient`. Identisches Pattern zu allen Phase-93-97
  Discord-ITs (siehe `DiscordWebhookClientMultipartEditIT`). Null neue
  Test-Infrastruktur, null Boilerplate.

- **D-98-E2E-3: `TestDataService.seedFullMatchdayLifecycle()` neue Methode.**
  Test-Prefix-Daten (`T-`/`Test-` per CLAUDE.md "Isolate Test Data
  Completely") für 1 Test-Season + 1 Matchday + 1 Match + 2 Test-Teams +
  Sub-Teams + Drivers + RaceLineup + Settings + Race-Times. Spiegelt
  `TestHelper.createFullSeasonFixture()`-Pattern aus Controller-Tests.
  REJECTED: Inline-UI-Click-Setup (zu langsam + zu fragil); Flyway-
  Repeatable-Migration (deviert vom Java-`TestDataService`-Pattern).

- **D-98-E2E-4: Per-Stage `WireMock.verify()` mit `RequestPatternBuilder`.**
  Pro Stage Assertions auf URL-Path + `withQueryParam(...)` (z.B.
  `?thread_id=...` für Forum-Thread-Webhooks per
  [[feedback-wiremock-vs-real-api]] explicit-query-param-rule) +
  Multipart-Body-Parts (filename, content-type, body-size > 1024 bytes für
  Attachment-Posts: TEAM_CARDS / SETTINGS / LINEUPS / PROVISIONAL /
  MATCH_RESULTS) + Headers (`X-RateLimit-Remaining`,
  `X-RateLimit-Reset-After` für `DiscordRateLimitInterceptor`-Smoke).
  REJECTED: Snapshot-byte-equals (Maintenance-Burden + Pixel-Hash-
  Brittleness contra [[feedback-graphic-design-iteration]]); URL-Pattern-only
  (verletzt [[feedback-wiremock-vs-real-api]] WireMock-vs-real-API-Lehre aus
  den 5 Phase-95-Bugs).

- **D-98-E2E-5: Standard-200-Stubs ohne 429-Path.** Alle Discord-API-Stubs
  antworten 200 + realistische Rate-Limit-Headers. 429-Path bereits in
  Phase-93 `DiscordRateLimitInterceptorIT` abgedeckt. Hält Mega-Test-Laufzeit
  unter 60s + vermeidet Flake-Risk. DEFERRED: 429-Path im Mega-Walkthrough
  → DISC-FUTURE Ticket falls Operator-Use-Case-Druck entsteht.

- **D-98-E2E-6: Hardcoded deterministische Snowflake-IDs pro Stage.**
  `900000000000000001L` + stage_offset (oder ähnlich) für
  Channel/Webhook/Message/Thread. Erlaubt DB-Assertions auf exakte IDs
  (`match.discordChannelId == 900000000000000001L`). REJECTED: ULID-
  ähnliche Test-IDs (mehr Boilerplate, kein Mehrwert für WireMock-only
  Test).

- **D-98-E2E-7: Eine Playwright `Page` durch alle 8 Stages.** `page =
  browser.newPage()` einmal in `@BeforeAll`/`@BeforeEach`. Simuliert echtes
  Operator-Browser-Verhalten inkl. CSRF-Cookies + Flash-Messages +
  Modal-State. REJECTED: Komplett-Reset zwischen Stages (zu langsam,
  unrealistisch).

- **D-98-E2E-8: Test-Package `src/test/java/org/ctc/e2e/discord/lifecycle/`.**
  Neuer Sub-Package als sibling zu existierenden `posts/`, `matchday/`,
  `forum/`. Semantisch klar ("lifecycle" = full-walkthrough). Klasse:
  `DiscordFullMatchdayLifecycleE2ETest`.

- **D-98-E2E-9: UAT-08 Live-Lifecycle als Phase-98-Close-Bedingung in
  STATE.md § Pending UATs.** Phase 98 schließt nach Plan 98-03 + erfolgreicher
  manueller UAT-08-Validierung durch Operator gegen Test-Guild. UAT-08-
  Procedure wird in `docs/operations/discord-integration.md` § 7 dokumentiert
  (Plan 98-01). UAT-08-PASS-Notiz landet retroaktiv in STATE.md +
  98-VALIDATION.md analog zu UAT-07 in Phase 97. WireMock-only Phase-Close
  als CI-Gate ist nicht ausreichend — UAT-08 ist Pflicht VOR
  `/gsd-complete-milestone` per User-Workflow.

- **D-98-E2E-10: Multipart-Assertions: filename + Content-Type + Body-Size
  > 1024 bytes.** Pro Attachment-Stage: assert Multipart-Part
  `name="files[0..N]"`, `filename` matcht `team-card-{i}.png` /
  `settings-race-{i}.png` / `lineups-race-{i}.png` / `provisional-{md}.png`
  / `match-results-{m}.png`, `Content-Type: image/png`, `bodySize > 1024`
  (echte PNGs, nicht leere). Pragmatisch ohne Pixel-Hash-Brittleness.

- **D-98-E2E-11: `WireMockDiscordStubs` Helper-Klasse in
  `src/test/java/org/ctc/discord/wiremock/`.** Static-Method-Holder neben
  Production-`org.ctc.discord.*` Code. Test-only-Visibility. Reduziert
  Mega-Test-Datei auf ~120 Zeilen. Wiederverwendbar für künftige ITs.
  REJECTED: Inline-Stubs im Test (~250 Zeilen, schwer lesbar); Helper im
  `e2e/discord/lifecycle/`-Package (verhindert späteren Reuse cross-Package).

- **D-98-E2E-12: Nur `@Tag("e2e")`, kein Sub-Tag.** Konsistent mit allen
  Phase-94-97 E2E-Tests. Selektives Triggern via
  `-Dit.test=DiscordFullMatchdayLifecycleE2ETest -DfailIfNoTests=false`.

### Q-98-02 — Mobile `.card`-Overflow-Fix Scope (Area 2)

- **D-98-MOB-1: Globaler CSS-Sweep auf `.card` + `.form-group input/
  select/textarea` + `.searchable-dropdown .dropdown-list` in
  `admin.css`.** Eine zentrale Korrektur statt targeted `.card--discord`-
  Variante: `min-width: 0` + `box-sizing: border-box` + `max-width: 100%`
  auf `.card` (Line 166); `min-width: 0` auf `.form-group` input/select/
  textarea (Line 309-311); `max-width: 100%` auf
  `.searchable-dropdown .dropdown-list` (Line 823). Wirkt auf ALLE 100+
  admin-Seiten konsistent — vermeidet späteren Drift auf nicht-Discord-
  Pages. REJECTED: `.card--discord` Modifier-Klasse (lässt Tech-Debt
  liegen, widerspricht [[feedback-in-milestone-polish]] Spirit); Hybrid-
  Variante (doppelte Surface).

- **D-98-MOB-2: Mobile-Padding-Reduktion via existierendem
  `@media (max-width: 640px)` (Line 221).** `.card { padding: 16px; }`
  hinzugefügt im selben MQ den `.discord-actions` Phase 94 nutzt. 16 px
  statt 24 px = 16 px mehr inhaltliche Breite (375 → 343 statt 327). Keine
  Desktop-Auswirkung. REJECTED: Padding global 16 px (zu viel
  Layout-Blast-Radius); Padding bleibt 24 px (engte Inputs auf
  Webhook-URL-Felder).

- **D-98-MOB-3: Verifikation auf 9 Pages × Desktop + Mobile (375×667).**
  ROADMAP-Krit-6 Pflicht: `/admin/discord-config`, `/admin/teams/{id}/edit`,
  `/admin/matches/{id}`, `/admin/matches/{id}/edit`. Stichproben für
  Regression-Detection: `/admin` Dashboard, `/admin/seasons/{id}`,
  `/admin/matchdays/{id}`, `/admin/drivers`, `/admin/tools`. 18 Screenshots
  in `.screenshots/98-mobile-polish/` (gitignored,
  [[feedback-screenshots-folder]]).

- **D-98-MOB-4: Easy non-Discord Mobile-Wins im selben Plan adressieren
  (< 30 min Aufwand) per [[feedback-in-milestone-polish]].** Größere
  Findings werden in DEFERRED notiert + zu v1.14 zugeordnet. Wave-Pause
  nicht pro-Fund, sondern am Ende des Plans (Standard).

### Q-98-03 — DOCS-02 Runbook Strategy (Area 3)

- **D-98-DOCS-1: Inkrementelle Erweiterung der bestehenden 417-Zeilen-
  `discord-integration.md`, KEINE Restruktur.** §§ 1-5 bleiben mit
  minimalen Edits (OAuth-Bitmask Line 60-75 ist bereits vollständig
  inkl. `Manage Threads`; Token-Wiring § 1.5 ist bereits gut). Neue
  Sektionen werden APPENDED. History-friendly + git-blame bleibt
  intakt + Reader-Pfade unverändert.

- **D-98-DOCS-2: Neue Sektionen:**
  - **§ 1.9 Forum-Channel + Thread Setup** (race-results + standings):
    Discord-UI-Schritte + `discord_global_config.race_results_forum_webhook_url`
    + `seasons.discord_race_results_thread_id` Workflow, selbe Sequenz
    für Standings.
  - **§ 2.3 Daily Operations**: Phase 94-97 Operator-Workflows inkl.
    aller 11 Post-Types + Pre-Flight + Stale-Detection + Re-Post-Semantik
    + Archive-Modal.
  - **§ 6 Token-Rotation Procedure**: Regenerate-Flow + `.env`-Update +
    `app.sh restart` + Verification + Emergency-Rotation bei Leak.
  - **§ 7 UAT-08 Procedure + Extended Troubleshooting**: 8-Stage
    Full-Matchday-Lifecycle Operator-Procedure (analog UAT-03/04/05/06/07)
    + neue Troubleshooting-Entries für `category-full` (Phase 94),
    `not-found bei archived channels`, missing webhook/thread-id, missing
    `discordTeaser`.

- **D-98-DOCS-3: Screenshot-Strategie — App-UI automatisiert, Discord-
  Portal/Server textuell.** Per User-Direktive 2026-05-24: "alles kann
  automatisiert werden, ohne dass tatsächliche Screenshots bereitgestellt
  werden müssen". App-UI Screenshots werden via playwright-cli
  automatisiert generiert und committed nach
  `docs/operations/images/discord/` (NICHT `.screenshots/` da gitignored).
  Discord-Developer-Portal + Discord-Server-Interaktionen werden
  TEXTUELL als nummerierte Schritte dokumentiert. REJECTED: Wiki-Hosted
  Discord-Portal-Screenshots (User-Override 2026-05-24); externe URLs
  (rot-Risk).

- **D-98-DOCS-4: Single-Audience (Operator) durchgängig.** Bestehende
  Imperativ-Stil-Konvention (§§ 1-5: "You open the Developer Portal")
  bleibt für neue Sektionen unverändert. Keine Setup/Daily-Pfad-Spaltung.
  REJECTED: Quick-Start-Top-Section (bricht §1-5 Setup-First-Struktur);
  zwei separate Dateien (bricht docs/operations/-Konvention).

### Q-98-04 — Plan Decomposition + Milestone-Close (Area 4)

- **D-98-PLAN-1: 3 Plans — sequenziell inline auf `gsd/v1.13-discord-
  integration`, Mobile-Polish zuerst, dann E2E, dann DOCS-03 + Close.**
  Reihenfolge:
  - **Plan 98-01** Polish+Runbook (DOCS-02 + ROADMAP-Krit-6 Mobile) —
    Mobile CSS-Sweep landet zuerst, sodass nachfolgende Plan-Screenshots
    (98-02 E2E sieht den polished Layout, Plan 98-03 README/Wiki nutzt
    finale Screenshots) konsistent sind.
  - **Plan 98-02** E2E-01 Mega-Walkthrough Test +
    `TestDataService.seedFullMatchdayLifecycle` +
    `WireMockDiscordStubs` Helper.
  - **Plan 98-03** DOCS-03 README + Wiki + MILESTONES.md +
    REQUIREMENTS.md Flip + PR-Body Final-Update.
  REJECTED: 4 oder 5 Plans (zu granular, zu viele Wave-Pauses); E2E zuerst
  (E2E würde gegen pre-polished Layout schreiben + müsste neu screenshoten
  nach Polish). Keine Worktrees, keine Subagents per
  [[feedback-inline-sequential-execution]]. Wave-Pause + Mobile-Sweep nach
  JEDEM der 3 Plan-Closes.

- **D-98-PLAN-2: REQ-ID Mapping pro Plan:**
  - Plan 98-01 → DOCS-02 + ROADMAP § Phase 98 Erfolgskrit 6 ("Mobile-
    viewport `.card` overflow")
  - Plan 98-02 → E2E-01
  - Plan 98-03 → DOCS-03 + Milestone-Bookkeeping (MILESTONES.md +
    REQUIREMENTS.md flip + PR-Body)

- **D-98-PLAN-3: Test-Count + Coverage-Erwartung pro Plan:**
  - 98-01: ~3-5 Tests (MD-Link-Validator-Test + Bildpfad-Existenz-Test
    für `docs/operations/images/discord/*.png` + ggf. UI-Smoke-Tests für
    polished Mobile-Layouts).
  - 98-02: 1 Mega-`DiscordFullMatchdayLifecycleE2ETest`. Coverage-Delta
    erwartet zwischen −0.1 und +0.3 pp; Baseline 88.88 % bleibt; Plan
    asserted Post-Run JaCoCo ≥ Phase-97-Baseline.
  - 98-03: 0 Tests (rein dokumentarisch).
  Total Phase 98 ~4-6 neue Tests.

- **D-98-PLAN-4: `/gsd-complete-milestone` ist manueller User-Step VOR
  Squash-Merge, NACH UAT-08-PASS.** Per User-Direktive 2026-05-24:
  "ich mache das manuell, nachdem ich einen kompletten Test durchgeführt
  habe. Der GSD complete milestone Task ist in meinem Workflow immer der
  manuelle Abschluss vor dem Merge." Sequenz:
  1. Plan 98-01 + 98-02 + 98-03 committed auf `gsd/v1.13-discord-integration`
  2. `/gsd-validate-phase 98` → 98-VALIDATION.md PASS
  3. User führt UAT-08 manuell auf Test-Guild durch (Procedure aus
     `docs/operations/discord-integration.md § 7`)
  4. UAT-08-PASS in STATE.md eingetragen
  5. User ruft `/gsd-complete-milestone` MANUELL → archiviert
     `.planning/phases/9{2..8}-*/` nach `.planning/milestones/v1.13-phases/`,
     schreibt finale `v1.13-MILESTONE-AUDIT.md`
  6. PR-Body Final-Update via `gh pr edit`
  7. User reviewt PR, drückt Squash-Merge mit Subject
     `feat(v1.13): discord integration & carry-forwards`
  8. CI macht Tag + Release per [[feedback-no-local-git-tags]].
  Plan 98-03 enthält die Schritte 1-4-6 als Plan-Tasks; Schritte 5+7+8 sind
  User-Manual. **Reihenfolge ist hardgelocked** — `gsd-complete-milestone`
  läuft NIE post-merge.

- **D-98-PLAN-5: ALLE bookkeeping-Updates müssen Pre-Merge in der PR drin
  sein.** Per User-Direktive 2026-05-24: "Wir dürfen nicht direkt auf
  master pushen, daher muss alles im PR mit drin sein." Plan 98-03
  committet:
  - MILESTONES.md v1.13-Entry (Phases 92-98 + Plans + Tests + Coverage +
    CI E2E-Time + 25/25 REQ-Coverage + ggf. v1.14-Carry-Over-Items)
  - REQUIREMENTS.md Resolved-Marker für alle 25 v1.13-REQ-IDs
    (UX-01 / COV-01 / CLEAN-01 / DOCS-01 / BOOK-01 / INFRA-01..03 /
    CHAN-01..03 / POST-01..05 / GRAFX-01 / FORUM-01..02 / POST-06..08 /
    E2E-01 / DOCS-02 / DOCS-03)
  - PR-Body Final-Update via `gh pr edit <num> --body-file`
  Korrigiert die zuvor in [[feedback-pr-description-update]] (möglicherweise
  fehlerhaft interpretierte) "post-merge bookkeeping"-Annahme.

### Q-98-05 — Wiki-Update Mechanik (Area 4 follow-up)

- **D-98-WIKI-1: Plan 98-03 cloned `ctc-manager.wiki.git` lokal und
  pusht Updates direkt.** `.wiki-clone/` als gitignored-Folder im
  Repo-Root, Plan-Tasks: `git clone https://github.com/jegr78/ctc-manager.wiki.git
  .wiki-clone` → Wiki-Dateien editieren (`Discord-Integration.md` neu,
  `Home.md` Sidebar update, ggf. Wiki-eigenes `README.md`) → committen +
  pushen. Automatisierbar in einem Plan-Run.

- **D-98-WIKI-2: Wiki-Pages die angelegt/aktualisiert werden:**
  - **NEW `Discord-Integration.md`** — canonical paragraph identisch zur
    README-Version, plus Link zu `docs/operations/discord-integration.md`
    im main-Repo, plus 1-2 App-UI-Screenshots als visual hook
    (Wiki-Asset-Upload via `git add`).
  - **UPDATE `Home.md`** — Sidebar/Navigation-Link zu Discord-Integration
    analog zu existierenden Wiki-Pages.
  - **UPDATE Wiki-eigenes `README.md`** falls vorhanden — sonst skip.
  DEFERRED: Eine separate `Discord-Setup-Walkthrough.md` Subseite mit
  annotated Discord-Portal-Screenshots → v1.14 oder ad-hoc wenn Operator
  manuell Portal-Screenshots ergänzen möchte (User wollte das in v1.13
  NICHT, Portal-Steps bleiben textuell).

- **D-98-WIKI-3: Canonical Discord-Integration Paragraph — Kurz-Beschreibung
  + Use-Cases + Setup-Link.** ~5-8 Sätze: was die Integration tut
  (per-match Discord-Channel + 11 strukturierte Posts + Forum-Threads für
  race-results + standings) + für wen (League-Operator) + Highlight-Features
  (Auto-Edit auf Schedule/Preview, Pre-Flight-Gates, Stale-Detection) +
  Link zu `docs/operations/discord-integration.md` + 1 App-UI Screenshot
  von `/admin/discord-config`. REJECTED: detaillierte Feature-Liste mit
  allen 11 Post-Types (stale-prone, gehört ins Runbook); Marketing-Ton
  (bricht README-Stil).

### Q-98-06 — Matchday-Level Announcement Post Output Forms (2026-05-25 Re-Open)

Phase 98 is re-opened with two new matchday-level posts on the **Announcement-Channel** (`DiscordGlobalConfig.announcementWebhookUrl`, "CTC Announcements APP" identity — same channel as MATCH_PREVIEW POST-06, NOT the per-Match-Channel from Phase 94). Operator workflow: PAIRINGS (pre-pick, weeks before matchday) → SCHEDULE (post-pick, days before race weekend) → per-Match-Preview (MATCH_PREVIEW per match) → per-Match-Channel posts (Phase 95) → Forum-Thread results (Phase 96/97).

D-97-PREV-2's "MATCHDAY_PAIRINGS dropped, deferred to v1.14" is **explicitly re-scoped into v1.13** per operator decision 2026-05-25 (in-milestone-polish discovery during UAT-08 Stage 5 verification + operator screenshots of existing manual workflow). Historical 97-CONTEXT.md and STATE.md UAT-08 Stage 11 note remain immutable; this CONTEXT entry is the authoritative re-scope marker.

- **D-98-PAIR-1: MATCHDAY_PAIRINGS = Hybrid (Markdown body + 1 Pairings-PNG attachment) on Announcement-Channel.** Markdown body contains: H1 `Match Day {N} Pairings`, bullet `Home Teams are on the left hand side.`, bullet `Deadline for the picks: <t:UNIX:F> (use the pinned form in your private team chat channel)`, bullet `Scheduled weekend for the races: {weekend}`, line `Game On! :CTC:`. Pairings-PNG via new `MatchdayPairingsGraphicService` (Playwright analog `MatchdayOverviewGraphicService`): matchday-header (`Match Day {N}` + `{year}`), :CTC: watermark logos top-left + top-right, N rows (one per match) with `{rating}` + Team-A logo+name (left) + vs-emoji (center) + Team-B name+logo+{rating} (right), brand-color gradients per team. Reference screenshot in user-message 2026-05-25. REJECTED: Pure-PNG (no Markdown body — operator needs Deadline + Weekend as machine-readable text); Pure-JSON-Embed (loses the visual Pairings-impact); Hybrid with N×2 Team-Card-PNGs (Discord 10-attachment limit at N=7 matches).

- **D-98-PAIR-2: Flyway V15 — 2 new Matchday columns + 1 new DiscordGlobalConfig column.** `V15__add_matchday_pairings_fields.sql`:
  - `matchday.pick_deadline` (TIMESTAMP / DATETIME, nullable — H2 + MariaDB compatible)
  - `matchday.scheduled_weekend` (VARCHAR(64), nullable — free-text like "22-24 May" or "Tue 26 May" — operator-chosen format)
  - `discord_global_config.matchday_pairings_template` (TEXT, nullable — template Markdown body with `{{deadline}}` + `{{weekend}}` placeholders + Default-fallback in service code)
  REJECTED: free-text-only on matchday (lose structured deadline → no Discord `<t:UNIX:F>` rendering); 3 matchday columns (overkill, global template suffices).

- **D-98-PAIR-3: Template-Resolving in service code, `{{deadline}}` → `<t:UNIX:F>` Discord-timestamp.** `DiscordPostService.buildMatchdayPairingsMarkdown(matchday, config)` reads `config.getMatchdayPairingsTemplate()`, falls back to hardcoded Default if null/blank, replaces `{{deadline}}` with `DiscordTimestamps.longDateTime(matchday.getPickDeadline())` and `{{weekend}}` with `matchday.getScheduledWeekend()`. Empty placeholders render as `_TBD_` (analog MATCH_PREVIEW streamLink fallback). Default-Template hardcoded in service initializer (operator can override globally).

- **D-98-SCHED-1: MATCHDAY_SCHEDULE = pure Multipart-PNG on Announcement-Channel.** No Markdown body, no JSON embed, no template. New `MatchdayScheduleGraphicService` (Playwright analog) generates one PNG: matchday-header (`Match Day {N} Schedule` + `Community Team Cup {year}`), :CTC: watermark logos top-left + top-right, N rows (one per match) with `{Day, DD Mon HH:MM TZ}` (left, formatted from `match.races[0].dateTime` in Europe/London BST/GMT per operator), Team-A logo+name+`{W-L}` record (mid-left), vs-emoji (center), Team-B record+name+logo (right). Reference screenshot in user-message 2026-05-25.

- **D-98-SCHED-2: No new schema for MATCHDAY_SCHEDULE.** Data sources are all pre-existing fields: `match.races[0].dateTime` (Race entity), `match.streamLink` (optional — visual is operator-driven via Stream-Link Match-Edit; not embedded in PNG layout per screenshot), `seasonTeam.{wins,draws,losses}` for `W-L` record (or `W-D-L` if draws are non-zero — Planner-Discretion). Timezone: render in `Europe/London` per `DiscordTimestamps` convention; operator confirmed `BST` suffix in screenshot example.

### Q-98-07 — REQ-IDs + Plan-Decomposition (2026-05-25 Re-Open)

- **D-98-REQ-1: Two new REQ-IDs — POST-09 (MATCHDAY_PAIRINGS) + POST-10 (MATCHDAY_SCHEDULE).** REQUIREMENTS.md gets two new rows under Discord-Posts section, both initially `Pending`, flipped to `Resolved` by Plan 98-07 after live UAT-Re-Run. POST-06 (MATCH_PREVIEW) remains untouched and stays Resolved. v1.13-REQ-coverage rises from 25 → 27. REJECTED: extending POST-06 (would break Phase-97-audit historical immutability + vermix per-match with matchday-level scope); single combined POST-09 covering both (loses Pairings-vs-Schedule traceability — Hybrid vs Pure-PNG are very different surfaces).

- **D-98-REQ-2: ROADMAP-bump — v1.13-ROADMAP Phase-98 gains Erfolgskriterien 7 + 8.** Erfolgskrit 7: "MATCHDAY_PAIRINGS Hybrid-Post on Announcement-Channel + V15-migration + IT + E2E + Pre-Flight-button-state + live-verified Stage 14". Erfolgskrit 8: "MATCHDAY_SCHEDULE pure-PNG Post on Announcement-Channel + IT + E2E + Pre-Flight-button-state + live-verified Stage 15". `.planning/REQUIREMENTS.md` carry-forward block bumps to 27 REQ-IDs.

- **D-98-PLAN-6: Three new plans, sequential inline, Pairings → Schedule → Bundle-Verify.** (Extends D-98-PLAN-1.) Reihenfolge:
  - **Plan 98-05** — POST-09 MATCHDAY_PAIRINGS: Flyway V15 + new `MatchdayPairingsGraphicService` + Thymeleaf-Template `admin/matchday-pairings-render` + new `matchday-pairings-template.html` custom-template-file + new `DiscordPostService.canPostMatchdayPairings(matchday, config)` + `postMatchdayPairings(matchday)` + `buildMatchdayPairingsMarkdown(...)` + new Button "Post Matchday Pairings" on Matchday-Detail Discord-Actions card + new Form for `pick_deadline` + `scheduled_weekend` on Matchday-Edit OR Matchday-Detail-Inline-Edit (Planner-Discretion which) + new global Template-Edit-Field in Discord-Config-Form + IT `DiscordPostServiceMatchdayPairingsIT` + E2E `MatchdayDetailDiscordAnnouncementE2ETest` + Mockito-Unit `DiscordPostServiceMatchdayPairingsPreFlightTest`. Operator-driven Re-Post-Click, NO Auto-Edit-Hook (per D-98-AUTO-1).
  - **Plan 98-06** — POST-10 MATCHDAY_SCHEDULE: new `MatchdayScheduleGraphicService` + Thymeleaf-Template `admin/matchday-schedule-render` + new `matchday-schedule-template.html` custom-template-file + new `DiscordPostService.canPostMatchdaySchedule(matchday, config)` + `postMatchdaySchedule(matchday)` (NO `buildMatchdayScheduleMarkdown` — pure PNG) + new Button "Post Matchday Schedule" on Matchday-Detail Discord-Actions card (sibling to Pairings + Results + Power-Rankings) + IT `DiscordPostServiceMatchdayScheduleIT` + EXTEND `MatchdayDetailDiscordAnnouncementE2ETest` with Schedule-button cases + EXTEND `DiscordPostServiceMatchdayPairingsPreFlightTest` with Schedule-Pre-Flight tests OR new sibling class. No schema migration (per D-98-SCHED-2). Operator-driven Re-Post-Click, NO Auto-Edit-Hook.
  - **Plan 98-07** — Bundle-Verify + Live-UAT-Re-Run + Pre-Merge-Bookkeeping: ONE `./mvnw clean verify -Pe2e` covering 98-04 Schedule-Embed-IT + 98-05 Pairings + 98-06 Schedule (per D-98-VERIFY-1). Operator-Live-UAT-Re-Run for 3 new stages (5c + 14 + 15) per D-98-UAT-1. REQUIREMENTS.md POST-09 + POST-10 flip Pending → Resolved. v1.13-ROADMAP.md Phase-98 Erfolgskriterien 7 + 8 mark ✓. MILESTONES.md v1.13-Entry updated (3 new plans, +6 tests, coverage delta, 27/27 REQ-Coverage). PR #130 body final-update via `gh pr edit 130 --body-file -` (squash-subject locked: `feat(v1.13): discord integration & carry-forwards`). STATE.md UAT-08 in-place edit adds Stages 5c + 14 + 15 (per D-98-UAT-2). DOCS-02 Runbook §§ 2.3 + 7 erweitert um die 2 neuen Buttons + UAT-Stages.

- **D-98-PLAN-7: Wave-Pause nach jedem der 3 neuen Plans.** Konsistent mit [[feedback-wave-pause]]. Inline-sequential auf `gsd/v1.13-discord-integration`, kein Worktree, kein Subagent (per [[feedback-inline-sequential-execution]]).

- **D-98-FILES-1: Files-modified-Whitelist (Append-Only auf shared Files).**
  - **Plan 98-05:**
    - NEU: `src/main/resources/db/migration/V15__add_matchday_pairings_fields.sql`
    - NEU: `src/main/java/org/ctc/admin/service/MatchdayPairingsGraphicService.java`
    - NEU: `src/main/resources/templates/admin/matchday-pairings-render.html`
    - NEU: `src/main/java/org/ctc/admin/dto/MatchdayPairingsForm.java` (für `pick_deadline` + `scheduled_weekend` — Planner-Discretion: separater Edit-Endpoint oder integriert in `MatchdayForm`)
    - NEU: `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsIT.java`
    - NEU: `src/test/java/org/ctc/e2e/discord/announcement/MatchdayDetailDiscordAnnouncementE2ETest.java`
    - NEU: `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsPreFlightTest.java`
    - APPEND: `src/main/java/org/ctc/domain/model/Matchday.java` (2 neue Felder + Getter/Setter via Lombok)
    - APPEND: `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` (1 neues Feld `matchdayPairingsTemplate`)
    - APPEND: `src/main/java/org/ctc/discord/service/DiscordPostService.java` (`canPostMatchdayPairings` + `postMatchdayPairings` + `buildMatchdayPairingsMarkdown`)
    - APPEND: `src/main/java/org/ctc/admin/controller/MatchdayController.java` (neuer POST-Endpoint für "Post Matchday Pairings" Button + GET-Page für Pick-Deadline + Scheduled-Weekend-Edit ODER Edit über bestehende Matchday-Form)
    - APPEND: `src/main/resources/templates/admin/matchday-detail.html` (neuer Button in Discord-Actions card + ggf. Inline-Edit-Felder)
    - APPEND: `src/main/resources/templates/admin/discord-config.html` (neues globales Template-Edit-Field)
    - APPEND: `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` (`matchdayPairingsTemplate` Property)
    - APPEND: `src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java` (form → entity Mapping)
    - APPEND: `src/main/java/org/ctc/discord/web/DiscordConfigController.java` (nullSafe-Wiring)
  - **Plan 98-06:**
    - NEU: `src/main/java/org/ctc/admin/service/MatchdayScheduleGraphicService.java`
    - NEU: `src/main/resources/templates/admin/matchday-schedule-render.html`
    - NEU: `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayScheduleIT.java`
    - APPEND: `src/test/java/org/ctc/e2e/discord/announcement/MatchdayDetailDiscordAnnouncementE2ETest.java` (Schedule-Button-Cases)
    - APPEND: `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsPreFlightTest.java` (Schedule-Pre-Flight) ODER NEU `DiscordPostServiceMatchdayScheduleePreFlightTest.java` (Planner-Discretion)
    - APPEND: `src/main/java/org/ctc/discord/service/DiscordPostService.java` (`canPostMatchdaySchedule` + `postMatchdaySchedule`)
    - APPEND: `src/main/java/org/ctc/admin/controller/MatchdayController.java` (neuer POST-Endpoint "Post Matchday Schedule")
    - APPEND: `src/main/resources/templates/admin/matchday-detail.html` (Sibling-Button in Discord-Actions card)
    - Kein Schema, kein DTO, kein Form.
  - **Plan 98-07:**
    - APPEND: `.planning/REQUIREMENTS.md` (POST-09 + POST-10 add Pending → Resolved-Flip, 25/25 → 27/27)
    - APPEND: `.planning/milestones/v1.13-ROADMAP.md` (Phase-98 Erfolgskriterien 7 + 8 + ✓-Marker)
    - APPEND: `.planning/MILESTONES.md` (v1.13-Entry update — 3 new plans, test-count + coverage delta)
    - APPEND: `.planning/STATE.md` (UAT-08 in-place edit + Stages 5c/14/15 + Plan 98-04 Deferred-Item-Resolution-Note)
    - APPEND: `docs/operations/discord-integration.md` (§ 2.3 Daily Operations 2 neue Buttons + § 7 UAT-08-Stages 14 + 15)
    - APPEND: `docs/operations/images/discord/*.png` (neue App-UI Screenshots playwright-cli für die 2 neuen Buttons)
    - APPEND: `.planning/phases/98-polish-e2e-docs-close/98-VALIDATION.md` (Phase-Goal-Backward-Walkthrough + Sub-Deliverables 9 + 10 + Test-Counts + JaCoCo)
    - GH-API: `gh pr edit 130 --body-file -` (PR-Body Final-Update)

### Q-98-08 — Auto-Edit + Pre-Flight + Test-Architektur (2026-05-25 Re-Open)

- **D-98-AUTO-1: Beide neuen Posts operator-driven (NO AFTER_COMMIT-Listener).** Spiegelt POST-07a (MATCHDAY_OVERVIEW) + POST-07b (POWER_RANKINGS) + POST-08 (STANDINGS) Pattern aus Phase 97. KEIN AFTER_COMMIT-Event, KEIN Listener-PATCH bei Field-Edit. Operator klickt "Re-Post Matchday Pairings" / "Update Matchday Pairings" (Label flip via Stale-Detection) bzw. die Sibling-Buttons für Schedule. REJECTED: Auto-Edit für Pairings only (Markdown-Diff trivial) — User-decision 2026-05-25 für Konsistenz mit Forum-Thread-Posts; Auto-Edit für Schedule (PNG-Re-Render Playwright-teuer 30-60s, operator-driven besser).

- **D-98-PRE-1: MATCHDAY_PAIRINGS Pre-Flight-Predicates.** `canPostMatchdayPairings(matchday, config)` returns `MatchPreviewPreFlightResult(canPost, disabledReason)`:
  - `matchday.pickDeadline != null` ELSE "Set pick deadline first"
  - `matchday.scheduledWeekend != null && !blank` ELSE "Set scheduled weekend first"
  - alle Matches haben HomeTeam + AwayTeam zugewiesen (matchday.matches.allMatch(m -> m.getHomeTeam() != null && m.getAwayTeam() != null)) ELSE "Assign teams to all matches first"
  - `config.announcementWebhookUrl != null && !blank` ELSE "Configure announcement-webhook in Discord settings"
  - REJECTED: pre-flight on template (Default-fallback macht Template-set non-Pflicht); pre-flight on `discordTeaser` (matchday-level Post hat kein per-Match-Teaser).

- **D-98-PRE-2: MATCHDAY_SCHEDULE Pre-Flight-Predicates.** `canPostMatchdaySchedule(matchday, config)`:
  - alle Non-BYE-Matches haben mindestens 1 Race mit `dateTime != null` (matchday.matches.filter(non-bye).allMatch(m -> firstRaceTime(m).isPresent())) ELSE "Set Race date+time for all matches first"
  - `config.announcementWebhookUrl != null && !blank` ELSE "Configure announcement-webhook in Discord settings"
  - REJECTED: Pre-Flight on streamLink (operator-driven manual fill, "TBA"-Fallback ist OK aber das PNG zeigt streamLink nicht prominent per Screenshot); Pre-Flight on alle Team-Records (records sind auto-computed, kein operator action erforderlich).

- **D-98-STALE-1: Stale-Detection Pattern.** Für Pairings: `matchday.updatedAt > post.updatedAt` (jede Edit auf pick_deadline / scheduled_weekend / matchday-level field triggert Label-Flip). Für Schedule: `MAX(match.updatedAt, race.updatedAt) > post.updatedAt` über alle Non-BYE-Matches/Races im Matchday. Label-Flip: grey "Re-Post Matchday Pairings/Schedule" → light-yellow "Update Matchday Pairings/Schedule" + warn-tooltip. Spiegelt MATCH_RESULTS Stale-Pattern (Phase 95 D-95-04). Planner-Discretion: ob die Stale-Check als SQL-Predicate-Aggregation auf der Matchday-Detail-Seite live-läuft oder als pre-loaded `lastUpdatedAtVsPost`-Field via Service-Aggregation.

- **D-98-TEST-3: Pro Plan 1 IT + 1 E2E + 1 Mockito-Unit (6 neue Tests total).** Spiegelt Phase 95/97. Test-Klassen:
  - **98-05:** `DiscordPostServiceMatchdayPairingsIT` (Spring-Boot + WireMock + V15-Migration-Check + Multipart-Body-Verify mit `withQueryParam` falls thread_id, `matchingJsonPath` falls Embed, `bodySize > 1024` für PNG-Attachment per [[feedback-wiremock-vs-real-api]]; covering Initial-Post + Re-Post-PATCH + Pre-Flight-Reject-Branches inkl. 4 sealed `DiscordApiException`-Permits) + `MatchdayDetailDiscordAnnouncementE2ETest` (Playwright sibling-button-state-matrix: Pre-Flight-Disabled-Tooltips × 4 Reject-States, Initial-Post-Success, Re-Post-Stale-Label-Flip nach matchday-Edit) + `DiscordPostServiceMatchdayPairingsPreFlightTest` (Mockito-Unit, alle 4 Pre-Flight-Branches × can/cant + 2 success-state).
  - **98-06:** `DiscordPostServiceMatchdayScheduleIT` (analog Pairings IT, ohne V15 + ohne Markdown-JSON-Path-Assertions, mit Multipart-PNG-Body-Verify + bodySize-Check) + EXTEND `MatchdayDetailDiscordAnnouncementE2ETest` (Schedule-Button-Cases als zusätzliche `@Test`-Methoden) + EXTEND Mockito-Unit oder NEU `DiscordPostServiceMatchdaySchedulePreFlightTest`.
  - **`@Tag`-Konvention:** ITs untagged-or-`@Tag("integration")` (Surefire route ist `@Tag`-aware per CLAUDE.md "Test Categorization"), E2E `@Tag("e2e")` in `org.ctc.e2e.discord.announcement` package, Mockito-Unit untagged.

- **D-98-COV-1: Coverage-Erwartung.** Baseline nach Phase 98-03 = 88.71 % JaCoCo line. Plan 98-05 + 98-06 fügen 2 neue Playwright-Services hinzu — `MatchdayPairingsGraphicService` + `MatchdayScheduleGraphicService` werden in `pom.xml` `<excludes>` ergänzt (Playwright-runtime-services sind nicht JaCoCo-instrumentierbar per CLAUDE.md "Excluded from coverage"). Coverage-Delta erwartet zwischen +0.1 und +0.5 pp durch neue IT/E2E-Coverage. Coverage-MUST ≥ 88.71 %.

### Q-98-09 — UAT-08-Re-Run + Bundle-Verify + Bookkeeping (2026-05-25 Re-Open)

- **D-98-VERIFY-1: EIN voller `./mvnw clean verify -Pe2e` in Plan 98-07.** Plans 98-05 + 98-06 nutzen während TDD-Loop nur targeted `-Dit.test=ClassName -DfailIfNoTests=false`. Wave-Pause nach jedem Plan stoppt zum User-Feedback (kein verify required). Plan 98-07 macht EINEN `./mvnw clean verify -Pe2e` der ALLE drei deferred Items abdeckt: 98-04 `DiscordPostServiceScheduleIT` (inline:false invariants) + 98-05 Pairings + 98-06 Schedule. Coverage-Snapshot + Test-Count harvested für MILESTONES + PR-Body.

- **D-98-UAT-1: Inkrementelles UAT-Re-Run — 3 neue Stages + Smoke-Regression.**
  - **Stage 5c** (98-04 SCHEDULE Re-Post live-PATCH): Operator klickt Re-Post Schedule nach Dev-Server-Restart → verifiziert dass die Discord-Embed-Felder jetzt mit `inline: false` rendern (1 Field pro Zeile) UND dass `messageId` derselbe bleibt (PATCH, nicht POST).
  - **Stage 14** (98-05 MATCHDAY_PAIRINGS): Operator setzt `pick_deadline` + `scheduled_weekend` + globales Template (oder Default) → klickt "Post Matchday Pairings" → verifiziert Hybrid-Post auf Announcement-Channel (Markdown-Body korrekt, `<t:N:F>` Deadline, Pairings-PNG mit 7 Matches korrekt gerendert, `:CTC:` Emoji rendert nicht als raw text). Edit pick_deadline → Button-Label flip auf "Update" → Update klicken → PATCH verifiziert (same messageId). Plus 1-2 Pre-Flight-Reject-Stichproben (Webhook clear → Tooltip; Team-Pairing unvollständig → Tooltip).
  - **Stage 15** (98-06 MATCHDAY_SCHEDULE): Operator klickt "Post Matchday Schedule" → verifiziert pure-PNG-Post auf Announcement-Channel (alle 7 Matches mit korrekten Day/Date/HH:MM-BST/GMT-Werten + Team-Records). Edit Race.dateTime → Button-Label flip auf "Update" → Update klicken → PATCH verifiziert.
  - **Smoke-Regression:** 1-2 Stages aus 1-13 zur Regression-Sicherheit (z.B. Stage 1 create-channel + Stage 9 move-to-archive). 30-45 min Operator-Aufwand.

- **D-98-UAT-2: STATE.md UAT-08 in-place edit, KEINE separate UAT-08b-Sektion.** Der bestehende UAT-08 Block in STATE.md wird APPEND-style erweitert: neue Sub-Sektionen "Stage 5c (Plan 98-04 Re-Post Live-Verifikation)", "Stage 14 (Plan 98-05 MATCHDAY_PAIRINGS)", "Stage 15 (Plan 98-06 MATCHDAY_SCHEDULE)" werden ans Ende des UAT-08-Blocks angehängt mit Date-Stamps 2026-05-25 (oder neueren Date wenn UAT-Re-Run später erfolgt). Originaler UAT-08-PASS-Snapshot 2026-05-25 bleibt textuell unverändert.

- **D-98-BOOK-1: Plan 98-07 Pre-Merge-Bookkeeping-Tasks (alle in PR #130).** Per D-98-PLAN-5 (carry-forward) müssen ALLE bookkeeping-Updates Pre-Squash-Merge in PR #130 sein:
  1. `.planning/REQUIREMENTS.md` — POST-09 + POST-10 add als Pending (Plan-Start) → flip Resolved nach UAT-Re-Run-PASS (Plan-End). 25/25 → 27/27 REQ-Coverage.
  2. `.planning/milestones/v1.13-ROADMAP.md` — Phase-98 Erfolgskriterien 7 + 8 add + ✓-Marker.
  3. `.planning/MILESTONES.md` — v1.13-Entry update: "+3 plans (98-05/06/07), +6 tests, JaCoCo {new}%, 27/27 REQ-Coverage". Plan 98-04 wird als Spezial-Polish-Plan markiert (Plan-Counter total 7 = 98-01..98-07).
  4. `.planning/STATE.md` — UAT-08 Stages 5c/14/15 (per D-98-UAT-2) + Deferred-Items Schedule-Embed (98-04) als Resolved verlinkt mit Stage-5c-Live-Verifikation.
  5. `docs/operations/discord-integration.md` — § 2.3 Daily Operations: 2 neue Bullet-Points "Post Matchday Pairings (pre-pick announcement)" + "Post Matchday Schedule (post-pick announcement)" mit Pre-Flight-Predicates + Stale-Detection. § 7 UAT-08 Procedure: Stages 14 + 15 ergänzt.
  6. `docs/operations/images/discord/` — neue App-UI-Screenshots via playwright-cli für die 2 neuen Buttons.
  7. PR #130 Body — Final-Update via `gh pr edit 130 --body-file -` mit:
     - Phase-Tracker update auf 98-01 ✓ / 98-02 ✓ / 98-03 ✓ / 98-04 ✓ (polish) / 98-05 ✓ / 98-06 ✓ / 98-07 ✓
     - Coverage-Delta von 88.71 % auf {new-%}
     - Test-Count-Delta +6 neue Tests
     - 27/27 REQ-Coverage (statt 25/25)
     - Squash-subject locked: `feat(v1.13): discord integration & carry-forwards`
  8. 98-VALIDATION.md — Phase-Goal-Backward-Walkthrough refresh mit Sub-Deliverables 9 + 10 + Roll-up der Nyquist-Discipline (98-05 + 98-06 each: 1/0/0 expected; 98-07: n/a — bookkeeping only).

- **D-98-DEV-1: Dev-Server-Restart-Strategie für Live-UAT-Re-Run.** Plan 98-07 verlangt 1 `./mvnw clean` (Bundle-Verify) — per [[feedback-clean-kills-app-pid]] löscht das `data/app-dev.pid`. Operator-Sequenz: (a) Bundle-Verify durchlaufen lassen, (b) `./scripts/app.sh restart --profile dev` (oder `start --profile dev` falls stop fehlschlägt aufgrund pid-loss), (c) Live-UAT-Re-Run 5c+14+15+Smoke. Plan 98-07 dokumentiert das in einem "Live-Verifikation"-Task-Block.

</new_string>
</invoke>

- **D-98-QG-1: Standard-Gates unverändert.**
  - JaCoCo line coverage ≥ 88.88 % (Phase-97-Baseline). Phase 98 fügt
    ~4-6 neue Tests hinzu (überwiegend E2E im Plan 98-02, ein
    Coverage-Touch in Plan 98-01). Erwartung: marginale Delta (−0.1 bis
    +0.3 pp).
  - SpotBugs `BugInstance` count = 0 (blocking, verify-bound).
  - CodeQL gate-step exit 0 auf PR HEAD SHA. Erwartet keine neuen Findings
    (Phase 98 fügt keine HTTP-Outbound-Pfade hinzu; `WireMockDiscordStubs`
    ist Test-only).
  - `EXPORT_ORDER` und `BackupSchema.SCHEMA_VERSION` bleiben unverändert
    (keine neuen Entities in Phase 98).
  - Flyway V1-V13 immutable. Keine neue Migration in Phase 98.
  - `./mvnw clean verify -Pe2e` finishes within v1.12 CI E2E 17:39 ± 20 %
    tolerance. Plan 98-02 fügt 1 neuen E2E-Test + 1 neue
    WireMock-Helper-Klasse hinzu. Erwarteter Impact: + 30-90 s gesamt.

### Test Discipline (carried forward)

- **D-98-TEST-1: Per-Plan Nyquist VALIDATION.md** per [[feedback-validation-discipline]].
  Plans 98-01..03 shippen jeweils mit eigener `VALIDATION.md`. Phase 98
  self-validates via `/gsd-validate-phase 98` vor `/gsd-complete-milestone`.

- **D-98-TEST-2: `@Tag`-Convention per CLAUDE.md.** WireMock-backed ITs
  (kommen in Phase 98 keine zusätzlichen vor; existierende bleiben) →
  `@Tag("integration")`. Playwright E2E (`DiscordFullMatchdayLifecycleE2ETest`,
  ggf. neue Mobile-Smoke-Tests in Plan 98-01) → `@Tag("e2e")`, Package
  `org.ctc.e2e.discord.lifecycle` (Plan 98-02) bzw. existing
  `org.ctc.e2e.*` für Smoke-Tests Plan 98-01. Doc-Helper-Tests
  (MD-Link-Validator, Image-Path-Validator) → untagged Mockito-Unit-Style.

### Production Behavior Boundary

- **D-98-PROD-1: Production-Code-Pfade in Phase 98 begrenzt auf:**
  - **Plan 98-01 (Polish + Runbook):**
    - `src/main/resources/static/admin/css/admin.css` — `.card`,
      `.form-group input/select/textarea`, `.searchable-dropdown
      .dropdown-list`, `@media (max-width: 640px) { .card { padding: 16px } }`.
      APPEND-only Pattern per [[feedback-worktree-file-clobber]] — keine
      bestehenden Klassen overwriten.
    - `docs/operations/discord-integration.md` — § 1.9, § 2.3, § 6, § 7
      neu APPENDED. § 1-5 minimale Edits nur wenn nötig.
    - `docs/operations/images/discord/*.png` — neue committed App-UI
      Screenshots (5-8 PNGs).
  - **Plan 98-02 (E2E):**
    - `src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java` (NEU).
    - `src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java` (NEU).
    - `src/test/java/org/ctc/admin/TestDataService.java` — neue Methode
      `seedFullMatchdayLifecycle()` APPENDED.
    - Keine Production-Code-Edits in `src/main/`.
  - **Plan 98-03 (DOCS-03 + Close):**
    - `README.md` — neuer "Discord Integration" Bullet + Link.
    - `.planning/MILESTONES.md` — v1.13-Entry APPENDED.
    - `.planning/REQUIREMENTS.md` — Resolved-Marker für alle 25 v1.13-REQs.
    - `.planning/STATE.md` — UAT-08 staged, milestone-close-prepared.
    - `.wiki-clone/Discord-Integration.md`, `.wiki-clone/Home.md`,
      `.wiki-clone/README.md` (falls vorhanden) — Wiki-Repo-Updates (NICHT
      Teil des main-Repo-Commits).
    - `.gitignore` — `.wiki-clone/` Eintrag falls noch nicht vorhanden.
  Jeder Plan-SUMMARY asserts dass `git diff --name-only base..HEAD`
  ausschließlich diese Pfade berührt.

### Claude's Discretion

- **Mega-Test-Struktur: ein-`@Test` mit 8 `step1..8`-Private-Methoden
  ODER inline-prozedural in einer `@Test`-Methode** — Planner-Discretion
  basierend auf Failure-Stack-Trace-Lokalisierung. Default: 8 Step-Methoden
  für klare Logs.
- **`WireMockDiscordStubs` Method-Signaturen** — Static-Method-Holder
  vs. Builder-Pattern (`WireMockDiscordStubs.matchChannel()
  .withSnowflake(...).register()`). Planner picks based on call-site
  readability (vermutlich Static-Methods für 8 Stages).
- **Mobile-Polish: zusätzliche Touch-Target-44px-Regel auf `.btn`?** — iOS
  HIG empfiehlt 44 px, existing `.btn { min-height: 36px }` ist darunter.
  Planner-Discretion ob Phase 98 das mitnimmt (in-milestone polish-Argument)
  oder auf DISC-FUTURE schiebt.
- **Wiki-Page-Naming: `Discord-Integration.md` vs `Discord-Integration-Setup.md`
  vs `Discord.md`** — Planner picks based on existing Wiki-Naming-
  Convention (vermutlich Long-Form analog zu `Backup-and-Restore.md`).
- **Runbook-Section-Numbering: § 1.9 vs § 1.10 für Forum-Setup, § 6 vs § 5.x
  für Token-Rotation** — Planner picks based on bestehende Hierarchie
  ohne Bruch der existing § 1-5 Ordnung.
- **README-Bullet-Platzierung: unter "Admin Features" oder eigene
  Sektion** — Planner picks based on existing README-Struktur.
- **Test-Datei-Bildpfade als `docs/operations/images/discord/01-discord-config-cold.png`
  vs `discord-config-cold.png`** — Planner picks (Prefix-Number erlaubt
  Sort-by-Story, ohne Prefix einfacher zu suchen).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 98: Polish + E2E + Docs + Close" —
  Goal, Depends-on (Phase 97), Requirements (E2E-01, DOCS-02, DOCS-03).
- `.planning/REQUIREMENTS.md` § "E2E-01" / "DOCS-02" / "DOCS-03" — full
  Requirement-Texte inkl. Acceptance Criteria (E2E-01: 8 Stages,
  WireMock-recorded-payloads-Assertions; DOCS-02: 6 lit-Punkte (a)-(f)
  inkl. annotated Screenshots + OAuth-Bitmask `MANAGE_THREADS`;
  DOCS-03: README + Wiki + Sample-Screenshot + Changelog-Link).
- `.planning/milestones/v1.13-ROADMAP.md` § "Phase 98" — 6 Success
  Criteria + Phase Dependency Graph (depends auf Phase 97; Final-Phase
  des Milestones). **Erfolgskriterium 6 ist hard-locked**: Mobile-
  viewport `.card`-Overflow-Fix auf 4 Discord-Seiten ("muss noch
  innerhalb des Meilensteins" User-Direktive 2026-05-21).
- `.planning/PROJECT.md` § "Current Milestone: v1.13" — "zero new
  production dependencies", "outbound-only", "button-triggered"
  Invarianten (alle in v1.13 weiterhin gewahrt).
- `.planning/STATE.md` § "Active Milestone — v1.13" + § "Baselines to
  Preserve" (JaCoCo ≥ 88.88 % als Phase-97-Baseline, CI E2E 17:39 ± 20 %,
  SpotBugs 0, CodeQL exit 0, EXPORT_ORDER und SCHEMA_VERSION unverändert,
  Flyway V1-V13 immutable) + § "Pending UATs" (UAT-07 PASS bestätigt,
  UAT-08 wird in Phase-98-Close hinzugefügt per D-98-E2E-9) +
  § "Deferred Items" (Mobile-`.card`-Overflow als REQ in dieser Phase).
- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` —
  DIE authoritative Design-Reference für v1.13. § 5 Phase Breakdown
  (Phase 98 = Polish + E2E + Close), § 6 Risks (kein neuer Risk in
  Phase 98 — E2E ist Verifikations-Surface). § 4.x Page-Layouts werden
  in Plan 98-01 für die Runbook-§-2.3-Daily-Operations-Sektion
  referenziert.

### Existing Code Touchpoints (Phase-98-Scope-Reuse)

- `src/main/resources/static/admin/css/admin.css` — Plan 98-01 editiert
  `.card` (Line 166), `.form-group` input/select/textarea (Line 309-311),
  `.searchable-dropdown .dropdown-list` (Line 823), und das existing
  `@media (max-width: 640px)` (Line 221).
- `docs/operations/discord-integration.md` — 417-Zeilen existing
  Runbook, Plan 98-01 APPENDED §§ 1.9 / 2.3 / 6 / 7.
- `src/main/java/org/ctc/admin/TestDataService.java` — existierender
  Test-Data-Seeder, Plan 98-02 APPENDED neue Methode
  `seedFullMatchdayLifecycle()`.
- `src/test/java/org/ctc/e2e/discord/posts/`, `e2e/discord/matchday/`,
  `e2e/discord/forum/` — Sibling-Packages zum neuen
  `e2e/discord/lifecycle/` Plan 98-02.
- `src/main/java/org/ctc/discord/DiscordRestClient.java` — Phase-93
  INFRA-01 Client, Plan 98-02 nutzt seinen `discord.api.base-url`
  property-override für `@AutoConfigureWireMock`.
- `src/main/java/org/ctc/discord/DiscordWebhookClient.java` — Phase-93+95
  Webhook-Client, der Plan 98-02 WireMock-Stubs für `?thread_id=`
  Query-Param-Verifikation nutzt.
- `src/main/java/org/ctc/discord/event/DiscordAutoPostListener.java` —
  Phase-95/97 `@TransactionalEventListener AFTER_COMMIT`-Hook für
  Schedule + Match-Preview-Auto-Edit; Plan 98-02 verifiziert den Hook
  im Stage-8-Archive-Workflow (wenn Operator Match-Form ändert).
- `README.md` — existing Wiki-Link bei Line "See the [Wiki](../../wiki)
  for detailed documentation" — Plan 98-03 APPENDED Discord-Integration-
  Bullet darüber.

### Existing Test Patterns (Phase-93-97-Pattern)

- `src/test/java/org/ctc/discord/integration/DiscordWebhookClientMultipartEditIT.java`
  (Phase 95) — Vorlage für `@AutoConfigureWireMock`-Setup + Multipart-
  Body-Verification.
- `src/test/java/org/ctc/discord/service/DiscordPostServiceIT.java`
  (Phase 95) — Vorlage für Spring-Context + WireMock-Property-Override.
- `src/test/java/org/ctc/discord/event/MatchServiceScheduleEditHookIT.java`
  (Phase 95) — Pattern für `@TransactionalEventListener AFTER_COMMIT`-
  Verification.
- `src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java`
  (Phase 95) — Vorlage für Playwright + Spring-Context E2E.
- `src/test/java/org/ctc/admin/TestHelper.java` — Existing
  `createFullSeasonFixture()`-Pattern für Test-Daten-Setup.

### Phase 92-97 Hand-off (Carry-Forward-Pattern)

- `.planning/phases/97-matchday-level-posts/97-CONTEXT.md` § D-97-PREV-1
  (`@TransactionalEventListener AFTER_COMMIT`-Pattern) — Plan 98-02
  E2E muss diesen Async-Path im Test-Setup berücksichtigen
  (`@Transactional(propagation = REQUIRES_NEW)` + `await()` wenn nötig).
- `.planning/phases/96-provisional-graphic-forum-threads/96-CONTEXT.md`
  § D-96-FOR-3a (Method-Overload für `?thread_id=` Webhook-Posts) —
  Plan 98-02 WireMock-Stubs müssen die Query-Param-Variante stubben.
- `.planning/phases/95-match-channel-posts/95-CONTEXT.md` § D-95-04
  (Schedule-Auto-Edit) — Plan 98-02 verifiziert dass der Hook bei
  Match-Form-Save im Stage-8 läuft (assert nur 1 PATCH).
- `.planning/phases/94-team-roles-match-channel-lifecycle/94-CONTEXT.md`
  § D-94-04 (Permission-Audit + Channel-Delete-on-Fail) — Plan 98-02
  Stage-1 (create-channel) muss diesen Audit-Flow durchspielen.
- `.planning/phases/93-discord-foundation/93-CONTEXT.md` § D-93-01
  (UAT-03 Pattern) — Template für UAT-08 Procedure in Runbook § 7.

### Re-Open Refs (2026-05-25 — Plans 98-04/05/06/07)

- `.planning/phases/98-polish-e2e-docs-close/98-04-SUMMARY.md` — Plan 98-04
  Schedule-Embed Layout-Polish (`inline:false`). Operator-Decision 2026-05-25
  to bundle live re-post + full verify with 98-05/06.
- `.planning/STATE.md` § Deferred Items "Schedule-Embed Layout asymmetry"
  + § Pending UATs / UAT-08 PASS — operator screenshots context for the
  in-milestone re-scope.
- `src/main/java/org/ctc/discord/model/DiscordPostType.java` — enum
  `MATCHDAY_PAIRINGS` (Line 11, currently unused) + new value
  `MATCHDAY_SCHEDULE` (added by Plan 98-06).
- `src/main/java/org/ctc/admin/service/MatchdayOverviewGraphicService.java`
  — Pattern-Vorlage für die neuen `MatchdayPairingsGraphicService` +
  `MatchdayScheduleGraphicService` (Playwright-rendered Matchday-Level
  PNGs).
- `src/main/java/org/ctc/discord/service/DiscordPostService.java`
  § canPostMatchPreview (Lines 227-245) + postMatchPreview (Lines 248-267)
  + buildMatchPreviewMarkdown (Lines 297-317) — Hybrid-Post Pattern für
  Plan 98-05 Pairings (Markdown + Multipart).
- `src/main/java/org/ctc/discord/service/DiscordPostService.java`
  § canPostMatchdayResults (Lines 319-332) + postMatchdayResults
  (Lines 349-373) — Pure-PNG-Post Pattern für Plan 98-06 Schedule.
- `src/main/resources/db/migration/V14__*.sql` — Phase-97 STANDINGS
  Phase-FK-Migration als Vorlage für Plan 98-05's V15 (H2 + MariaDB
  syntax compatibility).
- User-Screenshots 2026-05-25 — 2 Operator-Discord-Screenshots
  (MATCHDAY_PAIRINGS Hybrid mit Pairings-Grafik + Deadline-Bullet +
  `Game On! :CTC:` Line | MATCHDAY_SCHEDULE pure PNG mit 7 Match-Zeilen
  + BST-Timezone-Format + Team-Records `W-L`). Layout-Authoritative
  für die Grafik-Templates.

### Convention References

- `CLAUDE.md` § Architectural Principles — Keep Controllers Thin, DTOs
  instead of Entities, No Comment Pollution, No Inline Styles.
- `CLAUDE.md` § Conventions / No Comment Pollution — gilt für neue
  E2E-Test-Klasse + alle Runbook-MD-Sektionen (kein Phase-/Plan-Marker
  in Code).
- `CLAUDE.md` § Build & Test Discipline / WireMock is not Real-API
  Coverage — gilt für Plan-98-02-Assertions: `withQueryParam(...)`,
  nicht nur `urlPathEqualTo`; recorded-payloads-Snapshot ist nicht
  Pflicht, aber Body-Parts-Verifikation ist.
- `CLAUDE.md` § Subagent Rules — keine Subagents in Phase 98 (inline-
  sequential per [[feedback-inline-sequential-execution]]).
- `CLAUDE.md` § GSD Workflow Discipline / Wave-Pause for User Feedback —
  3 Wave-Pauses (nach Plan 98-01, 98-02, 98-03).
- `CLAUDE.md` § Memory-Aware Subagent Dispatch — wenn überhaupt Subagents
  (außer Forschungsagents), dann hand-carried Memory-Konventionen.
- `CLAUDE.md` § Conventions / Documentation Maintenance — Three-place
  rule: CLAUDE.md / README.md / Wiki. Plan 98-03 deckt README + Wiki ab;
  CLAUDE.md ist bereits über die Phase 92-97 inkrementell aktualisiert
  worden.
- `.planning/codebase/TESTING.md` § Test Categorization (`@Tag`) —
  `@Tag("e2e")` für `DiscordFullMatchdayLifecycleE2ETest`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **WireMock-Setup (Phase 93-97 actual pattern, see 98-PATTERNS.md):**
  `@RegisterExtension static WireMockExtension wm =
  WireMockExtension.newInstance().options(WireMockConfiguration.options()
  .dynamicPort()).build();` + `@DynamicPropertySource static void
  overrideBaseUrl(DynamicPropertyRegistry r) { r.add("app.discord.base-url",
  () -> wm.baseUrl() + "/api/v10"); }`. Plan 98-02 spiegelt 1:1 dieses
  Pattern, das alle 14 Discord-ITs in `src/test/java/org/ctc/discord/`
  nutzen. **DEPRECATED guidance:** D-98-E2E-2 erwähnte ursprünglich
  `@AutoConfigureWireMock(port = 0)` + Property `discord.api.base-url` —
  diese Form existiert NICHT im Codebase und ist durch PATTERNS.md +
  98-02-PLAN.md `<key_constraints>` überschrieben.
- **`TestDataService` Methoden-Bibliothek** (alle Phasen): bestehende
  Test-Prefix-Daten-Konvention (`T-`/`Test-`). Plan 98-02 erweitert um
  `seedFullMatchdayLifecycle()` — nutzt existierende `Team`-/`Driver`-/
  `Season`-/`Match`-Konstruktoren + JPA-Repositories.
- **Playwright `PlaywrightConfig` + `BasePage`-Pattern** (alle E2E):
  Plan 98-02 nutzt das existing Setup für Browser-Lifecycle + Page-
  Construction.
- **Existing CSS `@media (max-width: 640px)`** (Phase 94 `.discord-actions`
  flex-wrap): Plan 98-01 nutzt den selben Breakpoint für die `.card`-
  Padding-Reduktion auf 16 px.
- **`DiscordAutoPostListener` Async-Hook** (Phase 95/97): Plan 98-02
  Stage 8 verifiziert dass der Listener bei Match-Form-Save den
  Schedule-Auto-Edit korrekt feuert.
- **`docs/operations/`-Hierarchie**: 4 existing Runbook-Files
  (`discord-integration.md`, `google-integration.md`, `import-runbook.md`,
  `release-runbook.md`). Plan 98-01 erweitert `discord-integration.md`
  inkrementell.

### Established Patterns

- **Inline-Sequential auf Milestone-Branch** (Phase 92-97 D-9*-05): Phase
  98 erbt unverändert. Keine Worktrees, keine Subagents.
- **Wave-Pause nach jedem Plan** (Phase 92-97 D-9*-08): 3 Wave-Pauses in
  Phase 98 (nach 98-01, 98-02, 98-03).
- **Rolling Milestone-PR via `gh pr edit --body-file`** (Phase 92-97
  D-9*-06): Plan 98-03 final-update der PR-Body (Phase-Tracker komplett,
  Coverage final, Test-Count final, alle 25 REQ-IDs ✓, Squash-Subject-
  Erinnerung).
- **Per-Plan Nyquist VALIDATION.md** (Phase 89+): 3 Plans, 3
  VALIDATION.md.
- **`@Tag`-Convention** (CLAUDE.md): konsistent angewandt.
- **`feedback_in_milestone_polish`** (CLAUDE.md): Easy-Wins werden im
  selben Plan adressiert, größere Findings in DEFERRED.
- **Append-only auf shared Files** (CLAUDE.md
  [[feedback-worktree-file-clobber]]): `admin.css`,
  `discord-integration.md`, `TestDataService.java` werden APPENDED, NICHT
  rewritten.

### Integration Points

- **`admin.css .card` ← Mobile-Polish-Sweep (Plan 98-01)**: Globaler Fix
  wirkt auf alle admin-Pages.
- **`DiscordRestClient ← @AutoConfigureWireMock` (Plan 98-02)**: WireMock-
  Server stubst alle Discord-API-Calls.
- **`DiscordWebhookClient ← WireMock-Multipart-Stubs` (Plan 98-02)**:
  Body-Parts + `?thread_id=` Query-Param-Verifikation.
- **`MatchService.save ← DiscordAutoPostListener AFTER_COMMIT` (Plan
  98-02 Stage 8)**: Async-Hook-Verification.
- **`TestDataService.seedFullMatchdayLifecycle() → DiscordFullMatchdayLifecycleE2ETest`
  (Plan 98-02)**: Daten-Setup für 8 Stages.
- **`README.md ← Discord-Integration-Bullet → ../../wiki/Discord-Integration` (Plan 98-03)**:
  README verlinkt Wiki-Page.
- **`ctc-manager.wiki.git ← Plan 98-03 Wiki-Update`**: Cloned + edited +
  pushed via `.wiki-clone/`.

</code_context>

<specifics>
## Specific Ideas

- **User-Direktive 2026-05-24 (Screenshot-Strategie)**: "alles kann
  automatisiert werden, ohne dass tatsächliche Screenshots bereitgestellt
  werden müssen. Das reicht für den Beginn völlig aus" → App-UI per
  playwright-cli, Discord-Portal-Steps textuell. Keine Manual-Screenshot-
  Pflicht für Plan 98-01.
- **User-Direktive 2026-05-24 (Pre-Merge-Bookkeeping)**: "Wir dürfen
  nicht direkt auf master pushen, daher muss alles im PR mit drin sein"
  → MILESTONES.md + REQUIREMENTS.md-Flips + PR-Body sind ALLE in Plan
  98-03 Pre-Merge committet. Korrigiert frühere Annahme dass
  REQUIREMENTS.md-Flips post-merge geschehen.
- **User-Direktive 2026-05-24 (`/gsd-complete-milestone`-Timing)**: "ich
  mache das manuell, nachdem ich einen kompletten Test durchgeführt
  habe. Der GSD complete milestone Task ist in meinem Workflow immer
  der manuelle Abschluss vor dem Merge" → `/gsd-complete-milestone` ist
  User-Manual-Step nach Phase-98-Close + UAT-08 + VOR Squash-Merge.
- **Mobile-Polish-Pflichtseiten** (ROADMAP-Krit 6, User-Direktive
  2026-05-21): 4 Discord-touching Pages MÜSSEN den Fix sehen, das ist
  Hard-Gate für Phase 98.
- **UAT-08 Pattern** spiegelt UAT-03 (Phase 93) und UAT-04..07 (Phase
  94..97) — Live-Discord-Test gegen Operator-Test-Guild, dokumentiert
  in Runbook + STATE.md.

- **User-Direktive 2026-05-25 (Channel-Differenzierung)**: "Nicht wieder
  Match Day und Announcement Kanal durcheinander würfeln." Die neuen
  matchday-level Posts (MATCHDAY_PAIRINGS + MATCHDAY_SCHEDULE) leben auf
  dem Announcement-Channel (analog MATCH_PREVIEW), NICHT auf den
  per-Match-Channels. Siehe Memory `feedback_discord_channel_types`.

- **User-Direktive 2026-05-25 (Re-Open Scope)**: MATCHDAY_PAIRINGS +
  MATCHDAY_SCHEDULE werden trotz D-97-PREV-2 "deferred to v1.14" jetzt
  in v1.13 mitgenommen — operator-screenshots zeigen den bestehenden
  manuellen Workflow, der via 2 neue Buttons automatisiert wird.
  In-milestone-polish-discovery (per CLAUDE.md "GSD Workflow Discipline")
  rechtfertigt den Scope-Bump.

- **User-Direktive 2026-05-25 (Post-Output-Form)**: MATCHDAY_PAIRINGS =
  Hybrid (Markdown body mit Deadline + Weekend + `Game On! :CTC:` +
  1 Pairings-Grafik-PNG). MATCHDAY_SCHEDULE = pure PNG (kein Markdown,
  kein Embed). Beide auf Announcement-Channel.

- **User-Direktive 2026-05-25 (Auto-Edit verworfen)**: Beide neuen Posts
  sind operator-driven Re-Post-Click (analog POST-07a/b/STANDINGS).
  KEIN AFTER_COMMIT-Listener, KEIN automatischer PATCH. Stale-Detection
  Label-Flip "Re-Post" → "Update" bleibt.

</specifics>

<deferred>
## Deferred Ideas

> **Re-Open-Update 2026-05-25:** `MATCHDAY_PAIRINGS` + `MATCHDAY_SCHEDULE`
> sind aus Deferred entfernt und in Plans 98-05/06 (POST-09/POST-10)
> aktive Phase-98-Scope-Items. D-97-PREV-2 bleibt historisch immutabel,
> ist aber inhaltlich durch Q-98-07 D-98-REQ-1 überschrieben.

- **Touch-Target-44 px-Regel auf `.btn`** (Mobile-Polish-Erweiterung):
  iOS HIG empfiehlt 44 px, existing `.btn { min-height: 36px }` ist
  darunter. Planner-Discretion ob Phase 98 das mitnimmt (Easy-Win) oder
  DISC-FUTURE.
- **429-Rate-Limit-Path im Mega-Walkthrough**: DEFERRED → DISC-FUTURE
  Ticket falls Operator-Use-Case-Druck. Aktuell ausreichend abgedeckt
  durch Phase-93 `DiscordRateLimitInterceptorIT`.
- **`Discord-Setup-Walkthrough.md` Wiki-Subseite mit annotated
  Discord-Portal-Screenshots**: → v1.14 oder ad-hoc wenn Operator
  manuell Portal-Screenshots ergänzen möchte. Aktuell textuelle
  Beschreibung ausreichend.
- **Race.dateTime-Trigger für SCHEDULE-Auto-Edit + MATCH_PREVIEW-Auto-
  Edit** (Phase 95 D-95-04a + Phase 97 D-97-PREV-1a): DEFERRED →
  v1.14 DISC-FUTURE. Phase 98 verifiziert nicht diesen Pfad.
- **K-von-N Settings/Lineups-Posting** (Phase 95 deferred): bleibt
  v1.14-Wunsch wenn Operator-Feedback Druck zeigt.
- **`/admin/discord/posts` Bulk-Re-Post-Button** (Phase 95 deferred):
  bleibt v1.14-Wunsch.
- **Multi-Server / Multi-Guild Support**: explicit-out-of-scope für
  v1.13 + alle künftigen Milestones bis Deployment-Modell wechselt
  (`.planning/REQUIREMENTS.md § Out of Scope`).
- **Inbound Discord Interactions** (Slash-Commands, Polls,
  Reaction-Reads): explicit-out-of-scope (`.planning/REQUIREMENTS.md
  § Out of Scope`).

</deferred>

---

*Phase: 98-polish-e2e-docs-close*
*Context gathered: 2026-05-24*
