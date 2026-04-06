# Roadmap: CTC Manager

## Milestones

- :white_check_mark: **v1.0 Technical Debt Cleanup** — Phases 1-5 (shipped 2026-04-04)
- :construction: **v1.1 Codebase Concerns Cleanup** — Phases 6-11 (in progress)

## Phases

<details>
<summary>v1.0 Technical Debt Cleanup (Phases 1-5) -- SHIPPED 2026-04-04</summary>

- [x] Phase 1: Exception Infrastructure (2/2 plans) -- completed 2026-04-03
- [x] Phase 2: Service Layer Extraction (4/4 plans) -- completed 2026-04-04
- [x] Phase 3: God Service Split (2/2 plans) -- completed 2026-04-04
- [x] Phase 4: Database Optimization (1/1 plan) -- completed 2026-04-04
- [x] Phase 5: Security (3/3 plans) -- completed 2026-04-04

</details>

### v1.1 Codebase Concerns Cleanup (In Progress)

**Milestone Goal:** Alle identifizierten technischen Concerns aus dem Codebase-Audit systematisch beheben -- von Layer-Violations ueber Security-Hardening bis hin zu Code-Reduktion.

- [x] **Phase 6: Security Hardening** (1/1 plan) -- completed 2026-04-04
- [x] **Phase 7: Layer Cleanup** (3/3 plans) -- completed 2026-04-05
- [x] **Phase 8: Exception Refinement** (2/2 plans) -- completed 2026-04-05
- [x] **Phase 9: Alltime Standings** (1/1 plan) -- completed 2026-04-05
- [x] **Phase 10: Service Refactoring** (3/3 plans) -- completed 2026-04-06
- [x] **Phase 11: Template Quality** (3/3 plans) -- completed 2026-04-06
- [ ] **Phase 12: Security Hardening Recovery** - Re-apply SSRF + path traversal protections lost by worktree clobber
- [ ] **Phase 13: Layer Cleanup Recovery** - Re-apply controller service delegation + DTO decoupling
- [ ] **Phase 14: Exception Refinement Recovery** - Re-apply specific exception catches in controllers
- [ ] **Phase 15: Alltime Standings Recovery** - Re-apply cross-season standings aggregation

## Phase Details

<details>
<summary>Phase 6: Security Hardening -- COMPLETED 2026-04-04</summary>

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

</details>

<details>
<summary>Phase 7: Layer Cleanup -- COMPLETED 2026-04-05</summary>

**Goal**: Controllers contain no business logic and no direct repository access -- clean three-tier separation
**Depends on**: Phase 6
**Requirements**: ARCH-01, ARCH-02, FEAT-02
**Success Criteria** (what must be TRUE):
  1. Domain services (org.ctc.domain.service) have zero imports from org.ctc.admin.dto
  2. StandingsController, PowerRankingsController, PlayoffController, TeamCardController, and CsvImportController inject only services, no repositories
  3. Buchholz calculation and Swiss-system sorting logic lives in StandingsService, not in StandingsController
  4. All existing admin UI pages render correctly with unchanged behavior
**Plans:** 3 plans

Plans:
- [x] 07-01-PLAN.md — Service layer cleanup
- [x] 07-02-PLAN.md — Simple service DTO decoupling
- [x] 07-03-PLAN.md — DTO decoupling (ARCH-01)

</details>

<details>
<summary>Phase 8: Exception Refinement -- COMPLETED 2026-04-05</summary>

**Goal**: Exception handling is specific and intentional -- no blanket catch-all blocks hiding real errors
**Depends on**: Phase 7
**Requirements**: ERRH-01, QUAL-02
**Success Criteria** (what must be TRUE):
  1. No catch(Exception e) blocks remain in controllers -- each catch targets a specific exception type (IOException, IllegalStateException, etc.)
  2. Unexpected exceptions propagate to GlobalExceptionHandler and display the admin error page
  3. RaceService, DriverService, and DriverRankingService findAll() calls are scoped by seasonId or have result limits
**Plans:** 2 plans

Plans:
- [x] 08-01-PLAN.md — Exception refinement in controllers
- [x] 08-02-PLAN.md — Unbounded query scoping

</details>

<details>
<summary>Phase 9: Alltime Standings -- COMPLETED 2026-04-05</summary>

**Goal**: Users can view aggregated team standings across all seasons
**Depends on**: Phase 7 (FEAT-02: StandingsService refactoring)
**Requirements**: FEAT-01
**Success Criteria** (what must be TRUE):
  1. Alltime Standings page displays a ranked list of teams with aggregated cross-season data
  2. StandingsService.calculateAlltimeStandings() returns correct aggregation across multiple seasons
  3. The existing per-season standings remain unchanged
**Plans:** 1 plan
**UI hint**: yes

Plans:
- [x] 09-01-PLAN.md — Cross-season team standings aggregation (TDD)

</details>

<details>
<summary>Phase 10: Service Refactoring -- COMPLETED 2026-04-06</summary>

**Goal**: Large service classes and duplicated controller code are split into focused, maintainable units
**Depends on**: Phase 7
**Requirements**: ARCH-03, ARCH-04, ARCH-05
**Success Criteria** (what must be TRUE):
  1. TemplateEditorController uses a generic Map<String, TemplateManageable> dispatch instead of 20 copy-paste save/reset blocks
  2. PlayoffService bracket-view and seeding logic are in separate focused services
  3. RaceService form-data assembly and calendar-event logic are in separate focused services
  4. All graphic editing, playoff, and race functionality works identically from the UI
**Plans:** 3/3 plans complete

Plans:
- [x] 10-01-PLAN.md — TemplateEditorController generic dispatch with TemplateManageable interface
- [x] 10-02-PLAN.md — PlayoffService split into BracketView + Seeding services
- [x] 10-03-PLAN.md — RaceService split into FormData + Calendar services

</details>

<details>
<summary>Phase 11: Template Quality -- COMPLETED 2026-04-06</summary>

**Goal**: Admin templates use consistent CSS classes instead of scattered inline styles
**Goal**: Admin templates use consistent CSS classes instead of scattered inline styles
**Depends on**: Nothing (independent)
**Requirements**: QUAL-01
**Success Criteria** (what must be TRUE):
  1. season-detail and race-detail templates contain no inline style attributes (excluding graphic-render templates)
  2. All remaining admin templates have inline styles replaced with CSS utility classes from admin.css
  3. Visual appearance of all admin pages is unchanged (verified via Playwright screenshots)
**Plans:** 3/3 plans complete
**UI hint**: yes

Plans:
- [x] 11-01-PLAN.md — CSS class library + P1 templates (season-detail, race-detail)
- [x] 11-02-PLAN.md — P2 templates (matchday-detail + 34 remaining templates)
- [x] 11-03-PLAN.md — template-editors.html (181 inline styles, separate per D-07)

</details>

### Phase 12: Security Hardening Recovery
**Goal**: Re-apply SSRF hostname validation and path traversal protections lost by worktree file clobber
**Depends on**: Nothing (independent)
**Requirements**: SECU-01, SECU-02
**Gap Closure**: Closes gaps from v1.1 audit (commit 5b3a58b regression)
**Success Criteria** (what must be TRUE):
  1. FileStorageService.storeFromUrl() rejects URLs targeting private IPs, localhost, and internal networks
  2. FileStorageService.store() and storeImage() reject filenames containing path traversal sequences
  3. Existing file upload and URL import functionality continues to work for legitimate inputs
**Recovery Source**: Commit `84e8896` (original Phase 6 implementation)
**Plans:** 1/1 plans complete

Plans:
- [x] 12-01-PLAN.md — Restore SSRF hostname validation + path traversal protection (TDD)

### Phase 13: Layer Cleanup Recovery
**Goal**: Re-apply controller service delegation and domain DTO decoupling lost by worktree file clobber
**Depends on**: Phase 12
**Requirements**: ARCH-01, ARCH-02, FEAT-02
**Gap Closure**: Closes gaps from v1.1 audit (commit 5b3a58b regression)
**Success Criteria** (what must be TRUE):
  1. Domain services (org.ctc.domain.service) have zero imports from org.ctc.admin.dto
  2. StandingsController, PowerRankingsController, PlayoffController, TeamCardController, CsvImportController inject only services, no repositories
  3. Buchholz calculation and Swiss-system sorting logic lives in StandingsService, not StandingsController
  4. All existing admin UI pages render correctly with unchanged behavior
**Recovery Source**: Phase 7 commits (`b733781` + Plan 02/03 commits)
**Plans:** 3 plans

Plans:
- [ ] 13-01-PLAN.md — Service finder methods + controller repository removal (ARCH-02, FEAT-02)
- [ ] 13-02-PLAN.md — Simple service DTO decoupling (ARCH-01: Car, Track, Driver, RaceScoring, MatchScoring)
- [ ] 13-03-PLAN.md — Complex service DTO decoupling (ARCH-01: Season, Team, Playoff, Matchday)

### Phase 14: Exception Refinement Recovery
**Goal**: Re-apply specific exception catches in controllers lost by worktree file clobber
**Depends on**: Phase 13
**Requirements**: ERRH-01
**Gap Closure**: Closes gaps from v1.1 audit (commit 5b3a58b regression)
**Success Criteria** (what must be TRUE):
  1. No catch(Exception e) blocks remain in controllers — each catch targets a specific exception type
  2. Unexpected exceptions propagate to GlobalExceptionHandler and display the admin error page
**Recovery Source**: Phase 8 commits (`1960a9e`, `e645d43`, `3a21dde`, `981a42a`, `1d7e37f`)

### Phase 15: Alltime Standings Recovery
**Goal**: Re-apply cross-season team standings aggregation lost by worktree file clobber
**Depends on**: Phase 13 (FEAT-02: StandingsService refactoring)
**Requirements**: FEAT-01
**Gap Closure**: Closes gaps from v1.1 audit (commit 5b3a58b regression)
**Success Criteria** (what must be TRUE):
  1. StandingsService.calculateAlltimeStandings() returns correct aggregation across multiple seasons
  2. GET /admin/standings with alltime=true displays a ranked list of teams
  3. The existing per-season standings remain unchanged
**Recovery Source**: Phase 9 commits (`0979c0f`, `d5c6e56`)

## Progress

**Execution Order:**
Phases execute in numeric order: 6 -> 7 -> 8 -> 9 -> 10 -> 11 -> 12 -> 13 -> 14 -> 15

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
| 10. Service Refactoring | v1.1 | 3/3 | Complete    | 2026-04-06 |
| 11. Template Quality | v1.1 | 3/3 | Complete    | 2026-04-06 |
| 12. Security Hardening Recovery | v1.1 | 1/1 | Complete    | 2026-04-06 |
| 13. Layer Cleanup Recovery | v1.1 | 0/3 | Planned | — |
| 14. Exception Refinement Recovery | v1.1 | 0/0 | Pending | — |
| 15. Alltime Standings Recovery | v1.1 | 0/0 | Pending | — |
