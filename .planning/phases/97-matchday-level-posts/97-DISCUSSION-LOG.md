# Phase 97: Matchday-Level Posts - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-23
**Phase:** 97-matchday-level-posts
**Areas discussed:** Auto-Edit-Hook MATCH_PREVIEW, POST-06 Batch/Single Surface, POST-07 Two-Post Atomicity, POST-08 Stale-Detection, Saison-Phasen-Awareness (Regular/Playoff/Placement), StandingsGraphic Layout + Multi-Group-Rendering + Schema

---

## Area 1 — MATCH_PREVIEW Auto-Edit-Hook (POST-06b)

### Q-1.1 — Event-Listener Pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror Phase 95 D-95-04 verbatim | New MatchPreviewFieldsChangedEvent + AFTER_COMMIT listener + REQUIRES_NEW + DiscordPostService.autoEditMatchPreviewIfNeeded | ✓ |
| Generalise MatchScheduleFieldsChangedEvent → MatchDiscordFieldsChangedEvent | One event, internal field-set dispatch; refactor blast-radius on Phase 95 tests | |
| Inline call without event (synchronous) | MatchService.save → DiscordPostService directly in finally-block; tight coupling, REJECTED by Phase 95 D-95-04 | |

**User's choice:** Mirror Phase 95 D-95-04 verbatim (Recommended).
**Notes:** Locked as D-97-PREV-1. Saubere Konsistenz mit existing SCHEDULE-Auto-Edit-Pattern.

### Q-1.2 — Trigger Fields + Re-PATCH Granularity

| Option | Description | Selected |
|--------|-------------|----------|
| streamLink + discordTeaser only, per-Match 1 PATCH | Race.dateTime triggert NICHT (analog D-95-04a); RaceService bleibt Discord-frei | ✓ |
| streamLink + discordTeaser + Race.dateTime + match.scheduled_at | Race-Date-Verschiebung triggert MATCH_PREVIEW Re-Render; breite Coupling-Surface | |
| streamLink + discordTeaser + matchday-level Date | Matchday-Date-Änderung patcht ALLE MATCH_PREVIEW Rows; Rate-Limit-Risiko | |

**User's choice:** streamLink + discordTeaser only (Recommended).
**Notes:** Locked as D-97-PREV-1a + D-97-PREV-1b.

---

## Area 2 — POST-06 Batch / Single Surface (Match Preview Announcement)

### Q-2.1 — Batch-Iteration über matchday.matches

| Option | Description | Selected |
|--------|-------------|----------|
| Sequentiell single-threaded | for-each match, deterministische Reihenfolge | ✓ (zunächst, dann revidiert siehe Q-2.2) |
| Parallel max-5 via Executor | Schneller, aber Reihenfolge nicht deterministisch | |
| Sequentiell mit Thread.sleep | Hardcoded delay, YAGNI | |

**User's choice:** Sequentiell single-threaded (initial), DANN überstimmt durch Q-2.2-Klärung.
**Notes:** Kein Batch nötig — user korrigierte das Modell zu per-Match-einzeln (siehe Q-2.4).

### Q-2.2 — Partial-Fail-Handling (Batch)

**User's choice:** "Hier haben wir glaube ich ein Missverständnis. Die Preview Announcements werden einzeln und manuell vom Operator gepostet. Kein Batch".
**Notes:** **Kritische Scope-Korrektur.** Die approved 97-UI-SPEC.md hat POST-06b als Batch-Button-mit-Modal entworfen. User hat klargestellt: per-Match-einzeln. UI-SPEC + REQUIREMENTS.md müssen revidiert werden.

### Q-2.3 — POST-06a "Matchday Pairings" Status

**User's choice:** "Auch hier ist wieder ein Missverständnis. Im Announcement sind sowohl die Settings als auch die Lineup Grafiken im selben Post enthalten. Keine separaten Posts. Das Template wurde bereits bei der initialen Planung gezeigt."
**Notes:** User reichte Screenshot der aktuellen Saison nach. Sichtbar: 1 Post pro Match im Announcement-Kanal mit Markdown (H1/H2/H3 + Teaser + Date + Stream + Game On!) + Settings.png + Lineups.png als 2 Attachments + `(edited)`-Indikator (validiert Auto-Edit-Hook von Phase 95). Klärung: POST-06a wird gestrichen, nur POST-06 = per-Match Match-Preview-Announcement existiert.

### Q-2.4 — Button-Location nach Scope-Vereinfachung

| Option | Description | Selected |
|--------|-------------|----------|
| Match-Detail Page (.discord-actions--posts Cluster) | Neben TEAM_CARDS/SETTINGS/LINEUPS/SCHEDULE/MATCH_RESULTS; konsistent | ✓ |
| Matchday-Detail Page Match-Tabelle | Zentraler Überblick, mehr UI-Code | |
| Beide parallel | Phase 95 REJECTED Duplicate-Buttons | |

**User's choice:** Match-Detail Page (Recommended).
**Notes:** Locked als D-97-PREV-2.

### Q-2.5 — Markdown H3 Header Konvention

| Option | Description | Selected |
|--------|-------------|----------|
| team.name (full name) | UI-SPEC default | |
| team.shortName | DTR/TNR als shortNames | |
| team.shortName + sub-team-suffix (e.g. "B") | "DTR vs. TNR B" — wie im Screenshot | ✓ |

**User's choice:** shortName + sub-team-suffix.
**Notes:** Locked als D-97-PREV-2b. Planner inspect Team-Entity für saubere Suffix-Resolution.

### Q-2.6 — POST-06a separater Pairings-Overview-Post

| Option | Description | Selected |
|--------|-------------|----------|
| Nein — POST-06a komplett streichen | Nur per-Match Preview-Announcement; MATCHDAY_PAIRINGS Enum bleibt für v1.14 | ✓ |
| Ja — zusätzlicher Pairings-Button auf Matchday-Detail | MatchdayOverviewGraphic PNG; nicht im aktuellen Workflow | |

**User's choice:** Streichen (Recommended).
**Notes:** Locked. REQUIREMENTS.md POST-06 wird vereinfacht.

### Q-2.7 — Per-Match Preview-Button-Ort

| Option | Description | Selected |
|--------|-------------|----------|
| Match-Detail (.discord-actions--posts Cluster) | Konsistent mit Phase 95 Posts | ✓ |
| Matchday-Detail per Match-Zeile | Zentral, mehr UI-Code | |
| Beide parallel | REJECTED | |

**User's choice:** Match-Detail (Recommended).
**Notes:** Confirms Q-2.4. Locked.

### Q-2.8 — Pre-Flight-Predicates für Post-Match-Preview-Button

| Option | Description | Selected |
|--------|-------------|----------|
| Settings + Lineups + Teaser + StreamLink + ≥1 Race.dateTime | Strikt, 5 distinct tooltips | |
| Nur Settings + Lineups (Markdown-Felder optional) | Lockerste, TBD-Fallback | |
| Alle Felder zwingend (inkl. Teaser strikt) | Striktest, blockiert Operator | |

**User's choice:** "1, aber Streamlink kann null/leer sein (soll dann als TBA ausgegeben werden). Link kann häufig erst hinterlegt werden, wenn der Stream selbst live ist. Announcement muss aber schon davor raus".
**Notes:** Locked als D-97-PREV-2c — Hybridvariante. StreamLink darf null sein, alle anderen Felder Pflicht. `null` → renders "Stream: TBA". Auto-Edit-Hook patcht nachträglich wenn Operator StreamLink ergänzt.

---

## Area 3 — POST-07 (Matchday Overview + Power Rankings)

### Q-3.1 — Two separate posts oder 1 combined

| Option | Description | Selected |
|--------|-------------|----------|
| 1 combined Post mit beiden PNGs | Konsistent mit POST-06; atomic | |
| 2 separate Posts (UI-SPEC original) | MO + PR sequentiell; partial-fail-Logik nötig | ✓ |
| Anderes Layout | — | |

**User's choice:** "Match Day Results und Power Rankings sind unabhängig voneinander - 2 separate Posts, zeitlich entkoppelt. Power Rankings kommen zeitlich nach Results. Daher auch bitte 2 eigene Buttons dafür." + Screenshot des Forum-Threads.
**Notes:** Locked als D-97-MD-1. POST-07 wird in **POST-07a (Match Day Results)** + **POST-07b (Power Rankings)** mit zwei unabhängigen Buttons aufgespalten. Screenshot zeigt: 2 separate Posts im "Season 4 - 2026" Forum-Thread, zeitlich getrennt; Match Day 3 Results (per-match Scoring-Tabelle) zuerst, dann Power Rankings (1-14 ranked Teams in 2 Spalten).

### Q-3.2 — POST-07 Visibility-Predicate

| Option | Description | Selected |
|--------|-------------|----------|
| allMatchesFinal && thread linked && webhook configured | UI-SPEC | ✓ |
| ≥1 Match final + thread + webhook | Operator kann früher posten | |
| ≥1 Match final + alle Race-Results vorhanden | Stricter als B | |

**User's choice:** allMatchesFinal + thread + webhook (Recommended).
**Notes:** Locked für POST-07a als D-97-MD-1 (Pre-Flight POST-07a).

### Q-3.3 — POST-07 Stale-Detection

| Option | Description | Selected |
|--------|-------------|----------|
| Stale wenn ≥1 Race.results.updatedAt > matchdayOverviewPost.updatedAt | Service-Query, keine Schema-Änderung | ✓ |
| Stale wenn matchday.updatedAt > matchdayOverviewPost.updatedAt | Cross-cutting Hook | |
| Keine Stale-Detection | Operator entscheidet ohne Signal | |

**User's choice:** ≥1 Race.results.updatedAt > post.updatedAt (Recommended).
**Notes:** Locked für POST-07a. POST-07b verwendet stattdessen MAX(SeasonTeam.updatedAt) (siehe Q-3.5).

### Q-3.4 — Button-Placement + Discord-Post-Type-Granularität

| Option | Description | Selected |
|--------|-------------|----------|
| 2 separate Buttons im selben Cluster, 2 distinct types | Eine Discord-Actions-Card, 2 unabhängige Posts | ✓ |
| 2 separate Cards/Sektionen | Mehr visuelle Trennung | |
| Anders | — | |

**User's choice:** 2 separate Buttons im selben Cluster (Recommended).
**Notes:** Locked als D-97-MD-2.

### Q-3.5 — POST-07b Power Rankings Pre-Flight

| Option | Description | Selected |
|--------|-------------|----------|
| Identisch zu POST-07a (allMatchesFinal) | Konservativ | |
| Stricter: + season hat ≥2 Matchdays mit Results | Power Rankings brauchen Vergleichs-Historie | |
| Looser: nur thread + webhook | Operator entscheidet manuell | ✓ |

**User's choice:** "3, Die Ratings der Teams werden nach dem Matchday vom Operator manuell aktualisiert. Wird auch für die neuen Stände der Team Cards benötigt. Die Power Rankings spiegeln nicht immer den gleichen Stand wie die Standings dar".
**Notes:** **Wichtige Klärung:** Power Rankings sind operator-manuell-gepflegt (über `/admin/tools/power-rankings` → `SeasonTeam.rating`). Nicht aus Standings auto-berechnet. Auch Quelle für Team-Card-Updates. Locked als D-97-MD-1 (Pre-Flight POST-07b) + D-97-MD-1 (Source = SeasonTeam.rating).

---

## Area 4 — POST-08 Standings on Season-Form

### Q-4.1 — Standings Stale-Detection-Signal-Source

| Option | Description | Selected |
|--------|-------------|----------|
| Service-Query MAX(RaceResult.updatedAt im season) > standingsPost.updatedAt | Keine Schema-Änderung | ✓ |
| Cross-cutting Hook bumpt season.updatedAt | Weniger Queries, aber false-positive Stale-Signals | |
| Neue Spalte seasons.last_standings_relevant_change_at | Saubere Trennung, V14 nötig | |
| Keine Stale-Detection | Konsistent mit POST-07b | |

**User's choice:** Service-Query MAX(RaceResult.updatedAt) (Recommended).
**Notes:** Locked als D-97-STA-3. KEIN Auto-Edit-Hook.

### Q-4.2 — Standings Button-Location

| Option | Description | Selected |
|--------|-------------|----------|
| season-form.html #discordIntegration Card | UI-SPEC verbatim | ✓ |
| Season-Detail Page (Design-Spec § 4.3) | Phase 96 D-96-FOR-2b hat Edit-Page bevorzugt | |
| Beide parallel | REJECTED | |

**User's choice:** season-form.html #discordIntegration Card (Recommended).
**Notes:** Locked als D-97-STA-2.

---

---

## Area 5 — Saison-Phasen-Awareness (Regular Season mit/ohne Gruppen, Playoffs, Placement)

### Q-5.1 — POST-07a Service-Dispatch je PhaseType

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-dispatch per matchday.phase.phaseType | REGULAR → MatchdayResultsGraphic; PLAYOFF → PlayoffRoundResults (mit Matchday→Round-Mapping); PLACEMENT → ? | |
| Nur REGULAR (Phase 97 Scope), Playoffs als deferred | Reduzierter Scope | |
| MatchdayResultsGraphicService für ALLE PhaseTypes | Einfachster Code, kein PhaseType-Switch | ✓ |

**User's choice:** "3 passt vom Grundsatz her doch. ich verstehe den Einwand mit dem Bracket nicht. Ist doch in der Grafik nicht wichtig. Es müssen einfach nur alle Ergebnisse des Matchdays bzw. Playoff Runde dargestellt werden".
**Notes:** Locked als D-97-PHA-1. PlayoffRound{Overview,Results,Schedule}GraphicService bleiben für andere Use-Cases (z.B. Playoff-Bracket-Overview-Page); Phase 97 nutzt sie nicht.

### Q-5.2 — POST-06 Markdown H2 bei Playoff/Placement

| Option | Description | Selected |
|--------|-------------|----------|
| matchday.label direkt | Operator-managed, maximale Flexibilität | ✓ |
| Phase-bedingt: 'Match Day N' / 'Playoff Round N' / 'Placement Match N' | Konsistent, keine Override-Möglichkeit | |
| Hybrid: label falls gesetzt, sonst formatted | Mehr Logik | |

**User's choice:** matchday.label direkt (Recommended).
**Notes:** Locked als D-97-PHA-2. UI-SPEC's hardcoded `## Match Day {N}` wird in Plan 97-01 zu `## {matchday.label}` revidiert.

### Q-5.3 — POST-07b Power Rankings phasen-spezifisch oder season-weit?

| Option | Description | Selected |
|--------|-------------|----------|
| Season-weit, wie heute | PowerRankingsGraphicService.loadTeamsForSeasonGroup(year, number) liest alle Season-Teams | ✓ |
| Per Phase getrennt | Substantielle Erweiterung nötig | |
| Per Group getrennt | Existing Service supportet nicht | |

**User's choice:** Season-weit (Recommended).
**Notes:** Locked als D-97-PHA-3. Power Rankings spiegeln operator-curated `SeasonTeam.rating` über die ganze Season; phasen-agnostisch.

---

## Area 6 — StandingsGraphic Layout + Multi-Group-Rendering + Schema

### Q-6.1 — Standings PNG-Granularität pro Klick

| Option | Description | Selected |
|--------|-------------|----------|
| 1 PNG für 1 Phase + 1 Group (Operator selektiert) | Dropdown auf season-form, 1 Discord-Post pro Klick | |
| 1 kombinierte PNG mit allen Phasen × Groups | Multi-Section, schwer auf Mobile | |
| Multi-Post pro Klick: 1 PNG pro Phase × Group automatisch | Discord-Spam-Risiko | |
| Default: aktuell-aktive Phase + all Groups als 1 PNG mit Tabs | Wenig Kontrolle | |
| **User-defined Hybrid** | REGULAR ohne Groups → 1 PNG; REGULAR mit Groups → 1 PNG je Group im selben Post; PLAYOFF/PLACEMENT → 1 PNG (keine Groups) | ✓ |

**User's choice:** "Bei Regular Season Phase ohne Gruppen 1 PNG. Bei Regular Season Phase mit mehreren Gruppen 1 PNG je Gruppe im selben Post. Bei Playoff und Placement Matches 1 PNG (hier wird es keine Gruppen geben)".
**Notes:** Locked als D-97-STA-3. Granularität-Tabelle:

| Phase | PhaseLayout | PNG count | Discord-message shape |
|-------|-------------|-----------|-----------------------|
| REGULAR | non-GROUPS | 1 | Single attachment |
| REGULAR | GROUPS | N (1/Group) | Multipart in ONE message |
| PLAYOFF | non-GROUPS | 1 | Single attachment |
| PLACEMENT | non-GROUPS | 1 | Single attachment |

Operator wählt Phase via Dropdown; bei N=1 implicit, bei N>1 Multipart-Webhook-POST (analog Phase 96 ProvisionalScores).

### Q-6.2 — Layout-Quelle

| Option | Description | Selected |
|--------|-------------|----------|
| Iterativ designen via playwright-cli | Anlehnung an admin/standings.html, Wave-Pause-Approval | ✓ |
| User stellt Reference-Screenshot bereit | Pixel-near nachbauen | |
| Anlehnung an site/standings.html (Public-Site) | Designtechnisch ausgereifter | |
| Anlehnung an MatchdayResultsGraphic | Konsistente Discord-Brand-Welt | |

**User's choice:** "1, aber gleich berücksichtigen, dass das aktuelle Team Logo integriert sein soll".
**Notes:** Locked als D-97-STA-1. Iterativer Design-Loop via playwright-cli ([[feedback-graphic-pixel-positioning]] + [[feedback-graphic-design-iteration]]). **Team-Logos müssen integriert sein** — konsistent mit MatchdayResultsGraphic + PowerRankingsGraphic.

### Q-6.3 — Schema-Änderung für discord_post phase/group

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse season_id, snapshot phase/group in payload_json | Keine FK-Spalte, JSON-Snapshot | ✓ (vom User, dann pivot zu FK-Column siehe Notes) |
| Neue FK-Spalten phase_id + group_id (V14) | Sauberer schema-design | |
| Mehrere STANDINGS-Rows per (season + discord_message_id) | Identity-Logik komplex | |

**User's choice:** Reuse season_id, snapshot phase/group in payload_json (Recommended).
**Notes:** User wählte Option A (payload_json). **Implementation pivotiert zu dedicated FK-Spalte** (D-97-STA-4) für Konsistenz mit existing polymorphem FK-Pattern (V12 hat match_id/matchday_id/race_id/season_id alle als FK). Group-Discriminator entfällt durch D-97-STA-3 — Multipart-Post bündelt alle Groups einer Phase in 1 Row. Neue V14: `ALTER TABLE discord_post ADD COLUMN phase_id UUID NULL` + FK + Index. Identity-Key STANDINGS: `(season_id, phase_id, post_type=STANDINGS)`. SeasonRef-Record (Phase 96 D-96-FOR-3b) wird widened zu `SeasonRef(seasonId, phaseId)` (recommended) ODER neuer SeasonPhaseRef-Permit (Planner-Discretion).

---

## Claude's Discretion

- **Power Rankings persistence strategy (subtitle + teamIds-order)** — Planner picks
  snapshot-into-discord_post.payload_json vs always-regenerate-from-current-rating.
  Recommended: regenerate (matches operator mental model).
- **Sub-team-suffix resolution for Markdown H3** — Planner inspects Team entity to
  pick cleanest field/method for the "B"/"C"/... suffix.
- **`StandingsGraphicService` template strategy** — duplicate `standings.html`
  vs reuse via Thymeleaf fragments.
- **`MatchdayResultsGraphicService.generateMatchdayResultsBytes` byte[] variant** —
  Planner verifies existing presence or adds analog to Phase 96 D-96-FOR-3d.
- **`discord_post.matchday_id` FK column existence** — Planner verifies that Phase 95
  V11 provisioned the matchday_id column alongside the other polymorphic FKs; if not,
  Plan 97-02 adds V14 migration as a prerequisite.
- **Visual-regression snapshot tests** for the 5 new Discord-message layouts
  (MATCH_PREVIEW, MATCHDAY_OVERVIEW, POWER_RANKINGS, STANDINGS-single,
  STANDINGS-multi-group) — Plan 97-02 + 97-03 Planner picks test-or-defer.
- **`SeasonRef` widening vs new `SeasonPhaseRef` permit** (D-97-STA-4) — Planner
  greps Phase 96 `SeasonRef.*new` callsites; widening recommended if ≤ 2 callsites.
- **`StandingsGraphicService` Multipart-PNG iteration order** — `SeasonPhaseGroup.
  sortIndex ASC` (existing convention).

---

## Deferred Ideas

- **MATCHDAY_PAIRINGS overview-graphic post** — REQUIREMENTS.md POST-06 originally
  included; production workflow doesn't use. Enum value stays for potential v1.14.
- **Race.dateTime auto-edit trigger for MATCH_PREVIEW** — if reschedule-after-
  announcement becomes frequent, v1.14+ can extend the diff-publish hook to
  RaceService.save.
- **PowerRankings persistent ordering snapshot per Discord-post** — if operator-
  friction with "rating changed between Post and Re-Post" appears in UAT-07, a
  `discord_post.payload_json` column + V14 migration can be added in v1.14.
- **Pinned-thread auto-bump-to-top on Re-Post** — Discord forum-threads bump on new
  activity; PATCH doesn't. Out of v1.13 scope.
- **`/admin/discord/posts` listing filter dropdowns per scope** — extra dropdowns
  for season-scope / matchday-scope; Phase 98 polish if needed.
