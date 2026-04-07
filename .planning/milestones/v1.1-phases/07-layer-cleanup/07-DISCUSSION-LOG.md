# Phase 7: Layer Cleanup - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 07-layer-cleanup
**Areas discussed:** DTO decoupling strategy, Repository removal approach, Buchholz/Swiss integration
**Mode:** Auto (--auto flag)

---

## DTO Decoupling Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Move conversion to controllers | Services accept primitives/entities, controllers map Form→Entity | ✓ |
| Create domain-layer DTOs | New DTO classes in domain layer that mirror admin DTOs | |
| Keep Form DTOs but move to shared package | Move DTOs to a shared module | |

**User's choice:** [auto] Move conversion to controllers (recommended default)
**Notes:** Avoids new DTO layer, aligns with CLAUDE.md principle "DTOs statt Entities in Controllern"

---

## Repository Removal Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Add methods to existing services | Extend SeasonManagementService, TeamManagementService, PlayoffService with finder methods | ✓ |
| Create new facade services | New services per controller (StandingsFacade, etc.) | |
| Use a generic lookup service | Single EntityLookupService for all findById calls | |

**User's choice:** [auto] Add methods to existing services (recommended default)
**Notes:** Simplest path, follows v1.0 Phase 2 pattern. No new service classes needed.

---

## Buchholz/Swiss Integration

| Option | Description | Selected |
|--------|-------------|----------|
| Extend StandingsService with combined method | New calculateStandingsWithBuchholz() that integrates Buchholz + sorting | ✓ |
| Merge into existing calculateStandings() | Add Buchholz as optional parameter to existing method | |
| Create separate SwissStandingsService | Dedicated service for Swiss-format standings | |

**User's choice:** [auto] Extend StandingsService with combined method (recommended default)
**Notes:** Keeps existing calculateStandings() unchanged for non-Swiss formats. Single new method encapsulates the orchestration.

---

## Claude's Discretion

- Method signature design for refactored services
- Method naming (overloaded vs new names)
- Refactoring order (controllers first vs services first)
- Test strategy details

## Deferred Ideas

None — discussion stayed within phase scope.
