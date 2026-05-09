# Phase 60: Admin UI - Context

**Gathered:** 2026-04-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Die Admin-UI wird vollständig auf das neue Saison/Phase/Group-Modell umgestellt. Alle 7 UI-Anforderungen (UI-01..UI-07) werden in dieser Phase geliefert:

- **Saison-Form** wird auf Stamm-/Identitätsfelder reduziert; format/scoring/dates/rounds/legs verschwinden aus dieser Form (Phase 58 D-25 Auto-Sync-Block wird mit-entfernt).
- **Saison-Detail-Page** rendert Phase-Tabs (nur existierende + ein "+ Add Phase"-CTA) mit Group-Sub-Tabs in GROUPS-Layout-Phasen.
- **Phase-CRUD-Form** unter `/admin/seasons/{id}/phases/new` und `.../{phaseId}/edit` mit allen Phase-Feldern (Typ, Layout, Format, Scoring, Dates, Rounds, Legs, EventDuration, Label).
- **Group-CRUD-Form** unter `/admin/seasons/{sid}/phases/{pid}/groups/new` (Two-Step: Name → dann Roster-Editor).
- **Standings-UI** mit Phase-Tabs + Group-Sub-Tabs konsistent zur Saison-Detail-Architektur; Default-View bei GROUPS = Combined.
- **Driver-Import-Preview** rendert Group-Spalte (konditional bei GROUPS-Layout) + TabWarning-Banner + Inline-Badges.
- **Playoff-UI** bleibt unter `/admin/playoffs/{id}` (server-side Phase-Resolution); "+ Create Playoff Phase"-CTA wandert auf die Saison-Detail-Page als "+ Add Phase" mit phaseType=PLAYOFF.

**Backend ist vollständig vorbereitet** (Phase 56-59) und wird nicht erneut diskutiert:
- Phase 58 D-01/D-03: Services haben `phaseId`-kanonische Methoden + `@Deprecated` `seasonId`-Overloads.
- Phase 58 D-04/D-05: Combined-View = flache `List<TeamStanding>` mit nullable `group`-Feld.
- Phase 58 D-19: `PlayoffService.createPlayoff(...)` legt PLAYOFF-Phase auto an.
- Phase 58 D-20: REGULAR-Phase-Roster ist auto-derived; GROUPS-Phase-Roster startet leer.
- Phase 58 D-25: `SeasonManagementService.save` syncen heute scoring/format/dates auf REGULAR-Phase — Block wird in dieser Phase entfernt.
- Phase 58 D-26: `MatchdayService` hat sowohl `findBySeasonId` (deprecated) als auch `findByPhaseId`.
- Phase 59 D-08: `TabPreview.warnings` und `Driver-Row.resolvedGroupName` sind backend-seitig fertig.

**Explizit out of scope für Phase 60:**

- **Manueller Saison-Selector-Dropdown für ambiguous Tabs** (Phase 59 D-03 versprach UI in Phase 60) — *nicht gebaut*. Real-world-Sheets enthalten ausschliesslich volle Saisons (project memory `feedback_real_world_sheet_shape`), Group-Resolution ist Edge-Case. Bei `ambiguousReason != null` rendert UI nur ein Plain-Banner. Backend-Pfad bleibt unverändert.
- **`MIGR-06` Cleanup (Drop legacy `Season`-Spalten + `playoff_seasons`-Tabelle)** — Phase 61.
- **Aggressive `@Deprecated`-Service-Bridge-Cleanup** — Phase 60 entfernt nur Overloads, deren Controller-Caller hier wegfallen (D-44, "Conservative"). Restliche Bridges bleiben für Phase 61.
- **E2E Playwright-Test für GROUPS-Saison-Workflow** — Phase 61 (QUAL-02).
- **Drag-and-Drop für Tab-/Group-Reorder** — sortIndex ist auto-set + manuell editierbar in Form (D-10, D-24).
- **Per-Group-Playoff-Brackets** (`PLAYOFF-FUT-01`) — future milestone.
- **`CONSOL-FUT-01` UI für manuelle Saison-Konsolidierung** — future milestone (von Phase 59 deferred).
- **Mobile-Dropdown-Navigation** (Alternative zu Horizontal-Scroll) — D-11 entscheidet sich für Horizontal-Scroll.

</domain>

<decisions>
## Implementation Decisions

### Saison-Detail-Tabs (UI-01, UI-02)

- **D-01: Tab-Sichtbarkeit = nur existierende Phasen + ein einzelner "+ Add Phase"-CTA.** Keine Placeholder-Tabs für nicht-existierende phaseType-Slots. Saubere visuelle Hierarchie. Konsistent mit Phase 58 D-19 (PLAYOFF auto-create) — User klickt "+ Add Phase" und wählt Typ.

- **D-02: Page-Komposition = Saison-Header oben (Stamm-Daten + Teams + Cars/Tracks) + Phase-Tabs darunter.** Teams (`SeasonTeam`-Liste) und Cars/Tracks bleiben Saison-weit (pure-gem.md Z. 110-112: "cars/tracks (Saison-weit verfügbare Assets bleiben Saison-Eigentum)"). Phase-Tabs zeigen nur phase-spezifischen Inhalt: Roster (`PhaseTeam`), Matchdays, Standings, ggf. Bracket.

- **D-03: Server-Routing = `/admin/seasons/{id}/phases/{phaseId}` + `/admin/seasons/{id}/phases/{phaseId}/groups/{groupId}`.** Pro Phase/Group ein eigener Pfad. `/admin/seasons/{id}` redirected auf REGULAR-Phase (oder Empty-State per D-08). Konsequenz: SeasonController bekommt zusätzliche Routen `/{id}/phases/{phaseId}` und `/{id}/phases/{phaseId}/groups/{groupId}` — alternativ ein neuer SeasonPhaseController mit basePath `/admin/seasons/{seasonId}/phases`.

- **D-04: Inhalt innerhalb eines Phase-Tabs = linear gestapelte Sections** (Roster → Matchdays → Standings; bei PLAYOFF: Roster → Bracket → Seeds). Anker-Links (`#roster`, `#matchdays`, `#standings`) für Sprung-Navigation. Konsistent mit existierender season-detail.html-Card-Stack-Konvention (293 Zeilen, keine Sub-Tabs).

- **D-05: Tab-Beschriftung = `phase.label` falls non-blank, sonst phaseType-Default** (`"Regular Season"`, `"Playoff"`, `"Placement"`). Erlaubt User-Anpassung (z. B. "Reg. Season Spring 2025"). Konsistent mit Phase 58 D-19 (`label = playoff.name` beim Auto-Create).

- **D-06: "+ Add Phase"-CTA = Navigation zu eigener Form-Page** `/admin/seasons/{id}/phases/new`. Kein Modal, kein Inline-Form. Konsistent mit Codebase-Konvention (alle anderen Forms sind eigene Pages).

- **D-07: Action-Buttons-Verteilung = Saison-Header behält nur Saison-weite Aktionen** (Edit Saison, Delete Saison). Phase-spezifische Aktionen ("Edit Phase", "Delete Phase", "Generate Matchdays", "Swiss Rounds") wandern in den jeweiligen Phase-Tab. Klare Verantwortungs-Trennung — der heutige toolbar-Block in season-detail.html (Z. 6-20) wird auf Saison-Aktionen reduziert.

- **D-08: Empty-State bei fehlender REGULAR-Phase = Empty-State-Card mit "+ Add REGULAR Phase"-CTA.** Theoretisch unmöglich post-V4-Migration, aber defensiv: wenn manuelle Löschung passiert, render `phaseType=REGULAR`-vorausgewählter Form-Link. Keine Exception, keine Cascade-Aktion.

- **D-09: URL-Safety bei Phase/Saison-Mismatch = strikter 404 via `EntityNotFoundException`.** Controller-Validierung: `phase.getSeason().getId().equals(seasonId)` muss true sein, sonst Exception. GlobalExceptionHandler rendert 404. Schutz gegen IDOR.

- **D-10: `SeasonPhase.sortIndex` = auto-set on create, nicht UI-änderbar.** REGULAR=0, PLAYOFF=10, PLACEMENT=20 (hardcoded; Phase 58 D-19 setzt heute schon sortIndex=10 für PLAYOFF). Tab-Reihenfolge ist deterministisch. Keine Drag-and-Drop-UI.

- **D-11: Mobile-Layout = Horizontal-Scroll-Tabs** (`overflow-x: auto` auf `.tabs`-Container). Group-Sub-Tabs ebenso. Touch-nativ, keine User-Agent-Detection nötig.

- **D-12: Legacy-URL `/admin/standings?season={id}` bleibt funktional** via Auto-Resolve zur REGULAR-Phase. Keine 302-Redirect — Controller akzeptiert beide Query-Params (`?season=` legacy, `?phase=` neu). Default-View für GROUPS bei Legacy-URL: Combined. Konsistent mit CLAUDE.md "Backward Compatibility".

- **D-13: Page-Title = `"{season.name} — {phase.label}"`** (sowohl Browser-Tab-Title als auch h1). Eindeutig im Browser-History und in Bookmarks. Konsistent mit existing pattern (`"Season: " + ${season.name}`).

- **D-14: Keine Breadcrumb-Navigation eingeführt.** Bestehender `← Back to Seasons`-Link bleibt. Tabs selbst signalisieren Position. Keine Pattern-Drift.

- **D-15: `Season.active`-Badge bleibt Saison-weit** (Header). Keine zusätzliche Active-Phase-Visualisierung basierend auf `phase.startDate`/`endDate` — Defaults sind oft leer, würde inkonsistent wirken.

### Phase + Group Form-Muster (UI-03, UI-04)

- **D-16: Phase-Form = volle Form mit allen Feldern.** `phaseType`, `layout`, `format`, `raceScoring`, `matchScoring`, `startDate`, `endDate`, `totalRounds`, `legs`, `eventDurationMinutes`, `label`. PLAYOFF kann manuell ausgewählt werden (Override-Option), aber Phase 58 D-19 macht den Auto-Create-Pfad zum Standard. Edit-Modus zeigt dieselben Felder.

- **D-17: Form-Defaults bei `+ Add Phase` = aus REGULAR-Phase der Saison kopieren.** Konsistent mit pure-gem.md §"Schlüssel-Entscheidungen" Punkt 3 ("Sind sie pro Saison identisch, werden sie über Form-Defaults bequem geerbt"). Bei keiner REGULAR-Phase: Application-weite Defaults (LEAGUE format, kein Scoring vorausgewählt). User kann jederzeit überschreiben.

- **D-18: GROUPS-Layout-Phase wird mit 0 Groups angelegt** — User legt Groups danach via separater Group-Form an. Phase-Save → Phase-Detail-Tab öffnet sich → Group-Section ist leer + zeigt "+ Add Group"-CTA. Konsistent mit Phase 58 D-20 ("GROUPS-layout REGULAR roster startet leer").

- **D-19: Group-Form = Two-Step.** Schritt 1: `/admin/seasons/{sid}/phases/{pid}/groups/new` mit `name` + `sortIndex`. Schritt 2: Group-Detail-Page (oder Sub-Tab) zeigt Roster mit Multi-Select-Editor aus Saison-Teams. Klar separierte Verantwortungen.

- **D-20: Roster-Widget = Multi-Select-Checkbox-Liste mit Group-Dropdown pro Team.** Liste aller `SeasonTeam` der Saison + Checkbox (in Phase aufgenommen?) + Dropdown pro Team ("— No Group —" / "Group A" / ...). Submit aktualisiert `PhaseTeam`-Rows in einer Transaction. Funktioniert für LEAGUE (Dropdown versteckt, alle Group=NULL) und GROUPS (Dropdown sichtbar). Kein Drag-and-Drop.

- **D-21: Edit-Phase-Form = alle Felder editierbar; format/layout-Wechsel mit Server-Side-Pre-Check + Warning-Banner bei Datenkonflikt.** `SeasonPhaseService.update()` prüft Matchday/Race-Existenz und wirft `BusinessRuleException` wenn struktureller Konflikt. Form rendert das Banner: "This phase has X matchdays — changing layout requires deleting them first." Konsistent mit "No Fallback Calculations" — kein Silent-Cascade.

- **D-22: Form-Validation = `@NotNull` auf `phaseType`, `layout`, `format`. Optional: `dates`, `rounds`, `legs`, `eventDuration`, `label`.** Layout-Format-Kompatibilität (z. B. `layout=BRACKET` nur bei `phaseType=PLAYOFF`) wird serverseitig in `SeasonPhaseService.create/update` validiert. UNIQUE-Constraint pro phaseType wird durch Phase 56 D-03 + Phase 58 D-14 abgedeckt.

- **D-23: Delete-Phase = strict guard via `SeasonPhaseService.delete()`.** Service refused Delete wenn Phase Matchdays/PlayoffMatches/PhaseTeams enthält. Wirft `BusinessRuleException("Phase has X matchdays — clear them first")`. Flash-Error auf der Saison-Detail-Page. Konsistent mit Phase 58 D-18 (Saison-Delete) und CLAUDE.md "No Fallback".

- **D-24: `SeasonPhaseGroup.sortIndex` = auto-increment (max+1) beim Anlegen, manuell anpassbar in Form** (Integer-Input). Group-Tabs werden in `sortIndex`-Reihenfolge angezeigt. Kein Drag-and-Drop.

- **D-25: SeasonTeam-Delete = strict guard wenn `PhaseTeam`-Rows existieren.** `SeasonManagementService.removeTeamFromSeason` wirft `BusinessRuleException` wenn Team in irgendeiner Phase als PhaseTeam aktiv ist. User muss Team erst aus Phasen entfernen, dann aus Saison. Spiegelt Phase 58 D-18.

- **D-26: SeasonTeam-Add = auto-add zur REGULAR-Phase mit `group=NULL`.** Konsistent mit Phase 58 D-20. Atomisch in derselben Transaction. Bei GROUPS-Phase: User muss anschliessend Group-Zuordnung via Roster-Editor machen.

- **D-27: Form-Errors = Field-Level mit `BindingResult` + Flash-Error-Banner für `BusinessRuleException`.** Bean-Validation per `th:errors` direkt unter dem Feld. BusinessRule oben als `successMessage`/`errorMessage` (CLAUDE.md §Conventions). Konsistent mit existing forms (z. B. season-form.html).

- **D-28: Delete-Group = strict guard wenn Group Teams oder Matchdays enthält.** `SeasonPhaseGroupService.delete` wirft `BusinessRuleException`. User muss erst Teams umverteilen / Matchdays löschen.

### Standings-UI + URL-Strategie (UI-05)

- **D-29: Selector-Stil = Phase-Tabs + Group-Sub-Tabs** (konsistent mit Saison-Detail-Tab-Architektur, Bereich 1). Tab-Row 1: Phase-Tabs (sichtbar sind nur existierende Phasen). Tab-Row 2 (nur bei GROUPS-Phase): "Combined" + "Group A" + "Group B" + ... Visual-Konsistenz zwischen `/admin/seasons/{id}/...` und `/admin/standings`.

- **D-30: Default-View für GROUPS-Layout = Combined-View** (alle Groups flach mit Group-Badge-Spalte). User klickt explizit auf einzelne Group für Per-Group-Sicht. Konsistent mit Phase 58 D-04.

- **D-31: URL-Schema = `/admin/standings?phase={phaseId}&group={groupId}`** mit Legacy-Bridge `?season={seasonId}` (auto-resolved zur REGULAR-Phase, D-12). Beide funktionieren parallel. Path-basiert (`/admin/standings/{phaseId}/...`) wurde abgelehnt — minimale URL-Änderung, klare Bridge.

- **D-32: Combined- vs. Per-Group-View-Rendering = Combined hat Group-Spalte, Per-Group hat sie nicht.** Server liefert `combinedView`-Boolean ans Template. `th:if="${combinedView}"` zeigt/blendet Spalte aus. Spaltenstruktur sonst identisch.

- **D-33: Buchholz-Spalte = nur Per-Group-View bei Swiss-Format-Phase, sonst versteckt.** Server-Flag: `showBuchholz = phase.format == SWISS && groupId != null`. Combined-View blendet die Spalte aus (Phase 58 D-06: für Combined wird sie ohnehin nicht zur Sortierung herangezogen).

- **D-34: Saison-Dropdown bleibt oben** (zeigt alle Saisons sortiert). Saison-Auswahl rendert die Phase-Tabs der ausgewählten Saison. Default beim Page-Load: `season.active=true`. Casual-Browsing-UX bleibt erhalten.

- **D-35: Page-Title = h1 "Standings"; Saison-Name + Phase-Name als Sub-Header.** Statische h1, dynamische Sub-Header. Konsistent mit existing static-h1-Pattern auf anderen Index-Pages.

- **D-36: Empty-State bei keinem Race-Result = Tabelle mit allen Teams + 0-Punkten + Hinweis-Banner "No results yet".** Roster wird gerendert (alle Teams aus PhaseTeam-Roster, 0 Punkte). Banner: "This phase has no race results yet — standings will appear once results are recorded."

### Driver-Import-Preview + Playoff-Cutover (UI-06, UI-07)

- **D-37: Tab-Label-Display = raw Sheet-Tab-Name 1:1 übernehmen.** Echte Sheets enthalten ausschliesslich volle Saisons (project memory `feedback_real_world_sheet_shape`); keine `year_S{number}`-Transformation, keine human-friendly-Erweiterung nötig. Tab-Header zeigt schlicht den Sheet-Tab-Namen (`2025` oder `2025_S2` exakt wie im Sheet). Saison-Auflösung passiert backend-seitig via `SeasonRepository.findByYearAndNumber`-Wrapper (Phase 59 D-02).

- **D-38: Manueller Saison-Selector-Dropdown für ambigue Tabs = NICHT gebaut.** Phase 59 D-03 hat eine UI-Dropdown-Lösung in Phase 60 versprochen, aber: Real-world-Sheets triggern den ambiguen Fall nicht (Group-Resolution ist Edge-Case). Falls `tabPreview.ambiguousReason != null` doch auftritt, rendert UI nur ein Plain-Banner "Ambiguous tab — rename to {year}_S{N}". Backend-Verhalten (BusinessRuleException-Wrapping) bleibt unverändert.

- **D-39: TabWarning-Rendering = Warning-Banner oben im Tab + Inline-Badge auf jeder betroffenen Driver-Zeile.** Tab-Header rendert aufklappbares Banner: "⚠ {N} teams without group assignment in this season's REGULAR phase". Driver-Zeilen mit `resolvedGroupName == null` und `phase.layout == GROUPS` zeigen in der Group-Spalte ein Badge "⚠ No group" + Tooltip mit dem Team-Namen. Phase 59 D-08 liefert `resolvedGroupName` und `TabWarning`-Records.

- **D-40: Group-Spalte in Driver-Preview-Tabelle = neue Spalte zwischen Team und Action, sichtbar nur bei GROUPS-Layout.** Server-Flag: `showGroupColumn = phase.layout == GROUPS`. `th:if`-konditional gerendert. Bei LEAGUE: Spalte versteckt (alle `resolvedGroupName=null`). Konsistent mit Standings-D-32.

- **D-41: Playoff-URL = `/admin/playoffs/{id}` bleibt unverändert.** PlayoffController resolved `playoff.getPhase()` server-side. Alte URLs/Bookmarks bleiben gültig. Konsistent mit CLAUDE.md "Backward Compatibility" und D-12. Keine 302-Redirects.

- **D-42: "+ Create Playoff Phase"-CTA = auf Saison-Detail-Page als "+ Add Phase" mit `phaseType=PLAYOFF`.** Konsistent mit D-06 (eine zentrale Phase-Form). Existing `/admin/playoffs/new` bleibt funktional, ist aber redundant — interne Links werden nicht mehr darauf zeigen. Phase 58 D-19's PlayoffService.createPlayoff-Auto-Create-Pfad bleibt der Service-API-Default; UI bedient sich seiner.

- **D-43: Legacy-Endpoints `/admin/playoffs/{id}/add-season` + `/remove-season` = backend bleibt funktional, UI verbirgt sie.** PlayoffController-Routen werden NICHT entfernt (Phase 61 cleant M:N `playoff_seasons`). `playoff-bracket.html` rendert die "Add Season"-Buttons nicht mehr. Backend bleibt `@Deprecated`-konform, kein API-Bruch.

- **D-44: `@Deprecated`-Service-Bridge-Cleanup = Conservative.** Phase 60 entfernt nur die Overloads, deren Controller-Caller in dieser Phase wegfallen. Konkret: PlayoffController, StandingsController, SeasonController, MatchdayController, RaceController switchen auf `phaseId`-kanonische Methoden — die korrespondierenden Service-Overloads gehen weg. Service-Methoden mit anderen Konsumenten (SiteGenerator hat Phase 58 D-23 schon) behalten ihre Overloads bis Phase 61 (MIGR-06-Cleanup).

### Claude's Discretion

- Exakte Aufteilung in Sub-Controller (`SeasonPhaseController` vs. SeasonController-Routen) — Planner wählt das pattern, das mit existierenden Controller-Strukturen am wenigsten Pattern-Drift erzeugt; Empfehlung: separater `SeasonPhaseController` für `/admin/seasons/{id}/phases/...` analog zu MatchdayController-Struktur, plus `SeasonPhaseGroupController` für Group-Routen.
- Detaillierte CSS-Klassen-Namen für die Tab-Implementierung (`tabs-row-1`, `tabs-row-2`, `phase-tab`, `group-sub-tab`) — Planner wählt aus admin.css-Vorbildern; CLAUDE.md "No Inline Styles on Buttons" gilt strikt.
- Exakte Templates-Dateinamen (`season-phase-form.html`, `season-phase-group-form.html`, oder `season-detail.html`-Erweiterung statt neuer Datei) — Planner finalisiert; Empfehlung: zwei neue Templates für Forms, season-detail.html wird massiv erweitert (Tab-Struktur), kein neues Template dafür nötig.
- `SeasonPhaseForm`-DTO Feldnamen + Validierungs-Annotation-Details — Planner folgt SeasonForm-Konventionen (Lombok `@Getter @Setter @NoArgsConstructor`, `jakarta.validation.constraints.NotNull`).
- Wave-Plan-Split (z. B. Wave 1: Phase + Group Backend-CRUD-Routes, Wave 2: Saison-Detail-Templates, Wave 3: Standings + Importer-Preview-Templates, Wave 4: Playoff-Cutover) — Planner finalisiert.
- Konkrete Test-Strategie pro UI-Anforderung (Mockito Controller-Tests + Service-IT + ggf. Playwright-CLI-Verifikation für visuelle Regression) — Planner entscheidet nach Phase-58/59-Vorbildern; CLAUDE.md verlangt mindestens unit + integration + JaCoCo ≥ 82%.
- Behandlung von gleichzeitigem Edit von Phase und ihrer Saison (optimistic locking?) — wenn keine concurrent-edit-Issues in der Praxis erwartet werden, Planner kann das ignorieren; sonst Standard-Lombok-`@Version`-Pattern.

</decisions>

<specifics>
## Specific Ideas

- **Project memory `feedback_real_world_sheet_shape`** war ausschlaggebend für D-37/D-38: "Echte Sheets enthalten nur volle Saisons, keine Group-Splits — Group-Resolution ist Edge-Case". Phase 59 D-03 versprach UI-Dropdown — der wird in Phase 60 explizit nicht gebaut, weil reale Daten ihn nicht triggern. Backend-Pfad bleibt erhalten, Plain-Banner reicht.
- **D-29's Tab-Konsistenz** zwischen Saison-Detail und Standings ist visuelles Kern-Element: User sieht denselben Phase-Tab + Group-Sub-Tab-Mechanismus auf beiden Pages. Reduziert kognitive Last.
- **D-25/D-26 SeasonTeam-Sync** ist die UI-seitige Manifestation von Phase 58 D-20's Backend-Decision. Add-to-SeasonTeam-Form muss nun zwei Inserts machen (SeasonTeam + REGULAR-PhaseTeam mit group=NULL) atomisch — Service-Layer muss das in einer Transaction kapseln.
- **D-44's Conservative Cleanup** macht Phase 61 zum tatsächlichen "Final-Migration"-Schritt: Phase 60 nimmt sich nur die Bridges, die durch UI-Cutover unbenutzt werden. Phase 61 (MIGR-06) räumt den Rest auf. Klarer Phasen-Schnitt.
- **Standings.html (heute 230 Zeilen)** wird ein wesentliches Refactor: Saison-Dropdown bleibt, Phase-Tab + Group-Sub-Tab + Combined-View-Spalte ergänzen sich. Geschätzt +50-80 Zeilen Template-Logik plus serverseitige `combinedView`-/`showBuchholz`-/`showGroupColumn`-Flags.
- **D-09's strict 404 IDOR-Schutz** ist Standard-Spring-Pattern: Controller validiert Hierarchie-Zugehörigkeit explizit. Kein neuer Pattern, keine zusätzliche Sicherheits-Infrastruktur.
- **D-13's Page-Title-Pattern** macht Phase-Layouts in der Browser-History deutlich erkennbar — wichtig für User mit vielen Saisons.
- **CLAUDE.md `playwright-cli`-Pflicht** für UI-Änderungen: Planner sollte für jeden geänderten Template-Pfad eine Visual-Verification-Subtask vorsehen — Desktop + Mobile.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Foundation & requirements (read before planning)
- `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` §"UI-Änderungen" (Z. 125-164) — non-negotiable design source für Saison-Detail / Phase-Form / Standings / Driver-Import / Playoff. Liest die ganzen Z. 125-164 als die UI-Vision.
- `.planning/REQUIREMENTS.md` §UI-01..UI-07 — locked requirements scoped to this phase.
- `.planning/REQUIREMENTS.md` §"Out of Scope" Tabelle — `UNIQUE (year, number)`-Constraint, heuristische Saison-Konsolidierung, sub-group-aware Playoffs sind explizit ausgeschlossen.
- `.planning/ROADMAP.md` §"Phase 60: Admin UI" — Goal, success criteria 1-7, dependency boundary (Depends on Phase 58).
- `.planning/STATE.md` §"Key Technical Context" — Liste der ~25-30 zu modifizierenden Files + ~12-15 neuen Files.
- `.planning/PROJECT.md` §"Current Milestone: v1.9 Season Phases & Groups" — Milestone-Goal-Alignment.

### Prior phase contexts (read for state inheritance)
- `.planning/phases/56-model-schema-foundation/56-CONTEXT.md` §D-03 (UNIQUE on phase_teams), §D-04 (PhaseType enum), §D-05 (PhaseLayout enum) — Schema-Invariants.
- `.planning/phases/57-data-migration/57-CONTEXT.md` §D-08 (PLAYOFF format='LEAGUE' DB-default), §D-09 (`playoff.phase_id` populated), §D-11 (`PhaseTeam` derivation) — Bestand-Daten-Form.
- `.planning/phases/58-service-layer/58-CONTEXT.md` §D-01..D-03 (phaseId-canonical APIs + @Deprecated bridges), §D-04..D-06 (Combined-View standings), §D-14 (UNIQUE phaseType pre-check), §D-18 (Saison-Delete-Guard), §D-19 (PlayoffService.createPlayoff auto-creates PLAYOFF), §D-20 (PhaseTeam roster init), §D-25 (auto-sync block — wird in Phase 60 entfernt), §D-26 (MatchdayService dual-API).
- `.planning/phases/59-import-test-data/59-CONTEXT.md` §D-03 (ambiguous-tab UI in Phase 60 versprochen — D-38 macht das nicht-buildable), §D-05 (Group-Lookup via PhaseTeam-REGULAR), §D-06 (TabWarning-Records), §D-08 (resolvedGroupName auf Driver-Row-Records).

### Project conventions (binding)
- `CLAUDE.md` §"Architectural Principles" — **Keep Controllers Thin** (D-03 SeasonPhaseController-Wahl), **DTOs instead of Entities in Controllers** (PhaseForm, GroupForm, PhaseTeamForm), **No Fallback Calculations** (D-21, D-23, D-25, D-28 strikte Guards statt Cascade-Hacks), **Keep Thymeleaf Templates Lean** (server-side `combinedView`/`showBuchholz`/`showGroupColumn`-Flags statt SpEL).
- `CLAUDE.md` §"Constraints" — 82% Line-Coverage, Flyway V1+V2+V3+V4 immutable (Phase 60 fügt KEINE neue Migration hinzu — nur Phase 61 mit MIGR-06), H2 + MariaDB Kompatibilität, **No breaking changes to existing URLs/endpoints** (D-12, D-31, D-41 erfüllen das), OSIV bleibt aktiv.
- `CLAUDE.md` §"Development Approach" — TDD: Tests first, given/when/then naming. **Visual Verification with `playwright-cli`** für UI-Änderungen ist Pflicht (Desktop + Mobile).
- `CLAUDE.md` §"CSS Guidelines" — **Keine Inline-Styles auf Buttons**; admin.css-Klassen (`btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`). Bei JS-className-Setzungen neue Klassen mitpflegen.
- `CLAUDE.md` §"Conventions" §"Controller & DTO Patterns" — Form-DTOs mit `@Valid` + `BindingResult`; Flash-Attributes `successMessage`/`errorMessage`; Entities direkt im GET (OSIV aktiv).
- `CLAUDE.md` §"Language" — Documentation, Code, Comments, UI-Texts: **English** (project memory `feedback_ui_language` ist hart).
- `.planning/codebase/ARCHITECTURE.md` — three-tier MVC, Controller→Service→Repository.
- `.planning/codebase/CONVENTIONS.md` — Service-Naming, `@Service` + `@RequiredArgsConstructor` + `@Slf4j`, `BusinessRuleException` für Business-Rule-Verletzungen, `EntityNotFoundException` für fehlende Rows.
- `.planning/codebase/STRUCTURE.md` — `org.ctc.admin.controller`, `org.ctc.admin.dto`, `org.ctc.admin.service`-Layer-Mapping.
- `.planning/codebase/TESTING.md` — Mockito-first für Service-Tests; `@SpringBootTest @ActiveProfiles("dev") @Transactional` für IT (Phase 58 D-13 override).

### Existing code (read for pattern alignment)
- `src/main/java/org/ctc/admin/controller/SeasonController.java` — site of D-03 routing additions; aktuelle `detail`/`list`/`create`/`edit` flows.
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — 17 Endpoints, alle bleiben funktional (D-41/D-43); UI-seitige Anpassungen via Templates.
- `src/main/java/org/ctc/admin/controller/StandingsController.java` — kanonische Phase-Migration; D-31 URL-Strategie.
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — D-44 Conservative-Cleanup target; switcht auf `findByPhaseId`.
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — D-39/D-40 Template-Anpassungen werden hier eingebunden.
- `src/main/java/org/ctc/admin/dto/SeasonForm.java` — UI-01 Slimming target (10 Felder → 5: name, year, number, description, active).
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — D-25/D-26-Targets; UI-01 entfernt den Phase 58 D-25 Auto-Sync-Block.
- `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` — Phase 58-Service; D-21/D-22/D-23 Validierungen leben hier.
- `src/main/java/org/ctc/domain/service/PlayoffService.java` — Phase 58 D-19 createPlayoff; D-42 CTA bedient diesen Pfad.
- `src/main/java/org/ctc/domain/service/StandingsService.java` — Phase 58 D-04/D-05; D-32/D-33 Templates greifen auf TeamStanding.group + buchholz zu.
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — Phase 59 D-08-Records; D-39/D-40 Template-Konsumenten.
- `src/main/resources/templates/admin/season-detail.html` (293 Zeilen) — D-01..D-15-Rewrite-Target; behält Saison-Header (Z. 6-90 in etwa), Phase-Tabs werden eingebaut.
- `src/main/resources/templates/admin/season-form.html` (264 Zeilen) — UI-01-Slimming; entfernt Format/Scoring/Dates/Rounds/Legs/EventDuration.
- `src/main/resources/templates/admin/standings.html` (230 Zeilen) — D-29..D-36-Rewrite-Target.
- `src/main/resources/templates/admin/driver-import-preview.html` (182 Zeilen) — D-37..D-40-Erweiterung.
- `src/main/resources/templates/admin/playoff-bracket.html` (131 Zeilen) — D-43 entfernt "Add Season"-UI.
- `src/main/resources/templates/admin/layout.html` — base layout; Tab-CSS-Klassen-Erweiterung in admin.css.
- `src/main/resources/static/admin.css` — `.tabs`-Existing-Klassen + neue für 2-Ebenen-Tabs (`tabs-secondary`, `phase-tab-active`, etc.).

### New files (to be created)
- `src/main/java/org/ctc/admin/controller/SeasonPhaseController.java` — D-03 Routes `/admin/seasons/{seasonId}/phases/...`.
- `src/main/java/org/ctc/admin/controller/SeasonPhaseGroupController.java` — D-19 Routes `/admin/seasons/{sid}/phases/{pid}/groups/...`.
- `src/main/java/org/ctc/admin/dto/SeasonPhaseForm.java` — D-16/D-17/D-22 Form-DTO mit Bean-Validation.
- `src/main/java/org/ctc/admin/dto/SeasonPhaseGroupForm.java` — D-19 Form-DTO.
- `src/main/java/org/ctc/admin/dto/PhaseTeamForm.java` — D-20 Roster-Editor-DTO.
- `src/main/resources/templates/admin/season-phase-form.html` — D-16 Phase-Form-Template.
- `src/main/resources/templates/admin/season-phase-group-form.html` — D-19 Group-Form-Template (Schritt 1).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`SeasonForm`-DTO** (10 Felder): D-16/D-22 spiegelt das Naming + Lombok-Annotation-Pattern wider; `SeasonPhaseForm` folgt identisch (`@Getter @Setter @NoArgsConstructor`, `@NotBlank`/`@NotNull`).
- **`PhaseTeamRepository.findByPhaseIdAndTeamId(UUID, UUID)`** + **`findByPhaseId(UUID)`** + **`findByPhaseIdAndGroupId(UUID, UUID)`** (Phase 58 D-22) — Roster-Editor (D-20) ruft diese.
- **`SeasonPhaseService.findRegularPhase(UUID seasonId)`** (Phase 58 D-02) — D-12 Legacy-Standings-URL-Resolver.
- **`StandingsService.calculateStandings(UUID phaseId, UUID groupId)`** (Phase 58 D-04, nullable groupId für Combined) — D-30/D-32-Page-Render.
- **`StandingsService.calculateStandingsWithBuchholz(UUID phaseId, UUID groupId)`** (Phase 58 D-06) — D-33-Conditional.
- **`SeasonPhaseService.create(...)`** (Phase 58 D-14: pre-check max-1-pro-phaseType) — D-22-Validation belt.
- **`PlayoffService.createPlayoff(seasonId, ...)`** (Phase 58 D-19: auto-creates PLAYOFF-Phase) — D-42-CTA-Endpoint.
- **`MatchdayService.findByPhaseId(UUID)`** + **`findByPhaseIdAndGroupId(UUID, UUID)`** (Phase 58 D-26) — D-04-Tab-Inhalt; alte `findBySeasonId` bleibt @Deprecated bis D-44 cleant.
- **`DriverRankingService.calculateRankingForPhase(UUID phaseId)`** + **`aggregateAcrossPhases(...)`** (Phase 58 D-09) — Rankings-View (Phase 60 nicht im Scope, aber Templates-Pattern adaptiert).
- **`TabPreview.warnings`** + **`Driver-Row.resolvedGroupName`** (Phase 59 D-08) — D-39/D-40-Render-Quellen.
- **`@Slf4j @Service @RequiredArgsConstructor`** Spring-Stereotype-Combo — neue Services + Controller folgen.
- **`@Transactional(readOnly = true)`** auf alle GET-Service-Methoden, **`@Transactional`** voll auf alle Write-Methoden.
- **`BindingResult` + `@Valid`** auf Form-Endpoints — D-27.
- **Flash-Attributes** `successMessage`/`errorMessage` (CLAUDE.md §Conventions) — D-23/D-25/D-27/D-28 BusinessRule-Errors.

### Established Patterns

- **Controller-Form-Page-Pattern** (z. B. SeasonController.create/edit + matching Templates) — D-06/D-19 wendet das identisch an.
- **`record` für Service-Return-Daten** (z. B. SeasonDetailData von SeasonManagementService.getDetailData) — Planner kann ein `SeasonDetailDataV2` mit Phase-Tabs-State einführen.
- **`th:errors`-Pattern** in season-form.html / matchday-form.html für Bean-Validation — D-27.
- **`th:if="${condition}"`** für konditionales Rendering — D-32/D-33/D-40 verwenden das.
- **Flash-Attribute Redirect-Pattern** auf POST-Endpoints — `RedirectAttributes`-Injection auf jedem Save/Delete-Endpoint.
- **Spring Data magic-naming** für Custom-Finders (Phase 58 D-22) — neue Service-Methoden bleiben in dem Stil.
- **Backward-Compatible-Routing** durch Query-Param-Bridges (Phase 58 D-01 mit `seasonId`-Overloads; D-12/D-31 erweitern auf URL-Ebene).
- **`@Deprecated`-Bridges** bleiben funktional bis ihre einzigen Konsumenten weg sind — D-44 conservative cleanup, MIGR-06 (Phase 61) macht den Rest.
- **Centralized Exception Handling** via `GlobalExceptionHandler` — D-09's `EntityNotFoundException` rendert die existierende 404-Page; D-23/D-25/D-28's `BusinessRuleException` rendert Flash-Errors.

### Integration Points

- **`SeasonController`** (existiert): bekommt Routen `/{id}/phases/{phaseId}` + `/{id}/phases/{phaseId}/groups/{groupId}` ODER (Empfehlung) ein neuer `SeasonPhaseController` übernimmt diese. Plan-Ebene entscheidet.
- **`SeasonManagementService`** (existiert): D-25 entfernt den Phase 58 D-25 Auto-Sync-Block; D-26 `addTeamToSeason` muss zusätzlich zu `SeasonTeam` einen `PhaseTeam`-Row für die REGULAR-Phase mit `group=NULL` einfügen (atomisch in derselben Transaction).
- **`SeasonPhaseService`** (Phase 58 vorhanden): bekommt zusätzliche Update-/Delete-Methoden + Validierungs-Guards (D-21, D-22, D-23). `SeasonPhaseGroupService` (oder Methoden auf SeasonPhaseService) für D-24/D-28.
- **`StandingsController`** (existiert): D-31 URL-Schema ergänzt; Resolver-Logik für `?season=` legacy → REGULAR-Phase. `combinedView`/`showBuchholz`/`showGroupColumn`-Server-Flags an Template.
- **`PlayoffController`** (17 Endpoints, existiert): D-41 keine Route-Änderung; `playoff.getPhase()` server-side resolved. UI-Templates (playoff-bracket.html, playoff-form.html, etc.) entfernen Add-Season/Remove-Season-Buttons (D-43).
- **`DriverSheetImportController` + `driver-import-preview.html`**: D-39/D-40 lesen `tabPreview.warnings` und `row.resolvedGroupName`.
- **`MatchdayController`**: D-44 cleanup — switcht auf `findByPhaseId`-kanonische Methoden; `findBySeasonId`-Overload-Service-Method wird entfernt wenn nur MatchdayController/SeasonController davon abhing.
- **`RaceController` / `RaceLineupController`**: möglicherweise indirekt durch Matchday-Phase-Kontext betroffen — Planner prüft.
- **`SiteGeneratorService`** (Phase 58 D-23 schon umgestellt): nicht in Phase 60 angefasst.
- **`TestDataService` + `DevDataSeeder`** (Phase 59 abgeschlossen): nicht in Phase 60 angefasst, aber neue Phase/Group-CRUD-Forms müssen mit den von Phase 59 D-09 erzeugten 2023-GROUPS-Daten visuell funktionieren — playwright-cli-Verifikation prüft das.
- **`admin.css`**: neue Klassen für 2-Ebenen-Tabs (`tabs-primary`, `tabs-secondary`, `tab-active`, etc.). CLAUDE.md "No Inline Styles" gilt strikt.

</code_context>

<deferred>
## Deferred Ideas

- **Manueller Saison-Selector-Dropdown für ambigue Tabs** (Phase 59 D-03 hat das in Phase 60 versprochen) — explizit nicht gebaut. Reale Sheets enthalten nur volle Saisons (project memory). Falls jemand künftig Group-Workaround-Sheets importieren will, kann eine Folge-Phase die Dropdown-UI nachreichen. Backend-Pfad bleibt erhalten.
- **`MIGR-06` Drop legacy `Season`-Spalten + `playoff_seasons` M:N-Tabelle** — Phase 61 (Cleanup & Quality Gate). Phase 60 lässt diese intakt; Conservative-Bridge-Cleanup (D-44) berührt nur die Service-Overloads, deren Controller-Konsumenten hier wegfallen.
- **`PLAYOFF-FUT-01` sub-group-aware Playoff-Brackets** — future milestone. Modell unterstützt's via Phase pro Gruppe, Default bleibt gemeinsamer Bracket pro PLAYOFF-Phase.
- **`CONSOL-FUT-01` Saison-Konsolidierung-UI** (zwei Bestandssaisons mit `(year, number)`-Duplikaten zu einer GROUPS-Saison zusammenführen) — future milestone, Phase 59 hatte's schon dorthin verschoben.
- **`IMPORT-FUT-01` Phase-/Group-Override-Spalte im Sheet** — future milestone.
- **Drag-and-Drop für Tab-/Group-Reorder** — D-10/D-24 entscheiden für auto-set + manuelle Form-Anpassung. Drag-and-Drop wäre nice-to-have, aber JS-Komplexität rechtfertigt es nicht.
- **Mobile-Dropdown-Navigation** statt Horizontal-Scroll — D-11. Falls Mobile-User zu viele Phasen haben (>6), kann eine Folge-Phase Dropdown nachreichen.
- **Drag-and-Drop für PhaseTeam-Roster-Editor** — D-20 entscheidet für Multi-Select-Checkbox-Liste mit Group-Dropdown. Drag-and-Drop wäre visuell ansprechend, aber `admin.js` müsste massiv ausgebaut werden.
- **"Reset Phase"-Action** für format/layout-Wechsel mit existierenden Daten — D-21 entscheidet sich für strikten Server-Side-Pre-Check + Warning-Banner. "Reset" als Option (löscht alle Phase-Daten) wäre denkbar, aber gefährlich; deferred.
- **Soft-Delete für Phasen** (statt hard delete) — D-23. Fügt Spalten-Komplexität hinzu, ist nicht im Modell vorgesehen.
- **Active-Phase-Visualisierung basierend auf `startDate`/`endDate`** — D-15. Default-Werte oft leer, würde inkonsistent wirken.
- **E2E Playwright-Test für GROUPS-Saison-Workflow** — Phase 61 (QUAL-02). Phase 60 deckt mit Mockito + Service-IT + manueller `playwright-cli`-Visual-Verifikation ab.
- **Aggressive Service-Bridge-Cleanup** (alle `@Deprecated`-Overloads in Phase 60 entfernen) — D-44 entscheidet konservativ. Phase 61 (MIGR-06) macht den Endschnitt.
- **Cascade-Delete für Phase / Group / SeasonTeam mit Confirm-Dialog** — D-23/D-25/D-28 entscheiden für strict guards. Cascade ist gefährlich + nicht im "No Fallback Calculations"-Sinn.
- **Optimistic Locking auf Phase-Edit** (concurrent-edit-Protection) — Claude's Discretion; falls in der Praxis kein Issue, ignoriert. Sonst Standard `@Version`-Annotation.

</deferred>

---

*Phase: 60-admin-ui*
*Context gathered: 2026-04-29*
