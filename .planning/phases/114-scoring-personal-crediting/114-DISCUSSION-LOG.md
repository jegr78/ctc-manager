# Phase 114: Scoring & Personal Crediting - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-01
**Phase:** 114-Scoring & Personal Crediting
**Areas discussed:** Team-Zuordnung im Ranking, Alltime & Fahrer-Profil, Additiv & dual role, Idempotenz & Gast-Entfernen, Attribution-Konsistenz, 0-Punkte/DNF/n.a., Test-/Demo-Fixture

---

## Team-Zuordnung im Ranking

### dual role (Roster Team A + Gast Team B) — welches Team in der persönlichen Ranking-Zeile?
| Option | Description | Selected |
|--------|-------------|----------|
| Heimat-Team (Team A) | SeasonDriver-Team der Saison; deterministisch, intuitiv | ✓ |
| Team mit den meisten Races | Häufigstes Team; Tie-Break nötig | |
| Erstes Race (heutiges Verhalten) | Nicht-deterministisch je nach Result-Reihenfolge | |

### Reiner Gast (kein SeasonDriver) — welches Team?
| Option | Description | Selected |
|--------|-------------|----------|
| Einsetzendes Team (via RaceLineup) | RaceLineup-Team, Sub-Team→Parent; immer konkret | ✓ |
| Kein Team / 'Gast'-Label | Neutrale Spalte; eher Phase 115 | |

**Notes:** Policy = home-first, fallback fielding. Für normale Roster-Fahrer keine Änderung.

---

## Alltime & Fahrer-Profil

### Zählt ein Gast-Race ins Alltime-Ranking?
| Option | Description | Selected |
|--------|-------------|----------|
| Ja, zählt mit (Lücke schließen) | Team=null-Gap via RaceLineup schließen | ✓ |
| Nein, nur Saison-Ranking | Müsste Gast-Races aktiv ausfiltern | |

### Profilseite für reinen Gast?
| Option | Description | Selected |
|--------|-------------|----------|
| Ja, Seite erzeugen (Daten-Hook in 114) | Generator-Iteration erweitern; Marking 115 | ✓ |
| Nur Marking-Hook, Erzeugung in 115 | Generator unangetastet in 114 | |

**Notes:** 114 liefert Daten/Seiten-Existenz, visuelles Marking bleibt MARK-06/Phase 115.

---

## Additiv & dual role

### Gast-Race in racesCount/Ø/beste Position oder nur Gesamtpunkte?
| Option | Description | Selected |
|--------|-------------|----------|
| Voll zählen (wie normales Race) | racesCount+1, Ø + beste Position einbezogen | ✓ |
| Nur Gesamtpunkte (Credit-only) | Verzerrt Ø-Punkte; widerspricht No-Fallback | |

### Gast-Race in nicht-gerosterter Phase — ins Saison-Gesamt?
| Option | Description | Selected |
|--------|-------------|----------|
| Ja, alle Saison-Phasen mergen | Heutiges aggregateAcrossPhases-Verhalten | ✓ |
| Nur Phasen mit Roster-Zugehörigkeit | Schließt echte Gast-Einsätze aus | |

---

## Idempotenz & Gast-Entfernen

### Architektur-Lock
| Option | Description | Selected |
|--------|-------------|----------|
| Live-Read-Modell beibehalten | Kein neues Table; idempotent by-design | ✓ |
| Persistiertes Personal-Punkte-Modell | Migration + DualRolecount-Risiko | |

### Test-Scope (Mehrfachauswahl)
| Option | Description | Selected |
|--------|-------------|----------|
| SCORE-01: Gast-Punkte im Team-Score | IT inkl. Sub-Team→Parent | ✓ |
| SCORE-02: reiner Gast (kein SeasonDriver) | Saison-Ranking + additiv | ✓ |
| SCORE-03: Idempotenz & Mehrfach-Save | Kein DualRolecount; Gast-Entfernen | ✓ |
| Alltime + Profilseite für Gast | Team≠null; Profilseite | ✓ |

---

## Attribution-Konsistenz über 3 Pfade

| Option | Description | Selected |
|--------|-------------|----------|
| Einheitlich überall (home-first) | Eine Attribution-Logik über alle 3 Methoden | ✓ |
| Kontextbezogen | Per-Phase zeigt einsetzendes Team; Saison/Alltime Heimat | |

**Notes:** Akzeptierte Kante — per-Phase-Ansicht kann phasenfremdes Heimat-Team zeigen; einfaches Modell bevorzugt.

---

## 0-Punkte / DNF / n.a. Gast

| Option | Description | Selected |
|--------|-------------|----------|
| Sobald eine RaceResult-Zeile existiert | 0/letzter/DNF zählt; reines n.a. erscheint nicht | ✓ |
| Nur bei Punkten > 0 | Ignoriert 0-Punkte-Ergebnisse | |

---

## Test-/Demo-Fixture-Strategie

### Test-Fixture
| Option | Description | Selected |
|--------|-------------|----------|
| Bestehende Fixture erweitern | createFullSeasonFixture/TestDataService um Gast-Szenario | ✓ |
| Bespoke pro IT | Dupliziert Setup, nicht für 115 nutzbar | |

### Demo-Seed
| Option | Description | Selected |
|--------|-------------|----------|
| Ja, Gast in dev,demo-Seed | Echte Daten für 114-Auto-UAT + 115-Visual | ✓ |
| Nein, kein Demo-Seed | Auto-UAT müsste Gast manuell anlegen | |

**Notes:** Seeder bleibt `@Profile("dev")` — nie local/prod/docker.

## Claude's Discretion

- Exakte Signatur/Ort der vereinheitlichten Attribution-Hilfsmethode.
- Ob Alltime-Team-Map und Profil-Generator-Iteration eine gemeinsame "Teilnehmer der Saison X"-Query teilen.
- Konkretes Fixture-Wiring (Entities, IDs, welche Saison-Fixture erweitert wird).

## Deferred Ideas

- Visuelles Gast-Marking über alle Surfaces → Phase 115 (MARK-01..06), gegen Render-Referenz. 114 garantiert nur Daten/Seiten-Existenz.
