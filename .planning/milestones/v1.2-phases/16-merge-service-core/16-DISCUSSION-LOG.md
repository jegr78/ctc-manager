# Phase 16: Merge Service Core - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-07
**Phase:** 16-Merge Service Core
**Areas discussed:** Service-Architektur, Merge-Validierung, PSN-ID Transfer, Merge-Ergebnis

---

## Service-Architektur

| Option | Description | Selected |
|--------|-------------|----------|
| Neuer DriverMergeService | Eigener Service mit eigenem Test. DriverService bleibt fokussiert auf CRUD. | :heavy_check_mark: |
| In DriverService erweitern | Merge-Methode direkt in DriverService. Weniger Klassen, aber DriverService waechst. | |

**User's choice:** Neuer DriverMergeService
**Notes:** Keine

| Option | Description | Selected |
|--------|-------------|----------|
| Eine Transaktion | Alles oder nichts — Rollback bei Fehler. Einfach und sicher. | :heavy_check_mark: |
| Einzelne Transaktionen | Pro FK-Tabelle eigene Transaktion. Komplexer, braucht Recovery-Logik. | |

**User's choice:** Eine Transaktion
**Notes:** Keine

---

## Merge-Validierung

| Option | Description | Selected |
|--------|-------------|----------|
| Self-Merge verhindern | Source == Target -> BusinessRuleException | :heavy_check_mark: |
| Source/Target muessen existieren | EntityNotFoundException Standard-Pattern | :heavy_check_mark: |
| Keine Einschraenkung bei aktiv/inaktiv | Merge erlaubt unabhaengig vom active-Flag | :heavy_check_mark: |
| Source darf keine laufende Season haben | Zusaetzlicher Check gegen aktive Season | |

**User's choice:** Self-Merge verhindern, Source/Target existieren, keine aktiv/inaktiv-Einschraenkung
**Notes:** Inaktive Fahrer sind der primaere Merge-Anwendungsfall

---

## PSN-ID Transfer

| Option | Description | Selected |
|--------|-------------|----------|
| Skip wenn schon vorhanden | Idempotent: Wenn Alias schon existiert, nichts tun. | :heavy_check_mark: |
| Fehler werfen | BusinessRuleException wenn PSN-ID schon als Alias existiert. | |
| Alias ueberschreiben | Bestehenden Alias loeschen und neu anlegen. | |

**User's choice:** Skip wenn schon vorhanden
**Notes:** Keine

| Option | Description | Selected |
|--------|-------------|----------|
| FK umhaengen | alias.setDriver(target) ueber Repository | :heavy_check_mark: |
| Loeschen + neu anlegen | Source-Aliases loeschen, am Target addAlias() | |

**User's choice:** FK umhaengen
**Notes:** Beachte CascadeType.ALL + orphanRemoval — ueber Repository arbeiten, nicht ueber Collection

---

## Merge-Ergebnis

| Option | Description | Selected |
|--------|-------------|----------|
| MergeResult Record | Java Record mit Counts pro FK-Tabelle | :heavy_check_mark: |
| Void mit nur Logging | Methode gibt nichts zurueck, Counts nur im log.info() | |

**User's choice:** MergeResult Record
**Notes:** UI (Phase 18) kann das fuer Success-Message nutzen

---

## Claude's Discretion

- Method signature details (parameter types, exact record field names)
- Internal ordering of FK reassignment steps
- Repository method additions needed for bulk updates

## Deferred Ideas

None — discussion stayed within phase scope
