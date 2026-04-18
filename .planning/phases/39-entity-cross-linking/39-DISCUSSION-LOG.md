# Phase 39: Entity Cross-Linking - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-16
**Phase:** 39-entity-cross-linking
**Areas discussed:** Fahrerliste auf Team-Profil, Index-Seite Scope, Link-Styling

---

## Fahrerliste auf Team-Profil

### Driver Info

| Option | Description | Selected |
|--------|-------------|----------|
| Kompakt | PSN-ID (als Link) + Gesamtpunkte in der Season. Passt zum minimalistischen Design. | ✓ |
| Detailliert | PSN-ID (als Link) + Races, Best Pos, Avg Points, Total Points — ähnlich Driver-Ranking-Tabelle. | |
| Minimal | Nur PSN-ID als Link, ohne weitere Stats. Reine Navigation. | |

**User's choice:** Kompakt (Empfohlen)
**Notes:** Passt zum bestehenden minimalistischen Design der Site.

### Datenquelle

| Option | Description | Selected |
|--------|-------------|----------|
| SeasonDriver | Bereits in generateTeamProfiles() verfügbar über seasonDriverRepository. Konsistent mit generateDriverProfiles(). | ✓ |
| RaceLineup-basiert | Fahrer, die tatsächlich Rennen für das Team gefahren sind. Genauer, aber komplexere Query. | |

**User's choice:** SeasonDriver (Empfohlen)
**Notes:** Konsistenz mit bestehendem Code bevorzugt.

---

## Index-Seite Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, konsistent | Standings-Tabelle und Last-Matchday-Ergebnisse auf Index-Seite bekommen dieselben Links. | ✓ |
| Nur Detailseiten | Nur standings.html, driver-ranking.html und matchday.html bekommen Links. | |
| Claude entscheidet | Claude beurteilt, was UX-seitig am sinnvollsten ist. | |

**User's choice:** Ja, konsistent (Empfohlen)
**Notes:** Konsistentes Verhalten überall — egal wo der User auf einen Teamnamen klickt.

---

## Link-Styling

| Option | Description | Selected |
|--------|-------------|----------|
| Akzentfarbe | Links in --accent (#4fc3f7, hellblau). Klar erkennbar als klickbar. Hover: heller/underline. | ✓ |
| Dezent (Hover-only) | Links erben aktuelle Textfarbe. Erst bei Hover Akzentfarbe + Underline. | |
| Claude entscheidet | Claude wählt den Stil basierend auf dem bestehenden Design. | |

**User's choice:** Akzentfarbe (Empfohlen)
**Notes:** Nutzt die bereits definierte --accent Variable. Klare visuelle Erkennbarkeit.

---

## Claude's Discretion

- Exact CSS hover properties (opacity, text-decoration style, transition)
- Whether to extend RaceView.ResultView with slug fields or pass a separate map
- Internal refactoring of generateTeamProfiles() for driver data loading
- Whether to create a DriverEntry record for team profile data

## Deferred Ideas

None — discussion stayed within phase scope
