# Roadmap: CTC Manager

## Milestones

- :white_check_mark: **v1.0 Technical Debt Cleanup** — Phases 1-5 (shipped 2026-04-04)
- :white_check_mark: **v1.1 Codebase Concerns Cleanup** — Phases 6-15 (shipped 2026-04-07)
- :white_check_mark: **v1.2 Driver Merge** — Phases 16-19 (shipped 2026-04-07)
- :construction: **v1.3 English & Test Data** — Phases 20-24 (in progress)

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
<summary>v1.2 Driver Merge (Phases 16-19) -- SHIPPED 2026-04-07</summary>

- [x] Phase 16: Merge Service Core (1/1 plan) -- completed 2026-04-07
- [x] Phase 17: Duplicate-Handling (1/1 plan) -- completed 2026-04-07
- [x] Phase 18: Merge UI (2/2 plans) -- completed 2026-04-07
- [x] Phase 19: Merge Error Handling (1/1 plan) -- completed 2026-04-07

See: milestones/v1.2-ROADMAP.md for full details

</details>

### v1.3 English & Test Data (In Progress)

**Milestone Goal:** Full English cleanup of the entire codebase and comprehensive dev test data covering all season formats with real scoring and playoffs.

- [x] **Phase 20: English Messages** — Convert all German log and exception messages to English (completed 2026-04-07)
- [x] **Phase 21: English Code** — Convert all German constants, comments, Javadoc, variable and method names to English (completed 2026-04-09)
- [x] **Phase 22: Dev Teams & Drivers** — Seed 14+ fictive teams with sub-teams, 10 drivers each, and generate team cards (completed 2026-04-09)
- [ ] **Phase 23: Dev Seasons with Results** — Seed League, Swiss, and Round Robin seasons with matchdays, races, and scored results
- [ ] **Phase 24: Dev Playoffs** — Seed first playoff round for one completed season

## Phase Details

### Phase 20: English Messages
**Goal**: All runtime-observable German messages are replaced by English equivalents
**Depends on**: Phase 19 (v1.2 complete)
**Requirements**: I18N-01, I18N-02
**Success Criteria** (what must be TRUE):
  1. No German text appears in application log output during normal operation
  2. All exception messages thrown by the application are in English
  3. Developer scanning logs can read every entry without German language knowledge
  4. Exception stack traces shown on the error page contain only English descriptions
**Plans:** 1/1 plans complete
Plans:
- [x] 20-01-PLAN.md — Translate all German comments to English across 5 files and verify

### Phase 21: English Code
**Goal**: Every German identifier, string literal, constant, comment, and Javadoc entry in the codebase has been replaced with English equivalents
**Depends on**: Phase 20
**Requirements**: I18N-03, I18N-04, I18N-05
**Success Criteria** (what must be TRUE):
  1. IDE search for German words returns zero hits in production source files
  2. All public and private method names, variable names, and field names are English
  3. All enum constants and string literals used in logic are English
  4. All Javadoc and inline comments are English
  5. Code reviews can be conducted without German language knowledge
**Plans:** 1/1 plans complete
Plans:
- [x] 21-01-PLAN.md — Replace German text in test files and HTML templates, verify no German remains

### Phase 22: Dev Teams & Drivers
**Goal**: The dev profile starts with a complete, realistic set of fictive teams and drivers that cover all structural variations (parent teams, sub-teams) and have generated team card images
**Depends on**: Phase 21
**Requirements**: DATA-01, DATA-02, DATA-03
**Success Criteria** (what must be TRUE):
  1. Starting the dev profile creates 14 or more teams with entirely fictive names
  2. At least 2 parent teams exist, each with 2 or more named sub-teams
  3. Each team has exactly 10 drivers with fictive names
  4. Every team has a generated team card image visible on the teams list page
**Plans:** 1/1 plans complete
Plans:
- [x] 22-01-PLAN.md — Replace real teams/drivers with fictive data, add team card generation
**UI hint**: yes

### Phase 23: Dev Seasons with Results
**Goal**: The dev profile populates three fully played-out seasons — one per format — each with real matchdays, race results, and points calculated by the existing scoring system
**Depends on**: Phase 22
**Requirements**: DATA-04, DATA-05, DATA-06, DATA-07
**Success Criteria** (what must be TRUE):
  1. A League format season exists with at least 3 matchdays, each containing races with results
  2. A Swiss format season exists with at least 3 matchdays, each containing races with results
  3. A Round Robin format season with 2 groups exists with at least 2 matchdays per group
  4. Standings pages for all three seasons display non-zero points derived from the scoring system
  5. Race result entries reflect point values consistent with the configured scoring rules
**Plans:** 1 plan
Plans:
- [ ] 21-01-PLAN.md — Replace German text in test files and HTML templates, verify no German remains
**UI hint**: yes

### Phase 24: Dev Playoffs
**Goal**: One completed season in the dev profile has a first playoff round seeded and visible in the bracket view
**Depends on**: Phase 23
**Requirements**: DATA-08
**Success Criteria** (what must be TRUE):
  1. One season is marked as completed with a playoff bracket created
  2. The first playoff round shows seeded matchups on the bracket page
  3. The bracket view renders without errors using dev profile data
**Plans:** 1 plan
Plans:
- [ ] 21-01-PLAN.md — Replace German text in test files and HTML templates, verify no German remains
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
| 16. Merge Service Core | v1.2 | 1/1 | Complete | 2026-04-07 |
| 17. Duplicate-Handling | v1.2 | 1/1 | Complete | 2026-04-07 |
| 18. Merge UI | v1.2 | 2/2 | Complete | 2026-04-07 |
| 19. Merge Error Handling | v1.2 | 1/1 | Complete | 2026-04-07 |
| 20. English Messages | v1.3 | 1/1 | Complete    | 2026-04-07 |
| 21. English Code | v1.3 | 1/1 | Complete    | 2026-04-09 |
| 22. Dev Teams & Drivers | v1.3 | 1/1 | Complete    | 2026-04-09 |
| 23. Dev Seasons with Results | v1.3 | 0/? | Not started | - |
| 24. Dev Playoffs | v1.3 | 0/? | Not started | - |
