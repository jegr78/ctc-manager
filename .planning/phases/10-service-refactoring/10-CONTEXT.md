# Phase 10: Service Refactoring - Context

**Gathered:** 2026-04-06 (assumptions mode)
**Status:** Ready for planning

<domain>
## Phase Boundary

Large service classes and duplicated controller code are split into focused, maintainable units. TemplateEditorController uses generic dispatch, PlayoffService and RaceService are split into focused services. All existing functionality works identically from the UI.

</domain>

<decisions>
## Implementation Decisions

### TemplateEditorController Dispatch Strategy
- **D-01:** Introduce a `TemplateManageable` interface with `loadTemplate()`, `saveTemplate(String)`, `resetTemplate()`, `hasCustomTemplate()` methods. All graphic services that support template editing implement this interface, including `TeamCardService` (which does not extend `AbstractGraphicService`).
- **D-02:** Use Spring's `Map<String, TemplateManageable>` autowiring to build the dispatch map. The map key is the bean name (e.g., `lineupGraphicService`), mapped to a URL-friendly template type string.

### TemplateEditorController URL Mapping
- **D-03:** Replace all 20 individual save/reset endpoint methods (10 save + 10 reset) with two generic endpoints: `@PostMapping("/{templateType}/save")` and `@PostMapping("/{templateType}/reset")` using `@PathVariable` dispatch into the service map.
- **D-04:** Tab parameter for redirects is derived from the `templateType` path variable — the existing tab names already match URL segments. The preview endpoint already uses this generic approach.

### PlayoffService Split
- **D-05:** Extract `PlayoffBracketViewService` containing bracket view assembly (getBracketView, buildMatchupView, view record types) and `PlayoffSeedingService` containing seeding logic (seedTeam, autoSeedBracket, saveSeed, saveSeedNumbers, getSeedingData).
- **D-06:** Shared helper `calculateTeamTotals` stays in the core `PlayoffService` and is injected into `PlayoffBracketViewService` — no duplication.
- **D-07:** Remaining matchup lifecycle methods (create, advance winner, delete) stay in `PlayoffService`.

### RaceService Split
- **D-08:** Extract `RaceFormDataService` for form assembly methods (getNewRaceFormData, getRaceFormData, getResultsFormData, toRaceData, populateDrivers, getUsedCarIds, getUsedTrackIds) — read-only, no side effects.
- **D-09:** Extract `RaceCalendarService` for calendar methods (createOrUpdateCalendarEvent, resolveEventDuration) — isolates GoogleCalendarService dependency.
- **D-10:** Core CRUD (saveRace, saveResults, deleteRace, quickScore, getRaceListData, getRaceDetailData) stays in `RaceService`. Calendar-checks in `getRaceDetailData` resolved by injecting `RaceCalendarService` into `RaceService`.

### Claude's Discretion
- Exact naming of the template type mapping (bean name substring vs. explicit map configuration)
- Internal method distribution when boundary cases arise during implementation
- Test organization for the new services

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### TemplateEditorController (ARCH-03)
- `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` — Current 30+ copy-paste blocks to be replaced with generic dispatch
- `src/main/java/org/ctc/admin/service/AbstractGraphicService.java` — Base class for Playwright rendering (no template methods)
- `src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java` — Has loadTemplate/saveTemplate/resetTemplate/hasCustomTemplate
- `src/main/java/org/ctc/admin/service/AbstractPlayoffRoundGraphicService.java` — Has loadTemplate/saveTemplate/resetTemplate/hasCustomTemplate
- `src/main/java/org/ctc/admin/service/TeamCardService.java` — Standalone service (does not extend AbstractGraphicService) with own template methods

### PlayoffService (ARCH-04)
- `src/main/java/org/ctc/domain/service/PlayoffService.java` — 621 lines, three responsibility clusters to split
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — Consumer of PlayoffService methods

### RaceService (ARCH-05)
- `src/main/java/org/ctc/domain/service/RaceService.java` — 525 lines, form-data assembly and calendar logic to extract
- `src/main/java/org/ctc/admin/controller/RaceController.java` — Consumer of RaceService methods
- `src/main/java/org/ctc/dataimport/GoogleCalendarService.java` — Calendar API client used by calendar methods

### Conventions
- `.planning/codebase/CONVENTIONS.md` — Naming patterns, Lombok usage, service patterns
- `.planning/codebase/STRUCTURE.md` — Package structure, where to add new code

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AbstractGraphicService`: Base with Playwright rendering, base64 encoding, file I/O — foundation for graphic services
- `AbstractMatchdayGraphicService` and `AbstractPlayoffRoundGraphicService`: Already have template management methods that can inform the `TemplateManageable` interface contract
- Spring `Map<String, T>` autowiring: Standard Spring idiom for service dispatch, already used conceptually in the preview endpoint

### Established Patterns
- Services use `@Service @RequiredArgsConstructor @Slf4j` — new extracted services follow this
- Domain services in `org.ctc.domain.service`, admin services in `org.ctc.admin.service`
- Java records for service API contracts (established in Phase 7)
- `@Transactional(readOnly = true)` for read-only methods

### Integration Points
- `TemplateEditorController` → all graphic services (currently direct injection, will become map lookup)
- `PlayoffController` → `PlayoffService` (will need to inject new bracket/seeding services)
- `RaceController` → `RaceService` (will need to inject new form-data/calendar services)
- `GoogleCalendarService` → currently injected in RaceService, moves to RaceCalendarService

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — analysis stayed within phase scope

</deferred>

---

*Phase: 10-service-refactoring*
*Context gathered: 2026-04-06*
