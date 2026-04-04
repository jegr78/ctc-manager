# Phase 2: Service Layer Extraction - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-03
**Phase:** 02-service-layer-extraction
**Areas discussed:** Service-Schnitt, Season Mega-Controller

---

## Service-Schnitt

| Option | Description | Selected |
|--------|-------------|----------|
| Bestehende erweitern | TeamManagementService, DriverService etc. ergaenzen | |
| Neue Services | CarService, TrackService etc. als eigene Klassen | |
| Hybrid | Bestehende erweitern wo moeglich, neue nur wo kein Service existiert | ✓ |
| You decide | Claude analysiert was am besten passt | |

**User's choice:** Hybrid

| Option | Description | Selected |
|--------|-------------|----------|
| TeamMgmtService | Alles in TeamManagementService | |
| Aufteilen | TeamManagementService + TeamColorService | |
| You decide | Claude analysiert die Methoden | ✓ |

**User's choice:** You decide

---

## Season Mega-Controller

| Option | Description | Selected |
|--------|-------------|----------|
| SeasonMgmtService | Alles in bestehenden SeasonManagementService | ✓ |
| Aufteilen | SeasonManagementService + SeasonPoolService | |
| You decide | Claude analysiert | |

**User's choice:** SeasonMgmtService

| Option | Description | Selected |
|--------|-------------|----------|
| Max 3 | Streng | |
| Max 5 | Pragmatisch | |
| You decide | Claude entscheidet | ✓ |

**User's choice:** You decide

---

## Claude's Discretion

- TeamController Aufteilung
- Max Service-Injections pro Controller
- Migrations-Reihenfolge
- Test-Strategie
- Package/Naming (vorgegeben: domain.service, EntityService Naming)

## Deferred Ideas

None
