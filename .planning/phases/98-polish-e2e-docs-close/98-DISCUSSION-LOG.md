# Phase 98: Polish + E2E + Docs + Close - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-24
**Phase:** 98-polish-e2e-docs-close
**Areas discussed:** E2E test architecture, Mobile `.card` overflow, DOCS-02 runbook strategy, Plan decomposition + milestone close

---

## Area 1: E2E Test Architecture

### Q-98-01.1 — Test Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Ein Mega-Walkthrough (8 Stages, 1 Test) | `DiscordFullMatchdayLifecycleE2ETest` mit einer einzigen `@Test`-Methode | ✓ |
| Suite aus 8 fokussierten Tests + Smoke-Aggregator | Ein Test pro Stage + zusätzlicher Smoke-Aggregator | |
| Hybrid — 1 Mega-Test + 8 dedizierte Stage-Step-Methoden | Eine Test-Klasse mit `@Test` + 8 `private` Step-Methoden | |

**User's choice:** Mega-Walkthrough (8 Stages, 1 Test) — entspricht ROADMAP-Wortlaut "one suite".
**Notes:** Hybrid mit 8 Step-Methoden als Planner-Discretion offen.

### Q-98-01.2 — WireMock Integration

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Boot `@AutoConfigureWireMock` auf Test-Klasse | Dynamischer Port + property-override für `discord.api.base-url` | ✓ |
| Dedicated `WireMockServer` mit `@BeforeAll` Lifecycle | Manueller `WireMockServer.start()` | |
| Shared `DiscordWireMockExtension` JUnit 5 Extension | Extension mit vorinstallierten Stubs | |

**User's choice:** Spring Boot `@AutoConfigureWireMock(port = 0)`.
**Notes:** Identisches Pattern zu existierenden Phase-95-97 ITs.

### Q-98-01.3 — DB Seed Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| `TestDataService.seedFullMatchdayLifecycle()` neue Methode | Test-Prefix Java-Setup | ✓ |
| Inline Playwright-Click-Flow (komplettes Setup via UI) | E2E auf Setup-Surface | |
| Flyway Test-Migration `R__discord_e2e_seed.sql` | Repeatable Migration | |

**User's choice:** `TestDataService.seedFullMatchdayLifecycle()`.
**Notes:** Spiegelt `TestHelper.createFullSeasonFixture()`-Pattern aus Controller-Tests.

### Q-98-01.4 — Payload Assertions

| Option | Description | Selected |
|--------|-------------|----------|
| Per-Stage `verify()` mit `RequestPatternBuilder` | URL + body parts + headers per stage | ✓ |
| Recorded-Payload-Snapshot-Files in `src/test/resources/wiremock/` | Snapshot-File-Diff | |
| Hybrid — `verify()` für URL/Header + Snapshot für Body | Doppelte Maintenance-Surface | |

**User's choice:** Per-Stage `verify()` mit `RequestPatternBuilder`.
**Notes:** `withQueryParam(...)` für `?thread_id=` per [[feedback-wiremock-vs-real-api]].

### Q-98-01.5 — Rate-Limit Headers

| Option | Description | Selected |
|--------|-------------|----------|
| Standard-200-Stubs ohne Rate-Limit-Trigger | Headers werden gelesen, keine Wartezyklen | ✓ |
| Mid-Run 429 + Retry-After Stage einbauen | 429-Path im Walkthrough | |
| Beide — separate `@Test`-Methode für 429-Path | 2 Tests | |

**User's choice:** Standard-200-Stubs.
**Notes:** 429-Edge-Case bereits in Phase-93 `DiscordRateLimitInterceptorIT` abgedeckt.

### Q-98-01.6 — Snowflake IDs

| Option | Description | Selected |
|--------|-------------|----------|
| Hardcoded deterministische IDs pro Stage | `900000000000000001L` + offset | ✓ |
| ULID-ähnliche generierte Test-IDs | WireMock-Templating per-request | |
| Mix — Channel/Webhook hardcoded, Message counter-basiert | Hybrid | |

**User's choice:** Hardcoded deterministische IDs.

### Q-98-01.7 — Browser State

| Option | Description | Selected |
|--------|-------------|----------|
| Eine Page durch alle 8 Stages | `browser.newPage()` einmal | ✓ |
| Neue Page pro Stage, gleicher BrowserContext | `context.newPage()` zwischen Stages | |
| Komplett-Reset zwischen Stages | Neuer Browser pro Stage | |

**User's choice:** Eine Page durch alle 8 Stages.

### Q-98-01.8 — Test Package

| Option | Description | Selected |
|--------|-------------|----------|
| `src/test/java/org/ctc/e2e/discord/lifecycle/` | Neuer Sub-Package | ✓ |
| Direkt in `src/test/java/org/ctc/e2e/discord/` | Bestehender top-level Discord-E2E-Package | |
| `src/test/java/org/ctc/e2e/v113/` | Milestone-spezifischer Package | |

**User's choice:** `e2e/discord/lifecycle/`.

### Q-98-01.9 — UAT-08 Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| WireMock-IT-only Phase-Close + UAT-08 staged in STATE.md | Pattern aus Phase 95/96/97 | ✓ |
| Live-UAT-08 als Phase-98-Close-Bedingung | Blockt PR-Merge bis Operator live testet | |
| Live-UAT-08 als optionales Post-Merge-Smoke | Lockerer als 95/96/97 | |

**User's choice:** WireMock-IT-only Close + UAT-08 staged. Modifiziert später durch
Q-98-04.3-Antwort: UAT-08 ist Pflicht vor `/gsd-complete-milestone`, das wiederum
manuell vor dem Merge läuft → UAT-08 ist effektiv Pre-Merge-Pflicht.

### Q-98-01.10 — Multipart Body Assertions

| Option | Description | Selected |
|--------|-------------|----------|
| Filename + Content-Type + Body-Size-Bounds | Pragmatisch ohne Pixel-Brittleness | ✓ |
| Nur Filename + Content-Type | Body-Size-Check weglassen | |
| Strikte byte[]-Equals gegen Snapshot | Snapshot-Maintenance-Burden | |

**User's choice:** Filename + Content-Type + Body-Size > 1024 bytes.

### Q-98-01.11 — Stub Helper

| Option | Description | Selected |
|--------|-------------|----------|
| Ja — neue Helper-Klasse `WireMockDiscordStubs` | Test-Code-Reuse | ✓ |
| Nein — alle Stubs inline | ~250 Zeilen Test-Klasse | |
| Helper im Mega-Test-Package | Eingeschränkter Reuse | |

**User's choice:** Helper-Klasse extrahieren.

### Q-98-01.12 — Helper Package

| Option | Description | Selected |
|--------|-------------|----------|
| `src/test/java/org/ctc/discord/wiremock/` | Sibling zu Production-discord-Code | ✓ |
| `src/test/java/org/ctc/e2e/discord/lifecycle/` | Co-located mit Mega-Test | |
| `src/test/java/org/ctc/test/fixtures/` | Generisches Fixtures-Package (neu) | |

**User's choice:** `src/test/java/org/ctc/discord/wiremock/`.

### Q-98-01.13 — Test Tag

| Option | Description | Selected |
|--------|-------------|----------|
| Nur `@Tag("e2e")` (Standard) | Konsistent mit allen anderen E2E-Tests | ✓ |
| Zusätzliches `@Tag("discord-lifecycle")` | Neue Tag-Konvention | |
| `@Tag("e2e")` + `@Tag("slow")` für CI-Budgeting | Etabliert "slow"-Konvention | |

**User's choice:** `@Tag("e2e")` only.

---

## Area 2: Mobile `.card` Overflow Fix

### Q-98-02.1 — Fix Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Global Sweep auf `.card` + `.form-group` + `.searchable-dropdown` | Eine zentrale Korrektur | ✓ |
| Targeted Variante `.card--compact-mobile` nur auf Discord-Seiten | Minimaler Blast-Radius | |
| Hybrid — globale `.card` Min-Fix + `.card--discord` schärfer | Doppelte Surface | |

**User's choice:** Global Sweep.
**Notes:** Vermeidet späteren Drift auf nicht-Discord-Pages.

### Q-98-02.2 — Mobile Padding

| Option | Description | Selected |
|--------|-------------|----------|
| Mobile-Padding 16 px im `@media (max-width: 640px)` | Existing MQ erweitern | ✓ |
| Padding bleibt 24 px überall | Minimal-invasive Variante | |
| Padding global 16 px | Auch Desktop bekommt 16 px | |

**User's choice:** Mobile-Padding 16 px im existing 640 px MQ (Line 221).

### Q-98-02.3 — Verification Pages

| Option | Description | Selected |
|--------|-------------|----------|
| 4 Krit-6-Pages + 4-5 Stichproben | Discord-Pflicht + Regression-Detection | ✓ |
| Nur die 4 Krit-6-Pages | Minimaler Verifikations-Scope | |
| Alle 25-30 Top-Level Admin-Pages | Maximalverifikation | |

**User's choice:** 4 Pflicht + 4-5 Stichproben (9 total × Desktop+Mobile = 18 Screenshots).

### Q-98-02.4 — Discovery Policy

| Option | Description | Selected |
|--------|-------------|----------|
| Easy non-Discord-Wins (<30 min) im selben Plan | Per `feedback_in_milestone_polish` | ✓ |
| Strikt nur die 4 Discord-Seiten | Findings → v1.14 DEFERRED | |
| Wave-Pause pro Fund | User-Decision per Fund | |

**User's choice:** Easy wins im selben Plan, größere Findings DEFERRED.

---

## Area 3: DOCS-02 Runbook Strategy

### Q-98-03.1 — Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Inkrementelle Ergänzung der bestehenden Struktur | 417 Zeilen bleiben, neue Sektionen APPENDED | ✓ |
| Restruktur in 7-Sektionen-Layout | Neue Top-Level-Sektionen | |
| Hybrid — § 1-5 bleiben + neuer "Operations" davor | Spaltet Reader-Pfade | |

**User's choice:** Inkrementelle Ergänzung.

### Q-98-03.2 — Screenshot Location

| Option | Description | Selected |
|--------|-------------|----------|
| `docs/operations/images/discord/` committed PNGs | Permanent committed | |
| GitHub Wiki-Hosted Screenshots, Runbook verlinkt | Wiki als single source | ✓ (initial) |
| External (Imgur, S3) URLs | Drittanbieter | |

**User's choice (initial):** GitHub Wiki-Hosted.
**Notes:** Bedingt revidiert durch Q-98-03.3 — User wechselt zu "alles kann automatisiert werden".

### Q-98-03.3 — Wiki Reference Style

| Option | Description | Selected |
|--------|-------------|----------|
| Wiki-Pages als Subseiten, Runbook textuell verlinkt | Repo bleibt klein | ✓ |
| Embedded `![alt](wiki-image-url.png)` | Direkt-Embed via Wiki-URL | |
| Beide | Doppelte Maintenance | |

**User's choice:** Wiki-Pages als Subseiten + textuelle Links.

### Q-98-03.4 — Author Strategy (free-text)

**User's free-text response:** "Nur App UI Screenshots und die Discord Developer und
Server/App Einstellungen textuell beschreiben. Damit kann alles meiner Meinung nach
automatisiert werden, ohne dass tatsächliche Screenshots bereitgestellt werden müssen.
Das reicht für den Beginn völlig aus."

**Resolution:** App-UI-Screenshots werden playwright-cli automatisiert generiert,
Discord-Developer-Portal + Discord-Server-Interaktionen werden textuell als
nummerierte Schritte dokumentiert. Keine Discord-Portal-Screenshots in v1.13.

### Q-98-03.5 — Screenshot Committed Path

| Option | Description | Selected |
|--------|-------------|----------|
| `docs/operations/images/discord/` committed | Konsistent mit `docs/operations/`-Hierarchie | ✓ |
| Direkt in GitHub Wiki hochgeladen | Bricht "alles automatisierbar" | |
| `src/main/resources/static/admin/img/runbook/` | App-Bundle-Bloat | |

**User's choice:** `docs/operations/images/discord/`.

### Q-98-03.6 — New Sections

**Selected (multiSelect, alle 4):**
- ✓ § 1.9 Forum-Channel + Thread Setup
- ✓ § 2.3 Daily Operations (Phase 94-97 Workflows)
- ✓ § 6 Token-Rotation Procedure
- ✓ § 7 UAT-08 + Extended Troubleshooting

### Q-98-03.7 — Audience

| Option | Description | Selected |
|--------|-------------|----------|
| Single-Audience (Operator) durchgängig | Existing Stil unverändert | ✓ |
| Top-Section "Quick-Start" + Tiefer-Setup separat | Zwei Lese-Pfade | |
| Zwei separate Dateien | Bricht ein-Datei-Konvention | |

**User's choice:** Single-Audience durchgängig.

---

## Area 4: Plan Decomposition + Milestone Close

### Q-98-04.1 — Plan Count

| Option | Description | Selected |
|--------|-------------|----------|
| 3 Plans — E2E / Polish+Runbook / README+Wiki+Close | Standard-Reihenfolge | ✓ |
| 4 Plans — E2E / Mobile / Runbook / README+Wiki+Close | Mehr Wave-Pause | |
| 5 Plans — E2E / Mobile / Runbook / DOCS-03 / Close | Dedizierter Close-Plan | |

**User's choice:** 3 Plans.

### Q-98-04.2 — Plan Order

| Option | Description | Selected |
|--------|-------------|----------|
| Mobile-Polish ZUERST, dann E2E, dann DOCS-03+Close | Plan 98-01 = Polish+Runbook | ✓ |
| E2E ZUERST, dann Polish+Runbook, dann DOCS-03+Close | E2E gegen pre-polished State | |
| Polish+Runbook+E2E parallel als Plan 98-01 | Bricht Atomic Plans | |

**User's choice:** Mobile-Polish zuerst (Plan 98-01 Polish+Runbook), E2E zweitens
(Plan 98-02), DOCS-03+Close drittens (Plan 98-03).

### Q-98-04.3 — Complete-Milestone Timing (free-text)

**User's free-text response:** "ich mache das manuell, nachdem ich einen kompletten
Test durchgeführt habe. Der GSD complete milestone Task ist in meinem Workflow immer
der manuelle Abschluss vor dem Merge"

**Resolution:** `/gsd-complete-milestone` läuft MANUELL durch den User nach
Phase-98-Close + UAT-08-PASS, VOR dem Squash-Merge. Plan 98-03 enthält die
bookkeeping-Tasks (MILESTONES.md, REQUIREMENTS.md, PR-Body), aber NICHT den
`/gsd-complete-milestone`-Call selbst.

### Q-98-04.4 — UAT-08 Staging

| Option | Description | Selected |
|--------|-------------|----------|
| STATE.md § Pending UATs als "Phase-98-Close-Bedingung" | Pflicht vor `/gsd-complete-milestone` | ✓ |
| STATE.md als "post-merge operator action" (wie UAT-02) | Erst nach Merge | |
| Separate Phase 99 "Post-merge live validation" | Neue Phase | |

**User's choice:** Phase-98-Close-Bedingung.

### Q-98-04.5 — REQ-ID Mapping

| Option | Description | Selected |
|--------|-------------|----------|
| 98-01 Polish+Runbook (DOCS-02 + Krit-6) · 98-02 E2E · 98-03 DOCS-03+Close | Klares 1:1 Mapping | ✓ |
| 98-01 Mobile-Polish · 98-02 E2E+Runbook · 98-03 DOCS-03+Close | Polish separater Plan | |
| 98-01 Runbook+Polish · 98-02 E2E · 98-03 DOCS-03+Close+Wiki | Äquivalent | |

**User's choice:** 98-01 Polish+Runbook (DOCS-02 + Krit-6) · 98-02 E2E (E2E-01) ·
98-03 DOCS-03+Close (DOCS-03 + MILESTONES.md).

### Q-98-04.6 — Additional Gray Area Selected

**User's choice:** "Coverage-Impact + Test-Count-Schätzung".

### Q-98-04.6.1 — Coverage Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Erwartung: minimaler Effekt, Baseline 88.88% bleibt | Mega-Test triggert gecoverte Pfade | ✓ |
| Coverage-Threshold temporär auf 88.0% absenken | Backwards-Compat-Bruch | |
| JaCoCo-Exclusion-Liste um WireMockDiscordStubs erweitern | Redundant (Test-Code zählt nicht) | |

**User's choice:** Minimaler Effekt erwartet, Baseline 88.88% bleibt.

### Q-98-04.6.2 — Test Count

| Option | Description | Selected |
|--------|-------------|----------|
| 98-01 ~3-5 Tests · 98-02 1 Test · 98-03 0 Tests | Mega-Test in 98-02, Doc-Helper-Tests in 98-01 | ✓ |
| 98-01 0 Tests · 98-02 5+ Tests · 98-03 0 Tests | Splittet Mega-Test in 8 Methoden | |
| Keine neuen Tests, alles Doku + UI-only | Bricht E2E-01-REQ | |

**User's choice:** ~3-5 / 1 / 0.

### Q-98-04.7 — Next Gray Area Selected

**User's choice:** "Wiki-Update-Mechanik klarstellen".

### Q-98-04.7.1 — Wiki Update Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Plan committet zu `<repo>.wiki.git` lokal clone + push | Automatisierbar | ✓ |
| Plan schreibt Stubs in `docs/wiki-staged/` | Manual Übertragung | |
| User aktualisiert Wiki vollständig manuell | Operator-Friction max | |

**User's choice:** Lokaler Wiki-Clone + push aus Plan 98-03.

### Q-98-04.7.2 — Wiki Pages

**Selected (multiSelect):**
- ✓ Neue Page `Discord-Integration.md`
- ✓ `Home.md` Sidebar-Update mit Discord-Integration-Link
- ✓ Update `README.md` im Wiki (falls existiert)
- (deferred) Optional `Discord-Setup-Walkthrough.md` (später)

### Q-98-04.7.3 — Canonical Paragraph

| Option | Description | Selected |
|--------|-------------|----------|
| Kurz-Beschreibung + Use-Cases + Setup-Link | ~5-8 Sätze | ✓ |
| Detaillierte Feature-Liste mit allen 11 Post-Types | Stale-prone | |
| Marketing-Ansatz "Why Discord matters" | Selling-Ton | |

**User's choice:** Kurz-Beschreibung mit Use-Cases + Setup-Link + 1 App-UI-Screenshot.

### Q-98-04.8 — MILESTONES.md + REQUIREMENTS.md Flip Timing (free-text)

**User's free-text response:** "Wir dürfen nicht direkt auf master pushen, daher
muss alles im PR mit drin sein. Ich weiß nicht, woher du die abweichenden Infos
her hast. Habe ich soweit ich mich erinnern kann nie so vorgegeben und wurde
evtl. fälscherweise von dir so interpretiert"

**Resolution:** ALLE bookkeeping-Updates (MILESTONES.md v1.13-Entry +
REQUIREMENTS.md-Resolved-Flips für alle 25 v1.13-REQ-IDs + PR-Body-Final-Update)
laufen in Plan 98-03 Pre-Merge. Korrigiert Memory `feedback_pr_description_update`
falls dieses "post-merge bookkeeping" suggerierte.

---

## Claude's Discretion

- Mega-Test interne Struktur (eine `@Test` mit 8 Step-Methoden oder inline-prozedural).
- `WireMockDiscordStubs` Method-Signaturen (Static-Methods vs Builder-Pattern).
- Mobile-Polish optional zusätzliche Touch-Target-44px-Regel auf `.btn`.
- Wiki-Page-Naming (`Discord-Integration.md` vs alternative).
- Runbook-Section-Numbering (§ 1.9 vs § 1.10, § 6 vs § 5.x).
- README-Bullet-Platzierung (unter "Admin Features" vs eigene Sektion).
- Test-Datei-Bildpfade Prefix-Numbering.

---

## Deferred Ideas

- Touch-Target-44px Mobile-Polish-Erweiterung (Phase 98 Easy-Win-Discretion oder v1.14).
- 429-Rate-Limit-Path im Mega-Walkthrough (Phase-93 IT deckt das ab).
- `Discord-Setup-Walkthrough.md` Wiki-Subseite mit annotated Portal-Screenshots (v1.14 oder ad-hoc).
- Race.dateTime-Trigger für SCHEDULE/MATCH_PREVIEW Auto-Edit (Phase 95/97 deferred → v1.14).
- K-von-N Settings/Lineups-Posting (Phase 95 deferred → v1.14 wenn Operator-Druck).
- `/admin/discord/posts` Bulk-Re-Post-Button (Phase 95 deferred → v1.14).
- Multi-Server/Multi-Guild Support (explicit out of scope, alle v1.x).
- Inbound Discord Interactions / Slash-Commands / Polls / Reaction-Reads (explicit out of scope).
