# Phase 60: Admin UI - Research

**Researched:** 2026-04-30
**Domain:** Spring Boot 4.x + Thymeleaf admin-UI cutover to Saison/Phase/Group-Modell (UI-01..UI-07)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

44 decisions D-01..D-44 sind in `60-CONTEXT.md` festgeschrieben — Auszug nach Block:

**Saison-Detail-Tabs (UI-01, UI-02):**
- D-01 Tabs nur für existierende Phasen + ein einzelner "+ Add Phase"-CTA. Keine Placeholder.
- D-02 Layout = Saison-Header oben (Stamm-Daten + Teams + Cars/Tracks) + Phase-Tabs darunter.
- D-03 Routing = `/admin/seasons/{id}/phases/{phaseId}` und `/admin/seasons/{id}/phases/{phaseId}/groups/{groupId}`. `/admin/seasons/{id}` redirected auf REGULAR.
- D-04 Inhalt pro Phase-Tab linear gestapelt (Roster → Matchdays → Standings; PLAYOFF: Roster → Bracket → Seeds), Anker-Links.
- D-05 Tab-Beschriftung = `phase.label` falls non-blank, sonst phaseType-Default ("Regular Season"/"Playoff"/"Placement").
- D-06 "+ Add Phase"-CTA = Navigation zu eigener Form-Page `/admin/seasons/{id}/phases/new`. Kein Modal.
- D-07 Saison-Header trägt nur Saison-weite Aktionen (Edit Saison, Delete Saison). Phase-spezifische Aktionen leben im Phase-Tab.
- D-08 Empty-State bei fehlender REGULAR-Phase = Card mit "+ Add REGULAR Phase"-CTA. Defensiv, nicht: Exception.
- D-09 URL-Safety bei Phase/Saison-Mismatch = strikter 404 via `EntityNotFoundException` (IDOR-Schutz).
- D-10 `SeasonPhase.sortIndex` = auto-set (REGULAR=0, PLAYOFF=10, PLACEMENT=20), nicht UI-änderbar.
- D-11 Mobile = Horizontal-Scroll-Tabs (`overflow-x: auto`). Keine Dropdown-Variante.
- D-12 `/admin/standings?season={id}` bleibt funktional (Auto-Resolve zur REGULAR-Phase) — keine 302-Redirects.
- D-13 Page-Title-Pattern = `"{season.name} — {phase.label}"` (Browser-Tab + h1).
- D-14 Keine Breadcrumbs eingeführt. Bestehender `← Back to Seasons` reicht.
- D-15 `Season.active`-Badge bleibt saison-weit. Keine Active-Phase-Visualisierung über Datum.

**Phase + Group Form-Muster (UI-03, UI-04):**
- D-16 Phase-Form = volle Form (phaseType, layout, format, raceScoring, matchScoring, dates, totalRounds, legs, eventDurationMinutes, label).
- D-17 Form-Defaults bei "+ Add Phase" = aus REGULAR-Phase kopieren; Fallback Application-weite Defaults.
- D-18 GROUPS-Layout-Phase wird mit 0 Groups angelegt — Group-Anlage danach via separater Form.
- D-19 Group-Form = Two-Step: Schritt 1 Name + sortIndex, Schritt 2 Roster-Editor.
- D-20 Roster-Widget = Multi-Select-Checkbox-Liste mit Group-Dropdown pro Team. Kein Drag-and-Drop.
- D-21 Edit-Phase-Form = alle Felder editierbar; format/layout-Wechsel mit Server-Side-Pre-Check + Warning-Banner bei Datenkonflikt.
- D-22 Validation = `@NotNull` auf phaseType/layout/format. Optional: dates/rounds/legs/eventDuration/label. Layout-Format-Kompatibilität serverseitig.
- D-23 Delete-Phase = strict guard via `SeasonPhaseService.delete()` → `BusinessRuleException` bei Matchdays/Playoffs/PhaseTeams.
- D-24 `SeasonPhaseGroup.sortIndex` = auto-increment (max+1) beim Anlegen, manuell editierbar.
- D-25 SeasonTeam-Delete = strict guard wenn `PhaseTeam`-Rows existieren.
- D-26 SeasonTeam-Add = atomisch: SeasonTeam + REGULAR-PhaseTeam (group=NULL) in einer Transaction.
- D-27 Form-Errors = Field-Level mit `BindingResult` + Flash-Error-Banner für `BusinessRuleException`.
- D-28 Delete-Group = strict guard wenn Group Teams oder Matchdays enthält.

**Standings-UI + URL-Strategie (UI-05):**
- D-29 Selector-Stil = Phase-Tabs + Group-Sub-Tabs (konsistent mit Saison-Detail).
- D-30 Default-View für GROUPS = Combined-View (alle Groups flach mit Group-Badge-Spalte).
- D-31 URL = `/admin/standings?phase={phaseId}&group={groupId}` mit Legacy-Bridge `?season={seasonId}`.
- D-32 Combined hat Group-Spalte, Per-Group hat sie nicht. Server-Flag `combinedView` ans Template.
- D-33 Buchholz-Spalte = nur Per-Group bei Swiss-Format-Phase. Server-Flag `showBuchholz`.
- D-34 Saison-Dropdown bleibt oben. Casual-Browsing-UX bleibt erhalten.
- D-35 Page-Title = h1 "Standings"; Saison-Name + Phase-Name als Sub-Header.
- D-36 Empty-State bei keinem Race-Result = Tabelle mit allen Teams + 0-Punkten + Hinweis-Banner.

**Driver-Import-Preview + Playoff-Cutover (UI-06, UI-07):**
- D-37 Tab-Label-Display = raw Sheet-Tab-Name 1:1 übernehmen.
- D-38 Manueller Saison-Selector-Dropdown für ambigue Tabs = NICHT gebaut. Plain-Banner reicht.
- D-39 TabWarning-Rendering = Banner oben + Inline-Badge auf jeder betroffenen Driver-Zeile.
- D-40 Group-Spalte in Driver-Preview = neue Spalte (sichtbar nur bei GROUPS-Layout). Server-Flag `showGroupColumn`.
- D-41 Playoff-URL `/admin/playoffs/{id}` bleibt unverändert. Server-side Phase-Resolution.
- D-42 "+ Create Playoff Phase"-CTA wandert auf Saison-Detail als "+ Add Phase" mit `phaseType=PLAYOFF`. `/admin/playoffs/new` bleibt funktional.
- D-43 Legacy-Endpoints `/admin/playoffs/{id}/add-season` + `/remove-season` = Backend bleibt, UI verbirgt sie.
- D-44 `@Deprecated`-Service-Bridge-Cleanup = Conservative — nur Overloads entfernen, deren Caller in Phase 60 wegfallen.

### Claude's Discretion

- Aufteilung Sub-Controller (`SeasonPhaseController` vs. SeasonController-Routen). Empfehlung: separater `SeasonPhaseController` + `SeasonPhaseGroupController` analog MatchdayController-Struktur.
- Detaillierte CSS-Klassen-Namen für Tab-Implementierung. CLAUDE.md "No Inline Styles" gilt strikt.
- Templates-Dateinamen (`season-phase-form.html`, `season-phase-group-form.html`); season-detail.html wird massiv erweitert, kein neues Template dafür.
- `SeasonPhaseForm`-DTO Feldnamen + Validierungs-Annotation-Details (folgt SeasonForm-Konvention).
- Wave-Plan-Split (Empfehlung: 4 Waves siehe unten).
- Test-Strategie pro UI-Anforderung (Mockito + Service-IT + playwright-cli).
- Optimistic Locking auf Phase-Edit — falls in Praxis kein Issue, ignoriert.

### Deferred Ideas (OUT OF SCOPE)

- Manueller Saison-Selector-Dropdown für ambigue Tabs (Phase 59 D-03 versprach es) — explizit nicht gebaut.
- `MIGR-06` Drop legacy Season-Spalten + `playoff_seasons` M:N-Tabelle — Phase 61.
- Aggressive `@Deprecated`-Service-Bridge-Cleanup — Phase 60 conservative; Phase 61 macht den Endschnitt.
- E2E Playwright-Test für GROUPS-Saison-Workflow — Phase 61 (QUAL-02).
- Drag-and-Drop für Tab-/Group-Reorder — D-10/D-24 entscheiden für auto-set.
- `PLAYOFF-FUT-01` sub-group-aware Playoff-Brackets — future milestone.
- `CONSOL-FUT-01` Saison-Konsolidierung-UI — future milestone.
- `IMPORT-FUT-01` Phase-/Group-Override-Spalte im Sheet — future milestone.
- Mobile-Dropdown-Navigation — D-11.
- Drag-and-Drop für PhaseTeam-Roster-Editor — D-20.
- "Reset Phase"-Action für format/layout-Wechsel — D-21.
- Soft-Delete für Phasen — D-23.
- Active-Phase-Visualisierung basierend auf Datum — D-15.
- Cascade-Delete mit Confirm-Dialog — D-23/D-25/D-28 strict guards.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **UI-01** | Saison-Form schlanker (year/number/name/description/active); Format/Scoring/Dates entfallen aus dieser Form | SeasonForm-Slimming (10→5 Felder), SeasonController.save-Signatur reduziert, SeasonManagementService.save: D-25 Auto-Sync-Block entfernen, season-form.html stark schrumpfen. Siehe "Implementation Approach UI-01". |
| **UI-02** | Saison-Detail mit Phasen-Tabs; bei GROUPS-Phase zweite Tab-Ebene pro Gruppe (Roster, Matchdays, Standings je Tab) | season-detail.html komplett umgebaut auf Two-Row-Tabs. Neuer `SeasonPhaseController` mit GET-Routen. `tab-btn`/`tab-active`/`tabs-secondary` CSS-Klassen wiederverwenden + erweitern. Server-Flags statt SpEL. |
| **UI-03** | Neue Phase-Form für `SeasonPhase`-CRUD (Typ, Layout, Format, Scoring, Zeitraum, Rounds, Legs) | Neue `SeasonPhaseForm`-DTO, neues `season-phase-form.html` Template. `SeasonPhaseService.create/update/delete`. D-22 Bean-Validation. |
| **UI-04** | Neue Group-Form für `SeasonPhaseGroup`-CRUD inkl. Team-Zuordnung via `PhaseTeam` | Neue `SeasonPhaseGroupForm`-DTO + `PhaseTeamForm`-DTO, `season-phase-group-form.html`, Two-Step-Workflow (D-19). `SeasonPhaseService.createGroup/updateGroup/deleteGroup` + neue `assignTeamsToPhase(...)`-Bulk-Methode. |
| **UI-05** | Standings-UI mit Phase-/Group-Auswahl + Combined-View-Tab über Sub-Gruppen | StandingsController phaseId-canonical: `?phase=&group=`. Legacy `?season=` bleibt. standings.html komplett refactored mit Phase-Tabs + Group-Sub-Tabs + `combinedView`/`showBuchholz`/`showGroupColumn`-Flags. |
| **UI-06** | Driver-Import-Preview-Template zeigt eindeutige Saison-Zuordnung + Warnungen für unzugeordnete Teams | driver-import-preview.html erweitert: Tab-Header zeigt raw Tab-Name (D-37). Tab-Warning-Banner bereits vorhanden, Inline-Badge "⚠ No group" auf Driver-Zeilen ergänzen, Group-Spalte konditional bei `showGroupColumn`. |
| **UI-07** | Playoff-UI auf PLAYOFF-Phase umgestellt (statt Saison) | PlayoffController bleibt strukturell unverändert (D-41). playoff-bracket.html: Add-Season-/Remove-Season-Buttons entfernen (D-43). PlayoffService.createPlayoff bleibt API-Default. Saison-Detail-Page rendert "+ Add Phase" für PLAYOFF-Auto-Create (D-42). |
</phase_requirements>

---

## Summary

Phase 60 ist eine **Pure-UI-Cutover-Phase**: Backend-Modell und -Services sind seit Phase 56-59 vollständig vorbereitet, jetzt werden Controllers und Templates auf das neue Saison/Phase/Group-Modell umgeschrieben. Der Refactor umfasst sieben UI-Anforderungen (UI-01..UI-07) mit insgesamt ~7-9 modifizierten Templates, ~5 angepassten Controllers, 2 neuen Controllers, 3 neuen DTOs, 2 neuen Templates und einem konservativen Service-Bridge-Cleanup.

Die größte technische Herausforderung ist die **Two-Row-Tab-Struktur** auf Saison-Detail- und Standings-Pages: Row 1 = Phase-Tabs (REGULAR/PLAYOFF/PLACEMENT, nur existierende), Row 2 = Group-Sub-Tabs (nur bei GROUPS-Layout, plus "Combined"-Tab). Beide Reihen müssen mobile-tauglich (`overflow-x: auto`) und konsistent zwischen `/admin/seasons/{id}/phases/...` und `/admin/standings` sein. Die existierenden CSS-Klassen `tab-btn`, `tab-active`, `tab-nav` aus `admin.css` Z. 1038-1056 + 1740-1748 sind die Basis; eine zweite Klasse (z. B. `tabs-secondary`) wird für Row 2 eingeführt.

Der zweite Hot-Spot ist die **atomare SeasonTeam ↔ PhaseTeam-Synchronisation** (D-26): Wenn ein Team zu einer Saison hinzugefügt wird, muss in derselben Transaction auch ein `PhaseTeam`-Row für die REGULAR-Phase mit `group=NULL` angelegt werden. Spiegelbildlich blockiert D-25 das Entfernen eines Teams aus der Saison, solange `PhaseTeam`-Rows existieren. Diese Service-Layer-Ergänzung in `SeasonManagementService.addTeamToSeason`/`removeTeamFromSeason` ist eine **Verhaltensänderung gegenüber Phase 58** und muss explizit getestet werden.

Drittens muss der **Phase 58 D-25 Auto-Sync-Block** in `SeasonManagementService.save` (Zeilen 200-222 dort) zusammen mit UI-01 entfernt werden — der Block schreibt heute Format/Scoring/Dates auf die REGULAR-Phase durch; ab Phase 60 verwaltet die Phase-Form diese Felder direkt. Existierende Tests für diesen Auto-Sync (`SeasonManagementServiceTest`) müssen umformuliert werden.

**Primary recommendation:** Vier Waves: (1) Backend Phase + Group CRUD-Controller/DTOs, (2) Saison-Detail-Page-Umbau (UI-01/02), (3) Standings + Importer + Playoff-Cutover (UI-05/06/07), (4) Conservative `@Deprecated`-Cleanup + finale `playwright-cli`-Verifikation. Tests pro Wave inkrementell ergänzen, finaler `./mvnw verify -Pe2e` am Ende.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Saison-Detail-Page-Render mit Phase-Tabs | Frontend Server (Thymeleaf) | API/Backend (`SeasonPhaseService`) | Server-side Rendering — Tab-State + Phase-Tabs werden im Controller berechnet, nicht client-side. |
| Phase-CRUD (Create/Edit/Delete) | API/Backend (Service+Controller) | Frontend Server (form-template) | `SeasonPhaseService` ist kanonischer Eigentümer; Controller dünn (Thin-Controller-Prinzip). |
| Group-CRUD inkl. Roster | API/Backend (Service+Controller) | Frontend Server (form-template) | `SeasonPhaseService.createGroup/assignTeamToPhase`; UI-Routing über Two-Step. |
| Standings-Calculation per Phase/Group | API/Backend (`StandingsService`) | Frontend Server (template) | Phase 58 D-04 schon kanonisch; Controller ruft `calculateStandings(phaseId, groupId)`. |
| Roster-Multi-Select-Editor | Frontend Server (form-template) | API/Backend (`PhaseTeamForm`-binding) | Browser zeigt Checkboxen + Group-Dropdown; Spring-Binding macht den Rest. Kein JS-Heavy-Lifting. |
| Tab-Navigation (Two-Row) | Frontend Server (template + CSS) | Browser (overflow-x scroll) | Server rendert Tabs; Browser scrollt horizontal. Kein JS für Tab-Wechsel — Server-Routing. |
| Driver-Import-Group-Spalte | Frontend Server (template) | API/Backend (`TabPreview.warnings`+`row.resolvedGroupName`) | Phase 59 D-08 liefert die Daten; Template rendert konditional. |
| Legacy-URL-Bridge `?season={id}` | API/Backend (Controller) | — | Server löst zur REGULAR-Phase auf; keine 302-Redirects. |
| Playoff-Phase-Resolution | API/Backend (`PlayoffController`+`Playoff.getPhase()`) | — | Bestehende URL `/admin/playoffs/{id}` ruft `playoff.getPhase()` auf — kein Routing-Refactor. |

**Why this matters:** Alle UI-Verantwortungen liegen klar im Frontend-Server (Thymeleaf SSR mit OSIV) — kein Browser-State-Management, kein JS-Tab-Switching. Backend-Tier-Verantwortung fokussiert auf Service-CRUD-Routen + Validierungs-Guards. Diese Aufteilung minimiert Komplexität und harmoniert mit dem CLAUDE.md "Keep Controllers Thin" + "Keep Thymeleaf Templates Lean"-Prinzip.

---

## Standard Stack

### Core (already locked, no change)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.x | Application framework | Existing stack — pom.xml |
| Thymeleaf | (Spring Boot starter version) | Server-side templating | Existing standard, OSIV-friendly |
| Lombok | (Spring Boot managed) | Boilerplate reduction (@Getter/@Setter/@RequiredArgsConstructor/@Slf4j) | Used everywhere in codebase |
| jakarta.validation | (Spring Boot managed) | Bean-Validation (@NotNull, @NotBlank) | SeasonForm-Pattern |
| JUnit 5 | (Spring Boot managed) | Unit + Integration tests | Test framework |
| Mockito | (Spring Boot managed) | Mocking | Service-test-pattern |
| Playwright | compile-scope (graphics) | Visual verification + E2E | `playwright-cli` MANDATORY für UI-Änderungen |

**No new dependencies needed for Phase 60.** Alle benötigten Bibliotheken sind bereits vorhanden.

### Existing Pattern Inventory (for replication)

| Asset | Location | Purpose for Phase 60 |
|-------|----------|----------------------|
| Tab-CSS (`tab-btn`, `tab-active`, `tab-nav`) | `static/admin/css/admin.css` Z. 1038-1056, 1740-1748 | Basis für Two-Row-Tabs (UI-02, UI-05) |
| `editor-tab-bar` Pattern | `static/admin/css/admin.css` Z. 1781-1786, `template-editors.html` Z. 11-42 | Vorbild für `tabs-primary` / `tabs-secondary` Container |
| Form+BindingResult Pattern | `season-form.html`, `matchday-form.html` (40 Zeilen, kompakt) | UI-03 Phase-Form, UI-04 Group-Form |
| Flash-Attribute Redirect | SeasonController.save (`successMessage`/`errorMessage`) | Alle Phase/Group-Save/Delete-Endpoints |
| Modal-Overlay Pattern | season-detail.html Z. 99-186 (Edit/Replace-Modals) | Falls Phase 60 Modals nötig — D-06 sagt NEIN, also nicht erforderlich |
| Empty-State Pattern | `empty-state` / `empty-state--compact` (überall) | D-08 fehlende REGULAR-Phase, D-36 Standings ohne Results |
| Card-Stack Pattern | season-detail.html, alle Detail-Pages | D-04 lineare Sections im Phase-Tab |
| Searchable-Dropdown JS | `static/admin/js/searchable-dropdown.js` | (Optional) für Roster-Editor mit vielen Teams |

**Verification:** Bei Bedarf prüfen, ob `editor-tab-bar` und `tab-nav` denselben visuellen Effekt erzeugen — die haben unterschiedliche Klassen-Namen, könnten aber semantisch redundant sein. Empfehlung: konsequent die `tab-btn`+`tab-active`-Variante (verwendet in template-editors.html) nehmen, weil sie die `?tab=`-Query-Param-Konvention bedient und gut zu D-03 Server-Routing passt.

---

## Architecture Patterns

### System Architecture Diagram

```
                               Browser (HTTP GET /admin/seasons/{id}/phases/{phaseId})
                                              │
                                              ▼
                          ┌─────────────────────────────────────┐
                          │       SeasonPhaseController          │  (NEW)
                          │  resolves season → phase             │
                          │  validates season.id == phase.season │  (D-09 IDOR)
                          │  populates Model with:                │
                          │   - season, phase, allPhases          │
                          │   - phaseTeams (roster), groups       │
                          │   - matchdays(phaseId,groupId)        │
                          │   - tabState flags (isLeague/Groups)  │
                          └───────────────┬─────────────────────┘
                                          │
                       ┌──────────────────┼─────────────────────┐
                       │                  │                     │
                       ▼                  ▼                     ▼
          SeasonManagementService  SeasonPhaseService   MatchdayService
          (Saison-Stamm + Teams)  (CRUD + roster init)  (per phaseId)
                                          │
                                          ▼
                            ┌────────────────────────────┐
                            │  SeasonPhaseRepository      │
                            │  SeasonPhaseGroupRepository │
                            │  PhaseTeamRepository        │
                            │  MatchdayRepository         │
                            │  PlayoffRepository          │
                            └────────────────────────────┘
                                          │
                                          ▼  (OSIV)
                          ┌─────────────────────────────────────┐
                          │   season-detail.html (Thymeleaf)     │
                          │  Saison-Header  ←── season           │
                          │  Phase-Tabs     ←── allPhases        │  D-01,D-05
                          │  Group-Sub-Tabs ←── phase.groups     │  D-29
                          │  Sections (Roster→Matchdays→Standings) D-04
                          │   • Roster   ← phaseTeams            │
                          │   • Matchdays ← matchdays            │
                          │   • Standings ← StandingsService     │
                          └─────────────────────────────────────┘

       Standings flow (UI-05):
       Browser GET /admin/standings?phase={phaseId}&group={groupId}
       (or legacy ?season={seasonId} → resolve to REGULAR phase)
                       │
                       ▼
       StandingsController → StandingsService.calculateStandings(phaseId, groupId)
                       │           (Phase 58 D-04 already canonical)
                       ▼
       standings.html: Phase-Tabs (Row 1) + Group-Sub-Tabs (Row 2)
                       + combinedView/showBuchholz/showGroupColumn flags

       Driver Import flow (UI-06):
       DriverSheetImportController → driver-import-preview.html
       (TabPreview already carries warnings + resolvedGroupName from Phase 59 D-08)

       Playoff flow (UI-07):
       Existing /admin/playoffs/{id} URL unchanged.
       PlayoffController.list/detail → playoff-bracket.html
       (Add-Season UI removed per D-43; backend endpoints stay)
```

### Recommended Project Structure (Delta)

```
src/main/java/org/ctc/admin/
├── controller/
│   ├── SeasonController.java              # MODIFY (slim: drop scoring/dates/format params)
│   ├── SeasonPhaseController.java         # NEW (D-03, D-06, D-16, D-21, D-23)
│   ├── SeasonPhaseGroupController.java    # NEW (D-19, D-20, D-24, D-28)
│   ├── StandingsController.java           # MODIFY (D-12 legacy bridge + D-31 phase-canonical)
│   ├── PlayoffController.java             # MODIFY (server-side phase resolution; D-43 hide UI buttons)
│   ├── MatchdayController.java            # MODIFY (D-44 switch to findByPhaseId)
│   └── DriverSheetImportController.java   # NO CHANGE (template carries the load)
├── dto/
│   ├── SeasonForm.java                    # MODIFY (slim: remove format/scoring/dates/rounds/legs/eventDuration)
│   ├── SeasonPhaseForm.java               # NEW (D-16 phase fields)
│   ├── SeasonPhaseGroupForm.java          # NEW (D-19 name + sortIndex)
│   └── PhaseTeamForm.java                 # NEW (D-20 multi-select assignment)

src/main/resources/templates/admin/
├── season-detail.html                     # REWRITE (Two-Row Tabs, Phase-Sections per D-02/D-04)
├── season-form.html                       # SLIM (remove format/scoring/dates/rounds/legs/eventDuration blocks)
├── season-phase-form.html                 # NEW (D-16 form)
├── season-phase-group-form.html           # NEW (D-19 step 1)
├── standings.html                         # REWRITE (Two-Row Tabs, Combined-View, server flags)
├── driver-import-preview.html             # MODIFY (D-39 inline badge, D-40 group column conditional, D-37 raw tab name)
└── playoff-bracket.html                   # MODIFY (D-43 hide Add-/Remove-Season buttons)

src/main/resources/static/admin/css/
└── admin.css                              # APPEND (tabs-secondary, group-sub-tab, mobile horizontal scroll)
```

### Pattern 1: Two-Row Tab Server-Side-Rendered

**What:** Server berechnet welche Phase-Tabs existieren + welche Group-Sub-Tabs zu rendern sind. Kein JS-Tab-Switching — jeder Tab ist ein eigener Link mit eigener URL.

**When to use:** Saison-Detail (UI-02), Standings (UI-05).

**Template-Skelett (server-side):**
```html
<!-- Tab Row 1: Phase-Tabs (D-01 nur existierende + 1 CTA) -->
<div class="tab-nav" role="tablist" aria-label="Phase tabs">
    <a th:each="p : ${allPhases}"
       th:href="@{/admin/seasons/{sid}/phases/{pid}(sid=${season.id}, pid=${p.id})}"
       th:classappend="${p.id == phase.id ? 'tab-active' : ''}"
       class="tab-btn"
       th:text="${p.label != null and !p.label.isBlank() ? p.label : p.phaseType.displayName}">Tab</a>
    <a th:href="@{/admin/seasons/{id}/phases/new(id=${season.id})}"
       class="tab-btn tab-add">+ Add Phase</a>
</div>

<!-- Tab Row 2: Group-Sub-Tabs (D-29 nur bei GROUPS-Layout) -->
<div th:if="${phase.layout.name() == 'GROUPS'}"
     class="tab-nav tabs-secondary" role="tablist" aria-label="Group sub-tabs">
    <a th:href="@{/admin/seasons/{sid}/phases/{pid}(sid=${season.id}, pid=${phase.id})}"
       th:classappend="${selectedGroupId == null ? 'tab-active' : ''}"
       class="tab-btn">Combined</a>
    <a th:each="g : ${phase.groups}"
       th:href="@{/admin/seasons/{sid}/phases/{pid}/groups/{gid}(sid=${season.id}, pid=${phase.id}, gid=${g.id})}"
       th:classappend="${selectedGroupId == g.id ? 'tab-active' : ''}"
       class="tab-btn"
       th:text="${g.name}">Group A</a>
</div>
```

**CSS-Append (admin.css):**
```css
/* Two-row tabs: secondary row */
.tabs-secondary {
    margin-top: 0;
    border-bottom: 1px solid var(--border);
    background: var(--bg-card);
}
.tabs-secondary .tab-btn {
    padding: 8px 16px;
    font-size: 13px;
}

/* Mobile: horizontal scroll for both tab rows (D-11) */
@media (max-width: 768px) {
    .tab-nav {
        overflow-x: auto;
        flex-wrap: nowrap;
        -webkit-overflow-scrolling: touch;
    }
    .tab-nav .tab-btn {
        flex-shrink: 0;
    }
}
```

**Anti-Pattern to avoid:** Kein JS-Tab-Switching. Server-Routing pro Tab. Konsistent mit `template-editors.html` Pattern. Keine Inline-Styles auf den Tab-Buttons (CLAUDE.md hart).

### Pattern 2: Phase-Form mit Defaults aus REGULAR-Phase (D-17)

```java
// In SeasonPhaseController.create(seasonId, model)
@GetMapping("/admin/seasons/{seasonId}/phases/new")
public String create(@PathVariable UUID seasonId,
                     @RequestParam(required = false) PhaseType type,
                     Model model) {
    var season = seasonManagementService.findById(seasonId);
    var form = new SeasonPhaseForm();
    form.setPhaseType(type);  // pre-selected if linked from "+ Add PLAYOFF Phase"

    // D-17: Copy defaults from REGULAR phase if it exists
    seasonPhaseService.findByType(seasonId, PhaseType.REGULAR).ifPresent(regular -> {
        form.setFormat(regular.getFormat());
        form.setRaceScoringId(regular.getRaceScoring().getId());
        form.setMatchScoringId(regular.getMatchScoring().getId());
        form.setLegs(regular.getLegs());
        form.setEventDurationMinutes(regular.getEventDurationMinutes());
        // dates and totalRounds intentionally NOT copied
    });

    model.addAttribute("seasonPhaseForm", form);
    model.addAttribute("season", season);
    addScoringLists(model);
    return "admin/season-phase-form";
}
```

### Pattern 3: Roster-Editor mit Multi-Select + Group-Dropdown (D-20)

**Template (season-phase-group-form.html, Schritt 2 oder Phase-Detail-Section):**
```html
<form th:action="@{/admin/seasons/{sid}/phases/{pid}/roster(sid=${season.id}, pid=${phase.id})}"
      th:object="${phaseTeamForm}" method="post">
    <table>
        <thead><tr><th>Include</th><th>Team</th><th th:if="${phase.layout.name() == 'GROUPS'}">Group</th></tr></thead>
        <tbody>
            <tr th:each="st, iter : ${season.seasonTeams}">
                <td><input type="checkbox"
                           th:name="'assignments[' + ${iter.index} + '].included'"
                           th:checked="${assignedTeamIds.contains(st.team.id)}"></td>
                <td>
                    <input type="hidden"
                           th:name="'assignments[' + ${iter.index} + '].teamId'"
                           th:value="${st.team.id}">
                    <span th:text="${st.team.shortName + ' — ' + st.team.name}"></span>
                </td>
                <td th:if="${phase.layout.name() == 'GROUPS'}">
                    <select th:name="'assignments[' + ${iter.index} + '].groupId'">
                        <option value="">— No Group —</option>
                        <option th:each="g : ${phase.groups}"
                                th:value="${g.id}"
                                th:text="${g.name}"
                                th:selected="${assignedGroupByTeam.get(st.team.id) == g.id}"></option>
                    </select>
                </td>
            </tr>
        </tbody>
    </table>
    <div class="actions mt-md">
        <button type="submit" class="btn btn-primary">Save Roster</button>
        <a th:href="@{/admin/seasons/{sid}/phases/{pid}(sid=${season.id}, pid=${phase.id})}"
           class="btn btn-secondary">Cancel</a>
    </div>
</form>
```

**DTO-Pattern für Spring-Binding (Indexed-Properties):**
```java
@Getter @Setter @NoArgsConstructor
public class PhaseTeamForm {
    private List<Assignment> assignments = new ArrayList<>();

    @Getter @Setter @NoArgsConstructor
    public static class Assignment {
        private UUID teamId;
        private boolean included;
        private UUID groupId; // nullable
    }
}
```

> **Pitfall:** Spring-Indexed-Properties brauchen einen vorinitialisierten `List`. Bei einer leeren Liste throwt Spring `IndexOutOfBoundsException` beim Auto-Grow. `AutoPopulatingList` aus `org.springframework.util` ist der robuste Workaround — siehe Pitfall-Sektion.

### Anti-Patterns to Avoid

- **SpEL-Projection im Template** (z. B. `${season.phases.?[phaseType.name() == 'REGULAR']}`) — CLAUDE.md "Keep Thymeleaf Templates Lean": Server-Flag auf das Modell legen.
- **Inline-Styles auf Tab-Buttons** (`style="..."`) — CSS-Klasse erweitern.
- **Cascade-Delete mit Confirm-Dialog** — D-23/D-25/D-28 wählen strict guards.
- **Drag-and-Drop für Tabs/Groups/Roster** — D-10/D-20/D-24 entscheiden gegen JS.
- **Alle alternativen Endpoints für rückwärtskompatible URLs entfernen** — D-12, D-41, D-43 entscheiden für Backward-Compat.
- **Phase-Modell direkt als `@ModelAttribute` POST-binden** — Mass-Assignment-Risiko (CLAUDE.md). Immer DTO-Form.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Tab-Switching mit JS | DOM-Manipulation `display:none/block` | Server-Routing pro Tab (Link-basiert) | OSIV ist aktiv; SSR ist die Codebase-Konvention. JS-Tab-Switching wäre Pattern-Drift gegen `template-editors.html`. |
| Phase-Saison-Mismatch-Validation | Manuelle if-Checks in jedem Endpoint | Re-use existing `EntityNotFoundException` + `phase.getSeason().getId().equals(seasonId)` Guard, GlobalExceptionHandler rendert 404 | D-09; bereits etabliert in MatchupDetail-Controller-Logik. |
| URL-Param-Bridge `?season=` → `?phase=` | Custom-Filter / WebMvcInterceptor | Akzeptiere beide Params im StandingsController, resolve serverseitig zur REGULAR-Phase via `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)` | D-12 minimal-invasiv. Phase 58 D-25 Auto-Sync-Block hat denselben Wrapper schon. |
| Phase-Sortierung | Stream-Sort im Template | DB-Order via existing `findBySeasonIdOrderBySortIndex(UUID)` (Phase 58) | Phase 58 ist fertig; D-10 sortIndex ist deterministisch. |
| Combined-View-Aggregation | Custom Stream-Logik im Controller | `StandingsService.calculateStandings(phaseId, null)` (Phase 58 D-04) | Bereits Phase-58-kanonisch. |
| Buchholz-Conditional | If-Else-Wand im Controller | Server-Flag `showBuchholz = phase.format == SWISS && groupId != null` ans Template | D-33; einzeilige Berechnung. |
| Roster-Bulk-Save | Many-Round-Trips zur DB | Eine `@Transactional` Service-Methode `assignTeamsToPhase(phaseId, List<Assignment>)` mit Diff-Logic (Add/Update/Delete-Ableitung) | Atomare Operation, eine Transaction = klare Recovery-Semantik. |
| Bean-Validation-Messages | Eigene `@Valid`-Logik | jakarta.validation `@NotNull`/`@NotBlank` + `BindingResult.hasErrors()` (existing pattern) | Phase 58 SeasonForm-Pattern bereits etabliert. |
| Tab-Mobile-Scroll | User-Agent-Detection + Render-Switch | `overflow-x: auto` + `flex-wrap: nowrap` (CSS only) | D-11; touch-nativ. |
| Layout-Format-Compatibility-Check | Manuelle If-Cascades | Service-Methode `validateLayoutFormat(layout, format)` mit `BusinessRuleException` | D-22 serverseitig. |

**Key insight:** Backend ist **schon fertig**. Phase 60 ist eine Frontend/Routing/DTO-Phase — Service-Logik ist da, Repository-Methoden sind da, Bean-Validation ist da. Hand-Rolling Versuchungen sind ausschließlich UI-Hacks (JS-Tabs, SpEL-Projections); diese sind explizit untersagt.

---

## Common Pitfalls

### Pitfall 1: Phase 58 D-25 Auto-Sync-Block-Removal bricht existierende Tests

**What goes wrong:** `SeasonManagementService.save` synchronisiert heute (Phase 58 D-25) automatisch Format/Scoring/Dates/Rounds/Legs/EventDuration auf die REGULAR-Phase. Phase 60 entfernt diesen Block (UI-01) — die UI-Form-Felder existieren nicht mehr. **Aber:** Mehrere Tests in `SeasonManagementServiceTest` (Z. 556+ "givenNewSeasonPrimitives_whenSave_thenCreatesSeasonWithScoringLookups", Z. 596+ "givenExistingSeasonPrimitives_whenSave_thenUpdatesSeasonFields") und auch `SeasonControllerTest` (Z. 67-83 "givenValidScoringRefs_whenSaveSeason_thenRedirects") bauen auf der alten Service-Signatur auf.

**Why it happens:** Service-Signatur ändert sich von `save(id, name, year, number, description, startDate, endDate, active, format, totalRounds, legs, eventDurationMinutes, raceScoringId, matchScoringId)` zu `save(id, name, year, number, description, active)`. 14 → 6 Parameter. Plus: REGULAR-Phase wird **nicht mehr** in `SeasonManagementService.save` find-or-created — neue Saisons starten ohne REGULAR-Phase, was D-08 Empty-State triggert.

**How to avoid:**
1. SeasonManagementService.save-Signatur reduzieren UND einen separaten REGULAR-Phase-Bootstrap-Flow definieren: Entweder (a) `save` legt automatisch eine leere REGULAR-Phase an mit Application-weiten Defaults, oder (b) nur Saison-Stamm anlegen, REGULAR-Phase muss manuell über D-08 Empty-State erfasst werden.
2. **Empfehlung (a):** Beim Anlegen einer neuen Saison wird automatisch eine REGULAR-Phase mit `phaseType=REGULAR, layout=LEAGUE, sortIndex=0, format=null` (ohne Format-Default — User muss Format wählen) erzeugt. Das vermeidet D-08-Empty-State im Normalfall und hält die "Saison hat immer eine REGULAR-Phase"-Invariante. Begründung: pure-gem.md Z. 18: "1× **REGULAR**" — nicht 0..1. Service-Bootstrap.
3. Tests umschreiben: `SeasonManagementServiceTest` Auto-Sync-Tests entfernen, neue Tests hinzufügen für "ohne Auto-Sync-Felder bleibt REGULAR-Phase unangetastet". `SeasonControllerTest`-Save-Test mit reduzierter Form anpassen.

**Warning signs:** Compile-Errors in SeasonControllerTest beim Reduzieren des Parameter-Sets — explizit Phasen-Modell als Pflicht-Sicherheit.

### Pitfall 2: Spring-Indexed-Properties leere Liste IndexOutOfBoundsException

**What goes wrong:** Bei `PhaseTeamForm.assignments = new ArrayList<>()` und Form-Submit `assignments[5].teamId=...` versucht Spring den Index 5 anzulegen, aber die Liste ist leer.

**Why it happens:** Spring's `BeanWrapperImpl` erwartet vorinitialisierte oder auto-growing Lists.

**How to avoid:** Liste mit Größe der bekannten Saison-Teams vor-initialisieren ODER `org.springframework.util.AutoPopulatingList` verwenden. Pattern:
```java
@Getter @Setter
public class PhaseTeamForm {
    private List<Assignment> assignments = new AutoPopulatingList<>(Assignment.class);
}
```
Dann darf der Form-Inhalt beliebige Indizes haben.

**Warning signs:** `IndexOutOfBoundsException: Index 5 out of bounds for length 0` beim Form-POST.

### Pitfall 3: Two-Row Tabs Mobile — Active-State sichtbar trotz overflow-x

**What goes wrong:** Auf Mobile ist die aktive Tab-Beschriftung außerhalb des Sichtbereichs (z. B. weiter rechts). Der User sieht zwar den Scrollbar, aber nicht welche Phase/Gruppe ausgewählt ist.

**Why it happens:** `overflow-x: auto` startet immer am linken Rand (`scroll-position: 0`).

**How to avoid:** Server-Side-Render einen `data-active-index="N"` Attribut auf den Container und kleines JS-Snippet (5 Zeilen) im `sidebar-toggle.js` (oder neuem `tabs-scroll.js`) das beim Page-Load `scrollIntoView({ block: 'nearest', inline: 'center' })` auf das Active-Element ruft. Alternative: CSS `scroll-snap-type: x mandatory` plus `scroll-snap-align: center` auf `.tab-active` — kein JS, aber Browser-Support prüfen.

**Warning signs:** `playwright-cli`-Mobile-Screenshot zeigt linken Tab, nicht den ausgewählten.

### Pitfall 4: D-12 Legacy-URL `?season=` ohne REGULAR-Phase

**What goes wrong:** Pre-V4-Migration-Saisons (oder Test-Daten ohne REGULAR-Phase) erzeugen `EntityNotFoundException` wenn `findRegularPhase(seasonId)` aufgerufen wird → 404 statt User-freundliche Empty-State.

**Why it happens:** `SeasonPhaseService.findRegularPhase` wirft fail-loud (Phase 58 D-02). Der Bridge-Pfad in StandingsController müsste `findByType(seasonId, REGULAR)` (Optional-Variante) verwenden.

**How to avoid:** StandingsController bei Legacy-URL `?season=` immer `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)` nutzen, bei Empty den D-08-Empty-State (mit "Add REGULAR Phase"-Link) rendern statt zu wirft. Konsistent mit StandingsService Phase 58 D-01 `@Deprecated`-Bridge-Pattern (StandingsService.java Z. 154-164).

**Warning signs:** 404-Page bei `/admin/standings?season={seasonOhneRegular}` statt Tabelle mit Empty-State.

### Pitfall 5: Routing-Collision SeasonController vs. SeasonPhaseController

**What goes wrong:** Spring's PathMatcher matcht `/admin/seasons/{id}/edit` und `/admin/seasons/{id}/phases/new` beide gegen das `@RequestMapping("/admin/seasons")`-Prefix. Wenn beide Controller das Prefix teilen, kann es zu mehrdeutigem Routing führen.

**Why it happens:** SeasonController hat `@RequestMapping("/admin/seasons")` Class-Level. Wenn SeasonPhaseController auch `@RequestMapping("/admin/seasons/{seasonId}/phases")` setzt — das ist ein anderer (nested) Path und Spring akzeptiert beide; Konflikt entsteht nicht.

**How to avoid:** Klare Trennung: SeasonController bleibt `/admin/seasons/...`, SeasonPhaseController hat `@RequestMapping("/admin/seasons/{seasonId}/phases")`. Sub-Mappings: `@GetMapping("/new")`, `@GetMapping("/{phaseId}")`, etc. Achtung: Das `{seasonId}` muss in jedem Controller-Endpoint als `@PathVariable UUID seasonId` aufgenommen werden.

**Warning signs:** Spring-Boot-Startup-Error `Ambiguous mapping` — wäre offensichtlich.

### Pitfall 6: PlayoffController-17-Endpoints + D-43 UI-Hide ohne Backend-Bruch

**What goes wrong:** PlayoffController hat 17 Endpoints. D-43 sagt: Backend-Endpoints `/admin/playoffs/{id}/add-season` und `/admin/playoffs/{id}/remove-season` bleiben, UI versteckt sie. Ein Refactor-versucher entfernt versehentlich auch die `@Deprecated PlayoffService.addSeasonToPlayoff/removeSeasonFromPlayoff`-Service-Methoden — und der Controller schlägt fehl beim Compile.

**Why it happens:** `@Deprecated` ohne klare Cleanup-Phase ist verlockend.

**How to avoid:** D-44 ist conservative — die `addSeasonToPlayoff/removeSeasonFromPlayoff`-Service-Methoden bleiben bis Phase 61 (MIGR-06). Plan muss explizit sagen: **Lass diese zwei `@Deprecated`-Methoden in Phase 60 stehen.** Sie haben heute genau einen Caller (PlayoffController), aber dieser Caller bleibt funktional pro D-43.

**Warning signs:** Compile-Error in PlayoffController nach Service-Cleanup. Dann: zurückrollen.

### Pitfall 7: SeasonTeam-Add Atomar (D-26) Race-Condition

**What goes wrong:** `SeasonManagementService.addTeamToSeason` schreibt heute nur `SeasonTeam`. D-26 verlangt zusätzlich `PhaseTeam`-Insert für REGULAR-Phase mit `group=NULL`. Wenn nur SeasonTeam ohne Transaction-Boundary zur PhaseTeam-Insertion erfolgt, kann ein Crash zwischen den beiden Inserts zu inkonsistentem State führen.

**Why it happens:** Ohne `@Transactional` sind die zwei JPA-Inserts in separaten Transactions.

**How to avoid:** `addTeamToSeason` ist bereits `@Transactional` (siehe SeasonManagementService.java Z. 314). Erweiterung: nach `season.addTeam(team)` direkt `phaseTeamRepository.save(new PhaseTeam(regularPhase, team))` aufrufen, REGULAR-Phase via `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)`. Bei Empty-Phase (D-08): nichts hinzufügen oder D-08-Empty-State-Trigger.

**Warning signs:** Integration-Test "givenSeasonTeamAdded_whenLookupPhaseTeams_thenContainsTeam" schlägt fehl.

### Pitfall 8: Roster-Editor Diff-Logic bei UPDATE

**What goes wrong:** Bei jedem POST `/admin/seasons/{sid}/phases/{pid}/roster` werden alle Checkbox-States gesendet. Naiv-Implementation löscht alle PhaseTeams und schreibt sie alle neu — das verletzt die `UNIQUE (phase_id, team_id)` Constraint kurzzeitig oder zerstört Audit-IDs.

**Why it happens:** `phaseTeamRepository.deleteAll()` + `saveAll()` ist nicht idempotent gegenüber `created_at`-Audit-Feldern und kann mit anderen FKs (z. B. zukünftige Stats-Joins) brechen.

**How to avoid:** Diff-Logik im Service:
```java
@Transactional
public void assignTeamsToPhase(UUID phaseId, List<Assignment> assignments) {
    var existing = phaseTeamRepository.findByPhaseId(phaseId);
    var existingByTeamId = existing.stream().collect(Collectors.toMap(pt -> pt.getTeam().getId(), pt -> pt));

    Set<UUID> includedTeamIds = new HashSet<>();
    for (var a : assignments) {
        if (!a.isIncluded()) continue;
        includedTeamIds.add(a.getTeamId());
        var pt = existingByTeamId.get(a.getTeamId());
        if (pt == null) {
            // INSERT
            var phase = findById(phaseId);
            var team = teamRepository.findById(a.getTeamId()).orElseThrow(...);
            var newPt = new PhaseTeam(phase, team);
            if (a.getGroupId() != null) newPt.setGroup(groupRepo.findById(a.getGroupId()).orElseThrow(...));
            phaseTeamRepository.save(newPt);
        } else if (!Objects.equals(pt.getGroup() != null ? pt.getGroup().getId() : null, a.getGroupId())) {
            // UPDATE group only
            pt.setGroup(a.getGroupId() != null ? groupRepo.findById(a.getGroupId()).orElseThrow(...) : null);
            phaseTeamRepository.save(pt);
        }
        // else: no-op
    }
    // DELETE removed
    for (var pt : existing) {
        if (!includedTeamIds.contains(pt.getTeam().getId())) {
            phaseTeamRepository.delete(pt);
        }
    }
}
```

**Warning signs:** UNIQUE-Constraint-Violation in den Logs nach "Save Roster"-POST.

### Pitfall 9: D-13 Page-Title mit `phase.label` null

**What goes wrong:** `phase.label` ist optional (Phase 58 D-19 setzt `label = playoff.name`, REGULAR ist häufig null). Template `${season.name} — ${phase.label}` rendert dann `… — `.

**How to avoid:** Server-Side `effectiveLabel = phase.label != null && !phase.label.isBlank() ? phase.label : phase.phaseType.displayName()` (D-05) als Model-Attribut. Im Template `${effectivePhaseLabel}`.

**Warning signs:** Browser-Tab "Spring 2025 — " (trailing dash).

### Pitfall 10: Driver-Import-Preview `tab.year()` Display

**What goes wrong:** Heute zeigt das Template `<h2 th:text="${tab.year()}"></h2>` — also nur das Jahr. D-37 verlangt: raw Tab-Name 1:1 (kann `2025` ODER `2025_S2` sein).

**Why it happens:** `TabPreview` hat sowohl `year` als auch `tabName` als Felder; Phase 59 hat `tabName` ergänzt aber das Template zieht weiter `year`.

**How to avoid:** Im Template `${tab.tabName()}` statt `${tab.year()}` rendern. `tabName` ist bereits im Record (Phase 59 — siehe DriverSheetImportService.java Z. 405-407: `String tabName, int year, Integer number, ...`).

**Warning signs:** Sheet-Tab `2025_S2` zeigt Header `2025` statt `2025_S2`.

---

## Code Excerpts (existing patterns to replicate)

### Existing Tab Pattern — `template-editors.html` (Vorlage für UI-02/UI-05 Phase-Tabs)

```html
<!-- Source: src/main/resources/templates/admin/template-editors.html Z. 11-42 -->
<div class="editor-tab-bar">
    <a th:href="@{/admin/tools/template-editors(tab='team-cards')}"
       th:classappend="${activeTab == 'team-cards' ? 'tab-active' : ''}"
       class="tab-btn">Team Cards</a>
    <a th:href="@{/admin/tools/template-editors(tab='lineup')}"
       th:classappend="${activeTab == 'lineup' ? 'tab-active' : ''}"
       class="tab-btn">Race Lineup</a>
    <!-- ... -->
</div>
```

### Existing Form-with-BindingResult Pattern — `season-form.html` (UI-03 Vorlage)

```html
<!-- Source: src/main/resources/templates/admin/season-form.html Z. 8-22 -->
<form th:action="@{/admin/seasons/save}" th:object="${seasonForm}" method="post">
    <input type="hidden" th:field="*{id}">
    <div class="form-row">
        <div class="form-group">
            <label for="name">Name</label>
            <input type="text" id="name" th:field="*{name}" placeholder="e.g. Regular Season" required>
            <span class="field-error" th:if="${#fields.hasErrors('name')}" th:errors="*{name}"></span>
        </div>
    </div>
    <!-- ... -->
    <div class="actions mt-md">
        <button type="submit" class="btn btn-primary">Save</button>
        <a th:href="@{/admin/seasons}" class="btn btn-secondary">Cancel</a>
    </div>
</form>
```

### Existing Flash-Attribute Redirect — `SeasonController.save`

```java
// Source: src/main/java/org/ctc/admin/controller/SeasonController.java Z. 84-99
@PostMapping("/save")
public String save(@Valid @ModelAttribute("seasonForm") SeasonForm form, BindingResult result,
                   @RequestParam UUID raceScoring,
                   @RequestParam UUID matchScoring,
                   RedirectAttributes redirectAttributes, Model model) {
    if (result.hasErrors()) {
        addScoringLists(model);
        return "admin/season-form";
    }
    var season = seasonManagementService.save(form.getId(), form.getName(), form.getYear(), ...);
    redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + season.getName());
    return "redirect:/admin/seasons";
}
```

### Existing BusinessRuleException Handling — `SeasonController.removeTeam`

```java
// Source: src/main/java/org/ctc/admin/controller/SeasonController.java Z. 109-119
@PostMapping("/{id}/remove-team")
public String removeTeam(@PathVariable UUID id, @RequestParam UUID teamId,
                         RedirectAttributes redirectAttributes) {
    try {
        seasonManagementService.removeTeamFromSeason(id, teamId);
        redirectAttributes.addFlashAttribute("successMessage", "Team removed");
    } catch (IllegalStateException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/seasons/" + id + "/edit";
}
```

> **Note for Phase 60:** Für Konsistenz mit Phase 58 D-18/D-25/D-28 muss neuer Code `BusinessRuleException` werfen + im Controller fangen — D-27 + GlobalExceptionHandler. `IllegalStateException` ist Legacy.

### Existing GlobalExceptionHandler-Mapping

```java
// Source: src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java Z. 28-50
@ExceptionHandler(EntityNotFoundException.class)
public ModelAndView handleEntityNotFound(EntityNotFoundException ex) { /* 404 */ }

@ExceptionHandler(BusinessRuleException.class)
public ModelAndView handleBusinessRule(BusinessRuleException ex) { /* 409 Conflict */ }

@ExceptionHandler(ValidationException.class)
public ModelAndView handleValidation(ValidationException ex) { /* 400 */ }
```

> **D-09 + D-23 + D-25 + D-28 leveragen alle bereits diese Mappings.** Kein neues GlobalExceptionHandler-Coding nötig.

### Existing Phase-Aware Service Calls (Phase 58 already canonical)

```java
// SeasonPhaseService:
seasonPhaseService.findRegularPhase(seasonId);           // throws if missing
seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF); // returns Optional
seasonPhaseService.findById(phaseId);
seasonPhaseService.findAllPhases(seasonId);              // sorted by sortIndex
seasonPhaseService.create(seasonId, type, layout, sortIndex, label, raceScoring, matchScoring,
                          format, startDate, endDate, totalRounds, legs, eventDurationMinutes);
seasonPhaseService.createGroup(phaseId, name, sortIndex);
seasonPhaseService.assignTeamToPhase(phaseId, teamId, groupId); // groupId nullable

// StandingsService:
standingsService.calculateStandings(phaseId, groupId);   // groupId nullable for combined view
standingsService.calculateStandingsWithBuchholz(phaseId, groupId);

// MatchdayService:
matchdayService.findByPhaseId(phaseId);
matchdayService.findByPhaseIdAndGroupId(phaseId, groupId);

// PhaseTeamRepository:
phaseTeamRepository.findByPhaseId(phaseId);
phaseTeamRepository.findByPhaseIdAndGroupId(phaseId, groupId);  // null derives IS NULL
phaseTeamRepository.findByPhaseIdAndTeamId(phaseId, teamId);    // returns Optional (UNIQUE)
```

**For Phase 60 the planner adds (D-21, D-23, D-28, D-26 atomic):**
- `SeasonPhaseService.update(phaseId, ...)` — D-21 mit Layout/Format-Wechsel-Pre-Check.
- `SeasonPhaseService.delete(phaseId)` — D-23 strict guard.
- `SeasonPhaseService.updateGroup(groupId, name, sortIndex)` — D-24 manuell editierbar.
- `SeasonPhaseService.deleteGroup(groupId)` — D-28 strict guard.
- `SeasonPhaseService.assignTeamsToPhase(phaseId, List<Assignment>)` — D-20 Bulk-Diff-Save.
- `SeasonManagementService.addTeamToSeason` — Erweiterung: zusätzlich `PhaseTeam(REGULAR, team, group=null)`-Insert (D-26).
- `SeasonManagementService.removeTeamFromSeason` — Erweiterung: D-25 strict guard wenn `phase_teams`-Rows existieren.

---

## Test Strategy

### Existing Test Classes — Extension Required

| Test Class | Lines | Reason for Extension |
|------------|-------|---------------------|
| `SeasonControllerTest` (~306 LOC, 16 Tests) | UI-01 | Reduzierte Save-Signatur, Form-Felder weniger, neue Empty-State-Routes |
| `SeasonControllerExceptionTest` | UI-01 | IO-Exception bei Logo (bestehend) — kein direct Phase-60-Impact, aber Routes prüfen |
| `StandingsControllerTest` (~157 LOC, 6 Tests) | UI-05 | Phase-canonical Routes `?phase=&group=`, Combined-View, Buchholz-Conditional, Legacy-Bridge `?season=` |
| `PlayoffControllerTest` (~317 LOC, 19 Tests) | UI-07 | Server-side Phase-Resolution; Add-/Remove-Season-Endpoints bleiben (Tests ändern sich nicht); UI-Templates prüfen via `view().name(...)` ist OK |
| `MatchdayControllerTest` (~320 LOC, 20 Tests) | UI-02, D-44 | Nichts UI-spezifisches; aber switchen MatchdayController auf phaseId-canonical → Tests ggf. umformulieren |
| `DriverSheetImportControllerTest` (~495 LOC, 19 Tests) | UI-06 | Tab-Header zeigt `tabName` statt `year`; Inline-Badge-Render; Group-Spalte konditional |
| `SeasonManagementServiceTest` (~600+ LOC, ~25 Tests) | UI-01, D-25 D-26 | Auto-Sync-Tests entfernen, D-25/D-26 atomare Service-Tests hinzufügen |
| `StandingsServiceTest` (~25 Tests) | — | Phase 58 D-04 schon kanonisch; keine Änderung nötig |
| `SeasonPhaseServiceTest` (~11 Tests) | UI-03/04 | Erweiterung: `update`, `delete`, `updateGroup`, `deleteGroup`, `assignTeamsToPhase`-Tests |
| `MatchdayServiceTest` (~15 Tests) | D-44 | Phase-aware Finder-Tests bleiben; `findBySeasonId`-Bridge-Removal-Test ergänzen |

### New Test Classes Required

| New Test Class | Purpose |
|----------------|---------|
| `SeasonPhaseControllerTest` | UI-02/03 — alle GET/POST-Routes des neuen Controllers (`/admin/seasons/{sid}/phases/...`); Mockito (15-20 Tests) |
| `SeasonPhaseGroupControllerTest` | UI-04 — alle GET/POST-Routes des neuen Controllers (10-12 Tests) |
| `SeasonPhaseFormTest` (optional) | Bean-Validation-Tests für SeasonPhaseForm — `@NotNull`, `@NotBlank`-Konstellationen |
| `PhaseTeamFormTest` (optional) | AutoPopulatingList-Verhalten validieren — happy path + edge cases |
| `SeasonPhaseControllerIT` | UI-02/03 IT — `@SpringBootTest @ActiveProfiles("dev") @Transactional`; persistente Test-Daten via `TestHelper`/`TestDataService` |
| `SeasonPhaseGroupControllerIT` | UI-04 IT — Two-Step-Flow + Roster-Diff-Save |
| `StandingsControllerCombinedViewTest` | UI-05 — Combined-View mit `groupId=null`, Per-Group, Legacy-Bridge `?season=` |

### Test-Data Setup (T-prefixed isolation per CLAUDE.md)

CLAUDE.md `Isolate Test Data Completely` verlangt T-Prefix-Konvention. Phase 59 hat den `TestDataService` bereits auf das Phase/Group-Modell umgestellt (DATA-01 complete). Für Phase 60-Tests brauchen wir:

| Test Data Need | Helper | Status |
|----------------|--------|--------|
| Test-Saison mit REGULAR-Phase (LEAGUE) | `testHelper.createSeason("T-Standings " + uuid)` + Phase 58 D-25 hat schon Auto-REGULAR (bis Phase 60 entfernt) | Ändert sich in Phase 60 — Test-Helper muss explizit REGULAR anlegen |
| Test-Saison mit REGULAR-Phase (GROUPS) + 2 Groups | `TestDataService.createGroupsTestSeason("T-Groups", ...)` (aus Phase 59 DATA-01) | Vorhanden |
| Test-Saison mit PLAYOFF-Phase | `TestDataService` oder direkt via `playoffService.createPlayoff(...)` | Vorhanden |
| Test-Phase ohne Roster | `seasonPhaseService.create(...)` mit GROUPS-Layout (D-20) | Vorhanden |

> **Action item für Plan:** Wenn UI-01 die Auto-REGULAR-Phase-Erzeugung in `SeasonManagementService.save` entfernt (Pitfall 1 / Auto-Sync-Block), muss `TestHelper.createSeason` (oder ein neuer `createSeasonWithRegular`) explizit eine REGULAR-Phase anlegen. Sonst brechen alle Tests, die `season.matchdays` etc. erwarten.

### Validation Architecture

> Per `.planning/config.json` ist `nyquist_validation: true` — Section ist Pflicht.

#### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5.x + Mockito + Spring-Test (MockMvc) + Playwright (E2E) |
| Config files | `pom.xml` (Surefire+Failsafe+JaCoCo), `application-dev.yml` (H2 in-mem) |
| Quick run command | `./mvnw test -Dtest=SeasonPhaseControllerTest` (single class) |
| Full suite command | `./mvnw verify` (Unit+IT+JaCoCo) — finale Verifikation: `./mvnw verify -Pe2e` (inkl. Playwright) |

#### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UI-01 | Saison-Form ohne Format/Scoring/Dates rendert + speichert | unit + IT | `./mvnw test -Dtest=SeasonControllerTest#whenGetNewSeasonForm_thenReturnsSeasonForm` | ✅ existing — needs update |
| UI-01 | SeasonManagementService.save reicht nur Stammdaten durch | unit | `./mvnw test -Dtest=SeasonManagementServiceTest#givenSlimForm_whenSave_thenSeasonPersisted` | ❌ Wave 1 |
| UI-01 | D-25 Auto-Sync-Block ist entfernt — REGULAR-Phase format/scoring werden NICHT von Saison-Save überschrieben | unit | `./mvnw test -Dtest=SeasonManagementServiceTest#givenExistingPhaseWithFormat_whenSeasonSaved_thenPhaseFormatUntouched` | ❌ Wave 1 |
| UI-02 | Saison-Detail rendert Phase-Tabs + Group-Sub-Tabs konditional | IT (MockMvc) | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenGroupsLayoutPhase_whenGetSeasonDetail_thenGroupSubTabsRendered` | ❌ Wave 0 |
| UI-02 | D-09 Phase-Saison-Mismatch → 404 | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenWrongSeasonId_whenGetPhase_thenReturns404` | ❌ Wave 0 |
| UI-02 | D-08 Empty-State bei keiner REGULAR-Phase | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenSeasonWithoutRegularPhase_whenGetSeasonDetail_thenRendersEmptyStateCard` | ❌ Wave 0 |
| UI-02 | Visual: Tab-Active-State + Mobile-Scroll | manual playwright-cli | `playwright-cli open http://localhost:9090/admin/seasons/{id}` (Desktop + Mobile) | manual |
| UI-03 | Phase-Form rendert mit Defaults aus REGULAR | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenRegularPhase_whenAddPhase_thenFormPrefilledWithRegularDefaults` | ❌ Wave 0 |
| UI-03 | Phase-Form save → BusinessRuleException bei Duplicate | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenExistingRegular_whenCreateSecondRegular_thenFlashError` | ❌ Wave 0 |
| UI-03 | Phase-Edit → Layout-Wechsel-Warning | IT | `./mvnw test -Dtest=SeasonPhaseServiceTest#givenPhaseWithMatchdays_whenChangeLayout_thenThrowsBusinessRule` | ❌ Wave 0 |
| UI-03 | Delete-Phase mit Matchdays → strict guard | IT | `./mvnw test -Dtest=SeasonPhaseServiceTest#givenPhaseWithMatchdays_whenDelete_thenThrowsBusinessRule` | ❌ Wave 0 |
| UI-04 | Group-Form Two-Step → Step 1 saves group | IT | `./mvnw test -Dtest=SeasonPhaseGroupControllerIT#givenGroupsPhase_whenSaveGroup_thenRedirectsToRosterStep` | ❌ Wave 0 |
| UI-04 | Roster-Multi-Select Save → Diff-Logic | IT | `./mvnw test -Dtest=SeasonPhaseGroupControllerIT#givenRosterDiff_whenSave_thenInsertsAndDeletesAndUpdates` | ❌ Wave 0 |
| UI-04 | D-25 SeasonTeam-Delete strict guard | unit | `./mvnw test -Dtest=SeasonManagementServiceTest#givenPhaseTeamRefs_whenRemoveSeasonTeam_thenThrowsBusinessRule` | ❌ Wave 1 |
| UI-04 | D-26 SeasonTeam-Add atomar PhaseTeam-Insert | IT | `./mvnw test -Dtest=SeasonManagementServiceIT#givenAddTeamToSeason_thenPhaseTeamCreatedInRegular` | ❌ Wave 1 |
| UI-05 | Standings rendert Phase-Tabs + Group-Sub-Tabs | IT | `./mvnw test -Dtest=StandingsControllerTest#givenGroupsPhase_whenGetStandings_thenSubTabsRendered` | ✅ extend |
| UI-05 | Combined-View ohne groupId zeigt Group-Spalte | IT | `./mvnw test -Dtest=StandingsControllerTest#givenGroupsPhase_whenGetStandingsWithoutGroup_thenCombinedViewWithGroupColumn` | ❌ Wave 0 |
| UI-05 | Buchholz-Spalte konditional auf Per-Group + Swiss | IT | `./mvnw test -Dtest=StandingsControllerTest#givenSwissPerGroup_whenGetStandings_thenShowBuchholzTrue` | ❌ Wave 0 |
| UI-05 | Legacy `?season=` resolved zu REGULAR-Phase | IT | `./mvnw test -Dtest=StandingsControllerTest#givenLegacySeasonParam_whenGetStandings_thenResolvesToRegularPhase` | ✅ extend |
| UI-05 | Visual: Phase-/Group-Tabs sichtbar + funktional | manual playwright-cli | `playwright-cli open http://localhost:9090/admin/standings?phase={pid}&group={gid}` Desktop + Mobile | manual |
| UI-06 | Tab-Header zeigt `tab.tabName()` (raw) | IT | `./mvnw test -Dtest=DriverSheetImportControllerTest#given2025_S2Tab_whenPreview_thenH2ShowsRawName` | ✅ extend |
| UI-06 | TabWarning-Banner rendert | IT | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenTabWithWarning_whenPreview_thenBannerVisible` | ✅ exists (Phase 59) |
| UI-06 | Group-Spalte konditional bei GROUPS-Layout | IT | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenGroupsLayoutTarget_whenPreview_thenGroupColumnRendered` | ❌ Wave 0 |
| UI-06 | Inline-Badge "⚠ No group" auf Driver-Zeilen | IT | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenDriverWithNullResolvedGroup_whenPreview_thenInlineBadgeRendered` | ❌ Wave 0 |
| UI-06 | Visual: Banner + Group-Spalte + Inline-Badge | manual playwright-cli | `playwright-cli open http://localhost:9090/admin/drivers/import` (mocked sheet) Desktop + Mobile | manual |
| UI-07 | Playoff-URL `/admin/playoffs/{id}` weiter funktional | IT | `./mvnw test -Dtest=PlayoffControllerTest#givenPlayoff_whenGetPlayoffsForSeason_thenReturnsBracket` | ✅ existing |
| UI-07 | Add-Season-Backend-Endpoint bleibt funktional | IT | `./mvnw test -Dtest=PlayoffControllerTest#givenPlayoffAndOtherSeason_whenAddAndRemoveSeasonFromPlayoff_thenBothSucceed` | ✅ existing |
| UI-07 | Bracket-UI rendert KEINE Add-Season-Buttons | IT (MockMvc + xpath) | `./mvnw test -Dtest=PlayoffControllerTest#givenPlayoff_whenGetBracket_thenAddSeasonButtonNotPresent` | ❌ Wave 0 |
| UI-07 | "+ Add Phase" mit phaseType=PLAYOFF auf Saison-Detail | IT | `./mvnw test -Dtest=SeasonPhaseControllerIT#givenSeasonWithoutPlayoff_whenAddPhasePLAYOFF_thenPlayoffServiceAutoCreatesPlayoff` | ❌ Wave 0 |
| UI-07 | Visual: Bracket ohne Add-Season-UI | manual playwright-cli | `playwright-cli open http://localhost:9090/admin/playoffs/{id}` Desktop + Mobile | manual |

#### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest={ClassName}` (gezielt, ~5-15 Sek.)
- **Per wave merge:** `./mvnw verify` (Unit + IT + JaCoCo, ~3-5 Min.)
- **Phase gate:** `./mvnw verify -Pe2e` grün + JaCoCo ≥ 82 % + manuelle `playwright-cli`-Verifikation pro UI-Page (Desktop + Mobile) — bevor `/gsd-verify-work` ausgeführt wird.

#### Wave 0 Gaps (test-files to create before implementation)

- [ ] `src/test/java/org/ctc/admin/controller/SeasonPhaseControllerTest.java` — covers UI-02/UI-03 unit tests
- [ ] `src/test/java/org/ctc/admin/controller/SeasonPhaseGroupControllerTest.java` — covers UI-04 unit tests
- [ ] `src/test/java/org/ctc/admin/controller/integration/SeasonPhaseControllerIT.java` — covers UI-02/UI-03 IT
- [ ] `src/test/java/org/ctc/admin/controller/integration/SeasonPhaseGroupControllerIT.java` — covers UI-04 IT
- [ ] `src/test/java/org/ctc/admin/dto/SeasonPhaseFormTest.java` — covers Bean-Validation
- [ ] `src/test/java/org/ctc/admin/dto/PhaseTeamFormTest.java` — covers AutoPopulatingList behavior
- [ ] Extend `SeasonControllerTest.java` — Slim-Form-Save-Test
- [ ] Extend `SeasonManagementServiceTest.java` — D-25/D-26 atomar tests, Auto-Sync-Removal-tests
- [ ] Extend `StandingsControllerTest.java` — Combined-View, Buchholz-Conditional, Legacy-Bridge
- [ ] Extend `DriverSheetImportControllerTest.java` — Tab-Name-Display, Inline-Badge, Group-Spalte
- [ ] Extend `PlayoffControllerTest.java` — Add-Season-UI-Hidden Test
- [ ] Extend `SeasonPhaseServiceTest.java` — `update`, `delete`, `updateGroup`, `deleteGroup`, `assignTeamsToPhase` Tests

> Framework install: not needed — all dependencies (JUnit 5, Mockito, Spring-Test, Playwright) bereits in pom.xml.

---

## Dependency Cleanup Map (D-44 conservative)

Diese Tabelle listet exakt jeden `@Deprecated`-Service-Overload, dessen einzige Caller in Phase 60 verschwinden — **diese sind die Cleanup-Kandidaten**. Service-Methoden mit anderen Konsumenten (SiteGenerator, DevDataSeeder, andere Services) bleiben in Phase 60 stehen und werden in Phase 61 (MIGR-06) bereinigt.

### Cross-Reference: Wer ruft `@Deprecated`-Methoden heute?

Aus `grep` Analysis:

| `@Deprecated` Service Method | Heute aufgerufen von | Phase-60-Cutover möglich? |
|------------------------------|----------------------|----------------------------|
| `StandingsService.calculateStandings(seasonId)` | StandingsController, AbstractMatchdayGraphicService, OverlayGraphicService, TeamCardService, LineupGraphicService, SettingsGraphicService, SiteGeneratorService | ❌ NEIN — viele Consumer (Graphics + Site Gen) bleiben |
| `StandingsService.calculateStandingsWithBuchholz(seasonId)` | StandingsController | ✅ JA — wenn StandingsController auf phaseId umgebaut wird |
| `DriverRankingService.calculateRanking(seasonId)` | StandingsController | ✅ JA — wenn StandingsController auf phaseId umgebaut wird |
| `MatchdayService.findBySeasonId(seasonId)` | (kein Caller im main-code — nur Tests) | ✅ JA — D-44 erlaubt Removal |
| `MatchdayGeneratorService.generate(seasonId, ...)` | SeasonController.generate (Z. 240) | ✅ JA — wenn SeasonController-Generate-Form auf phaseId-canonical umgebaut wird (Plan: ja) |
| `SwissPairingService.generateNextRound(seasonId)` | SeasonController.generateSwissRound (Z. 209) | ✅ JA — wenn SeasonController-Swiss-Form auf phaseId umgebaut wird |
| `SwissPairingService.getByeTeams(seasonId)` | (Test only?) | ✅ JA wenn nur Tests; sonst behalten |
| `SwissPairingService.getCurrentRound(seasonId)` | SeasonController.swissRounds (Z. 199) | ✅ JA — wenn SeasonController-Swiss-View auf phaseId umgebaut wird |
| `SwissPairingService.isCurrentRoundComplete(seasonId)` | SeasonController.swissRounds (Z. 200) | ✅ JA |
| `PlayoffService.addSeasonToPlayoff(playoffId, seasonId)` | PlayoffController.addSeason (Z. 106) | ❌ NEIN — D-43 sagt explizit: Backend bleibt funktional, UI versteckt |
| `PlayoffService.removeSeasonFromPlayoff(...)` | PlayoffController.removeSeason (Z. 113) | ❌ NEIN — D-43 |

### Final D-44 Removal-Liste für Phase 60

**Sicher entfernen:**
- `MatchdayService.findBySeasonId(seasonId)` (MatchdayService.java Z. 156-162) — kein Production-Caller.
- `MatchdayGeneratorService.generate(seasonId, numberOfRounds, homeAndAway)` (MatchdayGeneratorService.java Z. 122-126) — wenn SeasonController.generate auf phaseId umgebaut.
- `SwissPairingService.generateNextRound(seasonId)`, `getByeTeams(seasonId)`, `getCurrentRound(seasonId)`, `isCurrentRoundComplete(seasonId)` (SwissPairingService.java Z. 167-218 + private legacy fallback Z. 220+) — alle vier Bridge-Methods + Legacy-Fallback-Methods, **wenn** SeasonController-Swiss-Routes (`/{id}/swiss`, `/{id}/swiss/generate`) auf phaseId-canonical umgebaut werden.
- `StandingsService.calculateStandingsWithBuchholz(seasonId)` (StandingsService.java Z. 169-178) — wenn StandingsController auf phaseId umgebaut.
- `DriverRankingService.calculateRanking(seasonId)` (DriverRankingService.java Z. 117-124) — wenn StandingsController auf phaseId umgebaut.

**Behalten bis Phase 61:**
- `StandingsService.calculateStandings(seasonId)` — 6 Production-Caller außerhalb von Controllers (alle Graphics-Services + SiteGenerator). Phase 61 macht das nach MIGR-06.
- `PlayoffService.addSeasonToPlayoff` / `removeSeasonFromPlayoff` — D-43 hart.
- Alle anderen `@Deprecated`-Methoden in Services, die Tests oder Site-Gen weiter nutzen.

**Action item für Plan:** Vor Removal jeder dieser `@Deprecated`-Methoden den Compile-Test laufen lassen (`./mvnw compile`) — wenn ein Caller übersehen wurde, schlägt das früh fehl.

> **Cross-check Tests:** Wenn ein `@Deprecated`-Method gelöscht wird, müssen ALLE Tests, die ihn aufrufen, auch auf phaseId-canonical umgebaut werden. Z. B. wenn `SeasonControllerTest` indirekt `MatchdayGeneratorService.generate(seasonId, ...)` testet, muss der Test auf die neue Phase-Form umgestellt werden.

---

## Wave-split Recommendation

**Empfehlung: 4 Waves.** Die Aufteilung folgt strikt dem Build-Order-Prinzip (Backend Routes vor Templates, Tests parallel zu jeder Wave). Es ist möglich, Wave 2 und Wave 3 parallel zu fahren, falls genug Hands verfügbar — die Templates greifen aber teilweise auf gemeinsame CSS-Anpassungen (`tabs-secondary`) zu, was Reihenfolge wichtig macht.

### Wave 0: Test-Skeleton (TDD-RED)

**Goal:** Alle neuen Test-Dateien anlegen mit failing-Tests, die das Phase-60-Verhalten beschreiben.

**Tasks:**
1. Lege `SeasonPhaseControllerTest`, `SeasonPhaseGroupControllerTest`, `SeasonPhaseFormTest`, `PhaseTeamFormTest`, `StandingsControllerCombinedViewTest` an mit `@Test`-Methoden, die alle initial failen (Methoden-Stubs auf den Controllers fehlen → 404 erwartet, aber 400 actual).
2. Erweitere `SeasonControllerTest`, `SeasonManagementServiceTest`, `DriverSheetImportControllerTest`, `PlayoffControllerTest`, `SeasonPhaseServiceTest` um Phase-60-Tests.
3. Verify: `./mvnw test` läuft, neue Tests sind RED, alte sind GREEN.

**Rationale:** TDD-Discipline + CLAUDE.md verlangt es. Nach Wave 0 hat die Phase einen lückenlosen Test-Plan.

**Exit gate:** Alle Phase-60-Tests existieren und scheitern aus dem richtigen Grund.

### Wave 1: Backend Phase + Group CRUD (UI-03, UI-04 Backend; UI-01 Service-Slim)

**Goal:** Alle neuen DTOs, Service-Methoden und Controllers existieren — UI rendert noch nicht.

**Tasks:**
1. **DTOs anlegen:** `SeasonPhaseForm`, `SeasonPhaseGroupForm`, `PhaseTeamForm`. Bean-Validation-Annotationen pro D-22.
2. **`SeasonPhaseService` erweitern:** `update(...)`, `delete(...)`, `updateGroup(...)`, `deleteGroup(...)`, `assignTeamsToPhase(phaseId, List<Assignment>)` mit Diff-Logic. Service-Tests grün machen.
3. **`SeasonManagementService.addTeamToSeason` D-26-Erweiterung** + **`removeTeamFromSeason` D-25 strict-guard**. Tests grün machen.
4. **`SeasonManagementService.save` UI-01-Slim:** Phase 58 D-25 Auto-Sync-Block entfernen. Service-Signatur reduzieren auf `save(id, name, year, number, description, active)`. REGULAR-Phase-Bootstrap behalten (siehe Pitfall 1, Empfehlung a). Tests umschreiben.
5. **`SeasonForm`-DTO slimmen** auf 5 Felder.
6. **`SeasonController.save`** auf neue Service-Signatur anpassen, `@RequestParam UUID raceScoring/matchScoring` entfernen, `addScoringLists` entfernen.
7. **`SeasonController.edit`** form-Population reduzieren.
8. **Neuer `SeasonPhaseController`** mit GET/POST-Routes für `/admin/seasons/{seasonId}/phases/...`. D-09-Mismatch-Validation einbauen.
9. **Neuer `SeasonPhaseGroupController`** für `/admin/seasons/{sid}/phases/{pid}/groups/...`.
10. **Wave 0-Tests grün machen:** Service-Tests + Controller-Tests + Form-Tests.

**Exit gate:** `./mvnw test` GREEN für alle neuen Service- und Controller-Tests. Templates können noch fehlen — Tests können MockMvc-View-Existenz erst in Wave 2 prüfen.

### Wave 2: Saison-Detail + Phase/Group-Form-Templates (UI-01, UI-02, UI-03, UI-04 Frontend)

**Goal:** Saison-Detail-Page mit Phase-Tabs + Group-Sub-Tabs, Phase-Form, Group-Form-Templates produktiv.

**Tasks:**
1. **`season-form.html` slimmen.** Format/Scoring/Dates-Blocks entfernen.
2. **`season-detail.html` REWRITE:** Toolbar mit Saison-weiten Aktionen + Saison-Header (Stamm-Daten + Teams + Cars/Tracks) bleiben. Darunter: Two-Row-Tabs-Block (D-01 Phase-Tabs + Group-Sub-Tabs). Per-Tab-Inhalt: Roster + Matchdays + Standings (gestapelt).
3. **`season-phase-form.html` NEU:** D-16 Felder + D-17 Defaults + D-22 BindingResult-Errors.
4. **`season-phase-group-form.html` NEU:** D-19 Step 1 (Name + sortIndex), Step 2 ist eingebettet im Saison-Detail-Phase-Tab als Roster-Section.
5. **`admin.css`-Append:** `tabs-secondary`, Mobile-Scroll-Media-Query (D-11), `tab-add` (für "+ Add Phase"-Button).
6. **`playwright-cli` Visual-Verifikation pro Page** (Desktop + Mobile, beide aktive + nicht-aktive Tabs).

**Exit gate:** `./mvnw verify` GREEN. Manuelle visuelle Verifikation pro `playwright-cli` zeigt: Two-Row-Tabs funktionieren auf Desktop + Mobile, Phase-Form rendert, Group-Form rendert.

### Wave 3: Standings + Importer + Playoff-Cutover (UI-05, UI-06, UI-07)

**Goal:** Alle übrigen UI-Cutover-Targets fertig.

**Tasks:**
1. **`StandingsController` erweitern:** Akzeptiere `@RequestParam UUID phase`, `@RequestParam UUID group` zusätzlich zu Legacy `seasonId`. Server-Resolution-Logik: `phase` priorisieren; Fallback `season → REGULAR-Phase via findByType` (D-12). Server-Flags `combinedView`/`showBuchholz`/`showGroupColumn` auf Modell.
2. **`standings.html` REWRITE:** Saison-Dropdown bleibt; darunter Phase-Tabs (D-29 Row 1) + Group-Sub-Tabs (D-29 Row 2 inkl. "Combined"-Tab); Tabelle nutzt Server-Flags für Spalten-Conditional-Rendering. D-36 Empty-State-Banner ergänzen.
3. **`driver-import-preview.html` MODIFIZIEREN:**
   - `<h2 th:text="${tab.tabName()}">` (D-37, fix Pitfall 10).
   - Inline-Badge "⚠ No group" konditional auf Driver-Zeilen (D-39).
   - Group-Spalte konditional rendern (D-40, Server-Flag aus DriverSheetImportController via `targetPhase.layout == GROUPS` ergänzen).
4. **`playoff-bracket.html` MODIFIZIEREN:** Add-Season-/Remove-Season-Buttons-Section (Z. 96-122) komplett entfernen oder hinter `th:if="${false}"` legen — D-43.
5. **`SeasonController.swissRounds` + `generate` + `generateSwissRound` auf phaseId-canonical umstellen** (Plan-Detail: braucht neue Routes wie `/admin/seasons/{sid}/phases/{pid}/swiss` ODER weiterhin saison-zentriert mit interner Resolve-zu-REGULAR-Phase). Empfehlung: interner Resolve, behält die Saison-zentrierten URLs für UX-Continuity.
6. **`MatchdayController.list` D-44 cleanup:** Wenn die `findBySeasonId`-Bridge entfernt wird, Controller auf phaseId-canonical umstellen oder weiterhin saison-aggregiert über `seasonPhaseService.findAllPhases(seasonId).flatMap(...)` arbeiten.
7. **`playwright-cli` Visual-Verifikation pro Page** (Standings, Driver-Import-Preview, Playoff-Bracket).

**Exit gate:** Alle UI-Tests GREEN. Visual-Verifikation komplett.

### Wave 4: Conservative @Deprecated Cleanup + Final Verification (D-44)

**Goal:** Nicht mehr verwendete `@Deprecated`-Service-Methoden entfernen + finale Vollverifikation.

**Tasks:**
1. **D-44 Removal-Liste durcharbeiten** (siehe oben). Pro Method: `grep` nach Callern, wenn nur Tests → Tests umschreiben, dann Method entfernen.
2. **JaCoCo-Coverage prüfen:** `./mvnw verify` + `target/site/jacoco/index.html` öffnen. Falls < 82 % → Wave 0 Tests aufstocken.
3. **`./mvnw verify -Pe2e`** (Unit + IT + Playwright E2E) — finale Verifikation per project memory `feedback_e2e_verification`.
4. **Manuelle `playwright-cli`-Final-Pass:** Alle 7 modifizierten Pages, beide Viewports.
5. **Doku-Updates** (per project memory `feedback_docs_update`): README ggf. anpassen, falls neue URLs erwähnt werden.

**Exit gate:** `./mvnw verify -Pe2e` GREEN, JaCoCo ≥ 82 %, alle Templates visual-verifiziert.

### Total Wave-Summary

| Wave | Goal | Files modified | Files created | Estimated effort |
|------|------|----------------|---------------|------------------|
| 0 | Test skeletons (TDD-RED) | ~6 test files extended | ~7 test files new | ~2-3 h |
| 1 | Backend CRUD + Slim Service | ~4 main + ~3 test | ~5 main (DTOs/Controllers) | ~6-8 h |
| 2 | Saison-Detail + Phase-/Group-Form-Templates | ~3 templates + admin.css | ~2 templates | ~4-6 h |
| 3 | Standings + Importer + Playoff-Cutover | ~5 controllers + ~4 templates | 0 | ~5-7 h |
| 4 | Cleanup + Final Verify | ~6 services (Deprecated removal) + tests | 0 | ~2-3 h |
| **Total** | | **~30 files modified** | **~14 files new** | **~19-27 h** |

> Estimate ist Rough — abhängig von wie sauber die Tests in Wave 0 designed sind.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Season.format/totalRounds/legs/...` direkt in Saison | `SeasonPhase.format/totalRounds/legs/...` pro Phase | Phase 56-58 (shipped) | UI-01 Saison-Form schrumpft auf 5 Felder |
| `Matchday.season_id` FK | `Matchday.phase_id` (+ optional `Matchday.group_id`) | Phase 56-57 (shipped) | UI-02 Matchday-Section zeigt phase/group-spezifische Liste |
| `Playoff.season_id` 1:1 + `playoff_seasons` M:N | `Playoff.phase_id` 1:1; M:N bleibt für Phase 61 | Phase 56-58 (shipped) | UI-07 Bracket-View phase-resolved |
| Standings per `seasonId` | Standings per `phaseId, groupId?` mit Combined-View | Phase 58 (shipped) | UI-05 Two-Row-Tabs |
| Driver-Import per `findByYear` (ambig) | `findByYearAndNumber` eindeutig | Phase 59 (shipped) | UI-06 keine Dropdowns |
| Single-Tab-Page | Multi-Phase-Tab-Page | Phase 60 (this) | UI-02 Saison-Detail neu |

**Deprecated/outdated:**
- `SeasonForm.format/totalRounds/legs/eventDuration/startDate/endDate` — werden in UI-01 entfernt.
- `SeasonController.save(... format, totalRounds, legs, ...)` — Signatur reduziert.
- `SeasonManagementService.save`'s D-25 Auto-Sync-Block — entfernt in UI-01.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | "Empfehlung: Bei neuer Saison wird automatisch eine REGULAR-Phase mit `format=null` erzeugt" (Pitfall 1, Wave 1 Task 4) | [ASSUMED] | Nutzer könnte stattdessen wollen, dass Saison ohne REGULAR-Phase angelegt wird und dann D-08 Empty-State-Form triggert. Das wäre UX-mäßig anders. Plan-/Discuss-Phase sollte das verifizieren. |
| A2 | "AutoPopulatingList ist die Spring-empfohlene Lösung für leere Indexed-Properties" (Pitfall 2) | [CITED: Spring-Doc] | Wenn der Planner stattdessen ein vorinitialisiertes `List<Assignment>` mit der bekannten Saison-Team-Größe wählt, ist das gleichwertig. Kein Risiko. |
| A3 | "`overflow-x: auto` + `flex-wrap: nowrap` auf `.tab-nav` in Mobile-Media-Query reicht für Touch-Scroll" (Pattern 1) | [ASSUMED] | Falls iOS Safari `-webkit-overflow-scrolling: touch` doch braucht (unsere Codebase hat es nicht in `admin.css`), Visual-Verifikation per `playwright-cli` Mobile-Profile bringt das schnell ans Licht. |
| A4 | "Roster-Bulk-Save ist eine einzelne Page (`/admin/seasons/{sid}/phases/{pid}/roster` POST)" (Pattern 3) | [ASSUMED] | Alternativ: pro Team-Row ein eigener AJAX-POST. Würde D-20 ("Submit aktualisiert PhaseTeam-Rows in einer Transaction") brechen. Annahme ist konsistent mit D-20. |
| A5 | "PlayoffService.addSeasonToPlayoff/removeSeasonFromPlayoff bleiben in Phase 60 stehen, da D-43 das Backend explizit erhält" (Pitfall 6, Cleanup-Map) | [VERIFIED: D-43 + grep-confirmation] | Verified gegen Service-Code (Z. 125-152) und Controller-Calls (Z. 103-117). Hard requirement. |
| A6 | "`SeasonPhase.label` ist optional, kann null/blank sein — daher Effective-Label Server-Side-Berechnung nötig" (Pitfall 9) | [VERIFIED: SeasonPhase.java Z. 43] | `private String label;` ohne `@NotBlank`/`@NotNull`. Verifiziert. |
| A7 | "Phase 59's `TabPreview` carries `tabName` field" (Pitfall 10, UI-06) | [VERIFIED: DriverSheetImportService.java Z. 405-407] | `tabName, year, Integer number, suggestedSeasonId, ambiguousReason, warnings, ...` — verified. |
| A8 | "MatchdayService.findBySeasonId hat keine Production-Caller mehr, nur Tests" | [VERIFIED: grep-result] | `grep -rn` ergab nur ein Vorkommen in MatchdayController via JSON-API `getMatchdaysBySeason` — das ist eine andere Method. `findBySeasonId` ist für D-44-Removal frei. Verifiziert. |
| A9 | "SeasonController-Swiss-Endpoints können auf interner Resolve-zu-REGULAR-Phase umgestellt werden, ohne URL-Bruch" (Wave 3 Task 5) | [ASSUMED] | Konsistent mit D-12 / D-31 / D-41 Backward-Compat-Doktrin. Kein neues Routing-Pattern. |
| A10 | "JaCoCo-Coverage von 82 % wird durch Wave 0 Test-Skeletons + Wave 1-3 Implementation gehalten" | [ASSUMED] | Falls Wave 4 sich Coverage-Drop zeigt, müssen mehr Tests in Wave 0 Iteration nachgepflegt werden. Project memory `feedback_coverage_strategy` warnt explizit davor — 877+ Tests, 82 % Minimum. |

**If this table is empty:** N/A (10 Annahmen, davon 4 verified, 1 cited, 5 reine Assumptions).

---

## Open Questions

1. **Soll bei neuer Saison automatisch eine REGULAR-Phase angelegt werden?**
   - What we know: pure-gem.md Z. 18 sagt "1× **REGULAR**" als hartes Modell. UI-01 nimmt das Auto-Sync raus.
   - What's unclear: Soll `SeasonManagementService.save` selbst eine leere REGULAR-Phase mitanlegen (mit `format=null`) oder soll der User explizit über D-08 Empty-State eine Phase anlegen müssen?
   - Recommendation: Auto-Bootstrap mit `format=null` — User füllt das Format dann via Phase-Edit-Form. Vermeidet Pitfall 1 + D-08 ist nur defensive Sicherheit, nicht Standard-Workflow.

2. **Sollen Phase-Routes auch eigenständig erreichbar sein, oder nur als Sub-Tab in Saison-Detail?**
   - What we know: D-03 sagt `/admin/seasons/{id}/phases/{phaseId}` — eingebettet.
   - What's unclear: Soll `/admin/phases/{phaseId}` als short-cut existieren?
   - Recommendation: NEIN — D-03 ist klar; ein flacher `/admin/phases/{id}` würde die Saison-Hierarchie verstecken.

3. **Was tun bei Saison ohne SeasonTeams aber mit REGULAR-Phase?**
   - What we know: D-26 sagt SeasonTeam-Add füllt PhaseTeam mit. D-20 sagt GROUPS-Phase startet leer.
   - What's unclear: Wenn ein User eine Saison ohne SeasonTeams hat und die REGULAR-Phase auf GROUPS umstellt — der Roster-Editor zeigt eine leere Tabelle ("No teams").
   - Recommendation: Empty-State-Banner "Add teams to season first via the Saison-Header"-Hinweis. Klare User-Guidance.

4. **Wie behandeln wir Standings, wenn die Phase noch keine Matchdays hat?**
   - What we know: D-36 sagt Empty-State-Banner.
   - What's unclear: Soll das Banner einen "+ Generate Matchdays"-CTA haben?
   - Recommendation: Ja, Action-orientiert — verlinke auf `/admin/seasons/{sid}/phases/{pid}/generate` (oder wo auch immer der phase-aware Generate-Endpoint sitzt).

5. **Phase-Edit für PLAYOFF-Phase: kann man phaseType in PLAYOFF→REGULAR umändern?**
   - What we know: D-16 sagt alle Felder editierbar. D-22 sagt Layout-Format-Kompatibilität serverseitig validiert.
   - What's unclear: PhaseType-Wechsel bricht Playoff (FK ist `phase_id`).
   - Recommendation: SeasonPhaseService.update wirft `BusinessRuleException` wenn `oldPhase.phaseType == PLAYOFF` und Playoff-Row existiert + Wechsel auf REGULAR/PLACEMENT versucht wird. Generell sollten phaseType-Wechsel-Cases sehr restriktiv sein.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Eclipse Temurin) | Build | ✓ (per pom.xml) | 25 | — |
| Maven via `./mvnw` | Build | ✓ | wrapper | — |
| Spring Boot 4.x | App | ✓ | 4.x | — |
| MariaDB | local/docker | ✓ | (docker-compose) | H2 in dev |
| H2 | dev/test | ✓ | (in-mem) | — |
| Playwright Chromium | E2E + Visual-Verifikation | conditional | (per pom.xml) | Manual `playwright-cli` install: `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` |
| `playwright-cli` (skill) | Visual verification | per CLAUDE.md required | (skill) | None — CLAUDE.md verlangt es |
| `gh` CLI | Git workflow | ✓ | (system) | — |

**Missing dependencies with no fallback:** none — alle Pflicht-Dependencies sind im Build-System.

**Missing dependencies with fallback:** Playwright Chromium ist conditional; Plan muss in Wave 4 ggf. Install-Step ergänzen falls noch nicht installiert.

---

## Sources

### Primary (HIGH confidence — read in this research)

- `/Users/jegr/Documents/github/ctc-manager/.planning/phases/60-admin-ui/60-CONTEXT.md` — 44 Decisions D-01..D-44
- `/Users/jegr/Documents/github/ctc-manager/.planning/phases/60-admin-ui/60-DISCUSSION-LOG.md` — Audit trail
- `/Users/jegr/Documents/github/ctc-manager/.planning/REQUIREMENTS.md` — UI-01..UI-07 verbatim
- `/Users/jegr/Documents/github/ctc-manager/.planning/ROADMAP.md` — Phase 60 Goal + Success Criteria 1-7
- `/Users/jegr/Documents/github/ctc-manager/.planning/STATE.md` — Cross-phase context
- `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` — UI-Änderungen Z. 125-164 (non-negotiable)
- `/Users/jegr/Documents/github/ctc-manager/CLAUDE.md` — Project Constraints
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/controller/SeasonController.java` — existing pattern
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/controller/StandingsController.java` — existing pattern
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/controller/PlayoffController.java` — 17 endpoints
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/controller/MatchdayController.java`
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java`
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/dto/SeasonForm.java`
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/domain/service/SeasonManagementService.java` — D-25 Auto-Sync-Block (Z. 200-222)
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/domain/service/SeasonPhaseService.java` — Phase 58 D-02/D-14/D-19/D-20
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/domain/service/StandingsService.java` — Phase 58 D-04/D-05/D-06
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/domain/service/MatchdayService.java` — D-26 Dual-API
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — TabPreview record
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/season-detail.html` (293 LOC)
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/standings.html` (230 LOC)
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/driver-import-preview.html` (182 LOC)
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/playoff-bracket.html`
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/template-editors.html` Z. 11-42 — Tab-Pattern-Vorlage
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/static/admin/css/admin.css` Z. 1038-1056, 1740-1748 — Tab-CSS
- `/Users/jegr/Documents/github/ctc-manager/src/test/java/org/ctc/domain/service/PhaseTestFixtures.java`
- Existing tests: `SeasonControllerTest`, `StandingsControllerTest`, `PlayoffControllerTest`, `MatchdayControllerTest`, `SeasonManagementServiceTest`, `SeasonPhaseServiceTest`, `DriverSheetImportControllerTest` — line counts and method enumerations
- Phase 58 + 59 CONTEXT.md — Service-Layer + Import-Test-Data state

### Secondary (MEDIUM confidence)

- Spring Framework documentation (built-in knowledge): AutoPopulatingList, BeanWrapper, indexed-properties — verified against Spring Boot 4.x patterns in existing codebase.
- Thymeleaf 3.x patterns (built-in knowledge): `th:each` + `th:object`/`*{...}` form-binding, `th:errors` field-level, `th:if`/`th:unless` conditionals — all verified against existing templates.

### Tertiary (LOW confidence)

- None — alles Phase-60-Spezifische ist aus den primären Quellen verifiziert.

---

## Project Constraints (from CLAUDE.md)

Diese Direktiven müssen die Pläne/Tasks unangetastet erfüllen:

1. **Test Coverage:** ≥ 82 % Line-Coverage. JaCoCo-Report nach `./mvnw verify`. Keine Coverage-Verluste durch Phase 60 — Wave 0 Tests müssen das ausgleichen.
2. **Flyway:** Phase 60 fügt KEINE neue Migration hinzu. V1+V2+V3+V4 immutable. MIGR-06 Cleanup-Migration ist Phase 61.
3. **Profiles:** Auth nur für `prod`/`docker`; `dev`/`local` ohne Auth.
4. **OSIV:** bleibt aktiv. Templates dürfen Lazy-Felder lesen.
5. **Backward Compatibility:** Keine Breaking Changes zu existierenden URLs/Endpoints. D-12 (`?season=`-Bridge), D-31 (Standings-Legacy), D-41 (Playoff-URL), D-43 (Add-Season-Endpoints) erfüllen das.
6. **Playwright:** bleibt compile-scope.
7. **Thin Controllers:** Alle Controller delegieren an Services. Keine Business-Logik in Controllern.
8. **DTOs in Controllern:** POST-Forms IMMER über Form-DTO + `@ModelAttribute` + `@Valid` + `BindingResult`. Kein direktes Entity-Binding.
9. **No Fallback Calculations:** D-21/D-23/D-25/D-28 strikte Guards statt Cascade-Hacks.
10. **Lean Thymeleaf Templates:** Server-Side-Flags (`combinedView`/`showBuchholz`/`showGroupColumn`/`effectivePhaseLabel`) statt SpEL-Projections.
11. **No Inline Styles on Buttons:** CSS-Klassen aus admin.css. JS-className-Setzungen mitpflegen.
12. **Test Data Isolation:** T-Prefix-Konvention für E2E-Test-Daten. Kein Eingriff in echte Saisons.
13. **RaceLineup Source of Truth:** für Driver-Team-Zuordnungen — RaceLineup vor SeasonDriver.
14. **Language:** Deutsch in Kommunikation; Code/Doku/UI English.
15. **TDD:** Tests first → Implementation. Given-When-Then.
16. **`playwright-cli` PFLICHT** für UI-Änderungen — Desktop + Mobile pro Page.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — alles im Codebase + pom.xml verifiziert.
- Architecture patterns: HIGH — alle Pattern-Vorlagen im Codebase identifiziert (template-editors.html, season-form.html, GlobalExceptionHandler).
- Pitfalls: HIGH — 10 spezifische Pitfalls aus Backend-Code-Analyse + bekannten Spring-Verhalten abgeleitet.
- Code excerpts: HIGH — alle Code-Snippets sind verbatim oder Pseudo-Code based on existing patterns.
- Test strategy: HIGH — alle existing test classes enumeriert + Methoden gezählt.
- Dependency cleanup map: HIGH — `grep`-verifiziert pro `@Deprecated`-Service-Method.
- Wave-split: MEDIUM — abhängig vom Aufwand-Schätzung.

**Research date:** 2026-04-30
**Valid until:** 2026-05-30 (Phase 60 ist eine Pure-UI-Phase auf einer stabilen Backend-API; keine externen Bibliotheks-Updates erwartet)

---

## RESEARCH COMPLETE

**Phase:** 60 - Admin UI
**Confidence:** HIGH

### Key Findings

1. **Backend ist vollständig vorbereitet** (Phase 56-59) — Phase 60 ist eine Pure-UI-Cutover-Phase mit minimalem Service-Layer-Refactor (UI-01 D-25 Auto-Sync-Block-Removal + D-25/D-26 SeasonTeam-PhaseTeam-Synchronisation).
2. **Two-Row Tab-Pattern** ist der zentrale neue UX-Baustein: `tab-btn`/`tab-active`-Klassen aus admin.css existieren bereits (für template-editors.html), eine neue `tabs-secondary`-Klasse + Mobile-`overflow-x: auto`-Media-Query reicht für Group-Sub-Tabs. Server-Routing pro Tab (kein JS).
3. **Spring-Indexed-Properties + AutoPopulatingList** sind das Standard-Pattern für PhaseTeamForm-Roster-Bulk-Save. Alternative: vorinitialisierte List.
4. **D-44 Conservative Cleanup** entfernt nur 6-7 von ~13 `@Deprecated`-Service-Methoden — der Rest braucht andere Konsumenten (Graphics-Services, SiteGenerator, PlayoffController per D-43) und bleibt für Phase 61.
5. **Test-Strategie:** ~7 neue Test-Files + ~6 erweiterte; alle bestehenden ControllerTests müssen für Slim-SeasonForm-Signatur und phaseId-Routes umgeschrieben werden. Kein Backward-Compat-Test-Layer.
6. **playwright-cli MANDATORY:** 7 modifizierte/neue Pages × 2 Viewports = 14 Visual-Verifications.
7. **Empfehlung 4 Waves:** (0) Test-Skeletons (1) Backend Phase+Group CRUD (2) Saison-Detail+Phase/Group-Form-Templates (3) Standings+Importer+Playoff-Cutover (4) Cleanup+Verify. Total ~30 modified + ~14 created files, ~19-27 h.

### File Created

`/Users/jegr/Documents/github/ctc-manager/.planning/phases/60-admin-ui/60-RESEARCH.md`

### Confidence Assessment

| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | pom.xml + Codebase-Patterns alle verifiziert |
| Architecture | HIGH | Two-Row Tabs Pattern + Pattern-Vorlagen identifiziert |
| Pitfalls | HIGH | 10 konkrete Pitfalls aus Code-Analyse |
| Test Strategy | HIGH | Alle existing tests enumeriert |
| Dependency Cleanup | HIGH | grep-verified |
| Wave-Split | MEDIUM | Aufwand-Schätzung Range |

### Open Questions

5 Open Questions in der "Open Questions"-Section dokumentiert — die wichtigste: ob `SeasonManagementService.save` beim Anlegen einer neuen Saison automatisch eine REGULAR-Phase mit `format=null` mitanlegen soll oder den User über D-08 Empty-State zwingen soll. Empfehlung: Auto-Bootstrap.

### Ready for Planning

Research komplett. Planner kann jetzt PLAN.md-Files für die 4 Waves erstellen — alle Code-Patterns, Test-Strategien, Cleanup-Mappings, Pitfalls und Wave-Split sind dokumentiert.
