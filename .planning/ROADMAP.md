# Roadmap: CTC Manager

## Milestones

- :white_check_mark: **v1.0 Technical Debt Cleanup** - Phases 1-5 (shipped 2026-04-04)
- :construction: **v1.1 Codebase Concerns Cleanup** - Phases 6-11 (in progress)

## Phases

<details>
<summary>v1.0 Technical Debt Cleanup (Phases 1-5) - SHIPPED 2026-04-04</summary>

- [x] Phase 1: Exception Infrastructure (2/2 plans) - completed 2026-04-03
- [x] Phase 2: Service Layer Extraction (4/4 plans) - completed 2026-04-04
- [x] Phase 3: God Service Split (2/2 plans) - completed 2026-04-04
- [x] Phase 4: Database Optimization (1/1 plan) - completed 2026-04-04
- [x] Phase 5: Security (3/3 plans) - completed 2026-04-04

</details>

### v1.1 Codebase Concerns Cleanup (In Progress)

**Milestone Goal:** Alle identifizierten technischen Concerns aus dem Codebase-Audit systematisch beheben -- von Layer-Violations ueber Security-Hardening bis hin zu Code-Reduktion.

- [ ] **Phase 6: Security Hardening** - Path-traversal and SSRF hostname validation in FileStorageService
- [ ] **Phase 7: Layer Cleanup** - Fix layer violations, remove residual repo access, move StandingsController logic to service
- [ ] **Phase 8: Exception Refinement** - Replace catch(Exception e) with specific catches, constrain unbounded queries
- [ ] **Phase 9: Alltime Standings** - Implement cross-season team aggregation in StandingsService
- [ ] **Phase 10: Service Refactoring** - Split large services and deduplicate TemplateEditorController
- [ ] **Phase 11: Template Quality** - Replace inline styles with CSS utility classes in admin templates

## Phase Details

### Phase 6: Security Hardening
**Goal**: File upload and URL storage operations are protected against path traversal and SSRF attacks
**Depends on**: Nothing (independent of other v1.1 work)
**Requirements**: SECU-01, SECU-02
**Success Criteria** (what must be TRUE):
  1. FileStorageService.storeFromUrl() rejects URLs targeting private IPs, localhost, and internal networks
  2. FileStorageService.store() and storeImage() reject filenames containing path traversal sequences (../, absolute paths)
  3. Existing file upload and URL import functionality continues to work for legitimate inputs
**Plans:** 1 plan

Plans:
- [x] 06-01-PLAN.md — SSRF hostname validation + path traversal protection (TDD)

### Phase 7: Layer Cleanup
**Goal**: Controllers contain no business logic and no direct repository access -- clean three-tier separation
**Depends on**: Phase 6
**Requirements**: ARCH-01, ARCH-02, FEAT-02
**Success Criteria** (what must be TRUE):
  1. Domain services (org.ctc.domain.service) have zero imports from org.ctc.admin.dto
  2. StandingsController, PowerRankingsController, PlayoffController, TeamCardController, and CsvImportController inject only services, no repositories
  3. Buchholz calculation and Swiss-system sorting logic lives in StandingsService, not in StandingsController
  4. All existing admin UI pages render correctly with unchanged behavior
**Plans**: TBD

Plans:
- [ ] 07-01: TBD
- [ ] 07-02: TBD

### Phase 8: Exception Refinement
**Goal**: Exception handling is specific and intentional -- no blanket catch-all blocks hiding real errors
**Depends on**: Phase 7
**Requirements**: ERRH-01, QUAL-02
**Success Criteria** (what must be TRUE):
  1. No catch(Exception e) blocks remain in controllers -- each catch targets a specific exception type (IOException, IllegalStateException, etc.)
  2. Unexpected exceptions propagate to GlobalExceptionHandler and display the admin error page
  3. RaceService, DriverService, and DriverRankingService findAll() calls are scoped by seasonId or have result limits
**Plans**: TBD

Plans:
- [ ] 08-01: TBD

### Phase 9: Alltime Standings
**Goal**: Users can view aggregated team standings across all seasons
**Depends on**: Phase 7 (FEAT-02: StandingsService refactoring)
**Requirements**: FEAT-01
**Success Criteria** (what must be TRUE):
  1. Alltime Standings page displays a ranked list of teams with aggregated cross-season data
  2. StandingsService.calculateAlltimeStandings() returns correct aggregation across multiple seasons
  3. The existing per-season standings remain unchanged
**Plans**: TBD
**UI hint**: yes

Plans:
- [ ] 09-01: TBD

### Phase 10: Service Refactoring
**Goal**: Large service classes and duplicated controller code are split into focused, maintainable units
**Depends on**: Phase 7
**Requirements**: ARCH-03, ARCH-04, ARCH-05
**Success Criteria** (what must be TRUE):
  1. TemplateEditorController uses a generic Map<String, AbstractGraphicService> dispatch instead of 30+ copy-paste blocks
  2. PlayoffService bracket-view and seeding logic are in separate focused services
  3. RaceService form-data assembly and calendar-event logic are in separate focused services
  4. All graphic editing, playoff, and race functionality works identically from the UI
**Plans**: TBD

Plans:
- [ ] 10-01: TBD
- [ ] 10-02: TBD

### Phase 11: Template Quality
**Goal**: Admin templates use consistent CSS classes instead of scattered inline styles
**Depends on**: Nothing (independent)
**Requirements**: QUAL-01
**Success Criteria** (what must be TRUE):
  1. season-detail and race-detail templates contain no inline style attributes (excluding graphic-render templates)
  2. All remaining admin templates have inline styles replaced with CSS utility classes from admin.css
  3. Visual appearance of all admin pages is unchanged (verified via Playwright screenshots)
**Plans**: TBD
**UI hint**: yes

Plans:
- [ ] 11-01: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 6 -> 7 -> 8 -> 9 -> 10 -> 11

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Exception Infrastructure | v1.0 | 2/2 | Complete | 2026-04-03 |
| 2. Service Layer Extraction | v1.0 | 4/4 | Complete | 2026-04-04 |
| 3. God Service Split | v1.0 | 2/2 | Complete | 2026-04-04 |
| 4. Database Optimization | v1.0 | 1/1 | Complete | 2026-04-04 |
| 5. Security | v1.0 | 3/3 | Complete | 2026-04-04 |
| 6. Security Hardening | v1.1 | 0/1 | Not started | - |
| 7. Layer Cleanup | v1.1 | 0/2 | Not started | - |
| 8. Exception Refinement | v1.1 | 0/1 | Not started | - |
| 9. Alltime Standings | v1.1 | 0/1 | Not started | - |
| 10. Service Refactoring | v1.1 | 0/2 | Not started | - |
| 11. Template Quality | v1.1 | 0/1 | Not started | - |
