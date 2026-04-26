# Phase 56: Model & Schema Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 56-model-schema-foundation
**Areas discussed:** Entity-Java-Scope, phase_id Nullability + Cascade, DB-Constraint für „max 1 REGULAR pro Saison", Enum-Strategie

---

## Entity-Java-Scope (additiv vs. invasiv)

| Option | Description | Selected |
|--------|-------------|----------|
| B: Parallel additiv | Neue Entities + neue Felder daneben (`Season.phases`, `Matchday.phase`, `Playoff.phase`); alte Felder/FKs unangetastet; Code kompiliert weiter; Phase 57 füllt Felder, Phase 58 Services um, Phase 61 alte Felder weg. | ✓ |
| A: Rein additiv (kein Touch auf Bestand) | Nur neue Entities + Tabellen; keine `Season.phases`-Collection, kein `Matchday.phase`. Phase 57 müsste Bridge-Felder zusätzlich einführen → Phase 57 wird größer. | |
| C: Aggressiv (ROADMAP-SC3 wörtlich) | Season-Felder format/scoring/dates/legs sofort entfernen → bricht 12+ Services und Templates → Phase 56 müsste Service-Umbau (Phase 58 Scope) miterledigen. | |

**User's choice:** B — Parallel additiv (Recommended)
**Notes:** Bestätigt als sicherster Pfad. Phase-Trennung bleibt sauber: 56 = Schema, 57 = Daten, 58 = Services, 61 = Cleanup. ROADMAP-Phase-56-SC3-Wording („Season entity no longer carries…") wird umgedeutet auf Phase 61.

---

## Bidirektionale Beziehungen + phase_id-Nullability

| Option | Description | Selected |
|--------|-------------|----------|
| NULLABLE in 56 → NOT NULL in 57 | `matchday.phase_id` und `playoff.phase_id` als NULLABLE in Phase 56; Phase 57 füllt + flippt im selben Schritt auf NOT NULL. ON DELETE RESTRICT (DB-FK Default), JPA `orphanRemoval=true`. | ✓ |
| NULLABLE in 56, NOT NULL erst in 61 | Längere unsichere Periode wo neue Inserts theoretisch ohne phase_id durchgehen könnten; Service-Validierung müsste das absichern. | |
| NOT NULL ab Phase 56 mit DEFAULT-Insert-Trick | Vermischt 56 + 57; widerspricht Phasentrennung. | |

**User's choice:** NULLABLE in 56 → NOT NULL in 57 (Recommended)
**Notes:** Klarer Übergang; Phase 57 ist ohnehin die Daten-Migration und kann den NOT-NULL-Switch als finalen Schritt seiner Migration mitmachen. Cascade-Verhalten matcht das bestehende `Season.matchdays`-Pattern (`@OneToMany cascade=ALL orphanRemoval=true`).

---

## DB-Constraint für „max 1 REGULAR pro Saison"

| Option | Description | Selected |
|--------|-------------|----------|
| UNIQUE (season_id, phase_type) | Einfache UNIQUE-Constraint; max 1× je phase_type pro Saison; H2 + MariaDB portabel; deckt SC4 vollständig ab. | ✓ |
| UNIQUE (season_id, phase_type) + CHECK-Constraint | Zusätzlich `CHECK (phase_type IN ('REGULAR','PLAYOFF','PLACEMENT'))`; doppelte Sicherung gegen falsche String-Werte; eigentlich überflüssig wegen `@Enumerated(EnumType.STRING)`. | |
| Nur Service-Layer, kein DB-Constraint | Validierung im SeasonPhaseService (Phase 58); widerspricht SC4. | |

**User's choice:** UNIQUE (season_id, phase_type) (Recommended)
**Notes:** JPA `@Enumerated(EnumType.STRING)` plus typed Enum schützt vor falschen Werten — kein zusätzlicher CHECK nötig. Service-Layer-Guard kann in Phase 58 als sanity layer ergänzt werden, ist aber nicht Phase-56-Verantwortung.

---

## SeasonFormat-Enum-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| SeasonFormat reusen, PhaseType + PhaseLayout neu | Bestehendes `SeasonFormat` (LEAGUE/SWISS/ROUND_ROBIN) bleibt; auf `SeasonPhase.format` referenzieren; kein Java-Refactor. Neue `PhaseType` + `PhaseLayout` als separate Top-Level-Files. | ✓ |
| SeasonFormat → PhaseFormat umbenennen + 2 neue Enums | Java-Rename (DB-String-Werte unverändert); saubereres Naming aber Refactor-Touch in mehreren Files; PhaseType + PhaseLayout neu wie oben. | |
| Inner-Enums auf SeasonPhase | `SeasonPhase.PhaseType` und `SeasonPhase.Layout` nested; bricht Konvention (alle bestehenden Enums sind Top-Level). | |

**User's choice:** SeasonFormat reusen, PhaseType + PhaseLayout neu (Recommended)
**Notes:** Kein unnötiger Refactor; semantischer Match zwischen Saison-Format und Phase-Format ist gegeben. Neue Enums folgen Konvention (separate Files in `domain.model`, analog zu `SeasonFormat.java` und `AttachmentType.java`).

---

## Claude's Discretion

Bereiche wo der Planner Flexibilität hat:

- **Anzahl Flyway-Migration-Files** — Single V3 oder V3 (Phase-Tabellen) + V4 (FK-Spalten auf matchdays/playoffs). Beide akzeptabel.
- **`@EntityGraph`-Annotationen** auf neuen Repositories — Phase 56 hat noch keine Collection-Returning-Methoden; Planner darf simple Defaults seedne wenn Tests es brauchen.
- **Cascade-Detail auf `Season.phases`** — Default `CascadeType.ALL + orphanRemoval=true` mirrort `Season.matchdays`; Planner darf einschränken wenn konkreter Grund.
- **Test-Scope für Phase 56** — Empfehlung: `@DataJpaTest`-Integrationstests für (a) Persist+Reload aller drei Entities, (b) UNIQUE-Constraint-Verletzungen, (c) Flyway-Migration grün auf H2. Planner finalisiert.

## Deferred Ideas

- **Drop alter `seasons`-Spalten** (format, total_rounds, legs, event_duration_minutes, start_date, end_date, race_scoring_id, match_scoring_id) und Removal aus Java-Entity → Phase 61 (MIGR-06).
- **Drop M:N `playoff_seasons`-Join-Table** und `Playoff.seasons`-Collection → Phase 61.
- **NOT-NULL-Flip auf `matchdays.phase_id` / `playoffs.phase_id`** → Phase 57 nach Backfill.
- **Custom Phase-/Group-aware Repository-Finder** (findBySeasonAndPhaseType, etc.) → Phase 58.
- **`SeasonPhaseService`, Controller, Forms, Templates** → Phasen 58 und 60.
- **Service-Layer-Guard gegen Duplicate REGULAR/PLAYOFF/PLACEMENT** → Phase 58 (`SeasonPhaseService.create()`).
- **Sub-Group-aware Playoff-Brackets** (PLAYOFF-FUT-01) → Future Milestone.
- **UI-Konsolidierung alter Group-Workaround-Saisons** (CONSOL-FUT-01) → Future Milestone.
- **Phase-/Group-Override im Driver-Import** (IMPORT-FUT-01) → bewusst nicht eingeführt.
