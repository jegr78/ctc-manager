# Roadmap: CTC Manager

## Milestones

- :white_check_mark: **v1.0 Technical Debt Cleanup** — Phases 1-5 (shipped 2026-04-04)
- :white_check_mark: **v1.1 Codebase Concerns Cleanup** — Phases 6-15 (shipped 2026-04-07)
- **v1.2 Driver Merge** — Phases 16-18 (active)

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

### v1.2 Driver Merge (Phases 16-18)

- [ ] **Phase 16: Merge Service Core** - FK reassignment, audit logging, and source driver deletion
- [ ] **Phase 17: Duplicate-Handling** - Conflict resolution for unique-constraint violations across all three FK tables
- [ ] **Phase 18: Merge UI** - Merge button, target selection, preview, and confirmation flow on driver detail page

## Phase Details

### Phase 16: Merge Service Core
**Goal**: Admin can execute a driver merge that reassigns all FK references and deletes the source driver
**Depends on**: Nothing (self-contained service layer)
**Requirements**: MERGE-05, MERGE-06, MERGE-07, MERGE-08, MERGE-09, MERGE-10, MERGE-14
**Success Criteria** (what must be TRUE):
  1. All SeasonDriver entries of the source driver are reassigned to the target driver
  2. All RaceLineup entries of the source driver are reassigned to the target driver
  3. All RaceResult entries of the source driver are reassigned to the target driver
  4. All PsnAlias entries and the source driver's PSN-ID are transferred to the target driver as aliases
  5. The source driver is deleted after all FK references are reassigned
  6. Every merge attempt is logged with source driver, target driver, timestamp, and count of affected references
**Plans:** 1 plan
Plans:
- [ ] 16-01-PLAN.md — TDD: DriverMergeService with FK reassignment, PSN-ID transfer, audit logging

### Phase 17: Duplicate-Handling
**Goal**: The merge service resolves unique-constraint conflicts without data loss or uncaught exceptions
**Depends on**: Phase 16
**Requirements**: MERGE-11, MERGE-12, MERGE-13
**Success Criteria** (what must be TRUE):
  1. When source and target driver are both in the same season, the duplicate SeasonDriver entry is dropped rather than causing a constraint violation
  2. When source and target driver are both in the same race lineup, the duplicate RaceLineup entry is dropped rather than causing a constraint violation
  3. When source and target driver have results for the same race, the duplicate RaceResult entry is dropped rather than causing a constraint violation
  4. All non-duplicate entries across all three FK tables are still reassigned correctly when conflicts exist
**Plans**: TBD

### Phase 18: Merge UI
**Goal**: Admin can initiate, preview, and confirm a driver merge from the driver detail page
**Depends on**: Phase 17
**Requirements**: MERGE-01, MERGE-02, MERGE-03, MERGE-04
**Success Criteria** (what must be TRUE):
  1. A merge button is visible on the driver detail page that opens the merge workflow
  2. Admin can search for and select the target driver from all existing drivers except the current one
  3. Admin sees a preview listing the number of SeasonDriver, RaceLineup, RaceResult, and PsnAlias entries that will be reassigned
  4. Admin must explicitly confirm the merge before any data is changed
  5. After a successful merge the admin is redirected to the target driver's detail page with a success message
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
| 16. Merge Service Core | v1.2 | 0/1 | Not started | - |
| 17. Duplicate-Handling | v1.2 | 0/? | Not started | - |
| 18. Merge UI | v1.2 | 0/? | Not started | - |
