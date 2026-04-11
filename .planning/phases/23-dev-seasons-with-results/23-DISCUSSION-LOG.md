# Phase 23: Dev Seasons with Results - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-09
**Phase:** 23-dev-seasons-with-results
**Areas discussed:** Season-Zuordnung, Matchday-Tiefe, Result-Generierung, Team-Verteilung

---

## Season-Zuordnung

| Option | Description | Selected |
|--------|-------------|----------|
| Bestehende umwidmen | S1→RR, S2→Swiss, S4→League. S3a/b bleiben ohne Results. | ✓ |
| Neue Seasons anlegen | 3 komplett neue Seasons mit passenden Formaten | |
| Mix | Teilweise bestehende umwidmen, teilweise neu | |

**User's choice:** Bestehende umwidmen
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| 2 Seasons = 2 Gruppen | Group A und Group B bleiben separate Season-Entities mit format=ROUND_ROBIN | ✓ |
| 1 Season + Gruppen-Info | Eine Season mit Gruppen-Kennzeichnung in den Matchdays | |

**User's choice:** 2 Seasons = 2 Gruppen
**Notes:** None

---

## Matchday-Tiefe

| Option | Description | Selected |
|--------|-------------|----------|
| Minimum (SC-konform) | League: 3, Swiss: 3, RR: 2 pro Gruppe | |
| Realistisch (5+) | League: 5, Swiss: 5, RR: 3 pro Gruppe | ✓ |
| Voll durchgespielt | Komplette Season mit allen Paarungen | |

**User's choice:** Realistisch (5+)
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| 1 Race pro Match | Einfach, genuegt fuer Punkte-Berechnung | |
| 2 Races (Hin + Rueck) | Realistischer, zeigt Leg-Aggregation | |

**User's choice:** Custom — S1 (RR 2023) und S2 (Swiss 2024): 2 Races pro Match. S3 und S4 (League): 1 Race pro Match.
**Notes:** Format-abhaengig: aeltere Seasons mit 2 Legs, aktuelle mit 1 Leg.

| Option | Description | Selected |
|--------|-------------|----------|
| 4 pro Team (8 total) | Genuegt fuer Ergebnislisten | |
| 6 pro Team (12 total) | Nutzt volles Positions-Spektrum | ✓ |
| Alle 10 pro Team | Maximum, aber @Max(12) limitiert | |

**User's choice:** 6 pro Team (12 total)
**Notes:** None

---

## Result-Generierung

| Option | Description | Selected |
|--------|-------------|----------|
| Deterministisch | Feste Positionen pro Race, 100% reproduzierbar | ✓ |
| Algorithmus mit Seed | Pseudo-Zufall mit festem Random-Seed | |
| Rating-basiert | Teams mit hoeherem Rating tendieren zu besseren Positionen | |

**User's choice:** Deterministisch
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Einfache Rotation | Position rotiert leicht zwischen MDs, FL wechselt | ✓ |
| Statisch | Immer gleiche Reihenfolge | |
| Claude's Discretion | Claude entscheidet ueber Verteilung | |

**User's choice:** Einfache Rotation
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| ScoringService nutzen | calculatePoints() + aggregateMatchScores() | ✓ |
| Feste Werte | Punkte direkt im RaceResult | |

**User's choice:** ScoringService nutzen
**Notes:** None

---

## Team-Verteilung

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Parent-Teams | 10 Parents in Gr.A, 10 in Gr.B | |
| Mit Sub-Teams | Mix aus Parents und Subs | ✓ |

**User's choice:** Mit Sub-Teams (Round Robin)
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Parents beibehalten | 10 Parent-Teams fuer Swiss | ✓ |
| Parents + Sub-Teams | Mehr Teams fuer Swiss | |

**User's choice:** Nur Parents beibehalten (Swiss)
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| 5-6 Teams pro Gruppe | Mix aus Parents und Subs pro RR-Gruppe | ✓ |
| Claude's Discretion | Claude teilt sinnvoll auf | |

**User's choice:** 5-6 Teams pro Gruppe
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Subs in der Season | Season-Teams auf 7 Sub-Teams reduzieren | |
| Parents bleiben, nur Subs matchen | 17 bleiben, nur Subs spielen | |
| Alle matchen doch | Alle 17 spielen gegeneinander | |

**User's choice:** Custom — 14 Teams: alle Standalone-Parents + alle Sub-Teams. Parent-Teams mit Subs (VRX, SGM, TBR) nehmen nicht teil.
**Notes:** User clarified: 14 Teams = 7 standalone parents (ADR, ICL, SVT, NFR, EGP, HMS, PWR) + 7 sub-teams (VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G).

---

## Claude's Discretion

- Specific match pairings per matchday
- Exact position assignments per race (with rotation)
- Which 6 of 10 drivers per team play each race
- RaceLineup creation, SeasonDriver assignments for S1/S2
- RaceSettings values

## Deferred Ideas

None
