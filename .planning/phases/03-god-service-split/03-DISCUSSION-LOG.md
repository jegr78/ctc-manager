# Phase 3: God Service Split - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 03-god-service-split
**Areas discussed:** Detail-Methode aufteilen, Calendar-Integration, Record-Typen & Testaufteilung, Controller-Rewiring

---

## Detail-Methode aufteilen

| Option | Description | Selected |
|--------|-------------|----------|
| Komplett in RaceService (Empfohlen) | Die Methode bleibt als Ganzes in RaceService. Einfachster Split, keine API-Aenderung am Record. | |
| Aufteilen in zwei Methoden | Core-Daten in RaceService, Grafik-Checks in RaceGraphicService. Sauberer, aber aendert Datenstruktur. | |
| Claude entscheidet | Claude analysiert die Abhaengigkeiten und waehlt die bessere Variante. | ✓ |

**User's choice:** Claude entscheidet
**Notes:** Keine weitere Praeferenz — Claude hat Discretion ueber die Aufteilung von getRaceDetailData().

---

## Calendar-Integration

| Option | Description | Selected |
|--------|-------------|----------|
| In RaceService belassen (Empfohlen) | Calendar als Nebeneffekt von Race-Management. Kein eigener Service fuer eine Methode. | |
| Eigener RaceCalendarService | Saubere Trennung, aber Overengineering fuer eine Methode. | |
| Claude entscheidet | Claude analysiert ob es noch Calendar-Logik gibt die dazukommt. | ✓ |

**User's choice:** Claude entscheidet
**Notes:** Keine weitere Praeferenz — Claude hat Discretion.

---

## Record-Typen & Testaufteilung

| Option | Description | Selected |
|--------|-------------|----------|
| Records bleiben als inner Records (Empfohlen) | Jeder neue Service behaelt seine Records. Tests werden auf drei Testklassen aufgeteilt. | ✓ |
| Records in eigene Dateien | In admin.dto Package auslagern. Konsistenter, aber mehr Dateien. | |
| Claude entscheidet | Claude waehlt basierend auf Konventionen. | |

**User's choice:** Records bleiben als inner Records (Empfohlen)
**Notes:** Explizite Entscheidung fuer inner Records + Test-Aufteilung auf drei Klassen.

---

## Controller-Rewiring

| Option | Description | Selected |
|--------|-------------|----------|
| Controller injiziert alle 3 (Empfohlen) | RaceController bekommt alle drei Services. Jeder Endpoint ruft den zustaendigen Service direkt auf. | ✓ |
| RaceService als Fassade | Controller injiziert nur RaceService, der intern delegiert. Weniger Injections, aber versteckt Abhaengigkeiten. | |
| Claude entscheidet | Claude waehlt basierend auf Thin-Controller-Prinzip. | |

**User's choice:** Controller injiziert alle 3 (Empfohlen)
**Notes:** Explizite Entscheidung fuer transparente 3-Service-Injection im Controller.

---

## Claude's Discretion

- getRaceDetailData() Aufteilung (Core vs. Grafik-Checks)
- createOrUpdateCalendarEvent() Platzierung
- Migrations-Reihenfolge
- DRY-Optimierung der generateXxx()-Methoden

## Deferred Ideas

None — discussion stayed within phase scope.
