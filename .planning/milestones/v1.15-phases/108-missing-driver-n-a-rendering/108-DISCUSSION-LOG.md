# Phase 108: Missing-Driver n/a Rendering - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-30
**Phase:** 108-missing-driver-n-a-rendering
**Areas discussed:** Daten-Repräsentation, "Fehlender Slot"-Definition, n/a Visual-Styling, Teamgröße

---

## Daten-Repräsentation (LINEUP-04 / SC4)

| Option | Description | Selected |
|--------|-------------|----------|
| Render-Padding im Service | 3 Graphic-Services bauen 6-Zeilen-DTO mit "n/a"/0; keine persistierten Placeholder, keine Flyway-Migration; Scoring bleibt korrekt (abwesende Fahrer = 0/keine Position) | ✓ |
| Placeholder-Rows persistieren | Echte 0-Punkte/0-Position-RaceResult-Zeilen schreiben; Flyway-Migration (position nullable / missing-Flag) + Audit aller Standings-/Positions-Queries | |

**User's choice:** Render-Padding im Service
**Notes:** Honoriert CLAUDE.md "No Fallback Calculations" (Fix im Service, nicht im Template). `RaceResult.position` ist `@Min(1)` + NOT NULL → Persistenz wäre überproportional riskant für einen Rendering-Fix.

---

## "Fehlender Slot"-Definition

| Option | Description | Selected |
|--------|-------------|----------|
| "n/a" (Roadmap SC3) | Padding nach Anzahl vorhandener Results; jeder Slot ohne Result = "n/a" | ✓ |
| Echten Namen + 0 Punkte | Gerosterter Fahrer ohne Result behält Namen, zeigt 0 Punkte; "n/a" nur für leere Roster-Slots | |

**User's choice:** "n/a" (Roadmap SC3)
**Notes:** Einheitliche Regel, deckt SC3 ("if fewer than 6 drivers have results, the remaining rows appear with 'n/a'"). Kein per-Slot-Roster-Abgleich nötig.

---

## n/a Visual-Styling

| Option | Description | Selected |
|--------|-------------|----------|
| Dezent ausgegraut/gedimmt | Eigene CSS-Klasse (.empty-slot, reduzierte Opacity), Punkte-Spalte zeigt "0", konsistent über alle 3 Grafiken | ✓ |
| Identisch, nur Text "n/a" | Kein Sonder-Styling | |

**User's choice:** Dezent ausgegraut/gedimmt
**Notes:** Finale Optik wird während Execution visuell geprüft (Screenshot + frei getipptes Feedback) — nicht aus CONTEXT.md fixiert. Keine Inline-Styles (admin.css).

---

## Teamgröße

| Option | Description | Selected |
|--------|-------------|----------|
| Fixe Konstante (6) | Eine zentrale Konstante | (initial) |
| Saison-konfigurierbar | Teamgröße pro Saison lesbar | (initial pick) |
| → Klarstellung: Zentrale Stelle, kein Schema | Konstante/Property, global, kein DB-Change; echte Per-Saison-Config als eigene Phase deferred | ✓ |
| → Klarstellung: Per-Saison jetzt mitziehen | Neues SeasonPhase-Feld + Flyway V17 + Admin-UI in Phase 108 | |
| → Klarstellung: Echte Config als eigene Phase | Phase 108 Konstante; per-Saison als neue Phase | |

**User's choice:** Zentrale Stelle, kein Schema (nach Klarstellung)
**Notes:** User wählte zunächst "saison-konfigurierbar". Codebase-Check ergab: KEIN Teamgrößen-Feld auf `Season`/`SeasonPhase` — echte Per-Saison-Config bräuchte Schema + Flyway + Admin-UI (neue Capability, Scope-Sprengung, V17 kollidiert mit Phase 109). Nach Klarstellung: zentrale Konstante (default `TEAM_DRIVERS=6`) für Phase 108; echte per-Saison-Konfigurierbarkeit als eigene Phase deferred.

---

## Claude's Discretion

- Exakter Ort des Teamgrößen-Werts (Java-Konstante vs `application.yml`-Property) — Default Konstante.
- Exakter `.empty-slot`-Klassenname und CSS-Werte (visueller Checkpoint).
- Optionaler Defensiv-Guard in `ScoringService` für den <6-Fall, falls Research Null-Risiko zeigt.

## Deferred Ideas

- **Per-season configurable team size** — neues `driverSlots`-Feld auf `SeasonPhase`/`Season` (default 6) + Flyway-Migration + Admin-UI; alle Grafiken/Scoring lesen den Saison-Wert. Eigene Phase (v1.15-Backlog oder späteres Milestone). Phase 108 nutzt zentrale Konstante, damit der spätere Umbau lokal bleibt.
