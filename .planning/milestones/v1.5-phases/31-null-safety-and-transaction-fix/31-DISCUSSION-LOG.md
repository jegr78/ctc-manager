# Phase 31: Null Safety and Transaction Fix - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-13
**Phase:** 31-null-safety-and-transaction-fix
**Areas discussed:** Import-Rollback-Strategie, Null-Safety-Reichweite, Season-Filter-Scope

---

## Import-Rollback-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Validate-then-import | Erst ALLE Team-Paare und Rows validieren. Nur wenn alles valide ist, wird importiert. Ein einziger Fehler → kein Import, alle Fehler zurückgemeldet. | ✓ |
| Fail-fast mit Rollback | Beim ersten Fehler sofort eine ValidationException werfen. @Transactional rollt alles zurück. Nachteil: User sieht nur den ersten Fehler pro Versuch. | |
| Partial Import beibehalten | Aktuelles Verhalten: Fehlerhafte Team-Paare überspringen, valide importieren. Widerspricht DATA-01 (all-or-nothing). | |

**User's choice:** Validate-then-import
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, vollständig | Duplikat-Check ist Teil der Validierung. Wenn overwrite=false und ein Match existiert, zählt das als Validierungsfehler → gesamter Import abgebrochen. | ✓ |
| Nein, nur Auflösung | Duplikat-Handling bleibt in der Import-Phase. Validierung prüft nur Team/Driver-Auflösbarkeit. | |

**User's choice:** Ja, vollständig
**Notes:** None

---

## Null-Safety-Reichweite

| Option | Description | Selected |
|--------|-------------|----------|
| Bekannte Pfade fixen | Nur die 3-4 identifizierten NPE-Stellen absichern (RaceFormDataService, ScoringService). Gezielt, überschaubar, direkt an den Success Criteria orientiert. | ✓ |
| Systematischer Audit | Alle getHomeTeam()/getAwayTeam()-Aufrufe in domain.service durchgehen und absichern. Gründlicher, aber größerer Scope. | |

**User's choice:** Bekannte Pfade fixen
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Safe Defaults | Leere/neutrale Werte zurückgeben: leere Ergebnis-Listen, Score 0, überspringen bei populateDrivers. Kein Crash, kein Fehler-Log — Byes sind normaler Betrieb. | ✓ |
| Early return mit Log | Method-level Early Return mit log.debug() Hinweis. Macht im Log sichtbar, dass ein Bye übersprungen wurde. | |

**User's choice:** Safe Defaults
**Notes:** None

---

## Season-Filter-Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Nur ScoringService | Nur ScoringService.isDriverInTeam() fixen — die einzige Stelle die ohne Season-Filter Scoring-Ergebnisse beeinflusst. Andere Fallbacks sind Display-only. | ✓ |
| Alle Fallback-Stellen | Auch TeamManagementService, RaceFormDataService und SiteGeneratorService mit Season-Filter versehen. | |
| Claude entscheidet | Researcher und Planner identifizieren alle betroffenen Stellen und entscheiden basierend auf Risiko. | |

**User's choice:** Nur ScoringService
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Aus Race ableiten | Race → Matchday → Season. Kein Methoden-Signatur-Änderung nötig — die Methode hat bereits raceId, daraus kann intern die Season aufgelöst werden. | ✓ |
| seasonId als Parameter | Methoden-Signatur erweitern um UUID seasonId. Sauberer, aber alle Aufrufer müssen angepasst werden. | |

**User's choice:** Aus Race ableiten
**Notes:** None

---

## Claude's Discretion

- Internal structure of the validation phase in `executeImport()` (private method extraction, error collection pattern)
- Whether to load Race entity in `isDriverInTeam()` via repository lookup or pass through existing context
- Test structure and naming for new validation and null-safety tests

## Deferred Ideas

None — discussion stayed within phase scope
