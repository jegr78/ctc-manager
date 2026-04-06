# Phase 13: Layer Cleanup Recovery - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-06
**Phase:** 13-layer-cleanup-recovery
**Areas discussed:** Recovery-Strategie, Scope-Grenze, Test-Strategie, Reihenfolge

---

## Recovery-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Cherry-Pick (Empfohlen) | Phase-7-Commits cherry-picken und Konflikte auflösen. Schnellster Weg. | ✓ |
| Frisch reimplementieren | Phase-7-Entscheidungen als Spec nehmen und neu schreiben. | |
| Hybrid | Cherry-Pick als Ausgangspunkt, bei Konflikten frisch schreiben. | |

**User's choice:** Cherry-Pick (Empfohlen)

| Option | Description | Selected |
|--------|-------------|----------|
| Logische Gruppen | Ein Commit pro Bereich (DTO, Repo, Buchholz). Cleaner History. | |
| Original-Commits | Jeden Phase-7-Commit einzeln cherry-picken. | |
| Du entscheidest | Claude wählt je nach Merge-Konflikt-Situation. | ✓ |

**User's choice:** Du entscheidest

---

## Scope-Grenze zu Phase 7

| Option | Description | Selected |
|--------|-------------|----------|
| 1:1 identisch mit Phase 7 (Empfohlen) | Alle 3 Bereiche: DTO-Decoupling, Repository-Removal, Buchholz-Integration. | ✓ |
| Nur Repository + DTO | Ohne Buchholz-Integration. Kleinerer Blast-Radius. | |
| Minimal: nur DTO-Decoupling | Nur die 10 Domain-Services von admin.dto befreien. | |

**User's choice:** 1:1 identisch mit Phase 7

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, mit aufnehmen (Empfohlen) | CsvImportController hat immer noch SeasonRepository-Injection. | ✓ |
| Nein, auslassen | Nur die 4 Controller unter admin.controller. | |

**User's choice:** Ja, mit aufnehmen — CsvImportController included despite package move

---

## Test-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Tests aus Phase-7 cherry-picken (Empfohlen) | Phase-7-Tests sollten größtenteils passen. Bei Konflikten anpassen. | ✓ |
| Alle Tests neu schreiben | Frische Tests basierend auf aktuellem Code-Stand. | |
| Nur Integrationstests | Nur sicherstellen, dass Seiten noch korrekt rendern. | |

**User's choice:** Tests aus Phase-7 cherry-picken

| Option | Description | Selected |
|--------|-------------|----------|
| verify reicht (Empfohlen) | Unit + Integration Tests prüfen Layer-Trennung. | |
| verify + E2E | ./mvnw verify -Pe2e für komplette Sicherheit. | ✓ |
| Du entscheidest | Claude wählt basierend auf Cherry-Pick-Ergebnis. | |

**User's choice:** verify + E2E — vollständige Verifikation inklusive Playwright

---

## Reihenfolge

| Option | Description | Selected |
|--------|-------------|----------|
| Original Phase-7 Reihenfolge (Empfohlen) | Plan 01: DTO-Decoupling → Plan 02: Repository-Removal → Plan 03: Buchholz. | ✓ |
| Repository-Removal zuerst | Erst Controller von Repos befreien, dann DTO-Decoupling. | |
| Alles in einem Commit | Cherry-Pick aller Commits, als ein Commit squashen. | |

**User's choice:** Original Phase-7 Reihenfolge

---

## Claude's Discretion

- Commit grouping strategy (logical groups vs individual cherry-picks)
- Method signature design for refactored services
- Whether to use overloaded methods or new method names

## Deferred Ideas

None — discussion stayed within phase scope.
