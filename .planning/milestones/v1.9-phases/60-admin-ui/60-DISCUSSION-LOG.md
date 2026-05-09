# Phase 60: Admin UI - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-29
**Phase:** 60-admin-ui
**Areas discussed:** Saison-Detail-Tabs (UI-01/02), Phase + Group Form-Muster (UI-03/04), Standings-UI + URL-Strategie (UI-05), Importer-Preview + Playoff-Cutover (UI-06/07)

---

## Saison-Detail-Tabs (UI-01/02)

### D-01 — Tab-Sichtbarkeit

| Option | Description | Selected |
|--------|-------------|----------|
| Nur existierende + ein "+ Add Phase"-CTA | Tab pro angelegter Phase + einzelner Add-Button. Sauber, kein Placeholder-Rauschen. | ✓ |
| Alle 3 Phase-Typen mit Add-Placeholders | Drei Tabs (REGULAR/PLAYOFF/PLACEMENT), nicht-existierende disabled mit "+ Add". | |
| Hybrid: Tabs für REGULAR + Buttons für PLAYOFF/PLACEMENT | Zwei verschiedene Patterns nebeneinander. | |

### D-02 — Page-Komposition

| Option | Description | Selected |
|--------|-------------|----------|
| Saison-Header oben + Phase-Tabs darunter | Stamm-Daten + Teams + Cars/Tracks bleiben Saison-weit oberhalb der Tabs. | ✓ |
| Stamm-Daten in REGULAR-Tab eingebettet, Saison-Header minimal | Cars/Tracks in eigenem Asset-Tab. | |
| Zwei-Section-Layout: Saison-Card + separate Phase-Section | Tabs zeigen nur phase-spezifische Daten. | |

### D-03 — Tab-Routing

| Option | Description | Selected |
|--------|-------------|----------|
| Server-Routing /admin/seasons/{id}/phases/{phaseId} | Group-Sub-Tab als zusätzlicher Pfad-Segment. Sauber für Bookmarks. | ✓ |
| Query-Param /admin/seasons/{id}?phase=...&group=... | Eine Controller-Action, alles über Query-Params. | |
| URL-Fragment + JS | Client-side Tab-Wechsel. | |

### D-04 — Inhalt innerhalb Phase-Tab

| Option | Description | Selected |
|--------|-------------|----------|
| Inhalt linear gestapelt: Roster → Matchdays → Standings | Anker-Links für Sprung-Navigation. | ✓ |
| Sub-Sub-Tabs innerhalb Phase-Tab | 3 Tab-Ebenen, schlecht für Mobile. | |
| Standings + Matchdays als eigenständige Pages | Phase-Tab nur Roster + Quick-Links. | |

### D-05 — Tab-Beschriftung

| Option | Description | Selected |
|--------|-------------|----------|
| label > phaseType-Default | phase.label falls non-blank, sonst Default ("Regular Season"). | ✓ |
| Immer phaseType-Default, label als Subtitle | Konsistente Hauptbeschriftung. | |
| Immer label, phaseType nur als Badge | Maximale User-Kontrolle. | |

### D-06 — Add-Phase-CTA

| Option | Description | Selected |
|--------|-------------|----------|
| Navigation zu eigener Form-Page | /admin/seasons/{id}/phases/new. Konsistent mit Codebase. | ✓ |
| Modal-Dialog | Schneller Workflow, aber neue Modal-Infrastruktur nötig. | |
| Inline-Form unten am Tab-Row eingeklappt | Single-Page-Workflow ohne Modal. | |

### D-07 — Action-Buttons-Verteilung

| Option | Description | Selected |
|--------|-------------|----------|
| Saison-Header nur Saison-weite Aktionen, Phase-Aktionen im Phase-Tab | Klare Verantwortungs-Trennung. | ✓ |
| Alle Aktionen im Saison-Header, kontextuell gefiltert | Eng gekoppelt zwischen Header und Tab. | |

### D-08 — Empty-State bei fehlender REGULAR-Phase

| Option | Description | Selected |
|--------|-------------|----------|
| Empty-State-Card mit "+ Add REGULAR Phase"-CTA | Defensive Code-Sicherheit. | ✓ |
| Redirect zur Phase-Form mit phaseType=REGULAR vorausgewählt | Forciert. | |
| BusinessRuleException ("Season has no REGULAR phase") | Defensive Aborterror. | |

### D-09 — URL-Safety bei Phase/Saison-Mismatch

| Option | Description | Selected |
|--------|-------------|----------|
| 404 — EntityNotFoundException | Strikte Validierung, IDOR-Schutz. | ✓ |
| 302-Redirect zu /admin/seasons/{phase.season.id}/phases/{phaseId} | User-freundlicher, leakt aber Saison-ID. | |
| Redirect zu /admin/seasons/{seasonId} (Default-REGULAR) | Maximale Toleranz. | |

### D-10 — sortIndex-Management

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-set on create, nicht UI-änderbar | REGULAR=0, PLAYOFF=10, PLACEMENT=20. | ✓ |
| sortIndex in Phase-Form bearbeitbar | Mehr Flexibilität. | |
| Drag-and-Drop-Reorder im Tab-Row | Maximale UX, aber JS-Aufwand. | |

### D-11 — Mobile-Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Horizontal-Scroll-Tabs | Touch-Scrolling nativ. | ✓ |
| Dropdown auf Mobile | Spart Platz, aber zwei Render-Modi. | |
| Vertikal gestapelt auf Mobile | Bricht Tab-Modell. | |

### D-12 — Legacy /admin/standings?season=

| Option | Description | Selected |
|--------|-------------|----------|
| Bleibt funktional via Auto-Resolve REGULAR-Phase | Bewahrt alle externen Links. | ✓ |
| 302-Redirect zu /admin/standings?phase={regularPhaseId} | Sauber, aber zusätzlicher Roundtrip. | |
| Entfernen — nur ?phase= unterstützt | Verletzt CLAUDE.md "No breaking changes". | |

### D-13 — Page-Title-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| "{season.name} — {phase.label}" | Browser-Tab-Title und h1 zeigen Pfad. | ✓ |
| h1 = Saison-Name, Phase-Name als Subtitle | Visuelle Hierarchie. | |
| Nur Saison-Name, Phase-Tab signalisiert Auswahl | Minimalistisch. | |

### D-14 — Breadcrumbs

| Option | Description | Selected |
|--------|-------------|----------|
| Nur "← Back to Seasons"-Link wie heute | Konsistent mit aktuellem Stil. | ✓ |
| Breadcrumb "Seasons › {Saison} › {Phase} › {Group}" | Vollwertiges Breadcrumb. | |

### D-15 — Active-Phase-Visualisierung

| Option | Description | Selected |
|--------|-------------|----------|
| Active-Saison-Badge bleibt saison-weit | Phase-Tabs sind nicht visuell verändert. | ✓ |
| Active-Phase-Highlight basierend auf Datum | Visuelles Signal, aber Defaults oft leer. | |

---

## Phase + Group Form-Muster (UI-03/04)

### D-16 — Phase-Form-Felder

| Option | Description | Selected |
|--------|-------------|----------|
| Volle Form: Typ, Layout, Format, Scoring, Dates, Rounds, Legs, EventDuration, Label | Vor-Default vom Saison-Format/Scoring. | ✓ |
| Schlank: nur Typ, Layout, Label — Detail-Felder auf separater Edit-Page | Zwei-Schritt-Workflow. | |
| Layout-aware: PLAYOFF nicht in Form (auto-create) | Konsistent mit Phase 58 D-19, aber unklar warum PLAYOFF fehlt. | |

### D-17 — Form-Defaults

| Option | Description | Selected |
|--------|-------------|----------|
| Aus REGULAR-Phase der Saison kopieren | Konsistent mit pure-gem.md "Form-Defaults bequem geerbt". | ✓ |
| Application-weite Defaults | Form ist immer leer. | |
| Aus letzter Phase (jeder Typ separat) | Maximale Smartness, komplexerer Service. | |

### D-18 — Layout-Switch (REGULAR + GROUPS)

| Option | Description | Selected |
|--------|-------------|----------|
| Phase wird mit 0 Groups angelegt; User legt Groups danach an | Sequentieller Workflow. Konsistent mit Phase 58 D-20. | ✓ |
| Form zeigt zusätzlichen Group-Editor inline | One-Pass-Workflow. | |
| Wizard: Phase-Form → Group-Setup-Step → PhaseTeam-Setup-Step | Multi-Step, common case wird aufwändiger. | |

### D-19 — Group-Form

| Option | Description | Selected |
|--------|-------------|----------|
| Two-Step: Group-Form (Name) → Group-Detail mit Roster-Editor | Klar separierte Verantwortungen. | ✓ |
| Single-Form: Group + Teams in einem Form | Schnell, aber Form wird groß. | |
| Nur Group-Form (Name); Roster-Verwaltung im Phase-Tab | Roster-Drift wird zentral verwaltet. | |

### D-20 — Roster-Widget

| Option | Description | Selected |
|--------|-------------|----------|
| Multi-Select-Checkbox-Liste mit Group-Dropdown pro Team | Funktioniert für LEAGUE und GROUPS. | ✓ |
| Drag-and-Drop zwischen Group-Spalten | JS-Komplexität. | |
| Pro-Team-Zeile mit Add-Button + Group-Selector pro Zeile | CRUD-y, kein One-Submit. | |

### D-21 — Edit-Phase-Form

| Option | Description | Selected |
|--------|-------------|----------|
| Alle Felder editierbar; format/layout-Wechsel mit Warning bei Datenkonflikt | Server-side Pre-Check via SeasonPhaseService.update(). | ✓ |
| format/layout/phaseType ge-disabled wenn Phase Daten enthält | canChangeStructure-flag. | |
| Edit-Form zeigt nur sichere Felder; strukturelle nur bei leeren Phasen | "Reset Phase"-Action für Restructure. | |

### D-22 — Validation

| Option | Description | Selected |
|--------|-------------|----------|
| Pflicht: Typ + Layout + Format. Optional: alles andere | Layout-Format-Kompatibilität serverseitig. | ✓ |
| Maximaler Pflicht-Set | User wird gezwungen alle Konfig-Felder zu füllen. | |
| Minimal: nur Typ + Layout | Default-Branches möglicherweise unsicher. | |

### D-23 — Delete-Phase-Guard

| Option | Description | Selected |
|--------|-------------|----------|
| Strict guard via SeasonPhaseService.delete() | Konsistent mit Phase 58 D-18 + No Fallback. | ✓ |
| Cascade-Delete mit Confirm-Dialog | Risiko Datenverlust. | |
| Soft-Delete | Spalten-Komplexität. | |

### D-24 — sortIndex SeasonPhaseGroup

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-increment beim Anlegen, manuell anpassbar in Form | Pragmatisch. | ✓ |
| Drag-and-Drop-Reorder | JS-Komplexität. | |
| Nur alphabetisch | Schlechter für nicht-alphabetische Reihenfolgen. | |

### D-25 — SeasonTeam-Delete

| Option | Description | Selected |
|--------|-------------|----------|
| Strict guard: SeasonTeam-Delete refused wenn PhaseTeam-Rows existieren | Spiegelt Phase 58 D-18. | ✓ |
| Cascade-Delete: SeasonTeam-Delete entfernt automatisch alle PhaseTeam-Rows | Risiko ohne Confirm. | |
| Soft-Reject mit Confirm-Dialog auf UI-Ebene | Server cascadiert dann. | |

### D-26 — SeasonTeam-Add → Auto-PhaseTeam

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, auto-add zur REGULAR-Phase mit group=NULL | Konsistent mit Phase 58 D-20. | ✓ |
| Nein, User muss explizit zuweisen | Mehr Schritte, klare Trennung. | |

### D-27 — Form-Errors

| Option | Description | Selected |
|--------|-------------|----------|
| Field-Level mit BindingResult + Flash-Error für BusinessRule | Konsistent mit existing forms. | ✓ |
| Alle Errors im Banner oben | Schlechte UX bei vielen Form-Feldern. | |
| Inline-JS-Validation mit Submit-Disabled | Doppelt mit Server-Side. | |

### D-28 — Group-Delete-Guard

| Option | Description | Selected |
|--------|-------------|----------|
| Strict guard: refused wenn Group Teams oder Matchdays enthält | BusinessRuleException. | ✓ |
| Cascade: Teams ungrouped, Matchdays gelöscht (mit Confirm) | Risiko. | |
| Group umbenennen statt löschen | Drastisch. | |

---

## Standings-UI + URL-Strategie (UI-05)

### D-29 — Selector-Stil

| Option | Description | Selected |
|--------|-------------|----------|
| Tabs für Phase + Sub-Tabs für Group | Konsistent mit Saison-Detail-Tab-Architektur. | ✓ |
| Dropdowns oben | Saison + Phase + Group nebeneinander. | |
| Kompakter Selector: Saison-Dropdown + Tab-Row für Phase/Group kombiniert | Bei vielen Groups unübersichtlich. | |

### D-30 — Default-View für GROUPS

| Option | Description | Selected |
|--------|-------------|----------|
| Combined-View | User sieht sofort Saisons-weite Sicht. | ✓ |
| Erste Group (sortIndex=0) | Pro-Group-Standings als Hauptfunktion. | |
| Strikt: User muss explizit auswählen | Schlechter UX für Casual. | |

### D-31 — URL-Schema

| Option | Description | Selected |
|--------|-------------|----------|
| /admin/standings?phase=&group= mit Legacy-Bridge ?season= | Beide funktionieren parallel. | ✓ |
| Path-basiert: /admin/standings/{phaseId}/groups/{groupId} | Mehr Routing-Änderung. | |
| Query-Param mit Prefix: ?ref=phase:... | Verwirrend, kein Standard. | |

### D-32 — Combined- vs. Per-Group-Rendering

| Option | Description | Selected |
|--------|-------------|----------|
| Per-Group ohne Group-Spalte; Combined mit Group-Spalte | Klare Visual-Differenz. | ✓ |
| Per-Group mit Group-Header oben; Combined mit farbigen Group-Badges | Group-Farbe heute nicht definiert. | |
| Eine Tabelle mit Combined-Toggle | Identisch zu Option 1. | |

### D-33 — Buchholz-Spalte

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Per-Group bei Swiss-Format-Phase | Combined blendet Spalte aus. | ✓ |
| Immer sichtbar bei Swiss-Format mit Tooltip | Visuell verwirrend. | |
| Immer sichtbar wenn populiert | Combined zeigt Daten ohne Sortier-Relevanz. | |

### D-34 — Saison-Dropdown

| Option | Description | Selected |
|--------|-------------|----------|
| Saison-Dropdown bleibt + ausgewählte Saison rendert Phase-Tabs | Konsistent mit existing UX. | ✓ |
| Kein Saison-Dropdown — erwartet ?phase=/?season= URL-Param | Casual-Browsing schwieriger. | |
| Saison + Phase als zwei Dropdowns; Group wird Tab | Hybrid bricht Tab-Hierarchy. | |

### D-35 — Page-Title

| Option | Description | Selected |
|--------|-------------|----------|
| h1 = "Standings"; Saison-Name + Phase-Name als Sub-Header | Statische h1, dynamische Sub-Header. | ✓ |
| h1 = "{Saison} — {Phase}" dynamisch | Bricht Standard-Page-Title-Pattern. | |

### D-36 — Empty-State

| Option | Description | Selected |
|--------|-------------|----------|
| Tabelle mit allen Teams + 0-Punkten + Hinweis-Banner | Konsistent mit existing Behavior. | ✓ |
| Empty-State-Card mit Action-Links | Action-orientiert. | |
| Mix aus Empty-State + leerer Tabelle | Visuell unschön. | |

---

## Importer-Preview + Playoff-Cutover (UI-06/07)

### D-37 — Tab-Label-Display (Driver-Import-Preview)

| Option | Description | Selected |
|--------|-------------|----------|
| Tab-Header zeigt year_S{number}-Label + Saison-Name | Beide Formate sichtbar. | |
| Nur Saison-Name menschlich, Tab-Name als Subtitle | Lesbarer. | |
| Nur raw Tab-Name (wie heute) | Status quo. | |

**User's choice (free-text):** "Es gibt nur volle Saisons, also 1:1 aus Tab übernehmen"
**Notes:** Raw Sheet-Tab-Name 1:1 anzeigen. Real-world-Sheets enthalten keine Group-Splits (project memory `feedback_real_world_sheet_shape`), keine Transformation nötig.

### D-38 — Manueller Saison-Selector für ambiguous Tabs

| Option | Description | Selected |
|--------|-------------|----------|
| Tab-Header zeigt `<select>`-Dropdown + "Resolve"-Button | Konsistent mit Phase 59 D-03. | |
| Tab disabled mit Banner | Zu strikt. | |
| Beide: Banner + Dropdown | Redundant. | |

**User's choice (free-text):** "Siehe vorherige Antwort, es wird keine ambiguous Namen geben"
**Notes:** Manueller Dropdown wird NICHT gebaut. Real-world-Sheets triggern den ambiguen Fall nicht. Backend-Pfad bleibt; UI rendert nur ein Plain-Banner falls `ambiguousReason` doch auftritt.

### D-39 — TabWarning-Rendering

| Option | Description | Selected |
|--------|-------------|----------|
| Warning-Banner oben im Tab + Inline-Badge auf jeder betroffenen Driver-Zeile | Phase 59 D-08 liefert resolvedGroupName und TabWarning. | ✓ |
| Nur Inline-Badge, kein Banner | User muss durchscrollen. | |
| Nur Banner mit Liste der betroffenen Teams | User muss zuordnen. | |

### D-40 — Group-Spalte in Driver-Preview

| Option | Description | Selected |
|--------|-------------|----------|
| Neue Spalte "Group" zwischen Team und Action, sichtbar nur bei GROUPS-Layout | Konditional (phase.layout == GROUPS). | ✓ |
| Group als Sub-Text unter Team-Name | Mobile-freundlicher, aber schlechter sortierbar. | |
| Group nur als Tooltip auf Team-Namen | Mobile geht das nicht. | |

### D-41 — Playoff-URL

| Option | Description | Selected |
|--------|-------------|----------|
| Existing /admin/playoffs/{id} bleibt; Controller resolved Phase intern | Konsistent mit CLAUDE.md "Backward Compatibility". | ✓ |
| Neue URL /admin/seasons/{seasonId}/phases/{phaseId}/playoff + 302 | Bricht alte Bookmarks. | |
| Playoff wird Tab in Saison-Detail; /admin/playoffs/{id} entfällt | Hoher Refactor-Aufwand. | |

### D-42 — Playoff-CTA-Position

| Option | Description | Selected |
|--------|-------------|----------|
| Auf Saison-Detail-Page als "+ Add Phase" mit phaseType=PLAYOFF | Konsistent mit D-06 (eine zentrale Phase-Form). | ✓ |
| Beide: Saison-Detail UND Playoffs-List-Page | Maximum-Discoverability, doppelte Maintenance. | |
| Nur über Playoffs-List-Page wie heute | Saison-Detail-Tab inkonsistent. | |

### D-43 — Legacy add-season/remove-season-Endpoints

| Option | Description | Selected |
|--------|-------------|----------|
| Endpoints bleiben funktional, im UI versteckt | Backend bleibt @Deprecated-konform. | ✓ |
| Endpoints + UI komplett entfernen | Backward-Compat-Verletzung. | |
| Endpoints bleiben + UI zeigt sie als "Legacy" | Zusätzliches UI für Funktion die nicht genutzt werden soll. | |

### D-44 — Service-Bridge-Cleanup-Aggressivität

| Option | Description | Selected |
|--------|-------------|----------|
| Conservative: nur Overloads deren Controller-Caller in Phase 60 wegfallen | Schritt-für-Schritt. | ✓ |
| Aggressive: alle @Deprecated-Overloads in Phase 60 entfernen | Mehr Refactor-Risiko. | |
| Defer: alle Bridges bleiben in Phase 60, alle Bereinigung in Phase 61 | Phase 60 wird "Templates"-Phase, nicht "Cutover". | |

---

## Claude's Discretion

- Exakte Aufteilung in Sub-Controller (`SeasonPhaseController` vs. SeasonController-Routen).
- Detaillierte CSS-Klassen-Namen für Tab-Implementierung (`tabs-row-1`, `tabs-row-2`, `phase-tab`, `group-sub-tab`).
- Exakte Templates-Dateinamen (`season-phase-form.html`, `season-phase-group-form.html`).
- `SeasonPhaseForm`-DTO Feldnamen + Validierungs-Annotation-Details.
- Wave-Plan-Split (Backend-CRUD-Routes vs. Templates vs. Controller-Cutover).
- Konkrete Test-Strategie pro UI-Anforderung (Mockito + Service-IT + playwright-cli).
- Optimistic Locking auf Phase-Edit (concurrent-edit-Protection) — falls in der Praxis kein Issue, ignoriert.

## Deferred Ideas

- Manueller Saison-Selector-Dropdown für ambigue Tabs (Phase 59 D-03 Versprechen) — explizit nicht gebaut.
- MIGR-06 Drop legacy `Season`-Spalten + `playoff_seasons` M:N-Tabelle — Phase 61.
- PLAYOFF-FUT-01 sub-group-aware Playoff-Brackets — future milestone.
- CONSOL-FUT-01 Saison-Konsolidierung-UI — future milestone.
- IMPORT-FUT-01 Phase-/Group-Override-Spalte im Sheet — future milestone.
- Drag-and-Drop für Tab-/Group-Reorder — D-10/D-24 entscheiden für auto-set.
- Mobile-Dropdown-Navigation statt Horizontal-Scroll — D-11.
- Drag-and-Drop für PhaseTeam-Roster-Editor — D-20.
- "Reset Phase"-Action für format/layout-Wechsel mit existierenden Daten — D-21.
- Soft-Delete für Phasen — D-23.
- Active-Phase-Visualisierung basierend auf Datum — D-15.
- E2E Playwright-Test für GROUPS-Saison-Workflow — Phase 61 (QUAL-02).
- Aggressive Service-Bridge-Cleanup — D-44 Conservative; Phase 61 macht den Rest.
- Cascade-Delete mit Confirm-Dialog — D-23/D-25/D-28 strict guards.
