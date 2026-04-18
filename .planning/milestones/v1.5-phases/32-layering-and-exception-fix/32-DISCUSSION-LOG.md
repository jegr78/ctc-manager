# Phase 32: Layering and Exception Fix - Discussion Log

**Date:** 2026-04-14
**Mode:** --auto (all decisions auto-selected)

## Gray Areas Identified

1. **Layering Fix Strategy** — How to resolve admin imports in domain services
2. **Exception Replacement** — Which domain exceptions replace ResponseStatusException

## Decisions

### Layering Fix Strategy

**Q:** How to fix admin imports in domain services?
**Options:** Move services to admin layer | Introduce domain interfaces | Keep and suppress
**Selected:** Move RaceGraphicService to admin layer, extract TeamCardService call to controller (recommended default)
**Rationale:** RaceGraphicService is purely a delegation facade — it belongs in the admin layer. RaceService's TeamCardService dependency is a cross-layer coordination concern that belongs in the controller.

### Exception Replacement

**Q:** Which domain exception for MatchdayService?
**Options:** EntityNotFoundException + BusinessRuleException | New MatchdayConflictException | Keep ResponseStatusException
**Selected:** EntityNotFoundException for NOT_FOUND, BusinessRuleException for CONFLICT (recommended default)
**Rationale:** Both domain exceptions already exist and are handled by GlobalExceptionHandler. Direct mapping is cleanest.

## Codebase Scout Findings

- `RaceGraphicService.java` — imports LineupGraphicService, OverlayGraphicService, ResultsGraphicService, SettingsGraphicService from admin.service
- `RaceService.java` — imports TeamCardService from admin.service
- `MatchdayService.java` — L132: ResponseStatusException(NOT_FOUND), L140: ResponseStatusException(CONFLICT)
- Domain exceptions exist: EntityNotFoundException, BusinessRuleException, ValidationException
- GlobalExceptionHandler handles both EntityNotFoundException and ResponseStatusException
