# Requirements: CTC Manager

**Defined:** 2026-04-13
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.5 Requirements

Requirements for Code Review Fixes milestone. Each maps to roadmap phases.

### Security

- [x] **SECU-01**: Admin can create/edit matchdays via MatchdayForm DTO instead of direct JPA entity binding
- [x] **SECU-02**: File download validates resolved path stays within upload directory (path traversal defense)
- [ ] **SECU-03**: AJAX POST requests include CSRF token for prod/docker profile compatibility
- [ ] **SECU-04**: Custom template rendering validates content for SpEL/OGNL injection before execution
- [x] **SECU-05**: Content-Disposition header uses sanitized filename to prevent header injection

### Data Integrity

- [ ] **DATA-01**: Multi-race CSV import runs within a single transaction (all-or-nothing)
- [x] **DATA-02**: File download handles null content type from probeContentType gracefully
- [ ] **DATA-03**: Race services handle null home/away teams without NPE (bye matches, unlinked races)
- [ ] **DATA-04**: Driver-team fallback check filters by current season to prevent cross-season misattribution

### Architecture

- [ ] **ARCH-01**: Domain services do not import from admin service layer (layering fix or relocation)
- [ ] **ARCH-02**: Domain services use domain exceptions instead of HTTP-specific ResponseStatusException
- [ ] **ARCH-03**: Controller methods delegate data transformation and business logic to service layer
- [ ] **ARCH-04**: Site generator uses RaceLineup as source of truth for driver-team assignment

### Convention

- [ ] **CONV-01**: PlayoffController.save() validates form input with @Valid and BindingResult
- [ ] **CONV-02**: SeasonTeam and RaceSettings entities exclude lazy associations from toString
- [ ] **CONV-03**: All UI text and code comments use English (no German remnants)
- [ ] **CONV-04**: Race results page uses CSS classes from admin.css instead of inline styles
- [ ] **CONV-05**: Business rule violations log at warn level, not error level

## Future Requirements

(None deferred for this milestone)

## Out of Scope

| Feature | Reason |
|---------|--------|
| CSRF disabled globally | Only fixing missing tokens on AJAX requests, not changing overall CSRF strategy |
| Full SpEL sandbox | Validation-based approach sufficient for admin-only templates |
| Refactor all controller logic | Only fixing identified violations, not comprehensive controller audit |
| Database schema changes | No Flyway migrations needed for these fixes |
| New test infrastructure | Fixes verified by existing test suite + targeted new tests |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SECU-01 | Phase 29 | Verified |
| SECU-02 | Phase 28 | Verified |
| SECU-03 | Phase 30 | Pending |
| SECU-04 | Phase 30 | Pending |
| SECU-05 | Phase 28 | Verified |
| DATA-01 | Phase 31 | Pending |
| DATA-02 | Phase 28 | Verified |
| DATA-03 | Phase 31, Phase 35 | Pending |
| DATA-04 | Phase 31 | Pending |
| ARCH-01 | Phase 32 | Pending |
| ARCH-02 | Phase 32 | Pending |
| ARCH-03 | Phase 33 | Pending |
| ARCH-04 | Phase 33 | Pending |
| CONV-01 | Phase 34 | Pending |
| CONV-02 | Phase 34 | Compliant (no change needed) |
| CONV-03 | Phase 34 | Compliant (no change needed) |
| CONV-04 | Phase 34, Phase 36 | Pending |
| CONV-05 | Phase 34 | Compliant (no change needed) |

**Coverage:**

- v1.5 requirements: 18 total
- Mapped to phases: 18
- Verified: 4 (SECU-01, SECU-02, SECU-05, DATA-02)
- Compliant (no change needed): 3 (CONV-02, CONV-03, CONV-05)
- Pending verification: 11
- Unmapped: 0

---
*Requirements defined: 2026-04-13*
*Last updated: 2026-04-14 after gap closure phases added (35-36)*
