# Requirements: CTC Manager

**Defined:** 2026-04-07
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.3 Requirements

Requirements for milestone v1.3 — English & Test Data.

### English Cleanup (I18N)

- [ ] **I18N-01**: All German log messages converted to English
- [ ] **I18N-02**: All German exception messages converted to English
- [ ] **I18N-03**: All German constants, enum labels, and string literals converted to English
- [ ] **I18N-04**: All German code comments and Javadoc converted to English
- [ ] **I18N-05**: All German variable and method names renamed to English equivalents

### Test Data (DATA)

- [ ] **DATA-01**: Dev profile creates 14+ teams with fictive names, including 2-3 parent teams with sub-teams
- [ ] **DATA-02**: Dev profile creates 10 drivers per team with fictive names
- [ ] **DATA-03**: Team cards generated for all dev teams
- [ ] **DATA-04**: Dev profile creates League format season with matchdays, races, and results
- [ ] **DATA-05**: Dev profile creates Swiss format season with matchdays, races, and results
- [ ] **DATA-06**: Dev profile creates Round Robin format season (2 groups) with matchdays, races, and results
- [ ] **DATA-07**: Race results use actual existing scoring system for point calculation
- [ ] **DATA-08**: One completed season with first playoff round created

## Future Requirements

None deferred for this milestone.

## Out of Scope

| Feature | Reason |
|---------|--------|
| UI text internationalization (i18n framework) | v1.3 is code-level cleanup, not runtime i18n |
| E2E test updates for new test data | Future milestone — existing E2E tests stay unchanged |
| Flyway migration changes | Test data is dev-profile only, seeded at runtime |
| Production data migration | English cleanup affects code only, not stored data |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| I18N-01 | Phase 20 | Pending |
| I18N-02 | Phase 20 | Pending |
| I18N-03 | Phase 21 | Pending |
| I18N-04 | Phase 21 | Pending |
| I18N-05 | Phase 21 | Pending |
| DATA-01 | Phase 22 | Pending |
| DATA-02 | Phase 22 | Pending |
| DATA-03 | Phase 22 | Pending |
| DATA-04 | Phase 23 | Pending |
| DATA-05 | Phase 23 | Pending |
| DATA-06 | Phase 23 | Pending |
| DATA-07 | Phase 23 | Pending |
| DATA-08 | Phase 24 | Pending |

**Coverage:**
- v1.3 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-07*
*Last updated: 2026-04-07 after roadmap creation (v1.3 phases 20-24)*
