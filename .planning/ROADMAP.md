# Roadmap: CTC Manager

## Milestones

- :white_check_mark: **v1.0 Technical Debt Cleanup** — Phases 1-5 (shipped 2026-04-04)
- :white_check_mark: **v1.1 Codebase Concerns Cleanup** — Phases 6-15 (shipped 2026-04-07)
- :white_check_mark: **v1.3 English Test Data** — Phases 20-27 (shipped 2026-04-10)
- :construction: **v1.5 Code Review Fixes** — Phases 28-34 (in progress)

## Phases

<details>
<summary>v1.0 Technical Debt Cleanup (Phases 1-5) -- SHIPPED 2026-04-04</summary>

- [x] Phase 1: Exception Infrastructure (2/2 plans) -- completed 2026-04-03
- [x] Phase 2: Service Layer Extraction (4/4 plans) -- completed 2026-04-04
- [x] Phase 3: God Service Split (2/2 plans) -- completed 2026-04-04
- [x] Phase 4: Database Optimization (1/1 plan) -- completed 2026-04-04
- [x] Phase 5: Security (3/3 plans) -- completed 2026-04-04

</details>

<details>
<summary>v1.1 Codebase Concerns Cleanup (Phases 6-15) -- SHIPPED 2026-04-07</summary>

- [x] Phase 6: Security Hardening (1/1 plan) -- completed 2026-04-04
- [x] Phase 7: Layer Cleanup (3/3 plans) -- completed 2026-04-05
- [x] Phase 8: Exception Refinement (2/2 plans) -- completed 2026-04-05
- [x] Phase 9: Alltime Standings (1/1 plan) -- completed 2026-04-05
- [x] Phase 10: Service Refactoring (3/3 plans) -- completed 2026-04-06
- [x] Phase 11: Template Quality (3/3 plans) -- completed 2026-04-06
- [x] Phase 12: Security Hardening Recovery (1/1 plan) -- completed 2026-04-06
- [x] Phase 13: Layer Cleanup Recovery (3/3 plans) -- completed 2026-04-06
- [x] Phase 14: Exception Refinement Recovery (2/2 plans) -- completed 2026-04-07
- [x] Phase 15: Alltime Standings Recovery (1/1 plan) -- completed 2026-04-07

See: milestones/v1.1-ROADMAP.md for full details

</details>

<details>
<summary>v1.3 English Test Data (Phases 20-27) -- SHIPPED 2026-04-10</summary>

- [x] Phase 20: English Messages -- completed 2026-04-08
- [x] Phase 21: English Code -- completed 2026-04-09
- [x] Phase 22: Dev Teams & Drivers -- completed 2026-04-09
- [x] Phase 23: Dev Seasons with Results -- completed 2026-04-10
- [x] Phase 24: Restore Fictive Dev Data -- completed 2026-04-10
- [x] Phase 25: Fix I18N Regressions -- completed 2026-04-10
- [x] Phase 26: Restore Fictive Team Logos -- completed 2026-04-10
- [x] Phase 27: Restore Matchday/Result Seed Pipeline -- completed 2026-04-10

</details>

### v1.5 Code Review Fixes (Phases 28-34)

- [x] **Phase 28: RaceAttachment Security** - Path traversal defense, null content-type handling, header injection prevention in RaceAttachmentService (completed 2026-04-13)
- [ ] **Phase 29: Mass Assignment Fix** - Replace direct JPA entity binding on MatchdayController with MatchdayForm DTO
- [ ] **Phase 30: CSRF and Template Security** - CSRF tokens on AJAX POSTs, SpEL/OGNL injection validation in custom template rendering
- [ ] **Phase 31: Null Safety and Transaction Fix** - Transactional CSV import, null team guards in race services, season-scoped driver-team fallback
- [ ] **Phase 32: Layering and Exception Fix** - Remove admin layer imports from domain services, replace ResponseStatusException with domain exceptions
- [ ] **Phase 33: Controller Cleanup** - Move business logic and data transformation from controllers to service layer, fix RaceLineup usage in SiteGeneratorService
- [ ] **Phase 34: Convention Fixes** - Form validation, toString cleanup, English text, CSS classes, log level corrections

## Phase Details

### Phase 28: RaceAttachment Security
**Goal**: File download in RaceAttachmentService is secure against path traversal, null content-type crashes, and header injection
**Depends on**: Phase 27 (previous milestone complete)
**Requirements**: SECU-02, SECU-05, DATA-02
**Success Criteria** (what must be TRUE):
  1. Downloading a race attachment resolves a path and confirms it stays within the upload directory before serving
  2. Downloading a file with no detectable content type returns a safe default (application/octet-stream) instead of throwing NPE
  3. The Content-Disposition filename is sanitized to strip characters that could break the header (newlines, semicolons, quotes)
**Plans**: 1 plan
Plans:
- [x] 28-01-PLAN.md — TDD: Security tests + fixes for downloadAttachment (path traversal, null MIME, header injection)

### Phase 29: Mass Assignment Fix
**Goal**: Matchday create/edit forms bind to a DTO, not a JPA entity, eliminating mass assignment risk
**Depends on**: Phase 28
**Requirements**: SECU-01
**Success Criteria** (what must be TRUE):
  1. Admin can create a matchday and the form data flows through a MatchdayForm DTO before reaching the service
  2. Admin can edit a matchday and the controller never has a JPA Matchday entity as a @ModelAttribute POST target
  3. No JPA-managed fields (id, version, audit timestamps) are bindable from the matchday form submission
**Plans**: TBD

### Phase 30: CSRF and Template Security
**Goal**: AJAX POST requests carry CSRF tokens in prod/docker, and custom template rendering rejects dangerous content
**Depends on**: Phase 29
**Requirements**: SECU-03, SECU-04
**Success Criteria** (what must be TRUE):
  1. Any AJAX POST request sent in prod/docker profile includes the CSRF token and is accepted by Spring Security
  2. Submitting a custom template containing SpEL or OGNL expressions is rejected before rendering
  3. Legitimate custom templates without injection patterns render correctly
**Plans**: TBD
**UI hint**: yes

### Phase 31: Null Safety and Transaction Fix
**Goal**: Multi-race CSV import is atomic and race services handle bye matches and unlinked races without crashing
**Depends on**: Phase 30
**Requirements**: DATA-01, DATA-03, DATA-04
**Success Criteria** (what must be TRUE):
  1. If any race in a multi-race CSV import fails validation, the entire import is rolled back (no partial imports)
  2. Race services (scoring, graphic generation) processing a bye match (null home or away team) return a safe result instead of throwing NPE
  3. Driver-team fallback lookup in standings/scoring filters by the current season so no cross-season misattribution occurs
**Plans**: TBD

### Phase 32: Layering and Exception Fix
**Goal**: Domain services contain no imports from the admin service layer and use domain exceptions instead of HTTP exceptions
**Depends on**: Phase 31
**Requirements**: ARCH-01, ARCH-02
**Success Criteria** (what must be TRUE):
  1. No class under org.ctc.domain.service imports from org.ctc.admin.service
  2. Domain service methods that signal business rule violations throw a domain exception (e.g., ValidationException, EntityNotFoundException) rather than ResponseStatusException
  3. Existing error handling in GlobalExceptionHandler correctly maps the domain exceptions to HTTP responses
**Plans**: TBD

### Phase 33: Controller Cleanup
**Goal**: Controllers delegate all data transformation and business logic to services, and SiteGeneratorService uses RaceLineup as source of truth
**Depends on**: Phase 32
**Requirements**: ARCH-03, ARCH-04
**Success Criteria** (what must be TRUE):
  1. Controller methods identified in the review contain no inline data transformation or business rule logic — those calls are delegated to service methods
  2. SiteGeneratorService resolves driver-team assignment from RaceLineup entries, not from SeasonDriver fallback alone
  3. Generated site pages correctly attribute drivers to their sub-teams as recorded in race lineups
**Plans**: TBD

### Phase 34: Convention Fixes
**Goal**: Form validation, entity toString, UI language, CSS usage, and log levels all follow project conventions
**Depends on**: Phase 33
**Requirements**: CONV-01, CONV-02, CONV-03, CONV-04, CONV-05
**Success Criteria** (what must be TRUE):
  1. PlayoffController.save() validates form input via @Valid + BindingResult and returns the form view with errors on validation failure
  2. SeasonTeam and RaceSettings entity toString methods exclude lazy-loaded associations to prevent accidental N+1 loading
  3. All UI text visible in the browser and all code comments are in English (no German strings remain)
  4. The race results page uses CSS classes from admin.css instead of inline style attributes
  5. Log statements for business rule violations (non-fatal application logic rejections) use log.warn() instead of log.error()
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Exception Infrastructure | v1.0 | 2/2 | Complete | 2026-04-03 |
| 2. Service Layer Extraction | v1.0 | 4/4 | Complete | 2026-04-04 |
| 3. God Service Split | v1.0 | 2/2 | Complete | 2026-04-04 |
| 4. Database Optimization | v1.0 | 1/1 | Complete | 2026-04-04 |
| 5. Security | v1.0 | 3/3 | Complete | 2026-04-04 |
| 6. Security Hardening | v1.1 | 1/1 | Complete | 2026-04-04 |
| 7. Layer Cleanup | v1.1 | 3/3 | Complete | 2026-04-05 |
| 8. Exception Refinement | v1.1 | 2/2 | Complete | 2026-04-05 |
| 9. Alltime Standings | v1.1 | 1/1 | Complete | 2026-04-05 |
| 10. Service Refactoring | v1.1 | 3/3 | Complete | 2026-04-06 |
| 11. Template Quality | v1.1 | 3/3 | Complete | 2026-04-06 |
| 12. Security Hardening Recovery | v1.1 | 1/1 | Complete | 2026-04-06 |
| 13. Layer Cleanup Recovery | v1.1 | 3/3 | Complete | 2026-04-06 |
| 14. Exception Refinement Recovery | v1.1 | 2/2 | Complete | 2026-04-07 |
| 15. Alltime Standings Recovery | v1.1 | 1/1 | Complete | 2026-04-07 |
| 20. English Messages | v1.3 | — | Complete | 2026-04-08 |
| 21. English Code | v1.3 | — | Complete | 2026-04-09 |
| 22. Dev Teams & Drivers | v1.3 | — | Complete | 2026-04-09 |
| 23. Dev Seasons with Results | v1.3 | — | Complete | 2026-04-10 |
| 24. Restore Fictive Dev Data | v1.3 | 1/1 | Complete | 2026-04-10 |
| 25. Fix I18N Regressions | v1.3 | 1/1 | Complete | 2026-04-10 |
| 26. Restore Fictive Team Logos | v1.3 | 1/1 | Complete | 2026-04-10 |
| 27. Restore Matchday/Result Seed Pipeline | v1.3 | 1/1 | Complete | 2026-04-10 |
| 28. RaceAttachment Security | v1.5 | 1/1 | Complete    | 2026-04-13 |
| 29. Mass Assignment Fix | v1.5 | 0/TBD | Not started | - |
| 30. CSRF and Template Security | v1.5 | 0/TBD | Not started | - |
| 31. Null Safety and Transaction Fix | v1.5 | 0/TBD | Not started | - |
| 32. Layering and Exception Fix | v1.5 | 0/TBD | Not started | - |
| 33. Controller Cleanup | v1.5 | 0/TBD | Not started | - |
| 34. Convention Fixes | v1.5 | 0/TBD | Not started | - |
