# Phase 65: Graphics Services Bridge Migration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-07
**Phase:** 65-graphics-bridge-migration
**Areas discussed:** Bridge-Schicksal, Group-Awareness pro Caller, Test-Strategie GROUPS-Coverage, SwissPairingService.calculateBuchholz Schicksal, TeamCardService Cache-Effekte
**Areas dropped (moot):** Legacy-Fallback (calculateStandingsLegacy already removed in earlier cleanup — codebase scout found 0 occurrences)

---

## Bridge-Schicksal

### Q1 — What happens to the seasonId overload?

| Option | Description | Selected |
|---|---|---|
| Komplett entfernen (Recommended) | Method + private legacy fallback gone. Cleanest cut, closes W-A. | ✓ |
| @Deprecated with concrete removal target | Method stays, Javadoc dokumentiert removal-vNEXT. | |
| Documented convenience | Keep with updated Javadoc, no removal intent. | |

**User's choice:** Komplett entfernen.
**Notes:** ROADMAP SC4 explicitly leaves both options open; user picked the cleaner cut.

### Q2 — How do we treat the ~14 dedicated bridge tests in StandingsServiceTest?

| Option | Description | Selected |
|---|---|---|
| Loeschen | Delete duplicates with bridge. | |
| Auf (phaseId, null) umschreiben | Rewrite all on canonical API. | |
| Du entscheidest beim Plan | Planner decides per-test. | ✓ |

**User's choice:** Du entscheidest beim Plan (Claude's discretion at planning).

### Q3 — Scope: only 5 graphics or all 7 callers (incl. SwissPairing + private helper)?

| Option | Description | Selected |
|---|---|---|
| Alle 7 migrieren (Recommended) | Required for SC1 = 0. | ✓ |
| Nur 5 Graphics, Rest defern | ROADMAP wording literal; SC1 grep relaxed. | |
| Du entscheidest beim Plan | Planner triagieren. | |

**User's choice:** Alle 7 migrieren.
**Notes:** Important codebase-scout finding: ROADMAP only listed 5 callers; codebase has 7. SC1 is achievable only if all 7 migrate.

### Q4 — Migration atomicity / commit structure?

| Option | Description | Selected |
|---|---|---|
| 1 Plan pro Caller-Gruppe + 1 Cleanup (Recommended) | P1 graphics, P2 domain, P3 cleanup. | ✓ |
| 1 Plan, ein grosser Commit | Atomic but hard to localize regressions. | |
| 1 Plan pro Caller (7+1) | Maximum sequencing overhead. | |

**User's choice:** 3-plan structure.

---

## Group-Awareness pro Caller

### Q1 — Group scope for matchday/race callers in GROUPS-layout?

| Option | Description | Selected |
|---|---|---|
| Group-spezifisch wenn vorhanden (Recommended) | matchday.getPhase() + matchday.getGroup() | ✓ |
| Immer Combined-View (groupId=null) | Season-wide consistent look. | |
| Per-Graphic-Entscheidung | Differentiated, more code complexity. | |

**User's choice:** Group-spezifisch wenn vorhanden.

### Q2 — TeamCardService scope?

| Option | Description | Selected |
|---|---|---|
| findRegularPhase + groupId=null (Recommended) | Combined view, byte-identical to bridge. | ✓ |
| findRegularPhase + Team-Group ableiten | PhaseTeam-driven, extra DB query. | |

**User's choice:** findRegularPhase + null.

### Q3 — Helper extraction or inline per caller?

| Option | Description | Selected |
|---|---|---|
| Inline pro Caller (Recommended) | Locality > DRY for trivial 1–2 lines. | ✓ |
| New helper in StandingsService | calculateStandingsForMatchday(...). | |
| Static helper class in admin.service | PhaseResolver.resolveStandingsScope(matchday). | |

**User's choice:** Inline pro Caller.
**Notes:** Introducing a helper while removing another convenience overload would be self-contradictory.

### Q4 — LEAGUE-behavior preservation guard for SC2?

| Option | Description | Selected |
|---|---|---|
| Bestehende LEAGUE-Tests reichen | Existing 7 mocks rewritten = sufficient. | |
| Plus 1 expliziter LEAGUE-Regression-Test (Recommended) | Mockito.verify with isNull() per caller. | ✓ |
| Nichts | Bridge-removal tests are sufficient. | |

**User's choice:** Plus 1 explicit LEAGUE-regression-test (per caller).

---

## Test-Strategie GROUPS-Coverage

### Q1 — Add GROUPS-specific tests after migration?

| Option | Description | Selected |
|---|---|---|
| Pro Caller +1 GROUPS-Test (Recommended) | 5 Graphics × 1 GROUPS-test or representative subset. | ✓ |
| 1 Repraesentations-Test im Plan-03 | Single E2E in cleanup plan via SiteGeneratorPhaseAwarenessIT. | |
| Keine — vertraue 82%-JaCoCo + bestehende GROUPS-E2E | Migration is API-shape only. | |

**User's choice:** Pro Caller +1 GROUPS-Test (refined to 3 representative in Q2).

### Q2 — Coverage surface: all 5 or representative?

| Option | Description | Selected |
|---|---|---|
| Repraesentative 3 (Recommended) | Abstract, Overlay, Settings. | ✓ |
| Alle 5 | Per-caller coverage, code duplication. | |
| Nur 1 — AbstractMatchdayGraphicService | Single proof point. | |

**User's choice:** Repraesentative 3 (Abstract, Overlay, Settings).
**Notes:** LineupGraphic shares Race-pattern with Settings; TeamCardService stays season-only (combined view, GROUPS-equivalent in behavior).

### Q3 — Test style: Mockito vs @SpringBootTest?

| Option | Description | Selected |
|---|---|---|
| Mockito-Unit-Tests wie bestehend (Recommended) | Consistent with existing graphics-test style. | ✓ |
| @SpringBootTest @ActiveProfiles(dev) | Realistic but slower; doubles existing standings coverage. | |
| Mix — Mockito + 1 @SpringBootTest smoke | More test code, more safety. | |

**User's choice:** Mockito unit-tests.

---

## SwissPairingService.calculateBuchholz Schicksal

### Q1 — Public method, 0 production callers, 1 test, functionally duplicated by StandingsService.calculateStandingsWithBuchholz?

| Option | Description | Selected |
|---|---|---|
| Komplett entfernen + Test mit (Recommended) | Dead code out; cleanest cut, lands in Plan-02. | ✓ |
| Auf phase-aware umschreiben + Test anpassen | calculateBuchholz(UUID phaseId, UUID groupId), keep API surface. | |
| Du entscheidest beim Plan | Planner verifies 0-callers, then deletes. | |

**User's choice:** Komplett entfernen + Test mit.

---

## TeamCardService Cache-Effekte

### Q1 — Cache concerns about the migration?

| Option | Description | Selected |
|---|---|---|
| Alles gut — weiter zu CONTEXT.md | Migration data-identical for TeamCard; no path change. | ✓ |
| Card-Pfad sollte phase-aware werden | {phaseId}/{shortName}.png — scope expansion, breaks existing files. | |
| Anderes Cache-Thema | Free-text. | |

**User's choice:** Alles gut.
**Notes:** TeamCardService does NOT use Spring @Cacheable — cards are PNG/SVG files on disk under `data/dev/uploads/{seasonId}/{teamShortName}.png`. ResultsGraphicService and MatchResultsGraphicService consume them via buildCardPath but are NOT calculateStandings callers.

---

## Claude's Discretion

- **D-09 / D-14:** Bridge-test triage at planning time — Q2 of Bridge-Schicksal explicitly delegated.
- **D-15:** Migration wave order within each plan (e.g., simplest caller first) — implicit from "1 Plan pro Caller-Gruppe" decision.
- **D-04a/b:** SwissPairingService removal vs. inlining of calculateBuchholzScores — planner verifies 0 production callers and applies the cleanest path.

## Deferred Ideas

- **Card-path phase-awareness** (`{phaseId}/{shortName}.png` instead of `{seasonId}/{shortName}.png`): user rejected scope expansion. Card files stay seasonId-keyed.
- **Buchholz/playoff edge-case exploration**: not pursued — current tests cover Buchholz separately; playoff branch in `SettingsGraphicService` / `LineupGraphicService` is unaffected by the migration (bypasses calculateStandings entirely via `playoffSeedRepository`).
- **JaCoCo per-caller pre-measurement**: not pursued — project-wide 82% gate via `./mvnw verify` is the binding contract.

## Scope clarifications surfaced during scout

- **MatchdayScheduleGraphicService extends AbstractMatchdayGraphicService** — not a separate caller; covered by the parent's migration.
- **ResultsGraphicService + MatchResultsGraphicService** use `season.getId()` for `buildCardPath` (file system) but NOT for `calculateStandings`. Out of Phase 65 scope.
- **calculateStandingsLegacy** mentioned in Phase 58 SUMMARY as a private fallback — already removed in subsequent cleanup. Confirmed by codebase scout. Discussion area dropped as moot.
