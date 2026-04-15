# Phase 29: Mass Assignment Fix - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-13
**Phase:** 29-mass-assignment-fix
**Areas discussed:** SeasonId-Platzierung, Service-Signatur, Template-Binding

---

## SeasonId-Platzierung

| Option | Description | Selected |
|--------|-------------|----------|
| Ins DTO (Empfohlen) | seasonId als UUID-Feld im MatchdayForm. Konsistent mit anderen Forms (z.B. RaceForm). Ein Objekt fuer alle Form-Daten. | ✓ |
| Separater @RequestParam | seasonId bleibt @RequestParam wie bisher. Minimale Aenderung, aber inkonsistent mit dem DTO-Pattern. | |

**User's choice:** Ins DTO
**Notes:** Konsistenz mit bestehendem DTO-Pattern priorisiert.

---

## Service-Signatur

| Option | Description | Selected |
|--------|-------------|----------|
| Einzelwerte beibehalten | Service bleibt unabhaengig von admin.dto. Controller mappt DTO zu Einzelwerten. Saubere Layer-Trennung. | ✓ |
| DTO als Parameter | Service nimmt MatchdayForm direkt. Weniger Mapping-Code, aber admin.dto Import in domain.service verletzt Layering. | |

**User's choice:** Einzelwerte beibehalten
**Notes:** Layer-Trennung (domain.service kennt kein admin.dto) wichtiger als Convenience. Passt zu ARCH-01 Fix in Phase 32.

---

## Template-Binding

| Option | Description | Selected |
|--------|-------------|----------|
| Season separat ins Model (Empfohlen) | Controller laedt Season-Entity als separates Model-Attribut. Template nutzt ${season.displayLabel}. | ✓ |
| seasonName ins DTO | MatchdayForm bekommt zusaetzliches seasonName-Feld fuer Anzeige. DTO hat Display-Daten die nicht zum Form gehoeren. | |

**User's choice:** Season separat ins Model
**Notes:** Saubere Trennung: DTO nur fuer Form-Daten, Display-Daten als separate Model-Attribute.

---

## Claude's Discretion

- Validation annotations auf MatchdayForm (ueber @NotBlank auf label hinaus)
- Template hidden field handling fuer seasonId
- Test-Struktur und -Benennung

## Deferred Ideas

None — discussion stayed within phase scope
