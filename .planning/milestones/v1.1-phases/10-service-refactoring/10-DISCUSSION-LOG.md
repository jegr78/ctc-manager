# Phase 10: Service Refactoring - Discussion Log (Assumptions Mode)

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions captured in CONTEXT.md — this log preserves the analysis.

**Date:** 2026-04-06
**Phase:** 10-service-refactoring
**Mode:** assumptions
**Areas analyzed:** TemplateEditorController Dispatch Strategy, TemplateEditorController URL Mapping, PlayoffService Split, RaceService Split

## Assumptions Presented

### TemplateEditorController Dispatch Strategy
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| New `TemplateManageable` interface for map dispatch (TeamCardService doesn't extend AbstractGraphicService) | Confident | AbstractGraphicService.java, TeamCardService.java, AbstractMatchdayGraphicService.java |

### TemplateEditorController URL Mapping
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Generic `/{templateType}/save` and `/{templateType}/reset` replacing 20 individual endpoints | Confident | TemplateEditorController.java lines 135-353, preview endpoint already uses this pattern |

### PlayoffService Split
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Split into PlayoffBracketViewService + PlayoffSeedingService, calculateTeamTotals stays in core | Likely | PlayoffService.java (621 lines), three responsibility clusters |

### RaceService Split
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Extract RaceFormDataService (read-only form assembly) + RaceCalendarService (calendar events), core CRUD stays | Likely | RaceService.java (525 lines), GoogleCalendarService isolated dependency |

## Corrections Made

No corrections — all assumptions confirmed.
